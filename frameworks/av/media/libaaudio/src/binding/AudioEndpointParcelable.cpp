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

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <utility/AAudioUtilities.h>

#include "binding/AAudioServiceDefinitions.h"
#include "binding/RingBufferParcelable.h"
#include "binding/AudioEndpointParcelable.h"

using android::base::unique_fd;
using android::NO_ERROR;
using android::status_t;
using android::Parcel;
using android::Parcelable;

using namespace aaudio;

/**
 * Container for information about the message queues plus
 * general stream information needed by AAudio clients.
 * It contains no addresses, just sizes, offsets and file descriptors for
 * shared memory that can be passed through Binder.
 */
AudioEndpointParcelable::AudioEndpointParcelable() {}

AudioEndpointParcelable::~AudioEndpointParcelable() {}

/**
 * Add the file descriptor to the table.
 * @return index in table or negative error
 */
int32_t AudioEndpointParcelable::addFileDescriptor(const unique_fd& fd,
                                                   int32_t sizeInBytes) {
    if (mNumSharedMemories >= MAX_SHARED_MEMORIES) {
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }
    int32_t index = mNumSharedMemories++;
    mSharedMemories[index].setup(fd, sizeInBytes);
    return index;
}

/**
 * The read and write must be symmetric.
 */
status_t AudioEndpointParcelable::writeToParcel(Parcel* parcel) const {
    parcel->writeInt32(mNumSharedMemories);
    for (int i = 0; i < mNumSharedMemories; i++) {
        mSharedMemories[i].writeToParcel(parcel);
    }
    mUpMessageQueueParcelable.writeToParcel(parcel);
    mDownMessageQueueParcelable.writeToParcel(parcel);
    mUpDataQueueParcelable.writeToParcel(parcel);
    mDownDataQueueParcelable.writeToParcel(parcel);
    return NO_ERROR; // TODO check for errors above
}

status_t AudioEndpointParcelable::readFromParcel(const Parcel* parcel) {
    parcel->readInt32(&mNumSharedMemories);
    for (int i = 0; i < mNumSharedMemories; i++) {
        mSharedMemories[i].readFromParcel(parcel);
    }
    mUpMessageQueueParcelable.readFromParcel(parcel);
    mDownMessageQueueParcelable.readFromParcel(parcel);
    mUpDataQueueParcelable.readFromParcel(parcel);
    mDownDataQueueParcelable.readFromParcel(parcel);
    return NO_ERROR; // TODO check for errors above
}

aaudio_result_t AudioEndpointParcelable::resolve(EndpointDescriptor *descriptor) {
    aaudio_result_t result = mUpMessageQueueParcelable.resolve(mSharedMemories,
                                                           &descriptor->upMessageQueueDescriptor);
    if (result != AAUDIO_OK) return result;
    result = mDownMessageQueueParcelable.resolve(mSharedMemories,
                                        &descriptor->downMessageQueueDescriptor);
    if (result != AAUDIO_OK) return result;

    result = mDownDataQueueParcelable.resolve(mSharedMemories,
                                              &descriptor->dataQueueDescriptor);
    return result;
}

aaudio_result_t AudioEndpointParcelable::close() {
    int err = 0;
    for (int i = 0; i < mNumSharedMemories; i++) {
        int lastErr = mSharedMemories[i].close();
        if (lastErr < 0) err = lastErr;
    }
    return AAudioConvert_androidToAAudioResult(err);
}

aaudio_result_t AudioEndpointParcelable::validate() {
    aaudio_result_t result;
    if (mNumSharedMemories < 0 || mNumSharedMemories >= MAX_SHARED_MEMORIES) {
        ALOGE("AudioEndpointParcelable invalid mNumSharedMemories = %d", mNumSharedMemories);
        return AAUDIO_ERROR_INTERNAL;
    }
    for (int i = 0; i < mNumSharedMemories; i++) {
        result = mSharedMemories[i].validate();
        if (result != AAUDIO_OK) {
            ALOGE("AudioEndpointParcelable invalid mSharedMemories[%d] = %d", i, result);
            return result;
        }
    }
    if ((result = mUpMessageQueueParcelable.validate()) != AAUDIO_OK) {
        ALOGE("AudioEndpointParcelable invalid mUpMessageQueueParcelable = %d", result);
        return result;
    }
    if ((result = mDownMessageQueueParcelable.validate()) != AAUDIO_OK) {
        ALOGE("AudioEndpointParcelable invalid mDownMessageQueueParcelable = %d", result);
        return result;
    }
    if ((result = mUpDataQueueParcelable.validate()) != AAUDIO_OK) {
        ALOGE("AudioEndpointParcelable invalid mUpDataQueueParcelable = %d", result);
        return result;
    }
    if ((result = mDownDataQueueParcelable.validate()) != AAUDIO_OK) {
        ALOGE("AudioEndpointParcelable invalid mDownDataQueueParcelable = %d", result);
        return result;
    }
    return AAUDIO_OK;
}

void AudioEndpointParcelable::dump() {
    ALOGD("AudioEndpointParcelable ======================================= BEGIN");
    ALOGD("AudioEndpointParcelable mNumSharedMemories = %d", mNumSharedMemories);
    for (int i = 0; i < mNumSharedMemories; i++) {
        mSharedMemories[i].dump();
    }
    ALOGD("AudioEndpointParcelable mUpMessageQueueParcelable =========");
    mUpMessageQueueParcelable.dump();
    ALOGD("AudioEndpointParcelable mDownMessageQueueParcelable =======");
    mDownMessageQueueParcelable.dump();
    ALOGD("AudioEndpointParcelable mUpDataQueueParcelable ============");
    mUpDataQueueParcelable.dump();
    ALOGD("AudioEndpointParcelable mDownDataQueueParcelable ==========");
    mDownDataQueueParcelable.dump();
    ALOGD("AudioEndpointParcelable ======================================= END");
}

