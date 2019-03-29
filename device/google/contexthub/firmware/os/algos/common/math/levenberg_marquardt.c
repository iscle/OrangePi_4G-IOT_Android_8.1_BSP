#include "common/math/levenberg_marquardt.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "common/math/macros.h"
#include "common/math/mat.h"
#include "common/math/vec.h"

// FORWARD DECLARATIONS
////////////////////////////////////////////////////////////////////////
static bool checkRelativeStepSize(const float *step, const float *state,
                                  size_t dim, float relative_error_threshold);

static bool computeResidualAndGradients(ResidualAndJacobianFunction func,
                                        const float *state, const void *f_data,
                                        float *jacobian,
                                        float gradient_threshold,
                                        size_t state_dim, size_t meas_dim,
                                        float *residual, float *gradient,
                                        float *hessian);

static bool computeStep(const float *gradient, float *hessian, float *L,
                        float damping_factor, size_t dim, float *step);

const static float kEps = 1e-10f;

// FUNCTION IMPLEMENTATIONS
////////////////////////////////////////////////////////////////////////
void lmSolverInit(struct LmSolver *solver, const struct LmParams *params,
                  ResidualAndJacobianFunction func) {
  ASSERT_NOT_NULL(solver);
  ASSERT_NOT_NULL(params);
  ASSERT_NOT_NULL(func);
  memset(solver, 0, sizeof(struct LmSolver));
  memcpy(&solver->params, params, sizeof(struct LmParams));
  solver->func = func;
  solver->num_iter = 0;
}

void lmSolverDestroy(struct LmSolver *solver) {
  (void)solver;
}

void lmSolverSetData(struct LmSolver *solver, struct LmData *data) {
  ASSERT_NOT_NULL(solver);
  ASSERT_NOT_NULL(data);
  solver->data = data;
}

enum LmStatus lmSolverSolve(struct LmSolver *solver, const float *initial_state,
                            void *f_data, size_t state_dim, size_t meas_dim,
                            float *state) {
  // Initialize parameters.
  float damping_factor = 0.0f;
  float v = 2.0f;

  // Check dimensions.
  if (meas_dim > MAX_LM_MEAS_DIMENSION || state_dim > MAX_LM_STATE_DIMENSION) {
    return INVALID_DATA_DIMENSIONS;
  }

  // Check pointers (note that f_data can be null if no additional data is
  // required by the error function).
  ASSERT_NOT_NULL(solver);
  ASSERT_NOT_NULL(initial_state);
  ASSERT_NOT_NULL(state);
  ASSERT_NOT_NULL(solver->data);

  // Allocate memory for intermediate variables.
  float state_new[MAX_LM_STATE_DIMENSION];
  struct LmData *data = solver->data;

  // state = initial_state, num_iter = 0
  memcpy(state, initial_state, sizeof(float) * state_dim);
  solver->num_iter = 0;

  // Compute initial cost function gradient and return if already sufficiently
  // small to satisfy solution.
  if (computeResidualAndGradients(solver->func, state, f_data, data->temp,
                                  solver->params.gradient_threshold, state_dim,
                                  meas_dim, data->residual,
                                  data->gradient,
                                  data->hessian)) {
    return GRADIENT_SUFFICIENTLY_SMALL;
  }

  // Initialize damping parameter.
  damping_factor = solver->params.initial_u_scale *
      matMaxDiagonalElement(data->hessian, state_dim);

  // Iterate solution.
  for (solver->num_iter = 0;
       solver->num_iter < solver->params.max_iterations;
       ++solver->num_iter) {

    // Compute new solver step.
    if (!computeStep(data->gradient, data->hessian, data->temp, damping_factor,
                     state_dim, data->step)) {
      return CHOLESKY_FAIL;
    }

    // If the new step is already sufficiently small, we have a solution.
    if (checkRelativeStepSize(data->step, state, state_dim,
                              solver->params.relative_step_threshold)) {
      return RELATIVE_STEP_SUFFICIENTLY_SMALL;
    }

    // state_new = state + step.
    vecAdd(state_new, state, data->step, state_dim);

    // Compute new cost function residual.
    solver->func(state_new, f_data, data->residual_new, NULL);

    // Compute ratio of expected to actual cost function gain for this step.
    const float gain_ratio = computeGainRatio(data->residual,
                                              data->residual_new,
                                              data->step, data->gradient,
                                              damping_factor, state_dim,
                                              meas_dim);

    // If gain ratio is positive, the step size is good, otherwise adjust
    // damping factor and compute a new step.
    if (gain_ratio > 0.0f) {
      // Set state to new state vector: state = state_new.
      memcpy(state, state_new, sizeof(float) * state_dim);

      // Check if cost function gradient is now sufficiently small,
      // in which case we have a local solution.
      if (computeResidualAndGradients(solver->func, state, f_data, data->temp,
                                      solver->params.gradient_threshold,
                                      state_dim, meas_dim, data->residual,
                                      data->gradient, data->hessian)) {
        return GRADIENT_SUFFICIENTLY_SMALL;
      }

      // Update damping factor based on gain ratio.
      // Note, this update logic comes from Equation 2.21 in the following:
      // [Madsen, Kaj, Hans Bruun Nielsen, and Ole Tingleff.
      // "Methods for non-linear least squares problems." (2004)].
      const float tmp = 2.f * gain_ratio - 1.f;
      damping_factor *= NANO_MAX(0.33333f, 1.f - tmp * tmp * tmp);
      v = 2.f;
    } else {
      // Update damping factor and try again.
      damping_factor *= v;
      v *= 2.f;
    }
  }

  return HIT_MAX_ITERATIONS;
}

