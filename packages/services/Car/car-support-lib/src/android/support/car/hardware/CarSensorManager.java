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

package android.support.car.hardware;

import android.Manifest;
import android.support.annotation.IntDef;
import android.support.annotation.RequiresPermission;
import android.support.car.Car;
import android.support.car.CarManagerBase;
import android.support.car.CarNotConnectedException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  Enables applications to monitor car sensor data. Applications register listeners to this
 *  manager to subscribe to individual sensor streams using the SENSOR_TYPE_* constants as the
 *  keys. Data points are returned as {@link CarSensorEvent} objects that have translations for
 *  many built-in data types. For vendor extension streams, interpret data based on
 *  vendor-provided documentation.
 */
public abstract class CarSensorManager implements CarManagerBase {
    /**
     * Represent the direction of the car as an angle in degrees measured clockwise with 0 degree
     * pointing North. Sensor data in {@link CarSensorEvent} is a float (floatValues[0]).
     */
    public static final int SENSOR_TYPE_COMPASS = 1;
    /**
     * Represent vehicle speed in meters per second (m/s). Sensor data in
     * {@link CarSensorEvent} is a float >= 0. Requires {@link Car#PERMISSION_SPEED} permission.
     * @hide
     */
    public static final int SENSOR_TYPE_CAR_SPEED = 2;
    /**
     * Represent the engine RPM of the car. Sensor data in {@link CarSensorEvent} is a float.
     * @hide
     */
    public static final int SENSOR_TYPE_RPM = 3;
    /**
     * Represent the total travel distance of the car in kilometers. Sensor data is a float.
     * Requires {@link Car#PERMISSION_MILEAGE} permission.
     * @hide
     */
    public static final int SENSOR_TYPE_ODOMETER = 4;
    /**
     * Represent the fuel level of the car. In {@link CarSensorEvent}, floatValues[{@link
     * CarSensorEvent#INDEX_FUEL_LEVEL_IN_PERCENTILE}] represents fuel level in percentile (0 to
     * 100) while floatValues[{@link CarSensorEvent#INDEX_FUEL_LEVEL_IN_DISTANCE}] represents
     * estimated range in kilometers with the remaining fuel. The gas mileage used for the
     * estimation may not represent the current driving condition. Requires {@link
     * Car#PERMISSION_FUEL} permission.
     * @hide
     */
    public static final int SENSOR_TYPE_FUEL_LEVEL = 5;
    /**
     * Represent the current status of parking brake. Sensor data in {@link CarSensorEvent} is in
     * intValues[0]. A value of 1 indicates the parking brake is engaged; a value of 0 indicates
     * the parking brake is not engaged.
     * For this sensor, rate in {@link #addListener(OnSensorChangedListener, int, int)} is
     * ignored and all changes are notified.
     */
    public static final int SENSOR_TYPE_PARKING_BRAKE = 6;
    /**
     * Represent the current position of transmission gear. Sensor data in {@link
     * CarSensorEvent} is in intValues[0]. For the meaning of the value, check {@link
     * CarSensorEvent#GEAR_NEUTRAL} and other GEAR_*.
     * @hide
     */
    public static final int SENSOR_TYPE_GEAR = 7;

    /** @hide */
    public static final int SENSOR_TYPE_RESERVED8 = 8;

    /**
     * Represent the current status of the day/night sensor. Sensor data is in intValues[0].
     */
    public static final int SENSOR_TYPE_NIGHT = 9;
    /**
     * Represent the location. Sensor data is floatValues.
     * @hide
     */
    public static final int SENSOR_TYPE_LOCATION = 10;
    /**
     * Represent the current driving status of car. Different user interactions should be used
     * depending on the current driving status. Driving status is in intValues[0].
     */
    public static final int SENSOR_TYPE_DRIVING_STATUS = 11;
    /**
     * Environment (such as temperature and pressure).
     * @hide
     */
    public static final int SENSOR_TYPE_ENVIRONMENT = 12;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED13 = 13;
    /** @hide */
    public static final int SENSOR_TYPE_ACCELEROMETER = 14;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED15 = 15;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED16 = 16;
    /** @hide */
    public static final int SENSOR_TYPE_GPS_SATELLITE = 17;
    /** @hide */
    public static final int SENSOR_TYPE_GYROSCOPE = 18;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED19 = 19;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED20 = 20;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED21 = 21;
    /** @hide */
    public static final int SENSOR_TYPE_RESERVED22 = 22;
    /**
     * Represents wheel distance in millimeters.  Some cars may not have individual sensors on each
     * wheel.  If a value is not available, -1 will be reported.  The wheel distance accumulates
     * over time.
     * Requires {@link Car#PERMISSION_MILEAGE} permission.
     */
    public static final int SENSOR_TYPE_WHEEL_TICK_DISTANCE         = 23;
    /**
     * Set to true when ABS is active.  This sensor is event driven.
     * Requires {@link Car#PERMISSION_VEHICLE_DYNAMICS_STATE} permission.
     */
    public static final int SENSOR_TYPE_ABS_ACTIVE                  = 24;
    /**
     * Set to true when traction control is active.  This sensor is event driven.
     * Requires {@link Car#PERMISSION_VEHICLE_DYNAMICS_STATE} permission.
     */
    public static final int SENSOR_TYPE_TRACTION_CONTROL_ACTIVE     = 25;

