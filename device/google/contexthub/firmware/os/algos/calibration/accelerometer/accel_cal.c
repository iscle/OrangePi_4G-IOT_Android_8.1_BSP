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

#include "calibration/accelerometer/accel_cal.h"
#include <errno.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "calibration/magnetometer/mag_cal.h"
#include "calibration/util/cal_log.h"

#define KSCALE \
  0.101936799f         // Scaling from m/s^2 to g (0.101 = 1/(9.81 m/s^2)).
#define KSCALE2 9.81f  // Scaling from g to m/s^2.
#define PHI 0.707f     // = 1/sqrt(2) gives a 45 degree angle for sorting data.
#define PHIb -0.707f
#define PHIZ 0.866f    // smaller Z sphere cap, opening angle is 30 degrees.
#define PHIZb -0.866f
#define G_NORM_MAX \
  1.38f  // Norm during stillness should be 1 g, checking from max min values.
#define G_NORM_MIN 0.68f
#define MAX_OFF 0.1f    // Will not accept offsets that are larger than 100 mg.
#define MIN_TEMP 20.0f  // No Data is collected below 20 degree C.
#define MAX_TEMP 45.0f  // No Data is collected above 45 degree C.
#define TEMP_CUT 30     // Separation point for temperature buckets 30 degree C.
#define EIGEN_RATIO 0.35  // EIGEN_RATIO (must be greater than 0.35).
#define EIGEN_MAG 0.97    // Eigen value magnitude (must be greater than 0.97).
#ifdef ACCEL_CAL_DBG_ENABLED
#define TEMP_HIST_LOW \
  16  // Putting all Temp counts in first bucket for temp < 16 degree C.
#define TEMP_HIST_HIGH \
  62  // Putting all Temp counts in last bucket for temp > 62 degree C.
#define HIST_COUNT 9
#endif
#ifdef IMU_TEMP_DBG_ENABLED
#define IMU_TEMP_DELTA_TIME_NANOS \
  5000000000 // Printing every 5 seconds IMU temp.
#endif

/////////// Start Debug //////////////////////

#ifdef ACCEL_CAL_DBG_ENABLED
// Total bucket Counter.
static void accelStatsCounter(struct AccelStillDet *asd,
                              struct AccelStatsMem *adf) {
  // Sorting the data in the different buckets
  // x bucket ntx.
  if (PHI < asd->mean_x) {
    adf->ntx += 1;
  }
  // Negative x bucket ntxb.
  if (PHIb > asd->mean_x) {
    adf->ntxb += 1;
  }
  // Y bucket nty.
  if (PHI < asd->mean_y) {
    adf->nty += 1;
  }
  // Negative y bucket ntyb.
  if (PHIb > asd->mean_y) {
    adf->ntyb += 1;
  }
  // Z bucket ntz.
  if (PHIZ < asd->mean_z) {
    adf->ntz += 1;
  }
  // Negative z bucket ntzb.
  if (PHIZb > asd->mean_z) {
    adf->ntzb += 1;
  }
  // The leftover bucket ntle.
  if (PHI > asd->mean_x && PHIb < asd->mean_x && PHI > asd->mean_y &&
      PHIb < asd->mean_y && PHIZ > asd->mean_z && PHIZb < asd->mean_z) {
    adf->ntle += 1;
  }
}

// Temp histogram generation.
static void accelTempHisto(struct AccelStatsMem *adf, float temp) {
  int index = 0;

  // Take temp at every stillness detection.
  adf->start_time_nanos = 0;
  if (temp <= TEMP_HIST_LOW) {
    adf->t_hist[0] += 1;
    return;
  }
  if (temp >= TEMP_HIST_HIGH) {
    adf->t_hist[TEMP_HISTOGRAM - 1] += 1;
    return;
  }
  index = (int)(((temp - TEMP_HIST_LOW) / 2) + 1);
  adf->t_hist[index] += 1;
}

#endif
///////// End Debug ////////////////////

// Stillness detector reset.
static void asdReset(struct AccelStillDet *asd) {
  asd->nsamples = 0;
  asd->start_time = 0;
  asd->acc_x = asd->acc_y = asd->acc_z = 0.0f;
  asd->acc_xx = asd->acc_yy = asd->acc_zz = 0.0f;
}

