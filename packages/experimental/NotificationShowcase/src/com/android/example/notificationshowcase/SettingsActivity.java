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

package com.android.example.notificationshowcase;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {
    public static final String KEY_GLOBAL_FADE = "pref_key_global_fade";
    public static final String KEY_SMS_ENABLE = "pref_key_sms_enable";
    public static final String KEY_SMS_NOISY = "pref_key_sms_noisy";
    public static final String KEY_SMS_SOUND = "pref_key_sms_sound";
    public static final String KEY_SMS_BUZZY = "pref_key_sms_buzzy";
    public static final String KEY_SMS_ONCE = "pref_key_sms_once";
    public static final String KEY_SMS_PRIORITY = "pref_key_sms_priority";
    public static final String KEY_SMS_PERSON = "pref_key_sms_person";
    public static final String KEY_PHONE_ENABLE = "pref_key_phone_enable";
    public static final String KEY_PHONE_NOISY = "pref_key_phone_noisy";
    public static final String KEY_PHONE_FULLSCREEN = "pref_key_phone_fullscreen";
    public static final String KEY_PHONE_PERSON = "pref_key_phone_person";
    public static final String KEY_UPLOAD_ENABLE = "pref_key_upload_enable";
    public static final String KEY_TIMER_ENABLE = "pref_key_timer_enable";
    public static final String KEY_CALENDAR_ENABLE = "pref_key_calendar_enable";
    public static final String KEY_SOCIAL_ENABLE = "pref_key_social_enable";
    public static final String KEY_INBOX_ENABLE = "pref_key_inbox_enable";
    public static final String KEY_PICTURE_ENABLE = "pref_key_picture_enable";

    public class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

        public SettingsFragment() {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}
