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

package android.car.diagnostic;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonWriter;
import android.util.SparseArray;
import android.util.SparseIntArray;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * A CarDiagnosticEvent object corresponds to a single diagnostic event frame coming from the car.
 *
 * @hide
 */
@SystemApi
public class CarDiagnosticEvent implements Parcelable {
    /** Whether this frame represents a live or a freeze frame */
    public final int frameType;

    /**
     * When this data was acquired in car or received from car. It is elapsed real-time of data
     * reception from car in nanoseconds since system boot.
     */
    public final long timestamp;

    /**
     * Sparse array that contains the mapping of OBD2 diagnostic properties to their values for
     * integer valued properties
     */
    private final SparseIntArray intValues;

    /**
     * Sparse array that contains the mapping of OBD2 diagnostic properties to their values for
     * float valued properties
     */
    private final SparseArray<Float> floatValues;

    /**
     * Diagnostic Troubleshooting Code (DTC) that was detected and caused this frame to be stored
     * (if a freeze frame). Always null for a live frame.
     */
    public final String dtc;

    public CarDiagnosticEvent(Parcel in) {
        frameType = in.readInt();
        timestamp = in.readLong();
        int len = in.readInt();
        floatValues = new SparseArray<>(len);
        for (int i = 0; i < len; ++i) {
            int key = in.readInt();
            float value = in.readFloat();
            floatValues.put(key, value);
        }
        len = in.readInt();
        intValues = new SparseIntArray(len);
        for (int i = 0; i < len; ++i) {
            int key = in.readInt();
            int value = in.readInt();
            intValues.put(key, value);
        }
        dtc = (String) in.readValue(String.class.getClassLoader());
        // version 1 up to here
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(frameType);
        dest.writeLong(timestamp);
        dest.writeInt(floatValues.size());
        for (int i = 0; i < floatValues.size(); ++i) {
            int key = floatValues.keyAt(i);
            dest.writeInt(key);
            dest.writeFloat(floatValues.get(key));
        }
        dest.writeInt(intValues.size());
        for (int i = 0; i < intValues.size(); ++i) {
            int key = intValues.keyAt(i);
            dest.writeInt(key);
            dest.writeInt(intValues.get(key));
        }
        dest.writeValue(dtc);
    }

    /**
     * Store the contents of this diagnostic event in a JsonWriter.
     *
     * The data is stored as a JSON object, with these fields:
     *  type: either "live" or "freeze" depending on the type of frame;
     *  timestamp: the timestamp at which this frame was generated;
     *  intValues: an array of objects each of which has two elements:
     *    id: the integer identifier of the sensor;
     *    value: the integer value of the sensor;
     *  floatValues: an array of objects each of which has two elements:
     *    id: the integer identifier of the sensor;
     *    value: the floating-point value of the sensor;
     *  stringValue: the DTC for a freeze frame, omitted for a live frame
     */
    public void writeToJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();

        jsonWriter.name("type");
        switch (frameType) {
            case CarDiagnosticManager.FRAME_TYPE_LIVE:
                jsonWriter.value("live");
                break;
            case CarDiagnosticManager.FRAME_TYPE_FREEZE:
                jsonWriter.value("freeze");
                break;
            default:
                throw new IllegalStateException("unknown frameType " + frameType);
        }

        jsonWriter.name("timestamp").value(timestamp);

        jsonWriter.name("intValues").beginArray();
        for (int i = 0; i < intValues.size(); ++i) {
            jsonWriter.beginObject();
            jsonWriter.name("id").value(intValues.keyAt(i));
            jsonWriter.name("value").value(intValues.valueAt(i));
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        jsonWriter.name("floatValues").beginArray();
        for (int i = 0; i < floatValues.size(); ++i) {
            jsonWriter.beginObject();
            jsonWriter.name("id").value(floatValues.keyAt(i));
            jsonWriter.name("value").value(floatValues.valueAt(i));
            jsonWriter.endObject();
        }
        jsonWriter.endArray();

        if (dtc != null) {
            jsonWriter.name("stringValue").value(dtc);
        }

        jsonWriter.endObject();
    }

