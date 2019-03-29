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

package android.voiceinteraction.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.voiceinteraction.common.Utils;

public class VoiceInteractionTest extends ActivityInstrumentationTestCase2<TestStartActivity> {
    static final String TAG = "VoiceInteractionTest";
    private static final int TIMEOUT_MS = 20 * 1000;

    private TestStartActivity mTestActivity;
    private Context mContext;
    private TestResultsReceiver mReceiver;
    private Bundle mResults;
    private final CountDownLatch mLatch = new CountDownLatch(1);
    protected boolean mHasFeature;
    protected static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";

    public VoiceInteractionTest() {
        super(TestStartActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startTestActivity();
        mContext = getInstrumentation().getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS);
        if (mHasFeature) {
            mReceiver = new TestResultsReceiver();
            mContext.registerReceiver(mReceiver, new IntentFilter(Utils.BROADCAST_INTENT));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature && mReceiver != null) {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException e) {
                // This exception is thrown if mReceiver in
                // the above call to unregisterReceiver is never registered.
                // If so, no harm done by ignoring this exception.
            }
            mReceiver = null;
        }
        super.tearDown();
    }

    private void startTestActivity() throws Exception {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.TEST_START_ACTIVITY");
        intent.setComponent(new ComponentName(getInstrumentation().getContext(),
                TestStartActivity.class));
        setActivityIntent(intent);
        mTestActivity = getActivity();
    }

    public void testAll() throws Exception {
        VoiceInteractionTestReceiver.sServiceStartedLatch.await(5, TimeUnit.SECONDS);

        if (!mHasFeature) {
            Log.i(TAG, "The device doesn't support feature: " + FEATURE_VOICE_RECOGNIZERS);
            return;
        }
        if (!mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to receive broadcast in " + TIMEOUT_MS + "msec");
            return;
        }
        if (mResults == null) {
            fail("no results received at all!");
            return;
        }
        int numFails = 0;
        for (Utils.TestCaseType t : Utils.TestCaseType.values()) {
            String singleResult = mResults.getString(t.toString());
            if (singleResult == null) {
                numFails++;
                Log.i(TAG, "No testresults received for " + t);
            } else {
                verifySingleTestcaseResult(t, singleResult);
            }
        }
        assertEquals(0, numFails);
        mTestActivity.finish();
    }

    private void verifySingleTestcaseResult(Utils.TestCaseType testCaseType, String result) {
        Log.i(TAG, "Recevied testresult: " + result + " for " + testCaseType);
        switch (testCaseType) {
          case ABORT_REQUEST_CANCEL_TEST:
              assertTrue(result.equals(Utils.ABORT_REQUEST_CANCEL_SUCCESS));
              break;
          case ABORT_REQUEST_TEST:
              assertTrue(result.equals(Utils.ABORT_REQUEST_SUCCESS));
              break;
          case COMMANDREQUEST_TEST:
              assertTrue(result.equals(Utils.COMMANDREQUEST_SUCCESS));
              break;
          case COMMANDREQUEST_CANCEL_TEST:
              assertTrue(result.equals(Utils.COMMANDREQUEST_CANCEL_SUCCESS));
              break;
          case COMPLETION_REQUEST_CANCEL_TEST:
              assertTrue(result.equals(Utils.COMPLETION_REQUEST_CANCEL_SUCCESS));
              break;
          case COMPLETION_REQUEST_TEST:
              assertTrue(result.equals(Utils.COMPLETION_REQUEST_SUCCESS));
              break;
          case CONFIRMATION_REQUEST_CANCEL_TEST:
              assertTrue(result.equals(Utils.CONFIRMATION_REQUEST_CANCEL_SUCCESS));
              break;
          case CONFIRMATION_REQUEST_TEST:
              assertTrue(result.equals(Utils.CONFIRMATION_REQUEST_SUCCESS));
              break;
          case PICKOPTION_REQUEST_CANCEL_TEST:
              assertTrue(result.equals(Utils.PICKOPTION_REQUEST_CANCEL_SUCCESS));
              break;
          case PICKOPTION_REQUEST_TEST:
              assertTrue(result.equals(Utils.PICKOPTION_REQUEST_SUCCESS));
              break;
          case SUPPORTS_COMMANDS_TEST:
              assertTrue(result.equals(Utils.SUPPORTS_COMMANDS_SUCCESS));
              break;
          default:
              Log.wtf(TAG, "not expected");
              break;
        }
        Log.i(TAG, testCaseType + " passed");
    }


    class TestResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Utils.BROADCAST_INTENT)) {
                Log.i(TAG, "received broadcast with results ");
                VoiceInteractionTest.this.mResults = intent.getExtras();
                mLatch.countDown();
            }
        }
    }
}
