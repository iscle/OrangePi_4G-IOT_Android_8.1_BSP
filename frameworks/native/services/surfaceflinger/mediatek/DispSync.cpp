#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#define __STDC_FORMAT_MACROS 1

#include "DispSync.h"

#include <dlfcn.h>
#include <inttypes.h>

#include <log/log.h>
#include <utils/String8.h>

#include "SurfaceFlinger.h"

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
#include "vsync_enhance/DispSyncEnhancementApi.h"
#endif

namespace android {

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
void DispSync::initDispVsyncEnhancement() {
    typedef DispSyncEnhancementApi* (*createDispSyncPrototype)();
    mEnhancementHandle = dlopen("libvsync_enhance.so", RTLD_LAZY);
    if (mEnhancementHandle) {
        createDispSyncPrototype creatPtr = reinterpret_cast<createDispSyncPrototype>(dlsym(mEnhancementHandle, "createDispSyncEnhancement"));
        if (creatPtr) {
            mEnhancement = creatPtr();

            struct DispSyncEnhancementFunctionList list;
            getDispSyncEnhancementFunctionList(&list);
            mEnhancement->registerFunction(&list);
        } else {
            ALOGW("Failed to get function: createDispSyncEnhancement");
        }
    } else {
        ALOGW("Failed to load libsurfaceflinger_dispsync.so");
    }
}

void DispSync::deinitDispVsyncEnhancement() {
    if (mEnhancement) {
        delete mEnhancement;
    }
    if (mEnhancementHandle) {
        dlclose(mEnhancementHandle);
    }
}

bool DispSync::obeyResync() {
    Mutex::Autolock lock(mMutex);
    if (mEnhancement) {
        return mEnhancement->obeyResync();
    }
    return false;
}

void DispSync::setSurfaceFlinger(const sp<SurfaceFlinger> &sf) {
    Mutex::Autolock lock(mMutex);
    mFlinger = sf;
}

void DispSync::dumpEnhanceInfo(String8& result) const {
    if (mEnhancement) {
        mEnhancement->dump(result);
    }
}

nsecs_t DispSync::getAppPhase() const{
    Mutex::Autolock lock(mMutex);
    if (mEnhancement) {
        return mEnhancement->getAppPhase();
    }
    return SurfaceFlinger::vsyncPhaseOffsetNs;
}

nsecs_t DispSync::getSfPhase() const{
    Mutex::Autolock lock(mMutex);
    if (mEnhancement) {
        return mEnhancement->getSfPhase();
    }
    return SurfaceFlinger::sfVsyncPhaseOffsetNs;
}

bool DispSync::addPresentFenceEnhancementLocked(bool* res) {
    if (mEnhancement) {
        return mEnhancement->addPresentFence(res);
    }
    return false;
}

bool DispSync::addResyncSampleEnhancementLocked(bool* res, nsecs_t timestamp) {
    if (mEnhancement) {
        return mEnhancement->addResyncSample(res, timestamp, &mPeriod, &mPhase, &mReferenceTime);
    }
    return false;
}

bool DispSync::addEventListenerEnhancement(status_t* res, const char* name, nsecs_t phase, const sp<Callback>& callback) {
    if (mEnhancement) {
        return mEnhancement->addEventListener(res, &mMutex, name, phase, callback);
    }
    return false;
}

status_t DispSync::setVSyncMode(int32_t mode, int32_t fps) {
    Mutex::Autolock lock(mMutex);
    status_t res = NO_ERROR;
    if (mEnhancement) {
        res = mEnhancement->setVSyncMode(mode, fps, &mPeriod, &mPhase, &mReferenceTime);
    }
    return res;
}

void DispSync::enableHardwareVsync() {
    if (mFlinger != NULL) {
        mFlinger->enableHardwareVsync();
    }
}

bool DispSync::removeEventListenerEnhancement(status_t* res, const sp<Callback>& callback) {
    if (mEnhancement) {
        return mEnhancement->removeEventListener(res, &mMutex, callback);
    }
    return false;
}

void DispSync::onSwVsyncChange(int32_t mode, int32_t fps) {
    setVSyncMode(mode, fps);
}
#endif

}
