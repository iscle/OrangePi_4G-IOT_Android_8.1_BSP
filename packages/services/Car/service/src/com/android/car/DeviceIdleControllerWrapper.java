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
package com.android.car;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;

/**
* An wrapper class that can be backed by a real DeviceIdleController or a mocked one.
*/
public abstract class DeviceIdleControllerWrapper {
    private static final String TAG = "Garage_DeviceIdleWrapper";

    private static final int MSG_REPORT_ACTIVE = 1;

    @VisibleForTesting
    protected WeakReference<DeviceMaintenanceActivityListener> mListener;

    public interface DeviceMaintenanceActivityListener {
        public void onMaintenanceActivityChanged(boolean active);
    }
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private boolean mActive;

    private Handler mHandler = new IdleControllerHandler();

    private class IdleControllerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REPORT_ACTIVE:
                    boolean active  = msg.arg1 == 1;
                    if (mListener.get() != null) {
                        mListener.get().onMaintenanceActivityChanged(active);
                    }
                    break;
            }
        }
    }

    public boolean startTracking(DeviceMaintenanceActivityListener listener) {
        synchronized (mLock) {
            mListener = new WeakReference<DeviceMaintenanceActivityListener>(listener);
            mActive = startLocked();
            return mActive;
        }
    }

    protected abstract boolean startLocked();

    public abstract void stopTracking();

    @VisibleForTesting
    protected void reportActiveLocked(final boolean active) {
        // post to a handler instead of calling the callback directly to avoid potential deadlock.
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REPORT_ACTIVE, active ? 1 : 0, 0));
    }

    @VisibleForTesting
    protected void setMaintenanceActivity(final boolean active) {
        synchronized (mLock) {
            if (mActive == active) {
                return;
            }
            mActive = active;

            if (mListener.get() == null) {
                // do cleanup if the listener has gone and did not call release.
                stopTracking();
                return;
            }
            reportActiveLocked(active);
        }
    }
}
