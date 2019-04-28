/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_TAG "AAudio"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <stdint.h>

#include <sys/mman.h>
#include <binder/Parcelable.h>

#include <aaudio/AAudio.h>

#include "binding/SharedMemoryParcelable.h"
#include "binding/SharedRegionParcelable.h"

using android::NO_ERROR;
using android::status_t;
using android::Parcel;
using android::Parcelable;

using namespace aaudio;

SharedRegionParcelable::SharedRegionParcelable() {}
SharedRegionParcelable::~SharedRegionParcelable() {}

void SharedRegionParcelable::setup(int32_t sharedMemoryIndex,
                                   int32_t offsetInBytes,
                                   int32_t sizeInBytes) {
    mSharedMemoryIndex = sharedMemoryIndex;
    mOffsetInBytes = offsetInBytes;
    mSizeInBytes = sizeInBytes;
}

status_t SharedRegionParcelable::writeToParcel(Parcel* parcel) const {
    parcel->writeInt32(mSizeInBytes);
    if (mSizeInBytes > 0) {
        parcel->writeInt32(mSharedMemoryIndex);
        parcel->writeInt32(mOffsetInBytes);
    }
    return NO_ERROR; // TODO check for errors above
}

status_t SharedRegionParcelable::readFromParcel(const Parcel* parcel) {
    parcel->readInt32(&mSizeInBytes);
    if (mSizeInBytes > 0) {
        parcel->readInt32(&mSharedMemoryIndex);
        parcel->readInt32(&mOffsetInBytes);
    }
    return NO_ERROR; // TODO check for errors above
}

aaudio_result_t SharedRegionParcelable::resolve(SharedMemoryParcelable *memoryParcels,
                                              void **regionAddressPtr) {
    if (mSizeInBytes == 0) {
        *regionAddressPtr = nullptr;
        return AAUDIO_OK;
    }
    if (mSharedMemoryIndex < 0) {
        ALOGE("SharedRegionParcelable invalid mSharedMemoryIndex = %d", mSharedMemoryIndex);
        return AAUDIO_ERROR_INTERNAL;
    }
    SharedMemoryParcelable *memoryParcel = &memoryParcels[mSharedMemoryIndex];
    return memoryParcel->resolve(mOffsetInBytes, mSizeInBytes, regionAddressPtr);
}

aaudio_result_t SharedRegionParcelable::validate() {
    if (mSizeInBytes < 0 || mSizeInBytes >= MAX_MMAP_SIZE_BYTES) {
        ALOGE("SharedRegionParcelable invalid mSizeInBytes = %d", mSizeInBytes);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }
    if (mSizeInBytes > 0) {
        if (mOffsetInBytes < 0 || mOffsetInBytes >= MAX_MMAP_OFFSET_BYTES) {
            ALOGE("SharedRegionParcelable invalid mOffsetInBytes = %d", mOffsetInBytes);
            return AAUDIO_ERROR_OUT_OF_RANGE;
        }
        if (mSharedMemoryIndex < 0 || mSharedMemoryIndex >= MAX_SHARED_MEMORIES) {
            ALOGE("SharedRegionParcelable invalid mSharedMemoryIndex = %d", mSharedMemoryIndex);
            return AAUDIO_ERROR_INTERNAL;
        }
    }
    return AAUDIO_OK;
}

void SharedRegionParcelable::dump() {
    ALOGD("SharedRegionParcelable mSizeInBytes = %d -----", mSizeInBytes);
    if (mSizeInBytes > 0) {
        ALOGD("SharedRegionParcelable mSharedMemoryIndex = %d", mSharedMemoryIndex);
        ALOGD("SharedRegionParcelable mOffsetInBytes = %d", mOffsetInBytes);
    }
}
