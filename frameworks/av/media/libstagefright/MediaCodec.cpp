/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaCodec"
#include <inttypes.h>

#include "include/avc_utils.h"
#include "include/SecureBuffer.h"
#include "include/SharedMemoryBuffer.h"
#include "include/SoftwareRenderer.h"

#include <android/hardware/cas/native/1.0/IDescrambler.h>

#include <binder/IMemory.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <gui/BufferQueue.h>
#include <gui/Surface.h>
#include <media/ICrypto.h>
#include <media/IOMX.h>
#include <media/IResourceManagerService.h>
#include <media/MediaCodecBuffer.h>
#include <media/MediaAnalyticsItem.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/ACodec.h>
#include <media/stagefright/BufferProducerWrapper.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaCodecList.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaFilter.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/PersistentSurface.h>
#include <media/stagefright/SurfaceUtils.h>
#include <mediautils/BatteryNotifier.h>
#include <private/android_filesystem_config.h>
#include <utils/Log.h>
#include <utils/Singleton.h>
#include <stagefright/AVStageExtensions.h>

namespace android {

// key for media statistics
static const char *kCodecKeyName = "codec";
// attrs for media statistics
static const char *kCodecCodec = "android.media.mediacodec.codec";  /* e.g. OMX.google.aac.decoder */
static const char *kCodecMime = "android.media.mediacodec.mime";    /* e.g. audio/mime */
static const char *kCodecMode = "android.media.mediacodec.mode";    /* audio, video */
static const char *kCodecSecure = "android.media.mediacodec.secure";   /* 0, 1 */
static const char *kCodecHeight = "android.media.mediacodec.height";   /* 0..n */
static const char *kCodecWidth = "android.media.mediacodec.width";     /* 0..n */
static const char *kCodecRotation = "android.media.mediacodec.rotation-degrees";  /* 0/90/180/270 */
static const char *kCodecCrypto = "android.media.mediacodec.crypto";   /* 0,1 */
static const char *kCodecEncoder = "android.media.mediacodec.encoder"; /* 0,1 */

static const char *kCodecBytesIn = "android.media.mediacodec.bytesin";  /* 0..n */
static const char *kCodecProfile = "android.media.mediacodec.profile";  /* 0..n */
static const char *kCodecLevel = "android.media.mediacodec.level";  /* 0..n */
static const char *kCodecMaxWidth = "android.media.mediacodec.maxwidth";  /* 0..n */
static const char *kCodecMaxHeight = "android.media.mediacodec.maxheight";  /* 0..n */
static const char *kCodecError = "android.media.mediacodec.errcode";
static const char *kCodecErrorState = "android.media.mediacodec.errstate";


static int64_t getId(const sp<IResourceManagerClient> &client) {
    return (int64_t) client.get();
}

static bool isResourceError(status_t err) {
    return (err == NO_MEMORY);
}

static const int kMaxRetry = 2;
static const int kMaxReclaimWaitTimeInUs = 500000;  // 0.5s
static const int kNumBuffersAlign = 16;

////////////////////////////////////////////////////////////////////////////////

struct ResourceManagerClient : public BnResourceManagerClient {
    explicit ResourceManagerClient(MediaCodec* codec) : mMediaCodec(codec) {}

    virtual bool reclaimResource() {
        sp<MediaCodec> codec = mMediaCodec.promote();
        if (codec == NULL) {
            // codec is already gone.
            return true;
        }
        status_t err = codec->reclaim();
        if (err == WOULD_BLOCK) {
            ALOGD("Wait for the client to release codec.");
            usleep(kMaxReclaimWaitTimeInUs);
            ALOGD("Try to reclaim again.");
            err = codec->reclaim(true /* force */);
        }
        if (err != OK) {
            ALOGW("ResourceManagerClient failed to release codec with err %d", err);
        }
        return (err == OK);
    }

    virtual String8 getName() {
        String8 ret;
        sp<MediaCodec> codec = mMediaCodec.promote();
        if (codec == NULL) {
            // codec is already gone.
            return ret;
        }

        AString name;
        if (codec->getName(&name) == OK) {
            ret.setTo(name.c_str());
        }
        return ret;
    }

protected:
    virtual ~ResourceManagerClient() {}

private:
    wp<MediaCodec> mMediaCodec;

    DISALLOW_EVIL_CONSTRUCTORS(ResourceManagerClient);
};

MediaCodec::ResourceManagerServiceProxy::ResourceManagerServiceProxy(pid_t pid)
        : mPid(pid) {
    if (mPid == MediaCodec::kNoPid) {
        mPid = IPCThreadState::self()->getCallingPid();
    }
}

MediaCodec::ResourceManagerServiceProxy::~ResourceManagerServiceProxy() {
    if (mService != NULL) {
        IInterface::asBinder(mService)->unlinkToDeath(this);
    }
}

void MediaCodec::ResourceManagerServiceProxy::init() {
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.resource_manager"));
    mService = interface_cast<IResourceManagerService>(binder);
    if (mService == NULL) {
        ALOGE("Failed to get ResourceManagerService");
        return;
    }
    IInterface::asBinder(mService)->linkToDeath(this);
}

void MediaCodec::ResourceManagerServiceProxy::binderDied(const wp<IBinder>& /*who*/) {
    ALOGW("ResourceManagerService died.");
    Mutex::Autolock _l(mLock);
    mService.clear();
}

void MediaCodec::ResourceManagerServiceProxy::addResource(
        int64_t clientId,
        const sp<IResourceManagerClient> &client,
        const Vector<MediaResource> &resources) {
    Mutex::Autolock _l(mLock);
    if (mService == NULL) {
        return;
    }
    mService->addResource(mPid, clientId, client, resources);
}

void MediaCodec::ResourceManagerServiceProxy::removeResource(int64_t clientId) {
    Mutex::Autolock _l(mLock);
    if (mService == NULL) {
        return;
    }
    mService->removeResource(mPid, clientId);
}

bool MediaCodec::ResourceManagerServiceProxy::reclaimResource(
        const Vector<MediaResource> &resources) {
    Mutex::Autolock _l(mLock);
    if (mService == NULL) {
        return false;
    }
    return mService->reclaimResource(mPid, resources);
}

////////////////////////////////////////////////////////////////////////////////

MediaCodec::BufferInfo::BufferInfo() : mOwnedByClient(false) {}

////////////////////////////////////////////////////////////////////////////////

namespace {

enum {
    kWhatFillThisBuffer      = 'fill',
    kWhatDrainThisBuffer     = 'drai',
    kWhatEOS                 = 'eos ',
    kWhatStartCompleted      = 'Scom',
    kWhatStopCompleted       = 'scom',
    kWhatReleaseCompleted    = 'rcom',
    kWhatFlushCompleted      = 'fcom',
    kWhatError               = 'erro',
    kWhatComponentAllocated  = 'cAll',
    kWhatComponentConfigured = 'cCon',
    kWhatInputSurfaceCreated = 'isfc',
    kWhatInputSurfaceAccepted = 'isfa',
    kWhatSignaledInputEOS    = 'seos',
    kWhatOutputFramesRendered = 'outR',
    kWhatOutputBuffersChanged = 'outC',
};

class BufferCallback : public CodecBase::BufferCallback {
public:
    explicit BufferCallback(const sp<AMessage> &notify);
    virtual ~BufferCallback() = default;

    virtual void onInputBufferAvailable(
            size_t index, const sp<MediaCodecBuffer> &buffer) override;
    virtual void onOutputBufferAvailable(
            size_t index, const sp<MediaCodecBuffer> &buffer) override;
private:
    const sp<AMessage> mNotify;
};

BufferCallback::BufferCallback(const sp<AMessage> &notify)
    : mNotify(notify) {}

void BufferCallback::onInputBufferAvailable(
        size_t index, const sp<MediaCodecBuffer> &buffer) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatFillThisBuffer);
    notify->setSize("index", index);
    notify->setObject("buffer", buffer);
    notify->post();
}

void BufferCallback::onOutputBufferAvailable(
        size_t index, const sp<MediaCodecBuffer> &buffer) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatDrainThisBuffer);
    notify->setSize("index", index);
    notify->setObject("buffer", buffer);
    notify->post();
}

class CodecCallback : public CodecBase::CodecCallback {
public:
    explicit CodecCallback(const sp<AMessage> &notify);
    virtual ~CodecCallback() = default;

    virtual void onEos(status_t err) override;
    virtual void onStartCompleted() override;
    virtual void onStopCompleted() override;
    virtual void onReleaseCompleted() override;
    virtual void onFlushCompleted() override;
    virtual void onError(status_t err, enum ActionCode actionCode) override;
    virtual void onComponentAllocated(const char *componentName) override;
    virtual void onComponentConfigured(
            const sp<AMessage> &inputFormat, const sp<AMessage> &outputFormat) override;
    virtual void onInputSurfaceCreated(
            const sp<AMessage> &inputFormat,
            const sp<AMessage> &outputFormat,
            const sp<BufferProducerWrapper> &inputSurface) override;
    virtual void onInputSurfaceCreationFailed(status_t err) override;
    virtual void onInputSurfaceAccepted(
            const sp<AMessage> &inputFormat,
            const sp<AMessage> &outputFormat) override;
    virtual void onInputSurfaceDeclined(status_t err) override;
    virtual void onSignaledInputEOS(status_t err) override;
    virtual void onOutputFramesRendered(const std::list<FrameRenderTracker::Info> &done) override;
    virtual void onOutputBuffersChanged() override;
private:
    const sp<AMessage> mNotify;
};

CodecCallback::CodecCallback(const sp<AMessage> &notify) : mNotify(notify) {}

void CodecCallback::onEos(status_t err) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatEOS);
    notify->setInt32("err", err);
    notify->post();
}

void CodecCallback::onStartCompleted() {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatStartCompleted);
    notify->post();
}

void CodecCallback::onStopCompleted() {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatStopCompleted);
    notify->post();
}

void CodecCallback::onReleaseCompleted() {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatReleaseCompleted);
    notify->post();
}

void CodecCallback::onFlushCompleted() {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatFlushCompleted);
    notify->post();
}

void CodecCallback::onError(status_t err, enum ActionCode actionCode) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatError);
    notify->setInt32("err", err);
    notify->setInt32("actionCode", actionCode);
    notify->post();
}

void CodecCallback::onComponentAllocated(const char *componentName) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatComponentAllocated);
    notify->setString("componentName", componentName);
    notify->post();
}

void CodecCallback::onComponentConfigured(
        const sp<AMessage> &inputFormat, const sp<AMessage> &outputFormat) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatComponentConfigured);
    notify->setMessage("input-format", inputFormat);
    notify->setMessage("output-format", outputFormat);
    notify->post();
}

void CodecCallback::onInputSurfaceCreated(
        const sp<AMessage> &inputFormat,
        const sp<AMessage> &outputFormat,
        const sp<BufferProducerWrapper> &inputSurface) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatInputSurfaceCreated);
    notify->setMessage("input-format", inputFormat);
    notify->setMessage("output-format", outputFormat);
    notify->setObject("input-surface", inputSurface);
    notify->post();
}

void CodecCallback::onInputSurfaceCreationFailed(status_t err) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatInputSurfaceCreated);
    notify->setInt32("err", err);
    notify->post();
}

void CodecCallback::onInputSurfaceAccepted(
        const sp<AMessage> &inputFormat,
        const sp<AMessage> &outputFormat) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatInputSurfaceAccepted);
    notify->setMessage("input-format", inputFormat);
    notify->setMessage("output-format", outputFormat);
    notify->post();
}

void CodecCallback::onInputSurfaceDeclined(status_t err) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatInputSurfaceAccepted);
    notify->setInt32("err", err);
    notify->post();
}

void CodecCallback::onSignaledInputEOS(status_t err) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatSignaledInputEOS);
    if (err != OK) {
        notify->setInt32("err", err);
    }
    notify->post();
}

void CodecCallback::onOutputFramesRendered(const std::list<FrameRenderTracker::Info> &done) {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatOutputFramesRendered);
    if (MediaCodec::CreateFramesRenderedMessage(done, notify)) {
        notify->post();
    }
}

void CodecCallback::onOutputBuffersChanged() {
    sp<AMessage> notify(mNotify->dup());
    notify->setInt32("what", kWhatOutputBuffersChanged);
    notify->post();
}

}  // namespace

