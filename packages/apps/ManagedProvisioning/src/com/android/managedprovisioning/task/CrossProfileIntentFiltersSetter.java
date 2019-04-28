/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.task;

import static android.content.pm.PackageManager.ONLY_IF_NO_MATCH_FOUND;
import static android.content.pm.PackageManager.SKIP_CURRENT_PROFILE;
import static android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.hardware.usb.UsbManager;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.task.CrossProfileIntentFilter.Direction;

import java.util.Arrays;
import java.util.List;

/**
 * Class to set CrossProfileIntentFilters during managed profile creation, and reset them after an
 * ota.
 */
public class CrossProfileIntentFiltersSetter {

    // Intents from profile to parent user

    /** Emergency call intent with mime type is always resolved by primary user. */
    private static final CrossProfileIntentFilter EMERGENCY_CALL_MIME =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, SKIP_CURRENT_PROFILE)
                    .addAction(Intent.ACTION_CALL_EMERGENCY)
                    .addAction(Intent.ACTION_CALL_PRIVILEGED)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addDataType("vnd.android.cursor.item/phone")
                    .addDataType("vnd.android.cursor.item/phone_v2")
                    .addDataType("vnd.android.cursor.item/person")
                    .addDataType("vnd.android.cursor.dir/calls")
                    .addDataType("vnd.android.cursor.item/calls")
                    .build();

    /** Emergency call intent with data schemes is always resolved by primary user. */
    private static final CrossProfileIntentFilter EMERGENCY_CALL_DATA =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, SKIP_CURRENT_PROFILE)
                    .addAction(Intent.ACTION_CALL_EMERGENCY)
                    .addAction(Intent.ACTION_CALL_PRIVILEGED)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addDataScheme("tel")
                    .addDataScheme("sip")
                    .addDataScheme("voicemail")
                    .build();

    /** Dial intent with mime type can be handled by either managed profile or its parent user. */
    private static final CrossProfileIntentFilter DIAL_MIME =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, ONLY_IF_NO_MATCH_FOUND)
                    .addAction(Intent.ACTION_DIAL)
                    .addAction(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addDataType("vnd.android.cursor.item/phone")
                    .addDataType("vnd.android.cursor.item/phone_v2")
                    .addDataType("vnd.android.cursor.item/person")
                    .addDataType("vnd.android.cursor.dir/calls")
                    .addDataType("vnd.android.cursor.item/calls")
                    .build();

    /** Dial intent with data scheme can be handled by either managed profile or its parent user. */
    private static final CrossProfileIntentFilter DIAL_DATA =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, ONLY_IF_NO_MATCH_FOUND)
                    .addAction(Intent.ACTION_DIAL)
                    .addAction(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addDataScheme("tel")
                    .addDataScheme("sip")
                    .addDataScheme("voicemail")
                    .build();

    /**
     * Dial intent with no data scheme or type can be handled by either managed profile or its
     * parent user.
     */
    private static final CrossProfileIntentFilter DIAL_RAW =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, ONLY_IF_NO_MATCH_FOUND)
                    .addAction(Intent.ACTION_DIAL)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .build();

    /** Pressing the call button can be handled by either managed profile or its parent user. */
    private static final CrossProfileIntentFilter CALL_BUTTON =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, ONLY_IF_NO_MATCH_FOUND)
                    .addAction(Intent.ACTION_CALL_BUTTON)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    /** SMS and MMS are exclusively handled by the primary user. */
    private static final CrossProfileIntentFilter SMS_MMS =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, SKIP_CURRENT_PROFILE)
                    .addAction(Intent.ACTION_VIEW)
                    .addAction(Intent.ACTION_SENDTO)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addDataScheme("sms")
                    .addDataScheme("smsto")
                    .addDataScheme("mms")
                    .addDataScheme("mmsto")
                    .build();

    /** Mobile network settings is always shown in the primary user. */
    private static final CrossProfileIntentFilter MOBILE_NETWORK_SETTINGS =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, SKIP_CURRENT_PROFILE)
                    .addAction(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS)
                    .addAction(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    /** HOME intent is always resolved by the primary user. */
    @VisibleForTesting
    static final CrossProfileIntentFilter HOME =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, SKIP_CURRENT_PROFILE)
                    .addAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_HOME)
                    .build();

    /** Get content can be forwarded to parent user. */
    private static final CrossProfileIntentFilter GET_CONTENT =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addDataType("*/*")
                    .build();

    /** Open document intent can be forwarded to parent user. */
    private static final CrossProfileIntentFilter OPEN_DOCUMENT =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addDataType("*/*")
                    .build();

    /** Pick for any data type can be forwarded to parent user. */
    private static final CrossProfileIntentFilter ACTION_PICK_DATA =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(Intent.ACTION_PICK)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addDataType("*/*")
                    .build();

    /** Pick without data type can be forwarded to parent user. */
    private static final CrossProfileIntentFilter ACTION_PICK_RAW =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(Intent.ACTION_PICK)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    /** Speech recognition can be performed by primary user. */
    private static final CrossProfileIntentFilter RECOGNIZE_SPEECH =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(ACTION_RECOGNIZE_SPEECH)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    /** Media capture can be performed by primary user. */
    private static final CrossProfileIntentFilter MEDIA_CAPTURE =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(MediaStore.ACTION_IMAGE_CAPTURE)
                    .addAction(MediaStore.ACTION_IMAGE_CAPTURE_SECURE)
                    .addAction(MediaStore.ACTION_VIDEO_CAPTURE)
                    .addAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                    .addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                    .addAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    /** Alarm setting can be performed by primary user. */
    private static final CrossProfileIntentFilter SET_ALARM =
            new CrossProfileIntentFilter.Builder(Direction.TO_PARENT, 0)
                    .addAction(AlarmClock.ACTION_SET_ALARM)
                    .addAction(AlarmClock.ACTION_SHOW_ALARMS)
                    .addAction(AlarmClock.ACTION_SET_TIMER)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    // Intents from parent to profile user

    /** ACTION_SEND can be forwarded to the managed profile on user's choice. */
    @VisibleForTesting
    static final CrossProfileIntentFilter ACTION_SEND =
            new CrossProfileIntentFilter.Builder(Direction.TO_PROFILE, 0)
                    .addAction(Intent.ACTION_SEND)
                    .addAction(Intent.ACTION_SEND_MULTIPLE)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addDataType("*/*")
                    .build();

    /** USB devices attached can get forwarded to the profile. */
    private static final CrossProfileIntentFilter USB_DEVICE_ATTACHED =
            new CrossProfileIntentFilter.Builder(Direction.TO_PROFILE, 0)
                    .addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    .addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .build();

    @VisibleForTesting
    static final List<CrossProfileIntentFilter> FILTERS = Arrays.asList(
            EMERGENCY_CALL_MIME,
            EMERGENCY_CALL_DATA,
            DIAL_MIME,
            DIAL_DATA,
            DIAL_RAW,
            CALL_BUTTON,
            SMS_MMS,
            SET_ALARM,
            MEDIA_CAPTURE,
            RECOGNIZE_SPEECH,
            ACTION_PICK_RAW,
            ACTION_PICK_DATA,
            OPEN_DOCUMENT,
            GET_CONTENT,
            USB_DEVICE_ATTACHED,
            ACTION_SEND,
            HOME,
            MOBILE_NETWORK_SETTINGS);

    private final PackageManager mPackageManager;
    private final UserManager mUserManager;

    public CrossProfileIntentFiltersSetter(Context context) {
        this(context.getPackageManager(),
                (UserManager) context.getSystemService(Context.USER_SERVICE));
    }

    @VisibleForTesting
    CrossProfileIntentFiltersSetter(PackageManager packageManager, UserManager userManager) {
        mPackageManager = checkNotNull(packageManager);
        mUserManager = checkNotNull(userManager);
    }

    /**
     * Sets all default cross profile intent filters from {@code parentUserId} to
     * {@code managedProfileUserId}.
     */
    public void setFilters(int parentUserId, int managedProfileUserId) {
        ProvisionLogger.logd("Setting cross-profile intent filters");

        for (CrossProfileIntentFilter filter : FILTERS) {
            if (filter.direction == Direction.TO_PARENT) {
                mPackageManager.addCrossProfileIntentFilter(filter.filter, managedProfileUserId,
                        parentUserId, filter.flags);
            } else {
                mPackageManager.addCrossProfileIntentFilter(filter.filter, parentUserId,
                        managedProfileUserId, filter.flags);
            }
        }
    }

    /**
     * Reset the cross profile intent filters between {@code userId} and all of its managed profiles
     * if any.
     */
    public void resetFilters(int userId) {
        List<UserInfo> profiles = mUserManager.getProfiles(userId);
        if (profiles.size() <= 1) {
            return;
        }

        // Removes cross profile intent filters from the parent to all the managed profiles.
        mPackageManager.clearCrossProfileIntentFilters(userId);

        // For each managed profile reset cross profile intent filters
        for (UserInfo profile : profiles) {
            if (!profile.isManagedProfile()) {
                continue;
            }
            mPackageManager.clearCrossProfileIntentFilters(profile.id);
            setFilters(userId, profile.id);
        }
    }

}
