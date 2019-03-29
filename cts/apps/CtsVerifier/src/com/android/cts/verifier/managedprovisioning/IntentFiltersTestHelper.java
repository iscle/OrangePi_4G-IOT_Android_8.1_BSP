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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract.Events;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for testing if the required cross profile intent filters are set during the
 * managed provisioning.
 */
public class IntentFiltersTestHelper {

    private static final String TAG = "IntentFiltersTestHelper";

    // These are the intents which can be forwarded to the managed profile.
    private static final ArrayList<Intent> forwardedIntentsFromPrimary =
            new ArrayList<>(Arrays.asList(
                new Intent(Intent.ACTION_SEND).setType("*/*"),
                new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            ));

    // These are the intents which can be forwarded to the primary profile.
    private static final ArrayList<Intent> forwardedIntentsFromManaged =
            new ArrayList<>(Arrays.asList(
                new Intent(AlarmClock.ACTION_SET_ALARM),
                new Intent(AlarmClock.ACTION_SET_TIMER),
                new Intent(AlarmClock.ACTION_SHOW_ALARMS),
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent(Settings.ACTION_CAPTIONING_SETTINGS),
                new Intent(Settings.ACTION_DATE_SETTINGS),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                new Intent(Settings.ACTION_DISPLAY_SETTINGS),
                new Intent(Settings.ACTION_LOCALE_SETTINGS),
                new Intent(Settings.ACTION_PRIVACY_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD),
                new Intent("android.net.vpn.SETTINGS"),
                new Intent(Settings.ACTION_VPN_SETTINGS),
                new Intent("android.settings.ACCOUNT_SYNC_SETTINGS"),
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                new Intent("android.settings.LICENSE"),
                new Intent("android.settings.NOTIFICATION_SETTINGS"),
                new Intent("android.settings.ZEN_MODE_SETTINGS"),
                new Intent("com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS"),
                new Intent("com.android.settings.TTS_SETTINGS"),
                new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                new Intent(Settings.ACTION_SYNC_SETTINGS),
                new Intent(Settings.ACTION_ADD_ACCOUNT),
                new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(
                        Intent.CATEGORY_OPENABLE),
                new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(
                        Intent.CATEGORY_OPENABLE),
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
                new Intent(Settings.ACTION_APPLICATION_SETTINGS)
            ));

