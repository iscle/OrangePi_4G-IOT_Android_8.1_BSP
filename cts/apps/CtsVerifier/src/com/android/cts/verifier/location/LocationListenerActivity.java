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
 * limitations under the License
 */

package com.android.cts.verifier.location;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.R;

public class LocationListenerActivity extends Activity implements Handler.Callback {
    // Primary -> managed intent: request to goto the location settings page and listen to updates.
    public static final String ACTION_SET_LOCATION_AND_CHECK_UPDATES =
            "com.android.cts.verifier.location.SET_LOCATION_AND_CHECK";
    private static final int REQUEST_LOCATION_UPDATE = 1;

    private static final int MSG_TIMEOUT_ID = 1;

    private static final long MSG_TIMEOUT_MILLISEC = 15000; // 15 seconds.

    private LocationManager mLocationManager;
    private Handler mHandler;
    private boolean mIsLocationUpdated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mHandler = new Handler(this);
        mIsLocationUpdated = false;
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SET_LOCATION_AND_CHECK_UPDATES.equals(action)) {
                Log.d(getLogTag(), "ACTION_SET_LOCATION_AND_CHECK_UPDATES received in uid "
                        + Process.myUid());
                handleLocationAction();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_LOCATION_UPDATE: {
                Log.d(getLogTag(), "Exit location settings:OK");
                mLocationManager.removeUpdates(mLocationListener);
                mHandler.removeMessages(MSG_TIMEOUT_ID);
                finish();
                break;
            }
            default: {
                Log.wtf(getLogTag(), "Unknown requestCode " + requestCode + "; data = " + data);
                break;
            }
        }
    }

    protected void handleLocationAction() {
        Intent locationSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        if (locationSettingsIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(locationSettingsIntent, REQUEST_LOCATION_UPDATE);
            scheduleTimeout();
        } else {
            Log.e(getLogTag(), "Settings.ACTION_LOCATION_SOURCE_SETTINGS could not be resolved");
            finish();
        }
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    }

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            synchronized (LocationListenerActivity.this) {
                if (mIsLocationUpdated) return;
                showToast(R.string.provisioning_byod_location_mode_enable_toast_location_change);
                mIsLocationUpdated = true;
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private void scheduleTimeout() {
        mHandler.removeMessages(MSG_TIMEOUT_ID);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TIMEOUT_ID), MSG_TIMEOUT_MILLISEC);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_TIMEOUT_ID) {
            synchronized (this) {
                if (mIsLocationUpdated) return true;
                showToast(R.string.provisioning_byod_location_mode_time_out_toast);
            }
        }
        return true;
    }

    protected String getLogTag() {
        return "LocationListenerActivity";
    }

    protected void showToast(int messageId) {
        String message = getString(messageId);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
