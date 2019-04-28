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

#include <log/log.h>

#include <ScopedLocalRef.h>
#include <JNIHelp.h>
#include "config.h"
#include "JavaClassConstants.h"
#include "RoutingManager.h"

extern "C"
{
    #include "nfa_ee_api.h"
    #include "nfa_ce_api.h"
}
extern bool gActivated;
extern SyncEvent gDeactivatedEvent;


const JNINativeMethod RoutingManager::sMethods [] =
{
    {"doGetDefaultRouteDestination", "()I", (void*) RoutingManager::com_android_nfc_cardemulation_doGetDefaultRouteDestination},
    {"doGetDefaultOffHostRouteDestination", "()I", (void*) RoutingManager::com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination},
    {"doGetAidMatchingMode", "()I", (void*) RoutingManager::com_android_nfc_cardemulation_doGetAidMatchingMode}
};

static const int MAX_NUM_EE = 5;

RoutingManager::RoutingManager ()
{
    static const char fn [] = "RoutingManager::RoutingManager()";
    unsigned long num = 0;

    // Get the active SE
    if (GetNumValue("ACTIVE_SE", &num, sizeof(num)))
        mActiveSe = num;
    else
        mActiveSe = 0x00;

    // Get the active SE for Nfc-F
    if (GetNumValue("ACTIVE_SE_NFCF", &num, sizeof(num)))
        mActiveSeNfcF = num;
    else
        mActiveSeNfcF = 0x00;
    ALOGV("%s: Active SE for Nfc-F is 0x%02X", fn, mActiveSeNfcF);

    // Get the "default" route
    if (GetNumValue("DEFAULT_ISODEP_ROUTE", &num, sizeof(num)))
        mDefaultEe = num;
    else
        mDefaultEe = 0x00;
    ALOGV("%s: default route is 0x%02X", fn, mDefaultEe);

    // Get the "default" route for Nfc-F
    if (GetNumValue("DEFAULT_NFCF_ROUTE", &num, sizeof(num)))
        mDefaultEeNfcF = num;
    else
        mDefaultEeNfcF = 0x00;
    ALOGV("%s: default route for Nfc-F is 0x%02X", fn, mDefaultEeNfcF);

    // Get the default "off-host" route.  This is hard-coded at the Java layer
    // but we can override it here to avoid forcing Java changes.
    if (GetNumValue("DEFAULT_OFFHOST_ROUTE", &num, sizeof(num)))
        mOffHostEe = num;
    else
        mOffHostEe = 0xf4;

    if (GetNumValue("AID_MATCHING_MODE", &num, sizeof(num)))
        mAidMatchingMode = num;
    else
        mAidMatchingMode = AID_MATCHING_EXACT_ONLY;

    ALOGV("%s: mOffHostEe=0x%02X", fn, mOffHostEe);

    memset (&mEeInfo, 0, sizeof(mEeInfo));
    mReceivedEeInfo = false;
    mSeTechMask = 0x00;

    mNfcFOnDhHandle = NFA_HANDLE_INVALID;
}

RoutingManager::~RoutingManager ()
{
    NFA_EeDeregister (nfaEeCallback);
}