    // These are the intents which cannot be forwarded to the primary profile.
    private static final ArrayList<Intent> notForwardedIntentsFromManaged =
            new ArrayList<>(Arrays.asList(
                new Intent(Intent.ACTION_INSERT).setData(
                        Uri.parse("content://browser/bookmarks")),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("http://www.example.com")).addCategory(
                        Intent.CATEGORY_BROWSABLE),
                new Intent(Intent.ACTION_SENDTO).setData(
                        Uri.parse("mailto:user@example.com")),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("mailto:user@example.com")).addCategory(
                        Intent.CATEGORY_BROWSABLE),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("geo:0,0?q=BuckinghamPalace")),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("http://example.com/oceans.mp4")).setType("video/mp4"),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("http://www.example.com/horse.mp3")).setType("audio/*"),
                new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH),
                new Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse("market://details?id=com.android.chrome")).addCategory(
                        Intent.CATEGORY_BROWSABLE),
                new Intent(Intent.ACTION_WEB_SEARCH),
                new Intent(Settings.ACTION_SEARCH_SETTINGS),
                new Intent(Intent.ACTION_MANAGE_NETWORK_USAGE),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                        Uri.parse("package:com.android.chrome")),
                new Intent(Intent.ACTION_INSERT).setData(Events.CONTENT_URI),
                new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            ));

    // This flag specifies we are dealing with intents fired from the primary profile.
    public static final int FLAG_INTENTS_FROM_PRIMARY = 1;
    // This flag specifies we are dealing with intents fired from the managed profile.
    public static final int FLAG_INTENTS_FROM_MANAGED = 2;

    private Context mContext;

    IntentFiltersTestHelper(Context context) {
        mContext = context;

        addIntentsThatDependOnDeviceConfigs();
        addIntentsThatDependOnDeviceFeatures();
    }

    private void addIntentsThatDependOnDeviceConfigs() {
        if (UserManager.supportsMultipleUsers()) {
            forwardedIntentsFromManaged.add(
                    new Intent("android.settings.USER_SETTINGS"));
        }
    }

    private void addIntentsThatDependOnDeviceFeatures() {
        PackageManager pm = mContext.getPackageManager();

        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:123")),
                    new Intent("android.intent.action.CALL_EMERGENCY").setData(
                            Uri.parse("tel:123")),
                    new Intent("android.intent.action.CALL_PRIVILEGED").setData(
                            Uri.parse("tel:123")),
                    new Intent(Intent.ACTION_VIEW).setData(Uri.parse("tel:123")).addCategory(
                            Intent.CATEGORY_BROWSABLE),
                    new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                    new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                    new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("sms:07700900100")),
                    new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:07700900100")),
                    new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mms:07700900100")),
                    new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mmsto:07700900100")),
                    new Intent(Intent.ACTION_VIEW).setData(
                            Uri.parse("sms:07700900100?body=Hello%20world")).addCategory(
                            Intent.CATEGORY_BROWSABLE),
                    new Intent(Intent.ACTION_VIEW).setData(
                            Uri.parse("smsto:07700900100?body=Hello%20world")).addCategory(
                            Intent.CATEGORY_BROWSABLE),
                    new Intent(Intent.ACTION_VIEW).setData(
                            Uri.parse("mms:07700900100?body=Hello%20world")).addCategory(
                            Intent.CATEGORY_BROWSABLE),
                    new Intent(Intent.ACTION_VIEW).setData(
                            Uri.parse("mmsto:07700900100?body=Hello%20world")).addCategory(
                            Intent.CATEGORY_BROWSABLE),
                    new Intent(Settings.ACTION_APN_SETTINGS)));
            notForwardedIntentsFromManaged
                    .add(new Intent(Intent.ACTION_CALL).setData(Uri.parse("tel:123")));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(Settings.ACTION_NFC_SETTINGS),
                    new Intent(Settings.ACTION_NFCSHARING_SETTINGS)));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(CardEmulation.ACTION_CHANGE_DEFAULT),
                    new Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS)));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                    new Intent(MediaStore.ACTION_VIDEO_CAPTURE),
                    new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA),
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE),
                    new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)));
        }

        final String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_MEMORY_CARD_SETTINGS));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(Settings.ACTION_WIFI_IP_SETTINGS),
                    new Intent(Settings.ACTION_WIFI_SETTINGS)));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION),
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(Settings.ACTION_SOUND_SETTINGS),
                    new Intent("android.settings.ACTION_OTHER_SOUND_SETTINGS")));
            notForwardedIntentsFromManaged.add(
                    new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_HOME_SCREEN)) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_HOME_SETTINGS));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)) {
            forwardedIntentsFromManaged.addAll(Arrays.asList(
                    new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                    new Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)));
        }

        if (!pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_DREAM_SETTINGS));
        }

        if (!pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_PRINTING)) {
            notForwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_PRINT_SETTINGS));
        }

        if (Build.TYPE.equals("user")) {
            forwardedIntentsFromManaged.add(
                    new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        }
    }

    public boolean checkCrossProfileIntentFilters(int flag) {
        boolean crossProfileIntentFiltersSet;
        if (flag == FLAG_INTENTS_FROM_PRIMARY) {
            crossProfileIntentFiltersSet = checkForIntentsFromPrimary();
        } else {
            crossProfileIntentFiltersSet = checkForIntentsFromManaged();
        }
        return crossProfileIntentFiltersSet;
    }

    /**
     * Checks if required cross profile intent filters are set for the intents fired from the
     * primary profile.
     */
    private boolean checkForIntentsFromPrimary() {
        // Get the class name of the intentForwarderActivity in the primary profile by firing an
        // intent which we know will be forwarded from primary profile to managed profile.
        ActivityInfo forwarderActivityInfo =
                getForwarderActivityInfo(ByodHelperActivity.ACTION_QUERY_PROFILE_OWNER);
        if (forwarderActivityInfo == null) {
            return false;
        }

        // Check for intents which can be forwarded to the managed profile.
        return checkForIntentsNotHandled(forwardedIntentsFromPrimary,
                forwarderActivityInfo, "from primary profile should be forwarded to the " +
                "managed profile but is not.", true);
    }

    /**
     * Checks if required cross profile intent filters are set for the intents fired from the
     * managed profile.
     */
    private boolean checkForIntentsFromManaged() {
        // Get the class name of the intentForwarderActivity in the managed profile by firing an
        // intent which we know will be forwarded from managed profile to primary profile.
        ActivityInfo forwarderActivityInfo =
                getForwarderActivityInfo(ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS);
        if (forwarderActivityInfo == null) {
            return false;
        }

        boolean success = true;
        // Check for intents which can be forwarded to the primary profile.
        success &= checkForIntentsNotHandled(forwardedIntentsFromManaged,
                forwarderActivityInfo, " from managed profile should be forwarded to the " +
                "primary profile but is not.", true);

        // Check for intents which cannot be forwarded to the primary profile.
        success &= checkForIntentsNotHandled(notForwardedIntentsFromManaged,
                forwarderActivityInfo, "from managed profile should not be forwarded to the " +
                "primary profile but it is.", false);
        return success;
    }

    /**
     * Checks if the intentForwarderActivity can handle the intent passed.
     */
    private boolean canForwarderActivityHandleIntent(Intent intent,
            ActivityInfo forwarderActivityInfo) {
        // Get all the activities which can handle the intent.
        List<ResolveInfo> resolveInfoList =
                mContext.getPackageManager().queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        // Check if intentForwarderActivity is part of the list.
        for (ResolveInfo resolveInfo : resolveInfoList) {
            if (forwarderActivityInfo.packageName.equals(resolveInfo.activityInfo.packageName)
                    && forwarderActivityInfo.name.equals(resolveInfo.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the class name of the intentForwarderActivity.
     */
    private ActivityInfo getForwarderActivityInfo(String action) {
        Intent intent = new Intent(action);
        List<ResolveInfo> resolveInfoList =
                mContext.getPackageManager().queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfoList.isEmpty() || resolveInfoList.size() > 1) {
            Log.d(TAG, "There should be exactly one activity IntentForwarder which " +
                    "handles the intent " + intent);
            return null;
        }
        return resolveInfoList.get(0).activityInfo;
    }

    /**
     * Checks if the intents passed are correctly handled.
     * @return {@code false} if at least one intent is not handled correctly.
     */
    private boolean checkForIntentsNotHandled(ArrayList<Intent> intentList,
            ActivityInfo expectedForwarderActivityInfo, String errorMessage, boolean canResolve) {
        boolean success = true;
        for (Intent intent : intentList) {
            if (canForwarderActivityHandleIntent(intent,
                    expectedForwarderActivityInfo) != canResolve) {
                Log.e(TAG, intent + " " + errorMessage);
                success = false;
            }
        }
        return success;
    }
}
