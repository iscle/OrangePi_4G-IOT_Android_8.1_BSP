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

// adapted from frameworks/native/services/sensorservice/Fusion.cpp

#include <algos/fusion.h>

#include <errno.h>
#include <nanohub_math.h>
#include <stdio.h>

#include <seos.h>

#ifdef DEBUG_CH
// change to 0 to disable fusion debugging output
#define DEBUG_FUSION  0
#endif

#define ACC     1
#define MAG     2
#define GYRO    4

#define DEFAULT_GYRO_VAR         1e-7f
#define DEFAULT_GYRO_BIAS_VAR    1e-12f
#define DEFAULT_ACC_STDEV        5e-2f
#define DEFAULT_MAG_STDEV        5e-1f

#define GEOMAG_GYRO_VAR          2e-4f
#define GEOMAG_GYRO_BIAS_VAR     1e-4f
#define GEOMAG_ACC_STDEV         0.02f
#define GEOMAG_MAG_STDEV         0.02f

#define SYMMETRY_TOLERANCE       1e-10f
#define FAKE_MAG_INTERVAL        1.0f  //sec

#define NOMINAL_GRAVITY          9.81f
#define FREE_FALL_THRESHOLD      (0.1f * NOMINAL_GRAVITY)
#define FREE_FALL_THRESHOLD_SQ   (FREE_FALL_THRESHOLD * FREE_FALL_THRESHOLD)

#define MAX_VALID_MAGNETIC_FIELD    75.0f
#define MAX_VALID_MAGNETIC_FIELD_SQ (MAX_VALID_MAGNETIC_FIELD * MAX_VALID_MAGNETIC_FIELD)

#define MIN_VALID_MAGNETIC_FIELD    20.0f   //norminal mag field strength is 25uT in some area
#define MIN_VALID_MAGNETIC_FIELD_SQ (MIN_VALID_MAGNETIC_FIELD * MIN_VALID_MAGNETIC_FIELD)

#define MIN_VALID_CROSS_PRODUCT_MAG     1.0e-3
#define MIN_VALID_CROSS_PRODUCT_MAG_SQ  (MIN_VALID_CROSS_PRODUCT_MAG * MIN_VALID_CROSS_PRODUCT_MAG)

#define DELTA_TIME_MARGIN 1.0e-9f

#define TRUST_DURATION_MANUAL_MAG_CAL      5.f  //unit: seconds

void initFusion(struct Fusion *fusion, uint32_t flags) {
    fusion->flags = flags;

    if (flags & FUSION_USE_GYRO) {
        // normal fusion mode
        fusion->param.gyro_var = DEFAULT_GYRO_VAR;
        fusion->param.gyro_bias_var = DEFAULT_GYRO_BIAS_VAR;
        fusion->param.acc_stdev = DEFAULT_ACC_STDEV;
        fusion->param.mag_stdev = DEFAULT_MAG_STDEV;
    } else {
        // geo mag mode
        fusion->param.gyro_var = GEOMAG_GYRO_VAR;
        fusion->param.gyro_bias_var = GEOMAG_GYRO_BIAS_VAR;
        fusion->param.acc_stdev = GEOMAG_ACC_STDEV;
        fusion->param.mag_stdev = GEOMAG_MAG_STDEV;
    }

    if (flags & FUSION_REINITIALIZE)
    {
        initVec3(&fusion->Ba, 0.0f, 0.0f, 1.0f);
        initVec3(&fusion->Bm, 0.0f, 1.0f, 0.0f);

        initVec4(&fusion->x0, 0.0f, 0.0f, 0.0f, 0.0f);
        initVec3(&fusion->x1, 0.0f, 0.0f, 0.0f);

        fusion->mInitState = 0;

        fusion->mPredictDt = 0.0f;
        fusion->mCount[0] = fusion->mCount[1] = fusion->mCount[2] = 0;

        initVec3(&fusion->mData[0], 0.0f, 0.0f, 0.0f);
        initVec3(&fusion->mData[1], 0.0f, 0.0f, 0.0f);
        initVec3(&fusion->mData[2], 0.0f, 0.0f, 0.0f);

    } else  {
        // mask off disabled sensor bit
        fusion->mInitState &= (ACC
                               | ((fusion->flags & FUSION_USE_MAG) ? MAG : 0)
                               | ((fusion->flags & FUSION_USE_GYRO) ? GYRO : 0));
    }

    fusionSetMagTrust(fusion, NORMAL);
    fusion->lastMagInvalid = false;
}

