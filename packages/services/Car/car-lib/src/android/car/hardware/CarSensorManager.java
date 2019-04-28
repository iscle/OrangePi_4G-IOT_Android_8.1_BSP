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

package android.car.hardware;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.CarApiUtil;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.car.internal.CarRatedListeners;
import com.android.car.internal.SingleMessageHandler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 *  API for monitoring car sensor data.
 */
public final class CarSensorManager implements CarManagerBase {
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED1                   = 1;
    /**
     * This sensor represents vehicle speed in m/s.
     * Sensor data in {@link CarSensorEvent} is a float which will be >= 0.
     * This requires {@link Car#PERMISSION_SPEED} permission.
     */
    public static final int SENSOR_TYPE_CAR_SPEED                   = 2;
    /**
     * Represents engine RPM of the car. Sensor data in {@link CarSensorEvent} is a float.
     */
    public static final int SENSOR_TYPE_RPM                         = 3;
    /**
     * Total travel distance of the car in Kilometer. Sensor data is a float.
     * This requires {@link Car#PERMISSION_MILEAGE} permission.
     */
    public static final int SENSOR_TYPE_ODOMETER                    = 4;
    /**
     * Indicates fuel level of the car.
     * In {@link CarSensorEvent}, floatValues[{@link CarSensorEvent#INDEX_FUEL_LEVEL_IN_PERCENTILE}]
     * represents fuel level in percentile (0 to 100) while
     * floatValues[{@link CarSensorEvent#INDEX_FUEL_LEVEL_IN_DISTANCE}] represents estimated range
     * in Kilometer with the remaining fuel.
     * Note that the gas mileage used for the estimation may not represent the current driving
     * condition.
     * This requires {@link Car#PERMISSION_FUEL} permission.
     */
    public static final int SENSOR_TYPE_FUEL_LEVEL                  = 5;
    /**
     * Represents the current status of parking brake. Sensor data in {@link CarSensorEvent} is an
     * intValues[0]. Value of 1 represents parking brake applied while 0 means the other way
     * around. For this sensor, rate in {@link #registerListener(OnSensorChangedListener, int, int)}
     * will be ignored and all changes will be notified.
     */
    public static final int SENSOR_TYPE_PARKING_BRAKE               = 6;
    /**
     * This represents the current position of transmission gear. Sensor data in
     * {@link CarSensorEvent} is an intValues[0]. For the meaning of the value, check
     * {@link CarSensorEvent#GEAR_NEUTRAL} and other GEAR_*.
     */
    public static final int SENSOR_TYPE_GEAR                        = 7;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED8                   = 8;
    /**
     * Day/night sensor. Sensor data is intValues[0].
     */
    public static final int SENSOR_TYPE_NIGHT                       = 9;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED10                  = 10;
    /**
     * Represents the current driving status of car. Different user interaction should be used
     * depending on the current driving status. Driving status is intValues[0].
     */
    public static final int SENSOR_TYPE_DRIVING_STATUS              = 11;
    /**
     * Environment like temperature and pressure.
     */
    public static final int SENSOR_TYPE_ENVIRONMENT                 = 12;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED13                  = 13;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED14                  = 14;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED15                  = 15;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED16                  = 16;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED17                  = 17;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED18                  = 18;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED19                  = 19;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED20                  = 20;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED21                  = 21;
    /**
     * Represents ignition state. The value should be one of the constants that starts with
     * IGNITION_STATE_* in {@link CarSensorEvent}.
     */
    public static final int SENSOR_TYPE_IGNITION_STATE              = 22;
    /**
     * Represents wheel distance in millimeters.  Some cars may not have individual sensors on each
     * wheel.  If a value is not available, Long.MAX_VALUE will be reported.  The wheel distance
     * accumulates over time.  It increments on forward movement, and decrements on reverse.  Wheel
     * distance shall be reset to zero each time a vehicle is started by the user.
     * This requires {@link Car#PERMISSION_SPEED} permission.
     */
    public static final int SENSOR_TYPE_WHEEL_TICK_DISTANCE         = 23;
    /**
     * Set to true when ABS is active.  This sensor is event driven.
     * This requires {@link Car#PERMISSION_VEHICLE_DYNAMICS_STATE} permission.
     */
    public static final int SENSOR_TYPE_ABS_ACTIVE                  = 24;
    /**
     * Set to true when traction control is active.  This sensor is event driven.
     * This requires {@link Car#PERMISSION_VEHICLE_DYNAMICS_STATE} permission.
     */
    public static final int SENSOR_TYPE_TRACTION_CONTROL_ACTIVE     = 25;

