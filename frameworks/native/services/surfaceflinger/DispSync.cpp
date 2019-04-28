/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
//#define LOG_NDEBUG 0

// This is needed for stdint.h to define INT64_MAX in C++
#define __STDC_LIMIT_MACROS

#include <math.h>

#include <algorithm>

#include <log/log.h>
#include <utils/String8.h>
#include <utils/Thread.h>
#include <utils/Trace.h>
#include <utils/Vector.h>

#include <ui/FenceTime.h>

#include "DispSync.h"
#include "SurfaceFlinger.h"
#include "EventLog/EventLog.h"
#ifdef MTK_GPU_DVFS_SUPPORT
#include <dlfcn.h>
#include "vsync_hint/VsyncHintApi.h"
#endif

using std::max;
using std::min;
#ifdef MTK_SF_DEBUG_SUPPORT
#define DS_ATRACE_NAME(name) android::ScopedTrace ___tracer(ATRACE_TAG, name)

#define DS_ATRACE_BUFFER(x, ...)                                                \
    if (ATRACE_ENABLED()) {                                                     \
        char ___traceBuf[256];                                                  \
        snprintf(___traceBuf, sizeof(___traceBuf), x, ##__VA_ARGS__);           \
        android::ScopedTrace ___bufTracer(ATRACE_TAG, ___traceBuf);             \
    }
#endif

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
#include "vsync_enhance/DispSyncEnhancementApi.h"
#endif

namespace android {

// Setting this to true enables verbose tracing that can be used to debug
// vsync event model or phase issues.
static const bool kTraceDetailedInfo = false;

// Setting this to true adds a zero-phase tracer for correlating with hardware
// vsync events
static const bool kEnableZeroPhaseTracer = false;

// This is the threshold used to determine when hardware vsync events are
// needed to re-synchronize the software vsync model with the hardware.  The
// error metric used is the mean of the squared difference between each
// present time and the nearest software-predicted vsync.
static const nsecs_t kErrorThreshold = 160000000000;    // 400 usec squared

#undef LOG_TAG
#define LOG_TAG "DispSyncThread"
class DispSyncThread: public Thread {
public:

    explicit DispSyncThread(const char* name):
            mName(name),
            mStop(false),
            mPeriod(0),
            mPhase(0),
            mReferenceTime(0),
            mWakeupLatency(0),
#ifndef MTK_GPU_DVFS_SUPPORT
            mFrameNumber(0) {}

    virtual ~DispSyncThread() {}
#else
            mFrameNumber(0) {
        initVsyncHint();
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        initVsyncEnhance();
#endif
    }

    virtual ~DispSyncThread() {
        deinitVsyncHint();
    }
#endif

    void updateModel(nsecs_t period, nsecs_t phase, nsecs_t referenceTime) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        Mutex::Autolock lock(mMutex);
        mPeriod = period;
        mPhase = phase;
        mReferenceTime = referenceTime;
        ALOGV("[%s] updateModel: mPeriod = %" PRId64 ", mPhase = %" PRId64
                " mReferenceTime = %" PRId64, mName, ns2us(mPeriod),
                ns2us(mPhase), ns2us(mReferenceTime));
        mCond.signal();
    }

    void stop() {
        if (kTraceDetailedInfo) ATRACE_CALL();
        Mutex::Autolock lock(mMutex);
        mStop = true;
        mCond.signal();
    }

    virtual bool threadLoop() {
        status_t err;
        nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        int32_t vsyncMode = VSYNC_MODE_CALIBRATED_SW_VSYNC;
#endif

        while (true) {
#ifdef MTK_SF_DEBUG_SUPPORT
            DS_ATRACE_NAME("send_sw_vsync");
#endif
            Vector<CallbackInvocation> callbackInvocations;

            nsecs_t targetTime = 0;

            { // Scope for lock
                Mutex::Autolock lock(mMutex);

                if (kTraceDetailedInfo) {
                    ATRACE_INT64("DispSync:Frame", mFrameNumber);
                }
                ALOGV("[%s] Frame %" PRId64, mName, mFrameNumber);
                ++mFrameNumber;

                if (mStop) {
                    return false;
                }

                if (mPeriod == 0) {
                    err = mCond.wait(mMutex);
                    if (err != NO_ERROR) {
                        ALOGE("error waiting for new events: %s (%d)",
                                strerror(-err), err);
                        return false;
                    }
                    continue;
                }

                targetTime = computeNextEventTimeLocked(now);

                bool isWakeup = false;

                if (now < targetTime) {
#ifdef MTK_SF_DEBUG_SUPPORT
                    DS_ATRACE_BUFFER("sleep:%" PRId64, targetTime - now);
#endif
                    if (kTraceDetailedInfo) ATRACE_NAME("DispSync waiting");

                    if (targetTime == INT64_MAX) {
                        ALOGV("[%s] Waiting forever", mName);
                        err = mCond.wait(mMutex);
                    } else {
                        ALOGV("[%s] Waiting until %" PRId64, mName,
                                ns2us(targetTime));
                        err = mCond.waitRelative(mMutex, targetTime - now);
                    }

                    if (err == TIMED_OUT) {
                        isWakeup = true;
                    } else if (err != NO_ERROR) {
                        ALOGE("error waiting for next event: %s (%d)",
                                strerror(-err), err);
                        return false;
                    }
                }

                now = systemTime(SYSTEM_TIME_MONOTONIC);

                // Don't correct by more than 1.5 ms
                static const nsecs_t kMaxWakeupLatency = us2ns(1500);

                if (isWakeup) {
                    mWakeupLatency = ((mWakeupLatency * 63) +
                            (now - targetTime)) / 64;
                    mWakeupLatency = min(mWakeupLatency, kMaxWakeupLatency);
#ifdef MTK_SF_DEBUG_SUPPORT
                    ATRACE_INT64("DispSync:WakeupLat", now - targetTime);
                    ATRACE_INT64("DispSync:AvgWakeupLat", mWakeupLatency);
#else
                    if (kTraceDetailedInfo) {
                        ATRACE_INT64("DispSync:WakeupLat", now - targetTime);
                        ATRACE_INT64("DispSync:AvgWakeupLat", mWakeupLatency);
                    }
#endif
                }

                callbackInvocations = gatherCallbackInvocationsLocked(now);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
                vsyncMode = mVSyncMode;
#endif
            }

            if (callbackInvocations.size() > 0) {
                fireCallbackInvocations(callbackInvocations);
            }
        }

        return false;
    }

    status_t addEventListener(const char* name, nsecs_t phase,
            const sp<DispSync::Callback>& callback) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        Mutex::Autolock lock(mMutex);

        for (size_t i = 0; i < mEventListeners.size(); i++) {
            if (mEventListeners[i].mCallback == callback) {
                return BAD_VALUE;
            }
        }

        EventListener listener;
        listener.mName = name;
        listener.mPhase = phase;
        listener.mCallback = callback;
#ifdef MTK_GPU_DVFS_SUPPORT
        fillListenerType(&listener);
#endif

        // We want to allow the firstmost future event to fire without
        // allowing any past events to fire
        listener.mLastEventTime = systemTime() - mPeriod / 2 + mPhase -
                mWakeupLatency;

        mEventListeners.push(listener);

        mCond.signal();

        return NO_ERROR;
    }

    status_t removeEventListener(const sp<DispSync::Callback>& callback) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        Mutex::Autolock lock(mMutex);

        for (size_t i = 0; i < mEventListeners.size(); i++) {
            if (mEventListeners[i].mCallback == callback) {
                mEventListeners.removeAt(i);
                mCond.signal();
                return NO_ERROR;
            }
        }

        return BAD_VALUE;
    }

    // This method is only here to handle the !SurfaceFlinger::hasSyncFramework
    // case.
    bool hasAnyEventListeners() {
        if (kTraceDetailedInfo) ATRACE_CALL();
        Mutex::Autolock lock(mMutex);
        return !mEventListeners.empty();
    }

