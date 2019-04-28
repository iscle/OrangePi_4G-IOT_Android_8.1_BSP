/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.phone.otasp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;

import static com.android.phone.PhoneGlobals.getPhone;

/**
 * otasp activation service handles all logic related with OTASP call.
 * OTASP is a CDMA-specific feature: OTA or OTASP == Over The Air service provisioning
 * In practice, in a normal successful OTASP call, events come in as follows:
 * - SPL_UNLOCKED within a couple of seconds after the call starts
 * - PRL_DOWNLOADED and MDN_DOWNLOADED and COMMITTED within a span of 2 seconds
 * - poll cdma subscription from RIL after COMMITTED
 * - SIM reloading with provisioned MDN and MIN
 */
public class OtaspActivationService extends Service {
    private static final String TAG = OtaspActivationService.class.getSimpleName();
    private static final boolean DBG = true;
    /* non-interactive otasp number */
    private static final String OTASP_NUMBER = "*22899";

    /**
     * Otasp call follows with SIM reloading which might triggers a retry loop on activation
     * failure. A max retry limit could help prevent retry loop.
     */
    private static final int OTASP_CALL_RETRIES_MAX = 3;
    private static final int OTASP_CALL_RETRY_PERIOD_IN_MS = 3000;
    private static int sOtaspCallRetries = 0;

    /* events */
    private static final int EVENT_CALL_STATE_CHANGED                     = 0;
    private static final int EVENT_CDMA_OTASP_CALL_RETRY                  = 1;
    private static final int EVENT_CDMA_PROVISION_STATUS_UPDATE           = 2;
    private static final int EVENT_SERVICE_STATE_CHANGED                  = 3;
    private static final int EVENT_START_OTASP_CALL                       = 4;

    /* use iccid to detect hot sim swap */
    private static String sIccId = null;

    private Phone mPhone;
    /* committed flag indicates Otasp call succeed */
    private boolean mIsOtaspCallCommitted = false;

    @Override
    public void onCreate() {
        logd("otasp service onCreate");
        mPhone = PhoneGlobals.getPhone();
        if ((sIccId == null) || !sIccId.equals(mPhone.getIccSerialNumber())) {
            // reset to allow activation retry on new sim
            sIccId = mPhone.getIccSerialNumber();
            sOtaspCallRetries = 0;
        }
        sOtaspCallRetries++;
        logd("OTASP call tried " + sOtaspCallRetries + " times");
        if (sOtaspCallRetries > OTASP_CALL_RETRIES_MAX) {
            logd("OTASP call exceeds max retries => activation failed");
            updateActivationState(this, false);
            onComplete();
            return;
        }
        mHandler.sendEmptyMessage(EVENT_START_OTASP_CALL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SERVICE_STATE_CHANGED:
                    logd("EVENT_SERVICE_STATE_CHANGED");
                    onStartOtaspCall();
                    break;
                case EVENT_START_OTASP_CALL:
                    logd("EVENT_START_OTASP_CALL");
                    onStartOtaspCall();
                    break;
                case EVENT_CALL_STATE_CHANGED:
                    logd("OTASP_CALL_STATE_CHANGED");
                    onOtaspCallStateChanged();
                    break;
                case EVENT_CDMA_PROVISION_STATUS_UPDATE:
                    logd("OTASP_ACTIVATION_STATUS_UPDATE_EVENT");
                    onCdmaProvisionStatusUpdate((AsyncResult) msg.obj);
                    break;
                case EVENT_CDMA_OTASP_CALL_RETRY:
                    logd("EVENT_CDMA_OTASP_CALL_RETRY");
                    onStartOtaspCall();
                    break;
                default:
                    loge("invalid msg: " + msg.what + " not handled.");
            }
        }
    };

    /**
     * Starts the OTASP call without any UI.
     * platform only support background non-interactive otasp call, but users could still dial
     * interactive OTASP number through dialer if carrier allows (some carrier will
     * explicitly block any outgoing *288XX number).
     */
    private void onStartOtaspCall() {
        unregisterAll();
        if (mPhone.getServiceState().getState() != ServiceState.STATE_IN_SERVICE) {
            loge("OTASP call failure, wait for network available.");
            mPhone.registerForServiceStateChanged(mHandler, EVENT_SERVICE_STATE_CHANGED, null);
            return;
        }
        // otasp call follows with CDMA OTA PROVISION STATUS update which signals activation result
        mPhone.registerForCdmaOtaStatusChange(mHandler, EVENT_CDMA_PROVISION_STATUS_UPDATE, null);
        mPhone.registerForPreciseCallStateChanged(mHandler, EVENT_CALL_STATE_CHANGED, null);
        logd("startNonInteractiveOtasp: placing call to '" + OTASP_NUMBER + "'...");
        int callStatus = PhoneUtils.placeCall(this,
                getPhone(),
                OTASP_NUMBER,
                null,   // contactRef
                false); // isEmergencyCall
        if (callStatus == PhoneUtils.CALL_STATUS_DIALED) {
            if (DBG) logd("  ==> success return from placeCall(): callStatus = " + callStatus);
        } else {
            loge(" ==> failure return from placeCall(): callStatus = " + callStatus);
            mHandler.sendEmptyMessageDelayed(EVENT_CDMA_OTASP_CALL_RETRY,
                    OTASP_CALL_RETRY_PERIOD_IN_MS);
        }
    }

    /**
     * register for cdma ota provision status
     * see RIL_CDMA_OTA_ProvisionStatus in include/telephony/ril.h
     */
    private void onCdmaProvisionStatusUpdate(AsyncResult r) {
        int[] otaStatus = (int[]) r.result;
        logd("onCdmaProvisionStatusUpdate: " + otaStatus[0]);
        if (Phone.CDMA_OTA_PROVISION_STATUS_COMMITTED == otaStatus[0]) {
            mIsOtaspCallCommitted = true;
        }
    }

    /**
     * update activation state upon call disconnected.
     * check the mIsOtaspCallCommitted bit, and if that's true it means that activation
     * was successful.
     */
    private void onOtaspCallStateChanged() {
        logd("onOtaspCallStateChanged: " + mPhone.getState());
        if (mPhone.getState().equals(PhoneConstants.State.IDLE)) {
            if (mIsOtaspCallCommitted) {
                logd("Otasp activation succeed");
                updateActivationState(this, true);
            } else {
                logd("Otasp activation failed");
                updateActivationState(this, false);
            }
            onComplete();
        }
    }

    private void onComplete() {
        logd("otasp service onComplete");
        unregisterAll();
        stopSelf();
    }

    private void unregisterAll() {
        mPhone.unregisterForCdmaOtaStatusChange(mHandler);
        mPhone.unregisterForSubscriptionInfoReady(mHandler);
        mPhone.unregisterForServiceStateChanged(mHandler);
        mPhone.unregisterForPreciseCallStateChanged(mHandler);
        mHandler.removeCallbacksAndMessages(null);
    }

    public static void updateActivationState(Context context, boolean success) {
        final TelephonyManager mTelephonyMgr = TelephonyManager.from(context);
        int state = (success) ? TelephonyManager.SIM_ACTIVATION_STATE_ACTIVATED :
                TelephonyManager.SIM_ACTIVATION_STATE_DEACTIVATED;
        int subId = SubscriptionManager.getDefaultSubscriptionId();
        mTelephonyMgr.setVoiceActivationState(subId, state);
        mTelephonyMgr.setDataActivationState(subId, state);
    }

    private static void logd(String s) {
        android.util.Log.d(TAG, s);
    }

    private static void loge(String s) {
        android.util.Log.e(TAG, s);
    }
}
