/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.vvm;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneUtils;

import java.util.Map;
import java.util.Set;

/**
 * Tracks the status of all inserted SIMs. Will notify {@link RemoteVvmTaskManager} of when a SIM
 * connected to the service for the first time after it was inserted or the system booted, and when
 * the SIM is removed. Losing cell signal or entering airplane mode will not cause the connected
 * event to be triggered again. Reinserting the SIM will trigger the connected event. Changing the
 * carrier config will also trigger the connected event. Events will be delayed until the device has
 * been fully booted (and left FBE mode).
 */
public class VvmSimStateTracker extends BroadcastReceiver {

    private static final String TAG = "VvmSimStateTracker";

    /**
     * Map to keep track of currently inserted SIMs. If the SIM hasn't been connected to the service
     * before the value will be a {@link ServiceStateListener} that is still waiting for the
     * connection. A value of {@code null} means the SIM has been connected to the service before.
     */
    private static Map<PhoneAccountHandle, ServiceStateListener> sListeners = new ArrayMap<>();

    /**
     * Accounts that has events before the device is booted. The events should be regenerated after
     * the device has fully booted.
     */
    private static Set<PhoneAccountHandle> sPreBootHandles = new ArraySet<>();

    /**
     * Waits for the account to become {@link ServiceState#STATE_IN_SERVICE} and notify the
     * connected event. Will unregister itself once the event has been triggered.
     */
    private class ServiceStateListener extends PhoneStateListener {

        private final PhoneAccountHandle mPhoneAccountHandle;
        private final Context mContext;

        public ServiceStateListener(Context context, PhoneAccountHandle phoneAccountHandle) {
            mContext = context;
            mPhoneAccountHandle = phoneAccountHandle;
        }

        public void listen() {
            TelephonyManager telephonyManager = getTelephonyManager(mContext, mPhoneAccountHandle);
            if(telephonyManager == null){
                VvmLog.e(TAG, "Cannot create TelephonyManager from " + mPhoneAccountHandle);
                return;
            }
            telephonyManager.listen(this, PhoneStateListener.LISTEN_SERVICE_STATE);
        }

        public void unlisten() {
            // TelephonyManager does not need to be pinned to an account when removing a
            // PhoneStateListener, and mPhoneAccountHandle might be invalid at this point
            // (e.g. SIM removal)
            mContext.getSystemService(TelephonyManager.class)
                    .listen(this, PhoneStateListener.LISTEN_NONE);
            sListeners.put(mPhoneAccountHandle, null);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                VvmLog.i(TAG, "in service");
                sendConnected(mContext, mPhoneAccountHandle);
                unlisten();
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null) {
            VvmLog.w(TAG, "Null action for intent.");
            return;
        }
        VvmLog.i(TAG, action);
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                onBootCompleted(context);
                break;
            case TelephonyIntents.ACTION_SIM_STATE_CHANGED:
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))) {
                    // checkRemovedSim will scan all known accounts with isPhoneAccountActive() to find
                    // which SIM is removed.
                    // ACTION_SIM_STATE_CHANGED only provides subId which cannot be converted to a
                    // PhoneAccountHandle when the SIM is absent.
                    checkRemovedSim(context);
                }
                break;
            case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    VvmLog.i(TAG, "Received SIM change for invalid subscription id.");
                    checkRemovedSim(context);
                    return;
                }

                PhoneAccountHandle phoneAccountHandle =
                        PhoneAccountHandleConverter.fromSubId(subId);

                if ("null".equals(phoneAccountHandle.getId())) {
                    VvmLog.e(TAG,
                            "null phone account handle ID, possible modem crash."
                                    + " Ignoring carrier config changed event");
                    return;
                }
                onCarrierConfigChanged(context, phoneAccountHandle);
        }
    }

    private void onBootCompleted(Context context) {
        for (PhoneAccountHandle phoneAccountHandle : sPreBootHandles) {
            TelephonyManager telephonyManager = getTelephonyManager(context, phoneAccountHandle);
            if (telephonyManager == null) {
                continue;
            }
            if (telephonyManager.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
                sListeners.put(phoneAccountHandle, null);
                sendConnected(context, phoneAccountHandle);
            } else {
                listenToAccount(context, phoneAccountHandle);
            }
        }
        sPreBootHandles.clear();
    }

    private void sendConnected(Context context, PhoneAccountHandle phoneAccountHandle) {
        VvmLog.i(TAG, "Service connected on " + phoneAccountHandle);
        RemoteVvmTaskManager.startCellServiceConnected(context, phoneAccountHandle);
    }

    private void checkRemovedSim(Context context) {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        if (!isBootCompleted()) {
            for (PhoneAccountHandle phoneAccountHandle : sPreBootHandles) {
                if (!PhoneUtils.isPhoneAccountActive(subscriptionManager, phoneAccountHandle)) {
                    sPreBootHandles.remove(phoneAccountHandle);
                }
            }
            return;
        }
        Set<PhoneAccountHandle> removeList = new ArraySet<>();
        for (PhoneAccountHandle phoneAccountHandle : sListeners.keySet()) {
            if (!PhoneUtils.isPhoneAccountActive(subscriptionManager, phoneAccountHandle)) {
                removeList.add(phoneAccountHandle);
                ServiceStateListener listener = sListeners.get(phoneAccountHandle);
                if (listener != null) {
                    listener.unlisten();
                }
                sendSimRemoved(context, phoneAccountHandle);
            }
        }

        for (PhoneAccountHandle phoneAccountHandle : removeList) {
            sListeners.remove(phoneAccountHandle);
        }
    }

    private boolean isBootCompleted() {
        return SystemProperties.getBoolean("sys.boot_completed", false);
    }

    private void sendSimRemoved(Context context, PhoneAccountHandle phoneAccountHandle) {
        VvmLog.i(TAG, "Sim removed on " + phoneAccountHandle);
        RemoteVvmTaskManager.startSimRemoved(context, phoneAccountHandle);
    }

    private void onCarrierConfigChanged(Context context, PhoneAccountHandle phoneAccountHandle) {
        if (!isBootCompleted()) {
            sPreBootHandles.add(phoneAccountHandle);
            return;
        }
        TelephonyManager telephonyManager = getTelephonyManager(context, phoneAccountHandle);
        if(telephonyManager == null){
            int subId = context.getSystemService(TelephonyManager.class).getSubIdForPhoneAccount(
                    context.getSystemService(TelecomManager.class)
                            .getPhoneAccount(phoneAccountHandle));
            VvmLog.e(TAG, "Cannot create TelephonyManager from " + phoneAccountHandle + ", subId="
                    + subId);
            // TODO(b/33945549): investigate more why this is happening. The PhoneAccountHandle was
            // just converted from a valid subId so createForPhoneAccountHandle shouldn't really
            // return null.
            return;
        }
        if (telephonyManager.getServiceState().getState()
                == ServiceState.STATE_IN_SERVICE) {
            sendConnected(context, phoneAccountHandle);
            sListeners.put(phoneAccountHandle, null);
        } else {
            listenToAccount(context, phoneAccountHandle);
        }
    }

    private void listenToAccount(Context context, PhoneAccountHandle phoneAccountHandle) {
        ServiceStateListener listener = new ServiceStateListener(context, phoneAccountHandle);
        listener.listen();
        sListeners.put(phoneAccountHandle, listener);
    }

    @Nullable
    private static TelephonyManager getTelephonyManager(Context context,
            PhoneAccountHandle phoneAccountHandle) {
        return context.getSystemService(TelephonyManager.class)
                .createForPhoneAccountHandle(phoneAccountHandle);
    }
}
