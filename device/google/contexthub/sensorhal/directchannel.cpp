/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define LOG_TAG "directchannel"
#include "directchannel.h"

#include <cutils/ashmem.h>
#include <hardware/sensors.h>
#include <utils/Log.h>

#include <sys/mman.h>

namespace android {

bool DirectChannelBase::isValid() {
    return mBuffer != nullptr;
}

int DirectChannelBase::getError() {
    return mError;
}

void DirectChannelBase::write(const sensors_event_t * ev) {
    if (isValid()) {
        mBuffer->write(ev, 1);
    }
}

AshmemDirectChannel::AshmemDirectChannel(const struct sensors_direct_mem_t *mem) : mAshmemFd(0) {
    mAshmemFd = mem->handle->data[0];

    if (!::ashmem_valid(mAshmemFd)) {
        mError = BAD_VALUE;
        return;
    }

    if ((size_t)::ashmem_get_size_region(mAshmemFd) != mem->size) {
        mError = BAD_VALUE;
        return;
    }

    mSize = mem->size;

    mBase = ::mmap(NULL, mem->size, PROT_WRITE, MAP_SHARED, mAshmemFd, 0);
    if (mBase == nullptr) {
        mError = NO_MEMORY;
        return;
    }

    mBuffer = std::unique_ptr<LockfreeBuffer>(new LockfreeBuffer(mBase, mSize));
    if (!mBuffer) {
        mError = NO_MEMORY;
    }
}

AshmemDirectChannel::~AshmemDirectChannel() {
    if (mBase) {
        mBuffer = nullptr;
        ::munmap(mBase, mSize);
        mBase = nullptr;
    }
    ::close(mAshmemFd);
}

bool AshmemDirectChannel::memoryMatches(const struct sensors_direct_mem_t * /*mem*/) const {
    return false;
}

ANDROID_SINGLETON_STATIC_INSTANCE(GrallocHalWrapper);

GrallocHalWrapper::GrallocHalWrapper()
        : mError(NO_INIT), mVersion(-1),
          mGrallocModule(nullptr), mAllocDevice(nullptr), mGralloc1Device(nullptr),
          mPfnRetain(nullptr), mPfnRelease(nullptr), mPfnLock(nullptr), mPfnUnlock(nullptr),
          mUnregisterImplyDelete(false) {
    const hw_module_t *module;
    status_t err = ::hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &module);
    ALOGE_IF(err, "couldn't load %s module (%s)", GRALLOC_HARDWARE_MODULE_ID, strerror(-err));

    if (module == nullptr) {
        mError = (err < 0) ? err : NO_INIT;
    }

