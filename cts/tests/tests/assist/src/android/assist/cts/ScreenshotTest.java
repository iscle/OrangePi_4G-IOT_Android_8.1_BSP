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

package android.assist.cts;

import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScreenshotTest extends AssistTestBase {
    static final String TAG = "ScreenshotTest";

    private static final String TEST_CASE_TYPE = Utils.SCREENSHOT;

    private BroadcastReceiver mScreenshotActivityReceiver;
    private CountDownLatch mHasResumedLatch, mReadyLatch;

    public ScreenshotTest() {
        super();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReadyLatch = new CountDownLatch(1);
        // set up receiver
        mScreenshotActivityReceiver = new ScreenshotTestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        filter.addAction(Utils.APP_3P_HASRESUMED);
        mContext.registerReceiver(mScreenshotActivityReceiver, filter);

        // start test start activity
        startTestActivity(TEST_CASE_TYPE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mScreenshotActivityReceiver != null) {
            mContext.unregisterReceiver(mScreenshotActivityReceiver);
        }
        super.tearDown();
    }

    public void testRedScreenshot() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        Log.i(TAG, "Starting screenshot test");
        mTestActivity.startTest(TEST_CASE_TYPE);
        Log.i(TAG, "start waitForAssistantToBeReady()");
        waitForAssistantToBeReady(mReadyLatch);

        waitForActivityResumeAndAssist(Color.RED);
        verifyAssistDataNullness(false, false, false, false);
        assertTrue(mScreenshotMatches);
    }

    public void testGreenScreenshot() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        Log.i(TAG, "Starting screenshot test");
        mTestActivity.startTest(TEST_CASE_TYPE);
        Log.i(TAG, "start waitForAssistantToBeReady()");
        waitForAssistantToBeReady(mReadyLatch);

        waitForActivityResumeAndAssist(Color.GREEN);
        verifyAssistDataNullness(false, false, false, false);
        assertTrue(mScreenshotMatches);
    }

    public void testBlueScreenshot() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        Log.i(TAG, "Starting screenshot test");
        mTestActivity.startTest(TEST_CASE_TYPE);
        Log.i(TAG, "start waitForAssistantToBeReady()");
        waitForAssistantToBeReady(mReadyLatch);

        waitForActivityResumeAndAssist(Color.BLUE);
        verifyAssistDataNullness(false, false, false, false);
        assertTrue(mScreenshotMatches);
    }

    private void waitForActivityResumeAndAssist(int color) throws Exception {
        mHasResumedLatch = new CountDownLatch(1);
        Bundle extras = new Bundle();
        extras.putInt(Utils.SCREENSHOT_COLOR_KEY, color);
        startSession(TEST_CASE_TYPE, extras);
        Log.i(TAG, "waiting for onResume() before continuing.");
        if (!mHasResumedLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("activity failed to resume in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
        waitForContext();
    }

    private class ScreenshotTestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(ScreenshotTest.TAG, "Got some broadcast: " + action);
            if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED)) {
                Log.i(ScreenshotTest.TAG, "Received assist receiver is registered.");
                if (mReadyLatch != null) {
                    mReadyLatch.countDown();
                }
            } else if (action.equals(Utils.APP_3P_HASRESUMED)) {
                if (mHasResumedLatch != null) {
                    mHasResumedLatch.countDown();
                }
            }
        }
    }
}
