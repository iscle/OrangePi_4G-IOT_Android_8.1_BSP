/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.hardware.cts.helpers;

import android.hardware.Sensor;

/**
 * Exception that indicates that a given sensor is not supported in the device.
 */
public class SensorNotSupportedException extends SensorTestStateNotSupportedException {
    public SensorNotSupportedException(int sensorType) {
        super("Sensor '%s' of type %d is not supported.", getSensorName(sensorType), sensorType);
    }

    public SensorNotSupportedException(int sensorType, boolean wakeup) {
        super("Sensor '%s' of type %d and %s is not supported.", getSensorName(sensorType),
               sensorType, wakeup ? "wake-up" : "non wake-up");
    }

    private static String getSensorName(int sensorType) {
        return String.format("%s (%d)", getSimpleSensorName(sensorType), sensorType);
    }

    @SuppressWarnings("deprecation")
    private static String getSimpleSensorName(int sensorType) {
        switch(sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                return "Accelerometer";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "Magnetic Field";
            case Sensor.TYPE_ORIENTATION:
                return "Orientation";
            case Sensor.TYPE_GYROSCOPE:
                return "Gyroscope";
            case Sensor.TYPE_LIGHT:
                return "Light";
            case Sensor.TYPE_PRESSURE:
                return "Pressure";
            case Sensor.TYPE_TEMPERATURE:
                return "Temperature";
            case Sensor.TYPE_PROXIMITY:
                return "Proximity";
            case Sensor.TYPE_GRAVITY:
                return "Gravity";
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return "Linear Acceleration";
            case Sensor.TYPE_ROTATION_VECTOR:
                return "Rotation Vector";
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return "Relative Humidity";
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return "Ambient Temperature";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "Magnetic Field Uncalibrated";
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return "Game Rotation Vector";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return "Gyroscope Uncalibrated";
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                return "Significant Motion";
            case Sensor.TYPE_STEP_DETECTOR:
                return "Step Detector";
            case Sensor.TYPE_STEP_COUNTER:
                return "Step Counter";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                return "Geomagnetic Rotation Vector";
            default:
                return "<Unknown>";
        }
    }
}
