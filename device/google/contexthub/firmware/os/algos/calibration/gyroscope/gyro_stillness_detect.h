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

///////////////////////////////////////////////////////////////
/*
 * This module implements a sensor stillness detector with a
 * look-ahead feature that allows the sensor's mean to be
 * computed for an extended stillness period (one-pass, online
 * method) sample by sample without a memory buffer. Stillness
 * is computed using non-overlapping windows of signal variance
 * and thresholding logic. The look-ahead feature ensures that
 * the mean computation is not corrupted by the onset of sensor
 * activity.
 *
 * NOTE - Time units are agnostic (i.e., determined by the
 * user's application and usage). However, typical time units
 * are nanoseconds.
 */
///////////////////////////////////////////////////////////////
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_STILLNESS_DETECT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_STILLNESS_DETECT_H_

#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

struct GyroStillDet {
  // Variance threshold for the stillness confidence score.
  float var_threshold;  // [sensor units]^2

  // Delta about the variance threshold for calculation of the
  // stillness confidence score [0,1].
  float confidence_delta;  // [sensor units]^2

  // Flag to indicate when enough samples have been collected for
  // a complete stillness calculation.
  bool stillness_window_ready;

  // Flag to signal the beginning of a new stillness detection window. This
  // is used to keep track of the window start time.
  bool start_new_window;

  // Starting time stamp for the the current window.
  uint64_t window_start_time;

  // Accumulator variables for tracking the sample mean during
  // the stillness period.
  uint32_t num_acc_samples;
  float mean_x, mean_y, mean_z;

  // Accumulator variables for computing the window sample mean and
  // variance for the current window (used for stillness detection).
  uint32_t num_acc_win_samples;
  float win_mean_x, win_mean_y, win_mean_z;
  float assumed_mean_x, assumed_mean_y, assumed_mean_z;
  float acc_var_x, acc_var_y, acc_var_z;

  // Stillness period mean (used for look-ahead).
  float prev_mean_x, prev_mean_y, prev_mean_z;

  // Latest computed variance.
  float win_var_x, win_var_y, win_var_z;

  // Stillness confidence score for current and previous sample
  // windows [0,1] (used for look-ahead).
  float stillness_confidence;
  float prev_stillness_confidence;

  // Timestamp of last sample recorded.
  uint64_t last_sample_time;
};

/////// FUNCTION PROTOTYPES //////////////////////////////////////////

// Initialize the gyro_still_det_t structure.
void gyroStillDetInit(struct GyroStillDet* gyro_still_det,
                      float var_threshold, float confidence_delta);

// Update the stillness detector with a new sample.
void gyroStillDetUpdate(struct GyroStillDet* gyro_still_det,
                        uint64_t stillness_win_endtime, uint64_t sample_time,
                        float x, float y, float z);

// Calculates and returns the stillness confidence score [0,1].
float gyroStillDetCompute(struct GyroStillDet* gyro_still_det);

// Resets the stillness detector and initiates a new detection window.
// 'reset_stats' determines whether the stillness statistics are reset.
void gyroStillDetReset(struct GyroStillDet* gyro_still_det, bool reset_stats);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_GYROSCOPE_GYRO_STILLNESS_DETECT_H_
