/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef DVB_MANAGER_H_
#define DVB_MANAGER_H_

#include <jni.h>
#include <map>

#include "mutex.h"
#include "tunertvinput_jni.h"

class DvbManager {
    static const int NUM_POLLFDS = 1;
    static const int FE_LOCK_CHECK_INTERNAL_US = 100 * 1000;
    static const int FE_CONSECUTIVE_LOCK_SUCCESS_COUNT = 1;
    static const int DVB_ERROR_RETRY_INTERVAL_MS = 100 * 1000;
    static const int DVB_TUNE_STOP_DELAY_MS = 100 * 1000;
    static const int FE_POLL_TIMEOUT_MS = 100;
    static const int PAT_PID = 0;
    static const int DVB_API_VERSION_UNDEFINED = -1;
    static const int DVB_API_VERSION3 = 3;
    static const int DVB_API_VERSION5 = 5;

    static const int FILTER_TYPE_OTHER =
            com_android_tv_tuner_TunerHal_FILTER_TYPE_OTHER;
    static const int FILTER_TYPE_AUDIO =
            com_android_tv_tuner_TunerHal_FILTER_TYPE_AUDIO;
    static const int FILTER_TYPE_VIDEO =
            com_android_tv_tuner_TunerHal_FILTER_TYPE_VIDEO;
    static const int FILTER_TYPE_PCR =
            com_android_tv_tuner_TunerHal_FILTER_TYPE_PCR;

    static const int DELIVERY_SYSTEM_UNDEFINED =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_UNDEFINED;
    static const int DELIVERY_SYSTEM_ATSC =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_ATSC;
    static const int DELIVERY_SYSTEM_DVBC =
            com_android_tv_tuner_TunerHal_DDELIVERY_SYSTEM_DVBC;
    static const int DELIVERY_SYSTEM_DVBS =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_DVBS;
    static const int DELIVERY_SYSTEM_DVBS2 =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_DVBS2;
    static const int DELIVERY_SYSTEM_DVBT =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_DVBT;
    static const int DELIVERY_SYSTEM_DVBT2 =
            com_android_tv_tuner_TunerHal_DELIVERY_SYSTEM_DVBT2;


    int mFeFd;
    int mDemuxFd;
    int mDvrFd;
    int mPatFilterFd;
    int mDvbApiVersion;
    int mDeliverySystemType;
    bool mFeHasLock;
    // Flag for pending tune request. Used for canceling the current tune operation.
    bool volatile mHasPendingTune;
    std::map<int, int> mPidFilters;
    Mutex mFilterLock;
    jmethodID mOpenDvbFrontEndMethodID;
    jmethodID mOpenDvbDemuxMethodID;
    jmethodID mOpenDvbDvrMethodID;

public:
    DvbManager(JNIEnv *env, jobject thiz);
    ~DvbManager();
    int tune(JNIEnv *env, jobject thiz,
            const int frequency, const char *modulationStr, int timeout_ms);
    int stopTune();
    int readTsStream(JNIEnv *env, jobject thiz,
            uint8_t *tsBuffer, int tsBufferSize, int timeout_ms);
    int startTsPidFilter(JNIEnv *env, jobject thiz, int pid, int filterType);
    void closeAllDvbPidFilter();
    void setHasPendingTune(bool hasPendingTune);
    int getDeliverySystemType(JNIEnv *env, jobject thiz);

private:
    int openDvbFe(JNIEnv *env, jobject thiz);
    int openDvbDvr(JNIEnv *env, jobject thiz);
    void closePatFilter();
    void closeDvbFe();
    void closeDvbDvr();
    void reset();
    void resetExceptFe();
    bool isFeLocked();
    // Call to java method
    int openDvbFeFromSystemApi(JNIEnv *env, jobject thiz);
    int openDvbDemuxFromSystemApi(JNIEnv *env, jobject thiz);
    int openDvbDvrFromSystemApi(JNIEnv *env, jobject thiz);
};

#endif  // DVB_MANAGER_H_
