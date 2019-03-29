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
package com.android.server.cts.errors;

import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.DropBoxManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Used by ErrorTest. Spawns misbehaving activities so reports will appear in Dropbox.
 */
@RunWith(AndroidJUnit4.class)
public class ErrorsTests {
    private static final String TAG = "ErrorsTests";

    private static final String CRASH_TAG = "data_app_crash";
    private static final String ANR_TAG = "data_app_anr";
    private static final String NATIVE_CRASH_TAG = "SYSTEM_TOMBSTONE";

    private static final int TIMEOUT_SECS = 60 * 3;

    private CountDownLatch mResultsReceivedSignal;
    private DropBoxManager mDropbox;
    private long mStartMs;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDropbox = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        mResultsReceivedSignal = new CountDownLatch(1);
        mStartMs = System.currentTimeMillis();
    }

    @Test
    public void testException() throws Exception {
        Log.i(TAG, "testException");

        registerReceiver(mContext, mResultsReceivedSignal, CRASH_TAG,
                 mContext.getPackageName() + ":TestProcess",
                "java.lang.RuntimeException: This is a test exception");
        Intent intent = new Intent();
        intent.setClass(mContext, ExceptionActivity.class);
        mContext.startActivity(intent);

        assertTrue(mResultsReceivedSignal.await(TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    @Test
    public void testANR() throws Exception {
        Log.i(TAG, "testANR");

        registerReceiver(mContext, mResultsReceivedSignal, ANR_TAG,
                mContext.getPackageName() + ":TestProcess",
                "Subject: Broadcast of Intent { act=android.intent.action.SCREEN_ON");
        Intent intent = new Intent();
        intent.setClass(mContext, ANRActivity.class);
        mContext.startActivity(intent);

        assertTrue(mResultsReceivedSignal.await(TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    @Test
    public void testNativeCrash() throws Exception {
        Log.i(TAG, "testNativeCrash");

        registerReceiver(mContext, mResultsReceivedSignal, NATIVE_CRASH_TAG,
                mContext.getPackageName() + ":TestProcess", "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, NativeActivity.class);
        mContext.startActivity(intent);

        assertTrue(mResultsReceivedSignal.await(TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    void registerReceiver(Context ctx, CountDownLatch onReceiveLatch, String wantTag,
            String... wantInStackTrace) {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // DropBox might receive other entries while we're waiting for the error
                // entry, so we need to check the tag and stack trace before continuing.
                DropBoxManager.Entry entry = mDropbox.getNextEntry(wantTag, mStartMs);
                if (entry != null) {
                    String stackTrace = entry.getText(10000); // Only need to check a few lines.
                    boolean allMatches = true;
                    for (String line : wantInStackTrace) {
                        allMatches &= stackTrace.contains(line);
                    }
                    entry.close();
                    if (allMatches) {
                        onReceiveLatch.countDown();
                    }
                }
            }
        }, new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED));
    }
}
