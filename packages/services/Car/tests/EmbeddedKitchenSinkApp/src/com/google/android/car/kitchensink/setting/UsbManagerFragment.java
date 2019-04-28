/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.car.kitchensink.setting;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Log;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.setting.usb.UsbDevicePreference;
import com.google.android.car.kitchensink.setting.usb.UsbDeviceSettings;
import com.google.android.car.kitchensink.setting.usb.UsbSettingsStorage;

import java.util.HashMap;
import java.util.List;

/**
 * Usb manager handles management for USB device settings.
 */
public class UsbManagerFragment extends PreferenceFragment
        implements UsbDevicePreference.UsbDevicePreferenceCallback {

    // TODO: for produciton settings version we need to handle detach through broadcast.

    private static final String TAG = UsbManagerFragment.class.getSimpleName();
    private static final boolean LOCAL_LOGD = true;
    private static final boolean LOCAL_LOGV = true;

    private UsbSettingsStorage mUsbSettingsStorage;
    private UsbManager mUsbManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.usb_manager_prefs);
        mUsbSettingsStorage = new UsbSettingsStorage(getContext());
        mUsbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        populateSettings();
    }

    private void populateSettings() {
        getPreferenceScreen().removeAll();
        HashMap<String, UsbDevice> attachedDevices = mUsbManager.getDeviceList();
        List<UsbDeviceSettings> allSavedSettings = mUsbSettingsStorage.getAllSettings();
        PreferenceGroup availableDevicesCategory =
                createPreferenceCategory(1, R.string.usb_available_devices);
        getPreferenceScreen().addPreference(availableDevicesCategory);

        PreferenceGroup savedDevicesCategory =
                createPreferenceCategory(2, R.string.usb_saved_devices);
        getPreferenceScreen().addPreference(savedDevicesCategory);

        for (UsbDevice attachedDevice : attachedDevices.values()) {
            if (LOCAL_LOGD) {
                Log.d(TAG, "Attached device: " + attachedDevice);
            }
            if (attachedDevice.getSerialNumber() == null) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Attached device: " + attachedDevice + " misseshave serial number");
                }
                continue;
            }
            UsbDeviceSettings deviceSettings = getSavedSetting(allSavedSettings, attachedDevice);
            if (deviceSettings != null) {
                // This might be slow if there are a lot of settings.
                allSavedSettings.remove(deviceSettings);
            } else {
                deviceSettings = UsbDeviceSettings.constructSettings(attachedDevice);
            }
            UsbDevicePreference preference = new UsbDevicePreference(
                    getContext(), deviceSettings, this);
            availableDevicesCategory.addPreference(preference);
        }
        availableDevicesCategory.setEnabled(availableDevicesCategory.getPreferenceCount() > 0);

        for (UsbDeviceSettings savedSettings : allSavedSettings) {
            UsbDevicePreference preference = new UsbDevicePreference(
                    getContext(), savedSettings, this);
            savedDevicesCategory.addPreference(preference);
        }
        savedDevicesCategory.setEnabled(savedDevicesCategory.getPreferenceCount() > 0);
    }

    private PreferenceGroup createPreferenceCategory(int order, int titleId) {
        PreferenceGroup preferenceGroup = new PreferenceCategory(getContext());
        preferenceGroup.setTitle(titleId);
        preferenceGroup.setOrder(order);
        preferenceGroup.setSelectable(false);
        return preferenceGroup;
    }

    @Nullable
    private UsbDeviceSettings getSavedSetting(List<UsbDeviceSettings> settings, UsbDevice device) {
        for (UsbDeviceSettings savedSetting : settings) {
            if (savedSetting.matchesDevice(device)) {
                return savedSetting;
            }
        }
        return null;
    }

    @Override
    public void onUsbDevicePreferenceDelete(Preference preference, UsbDeviceSettings settings) {
        mUsbSettingsStorage.deleteSettings(
                settings.getSerialNumber(), settings.getVid(), settings.getPid());
        populateSettings();
    }

}
