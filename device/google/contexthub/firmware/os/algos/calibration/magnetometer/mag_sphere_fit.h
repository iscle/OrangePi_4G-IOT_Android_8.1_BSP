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
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_SPHERE_FIT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_SPHERE_FIT_H_

#include "calibration/common/sphere_fit_calibration.h"
#include "calibration/magnetometer/mag_cal.h"

#define NUM_SPHERE_FIT_DATA 50

#ifdef __cplusplus
extern "C" {
#endif

struct SphereFit {
  struct LmParams params;
  struct LmData lm_data;
  struct SphereFitCal sphere_cal;
  struct ThreeAxisCalData sphere_param;
};

struct MagCalSphere {
  // KASA Fit Struct.
  struct MagCal moc;

  // Sphere fit Struct.
  struct SphereFit sphere_fit;

  // down sampler control.
  uint32_t number_of_data_samples;
  uint32_t sample_counter;
  uint32_t sample_drop;
  float inv_data_size;
  float batch_time_in_sec;

  // Sphere fit data set.
  float sphere_data[THREE_AXIS_DATA_DIM * NUM_SPHERE_FIT_DATA];
};

void initMagCalSphere(struct MagCalSphere *mocs,
                      float x_bias, float y_bias, float z_bias,
                      float c00, float c01, float c02, float c10, float c11,
                      float c12, float c20, float c21, float c22,
                      uint32_t min_batch_window_in_micros,
                      size_t min_num_diverse_vectors,
                      size_t max_num_max_distance,
                      float var_threshold,
                      float max_min_threshold,
                      float local_field,
                      float threshold_tuning_param,
                      float max_distance_tuning_param);

void magCalSphereDestroy(struct MagCalSphere *mocs);

enum MagUpdate magCalSphereUpdate(struct MagCalSphere *mocs,
                                  uint64_t sample_time_us,
                                  float x, float y, float z);

void magCalSphereOdrUpdate(struct MagCalSphere *mocs, float odr_in_hz);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_SPHERE_FIT_H_
