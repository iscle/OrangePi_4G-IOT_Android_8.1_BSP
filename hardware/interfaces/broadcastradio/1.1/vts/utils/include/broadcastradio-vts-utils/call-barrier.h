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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_CALL_BARRIER
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_CALL_BARRIER

#include <chrono>
#include <thread>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace vts {

/**
 * A barrier for thread synchronization, where one should wait for another to
 * reach a specific point in execution.
 */
class CallBarrier {
   public:
    /**
     * Notify the other thread it may continue execution.
     *
     * This may be called before the other thread starts waiting on the barrier.
     */
    void call();

    /**
     * Wait for the other thread to reach call() execution point.
     *
     * @param timeout a maximum time to wait.
     * @returns {@code false} if timed out, {@code true} otherwise.
     */
    bool waitForCall(std::chrono::milliseconds timeout);

   private:
    bool mWasCalled = false;
    std::mutex mMut;
    std::condition_variable mCond;
};

}  // namespace vts
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_CALL_BARRIER