////////////////////////////////////////////////////////////////////////////////

// static
sp<MediaCodec> MediaCodec::CreateByType(
        const sp<ALooper> &looper, const AString &mime, bool encoder, status_t *err, pid_t pid,
        uid_t uid) {
    sp<MediaCodec> codec = new MediaCodec(looper, pid, uid);

    const status_t ret = codec->init(mime, true /* nameIsType */, encoder);
    if (err != NULL) {
        *err = ret;
    }
    return ret == OK ? codec : NULL; // NULL deallocates codec.
}

// static
sp<MediaCodec> MediaCodec::CreateByComponentName(
        const sp<ALooper> &looper, const AString &name, status_t *err, pid_t pid, uid_t uid) {
    sp<MediaCodec> codec = new MediaCodec(looper, pid, uid);

    const status_t ret = codec->init(name, false /* nameIsType */, false /* encoder */);
    if (err != NULL) {
        *err = ret;
    }
    return ret == OK ? codec : NULL; // NULL deallocates codec.
}

// static
sp<PersistentSurface> MediaCodec::CreatePersistentInputSurface() {
    OMXClient client;
    if (client.connect() != OK) {
        ALOGE("Failed to connect to OMX to create persistent input surface.");
        return NULL;
    }

    sp<IOMX> omx = client.interface();

    sp<IGraphicBufferProducer> bufferProducer;
    sp<IGraphicBufferSource> bufferSource;

    status_t err = omx->createInputSurface(&bufferProducer, &bufferSource);

    if (err != OK) {
        ALOGE("Failed to create persistent input surface.");
        return NULL;
    }

    return new PersistentSurface(bufferProducer, bufferSource);
}

MediaCodec::MediaCodec(const sp<ALooper> &looper, pid_t pid, uid_t uid)
    : mState(UNINITIALIZED),
      mReleasedByResourceManager(false),
      mLooper(looper),
      mCodec(NULL),
      mReplyID(0),
      mFlags(0),
      mStickyError(OK),
      mSoftRenderer(NULL),
      mAnalyticsItem(NULL),
      mResourceManagerClient(new ResourceManagerClient(this)),
      mResourceManagerService(new ResourceManagerServiceProxy(pid)),
      mBatteryStatNotified(false),
      mIsVideo(false),
      mVideoWidth(0),
      mVideoHeight(0),
      mRotationDegrees(0),
      mDequeueInputTimeoutGeneration(0),
      mDequeueInputReplyID(0),
      mDequeueOutputTimeoutGeneration(0),
      mDequeueOutputReplyID(0),
      mHaveInputSurface(false),
      mHavePendingInputBuffers(false) {
    if (uid == kNoUid) {
        mUid = IPCThreadState::self()->getCallingUid();
    } else {
        mUid = uid;
    }
    initAnalyticsItem();
}

MediaCodec::~MediaCodec() {
    CHECK_EQ(mState, UNINITIALIZED);
    mResourceManagerService->removeResource(getId(mResourceManagerClient));

    flushAnalyticsItem();
}

void MediaCodec::initAnalyticsItem() {
    CHECK(mAnalyticsItem == NULL);
    // set up our new record, get a sessionID, put it into the in-progress list
    mAnalyticsItem = new MediaAnalyticsItem(kCodecKeyName);
    if (mAnalyticsItem != NULL) {
        (void) mAnalyticsItem->generateSessionID();
        // don't record it yet; only at the end, when we have decided that we have
        // data worth writing (e.g. .count() > 0)
    }
}

void MediaCodec::flushAnalyticsItem() {
    if (mAnalyticsItem != NULL) {
        // don't log empty records
        if (mAnalyticsItem->count() > 0) {
            mAnalyticsItem->setFinalized(true);
            mAnalyticsItem->selfrecord();
        }
        delete mAnalyticsItem;
        mAnalyticsItem = NULL;
    }
}

// static
status_t MediaCodec::PostAndAwaitResponse(
        const sp<AMessage> &msg, sp<AMessage> *response) {
    status_t err = msg->postAndAwaitResponse(response);

    if (err != OK) {
        return err;
    }

    if (!(*response)->findInt32("err", &err)) {
        err = OK;
    }

    return err;
}

void MediaCodec::PostReplyWithError(const sp<AReplyToken> &replyID, int32_t err) {
    int32_t finalErr = err;
    if (mReleasedByResourceManager) {
        // override the err code if MediaCodec has been released by ResourceManager.
        finalErr = DEAD_OBJECT;
    }

    sp<AMessage> response = new AMessage;
    response->setInt32("err", finalErr);
    response->postReply(replyID);
}

//static
sp<CodecBase> MediaCodec::GetCodecBase(const AString &name, bool nameIsType) {
    // at this time only ACodec specifies a mime type.
    if (nameIsType || name.startsWithIgnoreCase("omx.")) {
        return AVStageFactory::get()->createACodec();
    } else if (name.startsWithIgnoreCase("android.filter.")) {
        return new MediaFilter;
    } else {
        return NULL;
    }
}

status_t MediaCodec::init(const AString &name, bool nameIsType, bool encoder) {
    mResourceManagerService->init();

    // save init parameters for reset
    mInitName = name;
    mInitNameIsType = nameIsType;
    mInitIsEncoder = encoder;

    // Current video decoders do not return from OMX_FillThisBuffer
    // quickly, violating the OpenMAX specs, until that is remedied
    // we need to invest in an extra looper to free the main event
    // queue.

    mCodec = GetCodecBase(name, nameIsType);
    if (mCodec == NULL) {
        return NAME_NOT_FOUND;
    }

    bool secureCodec = false;
    if (nameIsType && !strncasecmp(name.c_str(), "video/", 6)) {
        mIsVideo = true;
    } else {
        AString tmp = name;
        if (tmp.endsWith(".secure")) {
            secureCodec = true;
            tmp.erase(tmp.size() - 7, 7);
        }
        const sp<IMediaCodecList> mcl = MediaCodecList::getInstance();
        if (mcl == NULL) {
            mCodec = NULL;  // remove the codec.
            return NO_INIT; // if called from Java should raise IOException
        }
        ssize_t codecIdx = mcl->findCodecByName(tmp.c_str());
        if (codecIdx >= 0) {
            const sp<MediaCodecInfo> info = mcl->getCodecInfo(codecIdx);
            Vector<AString> mimes;
            info->getSupportedMimes(&mimes);
            for (size_t i = 0; i < mimes.size(); i++) {
                if (mimes[i].startsWith("video/")) {
                    mIsVideo = true;
                    break;
                }
            }
        }
    }

    if (mIsVideo) {
        // video codec needs dedicated looper
        if (mCodecLooper == NULL) {
            mCodecLooper = new ALooper;
            mCodecLooper->setName("CodecLooper");
            mCodecLooper->start(false, false, ANDROID_PRIORITY_AUDIO);
        }

        mCodecLooper->registerHandler(mCodec);
    } else {
        mLooper->registerHandler(mCodec);
    }

    mLooper->registerHandler(this);

    mCodec->setCallback(
            std::unique_ptr<CodecBase::CodecCallback>(
                    new CodecCallback(new AMessage(kWhatCodecNotify, this))));
    mBufferChannel = mCodec->getBufferChannel();
    mBufferChannel->setCallback(
            std::unique_ptr<CodecBase::BufferCallback>(
                    new BufferCallback(new AMessage(kWhatCodecNotify, this))));

    sp<AMessage> msg = new AMessage(kWhatInit, this);
    msg->setString("name", name);
    msg->setInt32("nameIsType", nameIsType);

    if (nameIsType) {
        msg->setInt32("encoder", encoder);
    }

    if (mAnalyticsItem != NULL) {
        if (nameIsType) {
            // name is the mime type
            mAnalyticsItem->setCString(kCodecMime, name.c_str());
        } else {
            mAnalyticsItem->setCString(kCodecCodec, name.c_str());
        }
        mAnalyticsItem->setCString(kCodecMode, mIsVideo ? "video" : "audio");
        if (nameIsType)
            mAnalyticsItem->setInt32(kCodecEncoder, encoder);
    }

    status_t err;
    Vector<MediaResource> resources;
    MediaResource::Type type =
            secureCodec ? MediaResource::kSecureCodec : MediaResource::kNonSecureCodec;
    MediaResource::SubType subtype =
            mIsVideo ? MediaResource::kVideoCodec : MediaResource::kAudioCodec;
    resources.push_back(MediaResource(type, subtype, 1));
    for (int i = 0; i <= kMaxRetry; ++i) {
        if (i > 0) {
            // Don't try to reclaim resource for the first time.
            if (!mResourceManagerService->reclaimResource(resources)) {
                break;
            }
        }

        sp<AMessage> response;
        err = PostAndAwaitResponse(msg, &response);
        if (!isResourceError(err)) {
            break;
        }
    }
    return err;
}

