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

#include "shared.rsh"

float negInf, posInf;

static half negInfHalf, posInfHalf;

// At present, no support for global of type half, or for invokable
// taking an argument of type half.
static void translate(half *tgt, const short src) {
  for (int i = 0; i < sizeof(half); ++i)
    ((char *)tgt)[i] = ((const char *)&src)[i];
}
void setInfsHalf(short forNegInfHalf, short forPosInfHalf) {
  translate(&negInfHalf, forNegInfHalf);
  translate(&posInfHalf, forPosInfHalf);
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(addint) \
  accumulator(aiAccum)

static void aiAccum(int *accum, int val) { *accum += val; }

/////////////////////////////////////////////////////////////////////////

// Finds LOCATION of min and max float values

#pragma rs reduce(findMinAndMax) \
  initializer(fMMInit) accumulator(fMMAccumulator) \
  combiner(fMMCombiner) outconverter(fMMOutConverter)

typedef struct {
  float val;
  int idx;
} IndexedVal;

typedef struct {
  IndexedVal min, max;
} MinAndMax;

static void fMMInit(MinAndMax *accum) {
  accum->min.val = posInf;
  accum->min.idx = -1;
  accum->max.val = negInf;
  accum->max.idx = -1;
}

static void fMMAccumulator(MinAndMax *accum, float in, int x) {
  IndexedVal me;
  me.val = in;
  me.idx = x;

  if (me.val <= accum->min.val)
    accum->min = me;
  if (me.val >= accum->max.val)
    accum->max = me;
}

static void fMMCombiner(MinAndMax *accum,
                        const MinAndMax *val) {
  if ((accum->min.idx < 0) || (val->min.val < accum->min.val))
    accum->min = val->min;
  if ((accum->max.idx < 0) || (val->max.val > accum->max.val))
    accum->max = val->max;
}

static void fMMOutConverter(int2 *result,
                            const MinAndMax *val) {
  result->x = val->min.idx;
  result->y = val->max.idx;
}

/////////////////////////////////////////////////////////////////////////

// finds min and max half values (not their locations)

// tests half input and half2 result

// .. reduction form

#pragma rs reduce(findMinAndMaxHalf) \
  initializer(fMMHalfInit) accumulator(fMMHalfAccumulator) \
  combiner(fMMHalfCombiner) outconverter(fMMHalfOutConverter)

typedef struct {
  half min, max;
} MinAndMaxHalf;

static void fMMHalfInit(MinAndMaxHalf *accum) {
  accum->min = posInfHalf;
  accum->max = negInfHalf;
}

static void fMMHalfAccumulator(MinAndMaxHalf *accum, half in) {
  accum->min = fmin(accum->min, in);
  accum->max = fmax(accum->max, in);
}

static void fMMHalfCombiner(MinAndMaxHalf *accum,
                            const MinAndMaxHalf *val) {
  accum->min = fmin(accum->min, val->min);
  accum->max = fmax(accum->max, val->max);
}

static void fMMHalfOutConverter(half2 *result,
                                const MinAndMaxHalf *val) {
  result->x = val->min;
  result->y = val->max;
}

// .. invokable (non reduction) form (no support for half computations in Java)

void findMinAndMaxHalf(rs_allocation out, rs_allocation in) {
  half min = posInfHalf, max = negInfHalf;

  const uint32_t len = rsAllocationGetDimX(in);
  for (uint32_t idx = 0; idx < len; ++idx) {
    const half val = rsGetElementAt_half(in, idx);
    min = fmin(min, val);
    max = fmax(max, val);
  }

  half2 result;
  result.x = min;
  result.y = max;
  rsSetElementAt_half2(out, result, 0);
}

// tests half input and array of half result;
//   reuses functions of findMinAndMaxHalf reduction kernel

#pragma rs reduce(findMinAndMaxHalfIntoArray) \
  initializer(fMMHalfInit) accumulator(fMMHalfAccumulator) \
  combiner(fMMHalfCombiner) outconverter(fMMHalfOutConverterIntoArray)

static void fMMHalfOutConverterIntoArray(half (*result)[2],
                                         const MinAndMaxHalf *val) {
  (*result)[0] = val->min;
  (*result)[1] = val->max;
}

/////////////////////////////////////////////////////////////////////////

// finds min and max half2 values (not their locations), element-wise:
//   result[0].x = fmin(input[...].x)
//   result[0].y = fmin(input[...].y)
//   result[1].x = fmax(input[...].x)
//   result[1].y = fmax(input[...].y)

// tests half2 input and half2[] result

// .. reduction form

#pragma rs reduce(findMinAndMaxHalf2) \
  initializer(fMMHalf2Init) accumulator(fMMHalf2Accumulator) \
  combiner(fMMHalf2Combiner) outconverter(fMMHalf2OutConverter)

typedef struct {
  half2 min, max;
} MinAndMaxHalf2;

static void fMMHalf2Init(MinAndMaxHalf2 *accum) {
  accum->min.x = posInfHalf;
  accum->min.y = posInfHalf;
  accum->max.x = negInfHalf;
  accum->max.y = negInfHalf;
}

static void fMMHalf2Accumulator(MinAndMaxHalf2 *accum, half2 in) {
  accum->min.x = fmin(accum->min.x, in.x);
  accum->min.y = fmin(accum->min.y, in.y);
  accum->max.x = fmax(accum->max.x, in.x);
  accum->max.y = fmax(accum->max.y, in.y);
}

static void fMMHalf2Combiner(MinAndMaxHalf2 *accum,
                            const MinAndMaxHalf2 *val) {
  accum->min.x = fmin(accum->min.x, val->min.x);
  accum->min.y = fmin(accum->min.y, val->min.y);
  accum->max.x = fmax(accum->max.x, val->max.x);
  accum->max.y = fmax(accum->max.y, val->max.y);
}

typedef half2 ArrayOf2Half2[2];

static void fMMHalf2OutConverter(ArrayOf2Half2 *result,
                                const MinAndMaxHalf2 *val) {
  (*result)[0] = val->min;
  (*result)[1] = val->max;
}

// .. invokable (non reduction) form (no support for half computations in Java)

void findMinAndMaxHalf2(rs_allocation out, rs_allocation in) {
  half2 min = { posInfHalf, posInfHalf }, max = { negInfHalf, negInfHalf };

  const uint32_t len = rsAllocationGetDimX(in);
  for (uint32_t idx = 0; idx < len; ++idx) {
    const half2 val = rsGetElementAt_half2(in, idx);
    min.x = fmin(min.x, val.x);
    min.y = fmin(min.y, val.y);
    max.x = fmax(max.x, val.x);
    max.y = fmax(max.y, val.y);
  }

  rsSetElementAt_half2(out, min, 0);
  rsSetElementAt_half2(out, max, 1);
}

/////////////////////////////////////////////////////////////////////////

// finds min values (not their locations) from matrix input

// tests matrix input and matrix accumulator

#pragma rs reduce(findMinMat) \
  initializer(fMinMatInit) accumulator(fMinMatAccumulator) \
  outconverter(fMinMatOutConverter)

static void fMinMatInit(rs_matrix2x2 *accum) {
  for (int i = 0; i < 2; ++i)
    for (int j = 0; j < 2; ++j)
      rsMatrixSet(accum, i, j, posInf);
}

static void fMinMatAccumulator(rs_matrix2x2 *accum, rs_matrix2x2 val) {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 2; ++j) {
      const float accumElt = rsMatrixGet(accum, i, j);
      const float valElt = rsMatrixGet(&val, i, j);
      if (valElt < accumElt)
        rsMatrixSet(accum, i, j, valElt);
    }
  }
}

