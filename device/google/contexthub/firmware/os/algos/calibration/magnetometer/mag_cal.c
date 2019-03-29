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

#include "calibration/magnetometer/mag_cal.h"

#include <errno.h>
#include <string.h>

#include "calibration/util/cal_log.h"

#ifdef MAG_CAL_ORIGINAL_TUNING
#define MAX_EIGEN_RATIO 25.0f
#define MAX_EIGEN_MAG 80.0f          // uT
#define MIN_EIGEN_MAG 10.0f          // uT
#define MAX_FIT_MAG 80.0f
#define MIN_FIT_MAG 10.0f
#define MAX_BATCH_WINDOW 15000000UL  // 15 sec
#define MIN_BATCH_SIZE 25            // samples
#else
#define MAX_EIGEN_RATIO 15.0f
#define MAX_EIGEN_MAG 70.0f          // uT
#define MIN_EIGEN_MAG 20.0f          // uT
#define MAX_FIT_MAG 70.0f
#define MIN_FIT_MAG 20.0f
#define MAX_BATCH_WINDOW 15000000UL  // 15 sec
#define MIN_BATCH_SIZE 25            // samples
#endif

#ifdef DIVERSITY_CHECK_ENABLED
#define MAX_DISTANCE_VIOLATIONS 2
#ifdef SPHERE_FIT_ENABLED
# define MAX_ITERATIONS 30
# define INITIAL_U_SCALE 1.0e-4f
# define GRADIENT_THRESHOLD 1.0e-16f
# define RELATIVE_STEP_THRESHOLD 1.0e-7f
# define FROM_MICRO_SEC_TO_SEC 1.0e-6f
#endif
#endif

// eigen value magnitude and ratio test
static int moc_eigen_test(struct KasaFit *kasa) {
  // covariance matrix
  struct Mat33 S;
  S.elem[0][0] = kasa->acc_xx - kasa->acc_x * kasa->acc_x;
  S.elem[0][1] = S.elem[1][0] = kasa->acc_xy - kasa->acc_x * kasa->acc_y;
  S.elem[0][2] = S.elem[2][0] = kasa->acc_xz - kasa->acc_x * kasa->acc_z;
  S.elem[1][1] = kasa->acc_yy - kasa->acc_y * kasa->acc_y;
  S.elem[1][2] = S.elem[2][1] = kasa->acc_yz - kasa->acc_y * kasa->acc_z;
  S.elem[2][2] = kasa->acc_zz - kasa->acc_z * kasa->acc_z;

  struct Vec3 eigenvals;
  struct Mat33 eigenvecs;
  mat33GetEigenbasis(&S, &eigenvals, &eigenvecs);

  float evmax = (eigenvals.x > eigenvals.y) ? eigenvals.x : eigenvals.y;
  evmax = (eigenvals.z > evmax) ? eigenvals.z : evmax;

  float evmin = (eigenvals.x < eigenvals.y) ? eigenvals.x : eigenvals.y;
  evmin = (eigenvals.z < evmin) ? eigenvals.z : evmin;

  float eigenvals_sum = eigenvals.x + eigenvals.y + eigenvals.z;

  // Testing for negative number.
  float evmag = (eigenvals_sum > 0) ? sqrtf(eigenvals_sum) : 0;

  int eigen_pass = (evmin * MAX_EIGEN_RATIO > evmax) &&
                   (evmag > MIN_EIGEN_MAG) && (evmag < MAX_EIGEN_MAG);

  return eigen_pass;
}

