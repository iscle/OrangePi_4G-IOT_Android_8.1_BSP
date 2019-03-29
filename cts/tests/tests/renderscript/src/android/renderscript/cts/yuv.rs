/*
 * Copyright (C) 2013 The Android Open Source Project
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

#pragma rs_fp_relaxed

rs_allocation mInput;

rs_allocation mInY;
rs_allocation mInU;
rs_allocation mInV;

static uchar4 yuvToRGBA4(uchar y, uchar u, uchar v) {
    short Y = ((short)y) - 16;
    short U = ((short)u) - 128;
    short V = ((short)v) - 128;

    short4 p;
    p.x = (Y * 298 + V * 409 + 128) >> 8;
    p.y = (Y * 298 - U * 100 - V * 208 + 128) >> 8;
    p.z = (Y * 298 + U * 516 + 128) >> 8;
    p.w = 255;
    if(p.x < 0) {
        p.x = 0;
    }
    if(p.x > 255) {
        p.x = 255;
    }
    if(p.y < 0) {
        p.y = 0;
    }
    if(p.y > 255) {
        p.y = 255;
    }
    if(p.z < 0) {
        p.z = 0;
    }
    if(p.z > 255) {
        p.z = 255;
    }

    return (uchar4){p.x, p.y, p.z, p.w};
}

static float4 yuvToRGBA_f4(uchar y, uchar u, uchar v) {
    float4 yuv_U_values = {0.f, -0.392f * 0.003921569f, +2.02 * 0.003921569f, 0.f};
    float4 yuv_V_values = {1.603f * 0.003921569f, -0.815f * 0.003921569f, 0.f, 0.f};

    float4 color = (float)y * 0.003921569f;
    float4 fU = ((float)u) - 128.f;
    float4 fV = ((float)v) - 128.f;

    color += fU * yuv_U_values;
    color += fV * yuv_V_values;
    color = clamp(color, 0.f, 1.f);
    return color;
}

void makeRef(rs_allocation ay, rs_allocation au, rs_allocation av, rs_allocation aout) {
    uint32_t w = rsAllocationGetDimX(ay);
    uint32_t h = rsAllocationGetDimY(ay);

    for (int y = 0; y < h; y++) {
        //rsDebug("y", y);
        for (int x = 0; x < w; x++) {

            int py = rsGetElementAt_uchar(ay, x, y);
            int pu = rsGetElementAt_uchar(au, x >> 1, y >> 1);
            int pv = rsGetElementAt_uchar(av, x >> 1, y >> 1);

            //rsDebug("py", py);
            //rsDebug(" u", pu);
            //rsDebug(" v", pv);

            uchar4 rgb = yuvToRGBA4(py, pu, pv);
            //rsDebug("  ", rgb);

            rsSetElementAt_uchar4(aout, rgb, x, y);
        }
    }
}

void makeRef_f4(rs_allocation ay, rs_allocation au, rs_allocation av, rs_allocation aout) {
    uint32_t w = rsAllocationGetDimX(ay);
    uint32_t h = rsAllocationGetDimY(ay);

    for (int y = 0; y < h; y++) {
        //rsDebug("y", y);
        for (int x = 0; x < w; x++) {

            uchar py = rsGetElementAt_uchar(ay, x, y);
            uchar pu = rsGetElementAt_uchar(au, x >> 1, y >> 1);
            uchar pv = rsGetElementAt_uchar(av, x >> 1, y >> 1);

            //rsDebug("py", py);
            //rsDebug(" u", pu);
            //rsDebug(" v", pv);

            float4 rgb = yuvToRGBA_f4(py, pu, pv);
            //rsDebug("  ", rgb);

            rsSetElementAt_float4(aout, rgb, x, y);
        }
    }
}

uchar4 __attribute__((kernel)) cvt(uint32_t x, uint32_t y) {

    uchar py = rsGetElementAtYuv_uchar_Y(mInput, x, y);
    uchar pu = rsGetElementAtYuv_uchar_U(mInput, x, y);
    uchar pv = rsGetElementAtYuv_uchar_V(mInput, x, y);

    //rsDebug("py2", py);
    //rsDebug(" u2", pu);
    //rsDebug(" v2", pv);

    return yuvToRGBA4(py, pu, pv);
}

float4 __attribute__((kernel)) cvt_f4(uint32_t x, uint32_t y) {

    uchar py = rsGetElementAtYuv_uchar_Y(mInput, x, y);
    uchar pu = rsGetElementAtYuv_uchar_U(mInput, x, y);
    uchar pv = rsGetElementAtYuv_uchar_V(mInput, x, y);

    //rsDebug("py2", py);
    //rsDebug(" u2", pu);
    //rsDebug(" v2", pv);

    return rsYuvToRGBA_float4(py, pu, pv);
}