int fusionHasEstimate(const struct Fusion *fusion) {
    // waive sensor init depends on the mode
    return fusion->mInitState == (ACC
                                  | ((fusion->flags & FUSION_USE_MAG) ? MAG : 0)
                                  | ((fusion->flags & FUSION_USE_GYRO) ? GYRO : 0));
}

static void updateDt(struct Fusion *fusion, float dT) {
    if (fabsf(fusion->mPredictDt - dT) > DELTA_TIME_MARGIN) {
        float dT2 = dT * dT;
        float dT3 = dT2 * dT;

        float q00 = fusion->param.gyro_var * dT +
                    0.33333f * fusion->param.gyro_bias_var * dT3;
        float q11 = fusion->param.gyro_bias_var * dT;
        float q10 = 0.5f * fusion->param.gyro_bias_var * dT2;
        float q01 = q10;

        initDiagonalMatrix(&fusion->GQGt[0][0], q00);
        initDiagonalMatrix(&fusion->GQGt[0][1], -q10);
        initDiagonalMatrix(&fusion->GQGt[1][0], -q01);
        initDiagonalMatrix(&fusion->GQGt[1][1], q11);
        fusion->mPredictDt = dT;
    }
}

static int fusion_init_complete(struct Fusion *fusion, int what, const struct Vec3 *d, float dT) {
    if (fusionHasEstimate(fusion)) {
        return 1;
    }

    switch (what) {
        case ACC:
        {
            if (!(fusion->flags & FUSION_USE_GYRO)) {
                updateDt(fusion, dT);
            }
            struct Vec3 unityD = *d;
            vec3Normalize(&unityD);

            vec3Add(&fusion->mData[0], &unityD);
            ++fusion->mCount[0];

            if (fusion->mCount[0] == 8) {
                fusion->mInitState |= ACC;
            }
            break;
        }

        case MAG:
        {
            struct Vec3 unityD = *d;
            vec3Normalize(&unityD);

            vec3Add(&fusion->mData[1], &unityD);
            ++fusion->mCount[1];

            fusion->mInitState |= MAG;
            break;
        }

        case GYRO:
        {
            updateDt(fusion, dT);

            struct Vec3 scaledD = *d;
            vec3ScalarMul(&scaledD, dT);

            vec3Add(&fusion->mData[2], &scaledD);
            ++fusion->mCount[2];

            fusion->mInitState |= GYRO;
            break;
        }

        default:
            // assert(!"should not be here");
            break;
    }

    if (fusionHasEstimate(fusion)) {
        vec3ScalarMul(&fusion->mData[0], 1.0f / fusion->mCount[0]);

        if (fusion->flags & FUSION_USE_MAG) {
            vec3ScalarMul(&fusion->mData[1], 1.0f / fusion->mCount[1]);
        } else {
            fusion->fake_mag_decimation = 0.f;
        }

        struct Vec3 up = fusion->mData[0];

        struct Vec3 east;
        if (fusion->flags & FUSION_USE_MAG) {
            vec3Cross(&east, &fusion->mData[1], &up);
            vec3Normalize(&east);
        } else {
            findOrthogonalVector(up.x, up.y, up.z, &east.x, &east.y, &east.z);
        }

        struct Vec3 north;
        vec3Cross(&north, &up, &east);

        struct Mat33 R;
        initMatrixColumns(&R, &east, &north, &up);

        //Quat q;
        //initQuat(&q, &R);

        initQuat(&fusion->x0, &R);
        initVec3(&fusion->x1, 0.0f, 0.0f, 0.0f);

        initZeroMatrix(&fusion->P[0][0]);
        initZeroMatrix(&fusion->P[0][1]);
        initZeroMatrix(&fusion->P[1][0]);
        initZeroMatrix(&fusion->P[1][1]);

        fusionSetMagTrust(fusion, INITIALIZATION);
    }

    return 0;
}