private:

    struct EventListener {
        const char* mName;
        nsecs_t mPhase;
        nsecs_t mLastEventTime;
        sp<DispSync::Callback> mCallback;
#ifdef MTK_GPU_DVFS_SUPPORT
        int mVsyncType;
#endif
    };

    struct CallbackInvocation {
        sp<DispSync::Callback> mCallback;
        nsecs_t mEventTime;
#ifdef MTK_GPU_DVFS_SUPPORT
        int mVsyncType;
#endif
    };

    nsecs_t computeNextEventTimeLocked(nsecs_t now) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        ALOGV("[%s] computeNextEventTimeLocked", mName);
        nsecs_t nextEventTime = INT64_MAX;
        for (size_t i = 0; i < mEventListeners.size(); i++) {
            nsecs_t t = computeListenerNextEventTimeLocked(mEventListeners[i],
                    now);

            if (t < nextEventTime) {
                nextEventTime = t;
            }
        }
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        if (mVSyncMode == VSYNC_MODE_PASS_HW_VSYNC) {
            nextEventTime = INT64_MAX;
        }
#endif

        ALOGV("[%s] nextEventTime = %" PRId64, mName, ns2us(nextEventTime));
        return nextEventTime;
    }

    Vector<CallbackInvocation> gatherCallbackInvocationsLocked(nsecs_t now) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        ALOGV("[%s] gatherCallbackInvocationsLocked @ %" PRId64, mName,
                ns2us(now));

        Vector<CallbackInvocation> callbackInvocations;
        nsecs_t onePeriodAgo = now - mPeriod;

        for (size_t i = 0; i < mEventListeners.size(); i++) {
            nsecs_t t = computeListenerNextEventTimeLocked(mEventListeners[i],
                    onePeriodAgo);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
            if (mVSyncMode == VSYNC_MODE_PASS_HW_VSYNC) {
                if (mWakeReason == WAKE_REASON_VSYNC) {
                    CallbackInvocation ci;
                    ci.mCallback = mEventListeners[i].mCallback;
                    ci.mEventTime = now;
                    callbackInvocations.push(ci);
                    mEventListeners.editItemAt(i).mLastEventTime = now;
                }
            } else
#endif
            if (t < now) {
                CallbackInvocation ci;
                ci.mCallback = mEventListeners[i].mCallback;
                ci.mEventTime = t;
#ifdef MTK_GPU_DVFS_SUPPORT
                fillCallbackType(&ci, mEventListeners[i]);
#endif
                ALOGV("[%s] [%s] Preparing to fire", mName,
                        mEventListeners[i].mName);
                callbackInvocations.push(ci);
                mEventListeners.editItemAt(i).mLastEventTime = t;
            }
        }
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        if (mWakeReason) {
            mWakeReason = WAKE_REASON_NONE;
        }
