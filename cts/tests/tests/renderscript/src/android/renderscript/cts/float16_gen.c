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

#include <stdlib.h>
#include <stdio.h>
#include <math.h>

// This utility generates a Java file containing data used in the CTS test for
// FP16 arithmetic.  The Java file containes a class with the following fields:
//     * A 1D array of length 'n' containing various constants used in the test
//     * Four n x n x 2 arrays, each containing the reference output for
//     pair-wise addition, subtraction, multiplication and division.  The
//     reference output is a range accounting for tolerable error.  The
//     acceptable error is 3 x ULP for division and 1 x ULP for other
//     operations.

typedef __fp16 half;

// Macros for names of the package, class and fields in the generated java file
#define PACKAGE_NAME "android.renderscript.cts"
#define CLASS_NAME "Float16TestData"
#define INPUT_ARRAY "input"
#define OUTPUT_ARRAY_ADD "ReferenceOutputForAdd"
#define OUTPUT_ARRAY_SUB "ReferenceOutputForSub"
#define OUTPUT_ARRAY_MUL "ReferenceOutputForMul"
#define OUTPUT_ARRAY_DIV "ReferenceOutputForDiv"

// Structure to hold an FP16 constant and its human-readable description, to be
// added as a comment, in the generated Java file
typedef struct {
  unsigned short value;
  const char* description;
} FP16Constant;

FP16Constant input[] = {
    { 0b0011110000000000, "one" },
    { 0b0100000000000000, "two" },
    { 0b0000000000000001, "smallest subnormal" },
    { 0b0000001111111111, "largest subnormal" },
    { 0b0000010000000000, "smallest normal" },
    { 0b0111101111111111, "largest normal" },
    { 0x3880, "0.562500" },
    { 0x3e80, "1.625000" },
    { 0x5140, "42.000000" },
    { 0x5ac0, "216.000000" },
    { 0x6c75, "4564.000000" },
    { 0x7b53, "60000.000000" },
    { 0b1011110000000000, "negative one" },
    { 0b1100000000000000, "negative two" },
    { 0b1000000000000001, "negative (smallest subnormal)" },
    { 0b1000001111111111, "negative (largest subnormal)" },
    { 0b1000010000000000, "negative (smallest normal)" },
    { 0b1111101111111111, "negative (largest normal)" },
    { 0xb880, "-0.562500" },
    { 0xbe80, "-1.625000" },
    { 0xd140, "-42.000000" },
    { 0xdac0, "-216.000000" },
    { 0xec75, "-4564.000000" },
    { 0xfb53, "-60000.000000" },
    { 0b0000000000000000, "zero" },
    { 0b0111110000000000, "infinity" },
    { 0b1000000000000000, "negative zero" },
    { 0b1111110000000000, "negative infinity" },
    { 0b0111110000000001, "nan" },
};

const int numInputs = sizeof(input) / sizeof(FP16Constant);

// 16-bit masks for extracting sign, exponent and mantissa bits
static unsigned short SIGN_MASK     = 0x8000;
static unsigned short EXPONENT_MASK = 0x7C00;
static unsigned short MANTISSA_MASK = 0x03FF;

// NaN has all exponent bits set to 1 and a non-zero mantissa
int isFloat16NaN(unsigned short val) {
  return (val & EXPONENT_MASK) == EXPONENT_MASK &&
         (val & MANTISSA_MASK) != 0;
}

// Infinity has all exponent bits set to 1 and zeroes in mantissa
int isFloat16Infinite(unsigned short val) {
  return (val & EXPONENT_MASK) == EXPONENT_MASK &&
         (val & MANTISSA_MASK) == 0;
}

// Subnormal numbers have exponent bits set to 0 and a non-zero mantissa
int isFloat16SubNormal(unsigned short val) {
    return (val & EXPONENT_MASK) == 0 && (val & MANTISSA_MASK) != 0;
}

// Negativity test checks the sign bit
int isFloat16Negative(unsigned short val) {
    return (val & SIGN_MASK) != 0;
}

// Interpret a short as a FP16 value and convert to float
float half2float(unsigned short s) {
  half h = *(half *) &s;
  return (float) h;
}

// Return the short value representing a float value in FP16
unsigned short float2half(float f) {
  half h = (half) f;
  return *(unsigned short *) &h;
}

