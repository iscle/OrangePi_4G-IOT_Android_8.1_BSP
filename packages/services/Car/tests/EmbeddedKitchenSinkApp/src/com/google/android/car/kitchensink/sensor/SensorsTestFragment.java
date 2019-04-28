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

package com.google.android.car.kitchensink.sensor;

import android.Manifest;
import android.annotation.Nullable;
import android.car.Car;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.car.CarNotConnectedException;
import android.support.car.hardware.CarSensorConfig;
import android.support.car.hardware.CarSensorEvent;
import android.support.car.hardware.CarSensorManager;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SensorsTestFragment extends Fragment {
    private static final String TAG = "CAR.SENSOR.KS";
    private static final boolean DBG = true;
    private static final boolean DBG_VERBOSE = false;
    private static final int KS_PERMISSIONS_REQUEST = 1;

    private final static String[] REQUIRED_PERMISSIONS = new String[]{
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Car.PERMISSION_MILEAGE,
        Car.PERMISSION_FUEL,
        Car.PERMISSION_SPEED,
        Car.PERMISSION_VEHICLE_DYNAMICS_STATE
    };

    private final CarSensorManager.OnSensorChangedListener mOnSensorChangedListener =
            new CarSensorManager.OnSensorChangedListener() {
                @Override
                public void onSensorChanged(CarSensorManager manager, CarSensorEvent event) {
                    if (DBG_VERBOSE) {
                        Log.v(TAG, "New car sensor event: " + event);
                    }
                    synchronized (SensorsTestFragment.this) {
                        mEventMap.put(event.sensorType, event);
                    }
                    refreshUi();
                }
            };
    private final Handler mHandler = new Handler();
    private final Map<Integer, CarSensorEvent> mEventMap = new ConcurrentHashMap<>();
    private final DateFormat mDateFormat = SimpleDateFormat.getDateTimeInstance();

    private KitchenSinkActivity mActivity;
    private TextView mSensorInfo;
    private Car mCar;
    private CarSensorManager mSensorManager;
    private String mNaString;
    private int[] supportedSensors = new int[0];
    private Set<String> mActivePermissions = new HashSet<String>();


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DBG) {
            Log.i(TAG, "onCreateView");
        }

        View view = inflater.inflate(R.layout.sensors, container, false);
        mActivity = (KitchenSinkActivity) getHost();

        mSensorInfo = (TextView) view.findViewById(R.id.sensor_info);
        mNaString = getContext().getString(R.string.sensor_na);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        initPermissions();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSensorManager != null) {
            mSensorManager.removeListener(mOnSensorChangedListener);
        }
    }

    private void initSensors() {
        try {
            mSensorManager = (CarSensorManager)
                    mActivity.getCar().getCarManager(Car.SENSOR_SERVICE);
            supportedSensors = mSensorManager.getSupportedSensors();
            for (Integer sensor : supportedSensors) {
                if ((sensor == CarSensorManager.SENSOR_TYPE_LOCATION
                     || sensor == CarSensorManager.SENSOR_TYPE_GPS_SATELLITE)
                    && !mActivePermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    continue;
                }
                mSensorManager.addListener(mOnSensorChangedListener, sensor,
                        CarSensorManager.SENSOR_RATE_NORMAL);
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected or not supported", e);
        }
    }

    private void initPermissions() {
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            requestPermissions(missingPermissions);
        } else {
            initSensors();
        }
    }

    private Set<String> checkExistingPermissions() {
        Set<String> missingPermissions = new HashSet<String>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (mActivity.checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED) {
                mActivePermissions.add(permission);
            } else {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    private void requestPermissions(Set<String> permissions) {
        Log.d(TAG, "requesting additional permissions=" + permissions);

        requestPermissions(permissions.toArray(new String[permissions.size()]),
                KS_PERMISSIONS_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult reqCode=" + requestCode);
        if (KS_PERMISSIONS_REQUEST == requestCode) {
            for (int i=0; i<permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    mActivePermissions.add(permissions[i]);
                }
            }
            initSensors();
        }
    }

    private void refreshUi() {
        String summaryString;
        synchronized (this) {
            List<String> summary = new ArrayList<>();
            for (Integer i : supportedSensors) {
                CarSensorEvent event = mEventMap.get(i);
                switch (i) {
                    case CarSensorManager.SENSOR_TYPE_COMPASS:
                        summary.add(getCompassString(event));
                        break;
                    case CarSensorManager.SENSOR_TYPE_CAR_SPEED:
                        summary.add(getContext().getString(R.string.sensor_speed,
                                getTimestamp(event),
                                event == null ? mNaString : event.getCarSpeedData().carSpeed));
                        break;
                    case CarSensorManager.SENSOR_TYPE_RPM:
                        summary.add(getContext().getString(R.string.sensor_rpm,
                                getTimestamp(event),
                                event == null ? mNaString : event.getRpmData().rpm));
                        break;
                    case CarSensorManager.SENSOR_TYPE_ODOMETER:
                        summary.add(getContext().getString(R.string.sensor_odometer,
                                getTimestamp(event),
                                event == null ? mNaString : event.getOdometerData().kms));
                        break;
                    case CarSensorManager.SENSOR_TYPE_FUEL_LEVEL:
                        String level = mNaString;
                        String range = mNaString;
                        String lowFuelWarning = mNaString;
                        if (event != null) {
                            CarSensorEvent.FuelLevelData fuelData = event.getFuelLevelData();
                            level = fuelData.level == -1 ? level : String.valueOf(fuelData.level);
                            range = fuelData.range == -1 ? range : String.valueOf(fuelData.range);
                            lowFuelWarning = String.valueOf(fuelData.lowFuelWarning);
                        }
                        summary.add(getContext().getString(R.string.sensor_fuel_level,
                                getTimestamp(event), level, range, lowFuelWarning));
                        break;
                    case CarSensorManager.SENSOR_TYPE_PARKING_BRAKE:
                        summary.add(getContext().getString(R.string.sensor_parking_brake,
                                getTimestamp(event),
                                event == null ? mNaString :
                                event.getParkingBrakeData().isEngaged));
                        break;
                    case CarSensorManager.SENSOR_TYPE_GEAR:
                        summary.add(getContext().getString(R.string.sensor_gear,
                                getTimestamp(event),
                                event == null ? mNaString : event.getGearData().gear));
                        break;
                    case CarSensorManager.SENSOR_TYPE_NIGHT:
                        summary.add(getContext().getString(R.string.sensor_night,
                                getTimestamp(event),
                                event == null ? mNaString : event.getNightData().isNightMode));
                        break;
                    case CarSensorManager.SENSOR_TYPE_LOCATION:
                        summary.add(getLocationString(event));
                        break;
                    case CarSensorManager.SENSOR_TYPE_DRIVING_STATUS:
                        String drivingStatus = mNaString;
                        String binDrivingStatus = mNaString;
                        if (event != null) {
                            CarSensorEvent.DrivingStatusData drivingStatusData =
                                    event.getDrivingStatusData();
                            drivingStatus = String.valueOf(drivingStatusData.status);
                            binDrivingStatus = Integer.toBinaryString(drivingStatusData.status);
                        }
                        summary.add(getContext().getString(R.string.sensor_driving_status,
                                getTimestamp(event), drivingStatus, binDrivingStatus));
                        break;
                    case CarSensorManager.SENSOR_TYPE_ENVIRONMENT:
                        String temperature = mNaString;
                        String pressure = mNaString;
                        if (event != null) {
                            CarSensorEvent.EnvironmentData env = event.getEnvironmentData();
                            temperature = Float.isNaN(env.temperature) ? temperature :
                                    String.valueOf(env.temperature);
                            pressure = Float.isNaN(env.pressure) ? pressure :
                                    String.valueOf(env.pressure);
                        }
                        summary.add(getContext().getString(R.string.sensor_environment,
                                getTimestamp(event), temperature, pressure));
                        break;
                    case CarSensorManager.SENSOR_TYPE_ACCELEROMETER:
                        summary.add(getAccelerometerString(event));
                        break;
                    case CarSensorManager.SENSOR_TYPE_GPS_SATELLITE:
                        summary.add(getGpsSatelliteString(event));
                        break;
                    case CarSensorManager.SENSOR_TYPE_GYROSCOPE:
                        summary.add(getGyroscopeString(event));
                        break;
                    case CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE:
                        if(event != null) {
                            CarSensorEvent.CarWheelTickDistanceData d =
                                event.getCarWheelTickDistanceData();
                            summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                                getTimestamp(event), d.sensorResetCount, d.frontLeftWheelDistanceMm,
                                d.frontRightWheelDistanceMm, d.rearLeftWheelDistanceMm,
                                d.rearRightWheelDistanceMm));
                        } else {
                            summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                                getTimestamp(event), mNaString, mNaString, mNaString, mNaString,
                                mNaString));
                        }
                        // Get the config data
                        try {
                            CarSensorConfig c = mSensorManager.getSensorConfig(
                                CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE);
                            summary.add(getContext().getString(R.string.sensor_wheel_ticks_cfg,
                                c.getInt(CarSensorConfig.WHEEL_TICK_DISTANCE_SUPPORTED_WHEELS),
                                c.getInt(CarSensorConfig.WHEEL_TICK_DISTANCE_FRONT_LEFT_UM_PER_TICK),
                                c.getInt(CarSensorConfig.WHEEL_TICK_DISTANCE_FRONT_RIGHT_UM_PER_TICK),
                                c.getInt(CarSensorConfig.WHEEL_TICK_DISTANCE_REAR_LEFT_UM_PER_TICK),
                                c.getInt(CarSensorConfig.WHEEL_TICK_DISTANCE_REAR_RIGHT_UM_PER_TICK)));
                        } catch (CarNotConnectedException e) {
                            Log.e(TAG, "Car not connected or not supported", e);
                        }
                        break;
                    case CarSensorManager.SENSOR_TYPE_ABS_ACTIVE:
                        summary.add(getContext().getString(R.string.sensor_abs_is_active,
                            getTimestamp(event), event == null ? mNaString :
                            event.getCarAbsActiveData().absIsActive));
                        break;

                    case CarSensorManager.SENSOR_TYPE_TRACTION_CONTROL_ACTIVE:
                        summary.add(
                            getContext().getString(R.string.sensor_traction_control_is_active,
                            getTimestamp(event), event == null ? mNaString :
                            event.getCarTractionControlActiveData().tractionControlIsActive));
                        break;
                    default:
                        // Should never happen.
                        Log.w(TAG, "Unrecognized event type: " + i);
                }
            }
            summaryString = TextUtils.join("\n", summary);
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSensorInfo.setText(summaryString);
            }
        });
    }

    private String getTimestamp(CarSensorEvent event) {
        if (event == null) {
            return mNaString;
        }
        return mDateFormat.format(new Date(event.timestamp / 1000L));
    }

    private String getCompassString(CarSensorEvent event) {
        String bear = mNaString;
        String pitch = mNaString;
        String roll = mNaString;
        if (event != null) {
            CarSensorEvent.CompassData compass = event.getCompassData();
            bear = Float.isNaN(compass.bearing) ? bear : String.valueOf(compass.bearing);
            pitch = Float.isNaN(compass.pitch) ? pitch : String.valueOf(compass.pitch);
            roll = Float.isNaN(compass.roll) ? roll : String.valueOf(compass.roll);
        }
        return getContext().getString(R.string.sensor_compass,
                getTimestamp(event), bear, pitch, roll);
    }

    private String getGyroscopeString(CarSensorEvent event) {
        String x = mNaString;
        String y = mNaString;
        String z = mNaString;
        if (event != null) {
            CarSensorEvent.GyroscopeData gyro = event.getGyroscopeData();
            x = Float.isNaN(gyro.x) ? x : String.valueOf(gyro.x);
            y = Float.isNaN(gyro.y) ? y : String.valueOf(gyro.y);
            z = Float.isNaN(gyro.z) ? z : String.valueOf(gyro.z);
        }
        return getContext().getString(R.string.sensor_gyroscope,
                getTimestamp(event), x, y, z);
    }

    private String getAccelerometerString(CarSensorEvent event) {
        String x = mNaString;
        String y = mNaString;
        String z = mNaString;
        if (event != null) {
            CarSensorEvent.AccelerometerData gyro = event.getAccelerometerData();
            x = Float.isNaN(gyro.x) ? x : String.valueOf(gyro.x);
            y = Float.isNaN(gyro.y) ? y : String.valueOf(gyro.y);
            z = Float.isNaN(gyro.z) ? z : String.valueOf(gyro.z);
        }
        return getContext().getString(R.string.sensor_accelerometer,
                getTimestamp(event), x, y, z);
    }

    private String getLocationString(CarSensorEvent event) {
        String lat = mNaString;
        String lon = mNaString;
        String accuracy = mNaString;
        String alt = mNaString;
        String speed = mNaString;
        String bearing = mNaString;
        if (event != null) {
            Location location = event.getLocation(null);
            lat = String.valueOf(location.getLatitude());
            lon = String.valueOf(location.getLongitude());
            accuracy = location.hasAccuracy() ? String.valueOf(location.getAccuracy()) : accuracy;
            alt = location.hasAltitude() ? String.valueOf(location.getAltitude()) : alt;
            speed = location.hasSpeed() ? String.valueOf(location.getSpeed()) : speed;
            bearing = location.hasBearing() ? String.valueOf(location.getBearing()) : bearing;
        }
        return getContext().getString(R.string.sensor_location,
                getTimestamp(event), lat, lon, accuracy, alt, speed, bearing);
    }

    private String getGpsSatelliteString(CarSensorEvent event) {
        String inUse = mNaString;
        String inView = mNaString;
        String perSattelite = "";
        if (event != null) {
            CarSensorEvent.GpsSatelliteData gpsData = event.getGpsSatelliteData(true);
            inUse = gpsData.numberInUse != -1 ? String.valueOf(gpsData.numberInUse) : inUse;
            inView = gpsData.numberInView != -1 ? String.valueOf(gpsData.numberInView) : inView;
            List<String> perSatteliteList = new ArrayList<>();
            int num = gpsData.usedInFix.length;
            for (int i=0; i<num; i++) {
                perSatteliteList.add(getContext().getString(R.string.sensor_single_gps_satellite,
                        i+1, gpsData.usedInFix[i], gpsData.prn[i], gpsData.snr[i],
                        gpsData.azimuth[i], gpsData.elevation[i]));
            }
            perSattelite = TextUtils.join(", ", perSatteliteList);
        }
        return getContext().getString(R.string.sensor_gps,
                getTimestamp(event), inView, inUse, perSattelite);
    }
}
