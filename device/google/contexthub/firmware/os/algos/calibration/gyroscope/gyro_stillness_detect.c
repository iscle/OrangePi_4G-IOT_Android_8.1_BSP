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

#include "calibration/gyroscope/gyro_stillness_detect.h"
#include <string.h>

/////// FORWARD DECLARATIONS /////////////////////////////////////////

// Enforces the limits of an input value [0,1].
static float gyroStillDetLimit(float value);

/////// FUNCTION DEFINITIONS /////////////////////////////////////////

// Initialize the GyroStillDet structure.
void gyroStillDetInit(struct GyroStillDet* gyro_still_det,
                      float var_threshold, float confidence_delta) {
  // Clear all data structure variables to 0.
  memset(gyro_still_det, 0, sizeof(struct GyroStillDet));

  // Set the delta about the variance threshold for calculation
  // of the stillness confidence score.
  if (confidence_delta < var_threshold) {
    gyro_still_det->confidence_delta = confidence_delta;
  } else {
    gyro_still_det->confidence_delta = var_threshold;
  }

  // Set the variance threshold parameter for the stillness
  // confidence score.
  gyro_still_det->var_threshold = var_threshold;

  // Signal to start capture of next stillness data window.
  gyro_still_det->start_new_window = true;
}

// Update the stillness detector with a new sample.
void gyroStillDetUpdate(struct GyroStillDet* gyro_still_det,
                        uint64_t stillness_win_endtime, uint64_t sample_time,
                        float x, float y, float z) {
  // Using the method of the assumed mean to preserve some numerical
  // stability while avoiding per-sample divisions that the more
  // numerically stable Welford method would afford.

  // Reference for the numerical method used below to compute the
  // online mean and variance statistics:
  //   1). en.wikipedia.org/wiki/assumed_mean

  float delta = 0;

  // If the window end time is not valid then wait till it is.
  if (stillness_win_endtime <= 0) {
    return;
  }

  // Increment the number of samples.
  gyro_still_det->num_acc_samples++;

  // Online computation of mean for the running stillness period.
  gyro_still_det->mean_x += x;
  gyro_still_det->mean_y += y;
  gyro_still_det->mean_z += z;

  // Is this the first sample of a new window?
  if (gyro_still_det->start_new_window) {
    // Record the window start time.
    gyro_still_det->window_start_time = sample_time;
    gyro_still_det->start_new_window = false;

    // Update assumed mean values.
    gyro_still_det->assumed_mean_x = x;
    gyro_still_det->assumed_mean_y = y;
    gyro_still_det->assumed_mean_z = z;

    // Reset current window mean and variance.
    gyro_still_det->num_acc_win_samples = 0;
    gyro_still_det->win_mean_x = 0;
    gyro_still_det->win_mean_y = 0;
    gyro_still_det->win_mean_z = 0;
    gyro_still_det->acc_var_x = 0;
    gyro_still_det->acc_var_y = 0;
    gyro_still_det->acc_var_z = 0;
  } else {
    // Check to see if we have enough samples to compute a stillness
    // confidence score.
    gyro_still_det->stillness_window_ready =
        (sample_time >= stillness_win_endtime) &&
        (gyro_still_det->num_acc_samples > 1);
  }

  // Record the most recent sample time stamp.
  gyro_still_det->last_sample_time = sample_time;

  // Online window mean and variance ("one-pass" accumulation).
  gyro_still_det->num_acc_win_samples++;

  delta = (x - gyro_still_det->assumed_mean_x);
  gyro_still_det->win_mean_x += delta;
  gyro_still_det->acc_var_x += delta * delta;

  delta = (y - gyro_still_det->assumed_mean_y);
  gyro_still_det->win_mean_y += delta;
  gyro_still_det->acc_var_y += delta * delta;

  delta = (z - gyro_still_det->assumed_mean_z);
  gyro_still_det->win_mean_z += delta;
  gyro_still_det->acc_var_z += delta * delta;
}

