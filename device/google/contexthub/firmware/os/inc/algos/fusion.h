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

#ifndef FUSION_H_

#define FUSION_H_

#include <common/math/vec.h>
#include <common/math/mat.h>
#include <common/math/quat.h>

#include <stdbool.h>
#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

struct FusionParam {
  float gyro_var;
  float gyro_bias_var;
  float acc_stdev;
  float mag_stdev;
};

struct Fusion {
    Quat x0;
    struct Vec3 x1;

    struct Mat33 P[2][2];
    struct Mat33 GQGt[2][2];

    struct Mat33 Phi0[2];
    struct Vec3 Ba, Bm;
    uint32_t mInitState;
    float mPredictDt;
    struct Vec3 mData[3];
    uint32_t mCount[3];
    uint32_t flags;

    float trustedMagDuration;
    bool    lastMagInvalid;

    float  fake_mag_decimation;
    struct FusionParam param;
};

enum FusionFlagBits {
    FUSION_USE_MAG      = 1 << 0,
    FUSION_USE_GYRO     = 1 << 1,
    FUSION_REINITIALIZE = 1 << 2,
};

enum MagTrustMode {
    NORMAL,
    INITIALIZATION,  // right after initialization of fusion
    BACK_TO_VALID,   // when the mag value goes from invalid to valid
    MANUAL_MAG_CAL   // right after a manual calibration
};

void initFusion(struct Fusion *fusion, uint32_t flags);

void fusionHandleGyro(struct Fusion *fusion, const struct Vec3 *w, float dT);
int fusionHandleAcc(struct Fusion *fusion, const struct Vec3 *a, float dT);
int fusionHandleMag(struct Fusion *fusion, const struct Vec3 *m, float dT);

// set trust mode of mag sensors depending on scenarios, see MagTrustMode
void fusionSetMagTrust(struct Fusion *fusion, int mode);

void fusionGetAttitude(const struct Fusion *fusion, struct Vec4 *attitude);
void fusionGetBias(const struct Fusion *fusion, struct Vec3 *bias);
void fusionGetRotationMatrix(const struct Fusion *fusion, struct Mat33 *R);
int fusionHasEstimate(const struct Fusion *fusion);


#ifdef __cplusplus
}
#endif

#endif  // FUSION_H_
