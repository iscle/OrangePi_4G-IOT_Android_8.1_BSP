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

import static android.text.format.DateUtils.DAY_IN_MILLIS;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * This service manages the display and animation of broadcast messages.
 * Emergency messages display with a flashing animated exclamation mark icon,
 * and an alert tone is played when the alert is first shown to the user
 * (but not when the user views a previously received broadcast).
 */
public class CellBroadcastAlertService extends Service {
    private static final String TAG = "CBAlertService";

    /** Intent action to display alert dialog/notification, after verifying the alert is new. */
    static final String SHOW_NEW_ALERT_ACTION = "cellbroadcastreceiver.SHOW_NEW_ALERT";

    /** Use the same notification ID for non-emergency alerts. */
    static final int NOTIFICATION_ID = 1;

    /**
     * Notification channel containing all cellbroadcast broadcast messages notifications.
     * Use the same notification channel for non-emergency alerts.
     */
    static final String NOTIFICATION_CHANNEL_BROADCAST_MESSAGES = "broadcastMessages";

    /** Sticky broadcast for latest area info broadcast received. */
    static final String CB_AREA_INFO_RECEIVED_ACTION =
            "com.android.cellbroadcastreceiver.CB_AREA_INFO_RECEIVED";

    static final String SETTINGS_APP = "com.android.settings";

    /** Intent extra for passing a SmsCbMessage */
    private static final String EXTRA_MESSAGE = "message";

    /**
     * Default message expiration time is 24 hours. Same message arrives within 24 hours will be
     * treated as a duplicate.
     */
    private static final long DEFAULT_EXPIRATION_TIME = DAY_IN_MILLIS;

    /**
     * Alert type
     */
    public enum AlertType {
        CMAS_DEFAULT,
        ETWS_DEFAULT,
        EARTHQUAKE,
        TSUNAMI,
        AREA,
        OTHER
    }

    /**
     *  Container for service category, serial number, location, body hash code, and ETWS primary/
     *  secondary information for duplication detection.
     */
    private static final class MessageServiceCategoryAndScope {
        private final int mServiceCategory;
        private final int mSerialNumber;
        private final SmsCbLocation mLocation;
        private final int mBodyHash;
        private final boolean mIsEtwsPrimary;

        MessageServiceCategoryAndScope(int serviceCategory, int serialNumber,
                SmsCbLocation location, int bodyHash, boolean isEtwsPrimary) {
            mServiceCategory = serviceCategory;
            mSerialNumber = serialNumber;
            mLocation = location;
            mBodyHash = bodyHash;
            mIsEtwsPrimary = isEtwsPrimary;
        }

        @Override
        public int hashCode() {
            return mLocation.hashCode() + 5 * mServiceCategory + 7 * mSerialNumber + 13 * mBodyHash
                    + 17 * Boolean.hashCode(mIsEtwsPrimary);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof MessageServiceCategoryAndScope) {
                MessageServiceCategoryAndScope other = (MessageServiceCategoryAndScope) o;
                return (mServiceCategory == other.mServiceCategory &&
                        mSerialNumber == other.mSerialNumber &&
                        mLocation.equals(other.mLocation) &&
                        mBodyHash == other.mBodyHash &&
                        mIsEtwsPrimary == other.mIsEtwsPrimary);
            }
            return false;
        }

