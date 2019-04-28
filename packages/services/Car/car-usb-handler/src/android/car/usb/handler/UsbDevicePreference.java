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
package android.car.usb.handler;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;

/**
 * Setting preference used for USB devices.
 */
public final class UsbDevicePreference extends Preference
        implements Preference.OnPreferenceClickListener {

    /**
     * Callbacks to handle preference changes.
     */
    public interface UsbDevicePreferenceCallback {
        /** Preference deleted */
        void onUsbDevicePreferenceDelete(Preference preference, UsbDeviceSettings settings);
    }

    private final UsbDeviceSettings mUsbDeviceSettings;
    private final UsbDevicePreferenceCallback mCallback;

    public UsbDevicePreference(Context context, UsbDeviceSettings usbDeviceSettings,
            UsbDevicePreferenceCallback callback) {
        super(context);
        mCallback = callback;
        mUsbDeviceSettings = usbDeviceSettings;
        setTitle(usbDeviceSettings.getDeviceName());
        if (usbDeviceSettings.getHandler() != null) {
            setSummary(usbDeviceSettings.getHandler().flattenToShortString());
        }
        setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.usb_pref_delete_title)
                .setMessage(String.format(
                        getContext().getResources().getString(R.string.usb_pref_delete_message),
                        mUsbDeviceSettings.getDeviceName()))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.usb_pref_delete_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mCallback.onUsbDevicePreferenceDelete(
                                        preference, mUsbDeviceSettings);
                            }})
                .setNegativeButton(R.string.usb_pref_delete_cancel, null)
                .show();
        return true;
    }
}
