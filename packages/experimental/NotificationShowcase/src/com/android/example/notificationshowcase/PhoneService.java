/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.example.notificationshowcase;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class PhoneService extends IntentService {

    private static final String TAG = "PhoneService";

    public static final String ACTION_ANSWER = "answer";
    public static final String ACTION_IGNORE = "ignore";

    public static final String EXTRA_ID = "id";

    private Handler handler;

    public PhoneService() {
        super(TAG);
    }
    public PhoneService(String name) {
        super(name);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler = new Handler();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.v(TAG, "clicked a thing! intent=" + intent.toString());
        int res = ACTION_ANSWER.equals(intent.getAction()) ? R.string.answered : R.string.ignored;
        final String text = getString(res);
        final int id = intent.getIntExtra(EXTRA_ID, -1);
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PhoneService.this, text, Toast.LENGTH_LONG).show();
                if (id >= 0) {
                    NotificationManager noMa =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    noMa.cancel(NotificationService.NOTIFICATION_ID + id);
                }
                Log.v(TAG, "phone toast " + text);
            }
        });
    }

    public static PendingIntent getPendingIntent(Context context, int id, String action) {
        Intent phoneIntent = new Intent(context, PhoneService.class);
        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        phoneIntent.setAction(action);
        phoneIntent.putExtra(EXTRA_ID, id);
        PendingIntent pi = PendingIntent.getService(
                context, 58, phoneIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return pi;
    }
}
