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

#include "sensorlist.h"

#include <math.h>

#include "hubdefs.h"

using namespace android;

const int kVersion = 1;

const float kMinSampleRateHzAccel = 6.250f;
const float kMaxSampleRateHzAccel = 400.0f;
const float kAccelRangeG = 16.0f;
extern const float kScaleAccel = (kAccelRangeG * 9.81f / 32768.0f);

const float kMinSampleRateHzGyro = 6.250f;
const float kMaxSampleRateHzGyro = 400.0f;

const float kMinSampleRateHzMag = 3.125f;
const float kMaxSampleRateHzMag = 50.0f;
extern const float kScaleMag = 0.0625f;         // 1.0f / 16.0f

const float kMinSampleRateHzPolling = 0.1f;
const float kMaxSampleRateHzPolling = 25.0f;

const float kMinSampleRateHzPressure = 0.1f;
const float kMaxSampleRateHzPressure = 10.0f;

const float kMinSampleRateHzTemperature = kMinSampleRateHzPolling;
const float kMaxSampleRateHzTemperature = kMaxSampleRateHzPolling;

const float kMinSampleRateHzProximity = kMinSampleRateHzPolling;
const float kMaxSampleRateHzProximity = 5.0;

const float kMinSampleRateHzLight = kMinSampleRateHzPolling;
const float kMaxSampleRateHzLight = 5.0;

const float kMinSampleRateHzOrientation = 12.5f;
const float kMaxSampleRateHzOrientation = 200.0f;

/*
 * The fowllowing max count is determined by the total number of blocks
 * avaliable in the shared nanohub buffer and number of samples each type of
 * event can hold within a buffer block.
 * For angler's case, there are 239 blocks in the shared sensor buffer and
 * each block can hold 30 OneAxis Samples, 15 ThreeAxis Samples or 24
 * RawThreeAxies Samples.
 */
const int kMaxOneAxisEventCount = 7170;
const int kMaxThreeAxisEventCount = 3585;
const int kMaxRawThreeAxisEventCount = 5736;

const int kMinFifoReservedEventCount = 20;

const char SENSOR_STRING_TYPE_INTERNAL_TEMPERATURE[] =
    "com.google.sensor.internal_temperature";
const char SENSOR_STRING_TYPE_SYNC[] =
    "com.google.sensor.sync";
const char SENSOR_STRING_TYPE_DOUBLE_TWIST[] =
    "com.google.sensor.double_twist";
const char SENSOR_STRING_TYPE_DOUBLE_TAP[] =
    "com.google.sensor.double_tap";