    /**
     * Sensors defined in this range [{@link #SENSOR_TYPE_VENDOR_EXTENSION_START},
     * {@link #SENSOR_TYPE_VENDOR_EXTENSION_END}] are for vendors and should be used only
     * by the system app to access sensors not defined as standard types.
     * Sensors supported in this range can vary depending on car models/manufacturers.
     * Third-party apps should not use sensors in this range as they are not compatible across
     * all cars; third-party apps that attempt to access a sensor in this range trigger a
     * security exception (as access is restricted to system apps).
     *
     * @hide
     */
    public static final int SENSOR_TYPE_VENDOR_EXTENSION_START = 0x60000000;
    /** @hide */
    public static final int SENSOR_TYPE_VENDOR_EXTENSION_END   = 0x6fffffff;

    /** @hide */
    @IntDef({
        SENSOR_TYPE_COMPASS,
        SENSOR_TYPE_CAR_SPEED,
        SENSOR_TYPE_RPM,
        SENSOR_TYPE_ODOMETER,
        SENSOR_TYPE_FUEL_LEVEL,
        SENSOR_TYPE_PARKING_BRAKE,
        SENSOR_TYPE_GEAR,
        SENSOR_TYPE_NIGHT,
        SENSOR_TYPE_LOCATION,
        SENSOR_TYPE_DRIVING_STATUS,
        SENSOR_TYPE_ENVIRONMENT,
        SENSOR_TYPE_ACCELEROMETER,
        SENSOR_TYPE_GPS_SATELLITE,
        SENSOR_TYPE_GYROSCOPE,
        SENSOR_TYPE_WHEEL_TICK_DISTANCE,
        SENSOR_TYPE_ABS_ACTIVE,
        SENSOR_TYPE_TRACTION_CONTROL_ACTIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SensorType {}

    /** Read sensor at the default normal rate set for each sensors. This is default rate. */
    public static final int SENSOR_RATE_NORMAL  = 3;
    /**@hide*/
    public static final int SENSOR_RATE_UI = 2;
    /**@hide*/
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

    /**
     * Listener for car sensor data change.
     * Callbacks are called in the Looper context.
     */
    public interface OnSensorChangedListener {
        /**
         * Called when there is new sensor data from car.
         * @param manager The manager the listener is attached to.  Useful if the app wished to
         * unregister.
         * @param event Incoming sensor event for the given sensor type.
         */
        void onSensorChanged(final CarSensorManager manager, final CarSensorEvent event);
    }

    /**
     * Get the list of CarSensors available in the connected car.
     * @return Array of all sensor types supported.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract int[] getSupportedSensors() throws CarNotConnectedException;

    /**
     * Indicate support for a given sensor.
     * @param sensorType
     * @return Returns {@code true} if the sensor is supported.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract boolean isSensorSupported(@SensorType int sensorType)
            throws CarNotConnectedException;

    /**
     * Register {@link OnSensorChangedListener} to get repeated sensor updates. Can register
     * multiple listeners for a single sensor or use the same listener for different
     * sensors. If the same listener is registered again for the same sensor, it is ignored or
     * updated (depending on the rate).
     * <p>
     * The {@link OnSensorChangedListener} is the identifier for the request and the same
     * instance must be passed into {@link #removeListener(OnSensorChangedListener)}.
     * <p>
     *
     * @param sensorType Sensor type to subscribe.
     * @param rate How fast sensor events are delivered. Should be one of
     *        {@link #SENSOR_RATE_FASTEST} or {@link #SENSOR_RATE_NORMAL}. Rate may not be
     *        respected, especially when the same sensor is registered with a different listener
     *        and with different rates.
     * @return Returns {@code true} if the sensor was successfully enabled.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @throws IllegalArgumentException if wrong argument (such as wrong rate).
     * @throws SecurityException if missing the appropriate permission.
     */
    @RequiresPermission(anyOf={Manifest.permission.ACCESS_FINE_LOCATION, Car.PERMISSION_SPEED,
            Car.PERMISSION_MILEAGE, Car.PERMISSION_FUEL, Car.PERMISSION_VEHICLE_DYNAMICS_STATE},
            conditional=true)
    public abstract boolean addListener(OnSensorChangedListener listener,
            @SensorType int sensorType, @SensorRate int rate)
                    throws CarNotConnectedException, IllegalArgumentException;

    /**
     * Stop getting sensor updates for the given listener. If there are multiple registrations for
     * this listener, all listening is stopped.
     * @param listener The listener to remove.
     */
    public abstract  void removeListener(OnSensorChangedListener listener);

    /**
     * Stop getting sensor updates for the given listener and sensor. If the same listener is used
     * for other sensors, those subscriptions are not affected.
     * @param listener The listener to remove.
     * @param sensorType The type to stop receiving notifications for.
     */
    public abstract  void removeListener(OnSensorChangedListener listener,
            @SensorType int sensorType);

    /**
     * Get the most recent CarSensorEvent for the given type.
     * @param type A sensor to request.
     * @return null if no sensor update since connection to the car.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract CarSensorEvent getLatestSensorEvent(@SensorType int type)
            throws CarNotConnectedException;

    /**
     * Get the config data for the given type.
     * @param sensor type to request
     * @return CarSensorConfig object
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     * @hide
     */
    public abstract CarSensorConfig getSensorConfig(@SensorType int type)
            throws CarNotConnectedException;

}
