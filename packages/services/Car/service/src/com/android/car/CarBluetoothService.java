/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car;

import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_0;
import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_1;
import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_0;
import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_1;
import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_0;
import static android.car.settings.CarSettings.Secure
        .KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_1;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.car.CarBluetoothManager;
import android.car.ICarBluetooth;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import java.io.PrintWriter;

/**
 * CarBluetoothService - deals with the automatically connecting to a known device via bluetooth.
 * Interacts with a policy -{@link BluetoothDeviceConnectionPolicy} -to initiate connections and
 * update status.
 * The {@link BluetoothDeviceConnectionPolicy} is responsible for finding the appropriate device to
 * connect for a specific profile.
 */

public class CarBluetoothService extends ICarBluetooth.Stub implements CarServiceBase {

    private static final String TAG = "CarBluetoothService";
    private final Context mContext;
    private final BluetoothDeviceConnectionPolicy mBluetoothDeviceConnectionPolicy;
    private static final boolean DBG = false;

    public CarBluetoothService(Context context, CarCabinService carCabinService,
            CarSensorService carSensorService, PerUserCarServiceHelper userSwitchService) {
        mContext = context;
        mBluetoothDeviceConnectionPolicy = BluetoothDeviceConnectionPolicy.create(mContext,
                carCabinService, carSensorService, userSwitchService, this);
    }

    @Override
    public void init() {
        mBluetoothDeviceConnectionPolicy.init();
    }

    @Override
    public synchronized void release() {
        mBluetoothDeviceConnectionPolicy.release();
    }

    /**
     * Set the Auto connect priority for a paired Bluetooth Device.
     * For example, if a device is tagged as a Primary device for a supported Bluetooth Profile,
     * every new Auto Connect attempt would start with trying to connect to *that* device.
     * This priority is set at a Bluetooth profile granularity
     *
     * @param deviceToSet   - Device to set priority (Tag)
     * @param profileToSet  - BluetoothProfile to set priority for.
     * @param priorityToSet - What priority level to set to
     * @hide
     */
    public void setBluetoothDeviceConnectionPriority(BluetoothDevice deviceToSet, int profileToSet,
            int priorityToSet) {
        setBluetoothDeviceConnectionPriority(deviceToSet.getAddress(), profileToSet, priorityToSet);
    }

    public void setBluetoothDeviceConnectionPriority(String deviceAddress, int profileToSet,
            int priorityToSet) {
        // Check if the caller has Bluetooth Admin Permissions
        enforceBluetoothAdminPermission();
        if (priorityToSet == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_1) {
            if (!isPriorityDevicePresent(profileToSet,
                    CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)) {
                Log.e(TAG, "Secondary Device not allowed without a primary device");
                return;
            }
        }
        // Write the priority preference to Secure settings.  The Bluetooth device connection policy
        // will look up the Settings when it initiates a connection
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                getKeyForProfile(profileToSet, priorityToSet), deviceAddress,
                ActivityManager.getCurrentUser());

    }

    /**
     * Unset the Auto connect priority for the given profile
     *
     * @param profileToClear  - Profile to unset priority
     * @param priorityToClear - Which priority to clear (Primary or Secondary)
     * @hide
     */
    public void clearBluetoothDeviceConnectionPriority(int profileToClear, int priorityToClear) {
        // Check if the caller has Bluetooth Admin F@Permissions
        enforceBluetoothAdminPermission();
        if (priorityToClear == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0) {
            if (isPriorityDevicePresent(profileToClear,
                    CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_1)) {
                Log.e(TAG, "Please remove Secondary device before removing Primary Device");
                return;
            }
        }
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                getKeyForProfile(profileToClear, priorityToClear),
                CarBluetoothManager.BLUETOOTH_NO_PRIORITY_DEVICE,
                ActivityManager.getCurrentUser());
    }

    /**
     * Returns if there is a device that has been tagged with the given priority for the given
     * profile.
     *
     * @param profile         - BluetoothProfile
     * @param priorityToCheck - Priority to check
     * @return true if there is a device present with the given priority, false if not
     */
    public boolean isPriorityDevicePresent(int profile, int priorityToCheck) {
        String deviceName = getDeviceNameWithPriority(profile, priorityToCheck);
        if (deviceName != null && !deviceName.equalsIgnoreCase(
                CarBluetoothManager.BLUETOOTH_NO_PRIORITY_DEVICE)) {
            return true;
        } else {
            if (DBG) {
                Log.d(TAG,
                        "No device present for priority: " + priorityToCheck + " profile: "
                                + profile);
            }
            return false;
        }
    }

    /**
     * Returns the Bluetooth device address as a String that has been tagged with the given priority
     * for the given profile.
     *
     * @param profile         - BluetoothProfile
     * @param priorityToCheck - Priority to check
     * @return BluetoothDevice address if present, null if absent
     */
    public String getDeviceNameWithPriority(int profile, int priorityToCheck) {
        String keyToQuery = null;
        String deviceName = null;
        enforceBluetoothAdminPermission();
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                keyToQuery = (priorityToCheck
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_1;
                break;
            case BluetoothProfile.HEADSET_CLIENT:
            case BluetoothProfile.PBAP_CLIENT:
                keyToQuery = (priorityToCheck
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_1;
                break;
            case BluetoothProfile.MAP_CLIENT:
                keyToQuery = (priorityToCheck
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_1;
                break;
            default:
                if (DBG) {
                    Log.d(TAG, "Unknown Bluetooth profile");
                }
        }
        if (keyToQuery != null) {
            deviceName = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                    keyToQuery, (int) ActivityManager.getCurrentUser());
        }
        return deviceName;
    }

    private void enforceBluetoothAdminPermission() {
        if (mContext != null
                && PackageManager.PERMISSION_GRANTED == mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.BLUETOOTH_ADMIN)) {
            return;
        }
        if (mContext == null) {
            Log.e(TAG, "CarBluetoothPrioritySettings does not have a Context");
        }
        throw new SecurityException("requires permission " + android.Manifest.permission.BLUETOOTH_ADMIN);
    }

    private String getKeyForProfile(int profile, int priority) {
        String keyToLookup = null;
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                keyToLookup = (priority
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_1;
                break;
            case BluetoothProfile.MAP_CLIENT:
                keyToLookup = (priority
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_1;
                break;
            case BluetoothProfile.PBAP_CLIENT:
                // fall through
            case BluetoothProfile.HEADSET_CLIENT:
                keyToLookup = (priority
                        == CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0)
                        ? KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_0
                        : KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_1;
                break;
            default:
                Log.e(TAG, "Unsupported Bluetooth profile to set priority to");
                break;
        }
        return keyToLookup;
    }

    @Override
    public synchronized void dump(PrintWriter writer) {
        mBluetoothDeviceConnectionPolicy.dump(writer);
    }

}
