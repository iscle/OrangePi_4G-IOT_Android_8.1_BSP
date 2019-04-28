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

package com.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.util.Partner;

/**
 * A class for handling the broadcast intents from PackageManager.
 */
public class PackageIntentsReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageIntentsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TvApplication.getSingletons(context).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager");
            return;
        }
        TvApplication.setCurrentRunningProcess(context, true);
        ((TvApplication) context.getApplicationContext()).handleInputCountChanged();

        Uri uri = intent.getData();
        final String packageName = (uri != null ? uri.getSchemeSpecificPart() : null);
        Partner.reset(context, packageName);
    }
}
