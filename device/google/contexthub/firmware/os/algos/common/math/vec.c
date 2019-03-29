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

#include "common/math/vec.h"
#include "common/math/macros.h"

void findOrthogonalVector(float inX, float inY, float inZ, float *outX,
                          float *outY, float *outZ) {
  ASSERT_NOT_NULL(outX);
  ASSERT_NOT_NULL(outY);
  ASSERT_NOT_NULL(outZ);
  float x, y, z;

  // discard the one with the smallest absolute value
  if (fabsf(inX) <= fabsf(inY) && fabsf(inX) <= fabsf(inZ)) {
    x = 0.0f;
    y = inZ;
    z = -inY;
  } else if (fabsf(inY) <= fabsf(inZ)) {
    x = inZ;
    y = 0.0f;
    z = -inX;
  } else {
    x = inY;
    y = -inX;
    z = 0.0f;
  }

  float magSquared = x * x + y * y + z * z;
  ASSERT(magSquared > 0);
  // Only set invMag if magSquared is non-zero.
  float invMag = 1.0f;
  if (magSquared > 0) {
    invMag = 1.0f / sqrtf(magSquared);
  }
  *outX = x * invMag;
  *outY = y * invMag;
  *outZ = z * invMag;
}

void vecAdd(float *u, const float *v, const float *w, size_t dim) {
  ASSERT_NOT_NULL(u);
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  size_t i;
  for (i = 0; i < dim; i++) {
    u[i] = v[i] + w[i];
  }
}

void vecAddInPlace(float *v, const float *w, size_t dim) {
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  size_t i;
  for (i = 0; i < dim; i++) {
    v[i] += w[i];
  }
}

void vecSub(float *u, const float *v, const float *w, size_t dim) {
  ASSERT_NOT_NULL(u);
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  size_t i;
  for (i = 0; i < dim; i++) {
    u[i] = v[i] - w[i];
  }
}

void vecScalarMul(float *u, const float *v, float c, size_t dim) {
  ASSERT_NOT_NULL(u);
  ASSERT_NOT_NULL(v);
  size_t i;
  for (i = 0; i < dim; i++) {
    u[i] = c * v[i];
  }
}

void vecScalarMulInPlace(float *v, float c, size_t dim) {
  ASSERT_NOT_NULL(v);
  size_t i;
  for (i = 0; i < dim; i++) {
    v[i] *= c;
  }
}

float vecNorm(const float *v, size_t dim) {
  ASSERT_NOT_NULL(v);
  float norm_sq = vecNormSquared(v, dim);
  return sqrtf(norm_sq);
}

float vecNormSquared(const float *v, size_t dim) {
  ASSERT_NOT_NULL(v);
  return vecDot(v, v, dim);
}

float vecDot(const float *v, const float *w, size_t dim) {
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  size_t i;
  float result = 0;
  for (i = 0; i < dim; ++i) {
    result += v[i] * w[i];
  }
  return result;
}

float vecMaxAbsoluteValue(const float *v, size_t dim) {
  ASSERT_NOT_NULL(v);
  float max = NANO_ABS(v[0]);
  float tmp;
  size_t i;
  for (i = 1; i < dim; ++i) {
    tmp = NANO_ABS(v[i]);
    if(tmp > max) {
      max = tmp;
    }
  }
  return max;
}
