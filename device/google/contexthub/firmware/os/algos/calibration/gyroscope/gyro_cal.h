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

/*
 * This module contains the algorithms for producing a gyroscope offset
 * calibration. The algorithm looks for periods of stillness as indicated by
 * accelerometer, magnetometer and gyroscope, and computes a bias estimate by
 * taking the average of the gyroscope during the stillness times.
 *
 * Currently, this algorithm is tuned such that the device is only considered
 * still when the device is on a stationary surface (e.g., not on a person).
 *
 * NOTE - Time units are agnostic (i.e., determined by the user's application
 * and usage). However, typical time units are nanoseconds.
 *
 * Required Sensors and Units:
 *       - Gyroscope     [rad/sec]
 *       - Accelerometer [m/sec^2]
 *
 * Optional Sensors and Units:
 *       - Magnetometer  [micro-Tesla, uT]
 *       - Temperature   [Celsius]
 *
 * #define GYRO_CAL_DBG_ENABLED to enable debug printout statements.
 * data to assist in tuning the GyroCal parameters.
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_CAL_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_CAL_H_

#include "calibration/gyroscope/gyro_stillness_detect.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifdef GYRO_CAL_DBG_ENABLED
// Debug printout state enumeration.
enum GyroCalDebugState {
  GYRO_IDLE = 0,
  GYRO_WAIT_STATE,
  GYRO_PRINT_OFFSET,
  GYRO_PRINT_STILLNESS_DATA,
  GYRO_PRINT_SAMPLE_RATE_AND_TEMPERATURE,
  GYRO_PRINT_GYRO_MINMAX_STILLNESS_MEAN,
  GYRO_PRINT_ACCEL_STATS,
  GYRO_PRINT_GYRO_STATS,
  GYRO_PRINT_MAG_STATS
};

// Gyro Cal debug information/data tracking structure.
struct DebugGyroCal {
  uint64_t start_still_time_nanos;
  uint64_t end_still_time_nanos;
  uint64_t stillness_duration_nanos;
  float mean_sampling_rate_hz;
  float accel_stillness_conf;
  float gyro_stillness_conf;
  float mag_stillness_conf;
  float calibration[3];
  float accel_mean[3];
  float gyro_mean[3];
  float mag_mean[3];
  float accel_var[3];
  float gyro_var[3];
  float mag_var[3];
  float gyro_winmean_min[3];
  float gyro_winmean_max[3];
  float temperature_min_celsius;
  float temperature_max_celsius;
  float temperature_mean_celsius;
  bool using_mag_sensor;
};

// Data structure for sample rate estimation.
struct SampleRateData {
  uint64_t last_timestamp_nanos;
  uint64_t time_delta_accumulator;
  size_t num_samples;
};
#endif  // GYRO_CAL_DBG_ENABLED

// Data structure for tracking min/max window mean during device stillness.
struct MinMaxWindowMeanData {
  float gyro_winmean_min[3];
  float gyro_winmean_max[3];
};

// Data structure for tracking temperature data during device stillness.
struct TemperatureMeanData {
  float temperature_min_celsius;
  float temperature_max_celsius;
  float latest_temperature_celsius;
  float mean_accumulator;
  size_t num_points;
};

struct GyroCal {
  // Stillness detectors.
  struct GyroStillDet accel_stillness_detect;
  struct GyroStillDet mag_stillness_detect;
  struct GyroStillDet gyro_stillness_detect;

  // Data for tracking temperature mean during periods of device stillness.
  struct TemperatureMeanData temperature_mean_tracker;

  // Data for tracking gyro mean during periods of device stillness.
  struct MinMaxWindowMeanData window_mean_tracker;

  // Aggregated sensor stillness threshold required for gyro bias calibration.
  float stillness_threshold;

  // Min and max durations for gyro bias calibration.
  uint64_t min_still_duration_nanos;
  uint64_t max_still_duration_nanos;

  // Duration of the stillness processing windows.
  uint64_t window_time_duration_nanos;

  // Timestamp when device started a still period.
  uint64_t start_still_time_nanos;

  // Gyro offset estimate, and the associated calibration temperature,
  // timestamp, and stillness confidence values.
  float bias_x, bias_y, bias_z;  // [rad/sec]
  float bias_temperature_celsius;
  float stillness_confidence;
  uint64_t calibration_time_nanos;

  // Current window end-time for all sensors. Used to assist in keeping
  // sensor data collection in sync. On initialization this will be set to
  // zero indicating that sensor data will be dropped until a valid end-time
  // is set from the first gyro timestamp received.
  uint64_t stillness_win_endtime_nanos;

  // Watchdog timer to reset to a known good state when data capture stalls.
  uint64_t gyro_watchdog_start_nanos;
  uint64_t gyro_watchdog_timeout_duration_nanos;
  bool gyro_watchdog_timeout;

  // Flag is "true" when the magnetometer is used.
  bool using_mag_sensor;

  // Flag set by user to control whether calibrations are used (default:
  // "true").
  bool gyro_calibration_enable;

  // Flag is 'true' when a new calibration update is ready.
  bool new_gyro_cal_available;

  // Flag to indicate if device was previously still.
  bool prev_still;

  // Min and maximum stillness window mean. This is used to check the stability
  // of the mean values computed for the gyroscope (i.e., provides further
  // validation for stillness).
  float gyro_winmean_min[3];
  float gyro_winmean_max[3];
  float stillness_mean_delta_limit;

  // The mean temperature over the stillness period. The limit is used to check
  // for temperature stability and provide a gate for when temperature is
  // rapidly changing.
  float temperature_mean_celsius;
  float temperature_delta_limit_celsius;

//----------------------------------------------------------------

#ifdef GYRO_CAL_DBG_ENABLED
  // Debug info.
  struct DebugGyroCal debug_gyro_cal;  // Debug data structure.
  enum GyroCalDebugState debug_state;  // Debug printout state machine.
  enum GyroCalDebugState next_state;   // Debug state machine next state.
  uint64_t wait_timer_nanos;           // Debug message throttle timer.

  struct SampleRateData sample_rate_estimator;  // Debug sample rate estimator.

  size_t debug_calibration_count;      // Total number of cals performed.
  size_t debug_watchdog_count;         // Total number of watchdog timeouts.
  bool debug_print_trigger;            // Flag used to trigger data printout.
#endif                                 // GYRO_CAL_DBG_ENABLED
};

/////// FUNCTION PROTOTYPES //////////////////////////////////////////

// Initialize the gyro calibration data structure.
void gyroCalInit(struct GyroCal* gyro_cal, uint64_t min_still_duration,
                 uint64_t max_still_duration_nanos, float bias_x, float bias_y,
                 float bias_z, uint64_t calibration_time_nanos,
                 uint64_t window_time_duration_nanos, float gyro_var_threshold,
                 float gyro_confidence_delta, float accel_var_threshold,
                 float accel_confidence_delta, float mag_var_threshold,
                 float mag_confidence_delta, float stillness_threshold,
                 float stillness_mean_delta_limit,
                 float temperature_delta_limit_celsius,
                 bool gyro_calibration_enable);

// Void all pointers in the gyro calibration data structure.
void gyroCalDestroy(struct GyroCal* gyro_cal);

// Get the most recent bias calibration value.
void gyroCalGetBias(struct GyroCal* gyro_cal, float* bias_x, float* bias_y,
                    float* bias_z, float* temperature_celsius);

// Set an initial bias calibration value.
void gyroCalSetBias(struct GyroCal* gyro_cal, float bias_x, float bias_y,
                    float bias_z, uint64_t calibration_time_nanos);

// Remove gyro bias from the calibration [rad/sec].
void gyroCalRemoveBias(struct GyroCal* gyro_cal, float xi, float yi, float zi,
                       float* xo, float* yo, float* zo);

// Returns true when a new gyro calibration is available.
bool gyroCalNewBiasAvailable(struct GyroCal* gyro_cal);

// Update the gyro calibration with gyro data [rad/sec].
void gyroCalUpdateGyro(struct GyroCal* gyro_cal, uint64_t sample_time_nanos,
                       float x, float y, float z, float temperature_celsius);

// Update the gyro calibration with mag data [micro Tesla].
void gyroCalUpdateMag(struct GyroCal* gyro_cal, uint64_t sample_time_nanos,
                      float x, float y, float z);

// Update the gyro calibration with accel data [m/sec^2].
void gyroCalUpdateAccel(struct GyroCal* gyro_cal, uint64_t sample_time_nanos,
                        float x, float y, float z);

#ifdef GYRO_CAL_DBG_ENABLED
// Print debug data report.
void gyroCalDebugPrint(struct GyroCal* gyro_cal,
                       uint64_t timestamp_nanos_nanos);
#endif

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_CAL_H_
