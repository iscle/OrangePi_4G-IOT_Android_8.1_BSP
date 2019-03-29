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
package android.appwidget.cts.packages;

import android.app.Activity;
import android.appwidget.cts.common.Constants;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.os.Bundle;

public class AppWidgetConfirmPin extends Activity {

    private PinItemRequest mRequest;

    private BroadcastReceiver mReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            final LauncherApps launcherApps = getSystemService(LauncherApps.class);
            mRequest = launcherApps.getPinItemRequest(getIntent());

            if (mRequest == null) {
                throw new IllegalArgumentException("Null request");
            }

            if (mRequest.getRequestType() != PinItemRequest.REQUEST_TYPE_APPWIDGET ||
                    mRequest.getAppWidgetProviderInfo(this) == null) {
                throw new IllegalArgumentException("Wrong request");
            }

            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onCommandReceive(intent);
                }
            };
            registerReceiver(mReceiver, new IntentFilter(Constants.ACTION_CONFIRM_PIN));
            sendSetupReply(true);
        } catch (Exception e) {
            sendSetupReply(false);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void sendSetupReply(boolean success) {
        sendBroadcast(new Intent(Constants.ACTION_SETUP_REPLY)
                .putExtra(Constants.EXTRA_SUCCESS, success)
                .putExtra(Constants.EXTRA_PACKAGE, getPackageName())
                .putExtra(Constants.EXTRA_REQUEST, mRequest));
    }

    private void onCommandReceive(Intent intent) {
        mRequest.accept(intent.getExtras());
        finish();
    }
}