// Kasa sphere fitting with normal equation
int magKasaFit(struct KasaFit *kasa, struct Vec3 *bias, float *radius) {
  //    A    *   out   =    b
  // (4 x 4)   (4 x 1)   (4 x 1)
  struct Mat44 A;
  A.elem[0][0] = kasa->acc_xx;
  A.elem[0][1] = kasa->acc_xy;
  A.elem[0][2] = kasa->acc_xz;
  A.elem[0][3] = kasa->acc_x;
  A.elem[1][0] = kasa->acc_xy;
  A.elem[1][1] = kasa->acc_yy;
  A.elem[1][2] = kasa->acc_yz;
  A.elem[1][3] = kasa->acc_y;
  A.elem[2][0] = kasa->acc_xz;
  A.elem[2][1] = kasa->acc_yz;
  A.elem[2][2] = kasa->acc_zz;
  A.elem[2][3] = kasa->acc_z;
  A.elem[3][0] = kasa->acc_x;
  A.elem[3][1] = kasa->acc_y;
  A.elem[3][2] = kasa->acc_z;
  A.elem[3][3] = 1.0f;

  struct Vec4 b;
  initVec4(&b, -kasa->acc_xw, -kasa->acc_yw, -kasa->acc_zw, -kasa->acc_w);

  struct Size4 pivot;
  mat44DecomposeLup(&A, &pivot);

  struct Vec4 out;
  mat44Solve(&A, &out, &b, &pivot);

  // sphere: (x - xc)^2 + (y - yc)^2 + (z - zc)^2 = r^2
  //
  // xc = -out[0] / 2, yc = -out[1] / 2, zc = -out[2] / 2
  // r = sqrt(xc^2 + yc^2 + zc^2 - out[3])

  struct Vec3 v;
  initVec3(&v, out.x, out.y, out.z);
  vec3ScalarMul(&v, -0.5f);

  float r_square = vec3Dot(&v, &v) - out.w;
  float r = (r_square > 0) ? sqrtf(r_square) : 0;

  initVec3(bias, v.x, v.y, v.z);
  *radius = r;

  int success = 0;
  if (r > MIN_FIT_MAG && r < MAX_FIT_MAG) {
    success = 1;
  }

  return success;
}

void magKasaReset(struct KasaFit *kasa) {
  kasa->acc_x = kasa->acc_y = kasa->acc_z = kasa->acc_w = 0.0f;
  kasa->acc_xx = kasa->acc_xy = kasa->acc_xz = kasa->acc_xw = 0.0f;
  kasa->acc_yy = kasa->acc_yz = kasa->acc_yw = 0.0f;
  kasa->acc_zz = kasa->acc_zw = 0.0f;

  kasa->nsamples = 0;
}

void magCalReset(struct MagCal *moc) {
  magKasaReset(&moc->kasa);
#ifdef DIVERSITY_CHECK_ENABLED
  diversityCheckerReset(&moc->diversity_checker);
#endif
  moc->start_time = 0;
  moc->kasa_batching = false;
}

static int moc_batch_complete(struct MagCal *moc, uint64_t sample_time_us) {
  int complete = 0;

  if ((sample_time_us - moc->start_time > moc->min_batch_window_in_micros) &&
      (moc->kasa.nsamples > MIN_BATCH_SIZE)) {
    complete = 1;

  } else if (sample_time_us - moc->start_time > MAX_BATCH_WINDOW) {
    // not enough samples collected in MAX_BATCH_WINDOW or too many
    // maximum distance violations detected.
    magCalReset(moc);
  }

  return complete;
}

void initKasa(struct KasaFit *kasa) {
  magKasaReset(kasa);
}

void initMagCal(struct MagCal *moc, float x_bias, float y_bias, float z_bias,
                float c00, float c01, float c02, float c10, float c11,
                float c12, float c20, float c21, float c22,
                uint32_t min_batch_window_in_micros
#ifdef DIVERSITY_CHECK_ENABLED
                ,size_t min_num_diverse_vectors
                ,size_t max_num_max_distance
                ,float var_threshold
                ,float max_min_threshold
                ,float local_field
                ,float threshold_tuning_param
                ,float max_distance_tuning_param
#endif
                ) {
  magCalReset(moc);
  moc->update_time = 0;
  moc->min_batch_window_in_micros = min_batch_window_in_micros;
  moc->radius = 0.0f;

  moc->x_bias = x_bias;
  moc->y_bias = y_bias;
  moc->z_bias = z_bias;

  moc->c00 = c00;
  moc->c01 = c01;
  moc->c02 = c02;
  moc->c10 = c10;
  moc->c11 = c11;
  moc->c12 = c12;
  moc->c20 = c20;
  moc->c21 = c21;
  moc->c22 = c22;

#ifdef MAG_CAL_DEBUG_ENABLE
  moc->mag_dbg.mag_trigger_count = 0;
  moc->mag_dbg.kasa_count = 0;
#endif

#ifdef DIVERSITY_CHECK_ENABLED
  // Diversity Checker
  diversityCheckerInit(&moc->diversity_checker,
                       min_num_diverse_vectors,
                       max_num_max_distance,
                       var_threshold,
                       max_min_threshold,
                       local_field,
                       threshold_tuning_param,
                       max_distance_tuning_param);
#endif
}

void magCalDestroy(struct MagCal *moc) { (void)moc; }

