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
 * limitations under the License
 */

package com.android.tv.settings.about;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.PreferenceScreen;

import com.android.tv.settings.PreferenceUtils;
import com.android.tv.settings.R;

@Keep
public class LegalFragment extends LeanbackPreferenceFragment {

    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    private static final String KEY_WEBVIEW_LICENSE = "webview_license";
    private static final String KEY_ADS = "ads";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.about_legal, null);
        final PreferenceScreen screen = getPreferenceScreen();

        final Context context = getActivity();
        PreferenceUtils.resolveSystemActivityOrRemove(context, screen,
                findPreference(KEY_TERMS), PreferenceUtils.FLAG_SET_TITLE);
        PreferenceUtils.resolveSystemActivityOrRemove(context, screen,
                findPreference(KEY_LICENSE), PreferenceUtils.FLAG_SET_TITLE);
        PreferenceUtils.resolveSystemActivityOrRemove(context, screen,
                findPreference(KEY_COPYRIGHT), PreferenceUtils.FLAG_SET_TITLE);
        PreferenceUtils.resolveSystemActivityOrRemove(context, screen,
                findPreference(KEY_WEBVIEW_LICENSE), PreferenceUtils.FLAG_SET_TITLE);
        PreferenceUtils.resolveSystemActivityOrRemove(context, screen,
                findPreference(KEY_ADS), PreferenceUtils.FLAG_SET_TITLE);
    }
}
