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

#ifndef MTK_HWUI_MONITOR_H
#define MTK_HWUI_MONITOR_H

#include <utils/Singleton.h>
#include <utils/threads.h>
#include <stdint.h>
#include <sys/types.h>

namespace android {
namespace uirenderer {

class MonitorThread;
class MonitorTask;
class MonitorQueue;

/*
 * MonitorThread helps to supervise the execution time of a job (e.g. RenderTask)
 * Queue a monitor task before running your job and remove it after the job's finished.
 * If the job runs too long, for example, more than 500ms, MonitorThread will keep
 * notifying to warn you until it's done.
 */

///////////////////////////////////////////////////////////////////////////////
// MonitorSignal
///////////////////////////////////////////////////////////////////////////////

class MonitorSignal {
public:
    MonitorSignal(Condition::WakeUpType type = Condition::WAKE_UP_ALL) : mType(type) { }
    ~MonitorSignal() { }

    void signal() {
        mCondition.signal(mType);
    }

    void wait(int reltimeMs) {
        Mutex::Autolock l(mLock);
        if (reltimeMs > 0) {
            mCondition.waitRelative(mLock, milliseconds_to_nanoseconds(reltimeMs));
        } else if (reltimeMs < 0){
            mCondition.wait(mLock);
        }
    }

private:
    Condition::WakeUpType mType;
    mutable Mutex mLock;
    mutable Condition mCondition;
};

///////////////////////////////////////////////////////////////////////////////
// MonitorTask
///////////////////////////////////////////////////////////////////////////////

class MonitorTask {
    friend class MonitorThread;
    friend class MonitorQueue;
public:
    MonitorTask(const char* label);

    // request to remove and return the total executation time of the task,
    // it may have deleted itself, do not reference it again
    nsecs_t requestRemove();

private:
    void run();

    // nano-seconds on the SYSTEM_TIME_MONOTONIC clock
    // start: when the task is added to montor thread
    // run: when the task is run in monitor thread
    // end: when the task is removed from monitor thread.
    nsecs_t mStartAt;
    nsecs_t mRunAt;
    nsecs_t mEndAt;

    // label to indicate this task is for what job
    const char* mLabel;
    MonitorTask* mNext;
    MonitorThread& mMonitorThread;
    bool mRequestRemove;
};

///////////////////////////////////////////////////////////////////////////////
// MonitorQueue
///////////////////////////////////////////////////////////////////////////////

class MonitorQueue {
    friend class MonitorThread;
public:
    MonitorQueue() : mHead(nullptr), mTail(nullptr) { }

    MonitorTask* next();
    void queue(MonitorTask* task);
    MonitorTask* peek();
    void remove(MonitorTask* task);

private:
    MonitorTask* mHead;
    MonitorTask* mTail;
};

///////////////////////////////////////////////////////////////////////////////
// MonitorThread
///////////////////////////////////////////////////////////////////////////////

class MonitorThread: public Thread, public Singleton<MonitorThread> {
    friend class MonitorTask;
public:
    MonitorThread();
    friend class Singleton<MonitorThread>;

public:
    // dump all task for debug purposes
    void dump(FILE* file = nullptr);

protected:
    virtual bool threadLoop() override;

private:
    // keep all operation as private, only MonitorTask can use

    // queue a task to monitor thread
    void queue(MonitorTask* task);
    void queueLocked(MonitorTask* task);

    // remove a task and return the total executation time of the task
    nsecs_t remove(MonitorTask* task);

    // Returns the next task to be run. If this returns NULL nextWakeup is set
    // to the time to requery for the nextTask to run. mNextWakeup is also
    // set to this time
    MonitorTask* nextTask(nsecs_t* nextWakeup);

    Mutex mLock;
    nsecs_t mNextWakeup;
    MonitorQueue mQueue;

    // Signal used to wake up the thread when a new
    // task is available in the list
    mutable MonitorSignal mSignal;
};

}; // namespace uirenderer
}; // namespace android

#endif /* MTK_HWUI_MONITORTHREAD_H */
