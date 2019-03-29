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

#ifndef _SENS_TYPE_H_
#define _SENS_TYPE_H_

#ifdef __cplusplus
extern "C" {
#endif

#define SENS_TYPE_INVALID         0
#define SENS_TYPE_ACCEL           1
#define SENS_TYPE_ANY_MOTION      2 //provided by ACCEL, nondiscardable edge trigger
#define SENS_TYPE_NO_MOTION       3 //provided by ACCEL, nondiscardable edge trigger
#define SENS_TYPE_SIG_MOTION      4 //provided by ACCEL, nondiscardable edge trigger
#define SENS_TYPE_FLAT            5
#define SENS_TYPE_GYRO            6
#define SENS_TYPE_GYRO_UNCAL      7
#define SENS_TYPE_MAG             8
#define SENS_TYPE_MAG_UNCAL       9
#define SENS_TYPE_BARO            10
#define SENS_TYPE_TEMP            11
#define SENS_TYPE_ALS             12
#define SENS_TYPE_PROX            13
#define SENS_TYPE_ORIENTATION     14
#define SENS_TYPE_HEARTRATE_ECG   15
#define SENS_TYPE_HEARTRATE_PPG   16
#define SENS_TYPE_GRAVITY         17
#define SENS_TYPE_LINEAR_ACCEL    18
#define SENS_TYPE_ROTATION_VECTOR 19
#define SENS_TYPE_GEO_MAG_ROT_VEC 20
#define SENS_TYPE_GAME_ROT_VECTOR 21
#define SENS_TYPE_STEP_COUNT      22
#define SENS_TYPE_STEP_DETECT     23
#define SENS_TYPE_GESTURE         24
#define SENS_TYPE_TILT            25
#define SENS_TYPE_DOUBLE_TWIST    26
#define SENS_TYPE_DOUBLE_TAP      27
#define SENS_TYPE_WIN_ORIENTATION 28
#define SENS_TYPE_HALL            29
#define SENS_TYPE_ACTIVITY        30
#define SENS_TYPE_VSYNC           31
#define SENS_TYPE_ACCEL_RAW       32
// Values 33-37 are reserved
#define SENS_TYPE_WRIST_TILT      39

// Activity recognition sensor types.
#define SENS_TYPE_ACTIVITY_IN_VEHICLE_START   40
#define SENS_TYPE_ACTIVITY_IN_VEHICLE_STOP    41
#define SENS_TYPE_ACTIVITY_ON_BICYCLE_START   42
#define SENS_TYPE_ACTIVITY_ON_BICYCLE_STOP    43
#define SENS_TYPE_ACTIVITY_WALKING_START      44
#define SENS_TYPE_ACTIVITY_WALKING_STOP       45
#define SENS_TYPE_ACTIVITY_RUNNING_START      46
#define SENS_TYPE_ACTIVITY_RUNNING_STOP       47
#define SENS_TYPE_ACTIVITY_STILL_START        48
#define SENS_TYPE_ACTIVITY_STILL_STOP         49
#define SENS_TYPE_ACTIVITY_TILTING            50
#define SENS_TYPE_ACTIVITY_RAW                51

#define SENS_TYPE_DOUBLE_TOUCH    52
#define SENS_TYPE_GAZE            53
#define SENS_TYPE_UNGAZE          54
#define SENS_TYPE_ACCEL_UNCAL     55
#define SENS_TYPE_ACCEL_TEMP      56
#define SENS_TYPE_GYRO_TEMP       57
#define SENS_TYPE_MAG_TEMP        58
#define SENS_TYPE_MAG_RAW         59
#define SENS_TYPE_LEDS            60
#define SENS_TYPE_HUMIDITY        61
#define SENS_TYPE_LEDS_I2C        62

#define SENS_TYPE_FIRST_USER      64 // event type necessarily begins with UserSensorEventHdr
#define SENS_TYPE_LAST_USER       128

#ifdef __cplusplus
}
#endif

#endif
