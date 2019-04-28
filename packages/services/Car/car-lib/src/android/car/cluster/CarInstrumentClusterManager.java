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

package android.car.cluster;

import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API to work with instrument cluster.
 *
 * @hide
 */
public class CarInstrumentClusterManager implements CarManagerBase {
    private static final String TAG = CarInstrumentClusterManager.class.getSimpleName();

    /** @hide */
    public static final String CATEGORY_NAVIGATION = "android.car.cluster.NAVIGATION";

    /**
     * When activity in the cluster is launched it will receive {@link ClusterActivityState} in the
     * intent's extra thus activity will know information about unobscured area, etc. upon activity
     * creation.
     *
     * @hide
     */
    public static final String KEY_EXTRA_ACTIVITY_STATE =
            "android.car.cluster.ClusterActivityState";

    private final EventHandler mHandler;
    private final Map<String, Set<Callback>> mCallbacksByCategory = new HashMap<>(0);
    private final Object mLock = new Object();
    private final Map<String, Bundle> mActivityStatesByCategory = new HashMap<>(0);

    private final IInstrumentClusterManagerService mService;

    private ClusterManagerCallback mServiceToManagerCallback;

    /**
     * Starts activity in the instrument cluster.
     *
     * @hide
     */
    public void startActivity(Intent intent) throws CarNotConnectedException {
        try {
            mService.startClusterActivity(intent);
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Caller of this method will receive immediate callback with the most recent state if state
     * exists for given category.
     *
     * @param category category of the activity in the cluster,
     *                         see {@link #CATEGORY_NAVIGATION}
     * @param callback instance of {@link Callback} class to receive events.
     *
     * @hide
     */
    public void registerCallback(String category, Callback callback)
            throws CarNotConnectedException {
        Log.i(TAG, "registerCallback, category: " + category + ", callback: " + callback);
        ClusterManagerCallback callbackToCarService = null;
        synchronized (mLock) {
            Set<Callback> callbacks = mCallbacksByCategory.get(category);
            if (callbacks == null) {
                callbacks = new HashSet<>(1);
                mCallbacksByCategory.put(category, callbacks);
            }
            if (!callbacks.add(callback)) {
                Log.w(TAG, "registerCallback: already registered");
                return;  // already registered
            }

            if (mActivityStatesByCategory.containsKey(category)) {
                Log.i(TAG, "registerCallback: sending activity state...");
                callback.onClusterActivityStateChanged(
                        category, mActivityStatesByCategory.get(category));
            }

            if (mServiceToManagerCallback == null) {
                Log.i(TAG, "registerCallback: registering callback with car service...");
                mServiceToManagerCallback = new ClusterManagerCallback();
                callbackToCarService = mServiceToManagerCallback;
            }
        }
        try {
            mService.registerCallback(callbackToCarService);
            Log.i(TAG, "registerCallback: done");
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Unregisters given callback for all activity categories.
     *
     * @param callback previously registered callback
     *
     * @hide
     */
    public void unregisterCallback(Callback callback) throws CarNotConnectedException {
        List<String> keysToRemove = new ArrayList<>(1);
        synchronized (mLock) {
            for (Map.Entry<String, Set<Callback>> entry : mCallbacksByCategory.entrySet()) {
                Set<Callback> callbacks = entry.getValue();
                if (callbacks.remove(callback) && callbacks.isEmpty()) {
                    keysToRemove.add(entry.getKey());
                }

            }

            for (String key: keysToRemove) {
                mCallbacksByCategory.remove(key);
            }

            if (mCallbacksByCategory.isEmpty()) {
                try {
                    mService.unregisterCallback(mServiceToManagerCallback);
                } catch (RemoteException e) {
                    throw new CarNotConnectedException(e);
                }
                mServiceToManagerCallback = null;
            }
        }
    }

    /** @hide */
    public CarInstrumentClusterManager(IBinder service, Handler handler) {
        mService = IInstrumentClusterManagerService.Stub.asInterface(service);

        mHandler = new EventHandler(handler.getLooper());
    }

    /** @hide */
    public interface Callback {

        /**
         * Notify client that activity state was changed.
         *
         * @param category cluster activity category, see {@link #CATEGORY_NAVIGATION}
         * @param clusterActivityState see {@link ClusterActivityState} how to read this bundle.
         */
        void onClusterActivityStateChanged(String category, Bundle clusterActivityState);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
    }

    private class EventHandler extends Handler {

        final static int MSG_ACTIVITY_STATE = 1;

        EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage, message: " + msg);
            switch (msg.what) {
                case MSG_ACTIVITY_STATE:
                    Pair<String, Bundle> info = (Pair<String, Bundle>) msg.obj;
                    String category = info.first;
                    Bundle state = info.second;
                    List<CarInstrumentClusterManager.Callback> callbacks = null;
                    synchronized (mLock) {
                        if (mCallbacksByCategory.containsKey(category)) {
                            callbacks = new ArrayList<>(mCallbacksByCategory.get(category));
                        }
                    }
                    Log.i(TAG, "handleMessage, callbacks: " + callbacks);
                    if (callbacks != null) {
                        for (CarInstrumentClusterManager.Callback cb : callbacks) {
                            cb.onClusterActivityStateChanged(category, state);
                        }
                    }
                    break;
                default:
                    Log.e(TAG, "Unexpected message: " + msg.what);
            }
        }
    }

    private class ClusterManagerCallback extends IInstrumentClusterManagerCallback.Stub {

        @Override
        public void setClusterActivityState(String category, Bundle clusterActivityState)
                throws RemoteException {
            Log.i(TAG, "setClusterActivityState, category: " + category);
            synchronized (mLock) {
                mActivityStatesByCategory.put(category, clusterActivityState);
            }

            mHandler.sendMessage(mHandler.obtainMessage(EventHandler.MSG_ACTIVITY_STATE,
                    new Pair<>(category, clusterActivityState)));
        }
    }
}