#endif

        return callbackInvocations;
    }

    nsecs_t computeListenerNextEventTimeLocked(const EventListener& listener,
            nsecs_t baseTime) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        ALOGV("[%s] [%s] computeListenerNextEventTimeLocked(%" PRId64 ")",
                mName, listener.mName, ns2us(baseTime));

        nsecs_t lastEventTime = listener.mLastEventTime + mWakeupLatency;
        ALOGV("[%s] lastEventTime: %" PRId64, mName, ns2us(lastEventTime));
        if (baseTime < lastEventTime) {
            baseTime = lastEventTime;
            ALOGV("[%s] Clamping baseTime to lastEventTime -> %" PRId64, mName,
                    ns2us(baseTime));
        }

        baseTime -= mReferenceTime;
        ALOGV("[%s] Relative baseTime = %" PRId64, mName, ns2us(baseTime));
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        nsecs_t phase = 0;
        if (mVSyncMode == VSYNC_MODE_CALIBRATED_SW_VSYNC) {
            phase = mPhase + listener.mPhase;
        } else {
            phase = mPhase;
        }
#else
        nsecs_t phase = mPhase + listener.mPhase;
#endif
        ALOGV("[%s] Phase = %" PRId64, mName, ns2us(phase));
        baseTime -= phase;
        ALOGV("[%s] baseTime - phase = %" PRId64, mName, ns2us(baseTime));

        // If our previous time is before the reference (because the reference
        // has since been updated), the division by mPeriod will truncate
        // towards zero instead of computing the floor. Since in all cases
        // before the reference we want the next time to be effectively now, we
        // set baseTime to -mPeriod so that numPeriods will be -1.
        // When we add 1 and the phase, we will be at the correct event time for
        // this period.
        if (baseTime < 0) {
            ALOGV("[%s] Correcting negative baseTime", mName);
            baseTime = -mPeriod;
        }

        nsecs_t numPeriods = baseTime / mPeriod;
        ALOGV("[%s] numPeriods = %" PRId64, mName, numPeriods);
        nsecs_t t = (numPeriods + 1) * mPeriod + phase;
        ALOGV("[%s] t = %" PRId64, mName, ns2us(t));
        t += mReferenceTime;
        ALOGV("[%s] Absolute t = %" PRId64, mName, ns2us(t));

        // Check that it's been slightly more than half a period since the last
        // event so that we don't accidentally fall into double-rate vsyncs
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
        if (t - listener.mLastEventTime < (mPeriod / 2) - mPhase) {
#else
        if (t - listener.mLastEventTime < (3 * mPeriod / 5)) {
#endif
            t += mPeriod;
            ALOGV("[%s] Modifying t -> %" PRId64, mName, ns2us(t));
        }

        t -= mWakeupLatency;
        ALOGV("[%s] Corrected for wakeup latency -> %" PRId64, mName, ns2us(t));

        return t;
    }

    void fireCallbackInvocations(const Vector<CallbackInvocation>& callbacks) {
        if (kTraceDetailedInfo) ATRACE_CALL();
        for (size_t i = 0; i < callbacks.size(); i++) {
            callbacks[i].mCallback->onDispSyncEvent(callbacks[i].mEventTime);
            fireVsyncHint(callbacks[i]);
        }
    }

    const char* const mName;

    bool mStop;

    nsecs_t mPeriod;
    nsecs_t mPhase;
    nsecs_t mReferenceTime;
    nsecs_t mWakeupLatency;

    int64_t mFrameNumber;

    Vector<EventListener> mEventListeners;

    Mutex mMutex;
    Condition mCond;
