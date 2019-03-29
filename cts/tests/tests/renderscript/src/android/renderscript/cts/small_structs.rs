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
#pragma rs java_package_name(android.renderscript.cts)

#include "shared.rsh"

// The intention of this test is to ensure that small structs are
// passed / returned correctly by forEach kernels. "small" here means
// under 64 bytes in size.
//
// The structure of this test is as follows:
//   - allocations are initialized in Java
//   - modify_* kernels change the allocation of small structs
//   - verify_* invokables check that the modification changed the data
//     as expected
//
// If there was an issue with passing / returning the small structs,
// then the verify_* invokable should detect that a value is not
// stored correctly.
//
// The first set of tests, char_array_*, test structs that contain
// char arrays of various sizes. The second set of tests,
// two_element_struct_*, test structs that contain two different types
// (so that non-trivial alignment rules apply).

/******************************************************************************/
/* Common code                                                                */
/******************************************************************************/

static bool failed = false;

void checkError() {
  if (failed) {
    rsSendToClientBlocking(RS_MSG_TEST_FAILED);
  } else {
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
  }
}

/******************************************************************************/
/* Char array testing                                                         */
/******************************************************************************/

static int numCharArrayTestsRun;

#define TOTAL_CHAR_ARRAY_TESTS 64

void checkNumberOfCharArrayTestsRun() {
  rsDebug("number of char array tests run", numCharArrayTestsRun);
  if (numCharArrayTestsRun != TOTAL_CHAR_ARRAY_TESTS) {
    rsSendToClientBlocking(RS_MSG_TEST_FAILED);
  } else {
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
  }
}

#define CHAR_ARRAY_TEST(SIZE)                                \
typedef struct char_array_##SIZE { char bytes[SIZE]; }       \
  char_array_##SIZE;                                         \
                                                             \
char_array_##SIZE RS_KERNEL                                  \
modify_char_array_##SIZE(char_array_##SIZE in) {             \
  for (int i = 0; i < SIZE; ++i) {                           \
    in.bytes[i] += i;                                        \
  }                                                          \
  return in;                                                 \
}                                                            \
                                                             \
void verify_char_array_##SIZE(rs_allocation alloc) {         \
  for (int i = 0; i < rsAllocationGetDimX(alloc); ++i) {     \
    struct char_array_##SIZE *elem =                         \
      (struct char_array_##SIZE *) rsGetElementAt(alloc, i); \
    for (int j = 0; j < SIZE; ++j) {                         \
      _RS_ASSERT(elem->bytes[j] == 2 * j + 1);               \
    }                                                        \
  }                                                          \
  ++numCharArrayTestsRun;                                    \
}

CHAR_ARRAY_TEST(1)
CHAR_ARRAY_TEST(2)
CHAR_ARRAY_TEST(3)
CHAR_ARRAY_TEST(4)
CHAR_ARRAY_TEST(5)
CHAR_ARRAY_TEST(6)
CHAR_ARRAY_TEST(7)
CHAR_ARRAY_TEST(8)
CHAR_ARRAY_TEST(9)
CHAR_ARRAY_TEST(10)
CHAR_ARRAY_TEST(11)
CHAR_ARRAY_TEST(12)
CHAR_ARRAY_TEST(13)
CHAR_ARRAY_TEST(14)
CHAR_ARRAY_TEST(15)
CHAR_ARRAY_TEST(16)
CHAR_ARRAY_TEST(17)
CHAR_ARRAY_TEST(18)
CHAR_ARRAY_TEST(19)
CHAR_ARRAY_TEST(20)
CHAR_ARRAY_TEST(21)
CHAR_ARRAY_TEST(22)
CHAR_ARRAY_TEST(23)
CHAR_ARRAY_TEST(24)
CHAR_ARRAY_TEST(25)
CHAR_ARRAY_TEST(26)
CHAR_ARRAY_TEST(27)
CHAR_ARRAY_TEST(28)
CHAR_ARRAY_TEST(29)
CHAR_ARRAY_TEST(30)
CHAR_ARRAY_TEST(31)
CHAR_ARRAY_TEST(32)
CHAR_ARRAY_TEST(33)
CHAR_ARRAY_TEST(34)
CHAR_ARRAY_TEST(35)
CHAR_ARRAY_TEST(36)
CHAR_ARRAY_TEST(37)
CHAR_ARRAY_TEST(38)
CHAR_ARRAY_TEST(39)
CHAR_ARRAY_TEST(40)
CHAR_ARRAY_TEST(41)
CHAR_ARRAY_TEST(42)
CHAR_ARRAY_TEST(43)
CHAR_ARRAY_TEST(44)
CHAR_ARRAY_TEST(45)
CHAR_ARRAY_TEST(46)
CHAR_ARRAY_TEST(47)
CHAR_ARRAY_TEST(48)
CHAR_ARRAY_TEST(49)
CHAR_ARRAY_TEST(50)
CHAR_ARRAY_TEST(51)
CHAR_ARRAY_TEST(52)
CHAR_ARRAY_TEST(53)
CHAR_ARRAY_TEST(54)
CHAR_ARRAY_TEST(55)
CHAR_ARRAY_TEST(56)
CHAR_ARRAY_TEST(57)
CHAR_ARRAY_TEST(58)
CHAR_ARRAY_TEST(59)
CHAR_ARRAY_TEST(60)
CHAR_ARRAY_TEST(61)
CHAR_ARRAY_TEST(62)
CHAR_ARRAY_TEST(63)
CHAR_ARRAY_TEST(64)

