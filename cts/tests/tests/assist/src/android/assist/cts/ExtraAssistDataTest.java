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
package android.assist.cts;

import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExtraAssistDataTest extends AssistTestBase {
    private static final String TAG = "ExtraAssistDataTest";
    private static final String TEST_CASE_TYPE = Utils.EXTRA_ASSIST;

    private BroadcastReceiver mReceiver;
    private CountDownLatch mHasResumedLatch = new CountDownLatch(1);
    private CountDownLatch mReadyLatch = new CountDownLatch(1);

    public ExtraAssistDataTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUpAndRegisterReceiver();
        startTestActivity(TEST_CASE_TYPE);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void setUpAndRegisterReceiver() {
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
        mReceiver = new ExtraAssistDataReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.APP_3P_HASRESUMED);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mReceiver, filter);
    }

    public void testAssistContentAndAssistData() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        mTestActivity.startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady(mReadyLatch);
        mTestActivity.start3pApp(TEST_CASE_TYPE);
        waitForOnResume();
        startSession();
        waitForContext();
        verifyAssistDataNullness(false, false, false, false);

        Log.i(TAG, "assist bundle is: " + Utils.toBundleString(mAssistBundle));

        // tests that the assist content's structured data is the expected
        assertEquals("AssistContent structured data did not match data in onProvideAssistContent",
                Utils.getStructuredJSON(), mAssistContent.getStructuredData());
        // tests the assist data. EXTRA_ASSIST_CONTEXT is what's expected.
        Bundle extraExpectedBundle = Utils.getExtraAssistBundle();
        Bundle extraAssistBundle = mAssistBundle.getBundle(Intent.EXTRA_ASSIST_CONTEXT);
        for (String key : extraExpectedBundle.keySet()) {
            assertTrue("Assist bundle does not contain expected extra context key: " + key,
                    extraAssistBundle.containsKey(key));
            assertEquals("Extra assist context bundle values do not match for key: " + key,
                    extraExpectedBundle.get(key), extraAssistBundle.get(key));
        }
    }

    private void waitForOnResume() throws Exception {
        Log.i(TAG, "waiting for onResume() before continuing");
        if (!mHasResumedLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to resume in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    private class ExtraAssistDataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.APP_3P_HASRESUMED)) {
                if (mHasResumedLatch != null) {
                    mHasResumedLatch.countDown();
                }
            } else if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED)) {
                if (mReadyLatch != null) {
                    mReadyLatch.countDown();
                }
            }
        }
    }
}
