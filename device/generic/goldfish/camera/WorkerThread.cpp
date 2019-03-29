/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "WorkerThread.h"

#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCamera_WorkerThread"
#include <cutils/log.h>

#include <algorithm>

namespace android {

WorkerThread::WorkerThread(const char* threadName,
                           EmulatedCameraDevice* cameraDevice,
                           EmulatedCamera* cameraHAL)
    : Thread(true),   // Callbacks may involve Java calls.
      mCameraDevice(cameraDevice),
      mCameraHAL(cameraHAL),
      mRunning(false),
      mThreadName(threadName) {
}

status_t WorkerThread::startThread(bool oneBurst) {
    ALOGV("Starting worker thread, oneBurst=%s", oneBurst ? "true" : "false");
    mOneBurst = oneBurst;
    {
        Mutex::Autolock lock(mRunningMutex);
        mRunning = true;
    }
    return run(mThreadName, ANDROID_PRIORITY_URGENT_DISPLAY, 0);
}

status_t WorkerThread::stopThread() {
    ALOGV("%s: Stopping worker thread...", __FUNCTION__);

    Mutex::Autolock lock(mRunningMutex);
    mRunning = false;
    mRunningCondition.signal();
    return NO_ERROR;
}

status_t WorkerThread::wakeThread() {
    ALOGV("%s: Waking emulated camera device's worker thread...", __FUNCTION__);

    mRunningCondition.signal();
    return NO_ERROR;
}

status_t WorkerThread::joinThread() {
    return join();
}

status_t WorkerThread::readyToRun()
{
    status_t res = onThreadStart();
    if (res != NO_ERROR) {
        return res;
    }
    return NO_ERROR;
}

bool WorkerThread::threadLoop() {
    if (inWorkerThread() && !mOneBurst) {
        /* Only return true if we're running. If mRunning has been set to false
         * we fall through to ensure that onThreadExit is called. */
        Mutex::Autolock lock(mRunningMutex);
        if (mRunning) {
            return true;
        }
    }
    onThreadExit();
    ALOGV("%s: Exiting thread, mOneBurst=%s",
          __FUNCTION__, mOneBurst ? "true" : "false");
    return false;
}

}  // namespace android

