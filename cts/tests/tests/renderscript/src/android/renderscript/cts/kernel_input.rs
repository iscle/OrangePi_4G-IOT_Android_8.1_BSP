/*
 * Copyright (C) 2015 The Android Open Source Project
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


#pragma version(1)
#pragma rs java_package_name(foo)

#include "shared.rsh"

static bool failed = false;

/*
 * This checks that modifications to input arguments done by a kernel
 * are never reflected back to the input Allocation. In order to do
 * this, we create kernels that modify their input arguments (the
 * clear_input_* kernels).
 *
 * When the kernels modify their input arguments, these modifications
 * should not be visible in the underlying Allocation. The
 * verify_input_* functions can be passed the same Allocation that was
 * used as an in input to a clear_input_* kernel. The verify_input_*
 * functions check their input against a global variable.
 */

// The clear_input_* kernels have rsDebug calls so that the writes they make to
// their parameters don't get optimized away.  To avoid logspam from the
// rsDebug, guard calls to it with a runtime test that is guaranteed to be
// false.
volatile int gDummy = 0;

// For clear_input_* kernels, we use a volatile qualified input argument
// to try to inhibit any optimizations that would result in the write to
// the input argument being optimized out by the compiler.

#define COMMON_TEST_CODE(type)                           \
  type initial_value_##type;                             \
  type RS_KERNEL clear_input_##type(volatile type in) {  \
    if (gDummy == 500) {                                 \
      rsDebug(#type, in);                                \
    }                                                    \
    in -= in;                                            \
    if (gDummy == 500) {                                 \
      rsDebug(#type, in);                                \
    }                                                    \
    return in;                                           \
  }

#define SCALAR_TEST(type)                         \
  COMMON_TEST_CODE(type)                          \
  void verify_input_##type(rs_allocation alloc) { \
    type elem = rsGetElementAt_##type(alloc, 0);  \
    _RS_ASSERT(elem == initial_value_##type);     \
  }

#define VEC2_TEST(type)                             \
  COMMON_TEST_CODE(type)                            \
  void verify_input_##type(rs_allocation alloc) {   \
    type elem = rsGetElementAt_##type(alloc, 0);    \
    _RS_ASSERT(elem[0] == initial_value_##type[0]); \
    _RS_ASSERT(elem[1] == initial_value_##type[1]); \
  }

#define VEC3_TEST(type)                             \
  COMMON_TEST_CODE(type)                            \
  void verify_input_##type(rs_allocation alloc) {   \
    type elem = rsGetElementAt_##type(alloc, 0);    \
    _RS_ASSERT(elem[0] == initial_value_##type[0]); \
    _RS_ASSERT(elem[1] == initial_value_##type[1]); \
    _RS_ASSERT(elem[2] == initial_value_##type[2]); \
  }

#define VEC4_TEST(type)                             \
  COMMON_TEST_CODE(type)                            \
  void verify_input_##type(rs_allocation alloc) {   \
    type elem = rsGetElementAt_##type(alloc, 0);    \
    _RS_ASSERT(elem[0] == initial_value_##type[0]); \
    _RS_ASSERT(elem[1] == initial_value_##type[1]); \
    _RS_ASSERT(elem[2] == initial_value_##type[2]); \
    _RS_ASSERT(elem[3] == initial_value_##type[3]); \
  }

SCALAR_TEST(char)

VEC2_TEST(char2)

VEC3_TEST(char3)

VEC4_TEST(char4)

SCALAR_TEST(double)

VEC2_TEST(double2)

VEC3_TEST(double3)

VEC4_TEST(double4)

SCALAR_TEST(float)

VEC2_TEST(float2)

VEC3_TEST(float3)

VEC4_TEST(float4)

SCALAR_TEST(int)

VEC2_TEST(int2)

VEC3_TEST(int3)

VEC4_TEST(int4)

SCALAR_TEST(long)

VEC2_TEST(long2)

VEC3_TEST(long3)

VEC4_TEST(long4)

SCALAR_TEST(short)

VEC2_TEST(short2)

VEC3_TEST(short3)

VEC4_TEST(short4)

SCALAR_TEST(uchar)

VEC2_TEST(uchar2)

VEC3_TEST(uchar3)

VEC4_TEST(uchar4)

SCALAR_TEST(uint)

VEC2_TEST(uint2)

VEC3_TEST(uint3)

VEC4_TEST(uint4)

SCALAR_TEST(ulong)

VEC2_TEST(ulong2)

VEC3_TEST(ulong3)

VEC4_TEST(ulong4)

SCALAR_TEST(ushort)

VEC2_TEST(ushort2)

VEC3_TEST(ushort3)

VEC4_TEST(ushort4)

typedef struct small {
  int x[1];
} small;

typedef struct big {
  int x[100];
} big;

small initial_value_small;

// See comment on volatile above.
small RS_KERNEL clear_input_small(volatile small in) {
  if (gDummy == 500) {
    rsDebug("in.x", in.x[0]);
  }
  in.x[0] = 0;
  if (gDummy == 500) {
    rsDebug("in.x", in.x[0]);
  }
  return in;
}

void verify_input_small(rs_allocation alloc) {
  const small *elem = (const small *) rsGetElementAt(alloc, 0);
  _RS_ASSERT(elem->x[0] == initial_value_small.x[0]);
}

big initial_value_big;

// See comment on volatile above.
big RS_KERNEL clear_input_big(volatile big in) {
  for (size_t i = 0; i < 100; ++i) {
    if (gDummy == 500) {
      rsDebug("in.x", in.x[i]);
    }
    in.x[i] = 0;
    if (gDummy == 500) {
      rsDebug("in.x", in.x[i]);
    }
  }
  return in;
}

void verify_input_big(rs_allocation alloc) {
  const big *elem = (const big *) rsGetElementAt(alloc, 0);
  for (size_t i = 0; i < 100; ++i) {
    _RS_ASSERT(elem->x[i] == initial_value_big.x[i]);
  }
}

void checkError() {
  if (failed) {
    rsSendToClientBlocking(RS_MSG_TEST_FAILED);
  } else {
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
  }
}