status_t MediaCodec::setCallback(const sp<AMessage> &callback) {
    sp<AMessage> msg = new AMessage(kWhatSetCallback, this);
    msg->setMessage("callback", callback);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::setOnFrameRenderedNotification(const sp<AMessage> &notify) {
    sp<AMessage> msg = new AMessage(kWhatSetNotification, this);
    msg->setMessage("on-frame-rendered", notify);
    return msg->post();
}

status_t MediaCodec::configure(
        const sp<AMessage> &format,
        const sp<Surface> &nativeWindow,
        const sp<ICrypto> &crypto,
        uint32_t flags) {
    return configure(format, nativeWindow, crypto, NULL, flags);
}

status_t MediaCodec::configure(
        const sp<AMessage> &format,
        const sp<Surface> &surface,
        const sp<ICrypto> &crypto,
        const sp<IDescrambler> &descrambler,
        uint32_t flags) {
    sp<AMessage> msg = new AMessage(kWhatConfigure, this);

    if (mAnalyticsItem != NULL) {
        int32_t profile = 0;
        if (format->findInt32("profile", &profile)) {
            mAnalyticsItem->setInt32(kCodecProfile, profile);
        }
        int32_t level = 0;
        if (format->findInt32("level", &level)) {
            mAnalyticsItem->setInt32(kCodecLevel, level);
        }
    }

    if (mIsVideo) {
        format->findInt32("width", &mVideoWidth);
        format->findInt32("height", &mVideoHeight);
        if (!format->findInt32("rotation-degrees", &mRotationDegrees)) {
            mRotationDegrees = 0;
        }

        if (mAnalyticsItem != NULL) {
            mAnalyticsItem->setInt32(kCodecWidth, mVideoWidth);
            mAnalyticsItem->setInt32(kCodecHeight, mVideoHeight);
            mAnalyticsItem->setInt32(kCodecRotation, mRotationDegrees);
            int32_t maxWidth = 0;
            if (format->findInt32("max-width", &maxWidth)) {
                mAnalyticsItem->setInt32(kCodecMaxWidth, maxWidth);
            }
            int32_t maxHeight = 0;
            if (format->findInt32("max-height", &maxHeight)) {
                mAnalyticsItem->setInt32(kCodecMaxHeight, maxHeight);
            }
        }

        // Prevent possible integer overflow in downstream code.
        if (mInitIsEncoder
                && (uint64_t)mVideoWidth * mVideoHeight > (uint64_t)INT32_MAX / 4) {
            ALOGE("buffer size is too big, width=%d, height=%d", mVideoWidth, mVideoHeight);
            return BAD_VALUE;
        }
    }

    msg->setMessage("format", format);
    msg->setInt32("flags", flags);
    msg->setObject("surface", surface);

    if (crypto != NULL || descrambler != NULL) {
        if (crypto != NULL) {
            msg->setPointer("crypto", crypto.get());
        } else {
            msg->setPointer("descrambler", descrambler.get());
        }
        if (mAnalyticsItem != NULL) {
            // XXX: save indication that it's crypto in some way...
            mAnalyticsItem->setInt32(kCodecCrypto, 1);
        }
    } else if (mFlags & kFlagIsSecure) {
        ALOGW("Crypto or descrambler should be given for secure codec");
    }

    // save msg for reset
    mConfigureMsg = msg;

    status_t err;
    Vector<MediaResource> resources;
    MediaResource::Type type = (mFlags & kFlagIsSecure) ?
            MediaResource::kSecureCodec : MediaResource::kNonSecureCodec;
    MediaResource::SubType subtype =
            mIsVideo ? MediaResource::kVideoCodec : MediaResource::kAudioCodec;
    resources.push_back(MediaResource(type, subtype, 1));
    // Don't know the buffer size at this point, but it's fine to use 1 because
    // the reclaimResource call doesn't consider the requester's buffer size for now.
    resources.push_back(MediaResource(MediaResource::kGraphicMemory, 1));
    for (int i = 0; i <= kMaxRetry; ++i) {
        if (i > 0) {
            // Don't try to reclaim resource for the first time.
            if (!mResourceManagerService->reclaimResource(resources)) {
                break;
            }
        }

        sp<AMessage> response;
        err = PostAndAwaitResponse(msg, &response);
        if (err != OK && err != INVALID_OPERATION) {
            // MediaCodec now set state to UNINITIALIZED upon any fatal error.
            // To maintain backward-compatibility, do a reset() to put codec
            // back into INITIALIZED state.
            // But don't reset if the err is INVALID_OPERATION, which means
            // the configure failure is due to wrong state.

            ALOGE("configure failed with err 0x%08x, resetting...", err);
            reset();
        }
        if (!isResourceError(err)) {
            break;
        }
    }
    return err;
}

status_t MediaCodec::releaseCrypto()
{
    ALOGV("releaseCrypto");

    sp<AMessage> msg = new AMessage(kWhatDrmReleaseCrypto, this);

    sp<AMessage> response;
    status_t status = msg->postAndAwaitResponse(&response);

    if (status == OK && response != NULL) {
        CHECK(response->findInt32("status", &status));
        ALOGV("releaseCrypto ret: %d ", status);
    }
    else {
        ALOGE("releaseCrypto err: %d", status);
    }

    return status;
}

void MediaCodec::onReleaseCrypto(const sp<AMessage>& msg)
{
    status_t status = INVALID_OPERATION;
    if (mCrypto != NULL) {
        ALOGV("onReleaseCrypto: mCrypto: %p (%d)", mCrypto.get(), mCrypto->getStrongCount());
        mBufferChannel->setCrypto(NULL);
        // TODO change to ALOGV
        ALOGD("onReleaseCrypto: [before clear]  mCrypto: %p (%d)",
                mCrypto.get(), mCrypto->getStrongCount());
        mCrypto.clear();

        status = OK;
    }
    else {
        ALOGW("onReleaseCrypto: No mCrypto. err: %d", status);
    }

    sp<AMessage> response = new AMessage;
    response->setInt32("status", status);

    sp<AReplyToken> replyID;
    CHECK(msg->senderAwaitsResponse(&replyID));
    response->postReply(replyID);
}

status_t MediaCodec::setInputSurface(
        const sp<PersistentSurface> &surface) {
    sp<AMessage> msg = new AMessage(kWhatSetInputSurface, this);
    msg->setObject("input-surface", surface.get());

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::setSurface(const sp<Surface> &surface) {
    sp<AMessage> msg = new AMessage(kWhatSetSurface, this);
    msg->setObject("surface", surface);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::createInputSurface(
        sp<IGraphicBufferProducer>* bufferProducer) {
    sp<AMessage> msg = new AMessage(kWhatCreateInputSurface, this);

    sp<AMessage> response;
    status_t err = PostAndAwaitResponse(msg, &response);
    if (err == NO_ERROR) {
        // unwrap the sp<IGraphicBufferProducer>
        sp<RefBase> obj;
        bool found = response->findObject("input-surface", &obj);
        CHECK(found);
        sp<BufferProducerWrapper> wrapper(
                static_cast<BufferProducerWrapper*>(obj.get()));
        *bufferProducer = wrapper->getBufferProducer();
    } else {
        ALOGW("createInputSurface failed, err=%d", err);
    }
    return err;
}

uint64_t MediaCodec::getGraphicBufferSize() {
    if (!mIsVideo) {
        return 0;
    }

    uint64_t size = 0;
    size_t portNum = sizeof(mPortBuffers) / sizeof((mPortBuffers)[0]);
    for (size_t i = 0; i < portNum; ++i) {
        // TODO: this is just an estimation, we should get the real buffer size from ACodec.
        size += mPortBuffers[i].size() * mVideoWidth * mVideoHeight * 3 / 2;
    }
    return size;
}

void MediaCodec::addResource(
        MediaResource::Type type, MediaResource::SubType subtype, uint64_t value) {
    Vector<MediaResource> resources;
    resources.push_back(MediaResource(type, subtype, value));
    mResourceManagerService->addResource(
            getId(mResourceManagerClient), mResourceManagerClient, resources);
}

status_t MediaCodec::start() {
    sp<AMessage> msg = new AMessage(kWhatStart, this);

    status_t err;
    Vector<MediaResource> resources;
    MediaResource::Type type = (mFlags & kFlagIsSecure) ?
            MediaResource::kSecureCodec : MediaResource::kNonSecureCodec;
    MediaResource::SubType subtype =
            mIsVideo ? MediaResource::kVideoCodec : MediaResource::kAudioCodec;
    resources.push_back(MediaResource(type, subtype, 1));
    // Don't know the buffer size at this point, but it's fine to use 1 because
    // the reclaimResource call doesn't consider the requester's buffer size for now.
    resources.push_back(MediaResource(MediaResource::kGraphicMemory, 1));
    for (int i = 0; i <= kMaxRetry; ++i) {
        if (i > 0) {
            // Don't try to reclaim resource for the first time.
            if (!mResourceManagerService->reclaimResource(resources)) {
                break;
            }
            // Recover codec from previous error before retry start.
            err = reset();
            if (err != OK) {
                ALOGE("retrying start: failed to reset codec");
                break;
            }
            sp<AMessage> response;
            err = PostAndAwaitResponse(mConfigureMsg, &response);
            if (err != OK) {
                ALOGE("retrying start: failed to configure codec");
                break;
            }
        }

        sp<AMessage> response;
        err = PostAndAwaitResponse(msg, &response);
        if (!isResourceError(err)) {
            break;
        }
    }
    return err;
}

status_t MediaCodec::stop() {
    sp<AMessage> msg = new AMessage(kWhatStop, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

bool MediaCodec::hasPendingBuffer(int portIndex) {
    return std::any_of(
            mPortBuffers[portIndex].begin(), mPortBuffers[portIndex].end(),
            [](const BufferInfo &info) { return info.mOwnedByClient; });
}

bool MediaCodec::hasPendingBuffer() {
    return hasPendingBuffer(kPortIndexInput) || hasPendingBuffer(kPortIndexOutput);
}

status_t MediaCodec::reclaim(bool force) {
    ALOGD("MediaCodec::reclaim(%p) %s", this, mInitName.c_str());
    sp<AMessage> msg = new AMessage(kWhatRelease, this);
    msg->setInt32("reclaimed", 1);
    msg->setInt32("force", force ? 1 : 0);

    sp<AMessage> response;
    status_t ret = PostAndAwaitResponse(msg, &response);
    if (ret == -ENOENT) {
        ALOGD("MediaCodec looper is gone, skip reclaim");
        ret = OK;
    }
    return ret;
}

status_t MediaCodec::release() {
    sp<AMessage> msg = new AMessage(kWhatRelease, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::reset() {
    /* When external-facing MediaCodec object is created,
       it is already initialized.  Thus, reset is essentially
       release() followed by init(), plus clearing the state */

    status_t err = release();

    // unregister handlers
    if (mCodec != NULL) {
        if (mCodecLooper != NULL) {
            mCodecLooper->unregisterHandler(mCodec->id());
        } else {
            mLooper->unregisterHandler(mCodec->id());
        }
        mCodec = NULL;
    }
    mLooper->unregisterHandler(id());

    mFlags = 0;    // clear all flags
    mStickyError = OK;

    // reset state not reset by setState(UNINITIALIZED)
    mReplyID = 0;
    mDequeueInputReplyID = 0;
    mDequeueOutputReplyID = 0;
    mDequeueInputTimeoutGeneration = 0;
    mDequeueOutputTimeoutGeneration = 0;
    mHaveInputSurface = false;

    if (err == OK) {
        err = init(mInitName, mInitNameIsType, mInitIsEncoder);
    }
    return err;
}

status_t MediaCodec::queueInputBuffer(
        size_t index,
        size_t offset,
        size_t size,
        int64_t presentationTimeUs,
        uint32_t flags,
        AString *errorDetailMsg) {
    if (errorDetailMsg != NULL) {
        errorDetailMsg->clear();
    }

    sp<AMessage> msg = new AMessage(kWhatQueueInputBuffer, this);
    msg->setSize("index", index);
    msg->setSize("offset", offset);
    msg->setSize("size", size);
    msg->setInt64("timeUs", presentationTimeUs);
    msg->setInt32("flags", flags);
    msg->setPointer("errorDetailMsg", errorDetailMsg);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::queueSecureInputBuffer(
        size_t index,
        size_t offset,
        const CryptoPlugin::SubSample *subSamples,
        size_t numSubSamples,
        const uint8_t key[16],
        const uint8_t iv[16],
        CryptoPlugin::Mode mode,
        const CryptoPlugin::Pattern &pattern,
        int64_t presentationTimeUs,
        uint32_t flags,
        AString *errorDetailMsg) {
    if (errorDetailMsg != NULL) {
        errorDetailMsg->clear();
    }

    sp<AMessage> msg = new AMessage(kWhatQueueInputBuffer, this);
    msg->setSize("index", index);
    msg->setSize("offset", offset);
    msg->setPointer("subSamples", (void *)subSamples);
    msg->setSize("numSubSamples", numSubSamples);
    msg->setPointer("key", (void *)key);
    msg->setPointer("iv", (void *)iv);
    msg->setInt32("mode", mode);
    msg->setInt32("encryptBlocks", pattern.mEncryptBlocks);
    msg->setInt32("skipBlocks", pattern.mSkipBlocks);
    msg->setInt64("timeUs", presentationTimeUs);
    msg->setInt32("flags", flags);
    msg->setPointer("errorDetailMsg", errorDetailMsg);

    sp<AMessage> response;
    status_t err = PostAndAwaitResponse(msg, &response);

    return err;
}

status_t MediaCodec::dequeueInputBuffer(size_t *index, int64_t timeoutUs) {
    sp<AMessage> msg = new AMessage(kWhatDequeueInputBuffer, this);
    msg->setInt64("timeoutUs", timeoutUs);

    sp<AMessage> response;
    status_t err;
    if ((err = PostAndAwaitResponse(msg, &response)) != OK) {
        return err;
    }

    CHECK(response->findSize("index", index));

    return OK;
}

status_t MediaCodec::dequeueOutputBuffer(
        size_t *index,
        size_t *offset,
        size_t *size,
        int64_t *presentationTimeUs,
        uint32_t *flags,
        int64_t timeoutUs) {
    sp<AMessage> msg = new AMessage(kWhatDequeueOutputBuffer, this);
    msg->setInt64("timeoutUs", timeoutUs);

    sp<AMessage> response;
    status_t err;
    if ((err = PostAndAwaitResponse(msg, &response)) != OK) {
        return err;
    }

    CHECK(response->findSize("index", index));
    CHECK(response->findSize("offset", offset));
    CHECK(response->findSize("size", size));
    CHECK(response->findInt64("timeUs", presentationTimeUs));
    CHECK(response->findInt32("flags", (int32_t *)flags));

    return OK;
}

status_t MediaCodec::renderOutputBufferAndRelease(size_t index) {
    sp<AMessage> msg = new AMessage(kWhatReleaseOutputBuffer, this);
    msg->setSize("index", index);
    msg->setInt32("render", true);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::renderOutputBufferAndRelease(size_t index, int64_t timestampNs) {
    sp<AMessage> msg = new AMessage(kWhatReleaseOutputBuffer, this);
    msg->setSize("index", index);
    msg->setInt32("render", true);
    msg->setInt64("timestampNs", timestampNs);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::releaseOutputBuffer(size_t index) {
    sp<AMessage> msg = new AMessage(kWhatReleaseOutputBuffer, this);
    msg->setSize("index", index);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::signalEndOfInputStream() {
    sp<AMessage> msg = new AMessage(kWhatSignalEndOfInputStream, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::getOutputFormat(sp<AMessage> *format) const {
    sp<AMessage> msg = new AMessage(kWhatGetOutputFormat, this);

    sp<AMessage> response;
    status_t err;
    if ((err = PostAndAwaitResponse(msg, &response)) != OK) {
        return err;
    }

    CHECK(response->findMessage("format", format));

    return OK;
}

status_t MediaCodec::getInputFormat(sp<AMessage> *format) const {
    sp<AMessage> msg = new AMessage(kWhatGetInputFormat, this);

    sp<AMessage> response;
    status_t err;
    if ((err = PostAndAwaitResponse(msg, &response)) != OK) {
        return err;
    }

    CHECK(response->findMessage("format", format));

    return OK;
}

status_t MediaCodec::getName(AString *name) const {
    sp<AMessage> msg = new AMessage(kWhatGetName, this);

    sp<AMessage> response;
    status_t err;
    if ((err = PostAndAwaitResponse(msg, &response)) != OK) {
        return err;
    }

    CHECK(response->findString("name", name));

    return OK;
}

status_t MediaCodec::getMetrics(MediaAnalyticsItem * &reply) {

    reply = NULL;

    // shouldn't happen, but be safe
    if (mAnalyticsItem == NULL) {
        return UNKNOWN_ERROR;
    }

    // XXX: go get current values for whatever in-flight data we want

    // send it back to the caller.
    reply = mAnalyticsItem->dup();

    return OK;
}

status_t MediaCodec::getInputBuffers(Vector<sp<MediaCodecBuffer> > *buffers) const {
    sp<AMessage> msg = new AMessage(kWhatGetBuffers, this);
    msg->setInt32("portIndex", kPortIndexInput);
    msg->setPointer("buffers", buffers);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::getOutputBuffers(Vector<sp<MediaCodecBuffer> > *buffers) const {
    sp<AMessage> msg = new AMessage(kWhatGetBuffers, this);
    msg->setInt32("portIndex", kPortIndexOutput);
    msg->setPointer("buffers", buffers);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::getOutputBuffer(size_t index, sp<MediaCodecBuffer> *buffer) {
    sp<AMessage> format;
    return getBufferAndFormat(kPortIndexOutput, index, buffer, &format);
}

status_t MediaCodec::getOutputFormat(size_t index, sp<AMessage> *format) {
    sp<MediaCodecBuffer> buffer;
    return getBufferAndFormat(kPortIndexOutput, index, &buffer, format);
}

status_t MediaCodec::getInputBuffer(size_t index, sp<MediaCodecBuffer> *buffer) {
    sp<AMessage> format;
    return getBufferAndFormat(kPortIndexInput, index, buffer, &format);
}

bool MediaCodec::isExecuting() const {
    return mState == STARTED || mState == FLUSHED;
}

status_t MediaCodec::getBufferAndFormat(
        size_t portIndex, size_t index,
        sp<MediaCodecBuffer> *buffer, sp<AMessage> *format) {
    // use mutex instead of a context switch
    if (mReleasedByResourceManager) {
        ALOGE("getBufferAndFormat - resource already released");
        return DEAD_OBJECT;
    }

    if (buffer == NULL) {
        ALOGE("getBufferAndFormat - null MediaCodecBuffer");
        return INVALID_OPERATION;
    }

    if (format == NULL) {
        ALOGE("getBufferAndFormat - null AMessage");
        return INVALID_OPERATION;
    }

    buffer->clear();
    format->clear();

    if (!isExecuting()) {
        ALOGE("getBufferAndFormat - not executing");
        return INVALID_OPERATION;
    }

    // we do not want mPortBuffers to change during this section
    // we also don't want mOwnedByClient to change during this
    Mutex::Autolock al(mBufferLock);

    std::vector<BufferInfo> &buffers = mPortBuffers[portIndex];
    if (index >= buffers.size()) {
        ALOGE("getBufferAndFormat - trying to get buffer with "
              "bad index (index=%zu buffer_size=%zu)", index, buffers.size());
        return INVALID_OPERATION;
    }

    const BufferInfo &info = buffers[index];
    if (!info.mOwnedByClient) {
        ALOGE("getBufferAndFormat - invalid operation "
              "(the index %zu is not owned by client)", index);
        return INVALID_OPERATION;
    }

    *buffer = info.mData;
    *format = info.mData->format();

    return OK;
}

status_t MediaCodec::flush() {
    sp<AMessage> msg = new AMessage(kWhatFlush, this);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::requestIDRFrame() {
    (new AMessage(kWhatRequestIDRFrame, this))->post();

    return OK;
}

void MediaCodec::requestActivityNotification(const sp<AMessage> &notify) {
    sp<AMessage> msg = new AMessage(kWhatRequestActivityNotification, this);
    msg->setMessage("notify", notify);
    msg->post();
}

////////////////////////////////////////////////////////////////////////////////

void MediaCodec::cancelPendingDequeueOperations() {
    if (mFlags & kFlagDequeueInputPending) {
        PostReplyWithError(mDequeueInputReplyID, INVALID_OPERATION);

        ++mDequeueInputTimeoutGeneration;
        mDequeueInputReplyID = 0;
        mFlags &= ~kFlagDequeueInputPending;
    }

    if (mFlags & kFlagDequeueOutputPending) {
        PostReplyWithError(mDequeueOutputReplyID, INVALID_OPERATION);

        ++mDequeueOutputTimeoutGeneration;
        mDequeueOutputReplyID = 0;
        mFlags &= ~kFlagDequeueOutputPending;
    }
}

bool MediaCodec::handleDequeueInputBuffer(const sp<AReplyToken> &replyID, bool newRequest) {
    if (!isExecuting() || (mFlags & kFlagIsAsync)
            || (newRequest && (mFlags & kFlagDequeueInputPending))) {
        PostReplyWithError(replyID, INVALID_OPERATION);
        return true;
    } else if (mFlags & kFlagStickyError) {
        PostReplyWithError(replyID, getStickyError());
        return true;
    }

    ssize_t index = dequeuePortBuffer(kPortIndexInput);

    if (index < 0) {
        CHECK_EQ(index, -EAGAIN);
        return false;
    }

    sp<AMessage> response = new AMessage;
    response->setSize("index", index);
    response->postReply(replyID);

    return true;
}

bool MediaCodec::handleDequeueOutputBuffer(const sp<AReplyToken> &replyID, bool newRequest) {
    if (!isExecuting() || (mFlags & kFlagIsAsync)
            || (newRequest && (mFlags & kFlagDequeueOutputPending))) {
        PostReplyWithError(replyID, INVALID_OPERATION);
    } else if (mFlags & kFlagStickyError) {
        PostReplyWithError(replyID, getStickyError());
    } else if (mFlags & kFlagOutputBuffersChanged) {
        PostReplyWithError(replyID, INFO_OUTPUT_BUFFERS_CHANGED);
        mFlags &= ~kFlagOutputBuffersChanged;
    } else if (mFlags & kFlagOutputFormatChanged) {
        PostReplyWithError(replyID, INFO_FORMAT_CHANGED);
        mFlags &= ~kFlagOutputFormatChanged;
    } else {
        sp<AMessage> response = new AMessage;
        ssize_t index = dequeuePortBuffer(kPortIndexOutput);

        if (index < 0) {
            CHECK_EQ(index, -EAGAIN);
            return false;
        }

        const sp<MediaCodecBuffer> &buffer =
            mPortBuffers[kPortIndexOutput][index].mData;

        response->setSize("index", index);
        response->setSize("offset", buffer->offset());
        response->setSize("size", buffer->size());

        int64_t timeUs;
        CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

        response->setInt64("timeUs", timeUs);

        int32_t flags;
        CHECK(buffer->meta()->findInt32("flags", &flags));

        response->setInt32("flags", flags);
        response->postReply(replyID);
    }

    return true;
}

void MediaCodec::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatCodecNotify:
        {
            int32_t what;
            CHECK(msg->findInt32("what", &what));

            switch (what) {
                case kWhatError:
                {
                    int32_t err, actionCode;
                    CHECK(msg->findInt32("err", &err));
                    CHECK(msg->findInt32("actionCode", &actionCode));

                    ALOGE("Codec reported err %#x, actionCode %d, while in state %d",
                            err, actionCode, mState);
                    if (err == DEAD_OBJECT) {
                        mFlags |= kFlagSawMediaServerDie;
                        mFlags &= ~kFlagIsComponentAllocated;
                    }

                    bool sendErrorResponse = true;

                    switch (mState) {
                        case INITIALIZING:
                        {
                            setState(UNINITIALIZED);
                            break;
                        }

                        case CONFIGURING:
                        {
                            if (actionCode == ACTION_CODE_FATAL) {
                                mAnalyticsItem->setInt32(kCodecError, err);
                                mAnalyticsItem->setInt32(kCodecErrorState, mState);
                                flushAnalyticsItem();
                                initAnalyticsItem();
                            }
                            setState(actionCode == ACTION_CODE_FATAL ?
                                    UNINITIALIZED : INITIALIZED);
                            break;
                        }

                        case STARTING:
                        {
                            if (actionCode == ACTION_CODE_FATAL) {
                                mAnalyticsItem->setInt32(kCodecError, err);
                                mAnalyticsItem->setInt32(kCodecErrorState, mState);
                                flushAnalyticsItem();
                                initAnalyticsItem();
                            }
                            setState(actionCode == ACTION_CODE_FATAL ?
                                    UNINITIALIZED : CONFIGURED);
                            break;
                        }

                        case RELEASING:
                        {
                            // Ignore the error, assuming we'll still get
                            // the shutdown complete notification. If we
                            // don't, we'll timeout and force release.
                            sendErrorResponse = false;
                        }
                        // fall-thru
                        case STOPPING:
                        {
                            if (mFlags & kFlagSawMediaServerDie) {
                                // MediaServer died, there definitely won't
                                // be a shutdown complete notification after
                                // all.

                                // note that we're directly going from
                                // STOPPING->UNINITIALIZED, instead of the
                                // usual STOPPING->INITIALIZED state.
                                setState(UNINITIALIZED);
                                if (mState == RELEASING) {
                                    mComponentName.clear();
                                }
                                (new AMessage)->postReply(mReplyID);
                                sendErrorResponse = false;
                            }
                            break;
                        }

                        case FLUSHING:
                        {
                            if (actionCode == ACTION_CODE_FATAL) {
                                mAnalyticsItem->setInt32(kCodecError, err);
                                mAnalyticsItem->setInt32(kCodecErrorState, mState);
                                flushAnalyticsItem();
                                initAnalyticsItem();

                                setState(UNINITIALIZED);
                            } else {
                                setState(
                                        (mFlags & kFlagIsAsync) ? FLUSHED : STARTED);
                            }
                            break;
                        }

                        case FLUSHED:
                        case STARTED:
                        {
                            sendErrorResponse = false;

                            setStickyError(err);
                            postActivityNotificationIfPossible();

                            cancelPendingDequeueOperations();

                            if (mFlags & kFlagIsAsync) {
                                onError(err, actionCode);
                            }
                            switch (actionCode) {
                            case ACTION_CODE_TRANSIENT:
                                break;
                            case ACTION_CODE_RECOVERABLE:
                                setState(INITIALIZED);
                                break;
                            default:
                                mAnalyticsItem->setInt32(kCodecError, err);
                                mAnalyticsItem->setInt32(kCodecErrorState, mState);
                                flushAnalyticsItem();
                                initAnalyticsItem();
                                setState(UNINITIALIZED);
                                break;
                            }
                            break;
                        }

                        default:
                        {
                            sendErrorResponse = false;

                            setStickyError(err);
                            postActivityNotificationIfPossible();

                            // actionCode in an uninitialized state is always fatal.
                            if (mState == UNINITIALIZED) {
                                actionCode = ACTION_CODE_FATAL;
                            }
                            if (mFlags & kFlagIsAsync) {
                                onError(err, actionCode);
                            }
                            switch (actionCode) {
                            case ACTION_CODE_TRANSIENT:
                                break;
                            case ACTION_CODE_RECOVERABLE:
                                setState(INITIALIZED);
                                break;
                            default:
                                setState(UNINITIALIZED);
                                break;
                            }
                            break;
                        }
                    }

                    if (sendErrorResponse) {
                        PostReplyWithError(mReplyID, err);
                    }
                    break;
                }

                case kWhatComponentAllocated:
                {
                    CHECK_EQ(mState, INITIALIZING);
                    setState(INITIALIZED);
                    mFlags |= kFlagIsComponentAllocated;

                    CHECK(msg->findString("componentName", &mComponentName));

                    if (mComponentName.c_str()) {
                        mAnalyticsItem->setCString(kCodecCodec, mComponentName.c_str());
                    }

                    if (mComponentName.startsWith("OMX.google.")) {
                        mFlags |= kFlagUsesSoftwareRenderer;
                    } else {
                        mFlags &= ~kFlagUsesSoftwareRenderer;
                    }

                    MediaResource::Type resourceType;
                    if (mComponentName.endsWith(".secure")) {
                        mFlags |= kFlagIsSecure;
                        resourceType = MediaResource::kSecureCodec;
                        mAnalyticsItem->setInt32(kCodecSecure, 1);
                    } else {
                        mFlags &= ~kFlagIsSecure;
                        resourceType = MediaResource::kNonSecureCodec;
                        mAnalyticsItem->setInt32(kCodecSecure, 0);
                    }

                    if (mIsVideo) {
                        // audio codec is currently ignored.
                        addResource(resourceType, MediaResource::kVideoCodec, 1);
                    }

                    (new AMessage)->postReply(mReplyID);
                    break;
                }

                case kWhatComponentConfigured:
                {
                    if (mState == UNINITIALIZED || mState == INITIALIZED) {
                        // In case a kWhatError message came in and replied with error,
                        // we log a warning and ignore.
                        ALOGW("configure interrupted by error, current state %d", mState);
                        break;
                    }
                    CHECK_EQ(mState, CONFIGURING);

                    // reset input surface flag
                    mHaveInputSurface = false;

                    CHECK(msg->findMessage("input-format", &mInputFormat));
                    CHECK(msg->findMessage("output-format", &mOutputFormat));
                    ALOGV("[%s] configured as input format: %s, output format: %s",
                            mComponentName.c_str(),
                            mInputFormat->debugString(4).c_str(),
                            mOutputFormat->debugString(4).c_str());
                    int32_t usingSwRenderer;
                    if (mOutputFormat->findInt32("using-sw-renderer", &usingSwRenderer)
                            && usingSwRenderer) {
                        mFlags |= kFlagUsesSoftwareRenderer;
                    }
                    setState(CONFIGURED);
                    (new AMessage)->postReply(mReplyID);

                    // augment our media metrics info, now that we know more things
                    if (mAnalyticsItem != NULL) {
                        sp<AMessage> format;
                        if (mConfigureMsg != NULL &&
                            mConfigureMsg->findMessage("format", &format)) {
                                // format includes: mime
                                AString mime;
                                if (format->findString("mime", &mime)) {
                                    mAnalyticsItem->setCString(kCodecMime, mime.c_str());
                                }
                            }
                    }
                    break;
                }

                case kWhatInputSurfaceCreated:
                {
                    // response to initiateCreateInputSurface()
                    status_t err = NO_ERROR;
                    sp<AMessage> response = new AMessage;
                    if (!msg->findInt32("err", &err)) {
                        sp<RefBase> obj;
                        msg->findObject("input-surface", &obj);
                        CHECK(msg->findMessage("input-format", &mInputFormat));
                        CHECK(msg->findMessage("output-format", &mOutputFormat));
                        ALOGV("[%s] input surface created as input format: %s, output format: %s",
                                mComponentName.c_str(),
                                mInputFormat->debugString(4).c_str(),
                                mOutputFormat->debugString(4).c_str());
                        CHECK(obj != NULL);
                        response->setObject("input-surface", obj);
                        mHaveInputSurface = true;
                    } else {
                        response->setInt32("err", err);
                    }
                    response->postReply(mReplyID);
                    break;
                }

                case kWhatInputSurfaceAccepted:
                {
                    // response to initiateSetInputSurface()
                    status_t err = NO_ERROR;
                    sp<AMessage> response = new AMessage();
                    if (!msg->findInt32("err", &err)) {
                        CHECK(msg->findMessage("input-format", &mInputFormat));
                        CHECK(msg->findMessage("output-format", &mOutputFormat));
                        mHaveInputSurface = true;
                    } else {
                        response->setInt32("err", err);
                    }
                    response->postReply(mReplyID);
                    break;
                }

                case kWhatSignaledInputEOS:
                {
                    // response to signalEndOfInputStream()
                    sp<AMessage> response = new AMessage;
                    status_t err;
                    if (msg->findInt32("err", &err)) {
                        response->setInt32("err", err);
                    }
                    response->postReply(mReplyID);
                    break;
                }

                case kWhatStartCompleted:
                {
                    CHECK_EQ(mState, STARTING);
                    if (mIsVideo) {
                        addResource(
                                MediaResource::kGraphicMemory,
                                MediaResource::kUnspecifiedSubType,
                                getGraphicBufferSize());
                    }
                    setState(STARTED);
                    (new AMessage)->postReply(mReplyID);
                    break;
                }

                case kWhatOutputBuffersChanged:
                {
                    mFlags |= kFlagOutputBuffersChanged;
                    postActivityNotificationIfPossible();
                    break;
                }

                case kWhatOutputFramesRendered:
                {
                    // ignore these in all states except running, and check that we have a
                    // notification set
                    if (mState == STARTED && mOnFrameRenderedNotification != NULL) {
                        sp<AMessage> notify = mOnFrameRenderedNotification->dup();
                        notify->setMessage("data", msg);
                        notify->post();
                    }
                    break;
                }

                case kWhatFillThisBuffer:
                {
                    /* size_t index = */updateBuffers(kPortIndexInput, msg);

                    if (mState == FLUSHING
                            || mState == STOPPING
                            || mState == RELEASING) {
                        returnBuffersToCodecOnPort(kPortIndexInput);
                        break;
                    }

                    if (!mCSD.empty()) {
                        ssize_t index = dequeuePortBuffer(kPortIndexInput);
                        CHECK_GE(index, 0);

                        // If codec specific data had been specified as
                        // part of the format in the call to configure and
                        // if there's more csd left, we submit it here
                        // clients only get access to input buffers once
                        // this data has been exhausted.

                        status_t err = queueCSDInputBuffer(index);

                        if (err != OK) {
                            ALOGE("queueCSDInputBuffer failed w/ error %d",
                                  err);

                            setStickyError(err);
                            postActivityNotificationIfPossible();

                            cancelPendingDequeueOperations();
                        }
                        break;
                    }

                    if (mFlags & kFlagIsAsync) {
                        if (!mHaveInputSurface) {
                            if (mState == FLUSHED) {
                                mHavePendingInputBuffers = true;
                            } else {
                                onInputBufferAvailable();
                            }
                        }
                    } else if (mFlags & kFlagDequeueInputPending) {
                        CHECK(handleDequeueInputBuffer(mDequeueInputReplyID));

                        ++mDequeueInputTimeoutGeneration;
                        mFlags &= ~kFlagDequeueInputPending;
                        mDequeueInputReplyID = 0;
                    } else {
                        postActivityNotificationIfPossible();
                    }
                    break;
                }

                case kWhatDrainThisBuffer:
                {
                    /* size_t index = */updateBuffers(kPortIndexOutput, msg);

                    if (mState == FLUSHING
                            || mState == STOPPING
                            || mState == RELEASING) {
                        returnBuffersToCodecOnPort(kPortIndexOutput);
                        break;
                    }

                    sp<RefBase> obj;
                    CHECK(msg->findObject("buffer", &obj));
                    sp<MediaCodecBuffer> buffer = static_cast<MediaCodecBuffer *>(obj.get());

                    if (mOutputFormat != buffer->format()) {
                        mOutputFormat = buffer->format();
                        ALOGV("[%s] output format changed to: %s",
                                mComponentName.c_str(), mOutputFormat->debugString(4).c_str());

                        if (mSoftRenderer == NULL &&
                                mSurface != NULL &&
                                (mFlags & kFlagUsesSoftwareRenderer)) {
                            AString mime;
                            CHECK(mOutputFormat->findString("mime", &mime));

                            // TODO: propagate color aspects to software renderer to allow better
                            // color conversion to RGB. For now, just mark dataspace for YUV
                            // rendering.
                            int32_t dataSpace;
                            if (mOutputFormat->findInt32("android._dataspace", &dataSpace)) {
                                ALOGD("[%s] setting dataspace on output surface to #%x",
                                        mComponentName.c_str(), dataSpace);
                                int err = native_window_set_buffers_data_space(
                                        mSurface.get(), (android_dataspace)dataSpace);
                                ALOGW_IF(err != 0, "failed to set dataspace on surface (%d)", err);
                            }

                            if (mime.startsWithIgnoreCase("video/")) {
                                mSoftRenderer = new SoftwareRenderer(mSurface, mRotationDegrees);
                            }
                        }

                        if (mFlags & kFlagIsEncoder) {
                            // Before we announce the format change we should
                            // collect codec specific data and amend the output
                            // format as necessary.
                            int32_t flags = 0;
                            (void) buffer->meta()->findInt32("flags", &flags);
                            if (flags & BUFFER_FLAG_CODECCONFIG) {
                                status_t err =
                                    amendOutputFormatWithCodecSpecificData(buffer);

                                if (err != OK) {
                                    ALOGE("Codec spit out malformed codec "
                                          "specific data!");
                                }
                            }
                        }

                        if (mFlags & kFlagIsAsync) {
                            onOutputFormatChanged();
                        } else {
                            mFlags |= kFlagOutputFormatChanged;
                            postActivityNotificationIfPossible();
                        }

                        // Notify mCrypto of video resolution changes
                        if (mCrypto != NULL) {
                            int32_t left, top, right, bottom, width, height;
                            if (mOutputFormat->findRect("crop", &left, &top, &right, &bottom)) {
                                mCrypto->notifyResolution(right - left + 1, bottom - top + 1);
                            } else if (mOutputFormat->findInt32("width", &width)
                                    && mOutputFormat->findInt32("height", &height)) {
                                mCrypto->notifyResolution(width, height);
                            }
                        }
                    }

                    if (mFlags & kFlagIsAsync) {
                        onOutputBufferAvailable();
                    } else if (mFlags & kFlagDequeueOutputPending) {
                        CHECK(handleDequeueOutputBuffer(mDequeueOutputReplyID));

                        ++mDequeueOutputTimeoutGeneration;
                        mFlags &= ~kFlagDequeueOutputPending;
                        mDequeueOutputReplyID = 0;
                    } else {
                        postActivityNotificationIfPossible();
                    }

                    break;
                }

                case kWhatEOS:
                {
                    // We already notify the client of this by using the
                    // corresponding flag in "onOutputBufferReady".
                    break;
                }

                case kWhatStopCompleted:
                {
                    if (mState != STOPPING) {
                        ALOGW("Received kWhatStopCompleted in state %d", mState);
                        break;
                    }
                    setState(INITIALIZED);
                    (new AMessage)->postReply(mReplyID);
                    break;
                }

                case kWhatReleaseCompleted:
                {
                    if (mState != RELEASING) {
                        ALOGW("Received kWhatReleaseCompleted in state %d", mState);
                        break;
                    }
                    setState(UNINITIALIZED);
                    mComponentName.clear();

                    mFlags &= ~kFlagIsComponentAllocated;

                    mResourceManagerService->removeResource(getId(mResourceManagerClient));

                    (new AMessage)->postReply(mReplyID);
                    break;
                }

                case kWhatFlushCompleted:
                {
                    if (mState != FLUSHING) {
                        ALOGW("received FlushCompleted message in state %d",
                                mState);
                        break;
                    }

                    if (mFlags & kFlagIsAsync) {
                        setState(FLUSHED);
                    } else {
                        setState(STARTED);
                        mCodec->signalResume();
                    }

                    (new AMessage)->postReply(mReplyID);
                    break;
                }

                default:
                    TRESPASS();
            }
            break;
        }

        case kWhatInit:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mState != UNINITIALIZED) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            mReplyID = replyID;
            setState(INITIALIZING);

            AString name;
            CHECK(msg->findString("name", &name));

            int32_t nameIsType;
            int32_t encoder = false;
            CHECK(msg->findInt32("nameIsType", &nameIsType));
            if (nameIsType) {
                CHECK(msg->findInt32("encoder", &encoder));
            }

            sp<AMessage> format = new AMessage;

            if (nameIsType) {
                format->setString("mime", name.c_str());
                format->setInt32("encoder", encoder);
            } else {
                format->setString("componentName", name.c_str());
            }

            mCodec->initiateAllocateComponent(format);
            break;
        }

        case kWhatSetNotification:
        {
            sp<AMessage> notify;
            if (msg->findMessage("on-frame-rendered", &notify)) {
                mOnFrameRenderedNotification = notify;
            }
            break;
        }

        case kWhatSetCallback:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mState == UNINITIALIZED
                    || mState == INITIALIZING
                    || isExecuting()) {
                // callback can't be set after codec is executing,
                // or before it's initialized (as the callback
                // will be cleared when it goes to INITIALIZED)
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            sp<AMessage> callback;
            CHECK(msg->findMessage("callback", &callback));

            mCallback = callback;

            if (mCallback != NULL) {
                ALOGI("MediaCodec will operate in async mode");
                mFlags |= kFlagIsAsync;
            } else {
                mFlags &= ~kFlagIsAsync;
            }

            sp<AMessage> response = new AMessage;
            response->postReply(replyID);
            break;
        }

        case kWhatConfigure:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mState != INITIALIZED) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            sp<RefBase> obj;
            CHECK(msg->findObject("surface", &obj));

            sp<AMessage> format;
            CHECK(msg->findMessage("format", &format));

            int32_t push;
            if (msg->findInt32("push-blank-buffers-on-shutdown", &push) && push != 0) {
                mFlags |= kFlagPushBlankBuffersOnShutdown;
            }

            if (obj != NULL) {
                format->setObject("native-window", obj);
                status_t err = handleSetSurface(static_cast<Surface *>(obj.get()));
                if (err != OK) {
                    PostReplyWithError(replyID, err);
                    break;
                }
            } else {
                handleSetSurface(NULL);
            }

            mReplyID = replyID;
            setState(CONFIGURING);

            void *crypto;
            if (!msg->findPointer("crypto", &crypto)) {
                crypto = NULL;
            }

            ALOGV("kWhatConfigure: Old mCrypto: %p (%d)",
                    mCrypto.get(), (mCrypto != NULL ? mCrypto->getStrongCount() : 0));

            mCrypto = static_cast<ICrypto *>(crypto);
            mBufferChannel->setCrypto(mCrypto);

            ALOGV("kWhatConfigure: New mCrypto: %p (%d)",
                    mCrypto.get(), (mCrypto != NULL ? mCrypto->getStrongCount() : 0));

            void *descrambler;
            if (!msg->findPointer("descrambler", &descrambler)) {
                descrambler = NULL;
            }

            mDescrambler = static_cast<IDescrambler *>(descrambler);
            mBufferChannel->setDescrambler(mDescrambler);

            uint32_t flags;
            CHECK(msg->findInt32("flags", (int32_t *)&flags));

            if (flags & CONFIGURE_FLAG_ENCODE) {
                format->setInt32("encoder", true);
                mFlags |= kFlagIsEncoder;
            }

            extractCSD(format);

#ifdef MTK_THUMBNAIL_OPTIMIZATION
            if (flags & CONFIGURE_FLAG_ENABLE_THUMBNAIL_OPTIMIZATION) {
                format->setInt32("enableThumbnailOptimzation", 1);
            }
#endif
            mCodec->initiateConfigureComponent(format);
            break;
        }

        case kWhatSetSurface:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            status_t err = OK;

            switch (mState) {
                case CONFIGURED:
                case STARTED:
                case FLUSHED:
                {
                    sp<RefBase> obj;
                    (void)msg->findObject("surface", &obj);
                    sp<Surface> surface = static_cast<Surface *>(obj.get());
                    if (mSurface == NULL) {
                        // do not support setting surface if it was not set
                        err = INVALID_OPERATION;
                    } else if (obj == NULL) {
                        // do not support unsetting surface
                        err = BAD_VALUE;
                    } else {
                        err = connectToSurface(surface);
                        if (err == ALREADY_EXISTS) {
                            // reconnecting to same surface
                            err = OK;
                        } else {
                            if (err == OK) {
                                if (mFlags & kFlagUsesSoftwareRenderer) {
                                    if (mSoftRenderer != NULL
                                            && (mFlags & kFlagPushBlankBuffersOnShutdown)) {
                                        pushBlankBuffersToNativeWindow(mSurface.get());
                                    }
                                    mSoftRenderer = new SoftwareRenderer(surface);
                                    // TODO: check if this was successful
                                } else {
                                    err = mCodec->setSurface(surface);
                                }
                            }
                            if (err == OK) {
                                (void)disconnectFromSurface();
                                mSurface = surface;
                            }
                        }
                    }
                    break;
                }

                default:
                    err = INVALID_OPERATION;
                    break;
            }

            PostReplyWithError(replyID, err);
            break;
        }

        case kWhatCreateInputSurface:
        case kWhatSetInputSurface:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            // Must be configured, but can't have been started yet.
            if (mState != CONFIGURED) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            mReplyID = replyID;
            if (msg->what() == kWhatCreateInputSurface) {
                mCodec->initiateCreateInputSurface();
            } else {
                sp<RefBase> obj;
                CHECK(msg->findObject("input-surface", &obj));

                mCodec->initiateSetInputSurface(
                        static_cast<PersistentSurface *>(obj.get()));
            }
            break;
        }
        case kWhatStart:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mState == FLUSHED) {
                setState(STARTED);
                if (mHavePendingInputBuffers) {
                    onInputBufferAvailable();
                    mHavePendingInputBuffers = false;
                }
                mCodec->signalResume();
                PostReplyWithError(replyID, OK);
                break;
            } else if (mState != CONFIGURED) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            mReplyID = replyID;
            setState(STARTING);

            mCodec->initiateStart();
            break;
        }

        case kWhatStop:
        case kWhatRelease:
        {
            State targetState =
                (msg->what() == kWhatStop) ? INITIALIZED : UNINITIALIZED;

            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            // already stopped/released
            if (mState == UNINITIALIZED && mReleasedByResourceManager) {
                sp<AMessage> response = new AMessage;
                response->setInt32("err", OK);
                response->postReply(replyID);
                break;
            }

            int32_t reclaimed = 0;
            msg->findInt32("reclaimed", &reclaimed);
            if (reclaimed) {
                mReleasedByResourceManager = true;

                int32_t force = 0;
                msg->findInt32("force", &force);
                if (!force && hasPendingBuffer()) {
                    ALOGW("Can't reclaim codec right now due to pending buffers.");

                    // return WOULD_BLOCK to ask resource manager to retry later.
                    sp<AMessage> response = new AMessage;
                    response->setInt32("err", WOULD_BLOCK);
                    response->postReply(replyID);

                    // notify the async client
                    if (mFlags & kFlagIsAsync) {
                        onError(DEAD_OBJECT, ACTION_CODE_FATAL);
                    }
                    break;
                }
            }

            bool isReleasingAllocatedComponent =
                    (mFlags & kFlagIsComponentAllocated) && targetState == UNINITIALIZED;
            if (!isReleasingAllocatedComponent // See 1
                    && mState != INITIALIZED
                    && mState != CONFIGURED && !isExecuting()) {
                // 1) Permit release to shut down the component if allocated.
                //
                // 2) We may be in "UNINITIALIZED" state already and
                // also shutdown the encoder/decoder without the
                // client being aware of this if media server died while
                // we were being stopped. The client would assume that
                // after stop() returned, it would be safe to call release()
                // and it should be in this case, no harm to allow a release()
                // if we're already uninitialized.
                sp<AMessage> response = new AMessage;
                // TODO: we shouldn't throw an exception for stop/release. Change this to wait until
                // the previous stop/release completes and then reply with OK.
                status_t err = mState == targetState ? OK : INVALID_OPERATION;
                response->setInt32("err", err);
                if (err == OK && targetState == UNINITIALIZED) {
                    mComponentName.clear();
                }
                response->postReply(replyID);
                break;
            }

            // If we're flushing, or we're stopping but received a release
            // request, post the reply for the pending call first, and consider
            // it done. The reply token will be replaced after this, and we'll
            // no longer be able to reply.
            if (mState == FLUSHING || mState == STOPPING) {
                (new AMessage)->postReply(mReplyID);
            }

            if (mFlags & kFlagSawMediaServerDie) {
                // It's dead, Jim. Don't expect initiateShutdown to yield
                // any useful results now...
                setState(UNINITIALIZED);
                if (targetState == UNINITIALIZED) {
                    mComponentName.clear();
                }
                (new AMessage)->postReply(replyID);
                break;
            }

            // If we already have an error, component may not be able to
            // complete the shutdown properly. If we're stopping, post the
            // reply now with an error to unblock the client, client can
            // release after the failure (instead of ANR).
            if (msg->what() == kWhatStop && (mFlags & kFlagStickyError)) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            mReplyID = replyID;
            setState(msg->what() == kWhatStop ? STOPPING : RELEASING);

            mCodec->initiateShutdown(
                    msg->what() == kWhatStop /* keepComponentAllocated */);

            returnBuffersToCodec(reclaimed);

            if (mSoftRenderer != NULL && (mFlags & kFlagPushBlankBuffersOnShutdown)) {
                pushBlankBuffersToNativeWindow(mSurface.get());
            }

            break;
        }

        case kWhatDequeueInputBuffer:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mFlags & kFlagIsAsync) {
                ALOGE("dequeueOutputBuffer can't be used in async mode");
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            if (mHaveInputSurface) {
                ALOGE("dequeueInputBuffer can't be used with input surface");
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            if (handleDequeueInputBuffer(replyID, true /* new request */)) {
                break;
            }

            int64_t timeoutUs;
            CHECK(msg->findInt64("timeoutUs", &timeoutUs));

            if (timeoutUs == 0ll) {
                PostReplyWithError(replyID, -EAGAIN);
                break;
            }

            mFlags |= kFlagDequeueInputPending;
            mDequeueInputReplyID = replyID;

            if (timeoutUs > 0ll) {
                sp<AMessage> timeoutMsg =
                    new AMessage(kWhatDequeueInputTimedOut, this);
                timeoutMsg->setInt32(
                        "generation", ++mDequeueInputTimeoutGeneration);
                timeoutMsg->post(timeoutUs);
            }
            break;
        }

        case kWhatDequeueInputTimedOut:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));

            if (generation != mDequeueInputTimeoutGeneration) {
                // Obsolete
                break;
            }

            CHECK(mFlags & kFlagDequeueInputPending);

            PostReplyWithError(mDequeueInputReplyID, -EAGAIN);

            mFlags &= ~kFlagDequeueInputPending;
            mDequeueInputReplyID = 0;
            break;
        }

        case kWhatQueueInputBuffer:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (!isExecuting()) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            status_t err = onQueueInputBuffer(msg);

            PostReplyWithError(replyID, err);
            break;
        }

        case kWhatDequeueOutputBuffer:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mFlags & kFlagIsAsync) {
                ALOGE("dequeueOutputBuffer can't be used in async mode");
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            if (handleDequeueOutputBuffer(replyID, true /* new request */)) {
                break;
            }

            int64_t timeoutUs;
            CHECK(msg->findInt64("timeoutUs", &timeoutUs));

            if (timeoutUs == 0ll) {
                PostReplyWithError(replyID, -EAGAIN);
                break;
            }

            mFlags |= kFlagDequeueOutputPending;
            mDequeueOutputReplyID = replyID;

            if (timeoutUs > 0ll) {
                sp<AMessage> timeoutMsg =
                    new AMessage(kWhatDequeueOutputTimedOut, this);
                timeoutMsg->setInt32(
                        "generation", ++mDequeueOutputTimeoutGeneration);
                timeoutMsg->post(timeoutUs);
            }
            break;
        }

        case kWhatDequeueOutputTimedOut:
        {
            int32_t generation;
            CHECK(msg->findInt32("generation", &generation));

            if (generation != mDequeueOutputTimeoutGeneration) {
                // Obsolete
                break;
            }

            CHECK(mFlags & kFlagDequeueOutputPending);

            PostReplyWithError(mDequeueOutputReplyID, -EAGAIN);

            mFlags &= ~kFlagDequeueOutputPending;
            mDequeueOutputReplyID = 0;
            break;
        }

        case kWhatReleaseOutputBuffer:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (!isExecuting()) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            status_t err = onReleaseOutputBuffer(msg);

            PostReplyWithError(replyID, err);
            break;
        }

        case kWhatSignalEndOfInputStream:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (!isExecuting() || !mHaveInputSurface) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            mReplyID = replyID;
            mCodec->signalEndOfInputStream();
            break;
        }

        case kWhatGetBuffers:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));
            if (!isExecuting() || (mFlags & kFlagIsAsync)) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            int32_t portIndex;
            CHECK(msg->findInt32("portIndex", &portIndex));

            Vector<sp<MediaCodecBuffer> > *dstBuffers;
            CHECK(msg->findPointer("buffers", (void **)&dstBuffers));

            dstBuffers->clear();
            // If we're using input surface (either non-persistent created by
            // createInputSurface(), or persistent set by setInputSurface()),
            // give the client an empty input buffers array.
            if (portIndex != kPortIndexInput || !mHaveInputSurface) {
                if (portIndex == kPortIndexInput) {
                    mBufferChannel->getInputBufferArray(dstBuffers);
                } else {
                    mBufferChannel->getOutputBufferArray(dstBuffers);
                }
            }

            (new AMessage)->postReply(replyID);
            break;
        }

        case kWhatFlush:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (!isExecuting()) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            mReplyID = replyID;
            // TODO: skip flushing if already FLUSHED
            setState(FLUSHING);

            mCodec->signalFlush();
            returnBuffersToCodec();
            break;
        }

        case kWhatGetInputFormat:
        case kWhatGetOutputFormat:
        {
            sp<AMessage> format =
                (msg->what() == kWhatGetOutputFormat ? mOutputFormat : mInputFormat);

            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if ((mState != CONFIGURED && mState != STARTING &&
                 mState != STARTED && mState != FLUSHING &&
                 mState != FLUSHED)
                    || format == NULL) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            } else if (mFlags & kFlagStickyError) {
                PostReplyWithError(replyID, getStickyError());
                break;
            }

            sp<AMessage> response = new AMessage;
            response->setMessage("format", format);
            response->postReply(replyID);
            break;
        }

        case kWhatRequestIDRFrame:
        {
            mCodec->signalRequestIDRFrame();
            break;
        }

        case kWhatRequestActivityNotification:
        {
            CHECK(mActivityNotify == NULL);
            CHECK(msg->findMessage("notify", &mActivityNotify));

            postActivityNotificationIfPossible();
            break;
        }

        case kWhatGetName:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            if (mComponentName.empty()) {
                PostReplyWithError(replyID, INVALID_OPERATION);
                break;
            }

            sp<AMessage> response = new AMessage;
            response->setString("name", mComponentName.c_str());
            response->postReply(replyID);
            break;
        }

        case kWhatSetParameters:
        {
            sp<AReplyToken> replyID;
            CHECK(msg->senderAwaitsResponse(&replyID));

            sp<AMessage> params;
            CHECK(msg->findMessage("params", &params));

            status_t err = onSetParameters(params);

            PostReplyWithError(replyID, err);
            break;
        }

        case kWhatDrmReleaseCrypto:
        {
            onReleaseCrypto(msg);
            break;
        }

        default:
            TRESPASS();
    }
}