    switch ((module->module_api_version >> 8) & 0xFF) {
        case 0:
            err = ::gralloc_open(module, &mAllocDevice);
            if (err != NO_ERROR) {
                ALOGE("cannot open alloc device (%s)", strerror(-err));
                break;
            }

            if (mAllocDevice == nullptr) {
                ALOGE("gralloc_open returns no error, but result is nullptr");
                err = INVALID_OPERATION;
                break;
            }

            // successfully initialized gralloc
            mGrallocModule = (gralloc_module_t *)module;
            mVersion = 0;
            break;
        case 1: {
            err = ::gralloc1_open(module, &mGralloc1Device);
            if (err != NO_ERROR) {
                ALOGE("cannot open gralloc1 device (%s)", strerror(-err));
                break;
            }

            if (mGralloc1Device == nullptr || mGralloc1Device->getFunction == nullptr) {
                ALOGE("gralloc1_open returns no error, but result is nullptr");
                err = INVALID_OPERATION;
                break;
            }

            mPfnRetain = (GRALLOC1_PFN_RETAIN)(mGralloc1Device->getFunction(mGralloc1Device,
                                                      GRALLOC1_FUNCTION_RETAIN));
            mPfnRelease = (GRALLOC1_PFN_RELEASE)(mGralloc1Device->getFunction(mGralloc1Device,
                                                       GRALLOC1_FUNCTION_RELEASE));
            mPfnLock = (GRALLOC1_PFN_LOCK)(mGralloc1Device->getFunction(mGralloc1Device,
                                                    GRALLOC1_FUNCTION_LOCK));
            mPfnUnlock = (GRALLOC1_PFN_UNLOCK)(mGralloc1Device->getFunction(mGralloc1Device,
                                                      GRALLOC1_FUNCTION_UNLOCK));
            mPfnGetBackingStore = (GRALLOC1_PFN_GET_BACKING_STORE)
                    (mGralloc1Device->getFunction(mGralloc1Device,
                                                  GRALLOC1_FUNCTION_GET_BACKING_STORE));
            if (mPfnRetain == nullptr || mPfnRelease == nullptr
                    || mPfnLock == nullptr || mPfnUnlock == nullptr
                    || mPfnGetBackingStore == nullptr) {
                ALOGE("Function pointer for retain, release, lock, unlock and getBackingStore are "
                      "%p, %p, %p, %p, %p",
                      mPfnRetain, mPfnRelease, mPfnLock, mPfnUnlock, mPfnGetBackingStore);
                err = BAD_VALUE;
                break;
            }


            int32_t caps[GRALLOC1_LAST_CAPABILITY];
            uint32_t n_cap = GRALLOC1_LAST_CAPABILITY;
            mGralloc1Device->getCapabilities(mGralloc1Device, &n_cap, caps);
            for (size_t i = 0; i < n_cap; ++i) {
                if (caps[i] == GRALLOC1_CAPABILITY_RELEASE_IMPLY_DELETE) {
                    mUnregisterImplyDelete = true;
                }
            }
            ALOGI("gralloc hal %ssupport RELEASE_IMPLY_DELETE",
                  mUnregisterImplyDelete ? "" : "does not ");

            // successfully initialized gralloc1
            mGrallocModule = (gralloc_module_t *)module;
            mVersion = 1;
            break;
        }
        default:
            ALOGE("Unknown version, not supported");
            break;
    }
    mError = err;
}

GrallocHalWrapper::~GrallocHalWrapper() {
    if (mAllocDevice != nullptr) {
        ::gralloc_close(mAllocDevice);
    }
}

int GrallocHalWrapper::registerBuffer(const native_handle_t *handle) {
    switch (mVersion) {
        case 0:
            return mGrallocModule->registerBuffer(mGrallocModule, handle);
        case 1:
            return mapGralloc1Error(mPfnRetain(mGralloc1Device, handle));
        default:
            return NO_INIT;
    }
}

int GrallocHalWrapper::unregisterBuffer(const native_handle_t *handle) {
    switch (mVersion) {
        case 0:
            return mGrallocModule->unregisterBuffer(mGrallocModule, handle);
        case 1:
            return mapGralloc1Error(mPfnRelease(mGralloc1Device, handle));
        default:
            return NO_INIT;
    }
}

int GrallocHalWrapper::lock(const native_handle_t *handle,
                           int usage, int l, int t, int w, int h, void **vaddr) {
    switch (mVersion) {
        case 0:
            return mGrallocModule->lock(mGrallocModule, handle, usage, l, t, w, h, vaddr);
        case 1: {
            const gralloc1_rect_t rect = {
                .left = l,
                .top = t,
                .width = w,
                .height = h
            };
            return mapGralloc1Error(mPfnLock(mGralloc1Device, handle,
                                             GRALLOC1_PRODUCER_USAGE_CPU_WRITE_OFTEN,
                                             GRALLOC1_CONSUMER_USAGE_NONE,
                                             &rect, vaddr, -1));
        }
        default:
            return NO_INIT;
    }
}

