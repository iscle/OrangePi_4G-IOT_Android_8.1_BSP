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
package com.android.tv.testing.testinput;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.android.tv.testing.ChannelInfo;

/**
 * Connection for controlling the Test TV Input Service.
 *
 * <p>Wrapped methods for calling {@link ITestInputControl} that waits for a binding and rethrows
 * {@link RemoteException} as {@link RuntimeException } are also included.
 */
public class TestInputControlConnection implements ServiceConnection {
    private static final String TAG = "TestInputControlConn";
    private static final int BOUND_CHECK_INTERVAL_MS = 10;

    private ITestInputControl mControl;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mControl = ITestInputControl.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.w(TAG, "TestInputControl service disconnected unexpectedly.");
        mControl = null;
    }

    /**
     * Is the service currently connected.
     */
    public boolean isBound() {
        return mControl != null;
    }

    /**
     * Update the state of the channel.
     *
     * @param channel the channel to update.
     * @param data    the new state for the channel.
     */
    public void updateChannelState(ChannelInfo channel, ChannelStateData data) {
        waitUntilBound();
        try {
            mControl.updateChannelState(channel.originalNetworkId, data);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sleep until {@link #isBound()} is true;
     */
    public void waitUntilBound() {
        while (!isBound()) {
            SystemClock.sleep(BOUND_CHECK_INTERVAL_MS);
        }
    }
}
