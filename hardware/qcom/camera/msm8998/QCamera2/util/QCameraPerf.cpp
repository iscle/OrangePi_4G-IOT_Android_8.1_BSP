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

#define LOG_TAG "QCameraPerf"

// To remove
#include <cutils/properties.h>
#include <utils/Errors.h>

// System dependencies
#include <stdlib.h>
#include <dlfcn.h>
#include <utils/Timers.h>
// Camera dependencies
#include "QCameraPerf.h"
#include "QCameraTrace.h"

#include <android-base/properties.h>

extern "C" {
#include "mm_camera_dbg.h"
}

namespace qcamera {

typedef enum {
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_0     = 0x40800000,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_1     = 0x40800010,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_2     = 0x40800020,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_3     = 0x40800030,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_0  = 0x40800100,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_1  = 0x40800110,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_2  = 0x40800120,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_3  = 0x40800130,

    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_0     = 0x40804000,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_1     = 0x40804010,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_2     = 0x40804020,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_3     = 0x40804030,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_0  = 0x40804100,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_1  = 0x40804110,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_2  = 0x40804120,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_3  = 0x40804130,

    MPCTLV3_MIN_ONLINE_CPU_CLUSTER_BIG      = 0x41000000,
    MPCTLV3_MIN_ONLINE_CPU_CLUSTER_LITTLE   = 0x41000100,
    MPCTLV3_MAX_ONLINE_CPU_CLUSTER_BIG      = 0x41004000,
    MPCTLV3_MAX_ONLINE_CPU_CLUSTER_LITTLE   = 0x41004100,

    MPCTLV3_ALL_CPUS_PWR_CLPS_DIS           = 0x40400000,
    MPCTLV3_CPUBW_HWMON_MIN_FREQ            = 0x41800000,
    MPCTLV3_CPUBW_HWMON_HYST_OPT            = 0x4180C000
} perf_lock_params;


static int32_t perfLockParamsOpenCamera[] = {
    // Disable power collapse and set CPU cloks to turbo
    MPCTLV3_ALL_CPUS_PWR_CLPS_DIS,          0x1,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF
};

static int32_t perfLockParamsCloseCamera[] = {
    // Disable power collapse and set CPU cloks to turbo
    MPCTLV3_ALL_CPUS_PWR_CLPS_DIS,          0x1,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF
};

static int32_t perfLockParamsStartPreview[] = {
    // Disable power collapse and set CPU cloks to turbo
    MPCTLV3_ALL_CPUS_PWR_CLPS_DIS,          0x1,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF
};

static int32_t perfLockParamsTakeSnapshot[] = {
    // Disable power collapse and set CPU cloks to turbo
    MPCTLV3_ALL_CPUS_PWR_CLPS_DIS,          0x1,
    MPCTLV3_MAX_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_BIG_CORE_0,    0xFFF,
    MPCTLV3_MAX_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF,
    MPCTLV3_MIN_FREQ_CLUSTER_LITTLE_CORE_0, 0xFFF,
    MPCTLV3_CPUBW_HWMON_HYST_OPT,           0x0,
    MPCTLV3_CPUBW_HWMON_MIN_FREQ,           0x8C
};

PerfLockInfo QCameraPerfLock::mPerfLockInfo[] = {
    { //PERF_LOCK_OPEN_CAMERA
      perfLockParamsOpenCamera,
      sizeof(perfLockParamsOpenCamera)/sizeof(int32_t) },
    { //PERF_LOCK_CLOSE_CAMERA
      perfLockParamsCloseCamera,
      sizeof(perfLockParamsCloseCamera)/sizeof(int32_t) },
    { //PERF_LOCK_START_PREVIEW
      perfLockParamsStartPreview,
      sizeof(perfLockParamsStartPreview)/sizeof(int32_t) },
    { //PERF_LOCK_TAKE_SNAPSHOT
      perfLockParamsTakeSnapshot,
      sizeof(perfLockParamsTakeSnapshot)/sizeof(int32_t) },
    { //PERF_LOCK_POWERHINT_PREVIEW
      NULL, 0},
    { //PERF_LOCK_POWERHINT_ENCODE
      NULL, 0}
    };

Mutex                QCameraPerfLockIntf::mMutex;
QCameraPerfLockIntf* QCameraPerfLockIntf::mInstance = NULL;


/*===========================================================================
 * FUNCTION   : QCameraPerfLockMgr constructor
 *
 * DESCRIPTION: Initialize the perf locks
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLockMgr::QCameraPerfLockMgr() :
    mState(LOCK_MGR_STATE_UNINITIALIZED)
{
    for (int i = 0; i < PERF_LOCK_COUNT; ++i) {
        mPerfLock[i] = QCameraPerfLock::create((PerfLockEnum)i);

        if (mPerfLock[i] == NULL) {
            mState = LOCK_MGR_STATE_ERROR;
            LOGE("Could not allocate perf locks");

            // Set the remaining perf locks to NULL
            for (int j = i+1; j < PERF_LOCK_COUNT; ++j) {
                mPerfLock[j] = NULL;
            }
            return;
        }
    }
    mState = LOCK_MGR_STATE_READY;
}


/*===========================================================================
 * FUNCTION   : QCameraPerfLockMgr destructor
 *
 * DESCRIPTION: class destructor
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLockMgr::~QCameraPerfLockMgr()
{
    for (int i = 0; i < PERF_LOCK_COUNT; ++i) {
        if (mPerfLock[i]) {
            delete mPerfLock[i];
        }
    }
}


/*===========================================================================
 * FUNCTION   : acquirePerfLock
 *
 * DESCRIPTION: Call acquirePerfLock function for the requested perf lock
 *
 * PARAMETERS :
 * @perfLockType: Perf lock enum
 * @timer:        Timer value in ms
 *
 * RETURN     : true  on success
 *              false on failure
 *==========================================================================*/
bool QCameraPerfLockMgr::acquirePerfLock(
        PerfLockEnum perfLockType,
        uint32_t     timer)
{
    bool ret = false;
    if ((mState == LOCK_MGR_STATE_READY) &&
        isValidPerfLockEnum(perfLockType)) {
        ret = mPerfLock[perfLockType]->acquirePerfLock(true, timer);
    }
    return ret;
}


/*===========================================================================
 * FUNCTION   : acquirePerfLockIfExpired
 *
 * DESCRIPTION: Call acquirePerfLock function for the requested perf lock
 *
 * PARAMETERS :
 * @perfLockType: Type of perf lock
 * @timer:        Timer value in ms
 *
 * RETURN     : true  on success
 *              false on failure
 *==========================================================================*/
bool QCameraPerfLockMgr::acquirePerfLockIfExpired(
        PerfLockEnum perfLockType,
        uint32_t     timer)
{
    bool ret = false;
    if ((mState == LOCK_MGR_STATE_READY) &&
        isValidPerfLockEnum(perfLockType)) {
        ret = mPerfLock[perfLockType]->acquirePerfLock(false, timer);
    }
    return ret;

}


/*===========================================================================
 * FUNCTION   : releasePerfLock
 *
 * DESCRIPTION: Call releasePerfLock function for the requested perf lock
 *
 * PARAMETERS :
 * @perfLockType: Enum of perf lock
 *
 * RETURN     : true  on success
 *              false on failure
 *==========================================================================*/
bool QCameraPerfLockMgr::releasePerfLock(
        PerfLockEnum perfLockType)
{
    bool ret = false;
    if ((mState == LOCK_MGR_STATE_READY) &&
        isValidPerfLockEnum(perfLockType)) {
        ret = mPerfLock[perfLockType]->releasePerfLock();
    }
    return ret;
}


/*===========================================================================
 * FUNCTION   : powerHintInternal
 *
 * DESCRIPTION: Calls the appropriate perf lock's powerHintInternal function
 *
 * PARAMETERS :
 * @perfLockType: Type of perf lock
 * @hint        : Power hint
 * @enable      : Enable power hint if set to 1. Disable if set to 0.
 *
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLockMgr::powerHintInternal(
        PerfLockEnum perfLockType,
        PowerHint    powerHint,
        bool         enable)
{
    if ((mState == LOCK_MGR_STATE_READY) &&
        isValidPerfLockEnum(perfLockType)) {
        mPerfLock[perfLockType]->powerHintInternal(powerHint, enable);
    }
}


/*===========================================================================
 * FUNCTION   : create
 *
 * DESCRIPTION: This is a static method to create perf lock object. It calls
 *              protected constructor of the class and only returns a valid object
 *              if it can successfully initialize the perf lock.
 *
 * PARAMETERS : None
 *
 * RETURN     : QCameraPerfLock object pointer on success
 *              NULL on failure
 *
 *==========================================================================*/
QCameraPerfLock* QCameraPerfLock::create(
        PerfLockEnum perfLockType)
{
    QCameraPerfLock *perfLock = NULL;

    if (perfLockType < PERF_LOCK_COUNT) {
        QCameraPerfLockIntf *perfLockIntf = QCameraPerfLockIntf::createSingleton();
        if (perfLockIntf) {
            perfLock = new QCameraPerfLock(perfLockType, perfLockIntf);
        }
    }
    return perfLock;
}


/*===========================================================================
 * FUNCTION   : QCameraPerfLock constructor
 *
 * DESCRIPTION: Initialize member variables
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLock::QCameraPerfLock(
        PerfLockEnum         perfLockType,
        QCameraPerfLockIntf *perfLockIntf) :
        mHandle(0),
        mRefCount(0),
        mTimeOut(0),
        mPerfLockType(perfLockType),
        mPerfLockIntf(perfLockIntf)
{
    mIsPerfdEnabled = android::base::GetBoolProperty("persist.camera.perfd.enable", false);
}


/*===========================================================================
 * FUNCTION   : QCameraPerfLock destructor
 *
 * DESCRIPTION: class destructor
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLock::~QCameraPerfLock()
{
    if (mHandle > 0) {
        (*mPerfLockIntf->perfLockRel())(mHandle);
    }
    QCameraPerfLockIntf::deleteInstance();
}


/*===========================================================================
 * FUNCTION   : isTimedOut
 *
 * DESCRIPTION: Check if the perf lock is timed out
 *
 * PARAMETERS : None
 *
 * RETURN     : boolean indicating if the perf lock is timed out
 *
 *==========================================================================*/
bool QCameraPerfLock::isTimedOut()
{
    if (mTimeOut && (systemTime() > mTimeOut)) {
        return true;
    }
    return false;
}


/*===========================================================================
 * FUNCTION   : restartTimer
 *
 * DESCRIPTION: Restart the timer for the duration specified
 *
 * PARAMETERS :
 *  @timer    : timer duration in milliseconds
 *
 * RETURN     : void
 *
 *==========================================================================*/
void inline QCameraPerfLock::restartTimer(
        uint32_t timer)
{
    if (timer > 0) {
        mTimeOut = systemTime() + ms2ns(timer);
    }
}


/*===========================================================================
 * FUNCTION   : acquirePerfLock
 *
 * DESCRIPTION: Acquires the perf lock for the duration specified. Do not acquire
 *              the perf lock is reacquire flag is set to false provided the perf
 *              lock is already acquired.
 *
 * PARAMETERS :
 * @forceReaquirePerfLock: Reacquire
 * @timer     : Duration of the perf lock
 *
 * RETURN     : true  on success
 *              false on failure
 *
 *==========================================================================*/
bool QCameraPerfLock::acquirePerfLock(
        bool     forceReaquirePerfLock,
        uint32_t timer)
{
    bool ret = true;
    Mutex::Autolock lock(mMutex);

    if ((mPerfLockType == PERF_LOCK_POWERHINT_PREVIEW) ||
        (mPerfLockType == PERF_LOCK_POWERHINT_ENCODE)) {
        powerHintInternal(PowerHint::VIDEO_ENCODE, true);
        return true;
    }

    if (!mIsPerfdEnabled) return ret;

    if (isTimedOut()) {
        mHandle   = 0;
        mRefCount = 0;
    }

    if ((mRefCount == 0) || forceReaquirePerfLock) {
        mHandle = (*mPerfLockIntf->perfLockAcq())(
            mHandle, timer,
            mPerfLockInfo[mPerfLockType].perfLockParams,
            mPerfLockInfo[mPerfLockType].perfLockParamsCount);

        if (mHandle > 0) {
            ++mRefCount;
            restartTimer(timer);
            LOGD("perfLockHandle %d, updated refCount: %d, perfLockType: %d",
                mHandle, mRefCount, mPerfLockType);
        } else {
            LOGE("Failed to acquire the perf lock");
            ret = false;
        }
    } else {
        LOGD("Perf lock already acquired, not re-aquiring");
    }

    return ret;
}


/*===========================================================================
 * FUNCTION   : releasePerfLock
 *
 * DESCRIPTION: Releases the perf lock
 *
 * PARAMETERS : None
 *
 * RETURN     : true  on success
 *              false on failure
 *
 *==========================================================================*/
bool QCameraPerfLock::releasePerfLock()
{
    bool ret = true;
    Mutex::Autolock lock(mMutex);

    if ((mPerfLockType == PERF_LOCK_POWERHINT_PREVIEW) ||
        (mPerfLockType == PERF_LOCK_POWERHINT_ENCODE)) {
        powerHintInternal(PowerHint::VIDEO_ENCODE, false);
        return true;
    }

    if (!mIsPerfdEnabled) return ret;

    if (mHandle > 0) {
        LOGD("perfLockHandle %d, refCount: %d, perfLockType: %d",
                    mHandle, mRefCount, mPerfLockType);

        if (isTimedOut()) {
            mHandle   = 0;
            mRefCount = 0;
        } else if (--mRefCount == 0) {
            int32_t rc = (*mPerfLockIntf->perfLockRel())(mHandle);
            mHandle = 0;
            mTimeOut = 0;
            if (rc < 0) {
                LOGE("Failed to release the perf lock");
                ret = false;
            }
        }
    } else {
        LOGW("Perf lock %d either not acquired or already released", mPerfLockType);
    }

    return ret;
}


/*===========================================================================
 * FUNCTION   : powerHintInternal
 *
 * DESCRIPTION: Sets the requested power hint and state to power HAL.
 *
 * PARAMETERS :
 * @hint      : Power hint
 * @enable    : Enable power hint if set to 1. Disable if set to 0.
 *
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLock::powerHintInternal(
        PowerHint    powerHint,
        bool         enable)
{
#ifdef HAS_MULTIMEDIA_HINTS
    if (!mPerfLockIntf->powerHint(powerHint, enable)) {
        LOGE("Send powerhint to PowerHal failed");
    }
#endif
}



/*===========================================================================
 * FUNCTION   : createSingleton
 *
 * DESCRIPTION: Open the perf lock library, query the function pointers and
 *              create a singleton object upon success
 *
 * PARAMETERS : None
 *
 * RETURN     : QCameraPerfLockIntf object pointer on success
 *              NULL on failure
 *
 *==========================================================================*/
QCameraPerfLockIntf* QCameraPerfLockIntf::createSingleton()
{
    bool error = true;
    Mutex::Autolock lock(mMutex);

    if (mInstance == NULL) {
        // Open perflock library and query for the function pointers
        uint32_t perfLockEnable = 0;
        char value[PROPERTY_VALUE_MAX];

        property_get("persist.camera.perflock.enable", value, "1");
        perfLockEnable = atoi(value);

        if (perfLockEnable) {
            mInstance = new QCameraPerfLockIntf();
            if (mInstance) {
                #ifdef HAS_MULTIMEDIA_HINTS
                mInstance->mPowerHal = IPower::getService();
                if (mInstance->mPowerHal == nullptr) {
                    ALOGE("Couldn't load PowerHAL module");
                }
                else
                #endif
                {
                    /* Retrieve the name of the vendor extension library */
                    void *dlHandle = NULL;
                    if ((property_get("ro.vendor.extension_library", value, NULL) > 0) &&
                        (dlHandle = dlopen(value, RTLD_NOW | RTLD_LOCAL))) {

                        perfLockAcquire pLockAcq = (perfLockAcquire)dlsym(dlHandle, "perf_lock_acq");
                        perfLockRelease pLockRel = (perfLockRelease)dlsym(dlHandle, "perf_lock_rel");

                        if (pLockAcq && pLockRel) {
                            mInstance->mDlHandle    = dlHandle;
                            mInstance->mPerfLockAcq = pLockAcq;
                            mInstance->mPerfLockRel = pLockRel;
                            error = false;
                        } else {
                            LOGE("Failed to link the symbols- perf_lock_acq, perf_lock_rel");
                        }
                    } else {
                        LOGE("Unable to load lib: %s", value);
                    }
                }
                if (error && mInstance) {
                    delete mInstance;
                    mInstance = NULL;
                }
            }
        }
    }

    if (mInstance) {
        ++(mInstance->mRefCount);
    }

    return mInstance;
}


/*===========================================================================
 * FUNCTION   : deleteInstance
 *
 * DESCRIPTION: Delete the object if refCount is 0
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLockIntf::deleteInstance()
{
    Mutex::Autolock lock(mMutex);

    if (mInstance && (--(mInstance->mRefCount) == 0)) {
        delete mInstance;
        mInstance = NULL;
    }
}


/*===========================================================================
 * FUNCTION   : QCameraPerfLockIntf destructor
 *
 * DESCRIPTION: class destructor
 *
 * PARAMETERS : None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLockIntf::~QCameraPerfLockIntf()
{
    if (mDlHandle) {
        dlclose(mDlHandle);
    }
}

}; // namespace qcamera