bool RoutingManager::initialize (nfc_jni_native_data* native)
{
    static const char fn [] = "RoutingManager::initialize()";
    mNativeData = native;

    tNFA_STATUS nfaStat;
    {
        SyncEventGuard guard (mEeRegisterEvent);
        ALOGV("%s: try ee register", fn);
        nfaStat = NFA_EeRegister (nfaEeCallback);
        if (nfaStat != NFA_STATUS_OK)
        {
            ALOGE("%s: fail ee register; error=0x%X", fn, nfaStat);
            return false;
        }
        mEeRegisterEvent.wait ();
    }

    mRxDataBuffer.clear ();

    if ((mActiveSe != 0) || (mActiveSeNfcF != 0))
    {
        ALOGV("%s: Technology Routing (NfcASe:0x%02x, NfcFSe:0x%02x)", fn, mActiveSe, mActiveSeNfcF);
        {
            // Wait for EE info if needed
            SyncEventGuard guard (mEeInfoEvent);
            if (!mReceivedEeInfo)
            {
                ALOGI("Waiting for EE info");
                mEeInfoEvent.wait();
            }
        }

        ALOGV("%s: Number of EE is %d", fn, mEeInfo.num_ee);
        for (uint8_t i = 0; i < mEeInfo.num_ee; i++)
        {
            tNFA_HANDLE eeHandle = mEeInfo.ee_disc_info[i].ee_handle;
            tNFA_TECHNOLOGY_MASK seTechMask = 0;

            ALOGV("%s   EE[%u] Handle: 0x%04x  techA: 0x%02x  techB: 0x%02x  techF: 0x%02x  techBprime: 0x%02x",
                   fn, i, eeHandle,
                   mEeInfo.ee_disc_info[i].la_protocol,
                   mEeInfo.ee_disc_info[i].lb_protocol,
                   mEeInfo.ee_disc_info[i].lf_protocol,
                   mEeInfo.ee_disc_info[i].lbp_protocol);
            if ((mActiveSe != 0) && (eeHandle == (mActiveSe | NFA_HANDLE_GROUP_EE)))
            {
                if (mEeInfo.ee_disc_info[i].la_protocol != 0)
                    seTechMask |= NFA_TECHNOLOGY_MASK_A;
            }
            if ((mActiveSeNfcF != 0) && (eeHandle == (mActiveSeNfcF | NFA_HANDLE_GROUP_EE)))
            {
                if (mEeInfo.ee_disc_info[i].lf_protocol != 0)
                    seTechMask |= NFA_TECHNOLOGY_MASK_F;
            }

            ALOGV("%s: seTechMask[%u]=0x%02x", fn, i, seTechMask);
            if (seTechMask != 0x00)
            {
                ALOGV("Configuring tech mask 0x%02x on EE 0x%04x", seTechMask, eeHandle);

                nfaStat = NFA_CeConfigureUiccListenTech(eeHandle, seTechMask);
                if (nfaStat != NFA_STATUS_OK)
                    ALOGE("Failed to configure UICC listen technologies.");

                // Set technology routes to UICC if it's there
                nfaStat = NFA_EeSetDefaultTechRouting(eeHandle, seTechMask, seTechMask, seTechMask);
                if (nfaStat != NFA_STATUS_OK)
                    ALOGE("Failed to configure UICC technology routing.");

                mSeTechMask |= seTechMask;
            }
        }
    }

    // Tell the host-routing to only listen on Nfc-A
    nfaStat = NFA_CeSetIsoDepListenTech(NFA_TECHNOLOGY_MASK_A);
    if (nfaStat != NFA_STATUS_OK)
        ALOGE("Failed to configure CE IsoDep technologies");

    // Register a wild-card for AIDs routed to the host
    nfaStat = NFA_CeRegisterAidOnDH (NULL, 0, stackCallback);
    if (nfaStat != NFA_STATUS_OK)
        ALOGE("Failed to register wildcard AID for DH");

    return true;
}

RoutingManager& RoutingManager::getInstance ()
{
    static RoutingManager manager;
    return manager;
}

void RoutingManager::enableRoutingToHost()
{
    tNFA_STATUS nfaStat;
    tNFA_TECHNOLOGY_MASK techMask;
    tNFA_PROTOCOL_MASK protoMask;
    SyncEventGuard guard (mRoutingEvent);

    // Set default routing at one time when the NFCEE IDs for Nfc-A and Nfc-F are same
    if (mDefaultEe == mDefaultEeNfcF)
    {
        // Route Nfc-A/Nfc-F to host if we don't have a SE
        techMask = (mSeTechMask ^ (NFA_TECHNOLOGY_MASK_A | NFA_TECHNOLOGY_MASK_F));
        if (techMask != 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEe, techMask, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-A/Nfc-F");
        }
        // Default routing for IsoDep and T3T protocol
        protoMask = (NFA_PROTOCOL_MASK_ISO_DEP | NFA_PROTOCOL_MASK_T3T);
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEe, protoMask, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for IsoDep and T3T");
    }
    else
    {
        // Route Nfc-A to host if we don't have a SE
        techMask = NFA_TECHNOLOGY_MASK_A;
        if ((mSeTechMask & NFA_TECHNOLOGY_MASK_A) == 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEe, techMask, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-A");
        }
        // Default routing for IsoDep protocol
        protoMask = NFA_PROTOCOL_MASK_ISO_DEP;
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEe, protoMask, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for IsoDep");

        // Route Nfc-F to host if we don't have a SE
        techMask = NFA_TECHNOLOGY_MASK_F;
        if ((mSeTechMask & NFA_TECHNOLOGY_MASK_F) == 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEeNfcF, techMask, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-F");
        }
        // Default routing for T3T protocol
        protoMask = NFA_PROTOCOL_MASK_T3T;
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEeNfcF, protoMask, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for T3T");
    }
}

