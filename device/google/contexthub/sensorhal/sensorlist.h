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

#ifndef SENSORLIST_H_

#define SENSORLIST_H_

#include <hardware/sensors.h>

extern const float kScaleAccel;
extern const float kScaleMag;
// A list of sensors provided by a device.
extern const sensor_t kSensorList[];
extern const size_t kSensorCount;

// TODO(aarossig): Consider de-duplicating Google sensors.

#endif  // SENSORLIST_H_
