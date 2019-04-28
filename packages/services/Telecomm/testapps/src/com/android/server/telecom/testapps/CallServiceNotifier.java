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

package com.android.server.telecom.testapps;

import com.android.server.telecom.testapps.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/**
 * Class used to create, update and cancel the notification used to display and update call state
 * for {@link TestConnectionService}.
 */
public class CallServiceNotifier {
    private static final CallServiceNotifier INSTANCE = new CallServiceNotifier();

    static final String CALL_PROVIDER_ID = "testapps_TestConnectionService_CALL_PROVIDER_ID";
    static final String SIM_SUBSCRIPTION_ID = "testapps_TestConnectionService_SIM_SUBSCRIPTION_ID";
    static final String CONNECTION_MANAGER_ID =
            "testapps_TestConnectionService_CONNECTION_MANAGER_ID";

    /**
     * Static notification IDs.
     */
    private static final int CALL_NOTIFICATION_ID = 1;
    private static final int PHONE_ACCOUNT_NOTIFICATION_ID = 2;

    /**
     * Whether the added call should be started as a video call. Referenced by
     * {@link TestConnectionService} to know whether to provide a call video provider.
     */
    public static boolean mStartVideoCall;

    /**
     * Singleton accessor.
     */
    public static CallServiceNotifier getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a CallService & initializes notification manager.
     */
    private CallServiceNotifier() {
    }

    /**
     * Updates the notification in the notification pane.
     */
    public void updateNotification(Context context) {
        log("adding the notification ------------");
        getNotificationManager(context).notify(CALL_NOTIFICATION_ID, getMainNotification(context));
        getNotificationManager(context).notify(
                PHONE_ACCOUNT_NOTIFICATION_ID, getPhoneAccountNotification(context));
    }

    /**
     * Cancels the notification.
     */
    public void cancelNotifications(Context context) {
        log("canceling notification");
        getNotificationManager(context).cancel(CALL_NOTIFICATION_ID);
        getNotificationManager(context).cancel(PHONE_ACCOUNT_NOTIFICATION_ID);
    }

    /**
     * Registers a phone account with telecom.
     */
    public void registerPhoneAccount(Context context) {
        TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

        telecomManager.clearAccounts();

        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        telecomManager.registerPhoneAccount(PhoneAccount.builder(
                new PhoneAccountHandle(
                        new ComponentName(context, TestConnectionService.class),
                        CALL_PROVIDER_ID),
                "TelecomTestApp Call Provider")
                .setAddress(Uri.parse("tel:555-TEST"))
                .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING |
                        PhoneAccount.CAPABILITY_RTT |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE)
                .setIcon(Icon.createWithResource(
                        context.getResources(), R.drawable.stat_sys_phone_call))
                .setHighlightColor(Color.RED)
                // TODO: Add icon tint (Color.RED)
                .setShortDescription("a short description for the call provider")
                .setSupportedUriSchemes(Arrays.asList("tel"))
                .setExtras(testBundle)
                .build());

        telecomManager.registerPhoneAccount(PhoneAccount.builder(
                new PhoneAccountHandle(
                        new ComponentName(context, TestConnectionService.class),
                        SIM_SUBSCRIPTION_ID),
                "TelecomTestApp SIM Subscription")
                .setAddress(Uri.parse("tel:555-TSIM"))
                .setSubscriptionAddress(Uri.parse("tel:555-TSIM"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING |
                        PhoneAccount.CAPABILITY_RTT |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE)
                .setIcon(Icon.createWithResource(
                        context.getResources(), R.drawable.stat_sys_phone_call))
                .setHighlightColor(Color.GREEN)
                // TODO: Add icon tint (Color.GREEN)
                .setShortDescription("a short description for the sim subscription")
                .build());

        telecomManager.registerPhoneAccount(PhoneAccount.builder(
                        new PhoneAccountHandle(
                                new ComponentName(context, TestConnectionManager.class),
                                CONNECTION_MANAGER_ID),
                        "TelecomTestApp CONNECTION MANAGER")
                .setAddress(Uri.parse("tel:555-CMGR"))
                .setSubscriptionAddress(Uri.parse("tel:555-CMGR"))
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .setIcon(Icon.createWithResource(
                        context.getResources(), R.drawable.stat_sys_phone_call))
                // TODO: Add icon tint (Color.BLUE)
                .setShortDescription("a short description for the connection manager")
                .build());
    }