void RoutingManager::disableRoutingToHost()
{
    tNFA_STATUS nfaStat;
    tNFA_TECHNOLOGY_MASK techMask;
    SyncEventGuard guard (mRoutingEvent);

    // Set default routing at one time when the NFCEE IDs for Nfc-A and Nfc-F are same
    if (mDefaultEe == mDefaultEeNfcF)
    {
        // Default routing for Nfc-A/Nfc-F technology if we don't have a SE
        techMask = (mSeTechMask ^ (NFA_TECHNOLOGY_MASK_A | NFA_TECHNOLOGY_MASK_F));
        if (techMask != 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEe, 0, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-A/Nfc-F");
        }
        // Default routing for IsoDep and T3T protocol
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEe, 0, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for IsoDep and T3T");
    }
    else
    {
        // Default routing for Nfc-A technology if we don't have a SE
        if ((mSeTechMask & NFA_TECHNOLOGY_MASK_A) == 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEe, 0, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-A");
        }
        // Default routing for IsoDep protocol
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEe, 0, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for IsoDep");

        // Default routing for Nfc-F technology if we don't have a SE
        if ((mSeTechMask & NFA_TECHNOLOGY_MASK_F) == 0)
        {
            nfaStat = NFA_EeSetDefaultTechRouting (mDefaultEeNfcF, 0, 0, 0);
            if (nfaStat == NFA_STATUS_OK)
                mRoutingEvent.wait ();
            else
                ALOGE("Fail to set default tech routing for Nfc-F");
        }
        // Default routing for T3T protocol
        nfaStat = NFA_EeSetDefaultProtoRouting(mDefaultEeNfcF, 0, 0, 0);
        if (nfaStat == NFA_STATUS_OK)
            mRoutingEvent.wait ();
        else
            ALOGE("Fail to set default proto routing for T3T");
    }
}

bool RoutingManager::addAidRouting(const uint8_t* aid, uint8_t aidLen, int route, int aidInfo)
{
    static const char fn [] = "RoutingManager::addAidRouting";
    ALOGV("%s: enter", fn);
    tNFA_STATUS nfaStat = NFA_EeAddAidRouting(route, aidLen, (uint8_t*) aid, 0x01, aidInfo);
    if (nfaStat == NFA_STATUS_OK)
    {
        ALOGV("%s: routed AID", fn);
        return true;
    } else
    {
        ALOGE("%s: failed to route AID", fn);
        return false;
    }
}

bool RoutingManager::removeAidRouting(const uint8_t* aid, uint8_t aidLen)
{
    static const char fn [] = "RoutingManager::removeAidRouting";
    ALOGV("%s: enter", fn);
    tNFA_STATUS nfaStat = NFA_EeRemoveAidRouting(aidLen, (uint8_t*) aid);
    if (nfaStat == NFA_STATUS_OK)
    {
        ALOGV("%s: removed AID", fn);
        return true;
    } else
    {
        ALOGE("%s: failed to remove AID", fn);
        return false;
    }
}

bool RoutingManager::commitRouting()
{
    static const char fn [] = "RoutingManager::commitRouting";
    tNFA_STATUS nfaStat = 0;
    ALOGV("%s", fn);
    {
        SyncEventGuard guard (mEeUpdateEvent);
        nfaStat = NFA_EeUpdateNow();
        if (nfaStat == NFA_STATUS_OK)
        {
            mEeUpdateEvent.wait (); //wait for NFA_EE_UPDATED_EVT
        }
    }
    return (nfaStat == NFA_STATUS_OK);
}