enum MagUpdate magCalUpdate(struct MagCal *moc, uint64_t sample_time_us,
                            float x, float y, float z) {
  enum MagUpdate new_bias = NO_UPDATE;

#ifdef DIVERSITY_CHECK_ENABLED
  // Diversity Checker Update.
  diversityCheckerUpdate(&moc->diversity_checker, x, y, z);
#endif

  // 1. run accumulators
  float w = x * x + y * y + z * z;

  moc->kasa.acc_x += x;
  moc->kasa.acc_y += y;
  moc->kasa.acc_z += z;
  moc->kasa.acc_w += w;

  moc->kasa.acc_xx += x * x;
  moc->kasa.acc_xy += x * y;
  moc->kasa.acc_xz += x * z;
  moc->kasa.acc_xw += x * w;

  moc->kasa.acc_yy += y * y;
  moc->kasa.acc_yz += y * z;
  moc->kasa.acc_yw += y * w;

  moc->kasa.acc_zz += z * z;
  moc->kasa.acc_zw += z * w;

  if (++moc->kasa.nsamples == 1) {
    moc->start_time = sample_time_us;
    moc->kasa_batching = true;
  }

  // 2. batch has enough samples?
  if (moc_batch_complete(moc, sample_time_us)) {
    float inv = 1.0f / moc->kasa.nsamples;

    moc->kasa.acc_x *= inv;
    moc->kasa.acc_y *= inv;
    moc->kasa.acc_z *= inv;
    moc->kasa.acc_w *= inv;

    moc->kasa.acc_xx *= inv;
    moc->kasa.acc_xy *= inv;
    moc->kasa.acc_xz *= inv;
    moc->kasa.acc_xw *= inv;

    moc->kasa.acc_yy *= inv;
    moc->kasa.acc_yz *= inv;
    moc->kasa.acc_yw *= inv;

    moc->kasa.acc_zz *= inv;
    moc->kasa.acc_zw *= inv;

    // 3. eigen test
    if (moc_eigen_test(&moc->kasa)) {
      struct Vec3 bias;
      float radius;
      // 4. Kasa sphere fitting
      if (magKasaFit(&moc->kasa, &bias, &radius)) {

#ifdef MAG_CAL_DEBUG_ENABLE
        moc->mag_dbg.kasa_count++;
        CAL_DEBUG_LOG("[MAG_CAL:KASA UPDATE] :,",
                      "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %lu, %lu",
                      CAL_ENCODE_FLOAT(bias.x, 3),
                      CAL_ENCODE_FLOAT(bias.y, 3),
                      CAL_ENCODE_FLOAT(bias.z, 3),
                      CAL_ENCODE_FLOAT(radius, 3),
                      (unsigned long int)moc->mag_dbg.kasa_count,
                      (unsigned long int)moc->mag_dbg.mag_trigger_count);
#endif

#ifdef DIVERSITY_CHECK_ENABLED
        // Update the local field.
        diversityCheckerLocalFieldUpdate(&moc->diversity_checker,
                                         radius);

        // checking if data is diverse.
        if (diversityCheckerNormQuality(&moc->diversity_checker,
                                        bias.x,
                                        bias.y,
                                        bias.z) &&
            moc->diversity_checker.num_max_dist_violations
            <= MAX_DISTANCE_VIOLATIONS) {

          // DEBUG PRINT OUT.
#ifdef MAG_CAL_DEBUG_ENABLE
          moc->mag_dbg.mag_trigger_count++;
#ifdef DIVERSE_DEBUG_ENABLE
          moc->diversity_checker.diversity_dbg.new_trigger = 1;
          CAL_DEBUG_LOG("[MAG_CAL:BIAS UPDATE] :, ",
                        "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%06d,"
                        "%s%d.%03d, %s%d.%03d, %s%d.%03d, %zu, %s%d.%03d, "
                        "%s%d.%03d, %lu, %lu, %llu, %s%d.%03d, %s%d.%03d, "
                        "%s%d.%03d, %llu",
                        CAL_ENCODE_FLOAT(bias.x, 3),
                        CAL_ENCODE_FLOAT(bias.y, 3),
                        CAL_ENCODE_FLOAT(bias.z, 3),
                        CAL_ENCODE_FLOAT(radius, 3),
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.diversity_dbg.var_log, 6),
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.diversity_dbg.mean_log, 3),
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.diversity_dbg.max_log, 3),
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.diversity_dbg.min_log, 3),
                        moc->diversity_checker.num_points,
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.threshold, 3),
                        CAL_ENCODE_FLOAT(
                            moc->diversity_checker.max_distance, 3),
                        (unsigned long int)moc->mag_dbg.mag_trigger_count,
                        (unsigned long int)moc->mag_dbg.kasa_count,
                        (unsigned long long int)sample_time_us,
                        CAL_ENCODE_FLOAT(moc->x_bias, 3),
                        CAL_ENCODE_FLOAT(moc->y_bias, 3),
                        CAL_ENCODE_FLOAT(moc->z_bias, 3),
                        (unsigned long long int)moc->update_time);
