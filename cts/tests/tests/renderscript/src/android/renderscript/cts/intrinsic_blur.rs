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

int height;
int width;
static int radius;
rs_allocation ScratchPixel1;
rs_allocation ScratchPixel2;

const int MAX_RADIUS = 25;

// Store our coefficients here
static float gaussian[MAX_RADIUS * 2 + 1];

float4 __attribute__((kernel)) convert1_uToF(uchar v) {
    float4 r = rsUnpackColor8888(v);
    return r.r;
}

float4 __attribute__((kernel)) convert4_uToF(uchar4 v) {
    return rsUnpackColor8888(v);
}

uchar __attribute__((kernel)) convert1_fToU(float4 v) {
    uchar4 r = rsPackColorTo8888(v);
    return r.r;
}

uchar4 __attribute__((kernel)) convert4_fToU(float4 v) {
    return rsPackColorTo8888(v);
}

void setRadius(int rad) {
    // This function is a duplicate of:
    // RsdCpuScriptIntrinsicBlur::ComputeGaussianWeights()
    // Which is the reference C implementation
    radius = rad;
    const float e = M_E;
    const float pi = M_PI;
    float sigma = 0.4f * (float)radius + 0.6f;
    float coeff1 = 1.0f / (sqrt( 2.0f * pi ) * sigma);
    float coeff2 = - 1.0f / (2.0f * sigma * sigma);
    float normalizeFactor = 0.0f;
    float floatR = 0.0f;
    for (int r = -radius; r <= radius; r ++) {
        floatR = (float)r;
        gaussian[r + radius] = coeff1 * pow(e, floatR * floatR * coeff2);
        normalizeFactor += gaussian[r + radius];
    }

    normalizeFactor = 1.0f / normalizeFactor;
    for (int r = -radius; r <= radius; r ++) {
        floatR = (float)r;
        gaussian[r + radius] *= normalizeFactor;
    }
}

float4 __attribute__((kernel)) vert(uint32_t x, uint32_t y) {
    float4 blurredPixel = 0;
    int gi = 0;
    for (int r = -radius; r <= radius; r ++) {
        int validH = clamp((int)y + r, (int)0, (int)(height - 1));
        float4 i = rsGetElementAt_float4(ScratchPixel2, x, validH);
        blurredPixel += i * gaussian[gi++];
    }
    return blurredPixel;
}

float4 __attribute__((kernel)) horz(uint32_t x, uint32_t y) {
    float4 blurredPixel = 0;
    int gi = 0;
    for (int r = -radius; r <= radius; r ++) {
        // Stepping left and right away from the pixel
        int validX = clamp((int)x + r, (int)0, (int)(width - 1));
        float4 i = rsGetElementAt_float4(ScratchPixel1, validX, y);
        blurredPixel += i * gaussian[gi++];
    }
    return blurredPixel;
}
