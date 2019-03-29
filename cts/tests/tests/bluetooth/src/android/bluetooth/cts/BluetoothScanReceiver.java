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

package android.bluetooth.cts;

import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class BluetoothScanReceiver extends BroadcastReceiver {

    private static final String TAG = "BluetoothScanReceiver";

    private static CountDownLatch sCountDownLatch;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received scan results:" + intent);
        Log.i(TAG, "ScanResults = " + intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT));
        Log.i(TAG, "Callback Type = "
                + intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1));
        Log.i(TAG, "Error Code = "
                + intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1));
        if (sCountDownLatch != null) {
            sCountDownLatch.countDown();
            sCountDownLatch = null;
        }
    }

    public static CountDownLatch createCountDownLatch() {
        sCountDownLatch = new CountDownLatch(1);
        return sCountDownLatch;
    }
}