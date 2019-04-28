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
public final class FloatSensorIndex {
    private FloatSensorIndex() {}

    public static final int CALCULATED_ENGINE_LOAD = 0;
    public static final int ENGINE_COOLANT_TEMPERATURE = 1;
    public static final int SHORT_TERM_FUEL_TRIM_BANK1 = 2;
    public static final int LONG_TERM_FUEL_TRIM_BANK1 = 3;
    public static final int SHORT_TERM_FUEL_TRIM_BANK2 = 4;
    public static final int LONG_TERM_FUEL_TRIM_BANK2 = 5;
    public static final int FUEL_PRESSURE = 6;
    public static final int INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 7;
    public static final int ENGINE_RPM = 8;
    public static final int VEHICLE_SPEED = 9;
    public static final int TIMING_ADVANCE = 10;
    public static final int MAF_AIR_FLOW_RATE = 11;
    public static final int THROTTLE_POSITION = 12;
    public static final int OXYGEN_SENSOR1_VOLTAGE = 13;
    public static final int OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM = 14;
    public static final int OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO = 15;
    public static final int OXYGEN_SENSOR2_VOLTAGE = 16;
    public static final int OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM = 17;
    public static final int OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO = 18;
    public static final int OXYGEN_SENSOR3_VOLTAGE = 19;
    public static final int OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM = 20;
    public static final int OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO = 21;
    public static final int OXYGEN_SENSOR4_VOLTAGE = 22;
    public static final int OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM = 23;
    public static final int OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO = 24;
    public static final int OXYGEN_SENSOR5_VOLTAGE = 25;
    public static final int OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM = 26;
    public static final int OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO = 27;
    public static final int OXYGEN_SENSOR6_VOLTAGE = 28;
    public static final int OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM = 29;
    public static final int OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO = 30;
    public static final int OXYGEN_SENSOR7_VOLTAGE = 31;
    public static final int OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM = 32;
    public static final int OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO = 33;
    public static final int OXYGEN_SENSOR8_VOLTAGE = 34;
    public static final int OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM = 35;
    public static final int OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO = 36;
    public static final int FUEL_RAIL_PRESSURE = 37;
    public static final int FUEL_RAIL_GAUGE_PRESSURE = 38;
    public static final int COMMANDED_EXHAUST_GAS_RECIRCULATION = 39;
    public static final int EXHAUST_GAS_RECIRCULATION_ERROR = 40;
    public static final int COMMANDED_EVAPORATIVE_PURGE = 41;
    public static final int FUEL_TANK_LEVEL_INPUT = 42;
    public static final int EVAPORATION_SYSTEM_VAPOR_PRESSURE = 43;
    public static final int CATALYST_TEMPERATURE_BANK1_SENSOR1 = 44;
    public static final int CATALYST_TEMPERATURE_BANK2_SENSOR1 = 45;
    public static final int CATALYST_TEMPERATURE_BANK1_SENSOR2 = 46;
    public static final int CATALYST_TEMPERATURE_BANK2_SENSOR2 = 47;
    public static final int ABSOLUTE_LOAD_VALUE = 48;
    public static final int FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO = 49;
    public static final int RELATIVE_THROTTLE_POSITION = 50;
    public static final int ABSOLUTE_THROTTLE_POSITION_B = 51;
    public static final int ABSOLUTE_THROTTLE_POSITION_C = 52;
    public static final int ACCELERATOR_PEDAL_POSITION_D = 53;
    public static final int ACCELERATOR_PEDAL_POSITION_E = 54;
    public static final int ACCELERATOR_PEDAL_POSITION_F = 55;
    public static final int COMMANDED_THROTTLE_ACTUATOR = 56;
    public static final int ETHANOL_FUEL_PERCENTAGE = 57;
    public static final int ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE = 58;
    public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 59;
    public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 60;
    public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 61;
    public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 62;
    public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 63;
    public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 64;
    public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 65;
    public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 66;
    public static final int RELATIVE_ACCELERATOR_PEDAL_POSITION = 67;
    public static final int HYBRID_BATTERY_PACK_REMAINING_LIFE = 68;
    public static final int FUEL_INJECTION_TIMING = 69;
    public static final int ENGINE_FUEL_RATE = 70;
    public static final int LAST_SYSTEM = ENGINE_FUEL_RATE;
    public static final int VENDOR_START = LAST_SYSTEM + 1;


    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        FloatSensorIndex.CALCULATED_ENGINE_LOAD,
        FloatSensorIndex.ENGINE_COOLANT_TEMPERATURE,
        FloatSensorIndex.SHORT_TERM_FUEL_TRIM_BANK1,
        FloatSensorIndex.LONG_TERM_FUEL_TRIM_BANK1,
        FloatSensorIndex.SHORT_TERM_FUEL_TRIM_BANK2,
        FloatSensorIndex.LONG_TERM_FUEL_TRIM_BANK2,
        FloatSensorIndex.FUEL_PRESSURE,
        FloatSensorIndex.INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
        FloatSensorIndex.ENGINE_RPM,
        FloatSensorIndex.VEHICLE_SPEED,
        FloatSensorIndex.TIMING_ADVANCE,
        FloatSensorIndex.MAF_AIR_FLOW_RATE,
        FloatSensorIndex.THROTTLE_POSITION,
        FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR2_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR3_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR4_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR5_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR6_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR7_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.OXYGEN_SENSOR8_VOLTAGE,
        FloatSensorIndex.OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM,
        FloatSensorIndex.OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO,
        FloatSensorIndex.FUEL_RAIL_PRESSURE,
        FloatSensorIndex.FUEL_RAIL_GAUGE_PRESSURE,
        FloatSensorIndex.COMMANDED_EXHAUST_GAS_RECIRCULATION,
        FloatSensorIndex.EXHAUST_GAS_RECIRCULATION_ERROR,
        FloatSensorIndex.COMMANDED_EVAPORATIVE_PURGE,
        FloatSensorIndex.FUEL_TANK_LEVEL_INPUT,
        FloatSensorIndex.EVAPORATION_SYSTEM_VAPOR_PRESSURE,
        FloatSensorIndex.CATALYST_TEMPERATURE_BANK1_SENSOR1,
        FloatSensorIndex.CATALYST_TEMPERATURE_BANK2_SENSOR1,
        FloatSensorIndex.CATALYST_TEMPERATURE_BANK1_SENSOR2,
        FloatSensorIndex.CATALYST_TEMPERATURE_BANK2_SENSOR2,
        FloatSensorIndex.ABSOLUTE_LOAD_VALUE,
        FloatSensorIndex.FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO,
        FloatSensorIndex.RELATIVE_THROTTLE_POSITION,
        FloatSensorIndex.ABSOLUTE_THROTTLE_POSITION_B,
        FloatSensorIndex.ABSOLUTE_THROTTLE_POSITION_C,
        FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_D,
        FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_E,
        FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_F,
        FloatSensorIndex.COMMANDED_THROTTLE_ACTUATOR,
        FloatSensorIndex.ETHANOL_FUEL_PERCENTAGE,
        FloatSensorIndex.ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE,
        FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
        FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
        FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
        FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
        FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
        FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
        FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
        FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
        FloatSensorIndex.RELATIVE_ACCELERATOR_PEDAL_POSITION,
        FloatSensorIndex.HYBRID_BATTERY_PACK_REMAINING_LIFE,
        FloatSensorIndex.FUEL_INJECTION_TIMING,
        FloatSensorIndex.ENGINE_FUEL_RATE,
        FloatSensorIndex.LAST_SYSTEM,
        FloatSensorIndex.VENDOR_START,
    })
    public @interface SensorIndex {}

}
