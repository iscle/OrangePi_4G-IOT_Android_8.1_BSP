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

package com.android.cts.managedprofile;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This class contains tests for cross profile widget providers that are run on the primary users.
 * The tests connect to a {@link android.appwidget.AppWidgetHost} and check whether the cross
 * cross-profile widget can / cannot be found from the primary user.
 * The tests cannot be run independently, but are part of one hostside test.
 */
public class CrossProfileWidgetPrimaryUserTest extends AndroidTestCase {
    private static final String TAG = "CrossProfileWidgetPrimaryUserTest";

    private static final int MSG_RESULT = 0;
    private static final int MSG_PROVIDER_PRESENT = 1;
    private static final int MSG_PROVIDER_UPDATES = 2;

    private static final int RESULT_UNKNOWN = 0;
    private static final int RESULT_PRESENT = 1;
    private static final int RESULT_NOTPRESENT = 2;
    private static final int RESULT_INTERRUPTED = 3;
    private static final int RESULT_TIMEOUT = 4;

    private static final String PACKAGE_EXTRA = "package-extra";

    private Messenger mService;
    private Connection mConnection;
    private Result mResult;
    private Messenger mResultMessenger;

    @Override
    protected void setUp() throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(CrossProfileWidgetTest.WIDGET_PROVIDER_PKG,
                CrossProfileWidgetTest.WIDGET_PROVIDER_PKG + ".SimpleAppWidgetHostService"));
        mConnection = new Connection();
        getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mConnection.waitForService();
        mResult = new Result(Looper.getMainLooper());
        mResultMessenger = new Messenger(mResult);
    }

    public void testHasCrossProfileWidgetProvider_false() throws Exception {
        int result = sendMessageToCallbacksService(MSG_PROVIDER_PRESENT,
                CrossProfileWidgetTest.WIDGET_PROVIDER_PKG);
        assertEquals(RESULT_NOTPRESENT, result);
    }

    public void testHostReceivesWidgetUpdates_false() throws Exception {
        int result = sendMessageToCallbacksService(MSG_PROVIDER_UPDATES,
                CrossProfileWidgetTest.WIDGET_PROVIDER_PKG);
        assertEquals(RESULT_NOTPRESENT, result);
    }

    public void testHasCrossProfileWidgetProvider_true() throws Exception {
        int result = sendMessageToCallbacksService(MSG_PROVIDER_PRESENT,
                CrossProfileWidgetTest.WIDGET_PROVIDER_PKG);
        assertEquals(RESULT_PRESENT, result);
    }

    public void testHostReceivesWidgetUpdates_true() throws Exception {
        int result = sendMessageToCallbacksService(MSG_PROVIDER_UPDATES,
                CrossProfileWidgetTest.WIDGET_PROVIDER_PKG);
        assertEquals(RESULT_PRESENT, result);
    }

    private int sendMessageToCallbacksService(int msg, String packageName)
            throws Exception {
        Bundle params = new Bundle();
        params.putString(PACKAGE_EXTRA, packageName);

        Message message = Message.obtain(null, msg, params);
        message.replyTo = mResultMessenger;

        mService.send(message);

        return mResult.waitForResult();
    }

    private static class Result extends Handler {

        private final Semaphore mSemaphore = new Semaphore(0);
        public int result = 0;

        public Result(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RESULT) {
                result = msg.arg1;
                mSemaphore.release();
            } else {
                super.handleMessage(msg);
            }
        }

        public int waitForResult() {
            try {
                if (mSemaphore.tryAcquire(120, TimeUnit.SECONDS)) {
                    return result;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted when talking to service", e);
            }
            return RESULT_TIMEOUT;
        }
    }

    private class Connection implements ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

        public void waitForService() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted when connecting to service", e);
            }
            fail("failed to connect to service");
        }
    };

}
