#include "calibration/common/sphere_fit_calibration.h"

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "calibration/util/cal_log.h"
#include "common/math/mat.h"
#include "common/math/vec.h"

// FORWARD DECLARATIONS
///////////////////////////////////////////////////////////////////////////////
// Utility for converting solver state to a calibration data structure.
static void convertStateToCalStruct(const float x[SF_STATE_DIM],
                                    struct ThreeAxisCalData *calstruct);

static bool runCalibration(struct SphereFitCal *sphere_cal,
                           const struct SphereFitData *data,
                           uint64_t timestamp_nanos);

#define MIN_VALID_DATA_NORM (1e-4)

// FUNCTION IMPLEMENTATIONS
//////////////////////////////////////////////////////////////////////////////
void sphereFitInit(struct SphereFitCal *sphere_cal,
                   const struct LmParams *lm_params,
                   const size_t min_num_points_for_cal) {
  ASSERT_NOT_NULL(sphere_cal);
  ASSERT_NOT_NULL(lm_params);

  // Initialize LM solver.
  lmSolverInit(&sphere_cal->lm_solver, lm_params,
               &sphereFitResidAndJacobianFunc);

  // Reset other parameters.
  sphereFitReset(sphere_cal);

  // Set num points for calibration, checking that it is above min.
  if (min_num_points_for_cal < MIN_NUM_SPHERE_FIT_POINTS) {
    sphere_cal->min_points_for_cal = MIN_NUM_SPHERE_FIT_POINTS;
  } else {
    sphere_cal->min_points_for_cal = min_num_points_for_cal;
  }
}

void sphereFitReset(struct SphereFitCal *sphere_cal) {
  ASSERT_NOT_NULL(sphere_cal);

  // Set state to default (diagonal scale matrix and zero offset).
  memset(&sphere_cal->x0[0], 0, sizeof(float) * SF_STATE_DIM);
  sphere_cal->x0[eParamScaleMatrix11] = 1.f;
  sphere_cal->x0[eParamScaleMatrix22] = 1.f;
  sphere_cal->x0[eParamScaleMatrix33] = 1.f;
  memcpy(sphere_cal->x, sphere_cal->x0, sizeof(sphere_cal->x));

  // Reset time.
  sphere_cal->estimate_time_nanos = 0;
}

void sphereFitSetSolverData(struct SphereFitCal *sphere_cal,
                            struct LmData *lm_data) {
  ASSERT_NOT_NULL(sphere_cal);
  ASSERT_NOT_NULL(lm_data);

  // Set solver data.
  lmSolverSetData(&sphere_cal->lm_solver, lm_data);
}

bool sphereFitRunCal(struct SphereFitCal *sphere_cal,
                     const struct SphereFitData *data,
                     uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(sphere_cal);
  ASSERT_NOT_NULL(data);

  // Run calibration if have enough points.
  if (data->num_fit_points >= sphere_cal->min_points_for_cal) {
    return runCalibration(sphere_cal, data, timestamp_nanos);
  }

  return false;
}

void sphereFitSetInitialBias(struct SphereFitCal *sphere_cal,
                             const float initial_bias[THREE_AXIS_DIM]) {
  ASSERT_NOT_NULL(sphere_cal);
  sphere_cal->x0[eParamOffset1] = initial_bias[0];
  sphere_cal->x0[eParamOffset2] = initial_bias[1];
  sphere_cal->x0[eParamOffset3] = initial_bias[2];
}

void sphereFitGetLatestCal(const struct SphereFitCal *sphere_cal,
                           struct ThreeAxisCalData *cal_data) {
  ASSERT_NOT_NULL(sphere_cal);
  ASSERT_NOT_NULL(cal_data);
  convertStateToCalStruct(sphere_cal->x, cal_data);
  cal_data->calibration_time_nanos = sphere_cal->estimate_time_nanos;
}

