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

package com.android.bluetooth.gatt;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.IAdvertisingSetCallback;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manages Bluetooth LE advertising operations and interacts with bluedroid stack. TODO: add tests.
 *
 * @hide
 */
class AdvertiseManager {
    private static final boolean DBG = GattServiceConfig.DBG;
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "AdvertiseManager";

    private final GattService mService;
    private final AdapterService mAdapterService;
    private Handler mHandler;
    Map<IBinder, AdvertiserInfo> mAdvertisers = Collections.synchronizedMap(new HashMap<>());
    static int sTempRegistrationId = -1;

    /**
     * Constructor of {@link AdvertiseManager}.
     */
    AdvertiseManager(GattService service, AdapterService adapterService) {
        if (DBG) Log.d(TAG, "advertise manager created");
        mService = service;
        mAdapterService = adapterService;
    }

    /**
     * Start a {@link HandlerThread} that handles advertising operations.
     */
    void start() {
        initializeNative();
        HandlerThread thread = new HandlerThread("BluetoothAdvertiseManager");
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    void cleanup() {
        if (DBG) Log.d(TAG, "cleanup()");
        cleanupNative();
        mAdvertisers.clear();
        sTempRegistrationId = -1;

        if (mHandler != null) {
            // Shut down the thread
            mHandler.removeCallbacksAndMessages(null);
            Looper looper = mHandler.getLooper();
            if (looper != null) {
                looper.quit();
            }
            mHandler = null;
        }
    }

    class AdvertiserInfo {
        /* When id is negative, the registration is ongoing. When the registration finishes, id
         * becomes equal to advertiser_id */
        public Integer id;
        public AdvertisingSetDeathRecipient deathRecipient;
        public IAdvertisingSetCallback callback;

        AdvertiserInfo(Integer id, AdvertisingSetDeathRecipient deathRecipient,
                IAdvertisingSetCallback callback) {
            this.id = id;
            this.deathRecipient = deathRecipient;
            this.callback = callback;
        }
    }

    IBinder toBinder(IAdvertisingSetCallback e) {
        return ((IInterface) e).asBinder();
    }

    class AdvertisingSetDeathRecipient implements IBinder.DeathRecipient {
        IAdvertisingSetCallback callback;

        public AdvertisingSetDeathRecipient(IAdvertisingSetCallback callback) {
            this.callback = callback;
        }

        @Override
        public void binderDied() {
            if (DBG) Log.d(TAG, "Binder is dead - unregistering advertising set");
            stopAdvertisingSet(callback);
        }
    }

    Map.Entry<IBinder, AdvertiserInfo> findAdvertiser(int advertiser_id) {
        Map.Entry<IBinder, AdvertiserInfo> entry = null;
        for (Map.Entry<IBinder, AdvertiserInfo> e : mAdvertisers.entrySet()) {
            if (e.getValue().id == advertiser_id) {
                entry = e;
                break;
            }
        }
        return entry;
    }

    void onAdvertisingSetStarted(int reg_id, int advertiser_id, int tx_power, int status)
            throws Exception {
        if (DBG) {
            Log.d(TAG, "onAdvertisingSetStarted() - reg_id=" + reg_id + ", advertiser_id="
                            + advertiser_id + ", status=" + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(reg_id);

        if (entry == null) {
            Log.i(TAG, "onAdvertisingSetStarted() - no callback found for reg_id " + reg_id);
            // Advertising set was stopped before it was properly registered.
            stopAdvertisingSetNative(advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        if (status == 0) {
            entry.setValue(
                    new AdvertiserInfo(advertiser_id, entry.getValue().deathRecipient, callback));
        } else {
            IBinder binder = entry.getKey();
            binder.unlinkToDeath(entry.getValue().deathRecipient, 0);
            mAdvertisers.remove(binder);
        }

        callback.onAdvertisingSetStarted(advertiser_id, tx_power, status);
    }

    void onAdvertisingEnabled(int advertiser_id, boolean enable, int status) throws Exception {
        if (DBG) {
            Log.d(TAG, "onAdvertisingSetEnabled() - advertiser_id=" + advertiser_id + ", enable="
                            + enable + ", status=" + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onAdvertisingSetEnable() - no callback found for advertiser_id "
                            + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onAdvertisingEnabled(advertiser_id, enable, status);
    }

    void startAdvertisingSet(AdvertisingSetParameters parameters, AdvertiseData advertiseData,
            AdvertiseData scanResponse, PeriodicAdvertisingParameters periodicParameters,
            AdvertiseData periodicData, int duration, int maxExtAdvEvents,
            IAdvertisingSetCallback callback) {
        AdvertisingSetDeathRecipient deathRecipient = new AdvertisingSetDeathRecipient(callback);
        IBinder binder = toBinder(callback);
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            throw new IllegalArgumentException("Can't link to advertiser's death");
        }

        String deviceName = AdapterService.getAdapterService().getName();
        byte[] adv_data = AdvertiseHelper.advertiseDataToBytes(advertiseData, deviceName);
        byte[] scan_response = AdvertiseHelper.advertiseDataToBytes(scanResponse, deviceName);
        byte[] periodic_data = AdvertiseHelper.advertiseDataToBytes(periodicData, deviceName);

        int cb_id = --sTempRegistrationId;
        mAdvertisers.put(binder, new AdvertiserInfo(cb_id, deathRecipient, callback));

        if (DBG) Log.d(TAG, "startAdvertisingSet() - reg_id=" + cb_id + ", callback: " + binder);
        startAdvertisingSetNative(parameters, adv_data, scan_response, periodicParameters,
                periodic_data, duration, maxExtAdvEvents, cb_id);
    }

    void onOwnAddressRead(int advertiser_id, int addressType, String address)
            throws RemoteException {
        if (DBG) Log.d(TAG, "onOwnAddressRead() advertiser_id=" + advertiser_id);

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onOwnAddressRead() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onOwnAddressRead(advertiser_id, addressType, address);
    }

    void getOwnAddress(int advertiserId) {
        getOwnAddressNative(advertiserId);
    }

    void stopAdvertisingSet(IAdvertisingSetCallback callback) {
        IBinder binder = toBinder(callback);
        if (DBG) Log.d(TAG, "stopAdvertisingSet() " + binder);

        AdvertiserInfo adv = mAdvertisers.remove(binder);
        if (adv == null) {
            Log.e(TAG, "stopAdvertisingSet() - no client found for callback");
            return;
        }

        Integer advertiser_id = adv.id;
        binder.unlinkToDeath(adv.deathRecipient, 0);

        if (advertiser_id < 0) {
            Log.i(TAG, "stopAdvertisingSet() - advertiser not finished registration yet");
            // Advertiser will be freed once initiated in onAdvertisingSetStarted()
            return;
        }

        stopAdvertisingSetNative(advertiser_id);

        try {
            callback.onAdvertisingSetStopped(advertiser_id);
        } catch (RemoteException e) {
            Log.i(TAG, "error sending onAdvertisingSetStopped callback", e);
        }
    }

    void enableAdvertisingSet(int advertiserId, boolean enable, int duration, int maxExtAdvEvents) {
        enableAdvertisingSetNative(advertiserId, enable, duration, maxExtAdvEvents);
    }

    void setAdvertisingData(int advertiserId, AdvertiseData data) {
        String deviceName = AdapterService.getAdapterService().getName();
        setAdvertisingDataNative(
                advertiserId, AdvertiseHelper.advertiseDataToBytes(data, deviceName));
    }

    void setScanResponseData(int advertiserId, AdvertiseData data) {
        String deviceName = AdapterService.getAdapterService().getName();
        setScanResponseDataNative(
                advertiserId, AdvertiseHelper.advertiseDataToBytes(data, deviceName));
    }

    void setAdvertisingParameters(int advertiserId, AdvertisingSetParameters parameters) {
        setAdvertisingParametersNative(advertiserId, parameters);
    }

    void setPeriodicAdvertisingParameters(
            int advertiserId, PeriodicAdvertisingParameters parameters) {
        setPeriodicAdvertisingParametersNative(advertiserId, parameters);
    }

    void setPeriodicAdvertisingData(int advertiserId, AdvertiseData data) {
        String deviceName = AdapterService.getAdapterService().getName();
        setPeriodicAdvertisingDataNative(
                advertiserId, AdvertiseHelper.advertiseDataToBytes(data, deviceName));
    }

    void setPeriodicAdvertisingEnable(int advertiserId, boolean enable) {
        setPeriodicAdvertisingEnableNative(advertiserId, enable);
    }

    void onAdvertisingDataSet(int advertiser_id, int status) throws Exception {
        if (DBG) {
            Log.d(TAG,
                    "onAdvertisingDataSet() advertiser_id=" + advertiser_id + ", status=" + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onAdvertisingDataSet() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onAdvertisingDataSet(advertiser_id, status);
    }

    void onScanResponseDataSet(int advertiser_id, int status) throws Exception {
        if (DBG)
            Log.d(TAG, "onScanResponseDataSet() advertiser_id=" + advertiser_id + ", status="
                            + status);

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onScanResponseDataSet() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onScanResponseDataSet(advertiser_id, status);
    }

    void onAdvertisingParametersUpdated(int advertiser_id, int tx_power, int status)
            throws Exception {
        if (DBG) {
            Log.d(TAG, "onAdvertisingParametersUpdated() advertiser_id=" + advertiser_id
                            + ", tx_power=" + tx_power + ", status=" + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onAdvertisingParametersUpdated() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onAdvertisingParametersUpdated(advertiser_id, tx_power, status);
    }

    void onPeriodicAdvertisingParametersUpdated(int advertiser_id, int status) throws Exception {
        if (DBG) {
            Log.d(TAG, "onPeriodicAdvertisingParametersUpdated() advertiser_id=" + advertiser_id
                            + ", status=" + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onPeriodicAdvertisingParametersUpdated() - bad advertiser_id "
                            + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onPeriodicAdvertisingParametersUpdated(advertiser_id, status);
    }

    void onPeriodicAdvertisingDataSet(int advertiser_id, int status) throws Exception {
        if (DBG) {
            Log.d(TAG, "onPeriodicAdvertisingDataSet() advertiser_id=" + advertiser_id + ", status="
                            + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onPeriodicAdvertisingDataSet() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onPeriodicAdvertisingDataSet(advertiser_id, status);
    }

    void onPeriodicAdvertisingEnabled(int advertiser_id, boolean enable, int status)
            throws Exception {
        if (DBG) {
            Log.d(TAG, "onPeriodicAdvertisingEnabled() advertiser_id=" + advertiser_id + ", status="
                            + status);
        }

        Map.Entry<IBinder, AdvertiserInfo> entry = findAdvertiser(advertiser_id);
        if (entry == null) {
            Log.i(TAG, "onAdvertisingSetEnable() - bad advertiser_id " + advertiser_id);
            return;
        }

        IAdvertisingSetCallback callback = entry.getValue().callback;
        callback.onPeriodicAdvertisingEnabled(advertiser_id, enable, status);
    }

    static {
        classInitNative();
    }

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native void startAdvertisingSetNative(AdvertisingSetParameters parameters,
            byte[] advertiseData, byte[] scanResponse,
            PeriodicAdvertisingParameters periodicParameters, byte[] periodicData, int duration,
            int maxExtAdvEvents, int reg_id);
    private native void getOwnAddressNative(int advertiserId);
    private native void stopAdvertisingSetNative(int advertiser_id);
    private native void enableAdvertisingSetNative(
            int advertiserId, boolean enable, int duration, int maxExtAdvEvents);
    private native void setAdvertisingDataNative(int advertiserId, byte[] data);
    private native void setScanResponseDataNative(int advertiserId, byte[] data);
    private native void setAdvertisingParametersNative(
            int advertiserId, AdvertisingSetParameters parameters);
    private native void setPeriodicAdvertisingParametersNative(
            int advertiserId, PeriodicAdvertisingParameters parameters);
    private native void setPeriodicAdvertisingDataNative(int advertiserId, byte[] data);
    private native void setPeriodicAdvertisingEnableNative(int advertiserId, boolean enable);
}
