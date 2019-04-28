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

package com.google.android.car.obd2app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import java.util.Timer;

public class MainActivity extends Activity implements StatusNotification {
    public static final String TAG = MainActivity.class.getSimpleName();

    private static final String BLUETOOTH_MAC_PREFERENCE_ID = "bluetooth_mac";
    private static final String SCAN_DELAY_PREFERENCE_ID = "scan_delay";

    private Obd2CollectionTask mCollectionTask = null;
    private final Timer mTimer = new Timer("com.google.android.car.obd2app.collection");

    private String getBluetoothDongleMacFromPreferences(String defaultValue) {
        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return appPreferences.getString(BLUETOOTH_MAC_PREFERENCE_ID, defaultValue);
    }

    private int getScanDelayFromPreferences(int defaultValue) {
        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return appPreferences.getInt(SCAN_DELAY_PREFERENCE_ID, defaultValue);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        String bluetoothDongleMac = getBluetoothDongleMacFromPreferences("");
        if (TextUtils.isEmpty(bluetoothDongleMac)) {
            notifyNoDongle();
        } else {
            notifyPaired(bluetoothDongleMac);
        }
        findViewById(R.id.connection)
                .setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                handleConnection(v);
                            }
                        });
        Log.i(TAG, "I did all the things");
    }

    private void stopConnection() {
        mCollectionTask.cancel();
        mTimer.purge();
        mCollectionTask = null;
    }

    @Override
    protected void onDestroy() {
        stopConnection();
    }

    public void doSettings(View view) {
        Intent launchSettings = new Intent(this, SettingsActivity.class);
        startActivity(launchSettings);
    }

    @Override
    public void notify(String status) {
        Log.i(TAG, status);
        runOnUiThread(() -> ((TextView) findViewById(R.id.statusBar)).setText(status));
    }

    public void handleConnection(View view) {
        String deviceAddress = getBluetoothDongleMacFromPreferences("");
        Log.i(TAG, "Considering a connection to " + deviceAddress);
        if (TextUtils.isEmpty(deviceAddress)) {
            notifyNoDongle();
        }
        if (mCollectionTask == null) {
            mCollectionTask = Obd2CollectionTask.create(this, this, deviceAddress);
            if (null == mCollectionTask) {
                notifyConnectionFailed();
                return;
            }
            final int delay = 1000 * getScanDelayFromPreferences(2);
            mTimer.scheduleAtFixedRate(mCollectionTask, delay, delay);
            ((Button) view).setText("Disconnect");
        } else {
            stopConnection();
            ((Button) view).setText("Connect");
        }
    }
}
