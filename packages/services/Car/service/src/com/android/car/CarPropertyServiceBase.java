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

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.hal.PropertyHalServiceBase;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the binder interface for ICarProperty.aidl to make it easier to create
 * multiple managers that deal with Vehicle Properties. To create a new service, simply extend
 * this class and call the super() constructor with the appropriate arguments for the new service.
 * {@link CarHvacService} shows the basic usage.
 */
public class CarPropertyServiceBase extends ICarProperty.Stub
        implements CarServiceBase, PropertyHalServiceBase.PropertyHalListener {
    private final Context mContext;
    private final boolean mDbg;
    private final Map<IBinder, PropertyDeathRecipient> mDeathRecipientMap =
            new ConcurrentHashMap<>();
    private final PropertyHalServiceBase mHal;
    private final Map<IBinder, ICarPropertyEventListener> mListenersMap = new ConcurrentHashMap<>();
    private final String mPermission;
    private final String mTag;

    private final Object mLock = new Object();

    public CarPropertyServiceBase(Context context, PropertyHalServiceBase hal, String permission,
            boolean dbg, String tag) {
        mContext = context;
        mHal = hal;
        mPermission = permission;
        mDbg = dbg;
        mTag = tag + ".service";
    }

    class PropertyDeathRecipient implements IBinder.DeathRecipient {
        private IBinder mListenerBinder;

        PropertyDeathRecipient(IBinder listenerBinder) {
            mListenerBinder = listenerBinder;
        }

        /**
         * Client died. Remove the listener from HAL service and unregister if this is the last
         * client.
         */
        @Override
        public void binderDied() {
            if (mDbg) {
                Log.d(mTag, "binderDied " + mListenerBinder);
            }
            CarPropertyServiceBase.this.unregisterListenerLocked(mListenerBinder);
        }

        void release() {
            mListenerBinder.unlinkToDeath(this, 0);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
        for (PropertyDeathRecipient recipient : mDeathRecipientMap.values()) {
            recipient.release();
        }
        mDeathRecipientMap.clear();
        mListenersMap.clear();
    }

    @Override
    public void dump(PrintWriter writer) {
    }

    @Override
    public void registerListener(ICarPropertyEventListener listener) {
        if (mDbg) {
            Log.d(mTag, "registerListener");
        }
        ICarImpl.assertPermission(mContext, mPermission);
        if (listener == null) {
            Log.e(mTag, "registerListener: Listener is null.");
            throw new IllegalArgumentException("listener cannot be null.");
        }

        IBinder listenerBinder = listener.asBinder();

        synchronized (mLock) {
            if (mListenersMap.containsKey(listenerBinder)) {
                // Already registered, nothing to do.
                return;
            }

            PropertyDeathRecipient deathRecipient = new PropertyDeathRecipient(listenerBinder);
            try {
                listenerBinder.linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(mTag, "Failed to link death for recipient. " + e);
                throw new IllegalStateException(Car.CAR_NOT_CONNECTED_EXCEPTION_MSG);
            }
            mDeathRecipientMap.put(listenerBinder, deathRecipient);

            if (mListenersMap.isEmpty()) {
                mHal.setListener(this);
            }

            mListenersMap.put(listenerBinder, listener);
        }
    }

    @Override
    public void unregisterListener(ICarPropertyEventListener listener) {
        if (mDbg) {
            Log.d(mTag, "unregisterListener");
        }
        ICarImpl.assertPermission(mContext, mPermission);
        if (listener == null) {
            Log.e(mTag, "unregisterListener: Listener is null.");
            throw new IllegalArgumentException("Listener is null");
        }

        IBinder listenerBinder = listener.asBinder();
        synchronized (mLock) {
            if (!mListenersMap.containsKey(listenerBinder)) {
                Log.e(mTag, "unregisterListener: Listener was not previously registered.");
            }
            unregisterListenerLocked(listenerBinder);
        }
    }

    // Removes the listenerBinder from the current state.
    // The function assumes that binder will exist both in listeners and death recipients list.
    private void unregisterListenerLocked(IBinder listenerBinder) {
        boolean found = mListenersMap.remove(listenerBinder) != null;

        if (found) {
            mDeathRecipientMap.get(listenerBinder).release();
            mDeathRecipientMap.remove(listenerBinder);
        }

        if (mListenersMap.isEmpty()) {
            mHal.setListener(null);
        }
    }

    @Override
    public List<CarPropertyConfig> getPropertyList() {
        ICarImpl.assertPermission(mContext, mPermission);
        return mHal.getPropertyList();
    }

    @Override
    public CarPropertyValue getProperty(int prop, int zone) {
        ICarImpl.assertPermission(mContext, mPermission);
        return mHal.getProperty(prop, zone);
    }

    @Override
    public void setProperty(CarPropertyValue prop) {
        ICarImpl.assertPermission(mContext, mPermission);
        mHal.setProperty(prop);
    }

    private ICarPropertyEventListener[] getListeners() {
        synchronized (mLock) {
            int size = mListenersMap.values().size();
            return mListenersMap.values().toArray(new ICarPropertyEventListener[size]);
        }
    }

    // Implement PropertyHalListener interface
    @Override
    public void onPropertyChange(CarPropertyEvent event) {
        for (ICarPropertyEventListener listener : getListeners()) {
            try {
                listener.onEvent(event);
            } catch (RemoteException ex) {
                // If we could not send a record, its likely the connection snapped. Let the binder
                // death handle the situation.
                Log.e(mTag, "onEvent calling failed: " + ex);
            }
        }
    }

    @Override
    public void onPropertySetError(int property, int area) {
        for (ICarPropertyEventListener listener : getListeners()) {
            try {
                listener.onEvent(createErrorEvent(property, area));
            } catch (RemoteException ex) {
                // If we could not send a record, its likely the connection snapped. Let the binder
                // death handle the situation.
                Log.e(mTag, "onEvent calling failed: " + ex);
            }
        }
    }

    private static CarPropertyEvent createErrorEvent(int property, int area) {
        return new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_ERROR,
                new CarPropertyValue<>(property, area, null));
    }
}