    /**
     * Displays all phone accounts registered with telecom.
     */
    public void showAllPhoneAccounts(Context context) {
        TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> accounts = telecomManager.getCallCapablePhoneAccounts();

        Toast.makeText(context, accounts.toString(), Toast.LENGTH_LONG).show();
    }

    /**
     * Returns the system's notification manager needed to add/remove notifications.
     */
    private NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Creates a notification object for using the telecom APIs.
     */
    private Notification getPhoneAccountNotification(Context context) {
        final Notification.Builder builder = new Notification.Builder(context);
        // Both notifications have buttons and only the first one with buttons will show its
        // buttons.  Since the phone accounts notification is always first, setting false ensures
        // it can be dismissed to use the other notification.
        builder.setOngoing(false);
        builder.setPriority(Notification.PRIORITY_HIGH);

        final PendingIntent intent = createShowAllPhoneAccountsIntent(context);
        builder.setContentIntent(intent);

        builder.setSmallIcon(android.R.drawable.stat_sys_phone_call);
        // TODO: Consider moving this into a strings.xml
        builder.setContentText("Test phone accounts via telecom APIs.");
        builder.setContentTitle("Test Phone Accounts");

        addRegisterPhoneAccountAction(builder, context);
        addShowAllPhoneAccountsAction(builder, context);

        return builder.build();
    }

    /**
     * Creates a notification object out of the current calls state.
     */
    private Notification getMainNotification(Context context) {
        final Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);
        builder.setSmallIcon(android.R.drawable.stat_sys_phone_call);
        builder.setContentText("Test calls via CallService API");
        builder.setContentTitle("Test Connection Service");

        addAddOneWayVideoCallAction(builder, context);
        addAddTwoWayVideoCallAction(builder, context);
        addAddCallAction(builder, context);
        addExitAction(builder, context);

        return builder.build();
    }

    /**
     * Creates the intent to remove the notification.
     */
    private PendingIntent createExitIntent(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_CALL_SERVICE_EXIT, null,
                context, CallNotificationReceiver.class);

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Creates the intent to register a phone account.
     */
    private PendingIntent createRegisterPhoneAccountIntent(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_REGISTER_PHONE_ACCOUNT,
                null, context, CallNotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Creates the intent to show all phone accounts.
     */
    private PendingIntent createShowAllPhoneAccountsIntent(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_SHOW_ALL_PHONE_ACCOUNTS,
                null, context, CallNotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Creates the intent to start an incoming 1-way video call (receive-only)
     */
    private PendingIntent createOneWayVideoCallIntent(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_ONE_WAY_VIDEO_CALL,
                null, context, CallNotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Creates the intent to start an incoming 2-way video call
     */
    private PendingIntent createTwoWayVideoCallIntent(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_TWO_WAY_VIDEO_CALL,
                null, context, CallNotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Creates the intent to start an incoming audio call
     */
    private PendingIntent createIncomingAudioCall(Context context) {
        final Intent intent = new Intent(CallNotificationReceiver.ACTION_AUDIO_CALL,
                null, context, CallNotificationReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Adds an action to the Notification Builder for adding an incoming call through Telecom.
     * @param builder The Notification Builder.
     */
    private void addAddCallAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "Add Call", createIncomingAudioCall(context));
    }

    /**
     * Adds an action to the Notification Builder to add an incoming one-way video call through
     * Telecom.
     */
    private void addAddOneWayVideoCallAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "1-way Video", createOneWayVideoCallIntent(context));
    }

    /**
     * Adds an action to the Notification Builder to add an incoming 2-way video call through
     * Telecom.
     */
    private void addAddTwoWayVideoCallAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "2-way Video", createTwoWayVideoCallIntent(context));
    }

    /**
     * Adds an action to remove the notification.
     */
    private void addExitAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "Exit", createExitIntent(context));
    }

    /**
     * Adds an action to show all registered phone accounts on a device.
     */
    private void addShowAllPhoneAccountsAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "Show Accts", createShowAllPhoneAccountsIntent(context));
    }

    /**
     * Adds an action to register a new phone account.
     */
    private void addRegisterPhoneAccountAction(Notification.Builder builder, Context context) {
        builder.addAction(0, "Reg.Acct.", createRegisterPhoneAccountIntent(context));
    }

    public boolean shouldStartVideoCall() {
        return mStartVideoCall;
    }

    private static void log(String msg) {
        Log.w("testcallservice", "[CallServiceNotifier] " + msg);
    }
}
