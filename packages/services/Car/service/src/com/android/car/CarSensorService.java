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
import android.car.hardware.CarSensorConfig;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.ICarSensor;
import android.car.hardware.ICarSensorEventListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import static com.android.car.Listeners.ClientWithRate;
import com.android.car.hal.SensorBase;
import com.android.car.hal.SensorHalService.SensorListener;
import com.google.android.collect.Lists;

import com.android.car.hal.SensorHalService;
import com.android.car.hal.SensorHalServiceBase;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;


public class CarSensorService extends ICarSensor.Stub
        implements CarServiceBase, SensorHalService.SensorListener {

    /**
     * Abstraction for logical sensor which is not physical sensor but presented as sensor to
     * upper layer. Currently {@link CarSensorManager#SENSOR_TYPE_NIGHT} and
     * {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS} falls into this category.
     * Implementation can call {@link CarSensorService#onSensorData(CarSensorEvent)} when there
     * is state change for the given sensor after {@link SensorHalServiceBase#init()}
     * is called.
     */
    public static abstract class LogicalSensor implements SensorBase {
        private final LinkedList<CarSensorEvent> mDispatchQ = new LinkedList<>();

        /** Sensor service is ready and all vehicle sensors are available. */
        public abstract void onSensorServiceReady();

        /**
         * Utility to help service to send one event as listener only takes list form.
         * @param listener
         * @param event
         */
        protected void dispatchCarSensorEvent(SensorListener listener, CarSensorEvent event) {
            synchronized (mDispatchQ) {
                mDispatchQ.add(event);
                listener.onSensorEvents(mDispatchQ);
                mDispatchQ.clear();
            }
        }
    }

    /**
     * When set, sensor service sets its own dispatching rate limit.
     * VehicleNetworkService is already doing this, so not necessary to set it for now.
     */
    private static final boolean ENABLE_DISPATCHING_LIMIT = false;

    /** {@link #mSensorLock} is not waited forever for handling disconnection */
    private static final long MAX_SENSOR_LOCK_WAIT_MS = 1000;

    /** lock to access sensor structures */
    private final ReentrantLock mSensorLock = new ReentrantLock();
    /** hold clients callback  */
    @GuardedBy("mSensorLock")
    private final LinkedList<SensorClient> mClients = new LinkedList<>();

    /** key: sensor type. */
    @GuardedBy("mSensorLock")
    private final SparseArray<Listeners<SensorClient>> mSensorListeners = new SparseArray<>();
    /** key: sensor type. */
    @GuardedBy("mSensorLock")
    private final SparseArray<SensorRecord> mSensorRecords = new SparseArray<>();

    private final SensorHalService mSensorHal;
    private int[] mCarProvidedSensors;
    private int[] mSupportedSensors;
    private final AtomicBoolean mSensorDiscovered = new AtomicBoolean(false);

    private final Context mContext;

    private final DrivingStatePolicy mDrivingStatePolicy;
    private boolean mUseDefaultDrivingPolicy = true;
    private final DayNightModePolicy mDayNightModePolicy;
    private boolean mUseDefaultDayNightModePolicy = true;

    private final HandlerThread mHandlerThread;
    private final SensorDispatchHandler mSensorDispatchHandler;

    public CarSensorService(Context context, SensorHalService sensorHal) {
        mContext = context;
        if (ENABLE_DISPATCHING_LIMIT) {
            mHandlerThread = new HandlerThread("SENSOR", Process.THREAD_PRIORITY_AUDIO);
            mHandlerThread.start();
            mSensorDispatchHandler = new SensorDispatchHandler(mHandlerThread.getLooper());
        } else {
            mHandlerThread = null;
            mSensorDispatchHandler = null;
        }
        // This triggers sensor hal init as well.
        mSensorHal = sensorHal;
        mDrivingStatePolicy = new DrivingStatePolicy(context, this);
        mDayNightModePolicy = new DayNightModePolicy(context);
    }

    @Override
    public void init() {
        mSensorLock.lock();
        try {
            mSensorHal.registerSensorListener(this);
            mCarProvidedSensors = mSensorHal.getSupportedSensors();
            mSupportedSensors = refreshSupportedSensorsLocked();

            addNewSensorRecordLocked(CarSensorManager.SENSOR_TYPE_DRIVING_STATUS,
                    getInitialDrivingStatus());
            addNewSensorRecordLocked(CarSensorManager.SENSOR_TYPE_NIGHT, getInitialNightMode());
            addNewSensorRecordLocked(CarSensorManager.SENSOR_TYPE_IGNITION_STATE,
                getInitialIgnitionState());

            notifyDefaultPoliciesLocked();
        } finally {
            mSensorLock.unlock();
        }
    }

    private CarSensorEvent getInitialIgnitionState() {
        return mSensorHal.getCurrentSensorValue(CarSensorManager.SENSOR_TYPE_IGNITION_STATE);
    }

    private CarSensorEvent getInitialNightMode() {
        CarSensorEvent event = null;
        if (mUseDefaultDayNightModePolicy) {
            mDayNightModePolicy.init();
            mDayNightModePolicy.registerSensorListener(this);
        } else {
            event = mSensorHal.getCurrentSensorValue(CarSensorManager.SENSOR_TYPE_NIGHT);
            Log.i(CarLog.TAG_SENSOR, "initial daynight: "
                    + ((event == null) ? "not ready" : + event.intValues[0]));
        }
        if (event == null) {
            event = DayNightModePolicy.getDefaultValue(CarSensorManager.SENSOR_TYPE_NIGHT);
            if (!mUseDefaultDayNightModePolicy) {
                Log.w(CarLog.TAG_SENSOR, "Default daynight set as sensor not ready");
            }
        }
        return event;
    }

    private CarSensorEvent getInitialDrivingStatus() {
        CarSensorEvent event = null;
        if (mUseDefaultDrivingPolicy) {
            mDrivingStatePolicy.init();
            mDrivingStatePolicy.registerSensorListener(this);
        } else {
            event = mSensorHal.getCurrentSensorValue(
                    CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
            Log.i(CarLog.TAG_SENSOR, "initial driving status:" + ((event == null)?
                    "not ready" : " 0x" + Integer.toHexString(event.intValues[0])));
        }
        if (event == null) {
            event = DrivingStatePolicy.getDefaultValue(
                    CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
            if (!mUseDefaultDrivingPolicy) {
                Log.w(CarLog.TAG_SENSOR, "Default driving status set as sensor not ready");
            }
        }
        return event;
    }

    private void addNewSensorRecordLocked(int type, CarSensorEvent event) {
        SensorRecord record = new SensorRecord();
        record.lastEvent = event;
        mSensorRecords.put(type,record);
    }

    @Override
    public void release() {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        tryHoldSensorLock();
        try {
            if (mUseDefaultDrivingPolicy) {
                mDrivingStatePolicy.release();
            }
            if (mUseDefaultDayNightModePolicy) {
                mDayNightModePolicy.release();
            }
            for (int i = mSensorListeners.size() - 1; i >= 0; --i) {
                Listeners listener = mSensorListeners.valueAt(i);
                listener.release();
            }
            mSensorListeners.clear();
            mSensorRecords.clear();
            mClients.clear();
        } finally {
            releaseSensorLockSafely();
        }
    }

    private void tryHoldSensorLock() {
        try {
            mSensorLock.tryLock(MAX_SENSOR_LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //ignore
        }
    }

    private void releaseSensorLockSafely() {
        if (mSensorLock.isHeldByCurrentThread()) {
            mSensorLock.unlock();
        }
    }

    private void notifyDefaultPoliciesLocked() {
        if (mUseDefaultDrivingPolicy) {
            mDrivingStatePolicy.onSensorServiceReady();
        }
        if (mUseDefaultDayNightModePolicy) {
            mDayNightModePolicy.onSensorServiceReady();
        }
    }

    private void processSensorData(List<CarSensorEvent> events) {
        ArrayMap<SensorClient, List<CarSensorEvent>> eventsByClient = new ArrayMap<>();

        mSensorLock.lock();
        for (CarSensorEvent event: events) {
            SensorRecord record = mSensorRecords.get(event.sensorType);
            if (record != null) {
                if (record.lastEvent == null) {
                    record.lastEvent = event;
                } else if (record.lastEvent.timestamp < event.timestamp) {
                    record.lastEvent = event;
                    //TODO recycle event, bug: 32094595
                } else { // wrong timestamp, throw away this.
                    //TODO recycle new event, bug: 32094595
                    continue;
                }

                Listeners<SensorClient> listeners = mSensorListeners.get(event.sensorType);
                if (listeners == null) {
                    continue;
                }

                for (ClientWithRate<SensorClient> clientWithRate : listeners.getClients()) {
                    SensorClient client = clientWithRate.getClient();
                    List<CarSensorEvent> clientEvents = eventsByClient.get(client);
                    if (clientEvents == null) {
                        clientEvents = new LinkedList<>();
                        eventsByClient.put(client, clientEvents);
                    }
                    clientEvents.add(event);
                }
            }
        }
        mSensorLock.unlock();

        for (ArrayMap.Entry<SensorClient, List<CarSensorEvent>> entry : eventsByClient.entrySet()) {
            SensorClient client = entry.getKey();
            List<CarSensorEvent> clientEvents = entry.getValue();

            client.dispatchSensorUpdate(clientEvents);
        }
    }

    /**
     * Received sensor data from car.
     */
    @Override
    public void onSensorEvents(List<CarSensorEvent> events) {
        if (ENABLE_DISPATCHING_LIMIT) {
            mSensorDispatchHandler.handleSensorEvents(events);
        } else {
            processSensorData(events);
        }
    }

    @Override
    public int[] getSupportedSensors() {
        mSensorLock.lock();
        int[] supportedSensors = mSupportedSensors;
        mSensorLock.unlock();
        return supportedSensors;
    }

    @Override
    public boolean registerOrUpdateSensorListener(int sensorType, int rate,
            ICarSensorEventListener listener) {
        boolean shouldStartSensors = false;
        SensorRecord sensorRecord = null;
        SensorClient sensorClient = null;
        Integer oldRate = null;
        Listeners<SensorClient> sensorListeners = null;
        mSensorLock.lock();
        try {
            sensorRecord = mSensorRecords.get(sensorType);
            if (sensorRecord == null) {
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.INFO)) {
                    Log.i(CarLog.TAG_SENSOR, "Requested sensor " + sensorType + " not supported");
                }
                return false;
            }
            if (Binder.getCallingUid() != Process.myUid()) {
                switch (getSensorPermission(sensorType)) {
                    case PackageManager.PERMISSION_DENIED:
                        throw new SecurityException("client does not have permission:"
                                + getPermissionName(sensorType)
                                + " pid:" + Binder.getCallingPid()
                                + " uid:" + Binder.getCallingUid());
                    case PackageManager.PERMISSION_GRANTED:
                        break;
                }
            }
            if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                Log.d(CarLog.TAG_SENSOR, "registerOrUpdateSensorListener " + sensorType + " " +
                        listener);
            }
            sensorClient = findSensorClientLocked(listener);
            ClientWithRate<SensorClient> sensorClientWithRate = null;
            sensorListeners = mSensorListeners.get(sensorType);
            if (sensorClient == null) {
                sensorClient = new SensorClient(listener);
                try {
                    listener.asBinder().linkToDeath(sensorClient, 0);
                } catch (RemoteException e) {
                    if (Log.isLoggable(CarLog.TAG_SENSOR, Log.INFO)) {
                        Log.i(CarLog.TAG_SENSOR, "Adding listener failed.");
                    }
                    return false;
                }
                mClients.add(sensorClient);
            }
            // If we have a cached event for this sensor, send the event.
            SensorRecord record = mSensorRecords.get(sensorType);
            if (record != null && record.lastEvent != null) {
                sensorClient.dispatchSensorUpdate(Lists.newArrayList(record.lastEvent));
            }
            if (sensorListeners == null) {
                sensorListeners = new Listeners<>(rate);
                mSensorListeners.put(sensorType, sensorListeners);
                shouldStartSensors = true;
            } else {
                oldRate = sensorListeners.getRate();
                sensorClientWithRate = sensorListeners.findClientWithRate(sensorClient);
            }
            if (sensorClientWithRate == null) {
                sensorClientWithRate = new ClientWithRate<>(sensorClient, rate);
                sensorListeners.addClientWithRate(sensorClientWithRate);
            } else {
                sensorClientWithRate.setRate(rate);
            }
            if (sensorListeners.getRate() > rate) {
                sensorListeners.setRate(rate);
                shouldStartSensors = sensorSupportRate(sensorType);
            }
            sensorClient.addSensor(sensorType);
        } finally {
            mSensorLock.unlock();
        }
        // start sensor outside lock as it can take time.
        if (shouldStartSensors) {
            if (!startSensor(sensorRecord, sensorType, rate)) {
                // failed. so remove from active sensor list.
                mSensorLock.lock();
                try {
                    sensorClient.removeSensor(sensorType);
                    if (oldRate != null) {
                        sensorListeners.setRate(oldRate);
                    } else {
                        mSensorListeners.remove(sensorType);
                    }
                } finally {
                    mSensorLock.unlock();
                }
                return false;
            }
        }
        return true;
    }

    private boolean sensorSupportRate(int sensorType) {
        switch (sensorType) {
            case CarSensorManager.SENSOR_TYPE_CAR_SPEED:
            case CarSensorManager.SENSOR_TYPE_RPM:
            case CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE:
                return true;
            case CarSensorManager.SENSOR_TYPE_ODOMETER:
            case CarSensorManager.SENSOR_TYPE_FUEL_LEVEL:
            case CarSensorManager.SENSOR_TYPE_PARKING_BRAKE:
            case CarSensorManager.SENSOR_TYPE_GEAR:
            case CarSensorManager.SENSOR_TYPE_NIGHT:
            case CarSensorManager.SENSOR_TYPE_DRIVING_STATUS:
            case CarSensorManager.SENSOR_TYPE_ENVIRONMENT:
                return false;
            default:
                Log.w(CarLog.TAG_SENSOR, "sensorSupportRate not listed sensor:" + sensorType);
                return false;
        }
    }

    private int getSensorPermission(int sensorType) {
        String permission = getPermissionName(sensorType);
        int result = PackageManager.PERMISSION_GRANTED;
        if (permission != null) {
            return mContext.checkCallingOrSelfPermission(permission);
        }
        // If no permission is required, return granted.
        return result;
    }

    //TODO handle per property OEM permission. bug: 32094983
    private String getPermissionName(int sensorType) {
        if ((sensorType >= CarSensorManager.SENSOR_TYPE_VENDOR_EXTENSION_START) &&
                (sensorType >= CarSensorManager.SENSOR_TYPE_VENDOR_EXTENSION_END)) {
            return Car.PERMISSION_VENDOR_EXTENSION;
        }
        String permission = null;
        switch (sensorType) {
            case CarSensorManager.SENSOR_TYPE_CAR_SPEED:
            case CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE:
                permission = Car.PERMISSION_SPEED;
                break;
            case CarSensorManager.SENSOR_TYPE_ODOMETER:
                permission = Car.PERMISSION_MILEAGE;
                break;
            case CarSensorManager.SENSOR_TYPE_FUEL_LEVEL:
                permission = Car.PERMISSION_FUEL;
                break;
            case CarSensorManager.SENSOR_TYPE_ABS_ACTIVE:
            case CarSensorManager.SENSOR_TYPE_TRACTION_CONTROL_ACTIVE:
                permission = Car.PERMISSION_VEHICLE_DYNAMICS_STATE;
                break;
            default:
                break;
        }
        return permission;
    }

    private boolean startSensor(SensorRecord record, int sensorType, int rate) {
        //TODO handle sensor rate properly. bug: 32095903
        //Some sensors which report only when there is change should be always set with maximum
        //rate. For now, set every sensor to the maximum.
        if (Log.isLoggable(CarLog.TAG_SENSOR, Log.VERBOSE)) {
            Log.v(CarLog.TAG_SENSOR, "startSensor " + sensorType + " with rate " + rate);
        }
        SensorBase sensorHal = getSensorHal(sensorType);
        if (sensorHal != null) {
            if (!sensorHal.isReady()) {
                Log.w(CarLog.TAG_SENSOR, "Sensor channel not available.");
                return false;
            }
            if (record.enabled) {
                return true;
            }
            if (sensorHal.requestSensorStart(sensorType, 0)) {
                record.enabled = true;
                return true;
            }
        }
        Log.w(CarLog.TAG_SENSOR, "requestSensorStart failed, sensor type:" + sensorType);
        return false;
    }

    @Override
    public void unregisterSensorListener(int sensorType, ICarSensorEventListener listener) {
        boolean shouldStopSensor = false;
        boolean shouldRestartSensor = false;
        SensorRecord record = null;
        int newRate = 0;
        mSensorLock.lock();
        try {
            record = mSensorRecords.get(sensorType);
            if (record == null) {
                // unregister not supported sensor. ignore.
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                    Log.d(CarLog.TAG_SENSOR, "unregister for unsupported sensor");
                }
                return;
            }
            SensorClient sensorClient = findSensorClientLocked(listener);
            if (sensorClient == null) {
                // never registered or already unregistered.
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                    Log.d(CarLog.TAG_SENSOR, "unregister for not existing client");
                }
                return;
            }
            sensorClient.removeSensor(sensorType);
            if (sensorClient.getNumberOfActiveSensor() == 0) {
                sensorClient.release();
                mClients.remove(sensorClient);
            }
            Listeners<SensorClient> sensorListeners = mSensorListeners.get(sensorType);
            if (sensorListeners == null) {
                // sensor not active
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                    Log.d(CarLog.TAG_SENSOR, "unregister for non-active sensor");
                }
                return;
            }
            ClientWithRate<SensorClient> clientWithRate =
                    sensorListeners.findClientWithRate(sensorClient);
            if (clientWithRate == null) {
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                    Log.d(CarLog.TAG_SENSOR, "unregister for not registered sensor");
                }
                return;
            }
            sensorListeners.removeClientWithRate(clientWithRate);
            if (sensorListeners.getNumberOfClients() == 0) {
                shouldStopSensor = true;
                mSensorListeners.remove(sensorType);
            } else if (sensorListeners.updateRate()) { // rate changed
                newRate = sensorListeners.getRate();
                shouldRestartSensor = sensorSupportRate(sensorType);
            }
            if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                Log.d(CarLog.TAG_SENSOR, "unregister succeeded");
            }
        } finally {
            mSensorLock.unlock();
        }
        if (shouldStopSensor) {
            stopSensor(record, sensorType);
        } else if (shouldRestartSensor) {
            startSensor(record, sensorType, newRate);
        }
    }

    @Override
    public CarSensorConfig getSensorConfig(int sensorType) {
        if (Binder.getCallingUid() != Process.myUid()) {
            switch (getSensorPermission(sensorType)) {
                case PackageManager.PERMISSION_DENIED:
                    throw new SecurityException("client does not have permission:"
                        + getPermissionName(sensorType)
                        + " pid:" + Binder.getCallingPid()
                        + " uid:" + Binder.getCallingUid());
                case PackageManager.PERMISSION_GRANTED:
                    break;
            }
        }
        return(mSensorHal.getSensorConfig(sensorType));
    }

    private void stopSensor(SensorRecord record, int sensorType) {
        if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
            Log.d(CarLog.TAG_SENSOR, "stopSensor " + sensorType);
        }
        SensorBase sensorHal = getSensorHal(sensorType);
        if (sensorHal == null || !sensorHal.isReady()) {
            Log.w(CarLog.TAG_SENSOR, "Sensor channel not available.");
            return;
        }
        if (!record.enabled) {
            return;
        }
        record.enabled = false;
        // make lastEvent invalid as old data can be sent to client when subscription is restarted
        // later.
        record.lastEvent = null;
        if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
            Log.d(CarLog.TAG_SENSOR, "stopSensor requestStop " + sensorType);
        }
        sensorHal.requestSensorStop(sensorType);
    }

    private SensorBase getSensorHal(int sensorType) {
        try {
            mSensorLock.lock();
            switch (sensorType) {
                case CarSensorManager.SENSOR_TYPE_DRIVING_STATUS:
                    if (mUseDefaultDrivingPolicy) {
                        return mDrivingStatePolicy;
                    }
                    break;
                case CarSensorManager.SENSOR_TYPE_NIGHT:
                    if (mUseDefaultDayNightModePolicy) {
                        return mDayNightModePolicy;
                    }
                    break;
            }
            return mSensorHal;
        } finally {
            mSensorLock.unlock();
        }
    }

    @Override
    public CarSensorEvent getLatestSensorEvent(int sensorType) {
        SensorRecord record = null;
        mSensorLock.lock();
        try {
            record = mSensorRecords.get(sensorType);
        } finally {
            mSensorLock.unlock();
        }
        if (record != null) {
            return record.lastEvent;
        }
        return null;
    }

    private int[] refreshSupportedSensorsLocked() {
        int numCarSensors = (mCarProvidedSensors == null) ? 0 : mCarProvidedSensors.length;
        for (int i = 0; i < numCarSensors; i++) {
            int sensor = mCarProvidedSensors[i];
            if (sensor == CarSensorManager.SENSOR_TYPE_DRIVING_STATUS) {
                mUseDefaultDrivingPolicy = false;
            } else if (sensor == CarSensorManager.SENSOR_TYPE_NIGHT) {
                mUseDefaultDayNightModePolicy = false;
            }
        }
        int totalNumSensors = numCarSensors;
        if (mUseDefaultDrivingPolicy) {
            totalNumSensors++;
        }
        if (mUseDefaultDayNightModePolicy) {
            totalNumSensors++;
        }
        // Two logical sensors are always added.
        int[] supportedSensors = new int[totalNumSensors];
        int index = 0;
        if (mUseDefaultDrivingPolicy) {
            supportedSensors[index] = CarSensorManager.SENSOR_TYPE_DRIVING_STATUS;
            index++;
        }
        if (mUseDefaultDayNightModePolicy) {
            supportedSensors[index] = CarSensorManager.SENSOR_TYPE_NIGHT;
            index++;
        }

        for (int i = 0; i < numCarSensors; i++) {
            int sensor = mCarProvidedSensors[i];

            if (mSensorRecords.get(sensor) == null) {
                SensorRecord record = new SensorRecord();
                mSensorRecords.put(sensor, record);
            }
            supportedSensors[index] = sensor;
            index++;
        }

        return supportedSensors;
    }

    private boolean isSensorRealLocked(int sensorType) {
        if (mCarProvidedSensors != null) {
            for (int sensor : mCarProvidedSensors) {
                if (sensor == sensorType ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find SensorClient from client list and return it.
     * This should be called with mClients locked.
     * @param listener
     * @return null if not found.
     */
    private SensorClient findSensorClientLocked(ICarSensorEventListener listener) {
        IBinder binder = listener.asBinder();
        for (SensorClient sensorClient : mClients) {
            if (sensorClient.isHoldingListenerBinder(binder)) {
                return sensorClient;
            }
        }
        return null;
    }

    private void removeClient(SensorClient sensorClient) {
        mSensorLock.lock();
        try {
            for (int sensor: sensorClient.getSensorArray()) {
                unregisterSensorListener(sensor,
                        sensorClient.getICarSensorEventListener());
            }
            mClients.remove(sensorClient);
        } finally {
            mSensorLock.unlock();
        }
    }

    private class SensorDispatchHandler extends Handler {
        private static final long SENSOR_DISPATCH_MIN_INTERVAL_MS = 16; // over 60Hz

        private static final int MSG_SENSOR_DATA = 0;

        private long mLastSensorDispatchTime = -1;
        private int mFreeListIndex = 0;
        private final LinkedList<CarSensorEvent>[] mSensorDataList = new LinkedList[2];

        private SensorDispatchHandler(Looper looper) {
            super(looper);
            for (int i = 0; i < mSensorDataList.length; i++) {
                mSensorDataList[i] = new LinkedList<CarSensorEvent>();
            }
        }

        private synchronized void handleSensorEvents(List<CarSensorEvent> data) {
            LinkedList<CarSensorEvent> list = mSensorDataList[mFreeListIndex];
            list.addAll(data);
            requestDispatchLocked();
        }

        private synchronized void handleSensorEvent(CarSensorEvent event) {
            LinkedList<CarSensorEvent> list = mSensorDataList[mFreeListIndex];
            list.add(event);
            requestDispatchLocked();
        }

        private void requestDispatchLocked() {
            Message msg = obtainMessage(MSG_SENSOR_DATA);
            long now = SystemClock.uptimeMillis();
            long delta = now - mLastSensorDispatchTime;
            if (delta > SENSOR_DISPATCH_MIN_INTERVAL_MS) {
                sendMessage(msg);
            } else {
                sendMessageDelayed(msg, SENSOR_DISPATCH_MIN_INTERVAL_MS - delta);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SENSOR_DATA:
                    doHandleSensorData();
                    break;
                default:
                    break;
            }
        }

        private void doHandleSensorData() {
            List<CarSensorEvent> listToDispatch = null;
            synchronized (this) {
                mLastSensorDispatchTime = SystemClock.uptimeMillis();
                int nonFreeListIndex = mFreeListIndex ^ 0x1;
                List<CarSensorEvent> nonFreeList = mSensorDataList[nonFreeListIndex];
                List<CarSensorEvent> freeList = mSensorDataList[mFreeListIndex];
                if (nonFreeList.size() > 0) {
                    Log.w(CarLog.TAG_SENSOR, "non free list not empty");
                    // copy again, but this should not be normal case
                    nonFreeList.addAll(freeList);
                    listToDispatch = nonFreeList;
                    freeList.clear();
                } else if (freeList.size() > 0) {
                    listToDispatch = freeList;
                    mFreeListIndex = nonFreeListIndex;
                }
            }
            // leave this part outside lock so that time-taking dispatching can be done without
            // blocking sensor event notification.
            if (listToDispatch != null) {
                processSensorData(listToDispatch);
                listToDispatch.clear();
            }
        }

    }

    /** internal instance for pending client request */
    private class SensorClient implements Listeners.IListener {
        /** callback for sensor events */
        private final ICarSensorEventListener mListener;
        private final SparseBooleanArray mActiveSensors = new SparseBooleanArray();

        /** when false, it is already released */
        private volatile boolean mActive = true;

        SensorClient(ICarSensorEventListener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SensorClient &&
                    mListener.asBinder() == ((SensorClient) o).mListener.asBinder()) {
                return true;
            }
            return false;
        }

        boolean isHoldingListenerBinder(IBinder listenerBinder) {
            return mListener.asBinder() == listenerBinder;
        }

        void addSensor(int sensor) {
            mActiveSensors.put(sensor, true);
        }

        void removeSensor(int sensor) {
            mActiveSensors.delete(sensor);
        }

        int getNumberOfActiveSensor() {
            return mActiveSensors.size();
        }

        int[] getSensorArray() {
            int[] sensors = new int[mActiveSensors.size()];
            for (int i = sensors.length - 1; i >= 0; --i) {
                sensors[i] = mActiveSensors.keyAt(i);
            }
            return sensors;
        }

        ICarSensorEventListener getICarSensorEventListener() {
            return mListener;
        }

        /**
         * Client dead. should remove all sensor requests from client
         */
        @Override
        public void binderDied() {
            mListener.asBinder().unlinkToDeath(this, 0);
            removeClient(this);
        }

        void dispatchSensorUpdate(List<CarSensorEvent> events) {
            if (events.size() == 0) {
                return;
            }
            if (mActive) {
                try {
                    mListener.onSensorChanged(events);
                } catch (RemoteException e) {
                    //ignore. crash will be handled by death handler
                }
            } else {
                if (Log.isLoggable(CarLog.TAG_SENSOR, Log.DEBUG)) {
                    Log.d(CarLog.TAG_SENSOR, "sensor update while client is already released");
                }
            }
        }

        @Override
        public void release() {
            if (mActive) {
                mListener.asBinder().unlinkToDeath(this, 0);
                mActiveSensors.clear();
                mActive = false;
            }
        }
    }

    private static class SensorRecord {
        /** Record the lastly received sensor event */
        CarSensorEvent lastEvent = null;
        /** sensor was enabled by at least one client */
        boolean enabled = false;
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarSensorService*");
        writer.println("supported sensors:" + Arrays.toString(mSupportedSensors));
        writer.println("**last events for sensors**");
        if (mSensorRecords != null) {
            try {
                int sensorRecordSize = mSensorRecords.size();
                for (int i = 0; i < sensorRecordSize; i++) {
                    int sensor = mSensorRecords.keyAt(i);
                    SensorRecord record = mSensorRecords.get(sensor);
                    if (record != null && record.lastEvent != null) {
                        writer.println("sensor: " + sensor
                                + " active: " + record.enabled);
                        writer.println(" " + record.lastEvent.toString());
                    }
                    Listeners listeners = mSensorListeners.get(sensor);
                    if (listeners != null) {
                        writer.println(" rate: " + listeners.getRate());
                    }
                }
            } catch (ConcurrentModificationException e) {
                writer.println("concurrent modification happened");
            }
        } else {
            writer.println("null records");
        }
        writer.println("**clients**");
        try {
            for (SensorClient client: mClients) {
                if (client != null) {
                    try {
                        writer.println("binder:" + client.mListener
                                + " active sensors:" + Arrays.toString(client.getSensorArray()));
                    } catch (ConcurrentModificationException e) {
                        writer.println("concurrent modification happened");
                    }
                } else {
                    writer.println("null client");
                }
            }
        } catch  (ConcurrentModificationException e) {
            writer.println("concurrent modification happened");
        }
        writer.println("**sensor listeners**");
        try {
            int sensorListenerSize = mSensorListeners.size();
            for (int i = 0; i < sensorListenerSize; i++) {
                int sensor = mSensorListeners.keyAt(i);
                Listeners sensorListeners = mSensorListeners.get(sensor);
                if (sensorListeners != null) {
                    writer.println(" Sensor:" + sensor
                            + " num client:" + sensorListeners.getNumberOfClients()
                            + " rate:" + sensorListeners.getRate());
                }
            }
        }  catch  (ConcurrentModificationException e) {
            writer.println("concurrent modification happened");
        }
        writer.println("mUseDefaultDrivingPolicy:" + mUseDefaultDrivingPolicy +
                ",mUseDefaultDayNightModePolicy" + mUseDefaultDayNightModePolicy);
        writer.println("**driving policy**");
        if (mUseDefaultDrivingPolicy) {
            mDrivingStatePolicy.dump(writer);
        }
        writer.println("**day/night policy**");
        if (mUseDefaultDayNightModePolicy) {
            mDayNightModePolicy.dump(writer);
        }
    }
}