static void matrixCross(struct Mat33 *out, struct Vec3 *p, float diag) {
    out->elem[0][0] = diag;
    out->elem[1][1] = diag;
    out->elem[2][2] = diag;
    out->elem[1][0] = p->z;
    out->elem[0][1] = -p->z;
    out->elem[2][0] = -p->y;
    out->elem[0][2] = p->y;
    out->elem[2][1] = p->x;
    out->elem[1][2] = -p->x;
}

static void fusionCheckState(struct Fusion *fusion) {

    if (!mat33IsPositiveSemidefinite(&fusion->P[0][0], SYMMETRY_TOLERANCE)
            || !mat33IsPositiveSemidefinite(
                &fusion->P[1][1], SYMMETRY_TOLERANCE)) {

        initZeroMatrix(&fusion->P[0][0]);
        initZeroMatrix(&fusion->P[0][1]);
        initZeroMatrix(&fusion->P[1][0]);
        initZeroMatrix(&fusion->P[1][1]);
    }
}

#define kEps 1.0E-4f

UNROLLED
static void fusionPredict(struct Fusion *fusion, const struct Vec3 *w) {
    const float dT = fusion->mPredictDt;

    Quat q = fusion->x0;
    struct Vec3 b = fusion->x1;

    struct Vec3 we = *w;
    vec3Sub(&we, &b);

    struct Mat33 I33;
    initDiagonalMatrix(&I33, 1.0f);

    struct Mat33 I33dT;
    initDiagonalMatrix(&I33dT, dT);

    struct Mat33 wx;
    matrixCross(&wx, &we, 0.0f);

    struct Mat33 wx2;
    mat33Multiply(&wx2, &wx, &wx);

    float norm_we = vec3Norm(&we);

    if (fabsf(norm_we) < kEps) {
        return;
    }

    float lwedT = norm_we * dT;
    float hlwedT = 0.5f * lwedT;
    float ilwe = 1.0f / norm_we;
    float k0 = (1.0f - cosf(lwedT)) * (ilwe * ilwe);
    float k1 = sinf(lwedT);
    float k2 = cosf(hlwedT);

    struct Vec3 psi = we;
    vec3ScalarMul(&psi, sinf(hlwedT) * ilwe);

    struct Vec3 negPsi = psi;
    vec3ScalarMul(&negPsi, -1.0f);

    struct Mat33 O33;
    matrixCross(&O33, &negPsi, k2);

    struct Mat44 O;
    uint32_t i;
    for (i = 0; i < 3; ++i) {
        uint32_t j;
        for (j = 0; j < 3; ++j) {
            O.elem[i][j] = O33.elem[i][j];
        }
    }

    O.elem[3][0] = -psi.x;
    O.elem[3][1] = -psi.y;
    O.elem[3][2] = -psi.z;
    O.elem[3][3] = k2;

    O.elem[0][3] = psi.x;
    O.elem[1][3] = psi.y;
    O.elem[2][3] = psi.z;

    struct Mat33 tmp = wx;
    mat33ScalarMul(&tmp, k1 * ilwe);

    fusion->Phi0[0] = I33;
    mat33Sub(&fusion->Phi0[0], &tmp);

    tmp = wx2;
    mat33ScalarMul(&tmp, k0);

    mat33Add(&fusion->Phi0[0], &tmp);

    tmp = wx;
    mat33ScalarMul(&tmp, k0);
    fusion->Phi0[1] = tmp;

    mat33Sub(&fusion->Phi0[1], &I33dT);

    tmp = wx2;
    mat33ScalarMul(&tmp, ilwe * ilwe * ilwe * (lwedT - k1));

    mat33Sub(&fusion->Phi0[1], &tmp);

    mat44Apply(&fusion->x0, &O, &q);

    if (fusion->x0.w < 0.0f) {
        fusion->x0.x = -fusion->x0.x;
        fusion->x0.y = -fusion->x0.y;
        fusion->x0.z = -fusion->x0.z;
        fusion->x0.w = -fusion->x0.w;
    }

    // Pnew = Phi * P

    struct Mat33 Pnew[2][2];
    mat33Multiply(&Pnew[0][0], &fusion->Phi0[0], &fusion->P[0][0]);
    mat33Multiply(&tmp, &fusion->Phi0[1], &fusion->P[1][0]);
    mat33Add(&Pnew[0][0], &tmp);

    mat33Multiply(&Pnew[0][1], &fusion->Phi0[0], &fusion->P[0][1]);
    mat33Multiply(&tmp, &fusion->Phi0[1], &fusion->P[1][1]);
    mat33Add(&Pnew[0][1], &tmp);

    Pnew[1][0] = fusion->P[1][0];
    Pnew[1][1] = fusion->P[1][1];

    // P = Pnew * Phi^T

    mat33MultiplyTransposed2(&fusion->P[0][0], &Pnew[0][0], &fusion->Phi0[0]);
    mat33MultiplyTransposed2(&tmp, &Pnew[0][1], &fusion->Phi0[1]);
    mat33Add(&fusion->P[0][0], &tmp);

    fusion->P[0][1] = Pnew[0][1];

    mat33MultiplyTransposed2(&fusion->P[1][0], &Pnew[1][0], &fusion->Phi0[0]);
    mat33MultiplyTransposed2(&tmp, &Pnew[1][1], &fusion->Phi0[1]);
    mat33Add(&fusion->P[1][0], &tmp);

    fusion->P[1][1] = Pnew[1][1];

    mat33Add(&fusion->P[0][0], &fusion->GQGt[0][0]);
    mat33Add(&fusion->P[0][1], &fusion->GQGt[0][1]);
    mat33Add(&fusion->P[1][0], &fusion->GQGt[1][0]);
    mat33Add(&fusion->P[1][1], &fusion->GQGt[1][1]);

    fusionCheckState(fusion);
}

