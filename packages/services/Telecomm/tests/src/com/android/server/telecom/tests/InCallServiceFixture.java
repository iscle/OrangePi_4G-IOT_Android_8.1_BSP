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

package com.android.server.telecom.tests;

import com.android.internal.telecom.IInCallAdapter;
import com.android.internal.telecom.IInCallService;

import org.mockito.Mockito;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.telecom.AudioState;
import android.telecom.CallAudioState;
import android.telecom.ParcelableCall;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Controls a test {@link IInCallService} as would be provided by an InCall UI on a system.
 */
public class InCallServiceFixture implements TestFixture<IInCallService> {

    public String mLatestCallId;
    public IInCallAdapter mInCallAdapter;
    public CallAudioState mCallAudioState;
    public final Map<String, ParcelableCall> mCallById = new HashMap<>();
    public final Map<String, String> mPostDialById = new HashMap<>();
    public final Map<String, String> mPostDialWaitById = new HashMap<>();
    public boolean mBringToForeground;
    public boolean mShowDialpad;
    public boolean mCanAddCall;
    public boolean mSilenceRinger;
    public CountDownLatch mLock = new CountDownLatch(1);

    public class FakeInCallService extends IInCallService.Stub {
        @Override
        public void setInCallAdapter(IInCallAdapter inCallAdapter) throws RemoteException {
            if (mInCallAdapter != null && inCallAdapter != null) {
                throw new RuntimeException("Adapter is already set");
            }
            if (mInCallAdapter == null && inCallAdapter == null) {
                throw new RuntimeException("Adapter was never set");
            }
            mInCallAdapter = inCallAdapter;
        }

        @Override
        public void addCall(ParcelableCall call) throws RemoteException {
            if (mCallById.containsKey(call.getId())) {
                throw new RuntimeException("Call " + call.getId() + " already added");
            }
            mCallById.put(call.getId(), call);
            mLatestCallId = call.getId();
        }

        @Override
        public void updateCall(ParcelableCall call) throws RemoteException {
            if (!mCallById.containsKey(call.getId())) {
                throw new RuntimeException("Call " + call.getId() + " not added yet");
            }
            mCallById.put(call.getId(), call);
            mLatestCallId = call.getId();
            mLock.countDown();
        }

        @Override
        public void setPostDial(String callId, String remaining) throws RemoteException {
            mPostDialWaitById.remove(callId);
            mPostDialById.put(callId, remaining);
        }

        @Override
        public void setPostDialWait(String callId, String remaining) throws RemoteException {
            mPostDialById.remove(callId);
            mPostDialWaitById.put(callId, remaining);
        }

        @Override
        public void onCallAudioStateChanged(CallAudioState audioState) throws RemoteException {
            mCallAudioState = audioState;
        }

        @Override
        public void bringToForeground(boolean showDialpad) throws RemoteException {
            mBringToForeground = true;
            mShowDialpad = showDialpad;
        }

        @Override
        public void onCanAddCallChanged(boolean canAddCall) throws RemoteException {
            mCanAddCall = canAddCall;
        }

        @Override
        public void silenceRinger() throws RemoteException {
            mSilenceRinger = true;
        }

        @Override
        public void onConnectionEvent(String callId, String event, Bundle extras)
                throws RemoteException {
        }

        @Override
        public void onRttUpgradeRequest(String callId, int id) throws RemoteException {
        }

        @Override
        public void onRttInitiationFailure(String callId, int reason) throws RemoteException {
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public IInterface queryLocalInterface(String descriptor) {
            return this;
        }
    }

    private IInCallService.Stub mInCallServiceFake = new FakeInCallService();
    private IInCallService.Stub mInCallServiceSpy = Mockito.spy(mInCallServiceFake);

    public InCallServiceFixture() throws Exception { }

    @Override
    public IInCallService getTestDouble() {
        return mInCallServiceSpy;
    }

    public ParcelableCall getCall(String id) {
        return mCallById.get(id);
    }

    public IInCallAdapter getInCallAdapter() {
        return mInCallAdapter;
    }

    public void waitForUpdate() {
        try {
            mLock.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            return;
        }
        mLock = new CountDownLatch(1);
    }
}