        @Override
        public String toString() {
            return "{mServiceCategory: " + mServiceCategory + " serial number: " + mSerialNumber +
                    " location: " + mLocation.toString() + " body hash: " + mBodyHash +
                    " mIsEtwsPrimary: " + mIsEtwsPrimary + "}";
        }
    }

    /** Maximum number of message IDs to save before removing the oldest message ID. */
    private static final int MAX_MESSAGE_ID_SIZE = 1024;

    /** Linked hash map of the message identities for duplication detection purposes. The key is the
     * the collection of different message keys used for duplication detection, and the value
     * is the timestamp of message arriving time. Some carriers may require shorter expiration time.
     */
    private static final LinkedHashMap<MessageServiceCategoryAndScope, Long> sMessagesMap =
            new LinkedHashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand: " + action);
        if (Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION.equals(action) ||
                Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION.equals(action)) {
            handleCellBroadcastIntent(intent);
        } else if (SHOW_NEW_ALERT_ACTION.equals(action)) {
            try {
                if (UserHandle.myUserId() ==
                        ActivityManager.getService().getCurrentUser().id) {
                    showNewAlert(intent);
                } else {
                    Log.d(TAG,"Not active user, ignore the alert display");
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Unrecognized intent action: " + action);
        }
        return START_NOT_STICKY;
    }

    /**
     * Get the carrier specific message duplicate expiration time.
     *
     * @param subId Subscription index
     * @return The expiration time in milliseconds. Small values like 0 (or negative values)
     * indicate expiration immediately (meaning the duplicate will always be displayed), while large
     * values indicate the duplicate will always be ignored. The default value would be 24 hours.
     */
    private long getDuplicateExpirationTime(int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                getApplicationContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        Log.d(TAG, "manager = " + configManager);
        if (configManager == null) {
            Log.e(TAG, "carrier config is not available.");
            return DEFAULT_EXPIRATION_TIME;
        }

        PersistableBundle b = configManager.getConfigForSubId(subId);
        if (b == null) {
            Log.e(TAG, "expiration key does not exist.");
            return DEFAULT_EXPIRATION_TIME;
        }

        long time = b.getLong(CarrierConfigManager.KEY_MESSAGE_EXPIRATION_TIME_LONG,
                DEFAULT_EXPIRATION_TIME);
        return time;
    }

    private void handleCellBroadcastIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "received SMS_CB_RECEIVED_ACTION with no extras!");
            return;
        }

        SmsCbMessage message = (SmsCbMessage) extras.get(EXTRA_MESSAGE);

        if (message == null) {
            Log.e(TAG, "received SMS_CB_RECEIVED_ACTION with no message extra");
            return;
        }

        final CellBroadcastMessage cbm = new CellBroadcastMessage(message);
        int subId = intent.getExtras().getInt(PhoneConstants.SUBSCRIPTION_KEY);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            cbm.setSubId(subId);
        } else {
            Log.e(TAG, "Invalid subscription id");
        }

        if (!isMessageEnabledByUser(cbm)) {
            Log.d(TAG, "ignoring alert of type " + cbm.getServiceCategory() +
                    " by user preference");
            return;
        }

        // Check if message body should be used for duplicate detection.
        boolean shouldCompareMessageBody =
                getApplicationContext().getResources().getBoolean(R.bool.duplicate_compare_body);

        int hashCode = shouldCompareMessageBody ? message.getMessageBody().hashCode() : 0;

        // If this is an ETWS message, we need to include primary/secondary message information to
        // be a factor for duplication detection as well. Per 3GPP TS 23.041 section 8.2,
        // duplicate message detection shall be performed independently for primary and secondary
        // notifications.
        boolean isEtwsPrimary = false;
        if (message.isEtwsMessage()) {
            SmsCbEtwsInfo etwsInfo = message.getEtwsWarningInfo();
            if (etwsInfo != null) {
                isEtwsPrimary = etwsInfo.isPrimary();
            } else {
                Log.w(TAG, "ETWS info is not available.");
            }
        }

        // Check for duplicate message IDs according to CMAS carrier requirements. Message IDs
        // are stored in volatile memory. If the maximum of 1024 messages is reached, the
        // message ID of the oldest message is deleted from the list.
        MessageServiceCategoryAndScope newCmasId = new MessageServiceCategoryAndScope(
                message.getServiceCategory(), message.getSerialNumber(), message.getLocation(),
                hashCode, isEtwsPrimary);

        Log.d(TAG, "message ID = " + newCmasId);

        long nowTime = SystemClock.elapsedRealtime();
        // Check if the identical message arrives again
        if (sMessagesMap.get(newCmasId) != null) {
            // And if the previous one has not expired yet, treat it as a duplicate message.
            long previousTime = sMessagesMap.get(newCmasId);
            long expirationTime = getDuplicateExpirationTime(subId);
            if (nowTime - previousTime < expirationTime) {
                Log.d(TAG, "ignoring the duplicate alert " + newCmasId + ", nowTime=" + nowTime
                        + ", previous=" + previousTime + ", expiration=" + expirationTime);
                return;
            }
            // otherwise, we don't treat it as a duplicate and will show the same message again.
            Log.d(TAG, "The same message shown up " + (nowTime - previousTime)
                    + " milliseconds ago. Not a duplicate.");
        } else if (sMessagesMap.size() >= MAX_MESSAGE_ID_SIZE){
            // If we reach the maximum, remove the first inserted message key.
            MessageServiceCategoryAndScope oldestCmasId = sMessagesMap.keySet().iterator().next();
            Log.d(TAG, "message ID limit reached, removing oldest message ID " + oldestCmasId);
            sMessagesMap.remove(oldestCmasId);
        } else {
            Log.d(TAG, "New message. Not a duplicate. Map size = " + sMessagesMap.size());
        }

        sMessagesMap.put(newCmasId, nowTime);

        final Intent alertIntent = new Intent(SHOW_NEW_ALERT_ACTION);
        alertIntent.setClass(this, CellBroadcastAlertService.class);
        alertIntent.putExtra(EXTRA_MESSAGE, cbm);

        // write to database on a background thread
        new CellBroadcastContentProvider.AsyncCellBroadcastTask(getContentResolver())
                .execute(new CellBroadcastContentProvider.CellBroadcastOperation() {
                    @Override
                    public boolean execute(CellBroadcastContentProvider provider) {
                        if (provider.insertNewBroadcast(cbm)) {
                            // new message, show the alert or notification on UI thread
                            startService(alertIntent);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
    }

    private void showNewAlert(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(TAG, "received SHOW_NEW_ALERT_ACTION with no extras!");
            return;
        }

        CellBroadcastMessage cbm = (CellBroadcastMessage) intent.getParcelableExtra(EXTRA_MESSAGE);

        if (cbm == null) {
            Log.e(TAG, "received SHOW_NEW_ALERT_ACTION with no message extra");
            return;
        }

        if (isEmergencyMessage(this, cbm)) {
            // start alert sound / vibration / TTS and display full-screen alert
            openEmergencyAlertNotification(cbm);
        } else {
            // add notification to the bar by passing the list of unread non-emergency
            // CellBroadcastMessages
            ArrayList<CellBroadcastMessage> messageList = CellBroadcastReceiverApp
                    .addNewMessageToList(cbm);
            addToNotificationBar(cbm, messageList, this, false);
        }
    }

    /**
     * Check if the device is currently on roaming.
     *
     * @param subId Subscription index
     * @return True if roaming, otherwise not roaming.
     */
    private boolean isRoaming(int subId) {
        Context context = getApplicationContext();

        if (context != null) {
            TelephonyManager tm =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm.isNetworkRoaming(subId);
        }

        return false;
    }

    /**
     * Filter out broadcasts on the test channels that the user has not enabled,
     * and types of notifications that the user is not interested in receiving.
     * This allows us to enable an entire range of message identifiers in the
     * radio and not have to explicitly disable the message identifiers for
     * test broadcasts. In the unlikely event that the default shared preference
     * values were not initialized in CellBroadcastReceiverApp, the second parameter
     * to the getBoolean() calls match the default values in res/xml/preferences.xml.
     *
     * @param message the message to check
     * @return true if the user has enabled this message type; false otherwise
     */
    private boolean isMessageEnabledByUser(CellBroadcastMessage message) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Check if all emergency alerts are disabled.
        boolean emergencyAlertEnabled =
                prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);

        // Check if ETWS/CMAS test message is forced to disabled on the device.
        boolean forceDisableEtwsCmasTest =
                CellBroadcastSettings.isFeatureEnabled(this,
                        CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL, false);

        boolean enableAreaUpdateInfoAlerts = Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_showAreaUpdateInfoSettings)
                && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS,
                false);

        if (message.isEtwsTestMessage()) {
            return emergencyAlertEnabled &&
                    !forceDisableEtwsCmasTest &&
                    PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(CellBroadcastSettings.KEY_ENABLE_ETWS_TEST_ALERTS, false);
        }

        if (message.isEtwsMessage()) {
            // ETWS messages.
            // Turn on/off emergency notifications is the only way to turn on/off ETWS messages.
            return emergencyAlertEnabled;

        }

        int channel = message.getServiceCategory();

        // Check if the messages are on additional channels enabled by the resource config.
        // If those channels are enabled by the carrier, but the device is actually roaming, we
        // should not allow the messages.
        ArrayList<CellBroadcastChannelRange> ranges = CellBroadcastChannelManager
                .getInstance().getCellBroadcastChannelRanges(getApplicationContext());

        if (ranges != null) {
            for (CellBroadcastChannelRange range : ranges) {
                if (range.mStartId <= channel && range.mEndId >= channel) {
                    // We only enable the channels when the device is not roaming.
                    if (isRoaming(message.getSubId())) {
                        return false;
                    }

                    // The area update information cell broadcast should not cause any pop-up.
                    // Instead the setting's app SIM status will show its information.
                    if (range.mAlertType == AlertType.AREA) {
                        if (enableAreaUpdateInfoAlerts) {
                            // save latest area info broadcast for Settings display and send as
                            // broadcast.
                            CellBroadcastReceiverApp.setLatestAreaInfo(message);
                            Intent intent = new Intent(CB_AREA_INFO_RECEIVED_ACTION);
                            intent.setPackage(SETTINGS_APP);
                            intent.putExtra(EXTRA_MESSAGE, message);
                            // Send broadcast twice, once for apps that have PRIVILEGED permission
                            // and once for those that have the runtime one.
                            sendBroadcastAsUser(intent, UserHandle.ALL,
                                    android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
                            sendBroadcastAsUser(intent, UserHandle.ALL,
                                    android.Manifest.permission.READ_PHONE_STATE);
                            // area info broadcasts are displayed in Settings status screen
                        }
                        return false;
                    }

                    return emergencyAlertEnabled;
                }
            }
        }

        if (message.isCmasMessage()) {
            switch (message.getCmasMessageClass()) {
                case SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT:
                    return emergencyAlertEnabled &&
                            PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);

                case SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT:
                    return emergencyAlertEnabled &&
                            PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                            CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);

                case SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY:
                    return emergencyAlertEnabled &&
                            PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

                case SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST:
                case SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE:
                case SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE:
                    return emergencyAlertEnabled &&
                            !forceDisableEtwsCmasTest &&
                            PreferenceManager.getDefaultSharedPreferences(this)
                                    .getBoolean(CellBroadcastSettings.KEY_ENABLE_CMAS_TEST_ALERTS,
                                            false);
                default:
                    return true;    // presidential-level CMAS alerts are always enabled
            }
        }

        return true;    // other broadcast messages are always enabled
    }

    /**
     * Display an alert message for emergency alerts.
     * @param message the alert to display
     */
    private void openEmergencyAlertNotification(CellBroadcastMessage message) {
        // Acquire a screen bright wakelock until the alert dialog and audio start playing.
        CellBroadcastAlertWakeLock.acquireScreenBrightWakeLock(this);

        // Close dialogs and window shade
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDialogs);

        // start audio/vibration/speech service for emergency alerts
        Intent audioIntent = new Intent(this, CellBroadcastAlertAudio.class);
        audioIntent.setAction(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        AlertType alertType = AlertType.CMAS_DEFAULT;
        if (message.isEtwsMessage()) {
            // For ETWS, always vibrate, even in silent mode.
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA, true);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_ETWS_VIBRATE_EXTRA, true);
            alertType = AlertType.ETWS_DEFAULT;

            if (message.getEtwsWarningInfo() != null) {
                int warningType = message.getEtwsWarningInfo().getWarningType();

                switch (warningType) {
                    case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE:
                    case SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI:
                        alertType = AlertType.EARTHQUAKE;
                        break;
                    case SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI:
                        alertType = AlertType.TSUNAMI;
                        break;
                    case SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY:
                        alertType = AlertType.OTHER;
                        break;
                }
            }
        } else {
            // For other alerts, vibration can be disabled in app settings.
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATE_EXTRA,
                    prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE, true));
            int channel = message.getServiceCategory();
            ArrayList<CellBroadcastChannelRange> ranges = CellBroadcastChannelManager
                    .getInstance().getCellBroadcastChannelRanges(getApplicationContext());
            if (ranges != null) {
                for (CellBroadcastChannelRange range : ranges) {
                    if (channel >= range.mStartId && channel <= range.mEndId) {
                        alertType = range.mAlertType;
                        break;
                    }
                }
            }
        }
        audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE, alertType);

        String messageBody = message.getMessageBody();

        if (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH, true)) {
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY, messageBody);

            String preferredLanguage = message.getLanguageCode();
            String defaultLanguage = null;
            if (message.isEtwsMessage()) {
                // Only do TTS for ETWS secondary message.
                // There is no text in ETWS primary message. When we construct the ETWS primary
                // message, we hardcode "ETWS" as the body hence we don't want to speak that out
                // here.

                // Also in many cases we see the secondary message comes few milliseconds after
                // the primary one. If we play TTS for the primary one, It will be overwritten by
                // the secondary one immediately anyway.
                if (!message.getEtwsWarningInfo().isPrimary()) {
                    // Since only Japanese carriers are using ETWS, if there is no language
                    // specified in the ETWS message, we'll use Japanese as the default language.
                    defaultLanguage = "ja";
                }
            } else {
                // If there is no language specified in the CMAS message, use device's
                // default language.
                defaultLanguage = Locale.getDefault().getLanguage();
            }

            Log.d(TAG, "Preferred language = " + preferredLanguage +
                    ", Default language = " + defaultLanguage);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_PREFERRED_LANGUAGE,
                    preferredLanguage);
            audioIntent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_DEFAULT_LANGUAGE,
                    defaultLanguage);
        }
        startService(audioIntent);

        ArrayList<CellBroadcastMessage> messageList = new ArrayList<CellBroadcastMessage>(1);
        messageList.add(message);

        // For FEATURE_WATCH, the dialog doesn't make sense from a UI/UX perspective
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            addToNotificationBar(message, messageList, this, false);
        } else {
            Intent alertDialogIntent = createDisplayMessageIntent(this,
                    CellBroadcastAlertDialog.class, messageList);
            alertDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(alertDialogIntent);
        }

    }

    /**
     * Add the new alert to the notification bar (non-emergency alerts), or launch a
     * high-priority immediate intent for emergency alerts.
     * @param message the alert to display
     */
    static void addToNotificationBar(CellBroadcastMessage message,
                                     ArrayList<CellBroadcastMessage> messageList, Context context,
                                     boolean fromSaveState) {
        int channelTitleId = CellBroadcastResources.getDialogTitleResource(context, message);
        CharSequence channelName = context.getText(channelTitleId);
        String messageBody = message.getMessageBody();
        final NotificationManager notificationManager = NotificationManager.from(context);
        createNotificationChannels(context);

        // Create intent to show the new messages when user selects the notification.
        Intent intent;
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // For FEATURE_WATCH we want to mark as read
            intent = createMarkAsReadIntent(context, message.getDeliveryTime());
        } else {
            // For anything else we handle it normally
            intent = createDisplayMessageIntent(context, CellBroadcastAlertDialog.class,
                    messageList);
        }

        intent.putExtra(CellBroadcastAlertDialog.FROM_NOTIFICATION_EXTRA, true);
        intent.putExtra(CellBroadcastAlertDialog.FROM_SAVE_STATE_NOTIFICATION_EXTRA, fromSaveState);

        PendingIntent pi;
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            pi = PendingIntent.getBroadcast(context, 0, intent, 0);
        } else {
            pi = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        // use default sound/vibration/lights for non-emergency broadcasts
        Notification.Builder builder = new Notification.Builder(
                context, NOTIFICATION_CHANNEL_BROADCAST_MESSAGES)
                .setSmallIcon(R.drawable.ic_notify_alert)
                .setTicker(channelName)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setColor(context.getResources().getColor(R.color.notification_color))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_ALL);

        builder.setDefaults(Notification.DEFAULT_ALL);

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            builder.setDeleteIntent(pi);
        } else {
            builder.setContentIntent(pi);
        }

        // increment unread alert count (decremented when user dismisses alert dialog)
        int unreadCount = messageList.size();
        if (unreadCount > 1) {
            // use generic count of unread broadcasts if more than one unread
            builder.setContentTitle(context.getString(R.string.notification_multiple_title));
            builder.setContentText(context.getString(R.string.notification_multiple, unreadCount));
        } else {
            builder.setContentTitle(channelName).setContentText(messageBody);
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Creates the notification channel and registers it with NotificationManager. If a channel
     * with the same ID is already registered, NotificationManager will ignore this call.
     */
    static void createNotificationChannels(Context context) {
        NotificationManager.from(context).createNotificationChannel(
                new NotificationChannel(
                NOTIFICATION_CHANNEL_BROADCAST_MESSAGES,
                context.getString(R.string.notification_channel_broadcast_messages),
                NotificationManager.IMPORTANCE_LOW));
    }

    static Intent createDisplayMessageIntent(Context context, Class intentClass,
            ArrayList<CellBroadcastMessage> messageList) {
        // Trigger the list activity to fire up a dialog that shows the received messages
        Intent intent = new Intent(context, intentClass);
        intent.putParcelableArrayListExtra(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA, messageList);
        return intent;
    }

    /**
     * Creates a delete intent that calls to the {@link CellBroadcastReceiver} in order to mark
     * a message as read
     *
     * @param context context of the caller
     * @param deliveryTime time the message was sent in order to mark as read
     * @return delete intent to add to the pending intent
     */
    static Intent createMarkAsReadIntent(Context context, long deliveryTime) {
        Intent deleteIntent = new Intent(context, CellBroadcastReceiver.class);
        deleteIntent.setAction(CellBroadcastReceiver.ACTION_MARK_AS_READ);
        deleteIntent.putExtra(CellBroadcastReceiver.EXTRA_DELIVERY_TIME, deliveryTime);
        return deleteIntent;
    }

    @VisibleForTesting
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @VisibleForTesting
    class LocalBinder extends Binder {
        public CellBroadcastAlertService getService() {
            return CellBroadcastAlertService.this;
        }
    }

    /**
     * Check if the cell broadcast message is an emergency message or not
     * @param context Device context
     * @param cbm Cell broadcast message
     * @return True if the message is an emergency message, otherwise false.
     */
    public static boolean isEmergencyMessage(Context context, CellBroadcastMessage cbm) {
        boolean isEmergency = false;

        if (cbm == null) {
            return false;
        }

        int id = cbm.getServiceCategory();

        if (cbm.isEmergencyAlertMessage()) {
            isEmergency = true;
        } else {
            ArrayList<CellBroadcastChannelRange> ranges = CellBroadcastChannelManager
                    .getInstance().getCellBroadcastChannelRanges(context);

            if (ranges != null) {
                for (CellBroadcastChannelRange range : ranges) {
                    if (range.mStartId <= id && range.mEndId >= id) {
                        isEmergency = range.mIsEmergency;
                        break;
                    }
                }
            }
        }

        Log.d(TAG, "isEmergencyMessage: " + isEmergency + "message id = " + id);
        return isEmergency;
    }
}