// Stillness detector init.
static void accelStillInit(struct AccelStillDet *asd, uint32_t t0, uint32_t n_s,
                           float th) {
  memset(asd, 0, sizeof(struct AccelStillDet));
  asd->var_th = th;
  asd->min_batch_window = t0;
  asd->max_batch_window = t0 + 100000000;
  asd->min_batch_size = n_s;
  asd->n_still = 0;
}

// Good data reset.
static void agdReset(struct AccelGoodData *agd) {
  agd->nx = agd->nxb = 0;
  agd->ny = agd->nyb = 0;
  agd->nz = agd->nzb = 0;
  agd->nle = 0;
  agd->acc_t = agd->acc_tt = 0;
  agd->e_x = agd->e_y = agd->e_z = 0;
}

// Good data init.
static void accelGoodDataInit(struct AccelGoodData *agd, uint32_t fx,
                              uint32_t fxb, uint32_t fy, uint32_t fyb,
                              uint32_t fz, uint32_t fzb, uint32_t fle) {
  memset(agd, 0, sizeof(struct AccelGoodData));
  agd->nfx = fx;
  agd->nfxb = fxb;
  agd->nfy = fy;
  agd->nfyb = fyb;
  agd->nfz = fz;
  agd->nfzb = fzb;
  agd->nfle = fle;
  agd->var_t = 0;
  agd->mean_t = 0;
}

// Accel cal algo init (ready for temp buckets).
static void accelCalAlgoInit(struct AccelCalAlgo *acc, uint32_t fx,
                             uint32_t fxb, uint32_t fy, uint32_t fyb,
                             uint32_t fz, uint32_t fzb, uint32_t fle) {
  accelGoodDataInit(&acc->agd, fx, fxb, fy, fyb, fz, fzb, fle);
  initKasa(&acc->akf);
}

// Accel cal init.
void accelCalInit(struct AccelCal *acc, uint32_t t0, uint32_t n_s, float th,
                  uint32_t fx, uint32_t fxb, uint32_t fy, uint32_t fyb,
                  uint32_t fz, uint32_t fzb, uint32_t fle) {
  // Init core accel data.
  accelCalAlgoInit(&acc->ac1[0], fx, fxb, fy, fyb, fz, fzb, fle);
  accelCalAlgoInit(&acc->ac1[1], fx, fxb, fy, fyb, fz, fzb, fle);

  // Stillness Reset.
  accelStillInit(&acc->asd, t0, n_s, th);

// Debug data init.
#ifdef ACCEL_CAL_DBG_ENABLED
  memset(&acc->adf, 0, sizeof(struct AccelStatsMem));
#endif

  acc->x_bias = acc->y_bias = acc->z_bias = 0;
  acc->x_bias_new = acc->y_bias_new = acc->z_bias_new = 0;
  acc->average_temperature_celsius = 0;

#ifdef IMU_TEMP_DBG_ENABLED
  acc->temp_time_nanos = 0;
#endif
}

// Stillness time check.
static int stillnessBatchComplete(struct AccelStillDet *asd,
                                  uint64_t sample_time_nanos) {
  int complete = 0;

  // Checking if enough data is accumulated to calc Mean and Var.
  if ((sample_time_nanos - asd->start_time > asd->min_batch_window) &&
      (asd->nsamples > asd->min_batch_size)) {
    if (sample_time_nanos - asd->start_time < asd->max_batch_window) {
      complete = 1;
    } else {
      // Checking for too long batch window, if yes reset and start over.
      asdReset(asd);
      return complete;
    }
  } else if (sample_time_nanos - asd->start_time > asd->min_batch_window &&
             (asd->nsamples < asd->min_batch_size)) {
    // Not enough samples collected in max_batch_window during sample window.
    asdReset(asd);
  }
  return complete;
}

// Releasing Memory.
void accelCalDestroy(struct AccelCal *acc) { (void)acc; }

