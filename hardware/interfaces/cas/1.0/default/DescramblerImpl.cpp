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
#define LOG_TAG "android.hardware.cas@1.0-DescramblerImpl"

#include <hidlmemory/mapping.h>
#include <media/cas/DescramblerAPI.h>
#include <media/hardware/CryptoAPI.h>
#include <media/stagefright/foundation/AUtils.h>
#include <utils/Log.h>

#include "DescramblerImpl.h"
#include "SharedLibrary.h"
#include "TypeConvert.h"

namespace android {
using hidl::memory::V1_0::IMemory;

namespace hardware {
namespace cas {
namespace V1_0 {
namespace implementation {

#define CHECK_SUBSAMPLE_DEF(type) \
static_assert(sizeof(SubSample) == sizeof(type::SubSample), \
        "SubSample: size doesn't match"); \
static_assert(offsetof(SubSample, numBytesOfClearData) \
        == offsetof(type::SubSample, mNumBytesOfClearData), \
        "SubSample: numBytesOfClearData offset doesn't match"); \
static_assert(offsetof(SubSample, numBytesOfEncryptedData) \
        == offsetof(type::SubSample, mNumBytesOfEncryptedData), \
        "SubSample: numBytesOfEncryptedData offset doesn't match")

CHECK_SUBSAMPLE_DEF(DescramblerPlugin);
CHECK_SUBSAMPLE_DEF(CryptoPlugin);

DescramblerImpl::DescramblerImpl(
        const sp<SharedLibrary>& library, DescramblerPlugin *plugin) :
        mLibrary(library), mPlugin(plugin) {
    ALOGV("CTOR: mPlugin=%p", mPlugin);
}

DescramblerImpl::~DescramblerImpl() {
    ALOGV("DTOR: mPlugin=%p", mPlugin);
    release();
}

Return<Status> DescramblerImpl::setMediaCasSession(const HidlCasSessionId& sessionId) {
    ALOGV("%s: sessionId=%s", __FUNCTION__,
            sessionIdToString(sessionId).string());

    return toStatus(mPlugin->setMediaCasSession(sessionId));
}

Return<bool> DescramblerImpl::requiresSecureDecoderComponent(
        const hidl_string& mime) {
    return mPlugin->requiresSecureDecoderComponent(String8(mime.c_str()));
}

static inline bool validateRangeForSize(
        uint64_t offset, uint64_t length, uint64_t size) {
    return isInRange<uint64_t, uint64_t>(0, size, offset, length);
}

Return<void> DescramblerImpl::descramble(
        ScramblingControl scramblingControl,
        const hidl_vec<SubSample>& subSamples,
        const SharedBuffer& srcBuffer,
        uint64_t srcOffset,
        const DestinationBuffer& dstBuffer,
        uint64_t dstOffset,
        descramble_cb _hidl_cb) {
    ALOGV("%s", __FUNCTION__);

    sp<IMemory> srcMem = mapMemory(srcBuffer.heapBase);

    // Validate if the offset and size in the SharedBuffer is consistent with the
    // mapped ashmem, since the offset and size is controlled by client.
    if (srcMem == NULL) {
        ALOGE("Failed to map src buffer.");
        _hidl_cb(toStatus(BAD_VALUE), 0, NULL);
        return Void();
    }
    if (!validateRangeForSize(
            srcBuffer.offset, srcBuffer.size, (uint64_t)srcMem->getSize())) {
        ALOGE("Invalid src buffer range: offset %llu, size %llu, srcMem size %llu",
                srcBuffer.offset, srcBuffer.size, (uint64_t)srcMem->getSize());
        android_errorWriteLog(0x534e4554, "67962232");
        _hidl_cb(toStatus(BAD_VALUE), 0, NULL);
        return Void();
    }

    // use 64-bit here to catch bad subsample size that might be overflowing.
    uint64_t totalBytesInSubSamples = 0;
    for (size_t i = 0; i < subSamples.size(); i++) {
        totalBytesInSubSamples += (uint64_t)subSamples[i].numBytesOfClearData +
                subSamples[i].numBytesOfEncryptedData;
    }
    // Further validate if the specified srcOffset and requested total subsample size
    // is consistent with the source shared buffer size.
    if (!validateRangeForSize(srcOffset, totalBytesInSubSamples, srcBuffer.size)) {
        ALOGE("Invalid srcOffset and subsample size: "
                "srcOffset %llu, totalBytesInSubSamples %llu, srcBuffer size %llu",
                srcOffset, totalBytesInSubSamples, srcBuffer.size);
        android_errorWriteLog(0x534e4554, "67962232");
        _hidl_cb(toStatus(BAD_VALUE), 0, NULL);
        return Void();
    }

    void *srcPtr = (uint8_t *)(void *)srcMem->getPointer() + srcBuffer.offset;
    void *dstPtr = NULL;
    if (dstBuffer.type == BufferType::SHARED_MEMORY) {
        // When using shared memory, src buffer is also used as dst,
        // we don't map it again here.
        dstPtr = srcPtr;

        // In this case the dst and src would be the same buffer, need to validate
        // dstOffset against the buffer size too.
        if (!validateRangeForSize(dstOffset, totalBytesInSubSamples, srcBuffer.size)) {
            ALOGE("Invalid dstOffset and subsample size: "
                    "dstOffset %llu, totalBytesInSubSamples %llu, srcBuffer size %llu",
                    dstOffset, totalBytesInSubSamples, srcBuffer.size);
            android_errorWriteLog(0x534e4554, "67962232");
            _hidl_cb(toStatus(BAD_VALUE), 0, NULL);
            return Void();
        }
    } else {
        native_handle_t *handle = const_cast<native_handle_t *>(
                dstBuffer.secureMemory.getNativeHandle());
        dstPtr = static_cast<void *>(handle);
    }
    // Casting hidl SubSample to DescramblerPlugin::SubSample, but need
    // to ensure structs are actually idential

    int32_t result = mPlugin->descramble(
            dstBuffer.type != BufferType::SHARED_MEMORY,
            (DescramblerPlugin::ScramblingControl)scramblingControl,
            subSamples.size(),
            (DescramblerPlugin::SubSample*)subSamples.data(),
            srcPtr,
            srcOffset,
            dstPtr,
            dstOffset,
            NULL);

    _hidl_cb(toStatus(result >= 0 ? OK : result), result, NULL);
    return Void();
}

Return<Status> DescramblerImpl::release() {
    ALOGV("%s: mPlugin=%p", __FUNCTION__, mPlugin);

    if (mPlugin != NULL) {
        delete mPlugin;
        mPlugin = NULL;
    }
    return Status::OK;
}

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android
