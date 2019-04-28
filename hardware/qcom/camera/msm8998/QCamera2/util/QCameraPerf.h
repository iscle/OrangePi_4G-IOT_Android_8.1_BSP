/* Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#ifndef __QCAMERAPERF_H__
#define __QCAMERAPERF_H__

// System dependencies
#include <utils/Mutex.h>

// Camera dependencies
#include <android/hardware/power/1.1/IPower.h>

using namespace android;
using android::hardware::power::V1_1::IPower;
using android::hardware::power::V1_0::PowerHint;
using ::android::hardware::Return;
using ::android::hardware::Void;

namespace qcamera {

#define DEFAULT_PERF_LOCK_TIMEOUT_MS 1000

typedef int32_t (*perfLockAcquire)(int, int, int[], int);
typedef int32_t (*perfLockRelease)(int);

typedef enum {
    PERF_LOCK_OPEN_CAMERA,
    PERF_LOCK_CLOSE_CAMERA,
    PERF_LOCK_START_PREVIEW,
    PERF_LOCK_STOP_PREVIEW    = PERF_LOCK_START_PREVIEW,
    PERF_LOCK_START_RECORDING = PERF_LOCK_START_PREVIEW,
    PERF_LOCK_STOP_RECORDING  = PERF_LOCK_STOP_PREVIEW,
    PERF_LOCK_TAKE_SNAPSHOT,
    PERF_LOCK_OFFLINE_REPROC  = PERF_LOCK_TAKE_SNAPSHOT,
    PERF_LOCK_POWERHINT_PREVIEW,
    PERF_LOCK_POWERHINT_ENCODE,
    PERF_LOCK_COUNT
} PerfLockEnum;

typedef enum {
    LOCK_MGR_STATE_UNINITIALIZED,
    LOCK_MGR_STATE_READY,
    LOCK_MGR_STATE_ERROR
} PerfLockMgrStateEnum;


typedef struct {
    int32_t      *perfLockParams;
    uint32_t      perfLockParamsCount;
} PerfLockInfo;


class QCameraPerfLockIntf;

class QCameraPerfLock {
public:
    static QCameraPerfLock* create(PerfLockEnum perfLockType);
    virtual ~QCameraPerfLock();

    bool releasePerfLock();
    bool acquirePerfLock(bool     forceReacquirePerfLock,
                         uint32_t timer = DEFAULT_PERF_LOCK_TIMEOUT_MS);
    void powerHintInternal(PowerHint powerHint, bool enable);

protected:
    QCameraPerfLock(PerfLockEnum perfLockType, QCameraPerfLockIntf *perfLockIntf);

private:
    Mutex                mMutex;
    int32_t              mHandle;
    uint32_t             mRefCount;
    nsecs_t              mTimeOut;
    PerfLockEnum         mPerfLockType;
    QCameraPerfLockIntf *mPerfLockIntf;
    bool                 mIsPerfdEnabled;

    static PerfLockInfo  mPerfLockInfo[PERF_LOCK_COUNT];

    void restartTimer(uint32_t timer);
    bool isTimedOut();
};


class QCameraPerfLockIntf {
private:
    static QCameraPerfLockIntf *mInstance;
    static Mutex                mMutex;

    uint32_t         mRefCount;
    perfLockAcquire  mPerfLockAcq;
    perfLockRelease  mPerfLockRel;
    sp<IPower>       mPowerHal;
    void            *mDlHandle;

protected:
    QCameraPerfLockIntf() { mRefCount = 0; mDlHandle = NULL; }
    virtual ~QCameraPerfLockIntf();

public:
    static QCameraPerfLockIntf* createSingleton();
    static void deleteInstance();

    inline perfLockAcquire perfLockAcq() { return mPerfLockAcq; }
    inline perfLockRelease perfLockRel() { return mPerfLockRel; }
    inline bool powerHint(PowerHint hint, int32_t data) {
        return ((mPowerHal == nullptr) ? false : mPowerHal->powerHintAsync(hint, data).isOk());
    }
};


class QCameraPerfLockMgr {
public:
    QCameraPerfLockMgr();
    virtual ~QCameraPerfLockMgr();

    bool releasePerfLock(PerfLockEnum perfLockType);
    bool acquirePerfLock(PerfLockEnum perfLockType, uint32_t timer = DEFAULT_PERF_LOCK_TIMEOUT_MS);

    bool acquirePerfLockIfExpired(PerfLockEnum perfLockRnum,
                                  uint32_t     timer = DEFAULT_PERF_LOCK_TIMEOUT_MS);
    void powerHintInternal(PerfLockEnum perfLockType, PowerHint powerHint, bool enable);

private:
    PerfLockMgrStateEnum mState;
    Mutex                mMutex;
    QCameraPerfLock*     mPerfLock[PERF_LOCK_COUNT];

    inline bool isValidPerfLockEnum(PerfLockEnum perfLockType)
                    { return (perfLockType < PERF_LOCK_COUNT); }
};

}; // namespace qcamera

#endif /* __QCAMREAPERF_H__ */
