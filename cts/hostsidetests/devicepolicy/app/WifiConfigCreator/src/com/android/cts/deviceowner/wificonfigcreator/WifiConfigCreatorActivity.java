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
 * limitations under the License.
 */

package com.android.cts.deviceowner.wificonfigcreator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.compatibility.common.util.WifiConfigCreator;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_CREATE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_NETID;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_PASSWORD;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SECURITY_TYPE;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SSID;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_REMOVE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_UPDATE_WIFI_CONFIG;

/**
 * A simple activity to create and manage wifi configurations.
 */
public class WifiConfigCreatorActivity extends Activity {
    private static final String TAG = "WifiConfigCreatorActivity";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(TAG, "Created for user " + android.os.Process.myUserHandle());
        WifiConfigCreator configCreator = new WifiConfigCreator(this);
        try {
            Intent intent = getIntent();
            String action = intent.getAction();
            if (ACTION_CREATE_WIFI_CONFIG.equals(action)) {
                String ssid = intent.getStringExtra(EXTRA_SSID);
                int securityType = intent.getIntExtra(EXTRA_SECURITY_TYPE, SECURITY_TYPE_NONE);
                String password = intent.getStringExtra(EXTRA_PASSWORD);
                configCreator.addNetwork(ssid, false, securityType, password);
            } else if (ACTION_UPDATE_WIFI_CONFIG.equals(action)) {
                int netId = intent.getIntExtra(EXTRA_NETID, -1);
                String ssid = intent.getStringExtra(EXTRA_SSID);
                int securityType = intent.getIntExtra(EXTRA_SECURITY_TYPE, SECURITY_TYPE_NONE);
                String password = intent.getStringExtra(EXTRA_PASSWORD);
                configCreator.updateNetwork(netId, ssid, false, securityType, password);
            } else if (ACTION_REMOVE_WIFI_CONFIG.equals(action)) {
                int netId = intent.getIntExtra(EXTRA_NETID, -1);
                if (netId != -1) {
                    configCreator.removeNetwork(netId);
                }
            } else {
                Log.i(TAG, "Unknown command: " + action);
            }
        } catch (InterruptedException ie) {
            Log.e(TAG, "Interrupted while changing wifi settings", ie);
        } finally {
            finish();
        }
    }
}
