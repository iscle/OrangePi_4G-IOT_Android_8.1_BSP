/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.device.apps;

import android.app.Fragment;
import android.os.Bundle;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.TvSettingsActivity;

/**
 * Activity allowing the management of apps settings.
 */
public class AppsActivity extends TvSettingsActivity {

    // Used for storage only.
    public static final String EXTRA_VOLUME_UUID = "volumeUuid";
    public static final String EXTRA_VOLUME_NAME = "volumeName";

    @Override
    protected Fragment createSettingsFragment() {
        final Bundle args = getIntent().getExtras();
        String volumeUuid = null;
        String volumeName = null;
        if (args != null && args.containsKey(EXTRA_VOLUME_UUID)) {
            volumeUuid = args.getString(EXTRA_VOLUME_UUID);
            volumeName = args.getString(EXTRA_VOLUME_NAME);
        }
        return SettingsFragment.newInstance(volumeUuid, volumeName);
    }

    public static class SettingsFragment extends BaseSettingsFragment {

        public static SettingsFragment newInstance(String volumeUuid, String volumeName) {
            final Bundle b = new Bundle(2);
            b.putString(EXTRA_VOLUME_UUID, volumeUuid);
            b.putString(EXTRA_VOLUME_NAME, volumeName);
            final SettingsFragment f = new SettingsFragment();
            f.setArguments(b);
            return f;
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            final String volumeUuid = getArguments().getString(EXTRA_VOLUME_UUID);
            final String volumeName = getArguments().getString(EXTRA_VOLUME_NAME);
            startPreferenceFragment(AppsFragment.newInstance(volumeUuid, volumeName));
        }
    }
}
