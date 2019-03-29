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

package com.android.compatibility.common.util;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BroadcastTestBase extends ActivityInstrumentationTestCase2<
                                       BroadcastTestStartActivity> {
    static final String TAG = "BroadcastTestBase";
    protected static final int TIMEOUT_MS = 20 * 1000;

    protected Context mContext;
    protected Bundle mResultExtras;
    private CountDownLatch mLatch;
    protected ActivityDoneReceiver mActivityDoneReceiver = null;
    private BroadcastTestStartActivity mActivity;
    private BroadcastUtils.TestcaseType mTestCaseType;
    protected boolean mHasFeature;

    public BroadcastTestBase() {
        super(BroadcastTestStartActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasFeature = false;
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature && mActivityDoneReceiver != null) {
            try {
                mContext.unregisterReceiver(mActivityDoneReceiver);
            } catch (IllegalArgumentException e) {
                // This exception is thrown if mActivityDoneReceiver in
                // the above call to unregisterReceiver is never registered.
                // If so, no harm done by ignoring this exception.
            }
            mActivityDoneReceiver = null;
        }
        super.tearDown();
    }

    protected boolean isIntentSupported(String intentStr) {
        Intent intent = new Intent(intentStr);
        final PackageManager manager = mContext.getPackageManager();
        assertNotNull(manager);
        if (manager.resolveActivity(intent, 0) == null) {
            Log.i(TAG, "No Activity found for the intent: " + intentStr);
            return false;
        }
        return true;
    }

    protected void startTestActivity(String intentSuffix) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.TEST_START_ACTIVITY_" + intentSuffix);
        intent.setComponent(new ComponentName(getInstrumentation().getContext(),
                BroadcastTestStartActivity.class));
        setActivityIntent(intent);
        mActivity = getActivity();
    }

    protected void registerBroadcastReceiver(BroadcastUtils.TestcaseType testCaseType) throws Exception {
        mTestCaseType = testCaseType;
        mLatch = new CountDownLatch(1);
        mActivityDoneReceiver = new ActivityDoneReceiver();
        mContext.registerReceiver(mActivityDoneReceiver,
                new IntentFilter(BroadcastUtils.BROADCAST_INTENT + testCaseType.toString()));
    }

    protected boolean startTestAndWaitForBroadcast(BroadcastUtils.TestcaseType testCaseType,
                                                   String pkg, String cls) throws Exception {
        Log.i(TAG, "Begin Testing: " + testCaseType);
        registerBroadcastReceiver(testCaseType);
        mActivity.startTest(testCaseType.toString(), pkg, cls);
        if (!mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to receive broadcast in " + TIMEOUT_MS + "msec");
            return false;
        }
        return true;
    }

    class ActivityDoneReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    BroadcastUtils.BROADCAST_INTENT +
                        BroadcastTestBase.this.mTestCaseType.toString())) {
                Bundle extras = intent.getExtras();
                Log.i(TAG, "received_broadcast for " + BroadcastUtils.toBundleString(extras));
                BroadcastTestBase.this.mResultExtras = extras;
                mLatch.countDown();
            }
        }
    }
}
