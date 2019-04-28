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

#include "TestMsgQ.h"

namespace android {
namespace hardware {
namespace tests {
namespace msgq {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::msgq::V1_0::ITestMsgQ follow.
Return<void> TestMsgQ::configureFmqSyncReadWrite(configureFmqSyncReadWrite_cb _hidl_cb) {
    static constexpr size_t kNumElementsInQueue = 1024;
    mFmqSynchronized.reset(new (std::nothrow) MessageQueueSync(
            kNumElementsInQueue, true /* configureEventFlagWord */));
    if ((mFmqSynchronized == nullptr) || (mFmqSynchronized->isValid() == false)) {
        _hidl_cb(false /* ret */, MessageQueueSync::Descriptor());
    } else {
        /*
         * Initialize the EventFlag word with bit FMQ_NOT_FULL.
         */
        auto evFlagWordPtr = mFmqSynchronized->getEventFlagWord();
        if (evFlagWordPtr != nullptr) {
            std::atomic_init(evFlagWordPtr,
                             static_cast<uint32_t>(ITestMsgQ::EventFlagBits::FMQ_NOT_FULL));
        }
        _hidl_cb(true /* ret */, *mFmqSynchronized->getDesc());
    }
    return Void();
}

Return<void> TestMsgQ::getFmqUnsyncWrite(bool configureFmq, getFmqUnsyncWrite_cb _hidl_cb) {
    if (configureFmq) {
        static constexpr size_t kNumElementsInQueue = 1024;
        mFmqUnsynchronized.reset(new (std::nothrow) MessageQueueUnsync(kNumElementsInQueue));
    }
    if ((mFmqUnsynchronized == nullptr) ||
        (mFmqUnsynchronized->isValid() == false)) {
        _hidl_cb(false /* ret */, MessageQueueUnsync::Descriptor());
    } else {
        _hidl_cb(true /* ret */, *mFmqUnsynchronized->getDesc());
    }
    return Void();
}

Return<bool> TestMsgQ::requestWriteFmqSync(int32_t count) {
    std::vector<uint16_t> data(count);
    for (int i = 0; i < count; i++) {
        data[i] = i;
    }
    bool result = mFmqSynchronized->write(&data[0], count);
    return result;
}

Return<bool> TestMsgQ::requestReadFmqSync(int32_t count) {
    std::vector<uint16_t> data(count);
    bool result = mFmqSynchronized->read(&data[0], count)
            && verifyData(&data[0], count);
    return result;
}

Return<bool> TestMsgQ::requestWriteFmqUnsync(int32_t count) {
    std::vector<uint16_t> data(count);
    for (int i = 0; i < count; i++) {
        data[i] = i;
    }
    bool result = mFmqUnsynchronized->write(&data[0], count);
    return result;
}

Return<bool> TestMsgQ::requestReadFmqUnsync(int32_t count) {
    std::vector<uint16_t> data(count);
    bool result =
            mFmqUnsynchronized->read(&data[0], count) && verifyData(&data[0], count);
    return result;
}

Return<void> TestMsgQ::requestBlockingRead(int32_t count) {
    std::vector<uint16_t> data(count);
    bool result = mFmqSynchronized->readBlocking(
            &data[0],
            count,
            static_cast<uint32_t>(ITestMsgQ::EventFlagBits::FMQ_NOT_FULL),
            static_cast<uint32_t>(ITestMsgQ::EventFlagBits::FMQ_NOT_EMPTY),
            5000000000 /* timeOutNanos */);

    if (result == false) {
        ALOGE("Blocking read fails");
    }
    return Void();
}

Return<void> TestMsgQ::requestBlockingReadDefaultEventFlagBits(int32_t count) {
    std::vector<uint16_t> data(count);
    bool result = mFmqSynchronized->readBlocking(
            &data[0],
            count);

    if (result == false) {
        ALOGE("Blocking read fails");
    }

    return Void();
}

Return<void> TestMsgQ::requestBlockingReadRepeat(int32_t count, int32_t numIter) {
    std::vector<uint16_t> data(count);
    for (int i = 0; i < numIter; i++) {
        bool result = mFmqSynchronized->readBlocking(
                &data[0],
                count,
                static_cast<uint32_t>(ITestMsgQ::EventFlagBits::FMQ_NOT_FULL),
                static_cast<uint32_t>(ITestMsgQ::EventFlagBits::FMQ_NOT_EMPTY),
                5000000000 /* timeOutNanos */);

        if (result == false) {
            ALOGE("Blocking read fails");
            break;
        }
    }
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

ITestMsgQ* HIDL_FETCH_ITestMsgQ(const char* /* name */) {
    return new TestMsgQ();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace msgq
}  // namespace tests
}  // namespace hardware
}  // namespace android
