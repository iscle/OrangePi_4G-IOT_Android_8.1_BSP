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
 * This module contains an algorithm for performing a sphere fit calibration.
 * A sphere fit calibration solves the following non-linear least squares
 * problem:
 *
 *   arg min || ||M(x - b)|| - exp_norm ||
 *      M,b
 *
 * where:
 *  x is a 3xN matrix containing N 3-dimensional uncalibrated data points,
 *  M is a 3x3 lower diagonal scaling matrix
 *  b is a 3x1 offset vector.
 *  exp_norm is the expected norm of an individual calibration data point.
 * M and b are solved such that the norm of the calibrated data (M(x - b)) is
 * near exp_norm.
 *
 * This module uses a Levenberg-Marquardt nonlinear least squares solver to find
 * M and b.  M is assumed to be a lower diagonal, consisting of 6 parameters.
 *
 */
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_SPHERE_FIT_CALIBRATION_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_SPHERE_FIT_CALIBRATION_H_

#include <stdbool.h>
#include <stdint.h>

#include "calibration/common/calibration_data.h"
#include "common/math/levenberg_marquardt.h"

#ifdef __cplusplus
extern "C" {
#endif

#define MIN_NUM_SPHERE_FIT_POINTS (14)

// Enum defining the meaning of the state parameters.  The 9-parameter
// sphere fit calibration computes a lower-diagonal scaling matrix (M) and
// an offset such that:
//    x_corrected = M * (x_impaired - offset)
enum SphereFitParams {
  eParamScaleMatrix11 = 0,
  eParamScaleMatrix21,
  eParamScaleMatrix22,
  eParamScaleMatrix31,
  eParamScaleMatrix32,
  eParamScaleMatrix33,
  eParamOffset1,
  eParamOffset2,
  eParamOffset3,
  SF_STATE_DIM
};

// Structure containing the data to be used for the sphere fit calibration.
struct SphereFitData {
  // Data for fit (assumed to be a matrix of size num_fit_points x SF_DATA_DIM)
  const float *fit_data;

  // Pointer to standard deviations of the fit data, used to weight individual
  // data points.  Assumed to point to a matrix of dimensions
  // num_fit_points x THREE_AXIS_DIM.
  // If NULL, data will all be used with equal weighting in the fit.
  const float *fit_data_std;

  // Number of fit points.
  size_t num_fit_points;

  // Expected data norm.
  float expected_norm;
};

// Structure for a sphere fit calibration, including a non-linear least squares
// solver and the latest state estimate.
struct SphereFitCal {
  // Levenberg-Marquardt solver.
  struct LmSolver lm_solver;

  // Minimum number of points for computing a calibration.
  size_t min_points_for_cal;

  // State estimate.
  float x[SF_STATE_DIM];
  uint64_t estimate_time_nanos;

  // Initial state for solver.
  float x0[SF_STATE_DIM];
};

// Initialize sphere fit calibration structure with solver and fit params.
void sphereFitInit(struct SphereFitCal *sphere_cal,
                   const struct LmParams *lm_params,
                   const size_t min_num_points_for_cal);

// Clears state estimate and initial state.
void sphereFitReset(struct SphereFitCal *sphere_cal);

// Sets data pointer for single solve of the Levenberg-Marquardt solver.
// Must be called before calling sphereFitRunCal().
void sphereFitSetSolverData(struct SphereFitCal *sphere_cal,
                            struct LmData *lm_data);

// Sends in a set of calibration data and attempts to run calibration.
// Returns true if a calibration was successfully triggered with this data.
bool sphereFitRunCal(struct SphereFitCal *sphere_cal,
                     const struct SphereFitData *data,
                     uint64_t timestamp_nanos);

// Set an initial condition for the bias state.
void sphereFitSetInitialBias(struct SphereFitCal *sphere_cal,
                             const float initial_bias[THREE_AXIS_DIM]);

// Returns the latest calibration data in a ThreeAxisCalData structure.
void sphereFitGetLatestCal(const struct SphereFitCal *sphere_cal,
                           struct ThreeAxisCalData *cal_data);

/////////////////  TEST UTILITIES ///////////////////////////////////////////
// The following functions are exposed in the header for testing only.

// The ResidualAndJacobianFunction for sphere calibration in the
// Levenberg-Marquardt solver.
void sphereFitResidAndJacobianFunc(const float *state, const void *f_data,
                                   float *residual, float *jacobian);

#ifdef __cplusplus
}
#endif

#endif  //  LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_COMMON_SPHERE_FIT_CALIBRATION_H_
