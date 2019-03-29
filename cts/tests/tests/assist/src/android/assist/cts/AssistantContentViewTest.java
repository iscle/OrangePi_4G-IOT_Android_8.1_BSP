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
import android.graphics.Point;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test verifying the Content View of the Assistant */
public class AssistantContentViewTest extends AssistTestBase {
    private static final String TAG = "ContentViewTest";
    private BroadcastReceiver mReceiver;
    private CountDownLatch mContentViewLatch, mReadyLatch;
    private Intent mIntent;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContentViewLatch = new CountDownLatch(1);
        mReadyLatch = new CountDownLatch(1);
        setUpAndRegisterReceiver();
        startTestActivity(Utils.VERIFY_CONTENT_VIEW);
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
        mReceiver = new AssistantContentViewReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.BROADCAST_CONTENT_VIEW_HEIGHT);
        filter.addAction(Utils.ASSIST_RECEIVER_REGISTERED);
        mContext.registerReceiver(mReceiver, filter);

    }

    private void waitForContentView() throws Exception {
        Log.i(TAG, "waiting for the Assistant's Content View  before continuing");
        if (!mContentViewLatch.await(Utils.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("failed to receive content view in " + Utils.TIMEOUT_MS + "msec");
        }
    }

    public void testAssistantContentViewDimens() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
          Log.d(TAG, "Not running assist tests on low-RAM device.");
          return;
        }
        mTestActivity.startTest(Utils.VERIFY_CONTENT_VIEW);
        waitForAssistantToBeReady(mReadyLatch);
        startSession();
        waitForContentView();
        int height = mIntent.getIntExtra(Utils.EXTRA_CONTENT_VIEW_HEIGHT, 0);
        int width = mIntent.getIntExtra(Utils.EXTRA_CONTENT_VIEW_WIDTH, 0);
        Point displayPoint = (Point) mIntent.getParcelableExtra(Utils.EXTRA_DISPLAY_POINT);
        assertEquals(displayPoint.y, height);
        assertEquals(displayPoint.x, width);
    }

    private class AssistantContentViewReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Utils.BROADCAST_CONTENT_VIEW_HEIGHT)) {
                mIntent = intent;
                if (mContentViewLatch != null) {
                    mContentViewLatch.countDown();
                }
            } else if (action.equals(Utils.ASSIST_RECEIVER_REGISTERED)) {
                if (mReadyLatch != null) {
                    mReadyLatch.countDown();
                }
            }
        }
    }
}