void MediaCodec::extractCSD(const sp<AMessage> &format) {
    mCSD.clear();

    size_t i = 0;
    for (;;) {
        sp<ABuffer> csd;
        if (!format->findBuffer(AStringPrintf("csd-%u", i).c_str(), &csd)) {
            break;
        }
        if (csd->size() == 0) {
            ALOGW("csd-%zu size is 0", i);
        }

        mCSD.push_back(csd);
        ++i;
    }

    ALOGV("Found %zu pieces of codec specific data.", mCSD.size());
}

status_t MediaCodec::queueCSDInputBuffer(size_t bufferIndex) {
    CHECK(!mCSD.empty());

    const BufferInfo &info = mPortBuffers[kPortIndexInput][bufferIndex];

    sp<ABuffer> csd = *mCSD.begin();
    mCSD.erase(mCSD.begin());

    const sp<MediaCodecBuffer> &codecInputData = info.mData;

    if (csd->size() > codecInputData->capacity()) {
        return -EINVAL;
    }
    if (codecInputData->data() == NULL) {
        ALOGV("Input buffer %zu is not properly allocated", bufferIndex);
        return -EINVAL;
    }

    memcpy(codecInputData->data(), csd->data(), csd->size());

    AString errorDetailMsg;

    sp<AMessage> msg = new AMessage(kWhatQueueInputBuffer, this);
    msg->setSize("index", bufferIndex);
    msg->setSize("offset", 0);
    msg->setSize("size", csd->size());
    msg->setInt64("timeUs", 0ll);
    msg->setInt32("flags", BUFFER_FLAG_CODECCONFIG);
    msg->setPointer("errorDetailMsg", &errorDetailMsg);

    return onQueueInputBuffer(msg);
}