extern const sensor_t kSensorList[] = {
    {
        "TMD27723 Proximity Sensor",
        "AMS",
        kVersion,
        COMMS_SENSOR_PROXIMITY,
        SENSOR_TYPE_PROXIMITY,
        5.0f,                                          // maxRange (cm)
        1.0f,                                          // resolution (cm)
        0.0f,                                          // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzProximity), // minDelay
        300,                                           // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_PROXIMITY,
        "",                                            // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzProximity),    // maxDelay
        SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_ON_CHANGE_MODE,
        { NULL, NULL }
    },
    {
        "TMD27723 Light Sensor",
        "AMS",
        kVersion,
        COMMS_SENSOR_LIGHT,
        SENSOR_TYPE_LIGHT,
        10000.0f,                                  // maxRange (lx)
        10.0f,                                     // XXX resolution (lx)
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzLight), // minDelay
        kMinFifoReservedEventCount,                // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                     // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_LIGHT,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzLight),    // maxDelay
        SENSOR_FLAG_ON_CHANGE_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 accelerometer",
        "Bosch",
        kVersion,
        COMMS_SENSOR_ACCEL,
        SENSOR_TYPE_ACCELEROMETER,
        GRAVITY_EARTH * kAccelRangeG,              // maxRange
        GRAVITY_EARTH * kAccelRangeG / 32768.0f,   // resolution
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzAccel), // minDelay
        3000,                                      // XXX fifoReservedEventCount
        kMaxRawThreeAxisEventCount,                // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_ACCELEROMETER,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzAccel),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 gyroscope",
        "Bosch",
        kVersion,
        COMMS_SENSOR_GYRO,
        SENSOR_TYPE_GYROSCOPE,
        1000.0f * M_PI / 180.0f,                   // maxRange
        1000.0f * M_PI / (180.0f * 32768.0f),      // resolution
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzGyro),  // minDelay
        kMinFifoReservedEventCount,                // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                   // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_GYROSCOPE,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzGyro),     // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 gyroscope (uncalibrated)",
        "Bosch",
        kVersion,
        COMMS_SENSOR_GYRO_UNCALIBRATED,
        SENSOR_TYPE_GYROSCOPE_UNCALIBRATED,
        1000.0f * M_PI / 180.0f,                   // maxRange
        1000.0f * M_PI / (180.0f * 32768.0f),      // resolution
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzGyro),  // minDelay
        kMinFifoReservedEventCount,                // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                   // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_GYROSCOPE_UNCALIBRATED,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzGyro),     // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMM150 magnetometer",
        "Bosch",
        kVersion,
        COMMS_SENSOR_MAG,
        SENSOR_TYPE_MAGNETIC_FIELD,
        1300.0f,                                   // XXX maxRange
        0.0f,                                      // XXX resolution
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzMag),   // minDelay
        600,                                       // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                   // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_MAGNETIC_FIELD,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzMag),      // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMM150 magnetometer (uncalibrated)",
        "Bosch",
        kVersion,
        COMMS_SENSOR_MAG_UNCALIBRATED,
        SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED,
        1300.0f,                                        // XXX maxRange
        0.0f,                                           // XXX resolution
        0.0f,                                           // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzMag),        // minDelay
        600,                                            // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                        // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_MAGNETIC_FIELD_UNCALIBRATED,
        "",                                             // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzMag),           // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMP280 pressure",
        "Bosch",
        kVersion,
        COMMS_SENSOR_PRESSURE,
        SENSOR_TYPE_PRESSURE,
        1100.0f,                                      // maxRange (hPa)
        0.005f,                                       // resolution (hPa)
        0.0f,                                         // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzPressure), // minDelay
        300,                                          // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                        // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_PRESSURE,
        "",                                           // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzPressure),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMP280 temperature",
        "Bosch",
        kVersion,
        COMMS_SENSOR_TEMPERATURE,
        SENSOR_TYPE_INTERNAL_TEMPERATURE,
        85.0f,                                           // maxRange (degC)
        0.01,                                            // resolution (degC)
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzTemperature), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                           // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_INTERNAL_TEMPERATURE,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzTemperature),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Orientation",
        "Google",
        kVersion,
        COMMS_SENSOR_ORIENTATION,
        SENSOR_TYPE_ORIENTATION,
        360.0f,                                          // maxRange (deg)
        1.0f,                                            // XXX resolution (deg)
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_ORIENTATION,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 Step detector",
        "Bosch",
        kVersion,
        COMMS_SENSOR_STEP_DETECTOR,
        SENSOR_TYPE_STEP_DETECTOR,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.0f,                                   // XXX power
        0,                                      // minDelay
        100,                                    // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_STEP_DETECTOR,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_SPECIAL_REPORTING_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 Step counter",
        "Bosch",
        kVersion,
        COMMS_SENSOR_STEP_COUNTER,
        SENSOR_TYPE_STEP_COUNTER,
        1.0f,                                   // XXX maxRange
        1.0f,                                   // resolution
        0.0f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_STEP_COUNTER,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_ON_CHANGE_MODE,
        { NULL, NULL }
    },
    {
        "Significant motion",
        "Google",
        kVersion,
        COMMS_SENSOR_SIGNIFICANT_MOTION,
        SENSOR_TYPE_SIGNIFICANT_MOTION,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.0f,                                   // XXX power
        -1,                                     // minDelay
        0,                                      // XXX fifoReservedEventCount
        0,                                      // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_SIGNIFICANT_MOTION,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_ONE_SHOT_MODE,
        { NULL, NULL }
    },
    {
        "Gravity",
        "Google",
        kVersion,
        COMMS_SENSOR_GRAVITY,
        SENSOR_TYPE_GRAVITY,
        1000.0f,                                         // maxRange
        1.0f,                                            // XXX resolution
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_GRAVITY,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Linear Acceleration",
        "Google",
        kVersion,
        COMMS_SENSOR_LINEAR_ACCEL,
        SENSOR_TYPE_LINEAR_ACCELERATION,
        1000.0f,                                         // maxRange
        1.0f,                                            // XXX resolution
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_LINEAR_ACCELERATION,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Rotation Vector",
        "Google",
        kVersion,
        COMMS_SENSOR_ROTATION_VECTOR,
        SENSOR_TYPE_ROTATION_VECTOR,
        1000.0f,                                         // maxRange
        1.0f,                                            // XXX resolution
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_ROTATION_VECTOR,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Geomagnetic Rotation Vector",
        "Google",
        kVersion,
        COMMS_SENSOR_GEO_MAG,
        SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR,
        1000.0f,                                         // maxRange
        1.0f,                                            // XXX resolution
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        kMinFifoReservedEventCount,                      // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Game Rotation Vector",
        "Google",
        kVersion,
        COMMS_SENSOR_GAME_ROTATION_VECTOR,
        SENSOR_TYPE_GAME_ROTATION_VECTOR,
        1000.0f,                                         // maxRange
        1.0f,                                            // XXX resolution
        0.0f,                                            // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzOrientation), // minDelay
        300,                                             // XXX fifoReservedEventCount
        kMaxThreeAxisEventCount,                         // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_GAME_ROTATION_VECTOR,
        "",                                              // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzOrientation),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
    {
        "Tilt Detector",
        "Google",
        kVersion,
        COMMS_SENSOR_TILT,
        SENSOR_TYPE_TILT_DETECTOR,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.0f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_TILT_DETECTOR,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SPECIAL_REPORTING_MODE,
        { NULL, NULL }
    },
    {
        "Pickup Gesture",
        "Google",
        kVersion,
        COMMS_SENSOR_GESTURE,
        SENSOR_TYPE_PICK_UP_GESTURE,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.0f,                                   // XXX power
        -1,                                     // minDelay
        0,                                      // XXX fifoReservedEventCount
        0,                                      // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_PICK_UP_GESTURE,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_ONE_SHOT_MODE,
        { NULL, NULL }
    },
    {
        "Sensors Sync",
        "Google",
        kVersion,
        COMMS_SENSOR_SYNC,
        SENSOR_TYPE_SYNC,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.1f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_SYNC,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_SPECIAL_REPORTING_MODE,
        { NULL, NULL }
    },
    {
        "Double Twist",
        "Google",
        kVersion,
        COMMS_SENSOR_DOUBLE_TWIST,
        SENSOR_TYPE_DOUBLE_TWIST,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.1f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_DOUBLE_TWIST,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SPECIAL_REPORTING_MODE,
        { NULL, NULL }
    },
    {
        "Double Tap",
        "Google",
        kVersion,
        COMMS_SENSOR_DOUBLE_TAP,
        SENSOR_TYPE_DOUBLE_TAP,
        1.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.1f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_DOUBLE_TAP,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_SPECIAL_REPORTING_MODE,
        { NULL, NULL }
    },
    {
        "Device Orientation",
        "Google",
        kVersion,
        COMMS_SENSOR_WINDOW_ORIENTATION,
        SENSOR_TYPE_DEVICE_ORIENTATION,
        3.0f,                                   // maxRange
        1.0f,                                   // XXX resolution
        0.1f,                                   // XXX power
        0,                                      // minDelay
        kMinFifoReservedEventCount,             // XXX fifoReservedEventCount
        kMaxOneAxisEventCount,                  // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_DEVICE_ORIENTATION,
        "",                                     // requiredPermission
        0,                                      // maxDelay
        SENSOR_FLAG_ON_CHANGE_MODE,
        { NULL, NULL }
    },
    {
        "BMI160 accelerometer (uncalibrated)",
        "Bosch",
        kVersion,
        COMMS_SENSOR_ACCEL_UNCALIBRATED,
        SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED,
        GRAVITY_EARTH * kAccelRangeG,              // maxRange
        GRAVITY_EARTH * kAccelRangeG / 32768.0f,   // resolution
        0.0f,                                      // XXX power
        (int32_t)(1.0E6f / kMaxSampleRateHzAccel), // minDelay
        3000,                                      // XXX fifoReservedEventCount
        kMaxRawThreeAxisEventCount,                // XXX fifoMaxEventCount
        SENSOR_STRING_TYPE_ACCELEROMETER_UNCALIBRATED,
        "",                                        // requiredPermission
        (long)(1.0E6f / kMinSampleRateHzAccel),    // maxDelay
        SENSOR_FLAG_CONTINUOUS_MODE,
        { NULL, NULL }
    },
};

extern const size_t kSensorCount = sizeof(kSensorList) / sizeof(sensor_t);