    public static final Parcelable.Creator<CarDiagnosticEvent> CREATOR =
            new Parcelable.Creator<CarDiagnosticEvent>() {
                public CarDiagnosticEvent createFromParcel(Parcel in) {
                    return new CarDiagnosticEvent(in);
                }

                public CarDiagnosticEvent[] newArray(int size) {
                    return new CarDiagnosticEvent[size];
                }
            };

    private CarDiagnosticEvent(
            int frameType,
            long timestamp,
            SparseArray<Float> floatValues,
            SparseIntArray intValues,
            String dtc) {
        this.frameType = frameType;
        this.timestamp = timestamp;
        this.floatValues = floatValues;
        this.intValues = intValues;
        this.dtc = dtc;
    }

    /**
     * This class can be used to incrementally construct a CarDiagnosticEvent.
     * CarDiagnosticEvent instances are immutable once built.
     */
    public static class Builder {
        private int mType = CarDiagnosticManager.FRAME_TYPE_LIVE;
        private long mTimestamp = 0;
        private SparseArray<Float> mFloatValues = new SparseArray<>();
        private SparseIntArray mIntValues = new SparseIntArray();
        private String mDtc = null;

        private Builder(int type) {
            mType = type;
        }

        /** Returns a new Builder for a live frame */
        public static Builder newLiveFrameBuilder() {
            return new Builder(CarDiagnosticManager.FRAME_TYPE_LIVE);
        }

        /** Returns a new Builder for a freeze frame */
        public static Builder newFreezeFrameBuilder() {
            return new Builder(CarDiagnosticManager.FRAME_TYPE_FREEZE);
        }

        /** Sets the timestamp for the frame being built */
        public Builder atTimestamp(long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        /** Adds an integer-valued sensor to the frame being built */
        public Builder withIntValue(int key, int value) {
            mIntValues.put(key, value);
            return this;
        }

        /** Adds a float-valued sensor to the frame being built */
        public Builder withFloatValue(int key, float value) {
            mFloatValues.put(key, value);
            return this;
        }

        /** Sets the DTC for the frame being built */
        public Builder withDtc(String dtc) {
            mDtc = dtc;
            return this;
        }

        /** Builds and returns the CarDiagnosticEvent */
        public CarDiagnosticEvent build() {
            return new CarDiagnosticEvent(mType, mTimestamp, mFloatValues, mIntValues, mDtc);
        }
    }

    /**
     * Returns a copy of this CarDiagnosticEvent with all vendor-specific sensors removed.
     *
     * @hide
     */
    public CarDiagnosticEvent withVendorSensorsRemoved() {
        SparseIntArray newIntValues = intValues.clone();
        SparseArray<Float> newFloatValues = floatValues.clone();
        for (int i = 0; i < intValues.size(); ++i) {
            int key = intValues.keyAt(i);
            if (key >= android.car.diagnostic.IntegerSensorIndex.LAST_SYSTEM) {
                newIntValues.delete(key);
            }
        }
        for (int i = 0; i < floatValues.size(); ++i) {
            int key = floatValues.keyAt(i);
            if (key >= android.car.diagnostic.FloatSensorIndex.LAST_SYSTEM) {
                newFloatValues.delete(key);
            }
        }
        return new CarDiagnosticEvent(frameType, timestamp, newFloatValues, newIntValues, dtc);
    }

    /** Returns true if this object is a live frame, false otherwise */
    public boolean isLiveFrame() {
        return CarDiagnosticManager.FRAME_TYPE_LIVE == frameType;
    }

    /** Returns true if this object is a freeze frame, false otherwise */
    public boolean isFreezeFrame() {
        return CarDiagnosticManager.FRAME_TYPE_FREEZE == frameType;
    }

    /** @hide */
    public boolean isEmptyFrame() {
        boolean empty = (0 == intValues.size());
        empty &= (0 == floatValues.size());
        if (isFreezeFrame()) empty &= dtc.isEmpty();
        return empty;
    }