#ifdef MTK_GPU_DVFS_SUPPORT
    void* mVsyncHintHandle;
    VsyncHintApi* mVsyncHint;

    // initial VsyncHint object
    void initVsyncHint();

    // deinit VsyncHint object
    void deinitVsyncHint();

    // base on the listener name to fill listener type
    void fillListenerType(EventListener* listener);

    // copy the listener type to CallbackInvocation
    void fillCallbackType(CallbackInvocation* callback, const EventListener& listener);

    // when DispSyncThread fire callback, also send the VSync event to other module
    void fireVsyncHint(const CallbackInvocation& ci);
#endif
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
public:
    // used to change the period and mode of VSync
    status_t setVSyncMode(int32_t mode, int32_t fps, nsecs_t period, nsecs_t phase, nsecs_t referenceTime);

    // store the vsync parameter when VSync mode is HW mode
    void addResyncSample(nsecs_t period, nsecs_t phase, nsecs_t referenceTime);
private:
    // initial VsyncEnhance parameter
    void initVsyncEnhance();

    int32_t mVSyncMode;
    int32_t mFps;
    int32_t mWakeReason;
#endif
};

#undef LOG_TAG
#define LOG_TAG "DispSync"

class ZeroPhaseTracer : public DispSync::Callback {
public:
    ZeroPhaseTracer() : mParity(false) {}

    virtual void onDispSyncEvent(nsecs_t /*when*/) {
        mParity = !mParity;
        ATRACE_INT("ZERO_PHASE_VSYNC", mParity ? 1 : 0);
    }

private:
    bool mParity;
};

DispSync::DispSync(const char* name) :
        mName(name),
        mRefreshSkipCount(0),
        mThread(new DispSyncThread(name)) {
}

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
DispSync::~DispSync() {
    deinitDispVsyncEnhancement();
}
#else
DispSync::~DispSync() {}
#endif

void DispSync::init(bool hasSyncFramework, int64_t dispSyncPresentTimeOffset) {
    mIgnorePresentFences = !hasSyncFramework;
    mPresentTimeOffset = dispSyncPresentTimeOffset;
    mThread->run("DispSync", PRIORITY_URGENT_DISPLAY + PRIORITY_MORE_FAVORABLE);

    // set DispSync to SCHED_FIFO to minimize jitter
    struct sched_param param = {0};
    param.sched_priority = 2;
    if (sched_setscheduler(mThread->getTid(), SCHED_FIFO, &param) != 0) {
        ALOGE("Couldn't set SCHED_FIFO for DispSyncThread");
    }

    reset();
    beginResync();

    if (kTraceDetailedInfo) {
        // If we're not getting present fences then the ZeroPhaseTracer
        // would prevent HW vsync event from ever being turned off.
        // Even if we're just ignoring the fences, the zero-phase tracing is
        // not needed because any time there is an event registered we will
        // turn on the HW vsync events.
        if (!mIgnorePresentFences && kEnableZeroPhaseTracer) {
            addEventListener("ZeroPhaseTracer", 0, new ZeroPhaseTracer());
        }
    }
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    initDispVsyncEnhancement();
#endif
}

