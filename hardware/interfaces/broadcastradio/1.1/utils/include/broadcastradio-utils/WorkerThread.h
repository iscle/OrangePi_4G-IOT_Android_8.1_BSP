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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_WORKERTHREAD_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_WORKERTHREAD_H

#include <chrono>
#include <queue>
#include <thread>

namespace android {

class WorkerThread {
   public:
    WorkerThread();
    virtual ~WorkerThread();

    void schedule(std::function<void()> task, std::chrono::milliseconds delay);
    void cancelAll();

   private:
    struct Task {
        std::chrono::time_point<std::chrono::steady_clock> when;
        std::function<void()> what;
    };
    friend bool operator<(const Task& lhs, const Task& rhs);

    std::atomic<bool> mIsTerminating;
    std::mutex mMut;
    std::condition_variable mCond;
    std::thread mThread;
    std::priority_queue<Task> mTasks;

    void threadLoop();
};

}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_WORKERTHREAD_H
