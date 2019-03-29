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
/////////////////////////////////////////////////////////////////////////////
/*
 * This module contains a data structure and corresponding helper functions for
 * a three-axis sensor calibration.  The calibration consists of a bias vector,
 * bias, and a lower-diagonal scaling and skew matrix, scale_skew_mat.
 *
 * The calibration is applied to impaired sensor data as follows:
 *
 * corrected_data = scale_skew_mat * (impaired_data - bias).
 */
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_CALIBRATION_DATA_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_CALIBRATION_DATA_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define THREE_AXIS_DIM (3)

// Calibration data structure.
struct ThreeAxisCalData {
  // Scale factor and skew terms.  Used to construct the following lower
  // diagonal scale_skew_mat:
  // scale_skew_mat = [scale_factor_x    0         0
  //                   skew_yx    scale_factor_y   0
  //                   skew_zx       skew_zy   scale_factor_z].
  float scale_factor_x;
  float scale_factor_y;
  float scale_factor_z;
  float skew_yx;
  float skew_zx;
  float skew_zy;

  // Sensor bias offset.
  float bias[THREE_AXIS_DIM];

  // Calibration time.
  uint64_t calibration_time_nanos;
};

// Set calibration data to identity scale factors, zero skew and
// zero bias.
void calDataReset(struct ThreeAxisCalData *calstruct);

// Apply a stored calibration to correct a single sample of impaired sensor
// data.
void calDataCorrectData(const struct ThreeAxisCalData* calstruct,
                        const float x_impaired[THREE_AXIS_DIM],
                        float* x_corrected);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_CALIBRATION_DATA_H_