int GrallocHalWrapper::unlock(const native_handle_t *handle) {
    switch (mVersion) {
        case 0:
            return mGrallocModule->unlock(mGrallocModule, handle);
        case 1: {
            int32_t dummy;
            return mapGralloc1Error(mPfnUnlock(mGralloc1Device, handle, &dummy));
        }
        default:
            return NO_INIT;
    }
}

bool GrallocHalWrapper::isSameMemory(const native_handle_t *h1, const native_handle_t *h2) {
    switch (mVersion) {
        case 0:
            return false; // version 1.0 cannot compare two memory
        case 1: {
            gralloc1_backing_store_t s1, s2;

            return mPfnGetBackingStore(mGralloc1Device, h1, &s1) == GRALLOC1_ERROR_NONE
                    && mPfnGetBackingStore(mGralloc1Device, h2, &s2) == GRALLOC1_ERROR_NONE
                    && s1 == s2;
        }
    }
    return false;
}

int GrallocHalWrapper::mapGralloc1Error(int grallocError) {
    switch (grallocError) {
        case GRALLOC1_ERROR_NONE:
            return NO_ERROR;
        case GRALLOC1_ERROR_BAD_DESCRIPTOR:
        case GRALLOC1_ERROR_BAD_HANDLE:
        case GRALLOC1_ERROR_BAD_VALUE:
            return BAD_VALUE;
        case GRALLOC1_ERROR_NOT_SHARED:
        case GRALLOC1_ERROR_NO_RESOURCES:
            return NO_MEMORY;
        case GRALLOC1_ERROR_UNDEFINED:
        case GRALLOC1_ERROR_UNSUPPORTED:
            return INVALID_OPERATION;
        default:
            return UNKNOWN_ERROR;
    }
}

GrallocDirectChannel::GrallocDirectChannel(const struct sensors_direct_mem_t *mem)
        : mNativeHandle(nullptr) {
    if (mem->handle == nullptr) {
        ALOGE("mem->handle == nullptr");
        mError = BAD_VALUE;
        return;
    }

    mNativeHandle = ::native_handle_clone(mem->handle);
    if (mNativeHandle == nullptr) {
        ALOGE("clone mem->handle failed...");
        mError = NO_MEMORY;
        return;
    }

    mError = GrallocHalWrapper::getInstance().registerBuffer(mNativeHandle);
    if (mError != NO_ERROR) {
        ALOGE("registerBuffer failed");
        return;
    }

    mError = GrallocHalWrapper::getInstance().lock(mNativeHandle,
            GRALLOC_USAGE_SW_WRITE_OFTEN, 0, 0, mem->size, 1, &mBase);
    if (mError != NO_ERROR) {
        ALOGE("lock buffer failed");
        return;
    }

    if (mBase == nullptr) {
        ALOGE("lock buffer => nullptr");
        mError = NO_MEMORY;
        return;
    }

    mSize = mem->size;
    mBuffer = std::make_unique<LockfreeBuffer>(mBase, mSize);
    if (!mBuffer) {
        mError = NO_MEMORY;
        return;
    }

    mError = NO_ERROR;
}

GrallocDirectChannel::~GrallocDirectChannel() {
    if (mNativeHandle != nullptr) {
        if (mBase) {
            mBuffer = nullptr;
            GrallocHalWrapper::getInstance().unlock(mNativeHandle);
            mBase = nullptr;
        }
        GrallocHalWrapper::getInstance().unregisterBuffer(mNativeHandle);
        if (!GrallocHalWrapper::getInstance().unregisterImplyDelete()) {
            ::native_handle_close(mNativeHandle);
            ::native_handle_delete(mNativeHandle);
        }
        mNativeHandle = nullptr;
    }
}

bool GrallocDirectChannel::memoryMatches(const struct sensors_direct_mem_t *mem) const {
    return mem->type == SENSOR_DIRECT_MEM_TYPE_GRALLOC &&
            GrallocHalWrapper::getInstance().isSameMemory(mem->handle, mNativeHandle);
}

} // namespace android
