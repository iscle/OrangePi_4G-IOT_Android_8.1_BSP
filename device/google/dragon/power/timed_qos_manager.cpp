/*
 * Copyright (c) 2014 Google, Inc.  All Rights Reserved.
 * Copyright (c) 2015 NVIDIA, Inc.  All Rights Reserved.
 *
 */
#include "timed_qos_manager.h"
#include <fcntl.h>
#include <assert.h>

#undef LOG_TAG
#define LOG_TAG "powerHAL::TimedQosManager"

void SysfsQosObject::enter()
{
    sysfs_write(mNodeName, mEnterCmd);
}

void SysfsQosObject::exit()
{
    sysfs_write(mNodeName, mExitCmd);
}

bool TimedQosManager::threadLoop()
{
    AutoMutex lock(mLock);

    ALOGI("threadLoop [%s] starting\n", mName);

    while (1) {
        if (exitPending()) {
            ALOGV("threadLoop [%s] exiting\n", mName);
            break;
        }
        if (mTargetTime == 0) {
            // wait for something to do
            ALOGV("threadLoop [%s] nothing to do, waiting\n", mName);
            mCondition.wait(mLock);
            ALOGV("threadLoop [%s] woke from wait\n", mName);
        } else {
            // open qos file if not already open
            mQosObject->enter();

            // wait for target time to expire
            nsecs_t currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
            ALOGV("threadLoop [%s] waiting with relative time %lld\n",
                    mName, mTargetTime - currentTime);
            mCondition.waitRelative(mLock, mTargetTime - currentTime);

            // check if we're done.  if not (typically because
            // someone extended our time while we were blocked)
            // just loop again and sleep until new target time
            currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
            if (currentTime >= mTargetTime) {
                mQosObject->exit();
                mTargetTime = 0;
            } else {
                ALOGV("threadLoop [%s] timeout extended\n");
            }
        }
    }
    return false;
}

void TimedQosManager::requestTimedQos(nsecs_t reltime)
{
    AutoMutex lock(mLock);
    nsecs_t targetTime = systemTime() + reltime;

    /* new target time should always be ahead of current one */
    assert(mTargetTime <= targetTime);
    mTargetTime = targetTime;
    ALOGV("threadLoop [%s] requesting reltime %lld, mTargetTime set to %lld\n",
          mName, reltime, mTargetTime);

    /* wake the Thread.  if it's already waiting on a different
     * timeout, this will just wake it early and it'll wait again.
     */
    mCondition.signal(Condition::WAKE_UP_ALL);
}