    /**
     * Sensor type bigger than this is invalid. Always update this after adding a new sensor.
     * @hide
     */
    private static final int SENSOR_TYPE_MAX = SENSOR_TYPE_TRACTION_CONTROL_ACTIVE;

    /**
     * Sensors defined in this range [{@link #SENSOR_TYPE_VENDOR_EXTENSION_START},
     * {@link #SENSOR_TYPE_VENDOR_EXTENSION_END}] is for each car vendor's to use.
     * This should be only used for system app to access sensors not defined as standard types.
     * So the sensor supported in this range can vary depending on car models / manufacturers.
     * 3rd party apps should not use sensors in this range as they are not compatible across
     * different cars. Additionally 3rd party apps trying to access sensor in this range will get
     * security exception as their access is restricted to system apps.
     *
     * @hide
     */
    public static final int SENSOR_TYPE_VENDOR_EXTENSION_START = 0x60000000;
    public static final int SENSOR_TYPE_VENDOR_EXTENSION_END   = 0x6fffffff;

    /** @hide */
    @IntDef({
        SENSOR_TYPE_CAR_SPEED,
        SENSOR_TYPE_RPM,
        SENSOR_TYPE_ODOMETER,
        SENSOR_TYPE_FUEL_LEVEL,
        SENSOR_TYPE_PARKING_BRAKE,
        SENSOR_TYPE_GEAR,
        SENSOR_TYPE_NIGHT,
        SENSOR_TYPE_DRIVING_STATUS,
        SENSOR_TYPE_ENVIRONMENT,
        SENSOR_TYPE_IGNITION_STATE,
        SENSOR_TYPE_WHEEL_TICK_DISTANCE,
        SENSOR_TYPE_ABS_ACTIVE,
        SENSOR_TYPE_TRACTION_CONTROL_ACTIVE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SensorType {}

    /** Read sensor in default normal rate set for each sensors. This is default rate. */
    public static final int SENSOR_RATE_NORMAL  = 3;
    public static final int SENSOR_RATE_UI = 2;
    public static final int SENSOR_RATE_FAST = 1;
    /** Read sensor at the maximum rate. Actual rate will be different depending on the sensor. */
    public static final int SENSOR_RATE_FASTEST = 0;

    /** @hide */
    @IntDef({
        SENSOR_RATE_NORMAL,
        SENSOR_RATE_UI,
        SENSOR_RATE_FAST,
        SENSOR_RATE_FASTEST
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SensorRate {}

    private static final int MSG_SENSOR_EVENTS = 0;

    private final ICarSensor mService;

    private CarSensorEventListenerToService mCarSensorEventListenerToService;

    /**
     * To keep record of locally active sensors. Key is sensor type. This is used as a basic lock
     * for all client accesses.
     */
    private final SparseArray<CarSensorListeners> mActiveSensorListeners = new SparseArray<>();

    /** Handles call back into clients. */
    private final SingleMessageHandler<CarSensorEvent> mHandlerCallback;


    /** @hide */
    public CarSensorManager(IBinder service, Context context, Handler handler) {
        mService = ICarSensor.Stub.asInterface(service);
        mHandlerCallback = new SingleMessageHandler<CarSensorEvent>(handler.getLooper(),
                MSG_SENSOR_EVENTS) {
            @Override
            protected void handleEvent(CarSensorEvent event) {
                CarSensorListeners listeners;
                synchronized (mActiveSensorListeners) {
                    listeners = mActiveSensorListeners.get(event.sensorType);
                }
                if (listeners != null) {
                    listeners.onSensorChanged(event);
                }
            }
        };
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized(mActiveSensorListeners) {
            mActiveSensorListeners.clear();
            mCarSensorEventListenerToService = null;
        }
    }

    /**
     * Give the list of CarSensors available in the connected car.
     * @return array of all sensor types supported.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public int[] getSupportedSensors() throws CarNotConnectedException {
        try {
            return mService.getSupportedSensors();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
        return new int[0];
    }

    /**
     * Tells if given sensor is supported or not.
     * @param sensorType
     * @return true if the sensor is supported.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public boolean isSensorSupported(@SensorType int sensorType) throws CarNotConnectedException {
        int[] sensors = getSupportedSensors();
        for (int sensorSupported: sensors) {
            if (sensorType == sensorSupported) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if given sensorList is including the sensorType.
     * @param sensorList
     * @param sensorType
     * @return
     */
    public static boolean isSensorSupported(int[] sensorList, @SensorType int sensorType) {
        for (int sensorSupported: sensorList) {
            if (sensorType == sensorSupported) {
                return true;
            }
        }
        return false;
    }

    /**
     * Listener for car sensor data change.
     * Callbacks are called in the Looper context.
     */
    public interface OnSensorChangedListener {
        /**
         * Called when there is a new sensor data from car.
         * @param event Incoming sensor event for the given sensor type.
         */
        void onSensorChanged(final CarSensorEvent event);
    }

    /**
     * Register {@link OnSensorChangedListener} to get repeated sensor updates. Multiple listeners
     * can be registered for a single sensor or the same listener can be used for different sensors.
     * If the same listener is registered again for the same sensor, it will be either ignored or
     * updated depending on the rate.
     * <p>
     * Requires {@link Car#PERMISSION_SPEED} for {@link #SENSOR_TYPE_CAR_SPEED} and
     *  {@link #SENSOR_TYPE_WHEEL_TICK_DISTANCE}, {@link Car#PERMISSION_MILEAGE} for
     *  {@link #SENSOR_TYPE_ODOMETER}, {@link Car#PERMISSION_FUEL} for
     *  {@link #SENSOR_TYPE_FUEL_LEVEL}, or {@link Car#PERMISSION_VEHICLE_DYNAMICS_STATE} for
     *  {@link #SENSOR_TYPE_ABS_ACTIVE} and {@link #SENSOR_TYPE_TRACTION_CONTROL_ACTIVE}
     *
     * @param listener
     * @param sensorType sensor type to subscribe.
     * @param rate how fast the sensor events are delivered. It should be one of
     *        {@link #SENSOR_RATE_FASTEST}, {@link #SENSOR_RATE_FAST}, {@link #SENSOR_RATE_UI},
     *        {@link #SENSOR_RATE_NORMAL}. Rate may not be respected especially when the same sensor
     *        is registered with different listener with different rates. Also, rate might be
     *        ignored when vehicle property raises events only when the value is actually changed,
     *        for example {@link #SENSOR_TYPE_PARKING_BRAKE} will raise an event only when parking
     *        brake was engaged or disengaged.
     * @return if the sensor was successfully enabled.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @throws IllegalArgumentException for wrong argument like wrong rate
     * @throws SecurityException if missing the appropriate permission
     */
    @RequiresPermission(anyOf={Manifest.permission.ACCESS_FINE_LOCATION, Car.PERMISSION_SPEED,
            Car.PERMISSION_MILEAGE, Car.PERMISSION_FUEL, Car.PERMISSION_VEHICLE_DYNAMICS_STATE},
            conditional=true)
    public boolean registerListener(OnSensorChangedListener listener, @SensorType int sensorType,
            @SensorRate int rate) throws CarNotConnectedException, IllegalArgumentException {
        assertSensorType(sensorType);
        if (rate != SENSOR_RATE_FASTEST && rate != SENSOR_RATE_NORMAL
                && rate != SENSOR_RATE_UI && rate != SENSOR_RATE_FAST) {
            throw new IllegalArgumentException("wrong rate " + rate);
        }
        synchronized(mActiveSensorListeners) {
            if (mCarSensorEventListenerToService == null) {
                mCarSensorEventListenerToService = new CarSensorEventListenerToService(this);
            }
            boolean needsServerUpdate = false;
            CarSensorListeners listeners;
            listeners = mActiveSensorListeners.get(sensorType);
            if (listeners == null) {
                listeners = new CarSensorListeners(rate);
                mActiveSensorListeners.put(sensorType, listeners);
                needsServerUpdate = true;
            }
            if (listeners.addAndUpdateRate(listener, rate)) {
                needsServerUpdate = true;
            }
            if (needsServerUpdate) {
                if (!registerOrUpdateSensorListener(sensorType, rate)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Stop getting sensor update for the given listener. If there are multiple registrations for
     * this listener, all listening will be stopped.
     * @param listener
     */
    public void unregisterListener(OnSensorChangedListener listener) {
        //TODO: removing listener should reset update rate, bug: 32060307
        synchronized(mActiveSensorListeners) {
            for (int i = 0; i < mActiveSensorListeners.size(); i++) {
                doUnregisterListenerLocked(listener, mActiveSensorListeners.keyAt(i));
            }
        }
    }

    /**
     * Stop getting sensor update for the given listener and sensor. If the same listener is used
     * for other sensors, those subscriptions will not be affected.
     * @param listener
     * @param sensorType
     */
    public void unregisterListener(OnSensorChangedListener listener, @SensorType int sensorType) {
        synchronized(mActiveSensorListeners) {
            doUnregisterListenerLocked(listener, sensorType);
        }
    }

    private void doUnregisterListenerLocked(OnSensorChangedListener listener, Integer sensor) {
        CarSensorListeners listeners = mActiveSensorListeners.get(sensor);
        if (listeners != null) {
            boolean needsServerUpdate = false;
            if (listeners.contains(listener)) {
                needsServerUpdate = listeners.remove(listener);
            }
            if (listeners.isEmpty()) {
                try {
                    mService.unregisterSensorListener(sensor.intValue(),
                            mCarSensorEventListenerToService);
                } catch (RemoteException e) {
                    //ignore
                }
                mActiveSensorListeners.remove(sensor);
            } else if (needsServerUpdate) {
                try {
                    registerOrUpdateSensorListener(sensor, listeners.getRate());
                } catch (CarNotConnectedException e) {
                    // ignore
                }
            }
        }
    }

    private boolean registerOrUpdateSensorListener(int sensor, int rate)
            throws CarNotConnectedException {
        try {
            if (!mService.registerOrUpdateSensorListener(sensor, rate,
                    mCarSensorEventListenerToService)) {
                return false;
            }
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
        return true;
    }

    /**
     * Get the most recent CarSensorEvent for the given type. Note that latest sensor data from car
     * will not be available if it was never subscribed before. This call will return immediately
     * with null if there is no data available.
     * @param type A sensor to request
     * @return null if there was no sensor update since connected to the car.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public CarSensorEvent getLatestSensorEvent(@SensorType int type)
            throws CarNotConnectedException {
        assertSensorType(type);
        try {
            return mService.getLatestSensorEvent(type);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch(RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
        return null;
    }

    private void handleCarServiceRemoteExceptionAndThrow(RemoteException e)
            throws CarNotConnectedException {
        if (Log.isLoggable(CarLibLog.TAG_SENSOR, Log.INFO)) {
            Log.i(CarLibLog.TAG_SENSOR, "RemoteException from car service:" + e.getMessage());
        }
        throw new CarNotConnectedException();
    }

    private void assertSensorType(int sensorType) {
        if (sensorType == 0 || !((sensorType <= SENSOR_TYPE_MAX) ||
                ((sensorType >= SENSOR_TYPE_VENDOR_EXTENSION_START) &&
                        (sensorType <= SENSOR_TYPE_VENDOR_EXTENSION_END)))) {
            throw new IllegalArgumentException("invalid sensor type " + sensorType);
        }
    }

    private void handleOnSensorChanged(List<CarSensorEvent> events) {
        mHandlerCallback.sendEvents(events);
    }

    private static class CarSensorEventListenerToService extends ICarSensorEventListener.Stub {
        private final WeakReference<CarSensorManager> mManager;

        public CarSensorEventListenerToService(CarSensorManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onSensorChanged(List<CarSensorEvent> events) {
            CarSensorManager manager = mManager.get();
            if (manager != null) {
                manager.handleOnSensorChanged(events);
            }
        }
    }

    private class CarSensorListeners extends CarRatedListeners<OnSensorChangedListener> {
        CarSensorListeners(int rate) {
            super(rate);
        }

        void onSensorChanged(final CarSensorEvent event) {
            // throw away old sensor data as oneway binder call can change order.
            long updateTime = event.timestamp;
            if (updateTime < mLastUpdateTime) {
                Log.w(CarLibLog.TAG_SENSOR, "dropping old sensor data");
                return;
            }
            mLastUpdateTime = updateTime;
            List<OnSensorChangedListener> listeners;
            synchronized (mActiveSensorListeners) {
                listeners = new ArrayList<>(getListeners());
            }
            listeners.forEach(new Consumer<OnSensorChangedListener>() {
                @Override
                public void accept(OnSensorChangedListener listener) {
                    listener.onSensorChanged(event);
                }
            });
        }
    }

    /**
     * Get the config data for the given type.
     *
     * A CarSensorConfig object is returned for every sensor type.  However, if there is no
     * config, the data will be empty.
     *
     * @param sensor type to request
     * @return CarSensorConfig object
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public CarSensorConfig getSensorConfig(@SensorType int type)
        throws CarNotConnectedException {
        assertSensorType(type);
        try {
            return mService.getSensorConfig(type);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch(RemoteException e) {
            handleCarServiceRemoteExceptionAndThrow(e);
        }
        return new CarSensorConfig(0, Bundle.EMPTY);
    }
}
