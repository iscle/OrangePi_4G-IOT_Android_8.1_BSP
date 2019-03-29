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

/////////////////////////////////////////////////////////////////////////
/*
 * This module contains vector math utilities for the following datatypes:
 * -) Vec3 structures for 3-dimensional vectors
 * -) Vec4 structures for 4-dimensional vectors
 * -) floating point arrays for N-dimensional vectors.
 *
 * Note that the Vec3 and Vec4 utilties were ported from the Android
 * repository and maintain dependenices in that separate codebase. As a
 * result, the function signatures were left untouched for compatibility with
 * this legacy code, despite certain style violations. In particular, for this
 * module the function argument ordering is outputs before inputs. This style
 * violation will be addressed once the full set of dependencies in Android
 * have been brought into this repository.
 */
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_VEC_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_VEC_H_

#ifdef NANOHUB_NON_CHRE_API
#include <nanohub_math.h>
#else
#include <math.h>
#endif  // NANOHUB_NON_CHRE_API

#include <stddef.h>
#include "util/nano_assert.h"

#ifdef __cplusplus
extern "C" {
#endif

struct Vec3 {
  float x, y, z;
};

struct Vec4 {
  float x, y, z, w;
};

// 3-DIMENSIONAL VECTOR MATH ///////////////////////////////////////////
static inline void initVec3(struct Vec3 *v, float x, float y, float z) {
  ASSERT_NOT_NULL(v);
  v->x = x;
  v->y = y;
  v->z = z;
}

// Updates v as the sum of v and w.
static inline void vec3Add(struct Vec3 *v, const struct Vec3 *w) {
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  v->x += w->x;
  v->y += w->y;
  v->z += w->z;
}

// Updates v as the subtraction of w from v.
static inline void vec3Sub(struct Vec3 *v, const struct Vec3 *w) {
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  v->x -= w->x;
  v->y -= w->y;
  v->z -= w->z;
}

// Scales v by the scalar c, i.e. v = c * v.
static inline void vec3ScalarMul(struct Vec3 *v, float c) {
  ASSERT_NOT_NULL(v);
  v->x *= c;
  v->y *= c;
  v->z *= c;
}

// Returns the dot product of v and w.
static inline float vec3Dot(const struct Vec3 *v, const struct Vec3 *w) {
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  return v->x * w->x + v->y * w->y + v->z * w->z;
}

// Returns the square of the L2-norm of the given vector.
static inline float vec3NormSquared(const struct Vec3 *v) {
  ASSERT_NOT_NULL(v);
  return vec3Dot(v, v);
}

// Returns the L2-norm of the given vector.
static inline float vec3Norm(const struct Vec3 *v) {
  ASSERT_NOT_NULL(v);
  return sqrtf(vec3NormSquared(v));
}

// Normalizes the provided vector to unit norm. If the provided vector has a
// norm of zero, the vector will be unchanged.
static inline void vec3Normalize(struct Vec3 *v) {
  ASSERT_NOT_NULL(v);
  float norm = vec3Norm(v);
  ASSERT(norm > 0);
  // Only normalize if norm is non-zero.
  if (norm > 0) {
    float invNorm = 1.0f / norm;
    v->x *= invNorm;
    v->y *= invNorm;
    v->z *= invNorm;
  }
}

// Updates u as the cross product of v and w.
static inline void vec3Cross(struct Vec3 *u, const struct Vec3 *v,
                             const struct Vec3 *w) {
  ASSERT_NOT_NULL(u);
  ASSERT_NOT_NULL(v);
  ASSERT_NOT_NULL(w);
  u->x = v->y * w->z - v->z * w->y;
  u->y = v->z * w->x - v->x * w->z;
  u->z = v->x * w->y - v->y * w->x;
}

// Finds a vector orthogonal to the vector [inX, inY, inZ] and returns
// this in the components [outX, outY, outZ].  The vector is chosen such
// that the smallest component of [inX, inY, inZ] is set to zero in the
// output vector. For example, for the in vector [0.01, 4.0, 5.0], this
// function will return [0, 5.0, -4.0].
void findOrthogonalVector(float inX, float inY, float inZ, float *outX,
                          float *outY, float *outZ);


// 4-DIMENSIONAL VECTOR MATH ///////////////////////////////////////////
// Initialize the Vec4 structure with the provided component values.
static inline void initVec4(struct Vec4 *v, float x, float y, float z,
                            float w) {
  ASSERT_NOT_NULL(v);
  v->x = x;
  v->y = y;
  v->z = z;
  v->w = w;
}

// N-DIMENSIONAL VECTOR MATH ///////////////////////////////////////////
// Dimension specified by the last argument in all functions below.

// Adds two vectors and returns the sum in the provided vector, i.e.
// u = v + w.
void vecAdd(float *u, const float *v, const float *w, size_t dim);

// Adds two vectors and returns the sum in the first vector, i.e.
// v = v + w.
void vecAddInPlace(float *v, const float *w, size_t dim);

// Subtracts two vectors and returns in the provided vector, i.e.
// u = v - w.
void vecSub(float *u, const float *v, const float *w, size_t dim);

// Scales vector by a scalar and returns in the provided vector, i.e.
// u = c * v.
void vecScalarMul(float *u, const float *v, float c, size_t dim);

// Scales vector by a scalar and returns in the same vector, i.e.
// v = c * v.
void vecScalarMulInPlace(float *v, float c, size_t dim);

// Returns the L2-norm of the given vector.
float vecNorm(const float *v, size_t dim);

// Returns the square of the L2-norm of the given vector.
float vecNormSquared(const float *v, size_t dim);

// Returns the dot product of v and w.
float vecDot(const float *v, const float *w, size_t dim);

// Returns the maximum absolute value in vector.
float vecMaxAbsoluteValue(const float *v, size_t dim);

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_COMMON_MATH_VEC_H_