// Compute ULP for 'value' and store value +/- tolerance * ULP in bounds sarray
void getErrorBar(unsigned short value, int tolerance, unsigned short bounds[2]) {
  // Validate 'tolerance' parameter
  if (tolerance != 1 && tolerance != 3) {
    fprintf(stderr, "Allowed ULP error should either be 1 or 3, and not %d\n",
            tolerance);
    exit(0);
  }

  half hValue = *(half *) &value;
  half ulp;

  // For Infinity and NaN, bounds are equal to 'value'
  if (isFloat16Infinite(value) || isFloat16NaN(value)) {
    bounds[0] = value;
    bounds[1] = value;
    return;
  }

  // Compute ULP
  if (isFloat16SubNormal(value)) {
    // 1 ulp for a subnormal number is the smallest possible subnormal
    unsigned short ulpInShort = 0b0000000000000001;
    ulp = *(half *) &ulpInShort;
  }
  else {
    // 1 ulp for a non-subnormal number is (b - a) where
    //   - a has same exponent as 'value', zeroes for sign and mantissa
    //   - b has same exponent and sign as 'a', and has '1' in the mantissa
    // (b - a) gives the ULP by getting rid of the implied '1' at the front of
    // the mantissa
    unsigned short a = (value & EXPONENT_MASK);
    unsigned short b = (a | 1);
    half hA = *(half *) &a;
    half hB = *(half *) &b;
    ulp = hB - hA;
  }

  // Compute error bar based on error tolerance
  half lb = hValue - tolerance * ulp;
  half ub = hValue + tolerance * ulp;
  if (lb > ub) {
    fprintf(stderr, "Warning! inconsistency in bounds\n");
    fprintf(stderr, "Value: %f, ulp: %f\n", (float) hValue, (float) ulp);
    fprintf(stderr, "lb: %f ub: %f\n", (float) lb, (float) ub);
    fprintf(stderr, "lb: %x ub: %x\n", *(unsigned short *) &lb, *(unsigned short *) &ub);
  }

  // Set the bounds
  bounds[0] = *(unsigned short *) &lb;
  bounds[1] = *(unsigned short *) &ub;

  // RS allows flush-to-zero for sub-normal results in relaxed precision.
  // Flush lower bound of a positive sub-normal result to zero.
  if (!isFloat16Negative(bounds[0]) && isFloat16SubNormal(bounds[0]))
    bounds[0] = 0x0;
  // Flush upper bound of a negative sub-normal result to negative zero.
  if (isFloat16Negative(bounds[1]) && isFloat16SubNormal(bounds[1]))
    bounds[1] = 0x0 | SIGN_MASK;

}

// Utilities that take 'unsigned short' representations of two fp16 values and
// return the result of an arithmetic operation as an 'unsigned short'.
typedef unsigned short operation_t(unsigned short, unsigned short);

unsigned short add(unsigned short a, unsigned short b) {
  float op1 = half2float(a);
  float op2 = half2float(b);
  return float2half(op1 + op2);
}

unsigned short subtract(unsigned short a, unsigned short b) {
  float op1 = half2float(a);
  float op2 = half2float(b);
  return float2half(op1 - op2);
}

unsigned short multiply(unsigned short a, unsigned short b) {
  float op1 = half2float(a);
  float op2 = half2float(b);
  return float2half(op1 * op2);
}

unsigned short divide(unsigned short a, unsigned short b) {
  float op1 = half2float(a);
  float op2 = half2float(b);
  return float2half(op1 / op2);
}

// Print Java code that initializes the input array (along with the description
// of the constant as a comment)
void printInput() {
  printf("static short[] %s = {\n", INPUT_ARRAY);

  for (int x = 0; x < numInputs; x ++)
    printf("(short) 0x%04x, // %s\n", input[x].value, input[x].description);

  printf("};\n\n");
}

// Print Java code that initializes the output array with the acceptable bounds
// on the output.  For each pair of inputs, bounds are calculated on the result
// from applying 'operation' on the pair.
void printReferenceOutput(const char *fieldName, operation_t operation,
                          int tolerance) {
  unsigned short result;
  unsigned short resultBounds[2];

  printf("static short[][][] %s = {\n", fieldName);

  for (int x = 0; x < numInputs; x ++) {
    printf("{");
    for (int y = 0; y < numInputs; y ++) {
      // Apply 'operation' and compute error bounds for the result.
      result = operation(input[x].value, input[y].value);
      getErrorBar(result, tolerance, resultBounds);

      printf("{ (short) 0x%04x, (short) 0x%04x},", resultBounds[0],
                                                   resultBounds[1]);
    }
    printf("},\n");
  }

  printf("};\n\n");
}

const char *preamble = "/*\n"
" * Copyright (C) 2015 The Android Open Source Project\n"
" *\n"
" * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
" * you may not use this file except in compliance with the License.\n"
" * You may obtain a copy of the License at\n"
" *\n"
" *      http://www.apache.org/licenses/LICENSE-2.0\n"
" *\n"
" * Unless required by applicable law or agreed to in writing, software\n"
" * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
" * See the License for the specific language governing permissions and\n"
" * limitations under the License.\n"
" */\n"
"\n"
"/* Don't edit this file!  It is auto-generated by float16_gen.sh */\n\n"
"package "PACKAGE_NAME";\n\n"
"public class "CLASS_NAME" {\n";

int main() {
  // Print a preamble with copyright and class declaration, followed by the
  // input FP16 array, and reference outputs for pair-wise arithmetic
  // operations.
  printf("%s", preamble);
  printInput();

  printReferenceOutput(OUTPUT_ARRAY_ADD, add, 1);
  printReferenceOutput(OUTPUT_ARRAY_SUB, subtract, 1);
  printReferenceOutput(OUTPUT_ARRAY_MUL, multiply, 1);
  printReferenceOutput(OUTPUT_ARRAY_DIV, divide, 3);

  printf("}");
}
