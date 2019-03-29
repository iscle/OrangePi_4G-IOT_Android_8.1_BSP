/*
 * Copyright (c) 2014 Google, Inc.  All Rights Reserved.
 *
 */
#ifndef POWER_HAL_TIME_QOS_MANAGER_H
#define POWER_HAL_TIME_QOS_MANAGER_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/threads.h>
#include <utils/String8.h>
#include <utils/Errors.h>
#include <utils/Log.h>

using namespace android;

extern void sysfs_write(const char *path, const char *s);

class QosObject {
public:
    virtual ~QosObject() {}

    virtual void enter() = 0;
    virtual void exit() = 0;
};

class SysfsQosObject : public QosObject {
public:
    SysfsQosObject(const char* nodeName, const char* enterCmd, const char* exitCmd)
     : mNodeName(nodeName), mEnterCmd(enterCmd), mExitCmd(exitCmd) {}

    virtual void enter();
    virtual void exit();

private:
    const char *mNodeName;
    const char *mEnterCmd;
    const char *mExitCmd;

};

class TimedQosManager : public Thread {
public:
    TimedQosManager(const char *name, QosObject *qosObj, bool oneShot) :
        Thread(false), mName(name), mQosObject(qosObj), mOneShot(oneShot), mLock("lock") {}

    virtual ~TimedQosManager() { delete mQosObject; }

    virtual bool threadLoop();

    void requestTimedQos(nsecs_t timeout);

private:
    const char *mName;
    QosObject *mQosObject;
    bool mOneShot;

    Mutex mLock;
    Condition mCondition;

    nsecs_t mTargetTime;
};

#endif