void RoutingManager::onNfccShutdown ()
{
    static const char fn [] = "RoutingManager:onNfccShutdown";
    if (mActiveSe == 0x00) return;

    tNFA_STATUS nfaStat = NFA_STATUS_FAILED;
    uint8_t actualNumEe = MAX_NUM_EE;
    tNFA_EE_INFO eeInfo[MAX_NUM_EE];

    memset (&eeInfo, 0, sizeof(eeInfo));
    if ((nfaStat = NFA_EeGetInfo (&actualNumEe, eeInfo)) != NFA_STATUS_OK)
    {
        ALOGE("%s: fail get info; error=0x%X", fn, nfaStat);
        return;
    }
    if (actualNumEe != 0)
    {
        for (uint8_t xx = 0; xx < actualNumEe; xx++)
        {
            if ((eeInfo[xx].num_interface != 0)
                && (eeInfo[xx].ee_interface[0] != NCI_NFCEE_INTERFACE_HCI_ACCESS)
                && (eeInfo[xx].ee_status == NFA_EE_STATUS_ACTIVE))
            {
                ALOGV("%s: Handle: 0x%04x Change Status Active to Inactive", fn, eeInfo[xx].ee_handle);
                SyncEventGuard guard (mEeSetModeEvent);
                if ((nfaStat = NFA_EeModeSet (eeInfo[xx].ee_handle, NFA_EE_MD_DEACTIVATE)) == NFA_STATUS_OK)
                {
                    mEeSetModeEvent.wait (); //wait for NFA_EE_MODE_SET_EVT
                }
                else
                {
                    ALOGE("Failed to set EE inactive");
                }
            }
        }
    }
    else
    {
        ALOGV("%s: No active EEs found", fn);
    }
}

void RoutingManager::notifyActivated (uint8_t technology)
{
    JNIEnv* e = NULL;
    ScopedAttach attach(mNativeData->vm, &e);
    if (e == NULL)
    {
        ALOGE("jni env is null");
        return;
    }

    e->CallVoidMethod (mNativeData->manager, android::gCachedNfcManagerNotifyHostEmuActivated, (int)technology);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE("fail notify");
    }
}

void RoutingManager::notifyDeactivated (uint8_t technology)
{
    mRxDataBuffer.clear();
    JNIEnv* e = NULL;
    ScopedAttach attach(mNativeData->vm, &e);
    if (e == NULL)
    {
        ALOGE("jni env is null");
        return;
    }

    e->CallVoidMethod (mNativeData->manager, android::gCachedNfcManagerNotifyHostEmuDeactivated, (int)technology);
    if (e->ExceptionCheck())
    {
        e->ExceptionClear();
        ALOGE("fail notify");
    }
}

void RoutingManager::handleData (uint8_t technology, const uint8_t* data, uint32_t dataLen, tNFA_STATUS status)
{
    if (status == NFA_STATUS_CONTINUE)
    {
        if (dataLen > 0)
        {
            mRxDataBuffer.insert (mRxDataBuffer.end(), &data[0], &data[dataLen]); //append data; more to come
        }
        return; //expect another NFA_CE_DATA_EVT to come
    }
    else if (status == NFA_STATUS_OK)
    {
        if (dataLen > 0)
        {
            mRxDataBuffer.insert (mRxDataBuffer.end(), &data[0], &data[dataLen]); //append data
        }
        //entire data packet has been received; no more NFA_CE_DATA_EVT
    }
    else if (status == NFA_STATUS_FAILED)
    {
        ALOGE("RoutingManager::handleData: read data fail");
        goto TheEnd;
    }

    {
        JNIEnv* e = NULL;
        ScopedAttach attach(mNativeData->vm, &e);
        if (e == NULL)
        {
            ALOGE("jni env is null");
            goto TheEnd;
        }

        ScopedLocalRef<jobject> dataJavaArray(e, e->NewByteArray(mRxDataBuffer.size()));
        if (dataJavaArray.get() == NULL)
        {
            ALOGE("fail allocate array");
            goto TheEnd;
        }

        e->SetByteArrayRegion ((jbyteArray)dataJavaArray.get(), 0, mRxDataBuffer.size(),
                (jbyte *)(&mRxDataBuffer[0]));
        if (e->ExceptionCheck())
        {
            e->ExceptionClear();
            ALOGE("fail fill array");
            goto TheEnd;
        }

        e->CallVoidMethod (mNativeData->manager, android::gCachedNfcManagerNotifyHostEmuData,
                (int)technology, dataJavaArray.get());
        if (e->ExceptionCheck())
        {
            e->ExceptionClear();
            ALOGE("fail notify");
        }
    }
TheEnd:
    mRxDataBuffer.clear();
}

