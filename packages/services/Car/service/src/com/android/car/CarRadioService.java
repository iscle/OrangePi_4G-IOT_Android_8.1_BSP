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

import android.car.Car;
import android.car.hardware.radio.CarRadioEvent;
import android.car.hardware.radio.CarRadioPreset;
import android.car.hardware.radio.ICarRadio;
import android.car.hardware.radio.ICarRadioEventListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.hal.RadioHalService;

import java.io.PrintWriter;
import java.util.HashMap;

public class CarRadioService extends ICarRadio.Stub
        implements CarServiceBase, RadioHalService.RadioListener {
    public static boolean DBG = false;
    public static String TAG = CarLog.TAG_RADIO + ".CarRadioService";

    private RadioHalService mRadioHal;
    private final HashMap<IBinder, ICarRadioEventListener> mListenersMap =
      new HashMap<IBinder, ICarRadioEventListener>();
    private final HashMap<IBinder, RadioDeathRecipient> mDeathRecipientMap =
        new HashMap<IBinder, RadioDeathRecipient>();
    private final Context mContext;

    public CarRadioService(Context context, RadioHalService radioHal) {
        mRadioHal = radioHal;
        mContext = context;
    }

    class RadioDeathRecipient implements IBinder.DeathRecipient {
        private String TAG = CarRadioService.TAG + ".RadioDeathRecipient";
        private IBinder mListenerBinder;

        RadioDeathRecipient(IBinder listenerBinder) {
            mListenerBinder = listenerBinder;
        }

        /**
         * Client died. Remove the listener from HAL service and unregister if this is the last
         * client.
         */
        @Override
        public void binderDied() {
            if (DBG) {
                Log.d(TAG, "binderDied " + mListenerBinder);
            }
            mListenerBinder.unlinkToDeath(this, 0);
            CarRadioService.this.unregisterListenerLocked(mListenerBinder);
        }

        void release() {
            mListenerBinder.unlinkToDeath(this, 0);
        }
    }

    @Override
    public synchronized void init() {
    }

    @Override
    public synchronized void release() {
        for (IBinder listenerBinder : mListenersMap.keySet()) {
            RadioDeathRecipient deathRecipient = mDeathRecipientMap.get(listenerBinder);
            deathRecipient.release();
        }
        mDeathRecipientMap.clear();
        mListenersMap.clear();
    }

    @Override
    public void dump(PrintWriter writer) {
    }

    @Override
    public int getPresetCount() {
        return mRadioHal.getPresetCount();
    }

    @Override
    public synchronized void registerListener(ICarRadioEventListener listener) {
        if (DBG) {
            Log.d(TAG, "registerListener");
        }
        if (listener == null) {
            Log.e(TAG, "registerListener: Listener is null.");
            throw new IllegalStateException("listener cannot be null.");
        }

        IBinder listenerBinder = listener.asBinder();
        if (mListenersMap.containsKey(listenerBinder)) {
            // Already registered, nothing to do.
            return;
        }

        RadioDeathRecipient deathRecipient = new RadioDeathRecipient(listenerBinder);
        try {
            listenerBinder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to link death for recipient. " + e);
            throw new IllegalStateException(Car.CAR_NOT_CONNECTED_EXCEPTION_MSG);
        }
        mDeathRecipientMap.put(listenerBinder, deathRecipient);

        if (mListenersMap.isEmpty()) {
            mRadioHal.registerListener(this);
        }

        mListenersMap.put(listenerBinder, listener);
    }

    @Override
    public synchronized void unregisterListener(ICarRadioEventListener listener) {
        if (DBG) {
            Log.d(TAG, "unregisterListener");
        }

        if (listener == null) {
            Log.e(TAG, "unregisterListener: Listener is null.");
            throw new IllegalArgumentException("Listener is null");
        }

        IBinder listenerBinder = listener.asBinder();
        if (!mListenersMap.containsKey(listenerBinder)) {
            Log.e(TAG, "unregisterListener: Listener was not previously registered.");
        }
        unregisterListenerLocked(listenerBinder);
    }

    // Removes the listenerBinder from the current state.
    // The function assumes that the binder will exist both in listeners and death recipients list.
    private void unregisterListenerLocked(IBinder listenerBinder) {
        Object status = mListenersMap.remove(listenerBinder);
        if (status == null) throw new IllegalStateException(
            "Map must contain the event listener.");

        // If there is a state muck up, the release() call will throw an exception automagically.
        mDeathRecipientMap.get(listenerBinder).release();
        mDeathRecipientMap.remove(listenerBinder);

        if (mListenersMap.isEmpty()) {
            mRadioHal.unregisterListener();
        }
    }

    @Override
    public CarRadioPreset getPreset(int index) {
        if (DBG) {
            Log.d(TAG, "getPreset " + index);
        }
        return mRadioHal.getRadioPreset(index);
    }

    @Override
    public boolean setPreset(CarRadioPreset preset) {
        checkRadioPremissions();
        if (DBG) {
            Log.d(TAG, "setPreset " + preset);
        }
        boolean status = mRadioHal.setRadioPreset(preset);
        if (status == false) {
            Log.e(TAG, "setPreset failed!");
        }
        return status;
    }

    @Override
    public synchronized void onEvent(CarRadioEvent event) {
        for (ICarRadioEventListener l : mListenersMap.values()) {
            try {
                l.onEvent(event);
            } catch (RemoteException ex) {
                // If we could not send a record, its likely the connection snapped. Let the binder
                // death handle the situation.
                Log.e(TAG, "onEvent calling failed: " + ex);
            }
        }
    }

    private void checkRadioPremissions() {
        if (getCallingUid() != Process.SYSTEM_UID &&
            mContext.checkCallingOrSelfPermission(Car.PERMISSION_CAR_RADIO) !=
            PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires system app or " +
                Car.PERMISSION_CAR_RADIO);
        }
    }
}
