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

package android.car.hardware.radio;

import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.hardware.radio.RadioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.ref.WeakReference;

/**
 * Car Radio manager.
 *
 * This API works in conjunction with the {@link RadioManager.java} and provides
 * features additional to the ones provided in there. It supports:
 *
 * 1. Capability to control presets.
 * @hide
 */
@SystemApi
public final class CarRadioManager implements CarManagerBase {
    private final static boolean DBG = false;
    private final static String TAG = "CarRadioManager";

    // Constants handled in the handler (see mHandler below).
    private final static int MSG_RADIO_EVENT = 0;

    private int mCount = 0;
    private final ICarRadio mService;
    @GuardedBy("this")
    private CarRadioEventListener mListener = null;
    @GuardedBy("this")
    private CarRadioEventListenerToService mListenerToService = null;
    private static final class EventCallbackHandler extends Handler {
        WeakReference<CarRadioManager> mMgr;

        EventCallbackHandler(CarRadioManager mgr, Looper looper) {
            super(looper);
            mMgr = new WeakReference<CarRadioManager>(mgr);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RADIO_EVENT:
                    CarRadioManager mgr = mMgr.get();
                    if (mgr != null) {
                        mgr.dispatchEventToClient((CarRadioEvent) msg.obj);
                    }
                    break;
                default:
                    Log.e(TAG, "Event type not handled?" + msg);
            }
        }
    }

    private final Handler mHandler;

    private static class CarRadioEventListenerToService extends ICarRadioEventListener.Stub {
        private final WeakReference<CarRadioManager> mManager;

        public CarRadioEventListenerToService(CarRadioManager manager) {
            mManager = new WeakReference<CarRadioManager>(manager);
        }

        @Override
        public void onEvent(CarRadioEvent event) {
            CarRadioManager manager = mManager.get();
            if (manager != null) {
                manager.handleEvent(event);
            }
        }
    }


    /** Listener for car radio events.
     */
    public interface CarRadioEventListener {
        /**
         * Called when there is a preset value is reprogrammed.
         */
        void onEvent(final CarRadioEvent event);
    }

    /**
     * Get an instance of the CarRadioManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @hide
     */
    public CarRadioManager(IBinder service, Handler handler) throws CarNotConnectedException {
        mService = ICarRadio.Stub.asInterface(service);
        mHandler = new EventCallbackHandler(this, handler.getLooper());

        // Populate the fixed values.
        try {
            mCount = mService.getPresetCount();
        } catch (RemoteException ex) {
            Log.e(TAG, "Could not connect: " + ex.toString());
            throw new CarNotConnectedException(ex);
        }
    }

    /**
     * Register {@link CarRadioEventListener} to get radio unit changes.
     */
    public synchronized void registerListener(CarRadioEventListener listener)
            throws CarNotConnectedException {
        if (mListener != null) {
            throw new IllegalStateException("Listener already registered. Did you call " +
                "registerListener() twice?");
        }

        mListener = listener;
        try {
            mListenerToService = new CarRadioEventListenerToService(this);
            mService.registerListener(mListenerToService);
        } catch (RemoteException ex) {
            // Do nothing.
            Log.e(TAG, "Could not connect: " + ex.toString());
            throw new CarNotConnectedException(ex);
        } catch (IllegalStateException ex) {
            Car.checkCarNotConnectedExceptionFromCarService(ex);
        }
    }

    /**
     * Unregister {@link CarRadioEventListener}.
     */
    public synchronized void unregisterListener() {
        if (DBG) {
            Log.d(TAG, "unregisterListener");
        }
        try {
            mService.unregisterListener(mListenerToService);
        } catch (RemoteException ex) {
            Log.e(TAG, "Could not connect: " + ex.toString());
            //ignore
        }
        mListenerToService = null;
        mListener = null;
    }

    /**
     * Get the number of (hard) presets supported by car radio unit.
     *
     * @return: A positive value if the call succeeded, -1 if it failed.
     */
    public int getPresetCount() throws CarNotConnectedException {
        return mCount;
    }

    /**
     * Get preset value for a specific radio preset.
     * @return: a {@link CarRadioPreset} object, {@link null} if the call failed.
     */
    public CarRadioPreset getPreset(int presetNumber) throws CarNotConnectedException {
        if (DBG) {
            Log.d(TAG, "getPreset");
        }
        try {
            CarRadioPreset preset = mService.getPreset(presetNumber);
            return preset;
        } catch (RemoteException ex) {
            Log.e(TAG, "getPreset failed with " + ex.toString());
            throw new CarNotConnectedException(ex);
        }
    }

    /**
     * Set the preset value to a specific radio preset.
     *
     * In order to ensure that the preset value indeed get updated, wait for event on the listener
     * registered via registerListener().
     *
     * @return: {@link boolean} value which returns true if the request succeeded and false
     * otherwise. Common reasons for the failure could be:
     * a) Preset is invalid (the preset number is out of range from {@link getPresetCount()}.
     * b) Listener is not set correctly, since otherwise the user of this API cannot confirm if the
     * request succeeded.
     */
    public boolean setPreset(CarRadioPreset preset) throws IllegalArgumentException,
            CarNotConnectedException {
        try {
            return mService.setPreset(preset);
        } catch (RemoteException ex) {
            throw new CarNotConnectedException(ex);
         }
    }

    private void dispatchEventToClient(CarRadioEvent event) {
        CarRadioEventListener listener;
        synchronized (this) {
            listener = mListener;
        }
        if (listener != null) {
            listener.onEvent(event);
        } else {
            Log.e(TAG, "Listener died, not dispatching event.");
        }
    }

    private void handleEvent(CarRadioEvent event) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_RADIO_EVENT, event));
    }

    /** @hide */
    @Override
    public synchronized void onCarDisconnected() {
        mListener = null;
        mListenerToService = null;
    }
}