void DispSync::reset() {
    Mutex::Autolock lock(mMutex);

    mPhase = 0;
    mReferenceTime = 0;
    mModelUpdated = false;
    mNumResyncSamples = 0;
    mFirstResyncSample = 0;
    mNumResyncSamplesSincePresent = 0;
    resetErrorLocked();
}

bool DispSync::addPresentFence(const std::shared_ptr<FenceTime>& fenceTime) {
    Mutex::Autolock lock(mMutex);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
   bool res = false;
   if (addPresentFenceEnhancementLocked(&res)) {
       return res;
   }
#endif

    mPresentFences[mPresentSampleOffset] = fenceTime;
    mPresentSampleOffset = (mPresentSampleOffset + 1) % NUM_PRESENT_SAMPLES;
    mNumResyncSamplesSincePresent = 0;

    updateErrorLocked();

    return !mModelUpdated || mError > kErrorThreshold;
}

void DispSync::beginResync() {
    Mutex::Autolock lock(mMutex);
    ALOGV("[%s] beginResync", mName);
    mModelUpdated = false;
    mNumResyncSamples = 0;
}

bool DispSync::addResyncSample(nsecs_t timestamp) {
    Mutex::Autolock lock(mMutex);

    ALOGV("[%s] addResyncSample(%" PRId64 ")", mName, ns2us(timestamp));
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    bool res = false;
    if (addResyncSampleEnhancementLocked(&res, timestamp)) {
        return res;
    }
#endif

    size_t idx = (mFirstResyncSample + mNumResyncSamples) % MAX_RESYNC_SAMPLES;
    mResyncSamples[idx] = timestamp;
    if (mNumResyncSamples == 0) {
        mPhase = 0;
        mReferenceTime = timestamp;
        ALOGV("[%s] First resync sample: mPeriod = %" PRId64 ", mPhase = 0, "
                "mReferenceTime = %" PRId64, mName, ns2us(mPeriod),
                ns2us(mReferenceTime));
        mThread->updateModel(mPeriod, mPhase, mReferenceTime);
    }

    if (mNumResyncSamples < MAX_RESYNC_SAMPLES) {
        mNumResyncSamples++;
    } else {
        mFirstResyncSample = (mFirstResyncSample + 1) % MAX_RESYNC_SAMPLES;
    }

    updateModelLocked();

    if (mNumResyncSamplesSincePresent++ > MAX_RESYNC_SAMPLES_WITHOUT_PRESENT) {
        resetErrorLocked();
    }

    if (mIgnorePresentFences) {
        // If we don't have the sync framework we will never have
        // addPresentFence called.  This means we have no way to know whether
        // or not we're synchronized with the HW vsyncs, so we just request
        // that the HW vsync events be turned on whenever we need to generate
        // SW vsync events.
        return mThread->hasAnyEventListeners();
    }

    // Check against kErrorThreshold / 2 to add some hysteresis before having to
    // resync again
    bool modelLocked = mModelUpdated && mError < (kErrorThreshold / 2);
    ALOGV("[%s] addResyncSample returning %s", mName,
            modelLocked ? "locked" : "unlocked");
    return !modelLocked;
}

void DispSync::endResync() {
}

status_t DispSync::addEventListener(const char* name, nsecs_t phase,
        const sp<Callback>& callback) {
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    status_t res = NO_ERROR;
    if (addEventListenerEnhancement(&res, name, phase, callback)) {
        return res;
    }
#endif
    Mutex::Autolock lock(mMutex);
    return mThread->addEventListener(name, phase, callback);
}

void DispSync::setRefreshSkipCount(int count) {
    Mutex::Autolock lock(mMutex);
    ALOGD("setRefreshSkipCount(%d)", count);
    mRefreshSkipCount = count;
    updateModelLocked();
}

status_t DispSync::removeEventListener(const sp<Callback>& callback) {
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    status_t res = NO_ERROR;
    if (removeEventListenerEnhancement(&res, callback)) {
        return res;
    }
#endif
    Mutex::Autolock lock(mMutex);
    return mThread->removeEventListener(callback);
}

void DispSync::setPeriod(nsecs_t period) {
    Mutex::Autolock lock(mMutex);
    mPeriod = period;
    mPhase = 0;
    mReferenceTime = 0;
    mThread->updateModel(mPeriod, mPhase, mReferenceTime);
}

