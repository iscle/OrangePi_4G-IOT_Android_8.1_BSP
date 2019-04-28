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
import android.annotation.SystemApi;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class is a container for the indices of diagnostic sensors. The values are extracted by
 * running packages/services/Car/tools/update-obd2-sensors.py against types.hal.
 *
 * DO NOT EDIT MANUALLY
 *
 * @hide
 */
@SystemApi
public final class IntegerSensorIndex {
    private IntegerSensorIndex() {}

    public static final int FUEL_SYSTEM_STATUS = 0;
    public static final int MALFUNCTION_INDICATOR_LIGHT_ON = 1;
    public static final int IGNITION_MONITORS_SUPPORTED = 2;
    public static final int IGNITION_SPECIFIC_MONITORS = 3;
    public static final int INTAKE_AIR_TEMPERATURE = 4;
    public static final int COMMANDED_SECONDARY_AIR_STATUS = 5;
    public static final int NUM_OXYGEN_SENSORS_PRESENT = 6;
    public static final int RUNTIME_SINCE_ENGINE_START = 7;
    public static final int DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON = 8;
    public static final int WARMUPS_SINCE_CODES_CLEARED = 9;
    public static final int DISTANCE_TRAVELED_SINCE_CODES_CLEARED = 10;
    public static final int ABSOLUTE_BAROMETRIC_PRESSURE = 11;
    public static final int CONTROL_MODULE_VOLTAGE = 12;
    public static final int AMBIENT_AIR_TEMPERATURE = 13;
    public static final int TIME_WITH_MALFUNCTION_LIGHT_ON = 14;
    public static final int TIME_SINCE_TROUBLE_CODES_CLEARED = 15;
    public static final int MAX_FUEL_AIR_EQUIVALENCE_RATIO = 16;
    public static final int MAX_OXYGEN_SENSOR_VOLTAGE = 17;
    public static final int MAX_OXYGEN_SENSOR_CURRENT = 18;
    public static final int MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 19;
    public static final int MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR = 20;
    public static final int FUEL_TYPE = 21;
    public static final int FUEL_RAIL_ABSOLUTE_PRESSURE = 22;
    public static final int ENGINE_OIL_TEMPERATURE = 23;
    public static final int DRIVER_DEMAND_PERCENT_TORQUE = 24;
    public static final int ENGINE_ACTUAL_PERCENT_TORQUE = 25;
    public static final int ENGINE_REFERENCE_PERCENT_TORQUE = 26;
    public static final int ENGINE_PERCENT_TORQUE_DATA_IDLE = 27;
    public static final int ENGINE_PERCENT_TORQUE_DATA_POINT1 = 28;
    public static final int ENGINE_PERCENT_TORQUE_DATA_POINT2 = 29;
    public static final int ENGINE_PERCENT_TORQUE_DATA_POINT3 = 30;
    public static final int ENGINE_PERCENT_TORQUE_DATA_POINT4 = 31;
    public static final int LAST_SYSTEM = ENGINE_PERCENT_TORQUE_DATA_POINT4;
    public static final int VENDOR_START = LAST_SYSTEM + 1;


    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        IntegerSensorIndex.FUEL_SYSTEM_STATUS,
        IntegerSensorIndex.MALFUNCTION_INDICATOR_LIGHT_ON,
        IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED,
        IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS,
        IntegerSensorIndex.INTAKE_AIR_TEMPERATURE,
        IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS,
        IntegerSensorIndex.NUM_OXYGEN_SENSORS_PRESENT,
        IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START,
        IntegerSensorIndex.DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON,
        IntegerSensorIndex.WARMUPS_SINCE_CODES_CLEARED,
        IntegerSensorIndex.DISTANCE_TRAVELED_SINCE_CODES_CLEARED,
        IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE,
        IntegerSensorIndex.CONTROL_MODULE_VOLTAGE,
        IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE,
        IntegerSensorIndex.TIME_WITH_MALFUNCTION_LIGHT_ON,
        IntegerSensorIndex.TIME_SINCE_TROUBLE_CODES_CLEARED,
        IntegerSensorIndex.MAX_FUEL_AIR_EQUIVALENCE_RATIO,
        IntegerSensorIndex.MAX_OXYGEN_SENSOR_VOLTAGE,
        IntegerSensorIndex.MAX_OXYGEN_SENSOR_CURRENT,
        IntegerSensorIndex.MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
        IntegerSensorIndex.MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR,
        IntegerSensorIndex.FUEL_TYPE,
        IntegerSensorIndex.FUEL_RAIL_ABSOLUTE_PRESSURE,
        IntegerSensorIndex.ENGINE_OIL_TEMPERATURE,
        IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE,
        IntegerSensorIndex.ENGINE_ACTUAL_PERCENT_TORQUE,
        IntegerSensorIndex.ENGINE_REFERENCE_PERCENT_TORQUE,
        IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_IDLE,
        IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT1,
        IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT2,
        IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT3,
        IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT4,
        IntegerSensorIndex.LAST_SYSTEM,
        IntegerSensorIndex.VENDOR_START,
    })
    public @interface SensorIndex {}

}
