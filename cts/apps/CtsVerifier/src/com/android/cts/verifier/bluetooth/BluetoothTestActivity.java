/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.widget.Toast;

import com.android.cts.verifier.ManifestTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BluetoothTestActivity extends PassFailButtons.TestListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.bluetooth_test, R.string.bluetooth_test_info, -1);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth not supported", Toast.LENGTH_SHORT);
            return;
        }

        List<String> disabledTestArray = new ArrayList<String>();
        for (String s : this.getResources().getStringArray(R.array.disabled_tests)) {
            disabledTestArray.add(s);
        }
        if (!this.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleAdvertiserTestActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleScannerTestActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleClientTestActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleServerTestBaseActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleConnectionPriorityServerBaseActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleConnectionPriorityClientBaseActivity");
        } else if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleAdvertiserTestActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleServerTestBaseActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleScannerTestActivity");
            disabledTestArray.add(
                  "com.android.cts.verifier.bluetooth.BleConnectionPriorityServerBaseActivity");
        }
        setTestListAdapter(new ManifestTestListAdapter(this, getClass().getName(),
                disabledTestArray.toArray(new String[disabledTestArray.size()])));
    }
}
