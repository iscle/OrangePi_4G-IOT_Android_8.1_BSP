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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.test.InstrumentationTestCase;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;

public class OwnerChangedBroadcastTest extends InstrumentationTestCase {

    private SharedPreferences mPreferences;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        mPreferences = context.getSharedPreferences(
                BroadcastIntentReceiver.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    // We can't just register a broadcast receiver in the code because the broadcast
    // may have been sent before this test is run. So we have a manifest receiver
    // listening to the broadcast and writing to a shared preference when it receives it.
    public void testOwnerChangedBroadcastReceived() throws InterruptedException {
        final Semaphore mPreferenceChanged = new Semaphore(0);

        OnSharedPreferenceChangeListener listener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(BroadcastIntentReceiver.OWNER_CHANGED_BROADCAST_RECEIVED_KEY)) {
                    mPreferenceChanged.release();
                }
            }
        };
        mPreferences.registerOnSharedPreferenceChangeListener(listener);

        if (mPreferences.getBoolean(BroadcastIntentReceiver.OWNER_CHANGED_BROADCAST_RECEIVED_KEY,
                false)) {
            // The broadcast intent has already been received? good
            // Otherwise, we'll wait until we receive it.
            return;
        }
        // We're relying on background broadcast intents, which can take a long time.
        assertTrue(mPreferenceChanged.tryAcquire(2, TimeUnit.MINUTES));
        assertTrue(mPreferences.getBoolean(
                BroadcastIntentReceiver.OWNER_CHANGED_BROADCAST_RECEIVED_KEY, false));
    }

    public void testOwnerChangedBroadcastNotReceived() {
        assertFalse(mPreferences.getBoolean(
                BroadcastIntentReceiver.OWNER_CHANGED_BROADCAST_RECEIVED_KEY, false));
    }
}
