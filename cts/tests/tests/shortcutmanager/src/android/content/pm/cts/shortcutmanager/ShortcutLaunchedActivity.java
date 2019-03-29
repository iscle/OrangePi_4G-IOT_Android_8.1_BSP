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
package android.content.pm.cts.shortcutmanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Activity that's started from shortcuts during CTS.  This one closes itself when it's shown,
 * and record all the incoming intents, which we'll examine during the tests.
 */
public class ShortcutLaunchedActivity extends Activity {
    private static final String TAG = "ShortcutLA";

    private static final AtomicInteger sNextInstanceId = new AtomicInteger();

    private final int mInstanceId = sNextInstanceId.getAndIncrement();

    private static final Object sLock = new Object();

    // @GuardedBy("sLock")
    private static final ArrayList<Intent> sReceivedIntents = new ArrayList<>();

    // @GuardedBy("sLock")
    private static final ArrayList<String> sExpectedVisibleOrder = new ArrayList<>();

    private Handler mHandler = new Handler();

    private Intent mIntentToAdd;

    private void log(String action) {
        Log.i(TAG, String.format("Activity #%d %s: intent=%s",
                mInstanceId, action, getIntent()));
    }

    public ShortcutLaunchedActivity() {
        log("ctor");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIntentToAdd = getIntent();

        log("onCreate");
    }

    @Override
    protected void onResume() {
        super.onResume();

        log("onResume");

        synchronized (sLock) {
            if (!Objects.equals(getIntent().getAction(), sExpectedVisibleOrder.get(0))) {
                log("Not my turn yet.");
                return;
            }
            sExpectedVisibleOrder.remove(0);

            // Make sure we only add it once, ever.
            if (mIntentToAdd != null) {
                sReceivedIntents.add(new Intent(getIntent()));
                mIntentToAdd = null;
            }
        }
        finish();
    }

    @Override
    protected void onPause() {
        log("onPause");

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");

        super.onDestroy();
    }

    public static void setExpectedOrder(String[] actions) {
        synchronized (sLock) {
            sReceivedIntents.clear();

            sExpectedVisibleOrder.clear();
            sExpectedVisibleOrder.addAll(Arrays.asList(actions));
        }
    }

    public static List<Intent> getIntents() {
        synchronized (sLock) {
            return new ArrayList(sReceivedIntents);
        }
    }
}
