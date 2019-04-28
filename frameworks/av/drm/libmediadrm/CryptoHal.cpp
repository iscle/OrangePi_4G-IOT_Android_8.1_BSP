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

//#define LOG_NDEBUG 0
#define LOG_TAG "CryptoHal"
#include <utils/Log.h>

#include <android/hardware/drm/1.0/types.h>
#include <android/hidl/manager/1.0/IServiceManager.h>

#include <binder/IMemory.h>
#include <cutils/native_handle.h>
#include <media/CryptoHal.h>
#include <media/hardware/CryptoAPI.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaErrors.h>

using ::android::hardware::drm::V1_0::BufferType;
using ::android::hardware::drm::V1_0::DestinationBuffer;
using ::android::hardware::drm::V1_0::ICryptoFactory;
using ::android::hardware::drm::V1_0::ICryptoPlugin;
using ::android::hardware::drm::V1_0::Mode;
using ::android::hardware::drm::V1_0::Pattern;
using ::android::hardware::drm::V1_0::SharedBuffer;
using ::android::hardware::drm::V1_0::Status;
using ::android::hardware::drm::V1_0::SubSample;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hidl::manager::V1_0::IServiceManager;
using ::android::sp;


namespace android {

static status_t toStatusT(Status status) {
    switch (status) {
    case Status::OK:
        return OK;
    case Status::ERROR_DRM_NO_LICENSE:
        return ERROR_DRM_NO_LICENSE;
    case Status::ERROR_DRM_LICENSE_EXPIRED:
        return ERROR_DRM_LICENSE_EXPIRED;
    case Status::ERROR_DRM_RESOURCE_BUSY:
        return ERROR_DRM_RESOURCE_BUSY;
    case Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION:
        return ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION;
    case Status::ERROR_DRM_SESSION_NOT_OPENED:
        return ERROR_DRM_SESSION_NOT_OPENED;
    case Status::ERROR_DRM_CANNOT_HANDLE:
        return ERROR_DRM_CANNOT_HANDLE;
    case Status::ERROR_DRM_DECRYPT:
        return ERROR_DRM_DECRYPT;
    default:
        return UNKNOWN_ERROR;
    }
}


static hidl_vec<uint8_t> toHidlVec(const Vector<uint8_t> &vector) {
    hidl_vec<uint8_t> vec;
    vec.setToExternal(const_cast<uint8_t *>(vector.array()), vector.size());
    return vec;
}

static hidl_vec<uint8_t> toHidlVec(const void *ptr, size_t size) {
    hidl_vec<uint8_t> vec;
    vec.resize(size);
    memcpy(vec.data(), ptr, size);
    return vec;
}

static hidl_array<uint8_t, 16> toHidlArray16(const uint8_t *ptr) {
    if (!ptr) {
        return hidl_array<uint8_t, 16>();
    }
    return hidl_array<uint8_t, 16>(ptr);
}


static String8 toString8(hidl_string hString) {
    return String8(hString.c_str());
}


CryptoHal::CryptoHal()
    : mFactories(makeCryptoFactories()),
      mInitCheck((mFactories.size() == 0) ? ERROR_UNSUPPORTED : NO_INIT),
      mNextBufferId(0),
      mHeapSeqNum(0) {
}

CryptoHal::~CryptoHal() {
}

Vector<sp<ICryptoFactory>> CryptoHal::makeCryptoFactories() {
    Vector<sp<ICryptoFactory>> factories;

    auto manager = ::IServiceManager::getService();
    if (manager != NULL) {
        manager->listByInterface(ICryptoFactory::descriptor,
                [&factories](const hidl_vec<hidl_string> &registered) {
                    for (const auto &instance : registered) {
                        auto factory = ICryptoFactory::getService(instance);
                        if (factory != NULL) {
                            factories.push_back(factory);
                            ALOGI("makeCryptoFactories: factory instance %s is %s",
                                    instance.c_str(),
                                    factory->isRemote() ? "Remote" : "Not Remote");
                        }
                    }
                }
            );
    }

    if (factories.size() == 0) {
        // must be in passthrough mode, load the default passthrough service
        auto passthrough = ICryptoFactory::getService();
        if (passthrough != NULL) {
            ALOGI("makeCryptoFactories: using default crypto instance");
            factories.push_back(passthrough);
        } else {
            ALOGE("Failed to find any crypto factories");
        }
    }
    return factories;
}

sp<ICryptoPlugin> CryptoHal::makeCryptoPlugin(const sp<ICryptoFactory>& factory,
        const uint8_t uuid[16], const void *initData, size_t initDataSize) {

    sp<ICryptoPlugin> plugin;
    Return<void> hResult = factory->createPlugin(toHidlArray16(uuid),
            toHidlVec(initData, initDataSize),
            [&](Status status, const sp<ICryptoPlugin>& hPlugin) {
                if (status != Status::OK) {
                    ALOGE("Failed to make crypto plugin");
                    return;
                }
                plugin = hPlugin;
            }
        );
    return plugin;
}


status_t CryptoHal::initCheck() const {
    return mInitCheck;
}


bool CryptoHal::isCryptoSchemeSupported(const uint8_t uuid[16]) {
    Mutex::Autolock autoLock(mLock);

    for (size_t i = 0; i < mFactories.size(); i++) {
        if (mFactories[i]->isCryptoSchemeSupported(uuid)) {
            return true;
        }
    }
    return false;
}

status_t CryptoHal::createPlugin(const uint8_t uuid[16], const void *data,
        size_t size) {
    Mutex::Autolock autoLock(mLock);

    for (size_t i = 0; i < mFactories.size(); i++) {
        if (mFactories[i]->isCryptoSchemeSupported(uuid)) {
            mPlugin = makeCryptoPlugin(mFactories[i], uuid, data, size);
        }
    }

    if (mPlugin == NULL) {
        mInitCheck = ERROR_UNSUPPORTED;
    } else {
        mInitCheck = OK;
    }

    return mInitCheck;
}

status_t CryptoHal::destroyPlugin() {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    mPlugin.clear();
    return OK;
}

bool CryptoHal::requiresSecureDecoderComponent(const char *mime) const {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return mPlugin->requiresSecureDecoderComponent(hidl_string(mime));
}


/**
 * If the heap base isn't set, get the heap base from the IMemory
 * and send it to the HAL so it can map a remote heap of the same
 * size.  Once the heap base is established, shared memory buffers
 * are sent by providing an offset into the heap and a buffer size.
 */
int32_t CryptoHal::setHeapBase(const sp<IMemoryHeap>& heap) {
    if (heap == NULL) {
        ALOGE("setHeapBase(): heap is NULL");
        return -1;
    }
    native_handle_t* nativeHandle = native_handle_create(1, 0);
    if (!nativeHandle) {
        ALOGE("setHeapBase(), failed to create native handle");
        return -1;
    }

    Mutex::Autolock autoLock(mLock);

    int32_t seqNum = mHeapSeqNum++;
    int fd = heap->getHeapID();
    nativeHandle->data[0] = fd;
    auto hidlHandle = hidl_handle(nativeHandle);
    auto hidlMemory = hidl_memory("ashmem", hidlHandle, heap->getSize());
    mHeapBases.add(seqNum, mNextBufferId);
    Return<void> hResult = mPlugin->setSharedBufferBase(hidlMemory, mNextBufferId++);
    ALOGE_IF(!hResult.isOk(), "setSharedBufferBase(): remote call failed");
    return seqNum;
}

void CryptoHal::clearHeapBase(int32_t seqNum) {
    Mutex::Autolock autoLock(mLock);

    mHeapBases.removeItem(seqNum);
}

status_t CryptoHal::toSharedBuffer(const sp<IMemory>& memory, int32_t seqNum, ::SharedBuffer* buffer) {
    ssize_t offset;
    size_t size;

    if (memory == NULL && buffer == NULL) {
        return UNEXPECTED_NULL;
    }

    sp<IMemoryHeap> heap = memory->getMemory(&offset, &size);
    if (heap == NULL) {
        return UNEXPECTED_NULL;
    }

    // memory must be in the declared heap
    CHECK(mHeapBases.indexOfKey(seqNum) >= 0);

    buffer->bufferId = mHeapBases.valueFor(seqNum);
    buffer->offset = offset >= 0 ? offset : 0;
    buffer->size = size;
    return OK;
}

ssize_t CryptoHal::decrypt(const uint8_t keyId[16], const uint8_t iv[16],
        CryptoPlugin::Mode mode, const CryptoPlugin::Pattern &pattern,
        const ICrypto::SourceBuffer &source, size_t offset,
        const CryptoPlugin::SubSample *subSamples, size_t numSubSamples,
        const ICrypto::DestinationBuffer &destination, AString *errorDetailMsg) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    Mode hMode;
    switch(mode) {
    case CryptoPlugin::kMode_Unencrypted:
        hMode = Mode::UNENCRYPTED ;
        break;
    case CryptoPlugin::kMode_AES_CTR:
        hMode = Mode::AES_CTR;
        break;
    case CryptoPlugin::kMode_AES_WV:
        hMode = Mode::AES_CBC_CTS;
        break;
    case CryptoPlugin::kMode_AES_CBC:
        hMode = Mode::AES_CBC;
        break;
    default:
        return UNKNOWN_ERROR;
    }

