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
#include <broadcastradio-vts-utils/call-barrier.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace vts {

using std::lock_guard;
using std::mutex;
using std::unique_lock;

void CallBarrier::call() {
    lock_guard<mutex> lk(mMut);
    mWasCalled = true;
    mCond.notify_all();
}

bool CallBarrier::waitForCall(std::chrono::milliseconds timeout) {
    unique_lock<mutex> lk(mMut);

    if (mWasCalled) return true;

    auto status = mCond.wait_for(lk, timeout);
    return status == std::cv_status::no_timeout;
}

}  // namespace vts
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