void fusionHandleGyro(struct Fusion *fusion, const struct Vec3 *w, float dT) {
    if (!fusion_init_complete(fusion, GYRO, w, dT)) {
        return;
    }

    updateDt(fusion, dT);

    fusionPredict(fusion, w);
}

UNROLLED
static void scaleCovariance(struct Mat33 *out, const struct Mat33 *A, const struct Mat33 *P) {
    uint32_t r;
    for (r = 0; r < 3; ++r) {
        uint32_t j;
        for (j = r; j < 3; ++j) {
            float apat = 0.0f;
            uint32_t c;
            for (c = 0; c < 3; ++c) {
                float v = A->elem[c][r] * P->elem[c][c] * 0.5f;
                uint32_t k;
                for (k = c + 1; k < 3; ++k) {
                    v += A->elem[k][r] * P->elem[c][k];
                }

                apat += 2.0f * v * A->elem[c][j];
            }

            out->elem[r][j] = apat;
            out->elem[j][r] = apat;
        }
    }
}

static void getF(struct Vec4 F[3], const struct Vec4 *q) {
    F[0].x = q->w;      F[1].x = -q->z;         F[2].x = q->y;
    F[0].y = q->z;      F[1].y = q->w;          F[2].y = -q->x;
    F[0].z = -q->y;     F[1].z = q->x;          F[2].z = q->w;
    F[0].w = -q->x;     F[1].w = -q->y;         F[2].w = -q->z;
}

