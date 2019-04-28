/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.Log;
import android.text.TextUtils;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract class to perform the work of binding and unbinding to the specified service interface.
 * Subclasses supply the service intent and component name and this class will invoke protected
 * methods when the class is bound, unbound, or upon failure.
 */
abstract class ServiceBinder {

    /**
     * Callback to notify after a binding succeeds or fails.
     */
    interface BindCallback {
        void onSuccess();
        void onFailure();
    }

    /**
     * Listener for bind events on ServiceBinder.
     */
    interface Listener<ServiceBinderClass extends ServiceBinder> {
        void onUnbind(ServiceBinderClass serviceBinder);
    }

    /**
     * Helper class to perform on-demand binding.
     */
    final class Binder2 {
        /**
         * Performs an asynchronous bind to the service (only if not already bound) and executes the
         * specified callback.
         *
         * @param callback The callback to notify of the binding's success or failure.
         * @param call The call for which we are being bound.
         */
        void bind(BindCallback callback, Call call) {
            Log.d(ServiceBinder.this, "bind()");

            // Reset any abort request if we're asked to bind again.
            clearAbort();

            if (!mCallbacks.isEmpty()) {
                // Binding already in progress, append to the list of callbacks and bail out.
                mCallbacks.add(callback);
                return;
            }

            mCallbacks.add(callback);
            if (mServiceConnection == null) {
                Intent serviceIntent = new Intent(mServiceAction).setComponent(mComponentName);
                ServiceConnection connection = new ServiceBinderConnection(call);

                Log.addEvent(call, LogUtils.Events.BIND_CS, mComponentName);
                final int bindingFlags = Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE;
                final boolean isBound;
                if (mUserHandle != null) {
                    isBound = mContext.bindServiceAsUser(serviceIntent, connection, bindingFlags,
                            mUserHandle);
                } else {
                    isBound = mContext.bindService(serviceIntent, connection, bindingFlags);
                }
                if (!isBound) {
                    handleFailedConnection();
                    return;
                }
            } else {
                Log.d(ServiceBinder.this, "Service is already bound.");
                Preconditions.checkNotNull(mBinder);
                handleSuccessfulConnection();
            }
        }
    }

    private class ServiceDeathRecipient implements IBinder.DeathRecipient {

        private ComponentName mComponentName;

        ServiceDeathRecipient(ComponentName name) {
            mComponentName = name;
        }

