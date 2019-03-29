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
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_CAL_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_CAL_H_

#ifdef SPHERE_FIT_ENABLED
#ifndef DIVERSITY_CHECK_ENABLED
#define DIVERSITY_CHECK_ENABLED
#endif
#endif

#include <stdbool.h>
#include <stdint.h>
#include <sys/types.h>
#ifdef DIVERSITY_CHECK_ENABLED
#include "calibration/common/diversity_checker.h"
#endif
#include "common/math/mat.h"
#include "common/math/vec.h"

#ifdef __cplusplus
extern "C" {
#endif

struct KasaFit {
  float acc_x, acc_y, acc_z, acc_w;
  float acc_xx, acc_xy, acc_xz, acc_xw;
  float acc_yy, acc_yz, acc_yw, acc_zz, acc_zw;
  size_t nsamples;
};

enum MagUpdate {
  NO_UPDATE = 0x00,
  UPDATE_BIAS = 0x01,
  UPDATE_SPHERE_FIT = 0x02,
};

#ifdef MAG_CAL_DEBUG_ENABLE
struct MagDbg {
  uint32_t mag_trigger_count;
  uint32_t kasa_count;
};
#endif

struct MagCal {
#ifdef DIVERSITY_CHECK_ENABLED
  struct DiversityChecker diversity_checker;
#endif
  struct KasaFit kasa;

  uint64_t start_time;
  uint64_t update_time;
  uint32_t min_batch_window_in_micros;
  float x_bias, y_bias, z_bias;
  float radius;
  bool kasa_batching;
  float c00, c01, c02, c10, c11, c12, c20, c21, c22;

#ifdef MAG_CAL_DEBUG_ENABLE
  struct MagDbg mag_dbg;
#endif
};

void initKasa(struct KasaFit *kasa);

#ifdef DIVERSITY_CHECK_ENABLED
void initMagCal(struct MagCal *moc, float x_bias, float y_bias, float z_bias,
                float c00, float c01, float c02, float c10, float c11,
                float c12, float c20, float c21, float c22,
                uint32_t min_batch_window_in_micros,
                size_t min_num_diverse_vectors, size_t max_num_max_distance,
                float var_threshold, float max_min_threshold, float local_field,
                float threshold_tuning_param, float max_distance_tuning_param);
#else
void initMagCal(struct MagCal *moc, float x_bias, float y_bias, float z_bias,
                float c00, float c01, float c02, float c10, float c11,
                float c12, float c20, float c21, float c22,
                uint32_t min_batch_window_in_micros);
#endif

void magCalDestroy(struct MagCal *moc);

enum MagUpdate magCalUpdate(struct MagCal *moc, uint64_t sample_time_us,
                            float x, float y, float z);

void magCalGetBias(struct MagCal *moc, float *x, float *y, float *z);

void magCalAddBias(struct MagCal *moc, float x, float y, float z);

void magCalRemoveBias(struct MagCal *moc, float xi, float yi, float zi,
                      float *xo, float *yo, float *zo);

void magCalSetSoftiron(struct MagCal *moc, float c00, float c01, float c02,
                       float c10, float c11, float c12, float c20, float c21,
                       float c22);

void magCalRemoveSoftiron(struct MagCal *moc, float xi, float yi, float zi,
                          float *xo, float *yo, float *zo);

void magKasaReset(struct KasaFit *kasa);

void magCalReset(struct MagCal *moc);

int magKasaFit(struct KasaFit *kasa, struct Vec3 *bias, float *radius);

#if defined MAG_CAL_DEBUG_ENABLE && defined DIVERSITY_CHECK_ENABLED
void magLogPrint(struct DiversityChecker *moc, float temp);
#endif

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_MAGNETOMETER_MAG_CAL_H_
