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

int32_t gWidthIn;
int32_t gHeightIn;
rs_allocation gIn;
float scaleX;
float scaleY;

static float4 cubicInterpolate_F4 (float4 p0,float4 p1,float4 p2,float4 p3 , float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}

static float3 cubicInterpolate_F3 (float3 p0,float3 p1,float3 p2,float3 p3 , float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}

static float2 cubicInterpolate_F2 (float2 p0,float2 p1,float2 p2,float2 p3 , float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}

static float cubicInterpolate_F1 (float p0,float p1,float p2,float p3 , float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}

uchar4 __attribute__((kernel)) bicubic_U4(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float4 p00 = convert_float4(rsGetElementAt_uchar4(gIn, xs0, ys0));
    float4 p01 = convert_float4(rsGetElementAt_uchar4(gIn, xs1, ys0));
    float4 p02 = convert_float4(rsGetElementAt_uchar4(gIn, xs2, ys0));
    float4 p03 = convert_float4(rsGetElementAt_uchar4(gIn, xs3, ys0));
    float4 p0  = cubicInterpolate_F4(p00, p01, p02, p03, xf);

    float4 p10 = convert_float4(rsGetElementAt_uchar4(gIn, xs0, ys1));
    float4 p11 = convert_float4(rsGetElementAt_uchar4(gIn, xs1, ys1));
    float4 p12 = convert_float4(rsGetElementAt_uchar4(gIn, xs2, ys1));
    float4 p13 = convert_float4(rsGetElementAt_uchar4(gIn, xs3, ys1));
    float4 p1  = cubicInterpolate_F4(p10, p11, p12, p13, xf);

    float4 p20 = convert_float4(rsGetElementAt_uchar4(gIn, xs0, ys2));
    float4 p21 = convert_float4(rsGetElementAt_uchar4(gIn, xs1, ys2));
    float4 p22 = convert_float4(rsGetElementAt_uchar4(gIn, xs2, ys2));
    float4 p23 = convert_float4(rsGetElementAt_uchar4(gIn, xs3, ys2));
    float4 p2  = cubicInterpolate_F4(p20, p21, p22, p23, xf);

    float4 p30 = convert_float4(rsGetElementAt_uchar4(gIn, xs0, ys3));
    float4 p31 = convert_float4(rsGetElementAt_uchar4(gIn, xs1, ys3));
    float4 p32 = convert_float4(rsGetElementAt_uchar4(gIn, xs2, ys3));
    float4 p33 = convert_float4(rsGetElementAt_uchar4(gIn, xs3, ys3));
    float4 p3  = cubicInterpolate_F4(p30, p31, p32, p33, xf);

    float4 p  = cubicInterpolate_F4(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return convert_uchar4(p);
}

uchar3 __attribute__((kernel)) bicubic_U3(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float3 p00 = convert_float3(rsGetElementAt_uchar3(gIn, xs0, ys0));
    float3 p01 = convert_float3(rsGetElementAt_uchar3(gIn, xs1, ys0));
    float3 p02 = convert_float3(rsGetElementAt_uchar3(gIn, xs2, ys0));
    float3 p03 = convert_float3(rsGetElementAt_uchar3(gIn, xs3, ys0));
    float3 p0  = cubicInterpolate_F3(p00, p01, p02, p03, xf);

    float3 p10 = convert_float3(rsGetElementAt_uchar3(gIn, xs0, ys1));
    float3 p11 = convert_float3(rsGetElementAt_uchar3(gIn, xs1, ys1));
    float3 p12 = convert_float3(rsGetElementAt_uchar3(gIn, xs2, ys1));
    float3 p13 = convert_float3(rsGetElementAt_uchar3(gIn, xs3, ys1));
    float3 p1  = cubicInterpolate_F3(p10, p11, p12, p13, xf);

    float3 p20 = convert_float3(rsGetElementAt_uchar3(gIn, xs0, ys2));
    float3 p21 = convert_float3(rsGetElementAt_uchar3(gIn, xs1, ys2));
    float3 p22 = convert_float3(rsGetElementAt_uchar3(gIn, xs2, ys2));
    float3 p23 = convert_float3(rsGetElementAt_uchar3(gIn, xs3, ys2));
    float3 p2  = cubicInterpolate_F3(p20, p21, p22, p23, xf);

    float3 p30 = convert_float3(rsGetElementAt_uchar3(gIn, xs0, ys3));
    float3 p31 = convert_float3(rsGetElementAt_uchar3(gIn, xs1, ys3));
    float3 p32 = convert_float3(rsGetElementAt_uchar3(gIn, xs2, ys3));
    float3 p33 = convert_float3(rsGetElementAt_uchar3(gIn, xs3, ys3));
    float3 p3  = cubicInterpolate_F3(p30, p31, p32, p33, xf);

    float3 p  = cubicInterpolate_F3(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return convert_uchar3(p);
}

uchar2 __attribute__((kernel)) bicubic_U2(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float2 p00 = convert_float2(rsGetElementAt_uchar2(gIn, xs0, ys0));
    float2 p01 = convert_float2(rsGetElementAt_uchar2(gIn, xs1, ys0));
    float2 p02 = convert_float2(rsGetElementAt_uchar2(gIn, xs2, ys0));
    float2 p03 = convert_float2(rsGetElementAt_uchar2(gIn, xs3, ys0));
    float2 p0  = cubicInterpolate_F2(p00, p01, p02, p03, xf);

    float2 p10 = convert_float2(rsGetElementAt_uchar2(gIn, xs0, ys1));
    float2 p11 = convert_float2(rsGetElementAt_uchar2(gIn, xs1, ys1));
    float2 p12 = convert_float2(rsGetElementAt_uchar2(gIn, xs2, ys1));
    float2 p13 = convert_float2(rsGetElementAt_uchar2(gIn, xs3, ys1));
    float2 p1  = cubicInterpolate_F2(p10, p11, p12, p13, xf);

    float2 p20 = convert_float2(rsGetElementAt_uchar2(gIn, xs0, ys2));
    float2 p21 = convert_float2(rsGetElementAt_uchar2(gIn, xs1, ys2));
    float2 p22 = convert_float2(rsGetElementAt_uchar2(gIn, xs2, ys2));
    float2 p23 = convert_float2(rsGetElementAt_uchar2(gIn, xs3, ys2));
    float2 p2  = cubicInterpolate_F2(p20, p21, p22, p23, xf);

    float2 p30 = convert_float2(rsGetElementAt_uchar2(gIn, xs0, ys3));
    float2 p31 = convert_float2(rsGetElementAt_uchar2(gIn, xs1, ys3));
    float2 p32 = convert_float2(rsGetElementAt_uchar2(gIn, xs2, ys3));
    float2 p33 = convert_float2(rsGetElementAt_uchar2(gIn, xs3, ys3));
    float2 p3  = cubicInterpolate_F2(p30, p31, p32, p33, xf);

    float2 p  = cubicInterpolate_F2(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return convert_uchar2(p);
}

uchar __attribute__((kernel)) bicubic_U1(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float p00 = (float)(rsGetElementAt_uchar(gIn, xs0, ys0));
    float p01 = (float)(rsGetElementAt_uchar(gIn, xs1, ys0));
    float p02 = (float)(rsGetElementAt_uchar(gIn, xs2, ys0));
    float p03 = (float)(rsGetElementAt_uchar(gIn, xs3, ys0));
    float p0  = cubicInterpolate_F1(p00, p01, p02, p03, xf);

    float p10 = (float)(rsGetElementAt_uchar(gIn, xs0, ys1));
    float p11 = (float)(rsGetElementAt_uchar(gIn, xs1, ys1));
    float p12 = (float)(rsGetElementAt_uchar(gIn, xs2, ys1));
    float p13 = (float)(rsGetElementAt_uchar(gIn, xs3, ys1));
    float p1  = cubicInterpolate_F1(p10, p11, p12, p13, xf);

    float p20 = (float)(rsGetElementAt_uchar(gIn, xs0, ys2));
    float p21 = (float)(rsGetElementAt_uchar(gIn, xs1, ys2));
    float p22 = (float)(rsGetElementAt_uchar(gIn, xs2, ys2));
    float p23 = (float)(rsGetElementAt_uchar(gIn, xs3, ys2));
    float p2  = cubicInterpolate_F1(p20, p21, p22, p23, xf);

    float p30 = (float)(rsGetElementAt_uchar(gIn, xs0, ys3));
    float p31 = (float)(rsGetElementAt_uchar(gIn, xs1, ys3));
    float p32 = (float)(rsGetElementAt_uchar(gIn, xs2, ys3));
    float p33 = (float)(rsGetElementAt_uchar(gIn, xs3, ys3));
    float p3  = cubicInterpolate_F1(p30, p31, p32, p33, xf);

    float p  = cubicInterpolate_F1(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return (uchar)p;
}

float4 __attribute__((kernel)) bicubic_F4(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float4 p00 = rsGetElementAt_float4(gIn, xs0, ys0);
    float4 p01 = rsGetElementAt_float4(gIn, xs1, ys0);
    float4 p02 = rsGetElementAt_float4(gIn, xs2, ys0);
    float4 p03 = rsGetElementAt_float4(gIn, xs3, ys0);
    float4 p0  = cubicInterpolate_F4(p00, p01, p02, p03, xf);

    float4 p10 = rsGetElementAt_float4(gIn, xs0, ys1);
    float4 p11 = rsGetElementAt_float4(gIn, xs1, ys1);
    float4 p12 = rsGetElementAt_float4(gIn, xs2, ys1);
    float4 p13 = rsGetElementAt_float4(gIn, xs3, ys1);
    float4 p1  = cubicInterpolate_F4(p10, p11, p12, p13, xf);

    float4 p20 = rsGetElementAt_float4(gIn, xs0, ys2);
    float4 p21 = rsGetElementAt_float4(gIn, xs1, ys2);
    float4 p22 = rsGetElementAt_float4(gIn, xs2, ys2);
    float4 p23 = rsGetElementAt_float4(gIn, xs3, ys2);
    float4 p2  = cubicInterpolate_F4(p20, p21, p22, p23, xf);

    float4 p30 = rsGetElementAt_float4(gIn, xs0, ys3);
    float4 p31 = rsGetElementAt_float4(gIn, xs1, ys3);
    float4 p32 = rsGetElementAt_float4(gIn, xs2, ys3);
    float4 p33 = rsGetElementAt_float4(gIn, xs3, ys3);
    float4 p3  = cubicInterpolate_F4(p30, p31, p32, p33, xf);

    float4 p  = cubicInterpolate_F4(p0, p1, p2, p3, yf);

    return p;
}

float3 __attribute__((kernel)) bicubic_F3(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float3 p00 = rsGetElementAt_float3(gIn, xs0, ys0);
    float3 p01 = rsGetElementAt_float3(gIn, xs1, ys0);
    float3 p02 = rsGetElementAt_float3(gIn, xs2, ys0);
    float3 p03 = rsGetElementAt_float3(gIn, xs3, ys0);
    float3 p0  = cubicInterpolate_F3(p00, p01, p02, p03, xf);

    float3 p10 = rsGetElementAt_float3(gIn, xs0, ys1);
    float3 p11 = rsGetElementAt_float3(gIn, xs1, ys1);
    float3 p12 = rsGetElementAt_float3(gIn, xs2, ys1);
    float3 p13 = rsGetElementAt_float3(gIn, xs3, ys1);
    float3 p1  = cubicInterpolate_F3(p10, p11, p12, p13, xf);

    float3 p20 = rsGetElementAt_float3(gIn, xs0, ys2);
    float3 p21 = rsGetElementAt_float3(gIn, xs1, ys2);
    float3 p22 = rsGetElementAt_float3(gIn, xs2, ys2);
    float3 p23 = rsGetElementAt_float3(gIn, xs3, ys2);
    float3 p2  = cubicInterpolate_F3(p20, p21, p22, p23, xf);

    float3 p30 = rsGetElementAt_float3(gIn, xs0, ys3);
    float3 p31 = rsGetElementAt_float3(gIn, xs1, ys3);
    float3 p32 = rsGetElementAt_float3(gIn, xs2, ys3);
    float3 p33 = rsGetElementAt_float3(gIn, xs3, ys3);
    float3 p3  = cubicInterpolate_F3(p30, p31, p32, p33, xf);

    float3 p  = cubicInterpolate_F3(p0, p1, p2, p3, yf);

    return p;
}

float2 __attribute__((kernel)) bicubic_F2(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float2 p00 = rsGetElementAt_float2(gIn, xs0, ys0);
    float2 p01 = rsGetElementAt_float2(gIn, xs1, ys0);
    float2 p02 = rsGetElementAt_float2(gIn, xs2, ys0);
    float2 p03 = rsGetElementAt_float2(gIn, xs3, ys0);
    float2 p0  = cubicInterpolate_F2(p00, p01, p02, p03, xf);

    float2 p10 = rsGetElementAt_float2(gIn, xs0, ys1);
    float2 p11 = rsGetElementAt_float2(gIn, xs1, ys1);
    float2 p12 = rsGetElementAt_float2(gIn, xs2, ys1);
    float2 p13 = rsGetElementAt_float2(gIn, xs3, ys1);
    float2 p1  = cubicInterpolate_F2(p10, p11, p12, p13, xf);

    float2 p20 = rsGetElementAt_float2(gIn, xs0, ys2);
    float2 p21 = rsGetElementAt_float2(gIn, xs1, ys2);
    float2 p22 = rsGetElementAt_float2(gIn, xs2, ys2);
    float2 p23 = rsGetElementAt_float2(gIn, xs3, ys2);
    float2 p2  = cubicInterpolate_F2(p20, p21, p22, p23, xf);

    float2 p30 = rsGetElementAt_float2(gIn, xs0, ys3);
    float2 p31 = rsGetElementAt_float2(gIn, xs1, ys3);
    float2 p32 = rsGetElementAt_float2(gIn, xs2, ys3);
    float2 p33 = rsGetElementAt_float2(gIn, xs3, ys3);
    float2 p3  = cubicInterpolate_F2(p30, p31, p32, p33, xf);

    float2 p  = cubicInterpolate_F2(p0, p1, p2, p3, yf);

    return p;
}

float __attribute__((kernel)) bicubic_F1(uint32_t x, uint32_t y) {
    float xf = (x + 0.5f) * scaleX - 0.5f;
    float yf = (y + 0.5f) * scaleY - 0.5f;

    int startx = (int) floor(xf - 1);
    int starty = (int) floor(yf - 1);
    xf = xf - floor(xf);
    yf = yf - floor(yf);
    int maxx = gWidthIn - 1;
    int maxy = gHeightIn - 1;

    uint32_t xs0 = (uint32_t) max(0, startx + 0);
    uint32_t xs1 = (uint32_t) max(0, startx + 1);
    uint32_t xs2 = (uint32_t) min(maxx, startx + 2);
    uint32_t xs3 = (uint32_t) min(maxx, startx + 3);

    uint32_t ys0 = (uint32_t) max(0, starty + 0);
    uint32_t ys1 = (uint32_t) max(0, starty + 1);
    uint32_t ys2 = (uint32_t) min(maxy, starty + 2);
    uint32_t ys3 = (uint32_t) min(maxy, starty + 3);

    float p00 = rsGetElementAt_float(gIn, xs0, ys0);
    float p01 = rsGetElementAt_float(gIn, xs1, ys0);
    float p02 = rsGetElementAt_float(gIn, xs2, ys0);
    float p03 = rsGetElementAt_float(gIn, xs3, ys0);
    float p0  = cubicInterpolate_F1(p00, p01, p02, p03, xf);

    float p10 = rsGetElementAt_float(gIn, xs0, ys1);
    float p11 = rsGetElementAt_float(gIn, xs1, ys1);
    float p12 = rsGetElementAt_float(gIn, xs2, ys1);
    float p13 = rsGetElementAt_float(gIn, xs3, ys1);
    float p1  = cubicInterpolate_F1(p10, p11, p12, p13, xf);

    float p20 = rsGetElementAt_float(gIn, xs0, ys2);
    float p21 = rsGetElementAt_float(gIn, xs1, ys2);
    float p22 = rsGetElementAt_float(gIn, xs2, ys2);
    float p23 = rsGetElementAt_float(gIn, xs3, ys2);
    float p2  = cubicInterpolate_F1(p20, p21, p22, p23, xf);

    float p30 = rsGetElementAt_float(gIn, xs0, ys3);
    float p31 = rsGetElementAt_float(gIn, xs1, ys3);
    float p32 = rsGetElementAt_float(gIn, xs2, ys3);
    float p33 = rsGetElementAt_float(gIn, xs3, ys3);
    float p3  = cubicInterpolate_F1(p30, p31, p32, p33, xf);

    float p  = cubicInterpolate_F1(p0, p1, p2, p3, yf);

    return p;
}

