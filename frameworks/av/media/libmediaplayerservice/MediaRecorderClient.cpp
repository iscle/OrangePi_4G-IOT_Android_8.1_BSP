/*
 ** Copyright 2008, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaRecorderService"
#include <utils/Log.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <string.h>
#include <cutils/atomic.h>
#include <cutils/properties.h> // for property_get
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryBase.h>

#include <utils/String16.h>

#include <system/audio.h>

#include "MediaRecorderClient.h"
#include "MediaPlayerService.h"

#include "StagefrightRecorder.h"
#include <gui/IGraphicBufferProducer.h>

namespace android {

const char* cameraPermission = "android.permission.CAMERA";
const char* recordAudioPermission = "android.permission.RECORD_AUDIO";

static bool checkPermission(const char* permissionString) {
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16(permissionString));
    if (!ok) ALOGE("Request requires %s", permissionString);
    return ok;
}

status_t MediaRecorderClient::setInputSurface(const sp<PersistentSurface>& surface)
{
    ALOGV("setInputSurface");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setInputSurface(surface);
}

sp<IGraphicBufferProducer> MediaRecorderClient::querySurfaceMediaSource()
{
    ALOGV("Query SurfaceMediaSource");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NULL;
    }
    return mRecorder->querySurfaceMediaSource();
}



status_t MediaRecorderClient::setCamera(const sp<hardware::ICamera>& camera,
                                        const sp<ICameraRecordingProxy>& proxy)
{
    ALOGV("setCamera");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setCamera(camera, proxy);
}

status_t MediaRecorderClient::setPreviewSurface(const sp<IGraphicBufferProducer>& surface)
{
    ALOGV("setPreviewSurface");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setPreviewSurface(surface);
}

status_t MediaRecorderClient::setVideoSource(int vs)
{
    ALOGV("setVideoSource(%d)", vs);
    // Check camera permission for sources other than SURFACE
    if (vs != VIDEO_SOURCE_SURFACE && !checkPermission(cameraPermission)) {
        return PERMISSION_DENIED;
    }
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL)     {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setVideoSource((video_source)vs);
}

status_t MediaRecorderClient::setAudioSource(int as)
{
    ALOGV("setAudioSource(%d)", as);
    if (!checkPermission(recordAudioPermission)) {
        return PERMISSION_DENIED;
    }
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL)  {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setAudioSource((audio_source_t)as);
}

status_t MediaRecorderClient::setOutputFormat(int of)
{
    ALOGV("setOutputFormat(%d)", of);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setOutputFormat((output_format)of);
}

status_t MediaRecorderClient::setVideoEncoder(int ve)
{
    ALOGV("setVideoEncoder(%d)", ve);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setVideoEncoder((video_encoder)ve);
}

status_t MediaRecorderClient::setAudioEncoder(int ae)
{
    ALOGV("setAudioEncoder(%d)", ae);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setAudioEncoder((audio_encoder)ae);
}

status_t MediaRecorderClient::setOutputFile(int fd)
{
    ALOGV("setOutputFile(%d)", fd);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setOutputFile(fd);
}

status_t MediaRecorderClient::setNextOutputFile(int fd)
{
    ALOGV("setNextOutputFile(%d)", fd);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setNextOutputFile(fd);
}

status_t MediaRecorderClient::setVideoSize(int width, int height)
{
    ALOGV("setVideoSize(%dx%d)", width, height);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setVideoSize(width, height);
}

status_t MediaRecorderClient::setVideoFrameRate(int frames_per_second)
{
    ALOGV("setVideoFrameRate(%d)", frames_per_second);
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setVideoFrameRate(frames_per_second);
}

status_t MediaRecorderClient::setParameters(const String8& params) {
    ALOGV("setParameters(%s)", params.string());
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setParameters(params);
}

status_t MediaRecorderClient::prepare()
{
    ALOGV("prepare");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->prepare();
}


status_t MediaRecorderClient::getMaxAmplitude(int* max)
{
    ALOGV("getMaxAmplitude");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->getMaxAmplitude(max);
}

status_t MediaRecorderClient::getMetrics(Parcel* reply)
{
    ALOGV("MediaRecorderClient::getMetrics");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->getMetrics(reply);
}

status_t MediaRecorderClient::start()
{
    ALOGV("start");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->start();

}

status_t MediaRecorderClient::stop()
{
    ALOGV("stop");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->stop();
}

status_t MediaRecorderClient::pause()
{
    ALOGV("pause");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->pause();

}

status_t MediaRecorderClient::resume()
{
    ALOGV("resume");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->resume();
}

status_t MediaRecorderClient::init()
{
    ALOGV("init");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->init();
}

status_t MediaRecorderClient::close()
{
    ALOGV("close");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->close();
}


status_t MediaRecorderClient::reset()
{
    ALOGV("reset");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->reset();
}

status_t MediaRecorderClient::release()
{
    ALOGV("release");
    Mutex::Autolock lock(mLock);
    if (mRecorder != NULL) {
        delete mRecorder;
        mRecorder = NULL;
        wp<MediaRecorderClient> client(this);
        mMediaPlayerService->removeMediaRecorderClient(client);
    }
    clearDeathNotifiers_l();
    return NO_ERROR;
}

MediaRecorderClient::MediaRecorderClient(const sp<MediaPlayerService>& service, pid_t pid,
        const String16& opPackageName)
{
    ALOGV("Client constructor");
    mPid = pid;
    mRecorder = new StagefrightRecorder(opPackageName);
    mMediaPlayerService = service;
}

MediaRecorderClient::~MediaRecorderClient()
{
    ALOGV("Client destructor");
    release();
}

MediaRecorderClient::ServiceDeathNotifier::ServiceDeathNotifier(
        const sp<IBinder>& service,
        const sp<IMediaRecorderClient>& listener,
        int which) {
    mService = service;
    mOmx = nullptr;
    mListener = listener;
    mWhich = which;
}

MediaRecorderClient::ServiceDeathNotifier::ServiceDeathNotifier(
        const sp<IOmx>& omx,
        const sp<IMediaRecorderClient>& listener,
        int which) {
    mService = nullptr;
    mOmx = omx;
    mListener = listener;
    mWhich = which;
}

MediaRecorderClient::ServiceDeathNotifier::~ServiceDeathNotifier() {
}

void MediaRecorderClient::ServiceDeathNotifier::binderDied(const wp<IBinder>& /*who*/) {
    sp<IMediaRecorderClient> listener = mListener.promote();
    if (listener != NULL) {
        listener->notify(MEDIA_ERROR, MEDIA_ERROR_SERVER_DIED, mWhich);
    } else {
        ALOGW("listener for process %d death is gone", mWhich);
    }
}

