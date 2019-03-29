/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.managedprofile;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Dummy activity necessary for the tests.
 */
public class TestActivity extends Activity {
    private static final String TAG = TestActivity.class.getName();

    private final Semaphore mActivityStartedSemaphore = new Semaphore(0);

    public void startActivity(String action) {
        startActivityForResult(new Intent(action), /* requestCode= */ 0);
    }

    public boolean checkActivityStarted() {
        Log.i(TAG, "Waiting to see if an activity was started");
        try {
            return mActivityStartedSemaphore.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
           return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "An activity sent a result");
        if (resultCode != RESULT_OK) {
            Log.w(TAG, "Activity returned error code: " + resultCode);
        }
        mActivityStartedSemaphore.release();
    }
}
