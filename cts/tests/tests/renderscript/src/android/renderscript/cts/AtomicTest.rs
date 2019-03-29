/*
 * Copyright (C) 2014 The Android Open Source Project
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
#include "rs_atomic.rsh"

volatile int32_t gISum;
volatile uint32_t gUSum;


void __attribute__((kernel)) test_Inc(int32_t v) {
    rsAtomicInc(&gISum);
}
void __attribute__((kernel)) test_uInc(uint32_t v) {
    rsAtomicInc(&gUSum);
}

void __attribute__((kernel)) test_Dec(int32_t v) {
    rsAtomicDec(&gISum);
}
void __attribute__((kernel)) test_uDec(uint32_t v) {
    rsAtomicDec(&gUSum);
}

#define TEST_OP(op)                                     \
void __attribute__((kernel)) test_##op(int32_t v) {     \
    rsAtomic##op(&gISum, v);                            \
}                                                       \
void __attribute__((kernel)) test_u##op(uint32_t v) {   \
    rsAtomic##op(&gUSum, v);                            \
}

TEST_OP(Add)
TEST_OP(Sub)
TEST_OP(And)
TEST_OP(Or)
TEST_OP(Xor)
TEST_OP(Min)
TEST_OP(Max)

// the folowing functions copy the global to an allocation
// to allow the calling code to read it back.
void getValueS(rs_allocation v) {
    rsSetElementAt_int(v, gISum, 0);
}

void getValueU(rs_allocation v) {
    rsSetElementAt_uint(v, gUSum, 0);
}

void computeReference_Min(rs_allocation a, rs_allocation result) {
    uint32_t dimX = rsAllocationGetDimX(a);
    uint32_t dimY = rsAllocationGetDimY(a);
    for (uint32_t y = 0; y < dimY; y++) {
        for (uint32_t x = 0; x < dimX; x++) {
            int v = rsGetElementAt_int(a, x, y);
            gISum = min(gISum, v);
        }
    }
    rsSetElementAt_int(result, gISum, 0);
}

void computeReference_uMin(rs_allocation a, rs_allocation result) {
    uint32_t dimX = rsAllocationGetDimX(a);
    uint32_t dimY = rsAllocationGetDimY(a);
    for (uint32_t y = 0; y < dimY; y++) {
        for (uint32_t x = 0; x < dimX; x++) {
            uint v = rsGetElementAt_uint(a, x, y);
            gUSum = min(gUSum, v);
        }
    }
    rsSetElementAt_uint(result, gUSum, 0);
}

void computeReference_Max(rs_allocation a, rs_allocation result) {
    uint32_t dimX = rsAllocationGetDimX(a);
    uint32_t dimY = rsAllocationGetDimY(a);
    for (uint32_t y = 0; y < dimY; y++) {
        for (uint32_t x = 0; x < dimX; x++) {
            int v = rsGetElementAt_int(a, x, y);
            gISum = max(gISum, v);
        }
    }
    rsSetElementAt_int(result, gISum, 0);
}

void computeReference_uMax(rs_allocation a, rs_allocation result) {
    uint32_t dimX = rsAllocationGetDimX(a);
    uint32_t dimY = rsAllocationGetDimY(a);
    for (uint32_t y = 0; y < dimY; y++) {
        for (uint32_t x = 0; x < dimX; x++) {
            uint v = rsGetElementAt_uint(a, x, y);
            gUSum = max(gUSum, v);
        }
    }
    rsSetElementAt_uint(result, gUSum, 0);
}


void __attribute__((kernel)) test_Cas(int32_t v) {
    int tmp = gISum;
    while (rsAtomicCas(&gISum, tmp, tmp + 1) != tmp) {
        tmp = gISum;
    }
}
void __attribute__((kernel)) test_uCas(uint32_t v) {
    uint tmp = gUSum;
    while (rsAtomicCas(&gUSum, tmp, tmp + 1) != tmp) {
        tmp = gUSum;
    }
}