nsecs_t DispSync::getPeriod() {
    // lock mutex as mPeriod changes multiple times in updateModelLocked
    Mutex::Autolock lock(mMutex);
    return mPeriod;
}

void DispSync::updateModelLocked() {
    ALOGV("[%s] updateModelLocked %zu", mName, mNumResyncSamples);
    if (mNumResyncSamples >= MIN_RESYNC_SAMPLES_FOR_UPDATE) {
#ifdef MTK_SF_DEBUG_SUPPORT
        ATRACE_INT("VSYNC_CALIBRATION", 1);
#endif
        ALOGV("[%s] Computing...", mName);
        nsecs_t durationSum = 0;
        nsecs_t minDuration = INT64_MAX;
        nsecs_t maxDuration = 0;
        for (size_t i = 1; i < mNumResyncSamples; i++) {
            size_t idx = (mFirstResyncSample + i) % MAX_RESYNC_SAMPLES;
            size_t prev = (idx + MAX_RESYNC_SAMPLES - 1) % MAX_RESYNC_SAMPLES;
            nsecs_t duration = mResyncSamples[idx] - mResyncSamples[prev];
            durationSum += duration;
            minDuration = min(minDuration, duration);
            maxDuration = max(maxDuration, duration);
        }

        // Exclude the min and max from the average
        durationSum -= minDuration + maxDuration;
        mPeriod = durationSum / (mNumResyncSamples - 3);

        ALOGV("[%s] mPeriod = %" PRId64, mName, ns2us(mPeriod));

        double sampleAvgX = 0;
        double sampleAvgY = 0;
        double scale = 2.0 * M_PI / double(mPeriod);
        // Intentionally skip the first sample
        for (size_t i = 1; i < mNumResyncSamples; i++) {
            size_t idx = (mFirstResyncSample + i) % MAX_RESYNC_SAMPLES;
            nsecs_t sample = mResyncSamples[idx] - mReferenceTime;
            double samplePhase = double(sample % mPeriod) * scale;
            sampleAvgX += cos(samplePhase);
            sampleAvgY += sin(samplePhase);
        }

        sampleAvgX /= double(mNumResyncSamples - 1);
        sampleAvgY /= double(mNumResyncSamples - 1);

        mPhase = nsecs_t(atan2(sampleAvgY, sampleAvgX) / scale);

        ALOGV("[%s] mPhase = %" PRId64, mName, ns2us(mPhase));

        if (mPhase < -(mPeriod / 2)) {
            mPhase += mPeriod;
            ALOGV("[%s] Adjusting mPhase -> %" PRId64, mName, ns2us(mPhase));
        }

        if (kTraceDetailedInfo) {
            ATRACE_INT64("DispSync:Period", mPeriod);
            ATRACE_INT64("DispSync:Phase", mPhase + mPeriod / 2);
        }

        // Artificially inflate the period if requested.
        mPeriod += mPeriod * mRefreshSkipCount;

        mThread->updateModel(mPeriod, mPhase, mReferenceTime);
        mModelUpdated = true;
    }
#ifdef MTK_SF_DEBUG_SUPPORT
    else {
        ATRACE_INT("VSYNC_CALIBRATION", 0);
    }
#endif
}

void DispSync::updateErrorLocked() {
    if (!mModelUpdated) {
        return;
    }

    // Need to compare present fences against the un-adjusted refresh period,
    // since they might arrive between two events.
    nsecs_t period = mPeriod / (1 + mRefreshSkipCount);

    int numErrSamples = 0;
    nsecs_t sqErrSum = 0;

    for (size_t i = 0; i < NUM_PRESENT_SAMPLES; i++) {
        // Only check for the cached value of signal time to avoid unecessary
        // syscalls. It is the responsibility of the DispSync owner to
        // call getSignalTime() periodically so the cache is updated when the
        // fence signals.
        nsecs_t time = mPresentFences[i]->getCachedSignalTime();
        if (time == Fence::SIGNAL_TIME_PENDING ||
                time == Fence::SIGNAL_TIME_INVALID) {
            continue;
        }

        nsecs_t sample = time - mReferenceTime;
        if (sample <= mPhase) {
            continue;
        }

        nsecs_t sampleErr = (sample - mPhase) % period;
        if (sampleErr > period / 2) {
            sampleErr -= period;
        }
        sqErrSum += sampleErr * sampleErr;
        numErrSamples++;
    }

    if (numErrSamples > 0) {
        mError = sqErrSum / numErrSamples;
        mZeroErrSamplesCount = 0;
    } else {
        mError = 0;
        // Use mod ACCEPTABLE_ZERO_ERR_SAMPLES_COUNT to avoid log spam.
        mZeroErrSamplesCount++;
        ALOGE_IF(
                (mZeroErrSamplesCount % ACCEPTABLE_ZERO_ERR_SAMPLES_COUNT) == 0,
                "No present times for model error.");
    }

    if (kTraceDetailedInfo) {
        ATRACE_INT64("DispSync:Error", mError);
    }
}

