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

import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.cts.TestUtils.InvokeCounter;

import java.util.concurrent.CountDownLatch;

/**
 * CTS Test self-managed {@link Connection} implementation.
 */
public class SelfManagedConnection extends Connection {

    InvokeCounter mCallAudioRouteInvokeCounter = new InvokeCounter("onCallAudioStateChanged");
    InvokeCounter mOnShowIncomingUiInvokeCounter = new InvokeCounter(
            "onShowIncomingUiInvokeCounter");
    CountDownLatch mOnHoldLatch = new CountDownLatch(1);

    public static abstract class Listener {
        void onDestroyed(SelfManagedConnection connection) { };
    }

    private final boolean mIsIncomingCall;
    private final Listener mListener;

    public SelfManagedConnection(boolean isIncomingCall, Listener listener) {
        mIsIncomingCall = isIncomingCall;
        mListener = listener;
    }

    public boolean isIncomingCall() {
        return mIsIncomingCall;
    }

    public void disconnectAndDestroy() {
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        mListener.onDestroyed(this);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        mCallAudioRouteInvokeCounter.invoke(state);
    }

    @Override
    public void onShowIncomingCallUi() {
        mOnShowIncomingUiInvokeCounter.invoke();
    }

    @Override
    public void onHold() {
        mOnHoldLatch.countDown();
    }

    public InvokeCounter getCallAudioStateChangedInvokeCounter() {
        return mCallAudioRouteInvokeCounter;
    }

    public InvokeCounter getOnShowIncomingUiInvokeCounter() {
        return mOnShowIncomingUiInvokeCounter;
    }

    public boolean waitOnHold() {
        mOnHoldLatch = TestUtils.waitForLock(mOnHoldLatch);
        return mOnHoldLatch != null;
    }
}
