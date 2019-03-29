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

public class LocalVoiceInteractionTest
        extends ActivityInstrumentationTestCase2<TestLocalInteractionActivity> {

    private static final int TIMEOUT_MS = 20 * 1000;

    private TestLocalInteractionActivity mTestActivity;
    private Context mContext;
    private final CountDownLatch mLatchStart = new CountDownLatch(1);
    private final CountDownLatch mLatchStop = new CountDownLatch(1);
    protected boolean mHasFeature;
    protected static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";

    public LocalVoiceInteractionTest() {
        super(TestLocalInteractionActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startTestActivity();
        mContext = getInstrumentation().getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS);
    }

    private void startTestActivity() throws Exception {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.TEST_LOCAL_INTERACTION_ACTIVITY");
        intent.setComponent(new ComponentName(getInstrumentation().getContext(),
                TestLocalInteractionActivity.class));
        setActivityIntent(intent);
        mTestActivity = getActivity();
    }

    public void testLifecycle() throws Exception {
        if (!mHasFeature) {
            return;
        }
        VoiceInteractionTestReceiver.sServiceStartedLatch.await(5, TimeUnit.SECONDS);

        assertTrue("Doesn't support LocalVoiceInteraction",
                mTestActivity.isLocalVoiceInteractionSupported());
        mTestActivity.startLocalInteraction(mLatchStart);
        if (!mLatchStart.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to start voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }
        mTestActivity.stopLocalInteraction(mLatchStop);
        if (!mLatchStop.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to stop voice interaction in " + TIMEOUT_MS + "msec");
            return;
        }
        mTestActivity.finish();
    }
}
