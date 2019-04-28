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

package com.android.car;

import android.car.hardware.CarSensorEvent;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;

//TODO add memory pool and recycling
public class CarSensorEventFactory {

    public static CarSensorEvent createBooleanEvent(int sensorType, long timestamp,
            boolean value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 0, 1, 0);
        event.intValues[0] = value ? 1 : 0;
        return event;
    }

    public static CarSensorEvent createIntEvent(int sensorType, long timestamp, int value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 0, 1, 0);
        event.intValues[0] = value;
        return event;
    }

    public static CarSensorEvent createFloatEvent(int sensorType, long timestamp, float value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 1, 0, 0);
        event.floatValues[0] = value;
        return event;
    }

    public static CarSensorEvent createComplexEvent(int sensorType, long timestamp,
                                                    VehiclePropValue v) {
        int numFloats = v.value.floatValues.size();
        int numInts = v.value.int32Values.size();
        int numLongs = v.value.int64Values.size();
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, numFloats, numInts,
            numLongs);
        // Copy arraylist elements into final arrays
        for (int i=0; i<numFloats; i++) {
            event.floatValues[i] = v.value.floatValues.get(i);
        }
        for (int i=0; i<numInts; i++) {
            event.intValues[i] = v.value.int32Values.get(i);
        }
        for (int i=0; i<numLongs; i++) {
            event.longValues[i] = v.value.int64Values.get(i);
        }
        return event;
    }

    public static void returnToPool(CarSensorEvent event) {
        //TODO
    }
}
