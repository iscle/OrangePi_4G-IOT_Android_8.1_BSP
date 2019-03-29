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
package android.net.cts.appForApi23;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

public class ConnectivityReceiver extends BroadcastReceiver {
    public static String GET_WIFI_CONNECTIVITY_ACTION_COUNT =
            "android.net.cts.appForApi23.getWifiConnectivityActionCount";

    private static int sWifiConnectivityActionCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0);
            if (networkType == ConnectivityManager.TYPE_WIFI) {
                sWifiConnectivityActionCount++;
            }
        }
        if (GET_WIFI_CONNECTIVITY_ACTION_COUNT.equals(intent.getAction())) {
            setResultCode(sWifiConnectivityActionCount);
        }
    }
}
