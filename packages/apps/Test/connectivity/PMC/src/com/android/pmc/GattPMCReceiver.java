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

package com.android.pmc;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

/**
 * PMC Receiver functions for GATT Client and Server.
 */
public class GattPMCReceiver extends BroadcastReceiver {
    public static final String TAG = "GATTPMC";
    public static final String GATTPMC_INTENT = "com.android.pmc.GATT";
    private final GattClientListener mGattClientListener;
    private final GattServer mGattServer;

    /**
     * Constructor to be called by PMC
     *
     * @param context - PMC will provide a context
     * @param alarmManager - PMC will provide alarmManager
     */
    public GattPMCReceiver(Context context, AlarmManager alarmManager) {
        Log.d(TAG, "Start GattPMCReceiver()");

        // Prepare for setting alarm service
        mGattClientListener = new GattClientListener(context, alarmManager);
        mGattServer = new GattServer(context);

        // RegisterAlarmReceiver for GattListener
        context.registerReceiver(mGattClientListener,
                new IntentFilter(GattClientListener.GATTCLIENT_ALARM));
        Log.d(TAG, "Start GattPMCReceiver()");
    }

    /**
     * Method to receive the broadcast from python client for PMC commands
     *
     * @param context - system will provide a context to this function
     * @param intent - system will provide an intent to this function
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Intent: " + intent.getAction());
        if (intent.getAction().equals(GATTPMC_INTENT)) {
            Bundle extras = intent.getExtras();
            int startTime = 0, writeTime = 0, idleTime = 0, Repetitions = 1;
            String str;

            if (extras == null) {
                Log.e(TAG, "No parameters specified");
                return;
            }

            if (extras.containsKey("GattServer")) {
                // this is for Gatt Server
                Log.d(TAG, "For Gatt Server");
                mGattServer.startGattServer();
                return;
            }

            if (!extras.containsKey("StartTime")) {
                Log.e(TAG, "No Start Time specified");
                return;
            }
            str = extras.getString("StartTime");
            Log.d(TAG, "Start Time = " + str);
            startTime = Integer.valueOf(str);


            if (!extras.containsKey("WriteTime")) {
                Log.e(TAG, "No WriteTime specified for GATT write");
                return;
            }
            str = extras.getString("WriteTime");
            Log.d(TAG, "Write Time = " + str);
            writeTime = Integer.valueOf(str);

            if (!extras.containsKey("IdleTime")) {
                Log.e(TAG, "No IdleTime specified for GATT write");
                return;
            }
            str = extras.getString("IdleTime");
            Log.d(TAG, "Idle Time = " + str);
            idleTime = Integer.valueOf(str);

            if (!extras.containsKey("Repetitions")) {
                Log.e(TAG, "No Repetitions specified for GATT write");
                return;
            }
            str = extras.getString("Repetitions");
            Log.d(TAG, "Repetitions = " + str);
            Repetitions = Integer.valueOf(str);

            mGattClientListener.startAlarm(startTime, writeTime, idleTime, Repetitions, null);
        }
    }
}
