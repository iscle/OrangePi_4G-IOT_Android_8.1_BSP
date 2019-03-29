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

#include "shared.rsh"

double gAllowedFloatMatError = 0.00000001;
double gAllowedDoubleMatError = 0.00000000001;
static bool hadError = false;
static int2 errorLoc = {0,0};

#define printCell(txt, a, xy) \
{                       \
    rs_element e = rsAllocationGetElement(a); \
    rs_data_type dt = rsElementGetDataType(e); \
    uint32_t vs = rsElementGetVectorSize(e); \
 \
    if (dt == RS_TYPE_UNSIGNED_8) { \
        switch(vs) { \
        case 4: \
            rsDebug(txt, rsGetElementAt_uchar4(a, xy.x, xy.y)); \
            break; \
        case 3: \
            rsDebug(txt, rsGetElementAt_uchar3(a, xy.x, xy.y)); \
            break; \
        case 2: \
            rsDebug(txt, rsGetElementAt_uchar2(a, xy.x, xy.y)); \
            break; \
        case 1: \
            rsDebug(txt, rsGetElementAt_uchar(a, xy.x, xy.y)); \
            break; \
        } \
    } else if (dt == RS_TYPE_FLOAT_32) { \
        switch(vs) { \
        case 4: \
            rsDebug(txt, rsGetElementAt_float4(a, xy.x, xy.y)); \
            break; \
        case 3: \
            rsDebug(txt, rsGetElementAt_float3(a, xy.x, xy.y)); \
            break; \
        case 2: \
            rsDebug(txt, rsGetElementAt_float2(a, xy.x, xy.y)); \
            break; \
        case 1: \
            rsDebug(txt, rsGetElementAt_float(a, xy.x, xy.y)); \
            break; \
        } \
    } else if (dt == RS_TYPE_FLOAT_64) { \
        switch(vs) { \
        case 4: \
            rsDebug(txt, rsGetElementAt_double4(a, xy.x, xy.y)); \
            break; \
        case 3: \
            rsDebug(txt, rsGetElementAt_double3(a, xy.x, xy.y)); \
            break; \
        case 2: \
            rsDebug(txt, rsGetElementAt_double2(a, xy.x, xy.y)); \
            break; \
        case 1: \
            rsDebug(txt, rsGetElementAt_double(a, xy.x, xy.y)); \
        } \
    } \
}

static bool verify_CMatrix(rs_allocation in1, rs_allocation in2, double l2Norm, bool isUpperMatrix) {
    uint32_t w = rsAllocationGetDimX(in1);
    uint32_t h = rsAllocationGetDimY(in1);
    for (uint32_t y = 0; y < h; y++) {
        uint32_t xStart = 0;
        if (isUpperMatrix) {
            // Just test the upper matrix for certain BLAS routines
            xStart = y;
        }
        for (uint32_t x = xStart; x < w; x++) {
            float2 pref = rsGetElementAt_float2(in1, x, y);
            float2 ptst = rsGetElementAt_float2(in2, x, y);
            double absErr = (pref.x - ptst.x) * (pref.x - ptst.x) + (pref.y - ptst.y) * (pref.y - ptst.y);
            if (absErr > l2Norm * gAllowedFloatMatError) {
                errorLoc.x = x;
                errorLoc.y = y;
                hadError = true;
                return false;
            }
        }
    }
    return true;
}

static bool verify_SMatrix(rs_allocation in1, rs_allocation in2, double l2Norm, bool isUpperMatrix) {
    uint32_t w = rsAllocationGetDimX(in1);
    uint32_t h = rsAllocationGetDimY(in1);
    for (uint32_t y = 0; y < h; y++) {
        uint32_t xStart = 0;
        if (isUpperMatrix) {
            // Just test the upper matrix for certain BLAS routines
            xStart = y;
        }
        for (uint32_t x = xStart; x < w; x++) {
            float pref = rsGetElementAt_float(in1, x, y);
            float ptst = rsGetElementAt_float(in2, x, y);
            double absErr = (pref - ptst) * (pref - ptst);
            if (absErr > l2Norm * gAllowedFloatMatError) {
                errorLoc.x = x;
                errorLoc.y = y;
                hadError = true;
                return false;
            }
        }
    }
    return true;
}

static bool verify_ZMatrix(rs_allocation in1, rs_allocation in2, double l2Norm, bool isUpperMatrix) {
    uint32_t w = rsAllocationGetDimX(in1);
    uint32_t h = rsAllocationGetDimY(in1);
    for (uint32_t y = 0; y < h; y++) {
        uint32_t xStart = 0;
        if (isUpperMatrix) {
            // Just test the upper matrix for certain BLAS routines
            xStart = y;
        }
        for (uint32_t x = xStart; x < w; x++) {
            double2 pref = rsGetElementAt_double2(in1, x, y);
            double2 ptst = rsGetElementAt_double2(in2, x, y);
            double absErr = (pref.x - ptst.x) * (pref.x - ptst.x) + (pref.y - ptst.y) * (pref.y - ptst.y);
            if (absErr > l2Norm * gAllowedDoubleMatError) {
                errorLoc.x = x;
                errorLoc.y = y;
                hadError = true;
                return false;
            }
        }
    }
    return true;
}

static bool verify_DMatrix(rs_allocation in1, rs_allocation in2, double l2Norm, bool isUpperMatrix) {
    uint32_t w = rsAllocationGetDimX(in1);
    uint32_t h = rsAllocationGetDimY(in1);
    for (uint32_t y = 0; y < h; y++) {
        uint32_t xStart = 0;
        if (isUpperMatrix) {
            // Just test the upper matrix for certain BLAS routines
            xStart = y;
        }
        for (uint32_t x = xStart; x < w; x++) {
            double pref = rsGetElementAt_double(in1, x, y);
            double ptst = rsGetElementAt_double(in2, x, y);
            double absErr = (pref - ptst) * (pref - ptst);
            if (absErr > l2Norm * gAllowedDoubleMatError) {
                errorLoc.x = x;
                errorLoc.y = y;
                hadError = true;
                return false;
            }
        }
    }
    return true;
}

void verifyMatrix(rs_allocation ref_in, rs_allocation tst_in, double l2Norm, bool isUpperMatrix) {
    rs_element e = rsAllocationGetElement(ref_in);
    rs_data_type dt = rsElementGetDataType(e);
    uint32_t vs = rsElementGetVectorSize(e);
    bool valid = false;

    if (dt == RS_TYPE_FLOAT_32) {
        switch(vs) {
        case 2:
            valid = verify_CMatrix(ref_in, tst_in, l2Norm, isUpperMatrix);
            break;
        case 1:
            valid = verify_SMatrix(ref_in, tst_in, l2Norm, isUpperMatrix);
            break;
        }
    } else if (dt == RS_TYPE_FLOAT_64) {
        switch(vs) {
        case 2:
            valid = verify_ZMatrix(ref_in, tst_in, l2Norm, isUpperMatrix);
            break;
        case 1:
            valid = verify_DMatrix(ref_in, tst_in, l2Norm, isUpperMatrix);
            break;
        }
    }
    if (!valid) {
        rsDebug("verify failure at xy", errorLoc);
        printCell("Expected value ", ref_in, errorLoc);
        printCell("Actual value   ", tst_in, errorLoc);
    }
}

void checkError()
{
    if (hadError) {
        rsSendToClientBlocking(RS_MSG_TEST_FAILED);
    } else {
        rsSendToClientBlocking(RS_MSG_TEST_PASSED);
    }
}
