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

package com.android.car;

import android.car.ICarUserService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;

/**
 * A Helper class that helps with the following:
 * 1. Provide methods to Bind/Unbind to the {@link PerUserCarService} as the current User
 * 2. Set up a listener to UserSwitch Broadcasts and call clients that have registered callbacks.
 *
 */
public class PerUserCarServiceHelper implements CarServiceBase {
    private static final String TAG = "PerUserCarSvcHelper";
    private static boolean DBG = false;
    private Context mContext;
    private ICarUserService mCarUserService;
    // listener to call on a ServiceConnection to PerUserCarService
    private List<ServiceCallback> mServiceCallbacks;
    private UserSwitchBroadcastReceiver mReceiver;
    private IntentFilter mUserSwitchFilter;
    private static final String EXTRA_USER_HANDLE = "android.intent.extra.user_handle";
    private final Object mServiceBindLock = new Object();
    @GuardedBy("mServiceBindLock")
    private boolean mBound = false;

    public PerUserCarServiceHelper(Context context) {
        mContext = context;
        mServiceCallbacks = new ArrayList<>();
        mReceiver = new UserSwitchBroadcastReceiver();
        setupUserSwitchListener();
    }

    @Override
    public synchronized void init() {
        bindToPerUserCarService();
    }

    @Override
    public synchronized void release() {
        unbindFromPerUserCarService();
    }

    /**
     * Setting up the intent filter for
     * 2. UserSwitch events
     */
    private void setupUserSwitchListener() {
        mUserSwitchFilter = new IntentFilter();
        mUserSwitchFilter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mReceiver, mUserSwitchFilter);
        if (DBG) {
            Log.d(TAG, "UserSwitch Listener Registered");
        }
    }

    /**
     * UserSwitchBroadcastReceiver receives broadcasts on User account switches.
     */
    public class UserSwitchBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ServiceCallback> callbacks;
            if (DBG) {
                Log.d(TAG, "User Switch Happened");
                boolean userSwitched = intent.getAction().equals(
                        Intent.ACTION_USER_SWITCHED);

                int user = intent.getExtras().getInt(EXTRA_USER_HANDLE);
                if (userSwitched) {
                    Log.d(TAG, "New User " + user);
                }
            }
            // Before unbinding, notify the callbacks about unbinding from the service
            // so the callbacks can clean up their state through the binder before the service is
            // killed.
            synchronized (this) {
                // copy the callbacks
                callbacks = new ArrayList<>(mServiceCallbacks);
            }
            // call them
            for (ServiceCallback callback : callbacks) {
                callback.onPreUnbind();
            }
            // unbind from the service running as the previous user.
            unbindFromPerUserCarService();
            // bind to the service running as the new user
            bindToPerUserCarService();
        }
    }

    /**
     * ServiceConnection to detect connecting/disconnecting to {@link PerUserCarService}
     */
    private final ServiceConnection mUserServiceConnection = new ServiceConnection() {
        // On connecting to the service, get the binder object to the CarBluetoothService
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            List<ServiceCallback> callbacks;
            if (DBG) {
                Log.d(TAG, "Connected to User Service");
            }
            mCarUserService = ICarUserService.Stub.asInterface(service);
            if (mCarUserService != null) {
                synchronized (this) {
                    // copy the callbacks
                    callbacks = new ArrayList<>(mServiceCallbacks);
                }
                // call them
                for (ServiceCallback callback : callbacks) {
                    callback.onServiceConnected(mCarUserService);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            List<ServiceCallback> callbacks;
            if (DBG) {
                Log.d(TAG, "Disconnected from User Service");
            }
            synchronized (this) {
                // copy the callbacks
                callbacks = new ArrayList<>(mServiceCallbacks);
            }
            // call them
            for (ServiceCallback callback : callbacks) {
                callback.onServiceDisconnected();
            }
        }
    };

    /**
     * Bind to the CarUserService {@link PerUserCarService} which is created to run as the Current
     * User.
     *
     */
    private void bindToPerUserCarService() {
        if (DBG) {
            Log.d(TAG, "Binding to User service");
        }
        Intent startIntent = new Intent(mContext, PerUserCarService.class);
        synchronized (mServiceBindLock) {
            mBound = true;
            boolean bindSuccess = mContext.bindServiceAsUser(startIntent, mUserServiceConnection,
                    mContext.BIND_AUTO_CREATE, UserHandle.CURRENT);
            // If valid connection not obtained, unbind
            if (!bindSuccess) {
                Log.e(TAG, "bindToPerUserCarService() failed to get valid connection");
                unbindFromPerUserCarService();
            }
        }
    }

    /**
     * Unbind from the {@link PerUserCarService} running as the Current user.
     */
    private void unbindFromPerUserCarService() {
        synchronized (mServiceBindLock) {
            // mBound flag makes sure we are unbinding only when the service is bound.
            if (mBound) {
                if (DBG) {
                    Log.d(TAG, "Unbinding from User Service");
                }
                mContext.unbindService(mUserServiceConnection);
                mBound = false;
            }
        }
    }

    /**
     * Register a listener that gets called on Connection state changes to the
     * {@link PerUserCarService}
     * @param listener - Callback to invoke on user switch event.
     */
    public void registerServiceCallback(ServiceCallback listener) {
        if (listener != null) {
            if (DBG) {
                Log.d(TAG, "Registering PerUserCarService Listener");
            }
            synchronized (this) {
                mServiceCallbacks.add(listener);
            }
        }
    }

    /**
     * Unregister the Service Listener
     * @param listener - Callback method to unregister
     */
    public void unregisterServiceCallback(ServiceCallback listener) {
        if (DBG) {
            Log.d(TAG, "Unregistering PerUserCarService Listener");
        }
        if (listener != null) {
            synchronized (this) {
                mServiceCallbacks.remove(listener);
            }
        }
    }

    /**
     * Listener to the PerUserCarService connection status that clients need to implement.
     */
    public interface ServiceCallback {
        // When Service Connects
        void onServiceConnected(ICarUserService carUserService);
        // Before an unbind call is going to be made.
        void onPreUnbind();
        // When Service crashed or disconnected
        void onServiceDisconnected();
    }

    @Override
    public synchronized void dump(PrintWriter writer) {

    }


}

