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

#ifndef ANDROID_HARDWARE_TESTS_MSGQ_V1_0_BENCHMARKMSGQ_H
#define ANDROID_HARDWARE_TESTS_MSGQ_V1_0_BENCHMARKMSGQ_H

#include <android/hardware/tests/msgq/1.0/IBenchmarkMsgQ.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <fmq/MessageQueue.h>

namespace android {
namespace hardware {
namespace tests {
namespace msgq {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::msgq::V1_0::IBenchmarkMsgQ;
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
using android::hardware::MQFlavor;

struct BenchmarkMsgQ : public IBenchmarkMsgQ {
    /*
     * The various packet sizes used are as follows.
     */
    enum PacketSizes {
        kPacketSize64 = 64,
        kPacketSize128 = 128,
        kPacketSize256 = 256,
        kPacketSize512 = 512,
        kPacketSize1024 = 1024
    };
    // Methods from ::android::hardware::tests::msgq::V1_0::IBenchmarkMsgQ follow.
    Return<void> configureClientInboxSyncReadWrite(configureClientInboxSyncReadWrite_cb _hidl_cb) override;
    Return<void> configureClientOutboxSyncReadWrite(configureClientOutboxSyncReadWrite_cb _hidl_cb) override;
    Return<bool> requestWrite(int32_t count) override;
    Return<bool> requestRead(int32_t count) override;
    Return<void> benchmarkPingPong(uint32_t numIter) override;
    Return<void> benchmarkServiceWriteClientRead(uint32_t numIter) override;
    Return<void> sendTimeData(const hidl_vec<int64_t>& timeData) override;

     /*
     * This method writes numIter packets into the mFmqOutbox queue
     * and notes the time before each write in the mTimeData array. It will
     * be used to calculate the average server to client write to read delay.
     */
    template <MQFlavor flavor>
    static void QueueWriter(android::hardware::MessageQueue<uint8_t, flavor>*
                     mFmqOutbox, int64_t* mTimeData, uint32_t numIter);
    /*
     * The method reads a packet from the inbox queue and writes the same
     * into the outbox queue. The client will calculate the average time taken
     * for each iteration which consists of two write and two read operations.
     */
    template <MQFlavor flavor>
    static void QueuePairReadWrite(
            android::hardware::MessageQueue<uint8_t, flavor>* mFmqInbox,
            android::hardware::MessageQueue<uint8_t, flavor>* mFmqOutbox,
            uint32_t numIter);

private:
    android::hardware::MessageQueue<uint8_t, kSynchronizedReadWrite>* mFmqInbox;
    android::hardware::MessageQueue<uint8_t, kSynchronizedReadWrite>* mFmqOutbox;
    int64_t* mTimeData;
};

extern "C" IBenchmarkMsgQ* HIDL_FETCH_IBenchmarkMsgQ(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace msgq
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_MSGQ_V1_0_BENCHMARKMSGQ_H
