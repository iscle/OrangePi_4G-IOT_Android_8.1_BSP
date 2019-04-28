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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A CarSensorEvent object corresponds to a single sensor event coming from the car. The sensor
 * data is stored in a sensor-type specific format in the object's float and byte arrays.
 *
 * To aid unmarshalling the object's data arrays, this class provides static nested classes and
 * conversion methods, for example {@link EnvironmentData} and {@link #getEnvironmentData}. The
 * conversion methods each have an optional data parameter which, if not null, will be used and
 * returned. This parameter should be used to avoid unnecessary object churn whenever possible.
 * Additionally, calling a conversion method on a CarSensorEvent object with an inappropriate type
 * will result in an {@code UnsupportedOperationException} being thrown.
 */
public class CarSensorEvent implements Parcelable {

    /**
     * Index in {@link #floatValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is fuel level in percentile.
     */
    public static final int INDEX_FUEL_LEVEL_IN_PERCENTILE = 0;
    /**
     * Index in {@link #floatValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is fuel level in coverable distance. The unit is Km.
     */
    public static final int INDEX_FUEL_LEVEL_IN_DISTANCE = 1;
    /**
     * Index in {@link #intValues} for {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL} type of
     * sensor. This value is set to 1 if fuel low level warning is on.
     */
    public static final int INDEX_FUEL_LOW_WARNING = 0;

    /**
     *  GEAR_* represents meaning of intValues[0] for {@link CarSensorManager#SENSOR_TYPE_GEAR}
     *  sensor type.
     *  GEAR_NEUTRAL means transmission gear is in neutral state, and the car may be moving.
     */
    public static final int GEAR_NEUTRAL    = 0;
    /**
     * intValues[0] from 1 to 99 represents transmission gear number for moving forward.
     * GEAR_FIRST is for gear number 1.
     */
    public static final int GEAR_FIRST      = 1;
    /** Gear number 2. */
    public static final int GEAR_SECOND     = 2;
    /** Gear number 3. */
    public static final int GEAR_THIRD      = 3;
    /** Gear number 4. */
    public static final int GEAR_FOURTH     = 4;
    /** Gear number 5. */
    public static final int GEAR_FIFTH      = 5;
    /** Gear number 6. */
    public static final int GEAR_SIXTH      = 6;
    /** Gear number 7. */
    public static final int GEAR_SEVENTH    = 7;
    /** Gear number 8. */
    public static final int GEAR_EIGHTH     = 8;
    /** Gear number 9. */
    public static final int GEAR_NINTH      = 9;
    /** Gear number 10. */
    public static final int GEAR_TENTH      = 10;
    /**
     * This is for transmission without specific gear number for moving forward like CVT. It tells
     * that car is in a transmission state to move it forward.
     */
    public static final int GEAR_DRIVE      = 100;
    /** Gear in parking state */
    public static final int GEAR_PARK       = 101;
    /** Gear in reverse */
    public static final int GEAR_REVERSE    = 102;

    /**
     * Ignition state is unknown.
     *
     * The constants that starts with IGNITION_STATE_ represent values for
     * {@link CarSensorManager#SENSOR_TYPE_IGNITION_STATE} sensor.
     * */
    public static final int IGNITION_STATE_UNDEFINED = 0;
    /**
     * Steering wheel is locked.
     */
    public static final int IGNITION_STATE_LOCK = 1;
    /** Typically engine is off, but steering wheel is unlocked. */
    public static final int IGNITION_STATE_OFF = 2;
    /** Accessory is turned off, but engine is not running yet (for EV car is not ready to move). */
    public static final int IGNITION_STATE_ACC = 3;
    /** In this state engine typically is running (for EV, car is ready to move). */
    public static final int IGNITION_STATE_ON = 4;
    /** In this state engine is typically starting (cranking). */
    public static final int IGNITION_STATE_START = 5;

    /**
     * Bitmask of driving restrictions.
     */
    /** No restrictions. */
    public static final int DRIVE_STATUS_UNRESTRICTED = 0;
    /** No video playback allowed. */
    public static final int DRIVE_STATUS_NO_VIDEO = 0x1;
    /** No keyboard or rotary controller input allowed. */
    public static final int DRIVE_STATUS_NO_KEYBOARD_INPUT = 0x2;
    /** No voice input allowed. */
    public static final int DRIVE_STATUS_NO_VOICE_INPUT = 0x4;
    /** No setup / configuration allowed. */
    public static final int DRIVE_STATUS_NO_CONFIG = 0x8;
    /** Limit displayed message length. */
    public static final int DRIVE_STATUS_LIMIT_MESSAGE_LEN = 0x10;
    /** represents case where all of the above items are restricted */
    public static final int DRIVE_STATUS_FULLY_RESTRICTED = DRIVE_STATUS_NO_VIDEO |
            DRIVE_STATUS_NO_KEYBOARD_INPUT | DRIVE_STATUS_NO_VOICE_INPUT | DRIVE_STATUS_NO_CONFIG |
            DRIVE_STATUS_LIMIT_MESSAGE_LEN;

    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT} in floatValues.
     * Temperature in Celsius degrees.
     */
    public static final int INDEX_ENVIRONMENT_TEMPERATURE = 0;
    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT} in floatValues.
     * Pressure in kPa.
     */
    public static final int INDEX_ENVIRONMENT_PRESSURE = 1;
    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE} in longValues. RESET_COUNT
     * is incremented whenever the HAL detects that a sensor reset has occurred.  It represents to
     * the upper layer that the WHEEL_DISTANCE values will not be contiguous with other values
     * reported with a different RESET_COUNT.
     */
    public static final int INDEX_WHEEL_DISTANCE_RESET_COUNT = 0;
    public static final int INDEX_WHEEL_DISTANCE_FRONT_LEFT = 1;
    public static final int INDEX_WHEEL_DISTANCE_FRONT_RIGHT = 2;
    public static final int INDEX_WHEEL_DISTANCE_REAR_RIGHT = 3;
    public static final int INDEX_WHEEL_DISTANCE_REAR_LEFT = 4;

    private static final long MILLI_IN_NANOS = 1000000L;

    /** Sensor type for this event like {@link CarSensorManager#SENSOR_TYPE_CAR_SPEED}. */
    public int sensorType;

    /**
     * When this data was received from car. It is elapsed real-time of data reception from car in
     * nanoseconds since system boot.
     */
    public long timestamp;
    /**
     * array holding float type of sensor data. If the sensor has single value, only floatValues[0]
     * should be used. */
    public final float[] floatValues;
    /** array holding int type of sensor data */
    public final int[] intValues;
    /** array holding long int type of sensor data */
    public final long[] longValues;

    /** @hide */
    public CarSensorEvent(Parcel in) {
        sensorType = in.readInt();
        timestamp = in.readLong();
        int len = in.readInt();
        floatValues = new float[len];
        in.readFloatArray(floatValues);
        len = in.readInt();
        intValues = new int[len];
        in.readIntArray(intValues);
        // version 1 up to here
        len = in.readInt();
        longValues = new long[len];
        in.readLongArray(longValues);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(sensorType);
        dest.writeLong(timestamp);
        dest.writeInt(floatValues.length);
        dest.writeFloatArray(floatValues);
        dest.writeInt(intValues.length);
        dest.writeIntArray(intValues);
        dest.writeInt(longValues.length);
        dest.writeLongArray(longValues);
    }

    public static final Parcelable.Creator<CarSensorEvent> CREATOR
    = new Parcelable.Creator<CarSensorEvent>() {
        public CarSensorEvent createFromParcel(Parcel in) {
            return new CarSensorEvent(in);
        }

        public CarSensorEvent[] newArray(int size) {
            return new CarSensorEvent[size];
        }
    };

    /** @hide */
    public CarSensorEvent(int sensorType, long timestamp, int floatValueSize, int intValueSize,
                          int longValueSize) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        floatValues = new float[floatValueSize];
        intValues = new int[intValueSize];
        longValues = new long[longValueSize];
    }

    /** @hide */
    CarSensorEvent(int sensorType, long timestamp, float[] floatValues, int[] intValues,
                   long[] longValues) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        this.floatValues = floatValues;
        this.intValues = intValues;
        this.longValues = longValues;
    }

    private void checkType(int type) {
        if (sensorType == type) {
            return;
        }
        throw new UnsupportedOperationException(String.format(
                "Invalid sensor type: expected %d, got %d", type, sensorType));
    }

    public static class EnvironmentData {
        public long timestamp;
        /** If unsupported by the car, this value is NaN. */
        public float temperature;
        /** If unsupported by the car, this value is NaN. */
        public float pressure;

        /** @hide */
        private EnvironmentData() {};
    }

    /**
     * Convenience method for obtaining an {@link EnvironmentData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ENVIRONMENT}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return an EnvironmentData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public EnvironmentData getEnvironmentData(EnvironmentData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ENVIRONMENT);
        if (data == null) {
            data = new EnvironmentData();
        }
        data.timestamp = timestamp;
        data.temperature = floatValues[INDEX_ENVIRONMENT_TEMPERATURE];
        data.pressure = floatValues[INDEX_ENVIRONMENT_PRESSURE];
        return data;
    }

    /** @hide */
    public static class NightData {
        public long timestamp;
        public boolean isNightMode;

        /** @hide */
        private NightData() {};
    }

    /**
     * Convenience method for obtaining a {@link NightData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_NIGHT}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a NightData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public NightData getNightData(NightData data) {
        checkType(CarSensorManager.SENSOR_TYPE_NIGHT);
        if (data == null) {
            data = new NightData();
        }
        data.timestamp = timestamp;
        data.isNightMode = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class GearData {
        public long timestamp;
        public int gear;

        /** @hide */
        private GearData() {};
    }

    /**
     * Convenience method for obtaining a {@link GearData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_GEAR}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a GearData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public GearData getGearData(GearData data) {
        checkType(CarSensorManager.SENSOR_TYPE_GEAR);
        if (data == null) {
            data = new GearData();
        }
        data.timestamp = timestamp;
        data.gear = intValues[0];
        return data;
    }

    /** @hide */
    public static class ParkingBrakeData {
        public long timestamp;
        public boolean isEngaged;

        /** @hide */
        private ParkingBrakeData() {}
    }

    /**
     * Convenience method for obtaining a {@link ParkingBrakeData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_PARKING_BRAKE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a ParkingBreakData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public ParkingBrakeData getParkingBrakeData(ParkingBrakeData data) {
        checkType(CarSensorManager.SENSOR_TYPE_PARKING_BRAKE);
        if (data == null) {
            data = new ParkingBrakeData();
        }
        data.timestamp = timestamp;
        data.isEngaged = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class FuelLevelData {
        public long timestamp;
        /** Fuel level in %. If unsupported by the car, this value is -1. */
        public int level;
        /** Fuel as possible range in Km. If unsupported by the car, this value is -1. */
        public float range;
        /** If unsupported by the car, this value is false. */
        public boolean lowFuelWarning;

        /** @hide */
        private FuelLevelData() {};
    }

    /**
     * Convenience method for obtaining a {@link FuelLevelData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a FuelLevel object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public FuelLevelData getFuelLevelData(FuelLevelData data) {
        checkType(CarSensorManager.SENSOR_TYPE_FUEL_LEVEL);
        if (data == null) {
            data = new FuelLevelData();
        }
        data.timestamp = timestamp;
        if (floatValues == null) {
            data.level = -1;
            data.range = -1;
        } else {
            if (floatValues[INDEX_FUEL_LEVEL_IN_PERCENTILE] < 0) {
                data.level = -1;
            } else {
                data.level = (int) floatValues[INDEX_FUEL_LEVEL_IN_PERCENTILE];
            }
            if (floatValues[INDEX_FUEL_LEVEL_IN_DISTANCE] < 0) {
                data.range = -1;
            } else {
                data.range = floatValues[INDEX_FUEL_LEVEL_IN_DISTANCE];
            }
        }
        data.lowFuelWarning = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class OdometerData {
        public long timestamp;
        public float kms;

        /** @hide */
        private OdometerData() {};
    }

    /**
     * Convenience method for obtaining an {@link OdometerData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ODOMETER}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return an OdometerData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public OdometerData getOdometerData(OdometerData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ODOMETER);
        if (data == null) {
            data = new OdometerData();
        }
        data.timestamp = timestamp;
        data.kms = floatValues[0];
        return data;
    }

    /** @hide */
    public static class RpmData {
        public long timestamp;
        public float rpm;

        /** @hide */
        private RpmData() {};
    }

    /**
     * Convenience method for obtaining a {@link RpmData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_RPM}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a RpmData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public RpmData getRpmData(RpmData data) {
        checkType(CarSensorManager.SENSOR_TYPE_RPM);
        if (data == null) {
            data = new RpmData();
        }
        data.timestamp = timestamp;
        data.rpm = floatValues[0];
        return data;
    }

    /** @hide */
    public static class CarSpeedData {
        public long timestamp;
        public float carSpeed;

        /** @hide */
        private CarSpeedData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarSpeedData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_CAR_SPEED}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarSpeedData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public CarSpeedData getCarSpeedData(CarSpeedData data) {
        checkType(CarSensorManager.SENSOR_TYPE_CAR_SPEED);
        if (data == null) {
            data = new CarSpeedData();
        }
        data.timestamp = timestamp;
        data.carSpeed = floatValues[0];
        return data;
    }

    /** @hide */
    public static class DrivingStatusData {
        public long timestamp;
        public int status;

        /** @hide */
        private DrivingStatusData() {};
    }

    /**
     * Convenience method for obtaining a {@link DrivingStatusData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_DRIVING_STATUS}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a DrivingStatusData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    public DrivingStatusData getDrivingStatusData(DrivingStatusData data) {
        checkType(CarSensorManager.SENSOR_TYPE_DRIVING_STATUS);
        if (data == null) {
            data = new DrivingStatusData();
        }
        data.status = intValues[0];
        return data;
    }

    /** @hide */
    public static class CarWheelTickDistanceData {
        public long timestamp;
        public long sensorResetCount;
        public long frontLeftWheelDistanceMm;
        public long frontRightWheelDistanceMm;
        public long rearRightWheelDistanceMm;
        public long rearLeftWheelDistanceMm;

        /** @hide */
        private CarWheelTickDistanceData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarWheelTickDistanceData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return CarWheelTickDistanceData object corresponding to data contained in the CarSensorEvent
     * @hide
     */
    public CarWheelTickDistanceData getCarWheelTickDistanceData(CarWheelTickDistanceData data) {
        checkType(CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE);
        if (data == null) {
            data = new CarWheelTickDistanceData();
        }
        data.timestamp = timestamp;
        data.sensorResetCount = longValues[INDEX_WHEEL_DISTANCE_RESET_COUNT];
        data.frontLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_LEFT];
        data.frontRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_RIGHT];
        data.rearRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_RIGHT];
        data.rearLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_LEFT];
        return data;
    }

    /** @hide */
    public static class CarAbsActiveData {
        public long timestamp;
        public boolean absIsActive;

        /** @hide */
        private CarAbsActiveData() {};
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
    public CarAbsActiveData getCarAbsActiveData(CarAbsActiveData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ABS_ACTIVE);
        if (data == null) {
            data = new CarAbsActiveData();
        }
        data.timestamp = timestamp;
        data.absIsActive = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarTractionControlActiveData {
        public long timestamp;
        public boolean tractionControlIsActive;

        /** @hide */
        private CarTractionControlActiveData() {};
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
    public CarTractionControlActiveData getCarTractionControlActiveData(
            CarTractionControlActiveData data) {
        checkType(CarSensorManager.SENSOR_TYPE_TRACTION_CONTROL_ACTIVE);
        if (data == null) {
            data = new CarTractionControlActiveData();
        }
        data.timestamp = timestamp;
        data.tractionControlIsActive = intValues[0] == 1;
        return data;
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
