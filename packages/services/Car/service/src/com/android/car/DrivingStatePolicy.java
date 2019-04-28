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

import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.ICarSensorEventListener;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.android.car.hal.SensorHalService.SensorListener;
import java.io.PrintWriter;
import java.util.List;


/**
 * Logical sensor implementing driving state policy. This policy sets only two states:
 * no restriction vs fully restrictive. To enter no restriction state, speed should be zero
 * while either parking brake is applied or transmission gear is in P.
 */
public class DrivingStatePolicy extends CarSensorService.LogicalSensor {

    private final Context mContext;
    private CarSensorService mSensorService;
    private int mDrivingState = CarSensorEvent.DRIVE_STATUS_FULLY_RESTRICTED;
    private SensorListener mSensorListener;
    private boolean mIsReady = false;
    private boolean mStarted = false;

    private static final int[] SUPPORTED_SENSORS = { CarSensorManager.SENSOR_TYPE_DRIVING_STATUS };

    private final ICarSensorEventListener mICarSensorEventListener =
            new ICarSensorEventListener.Stub() {
        @Override
        public void onSensorChanged(List<CarSensorEvent> events) {
            for (CarSensorEvent event: events) {
                handleSensorEvent(event);
            }
        }
    };

    public DrivingStatePolicy(Context context, CarSensorService sensorService) {
        mContext = context;
        mSensorService = sensorService;
    }

    @Override
    public void init() {
        mIsReady = true;
    }

    @Override
    public synchronized void onSensorServiceReady() {
        int sensorList[] = mSensorService.getSupportedSensors();
        boolean hasSpeed = subscribeIfSupportedLocked(sensorList,
                CarSensorManager.SENSOR_TYPE_CAR_SPEED, CarSensorManager.SENSOR_RATE_FASTEST);
        if (!hasSpeed) {
            Log.w(CarLog.TAG_SENSOR,
                    "No speed sensor from car. Driving state will be always fully restrictive");
        }
        boolean hasParkingBrake = subscribeIfSupportedLocked(sensorList,
                CarSensorManager.SENSOR_TYPE_PARKING_BRAKE, CarSensorManager.SENSOR_RATE_FASTEST);
        boolean hasGear = subscribeIfSupportedLocked(sensorList, CarSensorManager.SENSOR_TYPE_GEAR,
                CarSensorManager.SENSOR_RATE_FASTEST);
        if (!hasParkingBrake && !hasGear) {
            Log.w(CarLog.TAG_SENSOR,
                    "No brake info from car. Driving state will be always fully restrictive");
        }
    }

    @Override
    public void release() {
    }

    public static CarSensorEvent getDefaultValue(int sensorType) {
        if (sensorType != CarSensorManager.SENSOR_TYPE_DRIVING_STATUS) {
            Log.w(CarLog.TAG_SENSOR, "getDefaultValue to DrivingStatePolicy with sensorType:" +
                    sensorType);
            return null;
        }
        // There's a race condition and timestamp from vehicle HAL could be slightly less
        // then current call to SystemClock.elapsedRealtimeNanos() will return.
        // We want vehicle HAL value always override this default value so we set timestamp to 0.
        return createEvent(CarSensorEvent.DRIVE_STATUS_FULLY_RESTRICTED, 0 /* timestamp */);
    }

    public synchronized void registerSensorListener(SensorListener listener) {
        mSensorListener = listener;
    }

    @Override
    public synchronized boolean isReady() {
        return mIsReady;
    }

    @Override
    public int[] getSupportedSensors() {
        return SUPPORTED_SENSORS;
    }

    @Override
    public synchronized boolean requestSensorStart(int sensorType, int rate) {
        mStarted = true;
        dispatchCarSensorEvent(mSensorListener, createEvent(mDrivingState));
        return true;
    }

    @Override
    public synchronized void requestSensorStop(int sensorType) {
        mStarted = false;
    }

    @Override
    public void dump(PrintWriter writer) {
    }

    private boolean subscribeIfSupportedLocked(int sensorList[], int sensorType, int rate) {
        if (!CarSensorManager.isSensorSupported(sensorList, sensorType)) {
            Log.i(CarLog.TAG_SENSOR, "Sensor not supported:" + sensorType);
            return false;
        }
        return mSensorService.registerOrUpdateSensorListener(sensorType, rate,
                mICarSensorEventListener);
    }

    private synchronized void handleSensorEvent(CarSensorEvent event) {
        switch (event.sensorType) {
            case CarSensorManager.SENSOR_TYPE_PARKING_BRAKE:
            case CarSensorManager.SENSOR_TYPE_GEAR:
            case CarSensorManager.SENSOR_TYPE_CAR_SPEED:
                int drivingState = recalcDrivingStateLocked();
                if (drivingState != mDrivingState && mSensorListener != null) {
                    mDrivingState = drivingState;
                    dispatchCarSensorEvent(mSensorListener, createEvent(mDrivingState));
                }
                break;
            default:
                break;
        }
    }

    private int recalcDrivingStateLocked() {
        int drivingState = CarSensorEvent.DRIVE_STATUS_FULLY_RESTRICTED;
        CarSensorEvent lastParkingBrake = mSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_PARKING_BRAKE);
        CarSensorEvent lastGear = mSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_GEAR);
        CarSensorEvent lastSpeed = mSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_CAR_SPEED);
        if (lastSpeed != null && lastSpeed.floatValues[0] == 0f) { // stopped
            if (lastParkingBrake == null && isParkingBrakeApplied(lastParkingBrake)) {
                if (lastGear != null && isGearInParkingOrNeutral(lastGear)) {
                    drivingState = CarSensorEvent.DRIVE_STATUS_UNRESTRICTED;
                }
            } else { // parking brake not applied or not available
                if (lastGear != null && isGearInParking(lastGear)) { // gear in P
                    drivingState = CarSensorEvent.DRIVE_STATUS_UNRESTRICTED;
                }
            }
        } // else moving, full restriction
        return drivingState;
    }

    private boolean isSpeedZero(CarSensorEvent event) {
        return event.floatValues[0] == 0f;
    }

    private boolean isParkingBrakeApplied(CarSensorEvent event) {
        return event.intValues[0] == 1;
    }

    private boolean isGearInParkingOrNeutral(CarSensorEvent event) {
        int gear = event.intValues[0];
        return (gear == CarSensorEvent.GEAR_NEUTRAL) ||
                (gear == CarSensorEvent.GEAR_PARK);
    }

    private boolean isGearInParking(CarSensorEvent event) {
        int gear = event.intValues[0];
        return gear == CarSensorEvent.GEAR_PARK;
    }

    private static CarSensorEvent createEvent(int drivingState) {
        return createEvent(drivingState, SystemClock.elapsedRealtimeNanos());
    }

    private static CarSensorEvent createEvent(int drivingState, long timestamp) {
        CarSensorEvent event = new CarSensorEvent(
                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS,
                timestamp,
                0 /* float values */,
                1 /* int values */,
                0 /* long values */);
        event.intValues[0] = drivingState;
        return event;
    }
}
