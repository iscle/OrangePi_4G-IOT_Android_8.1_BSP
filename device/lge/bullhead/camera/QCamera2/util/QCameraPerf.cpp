/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

#include <cutils/properties.h>
#include <stdlib.h>
#include <utils/Log.h>
#include "QCameraPerf.h"

#ifdef CDBG
#undef CDBG
#endif //#ifdef CDBG
#define CDBG(fmt, args...) ALOGD_IF(gCamHalLogLevel >= 2, fmt, ##args)

#ifdef CDBG_HIGH
#undef CDBG_HIGH
#endif //#ifdef CDBG_HIGH
#define CDBG_HIGH(fmt, args...) ALOGD_IF(gCamHalLogLevel >= 1, fmt, ##args)


namespace qcamera {

extern volatile uint32_t gCamHalLogLevel;

/*===========================================================================
 * FUNCTION   : QCameraPerfLock constructor
 *
 * DESCRIPTION: initialize member variables
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLock::QCameraPerfLock() :
        perf_lock_acq(NULL),
        perf_lock_rel(NULL),
        mDlHandle(NULL),
        mPerfLockEnable(0),
        mPerfLockHandle(-1),
        mPerfLockHandleTimed(-1),
        mTimerSet(0),
        mPerfLockTimeout(0),
        mStartTimeofLock(0)
{
}

/*===========================================================================
 * FUNCTION   : QCameraPerfLock destructor
 *
 * DESCRIPTION: class desctructor
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraPerfLock::~QCameraPerfLock()
{
    lock_deinit();
}


/*===========================================================================
 * FUNCTION   : lock_init
 *
 * DESCRIPTION: opens the performance lib and initilizes the perf lock functions
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLock::lock_init()
{
    const char *rc;
    char value[PROPERTY_VALUE_MAX];
    int len;

    CDBG("%s E", __func__);
    Mutex::Autolock lock(mLock);

    property_get("persist.camera.perflock.enable", value, "1");
    mPerfLockEnable = atoi(value);
    mCurrentPowerHintEnable = 0;
#ifdef HAS_MULTIMEDIA_HINTS
    if (hw_get_module(POWER_HARDWARE_MODULE_ID, (const hw_module_t **)&m_pPowerModule)) {
        ALOGE("%s: %s module not found", __func__, POWER_HARDWARE_MODULE_ID);
    }
#endif

    if (mPerfLockEnable) {
        perf_lock_acq = NULL;
        perf_lock_rel = NULL;
        mPerfLockHandle = -1;
        /* Retrieve name of vendor extension library */
        if (property_get("ro.vendor.extension_library", value, NULL) <= 0) {
            goto cleanup;
        }

        mDlHandle = dlopen(value, RTLD_NOW | RTLD_LOCAL);
        if (mDlHandle == NULL) {
            goto cleanup;
        }

        dlerror();

        perf_lock_acq = (int (*) (int, int, int[], int))dlsym(mDlHandle, "perf_lock_acq");
        if ((rc = dlerror()) != NULL) {
            ALOGE("%s: failed to perf_lock_acq function handle", __func__);
            goto cleanup;
        }

        perf_lock_rel = (int (*) (int))dlsym(mDlHandle, "perf_lock_rel");
        if ((rc = dlerror()) != NULL) {
            ALOGE("%s: failed to perf_lock_rel function handle", __func__);
            goto cleanup;
        }
        CDBG("%s X", __func__);
        return;

cleanup:
        perf_lock_acq  = NULL;
        perf_lock_rel  = NULL;
        mPerfLockEnable = 0;
        if (mDlHandle) {
            dlclose(mDlHandle);
            mDlHandle = NULL;
        }
    }
    CDBG("%s X", __func__);
}

/*===========================================================================
 * FUNCTION   : lock_deinit
 *
 * DESCRIPTION: deinitialize the perf lock parameters
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLock::lock_deinit()
{
    Mutex::Autolock lock(mLock);
    if (mPerfLockEnable) {
        CDBG("%s E", __func__);
        if (mDlHandle) {
            perf_lock_acq  = NULL;
            perf_lock_rel  = NULL;

            dlclose(mDlHandle);
            mDlHandle       = NULL;
        }
        mPerfLockEnable = 0;
        CDBG("%s X", __func__);
    }
}

/*===========================================================================
 * FUNCTION   : isTimerReset
 *
 * DESCRIPTION: Check if timout duration is reached
 *
 * PARAMETERS : None
 *
 * RETURN     : true if timeout reached
 *              false if timeout not reached
 *
 *==========================================================================*/
