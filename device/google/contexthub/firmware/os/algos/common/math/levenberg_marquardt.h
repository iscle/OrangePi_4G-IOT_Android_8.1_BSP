/*
 * This module contains the definition of a Levenberg-Marquardt solver for
 * non-linear least squares problems of the following form:
 *
 *   arg min  ||f(X)||  =  arg min  f(X)^T f(X)
 *      X                     X
 *
 * where X is an Nx1 state vector and f(X) is an Mx1 nonlinear measurement error
 * function of X which we wish to minimize the norm of.
 *
 * Levenberg-Marquardt solves the above problem through a damped Gauss-Newton
 * method where the solver step on each iteration is chosen such that:
 *       (J' J + uI) * step = - J' f(x)
 * where J = df(x)/dx is the Jacobian of the error function, u is a damping
 * factor, and I is the identity matrix.
 *
 * The algorithm implemented here follows Algorithm 3.16 outlined in this paper:
 * Madsen, Kaj, Hans Bruun Nielsen, and Ole Tingleff.
 * "Methods for non-linear least squares problems." (2004).
 *
 * This algorithm uses a variant of the Marquardt method for updating the
 * damping factor which ensures that changes in the factor have no
 * discontinuities or fluttering behavior between solver steps.
 */
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_LEVENBERG_MARQUARDT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_LEVENBERG_MARQUARDT_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Function pointer for computing residual, f(X), and Jacobian, J = df(X)/dX
// for a given state value, X, and other parameter data needed for computing
// these terms, f_data.
//
// Note if f_data is not needed, it is allowable for f_data to be passed in
// as NULL.
//
// jacobian is also allowed to be passed in as NULL, and in this case only the
// residual will be computed and returned.
typedef void (*ResidualAndJacobianFunction)(const float *state,
                                            const void *f_data,
                                            float *residual, float *jacobian);


#define MAX_LM_STATE_DIMENSION (10)
#define MAX_LM_MEAS_DIMENSION (50)

// Structure containing fixed parameters for a single LM solve.
struct LmParams {
  // maximum number of iterations allowed by the solver.
  uint32_t max_iterations;

  // initial scaling factor for setting the damping factor, i.e.:
  // damping_factor = initial_u_scale * max(J'J).
  float initial_u_scale;

  // magnitude of the cost function gradient required for solution, i.e.
  // max(gradient) = max(J'f(x)) < gradient_threshold.
  float gradient_threshold;

  // magnitude of relative error required for solution step, i.e.
  // ||step|| / ||state|| < relative_step_thresold.
  float relative_step_threshold;
};

// Structure containing data needed for a single LM solve.
// Defining the data here allows flexibility in how the memory is allocated
// for the solver.
struct LmData {
  float step[MAX_LM_STATE_DIMENSION];
  float residual[MAX_LM_MEAS_DIMENSION];
  float residual_new[MAX_LM_MEAS_DIMENSION];
  float gradient[MAX_LM_MEAS_DIMENSION];
  float hessian[MAX_LM_STATE_DIMENSION * MAX_LM_STATE_DIMENSION];
  float temp[MAX_LM_STATE_DIMENSION * MAX_LM_MEAS_DIMENSION];
};

// Enumeration indicating status of the LM solver.
enum LmStatus {
  RUNNING = 0,
  // Successful solve conditions:
  RELATIVE_STEP_SUFFICIENTLY_SMALL,  // solver step is sufficiently small.
  GRADIENT_SUFFICIENTLY_SMALL,  // cost function gradient is below threshold.
  HIT_MAX_ITERATIONS,

  // Solver failure modes:
  CHOLESKY_FAIL,
  INVALID_DATA_DIMENSIONS,
};

// Structure for containing variables needed for a Levenberg-Marquardt solver.
struct LmSolver {
  // Solver parameters.
  struct LmParams params;

  // Pointer to solver data.
  struct LmData *data;

  // Function for computing residual (f(x)) and jacobian (df(x)/dx).
  ResidualAndJacobianFunction func;

  // Number of iterations in current solution.
  uint32_t num_iter;
};

// Initializes LM solver with provided parameters and error function.
void lmSolverInit(struct LmSolver *solver, const struct LmParams *params,
                  ResidualAndJacobianFunction error_func);

void lmSolverDestroy(struct LmSolver *solver);

// Sets pointer for temporary data needed for an individual LM solve.
// Note, this must be called prior to calling lmSolverSolve().
void lmSolverSetData(struct LmSolver *solver, struct LmData *data);

/*
 * Runs the LM solver for a given set of data and error function.
 *
 * INPUTS:
 * solver : pointer to an struct LmSolver structure.
 * initial_state : initial guess of the estimation state.
 * f_data : pointer to additional data needed by the error function.
 * state_dim : dimension of X.
 * meas_dim : dimension of f(X), defined in the error function.
 *
 * OUTPUTS:
 * LmStatus : enum indicating state of the solver on completion.
 * est_state : estimated state.
 */
enum LmStatus lmSolverSolve(struct LmSolver *solver, const float *initial_state,
                            void *f_data, size_t state_dim, size_t meas_dim,
                            float *est_state);

////////////////////////// TEST UTILITIES ////////////////////////////////////
// This function is exposed here for testing purposes only.
/*
 * Computes the ratio of actual cost function gain over expected cost
 * function gain for the given solver step.  This ratio is used to adjust
 * the solver step size for the next solver iteration.
 *
 * INPUTS:
 * residual: f(x) for the current state x.
 * residual_new: f(x + step) for the new state after the solver step.
 * step: the solver step.
 * gradient: gradient of the cost function F(x).
 * damping_factor: the current damping factor used in the solver.
 * state_dim: dimension of the state, x.
 * meas_dim: dimension of f(x).
 */
float computeGainRatio(const float *residual, const float *residual_new,
                       const float *step, const float *gradient,
                       float damping_factor, size_t state_dim,
                       size_t meas_dim);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_LEVENBERG_MARQUARDT_H_