static void fusionUpdate(
        struct Fusion *fusion, const struct Vec3 *z, const struct Vec3 *Bi, float sigma) {
    struct Mat33 A;
    quatToMatrix(&A, &fusion->x0);

    struct Vec3 Bb;
    mat33Apply(&Bb, &A, Bi);

    struct Mat33 L;
    matrixCross(&L, &Bb, 0.0f);

    struct Mat33 R;
    initDiagonalMatrix(&R, sigma * sigma);

    struct Mat33 S;
    scaleCovariance(&S, &L, &fusion->P[0][0]);

    mat33Add(&S, &R);

    struct Mat33 Si;
    mat33Invert(&Si, &S);

    struct Mat33 LtSi;
    mat33MultiplyTransposed(&LtSi, &L, &Si);

    struct Mat33 K[2];
    mat33Multiply(&K[0], &fusion->P[0][0], &LtSi);
    mat33MultiplyTransposed(&K[1], &fusion->P[0][1], &LtSi);

    struct Mat33 K0L;
    mat33Multiply(&K0L, &K[0], &L);

    struct Mat33 K1L;
    mat33Multiply(&K1L, &K[1], &L);

    struct Mat33 tmp;
    mat33Multiply(&tmp, &K0L, &fusion->P[0][0]);
    mat33Sub(&fusion->P[0][0], &tmp);

    mat33Multiply(&tmp, &K1L, &fusion->P[0][1]);
    mat33Sub(&fusion->P[1][1], &tmp);

    mat33Multiply(&tmp, &K0L, &fusion->P[0][1]);
    mat33Sub(&fusion->P[0][1], &tmp);

    mat33Transpose(&fusion->P[1][0], &fusion->P[0][1]);

    struct Vec3 e = *z;
    vec3Sub(&e, &Bb);

    struct Vec3 dq;
    mat33Apply(&dq, &K[0], &e);


    struct Vec4 F[3];
    getF(F, &fusion->x0);

    // 4x3 * 3x1 => 4x1

    struct Vec4 q;
    q.x = fusion->x0.x + 0.5f * (F[0].x * dq.x + F[1].x * dq.y + F[2].x * dq.z);
    q.y = fusion->x0.y + 0.5f * (F[0].y * dq.x + F[1].y * dq.y + F[2].y * dq.z);
    q.z = fusion->x0.z + 0.5f * (F[0].z * dq.x + F[1].z * dq.y + F[2].z * dq.z);
    q.w = fusion->x0.w + 0.5f * (F[0].w * dq.x + F[1].w * dq.y + F[2].w * dq.z);

    fusion->x0 = q;
    quatNormalize(&fusion->x0);

    if (fusion->flags & FUSION_USE_MAG) {
        // accumulate gyro bias (causes self spin) only if not
        // game rotation vector
        struct Vec3 db;
        mat33Apply(&db, &K[1], &e);
        vec3Add(&fusion->x1, &db);
    }

    fusionCheckState(fusion);
}

#define ACC_TRUSTWORTHY(abs_norm_err)  ((abs_norm_err) < 1.f)
#define ACC_COS_CONV_FACTOR  0.01f
#define ACC_COS_CONV_LIMIT   3.f

int fusionHandleAcc(struct Fusion *fusion, const struct Vec3 *a, float dT) {
    if (!fusion_init_complete(fusion, ACC, a,  dT)) {
        return -EINVAL;
    }

    float norm2 = vec3NormSquared(a);

    if (norm2 < FREE_FALL_THRESHOLD_SQ) {
        return -EINVAL;
    }

    float l = sqrtf(norm2);
    float l_inv = 1.0f / l;

    if (!(fusion->flags & FUSION_USE_GYRO)) {
        // geo mag mode
        // drive the Kalman filter with zero mean dummy gyro vector
        struct Vec3 w_dummy;

        // avoid (fabsf(norm_we) < kEps) in fusionPredict()
        initVec3(&w_dummy, fusion->x1.x + kEps, fusion->x1.y + kEps,
                 fusion->x1.z + kEps);

        updateDt(fusion, dT);
        fusionPredict(fusion, &w_dummy);
    }

    struct Mat33 R;
    fusionGetRotationMatrix(fusion, &R);

    if (!(fusion->flags & FUSION_USE_MAG) &&
        (fusion->fake_mag_decimation += dT) > FAKE_MAG_INTERVAL) {
        // game rotation mode, provide fake mag update to prevent
        // P to diverge over time
        struct Vec3 m;
        mat33Apply(&m, &R, &fusion->Bm);

        fusionUpdate(fusion, &m, &fusion->Bm,
                      fusion->param.mag_stdev);
        fusion->fake_mag_decimation = 0.f;
    }

    struct Vec3 unityA = *a;
    vec3ScalarMul(&unityA, l_inv);

    float d = fabsf(l - NOMINAL_GRAVITY);
    float p;
    if (fusion->flags & FUSION_USE_GYRO) {
        float fc = 0;
        // Enable faster convergence
        if (ACC_TRUSTWORTHY(d)) {
            struct Vec3 aa;
            mat33Apply(&aa, &R, &fusion->Ba);
            float cos_err = vec3Dot(&aa, &unityA);
            cos_err = cos_err < (1.f - ACC_COS_CONV_FACTOR) ?
                (1.f - ACC_COS_CONV_FACTOR) : cos_err;
            fc = (1.f - cos_err) *
                    (1.0f / ACC_COS_CONV_FACTOR * ACC_COS_CONV_LIMIT);
        }
        p = fusion->param.acc_stdev * expf(3 * d - fc);
    } else {
        // Adaptive acc weighting (trust acc less as it deviates from nominal g
        // more), acc_stdev *= e(sqrt(| |acc| - g_nominal|))
        //
        // The weighting equation comes from heuristics.
        p = fusion->param.acc_stdev * expf(sqrtf(d));
    }

    fusionUpdate(fusion, &unityA, &fusion->Ba, p);

    return 0;
}

