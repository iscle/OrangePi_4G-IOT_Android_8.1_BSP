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

/** Test that triggering the Assistant causes the underlying Activity to lose focus **/

public class FocusChangeTest extends AssistTestBase {
    private static final String TAG = "FocusChangeTest";
    private static final String TEST_CASE_TYPE = Utils.FOCUS_CHANGE;

    private BroadcastReceiver mReceiver;
    private CountDownLatch mHasGainedFocusLatch = new CountDownLatch(1);
    private CountDownLatch mHasLostFocusLatch = new CountDownLatch(1);
    private CountDownLatch mReadyLatch = new CountDownLatch(1);

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
        mReceiver = new FocusChangeTestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.GAINED_FOCUS);
        filter.addAction(Utils.LOST_FOCUS);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mReceiver, filter);
    }

    private void waitToGainFocus() throws Exception {
        Log.i(TAG, "Waiting for the underlying activity to gain focus before continuing.");
        if (!mHasGainedFocusLatch.await(Utils.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to gain focus in " + Utils.TIMEOUT_MS + "msec.");
        }
    }

    private void waitToLoseFocus() throws Exception {
        Log.i(TAG, "Waiting for the underlying activity to lose focus.");
        if (!mHasLostFocusLatch.await(Utils.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity maintained focus despite the Assistant Firing"
                 + Utils.TIMEOUT_MS + "msec.");
        }
    }

    public void testLayerCausesUnderlyingActivityToLoseFocus() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        mTestActivity.startTest(Utils.FOCUS_CHANGE);
        waitForAssistantToBeReady(mReadyLatch);
        mTestActivity.start3pApp(Utils.FOCUS_CHANGE);
        waitToGainFocus();
        startSession();
        waitToLoseFocus();
    }

    private class FocusChangeTestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.GAINED_FOCUS) && mHasGainedFocusLatch != null) {
                mHasGainedFocusLatch.countDown();
            } else if (action.equals(Utils.LOST_FOCUS) && mHasLostFocusLatch != null) {
                mHasLostFocusLatch.countDown();
            } else if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED)) {
                if (mReadyLatch != null) {
                    mReadyLatch.countDown();
                }
            }
        }
    }
}