void MediaCodec::setState(State newState) {
    if (newState == INITIALIZED || newState == UNINITIALIZED) {
        delete mSoftRenderer;
        mSoftRenderer = NULL;

        if ( mCrypto != NULL ) {
            ALOGV("setState: ~mCrypto: %p (%d)",
                    mCrypto.get(), (mCrypto != NULL ? mCrypto->getStrongCount() : 0));
        }
        mCrypto.clear();
        mDescrambler.clear();
        handleSetSurface(NULL);

        mInputFormat.clear();
        mOutputFormat.clear();
        mFlags &= ~kFlagOutputFormatChanged;
        mFlags &= ~kFlagOutputBuffersChanged;
        mFlags &= ~kFlagStickyError;
        mFlags &= ~kFlagIsEncoder;
        mFlags &= ~kFlagIsAsync;
        mStickyError = OK;

        mActivityNotify.clear();
        mCallback.clear();
    }

    if (newState == UNINITIALIZED) {
        // return any straggling buffers, e.g. if we got here on an error
        returnBuffersToCodec();

        // The component is gone, mediaserver's probably back up already
        // but should definitely be back up should we try to instantiate
        // another component.. and the cycle continues.
        mFlags &= ~kFlagSawMediaServerDie;
    }

    mState = newState;

    cancelPendingDequeueOperations();

    updateBatteryStat();
}