    Pattern hPattern;
    hPattern.encryptBlocks = pattern.mEncryptBlocks;
    hPattern.skipBlocks = pattern.mSkipBlocks;

    std::vector<SubSample> stdSubSamples;
    for (size_t i = 0; i < numSubSamples; i++) {
        SubSample subSample;
        subSample.numBytesOfClearData = subSamples[i].mNumBytesOfClearData;
        subSample.numBytesOfEncryptedData = subSamples[i].mNumBytesOfEncryptedData;
        stdSubSamples.push_back(subSample);
    }
    auto hSubSamples = hidl_vec<SubSample>(stdSubSamples);

    int32_t heapSeqNum = source.mHeapSeqNum;
    bool secure;
    ::DestinationBuffer hDestination;
    if (destination.mType == kDestinationTypeSharedMemory) {
        hDestination.type = BufferType::SHARED_MEMORY;
        status_t status = toSharedBuffer(destination.mSharedMemory, heapSeqNum,
                &hDestination.nonsecureMemory);
        if (status != OK) {
            return status;
        }
        secure = false;
    } else {
        hDestination.type = BufferType::NATIVE_HANDLE;
        hDestination.secureMemory = hidl_handle(destination.mHandle);
        secure = true;
    }

    ::SharedBuffer hSource;
    status_t status = toSharedBuffer(source.mSharedMemory, heapSeqNum, &hSource);
    if (status != OK) {
        return status;
    }

    status_t err = UNKNOWN_ERROR;
    uint32_t bytesWritten = 0;

    Return<void> hResult = mPlugin->decrypt(secure, toHidlArray16(keyId), toHidlArray16(iv), hMode,
            hPattern, hSubSamples, hSource, offset, hDestination,
            [&](Status status, uint32_t hBytesWritten, hidl_string hDetailedError) {
                if (status == Status::OK) {
                    bytesWritten = hBytesWritten;
                    *errorDetailMsg = toString8(hDetailedError);
                }
                err = toStatusT(status);
            }
        );

    if (!hResult.isOk()) {
        err = DEAD_OBJECT;
    }

    if (err == OK) {
        return bytesWritten;
    }
    return err;
}

void CryptoHal::notifyResolution(uint32_t width, uint32_t height) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return;
    }

    mPlugin->notifyResolution(width, height);
}

status_t CryptoHal::setMediaDrmSession(const Vector<uint8_t> &sessionId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return toStatusT(mPlugin->setMediaDrmSession(toHidlVec(sessionId)));
}

}  // namespace android