bool QCameraPerfLock::isTimerReset()
{
    Mutex::Autolock lock(mLock);
    if (mPerfLockEnable && mTimerSet) {
        nsecs_t timeDiff = systemTime() - mStartTimeofLock;
        if (ns2ms(timeDiff) > (uint32_t)mPerfLockTimeout) {
            mTimerSet = 0;
            return true;
        }
    }
    return false;
}

/*===========================================================================
 * FUNCTION   : start_timer
 *
 * DESCRIPTION: get the start of the timer
 *
 * PARAMETERS :
 *  @timer_val: timer duration in milliseconds
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *
 *==========================================================================*/
void QCameraPerfLock::startTimer(uint32_t timer_val)
{
    mStartTimeofLock = systemTime();
    mTimerSet = 1;
    mPerfLockTimeout = timer_val;
}

/*===========================================================================
 * FUNCTION   : lock_acq_timed
 *
 * DESCRIPTION: Acquire the performance lock for the specified duration.
 *              If an existing lock timeout has not elapsed, extend the
 *              lock further for the specified duration
 *
 * PARAMETERS :
 *  @timer_val: lock duration
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *
 *==========================================================================*/
int32_t QCameraPerfLock::lock_acq_timed(int32_t timer_val)
{
    int32_t ret = -1;

    CDBG("%s E", __func__);
    Mutex::Autolock lock(mLock);

    if (mPerfLockEnable) {
        int32_t perf_lock_params[] = {
                ALL_CPUS_PWR_CLPS_DIS,
                CPU0_MIN_FREQ_TURBO_MAX,
                CPU4_MIN_FREQ_TURBO_MAX
        };
        if (mTimerSet) {
            nsecs_t curElapsedTime = systemTime() - mStartTimeofLock;
            int32_t pendingTimeout = mPerfLockTimeout - ns2ms(curElapsedTime);
            timer_val += pendingTimeout;
        }
        startTimer(timer_val);

        // Disable power hint when acquiring the perf lock
        if (mCurrentPowerHintEnable) {
            CDBG_HIGH("%s mCurrentPowerHintEnable %d", __func__ ,mCurrentPowerHintEnable);
            powerHintInternal(mCurrentPowerHint, 0);
        }

        if ((NULL != perf_lock_acq) && (mPerfLockHandleTimed < 0)) {
            ret = (*perf_lock_acq)(mPerfLockHandleTimed, timer_val, perf_lock_params,
                    sizeof(perf_lock_params) / sizeof(int32_t));
            CDBG("%s ret %d", __func__, ret);
            if (ret < 0) {
                ALOGE("%s: failed to acquire lock", __func__);
            } else {
                mPerfLockHandleTimed = ret;
            }
        }
        CDBG("%s perf_handle_acq %d ",__func__, mPerfLockHandleTimed);
    }

    CDBG("%s X", __func__);
    return ret;
}

/*===========================================================================
 * FUNCTION   : lock_acq
 *
 * DESCRIPTION: acquire the performance lock
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *
 *==========================================================================*/
int32_t QCameraPerfLock::lock_acq()
{
    int32_t ret = -1;

    CDBG("%s E", __func__);
    Mutex::Autolock lock(mLock);

    if (mPerfLockEnable) {
        int32_t perf_lock_params[] = {
                ALL_CPUS_PWR_CLPS_DIS,
                CPU0_MIN_FREQ_TURBO_MAX,
                CPU4_MIN_FREQ_TURBO_MAX
        };

        // Disable power hint when acquiring the perf lock
        if (mCurrentPowerHintEnable) {
            powerHintInternal(mCurrentPowerHint, 0);
        }

        if ((NULL != perf_lock_acq) && (mPerfLockHandle < 0)) {
            ret = (*perf_lock_acq)(mPerfLockHandle, ONE_SEC, perf_lock_params,
                    sizeof(perf_lock_params) / sizeof(int32_t));
            CDBG("%s ret %d", __func__, ret);
            if (ret < 0) {
                ALOGE("%s: failed to acquire lock", __func__);
            } else {
                mPerfLockHandle = ret;
            }
        }
        CDBG("%s perf_handle_acq %d ",__func__, mPerfLockHandle);
    }

    CDBG("%s X", __func__);
    return ret;
}