// reduction does not support matrix result, so use array instead
static void fMinMatOutConverter(float (*result)[4],  const rs_matrix2x2 *accum) {
  for (int i = 0; i < 4; ++i)
    (*result)[i] = accum->m[i];
}

/////////////////////////////////////////////////////////////////////////

// finds min and max values (not their locations) from matrix input

// tests matrix input and array of matrix accumulator (0 = min, 1 = max)

#pragma rs reduce(findMinAndMaxMat) \
  initializer(fMinMaxMatInit) accumulator(fMinMaxMatAccumulator) \
  combiner(fMinMaxMatCombiner) outconverter(fMinMaxMatOutConverter)

typedef rs_matrix2x2 MatrixPair[2];
enum MatrixPairEntry { MPE_Min = 0, MPE_Max = 1 };  // indices into MatrixPair

static void fMinMaxMatInit(MatrixPair *accum) {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 2; ++j) {
      rsMatrixSet(&(*accum)[MPE_Min], i, j, posInf);
      rsMatrixSet(&(*accum)[MPE_Max], i, j, negInf);
    }
  }
}

static void fMinMaxMatAccumulator(MatrixPair *accum, rs_matrix2x2 val) {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 2; ++j) {
      const float valElt = rsMatrixGet(&val, i, j);

      const float minElt = rsMatrixGet(&(*accum)[MPE_Min], i, j);
      rsMatrixSet(&(*accum)[MPE_Min], i, j, fmin(minElt, valElt));

      const float maxElt = rsMatrixGet(&(*accum)[MPE_Max], i, j);
      rsMatrixSet(&(*accum)[MPE_Max], i, j, fmax(maxElt, valElt));
    }
  }
}