// Stillness Detection.
static int accelStillnessDetection(struct AccelStillDet *asd,
                                   uint64_t sample_time_nanos, float x, float y,
                                   float z) {
  float inv = 0.0f;
  int complete = 0.0f;
  float g_norm = 0.0f;

  // Accumulate for mean and VAR.
  asd->acc_x += x;
  asd->acc_xx += x * x;
  asd->acc_y += y;
  asd->acc_yy += y * y;
  asd->acc_z += z;
  asd->acc_zz += z * z;

  // Setting a new start time and wait until T0 is reached.
  if (++asd->nsamples == 1) {
    asd->start_time = sample_time_nanos;
  }
  if (stillnessBatchComplete(asd, sample_time_nanos)) {
    // Getting 1/#samples and checking asd->nsamples != 0.
    if (0 < asd->nsamples) {
      inv = 1.0f / asd->nsamples;
    } else {
      // Something went wrong resetting and start over.
      asdReset(asd);
      return complete;
    }
    // Calculating the VAR = sum(x^2)/n - sum(x)^2/n^2.
    asd->var_x = (asd->acc_xx - (asd->acc_x * asd->acc_x) * inv) * inv;
    asd->var_y = (asd->acc_yy - (asd->acc_y * asd->acc_y) * inv) * inv;
    asd->var_z = (asd->acc_zz - (asd->acc_z * asd->acc_z) * inv) * inv;
    // Checking if sensor is still.
    if (asd->var_x < asd->var_th && asd->var_y < asd->var_th &&
        asd->var_z < asd->var_th) {
      // Calcluating the MEAN = sum(x) / n.
      asd->mean_x = asd->acc_x * inv;
      asd->mean_y = asd->acc_y * inv;
      asd->mean_z = asd->acc_z * inv;
      // Calculating g_norm^2.
      g_norm = asd->mean_x * asd->mean_x + asd->mean_y * asd->mean_y +
               asd->mean_z * asd->mean_z;
      // Magnitude check, still passsing when we have worse case offset.
      if (g_norm < G_NORM_MAX && g_norm > G_NORM_MIN) {
        complete = 1;
        asd->n_still += 1;
      }
    }
    asdReset(asd);
  }
  return complete;
}

// Accumulate data for KASA fit.
static void accelCalUpdate(struct KasaFit *akf, struct AccelStillDet *asd) {
  // Run accumulators.
  float w = asd->mean_x * asd->mean_x + asd->mean_y * asd->mean_y +
            asd->mean_z * asd->mean_z;

  akf->acc_x += asd->mean_x;
  akf->acc_y += asd->mean_y;
  akf->acc_z += asd->mean_z;
  akf->acc_w += w;

  akf->acc_xx += asd->mean_x * asd->mean_x;
  akf->acc_xy += asd->mean_x * asd->mean_y;
  akf->acc_xz += asd->mean_x * asd->mean_z;
  akf->acc_xw += asd->mean_x * w;

  akf->acc_yy += asd->mean_y * asd->mean_y;
  akf->acc_yz += asd->mean_y * asd->mean_z;
  akf->acc_yw += asd->mean_y * w;

  akf->acc_zz += asd->mean_z * asd->mean_z;
  akf->acc_zw += asd->mean_z * w;
  akf->nsamples += 1;
}

