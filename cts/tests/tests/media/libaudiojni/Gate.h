/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_GATE_H
#define ANDROID_GATE_H

#include <stdint.h>
#include <mutex>

namespace android {

// Gate is a synchronization object.
//
// Threads will pass if it is open.
// Threads will block (wait) if it is closed.
//
// When a gate is opened, all waiting threads will pass through.
//
// Since gate holds no external locks, consistency with external
// state needs to be handled elsewhere.
//
// We use mWaitCount to indicate the number of threads that have
// arrived at the gate via wait().  Each thread entering
// wait obtains a unique waitId (which is the current mWaitCount).
// This can be viewed as a sequence number.
//
// We use mPassCount to indicate the number of threads that have
// passed the gate.  If the waitId is less than or equal to the mPassCount
// then that thread has passed the gate.  An open gate sets mPassedCount
// to the current mWaitCount, allowing all prior threads to pass.
//
// See sync_timeline, sync_pt, etc. for graphics.

class Gate {
public:
    Gate(bool open = false) :
        mOpen(open),
        mExit(false),
        mWaitCount(0),
        mPassCount(0)
    { }

    // waits for the gate to open, returns immediately if gate is already open.
    //
    // Do not hold a monitor lock while calling this.
    //
    // returns true if we passed the gate normally
    //         false if gate is terminated and we didn't pass the gate.
    bool wait() {
        std::unique_lock<std::mutex> l(mLock);
        size_t waitId = ++mWaitCount;
        if (mOpen) {
            mPassCount = waitId; // let me through
        }
        while (!passedGate_l(waitId) && !mExit) {
            mCondition.wait(l);
        }
        return passedGate_l(waitId);
    }

    // close the gate.
    void closeGate() {
        std::lock_guard<std::mutex> l(mLock);
        mOpen = false;
        mExit = false;
    }

    // open the gate.
    // signal to all waiters it is okay to go.
    void openGate() {
        std::lock_guard<std::mutex> l(mLock);
        mOpen = true;
        mExit = false;
        if (waiters_l() > 0) {
            mPassCount = mWaitCount;  // allow waiting threads to go through
            // unoptimized pthreads will wake thread to find we still hold lock.
            mCondition.notify_all();
        }
    }

    // terminate (term has expired).
    // all threads allowed to pass regardless of whether the gate is open or closed.
    void terminate() {
        std::lock_guard<std::mutex> l(mLock);
        mExit = true;
        if (waiters_l() > 0) {
            // unoptimized pthreads will wake thread to find we still hold lock.
            mCondition.notify_all();
        }
    }

    bool isOpen() {
        std::lock_guard<std::mutex> l(mLock);
        return mOpen;
    }

    // return how many waiters are at the gate.
    size_t waiters() {
        std::lock_guard<std::mutex> l(mLock);
        return waiters_l();
    }

private:
    bool                    mOpen;
    bool                    mExit;
    size_t                  mWaitCount;  // total number of threads that have called wait()
    size_t                  mPassCount;  // total number of threads passed the gate.
    std::condition_variable mCondition;
    std::mutex              mLock;

    // return how many waiters are at the gate.
    inline size_t waiters_l() {
        return mWaitCount - mPassCount;
    }

    // return whether the waitId (from mWaitCount) has passed through the gate
    inline bool passedGate_l(size_t waitId) {
        return (ssize_t)(waitId - mPassCount) <= 0;
    }
};

} // namespace android

#endif // ANDROID_GATE_H