#endif
#endif
#endif
          moc->x_bias = bias.x;
          moc->y_bias = bias.y;
          moc->z_bias = bias.z;

          moc->radius = radius;
          moc->update_time = sample_time_us;

          new_bias = UPDATE_BIAS;

#ifdef DIVERSITY_CHECK_ENABLED
        }
#endif
      }
    }

    // 5. reset for next batch
    magCalReset(moc);
  }

  return new_bias;
}

void magCalGetBias(struct MagCal *moc, float *x, float *y, float *z) {
  *x = moc->x_bias;
  *y = moc->y_bias;
  *z = moc->z_bias;
}

void magCalAddBias(struct MagCal *moc, float x, float y, float z) {
  moc->x_bias += x;
  moc->y_bias += y;
  moc->z_bias += z;
}

void magCalRemoveBias(struct MagCal *moc, float xi, float yi, float zi,
                      float *xo, float *yo, float *zo) {
  *xo = xi - moc->x_bias;
  *yo = yi - moc->y_bias;
  *zo = zi - moc->z_bias;
}

void magCalSetSoftiron(struct MagCal *moc, float c00, float c01, float c02,
                       float c10, float c11, float c12, float c20, float c21,
                       float c22) {
  moc->c00 = c00;
  moc->c01 = c01;
  moc->c02 = c02;
  moc->c10 = c10;
  moc->c11 = c11;
  moc->c12 = c12;
  moc->c20 = c20;
  moc->c21 = c21;
  moc->c22 = c22;
}

void magCalRemoveSoftiron(struct MagCal *moc, float xi, float yi, float zi,
                          float *xo, float *yo, float *zo) {
  *xo = moc->c00 * xi + moc->c01 * yi + moc->c02 * zi;
  *yo = moc->c10 * xi + moc->c11 * yi + moc->c12 * zi;
  *zo = moc->c20 * xi + moc->c21 * yi + moc->c22 * zi;
}

#if defined MAG_CAL_DEBUG_ENABLE && defined DIVERSE_DEBUG_ENABLE
// This function prints every second sample parts of the dbg diverse_data_log,
// which ensures that all the messages get printed into the log file.
void magLogPrint(struct DiversityChecker* diverse_data, float temp) {
  // Sample counter.
  static size_t sample_counter = 0;
  const float* data_log_ptr =
      &diverse_data->diversity_dbg.diverse_data_log[0];
  if (diverse_data->diversity_dbg.new_trigger == 1) {
    sample_counter++;
    if (sample_counter == 2) {
      CAL_DEBUG_LOG("[MAG_CAL:MEMORY X] :,",
                    "%lu, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d"
                    ", %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d",
                    (unsigned long int)diverse_data->
                    diversity_dbg.diversity_count,
                    CAL_ENCODE_FLOAT(data_log_ptr[0*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[1*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[2*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[3*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[4*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[5*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[6*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[7*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[8*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[9*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[10*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[11*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[12*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[13*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[14*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[15*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[16*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[17*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[18*3], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[19*3], 3),
                    CAL_ENCODE_FLOAT(temp, 3));
    }

    if (sample_counter == 4) {
      CAL_DEBUG_LOG("[MAG_CAL:MEMORY Y] :,",
                    "%lu, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d"
                    ", %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, ",
                    (unsigned long int)diverse_data->
                    diversity_dbg.diversity_count,
                    CAL_ENCODE_FLOAT(data_log_ptr[0*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[1*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[2*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[3*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[4*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[5*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[6*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[7*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[8*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[9*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[10*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[11*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[12*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[13*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[14*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[15*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[16*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[17*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[18*3+1], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[19*3+1], 3));
    }
    if (sample_counter == 6) {
      CAL_DEBUG_LOG("[MAG_CAL:MEMORY Z] :,",
                    "%lu, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d"
                    ", %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, "
                    "%s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, %s%d.%03d, ",
                    (unsigned long int)diverse_data->
                    diversity_dbg.diversity_count,
                    CAL_ENCODE_FLOAT(data_log_ptr[0*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[1*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[2*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[3*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[4*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[5*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[6*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[7*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[8*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[9*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[10*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[11*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[12*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[13*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[14*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[15*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[16*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[17*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[18*3+2], 3),
                    CAL_ENCODE_FLOAT(data_log_ptr[19*3+2], 3));
      sample_counter = 0;
      diverse_data->diversity_dbg.new_trigger = 0;
    }
  }
}
#endif
