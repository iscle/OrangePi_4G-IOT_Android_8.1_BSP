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

#include "calibration/common/diversity_checker.h"

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include "common/math/vec.h"

// Struct initialization.
void diversityCheckerInit(
    struct DiversityChecker* diverse_data,
    size_t min_num_diverse_vectors,
    size_t max_num_max_distance,
    float var_threshold,
    float max_min_threshold,
    float local_field,
    float threshold_tuning_param,
    float max_distance_tuning_param) {
  ASSERT_NOT_NULL(diverse_data);

  // Initialize parameters.
  diverse_data->threshold_tuning_param_sq =
      (threshold_tuning_param * threshold_tuning_param);
  diverse_data->max_distance_tuning_param_sq =
      (max_distance_tuning_param * max_distance_tuning_param);

  // Updating the threshold and max_distance using assumed local field.
  // Testing for zero and negative local_field.
  if (local_field <= 0) {
    local_field = 1;
  }
  diversityCheckerLocalFieldUpdate(diverse_data, local_field);
  diverse_data->min_num_diverse_vectors = min_num_diverse_vectors;

  // Checking for min_num_diverse_vectors = 0.
  if (min_num_diverse_vectors < 1) {
    diverse_data->min_num_diverse_vectors = 1;
  }
  diverse_data->max_num_max_distance = max_num_max_distance;
  diverse_data->var_threshold = var_threshold;
  diverse_data->max_min_threshold = max_min_threshold;

  // Setting the rest to zero.
  diversityCheckerReset(diverse_data);

   // Debug Messages
#ifdef DIVERSE_DEBUG_ENABLE
  memset(&diverse_data->diversity_dbg, 0, sizeof(diverse_data->diversity_dbg));
#endif
}

// Reset
void diversityCheckerReset(struct DiversityChecker* diverse_data) {
  ASSERT_NOT_NULL(diverse_data);
  // Clear data memory.
  memset(&diverse_data->diverse_data, 0,
         sizeof(diverse_data->diverse_data));

  // Resetting counters and data full bit.
  diverse_data->num_points = 0;
  diverse_data->num_max_dist_violations = 0;
  diverse_data->data_full = false;
}

void diversityCheckerUpdate(
    struct DiversityChecker* diverse_data, float x, float y, float z) {
  ASSERT_NOT_NULL(diverse_data);

  // Converting three single inputs to a vector.
  const float vec[3] = {x, y, z};

  // Result vector for vector difference.
  float vec_diff[3];

  // normSquared result (k)
  float norm_squared_result;

  // If memory is full, no need to run through the data.
  if (!diverse_data->data_full) {
    size_t i;
    // Running over all existing data points
    for (i = 0; i < diverse_data->num_points; ++i) {
      // v = v1 - v2;
      vecSub(vec_diff,
             &diverse_data->diverse_data[i * THREE_AXIS_DATA_DIM],
             vec,
             THREE_AXIS_DATA_DIM);

      // k = |v|^2
      norm_squared_result = vecNormSquared(vec_diff, THREE_AXIS_DATA_DIM);

      // if k < Threshold then leave the function.
      if (norm_squared_result < diverse_data->threshold) {
        return;
      }

      // if k > max_distance, count and leave the function.
      if (norm_squared_result > diverse_data->max_distance) {
        diverse_data->num_max_dist_violations++;
        return;
      }
    }

    // If none of the above caused to leave the function, data is diverse.
    // Notice that the first data vector will be stored no matter what.
    memcpy(&diverse_data->
           diverse_data[diverse_data->num_points * THREE_AXIS_DATA_DIM],
           vec,
           sizeof(float) * THREE_AXIS_DATA_DIM);
    // Count new data point.
    diverse_data->num_points++;

    // Setting data_full to 1, if memory is full.
    if (diverse_data->num_points == NUM_DIVERSE_VECTORS) {
      diverse_data->data_full = true;
    }
  }
}

bool diversityCheckerNormQuality(struct DiversityChecker* diverse_data,
                                 float x_bias,
                                 float y_bias,
                                 float z_bias) {
  ASSERT_NOT_NULL(diverse_data);
  // If not enough diverse data points or max distance violations return false.
  if (diverse_data->num_points <= diverse_data->min_num_diverse_vectors ||
      diverse_data->num_max_dist_violations >=
      diverse_data->max_num_max_distance) {
    return false;
  }
  float vec_bias[3] = {x_bias, y_bias, z_bias};
  float vec_bias_removed[3];
  float norm_results;
  float acc_norm = 0.0f;
  float acc_norm_square = 0.0f;
  float max = 0.0f;
  float min = 0.0f;
  size_t i;
  for (i = 0; i < diverse_data->num_points; ++i) {
    // v = v1 - v_bias;
    vecSub(vec_bias_removed,
           &diverse_data->diverse_data[i * THREE_AXIS_DATA_DIM],
           vec_bias,
           THREE_AXIS_DATA_DIM);

    // norm = ||v||
    norm_results = vecNorm(vec_bias_removed, THREE_AXIS_DATA_DIM);

    // Accumulate for mean and VAR.
    acc_norm += norm_results;
    acc_norm_square += norm_results * norm_results ;

    if (i == 0) {
      min = norm_results;
      max = norm_results;
    }
    // Finding min
    if (norm_results < min) {
      min = norm_results;
    }

    // Finding max.
    if (norm_results > max) {
      max = norm_results;
    }
    // can leave the function if max-min is violated
    // no need to continue.
    if ((max - min) > diverse_data->max_min_threshold) {
      return false;
    }
  }
  float inv = 1.0f / diverse_data->num_points;
  float var = (acc_norm_square - (acc_norm * acc_norm) * inv) * inv;

  // Debug Message.
#ifdef DIVERSE_DEBUG_ENABLE
  diverse_data->diversity_dbg.diversity_count++;
  diverse_data->diversity_dbg.var_log = var;
  diverse_data->diversity_dbg.mean_log = acc_norm * inv;
  diverse_data->diversity_dbg.max_log = max;
  diverse_data->diversity_dbg.min_log = min;
  memcpy(&diverse_data->diversity_dbg.diverse_data_log,
         &diverse_data->diverse_data,
         sizeof(diverse_data->diversity_dbg.diverse_data_log));
#endif
  return (var < diverse_data->var_threshold);
}

void diversityCheckerLocalFieldUpdate(struct DiversityChecker* diverse_data,
                                      float local_field) {
  if (local_field > 0) {
    // Updating threshold based on the local field information.
    diverse_data->threshold = diverse_data->threshold_tuning_param_sq *
        (local_field * local_field);

    // Updating max distance based on the local field information.
    diverse_data->max_distance = diverse_data->max_distance_tuning_param_sq *
        (local_field * local_field);
  }
}
