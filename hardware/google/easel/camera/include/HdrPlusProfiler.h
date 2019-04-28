/*
 * Copyright 2017 The Android Open Source Project
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
#ifndef HDR_PLUS_PROFILER_H
#define HDR_PLUS_PROFILER_H

#ifndef ENABLE_HDRPLUS_PROFILER
#define ENABLE_HDRPLUS_PROFILER 0
#endif

#if !ENABLE_HDRPLUS_PROFILER

// If profiler is not enabled, make every macro a noop
#define DECLARE_PROFILER_TIMER(_var, _description)
#define START_PROFILER_TIMER(_var) do {} while(0)
#define END_PROFILER_TIMER(_var) do {} while(0)
#define SCOPE_PROFILER_TIMER(_description) do {} while(0)

#else

#include <string>

/*
 * Declare a profiler timer.
 *
 * _var is the variable that will be declared as a timer.
 * _description is the description for this timer. It will be used when logging the timer duration.
 */
#define DECLARE_PROFILER_TIMER(_var, _description) pbcamera::TimerLogger _var = {_description}

/*
 * Start a timer.
 *
 * _var is a timer declared with DECALRE_PROFILER_TIMER.
 */
#define START_PROFILER_TIMER(_var) ((_var).start())

/*
 * End a timer and log the duration since last start.
 *
 * _var is a timer declared with DECALRE_PROFILER_TIMER.
 */
#define END_PROFILER_TIMER(_var) ((_var).end())

/*
 * Declare a scope timer that starts now and ends when it goes out of scope.
 *
 * __description is the description for this timer. It will be used when logging the timer duration.
 */
#define SCOPE_PROFILER_TIMER(_description) pbcamera::ScopeTimerLogger _timer(_description)

namespace pbcamera {

#define TIMER_TAG "[PROFILE_TIMER]"

/**
 * TimerLogger provides a timer to log the duration between start() and end().
 */
class TimerLogger {
public:
    TimerLogger(const char *name) : mName(name), mInvalid(true) {};

    // Start the timer.
    void start() {
        mInvalid = (clock_gettime(kClockId, &mStartTime) != 0);
    }

    // End the timer and log the duration since last start.
    void end() {
        if (mInvalid) {
            ALOGE("%s <%s> start time is invalid.", TIMER_TAG, mName.c_str());
            return;
        }

        struct timespec endTime;
        mInvalid = (clock_gettime(kClockId, &endTime) != 0);
        if (mInvalid) {
            ALOGE("%s <%s> end time is invalid.", TIMER_TAG, mName.c_str());
            return;
        }

        int64_t startNs = static_cast<int64_t>(mStartTime.tv_sec) * kNsPerSec + mStartTime.tv_nsec;
        int64_t endNs = static_cast<int64_t>(endTime.tv_sec) * kNsPerSec + endTime.tv_nsec;
        ALOGI("%s <%s> took %f ms.", TIMER_TAG, mName.c_str(),
            static_cast<float>(endNs - startNs) / kNsPerMs);
    }

private:
    const static int64_t kNsPerSec = 1000000000;
    const static int64_t kNsPerMs = 1000000;
    const static clockid_t kClockId = CLOCK_BOOTTIME;

    std::string mName;
    struct timespec mStartTime;
    bool mInvalid;

};

/**
 * ScopeTimerLogger provides a timer to log the duration of the instance lifetime.
 */
class ScopeTimerLogger {
public:
    ScopeTimerLogger(const char *name) : mTimerLogger(name) { mTimerLogger.start(); };
    virtual ~ScopeTimerLogger() { mTimerLogger.end(); };
private:
    TimerLogger mTimerLogger;
};

} // namespace pbcamera

#endif // !ENABLE_HDRPLUS_PROFILER

#endif // HDR_PLUS_PROFILER_H
