/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.cts.verifierusbcompanion;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Arrays;

/**
 * Framework for all companion tests of this app.
 */
abstract class TestCompanion extends Thread {
    private final @NonNull Context mContext;
    private final @NonNull TestHandler mHandler;
    private boolean mShouldAbort;

    /**
     * Create a new test companion
     *
     * @param context Context to be used for the test companion
     * @param observer Observer observing the test companion
     */
    TestCompanion(@NonNull Context context, @NonNull TestObserver observer) {
        mContext = context;
        mHandler = new TestHandler(observer);
    }

    /**
     * @return the context to be used by the test companion
     */
    protected @NonNull Context getContext() {
        return mContext;
    }

    void requestAbort() {
        mShouldAbort = true;
        interrupt();
    }

    /**
     * @return if the test companion should abort
     */
    protected boolean shouldAbort() {
        return mShouldAbort;
    }

    /**
     * Indicate that the test companion succeeded.
     */
    protected void success() {
        mHandler.obtainMessage(TestHandler.SUCCESS).sendToTarget();
    }

    /**
     * Indicate that the test companion failed.
     *
     * @param error Description why the test failed
     */
    protected void fail(@NonNull CharSequence error) {
        mHandler.obtainMessage(TestHandler.FAIL, error).sendToTarget();
    }

    /**
     * Indicate that the test companion was aborted.
     */
    private void abort() {
        mHandler.obtainMessage(TestHandler.ABORT).sendToTarget();
    }

    /**
     * Update the status of the test companion.
     *
     * @param status The new status message
     */
    protected void updateStatus(@NonNull CharSequence status) {
        Log.i(this.getClass().getSimpleName(), "Status: " + status);

        mHandler.obtainMessage(TestHandler.UPDATE_STATUS, status).sendToTarget();
    }

    @Override
    public final void run() {
        try {
            runTest();
        } catch (Throwable e) {
            if (e instanceof InterruptedException && shouldAbort()) {
                abort();
            } else {
                fail(e + "\n" + Arrays.toString(e.getStackTrace()));
            }

            return;
        }

        success();
    }

    /**
     * The test companion code.
     *
     * @throws Throwable  If this returns without an exception, the test companion succeeded.
     */
    protected abstract void runTest() throws Throwable;

    /**
     * Observe the state of this test companion
     */
    public interface TestObserver {
        void onStatusUpdate(@NonNull CharSequence status);
        void onSuccess();
        void onFail(@NonNull CharSequence error);
        void onAbort();
    }

    /**
     * Serialize callbacks to main thread.
     */
    public static class TestHandler extends Handler {
        static final int SUCCESS = 0;
        static final int FAIL = 1;
        static final int ABORT = 2;
        static final int UPDATE_STATUS = 3;

        private final @NonNull TestObserver mObserver;

        TestHandler(@NonNull TestObserver observer) {
            mObserver = observer;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SUCCESS:
                    mObserver.onSuccess();
                    break;
                case FAIL:
                    mObserver.onFail((CharSequence)msg.obj);
                    break;
                case ABORT:
                    mObserver.onAbort();
                    break;
                case UPDATE_STATUS:
                    mObserver.onStatusUpdate((CharSequence)msg.obj);
                    break;
            }
        }
    }
}
