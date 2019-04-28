/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.CarrierConfigManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;

public class CellBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CellBroadcastReceiver";
    static final boolean DBG = true;
    static final boolean VDBG = false;    // STOPSHIP: change to false before ship

    // Key to access the stored reminder interval default value
    private static final String CURRENT_INTERVAL_DEFAULT = "current_interval_default";

    // Intent actions and extras
    public static final String CELLBROADCAST_START_CONFIG_ACTION =
            "com.android.cellbroadcastreceiver.intent.START_CONFIG";
    public static final String ACTION_MARK_AS_READ =
            "com.android.cellbroadcastreceiver.intent.action.MARK_AS_READ";
    public static final String EXTRA_DELIVERY_TIME =
            "com.android.cellbroadcastreceiver.intent.extra.ID";

    @Override
    public void onReceive(Context context, Intent intent) {
        onReceiveWithPrivilege(context, intent, false);
    }

    protected void onReceiveWithPrivilege(Context context, Intent intent, boolean privileged) {
        if (DBG) log("onReceive " + intent);

        String action = intent.getAction();

        if (ACTION_MARK_AS_READ.equals(action)) {
            final long deliveryTime = intent.getLongExtra(EXTRA_DELIVERY_TIME, -1);
            new CellBroadcastContentProvider.AsyncCellBroadcastTask(context.getContentResolver())
                    .execute(new CellBroadcastContentProvider.CellBroadcastOperation() {
                        @Override
                        public boolean execute(CellBroadcastContentProvider provider) {
                            return provider.markBroadcastRead(CellBroadcasts.DELIVERY_TIME,
                                    deliveryTime);
                        }
                    });
        } else if (TelephonyIntents.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED.equals(action)
                || CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || CELLBROADCAST_START_CONFIG_ACTION.equals(action)) {
            // Todo: Add the service state check once the new get service state API is done.
            // Do not rely on mServiceState as it gets reset to -1 time to time because
            // the process of CellBroadcastReceiver gets killed every time once the job is done.
            if (UserManager.get(context).isSystemUser()) {
                startConfigService(context.getApplicationContext());

                // Whenever carrier changes, we need to adjust the emergency alert
                // reminder interval list because it might change since different
                // countries/carriers might have different interval settings.
                adjustReminderInterval(context);
            }
            else {
                Log.e(TAG, "Not system user. Ignored the intent " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            // If 'privileged' is false, it means that the intent was delivered to the base
            // no-permissions receiver class.  If we get an SMS_CB_RECEIVED message that way, it
            // means someone has tried to spoof the message by delivering it outside the normal
            // permission-checked route, so we just ignore it.
            if (privileged) {
                intent.setClass(context, CellBroadcastAlertService.class);
                context.startService(intent);
            } else {
                loge("ignoring unprivileged action received " + action);
            }
        } else if (Telephony.Sms.Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION
                .equals(action)) {
            if (privileged) {
                CdmaSmsCbProgramData[] programDataList = (CdmaSmsCbProgramData[])
                        intent.getParcelableArrayExtra("program_data_list");
                if (programDataList != null) {
                    handleCdmaSmsCbProgramData(context, programDataList);
                } else {
                    loge("SCPD intent received with no program_data_list");
                }
            } else {
                loge("ignoring unprivileged action received " + action);
            }
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // rename registered notification channels on locale change
            CellBroadcastAlertService.createNotificationChannels(context);
        } else {
            Log.w(TAG, "onReceive() unexpected action " + action);
        }
    }

    private void adjustReminderInterval(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String currentIntervalDefault = sp.getString(CURRENT_INTERVAL_DEFAULT, "0");

        // If interval default changes, reset the interval to the new default value.
        String newIntervalDefault = context.getResources().getString(
                R.string.alert_reminder_interval_default_value);
        if (!newIntervalDefault.equals(currentIntervalDefault)) {
            Log.d(TAG, "Default interval changed from " + currentIntervalDefault + " to " +
                    newIntervalDefault);

            Editor editor = sp.edit();
            // Reset the value to default.
            editor.putString(
                    CellBroadcastSettings.KEY_ALERT_REMINDER_INTERVAL, newIntervalDefault);
            // Save the new default value.
            editor.putString(CURRENT_INTERVAL_DEFAULT, newIntervalDefault);
            editor.commit();
        } else {
            if (DBG) Log.d(TAG, "Default interval " + currentIntervalDefault + " did not change.");
        }
    }

    /**
     * Handle Service Category Program Data message.
     * TODO: Send Service Category Program Results response message to sender
     *
     * @param context
     * @param programDataList
     */
    private void handleCdmaSmsCbProgramData(Context context,
                                            CdmaSmsCbProgramData[] programDataList) {
        for (CdmaSmsCbProgramData programData : programDataList) {
            switch (programData.getOperation()) {
                case CdmaSmsCbProgramData.OPERATION_ADD_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), true);
                    break;

                case CdmaSmsCbProgramData.OPERATION_DELETE_CATEGORY:
                    tryCdmaSetCategory(context, programData.getCategory(), false);
                    break;

                case CdmaSmsCbProgramData.OPERATION_CLEAR_CATEGORIES:
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY, false);
                    tryCdmaSetCategory(context,
                            SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE, false);
                    break;

                default:
                    loge("Ignoring unknown SCPD operation " + programData.getOperation());
            }
        }
    }

    private void tryCdmaSetCategory(Context context, int category, boolean enable) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        switch (category) {
            case SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, enable)
                        .apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, enable)
                        .apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, enable).apply();
                break;

            case SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE:
                sharedPrefs.edit().putBoolean(
                        CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS, enable).apply();
                break;

            default:
                Log.w(TAG, "Ignoring SCPD command to " + (enable ? "enable" : "disable")
                        + " alerts in category " + category);
        }
    }

    /**
     * Tell {@link CellBroadcastConfigService} to enable the CB channels.
     * @param context the broadcast receiver context
     */
    static void startConfigService(Context context) {
        Intent serviceIntent = new Intent(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS,
                null, context, CellBroadcastConfigService.class);
        Log.d(TAG, "Start Cell Broadcast configuration.");
        context.startService(serviceIntent);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
