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

package android.support.car.hardware;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CarSensorsProxy adds car sensors implementation for sensors that are not provided by vehicle HAL.
 * @hide
 */
class CarSensorsProxy {
    private static final String TAG = "CarSensorsProxy";
    private static final int MSG_SENSORT_EVENT = 1;

    // @GuardedBy("this")
    private final Map<Integer, Set<CarSensorManager.OnSensorChangedListener>> mListenersMultiMap;
    private final LocationManager mLocationManager;
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometerSensor;
    private final Sensor mMagneticFieldSensor;
    private final Sensor mGyroscopeSensor;
    private final int[] mSupportedSensors;

    // returned with the onSensorChanged messages.
    private final CarSensorManager mCarSensorManager;

    // @GuardedBy("this")
    private Location mLastLocation;
    // @GuardedBy("this")
    private GpsStatus mLastGpsStatus;
    // @GuardedBy("this")
    private float[] mLastAccelerometerData = new float[3];
    // @GuardedBy("this")
    private float[] mLastMagneticFieldData = new float[3];
    // @GuardedBy("this")
    private float[] mLastGyroscopeData = new float[3];
    // @GuardedBy("this")
    private float[] mR = new float[16];
    // @GuardedBy("this")
    private float[] mI = new float[16];
    // @GuardedBy("this")
    private float[] mOrientation = new float[3];
    // @GuardedBy("this")
    private long mLastLocationTime;
    // @GuardedBy("this")
    private long mLastGpsStatusTime;
    // @GuardedBy("this")
    private long mLastAccelerometerDataTime;
    // @GuardedBy("this")
    private long mLastMagneticFieldDataTime;
    // @GuardedBy("this")
    private long mLastGyroscopeDataTime;

