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
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *  Test that the AssistStructure returned is properly formatted.
 */

public class LargeViewHierarchyTest extends AssistTestBase {
    private static final String TAG = "LargeViewHierarchyTest";
    private static final String TEST_CASE_TYPE = Utils.LARGE_VIEW_HIERARCHY;

    private BroadcastReceiver mReceiver;
    private CountDownLatch mHasResumedLatch = new CountDownLatch(1);
    private CountDownLatch mReadyLatch = new CountDownLatch(1);

    public LargeViewHierarchyTest() {
        super();
    }

    @Override
    protected void setUp() throws Exception {
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
        mReceiver = new LargeViewTestBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.APP_3P_HASRESUMED);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mReceiver, filter);
    }

    private void waitForOnResume() throws Exception {
        Log.i(TAG, "waiting for onResume() before continuing");
        if (!mHasResumedLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to resume in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    public void testTextView() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        mTestActivity.start3pApp(TEST_CASE_TYPE);
        mTestActivity.startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady(mReadyLatch);
        waitForOnResume();
        startSession();
        waitForContext();
        verifyAssistDataNullness(false, false, false, false);

        verifyAssistStructure(Utils.getTestAppComponent(TEST_CASE_TYPE),
                false /*FLAG_SECURE set*/);
    }

    private class LargeViewTestBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.APP_3P_HASRESUMED) && mHasResumedLatch != null) {
                mHasResumedLatch.countDown();
            } else if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED) && mReadyLatch != null) {
                mReadyLatch.countDown();
            }
        }
    }
}
