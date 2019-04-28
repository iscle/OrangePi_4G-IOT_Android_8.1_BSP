/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.hardware.property;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.ICarPropertyEventListener;

/**
 * @hide
 */
interface ICarProperty {

    void registerListener(in ICarPropertyEventListener callback) = 0;

    void unregisterListener(in ICarPropertyEventListener callback) = 1;

    List<CarPropertyConfig> getPropertyList() = 2;

    CarPropertyValue getProperty(int prop, int zone) = 3;

    void setProperty(in CarPropertyValue prop) = 4;
}
