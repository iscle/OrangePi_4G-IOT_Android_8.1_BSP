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

/** Test we receive proper assist data when context is disabled or enabled */

public class LifecycleTest extends AssistTestBase {
    private static final String TAG = "LifecycleTest";
    private static final String action_hasResumed = Utils.LIFECYCLE_HASRESUMED;
    private static final String action_hasFocus = Utils.LIFECYCLE_HASFOCUS;
    private static final String action_lostFocus = Utils.LIFECYCLE_LOSTFOCUS;
    private static final String action_onPause = Utils.LIFECYCLE_ONPAUSE;
    private static final String action_onStop = Utils.LIFECYCLE_ONSTOP;
    private static final String action_onDestroy = Utils.LIFECYCLE_ONDESTROY;

    private static final String TEST_CASE_TYPE = Utils.LIFECYCLE;

    private BroadcastReceiver mLifecycleTestBroadcastReceiver;
    private CountDownLatch mHasResumedLatch;
    private CountDownLatch mHasFocusLatch;
    private CountDownLatch mLostFocusLatch;
    private CountDownLatch mActivityLifecycleLatch;
    private CountDownLatch mDestroyLatch;
    private CountDownLatch mReadyLatch;
    private boolean mLostFocusIsLifecycle;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mLifecycleTestBroadcastReceiver = new LifecycleTestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(action_hasResumed);
        filter.addAction(action_hasFocus);
        filter.addAction(action_lostFocus);
        filter.addAction(action_onPause);
        filter.addAction(action_onStop);
        filter.addAction(action_onDestroy);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mLifecycleTestBroadcastReceiver, filter);
        mHasResumedLatch = new CountDownLatch(1);
        mHasFocusLatch = new CountDownLatch(1);
        mLostFocusLatch = new CountDownLatch(1);
        mActivityLifecycleLatch = new CountDownLatch(1);
        mDestroyLatch = new CountDownLatch(1);
        mReadyLatch = new CountDownLatch(1);
        mLostFocusIsLifecycle = false;
        startTestActivity(TEST_CASE_TYPE);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mLifecycleTestBroadcastReceiver != null) {
            mContext.unregisterReceiver(mLifecycleTestBroadcastReceiver);
            mLifecycleTestBroadcastReceiver = null;
        }
        mHasResumedLatch = null;
        mHasFocusLatch = null;
        mLostFocusLatch = null;
        mActivityLifecycleLatch = null;
        mDestroyLatch = null;
        mReadyLatch = null;
    }

    private void waitForOnResume() throws Exception {
        Log.i(TAG, "waiting for onResume() before continuing");
        if (!mHasResumedLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to resume in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    private void waitForHasFocus() throws Exception {
        Log.i(TAG, "waiting for window focus gain before continuing");
        if (!mHasFocusLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to get focus in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    private void waitForLostFocus() throws Exception {
        Log.i(TAG, "waiting for window focus lost before continuing");
        if (!mLostFocusLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to lose focus in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    private void waitAndSeeIfLifecycleMethodsAreTriggered() throws Exception {
        if (mActivityLifecycleLatch.await(Utils.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("One or more lifecycle methods were called after triggering assist");
        }
    }

    private void waitForDestroy() throws Exception {
        Log.i(TAG, "waiting for activity destroy before continuing");
        if (!mDestroyLatch.await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity failed to destroy in " + Utils.ACTIVITY_ONRESUME_TIMEOUT_MS + "msec");
        }
    }

    public void testLayerDoesNotTriggerLifecycleMethods() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        mTestActivity.startTest(Utils.LIFECYCLE);
        waitForAssistantToBeReady(mReadyLatch);
        mTestActivity.start3pApp(Utils.LIFECYCLE);
        waitForOnResume();
        waitForHasFocus();
        startSession();
        waitForContext();
        // Since there is no UI, focus should not be lost.  We are counting focus lost as
        // a lifecycle event in this case.
        // Do this after waitForContext(), since we don't start looking for context until
        // calling the above (RACY!!!).
        waitForLostFocus();
        waitAndSeeIfLifecycleMethodsAreTriggered();
        mContext.sendBroadcast(new Intent(Utils.HIDE_LIFECYCLE_ACTIVITY));
        waitForDestroy();
    }

    public void testNoUiLayerDoesNotTriggerLifecycleMethods() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        mLostFocusIsLifecycle = true;
        mTestActivity.startTest(Utils.LIFECYCLE_NOUI);
        waitForAssistantToBeReady(mReadyLatch);
        mTestActivity.start3pApp(Utils.LIFECYCLE_NOUI);
        waitForOnResume();
        waitForHasFocus();
        startSession();
        waitForContext();
        // Do this after waitForContext(), since we don't start looking for context until
        // calling the above (RACY!!!).
        waitAndSeeIfLifecycleMethodsAreTriggered();
        mContext.sendBroadcast(new Intent(Utils.HIDE_LIFECYCLE_ACTIVITY));
        waitForDestroy();
    }

    private class LifecycleTestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(action_hasResumed) && mHasResumedLatch != null) {
                mHasResumedLatch.countDown();
            } else if (action.equals(action_hasFocus) && mHasFocusLatch != null) {
                mHasFocusLatch.countDown();
            } else if (action.equals(action_lostFocus) && mLostFocusLatch != null) {
                if (mLostFocusIsLifecycle) {
                    mActivityLifecycleLatch.countDown();
                } else {
                    mLostFocusLatch.countDown();
                }
            } else if (action.equals(action_onPause) && mActivityLifecycleLatch != null) {
                mActivityLifecycleLatch.countDown();
            } else if (action.equals(action_onStop) && mActivityLifecycleLatch != null) {
                mActivityLifecycleLatch.countDown();
            } else if (action.equals(action_onDestroy) && mActivityLifecycleLatch != null) {
                mActivityLifecycleLatch.countDown();
                mDestroyLatch.countDown();
            } else if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED)) {
                if (mReadyLatch != null) {
                    mReadyLatch.countDown();
                }
            }
        }
    }
}