static void fMinMaxMatCombiner(MatrixPair *accum, const MatrixPair *other) {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 2; ++j) {
      const float minElt = rsMatrixGet(&(*accum)[MPE_Min], i, j);
      const float minEltOther = rsMatrixGet(&(*other)[MPE_Min], i, j);
      rsMatrixSet(&(*accum)[MPE_Min], i, j, fmin(minElt, minEltOther));

      const float maxElt = rsMatrixGet(&(*accum)[MPE_Max], i, j);
      const float maxEltOther = rsMatrixGet(&(*other)[MPE_Max], i, j);
      rsMatrixSet(&(*accum)[MPE_Max], i, j, fmax(maxElt, maxEltOther));
    }
  }
}

// reduction does not support matrix result, so use array instead
static void fMinMaxMatOutConverter(float (*result)[8],  const MatrixPair *accum) {
  for (int i = 0; i < 4; ++i) {
    (*result)[i+0] = (*accum)[MPE_Min].m[i];
    (*result)[i+4] = (*accum)[MPE_Max].m[i];
  }
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz) \
  initializer(fzInit) \
  accumulator(fzAccum) combiner(fzCombine)

static void fzInit(int *accumIdx) { *accumIdx = -1; }

static void fzAccum(int *accumIdx,
                    int inVal, int x /* special arg */) {
  if (inVal==0) *accumIdx = x;
}

static void fzCombine(int *accumIdx, const int *accumIdx2) {
  if (*accumIdx2 >= 0) *accumIdx = *accumIdx2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz2) \
  initializer(fz2Init) \
  accumulator(fz2Accum) combiner(fz2Combine)

static void fz2Init(int2 *accum) { accum->x = accum->y = -1; }

static void fz2Accum(int2 *accum,
                     int inVal,
                     int x /* special arg */,
                     int y /* special arg */) {
  if (inVal==0) {
    accum->x = x;
    accum->y = y;
  }
}

static void fz2Combine(int2 *accum, const int2 *accum2) {
  if (accum2->x >= 0) *accum = *accum2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz3) \
  initializer(fz3Init) \
  accumulator(fz3Accum) combiner(fz3Combine)

static void fz3Init(int3 *accum) { accum->x = accum->y = accum->z = -1; }

static void fz3Accum(int3 *accum,
                     int inVal,
                     int x /* special arg */,
                     int y /* special arg */,
                     int z /* special arg */) {
  if (inVal==0) {
    accum->x = x;
    accum->y = y;
    accum->z = z;
  }
}

