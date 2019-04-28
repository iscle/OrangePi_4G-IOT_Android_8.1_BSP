/*
 * Copyright (C) 2012-2014 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.hfp.HeadsetHalConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

final class RemoteDevices {
    private static final boolean DBG = false;
    private static final String TAG = "BluetoothRemoteDevices";

    // Maximum number of device properties to remember
    private static final int MAX_DEVICE_QUEUE_SIZE = 200;

    private static BluetoothAdapter mAdapter;
    private static AdapterService mAdapterService;
    private static ArrayList<BluetoothDevice> mSdpTracker;
    private final Object mObject = new Object();

    private static final int UUID_INTENT_DELAY = 6000;
    private static final int MESSAGE_UUID_INTENT = 1;

    private final HashMap<String, DeviceProperties> mDevices;
    private Queue<String> mDeviceQueue;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothHeadset.ACTION_HF_INDICATORS_VALUE_CHANGED:
                    onHfIndicatorValueChanged(intent);
                    break;
                case BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT:
                    onVendorSpecificHeadsetEvent(intent);
                    break;
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    onHeadsetConnectionStateChanged(intent);
                    break;
                default:
                    Log.w(TAG, "Unhandled intent: " + intent);
                    break;
            }
        }
    };

    RemoteDevices(AdapterService service) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAdapterService = service;
        mSdpTracker = new ArrayList<BluetoothDevice>();
        mDevices = new HashMap<String, DeviceProperties>();
        mDeviceQueue = new LinkedList<String>();
    }

    /**
     * Init should be called before using this RemoteDevices object
     */
    void init() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHeadset.ACTION_HF_INDICATORS_VALUE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
        filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "."
                + BluetoothAssignedNumbers.PLANTRONICS);
        filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "."
                + BluetoothAssignedNumbers.APPLE);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        mAdapterService.registerReceiver(mReceiver, filter);
    }

    /**
     * Clean up should be called when this object is no longer needed, must be called after init()
     */
    void cleanup() {
        // Unregister receiver first, mAdapterService is never null
        mAdapterService.unregisterReceiver(mReceiver);
        reset();
    }

    /**
     * Reset should be called when the state of this object needs to be cleared
     * RemoteDevices is still usable after reset
     */
    void reset() {
        if (mSdpTracker !=null)
            mSdpTracker.clear();

        if (mDevices != null)
            mDevices.clear();

        if (mDeviceQueue != null)
            mDeviceQueue.clear();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    DeviceProperties getDeviceProperties(BluetoothDevice device) {
        synchronized (mDevices) {
            return mDevices.get(device.getAddress());
        }
    }

    BluetoothDevice getDevice(byte[] address) {
      /// M: ALPS02113131:
      /// Add mDevices lock to prevent race conditions and ConcurrentModificationException
        synchronized (mDevices) {
            DeviceProperties prop = mDevices.get(Utils.getAddressStringFromByte(address));
            if (prop == null)
               return null;
            return prop.getDevice();
        }
    }

    DeviceProperties addDeviceProperties(byte[] address) {
        synchronized (mDevices) {
            DeviceProperties prop = new DeviceProperties();
            prop.mDevice = mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
            prop.mAddress = address;
            String key = Utils.getAddressStringFromByte(address);
            DeviceProperties pv = mDevices.put(key, prop);

            if (pv == null) {
                mDeviceQueue.offer(key);
                if (mDeviceQueue.size() > MAX_DEVICE_QUEUE_SIZE) {
                    String deleteKey = mDeviceQueue.poll();
                    for (BluetoothDevice device : mAdapterService.getBondedDevices()) {
                        if (device.getAddress().equals(deleteKey)) return prop;
                    }
                    debugLog("Removing device " + deleteKey + " from property map");
                    mDevices.remove(deleteKey);
                }
            }
            return prop;
        }
    }

    class DeviceProperties {
        private String mName;
        private byte[] mAddress;
        private int mBluetoothClass = BluetoothClass.Device.Major.UNCATEGORIZED;
        private short mRssi;
        private ParcelUuid[] mUuids;
        private int mDeviceType;
        private String mAlias;
        private int mBondState;
        private BluetoothDevice mDevice;
        private boolean isBondingInitiatedLocally;
        private int mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;

        DeviceProperties() {
            mBondState = BluetoothDevice.BOND_NONE;
        }

        /**
         * @return the mName
         */
        String getName() {
            synchronized (mObject) {
                return mName;
            }
        }

        /**
         * @return the mClass
         */
        int getBluetoothClass() {
            synchronized (mObject) {
                return mBluetoothClass;
            }
        }

        /**
         * @return the mUuids
         */
        ParcelUuid[] getUuids() {
            synchronized (mObject) {
                return mUuids;
            }
        }

        /**
         * @return the mAddress
         */
        byte[] getAddress() {
            synchronized (mObject) {
                return mAddress;
            }
        }

        /**
         * @return the mDevice
         */
        BluetoothDevice getDevice() {
            synchronized (mObject) {
                return mDevice;
            }
        }

        /**
         * @return mRssi
         */
        short getRssi() {
            synchronized (mObject) {
                return mRssi;
            }
        }

        /**
         * @return mDeviceType
         */
        int getDeviceType() {
            synchronized (mObject) {
                return mDeviceType;
            }
        }

        /**
         * @return the mAlias
         */
        String getAlias() {
            synchronized (mObject) {
                return mAlias;
            }
        }

        /**
         * @param mAlias the mAlias to set
         */
        void setAlias(BluetoothDevice device, String mAlias) {
            synchronized (mObject) {
                this.mAlias = mAlias;
                mAdapterService.setDevicePropertyNative(mAddress,
                    AbstractionLayer.BT_PROPERTY_REMOTE_FRIENDLY_NAME, mAlias.getBytes());
                Intent intent = new Intent(BluetoothDevice.ACTION_ALIAS_CHANGED);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                intent.putExtra(BluetoothDevice.EXTRA_NAME, mAlias);
                mAdapterService.sendBroadcast(intent, AdapterService.BLUETOOTH_PERM);
            }
        }

        /**
         * @param mBondState the mBondState to set
         */
        void setBondState(int mBondState) {
            synchronized (mObject) {
                this.mBondState = mBondState;
                if (mBondState == BluetoothDevice.BOND_NONE)
                {
                    /* Clearing the Uuids local copy when the device is unpaired. If not cleared,
                    cachedBluetoothDevice issued a connect using the local cached copy of uuids,
                    without waiting for the ACTION_UUID intent.
                    This was resulting in multiple calls to connect().*/
                    mUuids = null;
                }
            }
        }

        /**
         * @return the mBondState
         */
        int getBondState() {
            synchronized (mObject) {
                return mBondState;
            }
        }

        /**
         * @param isBondingInitiatedLocally wether bonding is initiated locally
         */
        void setBondingInitiatedLocally(boolean isBondingInitiatedLocally) {
            synchronized (mObject) {
                this.isBondingInitiatedLocally = isBondingInitiatedLocally;
            }
        }

        /**
         * @return the isBondingInitiatedLocally
         */
        boolean isBondingInitiatedLocally() {
            synchronized (mObject) {
                return isBondingInitiatedLocally;
            }
        }

        int getBatteryLevel() {
            synchronized (mObject) {
                return mBatteryLevel;
            }
        }

        /**
         * @param batteryLevel the mBatteryLevel to set
         */
        void setBatteryLevel(int batteryLevel) {
            synchronized (mObject) {
                this.mBatteryLevel = batteryLevel;
            }
        }
    }

    private void sendUuidIntent(BluetoothDevice device) {
        DeviceProperties prop = getDeviceProperties(device);
        Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, prop == null ? null : prop.mUuids);
        mAdapterService.sendBroadcast(intent, AdapterService.BLUETOOTH_ADMIN_PERM);

        //Remove the outstanding UUID request
        mSdpTracker.remove(device);
    }

    /**
     * When bonding is initiated to remote device that we have never seen, i.e Out Of Band pairing,
     * we must add device first before setting it's properties. This is a helper method for doing
     * that.
     */
    void setBondingInitiatedLocally(byte[] address) {
        DeviceProperties properties;

        BluetoothDevice device = getDevice(address);
        if (device == null) {
            properties = addDeviceProperties(address);
        } else {
            properties = getDeviceProperties(device);
        }

        properties.setBondingInitiatedLocally(true);
    }

    /**
     * Update battery level in device properties
     * @param device The remote device to be updated
     * @param batteryLevel Battery level Indicator between 0-100,
     *                    {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN} is error
     */
    @VisibleForTesting
    void updateBatteryLevel(BluetoothDevice device, int batteryLevel) {
        if (device == null || batteryLevel < 0 || batteryLevel > 100) {
            warnLog("Invalid parameters device=" + String.valueOf(device == null)
                    + ", batteryLevel=" + String.valueOf(batteryLevel));
            return;
        }
        DeviceProperties deviceProperties = getDeviceProperties(device);
        if (deviceProperties == null) {
            deviceProperties = addDeviceProperties(Utils.getByteAddress(device));
        }
        synchronized (mObject) {
            int currentBatteryLevel = deviceProperties.getBatteryLevel();
            if (batteryLevel == currentBatteryLevel) {
                debugLog("Same battery level for device " + device + " received "
                        + String.valueOf(batteryLevel) + "%");
                return;
            }
            deviceProperties.setBatteryLevel(batteryLevel);
        }
        sendBatteryLevelChangedBroadcast(device, batteryLevel);
        Log.d(TAG, "Updated device " + device + " battery level to " + batteryLevel + "%");
    }

    /**
     * Reset battery level property to {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN} for a device
     * @param device device whose battery level property needs to be reset
     */
    @VisibleForTesting
    void resetBatteryLevel(BluetoothDevice device) {
        if (device == null) {
            warnLog("Device is null");
            return;
        }
        DeviceProperties deviceProperties = getDeviceProperties(device);
        if (deviceProperties == null) {
            return;
        }
        synchronized (mObject) {
            if (deviceProperties.getBatteryLevel() == BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                debugLog("Battery level was never set or is already reset, device=" + device);
                return;
            }
            deviceProperties.setBatteryLevel(BluetoothDevice.BATTERY_LEVEL_UNKNOWN);
        }
        sendBatteryLevelChangedBroadcast(device, BluetoothDevice.BATTERY_LEVEL_UNKNOWN);
        Log.d(TAG, "Reset battery level, device=" + device);
    }

    private void sendBatteryLevelChangedBroadcast(BluetoothDevice device, int batteryLevel) {
        Intent intent = new Intent(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BATTERY_LEVEL, batteryLevel);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mAdapterService.sendBroadcast(intent, AdapterService.BLUETOOTH_PERM);
    }

    void devicePropertyChangedCallback(byte[] address, int[] types, byte[][] values) {
        Intent intent;
        byte[] val;
        int type;
        BluetoothDevice bdDevice = getDevice(address);
        DeviceProperties device;
        if (bdDevice == null) {
            debugLog("Added new device property");
            device = addDeviceProperties(address);
            bdDevice = getDevice(address);
        } else {
            device = getDeviceProperties(bdDevice);
        }

        if (types.length <= 0) {
            errorLog("No properties to update");
            return;
        }

        for (int j = 0; j < types.length; j++) {
            type = types[j];
            val = values[j];
            if (val.length > 0) {
                synchronized(mObject) {
                    debugLog("Property type: " + type);
                    switch (type) {
                        case AbstractionLayer.BT_PROPERTY_BDNAME:
                            device.mName = new String(val);
                            intent = new Intent(BluetoothDevice.ACTION_NAME_CHANGED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bdDevice);
                            intent.putExtra(BluetoothDevice.EXTRA_NAME, device.mName);
                            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                            mAdapterService.sendBroadcast(intent, mAdapterService.BLUETOOTH_PERM);
                            debugLog("Remote Device name is: " + device.mName);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_FRIENDLY_NAME:
                            if (device.mAlias != null) {
                                System.arraycopy(val, 0, device.mAlias, 0, val.length);
                            }
                            else {
                                device.mAlias = new String(val);
                            }
                            break;
                        case AbstractionLayer.BT_PROPERTY_BDADDR:
                            device.mAddress = val;
                            debugLog("Remote Address is:" + Utils.getAddressStringFromByte(val));
                            break;
                        case AbstractionLayer.BT_PROPERTY_CLASS_OF_DEVICE:
                            device.mBluetoothClass =  Utils.byteArrayToInt(val);
                            intent = new Intent(BluetoothDevice.ACTION_CLASS_CHANGED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bdDevice);
                            intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                                    new BluetoothClass(device.mBluetoothClass));
                            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                            mAdapterService.sendBroadcast(intent, mAdapterService.BLUETOOTH_PERM);
                            debugLog("Remote class is:" + device.mBluetoothClass);
                            break;
                        case AbstractionLayer.BT_PROPERTY_UUIDS:
                            int numUuids = val.length/AbstractionLayer.BT_UUID_SIZE;
                            device.mUuids = Utils.byteArrayToUuid(val);
                            if (mAdapterService.getState() == BluetoothAdapter.STATE_ON)
                                sendUuidIntent(bdDevice);
                            break;
                        case AbstractionLayer.BT_PROPERTY_TYPE_OF_DEVICE:
                            // The device type from hal layer, defined in bluetooth.h,
                            // matches the type defined in BluetoothDevice.java
                            device.mDeviceType = Utils.byteArrayToInt(val);
                            break;
                        case AbstractionLayer.BT_PROPERTY_REMOTE_RSSI:
                            // RSSI from hal is in one byte
                            device.mRssi = val[0];
                            break;
                    }
                }
            }
        }
    }

    void deviceFoundCallback(byte[] address) {
        // The device properties are already registered - we can send the intent
        // now
        BluetoothDevice device = getDevice(address);
        debugLog("deviceFoundCallback: Remote Address is:" + device);
        DeviceProperties deviceProp = getDeviceProperties(device);
        if (deviceProp == null) {
            errorLog("Device Properties is null for Device:" + device);
            return;
        }

        Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_CLASS,
                new BluetoothClass(deviceProp.mBluetoothClass));
        intent.putExtra(BluetoothDevice.EXTRA_RSSI, deviceProp.mRssi);
        intent.putExtra(BluetoothDevice.EXTRA_NAME, deviceProp.mName);

        mAdapterService.sendBroadcastMultiplePermissions(intent,
                new String[] {AdapterService.BLUETOOTH_PERM,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    void aclStateChangeCallback(int status, byte[] address, int newState) {
        BluetoothDevice device = getDevice(address);

        if (device == null) {
            errorLog("aclStateChangeCallback: device is NULL, address="
                    + Utils.getAddressStringFromByte(address) + ", newState=" + newState);
            return;
        }
        int state = mAdapterService.getState();

        Intent intent = null;
        if (newState == AbstractionLayer.BT_ACL_STATE_CONNECTED) {
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_ON) {
                intent = new Intent(BluetoothDevice.ACTION_ACL_CONNECTED);
            } else if (state == BluetoothAdapter.STATE_BLE_ON || state == BluetoothAdapter.STATE_BLE_TURNING_ON) {
                intent = new Intent(BluetoothAdapter.ACTION_BLE_ACL_CONNECTED);
            }
            debugLog("aclStateChangeCallback: Adapter State: "
                    + BluetoothAdapter.nameForState(state) + " Connected: " + device);
        } else {
            if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                // Send PAIRING_CANCEL intent to dismiss any dialog requesting bonding.
                intent = new Intent(BluetoothDevice.ACTION_PAIRING_CANCEL);
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                intent.setPackage(mAdapterService.getString(R.string.pairing_ui_package));
                mAdapterService.sendBroadcast(intent, mAdapterService.BLUETOOTH_PERM);
            }
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_OFF) {
                intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_BLE_ON || state == BluetoothAdapter.STATE_BLE_TURNING_OFF) {
                intent = new Intent(BluetoothAdapter.ACTION_BLE_ACL_DISCONNECTED);
            }
            // Reset battery level on complete disconnection
            if (mAdapterService.getConnectionState(device) == 0) {
                resetBatteryLevel(device);
            }
            debugLog("aclStateChangeCallback: Adapter State: "
                    + BluetoothAdapter.nameForState(state) + " Disconnected: " + device);
        }

        if (intent != null) {
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mAdapterService.sendBroadcast(intent, mAdapterService.BLUETOOTH_PERM);
        } else {
            Log.e(TAG, "aclStateChangeCallback intent is null. deviceBondState: "
                    + device.getBondState());
        }
    }


    void fetchUuids(BluetoothDevice device) {
        if (mSdpTracker.contains(device)) return;
        mSdpTracker.add(device);

        Message message = mHandler.obtainMessage(MESSAGE_UUID_INTENT);
        message.obj = device;
        mHandler.sendMessageDelayed(message, UUID_INTENT_DELAY);

        mAdapterService.getRemoteServicesNative(Utils.getBytesFromAddress(device.getAddress()));
    }

    void updateUuids(BluetoothDevice device) {
        Message message = mHandler.obtainMessage(MESSAGE_UUID_INTENT);
        message.obj = device;
        mHandler.sendMessage(message);
    }

    /**
     * Handles headset connection state change event
     * @param intent must be {@link BluetoothHeadset#ACTION_CONNECTION_STATE_CHANGED} intent
     */
    @VisibleForTesting
    void onHeadsetConnectionStateChanged(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            Log.e(TAG, "onHeadsetConnectionStateChanged() remote device is null");
            return;
        }
        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                == BluetoothProfile.STATE_DISCONNECTED) {
            // TODO: Rework this when non-HFP sources of battery level indication is added
            resetBatteryLevel(device);
        }
    }

    @VisibleForTesting
    void onHfIndicatorValueChanged(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            Log.e(TAG, "onHfIndicatorValueChanged() remote device is null");
            return;
        }
        int indicatorId = intent.getIntExtra(BluetoothHeadset.EXTRA_HF_INDICATORS_IND_ID, -1);
        int indicatorValue = intent.getIntExtra(BluetoothHeadset.EXTRA_HF_INDICATORS_IND_VALUE, -1);
        if (indicatorId == HeadsetHalConstants.HF_INDICATOR_BATTERY_LEVEL_STATUS) {
            updateBatteryLevel(device, indicatorValue);
        }
    }

    /**
     * Handle {@link BluetoothHeadset#ACTION_VENDOR_SPECIFIC_HEADSET_EVENT} intent
     * @param intent must be {@link BluetoothHeadset#ACTION_VENDOR_SPECIFIC_HEADSET_EVENT} intent
     */
    @VisibleForTesting
    void onVendorSpecificHeadsetEvent(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() remote device is null");
            return;
        }
        String cmd =
                intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
        if (cmd == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() command is null");
            return;
        }
        int cmdType = intent.getIntExtra(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);
        // Only process set command
        if (cmdType != BluetoothHeadset.AT_CMD_TYPE_SET) {
            debugLog("onVendorSpecificHeadsetEvent() only SET command is processed");
            return;
        }
        Object[] args = (Object[]) intent.getExtras().get(
                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
        if (args == null) {
            Log.e(TAG, "onVendorSpecificHeadsetEvent() arguments are null");
            return;
        }
        int batteryPercent = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        switch (cmd) {
            case BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XEVENT:
                batteryPercent = getBatteryLevelFromXEventVsc(args);
                break;
            case BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV:
                batteryPercent = getBatteryLevelFromAppleBatteryVsc(args);
                break;
        }
        if (batteryPercent != BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
            updateBatteryLevel(device, batteryPercent);
            infoLog("Updated device " + device + " battery level to "
                    + String.valueOf(batteryPercent) + "%");
        }
    }

    /**
     * Parse
     *      AT+IPHONEACCEV=[NumberOfIndicators],[IndicatorType],[IndicatorValue]
     * vendor specific event
     * @param args Array of arguments on the right side of assignment
     * @return Battery level in percents, [0-100], {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN}
     *         when there is an error parsing the arguments
     */
    @VisibleForTesting
    static int getBatteryLevelFromAppleBatteryVsc(Object[] args) {
        if (args.length == 0) {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() empty arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int numKvPair;
        if (args[0] instanceof Integer) {
            numKvPair = (Integer) args[0];
        } else {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing number of arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (args.length != (numKvPair * 2 + 1)) {
            Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() number of arguments does not match");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int indicatorType;
        int indicatorValue = -1;
        for (int i = 0; i < numKvPair; ++i) {
            Object indicatorTypeObj = args[2 * i + 1];
            if (indicatorTypeObj instanceof Integer) {
                indicatorType = (Integer) indicatorTypeObj;
            } else {
                Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing indicator type");
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }
            if (indicatorType
                    != BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL) {
                continue;
            }
            Object indicatorValueObj = args[2 * i + 2];
            if (indicatorValueObj instanceof Integer) {
                indicatorValue = (Integer) indicatorValueObj;
            } else {
                Log.w(TAG, "getBatteryLevelFromAppleBatteryVsc() error parsing indicator value");
                return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
            }
            break;
        }
        return (indicatorValue < 0 || indicatorValue > 9) ? BluetoothDevice.BATTERY_LEVEL_UNKNOWN
                                                          : (indicatorValue + 1) * 10;
    }

    /**
     * Parse
     *      AT+XEVENT=BATTERY,[Level],[NumberOfLevel],[MinutesOfTalk],[IsCharging]
     * vendor specific event
     * @param args Array of arguments on the right side of SET command
     * @return Battery level in percents, [0-100], {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN}
     *         when there is an error parsing the arguments
     */
    @VisibleForTesting
    static int getBatteryLevelFromXEventVsc(Object[] args) {
        if (args.length == 0) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() empty arguments");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        Object eventNameObj = args[0];
        if (!(eventNameObj instanceof String)) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() error parsing event name");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        String eventName = (String) eventNameObj;
        if (!eventName.equals(
                    BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_XEVENT_BATTERY_LEVEL)) {
            infoLog("getBatteryLevelFromXEventVsc() skip none BATTERY event: " + eventName);
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (args.length != 5) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() wrong battery level event length: "
                            + String.valueOf(args.length));
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        if (!(args[1] instanceof Integer) || !(args[2] instanceof Integer)) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() error parsing event values");
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        int batteryLevel = (Integer) args[1];
        int numberOfLevels = (Integer) args[2];
        if (batteryLevel < 0 || numberOfLevels < 0 || batteryLevel > numberOfLevels) {
            Log.w(TAG, "getBatteryLevelFromXEventVsc() wrong event value, batteryLevel="
                            + String.valueOf(batteryLevel) + ", numberOfLevels="
                            + String.valueOf(numberOfLevels));
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        return batteryLevel * 100 / numberOfLevels;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_UUID_INTENT:
                BluetoothDevice device = (BluetoothDevice)msg.obj;
                if (device != null) {
                    sendUuidIntent(device);
                }
                break;
            }
        }
    };

    private static void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private static void debugLog(String msg) {
        if (DBG) Log.d(TAG, msg);
    }

    private static void infoLog(String msg) {
        if (DBG) Log.i(TAG, msg);
    }

    private static void warnLog(String msg) {
        Log.w(TAG, msg);
    }

}
