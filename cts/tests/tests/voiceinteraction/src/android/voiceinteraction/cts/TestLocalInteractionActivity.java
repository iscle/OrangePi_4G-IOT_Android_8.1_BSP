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

package android.voiceinteraction.cts;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.util.concurrent.CountDownLatch;

public class TestLocalInteractionActivity extends Activity {
    static final String TAG = "TestLocalInteractionActivity";

    private CountDownLatch mStarted;
    private CountDownLatch mStopped;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, " in onCreate");
    }

    void startLocalInteraction(CountDownLatch counter) {
        Bundle privateOptions = new Bundle();
        privateOptions.putString(Utils.PRIVATE_OPTIONS_KEY, Utils.PRIVATE_OPTIONS_VALUE);
        mStarted = counter;
        startLocalVoiceInteraction(privateOptions);
    }

    @Override
    public void onLocalVoiceInteractionStarted() {
        Log.i(TAG, " in onLocalVoiceInteractionStarted");
        if (mStarted != null) {
            mStarted.countDown();
        }
    }

    void stopLocalInteraction(CountDownLatch counter) {
        mStopped = counter;
        stopLocalVoiceInteraction();
    }

    @Override
    public void onLocalVoiceInteractionStopped() {
        Log.i(TAG, " in onLocalVoiceInteractionStarted");
        if (mStopped != null) {
            mStopped.countDown();
        }
    }
}
