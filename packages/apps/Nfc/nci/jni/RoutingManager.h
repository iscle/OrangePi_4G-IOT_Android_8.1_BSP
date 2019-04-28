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

/*
 *  Manage the listen-mode routing table.
 */
#pragma once
#include "SyncEvent.h"
#include "NfcJniUtil.h"
#include "RouteDataSet.h"
#include <vector>
extern "C"
{
    #include "nfa_api.h"
    #include "nfa_ee_api.h"
}

class RoutingManager
{
public:
    static RoutingManager& getInstance ();
    bool initialize(nfc_jni_native_data* native);
    void enableRoutingToHost();
    void disableRoutingToHost();
    bool addAidRouting(const uint8_t* aid, uint8_t aidLen, int route, int aidInfo);
    bool removeAidRouting(const uint8_t* aid, uint8_t aidLen);
    bool commitRouting();
    int registerT3tIdentifier(uint8_t* t3tId, uint8_t t3tIdLen);
    void deregisterT3tIdentifier(int handle);
    void onNfccShutdown();
    int registerJniFunctions (JNIEnv* e);
private:
    RoutingManager();
    ~RoutingManager();
    RoutingManager(const RoutingManager&);
    RoutingManager& operator=(const RoutingManager&);

    void handleData (uint8_t technology, const uint8_t* data, uint32_t dataLen, tNFA_STATUS status);
    void notifyActivated (uint8_t technology);
    void notifyDeactivated (uint8_t technology);

    // See AidRoutingManager.java for corresponding
    // AID_MATCHING_ constants

    // Every routing table entry is matched exact (BCM20793)
    static const int AID_MATCHING_EXACT_ONLY = 0x00;
    // Every routing table entry can be matched either exact or prefix
    static const int AID_MATCHING_EXACT_OR_PREFIX = 0x01;
    // Every routing table entry is matched as a prefix
    static const int AID_MATCHING_PREFIX_ONLY = 0x02;

    static void nfaEeCallback (tNFA_EE_EVT event, tNFA_EE_CBACK_DATA* eventData);
    static void stackCallback (uint8_t event, tNFA_CONN_EVT_DATA* eventData);
    static void nfcFCeCallback (uint8_t event, tNFA_CONN_EVT_DATA* eventData);

    static int com_android_nfc_cardemulation_doGetDefaultRouteDestination (JNIEnv* e);
    static int com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination (JNIEnv* e);
    static int com_android_nfc_cardemulation_doGetAidMatchingMode (JNIEnv* e);

    std::vector<uint8_t> mRxDataBuffer;

    // Fields below are final after initialize()
    nfc_jni_native_data* mNativeData;
    int mDefaultEe;
    int mDefaultEeNfcF;
    int mOffHostEe;
    int mActiveSe;
    int mActiveSeNfcF;
    int mAidMatchingMode;
    int mNfcFOnDhHandle;
    bool mReceivedEeInfo;
    tNFA_EE_DISCOVER_REQ mEeInfo;
    tNFA_TECHNOLOGY_MASK mSeTechMask;
    static const JNINativeMethod sMethods [];
    SyncEvent mEeRegisterEvent;
    SyncEvent mRoutingEvent;
    SyncEvent mEeUpdateEvent;
    SyncEvent mEeInfoEvent;
    SyncEvent mEeSetModeEvent;
};

