/*
 * Copyright (C) 2015-2016 The Android Open Source Project
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

rs_allocation gAllocOut;

int RS_KERNEL foo(int a) {
    return a * 2;
}

int RS_KERNEL goo(int a, int b) {
    return a + b;
}

void RS_KERNEL bar(int x, int y) {
    int a = rsGetElementAt_int(gAllocOut, x, y);
    a++;
    rsSetElementAt_int(gAllocOut, a, x, y);
}

void testSingleInput(rs_allocation in, rs_allocation out) {
    rsForEach(foo, in, out);
}

void testMultiInput(rs_allocation in1, rs_allocation in2, rs_allocation out) {
    rsForEach(goo, in1, in2, out);
}

void testLaunchOptions(rs_allocation in, rs_allocation out, int dimX, int dimY) {
    rs_script_call_t opts = {};
    opts.xStart = 0;
    opts.xEnd = dimX;
    opts.yStart = 0;
    opts.yEnd = dimY / 2;
    rsForEachWithOptions(foo, &opts, in, out);
}

void testAllocationlessLaunch(rs_allocation inAndOut, int dimX, int dimY) {
    gAllocOut = inAndOut;
    rs_script_call_t opts = {};
    opts.xStart = 0;
    opts.xEnd = dimX;
    opts.yStart = 0;
    opts.yEnd = dimY;
    rsForEachWithOptions(bar, &opts);
}

void testConsistency(rs_allocation in, rs_allocation out) {
    rsForEach(foo, in, out);
    const uint32_t dimX = rsAllocationGetDimX(in);
    const uint32_t dimY = rsAllocationGetDimY(in);
    for (int i = 0; i < dimX; i++) {
        for (int j = 0; j < dimY; j++) {
            if (rsGetElementAt_int(out, i, j) != 2 * rsGetElementAt_int(in, i, j)) {
                rsSendToClientBlocking(RS_MSG_TEST_FAILED);
                return;
            }
        }
    }
    rsSendToClientBlocking(RS_MSG_TEST_PASSED);
}