void RoutingManager::stackCallback (uint8_t event, tNFA_CONN_EVT_DATA* eventData)
{
    static const char fn [] = "RoutingManager::stackCallback";
    ALOGV("%s: event=0x%X", fn, event);
    RoutingManager& routingManager = RoutingManager::getInstance();

    switch (event)
    {
    case NFA_CE_REGISTERED_EVT:
        {
            tNFA_CE_REGISTERED& ce_registered = eventData->ce_registered;
            ALOGV("%s: NFA_CE_REGISTERED_EVT; status=0x%X; h=0x%X", fn, ce_registered.status, ce_registered.handle);
        }
        break;

    case NFA_CE_DEREGISTERED_EVT:
        {
            tNFA_CE_DEREGISTERED& ce_deregistered = eventData->ce_deregistered;
            ALOGV("%s: NFA_CE_DEREGISTERED_EVT; h=0x%X", fn, ce_deregistered.handle);
        }
        break;

    case NFA_CE_ACTIVATED_EVT:
        {
            routingManager.notifyActivated(NFA_TECHNOLOGY_MASK_A);
        }
        break;

    case NFA_DEACTIVATED_EVT:
    case NFA_CE_DEACTIVATED_EVT:
        {
            ALOGV("%s: NFA_DEACTIVATED_EVT, NFA_CE_DEACTIVATED_EVT", fn);
            routingManager.notifyDeactivated(NFA_TECHNOLOGY_MASK_A);
            SyncEventGuard g (gDeactivatedEvent);
            gActivated = false; //guard this variable from multi-threaded access
            gDeactivatedEvent.notifyOne ();
        }
        break;

    case NFA_CE_DATA_EVT:
        {
            tNFA_CE_DATA& ce_data = eventData->ce_data;
            ALOGV("%s: NFA_CE_DATA_EVT; stat=0x%X; h=0x%X; data len=%u", fn, ce_data.status, ce_data.handle, ce_data.len);
            getInstance().handleData(NFA_TECHNOLOGY_MASK_A, ce_data.p_data, ce_data.len, ce_data.status);
        }
        break;
    }
}
/*******************************************************************************
**
** Function:        nfaEeCallback
**
** Description:     Receive execution environment-related events from stack.
**                  event: Event code.
**                  eventData: Event data.
**
** Returns:         None
**
*******************************************************************************/
void RoutingManager::nfaEeCallback (tNFA_EE_EVT event, tNFA_EE_CBACK_DATA* eventData)
{
    static const char fn [] = "RoutingManager::nfaEeCallback";

    RoutingManager& routingManager = RoutingManager::getInstance();

    switch (event)
    {
    case NFA_EE_REGISTER_EVT:
        {
            SyncEventGuard guard (routingManager.mEeRegisterEvent);
            ALOGV("%s: NFA_EE_REGISTER_EVT; status=%u", fn, eventData->ee_register);
            routingManager.mEeRegisterEvent.notifyOne();
        }
        break;

    case NFA_EE_MODE_SET_EVT:
        {
            SyncEventGuard guard (routingManager.mEeSetModeEvent);
            ALOGV("%s: NFA_EE_MODE_SET_EVT; status: 0x%04X  handle: 0x%04X  ", fn,
                    eventData->mode_set.status, eventData->mode_set.ee_handle);
            routingManager.mEeSetModeEvent.notifyOne();
        }
        break;

    case NFA_EE_SET_TECH_CFG_EVT:
        {
            ALOGV("%s: NFA_EE_SET_TECH_CFG_EVT; status=0x%X", fn, eventData->status);
            SyncEventGuard guard(routingManager.mRoutingEvent);
            routingManager.mRoutingEvent.notifyOne();
        }
        break;

    case NFA_EE_SET_PROTO_CFG_EVT:
        {
            ALOGV("%s: NFA_EE_SET_PROTO_CFG_EVT; status=0x%X", fn, eventData->status);
            SyncEventGuard guard(routingManager.mRoutingEvent);
            routingManager.mRoutingEvent.notifyOne();
        }
        break;

    case NFA_EE_ACTION_EVT:
        {
            tNFA_EE_ACTION& action = eventData->action;
            if (action.trigger == NFC_EE_TRIG_SELECT)
                ALOGV("%s: NFA_EE_ACTION_EVT; h=0x%X; trigger=select (0x%X)", fn, action.ee_handle, action.trigger);
            else if (action.trigger == NFC_EE_TRIG_APP_INIT)
            {
                tNFC_APP_INIT& app_init = action.param.app_init;
                ALOGV("%s: NFA_EE_ACTION_EVT; h=0x%X; trigger=app-init (0x%X); aid len=%u; data len=%u", fn,
                        action.ee_handle, action.trigger, app_init.len_aid, app_init.len_data);
            }
            else if (action.trigger == NFC_EE_TRIG_RF_PROTOCOL)
                ALOGV("%s: NFA_EE_ACTION_EVT; h=0x%X; trigger=rf protocol (0x%X)", fn, action.ee_handle, action.trigger);
            else if (action.trigger == NFC_EE_TRIG_RF_TECHNOLOGY)
                ALOGV("%s: NFA_EE_ACTION_EVT; h=0x%X; trigger=rf tech (0x%X)", fn, action.ee_handle, action.trigger);
            else
                ALOGV("%s: NFA_EE_ACTION_EVT; h=0x%X; unknown trigger (0x%X)", fn, action.ee_handle, action.trigger);
        }
        break;

    case NFA_EE_DISCOVER_REQ_EVT:
        {
            ALOGV("%s: NFA_EE_DISCOVER_REQ_EVT; status=0x%X; num ee=%u", __func__,
                    eventData->discover_req.status, eventData->discover_req.num_ee);
            SyncEventGuard guard (routingManager.mEeInfoEvent);
            memcpy (&routingManager.mEeInfo, &eventData->discover_req, sizeof(routingManager.mEeInfo));
            routingManager.mReceivedEeInfo = true;
            routingManager.mEeInfoEvent.notifyOne();
        }
        break;

    case NFA_EE_NO_CB_ERR_EVT:
        ALOGV("%s: NFA_EE_NO_CB_ERR_EVT  status=%u", fn, eventData->status);
        break;

    case NFA_EE_ADD_AID_EVT:
        {
            ALOGV("%s: NFA_EE_ADD_AID_EVT  status=%u", fn, eventData->status);
        }
        break;

    case NFA_EE_REMOVE_AID_EVT:
        {
            ALOGV("%s: NFA_EE_REMOVE_AID_EVT  status=%u", fn, eventData->status);
        }
        break;

    case NFA_EE_NEW_EE_EVT:
        {
            ALOGV("%s: NFA_EE_NEW_EE_EVT  h=0x%X; status=%u", fn,
                eventData->new_ee.ee_handle, eventData->new_ee.ee_status);
        }
        break;

    case NFA_EE_UPDATED_EVT:
        {
            ALOGV("%s: NFA_EE_UPDATED_EVT", fn);
            SyncEventGuard guard(routingManager.mEeUpdateEvent);
            routingManager.mEeUpdateEvent.notifyOne();
        }
        break;

    default:
        ALOGV("%s: unknown event=%u ????", fn, event);
        break;
    }
}

