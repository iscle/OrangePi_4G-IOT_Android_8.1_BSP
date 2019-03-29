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
 * Test we receive proper assist data (root assistStructure with no children) when the assistant is
 * invoked on an app with FLAG_SECURE set.
 */
public class FlagSecureTest extends AssistTestBase {
    static final String TAG = "FlagSecureTest";

    private static final String TEST_CASE_TYPE = Utils.FLAG_SECURE;

    private BroadcastReceiver mReceiver;
    private CountDownLatch mHasResumedLatch = new CountDownLatch(1);
    private CountDownLatch mReadyLatch = new CountDownLatch(1);
    public FlagSecureTest() {
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
        mReceiver = new FlagSecureTestBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.FLAG_SECURE_HASRESUMED);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mReceiver, filter);
    }

    private void waitForOnResume() throws Exception {
        Log.i(TAG, "waiting for onResume() before continuing");
        if (!mHasResumedLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to resume in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    public void testSecureActivity() throws Exception {
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
        // verify that we have only the root window and not its children.
        verifyAssistStructure(Utils.getTestAppComponent(TEST_CASE_TYPE), true);
    }

    private class FlagSecureTestBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.FLAG_SECURE_HASRESUMED)) {
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
