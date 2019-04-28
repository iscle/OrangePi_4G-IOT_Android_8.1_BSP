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

package com.android.phone.testapps.embmsdownload;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.telephony.mbms.FileServiceInfo;

public class SideChannel {
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

    public static final ComponentName MIDDLEWARE_RECEIVER = new ComponentName(
            "com.android.phone.testapps.embmsmw",
            "com.android.phone.testapps.embmsmw.SideChannelReceiver");

    public static void triggerCleanup(Context context) {
        Intent intent  = new Intent(ACTION_TRIGGER_CLEANUP);
        intent.setComponent(MIDDLEWARE_RECEIVER);
        context.sendBroadcast(intent);
    }

    public static void requestSpuriousTempFiles(Context context, FileServiceInfo serviceInfo) {
        Intent intent = new Intent(ACTION_REQUEST_SPURIOUS_TEMP_FILES);
        intent.putExtra(EXTRA_SERVICE_INFO, serviceInfo);
        intent.setComponent(MIDDLEWARE_RECEIVER);
        context.sendBroadcast(intent);
    }

    public static void delayDownloads(Context context, int delay) {
        Intent intent = new Intent(ACTION_DELAY_DOWNLOAD);
        intent.putExtra(EXTRA_DELAY_FACTOR, delay);
        intent.setComponent(MIDDLEWARE_RECEIVER);
        context.sendBroadcast(intent);
    }
}
