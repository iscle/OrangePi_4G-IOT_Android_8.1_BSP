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

#ifndef ANDROID_HARDWARE_TESTS_MSGQ_V1_0_TESTMSGQ_H
#define ANDROID_HARDWARE_TESTS_MSGQ_V1_0_TESTMSGQ_H

#include <android/hardware/tests/msgq/1.0/ITestMsgQ.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <fmq/MessageQueue.h>
#include <fmq/EventFlag.h>

namespace android {
namespace hardware {
namespace tests {
namespace msgq {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::msgq::V1_0::ITestMsgQ;
using ::android::hidl::base::V1_0::DebugInfo;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

using android::hardware::kSynchronizedReadWrite;
using android::hardware::kUnsynchronizedWrite;
using android::hardware::MQDescriptorSync;
using android::hardware::MQDescriptorUnsync;

using android::hardware::MessageQueue;

struct TestMsgQ : public ITestMsgQ {
    typedef MessageQueue<uint16_t, kSynchronizedReadWrite> MessageQueueSync;
    typedef MessageQueue<uint16_t, kUnsynchronizedWrite> MessageQueueUnsync;

    TestMsgQ() : mFmqSynchronized(nullptr), mFmqUnsynchronized(nullptr) {}

    // Methods from ::android::hardware::tests::msgq::V1_0::ITestMsgQ follow.
    Return<void> configureFmqSyncReadWrite(configureFmqSyncReadWrite_cb _hidl_cb) override;
    Return<void> getFmqUnsyncWrite(bool configureFmq, getFmqUnsyncWrite_cb _hidl_cb) override;
    Return<bool> requestWriteFmqSync(int32_t count) override;
    Return<bool> requestReadFmqSync(int32_t count) override;
    Return<bool> requestWriteFmqUnsync(int32_t count) override;
    Return<bool> requestReadFmqUnsync(int32_t count) override;
    Return<void> requestBlockingRead(int32_t count) override;
    Return<void> requestBlockingReadDefaultEventFlagBits(int32_t count) override;
    Return<void> requestBlockingReadRepeat(int32_t count, int32_t numIter) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
private:
    std::unique_ptr<MessageQueueSync> mFmqSynchronized;
    std::unique_ptr<MessageQueueUnsync> mFmqUnsynchronized;

    /*
     * Utility function to verify data read from the fast message queue.
     */
    bool verifyData(uint16_t* data, int count) {
        for (int i = 0; i < count; i++) {
            if (data[i] != i) return false;
        }
        return true;
    }
};

extern "C" ITestMsgQ* HIDL_FETCH_ITestMsgQ(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace msgq
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_MSGQ_V1_0_TESTMSGQ_H
