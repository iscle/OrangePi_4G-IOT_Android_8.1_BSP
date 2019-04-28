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

#include "Callbacks.h"
#include <android-base/logging.h>

namespace android {
namespace hardware {
namespace neuralnetworks {
namespace V1_0 {
namespace implementation {

CallbackBase::CallbackBase() : mNotified(false) {}

CallbackBase::~CallbackBase() {
    // Note that we cannot call CallbackBase::join_thread from here:
    // CallbackBase is intended to be reference counted, and it is possible that
    // the reference count drops to zero in the bound thread, causing the
    // bound thread to call this destructor. If a thread tries to join
    // itself, it throws an exception, producing a message like the
    // following:
    //
    //     terminating with uncaught exception of type std::__1::system_error:
    //     thread::join failed: Resource deadlock would occur
}

void CallbackBase::wait() {
    std::unique_lock<std::mutex> lock(mMutex);
    mCondition.wait(lock, [this]{return mNotified;});
    join_thread_locked();
}

bool CallbackBase::on_finish(std::function<bool(void)> post_work) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mPostWork != nullptr) {
        LOG(ERROR) << "CallbackBase::on_finish -- a post-work function has already been bound to "
                   "this callback object";
        return false;
    }
    if (post_work == nullptr) {
        LOG(ERROR) << "CallbackBase::on_finish -- the new post-work function is invalid";
        return false;
    }
    mPostWork = std::move(post_work);
    return true;
}

bool CallbackBase::bind_thread(std::thread&& asyncThread) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mThread.joinable()) {
        LOG(ERROR) << "CallbackBase::bind_thread -- a thread has already been bound to this "
                   "callback object";
        return false;
    }
    if (!asyncThread.joinable()) {
        LOG(ERROR) << "CallbackBase::bind_thread -- the new thread is not joinable";
        return false;
    }
    mThread = std::move(asyncThread);
    return true;
}

void CallbackBase::join_thread() {
    std::lock_guard<std::mutex> lock(mMutex);
    join_thread_locked();
}

void CallbackBase::notify() {
    {
        std::lock_guard<std::mutex> lock(mMutex);
        mNotified = true;
        if (mPostWork != nullptr) {
            bool success = mPostWork();
            if (!success) {
                LOG(ERROR) << "CallbackBase::notify -- post work failed";
            }
        }
    }
    mCondition.notify_all();
}

void CallbackBase::join_thread_locked() {
    if (mThread.joinable()) {
        mThread.join();
    }
}

PreparedModelCallback::PreparedModelCallback() :
        mErrorStatus(ErrorStatus::GENERAL_FAILURE), mPreparedModel(nullptr) {}

PreparedModelCallback::~PreparedModelCallback() {}

Return<void> PreparedModelCallback::notify(ErrorStatus errorStatus,
                                           const sp<IPreparedModel>& preparedModel) {
    mErrorStatus = errorStatus;
    mPreparedModel = preparedModel;
    CallbackBase::notify();
    return Void();
}

ErrorStatus PreparedModelCallback::getStatus() {
    wait();
    return mErrorStatus;
}

sp<IPreparedModel> PreparedModelCallback::getPreparedModel() {
    wait();
    return mPreparedModel;
}

ExecutionCallback::ExecutionCallback() : mErrorStatus(ErrorStatus::GENERAL_FAILURE) {}

ExecutionCallback::~ExecutionCallback() {}

Return<void> ExecutionCallback::notify(ErrorStatus errorStatus) {
    mErrorStatus = errorStatus;
    CallbackBase::notify();
    return Void();
}

ErrorStatus ExecutionCallback::getStatus() {
    wait();
    return mErrorStatus;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace neuralnetworks
}  // namespace hardware
}  // namespace android
