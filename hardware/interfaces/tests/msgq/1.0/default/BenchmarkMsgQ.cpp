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

#include "BenchmarkMsgQ.h"
#include <iostream>
#include <thread>
#include <fmq/MessageQueue.h>

namespace android {
namespace hardware {
namespace tests {
namespace msgq {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::msgq::V1_0::IBenchmarkMsgQ follow.
Return<void> BenchmarkMsgQ::configureClientInboxSyncReadWrite(
        configureClientInboxSyncReadWrite_cb _hidl_cb) {
    static constexpr size_t kNumElementsInQueue = 16 * 1024;
    mFmqOutbox = new (std::nothrow) android::hardware::MessageQueue<uint8_t,
               kSynchronizedReadWrite>(kNumElementsInQueue);
    if (mFmqOutbox == nullptr) {
        _hidl_cb(false /* ret */, android::hardware::MQDescriptorSync<uint8_t>(
                std::vector<android::hardware::GrantorDescriptor>(),
                nullptr /* nhandle */, 0 /* size */));
    } else {
        _hidl_cb(true /* ret */, *mFmqOutbox->getDesc());
    }

    return Void();
}

Return<void> BenchmarkMsgQ::configureClientOutboxSyncReadWrite(
        configureClientOutboxSyncReadWrite_cb _hidl_cb) {
    static constexpr size_t kNumElementsInQueue = 16 * 1024;
    mFmqInbox = new (std::nothrow) android::hardware::MessageQueue<uint8_t,
              kSynchronizedReadWrite>(kNumElementsInQueue);
    if ((mFmqInbox == nullptr) || (mFmqInbox->isValid() == false)) {
        _hidl_cb(false /* ret */, android::hardware::MQDescriptorSync<uint8_t>(
                std::vector<android::hardware::GrantorDescriptor>(),
                nullptr /* nhandle */, 0 /* size */));
    } else {
        _hidl_cb(true /* ret */, *mFmqInbox->getDesc());
    }

    return Void();
}

Return<bool> BenchmarkMsgQ::requestWrite(int32_t count) {
    uint8_t* data = new (std::nothrow) uint8_t[count];
    for (int i = 0; i < count; i++) {
        data[i] = i;
    }
    bool result = mFmqOutbox->write(data, count);
    delete[] data;
    return result;
}

Return<bool> BenchmarkMsgQ::requestRead(int32_t count) {
    uint8_t* data = new (std::nothrow) uint8_t[count];
    bool result = mFmqInbox->read(data, count);
    delete[] data;
    return result;
}

Return<void> BenchmarkMsgQ::benchmarkPingPong(uint32_t numIter) {
    std::thread(QueuePairReadWrite<kSynchronizedReadWrite>, mFmqInbox,
                mFmqOutbox, numIter)
            .detach();
    return Void();
}

Return<void> BenchmarkMsgQ::benchmarkServiceWriteClientRead(uint32_t numIter) {
    if (mTimeData) delete[] mTimeData;
    mTimeData = new (std::nothrow) int64_t[numIter];
    std::thread(QueueWriter<kSynchronizedReadWrite>, mFmqOutbox,
                mTimeData, numIter).detach();
    return Void();
}

Return<void> BenchmarkMsgQ::sendTimeData(const hidl_vec<int64_t>& clientRcvTimeArray) {
    int64_t accumulatedTime = 0;

    for (uint32_t i = 0; i < clientRcvTimeArray.size(); i++) {
        std::chrono::time_point<std::chrono::high_resolution_clock>
                clientRcvTime((std::chrono::high_resolution_clock::duration(
                        clientRcvTimeArray[i])));
        std::chrono::time_point<std::chrono::high_resolution_clock>serverSendTime(
                (std::chrono::high_resolution_clock::duration(mTimeData[i])));
        accumulatedTime += static_cast<int64_t>(
                std::chrono::duration_cast<std::chrono::nanoseconds>(clientRcvTime -
                                                                     serverSendTime).count());
    }

    accumulatedTime /= clientRcvTimeArray.size();
    std::cout << "Average service to client write to read delay::"
         << accumulatedTime << "ns" << std::endl;
    return Void();
}

template <MQFlavor flavor>
void BenchmarkMsgQ::QueueWriter(android::hardware::MessageQueue<uint8_t, flavor>* mFmqOutbox,
                                int64_t* mTimeData,
                                uint32_t numIter) {
    uint8_t data[kPacketSize64];
    uint32_t numWrites = 0;

    while (numWrites < numIter) {
        do {
            mTimeData[numWrites] =
                    std::chrono::high_resolution_clock::now().time_since_epoch().count();
        } while (mFmqOutbox->write(data, kPacketSize64) == false);
        numWrites++;
    }
}

template <MQFlavor flavor>
void BenchmarkMsgQ::QueuePairReadWrite(
        android::hardware::MessageQueue<uint8_t, flavor>* mFmqInbox,
        android::hardware::MessageQueue<uint8_t, flavor>* mFmqOutbox,
        uint32_t numIter) {
    uint8_t data[kPacketSize64];
    uint32_t numRoundTrips = 0;

    while (numRoundTrips < numIter) {
        while (mFmqInbox->read(data, kPacketSize64) == false)
            ;
        while (mFmqOutbox->write(data, kPacketSize64) == false)
            ;
        numRoundTrips++;
    }
}

IBenchmarkMsgQ* HIDL_FETCH_IBenchmarkMsgQ(const char* /* name */) {
    return new BenchmarkMsgQ();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace msgq
}  // namespace tests
}  // namespace hardware
}  // namespace android
