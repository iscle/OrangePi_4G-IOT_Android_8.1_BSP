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

package com.google.android.car.kitchensink.bluetooth;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.car.kitchensink.R;

import java.util.List;

public class BluetoothHeadsetFragment extends Fragment {
    private static final String TAG = "CAR.BLUETOOTH.KS";
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mPickedDevice;

    TextView mPickedDeviceText;
    Button mDevicePicker;
    Button mConnect;
    Button mDisconnect;
    Button mScoConnect;
    Button mScoDisconnect;
    Button mEnableQuietMode;
    Button mHoldCall;

    BluetoothHeadsetClient mHfpClientProfile;

    // Intent for picking a Bluetooth device
    public static final String DEVICE_PICKER_ACTION =
        "android.bluetooth.devicepicker.action.LAUNCH";

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_headset, container, false);

        mPickedDeviceText = (TextView) v.findViewById(R.id.bluetooth_device);
        mDevicePicker = (Button) v.findViewById(R.id.bluetooth_pick_device);
        mConnect = (Button) v.findViewById(R.id.bluetooth_headset_connect);
        mDisconnect = (Button) v.findViewById(R.id.bluetooth_headset_disconnect);
        mScoConnect = (Button) v.findViewById(R.id.bluetooth_sco_connect);
        mScoDisconnect = (Button) v.findViewById(R.id.bluetooth_sco_disconnect);
        mEnableQuietMode = (Button) v.findViewById(R.id.bluetooth_quiet_mode_enable);
        mHoldCall = (Button) v.findViewById(R.id.bluetooth_hold_call);

        // Pick a bluetooth device
        mDevicePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchDevicePicker();
            }
        });

        // Connect profile
        mConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        // Disonnect profile
        mDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnect();
            }
        });

        // Connect SCO
        mScoConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectSco();
            }
        });

        // Disconnect SCO
        mScoDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnectSco();
            }
        });

        // Enable quiet mode
        mEnableQuietMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothAdapter.enableNoAutoConnect();
            }
        });

        // Place the current call on hold
        mHoldCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                holdCall();
            }
        });
        return v;
    }

    void launchDevicePicker() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        getContext().registerReceiver(mPickerReceiver, filter);

        Intent intent = new Intent(DEVICE_PICKER_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        getContext().startActivity(intent);
    }

    void connect() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to connect sco!");
            return;
        }

        // Check if we have the proxy and connect the device.
        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot connect sco to " + mPickedDevice);
            return;
        }
        mHfpClientProfile.connect(mPickedDevice);
    }

    void disconnect() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to connect sco!");
            return;
        }

        // Check if we have the proxy and connect the device.
        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot connect sco to " + mPickedDevice);
            return;
        }
        mHfpClientProfile.disconnect(mPickedDevice);
    }

    void connectSco() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to connect sco!");
            return;
        }

        // Check if we have the proxy and connect the device.
        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot connect sco to " + mPickedDevice);
            return;
        }
        mHfpClientProfile.connectAudio(mPickedDevice);
    }

    void disconnectSco() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to disconnect sco!");
            return;
        }

        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot disconnect sco to " +
                mPickedDevice);
            return;
        }
        mHfpClientProfile.disconnectAudio(mPickedDevice);
    }

    void holdCall() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to put the call on hold!");
            return;
        }

        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot put the call on hold " +
                mPickedDevice);
            return;
        }
        mHfpClientProfile.holdCall(mPickedDevice);
    }

    private final BroadcastReceiver mPickerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.v(TAG, "mPickerReceiver got " + action);

            if (BluetoothDevicePicker.ACTION_DEVICE_SELECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mPickedDevice = device;
                String text = device.getName() == null ?
                    device.getAddress() : device.getName() + " " + device.getAddress();
                mPickedDeviceText.setText(text);

                // The receiver can now be disabled.
                getContext().unregisterReceiver(mPickerReceiver);
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(
            getContext(), new ProfileServiceListener(), BluetoothProfile.HEADSET_CLIENT);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    class ProfileServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "Proxy connected for profile: " + profile);
            switch (profile) {
                case BluetoothProfile.HEADSET_CLIENT:
                    mHfpClientProfile = (BluetoothHeadsetClient) proxy;
                    break;
                default:
                    Log.w(TAG, "onServiceConnected not supported profile: " + profile);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "Proxy disconnected for profile: " + profile);
            switch (profile) {
                case BluetoothProfile.HEADSET_CLIENT:
                    mHfpClientProfile = null;
                    break;
                default:
                    Log.w(TAG, "onServiceDisconnected not supported profile: " + profile);
            }
        }
    }
}