void MediaRecorderClient::ServiceDeathNotifier::serviceDied(
        uint64_t /* cookie */,
        const wp<::android::hidl::base::V1_0::IBase>& /* who */) {
    sp<IMediaRecorderClient> listener = mListener.promote();
    if (listener != NULL) {
        listener->notify(MEDIA_ERROR, MEDIA_ERROR_SERVER_DIED, mWhich);
    } else {
        ALOGW("listener for process %d death is gone", mWhich);
    }
}

void MediaRecorderClient::ServiceDeathNotifier::unlinkToDeath() {
    if (mService != nullptr) {
        mService->unlinkToDeath(this);
        mService = nullptr;
    } else if (mOmx != nullptr) {
        mOmx->unlinkToDeath(this);
        mOmx = nullptr;
    }
}

void MediaRecorderClient::clearDeathNotifiers_l() {
    if (mCameraDeathListener != nullptr) {
        mCameraDeathListener->unlinkToDeath();
        mCameraDeathListener = nullptr;
    }
    if (mCodecDeathListener != nullptr) {
        mCodecDeathListener->unlinkToDeath();
        mCodecDeathListener = nullptr;
    }
}

status_t MediaRecorderClient::setListener(const sp<IMediaRecorderClient>& listener)
{
    ALOGV("setListener");
    Mutex::Autolock lock(mLock);
    clearDeathNotifiers_l();
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    mRecorder->setListener(listener);

    sp<IServiceManager> sm = defaultServiceManager();

    // WORKAROUND: We don't know if camera exists here and getService might block for 5 seconds.
    // Use checkService for camera if we don't know it exists.
    static std::atomic<bool> sCameraChecked(false);  // once true never becomes false.
    static std::atomic<bool> sCameraVerified(false); // once true never becomes false.
    sp<IBinder> binder = (sCameraVerified || !sCameraChecked)
        ? sm->getService(String16("media.camera")) : sm->checkService(String16("media.camera"));
    // If the device does not have a camera, do not create a death listener for it.
    if (binder != NULL) {
        sCameraVerified = true;
        mCameraDeathListener = new ServiceDeathNotifier(binder, listener,
                MediaPlayerService::CAMERA_PROCESS_DEATH);
        binder->linkToDeath(mCameraDeathListener);
    }
    sCameraChecked = true;

    if (property_get_bool("persist.media.treble_omx", true)) {
        // Treble IOmx
        sp<IOmx> omx = IOmx::getService();
        if (omx == nullptr) {
            ALOGE("Treble IOmx not available");
            return NO_INIT;
        }
        mCodecDeathListener = new ServiceDeathNotifier(omx, listener,
                MediaPlayerService::MEDIACODEC_PROCESS_DEATH);
        omx->linkToDeath(mCodecDeathListener, 0);
    } else {
        // Legacy IOMX
        binder = sm->getService(String16("media.codec"));
        if (binder == NULL) {
           ALOGE("Unable to connect to media codec service");
           return NO_INIT;
        }
        mCodecDeathListener = new ServiceDeathNotifier(binder, listener,
                MediaPlayerService::MEDIACODEC_PROCESS_DEATH);
        binder->linkToDeath(mCodecDeathListener);
    }

    return OK;
}

status_t MediaRecorderClient::setClientName(const String16& clientName) {
    ALOGV("setClientName(%s)", String8(clientName).string());
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        ALOGE("recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setClientName(clientName);
}

status_t MediaRecorderClient::dump(int fd, const Vector<String16>& args) {
    if (mRecorder != NULL) {
        return mRecorder->dump(fd, args);
    }
    return OK;
}

}; // namespace android