void MediaCodec::returnBuffersToCodec(bool isReclaim) {
    returnBuffersToCodecOnPort(kPortIndexInput, isReclaim);
    returnBuffersToCodecOnPort(kPortIndexOutput, isReclaim);
}

void MediaCodec::returnBuffersToCodecOnPort(int32_t portIndex, bool isReclaim) {
    CHECK(portIndex == kPortIndexInput || portIndex == kPortIndexOutput);
    Mutex::Autolock al(mBufferLock);

    for (size_t i = 0; i < mPortBuffers[portIndex].size(); ++i) {
        BufferInfo *info = &mPortBuffers[portIndex][i];

        if (info->mData != nullptr) {
            sp<MediaCodecBuffer> buffer = info->mData;
            if (isReclaim && info->mOwnedByClient) {
                ALOGD("port %d buffer %zu still owned by client when codec is reclaimed",
                        portIndex, i);
            } else {
                info->mOwnedByClient = false;
                info->mData.clear();
            }
            mBufferChannel->discardBuffer(buffer);
        }
    }

    mAvailPortBuffers[portIndex].clear();
}

size_t MediaCodec::updateBuffers(
        int32_t portIndex, const sp<AMessage> &msg) {
    CHECK(portIndex == kPortIndexInput || portIndex == kPortIndexOutput);
    size_t index;
    CHECK(msg->findSize("index", &index));
    sp<RefBase> obj;
    CHECK(msg->findObject("buffer", &obj));
    sp<MediaCodecBuffer> buffer = static_cast<MediaCodecBuffer *>(obj.get());

    {
        Mutex::Autolock al(mBufferLock);
        if (mPortBuffers[portIndex].size() <= index) {
            mPortBuffers[portIndex].resize(align(index + 1, kNumBuffersAlign));
        }
        mPortBuffers[portIndex][index].mData = buffer;
    }
    mAvailPortBuffers[portIndex].push_back(index);

    return index;
}

