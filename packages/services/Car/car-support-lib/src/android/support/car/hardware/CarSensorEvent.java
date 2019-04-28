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

import android.location.GpsSatellite;
import android.location.Location;
import android.os.SystemClock;
import android.support.annotation.RestrictTo;

import static android.support.annotation.RestrictTo.Scope.GROUP_ID;

/**
 * A CarSensorEvent object corresponds to a single sensor event coming from the car. Sensor
 * data is stored in a sensor type-specific format in the object's float and byte arrays.
 * </p>
 * To aid in unmarshalling the object's data arrays, this class provides static nested classes and
 * conversion methods (such as {@link DrivingStatusData} and {@link #getDrivingStatusData}).
 * Additionally, calling a conversion method on a CarSensorEvent object with an inappropriate type
 * results in an {@code UnsupportedOperationException} being thrown.
 */
public class CarSensorEvent {


    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: No restrictions.
     */
    public static final int DRIVE_STATUS_UNRESTRICTED = 0;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: No video playback allowed.
     * See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_NO_VIDEO = 0x1;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: No keyboard or rotary
     * controller input allowed. See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_NO_KEYBOARD_INPUT = 0x2;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: No voice input allowed.
     * See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_NO_VOICE_INPUT = 0x4;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: No setup/configuration
     * allowed. See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_NO_CONFIG = 0x8;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: Limit displayed message
     * length. See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_LIMIT_MESSAGE_LEN = 0x10;
    /**
     * Status for {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}: All driving restrictions
     * enabled. See {@link #getDrivingStatusData()}.
     */
    public static final int DRIVE_STATUS_FULLY_RESTRICTED = DRIVE_STATUS_NO_VIDEO |
            DRIVE_STATUS_NO_KEYBOARD_INPUT | DRIVE_STATUS_NO_VOICE_INPUT | DRIVE_STATUS_NO_CONFIG |
            DRIVE_STATUS_LIMIT_MESSAGE_LEN;
    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_COMPASS} in floatValues.
     * Angles are in degrees. Can be NaN if not available. See {@link #getCompassData()}.
     */
    public static final int INDEX_COMPASS_BEARING = 0;
    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_COMPASS} in floatValues.
     * Angles are in degrees. Can be NaN if not available. See {@link #getCompassData()}.
     */
    public static final int INDEX_COMPASS_PITCH   = 1;
    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_COMPASS} in floatValues.
     * Angles are in degrees. Can be NaN if not available. See {@link #getCompassData()}.
     */
    public static final int INDEX_COMPASS_ROLL    = 2;


    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE} in longValues.
     */
    public static final int INDEX_WHEEL_DISTANCE_RESET_COUNT = 0;
    public static final int INDEX_WHEEL_DISTANCE_FRONT_LEFT = 1;
    public static final int INDEX_WHEEL_DISTANCE_FRONT_RIGHT = 2;
    public static final int INDEX_WHEEL_DISTANCE_REAR_RIGHT = 3;
    public static final int INDEX_WHEEL_DISTANCE_REAR_LEFT = 4;

    private static final long MILLI_IN_NANOS = 1000000L;

    /** Sensor type for this event, such as {@link CarSensorManager#SENSOR_TYPE_COMPASS}. */
    public final int sensorType;

    /**
     * When this data was acquired in car or received from car. It is the elapsed time of data
     * reception from the car in nanoseconds since system boot.
     */
    public final long timestamp;
    /**
     * Array holding float type of sensor data. If the sensor has single value, only floatValues[0]
     * should be used. */
    public final float[] floatValues;
    /** Array holding int type of sensor data. */
    public final int[] intValues;
    /** array holding long int type of sensor data */
    public final long[] longValues;

    private static final float[] EMPTY_FLOAT_ARRAY = {};
    private static final int[] EMPTY_INT_ARRAY = {};
    private static final long[] EMPTY_LONG_ARRAY = {};

    /**
     * Constructs a {@link CarSensorEvent} from integer values. Handled by
     * CarSensorManager implementations. App developers need not worry about constructing these
     * objects.
     * @hide
     */
    @RestrictTo(GROUP_ID)
    public CarSensorEvent(int sensorType, long timestamp, int floatValueSize, int intValueSize,
                          int longValueSize) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        floatValues = new float[floatValueSize];
        intValues = new int[intValueSize];
        longValues = new long[longValueSize];
    }

    /**
     * Constructor
     * @param sensorType one of the {@link CarSensorManager}'s SENSOR_TYPE_* constants
     * @param timestamp time since system start in nanoseconds
     * @param floatValues {@code null} will be converted to an empty array
     * @param intValues {@code null} will be converted to an empty array
     * @param longValues {@code null} will be converted to an empty array
     * @hide
     */
    @RestrictTo(GROUP_ID)
    public CarSensorEvent(int sensorType, long timestamp, float[] floatValues, int[] intValues,
                          long[] longValues) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        this.floatValues = (floatValues == null) ? EMPTY_FLOAT_ARRAY : floatValues;
        this.intValues = (intValues == null) ? EMPTY_INT_ARRAY : intValues;
        this.longValues = (longValues == null) ? EMPTY_LONG_ARRAY : longValues;
    }

    /**
     * Constructor
     * @param sensorType one of the {@link CarSensorManager}'s SENSOR_TYPE_* constants
     * @param timestamp time since system start in nanoseconds
     * @param floatValues {@code null} will be converted to an empty array
     * @param byteValues bytes will be converted into the intValues array. {@code null} will be
     * converted to an empty array.
     * @param longValues {@code null} will be converted to an empty array
     * @hide
     */
    @RestrictTo(GROUP_ID)
    public CarSensorEvent(int sensorType, long timestamp, float[] floatValues, byte[] byteValues,
                          long[] longValues) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        this.floatValues = (floatValues == null) ? EMPTY_FLOAT_ARRAY : floatValues;
        if (byteValues == null) {
            this.intValues = EMPTY_INT_ARRAY;
        } else {
            this.intValues = new int[byteValues.length];
            for (int i = 0; i < byteValues.length; i++) {
                this.intValues[i] = byteValues[i];
            }
        }
        this.longValues = (longValues == null) ? EMPTY_LONG_ARRAY : longValues;
    }

    private void checkType(int type) {
        if (sensorType == type) {
            return;
        }
        throw new UnsupportedOperationException(String.format(
                "Invalid sensor type: expected %d, got %d", type, sensorType));
    }

    /**
     * Holds data about the car's compass readings. See {@link #getCompassData()}.
     */
    public static class CompassData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** The bearing in degrees. If unsupported by the car, this value is NaN. */
        public final float bearing;
        /**
         * The pitch in degrees. Nose down is positive. If unsupported by the car, this value is
         * NaN.
         */
        public final float pitch;
        /**
         * The roll in degrees. Right door down is positive. If unsupported by the car, this value
         * is NaN.
         */
        public final float roll;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public CompassData(long timestamp, float bearing, float pitch, float roll) {
            this.timestamp = timestamp;
            this.bearing = bearing;
            this.pitch = pitch;
            this.roll = roll;
        }
    }

    /**
     * Convenience method for obtaining a {@link CompassData} object from a CarSensorEvent object
     * with type {@link CarSensorManager#SENSOR_TYPE_COMPASS}.
     *
     * @return A CompassData object corresponding to the data contained in the CarSensorEvent.
     */
    public CompassData getCompassData() {
        checkType(CarSensorManager.SENSOR_TYPE_COMPASS);
        return new CompassData(0, floatValues[INDEX_COMPASS_BEARING],
                floatValues[INDEX_COMPASS_PITCH], floatValues[INDEX_COMPASS_ROLL]);
    }

    /**
     * Indicates the state of the parking brake (engaged or not). See
     * {@link #getParkingBrakeData()}.
     */
    public static class ParkingBrakeData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** Returns {@code true} if the parking brake is engaged. */
        public final boolean isEngaged;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public ParkingBrakeData(long timestamp, boolean isEngaged) {
            this.timestamp = timestamp;
            this.isEngaged = isEngaged;
        }
    }

    /**
     * Convenience method for obtaining a {@link ParkingBrakeData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_PARKING_BRAKE}. See
     * {@link #getParkingBrakeData()}.
     *
     * @return A ParkingBreakData object corresponding to the data contained in the CarSensorEvent.
     */
    public ParkingBrakeData getParkingBrakeData() {
        checkType(CarSensorManager.SENSOR_TYPE_PARKING_BRAKE);
        return new ParkingBrakeData(timestamp, (intValues[0] == 1));
    }

    /**
     * Indicates if the system is in night mode (a state in which the screen is
     * darkened or displays a darker color palette). See {@link #getNightData()}.
     */
    public static class NightData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** Returns {@code true} if the system is in night mode. */
        public final boolean isNightMode;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public NightData(long timestamp, boolean isNightMode) {
            this.timestamp = timestamp;
            this.isNightMode = isNightMode;
        }
    }

    /**
     * Convenience method for obtaining a {@link NightData} object from a CarSensorEvent object
     * with type {@link CarSensorManager#SENSOR_TYPE_NIGHT}.
     *
     * @return A NightData object corresponding to the data contained in the CarSensorEvent.
     */
    public NightData getNightData() {
        checkType(CarSensorManager.SENSOR_TYPE_NIGHT);
        return new NightData(timestamp, (intValues[0] == 1));
    }

    /**
     * Indicates the restrictions in effect based on the status of the vehicle. See
     * {@link #getDrivingStatusData()}.
     */
    public static class DrivingStatusData {
        /**
         * The time in nanoseconds since system boot.
         */
        public final long timestamp;
        /**
         * A bitmask with the following field values: {@link #DRIVE_STATUS_NO_VIDEO},
         * {@link #DRIVE_STATUS_NO_KEYBOARD_INPUT}, {@link #DRIVE_STATUS_NO_VOICE_INPUT},
         * {@link #DRIVE_STATUS_NO_CONFIG}, {@link #DRIVE_STATUS_LIMIT_MESSAGE_LEN}. You may read
         * this or use the convenience methods.
         */
        public final int status;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public DrivingStatusData(long timestamp, int status) {
            this.timestamp = timestamp;
            this.status = status;
        }

        /**
         * @return Returns {@code true} if the keyboard is not allowed at this time.
         */
        public boolean isKeyboardRestricted() {
            return DRIVE_STATUS_NO_KEYBOARD_INPUT == (status & DRIVE_STATUS_NO_KEYBOARD_INPUT);
        }

        /**
         * @return Returns {@code true} if voice commands are not allowed at this time.
         */
        public boolean isVoiceRestricted() {
            return DRIVE_STATUS_NO_VOICE_INPUT == (status & DRIVE_STATUS_NO_VOICE_INPUT);
        }

        /**
         * @return Returns {@code true} if video is not allowed at this time.
         */
        public boolean isVideoRestricted() {
            return DRIVE_STATUS_NO_VIDEO == (status & DRIVE_STATUS_NO_VIDEO);
        }

        /**
         * @return Returns {@code true} if configuration should not be performed at this time.
         */
        public boolean isConfigurationRestricted() {
            return DRIVE_STATUS_NO_CONFIG == (status & DRIVE_STATUS_NO_CONFIG);
        }

        /**
         * @return Returns {@code true} if message length should be limited at this time.
         */
        public boolean isMessageLengthRestricted() {
            return DRIVE_STATUS_LIMIT_MESSAGE_LEN == (status & DRIVE_STATUS_LIMIT_MESSAGE_LEN);
        }

        /**
         * @return Returns {@code true} if all restrictions are in place at this time.
         */
        public boolean isFullyRestricted() {
            return DRIVE_STATUS_FULLY_RESTRICTED == (status & DRIVE_STATUS_FULLY_RESTRICTED);
        }
    }

    /**
     * Convenience method for obtaining a {@link DrivingStatusData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}.
     *
     * @return A DrivingStatusData object corresponding to the data contained in the
     * CarSensorEvent.
     */
    public DrivingStatusData getDrivingStatusData() {
        checkType(CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
        return new DrivingStatusData(timestamp, intValues[0]);
    }


    /*things that are currently hidden*/


    /**
     * Index in {@link #floatValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is fuel level in percentile.
     * @hide
     */
    public static final int INDEX_FUEL_LEVEL_IN_PERCENTILE = 0;
    /**
     * Index in {@link #floatValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is fuel level in coverable distance. The unit is Km.
     * @hide
     */
    public static final int INDEX_FUEL_LEVEL_IN_DISTANCE = 1;
    /**
     * Index in {@link #intValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is set to 1 if fuel low level warning is on.
     * @hide
     */
    public static final int INDEX_FUEL_LOW_WARNING = 0;

    /**
     *  GEAR_* represents meaning of intValues[0] for {@link CarSensorManager#SENSOR_TYPE_GEAR}
     *  sensor type.
     *  GEAR_NEUTRAL means transmission gear is in neutral state, and the car may be moving.
     * @hide
     */
    public static final int GEAR_NEUTRAL    = 0;
    /**
     * intValues[0] from 1 to 99 represents transmission gear number for moving forward.
     * GEAR_FIRST is for gear number 1.
     * @hide
     */
    public static final int GEAR_FIRST      = 1;
    /** Gear number 2. @hide */
    public static final int GEAR_SECOND     = 2;
    /** Gear number 3. @hide */
    public static final int GEAR_THIRD      = 3;
    /** Gear number 4. @hide */
    public static final int GEAR_FOURTH     = 4;
    /** Gear number 5. @hide */
    public static final int GEAR_FIFTH      = 5;
    /** Gear number 6. @hide */
    public static final int GEAR_SIXTH      = 6;
    /** Gear number 7. @hide */
    public static final int GEAR_SEVENTH    = 7;
    /** Gear number 8. @hide */
    public static final int GEAR_EIGHTH     = 8;
    /** Gear number 9. @hide */
    public static final int GEAR_NINTH      = 9;
    /** Gear number 10. @hide */
    public static final int GEAR_TENTH      = 10;
    /**
     * This is for transmission without specific gear number for moving forward like CVT. It tells
     * that car is in a transmission state to move it forward.
     * @hide
     */
    public static final int GEAR_DRIVE      = 100;
    /** Gear in parking state @hide */
    public static final int GEAR_PARK       = 101;
    /** Gear in reverse @hide */
    public static final int GEAR_REVERSE    = 102;

    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_GYROSCOPE} in floatValues.
     * Rotation speed is in rad/s. Any component can be NaN if it is not available.
     */
    /**@hide*/
    public static final int INDEX_GYROSCOPE_X = 0;
    /**@hide*/
    public static final int INDEX_GYROSCOPE_Y = 1;
    /**@hide*/
    public static final int INDEX_GYROSCOPE_Z = 2;

    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_GPS_SATELLITE}.
     * Both byte values and float values are used.
     * Two first bytes encode number of satellites in-use/in-view (or 0xFF if unavailable).
     * Then optionally with INDEX_GPS_SATELLITE_ARRAY_BYTE_OFFSET offset and interval
     * INDEX_GPS_SATELLITE_ARRAY_BYTE_INTERVAL between elements are encoded boolean flags of 
     * whether particular satellite from in-view participate in in-use subset.
     * Float values with INDEX_GPS_SATELLITE_ARRAY_FLOAT_OFFSET offset and interval
     * INDEX_GPS_SATELLITE_ARRAY_FLOAT_INTERVAL between elements can optionally contain
     * per-satellite values of signal strength and other values or NaN if unavailable.
     */
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_NUMBER_IN_USE = 0;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_NUMBER_IN_VIEW = 1;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_ARRAY_INT_OFFSET = 2;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_ARRAY_INT_INTERVAL = 1;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_ARRAY_FLOAT_OFFSET = 0;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_ARRAY_FLOAT_INTERVAL = 4;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_PRN_OFFSET = 0;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_SNR_OFFSET = 1;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_AZIMUTH_OFFSET = 2;
    /**@hide*/
    public static final int INDEX_GPS_SATELLITE_ELEVATION_OFFSET = 3;


    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_LOCATION} in floatValues.
     * Each bit intValues[0] represents whether the corresponding data is present.
     */
    /**@hide*/
    public static final int INDEX_LOCATION_LATITUDE  = 0;
    /**@hide*/
    public static final int INDEX_LOCATION_LONGITUDE = 1;
    /**@hide*/
    public static final int INDEX_LOCATION_ACCURACY  = 2;
    /**@hide*/
    public static final int INDEX_LOCATION_ALTITUDE  = 3;
    /**@hide*/
    public static final int INDEX_LOCATION_SPEED     = 4;
    /**@hide*/
    public static final int INDEX_LOCATION_BEARING   = 5;
    /**@hide*/
    public static final int INDEX_LOCATION_MAX = INDEX_LOCATION_BEARING;
    /**@hide*/
    public static final int INDEX_LOCATION_LATITUDE_INTS = 1;
    /**@hide*/
    public static final int INDEX_LOCATION_LONGITUDE_INTS = 2;


    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT} in floatValues.
     * Temperature in Celsius degrees.
     * @hide
     */
    public static final int INDEX_ENVIRONMENT_TEMPERATURE = 0;
    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT} in floatValues.
     * Pressure in kPa.
     * @hide
     */
    public static final int INDEX_ENVIRONMENT_PRESSURE = 1;


    /**
     * Indices for {@link CarSensorManager#SENSOR_TYPE_ACCELEROMETER} in floatValues.
     * Acceleration (gravity) is in m/s^2. Any component can be NaN if it is not available.
     */
    /**@hide*/
    public static final int INDEX_ACCELEROMETER_X = 0;
    /**@hide*/
    public static final int INDEX_ACCELEROMETER_Y = 1;
    /**@hide*/
    public static final int INDEX_ACCELEROMETER_Z = 2;

    /** @hide */
    public static class EnvironmentData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** If unsupported by the car, this value is NaN. */
        public final float temperature;
        /** If unsupported by the car, this value is NaN. */
        public final float pressure;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public EnvironmentData(long timestamp, float temperature, float pressure) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.pressure = pressure;
        }
    }

    /**
     * Convenience method for obtaining an {@link EnvironmentData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT}.
     *
     * @return an EnvironmentData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public EnvironmentData getEnvironmentData() {
        checkType(CarSensorManager.SENSOR_TYPE_ENVIRONMENT);

        float temperature = floatValues[INDEX_ENVIRONMENT_TEMPERATURE];
        float pressure = floatValues[INDEX_ENVIRONMENT_PRESSURE];
        return new EnvironmentData(timestamp, temperature, pressure);
    }

    /** @hide */
    public static class GearData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        public final int gear;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public GearData(long timestamp, int gear) {
            this.timestamp = timestamp;
            this.gear = gear;
        }
    }

    /**
     * Convenience method for obtaining a {@link GearData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_GEAR}.
     *
     * @return a GearData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public GearData getGearData() {
        checkType(CarSensorManager.SENSOR_TYPE_GEAR);
        return new GearData(timestamp, intValues[0]);
    }

    /** @hide */
    public static class FuelLevelData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** Fuel level in %. If unsupported by the car, this value is -1. */
        public final int level;
        /** Fuel as possible range in Km. If unsupported by the car, this value is -1. */
        public final float range;
        /** If unsupported by the car, this value is false. */
        public final boolean lowFuelWarning;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public FuelLevelData(long timestamp, int level, float range, boolean lowFuelWarning) {
            this.timestamp = timestamp;
            this.level = level;
            this.range = range;
            this.lowFuelWarning = lowFuelWarning;
        }
    }

    /**
     * Convenience method for obtaining a {@link FuelLevelData} object from a CarSensorEvent object
     * with type {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL}.
     *
     * @return A FuelLevel object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public FuelLevelData getFuelLevelData() {
        checkType(CarSensorManager.SENSOR_TYPE_FUEL_LEVEL);
        int level = -1;
        float range = -1;
        if (floatValues != null) {
            if (floatValues[INDEX_FUEL_LEVEL_IN_PERCENTILE] >= 0) {
                level = (int) floatValues[INDEX_FUEL_LEVEL_IN_PERCENTILE];
            }

            if (floatValues[INDEX_FUEL_LEVEL_IN_DISTANCE] >= 0) {
                range = floatValues[INDEX_FUEL_LEVEL_IN_DISTANCE];
            }
        }
        boolean lowFuelWarning = (intValues[0] == 1);
        return new FuelLevelData(timestamp, level, range, lowFuelWarning);
    }

    /** @hide */
    public static class OdometerData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        public final float kms;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public OdometerData(long timestamp, float kms) {
            this.timestamp = timestamp;
            this.kms = kms;
        }
    }

    /**
     * Convenience method for obtaining an {@link OdometerData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ODOMETER}.
     *
     * @return an OdometerData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public OdometerData getOdometerData() {
        checkType(CarSensorManager.SENSOR_TYPE_ODOMETER);
            return new OdometerData(timestamp, floatValues[0]);
    }

    /** @hide */
    public static class RpmData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        public final float rpm;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public RpmData(long timestamp, float rpm) {
            this.timestamp = timestamp;
            this.rpm = rpm;
        }
    }

    /**
     * Convenience method for obtaining a {@link RpmData} object from a CarSensorEvent object with
     * type {@link CarSensorManager#SENSOR_TYPE_RPM}.
     *
     * @return An RpmData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public RpmData getRpmData() {
        checkType(CarSensorManager.SENSOR_TYPE_RPM);
        return new RpmData(timestamp, floatValues[0]);
    }

    /** @hide */
    public static class CarSpeedData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        public final float carSpeed;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public CarSpeedData(long timestamp, float carSpeed) {
            this.timestamp = timestamp;
            this.carSpeed = carSpeed;
        }
    }

    /**
     * Convenience method for obtaining a {@link CarSpeedData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_CAR_SPEED}.
     *
     * @return a CarSpeedData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public CarSpeedData getCarSpeedData() {
        checkType(CarSensorManager.SENSOR_TYPE_CAR_SPEED);
        return new CarSpeedData(timestamp, floatValues[0]);
    }

    /**
     * Convenience method for obtaining a {@link Location} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_LOCATION}.
     *
     * @param location an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a Location object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public Location getLocation(Location location) {
        checkType(CarSensorManager.SENSOR_TYPE_LOCATION);
        if (location == null) {
            location = new Location("Car-GPS");
        }
        // intValues[0]: bit flags for the presence of other values following.
        int presense = intValues[0];
        if ((presense & (0x1 << INDEX_LOCATION_LATITUDE)) != 0) {
            int latE7 = intValues[INDEX_LOCATION_LATITUDE_INTS];
            location.setLatitude(latE7 * 1e-7);
        }
        if ((presense & (0x1 << INDEX_LOCATION_LONGITUDE)) != 0) {
            int longE7 = intValues[INDEX_LOCATION_LONGITUDE_INTS];
            location.setLongitude(longE7 * 1e-7);
        }
        if ((presense & (0x1 << INDEX_LOCATION_ACCURACY)) != 0) {
            location.setAccuracy(floatValues[INDEX_LOCATION_ACCURACY]);
        }
        if ((presense & (0x1 << INDEX_LOCATION_ALTITUDE)) != 0) {
            location.setAltitude(floatValues[INDEX_LOCATION_ALTITUDE]);
        }
        if ((presense & (0x1 << INDEX_LOCATION_SPEED)) != 0) {
            location.setSpeed(floatValues[INDEX_LOCATION_SPEED]);
        }
        if ((presense & (0x1 << INDEX_LOCATION_BEARING)) != 0) {
            location.setBearing(floatValues[INDEX_LOCATION_BEARING]);
        }
        location.setElapsedRealtimeNanos(timestamp);
        // There is a risk of scheduler delaying 2nd elapsedRealtimeNs value.
        // But will not try to fix it assuming that is acceptable as UTC time's accuracy is not
        // guaranteed in Location data.
        long currentTimeMs = System.currentTimeMillis();
        long elapsedRealtimeNs = SystemClock.elapsedRealtimeNanos();
        location.setTime(
                currentTimeMs - (elapsedRealtimeNs - timestamp) / MILLI_IN_NANOS);
        return location;
    }

    /** @hide */
    public static class AccelerometerData  {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** If unsupported by the car, this value is NaN. */
        public final float x;
        /** If unsupported by the car, this value is NaN. */
        public final float y;
        /** If unsupported by the car, this value is NaN. */
        public final float z;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public AccelerometerData(long timestamp, float x, float y, float z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Convenience method for obtaining an {@link AccelerometerData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ACCELEROMETER}.
     *
     * @return An AccelerometerData object corresponding to the data contained in the
     * CarSensorEvent.
     * @hide
     */
    public AccelerometerData getAccelerometerData() {
        checkType(CarSensorManager.SENSOR_TYPE_ACCELEROMETER);
        float x = floatValues[INDEX_ACCELEROMETER_X];
        float y = floatValues[INDEX_ACCELEROMETER_Y];
        float z = floatValues[INDEX_ACCELEROMETER_Z];
        return new AccelerometerData(timestamp, x, y, z);
    }

    /** @hide */
    public static class GyroscopeData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /** If unsupported by the car, this value is NaN. */
        public final float x;
        /** If unsupported by the car, this value is NaN. */
        public final float y;
        /** If unsupported by the car, this value is NaN. */
        public final float z;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public GyroscopeData(long timestamp, float x, float y, float z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Convenience method for obtaining a {@link GyroscopeData} object from a CarSensorEvent object
     * with type {@link CarSensorManager#SENSOR_TYPE_GYROSCOPE}.
     *
     * @return A GyroscopeData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public GyroscopeData getGyroscopeData() {
        checkType(CarSensorManager.SENSOR_TYPE_GYROSCOPE);
        float x = floatValues[INDEX_GYROSCOPE_X];
        float y = floatValues[INDEX_GYROSCOPE_Y];
        float z = floatValues[INDEX_GYROSCOPE_Z];
        return new GyroscopeData(timestamp, x, y, z);
    }

    // android.location.GpsSatellite doesn't have a public constructor, so that can't be used.
    /**
     * Class that contains GPS satellite status. For more info on meaning of these fields refer
     * to the documentation to the {@link GpsSatellite} class.
     * @hide
     */
    public static class GpsSatelliteData {
        /** The time in nanoseconds since system boot. */
        public final long timestamp;
        /**
         * Number of satellites used in GPS fix or -1 of unavailable.
         */
        public final int numberInUse;
        /**
         * Number of satellites in view or -1 of unavailable.
         */
        public final int numberInView;
        /**
         * Per-satellite flag if this satellite was used for GPS fix.
         * Can be null if per-satellite data is unavailable.
         */
        public final boolean[] usedInFix;
        /**
         * Per-satellite pseudo-random id.
         * Can be null if per-satellite data is unavailable.
         */
        public final int[] prn;
        /**
         * Per-satellite signal to noise ratio.
         * Can be null if per-satellite data is unavailable.
         */
        public final float[] snr;
        /**
         * Per-satellite azimuth.
         * Can be null if per-satellite data is unavailable.
         */
        public final float[] azimuth;
        /**
         * Per-satellite elevation.
         * Can be null if per-satellite data is unavailable.
         */
        public final float[] elevation;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public GpsSatelliteData(long timestamp, int numberInUse, int numberInView,
                boolean[] usedInFix, int[] prn, float[] snr, float[] azimuth, float[] elevation) {
            this.timestamp = timestamp;
            this.numberInUse = numberInUse;
            this.numberInView = numberInView;
            this.usedInFix = usedInFix;
            this.prn = prn;
            this.snr = snr;
            this.azimuth = azimuth;
            this.elevation = elevation;
        }
    }

    private final int intOffset = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_OFFSET;
    private final int intInterval = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_INT_INTERVAL;
    private final int floatOffset = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_OFFSET;
    private final int floatInterval = CarSensorEvent.INDEX_GPS_SATELLITE_ARRAY_FLOAT_INTERVAL;

    /**
     * Convenience method for obtaining a {@link GpsSatelliteData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_GPS_SATELLITE} with optional 
     * per-satellite info.
     *
     * @param withPerSatellite whether to include per-satellite data.
     * @return a GpsSatelliteData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public GpsSatelliteData getGpsSatelliteData(boolean withPerSatellite) {
        checkType(CarSensorManager.SENSOR_TYPE_GPS_SATELLITE);

        //init all vars
        int numberInUse = intValues[CarSensorEvent.INDEX_GPS_SATELLITE_NUMBER_IN_USE];
        int numberInView = intValues[CarSensorEvent.INDEX_GPS_SATELLITE_NUMBER_IN_VIEW];
        boolean[] usedInFix = null;
        int[] prn = null;
        float[] snr = null;
        float[] azimuth = null;
        float[] elevation = null;

        if (withPerSatellite && numberInView >= 0) {
            final int numberOfSats = (floatValues.length - floatOffset) / floatInterval;
            usedInFix = new boolean[numberOfSats];
            prn = new int[numberOfSats];
            snr = new float[numberOfSats];
            azimuth = new float[numberOfSats];
            elevation = new float[numberOfSats];

            for (int i = 0; i < numberOfSats; ++i) {
                int iInt = intOffset + intInterval * i;
                int iFloat = floatOffset + floatInterval * i;
                usedInFix[i] = intValues[iInt] != 0;
                prn[i] = Math.round(
                        floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_PRN_OFFSET]);
                snr[i] =
                        floatValues[iFloat + CarSensorEvent.INDEX_GPS_SATELLITE_SNR_OFFSET];
                azimuth[i] = floatValues[iFloat
                        + CarSensorEvent.INDEX_GPS_SATELLITE_AZIMUTH_OFFSET];
                elevation[i] = floatValues[iFloat
                        + CarSensorEvent.INDEX_GPS_SATELLITE_ELEVATION_OFFSET];
            }
        }
        return new GpsSatelliteData(timestamp, numberInUse, numberInView, usedInFix, prn, snr,
                azimuth, elevation);
    }

    /** @hide */
    public static class CarWheelTickDistanceData {
        public final long timestamp;
        public final long sensorResetCount;
        public final long frontLeftWheelDistanceMm;
        public final long frontRightWheelDistanceMm;
        public final long rearRightWheelDistanceMm;
        public final long rearLeftWheelDistanceMm;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public CarWheelTickDistanceData(long timestamp, long sensorResetCount,
                                    long frontLeftWheelDistanceMm, long frontRightWheelDistanceMm,
                                    long rearRightWheelDistanceMm, long rearLeftWheelDistanceMm) {
            this.timestamp = timestamp;
            this.sensorResetCount = sensorResetCount;
            this.frontLeftWheelDistanceMm = frontLeftWheelDistanceMm;
            this.frontRightWheelDistanceMm = frontRightWheelDistanceMm;
            this.rearRightWheelDistanceMm = rearRightWheelDistanceMm;
            this.rearLeftWheelDistanceMm = rearLeftWheelDistanceMm;
        }
    }

    /**
     * Convenience method for obtaining a {@link CarWheelTickDistanceData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE}.
     *
     * @return CarWheelTickDistanceData object corresponding to data contained in the CarSensorEvent
     * @hide
     */
    public CarWheelTickDistanceData getCarWheelTickDistanceData() {
        checkType(CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE);
        long sensorResetCount = longValues[INDEX_WHEEL_DISTANCE_RESET_COUNT];
        long frontLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_LEFT];
        long frontRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_RIGHT];
        long rearRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_RIGHT];
        long rearLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_LEFT];
        return new CarWheelTickDistanceData(timestamp, sensorResetCount, frontLeftWheelDistanceMm,
            frontRightWheelDistanceMm, rearRightWheelDistanceMm, rearLeftWheelDistanceMm);
    }

    /** @hide */
    public static class CarAbsActiveData {
        public final long timestamp;
        public final boolean absIsActive;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public CarAbsActiveData(long timestamp, boolean absIsActive) {
            this.timestamp = timestamp;
            this.absIsActive = absIsActive;
        };
    }

    /**
     * Convenience method for obtaining a {@link CarAbsActiveData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ABS_ACTIVE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarAbsActiveData object corresponding to data contained in the CarSensorEvent.
     * @hide
     */
    public CarAbsActiveData getCarAbsActiveData() {
        checkType(CarSensorManager.SENSOR_TYPE_ABS_ACTIVE);
        boolean absIsActive = intValues[0] == 1;
        return new CarAbsActiveData(timestamp, absIsActive);
    }

    /** @hide */
    public static class CarTractionControlActiveData {
        public final long timestamp;
        public final boolean tractionControlIsActive;

        /** @hide */
        @RestrictTo(GROUP_ID)
        public CarTractionControlActiveData(long timestamp, boolean tractionControlIsActive) {
            this.timestamp = timestamp;
            this.tractionControlIsActive = tractionControlIsActive;
        };
    }

    /**
     * Convenience method for obtaining a {@link CarTractionControlActiveData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_TRACTION_CONTROL_ACTIVE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarTractionControlActiveData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    public CarTractionControlActiveData getCarTractionControlActiveData() {
        checkType(CarSensorManager.SENSOR_TYPE_TRACTION_CONTROL_ACTIVE);
        boolean tractionControlIsActive = intValues[0] == 1;
        return new CarTractionControlActiveData(timestamp, tractionControlIsActive);
    }

    /** @hide */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName() + "[");
        sb.append("type:" + Integer.toHexString(sensorType));
        if (floatValues != null && floatValues.length > 0) {
            sb.append(" float values:");
            for (float v: floatValues) {
                sb.append(" " + v);
            }
        }
        if (intValues != null && intValues.length > 0) {
            sb.append(" int values:");
            for (int v: intValues) {
                sb.append(" " + v);
            }
        }
        if (longValues != null && longValues.length > 0) {
            sb.append(" long values:");
            for (long v: longValues) {
                sb.append(" " + v);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