    /** @hide */
    public CarDiagnosticEvent checkLiveFrame() {
        if (!isLiveFrame()) throw new IllegalStateException("frame is not a live frame");
        return this;
    }

    /** @hide */
    public CarDiagnosticEvent checkFreezeFrame() {
        if (!isFreezeFrame()) throw new IllegalStateException("frame is not a freeze frame");
        return this;
    }

    /** @hide */
    public boolean isEarlierThan(CarDiagnosticEvent otherEvent) {
        otherEvent = Objects.requireNonNull(otherEvent);
        return (timestamp < otherEvent.timestamp);
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (null == otherObject) {
            return false;
        }
        if (!(otherObject instanceof CarDiagnosticEvent)) {
            return false;
        }
        CarDiagnosticEvent otherEvent = (CarDiagnosticEvent)otherObject;
        if (otherEvent.frameType != frameType)
            return false;
        if (otherEvent.timestamp != timestamp)
            return false;
        if (otherEvent.intValues.size() != intValues.size())
            return false;
        if (otherEvent.floatValues.size() != floatValues.size())
            return false;
        if (!Objects.equals(dtc, otherEvent.dtc))
            return false;
        for (int i = 0; i < intValues.size(); ++i) {
            int key = intValues.keyAt(i);
            int otherKey = otherEvent.intValues.keyAt(i);
            if (key != otherKey) {
                return false;
            }
            int value = intValues.valueAt(i);
            int otherValue = otherEvent.intValues.valueAt(i);
            if (value != otherValue) {
                return false;
            }
        }
        for (int i = 0; i < floatValues.size(); ++i) {
            int key = floatValues.keyAt(i);
            int otherKey = otherEvent.floatValues.keyAt(i);
            if (key != otherKey) {
                return false;
            }
            float value = floatValues.valueAt(i);
            float otherValue = otherEvent.floatValues.valueAt(i);
            if (value != otherValue) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        Integer[] intKeys = new Integer[intValues.size()];
        Integer[] floatKeys = new Integer[floatValues.size()];
        Integer[] intValues = new Integer[intKeys.length];
        Float[] floatValues = new Float[floatKeys.length];
        for (int i = 0; i < intKeys.length; ++i) {
            intKeys[i] = this.intValues.keyAt(i);
            intValues[i] = this.intValues.valueAt(i);
        }
        for (int i = 0; i < floatKeys.length; ++i) {
            floatKeys[i] = this.floatValues.keyAt(i);
            floatValues[i] = this.floatValues.valueAt(i);
        }
        int intKeysHash = Objects.hash((Object[])intKeys);
        int intValuesHash = Objects.hash((Object[])intValues);
        int floatKeysHash = Objects.hash((Object[])floatKeys);
        int floatValuesHash = Objects.hash((Object[])floatValues);
        return Objects.hash(frameType,
                timestamp,
                dtc,
                intKeysHash,
                intValuesHash,
                floatKeysHash,
                floatValuesHash);
    }

    @Override
    public String toString() {
        return String.format(
                "%s diagnostic frame {\n"
                        + "\ttimestamp: %d, "
                        + "DTC: %s\n"
                        + "\tintValues: %s\n"
                        + "\tfloatValues: %s\n}",
                isLiveFrame() ? "live" : "freeze",
                timestamp,
                dtc,
                intValues.toString(),
                floatValues.toString());
    }

    /**
     * Returns the value of the given integer sensor, if present in this frame.
     * Returns defaultValue otherwise.
     */
    public int getSystemIntegerSensor(
            @android.car.diagnostic.IntegerSensorIndex.SensorIndex int sensor, int defaultValue) {
        return intValues.get(sensor, defaultValue);
    }

    /**
     * Returns the value of the given float sensor, if present in this frame.
     * Returns defaultValue otherwise.
     */
    public float getSystemFloatSensor(
            @android.car.diagnostic.FloatSensorIndex.SensorIndex int sensor, float defaultValue) {
        return floatValues.get(sensor, defaultValue);
    }

    /**
     * Returns the value of the given integer sensor, if present in this frame.
     * Returns defaultValue otherwise.
     */
    public int getVendorIntegerSensor(int sensor, int defaultValue) {
        return intValues.get(sensor, defaultValue);
    }

    /**
     * Returns the value of the given float sensor, if present in this frame.
     * Returns defaultValue otherwise.
     */
    public float getVendorFloatSensor(int sensor, float defaultValue) {
        return floatValues.get(sensor, defaultValue);
    }

    /**
     * Returns the value of the given integer sensor, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable Integer getSystemIntegerSensor(
            @android.car.diagnostic.IntegerSensorIndex.SensorIndex int sensor) {
        int index = intValues.indexOfKey(sensor);
        if (index < 0) return null;
        return intValues.valueAt(index);
    }

    /**
     * Returns the value of the given float sensor, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable Float getSystemFloatSensor(
            @android.car.diagnostic.FloatSensorIndex.SensorIndex int sensor) {
        int index = floatValues.indexOfKey(sensor);
        if (index < 0) return null;
        return floatValues.valueAt(index);
    }

    /**
     * Returns the value of the given integer sensor, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable Integer getVendorIntegerSensor(int sensor) {
        int index = intValues.indexOfKey(sensor);
        if (index < 0) return null;
        return intValues.valueAt(index);
    }

    /**
     * Returns the value of the given float sensor, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable Float getVendorFloatSensor(int sensor) {
        int index = floatValues.indexOfKey(sensor);
        if (index < 0) return null;
        return floatValues.valueAt(index);
    }

    /**
     * Represents possible states of the fuel system; see {@link
     * android.car.diagnostic.IntegerSensorIndex#FUEL_SYSTEM_STATUS}
     */
    public static final class FuelSystemStatus {
        private FuelSystemStatus() {}

        public static final int OPEN_INSUFFICIENT_ENGINE_TEMPERATURE = 1;
        public static final int CLOSED_LOOP = 2;
        public static final int OPEN_ENGINE_LOAD_OR_DECELERATION = 4;
        public static final int OPEN_SYSTEM_FAILURE = 8;
        public static final int CLOSED_LOOP_BUT_FEEDBACK_FAULT = 16;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
            OPEN_INSUFFICIENT_ENGINE_TEMPERATURE,
            CLOSED_LOOP,
            OPEN_ENGINE_LOAD_OR_DECELERATION,
            OPEN_SYSTEM_FAILURE,
            CLOSED_LOOP_BUT_FEEDBACK_FAULT
        })
        /** @hide */
        public @interface Status {}
    }

