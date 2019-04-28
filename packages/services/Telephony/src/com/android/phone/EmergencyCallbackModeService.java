/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.util.NotificationChannelController;

import java.text.SimpleDateFormat;

/**
 * Application service that inserts/removes Emergency Callback Mode notification and
 * updates Emergency Callback Mode countdown clock in the notification
 *
 * @see EmergencyCallbackModeExitDialog
 */
public class EmergencyCallbackModeService extends Service {

    // Default Emergency Callback Mode timeout value
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final String LOG_TAG = "EmergencyCallbackModeService";

    private NotificationManager mNotificationManager = null;
    private CountDownTimer mTimer = null;
    private long mTimeLeft = 0;
    private Phone mPhone = null;
    private boolean mInEmergencyCall = false;

    private static final int ECM_TIMER_RESET = 1;

    private Handler mHandler = new Handler () {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ECM_TIMER_RESET:
                    resetEcmTimer((AsyncResult) msg.obj);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
         Phone phoneInEcm = PhoneGlobals.getInstance().getPhoneInEcm();
        // Check if it is CDMA phone
        if (phoneInEcm == null || ((phoneInEcm.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA)
                && (phoneInEcm.getImsPhone() == null))) {
            Log.e(LOG_TAG, "Error! Emergency Callback Mode not supported for " + phoneInEcm);
            stopSelf();
            return;
        }

        // Register receiver for intents
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS);
        registerReceiver(mEcmReceiver, filter);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Register ECM timer reset notfication
        mPhone = phoneInEcm;
        mPhone.registerForEcmTimerReset(mHandler, ECM_TIMER_RESET, null);

        startTimerNotification();
    }

    @Override
    public void onDestroy() {
        if (mPhone != null) {
            // Unregister receiver
            unregisterReceiver(mEcmReceiver);
            // Unregister ECM timer reset notification
            mPhone.unregisterForEcmTimerReset(mHandler);

            // Cancel the notification and timer
            mNotificationManager.cancel(R.string.phone_in_ecm_notification_title);
            mTimer.cancel();
        }
    }

    /**
     * Listens for Emergency Callback Mode intents
     */
    private BroadcastReceiver mEcmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Stop the service when phone exits Emergency Callback Mode
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                if (intent.getBooleanExtra("phoneinECMState", false) == false) {
                    stopSelf();
                }
            }
            // Show dialog box
            else if (intent.getAction().equals(
                    TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS)) {
                    context.startActivity(
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    };

    /**
     * Start timer notification for Emergency Callback Mode
     */
    private void startTimerNotification() {
        // Get Emergency Callback Mode timeout value
        long ecmTimeout = SystemProperties.getLong(
                    TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);

        // Show the notification
        showNotification(ecmTimeout);

        // Start countdown timer for the notification updates
        if (mTimer != null) {
            mTimer.cancel();
        } else {
            mTimer = new CountDownTimer(ecmTimeout, 1000) {

                @Override
                public void onTick(long millisUntilFinished) {
                    mTimeLeft = millisUntilFinished;
                }

                @Override
                public void onFinish() {
                    //Do nothing
                }

            };
        }
        mTimer.start();
    }

    /**
     * Shows notification for Emergency Callback Mode
     */
    private void showNotification(long millisUntilFinished) {
        Phone imsPhone = mPhone.getImsPhone();
        boolean isInEcm = mPhone.isInEcm() || (imsPhone != null && imsPhone.isInEcm());
        if (!isInEcm) {
            Log.i(LOG_TAG, "Asked to show notification but not in ECM mode");
            if (mTimer != null) {
                mTimer.cancel();
            }
            return;
        }
        final Notification.Builder builder = new Notification.Builder(getApplicationContext());
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);
        builder.setSmallIcon(R.drawable.ic_emergency_callback_mode);
        builder.setTicker(getText(R.string.phone_entered_ecm_text));
        builder.setContentTitle(getText(R.string.phone_in_ecm_notification_title));
        builder.setColor(getResources().getColor(R.color.dialer_theme_color));

        // PendingIntent to launch Emergency Callback Mode Exit activity if the user selects
        // this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(EmergencyCallbackModeExitDialog.ACTION_SHOW_ECM_EXIT_DIALOG), 0);
        builder.setContentIntent(contentIntent);

        // Format notification string
        String text = null;
        if(mInEmergencyCall) {
            text = getText(R.string.phone_in_ecm_call_notification_text).toString();
        } else {
            // Calculate the time in ms when the notification will be finished.
            long finishedCountMs = millisUntilFinished + System.currentTimeMillis();
            builder.setShowWhen(true);
            builder.setChronometerCountDown(true);
            builder.setUsesChronometer(true);
            builder.setWhen(finishedCountMs);

            String completeTime = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(
                    finishedCountMs);
            text = getResources().getString(R.string.phone_in_ecm_notification_complete_time,
                    completeTime);
        }
        builder.setContentText(text);
        builder.setChannelId(NotificationChannelController.CHANNEL_ID_ALERT);

        // Show notification
        mNotificationManager.notify(R.string.phone_in_ecm_notification_title, builder.build());
    }

    /**
     * Handle ECM_TIMER_RESET notification
     */
    private void resetEcmTimer(AsyncResult r) {
        boolean isTimerCanceled = ((Boolean)r.result).booleanValue();

        if (isTimerCanceled) {
            mInEmergencyCall = true;
            mTimer.cancel();
            showNotification(0);
        } else {
            mInEmergencyCall = false;
            startTimerNotification();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access
     */
    public class LocalBinder extends Binder {
        EmergencyCallbackModeService getService() {
            return EmergencyCallbackModeService.this;
        }
    }

    /**
     * Returns Emergency Callback Mode timeout value
     */
    public long getEmergencyCallbackModeTimeout() {
        return mTimeLeft;
    }

    /**
     * Returns Emergency Callback Mode call state
     */
    public boolean getEmergencyCallbackModeCallState() {
        return mInEmergencyCall;
    }
}
