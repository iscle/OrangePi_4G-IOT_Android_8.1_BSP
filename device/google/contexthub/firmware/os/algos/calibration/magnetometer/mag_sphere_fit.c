/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "calibration/magnetometer/mag_sphere_fit.h"

#include <errno.h>
#include <string.h>

#include "calibration/util/cal_log.h"

#define MAX_ITERATIONS 30
#define INITIAL_U_SCALE 1.0e-4f
#define GRADIENT_THRESHOLD 1.0e-16f
#define RELATIVE_STEP_THRESHOLD 1.0e-7f
#define FROM_MICRO_SEC_TO_SEC 1.0e-6f

void magCalSphereReset(struct MagCalSphere *mocs) {
  mocs->number_of_data_samples = 0;
  mocs->sample_counter = 0;
  memset(&mocs->sphere_data, 0, sizeof(mocs->sphere_data));
}

void initMagCalSphere(struct MagCalSphere *mocs, float x_bias, float y_bias,
                      float z_bias, float c00, float c01, float c02, float c10,
                      float c11, float c12, float c20, float c21, float c22,
                      uint32_t min_batch_window_in_micros,
                      size_t min_num_diverse_vectors,
                      size_t max_num_max_distance, float var_threshold,
                      float max_min_threshold, float local_field,
                      float threshold_tuning_param,
                      float max_distance_tuning_param) {
  initMagCal(&mocs->moc, x_bias, y_bias, z_bias, c00, c01, c02, c10, c11, c12,
             c20, c21, c22, min_batch_window_in_micros, min_num_diverse_vectors,
             max_num_max_distance, var_threshold, max_min_threshold,
             local_field, threshold_tuning_param, max_distance_tuning_param);
  mocs->inv_data_size = 1.0f / (float)NUM_SPHERE_FIT_DATA;
  mocs->batch_time_in_sec =
      (float)(min_batch_window_in_micros) * FROM_MICRO_SEC_TO_SEC;
  // Initialize to take every sample, default setting.
  mocs->sample_drop = 0;
  magCalSphereReset(mocs);

  // Setting lm params.
  mocs->sphere_fit.params.max_iterations = MAX_ITERATIONS;
  mocs->sphere_fit.params.initial_u_scale = INITIAL_U_SCALE;
  mocs->sphere_fit.params.gradient_threshold = GRADIENT_THRESHOLD;
  mocs->sphere_fit.params.relative_step_threshold = RELATIVE_STEP_THRESHOLD;
  sphereFitInit(&mocs->sphere_fit.sphere_cal, &mocs->sphere_fit.params,
                MIN_NUM_SPHERE_FIT_POINTS);
  sphereFitSetSolverData(&mocs->sphere_fit.sphere_cal,
                         &mocs->sphere_fit.lm_data);
  calDataReset(&mocs->sphere_fit.sphere_param);
}

void magCalSphereDestroy(struct MagCalSphere *mocs) { (void)mocs; }

void magCalSphereOdrUpdate(struct MagCalSphere *mocs, float odr_in_hz) {
  // Calculate the numbers of samples to be dropped, in order to fill up
  // the data set.
  float sample_drop = odr_in_hz * mocs->batch_time_in_sec * mocs->inv_data_size;
  mocs->sample_drop = (uint32_t)floorf(sample_drop);
}

// Updates the sphere fit data set, by calculating the numbers
// of samples to be dropped, based on odr_in_hz, to fill up the available memory
// in the given batch size window.
void magCalSphereDataUpdate(struct MagCalSphere *mocs, float x, float y,
                            float z) {
  // build a vector.
  const float vec[3] = {x, y, z};

  // sample_counter for the down sampling.
  mocs->sample_counter++;

  // checking if sample_count >= sample_drop, if yes we store the mag sample in
  // the data set.
  if (mocs->sample_counter >= mocs->sample_drop) {
    if (mocs->number_of_data_samples < NUM_SPHERE_FIT_DATA) {
      memcpy(&mocs->sphere_data[mocs->number_of_data_samples *
                                THREE_AXIS_DATA_DIM],
             vec, sizeof(float) * 3);
      // counting the numbers of samples in the data set.
      mocs->number_of_data_samples++;
    }
    // resetting the sample_counter.
    mocs->sample_counter = 0;
  }
}

// Runs the Sphere Fit.
enum MagUpdate magCalSphereFit(struct MagCalSphere *mocs,
                               uint64_t sample_time_us) {
  // Setting up sphere fit data.
  struct SphereFitData data = {&mocs->sphere_data[0], NULL,
                               mocs->number_of_data_samples, mocs->moc.radius};
  float initial_bias[3] = {mocs->moc.x_bias, mocs->moc.y_bias,
                           mocs->moc.z_bias};

  // Setting initial bias values based on the KASA fit.
  sphereFitSetInitialBias(&mocs->sphere_fit.sphere_cal, initial_bias);

  // Running the sphere fit and checking if successful.
  if (sphereFitRunCal(&mocs->sphere_fit.sphere_cal, &data, sample_time_us)) {
    // Updating Sphere parameters. Can use "calDataCorrectData" function to
    // correct data.
    sphereFitGetLatestCal(&mocs->sphere_fit.sphere_cal,
                          &mocs->sphere_fit.sphere_param);

    // Updating that a full sphere fit is available.
    return UPDATE_SPHERE_FIT;
  }
  return NO_UPDATE;
}

enum MagUpdate magCalSphereUpdate(struct MagCalSphere *mocs,
                                  uint64_t sample_time_us, float x, float y,
                                  float z) {
  enum MagUpdate new_cal = NO_UPDATE;

  // Saving data for sphere fit.
  magCalSphereDataUpdate(mocs, x, y, z);

  // Checking if KASA found a bias, if yes can run the sphere fit.
  if (UPDATE_BIAS == magCalUpdate(&mocs->moc, sample_time_us, x, y, z)) {
    // Running the sphere fit algo.
    new_cal = magCalSphereFit(mocs, sample_time_us);

    // Resetting.
    sphereFitReset(&mocs->sphere_fit.sphere_cal);
    magCalSphereReset(mocs);

    // If moc.kasa_batching is false, ran into a time out, hence the sphere
    // algo has to be reset as well.
  } else if (!mocs->moc.kasa_batching) {
    magCalSphereReset(mocs);
  }

  // Return which update has happened.
  return new_cal;
}