    /**
     * Represents possible states of the secondary air system; see {@link
     * android.car.diagnostic.IntegerSensorIndex#COMMANDED_SECONDARY_AIR_STATUS}
     */
    public static final class SecondaryAirStatus {
        private SecondaryAirStatus() {}

        public static final int UPSTREAM = 1;
        public static final int DOWNSTREAM_OF_CATALYCIC_CONVERTER = 2;
        public static final int FROM_OUTSIDE_OR_OFF = 4;
        public static final int PUMP_ON_FOR_DIAGNOSTICS = 8;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
            UPSTREAM,
            DOWNSTREAM_OF_CATALYCIC_CONVERTER,
            FROM_OUTSIDE_OR_OFF,
            PUMP_ON_FOR_DIAGNOSTICS
        })
        /** @hide */
        public @interface Status {}
    }

    /**
     * Represents possible types of fuel; see {@link
     * android.car.diagnostic.IntegerSensorIndex#FUEL_TYPE}
     */
    public static final class FuelType {
        private FuelType() {}

        public static final int NOT_AVAILABLE = 0;
        public static final int GASOLINE = 1;
        public static final int METHANOL = 2;
        public static final int ETHANOL = 3;
        public static final int DIESEL = 4;
        public static final int LPG = 5;
        public static final int CNG = 6;
        public static final int PROPANE = 7;
        public static final int ELECTRIC = 8;
        public static final int BIFUEL_RUNNING_GASOLINE = 9;
        public static final int BIFUEL_RUNNING_METHANOL = 10;
        public static final int BIFUEL_RUNNING_ETHANOL = 11;
        public static final int BIFUEL_RUNNING_LPG = 12;
        public static final int BIFUEL_RUNNING_CNG = 13;
        public static final int BIFUEL_RUNNING_PROPANE = 14;
        public static final int BIFUEL_RUNNING_ELECTRIC = 15;
        public static final int BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION = 16;
        public static final int HYBRID_GASOLINE = 17;
        public static final int HYBRID_ETHANOL = 18;
        public static final int HYBRID_DIESEL = 19;
        public static final int HYBRID_ELECTRIC = 20;
        public static final int HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION = 21;
        public static final int HYBRID_REGENERATIVE = 22;
        public static final int BIFUEL_RUNNING_DIESEL = 23;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
            NOT_AVAILABLE,
            GASOLINE,
            METHANOL,
            ETHANOL,
            DIESEL,
            LPG,
            CNG,
            PROPANE,
            ELECTRIC,
            BIFUEL_RUNNING_GASOLINE,
            BIFUEL_RUNNING_METHANOL,
            BIFUEL_RUNNING_ETHANOL,
            BIFUEL_RUNNING_LPG,
            BIFUEL_RUNNING_CNG,
            BIFUEL_RUNNING_PROPANE,
            BIFUEL_RUNNING_ELECTRIC,
            BIFUEL_RUNNING_ELECTRIC_AND_COMBUSTION,
            HYBRID_GASOLINE,
            HYBRID_ETHANOL,
            HYBRID_DIESEL,
            HYBRID_ELECTRIC,
            HYBRID_RUNNING_ELECTRIC_AND_COMBUSTION,
            HYBRID_REGENERATIVE,
            BIFUEL_RUNNING_DIESEL
        })
        /** @hide */
        public @interface Type {}
    }

    /**
     * Represents the state of an ignition monitor on a vehicle.
     */
    public static final class IgnitionMonitor {
        public final boolean available;
        public final boolean incomplete;

        IgnitionMonitor(boolean available, boolean incomplete) {
            this.available = available;
            this.incomplete = incomplete;
        }

        /** @hide */
        public static final class Decoder {
            private final int mAvailableBitmask;
            private final int mIncompleteBitmask;

            Decoder(int availableBitmask, int incompleteBitmask) {
                mAvailableBitmask = availableBitmask;
                mIncompleteBitmask = incompleteBitmask;
            }

            public IgnitionMonitor fromValue(int value) {
                boolean available = (0 != (value & mAvailableBitmask));
                boolean incomplete = (0 != (value & mIncompleteBitmask));

                return new IgnitionMonitor(available, incomplete);
            }
        }
    }

    /**
     * Contains information about ignition monitors common to all vehicle types.
     */
    public static class CommonIgnitionMonitors {
        public final IgnitionMonitor components;
        public final IgnitionMonitor fuelSystem;
        public final IgnitionMonitor misfire;

        /** @hide */
        public static final int COMPONENTS_AVAILABLE = 0x1 << 0;
        /** @hide */
        public static final int COMPONENTS_INCOMPLETE = 0x1 << 1;

        /** @hide */
        public static final int FUEL_SYSTEM_AVAILABLE = 0x1 << 2;
        /** @hide */
        public static final int FUEL_SYSTEM_INCOMPLETE = 0x1 << 3;

        /** @hide */
        public static final int MISFIRE_AVAILABLE = 0x1 << 4;
        /** @hide */
        public static final int MISFIRE_INCOMPLETE = 0x1 << 5;

        static final IgnitionMonitor.Decoder COMPONENTS_DECODER =
                new IgnitionMonitor.Decoder(COMPONENTS_AVAILABLE, COMPONENTS_INCOMPLETE);

        static final IgnitionMonitor.Decoder FUEL_SYSTEM_DECODER =
                new IgnitionMonitor.Decoder(FUEL_SYSTEM_AVAILABLE, FUEL_SYSTEM_INCOMPLETE);

        static final IgnitionMonitor.Decoder MISFIRE_DECODER =
                new IgnitionMonitor.Decoder(MISFIRE_AVAILABLE, MISFIRE_INCOMPLETE);

        CommonIgnitionMonitors(int bitmask) {
            components = COMPONENTS_DECODER.fromValue(bitmask);
            fuelSystem = FUEL_SYSTEM_DECODER.fromValue(bitmask);
            misfire = MISFIRE_DECODER.fromValue(bitmask);
        }

        /**
         * Returns data about ignition monitors specific to spark vehicles, if this
         * object represents ignition monitors for a spark vehicle.
         * Returns null otherwise.
         */
        public @Nullable SparkIgnitionMonitors asSparkIgnitionMonitors() {
            if (this instanceof SparkIgnitionMonitors) return (SparkIgnitionMonitors) this;
            return null;
        }

        /**
         * Returns data about ignition monitors specific to compression vehicles, if this
         * object represents ignition monitors for a compression vehicle.
         * Returns null otherwise.
         */
        public @Nullable CompressionIgnitionMonitors asCompressionIgnitionMonitors() {
            if (this instanceof CompressionIgnitionMonitors)
                return (CompressionIgnitionMonitors) this;
            return null;
        }
    }

    /**
     * Contains information about ignition monitors specific to spark vehicles.
     */
    public static final class SparkIgnitionMonitors extends CommonIgnitionMonitors {
        public final IgnitionMonitor EGR;
        public final IgnitionMonitor oxygenSensorHeater;
        public final IgnitionMonitor oxygenSensor;
        public final IgnitionMonitor ACRefrigerant;
        public final IgnitionMonitor secondaryAirSystem;
        public final IgnitionMonitor evaporativeSystem;
        public final IgnitionMonitor heatedCatalyst;
        public final IgnitionMonitor catalyst;

        /** @hide */
        public static final int EGR_AVAILABLE = 0x1 << 6;
        /** @hide */
        public static final int EGR_INCOMPLETE = 0x1 << 7;

        /** @hide */
        public static final int OXYGEN_SENSOR_HEATER_AVAILABLE = 0x1 << 8;
        /** @hide */
        public static final int OXYGEN_SENSOR_HEATER_INCOMPLETE = 0x1 << 9;

        /** @hide */
        public static final int OXYGEN_SENSOR_AVAILABLE = 0x1 << 10;
        /** @hide */
        public static final int OXYGEN_SENSOR_INCOMPLETE = 0x1 << 11;

        /** @hide */
        public static final int AC_REFRIGERANT_AVAILABLE = 0x1 << 12;
        /** @hide */
        public static final int AC_REFRIGERANT_INCOMPLETE = 0x1 << 13;

        /** @hide */
        public static final int SECONDARY_AIR_SYSTEM_AVAILABLE = 0x1 << 14;
        /** @hide */
        public static final int SECONDARY_AIR_SYSTEM_INCOMPLETE = 0x1 << 15;

        /** @hide */
        public static final int EVAPORATIVE_SYSTEM_AVAILABLE = 0x1 << 16;
        /** @hide */
        public static final int EVAPORATIVE_SYSTEM_INCOMPLETE = 0x1 << 17;

        /** @hide */
        public static final int HEATED_CATALYST_AVAILABLE = 0x1 << 18;
        /** @hide */
        public static final int HEATED_CATALYST_INCOMPLETE = 0x1 << 19;

        /** @hide */
        public static final int CATALYST_AVAILABLE = 0x1 << 20;
        /** @hide */
        public static final int CATALYST_INCOMPLETE = 0x1 << 21;

        static final IgnitionMonitor.Decoder EGR_DECODER =
                new IgnitionMonitor.Decoder(EGR_AVAILABLE, EGR_INCOMPLETE);

        static final IgnitionMonitor.Decoder OXYGEN_SENSOR_HEATER_DECODER =
                new IgnitionMonitor.Decoder(OXYGEN_SENSOR_HEATER_AVAILABLE,
                        OXYGEN_SENSOR_HEATER_INCOMPLETE);

        static final IgnitionMonitor.Decoder OXYGEN_SENSOR_DECODER =
                new IgnitionMonitor.Decoder(OXYGEN_SENSOR_AVAILABLE, OXYGEN_SENSOR_INCOMPLETE);

        static final IgnitionMonitor.Decoder AC_REFRIGERANT_DECODER =
                new IgnitionMonitor.Decoder(AC_REFRIGERANT_AVAILABLE,
                        AC_REFRIGERANT_INCOMPLETE);

        static final IgnitionMonitor.Decoder SECONDARY_AIR_SYSTEM_DECODER =
                new IgnitionMonitor.Decoder(SECONDARY_AIR_SYSTEM_AVAILABLE,
                        SECONDARY_AIR_SYSTEM_INCOMPLETE);

        static final IgnitionMonitor.Decoder EVAPORATIVE_SYSTEM_DECODER =
                new IgnitionMonitor.Decoder(EVAPORATIVE_SYSTEM_AVAILABLE,
                        EVAPORATIVE_SYSTEM_INCOMPLETE);

        static final IgnitionMonitor.Decoder HEATED_CATALYST_DECODER =
                new IgnitionMonitor.Decoder(HEATED_CATALYST_AVAILABLE,
                        HEATED_CATALYST_INCOMPLETE);

        static final IgnitionMonitor.Decoder CATALYST_DECODER =
                new IgnitionMonitor.Decoder(CATALYST_AVAILABLE, CATALYST_INCOMPLETE);

        SparkIgnitionMonitors(int bitmask) {
            super(bitmask);
            EGR = EGR_DECODER.fromValue(bitmask);
            oxygenSensorHeater = OXYGEN_SENSOR_HEATER_DECODER.fromValue(bitmask);
            oxygenSensor = OXYGEN_SENSOR_DECODER.fromValue(bitmask);
            ACRefrigerant = AC_REFRIGERANT_DECODER.fromValue(bitmask);
            secondaryAirSystem = SECONDARY_AIR_SYSTEM_DECODER.fromValue(bitmask);
            evaporativeSystem = EVAPORATIVE_SYSTEM_DECODER.fromValue(bitmask);
            heatedCatalyst = HEATED_CATALYST_DECODER.fromValue(bitmask);
            catalyst = CATALYST_DECODER.fromValue(bitmask);
        }
    }

    /**
     * Contains information about ignition monitors specific to compression vehicles.
     */
    public static final class CompressionIgnitionMonitors extends CommonIgnitionMonitors {
        public final IgnitionMonitor EGROrVVT;
        public final IgnitionMonitor PMFilter;
        public final IgnitionMonitor exhaustGasSensor;
        public final IgnitionMonitor boostPressure;
        public final IgnitionMonitor NOxSCR;
        public final IgnitionMonitor NMHCCatalyst;

        /** @hide */
        public static final int EGR_OR_VVT_AVAILABLE = 0x1 << 6;
        /** @hide */
        public static final int EGR_OR_VVT_INCOMPLETE = 0x1 << 7;

        /** @hide */
        public static final int PM_FILTER_AVAILABLE = 0x1 << 8;
        /** @hide */
        public static final int PM_FILTER_INCOMPLETE = 0x1 << 9;

        /** @hide */
        public static final int EXHAUST_GAS_SENSOR_AVAILABLE = 0x1 << 10;
        /** @hide */
        public static final int EXHAUST_GAS_SENSOR_INCOMPLETE = 0x1 << 11;

        /** @hide */
        public static final int BOOST_PRESSURE_AVAILABLE = 0x1 << 12;
        /** @hide */
        public static final int BOOST_PRESSURE_INCOMPLETE = 0x1 << 13;

        /** @hide */
        public static final int NOx_SCR_AVAILABLE = 0x1 << 14;
        /** @hide */
        public static final int NOx_SCR_INCOMPLETE = 0x1 << 15;

        /** @hide */
        public static final int NMHC_CATALYST_AVAILABLE = 0x1 << 16;
        /** @hide */
        public static final int NMHC_CATALYST_INCOMPLETE = 0x1 << 17;

        static final IgnitionMonitor.Decoder EGR_OR_VVT_DECODER =
                new IgnitionMonitor.Decoder(EGR_OR_VVT_AVAILABLE, EGR_OR_VVT_INCOMPLETE);

        static final IgnitionMonitor.Decoder PM_FILTER_DECODER =
                new IgnitionMonitor.Decoder(PM_FILTER_AVAILABLE, PM_FILTER_INCOMPLETE);

        static final IgnitionMonitor.Decoder EXHAUST_GAS_SENSOR_DECODER =
                new IgnitionMonitor.Decoder(EXHAUST_GAS_SENSOR_AVAILABLE,
                        EXHAUST_GAS_SENSOR_INCOMPLETE);

        static final IgnitionMonitor.Decoder BOOST_PRESSURE_DECODER =
                new IgnitionMonitor.Decoder(BOOST_PRESSURE_AVAILABLE,
                        BOOST_PRESSURE_INCOMPLETE);

        static final IgnitionMonitor.Decoder NOx_SCR_DECODER =
                new IgnitionMonitor.Decoder(NOx_SCR_AVAILABLE, NOx_SCR_INCOMPLETE);

        static final IgnitionMonitor.Decoder NMHC_CATALYST_DECODER =
                new IgnitionMonitor.Decoder(NMHC_CATALYST_AVAILABLE, NMHC_CATALYST_INCOMPLETE);

        CompressionIgnitionMonitors(int bitmask) {
            super(bitmask);
            EGROrVVT = EGR_OR_VVT_DECODER.fromValue(bitmask);
            PMFilter = PM_FILTER_DECODER.fromValue(bitmask);
            exhaustGasSensor = EXHAUST_GAS_SENSOR_DECODER.fromValue(bitmask);
            boostPressure = BOOST_PRESSURE_DECODER.fromValue(bitmask);
            NOxSCR = NOx_SCR_DECODER.fromValue(bitmask);
            NMHCCatalyst = NMHC_CATALYST_DECODER.fromValue(bitmask);
        }
    }

    /**
     * Returns the state of the fuel system, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable @FuelSystemStatus.Status Integer getFuelSystemStatus() {
        return getSystemIntegerSensor(android.car.diagnostic.IntegerSensorIndex.FUEL_SYSTEM_STATUS);
    }

    /**
     * Returns the state of the secondary air system, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable @SecondaryAirStatus.Status Integer getSecondaryAirStatus() {
        return getSystemIntegerSensor(android.car.diagnostic.IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS);
    }

    /**
     * Returns data about the ignition monitors, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable CommonIgnitionMonitors getIgnitionMonitors() {
        Integer ignitionMonitorsType =
                getSystemIntegerSensor(android.car.diagnostic.IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED);
        Integer ignitionMonitorsBitmask =
                getSystemIntegerSensor(android.car.diagnostic.IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS);
        if (null == ignitionMonitorsType) return null;
        if (null == ignitionMonitorsBitmask) return null;
        switch (ignitionMonitorsType) {
            case 0:
                return new SparkIgnitionMonitors(ignitionMonitorsBitmask);
            case 1:
                return new CompressionIgnitionMonitors(ignitionMonitorsBitmask);
            default:
                return null;
        }
    }

    /**
     * Returns the fuel type, if present in this frame.
     * Returns null otherwise.
     */
    public @Nullable @FuelType.Type Integer getFuelType() {
        return getSystemIntegerSensor(android.car.diagnostic.IntegerSensorIndex.FUEL_TYPE);
    }
}
