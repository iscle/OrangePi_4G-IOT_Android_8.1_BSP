/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac;

import android.app.Service;
import android.car.VehicleSeat;
import android.car.VehicleWindow;
import android.car.VehicleZone;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.support.car.Car;
import android.support.car.CarNotConnectedException;
import android.support.car.CarConnectionCallback;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;

public class HvacController extends Service {
    private static final String DEMO_MODE_PROPERTY = "android.car.hvac.demo";
    private static final String TAG = "HvacController";
    private static final int DRIVER_ZONE_ID = VehicleSeat.SEAT_ROW_1_LEFT;
    private static final int PASSENGER_ZONE_ID = VehicleSeat.SEAT_ROW_1_RIGHT;

    public static final int[] AIRFLOW_STATES = new int[]{
            CarHvacManager.FAN_POSITION_FACE,
            CarHvacManager.FAN_POSITION_FLOOR,
            CarHvacManager.FAN_POSITION_FACE_AND_FLOOR
    };

    /**
     * Callback for receiving updates from the hvac manager. A Callback can be
     * registered using {@link #registerCallback}.
     */
    public static abstract class Callback {

        public void onPassengerTemperatureChange(float temp) {
        }

        public void onDriverTemperatureChange(float temp) {
        }

        public void onFanSpeedChange(int position) {
        }

        public void onAcStateChange(boolean isOn) {
        }

        public void onFrontDefrosterChange(boolean isOn) {
        }

        public void onRearDefrosterChange(boolean isOn) {
        }

        public void onPassengerSeatWarmerChange(int level) {
        }

        public void onDriverSeatWarmerChange(int level) {
        }

        public void onFanDirectionChange(int direction) {
        }

        public void onAirCirculationChange(boolean isOn) {
        }

        public void onAutoModeChange(boolean isOn) {
        }

        public void onHvacPowerChange(boolean isOn){
        }
    }

    public class LocalBinder extends Binder {
        HvacController getService() {
            return HvacController.this;
        }
    }

    private final Binder mBinder = new LocalBinder();

    private Car mCarApiClient;
    private CarHvacManager mHvacManager;
    private Object mHvacManagerReady = new Object();

    private HvacPolicy mPolicy;
    @GuardedBy("mCallbacks")
    private List<Callback> mCallbacks = new ArrayList<>();
    private DataStore mDataStore = new DataStore();