void DispSync::resetErrorLocked() {
    mPresentSampleOffset = 0;
    mError = 0;
    mZeroErrSamplesCount = 0;
    for (size_t i = 0; i < NUM_PRESENT_SAMPLES; i++) {
        mPresentFences[i] = FenceTime::NO_FENCE;
    }
}

nsecs_t DispSync::computeNextRefresh(int periodOffset) const {
    Mutex::Autolock lock(mMutex);
    nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    nsecs_t phase = mReferenceTime + mPhase;
    return (((now - phase) / mPeriod) + periodOffset + 1) * mPeriod + phase;
}

void DispSync::dump(String8& result) const {
    Mutex::Autolock lock(mMutex);
    result.appendFormat("present fences are %s\n",
            mIgnorePresentFences ? "ignored" : "used");
    result.appendFormat("mPeriod: %" PRId64 " ns (%.3f fps; skipCount=%d)\n",
            mPeriod, 1000000000.0 / mPeriod, mRefreshSkipCount);
    result.appendFormat("mPhase: %" PRId64 " ns\n", mPhase);
    result.appendFormat("mError: %" PRId64 " ns (sqrt=%.1f)\n",
            mError, sqrt(mError));
    result.appendFormat("mNumResyncSamplesSincePresent: %d (limit %d)\n",
            mNumResyncSamplesSincePresent, MAX_RESYNC_SAMPLES_WITHOUT_PRESENT);
    result.appendFormat("mNumResyncSamples: %zd (max %d)\n",
            mNumResyncSamples, MAX_RESYNC_SAMPLES);

    result.appendFormat("mResyncSamples:\n");
    nsecs_t previous = -1;
    for (size_t i = 0; i < mNumResyncSamples; i++) {
        size_t idx = (mFirstResyncSample + i) % MAX_RESYNC_SAMPLES;
        nsecs_t sampleTime = mResyncSamples[idx];
        if (i == 0) {
            result.appendFormat("  %" PRId64 "\n", sampleTime);
        } else {
            result.appendFormat("  %" PRId64 " (+%" PRId64 ")\n",
                    sampleTime, sampleTime - previous);
        }
        previous = sampleTime;
    }

    result.appendFormat("mPresentFences [%d]:\n",
            NUM_PRESENT_SAMPLES);
    nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    previous = Fence::SIGNAL_TIME_INVALID;
    for (size_t i = 0; i < NUM_PRESENT_SAMPLES; i++) {
        size_t idx = (i + mPresentSampleOffset) % NUM_PRESENT_SAMPLES;
        nsecs_t presentTime = mPresentFences[idx]->getSignalTime();
        if (presentTime == Fence::SIGNAL_TIME_PENDING) {
            result.appendFormat("  [unsignaled fence]\n");
        } else if(presentTime == Fence::SIGNAL_TIME_INVALID) {
            result.appendFormat("  [invalid fence]\n");
        } else if (previous == Fence::SIGNAL_TIME_PENDING ||
                previous == Fence::SIGNAL_TIME_INVALID) {
            result.appendFormat("  %" PRId64 "  (%.3f ms ago)\n", presentTime,
                    (now - presentTime) / 1000000.0);
        } else {
            result.appendFormat("  %" PRId64 " (+%" PRId64 " / %.3f)  (%.3f ms ago)\n",
                    presentTime, presentTime - previous,
                    (presentTime - previous) / (double) mPeriod,
                    (now - presentTime) / 1000000.0);
        }
        previous = presentTime;
    }

    result.appendFormat("current monotonic time: %" PRId64 "\n", now);
#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
    dumpEnhanceInfo(result);
#endif
}