status_t MediaCodec::onQueueInputBuffer(const sp<AMessage> &msg) {
    size_t index;
    size_t offset;
    size_t size;
    int64_t timeUs;
    uint32_t flags;
    CHECK(msg->findSize("index", &index));
    CHECK(msg->findSize("offset", &offset));
    CHECK(msg->findInt64("timeUs", &timeUs));
    CHECK(msg->findInt32("flags", (int32_t *)&flags));

    const CryptoPlugin::SubSample *subSamples;
    size_t numSubSamples;
    const uint8_t *key;
    const uint8_t *iv;
    CryptoPlugin::Mode mode = CryptoPlugin::kMode_Unencrypted;

    // We allow the simpler queueInputBuffer API to be used even in
    // secure mode, by fabricating a single unencrypted subSample.
    CryptoPlugin::SubSample ss;
    CryptoPlugin::Pattern pattern;

    if (msg->findSize("size", &size)) {
        if (hasCryptoOrDescrambler()) {
            ss.mNumBytesOfClearData = size;
            ss.mNumBytesOfEncryptedData = 0;

            subSamples = &ss;
            numSubSamples = 1;
            key = NULL;
            iv = NULL;
            pattern.mEncryptBlocks = 0;
            pattern.mSkipBlocks = 0;
        }
    } else {
        if (!hasCryptoOrDescrambler()) {
            ALOGE("[%s] queuing secure buffer without mCrypto or mDescrambler!",
                    mComponentName.c_str());
            return -EINVAL;
        }

        CHECK(msg->findPointer("subSamples", (void **)&subSamples));
        CHECK(msg->findSize("numSubSamples", &numSubSamples));
        CHECK(msg->findPointer("key", (void **)&key));
        CHECK(msg->findPointer("iv", (void **)&iv));
        CHECK(msg->findInt32("encryptBlocks", (int32_t *)&pattern.mEncryptBlocks));
        CHECK(msg->findInt32("skipBlocks", (int32_t *)&pattern.mSkipBlocks));

        int32_t tmp;
        CHECK(msg->findInt32("mode", &tmp));

        mode = (CryptoPlugin::Mode)tmp;

        size = 0;
        for (size_t i = 0; i < numSubSamples; ++i) {
            size += subSamples[i].mNumBytesOfClearData;
            size += subSamples[i].mNumBytesOfEncryptedData;
        }
    }

    if (index >= mPortBuffers[kPortIndexInput].size()) {
        return -ERANGE;
    }

    BufferInfo *info = &mPortBuffers[kPortIndexInput][index];

    if (info->mData == nullptr || !info->mOwnedByClient) {
        return -EACCES;
    }

    if (offset + size > info->mData->capacity()) {
        return -EINVAL;
    }

    info->mData->setRange(offset, size);
    info->mData->meta()->setInt64("timeUs", timeUs);
    if (flags & BUFFER_FLAG_EOS) {
        info->mData->meta()->setInt32("eos", true);
    }

    if (flags & BUFFER_FLAG_CODECCONFIG) {
        info->mData->meta()->setInt32("csd", true);
    }

    sp<MediaCodecBuffer> buffer = info->mData;
    status_t err = OK;
    if (hasCryptoOrDescrambler()) {
        AString *errorDetailMsg;
        CHECK(msg->findPointer("errorDetailMsg", (void **)&errorDetailMsg));

        err = mBufferChannel->queueSecureInputBuffer(
                buffer,
                (mFlags & kFlagIsSecure),
                key,
                iv,
                mode,
                pattern,
                subSamples,
                numSubSamples,
                errorDetailMsg);
    } else {
        err = mBufferChannel->queueInputBuffer(buffer);
    }

    if (err == OK) {
        // synchronization boundary for getBufferAndFormat
        Mutex::Autolock al(mBufferLock);
        info->mOwnedByClient = false;
        info->mData.clear();
        if (mAnalyticsItem != NULL) {
            mAnalyticsItem->addInt64(kCodecBytesIn, size);
        }
    }

    return err;
}

