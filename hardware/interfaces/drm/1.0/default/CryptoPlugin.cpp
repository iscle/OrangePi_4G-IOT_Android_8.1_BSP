/*
 * Copyright (C) 2016 The Android Open Source Project
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
#define LOG_TAG "android.hardware.drm@1.0-impl"

#include "CryptoPlugin.h"
#include "TypeConvert.h"

#include <android/hidl/memory/1.0/IMemory.h>
#include <hidlmemory/mapping.h>
#include <log/log.h>
#include <media/stagefright/foundation/AString.h>

using android::hardware::hidl_memory;
using android::hidl::memory::V1_0::IMemory;

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

    // Methods from ::android::hardware::drm::V1_0::ICryptoPlugin follow
    Return<bool> CryptoPlugin::requiresSecureDecoderComponent(
            const hidl_string& mime) {
        return mLegacyPlugin->requiresSecureDecoderComponent(mime.c_str());
    }

    Return<void> CryptoPlugin::notifyResolution(uint32_t width,
            uint32_t height) {
        mLegacyPlugin->notifyResolution(width, height);
        return Void();
    }

    Return<Status> CryptoPlugin::setMediaDrmSession(
            const hidl_vec<uint8_t>& sessionId) {
        return toStatus(mLegacyPlugin->setMediaDrmSession(toVector(sessionId)));
    }

    Return<void> CryptoPlugin::setSharedBufferBase(const hidl_memory& base,
            uint32_t bufferId) {
        sp<IMemory> hidlMemory = mapMemory(base);
        ALOGE_IF(hidlMemory == nullptr, "mapMemory returns nullptr");

        // allow mapMemory to return nullptr
        mSharedBufferMap[bufferId] = hidlMemory;
        return Void();
    }

    Return<void> CryptoPlugin::decrypt(bool secure,
            const hidl_array<uint8_t, 16>& keyId,
            const hidl_array<uint8_t, 16>& iv, Mode mode,
            const Pattern& pattern, const hidl_vec<SubSample>& subSamples,
            const SharedBuffer& source, uint64_t offset,
            const DestinationBuffer& destination,
            decrypt_cb _hidl_cb) {

        if (mSharedBufferMap.find(source.bufferId) == mSharedBufferMap.end()) {
            _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "source decrypt buffer base not set");
            return Void();
        }

        if (destination.type == BufferType::SHARED_MEMORY) {
            const SharedBuffer& dest = destination.nonsecureMemory;
            if (mSharedBufferMap.find(dest.bufferId) == mSharedBufferMap.end()) {
                _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "destination decrypt buffer base not set");
                return Void();
            }
        }

        android::CryptoPlugin::Mode legacyMode;
        switch(mode) {
        case Mode::UNENCRYPTED:
            legacyMode = android::CryptoPlugin::kMode_Unencrypted;
            break;
        case Mode::AES_CTR:
            legacyMode = android::CryptoPlugin::kMode_AES_CTR;
            break;
        case Mode::AES_CBC_CTS:
            legacyMode = android::CryptoPlugin::kMode_AES_WV;
            break;
        case Mode::AES_CBC:
            legacyMode = android::CryptoPlugin::kMode_AES_CBC;
            break;
        }
        android::CryptoPlugin::Pattern legacyPattern;
        legacyPattern.mEncryptBlocks = pattern.encryptBlocks;
        legacyPattern.mSkipBlocks = pattern.skipBlocks;

        android::CryptoPlugin::SubSample *legacySubSamples =
            new android::CryptoPlugin::SubSample[subSamples.size()];

        for (size_t i = 0; i < subSamples.size(); i++) {
            legacySubSamples[i].mNumBytesOfClearData
                = subSamples[i].numBytesOfClearData;
            legacySubSamples[i].mNumBytesOfEncryptedData
                = subSamples[i].numBytesOfEncryptedData;
        }

        AString detailMessage;
        sp<IMemory> sourceBase = mSharedBufferMap[source.bufferId];
        if (sourceBase == nullptr) {
            _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "source is a nullptr");
            return Void();
        }

        if (source.offset + offset + source.size > sourceBase->getSize()) {
            _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "invalid buffer size");
            return Void();
        }

        uint8_t *base = static_cast<uint8_t *>
                (static_cast<void *>(sourceBase->getPointer()));
        void *srcPtr = static_cast<void *>(base + source.offset + offset);

        void *destPtr = NULL;
        if (destination.type == BufferType::SHARED_MEMORY) {
            const SharedBuffer& destBuffer = destination.nonsecureMemory;
            sp<IMemory> destBase = mSharedBufferMap[destBuffer.bufferId];
            if (destBase == nullptr) {
                _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "destination is a nullptr");
                return Void();
            }

            if (destBuffer.offset + destBuffer.size > destBase->getSize()) {
                _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, 0, "invalid buffer size");
                return Void();
            }
            destPtr = static_cast<void *>(base + destination.nonsecureMemory.offset);
        } else if (destination.type == BufferType::NATIVE_HANDLE) {
            native_handle_t *handle = const_cast<native_handle_t *>(
                    destination.secureMemory.getNativeHandle());
            destPtr = static_cast<void *>(handle);
        }
        ssize_t result = mLegacyPlugin->decrypt(secure, keyId.data(), iv.data(),
                legacyMode, legacyPattern, srcPtr, legacySubSamples,
                subSamples.size(), destPtr, &detailMessage);

        delete[] legacySubSamples;

        uint32_t status;
        uint32_t bytesWritten;

        if (result >= 0) {
            status = android::OK;
            bytesWritten = result;
        } else {
            status = result;
            bytesWritten = 0;
        }

        _hidl_cb(toStatus(status), bytesWritten, detailMessage.c_str());
        return Void();
    }

} // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android
