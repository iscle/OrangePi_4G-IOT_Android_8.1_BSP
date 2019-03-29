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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.cts.net.hostside.INetworkStateObserver;

public final class Common {

    static final String TAG = "CtsNetApp2";

    // Constants below must match values defined on app's
    // AbstractRestrictBackgroundNetworkTestCase.java
    static final String MANIFEST_RECEIVER = "ManifestReceiver";
    static final String DYNAMIC_RECEIVER = "DynamicReceiver";

    static final String ACTION_RECEIVER_READY =
            "com.android.cts.net.hostside.app2.action.RECEIVER_READY";
    static final String ACTION_FINISH_ACTIVITY =
            "com.android.cts.net.hostside.app2.action.FINISH_ACTIVITY";
    static final String ACTION_SHOW_TOAST =
            "com.android.cts.net.hostside.app2.action.SHOW_TOAST";

    static final String NOTIFICATION_TYPE_CONTENT = "CONTENT";
    static final String NOTIFICATION_TYPE_DELETE = "DELETE";
    static final String NOTIFICATION_TYPE_FULL_SCREEN = "FULL_SCREEN";
    static final String NOTIFICATION_TYPE_BUNDLE = "BUNDLE";
    static final String NOTIFICATION_TYPE_ACTION = "ACTION";
    static final String NOTIFICATION_TYPE_ACTION_BUNDLE = "ACTION_BUNDLE";
    static final String NOTIFICATION_TYPE_ACTION_REMOTE_INPUT = "ACTION_REMOTE_INPUT";

    static final String TEST_PKG = "com.android.cts.net.hostside";
    static final String KEY_NETWORK_STATE_OBSERVER = TEST_PKG + ".observer";

    static int getUid(Context context) {
        final String packageName = context.getPackageName();
        try {
            return context.getPackageManager().getPackageUid(packageName, 0);
        } catch (NameNotFoundException e) {
            throw new IllegalStateException("Could not get UID for " + packageName, e);
        }
    }

    static void notifyNetworkStateObserver(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        final INetworkStateObserver observer = INetworkStateObserver.Stub.asInterface(
                extras.getBinder(KEY_NETWORK_STATE_OBSERVER));
        if (observer != null) {
            try {
                if (!observer.isForeground()) {
                    Log.e(TAG, "App didn't come to foreground");
                    observer.onNetworkStateChecked(null);
                    return;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error occurred while reading the proc state: " + e);
            }
            AsyncTask.execute(() -> {
                try {
                    observer.onNetworkStateChecked(
                            MyBroadcastReceiver.checkNetworkStatus(context));
                } catch (RemoteException e) {
                    Log.e(TAG, "Error occurred while notifying the observer: " + e);
                }
            });
        }
    }
}
