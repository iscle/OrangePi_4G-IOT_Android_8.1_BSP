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

package com.android.cts.intent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BroadcastIntentReceiver extends BroadcastReceiver {

    static final String OWNER_CHANGED_BROADCAST_RECEIVED_KEY
         = "owner-changed-broadcast-received";

    static final String PREFERENCES_NAME = "BroadcastIntentReceiver";

    @Override
    public void onReceive(Context c, Intent i) {
        SharedPreferences prefs = c.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(OWNER_CHANGED_BROADCAST_RECEIVED_KEY, true);
        editor.apply();
    }
}