void sphereFitResidAndJacobianFunc(const float *state, const void *f_data,
                                   float *residual, float *jacobian) {
  ASSERT_NOT_NULL(state);
  ASSERT_NOT_NULL(f_data);
  ASSERT_NOT_NULL(residual);

  const struct SphereFitData *data = (const struct SphereFitData*)f_data;

  // Verify that expected norm is non-zero, else use default of 1.0.
  float expected_norm = 1.0;
  ASSERT(data->expected_norm > MIN_VALID_DATA_NORM);
  if (data->expected_norm > MIN_VALID_DATA_NORM) {
    expected_norm = data->expected_norm;
  }

  // Convert parameters to calibration structure.
  struct ThreeAxisCalData calstruct;
  convertStateToCalStruct(state, &calstruct);

  // Compute Jacobian helper matrix if Jacobian requested.
  //
  // J = d(||M(x_data - bias)|| - expected_norm)/dstate
  //   = (M(x_data - bias) / ||M(x_data - bias)||) * d(M(x_data - bias))/dstate
  //   = x_corr / ||x_corr|| * A
  // A = d(M(x_data - bias))/dstate
  //   = [dy/dM11, dy/dM21, dy/dM22, dy/dM31, dy/dM32, dy/dM3,...
  //      dy/db1, dy/db2, dy/db3]'
  // where:
  // dy/dM11 = [x_data[0] - bias[0], 0, 0]
  // dy/dM21 = [0, x_data[0] - bias[0], 0]
  // dy/dM22 = [0, x_data[1] - bias[1], 0]
  // dy/dM31 = [0, 0, x_data[0] - bias[0]]
  // dy/dM32 = [0, 0, x_data[1] - bias[1]]
  // dy/dM33 = [0, 0, x_data[2] - bias[2]]
  // dy/db1 = [-scale_factor_x, 0, 0]
  // dy/db2 = [0, -scale_factor_y, 0]
  // dy/db3 = [0, 0, -scale_factor_z]
  float A[SF_STATE_DIM * THREE_AXIS_DIM];
  if (jacobian) {
    memset(jacobian, 0, sizeof(float) * SF_STATE_DIM * data->num_fit_points);
    memset(A, 0, sizeof(A));
    A[0 * SF_STATE_DIM + eParamOffset1] = -calstruct.scale_factor_x;
    A[1 * SF_STATE_DIM + eParamOffset2] = -calstruct.scale_factor_y;
    A[2 * SF_STATE_DIM + eParamOffset3] = -calstruct.scale_factor_z;
  }

  // Loop over all data points to compute residual and Jacobian.
  // TODO(dvitus): Use fit_data_std when available to weight residuals.
  float x_corr[THREE_AXIS_DIM];
  float x_bias_corr[THREE_AXIS_DIM];
  size_t i;
  for (i = 0; i < data->num_fit_points; ++i) {
    const float *x_data = &data->fit_data[i * THREE_AXIS_DIM];

    // Compute corrected sensor data
    calDataCorrectData(&calstruct, x_data, x_corr);

    // Compute norm of x_corr.
    const float norm = vecNorm(x_corr, THREE_AXIS_DIM);

    // Compute residual error: f_x = norm - exp_norm
    residual[i] = norm - data->expected_norm;

    // Compute Jacobian if valid pointer.
    if (jacobian) {
      if (norm < MIN_VALID_DATA_NORM) {
        return;
      }
      const float scale = 1.f / norm;

      // Compute bias corrected data.
      vecSub(x_bias_corr, x_data, calstruct.bias, THREE_AXIS_DIM);

      // Populate non-bias terms for A
      A[0 + eParamScaleMatrix11] = x_bias_corr[0];
      A[1 * SF_STATE_DIM + eParamScaleMatrix21] = x_bias_corr[0];
      A[1 * SF_STATE_DIM + eParamScaleMatrix22] = x_bias_corr[1];
      A[2 * SF_STATE_DIM + eParamScaleMatrix31] = x_bias_corr[0];
      A[2 * SF_STATE_DIM + eParamScaleMatrix32] = x_bias_corr[1];
      A[2 * SF_STATE_DIM + eParamScaleMatrix33] = x_bias_corr[2];

      // Compute J = x_corr / ||x_corr|| * A
      matTransposeMultiplyVec(&jacobian[i * SF_STATE_DIM], A, x_corr,
                              THREE_AXIS_DIM, SF_STATE_DIM);
      vecScalarMulInPlace(&jacobian[i * SF_STATE_DIM], scale, SF_STATE_DIM);
    }
  }
}