static void fz3Combine(int3 *accum, const int3 *accum2) {
  if (accum2->x >= 0) *accum = *accum2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(histogram) \
  accumulator(hsgAccum) combiner(hsgCombine)

#define BUCKETS 256
typedef uint32_t Histogram[BUCKETS];

static void hsgAccum(Histogram *h, uchar in) { ++(*h)[in]; }

static void hsgCombine(Histogram *accum, const Histogram *addend) {
  for (int i = 0; i < BUCKETS; ++i)
    (*accum)[i] += (*addend)[i];
}

#pragma rs reduce(mode) \
  accumulator(hsgAccum) combiner(hsgCombine) \
  outconverter(modeOutConvert)

static void modeOutConvert(int2 *result, const Histogram *h) {
  uint32_t mode = 0;
  for (int i = 1; i < BUCKETS; ++i)
    if ((*h)[i] > (*h)[mode]) mode = i;
  result->x = mode;
  result->y = (*h)[mode];
}

/////////////////////////////////////////////////////////////////////////

// Simple test case where there are two inputs
#pragma rs reduce(sumxor) accumulator(sxAccum) combiner(sxCombine)

static void sxAccum(int *accum, int inVal1, int inVal2) { *accum += (inVal1 ^ inVal2); }

static void sxCombine(int *accum, const int *accum2) { *accum += *accum2; }

/////////////////////////////////////////////////////////////////////////

// Test case where inputs are of different types
#pragma rs reduce(sillysum) accumulator(ssAccum) combiner(ssCombine)

static void ssAccum(long *accum, char c, float f, int3 i3) {
  *accum += ((((c + (long)ceil(log(f))) + i3.x) + i3.y) + i3.z);
}

static void ssCombine(long *accum, const long *accum2) { *accum += *accum2; }

/////////////////////////////////////////////////////////////////////////

// Test out-of-range result.

// When a result is ulong, it can take on values not representable on
// the Java side, where there are no unsigned integral types and long
// is the largest integral type -- i.e., all values in the range
// (MAX_LONG, MAX_ULONG] are not representable in Java.  The reflected
// result_*.get() methods throw an exception if the result value is
// out of range.  The globals and reduction kernels below allow a test
// case on the Java side to describe what kind of result we should
// produce -- in particular, what to use for an in-range value and an
// out-of-range value, and where (if anywhere) to put an out-of-range
// value within the result (which might be scalar, vector, array of
// scalar, or array of vector).

// We don't care about the input at all.
// We use these globals to configure the generation of the result.
// A kernel puts 2*oorrBadResultHalf in the position (if any) of the result
// given by oorrBadResult, and oorrGoodResult everywhere else.
// The oorrBadPos encoding is as follows:
// - For scalar result, 0 = scalar; anything else = nowhere
// - For vector result, 0..length(vector)-1 = corresponding vector component
//     (0 = x, 1 = y, 2 = z, 3 = w); anything else = nowhere
// - For array of scalar result, 0..length(array)-1 = corresponding array element;
//     anything else = nowhere
// - For array of vector result, 0..length(vector)*length(array)-1 = corresponding
//     vector component C of corresponding array element E; anything else = nowhere
//     (encoding is C + length(vector)*E)
ulong oorrGoodResult;     // the value of a good result
ulong oorrBadResultHalf;  // half the value of a bad result
                          //   ("half" because Java can only set the global from long not from ulong)
int   oorrBadPos;         // position of bad result

#define oorrBadResult (2*oorrBadResultHalf)

static void oorrAccum(int *accum, int val) { }

#pragma rs reduce(oorrSca) accumulator(oorrAccum) outconverter(oorrScaOut)
static void oorrScaOut(ulong *out, const int *accum) {
  *out = (oorrBadPos ? oorrGoodResult : oorrBadResult);
}

#pragma rs reduce(oorrVec4) accumulator(oorrAccum) outconverter(oorrVec4Out)
static void oorrVec4Out(ulong4 *out, const int *accum) {
  out->x = (oorrBadPos==0 ? oorrBadResult : oorrGoodResult);
  out->y = (oorrBadPos==1 ? oorrBadResult : oorrGoodResult);
  out->z = (oorrBadPos==2 ? oorrBadResult : oorrGoodResult);
  out->w = (oorrBadPos==3 ? oorrBadResult : oorrGoodResult);
}

#pragma rs reduce(oorrArr9) accumulator(oorrAccum) outconverter(oorrArr9Out)
typedef ulong Arr9[9];
static void oorrArr9Out(Arr9 *out, const int *accum) {
  for (int i = 0; i < 9; ++i)
    (*out)[i] = (i == oorrBadPos ? oorrBadResult : oorrGoodResult);
}

#pragma rs reduce(oorrArr9Vec4) accumulator(oorrAccum) outconverter(oorrArr9Vec4Out)
typedef ulong4 Arr9Vec4[9];
static void oorrArr9Vec4Out(Arr9Vec4 *out, const int *accum) {
  const int badIdx = (oorrBadPos >= 0 ? oorrBadPos / 4: -1);
  const int badComp = (oorrBadPos >= 0 ? oorrBadPos % 4: -1);
  for (int i = 0; i < 9; ++i) {
    (*out)[i].x = ((i==badIdx) && (0==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].y = ((i==badIdx) && (1==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].z = ((i==badIdx) && (2==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].w = ((i==badIdx) && (3==badComp)) ? oorrBadResult : oorrGoodResult;
  }
}