int RoutingManager::registerT3tIdentifier(uint8_t* t3tId, uint8_t t3tIdLen)
{
    static const char fn [] = "RoutingManager::registerT3tIdentifier";

    ALOGV("%s: Start to register NFC-F system on DH", fn);

    if (t3tIdLen != (2 + NCI_RF_F_UID_LEN + NCI_T3T_PMM_LEN))
    {
        ALOGE("%s: Invalid length of T3T Identifier", fn);
        return NFA_HANDLE_INVALID;
    }

    SyncEventGuard guard (mRoutingEvent);
    mNfcFOnDhHandle = NFA_HANDLE_INVALID;

    int systemCode;
    uint8_t nfcid2[NCI_RF_F_UID_LEN];
    uint8_t t3tPmm[NCI_T3T_PMM_LEN];

    systemCode = (((int)t3tId[0] << 8) | ((int)t3tId[1] << 0));
    memcpy(nfcid2, t3tId + 2, NCI_RF_F_UID_LEN);
    memcpy(t3tPmm, t3tId + 10, NCI_T3T_PMM_LEN);

    tNFA_STATUS nfaStat = NFA_CeRegisterFelicaSystemCodeOnDH (systemCode, nfcid2, t3tPmm, nfcFCeCallback);
    if (nfaStat == NFA_STATUS_OK)
    {
        mRoutingEvent.wait ();
    }
    else
    {
        ALOGE("%s: Fail to register NFC-F system on DH", fn);
        return NFA_HANDLE_INVALID;
    }

    ALOGV("%s: Succeed to register NFC-F system on DH", fn);

    return mNfcFOnDhHandle;
}

