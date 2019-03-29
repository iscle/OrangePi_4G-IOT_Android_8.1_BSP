/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * InCallService implementation which declares in its manifest that it wants to be informed of
 * self-maanged connections.
 */
public class SelfManagedAwareInCallService extends InCallService {

    public static class CallCallback extends Call.Callback {
        public static int INVALID_STATE = -1;

        private CountDownLatch mStateChangeLatch = new CountDownLatch(1);
        private CountDownLatch mDetailsChangeLatch = new CountDownLatch(1);
        private int mNewState;

        @Override
        public void onStateChanged(Call call, int state) {
            mNewState = state;
            mStateChangeLatch.countDown();
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            mDetailsChangeLatch.countDown();
        }

        @Override
        public void onCallDestroyed(Call call) {

        }

        public int waitOnStateChanged() {
            mStateChangeLatch = TestUtils.waitForLock(mStateChangeLatch);
            if (mStateChangeLatch != null) {
                return mNewState;
            } else {
                mStateChangeLatch = new CountDownLatch(1);
                return INVALID_STATE;
            }
        }
    }

    private static final String LOG_TAG="SelfMgAwareICS";
    private static CountDownLatch sServiceBoundLatch = new CountDownLatch(1);
    private static CountDownLatch sServiceUnBoundLatch = new CountDownLatch(1);
    private static SelfManagedAwareInCallService sInCallService;

    private List<Call> mCalls = new ArrayList<Call>();
    private Map<Call, CallCallback> mCallCallbacks = new ArrayMap<>();
    private CountDownLatch mCallAddedLatch = new CountDownLatch(1);

    public SelfManagedAwareInCallService() throws Exception {
        super();
        sInCallService = this;
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        Log.i(LOG_TAG, "Service bound");

        sServiceBoundLatch.countDown();
        sServiceUnBoundLatch = new CountDownLatch(1);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sServiceBoundLatch = new CountDownLatch(1);
        sServiceUnBoundLatch.countDown();
        return super.onUnbind(intent);
    }

    public void tearDown() {
        sServiceBoundLatch = new CountDownLatch(1);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (!mCalls.contains(call)) {
            mCalls.add(call);
            CallCallback callback = new CallCallback();
            call.registerCallback(callback);
            mCallCallbacks.put(call, callback);
            mCallAddedLatch.countDown();
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        call.unregisterCallback(mCallCallbacks.get(call));
        mCallCallbacks.remove(call);
        mCalls.remove(call);
    }

    public static SelfManagedAwareInCallService getInCallService() {
        return sInCallService;
    }

    public Call waitForCallAdded() {
        mCallAddedLatch = TestUtils.waitForLock(mCallAddedLatch);
        if (mCallAddedLatch != null) {
            return mCalls.get(mCalls.size() - 1);
        } else {
            return null;
        }
    }

    public static boolean waitForBinding() {
        sServiceBoundLatch = TestUtils.waitForLock(sServiceBoundLatch);
        return sServiceBoundLatch != null;
    }

    public static boolean waitForUnBinding() {
        sServiceUnBoundLatch = TestUtils.waitForLock(sServiceUnBoundLatch);
        return sServiceUnBoundLatch != null;
    }

    public CallCallback getCallCallback(Call call) {
        return mCallCallbacks.get(call);
    }
}