/******************************************************************************/
/* Two element struct testing                                                 */
/******************************************************************************/

static int numTwoElementStructTestsRun;

#define TOTAL_TWO_ELEMENT_STRUCT_TESTS 49

void checkNumberOfTwoElementStructTestsRun() {
  rsDebug("number of two element struct tests run", numTwoElementStructTestsRun);
  if (numTwoElementStructTestsRun != TOTAL_TWO_ELEMENT_STRUCT_TESTS) {
    rsSendToClientBlocking(RS_MSG_TEST_FAILED);
  } else {
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
  }
}

#define TWO_ELEMENT_STRUCT_TEST(TAG, TYPE1, TYPE2)                              \
typedef struct two_element_struct_##TAG { TYPE1 a; TYPE2 b; }                   \
  two_element_struct_##TAG;                                                     \
                                                                                \
two_element_struct_##TAG RS_KERNEL                                              \
modify_two_element_struct_##TAG(two_element_struct_##TAG in, int32_t x) {       \
  in.a += initial_value_##TYPE1 * (x % 3);                                      \
  in.b += initial_value_##TYPE2 * (x % 4);                                      \
  return in;                                                                    \
}                                                                               \
                                                                                \
void verify_two_element_struct_##TAG(rs_allocation alloc) {                     \
  for (int i = 0; i < rsAllocationGetDimX(alloc); ++i) {                        \
    struct two_element_struct_##TAG *elem =                                     \
      (struct two_element_struct_##TAG *) rsGetElementAt(alloc, i);             \
    _RS_ASSERT(equals_##TYPE1(elem->a, (1 + (i % 3)) * initial_value_##TYPE1)); \
    _RS_ASSERT(equals_##TYPE2(elem->b, (1 + (i % 4)) * initial_value_##TYPE2)); \
  }                                                                             \
  ++numTwoElementStructTestsRun;                                                \
}

int8_t  initial_value_int8_t;
int16_t initial_value_int16_t;
int32_t initial_value_int32_t;
int64_t initial_value_int64_t;
float   initial_value_float;
double  initial_value_double;
float4  initial_value_float4;

#define MAKE_SCALAR_EQUALS(TYPE) \
static bool equals_##TYPE(TYPE a, TYPE b) { return a == b; }

MAKE_SCALAR_EQUALS(int8_t)
MAKE_SCALAR_EQUALS(int16_t)
MAKE_SCALAR_EQUALS(int32_t)
MAKE_SCALAR_EQUALS(int64_t)
MAKE_SCALAR_EQUALS(float)
MAKE_SCALAR_EQUALS(double)

static bool equals_float4(float4 a, float4 b) {
     return a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
}

#define MAKE_TWO_ELEMENT_STRUCT_TEST(LHS_TAG, LHS_TYPE)    \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_i8,   LHS_TYPE, int8_t)  \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_i16,  LHS_TYPE, int16_t) \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_i32,  LHS_TYPE, int32_t) \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_i64,  LHS_TYPE, int64_t) \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_f32,  LHS_TYPE, float)   \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_f64,  LHS_TYPE, double)  \
TWO_ELEMENT_STRUCT_TEST(LHS_TAG##_v128, LHS_TYPE, float4)

MAKE_TWO_ELEMENT_STRUCT_TEST(i8,   int8_t)
MAKE_TWO_ELEMENT_STRUCT_TEST(i16,  int16_t)
MAKE_TWO_ELEMENT_STRUCT_TEST(i32,  int32_t)
MAKE_TWO_ELEMENT_STRUCT_TEST(i64,  int64_t)
MAKE_TWO_ELEMENT_STRUCT_TEST(f32,  float)
MAKE_TWO_ELEMENT_STRUCT_TEST(f64,  double)
MAKE_TWO_ELEMENT_STRUCT_TEST(v128, float4)
