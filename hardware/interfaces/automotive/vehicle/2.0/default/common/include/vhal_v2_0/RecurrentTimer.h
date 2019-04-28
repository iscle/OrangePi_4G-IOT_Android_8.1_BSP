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

#ifndef android_hardware_automotive_vehicle_V2_0_RecurrentTimer_H_
#define android_hardware_automotive_vehicle_V2_0_RecurrentTimer_H_

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <list>
#include <mutex>
#include <set>
#include <thread>
#include <unordered_map>
#include <vector>

/**
 * This class allows to specify multiple time intervals to receive
 * notifications. A single thread is used internally.
 */
class RecurrentTimer {
private:
    using Nanos = std::chrono::nanoseconds;
    using Clock = std::chrono::steady_clock;
    using TimePoint = std::chrono::time_point<Clock, Nanos>;
public:
    using Action = std::function<void(const std::vector<int32_t>& cookies)>;

    RecurrentTimer(const Action& action) : mAction(action) {
        mTimerThread = std::thread(&RecurrentTimer::loop, this, action);
    }

    virtual ~RecurrentTimer() {
        stop();
    }

    /**
     * Registers recurrent event for a given interval. Registred events are distinguished by
     * cookies thus calling this method multiple times with the same cookie will override the
     * interval provided before.
     */
    void registerRecurrentEvent(std::chrono::nanoseconds interval, int32_t cookie) {
        TimePoint now = Clock::now();
        // Align event time point among all intervals. Thus if we have two intervals 1ms and 2ms,
        // during every second wake-up both intervals will be triggered.
        TimePoint absoluteTime = now - Nanos(now.time_since_epoch().count() % interval.count());

        {
            std::lock_guard<std::mutex> g(mLock);
            mCookieToEventsMap[cookie] = { interval, cookie, absoluteTime };
        }
        mCond.notify_one();
    }

    void unregisterRecurrentEvent(int32_t cookie) {
        {
            std::lock_guard<std::mutex> g(mLock);
            mCookieToEventsMap.erase(cookie);
        }
        mCond.notify_one();
    }


private:

    struct RecurrentEvent {
        Nanos interval;
        int32_t cookie;
        TimePoint absoluteTime;  // Absolute time of the next event.

        void updateNextEventTime(TimePoint now) {
            // We want to move time to next event by adding some number of intervals (usually 1)
            // to previous absoluteTime.
            int intervalMultiplier = (now - absoluteTime) / interval;
            if (intervalMultiplier <= 0) intervalMultiplier = 1;
            absoluteTime += intervalMultiplier * interval;
        }
    };

    void loop(const Action& action) {
        static constexpr auto kInvalidTime = TimePoint(Nanos::max());

        std::vector<int32_t> cookies;

        while (!mStopRequested) {
            auto now = Clock::now();
            auto nextEventTime = kInvalidTime;
            cookies.clear();

            {
                std::unique_lock<std::mutex> g(mLock);

                for (auto&& it : mCookieToEventsMap) {
                    RecurrentEvent& event = it.second;
                    if (event.absoluteTime <= now) {
                        event.updateNextEventTime(now);
                        cookies.push_back(event.cookie);
                    }

                    if (nextEventTime > event.absoluteTime) {
                        nextEventTime = event.absoluteTime;
                    }
                }
            }

            if (cookies.size() != 0) {
                action(cookies);
            }

            std::unique_lock<std::mutex> g(mLock);
            mCond.wait_until(g, nextEventTime);  // nextEventTime can be nanoseconds::max()
        }
    }

    void stop() {
        mStopRequested = true;
        {
            std::lock_guard<std::mutex> g(mLock);
            mCookieToEventsMap.clear();
        }
        mCond.notify_one();
        if (mTimerThread.joinable()) {
            mTimerThread.join();
        }
    }
private:
    mutable std::mutex mLock;
    std::thread mTimerThread;
    std::condition_variable mCond;
    std::atomic_bool mStopRequested { false };
    Action mAction;
    std::unordered_map<int32_t, RecurrentEvent> mCookieToEventsMap;
};


#endif  // android_hardware_automotive_vehicle_V2_0_RecurrentTimer_H