float computeGainRatio(const float *residual, const float *residual_new,
                       const float *step, const float *gradient,
                       float damping_factor, size_t state_dim,
                       size_t meas_dim) {
  // Compute true_gain = residual' residual - residual_new' residual_new.
  const float true_gain = vecDot(residual, residual, meas_dim)
      - vecDot(residual_new, residual_new, meas_dim);

  // predicted gain = 0.5 * step' * (damping_factor * step + gradient).
  float tmp[MAX_LM_STATE_DIMENSION];
  vecScalarMul(tmp, step, damping_factor, state_dim);
  vecAddInPlace(tmp, gradient, state_dim);
  const float predicted_gain = 0.5f * vecDot(step, tmp, state_dim);

  // Check that we don't divide by zero! If denominator is too small,
  // set gain_ratio = 1 to use the current step.
  if (predicted_gain < kEps) {
    return 1.f;
  }

  return true_gain / predicted_gain;
}

/*
 * Tests if a solution is found based on the size of the step relative to the
 * current state magnitude. Returns true if a solution is found.
 *
 * TODO(dvitus): consider optimization of this function to use squared norm
 * rather than norm for relative error computation to avoid square root.
 */
bool checkRelativeStepSize(const float *step, const float *state,
                           size_t dim, float relative_error_threshold) {
  // r = eps * (||x|| + eps)
  const float relative_error = relative_error_threshold *
      (vecNorm(state, dim) + relative_error_threshold);

  // solved if ||step|| <= r
  // use squared version of this compare to avoid square root.
  return (vecNormSquared(step, dim) <= relative_error * relative_error);
}

/*
 * Computes the residual, f(x), as well as the gradient and hessian of the cost
 * function for the given state.
 *
 * Returns a boolean indicating if the computed gradient is sufficiently small
 * to indicate that a solution has been found.
 *
 * INPUTS:
 * state: state estimate (x) for which to compute the gradient & hessian.
 * f_data: pointer to parameter data needed for the residual or jacobian.
 * jacobian: pointer to temporary memory for storing jacobian.
 *           Must be at least MAX_LM_STATE_DIMENSION * MAX_LM_MEAS_DIMENSION.
 * gradient_threshold: if gradient is below this threshold, function returns 1.
 *
 * OUTPUTS:
 * residual: f(x).
 * gradient: - J' f(x), where J = df(x)/dx
 * hessian: df^2(x)/dx^2 = J' J
 */
bool computeResidualAndGradients(ResidualAndJacobianFunction func,
                                 const float *state, const void *f_data,
                                 float *jacobian, float gradient_threshold,
                                 size_t state_dim, size_t meas_dim,
                                 float *residual, float *gradient,
                                 float *hessian) {
  // Compute residual and Jacobian.
  ASSERT_NOT_NULL(state);
  ASSERT_NOT_NULL(residual);
  ASSERT_NOT_NULL(gradient);
  ASSERT_NOT_NULL(hessian);
  func(state, f_data, residual, jacobian);

  // Compute the cost function hessian = jacobian' jacobian and
  // gradient = -jacobian' residual
  matTransposeMultiplyMat(hessian, jacobian, meas_dim, state_dim);
  matTransposeMultiplyVec(gradient, jacobian, residual, meas_dim, state_dim);
  vecScalarMulInPlace(gradient, -1.f, state_dim);

  // Check if solution is found (cost function gradient is sufficiently small).
  return (vecMaxAbsoluteValue(gradient, state_dim) < gradient_threshold);
}

/*
 * Computes the Levenberg-Marquardt solver step to satisfy the following:
 *    (J'J + uI) * step = - J' f
 *
 * INPUTS:
 * gradient:  -J'f
 * hessian:  J'J
 * L: temp memory of at least MAX_LM_STATE_DIMENSION * MAX_LM_STATE_DIMENSION.
 * damping_factor: u
 * dim: state dimension
 *
 * OUTPUTS:
 * step: solution to the above equation.
 * Function returns false if the solution fails (due to cholesky failure),
 * otherwise returns true.
 *
 * Note that the hessian is modified in this function in order to reduce
 * local memory requirements.
 */
bool computeStep(const float *gradient, float *hessian, float *L,
                 float damping_factor, size_t dim, float *step) {

  // 1) A = hessian + damping_factor * Identity.
  matAddConstantDiagonal(hessian, damping_factor, dim);

  // 2) Solve A * step = gradient for step.
  // a) compute cholesky decomposition of A = L L^T.
  if (!matCholeskyDecomposition(L, hessian, dim)) {
    return false;
  }

  // b) solve for step via back-solve.
  return matLinearSolveCholesky(step, L, gradient, dim);
}
