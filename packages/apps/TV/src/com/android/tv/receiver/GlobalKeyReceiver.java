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
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import com.android.tv.TvApplication;

/**
 * Handles global keys.
 */
public class GlobalKeyReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "GlobalKeyReceiver";

    private static final String ACTION_GLOBAL_BUTTON = "android.intent.action.GLOBAL_BUTTON";
    // Settings.Secure.USER_SETUP_COMPLETE is hidden.
    private static final String SETTINGS_USER_SETUP_COMPLETE = "user_setup_complete";

    private static long sLastEventTime;
    private static boolean sUserSetupComplete;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TvApplication.getSingletons(context).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager");
            return;
        }
        TvApplication.setCurrentRunningProcess(context, true);
        Context appContext = context.getApplicationContext();
        if (DEBUG) Log.d(TAG, "onReceive: " + intent);
        if (sUserSetupComplete) {
            handleIntent(appContext, intent);
        } else {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    return Settings.Secure.getInt(appContext.getContentResolver(),
                            SETTINGS_USER_SETUP_COMPLETE, 0) != 0;
                }

                @Override
                protected void onPostExecute(Boolean setupComplete) {
                    if (DEBUG) Log.d(TAG, "Is setup complete: " + setupComplete);
                    sUserSetupComplete = setupComplete;
                    if (sUserSetupComplete) {
                        handleIntent(appContext, intent);
                    }
                }
            }.execute();
        }
    }

    private void handleIntent(Context appContext, Intent intent) {
        if (ACTION_GLOBAL_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (DEBUG) Log.d(TAG, "handleIntent: " + event);
            int keyCode = event.getKeyCode();
            int action = event.getAction();
            long eventTime = event.getEventTime();
            if (action == KeyEvent.ACTION_UP && sLastEventTime != eventTime) {
                // Workaround for b/23947504, the same key event may be sent twice, filter it.
                sLastEventTime = eventTime;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_GUIDE:
                        ((TvApplication) appContext).handleGuideKey();
                        break;
                    case KeyEvent.KEYCODE_TV:
                        ((TvApplication) appContext).handleTvKey();
                        break;
                    case KeyEvent.KEYCODE_TV_INPUT:
                        ((TvApplication) appContext).handleTvInputKey();
                        break;
                    default:
                        // Do nothing
                        break;
                }
            }
        }
    }
}
