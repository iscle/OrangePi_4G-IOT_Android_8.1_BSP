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
 * limitations under the License
 */

package com.android.phone.testapps.embmsmw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.mbms.FileServiceInfo;
import android.util.Log;

/**
 * Class for triggering artificial events from the frontend app. These would normally not come
 * from the frontend app in a real embms implementation.
 */
public class SideChannelReceiver extends BroadcastReceiver {
    public static final String ACTION_TRIGGER_CLEANUP =
            "com.android.phone.testapps.embmsmw.TRIGGER_CLEANUP";
    public static final String ACTION_REQUEST_SPURIOUS_TEMP_FILES =
            "com.android.phone.testapps.embmsmw.REQUEST_SPURIOUS_TEMP_FILES";
    public static final String ACTION_DELAY_DOWNLOAD =
            "com.android.phone.testapps.embmsmw.DELAY_DOWNLOAD";

    public static final String EXTRA_SERVICE_INFO =
            "com.android.phone.testapps.embmsmw.SERVICE_INFO";
    public static final String EXTRA_DELAY_FACTOR =
            "com.android.phone.testapps.embmsmw.DELAY_FACTOR";

    private static final String LOG_TAG = "EmbmsSampleMwSC";
    @Override
    public void onReceive(Context context, Intent intent) {
        EmbmsSampleDownloadService downloadService = EmbmsSampleDownloadService.getInstance();
        if (downloadService == null) {
            Log.w(LOG_TAG, "don't have instance of dl service");
            return;
        }
        switch (intent.getAction()) {
            case ACTION_TRIGGER_CLEANUP:
                downloadService.requestCleanup();
                break;
            case ACTION_REQUEST_SPURIOUS_TEMP_FILES:
                FileServiceInfo serviceInfo = intent.getParcelableExtra(EXTRA_SERVICE_INFO);
                downloadService.requestExtraTempFiles(serviceInfo);
                break;
            case ACTION_DELAY_DOWNLOAD:
                // Increase download latency by a certain factor
                downloadService.delayDownloads(intent.getIntExtra(EXTRA_DELAY_FACTOR, 1));
                break;
        }
    }
}
