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

package android.alarmclock.service;

import android.alarmclock.common.Utils;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public class MainInteractionService extends VoiceInteractionService {
    static final String TAG = "MainInteractionService";
    private Intent mIntent;
    private boolean mReady = false;

    @Override
    public void onReady() {
        super.onReady();
        mReady = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received");
        mIntent = intent;
        Log.i(TAG, "received_testcasetype = " + mIntent.getStringExtra(Utils.TESTCASE_TYPE));
        maybeStart();
        return START_NOT_STICKY;
    }

    private void maybeStart() {
       if (mIntent == null || !mReady) {
            Log.wtf(TAG, "Can't start session because either intent is null or onReady() "
                    + "is not called yet. mIntent = " + mIntent + ", mReady = " + mReady);
        } else {
            Log.i(TAG,
                    "Yay! about to start MainInteractionSession as the voice_interaction_service");
            if (isActiveService(this, new ComponentName(this, getClass()))) {
                Bundle args = new Bundle();
                args.putString(Utils.TESTCASE_TYPE, mIntent.getStringExtra(Utils.TESTCASE_TYPE));
                Log.i(TAG, "xferring_testcasetype = " + args.getString(Utils.TESTCASE_TYPE));
                showSession(args, 0);
            } else {
                Log.wtf(TAG, "**** Not starting MainInteractionService because" +
                    " it is not set as the current voice_interaction_service");
            }
        }
    }
}