    private final GpsStatus.Listener mGpsStatusListener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                    synchronized (CarSensorsProxy.this) {
                        mLastGpsStatus = mLocationManager.getGpsStatus(mLastGpsStatus);
                        mLastGpsStatusTime = System.nanoTime();
                    }
                    pushSensorChanges(CarSensorManager.SENSOR_TYPE_GPS_SATELLITE);
                }
            }
        };

    private final LocationListener mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                synchronized (CarSensorsProxy.this) {
                    mLastLocation = location;
                    mLastLocationTime = System.nanoTime();
                }
                pushSensorChanges(CarSensorManager.SENSOR_TYPE_LOCATION);
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

    private final SensorEventListener mSensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int type = event.sensor.getType();
                synchronized (CarSensorsProxy.this) {
                    switch (type) {
                        case Sensor.TYPE_GYROSCOPE:
                            System.arraycopy((Object) event.values, 0, (Object) mLastGyroscopeData, 0, 3);
                            mLastGyroscopeDataTime = System.nanoTime();
                            pushSensorChanges(CarSensorManager.SENSOR_TYPE_GYROSCOPE);
                            break;
                        case Sensor.TYPE_MAGNETIC_FIELD:
                            System.arraycopy((Object) event.values, 0, (Object) mLastMagneticFieldData, 0, 3);
                            mLastMagneticFieldDataTime = System.nanoTime();
                            pushSensorChanges(CarSensorManager.SENSOR_TYPE_COMPASS);
                            break;
                        case Sensor.TYPE_ACCELEROMETER:
                            System.arraycopy((Object) event.values, 0, (Object) mLastAccelerometerData, 0, 3);
                            mLastAccelerometerDataTime = System.nanoTime();
                            pushSensorChanges(CarSensorManager.SENSOR_TYPE_ACCELEROMETER);
                            pushSensorChanges(CarSensorManager.SENSOR_TYPE_COMPASS);
                            break;
                        default:
                            Log.w(TAG, "Unexpected sensor event type: " + type);
                            // Should never happen.
                            return;
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

    private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SENSORT_EVENT:
                        int sensorType = msg.arg1;
                        Collection<CarSensorManager.OnSensorChangedListener> listenersCollection;
                        synchronized (CarSensorsProxy.this) {
                            listenersCollection = mListenersMultiMap.get(sensorType);
                        }
                        CarSensorEvent event = (CarSensorEvent) msg.obj;
                        if (event != null) {
                            for (CarSensorManager.OnSensorChangedListener listener :
                                         listenersCollection) {
                                listener.onSensorChanged(mCarSensorManager, event);
                            }
                        }
                        break;
                    default:
                        Log.w(TAG, "Unexpected msg dispatched. msg: " + msg);
                        super.handleMessage(msg);
                }
            }
        };

    CarSensorsProxy(CarSensorManager carSensorManager, Context context) {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mListenersMultiMap = new HashMap<Integer, Set<CarSensorManager.OnSensorChangedListener>>();
        mSupportedSensors = initSupportedSensors(context);
        mCarSensorManager = carSensorManager;
    }

    private int[] initSupportedSensors(Context context) {
        Set<Integer> features = new HashSet<>();
        PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)
                && packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            features.add(CarSensorManager.SENSOR_TYPE_COMPASS);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            features.add(CarSensorManager.SENSOR_TYPE_ACCELEROMETER);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
            features.add(CarSensorManager.SENSOR_TYPE_GYROSCOPE);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            features.add(CarSensorManager.SENSOR_TYPE_LOCATION);
            features.add(CarSensorManager.SENSOR_TYPE_GPS_SATELLITE);
        }
        return toIntArray(features);
    }

    private static int[] toIntArray(Collection<Integer> collection) {
        int len = collection.size();
        int[] arr = new int[len];
        int arrIndex = 0;
        for (Integer item : collection) {
            arr[arrIndex] = item;
            arrIndex++;
        }
        return arr;
    }


    public boolean isSensorSupported(int sensorType) {
        for (int sensor : mSupportedSensors) {
            if (sensor == sensorType) {
                return true;
            }
        }
        return false;
    }

    public int[] getSupportedSensors() {
        return mSupportedSensors;
    }

    public boolean registerSensorListener(CarSensorManager.OnSensorChangedListener listener,
            int sensorType, int rate) {
        // current implementation ignores rate.
        boolean sensorSetChanged = false;
        synchronized (this) {
            if (mListenersMultiMap.get(sensorType) == null) {
                mListenersMultiMap.put(sensorType,
                                       new HashSet<CarSensorManager.OnSensorChangedListener>());
                sensorSetChanged = true;
            }
            mListenersMultiMap.get(sensorType).add(listener);
        }

        pushSensorChanges(sensorType);

        if (sensorSetChanged) {
            updateSensorListeners();
        }
        return true;
    }

    public void unregisterSensorListener(CarSensorManager.OnSensorChangedListener listener,
            int sensorType) {
        if (listener == null) {
            return;
        }
        boolean sensorSetChanged = false;
        synchronized (this) {
            Set<CarSensorManager.OnSensorChangedListener> sensorTypeListeneres =
                    mListenersMultiMap.get(sensorType);
            if (sensorTypeListeneres != null) {
                sensorTypeListeneres.remove(listener);
                if (sensorTypeListeneres.isEmpty()) {
                    mListenersMultiMap.remove(sensorType);
                    sensorSetChanged = true;
                }
            }
        }
        if (sensorSetChanged) {
            updateSensorListeners();
        };
    }

    public void unregisterSensorListener(CarSensorManager.OnSensorChangedListener listener) {
        if (listener == null) {
            return;
        }
        Set<Integer> sensorsToRemove = new HashSet<>();
        synchronized (this) {
            for (Map.Entry<Integer, Set<CarSensorManager.OnSensorChangedListener>> entry :
                         mListenersMultiMap.entrySet()) {
                if (entry.getValue().contains(listener)) {
                    entry.getValue().remove(listener);
                }
                if (entry.getValue().isEmpty()) {
                    sensorsToRemove.add(entry.getKey());
                }
            }
        }
        if (!sensorsToRemove.isEmpty()) {
            for (Integer s : sensorsToRemove) {
                mListenersMultiMap.remove(s);
            }
            updateSensorListeners();
        };
    }

    public CarSensorEvent getLatestSensorEvent(int type) {
        return getSensorEvent(type);
    }

    private void pushSensorChanges(int sensorType) {
        CarSensorEvent event = getSensorEvent(sensorType);
        if (event == null) {
            return;
        }
        Message msg = mHandler.obtainMessage(MSG_SENSORT_EVENT, sensorType, 0, event);
        mHandler.sendMessage(msg);
    }

    private CarSensorEvent getSensorEvent(int sensorType) {
        CarSensorEvent event = null;
        synchronized (this) {
            switch (sensorType) {
                case CarSensorManager.SENSOR_TYPE_COMPASS:
                    if (mLastMagneticFieldDataTime != 0 && mLastAccelerometerDataTime != 0) {
                        event = new CarSensorEvent(sensorType, Math.max(mLastMagneticFieldDataTime,
                                mLastAccelerometerDataTime), 3, 0, 0);
                        SensorManager.getRotationMatrix(mR, mI, mLastAccelerometerData,
                                mLastMagneticFieldData);
                        SensorManager.getOrientation(mR, mOrientation);
                        event.floatValues[CarSensorEvent.INDEX_COMPASS_BEARING] =
                                (float) Math.toDegrees(mOrientation[0]);
                        event.floatValues[CarSensorEvent.INDEX_COMPASS_PITCH] =
                                (float) Math.toDegrees(mOrientation[1]);
                        event.floatValues[CarSensorEvent.INDEX_COMPASS_ROLL] =
                                (float) Math.toDegrees(mOrientation[2]);
                    }
                    break;
                case CarSensorManager.SENSOR_TYPE_LOCATION:
                    if (mLastLocationTime != 0) {
                        event = new CarSensorEvent(sensorType, mLastLocationTime, 6, 3, 0);
                        populateLocationCarSensorEvent(event, mLastLocation);
                    }
                    break;
                case CarSensorManager.SENSOR_TYPE_ACCELEROMETER:
                    if (mLastAccelerometerDataTime != 0) {
                        event = new CarSensorEvent(sensorType, mLastAccelerometerDataTime, 3, 0, 0);
                        event.floatValues[CarSensorEvent.INDEX_ACCELEROMETER_X] =
                                mLastAccelerometerData[0];
                        event.floatValues[CarSensorEvent.INDEX_ACCELEROMETER_Y] =
                                mLastAccelerometerData[1];
                        event.floatValues[CarSensorEvent.INDEX_ACCELEROMETER_Z] =
                                mLastAccelerometerData[2];
                    }
                    break;
                case CarSensorManager.SENSOR_TYPE_GPS_SATELLITE:
                    if (mLastGpsStatusTime != 0) {
                        event = createGpsStatusCarSensorEvent(mLastGpsStatus);
                    }
                    break;
                case CarSensorManager.SENSOR_TYPE_GYROSCOPE:
                    if (mLastGyroscopeDataTime != 0) {
                        event = new CarSensorEvent(sensorType, mLastGyroscopeDataTime, 3, 0, 0);
                        event.floatValues[CarSensorEvent.INDEX_GYROSCOPE_X] = mLastGyroscopeData[0];
                        event.floatValues[CarSensorEvent.INDEX_GYROSCOPE_Y] = mLastGyroscopeData[1];
                        event.floatValues[CarSensorEvent.INDEX_GYROSCOPE_Z] = mLastGyroscopeData[2];
                    }
                    break;
                default:
                    // Should not happen.
                    Log.w(TAG, "[getSensorEvent]: Unsupported sensor type:" + sensorType);
                    return null;
            }
        }
        return event;
    }

    private void populateLocationCarSensorEvent(CarSensorEvent event, Location location) {
        if (location == null) {
            return;
        }
        int present = 0;
        present |= (0x1 << CarSensorEvent.INDEX_LOCATION_LONGITUDE);
        event.intValues[CarSensorEvent.INDEX_LOCATION_LATITUDE_INTS] =
                (int) (location.getLongitude() * 1E7);

        present |= (0x1 << CarSensorEvent.INDEX_LOCATION_LATITUDE);
        event.intValues[CarSensorEvent.INDEX_LOCATION_LATITUDE_INTS] =
                (int) (location.getLatitude() * 1E7);

        if (location.hasAccuracy()) {
            present |= (0x1 << CarSensorEvent.INDEX_LOCATION_ACCURACY);
            event.floatValues[CarSensorEvent.INDEX_LOCATION_ACCURACY] = location.getAccuracy();
        }

        if (location.hasAltitude()) {
            present |= (0x1 << CarSensorEvent.INDEX_LOCATION_ALTITUDE);
            event.floatValues[CarSensorEvent.INDEX_LOCATION_ALTITUDE] =
                    (float) location.getAltitude();
        }

        if (location.hasSpeed()) {
            present |= (0x1 << CarSensorEvent.INDEX_LOCATION_SPEED);
            event.floatValues[CarSensorEvent.INDEX_LOCATION_SPEED] = location.getSpeed();
        }

        if (location.hasBearing()) {
            present |= (0x1 << CarSensorEvent.INDEX_LOCATION_BEARING);
            event.floatValues[CarSensorEvent.INDEX_LOCATION_BEARING] = location.getBearing();
        }

        event.intValues[0] = present;
    }

    private CarSensorEvent createGpsStatusCarSensorEvent(GpsStatus gpsStatus) {
        CarSensorEvent event = null;

        if (gpsStatus == null) {
            return event;
        }

        int numberInView = 0;
        int numberInUse = 0;
        for (GpsSatellite satellite : gpsStatus.getSatellites()) {
            ++numberInView;
            if (satellite.usedInFix()) {
                ++numberInUse;
            }
        }
        int floatValuesSize = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_INTERVAL * numberInView
                + CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_OFFSET;
        int intValuesSize = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_INTERVAL * numberInView
                + CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_OFFSET;
        event = new CarSensorEvent(CarSensorManager.SENSOR_TYPE_GPS_SATELLITE, mLastGpsStatusTime,
                floatValuesSize, intValuesSize, 0);
        event.intValues[CarSensorEvent.INDEX_GPS_SATELLITE_NUMBER_IN_USE] = numberInUse;
        event.intValues[CarSensorEvent.INDEX_GPS_SATELLITE_NUMBER_IN_VIEW] = numberInView;
        int i = 0;
        for (GpsSatellite satellite : gpsStatus.getSatellites()) {
            int iInt = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_OFFSET
                    + CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_INTERVAL * i;
            int iFloat = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_OFFSET
                    + CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_INTERVAL * i;
            event.floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_PRN_OFFSET] =
                    satellite.getPrn();
            event.floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_SNR_OFFSET] =
                    satellite.getSnr();
            event.floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_AZIMUTH_OFFSET] =
                    satellite.getAzimuth();
            event.floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_ELEVATION_OFFSET] =
                    satellite.getElevation();
            event.intValues[iInt] = satellite.usedInFix() ? 1 : 0;
            i++;
        }
        return event;
    }

    private void updateSensorListeners() {
        Set<Integer> activeSensors;
        synchronized (this) {
            activeSensors = mListenersMultiMap.keySet();
        }

        if (activeSensors.contains(CarSensorManager.SENSOR_TYPE_LOCATION)
            && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                    mLocationListener);
        } else {
            mLocationManager.removeUpdates(mLocationListener);
        }

        if (activeSensors.contains(CarSensorManager.SENSOR_TYPE_GPS_SATELLITE)) {
            mLocationManager.addGpsStatusListener(mGpsStatusListener);
        } else {
            mLocationManager.removeGpsStatusListener(mGpsStatusListener);
        }

        if (activeSensors.contains(CarSensorManager.SENSOR_TYPE_ACCELEROMETER)
                || activeSensors.contains(CarSensorManager.SENSOR_TYPE_COMPASS)) {
            mSensorManager.registerListener(mSensorListener, mAccelerometerSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager.unregisterListener(mSensorListener, mAccelerometerSensor);
        }

        if (activeSensors.contains(CarSensorManager.SENSOR_TYPE_COMPASS)) {
            mSensorManager.registerListener(mSensorListener, mMagneticFieldSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager.unregisterListener(mSensorListener, mMagneticFieldSensor);
        }

        if (activeSensors.contains(CarSensorManager.SENSOR_TYPE_GYROSCOPE)) {
            mSensorManager.registerListener(mSensorListener, mGyroscopeSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager.unregisterListener(mSensorListener, mGyroscopeSensor);
        }
    }
}