//static
size_t MediaCodec::CreateFramesRenderedMessage(
        const std::list<FrameRenderTracker::Info> &done, sp<AMessage> &msg) {
    size_t index = 0;

    for (std::list<FrameRenderTracker::Info>::const_iterator it = done.cbegin();
            it != done.cend(); ++it) {
        if (it->getRenderTimeNs() < 0) {
            continue; // dropped frame from tracking
        }
        msg->setInt64(AStringPrintf("%zu-media-time-us", index).c_str(), it->getMediaTimeUs());
        msg->setInt64(AStringPrintf("%zu-system-nano", index).c_str(), it->getRenderTimeNs());
        ++index;
    }
    return index;
}

status_t MediaCodec::onReleaseOutputBuffer(const sp<AMessage> &msg) {
    size_t index;
    CHECK(msg->findSize("index", &index));

    int32_t render;
    if (!msg->findInt32("render", &render)) {
        render = 0;
    }

    if (!isExecuting()) {
        return -EINVAL;
    }

    if (index >= mPortBuffers[kPortIndexOutput].size()) {
        return -ERANGE;
    }

    BufferInfo *info = &mPortBuffers[kPortIndexOutput][index];

    if (info->mData == nullptr || !info->mOwnedByClient) {
        return -EACCES;
    }

    // synchronization boundary for getBufferAndFormat
    sp<MediaCodecBuffer> buffer;
    {
        Mutex::Autolock al(mBufferLock);
        info->mOwnedByClient = false;
        buffer = info->mData;
        info->mData.clear();
    }

    if (render && buffer->size() != 0) {
        int64_t mediaTimeUs = -1;
        buffer->meta()->findInt64("timeUs", &mediaTimeUs);

        int64_t renderTimeNs = 0;
        if (!msg->findInt64("timestampNs", &renderTimeNs)) {
            // use media timestamp if client did not request a specific render timestamp
            ALOGV("using buffer PTS of %lld", (long long)mediaTimeUs);
            renderTimeNs = mediaTimeUs * 1000;
        }

        if (mSoftRenderer != NULL) {
            std::list<FrameRenderTracker::Info> doneFrames = mSoftRenderer->render(
                    buffer->data(), buffer->size(),
                    mediaTimeUs, renderTimeNs, NULL, buffer->format());

            // if we are running, notify rendered frames
            if (!doneFrames.empty() && mState == STARTED && mOnFrameRenderedNotification != NULL) {
                sp<AMessage> notify = mOnFrameRenderedNotification->dup();
                sp<AMessage> data = new AMessage;
                if (CreateFramesRenderedMessage(doneFrames, data)) {
                    notify->setMessage("data", data);
                    notify->post();
                }
            }
        }
        mBufferChannel->renderOutputBuffer(buffer, renderTimeNs);
    } else {
        mBufferChannel->discardBuffer(buffer);
    }

    return OK;
}

ssize_t MediaCodec::dequeuePortBuffer(int32_t portIndex) {
    CHECK(portIndex == kPortIndexInput || portIndex == kPortIndexOutput);

    List<size_t> *availBuffers = &mAvailPortBuffers[portIndex];

    if (availBuffers->empty()) {
        return -EAGAIN;
    }

    size_t index = *availBuffers->begin();
    availBuffers->erase(availBuffers->begin());

    BufferInfo *info = &mPortBuffers[portIndex][index];
    CHECK(!info->mOwnedByClient);
    {
        Mutex::Autolock al(mBufferLock);
        info->mOwnedByClient = true;

        // set image-data
        if (info->mData->format() != NULL) {
            sp<ABuffer> imageData;
            if (info->mData->format()->findBuffer("image-data", &imageData)) {
                info->mData->meta()->setBuffer("image-data", imageData);
            }
            int32_t left, top, right, bottom;
            if (info->mData->format()->findRect("crop", &left, &top, &right, &bottom)) {
                info->mData->meta()->setRect("crop-rect", left, top, right, bottom);
            }
        }
    }

    return index;
}

status_t MediaCodec::connectToSurface(const sp<Surface> &surface) {
    status_t err = OK;
    if (surface != NULL) {
        uint64_t oldId, newId;
        if (mSurface != NULL
                && surface->getUniqueId(&newId) == NO_ERROR
                && mSurface->getUniqueId(&oldId) == NO_ERROR
                && newId == oldId) {
            ALOGI("[%s] connecting to the same surface. Nothing to do.", mComponentName.c_str());
            return ALREADY_EXISTS;
        }

        err = nativeWindowConnect(surface.get(), "connectToSurface");
        if (err == OK) {
            // Require a fresh set of buffers after each connect by using a unique generation
            // number. Rely on the fact that max supported process id by Linux is 2^22.
            // PID is never 0 so we don't have to worry that we use the default generation of 0.
            // TODO: come up with a unique scheme if other producers also set the generation number.
            static uint32_t mSurfaceGeneration = 0;
            uint32_t generation = (getpid() << 10) | (++mSurfaceGeneration & ((1 << 10) - 1));
            surface->setGenerationNumber(generation);
            ALOGI("[%s] setting surface generation to %u", mComponentName.c_str(), generation);

            // HACK: clear any free buffers. Remove when connect will automatically do this.
            // This is needed as the consumer may be holding onto stale frames that it can reattach
            // to this surface after disconnect/connect, and those free frames would inherit the new
            // generation number. Disconnecting after setting a unique generation prevents this.
            nativeWindowDisconnect(surface.get(), "connectToSurface(reconnect)");
            err = nativeWindowConnect(surface.get(), "connectToSurface(reconnect)");
        }

        if (err != OK) {
            ALOGE("nativeWindowConnect returned an error: %s (%d)", strerror(-err), err);
        }
    }
    // do not return ALREADY_EXISTS unless surfaces are the same
    return err == ALREADY_EXISTS ? BAD_VALUE : err;
}

status_t MediaCodec::disconnectFromSurface() {
    status_t err = OK;
    if (mSurface != NULL) {
        // Resetting generation is not technically needed, but there is no need to keep it either
        mSurface->setGenerationNumber(0);
        err = nativeWindowDisconnect(mSurface.get(), "disconnectFromSurface");
        if (err != OK) {
            ALOGW("nativeWindowDisconnect returned an error: %s (%d)", strerror(-err), err);
        }
        // assume disconnected even on error
        mSurface.clear();
    }
    return err;
}

status_t MediaCodec::handleSetSurface(const sp<Surface> &surface) {
    status_t err = OK;
    if (mSurface != NULL) {
        (void)disconnectFromSurface();
    }
    if (surface != NULL) {
        err = connectToSurface(surface);
        if (err == OK) {
            mSurface = surface;
        }
    }
    return err;
}

void MediaCodec::onInputBufferAvailable() {
    int32_t index;
    while ((index = dequeuePortBuffer(kPortIndexInput)) >= 0) {
        sp<AMessage> msg = mCallback->dup();
        msg->setInt32("callbackID", CB_INPUT_AVAILABLE);
        msg->setInt32("index", index);
        msg->post();
    }
}

void MediaCodec::onOutputBufferAvailable() {
    int32_t index;
    while ((index = dequeuePortBuffer(kPortIndexOutput)) >= 0) {
        const sp<MediaCodecBuffer> &buffer =
            mPortBuffers[kPortIndexOutput][index].mData;
        sp<AMessage> msg = mCallback->dup();
        msg->setInt32("callbackID", CB_OUTPUT_AVAILABLE);
        msg->setInt32("index", index);
        msg->setSize("offset", buffer->offset());
        msg->setSize("size", buffer->size());

        int64_t timeUs;
        CHECK(buffer->meta()->findInt64("timeUs", &timeUs));

        msg->setInt64("timeUs", timeUs);

        int32_t flags;
        CHECK(buffer->meta()->findInt32("flags", &flags));

        msg->setInt32("flags", flags);

        msg->post();
    }
}

void MediaCodec::onError(status_t err, int32_t actionCode, const char *detail) {
    if (mCallback != NULL) {
        sp<AMessage> msg = mCallback->dup();
        msg->setInt32("callbackID", CB_ERROR);
        msg->setInt32("err", err);
        msg->setInt32("actionCode", actionCode);

        if (detail != NULL) {
            msg->setString("detail", detail);
        }

        msg->post();
    }
}

void MediaCodec::onOutputFormatChanged() {
    if (mCallback != NULL) {
        sp<AMessage> msg = mCallback->dup();
        msg->setInt32("callbackID", CB_OUTPUT_FORMAT_CHANGED);
        msg->setMessage("format", mOutputFormat);
        msg->post();
    }
}

void MediaCodec::postActivityNotificationIfPossible() {
    if (mActivityNotify == NULL) {
        return;
    }

    bool isErrorOrOutputChanged =
            (mFlags & (kFlagStickyError
                    | kFlagOutputBuffersChanged
                    | kFlagOutputFormatChanged));

    if (isErrorOrOutputChanged
            || !mAvailPortBuffers[kPortIndexInput].empty()
            || !mAvailPortBuffers[kPortIndexOutput].empty()) {
        mActivityNotify->setInt32("input-buffers",
                mAvailPortBuffers[kPortIndexInput].size());

        if (isErrorOrOutputChanged) {
            // we want consumer to dequeue as many times as it can
            mActivityNotify->setInt32("output-buffers", INT32_MAX);
        } else {
            mActivityNotify->setInt32("output-buffers",
                    mAvailPortBuffers[kPortIndexOutput].size());
        }
        mActivityNotify->post();
        mActivityNotify.clear();
    }
}

status_t MediaCodec::setParameters(const sp<AMessage> &params) {
    sp<AMessage> msg = new AMessage(kWhatSetParameters, this);
    msg->setMessage("params", params);

    sp<AMessage> response;
    return PostAndAwaitResponse(msg, &response);
}

status_t MediaCodec::onSetParameters(const sp<AMessage> &params) {
    mCodec->signalSetParameters(params);

    return OK;
}

status_t MediaCodec::amendOutputFormatWithCodecSpecificData(
        const sp<MediaCodecBuffer> &buffer) {
    AString mime;
    CHECK(mOutputFormat->findString("mime", &mime));

    if (!strcasecmp(mime.c_str(), MEDIA_MIMETYPE_VIDEO_AVC)) {
        // Codec specific data should be SPS and PPS in a single buffer,
        // each prefixed by a startcode (0x00 0x00 0x00 0x01).
        // We separate the two and put them into the output format
        // under the keys "csd-0" and "csd-1".

        unsigned csdIndex = 0;

        const uint8_t *data = buffer->data();
        size_t size = buffer->size();

        const uint8_t *nalStart;
        size_t nalSize;
        while (getNextNALUnit(&data, &size, &nalStart, &nalSize, true) == OK) {
            sp<ABuffer> csd = new ABuffer(nalSize + 4);
            memcpy(csd->data(), "\x00\x00\x00\x01", 4);
            memcpy(csd->data() + 4, nalStart, nalSize);

            mOutputFormat->setBuffer(
                    AStringPrintf("csd-%u", csdIndex).c_str(), csd);

            ++csdIndex;
        }

        if (csdIndex != 2) {
            return ERROR_MALFORMED;
        }
    } else {
        // For everything else we just stash the codec specific data into
        // the output format as a single piece of csd under "csd-0".
        sp<ABuffer> csd = new ABuffer(buffer->size());
        memcpy(csd->data(), buffer->data(), buffer->size());
        csd->setRange(0, buffer->size());
        mOutputFormat->setBuffer("csd-0", csd);
    }

    return OK;
}

void MediaCodec::updateBatteryStat() {
    if (!mIsVideo) {
        return;
    }

    if (mState == CONFIGURED && !mBatteryStatNotified) {
        BatteryNotifier::getInstance().noteStartVideo(mUid);
        mBatteryStatNotified = true;
    } else if (mState == UNINITIALIZED && mBatteryStatNotified) {
        BatteryNotifier::getInstance().noteStopVideo(mUid);
        mBatteryStatNotified = false;
    }
}

}  // namespace android