    @Override
    public void onCreate() {
        super.onCreate();
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            if (SystemProperties.getBoolean(DEMO_MODE_PROPERTY, false)) {
                IBinder binder = (new LocalHvacPropertyService()).getCarPropertyService();
                initHvacManager(new CarHvacManager(binder, this, new Handler()));
                return;
            }

            mCarApiClient = Car.createCar(this, mCarConnectionCallback);
            mCarApiClient.connect();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHvacManager != null) {
            mHvacManager.unregisterCallback(mHardwareCallback);
        }
        if (mCarApiClient != null) {
            mCarApiClient.disconnect();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void registerCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    private void initHvacManager(CarHvacManager carHvacManager) {
        mHvacManager = carHvacManager;
        List<CarPropertyConfig> properties = null;
        try {
            properties = mHvacManager.getPropertyList();
            mPolicy = new HvacPolicy(HvacController.this, properties);
            mHvacManager.registerCallback(mHardwareCallback);
        } catch (android.car.CarNotConnectedException e) {
            Log.e(TAG, "Car not connected in HVAC");
        }

    }

    private final CarConnectionCallback mCarConnectionCallback =
            new CarConnectionCallback() {
                @Override
                public void onConnected(Car car) {
                    synchronized (mHvacManagerReady) {
                        try {
                            initHvacManager((CarHvacManager) mCarApiClient.getCarManager(
                                    android.car.Car.HVAC_SERVICE));
                            mHvacManagerReady.notifyAll();
                        } catch (CarNotConnectedException e) {
                            Log.e(TAG, "Car not connected in onServiceConnected");
                        }
                    }
                }

                @Override
                public void onDisconnected(Car car) {
                }
            };

    private final CarHvacManager.CarHvacEventCallback mHardwareCallback =
            new CarHvacManager.CarHvacEventCallback() {
                @Override
                public void onChangeEvent(final CarPropertyValue val) {
                    int areaId = val.getAreaId();
                    switch (val.getPropertyId()) {
                        case CarHvacManager.ID_ZONED_AC_ON:
                            handleAcStateUpdate(getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_FAN_POSITION:
                            handleFanPositionUpdate(areaId, getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT:
                            handleFanSpeedUpdate(areaId, getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_TEMP_SETPOINT:
                            handleTempUpdate(areaId, getValue(val));
                            break;
                        case CarHvacManager.ID_WINDOW_DEFROSTER_ON:
                            handleDefrosterUpdate(areaId, getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON:
                            handleAirCirculationUpdate(getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_SEAT_TEMP:
                            handleSeatWarmerUpdate(areaId, getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON:
                            handleAutoModeUpdate(getValue(val));
                            break;
                        case CarHvacManager.ID_ZONED_HVAC_POWER_ON:
                            handleHvacPowerOn(getValue(val));
                            break;
                        default:
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Unhandled HVAC event, id: " + val.getPropertyId());
                            }
                    }
                }

                @Override
                public void onErrorEvent(final int propertyId, final int zone) {
                }
            };

    @SuppressWarnings("unchecked")
    public static <E> E getValue(CarPropertyValue propertyValue) {
        return (E) propertyValue.getValue();
    }

    void handleHvacPowerOn(boolean isOn) {
        boolean shouldPropagate = mDataStore.shouldPropagateHvacPowerUpdate(isOn);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Hvac Power On: " + isOn + " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onHvacPowerChange(isOn);
                }
            }
        }
    }

    void handleSeatWarmerUpdate(int zone, int level) {
        boolean shouldPropagate = mDataStore.shouldPropagateSeatWarmerLevelUpdate(zone, level);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Seat Warmer Update, zone: " + zone + " level: " + level +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    if (zone == VehicleZone.ZONE_ROW_1_LEFT) {
                        mCallbacks.get(i).onDriverSeatWarmerChange(level);
                    } else {
                        mCallbacks.get(i).onPassengerSeatWarmerChange(level);
                    }
                }
            }
        }
    }

    private void handleAirCirculationUpdate(boolean airCirculationState) {
        boolean shouldPropagate
                = mDataStore.shouldPropagateAirCirculationUpdate(airCirculationState);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Air Circulation Update: " + airCirculationState +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onAirCirculationChange(airCirculationState);
                }
            }
        }
    }

    private void handleAutoModeUpdate(boolean autoModeState) {
        boolean shouldPropagate = mDataStore.shouldPropagateAutoModeUpdate(autoModeState);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "AutoMode Update, id: " + autoModeState +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onAutoModeChange(autoModeState);
                }
            }
        }
    }

    private void handleAcStateUpdate(boolean acState) {
        boolean shouldPropagate = mDataStore.shouldPropagateAcUpdate(acState);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "AC State Update, id: " + acState +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onAcStateChange(acState);
                }
            }
        }
    }

    private void handleFanPositionUpdate(int zone, int position) {
        int index = fanPositionToAirflowIndex(position);
        boolean shouldPropagate = mDataStore.shouldPropagateFanPositionUpdate(zone, index);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Fan Position Update, zone: " + zone + " position: " + position +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onFanDirectionChange(position);
                }
            }
        }
    }

    private void handleFanSpeedUpdate(int zone, int speed) {
        boolean shouldPropagate = mDataStore.shouldPropagateFanSpeedUpdate(zone, speed);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Fan Speed Update, zone: " + zone + " speed: " + speed +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    mCallbacks.get(i).onFanSpeedChange(speed);
                }
            }
        }
    }

    private void handleTempUpdate(int zone, float temp) {
        boolean shouldPropagate = mDataStore.shouldPropagateTempUpdate(zone, temp);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Temp Update, zone: " + zone + " temp: " + temp +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            int userTemperature =  mPolicy.hardwareToUserTemp(temp);
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    if (zone == VehicleZone.ZONE_ROW_1_LEFT) {
                        mCallbacks.get(i)
                                .onDriverTemperatureChange(userTemperature);
                    } else {
                        mCallbacks.get(i)
                                .onPassengerTemperatureChange(userTemperature);
                    }
                }
            }
        }
    }

    private void handleDefrosterUpdate(int zone, boolean defrosterState) {
        boolean shouldPropagate = mDataStore.shouldPropagateDefrosterUpdate(zone, defrosterState);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Defroster Update, zone: " + zone + " state: " + defrosterState +
                    " should propagate: " + shouldPropagate);
        }
        if (shouldPropagate) {
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    if (zone == VehicleWindow.WINDOW_FRONT_WINDSHIELD) {
                        mCallbacks.get(i).onFrontDefrosterChange(defrosterState);
                    } else if (zone == VehicleWindow.WINDOW_REAR_WINDSHIELD) {
                        mCallbacks.get(i).onRearDefrosterChange(defrosterState);
                    }
                }
            }
        }
    }

    public void requestRefresh(final Runnable r, final Handler h) {
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                synchronized (mHvacManagerReady) {
                    while (mHvacManager == null) {
                        try {
                            mHvacManagerReady.wait();
                        } catch (InterruptedException e) {
                            // We got interrupted so we might be shutting down.
                            return null;
                        }
                    }
                }
                fetchTemperature(DRIVER_ZONE_ID);
                fetchTemperature(PASSENGER_ZONE_ID);
                fetchFanSpeed();
                fetchDefrosterState(VehicleWindow.WINDOW_FRONT_WINDSHIELD);
                fetchDefrosterState(VehicleWindow.WINDOW_REAR_WINDSHIELD);
                fetchAirflow(DRIVER_ZONE_ID);
                fetchAirflow(PASSENGER_ZONE_ID);
                fetchAcState();
                fetchAirCirculation();
                fetchHvacPowerState();
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                h.post(r);
            }
        };
        task.execute();
    }

    public HvacPolicy getPolicy() {
        return mPolicy;
    }

    private void fetchTemperature(int zone) {
        if (mHvacManager != null) {
            try {
                mDataStore.setTemperature(zone, mHvacManager.getFloatProperty(
                        CarHvacManager.ID_ZONED_TEMP_SETPOINT, zone));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchTemperature");
            }
        }
    }

    public int getDriverTemperature() {
        return mPolicy.hardwareToUserTemp(mDataStore.getTemperature(DRIVER_ZONE_ID));
    }

    public int getPassengerTemperature() {
        return mPolicy.hardwareToUserTemp(mDataStore.getTemperature(PASSENGER_ZONE_ID));
    }

    public void setDriverTemperature(int temperature) {
        setTemperature(DRIVER_ZONE_ID, mPolicy.userToHardwareTemp(temperature));
    }

    public void setPassengerTemperature(int temperature) {
        setTemperature(PASSENGER_ZONE_ID, mPolicy.userToHardwareTemp(temperature));
    }

    public void setTemperature(final int zone, final float temperature) {
        mDataStore.setTemperature(zone, temperature);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setFloatProperty(
                                CarHvacManager.ID_ZONED_TEMP_SETPOINT, zone, temperature);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setTemperature");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    public void setDriverSeatWarmerLevel(int level) {
        setSeatWarmerLevel(DRIVER_ZONE_ID, level);
    }

    public void setPassengerSeatWarmerLevel(int level) {
        setSeatWarmerLevel(PASSENGER_ZONE_ID, level);
    }

    public void setSeatWarmerLevel(final int zone, final int level) {
        mDataStore.setSeatWarmerLevel(zone, level);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setIntProperty(
                                CarHvacManager.ID_ZONED_SEAT_TEMP, zone, level);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setSeatWarmerLevel");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    private void fetchFanSpeed() {
        if (mHvacManager != null) {
            int zone = VehicleZone.ZONE_ROW_1_ALL; // Car specific workaround.
            try {
                mDataStore.setFanSpeed(mHvacManager.getIntProperty(
                        CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, zone));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchFanSpeed");
            }
        }
    }

    public int getFanSpeed() {
        return mDataStore.getFanSpeed();
    }

    public void setFanSpeed(final int fanSpeed) {
        mDataStore.setFanSpeed(fanSpeed);

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            int newFanSpeed;

            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    int zone = VehicleZone.ZONE_ROW_1_ALL; // Car specific workaround.
                    try {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Setting fanspeed to: " + fanSpeed);
                        }
                        mHvacManager.setIntProperty(
                                CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, zone, fanSpeed);

                        newFanSpeed = mHvacManager.getIntProperty(
                                CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, zone);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setFanSpeed");
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                Log.e(TAG, "postExecute new fanSpeed: " + newFanSpeed);
            }
        };
        task.execute();
    }

    private void fetchDefrosterState(int zone) {
        if (mHvacManager != null) {
            try {
                mDataStore.setDefrosterState(zone, mHvacManager.getBooleanProperty(
                        CarHvacManager.ID_WINDOW_DEFROSTER_ON, zone));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchDefrosterState");
            }
        }
    }

    public boolean getFrontDefrosterState() {
        return mDataStore.getDefrosterState(VehicleWindow.WINDOW_FRONT_WINDSHIELD);
    }

    public boolean getRearDefrosterState() {
        return mDataStore.getDefrosterState(VehicleWindow.WINDOW_REAR_WINDSHIELD);
    }

    public void setFrontDefrosterState(boolean state) {
        setDefrosterState(VehicleWindow.WINDOW_FRONT_WINDSHIELD, state);
    }

    public void setRearDefrosterState(boolean state) {
        setDefrosterState(VehicleWindow.WINDOW_REAR_WINDSHIELD, state);
    }

    public void setDefrosterState(final int zone, final boolean state) {
        mDataStore.setDefrosterState(zone, state);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setBooleanProperty(
                                CarHvacManager.ID_WINDOW_DEFROSTER_ON, zone, state);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setDeforsterState");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    private void fetchAcState() {
        if (mHvacManager != null) {
            try {
                mDataStore.setAcState(mHvacManager.getBooleanProperty(CarHvacManager.ID_ZONED_AC_ON,
                        VehicleZone.ZONE_ROW_1_ALL));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchAcState");
            }
        }
    }

    public boolean getAcState() {
        return mDataStore.getAcState();
    }

    public void setAcState(final boolean state) {
        mDataStore.setAcState(state);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_AC_ON,
                                VehicleZone.ZONE_ROW_1_ALL, state);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setAcState");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    private int fanPositionToAirflowIndex(int fanPosition) {
        for (int i = 0; i < AIRFLOW_STATES.length; i++) {
            if (fanPosition == AIRFLOW_STATES[i]) {
                return i;
            }
        }
        Log.e(TAG, "Unknown fan position " + fanPosition + ". Returning default.");
        return AIRFLOW_STATES[0];
    }

    private void fetchAirflow(int zone) {
        if (mHvacManager != null) {
            zone = VehicleZone.ZONE_ROW_1_ALL; // Car specific workaround.
            try {
                int val = mHvacManager.getIntProperty(CarHvacManager.ID_ZONED_FAN_POSITION, zone);
                mDataStore.setAirflow(zone, fanPositionToAirflowIndex(val));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchAirFlow");
            }
        }
    }

    public int getAirflowIndex(int zone) {
        return mDataStore.getAirflow(zone);
    }

    public void setAirflowIndex(final int zone, final int index) {
        mDataStore.setAirflow(zone, index);
        int override = VehicleZone.ZONE_ROW_1_ALL; // Car specific workaround.
        int val = AIRFLOW_STATES[index];
        setFanDirection(override, val);
    }

    public void setFanDirection(final int direction) {
        mDataStore.setAirflow(VehicleZone.ZONE_ROW_1_ALL, direction);
        setFanDirection(VehicleZone.ZONE_ROW_1_ALL, direction);
    }

    private void setFanDirection(final int zone, final int direction) {
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setIntProperty(
                                CarHvacManager.ID_ZONED_FAN_POSITION, zone, direction);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setAirflowIndex");
                    }
                }
                return null;
            }
        };
        task.execute();
    }


    private void fetchAirCirculation() {
        if (mHvacManager != null) {
            try {
                mDataStore.setAirCirculationState(mHvacManager
                        .getBooleanProperty(CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON,
                                VehicleZone.ZONE_ROW_1_ALL));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchAirCirculationState");
            }
        }
    }

    public boolean getAirCirculationState() {
        return mDataStore.getAirCirculationState();
    }

    public void setAirCirculation(final boolean state) {
        mDataStore.setAirCirculationState(state);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setBooleanProperty(
                                CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON,
                                VehicleZone.ZONE_ROW_1_ALL, state);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setAcState");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    public boolean getAutoModeState() {
        return mDataStore.getAutoModeState();
    }

    public void setAutoMode(final boolean state) {
        mDataStore.setAutoModeState(state);
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... unused) {
                if (mHvacManager != null) {
                    try {
                        mHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON,
                                VehicleZone.ZONE_ROW_1_ALL, state);
                    } catch (android.car.CarNotConnectedException e) {
                        Log.e(TAG, "Car not connected in setAutoModeState");
                    }
                }
                return null;
            }
        };
        task.execute();
    }

    public boolean getHvacPowerState() {
        return mDataStore.getHvacPowerState();
    }

    private void fetchHvacPowerState() {
        if (mHvacManager != null) {
            try {
                mDataStore.setHvacPowerState(mHvacManager.getBooleanProperty(
                        CarHvacManager.ID_ZONED_HVAC_POWER_ON, VehicleZone.ZONE_ROW_1_ALL));
            } catch (android.car.CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in fetchHvacPowerState");
            }
        }
    }
}
