/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.bluetooth;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.lang.Math;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class BleScannerPowerLevelActivity extends PassFailButtons.Activity {

    private static final String TAG = "BleScannerPowerLevel";

    private Map<Integer, TextView> mMacText;
    private Map<Integer, TextView> mCountText;
    private Map<Integer, TextView> mRssiText;
    private Map<Integer, TextView> mSetPowerText;
    private Map<Integer, Integer> mCount;
    private int[] mPowerLevel;

    private static final int[] POWER_DBM = {-21, -15, -7, 1, 9};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_scanner_power_level);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_power_level_name,
                         R.string.ble_power_level_info, -1);

        mRssiText = new HashMap<Integer, TextView>();
        mCountText = new HashMap<Integer, TextView>();
        mCount = null;
        mMacText = new HashMap<Integer, TextView>();
        mSetPowerText = new HashMap<Integer, TextView>();
        mPowerLevel = new int[]{AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            AdvertiseSettings.ADVERTISE_TX_POWER_HIGH};

        mMacText.put(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            (TextView)findViewById(R.id.ble_ultra_low_mac));
        mMacText.put(AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            (TextView)findViewById(R.id.ble_low_mac));
        mMacText.put(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            (TextView)findViewById(R.id.ble_medium_mac));
        mMacText.put(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            (TextView)findViewById(R.id.ble_high_mac));

        mCountText.put(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            (TextView)findViewById(R.id.ble_ultra_low_count));
        mCountText.put(AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            (TextView)findViewById(R.id.ble_low_count));
        mCountText.put(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            (TextView)findViewById(R.id.ble_medium_count));
        mCountText.put(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            (TextView)findViewById(R.id.ble_high_count));

        mRssiText.put(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            (TextView)findViewById(R.id.ble_ultra_low_rssi));
        mRssiText.put(AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            (TextView)findViewById(R.id.ble_low_rssi));
        mRssiText.put(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            (TextView)findViewById(R.id.ble_medium_rssi));
        mRssiText.put(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            (TextView)findViewById(R.id.ble_high_rssi));

        mSetPowerText.put(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            (TextView)findViewById(R.id.ble_ultra_low_set_power));
        mSetPowerText.put(AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            (TextView)findViewById(R.id.ble_low_set_power));
        mSetPowerText.put(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            (TextView)findViewById(R.id.ble_medium_set_power));
        mSetPowerText.put(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            (TextView)findViewById(R.id.ble_high_set_power));

        Intent intent = new Intent(this, BleScannerService.class);
        intent.putExtra(BleScannerService.EXTRA_COMMAND, BleScannerService.COMMAND_POWER_LEVEL);
        startService(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleScannerService.BLE_POWER_LEVEL);
        filter.addAction(BleScannerService.BLE_PRIVACY_NEW_MAC_RECEIVE);
        registerReceiver(onBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(onBroadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, BleScannerService.class));
    }

    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BleScannerService.BLE_POWER_LEVEL:
                    int powerLevelBit = intent.getIntExtra(
                            BleScannerService.EXTRA_POWER_LEVEL_BIT, -1);
                    int powerLevel = intent.getIntExtra(BleScannerService.EXTRA_POWER_LEVEL, -2);
                    if (powerLevelBit < 0 || powerLevelBit > 3) {
                        Toast.makeText(context, "Invalid power level", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    if (mCount == null) {
                        mCount = new HashMap<Integer, Integer>();
                        for (int i : mPowerLevel) {
                            mCount.put(i, 0);
                        }
                    }
                    Integer t = mCount.get(powerLevelBit) + 1;
                    mCount.put(powerLevelBit, t);
                    mCountText.get(powerLevelBit).setText(t.toString());

                    mMacText.get(powerLevelBit)
                        .setText(intent.getStringExtra(BleScannerService.EXTRA_MAC_ADDRESS));
                    mRssiText.get(powerLevelBit)
                        .setText(intent.getStringExtra(BleScannerService.EXTRA_RSSI));
                    if (Math.abs(POWER_DBM[powerLevelBit] - powerLevel) < 2) {
                        mSetPowerText.get(powerLevelBit).setText("Valid power level");
                    } else {
                        mSetPowerText.get(powerLevelBit)
                            .setText("Unknown BLe advertise tx power: " + powerLevel);
                    }
                    break;
                case BleScannerService.BLE_PRIVACY_NEW_MAC_RECEIVE:
                     Toast.makeText(context, "New MAC address detected", Toast.LENGTH_SHORT)
                            .show();
                     break;
            }
        }
    };
}
