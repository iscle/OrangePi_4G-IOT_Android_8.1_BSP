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
package com.android.car.hal;

import java.io.PrintWriter;

/**
 * Abstraction for a sensor, whether it be a physical on-board sensor of a vehicle, or a logical
 * data element which wants to be represented as sensor-like to higher-level entities
 */
public interface SensorBase {
    void init();
    void release();
    boolean isReady();
    int[] getSupportedSensors();
    boolean requestSensorStart(int sensor, int rate);
    void requestSensorStop(int sensor);
    void dump(PrintWriter writer);
}