/*===========================================================================
 * FUNCTION   : lock_rel_timed
 *
 * DESCRIPTION: release the performance lock
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *
 *==========================================================================*/
int32_t QCameraPerfLock::lock_rel_timed()
{
    int ret = -1;
    Mutex::Autolock lock(mLock);
    if (mPerfLockEnable) {
        CDBG("%s E", __func__);
        if (mPerfLockHandleTimed < 0) {
            ALOGE("%s: mPerfLockHandle < 0,check if lock is acquired", __func__);
            return ret;
        }
        CDBG("%s perf_handle_rel %d ",__func__, mPerfLockHandleTimed);

        if ((NULL != perf_lock_rel) && (0 <= mPerfLockHandleTimed)) {
            ret = (*perf_lock_rel)(mPerfLockHandleTimed);
            if (ret < 0) {
                ALOGE("%s: failed to release lock", __func__);
            }
            mPerfLockHandleTimed = -1;
        }

        if ((mCurrentPowerHintEnable == 1) && (mTimerSet == 0)) {
            powerHintInternal(mCurrentPowerHint, mCurrentPowerHintEnable);
        }
        CDBG("%s X", __func__);
    }
    return ret;
}

/*===========================================================================
 * FUNCTION   : lock_rel
 *
 * DESCRIPTION: release the performance lock
 *
 * PARAMETERS :
 *   None
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *
 *==========================================================================*/
int32_t QCameraPerfLock::lock_rel()
{
    int ret = -1;
    Mutex::Autolock lock(mLock);
    if (mPerfLockEnable) {
        CDBG("%s E", __func__);
        if (mPerfLockHandle < 0) {
            ALOGE("%s: mPerfLockHandle < 0,check if lock is acquired", __func__);
            return ret;
        }
        CDBG("%s perf_handle_rel %d ",__func__, mPerfLockHandle);

        if ((NULL != perf_lock_rel) && (0 <= mPerfLockHandle)) {
            ret = (*perf_lock_rel)(mPerfLockHandle);
            if (ret < 0) {
                ALOGE("%s: failed to release lock", __func__);
            }
            mPerfLockHandle = -1;
        }

        if ((mCurrentPowerHintEnable == 1) && (mTimerSet == 0)) {
            powerHintInternal(mCurrentPowerHint, mCurrentPowerHintEnable);
        }
        CDBG("%s X", __func__);
    }
    return ret;
}

/*===========================================================================
 * FUNCTION   : powerHintInternal
 *
 * DESCRIPTION: Sets the requested power hint and state to power HAL.
 *
 * PARAMETERS :
 * enable     : Enable power hint if set to 1. Disable if set to 0.
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLock::powerHintInternal(power_hint_t hint, uint32_t enable)
{
#ifdef HAS_MULTIMEDIA_HINTS
    if (m_pPowerModule != NULL) {
        if (enable == 1) {
            m_pPowerModule->powerHint(m_pPowerModule, hint, (void *)"state=1");
        }
        else {
            m_pPowerModule->powerHint(m_pPowerModule, hint, (void *)"state=0");
        }
    }
#endif
}

/*===========================================================================
 * FUNCTION   : powerHint
 *
 * DESCRIPTION: Sets the requested power hint and state to power HAL.
 *
 * PARAMETERS :
 * hint       : Power hint
 * enable     : Enable power hint if set to 1. Disable if set to 0.
 * RETURN     : void
 *
 *==========================================================================*/
void QCameraPerfLock::powerHint(power_hint_t hint, uint32_t enable)
{
#ifdef HAS_MULTIMEDIA_HINTS
    if (mCurrentPowerHintEnable) {
        //disable previous hint
        powerHintInternal(mCurrentPowerHint, 0);
    }
    powerHintInternal(hint, enable);

    mCurrentPowerHint       = hint;
    mCurrentPowerHintEnable = enable;
#endif
}

}; // namespace qcamera
