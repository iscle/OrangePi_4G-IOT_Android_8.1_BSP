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

package com.android.cts.deviceandprofileowner;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class used to call the activity in the non-test APK and wait for its result.
 */
public class AutofillActivity extends Activity {

    private static final String AUTOFILL_PACKAGE_NAME = "com.android.cts.devicepolicy.autofillapp";
    private static final String AUTOFILL_ACTIVITY_NAME = AUTOFILL_PACKAGE_NAME + ".SimpleActivity";

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private boolean mEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent launchIntent = new Intent();
        launchIntent.setComponent(new ComponentName(AUTOFILL_PACKAGE_NAME, AUTOFILL_ACTIVITY_NAME));
        startActivityForResult(launchIntent, 42);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mEnabled = resultCode == 1;
        mLatch.countDown();
    }

    public boolean isAutofillEnabled() throws InterruptedException {
        final boolean called = mLatch.await(2, TimeUnit.SECONDS);
        if (!called) {
            throw new IllegalStateException(AUTOFILL_ACTIVITY_NAME + " didn't finish in 2 seconds");
        }
        finish();
        return mEnabled;
    }
}
