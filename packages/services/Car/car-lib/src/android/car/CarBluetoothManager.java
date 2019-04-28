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
package android.car;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothDevice;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.ICarBluetooth;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * APIs for setting Car specific Bluetooth Connection Management policy
 *
 * @hide
 */
public final class CarBluetoothManager implements CarManagerBase {
    private static final String TAG = "CarBluetoothManager";
    private final Context mContext;
    private final ICarBluetooth mService;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0,
            BLUETOOTH_DEVICE_CONNECTION_PRIORITY_1})
    public @interface PriorityType {
    }

    public static final int BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0 = 0;
    public static final int BLUETOOTH_DEVICE_CONNECTION_PRIORITY_1 = 1;
    // Write an empty string to clear a Primary or Secondary device.
    public static final String BLUETOOTH_NO_PRIORITY_DEVICE = "";

    /**
     * Set the Auto Connect priority for a paired Bluetooth Device.
     * For example, if a device is tagged as a Primary device (Priority 0) for a supported
     * Bluetooth profile, every new Auto Connect attempt would start with trying to connect to
     * *that* device. This priority is set at a Bluetooth profile granularity.
     *
     * @param deviceToSet   - Device to set priority (Tag)
     * @param profileToSet  - BluetoothProfile to set priority for
     * @param priorityToSet - What priority level to set to
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void setBluetoothDeviceConnectionPriority(BluetoothDevice deviceToSet, int profileToSet,
            @PriorityType int priorityToSet) throws CarNotConnectedException {
        try {
            mService.setBluetoothDeviceConnectionPriority(deviceToSet, profileToSet, priorityToSet);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setBluetoothDeviceConnectionPriority failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Unset the Auto Connect priority for the given profile
     *
     * @param profileToClear  - Profile to unset priority
     * @param priorityToClear - Which priority to clear (Primary or Secondary)
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void clearBluetoothDeviceConnectionPriority(int profileToClear,
            @PriorityType int priorityToClear) throws CarNotConnectedException {
        try {
            mService.clearBluetoothDeviceConnectionPriority(profileToClear, priorityToClear);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "clearBluetoothDeviceConnectionPriority failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns if there is a device that has been tagged with the given priority for the given
     * profile.
     *
     * @param profile         - BluetoothProfile
     * @param priorityToCheck - Priority to check
     * @return true if there is a device present with the given priority, false if not
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean isPriorityDevicePresent(int profile, @PriorityType int priorityToCheck)
            throws CarNotConnectedException {
        try {
            return mService.isPriorityDevicePresent(profile, priorityToCheck);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "isPrioritySet failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the Bluetooth device address as a String that has been tagged with the given priority
     * for the given profile.
     *
     * @param profile         - BluetoothProfile
     * @param priorityToCheck - Priority to check
     * @return BluetoothDevice address if present, null if absent
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public String getDeviceNameWithPriority(int profile, @PriorityType int priorityToCheck)
            throws CarNotConnectedException {
        try {
            return mService.getDeviceNameWithPriority(profile, priorityToCheck);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getDeviceNameWithPriority failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /** @hide */
    public CarBluetoothManager(IBinder service, Context context) {
        mContext = context;
        mService = ICarBluetooth.Stub.asInterface(service);
    }

    @Override
    public void onCarDisconnected() {
    }
}
