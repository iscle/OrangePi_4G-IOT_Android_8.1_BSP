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

#include "HandlerThread.h"

namespace android {

HandlerThread::HandlerThread()
    : mShouldQuit(false) {

}

HandlerThread::~HandlerThread() {
    quit();
}

sp<Looper> HandlerThread::getLooper() {
    Mutex::Autolock autoLock(mLock);
    if (mLooper.get() == 0) {
        mLooperWait.wait(mLock);
    }
    return mLooper;
}

status_t HandlerThread::start(const char* name, int32_t priority, size_t stack) {
    return run(name, priority, stack);
}

void HandlerThread::quit() {
    if (!isRunning()) {
        return;
    }
    sp<Looper> looper = getLooper();
    mLock.lock();
    mShouldQuit = true;
    mLock.unlock();
    looper->wake();
    requestExitAndWait();
}

bool HandlerThread::threadLoop() {
    mLock.lock();
    mLooper = Looper::prepare(0);
    mLooperWait.broadcast();
    mLock.unlock();
    while (true) {
        do {
            Mutex::Autolock autoLock(mLock);
            if (mShouldQuit) {
                return false;
            }
        } while (false);
        mLooper->pollOnce(-1);
    }
    return false;
}


};
