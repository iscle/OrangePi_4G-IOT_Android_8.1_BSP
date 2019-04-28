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

package com.android.tv.settings.accessories;

import android.app.Fragment;
import android.os.Bundle;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.TvSettingsActivity;

public class BluetoothAccessoryActivity extends TvSettingsActivity {

    public static final String EXTRA_ACCESSORY_ADDRESS = "accessory_address";
    public static final String EXTRA_ACCESSORY_NAME = "accessory_name";
    public static final String EXTRA_ACCESSORY_ICON_ID = "accessory_icon_res";

    @Override
    protected Fragment createSettingsFragment() {
        String deviceAddress = null;
        String deviceName;
        int deviceImgId;
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            deviceAddress = bundle.getString(EXTRA_ACCESSORY_ADDRESS);
            deviceName = bundle.getString(EXTRA_ACCESSORY_NAME);
            deviceImgId = bundle.getInt(EXTRA_ACCESSORY_ICON_ID);
        } else {
            deviceName = getString(R.string.accessory_options);
            deviceImgId = R.drawable.ic_qs_bluetooth_not_connected;
        }

        return SettingsFragment.newInstance(deviceAddress, deviceName, deviceImgId);
    }

    public static class SettingsFragment extends BaseSettingsFragment {

        public static SettingsFragment newInstance(String deviceAddress, String deviceName,
                int deviceImgId) {
            final Bundle b = new Bundle(3);
            b.putString(EXTRA_ACCESSORY_ADDRESS, deviceAddress);
            b.putString(EXTRA_ACCESSORY_NAME, deviceName);
            b.putInt(EXTRA_ACCESSORY_ICON_ID, deviceImgId);
            final SettingsFragment f = new SettingsFragment();
            f.setArguments(b);
            return f;
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            final Bundle args = getArguments();
            String deviceAddress = args.getString(EXTRA_ACCESSORY_ADDRESS);
            String deviceName = args.getString(EXTRA_ACCESSORY_NAME);
            int deviceImgId = args.getInt(EXTRA_ACCESSORY_ICON_ID);
            startPreferenceFragment(
                    BluetoothAccessoryFragment.newInstance(deviceAddress, deviceName, deviceImgId));

        }
    }
}