#define MAG_COS_CONV_FACTOR   0.02f
#define MAG_COS_CONV_LIMIT    3.5f
#define MAG_STDEV_REDUCTION   0.005f // lower stdev means more trust

int fusionHandleMag(struct Fusion *fusion, const struct Vec3 *m, float dT) {
    if (!fusion_init_complete(fusion, MAG, m, 0.0f /* dT */)) {
        return -EINVAL;
    }

    float magFieldSq = vec3NormSquared(m);

    if (magFieldSq > MAX_VALID_MAGNETIC_FIELD_SQ
            || magFieldSq < MIN_VALID_MAGNETIC_FIELD_SQ) {
        fusionSetMagTrust(fusion, NORMAL);
        fusion->lastMagInvalid = true;
        return -EINVAL;
    }

    struct Mat33 R;
    fusionGetRotationMatrix(fusion, &R);

    struct Vec3 up;
    mat33Apply(&up, &R, &fusion->Ba);

    struct Vec3 east;
    vec3Cross(&east, m, &up);

    if (vec3NormSquared(&east) < MIN_VALID_CROSS_PRODUCT_MAG_SQ) {
        fusionSetMagTrust(fusion, NORMAL);
        fusion->lastMagInvalid = true;
        return -EINVAL;
    }

    if (fusion->lastMagInvalid) {
        fusion->lastMagInvalid = false;
        fusionSetMagTrust(fusion, BACK_TO_VALID);
    }

    struct Vec3 north;
    vec3Cross(&north, &up, &east);

    float invNorm = 1.0f / vec3Norm(&north);
    vec3ScalarMul(&north, invNorm);

    float p = fusion->param.mag_stdev;

    if (fusion->flags & FUSION_USE_GYRO) {
        struct Vec3 mm;
        mat33Apply(&mm, &R, &fusion->Bm);
        float cos_err = vec3Dot(&mm, &north);

        if (fusion->trustedMagDuration > 0) {
            // if the trust mag time period is not finished
            if (cos_err < (1.f - MAG_COS_CONV_FACTOR/4)) {
                // if the mag direction and the fusion north has not converged, lower the
                // standard deviation of mag to speed up convergence.
                p *= MAG_STDEV_REDUCTION;
                fusion->trustedMagDuration -= dT;
            } else {
                // it has converged already, so no need to keep the trust period any longer
                fusionSetMagTrust(fusion, NORMAL);
            }
        } else {
            cos_err = cos_err < (1.f - MAG_COS_CONV_FACTOR) ?
                (1.f - MAG_COS_CONV_FACTOR) : cos_err;

            float fc;
            fc = (1.f - cos_err) * (1.0f / MAG_COS_CONV_FACTOR * MAG_COS_CONV_LIMIT);
            p *= expf(-fc);
        }
    }

    fusionUpdate(fusion, &north, &fusion->Bm, p);

    return 0;
}

void fusionSetMagTrust(struct Fusion *fusion, int mode) {
    switch(mode) {
        case NORMAL:
            fusion->trustedMagDuration = 0; // disable
            break;
        case INITIALIZATION:
        case BACK_TO_VALID:
            fusion->trustedMagDuration = 0; // no special treatment for these two
            break;
        case MANUAL_MAG_CAL:
            fusion->trustedMagDuration = TRUST_DURATION_MANUAL_MAG_CAL;
            break;
        default:
            fusion->trustedMagDuration = 0; // by default it is disable
            break;
    }
}

void fusionGetAttitude(const struct Fusion *fusion, struct Vec4 *attitude) {
    *attitude = fusion->x0;
}

void fusionGetBias(const struct Fusion *fusion, struct Vec3 *bias) {
    *bias = fusion->x1;
}

void fusionGetRotationMatrix(const struct Fusion *fusion, struct Mat33 *R) {
    quatToMatrix(R, &fusion->x0);
}
