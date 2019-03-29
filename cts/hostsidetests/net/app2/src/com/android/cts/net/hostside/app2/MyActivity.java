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
package com.android.cts.net.hostside.app2;

import static com.android.cts.net.hostside.app2.Common.ACTION_FINISH_ACTIVITY;
import static com.android.cts.net.hostside.app2.Common.TAG;
import static com.android.cts.net.hostside.app2.Common.TEST_PKG;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.cts.net.hostside.INetworkStateObserver;

/**
 * Activity used to bring process to foreground.
 */
public class MyActivity extends Activity {

    private BroadcastReceiver finishCommandReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MyActivity.onCreate()");
        Common.notifyNetworkStateObserver(this, getIntent());
        finishCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Finishing MyActivity");
                MyActivity.this.finish();
            }
        };
        registerReceiver(finishCommandReceiver, new IntentFilter(ACTION_FINISH_ACTIVITY));
    }

    @Override
    public void finish() {
        if (finishCommandReceiver != null) {
            unregisterReceiver(finishCommandReceiver);
        }
        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MyActivity.onStart()");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MyActivity.onDestroy()");
        super.onDestroy();
    }
}