void RoutingManager::deregisterT3tIdentifier(int handle)
{
    static const char fn [] = "RoutingManager::deregisterT3tIdentifier";

    ALOGV("%s: Start to deregister NFC-F system on DH", fn);

    SyncEventGuard guard (mRoutingEvent);
    tNFA_STATUS nfaStat = NFA_CeDeregisterFelicaSystemCodeOnDH (handle);
    if (nfaStat == NFA_STATUS_OK)
    {
        mRoutingEvent.wait ();
        ALOGV("%s: Succeeded in deregistering NFC-F system on DH", fn);
    }
    else
    {
        ALOGE("%s: Fail to deregister NFC-F system on DH", fn);
    }
}

void RoutingManager::nfcFCeCallback (uint8_t event, tNFA_CONN_EVT_DATA* eventData)
{
    static const char fn [] = "RoutingManager::nfcFCeCallback";
    RoutingManager& routingManager = RoutingManager::getInstance();

    ALOGV("%s: 0x%x", __func__, event);

    switch (event)
    {
    case NFA_CE_REGISTERED_EVT:
        {
            ALOGV("%s: registerd event notified", fn);
            routingManager.mNfcFOnDhHandle = eventData->ce_registered.handle;
            SyncEventGuard guard(routingManager.mRoutingEvent);
            routingManager.mRoutingEvent.notifyOne();
        }
        break;
    case NFA_CE_DEREGISTERED_EVT:
        {
            ALOGV("%s: deregisterd event notified", fn);
            SyncEventGuard guard(routingManager.mRoutingEvent);
            routingManager.mRoutingEvent.notifyOne();
        }
        break;
    case NFA_CE_ACTIVATED_EVT:
        {
            ALOGV("%s: activated event notified", fn);
            routingManager.notifyActivated(NFA_TECHNOLOGY_MASK_F);
        }
        break;
    case NFA_CE_DEACTIVATED_EVT:
        {
            ALOGV("%s: deactivated event notified", fn);
            routingManager.notifyDeactivated(NFA_TECHNOLOGY_MASK_F);
        }
        break;
    case NFA_CE_DATA_EVT:
        {
            ALOGV("%s: data event notified", fn);
            tNFA_CE_DATA& ce_data = eventData->ce_data;
            routingManager.handleData(NFA_TECHNOLOGY_MASK_F, ce_data.p_data, ce_data.len, ce_data.status);
        }
        break;
    default:
        {
            ALOGV("%s: unknown event=%u ????", fn, event);
        }
        break;
    }
}

int RoutingManager::registerJniFunctions (JNIEnv* e)
{
    static const char fn [] = "RoutingManager::registerJniFunctions";
    ALOGV("%s", fn);
    return jniRegisterNativeMethods (e, "com/android/nfc/cardemulation/AidRoutingManager", sMethods, NELEM(sMethods));
}

int RoutingManager::com_android_nfc_cardemulation_doGetDefaultRouteDestination (JNIEnv*)
{
    return getInstance().mDefaultEe;
}

int RoutingManager::com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination (JNIEnv*)
{
    return getInstance().mOffHostEe;
}

int RoutingManager::com_android_nfc_cardemulation_doGetAidMatchingMode (JNIEnv*)
{
    return getInstance().mAidMatchingMode;
}