// Good data detection, sorting and accumulate the data for Kasa.
static int accelGoodData(struct AccelStillDet *asd, struct AccelCalAlgo *ac1,
                         float temp) {
  int complete = 0;
  float inv = 0.0f;

  // Sorting the data in the different buckets and accum
  // x bucket nx.
  if (PHI < asd->mean_x && ac1->agd.nx < ac1->agd.nfx) {
    ac1->agd.nx += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Negative x bucket nxb.
  if (PHIb > asd->mean_x && ac1->agd.nxb < ac1->agd.nfxb) {
    ac1->agd.nxb += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Y bucket ny.
  if (PHI < asd->mean_y && ac1->agd.ny < ac1->agd.nfy) {
    ac1->agd.ny += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Negative y bucket nyb.
  if (PHIb > asd->mean_y && ac1->agd.nyb < ac1->agd.nfyb) {
    ac1->agd.nyb += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Z bucket nz.
  if (PHIZ < asd->mean_z && ac1->agd.nz < ac1->agd.nfz) {
    ac1->agd.nz += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Negative z bucket nzb.
  if (PHIZb > asd->mean_z && ac1->agd.nzb < ac1->agd.nfzb) {
    ac1->agd.nzb += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // The leftover bucket nle.
  if (PHI > asd->mean_x && PHIb < asd->mean_x && PHI > asd->mean_y &&
      PHIb < asd->mean_y && PHIZ > asd->mean_z && PHIZb < asd->mean_z &&
      ac1->agd.nle < ac1->agd.nfle) {
    ac1->agd.nle += 1;
    ac1->agd.acc_t += temp;
    ac1->agd.acc_tt += temp * temp;
    accelCalUpdate(&ac1->akf, asd);
  }
  // Checking if all buckets are full.
  if (ac1->agd.nx == ac1->agd.nfx && ac1->agd.nxb == ac1->agd.nfxb &&
      ac1->agd.ny == ac1->agd.nfy && ac1->agd.nyb == ac1->agd.nfyb &&
      ac1->agd.nz == ac1->agd.nfz && ac1->agd.nzb == ac1->agd.nfzb) {
    //  Check if akf->nsamples is zero.
    if (ac1->akf.nsamples == 0) {
      agdReset(&ac1->agd);
      magKasaReset(&ac1->akf);
      complete = 0;
      return complete;
    } else {
      // Normalize the data to the sample numbers.
      inv = 1.0f / ac1->akf.nsamples;
    }

    ac1->akf.acc_x *= inv;
    ac1->akf.acc_y *= inv;
    ac1->akf.acc_z *= inv;
    ac1->akf.acc_w *= inv;

    ac1->akf.acc_xx *= inv;
    ac1->akf.acc_xy *= inv;
    ac1->akf.acc_xz *= inv;
    ac1->akf.acc_xw *= inv;

    ac1->akf.acc_yy *= inv;
    ac1->akf.acc_yz *= inv;
    ac1->akf.acc_yw *= inv;

    ac1->akf.acc_zz *= inv;
    ac1->akf.acc_zw *= inv;

    // Calculate the temp VAR and MEA.N
    ac1->agd.var_t =
        (ac1->agd.acc_tt - (ac1->agd.acc_t * ac1->agd.acc_t) * inv) * inv;
    ac1->agd.mean_t = ac1->agd.acc_t * inv;
    complete = 1;
  }

  // If any of the buckets has a bigger number as specified, reset and start
  // over.
  if (ac1->agd.nx > ac1->agd.nfx || ac1->agd.nxb > ac1->agd.nfxb ||
      ac1->agd.ny > ac1->agd.nfy || ac1->agd.nyb > ac1->agd.nfyb ||
      ac1->agd.nz > ac1->agd.nfz || ac1->agd.nzb > ac1->agd.nfzb) {
    agdReset(&ac1->agd);
    magKasaReset(&ac1->akf);
    complete = 0;
    return complete;
  }
  return complete;
}

// Eigen value magnitude and ratio test.
static int accEigenTest(struct KasaFit *akf, struct AccelGoodData *agd) {
  // covariance matrix.
  struct Mat33 S;
  S.elem[0][0] = akf->acc_xx - akf->acc_x * akf->acc_x;
  S.elem[0][1] = S.elem[1][0] = akf->acc_xy - akf->acc_x * akf->acc_y;
  S.elem[0][2] = S.elem[2][0] = akf->acc_xz - akf->acc_x * akf->acc_z;
  S.elem[1][1] = akf->acc_yy - akf->acc_y * akf->acc_y;
  S.elem[1][2] = S.elem[2][1] = akf->acc_yz - akf->acc_y * akf->acc_z;
  S.elem[2][2] = akf->acc_zz - akf->acc_z * akf->acc_z;

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

  // Passing when evmin/evmax> EIGEN_RATIO.
  int eigen_pass = (evmin > evmax * EIGEN_RATIO) && (evmag > EIGEN_MAG);

  agd->e_x = eigenvals.x;
  agd->e_y = eigenvals.y;
  agd->e_z = eigenvals.z;

  return eigen_pass;
}

// Updating the new bias and save to pointers. Return true if the bias changed.
bool accelCalUpdateBias(struct AccelCal *acc, float *x, float *y, float *z) {
  *x = acc->x_bias_new;
  *y = acc->y_bias_new;
  *z = acc->z_bias_new;

  // Check to see if the bias changed since last call to accelCalUpdateBias.
  // Compiler does not allow us to use "==" and "!=" when comparing floats, so
  // just use "<" and ">".
  if ((acc->x_bias < acc->x_bias_new) || (acc->x_bias > acc->x_bias_new) ||
      (acc->y_bias < acc->y_bias_new) || (acc->y_bias > acc->y_bias_new) ||
      (acc->z_bias < acc->z_bias_new) || (acc->z_bias > acc->z_bias_new)) {
    acc->x_bias = acc->x_bias_new;
    acc->y_bias = acc->y_bias_new;
    acc->z_bias = acc->z_bias_new;
    return true;
  }

  return false;
}

// Set the (initial) bias.
void accelCalBiasSet(struct AccelCal *acc, float x, float y, float z) {
  acc->x_bias = acc->x_bias_new = x;
  acc->y_bias = acc->y_bias_new = y;
  acc->z_bias = acc->z_bias_new = z;
}

// Removing the bias.
void accelCalBiasRemove(struct AccelCal *acc, float *x, float *y, float *z) {
  *x = *x - acc->x_bias;
  *y = *y - acc->y_bias;
  *z = *z - acc->z_bias;
}

// Accel Cal Runner.
void accelCalRun(struct AccelCal *acc, uint64_t sample_time_nanos, float x,
                 float y, float z, float temp) {
  // Scaling to 1g, better for the algorithm.
  x *= KSCALE;
  y *= KSCALE;
  z *= KSCALE;

  // DBG: IMU temp messages every 5s.
#ifdef IMU_TEMP_DBG_ENABLED
  if ((sample_time_nanos - acc->temp_time_nanos) > IMU_TEMP_DELTA_TIME_NANOS) {
    CAL_DEBUG_LOG("IMU Temp Data: ",
                  ", %s%d.%02d,  %llu, %s%d.%05d, %s%d.%05d, %s%d.%05d \n",
                  CAL_ENCODE_FLOAT(temp, 2),
                  (unsigned long long int)sample_time_nanos,
                  CAL_ENCODE_FLOAT(acc->x_bias_new,5),
                  CAL_ENCODE_FLOAT(acc->y_bias_new,5),
                  CAL_ENCODE_FLOAT(acc->z_bias_new,5));
    acc->temp_time_nanos = sample_time_nanos;
    }
#endif

  int temp_gate = 0;

  // Temp GATE.
  if (temp < MAX_TEMP && temp > MIN_TEMP) {
    // Checking if accel is still.
    if (accelStillnessDetection(&acc->asd, sample_time_nanos, x, y, z)) {
#ifdef ACCEL_CAL_DBG_ENABLED
      // Creating temp hist data.
      accelTempHisto(&acc->adf, temp);
#endif

      // Two temp buckets.
      if (temp < TEMP_CUT) {
        temp_gate = 0;
      } else {
        temp_gate = 1;
      }
#ifdef ACCEL_CAL_DBG_ENABLED
      accelStatsCounter(&acc->asd, &acc->adf);
#endif
      // If still -> pass the averaged accel data (mean) to the
      // sorting, counting and accum function.
      if (accelGoodData(&acc->asd, &acc->ac1[temp_gate], temp)) {
        // Running the Kasa fit.
        struct Vec3 bias;
        float radius;

        // Grabbing the fit from the MAG cal.
        magKasaFit(&acc->ac1[temp_gate].akf, &bias, &radius);

        // If offset is too large don't take.
        if (fabsf(bias.x) < MAX_OFF && fabsf(bias.y) < MAX_OFF &&
            fabsf(bias.z) < MAX_OFF) {
          // Eigen Ratio Test.
          if (accEigenTest(&acc->ac1[temp_gate].akf,
                           &acc->ac1[temp_gate].agd)) {
            // Storing the new offsets and average temperature.
            acc->x_bias_new = bias.x * KSCALE2;
            acc->y_bias_new = bias.y * KSCALE2;
            acc->z_bias_new = bias.z * KSCALE2;
            acc->average_temperature_celsius = acc->ac1[temp_gate].agd.mean_t;
          }
#ifdef ACCEL_CAL_DBG_ENABLED
          //// Debug ///////
          acc->adf.noff += 1;
          // Resetting the counter for the offset history.
          if (acc->adf.n_o > HIST_COUNT) {
            acc->adf.n_o = 0;
          }

          // Storing the Debug data.
          acc->adf.x_o[acc->adf.n_o] = bias.x;
          acc->adf.y_o[acc->adf.n_o] = bias.y;
          acc->adf.z_o[acc->adf.n_o] = bias.z;
          acc->adf.e_x[acc->adf.n_o] = acc->ac1[temp_gate].agd.e_x;
          acc->adf.e_y[acc->adf.n_o] = acc->ac1[temp_gate].agd.e_y;
          acc->adf.e_z[acc->adf.n_o] = acc->ac1[temp_gate].agd.e_z;
          acc->adf.var_t[acc->adf.n_o] = acc->ac1[temp_gate].agd.var_t;
          acc->adf.mean_t[acc->adf.n_o] = acc->ac1[temp_gate].agd.mean_t;
          acc->adf.cal_time[acc->adf.n_o] = sample_time_nanos;
          acc->adf.rad[acc->adf.n_o] = radius;
          acc->adf.n_o += 1;
#endif
        } else {
#ifdef ACCEL_CAL_DBG_ENABLED
          acc->adf.noff_max += 1;
#endif
        }
        ///////////////

        // Resetting the structs for a new accel cal run.
        agdReset(&acc->ac1[temp_gate].agd);
        magKasaReset(&acc->ac1[temp_gate].akf);
      }
    }
  }
}

#ifdef ACCEL_CAL_DBG_ENABLED
// Debug Print Output
void accelCalDebPrint(struct AccelCal *acc, float temp) {
  static int32_t kk = 0;
  if (++kk == 1000) {
    // X offset history last 10 values.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,11,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(x_off history)\n",
        CAL_ENCODE_FLOAT(acc->adf.x_o[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.x_o[9], 6));

    // Y offset history last 10 values.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,12,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(y_off history)\n",
        CAL_ENCODE_FLOAT(acc->adf.y_o[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.y_o[9], 6));

    // Z offset history last 10 values.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,13,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(z_off history)\n",
        CAL_ENCODE_FLOAT(acc->adf.z_o[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.z_o[9], 6));

    // Temp history variation VAR of offset.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,14,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(VAR temp history)\n",
        CAL_ENCODE_FLOAT(acc->adf.var_t[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.var_t[9], 6));

    // Temp mean history of offset.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,15,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(MEAN Temp history)\n",
        CAL_ENCODE_FLOAT(acc->adf.mean_t[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.mean_t[9], 6));

    // KASA radius history.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,16,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(radius)\n",
        CAL_ENCODE_FLOAT(acc->adf.rad[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.rad[9], 6));
    kk = 0;
  }

  if (kk == 750) {
    // Eigen Vector X.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, "
        "7,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(eigen x)\n",
        CAL_ENCODE_FLOAT(acc->adf.e_x[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_x[9], 6));
    // Y.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, "
        "8,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(eigen y)\n",
        CAL_ENCODE_FLOAT(acc->adf.e_y[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_y[9], 6));
    // Z.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, "
        "9,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,%s%d.%"
        "06d,%s%d.%06d,%s%d.%06d,%s%d.%06d,}(eigen z)\n",
        CAL_ENCODE_FLOAT(acc->adf.e_z[0], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[1], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[2], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[3], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[4], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[5], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[6], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[7], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[8], 6),
        CAL_ENCODE_FLOAT(acc->adf.e_z[9], 6));
    // Accel Time in ns.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL,10,%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,}("
        "timestamp ns)\n",
        acc->adf.cal_time[0], acc->adf.cal_time[1], acc->adf.cal_time[2],
        acc->adf.cal_time[3], acc->adf.cal_time[4], acc->adf.cal_time[5],
        acc->adf.cal_time[6], acc->adf.cal_time[7], acc->adf.cal_time[8],
        acc->adf.cal_time[9]);
  }

  if (kk == 500) {
    // Total bucket count.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 0,%2d, %2d, %2d, %2d, %2d, %2d, %2d,}(Total Bucket #)\n",
        (unsigned)acc->adf.ntx, (unsigned)acc->adf.ntxb, (unsigned)acc->adf.nty,
        (unsigned)acc->adf.ntyb, (unsigned)acc->adf.ntz,
        (unsigned)acc->adf.ntzb, (unsigned)acc->adf.ntle);
    // Live bucket count lower.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 1,%2d, %2d, %2d, %2d, %2d, %2d, %2d, %3d,}(Bucket # "
        "lower)\n",
        (unsigned)acc->ac1[0].agd.nx, (unsigned)acc->ac1[0].agd.nxb,
        (unsigned)acc->ac1[0].agd.ny, (unsigned)acc->ac1[0].agd.nyb,
        (unsigned)acc->ac1[0].agd.nz, (unsigned)acc->ac1[0].agd.nzb,
        (unsigned)acc->ac1[0].agd.nle, (unsigned)acc->ac1[0].akf.nsamples);
    // Live bucket count hogher.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 2,%2d, %2d, %2d, %2d, %2d, %2d, %2d, %3d,}(Bucket # "
        "higher)\n",
        (unsigned)acc->ac1[1].agd.nx, (unsigned)acc->ac1[1].agd.nxb,
        (unsigned)acc->ac1[1].agd.ny, (unsigned)acc->ac1[1].agd.nyb,
        (unsigned)acc->ac1[1].agd.nz, (unsigned)acc->ac1[1].agd.nzb,
        (unsigned)acc->ac1[1].agd.nle, (unsigned)acc->ac1[1].akf.nsamples);
    // Offset used.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 3,%s%d.%06d, %s%d.%06d, %s%d.%06d, %2d,}(updated offset "
        "x,y,z, total # of offsets)\n",
        CAL_ENCODE_FLOAT(acc->x_bias, 6), CAL_ENCODE_FLOAT(acc->y_bias, 6),
        CAL_ENCODE_FLOAT(acc->z_bias, 6), (unsigned)acc->adf.noff);
    // Offset New.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 4,%s%d.%06d, %s%d.%06d, %s%d.%06d, %s%d.%06d,}(New offset "
        "x,y,z, live temp)\n",
        CAL_ENCODE_FLOAT(acc->x_bias_new, 6),
        CAL_ENCODE_FLOAT(acc->y_bias_new, 6),
        CAL_ENCODE_FLOAT(acc->z_bias_new, 6), CAL_ENCODE_FLOAT(temp, 6));
    // Temp Histogram.
    CAL_DEBUG_LOG(
        "[BMI160]",
        "{MK_ACCEL, 5,%7d, %7d, %7d, %7d, %7d, %7d, %7d, %7d, %7d, %7d, %7d, "
        "%7d, %7d,}(temp histo)\n",
        (unsigned)acc->adf.t_hist[0], (unsigned)acc->adf.t_hist[1],
        (unsigned)acc->adf.t_hist[2], (unsigned)acc->adf.t_hist[3],
        (unsigned)acc->adf.t_hist[4], (unsigned)acc->adf.t_hist[5],
        (unsigned)acc->adf.t_hist[6], (unsigned)acc->adf.t_hist[7],
        (unsigned)acc->adf.t_hist[8], (unsigned)acc->adf.t_hist[9],
        (unsigned)acc->adf.t_hist[10], (unsigned)acc->adf.t_hist[11],
        (unsigned)acc->adf.t_hist[12]);
    CAL_DEBUG_LOG(
        "[BMI160]",
        "M{K_ACCEL, 6,%7d, %7d, %7d,%7d, %7d, %7d, %7d, %7d, %7d, %7d, %7d, "
        "%7d,}(temp histo)\n",
        (unsigned)acc->adf.t_hist[13], (unsigned)acc->adf.t_hist[14],
        (unsigned)acc->adf.t_hist[15], (unsigned)acc->adf.t_hist[16],
        (unsigned)acc->adf.t_hist[17], (unsigned)acc->adf.t_hist[18],
        (unsigned)acc->adf.t_hist[19], (unsigned)acc->adf.t_hist[20],
        (unsigned)acc->adf.t_hist[21], (unsigned)acc->adf.t_hist[22],
        (unsigned)acc->adf.t_hist[23], (unsigned)acc->adf.t_hist[24]);
  }
}
#endif
