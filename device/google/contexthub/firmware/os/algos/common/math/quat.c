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

#include "common/math/quat.h"

static float clamp(float x) { return x < 0.0f ? 0.0f : x; }

void initQuat(Quat *q, const struct Mat33 *R) {
  float Hx = R->elem[0][0];
  float My = R->elem[1][1];
  float Az = R->elem[2][2];

  q->x = sqrtf(clamp(Hx - My - Az + 1.0f) * 0.25f);
  q->y = sqrtf(clamp(-Hx + My - Az + 1.0f) * 0.25f);
  q->z = sqrtf(clamp(-Hx - My + Az + 1.0f) * 0.25f);
  q->w = sqrtf(clamp(Hx + My + Az + 1.0f) * 0.25f);
  q->x = copysignf(q->x, R->elem[1][2] - R->elem[2][1]);
  q->y = copysignf(q->y, R->elem[2][0] - R->elem[0][2]);
  q->z = copysignf(q->z, R->elem[0][1] - R->elem[1][0]);
}

void quatToMatrix(struct Mat33 *R, const Quat *q) {
  float q0 = q->w;
  float q1 = q->x;
  float q2 = q->y;
  float q3 = q->z;
  float sq_q1 = 2.0f * q1 * q1;
  float sq_q2 = 2.0f * q2 * q2;
  float sq_q3 = 2.0f * q3 * q3;
  float q1_q2 = 2.0f * q1 * q2;
  float q3_q0 = 2.0f * q3 * q0;
  float q1_q3 = 2.0f * q1 * q3;
  float q2_q0 = 2.0f * q2 * q0;
  float q2_q3 = 2.0f * q2 * q3;
  float q1_q0 = 2.0f * q1 * q0;

  R->elem[0][0] = 1.0f - sq_q2 - sq_q3;
  R->elem[1][0] = q1_q2 - q3_q0;
  R->elem[2][0] = q1_q3 + q2_q0;
  R->elem[0][1] = q1_q2 + q3_q0;
  R->elem[1][1] = 1.0f - sq_q1 - sq_q3;
  R->elem[2][1] = q2_q3 - q1_q0;
  R->elem[0][2] = q1_q3 - q2_q0;
  R->elem[1][2] = q2_q3 + q1_q0;
  R->elem[2][2] = 1.0f - sq_q1 - sq_q2;
}

void quatNormalize(Quat *q) {
  if (q->w < 0.0f) {
    q->x = -q->x;
    q->y = -q->y;
    q->z = -q->z;
    q->w = -q->w;
  }

  float invNorm =
      1.0f / sqrtf(q->x * q->x + q->y * q->y + q->z * q->z + q->w * q->w);

  q->x *= invNorm;
  q->y *= invNorm;
  q->z *= invNorm;
  q->w *= invNorm;
}
