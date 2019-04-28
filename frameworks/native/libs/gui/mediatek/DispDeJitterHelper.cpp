
#include <gui/mediatek/DispDeJitterHelper.h>

#include <dlfcn.h>
#include <cutils/log.h>

namespace android {

ANDROID_SINGLETON_STATIC_INSTANCE(DispDeJitterHelper);

DispDeJitterHelper::DispDeJitterHelper()
: mSoHandle(nullptr)
, mFnCreateDispDeJitter(nullptr)
, mFnDestroyDispDeJitter(nullptr)
, mFnShouldDelayPresent(nullptr)
, mFnMarkTimestamp(nullptr) {
    typedef DispDeJitter* (*CreateDispDeJitter)();
    typedef void (*DestroyDispDeJitter)(DispDeJitter*);
    typedef bool (*ShouldDelayPresent)(DispDeJitter*, const sp<GraphicBuffer>&, const nsecs_t&);
    typedef void (*MarkTimestamp)(const sp<GraphicBuffer>&);

    // dlopen must set RTLD_LAZY flag because of performance issue
    mSoHandle = dlopen("libdisp_dejitter.so", RTLD_LAZY);
    if (mSoHandle) {
        mFnCreateDispDeJitter = reinterpret_cast<CreateDispDeJitter>(dlsym(mSoHandle, "createDispDeJitter"));
        mFnDestroyDispDeJitter = reinterpret_cast<DestroyDispDeJitter>(dlsym(mSoHandle, "destroyDispDeJitter"));
        mFnShouldDelayPresent = reinterpret_cast<ShouldDelayPresent>(dlsym(mSoHandle, "shouldDelayPresent"));
        mFnMarkTimestamp = reinterpret_cast<MarkTimestamp>(dlsym(mSoHandle, "markTimestamp"));
        if (nullptr == mFnCreateDispDeJitter) {
            ALOGE("finding createDispDeJitter() failed");
        }
        if (nullptr == mFnDestroyDispDeJitter) {
            ALOGE("finding destroyDispDeJitter() failed");
        }
        if (nullptr == mFnShouldDelayPresent) {
            ALOGE("finding shouldDelayPresent() failed");
        }
        if (nullptr == mFnMarkTimestamp) {
            ALOGE("finding markTimestamp() failed");
        }
    } else {
        ALOGE("open libdisp_dejitter failed");
    }
}

DispDeJitterHelper::~DispDeJitterHelper() {
    if (mSoHandle) {
        dlclose(mSoHandle);
    }
}

DispDeJitter* DispDeJitterHelper::createDispDeJitter() {
    if (mFnCreateDispDeJitter) {
        return mFnCreateDispDeJitter();
    }
    return nullptr;
}

void DispDeJitterHelper::destroyDispDeJitter(DispDeJitter* dispDeJitter) {
    if (mFnDestroyDispDeJitter) {
        mFnDestroyDispDeJitter(dispDeJitter);
    }
}

bool DispDeJitterHelper::shouldDelayPresent(DispDeJitter* dispDeJitter, const sp<GraphicBuffer>& gb, const nsecs_t& expectedPresent) {
    if (mFnShouldDelayPresent) {
        return mFnShouldDelayPresent(dispDeJitter, gb, expectedPresent);
    }
    return false;
}

void DispDeJitterHelper::markTimestamp(const sp<GraphicBuffer>& gb) {
    if (mFnMarkTimestamp) {
        mFnMarkTimestamp(gb);
    }
}

} // namespace android
