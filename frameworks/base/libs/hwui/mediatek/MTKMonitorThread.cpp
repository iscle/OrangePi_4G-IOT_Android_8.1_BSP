/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#include "MTKMonitorThread.h"

#include <utils/Log.h>
#include <utils/Trace.h>
#include <utils/String8.h>

namespace android {

using namespace uirenderer;
ANDROID_SINGLETON_STATIC_INSTANCE(MonitorThread);

namespace uirenderer {

#define ANR_DURATION_MS 500 // notify if running longer than
#define ANR_DURATION_STEP_MS 500 // delay to notify again

///////////////////////////////////////////////////////////////////////////////
// MonitorTask
///////////////////////////////////////////////////////////////////////////////

MonitorTask::MonitorTask(const char* label)
    : mStartAt(systemTime(SYSTEM_TIME_MONOTONIC))
    , mRunAt(mStartAt + milliseconds_to_nanoseconds(ANR_DURATION_MS))
    , mEndAt(0)
    , mLabel(label)
    , mNext(nullptr)
    , mMonitorThread(MonitorThread::getInstance())
    , mRequestRemove(false) {
    mMonitorThread.queue(this);
}

nsecs_t MonitorTask::requestRemove() {
    return mMonitorThread.remove(this);
}

void MonitorTask::run() {
    LOG_ALWAYS_FATAL_IF(mNext,
        "Task should be removed from queue before running! task %p, next %p", this, mNext);

    // use MontorThread's lock to sync life cycle
    AutoMutex _lock(mMonitorThread.mLock);
    if (!mRequestRemove) {
        ALOGD("[ANR warning] (%p, %s) run from %" PRId64 " to %"
            PRId64 " (%" PRId64 "ms) but not finished yet!!",
            this, mLabel, mStartAt, mRunAt, nanoseconds_to_milliseconds(mRunAt - mStartAt));
        mRunAt += milliseconds_to_nanoseconds(ANR_DURATION_STEP_MS);

        // host is not finished yet, enqueue task to keep monitoring it
        mMonitorThread.queueLocked(this);
    } else {
        ALOGD("MonitorTask (%p, %s) run to delete", this, mLabel);
        // Commit suicide
        delete this;
    }
}

///////////////////////////////////////////////////////////////////////////////
// MonitorQueue
///////////////////////////////////////////////////////////////////////////////

MonitorTask* MonitorQueue::next() {
    MonitorTask* ret = mHead;
    if (ret) {
        mHead = ret->mNext;
        if (!mHead) {
            mTail = nullptr;
        }
        ret->mNext = nullptr;
    }
    return ret;
}

MonitorTask* MonitorQueue::peek() {
    return mHead;
}

void MonitorQueue::queue(MonitorTask* task) {
    // Since the monitor task itself forms the linked list it is not allowed
    // to have the same task queued twice
    LOG_ALWAYS_FATAL_IF(task->mNext || mTail == task,
        "Task is already in the queue! task %p, next %p, tail %p", task, task->mNext, mTail);
    if (mTail) {
        // Fast path if we can just append
        if (mTail->mRunAt <= task->mRunAt) {
            mTail->mNext = task;
            mTail = task;
        } else {
            // Need to find the proper insertion point
            MonitorTask* previous = nullptr;
            MonitorTask* next = mHead;
            while (next && next->mRunAt <= task->mRunAt) {
                previous = next;
                next = next->mNext;
            }
            if (!previous) {
                task->mNext = mHead;
                mHead = task;
            } else {
                previous->mNext = task;
                if (next) {
                    task->mNext = next;
                } else {
                    mTail = task;
                }
            }
        }
    } else {
        mTail = mHead = task;
    }
}

void MonitorQueue::remove(MonitorTask* task) {
    // TaskQueue is strict here to enforce that users are keeping track of
    // their MonitorTasks due to how their memory is managed
    LOG_ALWAYS_FATAL_IF(!task->mNext && mTail != task,
            "Cannot remove a task that isn't in the queue! task %p, next %p, tail %p",
            task, task->mNext, mTail);

    // If task is the head we can just call next() to pop it off
    // Otherwise we need to scan through to find the task before it
    if (peek() == task) {
        next();
    } else {
        MonitorTask* previous = mHead;
        while (previous->mNext != task) {
            previous = previous->mNext;
        }
        previous->mNext = task->mNext;
        if (mTail == task) {
            mTail = previous;
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
// MonitorThread
///////////////////////////////////////////////////////////////////////////////

MonitorThread::MonitorThread(): Thread(true), Singleton<MonitorThread>(), mNextWakeup(LLONG_MAX) {
    run("MonitorThread", PRIORITY_DEFAULT);
}

void MonitorThread::queue(MonitorTask* task) {
    AutoMutex _lock(mLock);
    queueLocked(task);
}

void MonitorThread::queueLocked(MonitorTask* task) {
    mQueue.queue(task);
    if (mNextWakeup && task->mRunAt < mNextWakeup) {
        mNextWakeup = 0;
        mSignal.signal();
    }
}

nsecs_t MonitorThread::remove(MonitorTask* task) {
    AutoMutex _lock(mLock);
    task->mRequestRemove = true;
    task->mEndAt = systemTime(SYSTEM_TIME_MONOTONIC);
    nsecs_t duration = task->mEndAt - task->mStartAt;
    // ANR might already happen because the task is requeued.
    if (task->mRunAt != task->mStartAt + milliseconds_to_nanoseconds(ANR_DURATION_MS)) {
        ALOGD("[ANR warning] (%p, %s) run from %" PRId64 " to %"
            PRId64 " (%" PRId64 "ms) too long!!", task, task->mLabel,
            task->mStartAt, task->mEndAt, nanoseconds_to_milliseconds(duration));
    }

    if (!task->mNext && mQueue.mTail != task) {
        // task is already removed from queue and running in monitor thread,
        // do not remove it again.
        ALOGD("Remove (%p, %s) but it's already removed", task, task->mLabel);
    } else {
        mQueue.remove(task);
        delete task;
    }
    return duration;
}

MonitorTask* MonitorThread::nextTask(nsecs_t* nextWakeup) {
    AutoMutex _lock(mLock);
    MonitorTask* next = mQueue.peek();
    if (!next) {
        mNextWakeup = LLONG_MAX;
    } else {
        mNextWakeup = next->mRunAt;
        // Most tasks won't be delayed, so avoid unnecessary systemTime() calls
        if (next->mRunAt <= 0 || next->mRunAt <= systemTime(SYSTEM_TIME_MONOTONIC)) {
            next = mQueue.next();
        } else {
            next = nullptr;
        }
    }
    if (nextWakeup) {
        *nextWakeup = mNextWakeup;
    }
    return next;
}

bool MonitorThread::threadLoop() {
    int timeoutMillis = -1;
    for (;;) {
        nsecs_t nextWakeup;
        // Process our queue, if we have anything
        while (MonitorTask* task = nextTask(&nextWakeup)) {
            task->run();
        }
        if (nextWakeup == LLONG_MAX) {
            timeoutMillis = -1;
        } else {
            nsecs_t timeoutNanos = nextWakeup - systemTime(SYSTEM_TIME_MONOTONIC);
            timeoutMillis = nanoseconds_to_milliseconds(timeoutNanos);
            if (timeoutMillis < 0) {
                timeoutMillis = 0;
            }
        }

        mSignal.wait(timeoutMillis);
    }

    return false;
}

void MonitorThread::dump(FILE* file) {
    AutoMutex _lock(mLock);
    MonitorTask* next = mQueue.mHead;
    String8 log;
    log.append("MonitorThread:\n");
    while (next) {
        nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
        log.appendFormat("task (%p, %s) run from %" PRId64 " to %" PRId64 " (%"
            PRId64 "ms) but not finished yet!!\n", this, next->mLabel, next->mStartAt,
            now, nanoseconds_to_milliseconds(now - next->mStartAt));
        next = next->mNext;
    }
    if (file) fprintf(file, "%s", log.string());
    else ALOGD("%s", log.string());
}

}; // namespace uirenderer
}; // namespace android
