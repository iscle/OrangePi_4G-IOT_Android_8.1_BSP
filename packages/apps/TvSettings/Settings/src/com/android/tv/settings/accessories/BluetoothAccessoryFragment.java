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

package com.android.tv.settings.accessories;

import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.R;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class BluetoothAccessoryFragment extends LeanbackPreferenceFragment {

    private static final boolean DEBUG = false;
    private static final String TAG = "BluetoothAccessoryFrag";

    private static final UUID GATT_BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private static final String KEY_CHANGE_NAME = "changeName";
    private static final String KEY_UNPAIR = "unpair";
    private static final String KEY_BATTERY = "battery";

    private static final String SAVE_STATE_UNPAIRING = "BluetoothAccessoryActivity.unpairing";

    private static final int UNPAIR_TIMEOUT = 5000;

    private static final String ARG_DEVICE = "device";
    private static final String ARG_ACCESSORY_ADDRESS = "accessory_address";
    private static final String ARG_ACCESSORY_NAME = "accessory_name";
    private static final String ARG_ACCESSORY_ICON_ID = "accessory_icon_res";

    private BluetoothDevice mDevice;
    private BluetoothGatt mDeviceGatt;
    private String mDeviceAddress;
    private String mDeviceName;
    private @DrawableRes int mDeviceImgId;
    private boolean mUnpairing;
    private Preference mChangeNamePref;
    private Preference mUnpairPref;
    private Preference mBatteryPref;

    private final Handler mHandler = new Handler();
    private Runnable mBailoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isResumed() && !getFragmentManager().popBackStackImmediate()) {
                getActivity().onBackPressed();
            }
        }
    };

    // Broadcast Receiver for Bluetooth related events
    private BroadcastReceiver mBroadcastReceiver;

    public static BluetoothAccessoryFragment newInstance(String deviceAddress, String deviceName,
            int deviceImgId) {
        final Bundle b = new Bundle(3);
        prepareArgs(b, deviceAddress, deviceName, deviceImgId);
        final BluetoothAccessoryFragment f = new BluetoothAccessoryFragment();
        f.setArguments(b);
        return f;
    }

    public static void prepareArgs(Bundle b, String deviceAddress, String deviceName,
            int deviceImgId) {
        b.putString(ARG_ACCESSORY_ADDRESS, deviceAddress);
        b.putString(ARG_ACCESSORY_NAME, deviceName);
        b.putInt(ARG_ACCESSORY_ICON_ID, deviceImgId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        if (bundle != null) {
            mDeviceAddress = bundle.getString(ARG_ACCESSORY_ADDRESS);
            mDeviceName = bundle.getString(ARG_ACCESSORY_NAME);
            mDeviceImgId = bundle.getInt(ARG_ACCESSORY_ICON_ID);
        } else {
            mDeviceName = getString(R.string.accessory_options);
            mDeviceImgId = R.drawable.ic_qs_bluetooth_not_connected;
        }


        mUnpairing = savedInstanceState != null
                && savedInstanceState.getBoolean(SAVE_STATE_UNPAIRING);

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            final Set<BluetoothDevice> bondedDevices = btAdapter.getBondedDevices();
            if (bondedDevices != null) {
                for (BluetoothDevice device : bondedDevices) {
                    if (mDeviceAddress.equals(device.getAddress())) {
                        mDevice = device;
                        break;
                    }
                }
            }
        }

        if (mDevice == null) {
            navigateBack();
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mDevice != null &&
                (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE ||
                        mDevice.getType() == BluetoothDevice.DEVICE_TYPE_DUAL)) {
            // Only LE devices support GATT
            mDeviceGatt = mDevice.connectGatt(getActivity(), true, new GattBatteryCallbacks());
        }
        // Set a broadcast receiver to let us know when the device has been removed
        final IntentFilter adapterIntentFilter = new IntentFilter();
        adapterIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mBroadcastReceiver = new UnpairReceiver(this, mDevice);
        getActivity().registerReceiver(mBroadcastReceiver, adapterIntentFilter);
        if (mDevice != null && mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            navigateBack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mBailoutRunnable);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(SAVE_STATE_UNPAIRING, mUnpairing);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDeviceGatt != null) {
            mDeviceGatt.close();
        }
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.bluetooth_accessory, null);
        final PreferenceScreen screen = getPreferenceScreen();
        screen.setTitle(mDeviceName);

        mChangeNamePref = findPreference(KEY_CHANGE_NAME);
        ChangeNameFragment.prepareArgs(mChangeNamePref.getExtras(), mDeviceName, mDeviceImgId);

        mUnpairPref = findPreference(KEY_UNPAIR);
        updatePrefsForUnpairing();
        UnpairConfirmFragment.prepareArgs(
                mUnpairPref.getExtras(), mDevice, mDeviceName, mDeviceImgId);

        mBatteryPref = findPreference(KEY_BATTERY);
        mBatteryPref.setVisible(false);
    }

    public void setUnpairing(boolean unpairing) {
        mUnpairing = unpairing;
        updatePrefsForUnpairing();
    }

    private void updatePrefsForUnpairing() {
        if (mUnpairing) {
            mUnpairPref.setTitle(R.string.accessory_unpairing);
            mUnpairPref.setEnabled(false);
            mChangeNamePref.setEnabled(false);
        } else {
            mUnpairPref.setTitle(R.string.accessory_unpair);
            mUnpairPref.setEnabled(true);
            mChangeNamePref.setEnabled(true);
        }
    }

    private void navigateBack() {
        // need to post this to avoid recursing in the fragment manager.
        mHandler.removeCallbacks(mBailoutRunnable);
        mHandler.post(mBailoutRunnable);
    }

    private void renameDevice(String deviceName) {
        mDeviceName = deviceName;
        if (mDevice != null) {
            mDevice.setAlias(deviceName);
            getPreferenceScreen().setTitle(deviceName);
            setTitle(deviceName);
            ChangeNameFragment.prepareArgs(mChangeNamePref.getExtras(), mDeviceName, mDeviceImgId);
            UnpairConfirmFragment.prepareArgs(
                    mUnpairPref.getExtras(), mDevice, mDeviceName, mDeviceImgId);
        }
    }

    private class GattBatteryCallbacks extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) {
                Log.d(TAG, "Connection status:" + status + " state:" + newState);
            }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Service discovery failure on " + gatt);
                }
                return;
            }

            final BluetoothGattService battService = gatt.getService(GATT_BATTERY_SERVICE_UUID);
            if (battService == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery service");
                }
                return;
            }

            final BluetoothGattCharacteristic battLevel =
                    battService.getCharacteristic(GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID);
            if (battLevel == null) {
                if (DEBUG) {
                    Log.d(TAG, "No battery level");
                }
                return;
            }

            gatt.readCharacteristic(battLevel);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (DEBUG) {
                    Log.e(TAG, "Read characteristic failure on " + gatt + " " + characteristic);
                }
                return;
            }

            if (GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final int batteryLevel =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mBatteryPref != null && !mUnpairing) {
                            mBatteryPref.setTitle(getString(R.string.accessory_battery,
                                    batteryLevel));
                            mBatteryPref.setVisible(true);
                        }
                    }
                });
            }
        }
    }

    public static class ChangeNameFragment extends GuidedStepFragment {

        public static void prepareArgs(@NonNull Bundle args, String deviceName,
                @DrawableRes int deviceImgId) {
            args.putString(ARG_ACCESSORY_NAME, deviceName);
            args.putInt(ARG_ACCESSORY_ICON_ID, deviceImgId);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.accessory_change_name_title),
                    null,
                    getArguments().getString(ARG_ACCESSORY_NAME),
                    getContext().getDrawable(getArguments().getInt(ARG_ACCESSORY_ICON_ID,
                            R.drawable.ic_qs_bluetooth_not_connected))
            );
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            final Context context = getContext();
            actions.add(new GuidedAction.Builder(context)
                    .title(getArguments().getString(ARG_ACCESSORY_NAME))
                    .editable(true)
                    .build());
        }

        @Override
        public long onGuidedActionEditedAndProceed(GuidedAction action) {
            if (!TextUtils.equals(action.getTitle(),
                    getArguments().getString(ARG_ACCESSORY_NAME))
                    && TextUtils.isGraphic(action.getTitle())) {
                final BluetoothAccessoryFragment fragment =
                        (BluetoothAccessoryFragment) getTargetFragment();
                fragment.renameDevice(action.getTitle().toString());
                getFragmentManager().popBackStack();
            }
            return GuidedAction.ACTION_ID_NEXT;
        }
    }

    private static class UnpairReceiver extends BroadcastReceiver {

        private final Fragment mFragment;
        private final BluetoothDevice mDevice;

        public UnpairReceiver(Fragment fragment, BluetoothDevice device) {
            mFragment = fragment;
            mDevice = device;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final BluetoothDevice device = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE);
            if (bondState == BluetoothDevice.BOND_NONE && Objects.equals(mDevice, device)) {
                // Device was removed, bail out of the fragment
                if (mFragment instanceof BluetoothAccessoryFragment) {
                    ((BluetoothAccessoryFragment) mFragment).navigateBack();
                } else if (mFragment instanceof UnpairConfirmFragment) {
                    ((UnpairConfirmFragment) mFragment).navigateBack();
                } else {
                    throw new IllegalStateException(
                            "UnpairReceiver attached to wrong fragment class");
                }
            }
        }
    }

    public static class UnpairConfirmFragment extends GuidedStepFragment {

        private BluetoothDevice mDevice;
        private BroadcastReceiver mBroadcastReceiver;
        private final Handler mHandler = new Handler();

        private Runnable mBailoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (isResumed() && !getFragmentManager().popBackStackImmediate()) {
                    getActivity().onBackPressed();
                }
            }
        };

        private final Runnable mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                navigateBack();
            }
        };

        public static void prepareArgs(@NonNull Bundle args, BluetoothDevice device,
                String deviceName, @DrawableRes int deviceImgId) {
            args.putParcelable(ARG_DEVICE, device);
            args.putString(ARG_ACCESSORY_NAME, deviceName);
            args.putInt(ARG_ACCESSORY_ICON_ID, deviceImgId);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mDevice = getArguments().getParcelable(ARG_DEVICE);
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                navigateBack();
            }
            final IntentFilter adapterIntentFilter = new IntentFilter();
            adapterIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            mBroadcastReceiver = new UnpairReceiver(this, mDevice);
            getActivity().registerReceiver(mBroadcastReceiver, adapterIntentFilter);
        }

        @Override
        public void onStop() {
            super.onStop();
            getActivity().unregisterReceiver(mBroadcastReceiver);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mHandler.removeCallbacks(mTimeoutRunnable);
            mHandler.removeCallbacks(mBailoutRunnable);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.accessory_unpair),
                    null,
                    getArguments().getString(ARG_ACCESSORY_NAME),
                    getContext().getDrawable(getArguments().getInt(ARG_ACCESSORY_ICON_ID,
                            R.drawable.ic_qs_bluetooth_not_connected))
            );
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            final Context context = getContext();
            actions.add(new GuidedAction.Builder(context)
                    .clickAction(GuidedAction.ACTION_ID_OK).build());
            actions.add(new GuidedAction.Builder(context)
                    .clickAction(GuidedAction.ACTION_ID_CANCEL).build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                unpairDevice();
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                getFragmentManager().popBackStack();
            } else {
                super.onGuidedActionClicked(action);
            }
        }

        private void navigateBack() {
            // need to post this to avoid recursing in the fragment manager.
            mHandler.removeCallbacks(mBailoutRunnable);
            mHandler.post(mBailoutRunnable);
        }

        private void unpairDevice() {
            if (mDevice != null) {
                int state = mDevice.getBondState();

                if (state == BluetoothDevice.BOND_BONDING) {
                    mDevice.cancelBondProcess();
                }

                if (state != BluetoothDevice.BOND_NONE) {
                    ((BluetoothAccessoryFragment) getTargetFragment()).setUnpairing(true);
                    // Set a timeout, just in case we don't receive the unpair notification we
                    // use to finish the activity
                    mHandler.postDelayed(mTimeoutRunnable, UNPAIR_TIMEOUT);
                    final boolean successful = mDevice.removeBond();
                    if (successful) {
                        if (DEBUG) {
                            Log.d(TAG, "Bluetooth device successfully unpaired.");
                        }
                    } else {
                        Log.e(TAG, "Failed to unpair Bluetooth Device: " + mDevice.getName());
                    }
                }
            } else {
                Log.e(TAG, "Bluetooth device not found. Address = " + mDevice.getAddress());
            }
        }
    }
}
