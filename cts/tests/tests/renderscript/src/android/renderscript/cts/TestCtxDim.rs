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

int gDimX, gDimY, gDimZ;
static bool failed = false;

void __attribute__((kernel)) check_kernel(int32_t in /* dummy */, rs_kernel_context context) {
    uint32_t dimX = rsGetDimX(context);
    _RS_ASSERT(gDimX == dimX);
    uint32_t dimY = rsGetDimY(context);
    _RS_ASSERT(gDimY == dimY);
    uint32_t dimZ = rsGetDimZ(context);
    _RS_ASSERT(gDimZ == dimZ);
}

void check_result() {
    if (failed) {
        rsSendToClientBlocking(RS_MSG_TEST_FAILED);
    }
    else {
        rsSendToClientBlocking(RS_MSG_TEST_PASSED);
    }
}