#ifdef MTK_VSYNC_ENHANCEMENT_SUPPORT
status_t DispSyncThread::setVSyncMode(int32_t mode, int32_t fps, nsecs_t period, nsecs_t phase,
                      nsecs_t referenceTime) {
    ALOGI("setVSync: mode[%d->%d] fps[%d->%d]", mVSyncMode, mode, mFps, fps);
    Mutex::Autolock lock(mMutex);
    status_t res = NO_ERROR;
    mFps = fps;
    mVSyncMode = mode;
    mPeriod = period;
    mPhase = phase;
    mReferenceTime = referenceTime;
    mCond.signal();
    return res;
}

void DispSyncThread::addResyncSample(nsecs_t period, nsecs_t phase, nsecs_t referenceTime) {
    Mutex::Autolock lock(mMutex);
    if (mVSyncMode == VSYNC_MODE_PASS_HW_VSYNC) {
        mPeriod = period;
        mPhase = phase;
        mReferenceTime = referenceTime;
        if (mEventListeners.size() > 0) {
            mWakeReason = WAKE_REASON_VSYNC;
            mCond.signal();
        }
    }
}

void DispSyncThread::initVsyncEnhance() {
    mVSyncMode = VSYNC_MODE_CALIBRATED_SW_VSYNC;
    mFps = DS_DEFAULT_FPS;
    mWakeReason = WAKE_REASON_NONE;
}

void DispSync::getDispSyncEnhancementFunctionList(struct DispSyncEnhancementFunctionList* list) {
    list->setVSyncMode = std::bind(&DispSyncThread::setVSyncMode, mThread.get(),
                                      std::placeholders::_1, std::placeholders::_2,
                                      std::placeholders::_3, std::placeholders::_4,
                                      std::placeholders::_5);
    list->addResyncSample = std::bind(&DispSyncThread::addResyncSample, mThread.get(),
                                         std::placeholders::_1, std::placeholders::_2,
                                         std::placeholders::_3);
    list->addEventListener = std::bind(&DispSyncThread::addEventListener, mThread.get(),
                                          std::placeholders::_1, std::placeholders::_2,
                                          std::placeholders::_3);
    list->hasAnyEventListeners = std::bind(&DispSyncThread::hasAnyEventListeners, mThread.get());
    list->enableHardwareVsync = std::bind(&DispSync::enableHardwareVsync, this);
    list->removeEventListener = std::bind(&DispSyncThread::removeEventListener, mThread.get(),
                                              std::placeholders::_1);
    list->onSwVsyncChange = std::bind(&DispSync::onSwVsyncChange, this,
                                         std::placeholders::_1, std::placeholders::_2);
}
#endif

#ifdef MTK_GPU_DVFS_SUPPORT
    void DispSyncThread::initVsyncHint() {
        typedef VsyncHintApi* (*createVsyncHintPrototype)();
        mVsyncHint = NULL;
        mVsyncHintHandle = dlopen("libvsync_hint.so", RTLD_LAZY);
        if (mVsyncHintHandle) {
            createVsyncHintPrototype createPtr = reinterpret_cast<createVsyncHintPrototype>(dlsym(mVsyncHintHandle, "createVsyncHint"));
            if (createPtr) {
                mVsyncHint = createPtr();
            } else {
                ALOGW("Failed to get function: createVsyncHint");
            }
        } else {
            ALOGW("Failed to load libvsync_hint.so");
        }
    }

    void DispSyncThread::deinitVsyncHint() {
        if (mVsyncHint) {
            delete mVsyncHint;
        }
        if (mVsyncHintHandle) {
            dlclose(mVsyncHintHandle);
        }
    }

    void DispSyncThread::fillListenerType(EventListener* listener) {
        if (mVsyncHint) {
            if (!strcmp(listener->mName, "sf")) {
                listener->mVsyncType = VsyncHintApi::VSYNC_TYPE_SF;
            } else if (!strcmp(listener->mName, "app")) {
                listener->mVsyncType = VsyncHintApi::VSYNC_TYPE_APP;
            } else {
                listener->mVsyncType = VsyncHintApi::VSYNC_TYPE_UNKNOWN;
            }
        }
    }

    void DispSyncThread::fillCallbackType(CallbackInvocation* callback, const EventListener& listener) {
        if (mVsyncHint) {
            callback->mVsyncType = listener.mVsyncType;
        }
    }

    void DispSyncThread::fireVsyncHint(const CallbackInvocation& ci) {
        if (mVsyncHint) {
            mVsyncHint->notifyVsync(ci.mVsyncType, mPeriod);
        }
    }
#endif
} // namespace android