// Calculates and returns the stillness confidence score [0,1].
float gyroStillDetCompute(struct GyroStillDet* gyro_still_det) {
  float tmp_denom = 1.f;
  float tmp_denom_mean = 1.f;

  // Don't divide by zero (not likely, but a precaution).
  if (gyro_still_det->num_acc_win_samples > 1) {
    tmp_denom = 1.f / (gyro_still_det->num_acc_win_samples - 1);
    tmp_denom_mean = 1.f / gyro_still_det->num_acc_win_samples;
  } else {
    // Return zero stillness confidence.
    gyro_still_det->stillness_confidence = 0;
    return gyro_still_det->stillness_confidence;
  }

  // Update the final calculation of window mean and variance.
  float tmp = gyro_still_det->win_mean_x;
  gyro_still_det->win_mean_x *= tmp_denom_mean;
  gyro_still_det->win_var_x =
      (gyro_still_det->acc_var_x - gyro_still_det->win_mean_x * tmp) *
      tmp_denom;

  tmp = gyro_still_det->win_mean_y;
  gyro_still_det->win_mean_y *= tmp_denom_mean;
  gyro_still_det->win_var_y =
      (gyro_still_det->acc_var_y - gyro_still_det->win_mean_y * tmp) *
      tmp_denom;

  tmp = gyro_still_det->win_mean_z;
  gyro_still_det->win_mean_z *= tmp_denom_mean;
  gyro_still_det->win_var_z =
      (gyro_still_det->acc_var_z - gyro_still_det->win_mean_z * tmp) *
      tmp_denom;

  // Adds the assumed mean value back to the total mean calculation.
  gyro_still_det->win_mean_x += gyro_still_det->assumed_mean_x;
  gyro_still_det->win_mean_y += gyro_still_det->assumed_mean_y;
  gyro_still_det->win_mean_z += gyro_still_det->assumed_mean_z;

  // Define the variance thresholds.
  float upper_var_thresh =
      (gyro_still_det->var_threshold + gyro_still_det->confidence_delta);

  float lower_var_thresh =
      (gyro_still_det->var_threshold - gyro_still_det->confidence_delta);

  // Compute the stillness confidence score.
  if ((gyro_still_det->win_var_x > upper_var_thresh) ||
      (gyro_still_det->win_var_y > upper_var_thresh) ||
      (gyro_still_det->win_var_z > upper_var_thresh)) {
    // Sensor variance exceeds the upper threshold (i.e., motion detected).
    // Set stillness confidence equal to 0.
    gyro_still_det->stillness_confidence = 0;

  } else {
    if ((gyro_still_det->win_var_x <= lower_var_thresh) &&
        (gyro_still_det->win_var_y <= lower_var_thresh) &&
        (gyro_still_det->win_var_z <= lower_var_thresh)) {
      // Sensor variance is below the lower threshold (i.e., stillness
      // detected).
      // Set stillness confidence equal to 1.
      gyro_still_det->stillness_confidence = 1.f;

    } else {
      // Motion detection thresholds not exceeded. Compute the stillness
      // confidence score.

      float var_thresh = gyro_still_det->var_threshold;

      // Compute the stillness confidence score.
      // Each axis score is limited [0,1].
      tmp_denom = 1.f / (upper_var_thresh - lower_var_thresh);
      gyro_still_det->stillness_confidence =
          gyroStillDetLimit(
              0.5f - (gyro_still_det->win_var_x - var_thresh) * tmp_denom) *
          gyroStillDetLimit(
              0.5f - (gyro_still_det->win_var_y - var_thresh) * tmp_denom) *
          gyroStillDetLimit(
              0.5f - (gyro_still_det->win_var_z - var_thresh) * tmp_denom);
    }
  }

  // Return the stillness confidence.
  return gyro_still_det->stillness_confidence;
}

// Resets the stillness detector and initiates a new detection window.
// 'reset_stats' determines whether the stillness statistics are reset.
void gyroStillDetReset(struct GyroStillDet* gyro_still_det,
                       bool reset_stats) {
  float tmp_denom = 1.f;

  // Reset the stillness data ready flag.
  gyro_still_det->stillness_window_ready = false;

  // Signal to start capture of next stillness data window.
  gyro_still_det->start_new_window = true;

  // Track the stillness confidence (current->previous).
  gyro_still_det->prev_stillness_confidence =
      gyro_still_det->stillness_confidence;

  // Track changes in the mean estimate.
  if (gyro_still_det->num_acc_samples > 1) {
    tmp_denom = 1.f / gyro_still_det->num_acc_samples;
  }
  gyro_still_det->prev_mean_x = gyro_still_det->mean_x * tmp_denom;
  gyro_still_det->prev_mean_y = gyro_still_det->mean_y * tmp_denom;
  gyro_still_det->prev_mean_z = gyro_still_det->mean_z * tmp_denom;

  // Reset the current statistics to zero.
  if (reset_stats) {
    gyro_still_det->num_acc_samples = 0;
    gyro_still_det->mean_x = 0;
    gyro_still_det->mean_y = 0;
    gyro_still_det->mean_z = 0;
    gyro_still_det->acc_var_x = 0;
    gyro_still_det->acc_var_y = 0;
    gyro_still_det->acc_var_z = 0;
  }
}

// Enforces the limits of an input value [0,1].
float gyroStillDetLimit(float value) {
  // Fix limits [0,1].
  if (value < 0) {
    value = 0;
  } else {
    if (value > 1.f) {
      value = 1.f;
    }
  }

  return value;
}
