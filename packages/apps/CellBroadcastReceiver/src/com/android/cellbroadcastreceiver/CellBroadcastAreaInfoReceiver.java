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

package com.android.cellbroadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.telephony.CellBroadcastMessage;
import android.util.Log;

public class CellBroadcastAreaInfoReceiver extends BroadcastReceiver {
    private static final String TAG = "CBAreaInfoReceiver";
    static final boolean DBG = false;    // STOPSHIP: change to false before ship
    private static final String GET_LATEST_CB_AREA_INFO_ACTION =
            "com.android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DBG) Log.d(TAG, "onReceive " + intent);

        String action = intent.getAction();

        if (GET_LATEST_CB_AREA_INFO_ACTION.equals(action)) {
            CellBroadcastMessage message = CellBroadcastReceiverApp.getLatestAreaInfo();
            if (message != null) {
                Intent areaInfoIntent = new Intent(
                        CellBroadcastAlertService.CB_AREA_INFO_RECEIVED_ACTION);
                areaInfoIntent.setPackage(CellBroadcastAlertService.SETTINGS_APP);
                areaInfoIntent.putExtra("message", message);
                // Send broadcast twice, once for apps that have PRIVILEGED permission and once
                // for those that have the runtime one
                context.sendBroadcastAsUser(areaInfoIntent, UserHandle.ALL,
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
                context.sendBroadcastAsUser(areaInfoIntent, UserHandle.ALL,
                        android.Manifest.permission.READ_PHONE_STATE);
            }
        }
    }
}
