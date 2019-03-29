/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.MockReceiver;
import android.app.stubs.MockService;
import android.app.stubs.PendingIntentStubActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PendingIntentTest extends AndroidTestCase {

    private static final int WAIT_TIME = 10000;
    private PendingIntent mPendingIntent;
    private Intent mIntent;
    private Context mContext;
    private boolean mFinishResult;
    private boolean mHandleResult;
    private String mResultAction;
    private PendingIntent.OnFinished mFinish;
    private boolean mLooperStart;
    private Looper mLooper;
    private Handler mHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mFinish = new PendingIntent.OnFinished() {
            public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                    String resultData, Bundle resultExtras) {
                synchronized (mFinish) {
                    mFinishResult = true;
                    if (intent != null) {
                        mResultAction = intent.getAction();
                    }
                    mFinish.notifyAll();
                }
            }
        };

        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLooperStart = true;
                Looper.loop();
            }
        }.start();
        while (!mLooperStart) {
            Thread.sleep(50);
        }
        mHandler = new Handler(mLooper) {
            @Override
            public void dispatchMessage(Message msg) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                super.dispatchMessage(msg);
            }

            @Override
            public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                return super.sendMessageAtTime(msg, uptimeMillis);
            }

            @Override
            public void handleMessage(Message msg) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                super.handleMessage(msg);
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mLooper.quit();
    }

    private void prepareFinish() {
        synchronized (mFinish) {
            mFinishResult = false;
            mHandleResult = false;
        }
    }

    public boolean waitForFinish(long timeout) {
        long now = SystemClock.elapsedRealtime();
        final long endTime = now + timeout;
        synchronized (mFinish) {
            while (!mFinishResult && now < endTime) {
                try {
                    mFinish.wait(endTime - now);
                } catch (InterruptedException e) {
                }
                now = SystemClock.elapsedRealtime();
            }
            return mFinishResult;
        }
    }

    public void testGetActivity() throws InterruptedException, CanceledException {
        PendingIntentStubActivity.prepare();
        mPendingIntent = null;
        mIntent = new Intent();

        mIntent.setClass(mContext, PendingIntentStubActivity.class);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        assertEquals(mContext.getPackageName(), mPendingIntent.getTargetPackage());

        mPendingIntent.send();

        PendingIntentStubActivity.waitForCreate(WAIT_TIME);
        assertNotNull(mPendingIntent);
        assertEquals(PendingIntentStubActivity.status, PendingIntentStubActivity.ON_CREATE);

        // test getActivity return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT);

        pendingIntentSendError(mPendingIntent);
    }

    private void pendingIntentSendError(PendingIntent pendingIntent) {
        try {
            // From the doc send function will throw CanceledException if the PendingIntent
            // is no longer allowing more intents to be sent through it. So here call it twice then
            // a CanceledException should be caught.
            mPendingIntent.send();
            mPendingIntent.send();
            fail("CanceledException expected, but not thrown");
        } catch (PendingIntent.CanceledException e) {
            // expected
        }
    }

    public void testGetBroadcast() throws InterruptedException, CanceledException {
        MockReceiver.prepareReceive(null, 0);
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // test getBroadcast return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT);

        pendingIntentSendError(mPendingIntent);
    }

    // Local receiver for examining delivered broadcast intents
    private class ExtraReceiver extends BroadcastReceiver {
        private final String extraName;

        public volatile int extra = 0;
        public CountDownLatch latch = null;

        public ExtraReceiver(String name) {
            extraName = name;
        }

        public void onReceive(Context ctx, Intent intent) {
            extra = intent.getIntExtra(extraName, 0);
            latch.countDown();
        }

        public void reset() {
            extra = 0;
            latch = new CountDownLatch(1);
        }

        public boolean waitForReceipt() throws InterruptedException {
            return latch.await(WAIT_TIME, TimeUnit.MILLISECONDS);
        }
    }

    public void testUpdateCurrent() throws InterruptedException, CanceledException {
        final int EXTRA_1 = 50;
        final int EXTRA_2 = 38;
        final String EXTRA_NAME = "test_extra";
        final String BROADCAST_ACTION = "testUpdateCurrent_action";

        final Context context = getContext();
        final ExtraReceiver br = new ExtraReceiver(EXTRA_NAME);
        final IntentFilter filter = new IntentFilter(BROADCAST_ACTION);
        context.registerReceiver(br, filter);

        // Baseline: establish that we get the extra properly
        PendingIntent pi;
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra(EXTRA_NAME, EXTRA_1);

        pi = PendingIntent.getBroadcast(context, 0, intent, 0);

        try {
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_1);

            // Change the extra in the Intent
            intent.putExtra(EXTRA_NAME, EXTRA_2);

            // Repeat PendingIntent.getBroadcast() *without* UPDATE_CURRENT, so we expect
            // the underlying Intent to still be the initial one with EXTRA_1
            pi = PendingIntent.getBroadcast(context, 0, intent, 0);
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_1);

            // This time use UPDATE_CURRENT, and expect to get the updated extra when the
            // PendingIntent is sent
            pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_2);
        } finally {
            pi.cancel();
            context.unregisterReceiver(br);
        }
    }

    public void testGetService() throws InterruptedException, CanceledException {
        MockService.prepareStart();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send();

        MockService.waitForStart(WAIT_TIME);
        assertTrue(MockService.result);

        // test getService return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT);

        pendingIntentSendError(mPendingIntent);
    }

    public void testStartServiceOnFinishedHandler() throws InterruptedException, CanceledException {
        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send(mContext, 1, null, mFinish, null);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertTrue(MockService.result);

        assertTrue(mFinishResult);
        assertFalse(mHandleResult);
        mPendingIntent.cancel();

        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send(mContext, 1, null, mFinish, mHandler);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertTrue(MockService.result);

        assertTrue(mFinishResult);
        assertTrue(mHandleResult);
        mPendingIntent.cancel();

    }

    public void testCancel() throws CanceledException {
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send();

        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    private void pendingIntentSendShouldFail(PendingIntent pendingIntent) {
        try {
            pendingIntent.send();
            fail("CanceledException expected, but not thrown");
        } catch (CanceledException e) {
            // expected
        }
    }

    public void testSend() throws InterruptedException, CanceledException {
        MockReceiver.prepareReceive(null, -1);
        mIntent = new Intent();
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);

        // send function to send default code 0
        assertEquals(0, MockReceiver.sResultCode);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        pendingIntentSendShouldFail(mPendingIntent);
    }

    public void testSendWithParamInt() throws InterruptedException, CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        MockReceiver.prepareReceive(null, 0);
        // send result code 1.
        mPendingIntent.send(1);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert the result code
        assertEquals(1, MockReceiver.sResultCode);
        assertEquals(mResultAction, null);

        MockReceiver.prepareReceive(null, 0);
        // send result code 2
        mPendingIntent.send(2);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert the result code
        assertEquals(2, MockReceiver.sResultCode);
        assertEquals(MockReceiver.sAction, MockReceiver.MOCKACTION);
        assertNull(mResultAction);
        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    public void testSendWithParamContextIntIntent() throws InterruptedException, CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        MockReceiver.prepareReceive(null, 0);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);

        mPendingIntent.send(mContext, 1, null);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        assertEquals(1, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);

        mPendingIntent.send(mContext, 2, mIntent);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        assertEquals(2, MockReceiver.sResultCode);
        mPendingIntent.cancel();
    }

    public void testSendWithParamIntOnFinishedHandler() throws InterruptedException,
            CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(1, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertFalse(mFinishResult);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(1, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(2, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(2, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        mPendingIntent.send(3, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertTrue(mFinishResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(3, MockReceiver.sResultCode);
        mPendingIntent.cancel();
    }

    public void testSendWithParamContextIntIntentOnFinishedHandler() throws InterruptedException,
            CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(getContext(), MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertFalse(mFinishResult);
        assertFalse(mHandleResult);
        assertNull(mResultAction);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertEquals(mResultAction, MockReceiver.MOCKACTION);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertEquals(mResultAction, MockReceiver.MOCKACTION);
        assertTrue(mFinishResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();
    }


    public void testSendNoReceiverOnFinishedHandler() throws InterruptedException,
            CanceledException {
        // This action won't match anything, so no receiver will run but we should
        // still get a finish result.
        final String BAD_ACTION = MockReceiver.MOCKACTION + "_bad";
        mIntent = new Intent(BAD_ACTION);
        mIntent.setAction(BAD_ACTION);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertEquals(mResultAction, BAD_ACTION);
        assertFalse(mHandleResult);
        assertNull(MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertEquals(mResultAction, BAD_ACTION);
        assertTrue(mFinishResult);
        assertNull(MockReceiver.sAction);
        mPendingIntent.cancel();
    }

    public void testGetTargetPackage() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        assertEquals(mContext.getPackageName(), mPendingIntent.getTargetPackage());
    }

    public void testEquals() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        PendingIntent target = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        assertFalse(mPendingIntent.equals(target));
        assertFalse(mPendingIntent.hashCode() == target.hashCode());
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent, 1);

        target = PendingIntent.getActivity(mContext, 1, mIntent, 1);
        assertTrue(mPendingIntent.equals(target));

        mIntent = new Intent(MockReceiver.MOCKACTION);
        target = PendingIntent.getBroadcast(mContext, 1, mIntent, 1);
        assertFalse(mPendingIntent.equals(target));
        assertFalse(mPendingIntent.hashCode() == target.hashCode());

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent, 1);
        target = PendingIntent.getActivity(mContext, 1, mIntent, 1);

        assertTrue(mPendingIntent.equals(target));
        assertEquals(mPendingIntent.hashCode(), target.hashCode());
    }

    public void testDescribeContents() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        final int expected = 0;
        assertEquals(expected, mPendingIntent.describeContents());
    }

    public void testWriteToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        Parcel parcel = Parcel.obtain();

        mPendingIntent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PendingIntent pendingIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        assertTrue(mPendingIntent.equals(pendingIntent));
    }

    public void testReadAndWritePendingIntentOrNullToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        assertNotNull(mPendingIntent.toString());

        Parcel parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        parcel.setDataPosition(0);
        PendingIntent target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertEquals(mPendingIntent, target);
        assertEquals(mPendingIntent.getTargetPackage(), target.getTargetPackage());

        mPendingIntent = null;
        parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertNull(target);
    }

}
