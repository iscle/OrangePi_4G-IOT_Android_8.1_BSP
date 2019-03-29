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
 * limitations under the License.
 */

package android.app.cts.android.app.cts.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Helper for binding to a service and monitoring the state of that service.
 */
public final class ServiceConnectionHandler implements ServiceConnection {
    static final String TAG = "ServiceConnectionHandler";

    final Context mContext;
    final Intent mIntent;
    boolean mMonitoring;
    boolean mBound;
    IBinder mService;

    final ServiceConnection mMainBinding = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    public ServiceConnectionHandler(Context context, Intent intent) {
        mContext = context;
        mIntent = intent;
    }

    public void startMonitoring() {
        synchronized (this) {
            if (mMonitoring) {
                throw new IllegalStateException("Already monitoring");
            }
            if (!mContext.bindService(mIntent, this, Context.BIND_WAIVE_PRIORITY)) {
                throw new IllegalStateException("Failed to bind " + mIntent);
            }
            mMonitoring = true;
            mService = null;
        }
    }

    public void waitForConnect(long timeout) {
        final long endTime = SystemClock.uptimeMillis() + timeout;

        synchronized (this) {
            while (mService == null) {
                final long now = SystemClock.uptimeMillis();
                if (now >= endTime) {
                    throw new IllegalStateException("Timed out binding to " + mIntent);
                }
                try {
                    wait(endTime - now);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public IBinder getServiceIBinder() {
        return mService;
    }

    public void waitForDisconnect(long timeout) {
        final long endTime = SystemClock.uptimeMillis() + timeout;

        synchronized (this) {
            while (mService != null) {
                final long now = SystemClock.uptimeMillis();
                if (now >= endTime) {
                    throw new IllegalStateException("Timed out unbinding from " + mIntent);
                }
                try {
                    wait(endTime - now);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public void stopMonitoringIfNeeded() {
        synchronized (this) {
            if (mMonitoring) {
                stopMonitoring();
            }
        }
    }

    public void stopMonitoring() {
        synchronized (this) {
            if (!mMonitoring) {
                throw new IllegalStateException("Not monitoring");
            }
            mContext.unbindService(this);
            mMonitoring = false;
        }
    }

    public void bind(long timeout) {
        synchronized (this) {
            if (mBound) {
                throw new IllegalStateException("Already bound");
            }
            // Here's the trick: the first binding allows us to to see the service come
            // up and go down but doesn't actually cause it to run or impact process management.
            // The second binding actually brings it up.
            startMonitoring();
            if (!mContext.bindService(mIntent, mMainBinding, Context.BIND_AUTO_CREATE)) {
                throw new IllegalStateException("Failed to bind " + mIntent);
            }
            mBound = true;
            waitForConnect(timeout);
        }
    }

    public void unbind(long timeout) {
        synchronized (this) {
            if (!mBound) {
                throw new IllegalStateException("Not bound");
            }
            // This allows the service to go down.  We maintain the second binding to be
            // able to see the connection go away which is what we want to wait on.
            mContext.unbindService(mMainBinding);
            mBound = false;

            try {
                waitForDisconnect(timeout);
            } finally {
                stopMonitoring();
            }
        }
    }

    public void cleanup(long timeout) {
        synchronized (this) {
            if (mBound) {
                unbind(timeout);
            } else if (mMonitoring) {
                stopMonitoring();
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        synchronized (this) {
            mService = service;
            notifyAll();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        synchronized (this) {
            mService = null;
            notifyAll();
        }
    }

    @Override
    public void onBindingDied(ComponentName name) {
        synchronized (this) {
            // We want to remain connected to this service.
            if (mMonitoring) {
                Log.d(TAG, "Disconnected but monitoring, unbinding " + this + "...");
                mContext.unbindService(this);
                Log.d(TAG, "...and rebinding");
                mContext.bindService(mIntent, this, Context.BIND_WAIVE_PRIORITY);
            }
        }
    }
}