void convertStateToCalStruct(const float x[SF_STATE_DIM],
                             struct ThreeAxisCalData *calstruct) {
  memcpy(&calstruct->bias[0], &x[eParamOffset1],
         sizeof(float) * THREE_AXIS_DIM);
  calstruct->scale_factor_x = x[eParamScaleMatrix11];
  calstruct->skew_yx = x[eParamScaleMatrix21];
  calstruct->scale_factor_y = x[eParamScaleMatrix22];
  calstruct->skew_zx = x[eParamScaleMatrix31];
  calstruct->skew_zy = x[eParamScaleMatrix32];
  calstruct->scale_factor_z = x[eParamScaleMatrix33];
}

bool runCalibration(struct SphereFitCal *sphere_cal,
                    const struct SphereFitData *data,
                    uint64_t timestamp_nanos) {
  float x_sol[SF_STATE_DIM];

  // Run calibration
  const enum LmStatus status = lmSolverSolve(&sphere_cal->lm_solver,
                                             sphere_cal->x0, (void *)data,
                                             SF_STATE_DIM, data->num_fit_points,
                                             x_sol);

  // Check if solver was successful
  if (status == RELATIVE_STEP_SUFFICIENTLY_SMALL ||
      status == GRADIENT_SUFFICIENTLY_SMALL) {
    // TODO(dvitus): Check quality of new fit before using.
    // Store new fit.
#ifdef SPHERE_FIT_DBG_ENABLED
    CAL_DEBUG_LOG(
        "[SPHERE CAL]",
        "Solution found in %d iterations with status %d.\n",
        sphere_cal->lm_solver.num_iter, status);
    CAL_DEBUG_LOG(
        "[SPHERE CAL]",
        "Solution:\n {%s%d.%06d [M(1,1)], %s%d.%06d [M(2,1)], "
        "%s%d.%06d [M(2,2)], \n"
        "%s%d.%06d [M(3,1)], %s%d.%06d [M(3,2)], %s%d.%06d [M(3,3)], \n"
        "%s%d.%06d [b(1)], %s%d.%06d [b(2)], %s%d.%06d [b(3)]}.",
        CAL_ENCODE_FLOAT(x_sol[0], 6), CAL_ENCODE_FLOAT(x_sol[1], 6),
        CAL_ENCODE_FLOAT(x_sol[2], 6), CAL_ENCODE_FLOAT(x_sol[3], 6),
        CAL_ENCODE_FLOAT(x_sol[4], 6), CAL_ENCODE_FLOAT(x_sol[5], 6),
        CAL_ENCODE_FLOAT(x_sol[6], 6), CAL_ENCODE_FLOAT(x_sol[7], 6),
        CAL_ENCODE_FLOAT(x_sol[8], 6));
#endif
    memcpy(sphere_cal->x, x_sol, sizeof(x_sol));
    sphere_cal->estimate_time_nanos = timestamp_nanos;
    return true;
  } else {
#ifdef SPHERE_FIT_DBG_ENABLED
     CAL_DEBUG_LOG(
        "[SPHERE CAL]",
        "Solution failed in %d iterations with status %d.\n",
        sphere_cal->lm_solver.num_iter, status);
#endif
  }

  return false;
}