        @Override
        public void binderDied() {
            try {
                synchronized (mLock) {
                    Log.startSession("SDR.bD");
                    Log.i(this, "binderDied: ConnectionService %s died.", mComponentName);
                    logServiceDisconnected("binderDied");
                    handleDisconnect();
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private final class ServiceBinderConnection implements ServiceConnection {
        /**
         * The initial call for which the service was bound.
         */
        private Call mCall;

        ServiceBinderConnection(Call call) {
            mCall = call;
        }

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            try {
                Log.startSession("SBC.oSC");
                synchronized (mLock) {
                    Log.i(this, "Service bound %s", componentName);

                    Log.addEvent(mCall, LogUtils.Events.CS_BOUND, componentName);
                    mCall = null;

                    // Unbind request was queued so unbind immediately.
                    if (mIsBindingAborted) {
                        clearAbort();
                        logServiceDisconnected("onServiceConnected");
                        mContext.unbindService(this);
                        handleFailedConnection();
                        return;
                    }
                    if (binder != null) {
                        mServiceDeathRecipient = new ServiceDeathRecipient(componentName);
                        try {
                            binder.linkToDeath(mServiceDeathRecipient, 0);
                            mServiceConnection = this;
                            setBinder(binder);
                            handleSuccessfulConnection();
                        } catch (RemoteException e) {
                            Log.w(this, "onServiceConnected: %s died.");
                            if (mServiceDeathRecipient != null) {
                                mServiceDeathRecipient.binderDied();
                            }
                        }
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            try {
                Log.startSession("SBC.oSD");
                synchronized (mLock) {
                    logServiceDisconnected("onServiceDisconnected");
                    handleDisconnect();
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private void handleDisconnect() {
        mServiceConnection = null;
        clearAbort();

        handleServiceDisconnected();
    }

    /** The application context. */
    private final Context mContext;

    /** The Telecom lock object. */
    protected final TelecomSystem.SyncRoot mLock;

    /** The intent action to use when binding through {@link Context#bindService}. */
    private final String mServiceAction;

    /** The component name of the service to bind to. */
    protected final ComponentName mComponentName;

    /** The set of callbacks waiting for notification of the binding's success or failure. */
    private final Set<BindCallback> mCallbacks = new ArraySet<>();

    /** Used to bind and unbind from the service. */
    private ServiceConnection mServiceConnection;

    /** Used to handle death of the service. */
    private ServiceDeathRecipient mServiceDeathRecipient;

    /** {@link UserHandle} to use for binding, to support work profiles and multi-user. */
    private UserHandle mUserHandle;

    /** The binder provided by {@link ServiceConnection#onServiceConnected} */
    private IBinder mBinder;

    private int mAssociatedCallCount = 0;

    /**
     * Indicates that an unbind request was made when the service was not yet bound. If the service
     * successfully connects when this is true, it should be unbound immediately.
     */
    private boolean mIsBindingAborted;

    /**
     * Set of currently registered listeners.
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

    /**
     * Persists the specified parameters and initializes the new instance.
     *
     * @param serviceAction The intent-action used with {@link Context#bindService}.
     * @param componentName The component name of the service with which to bind.
     * @param context The context.
     * @param userHandle The {@link UserHandle} to use for binding.
     */
    protected ServiceBinder(String serviceAction, ComponentName componentName, Context context,
            TelecomSystem.SyncRoot lock, UserHandle userHandle) {
        Preconditions.checkState(!TextUtils.isEmpty(serviceAction));
        Preconditions.checkNotNull(componentName);

        mContext = context;
        mLock = lock;
        mServiceAction = serviceAction;
        mComponentName = componentName;
        mUserHandle = userHandle;
    }

    final void incrementAssociatedCallCount() {
        mAssociatedCallCount++;
        Log.v(this, "Call count increment %d, %s", mAssociatedCallCount,
                mComponentName.flattenToShortString());
    }

    final void decrementAssociatedCallCount() {
        decrementAssociatedCallCount(false /*isSuppressingUnbind*/);
    }

    final void decrementAssociatedCallCount(boolean isSuppressingUnbind) {
        if (mAssociatedCallCount > 0) {
            mAssociatedCallCount--;
            Log.v(this, "Call count decrement %d, %s", mAssociatedCallCount,
                    mComponentName.flattenToShortString());

            if (!isSuppressingUnbind && mAssociatedCallCount == 0) {
                unbind();
            }
        } else {
            Log.wtf(this, "%s: ignoring a request to decrement mAssociatedCallCount below zero",
                    mComponentName.getClassName());
        }
    }

    final int getAssociatedCallCount() {
        return mAssociatedCallCount;
    }

    /**
     * Unbinds from the service if already bound, no-op otherwise.
     */
    final void unbind() {
        if (mServiceConnection == null) {
            // We're not yet bound, so queue up an abort request.
            mIsBindingAborted = true;
        } else {
            logServiceDisconnected("unbind");
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
            setBinder(null);
        }
    }

    final ComponentName getComponentName() {
        return mComponentName;
    }

    @VisibleForTesting
    public boolean isServiceValid(String actionName) {
        if (mBinder == null) {
            Log.w(this, "%s invoked while service is unbound", actionName);
            return false;
        }

        return true;
    }

    final void addListener(Listener listener) {
        mListeners.add(listener);
    }

    final void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * Logs a standard message upon service disconnection. This method exists because there is no
     * single method called whenever the service unbinds and we want to log the same string in all
     * instances where that occurs.  (Context.unbindService() does not cause onServiceDisconnected
     * to execute).
     *
     * @param sourceTag Tag to disambiguate
     */
    private void logServiceDisconnected(String sourceTag) {
        Log.i(this, "Service unbound %s, from %s.", mComponentName, sourceTag);
    }

    /**
     * Notifies all the outstanding callbacks that the service is successfully bound. The list of
     * outstanding callbacks is cleared afterwards.
     */
    private void handleSuccessfulConnection() {
        for (BindCallback callback : mCallbacks) {
            callback.onSuccess();
        }
        mCallbacks.clear();
    }

    /**
     * Notifies all the outstanding callbacks that the service failed to bind. The list of
     * outstanding callbacks is cleared afterwards.
     */
    private void handleFailedConnection() {
        for (BindCallback callback : mCallbacks) {
            callback.onFailure();
        }
        mCallbacks.clear();
    }

    /**
     * Handles a service disconnection.
     */
    private void handleServiceDisconnected() {
        setBinder(null);
    }

    private void clearAbort() {
        mIsBindingAborted = false;
    }

    /**
     * Sets the (private) binder and updates the child class.
     *
     * @param binder The new binder value.
     */
    private void setBinder(IBinder binder) {
        if (mBinder != binder) {
            if (binder == null) {
                removeServiceInterface();
                mBinder = null;
                for (Listener l : mListeners) {
                    l.onUnbind(this);
                }
            } else {
                mBinder = binder;
                setServiceInterface(binder);
            }
        }
    }

    /**
     * Sets the service interface after the service is bound.
     *
     * @param binder The new binder interface that is being set.
     */
    protected abstract void setServiceInterface(IBinder binder);

    /**
     * Removes the service interface before the service is unbound.
     */
    protected abstract void removeServiceInterface();
}
