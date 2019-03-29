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

static rs_allocation gCube;
static int4 gDims;
static float4 gCoordMul;

void setCube(rs_allocation c) {
    gCube = c;
    gDims.x = rsAllocationGetDimX(gCube);
    gDims.y = rsAllocationGetDimY(gCube);
    gDims.z = rsAllocationGetDimZ(gCube);
    gDims.w = 0;
    gCoordMul = (float4)(1.f / 255.f) * convert_float4(gDims - 1);
}

uchar4 __attribute__((kernel)) root(uchar4 in) {
    float4 baseCoord = convert_float4(in) * gCoordMul;
    int4 coord1 = convert_int4(floor(baseCoord));
    int4 coord2 = min(coord1 + 1, gDims - 1);
    float4 f = baseCoord - convert_float4(coord1);

    float4 v000 = convert_float4(rsGetElementAt_uchar4(gCube, coord1.x, coord1.y, coord1.z));
    float4 v100 = convert_float4(rsGetElementAt_uchar4(gCube, coord2.x, coord1.y, coord1.z));
    float4 v010 = convert_float4(rsGetElementAt_uchar4(gCube, coord1.x, coord2.y, coord1.z));
    float4 v110 = convert_float4(rsGetElementAt_uchar4(gCube, coord2.x, coord2.y, coord1.z));
    float4 v001 = convert_float4(rsGetElementAt_uchar4(gCube, coord1.x, coord1.y, coord2.z));
    float4 v101 = convert_float4(rsGetElementAt_uchar4(gCube, coord2.x, coord1.y, coord2.z));
    float4 v011 = convert_float4(rsGetElementAt_uchar4(gCube, coord1.x, coord2.y, coord2.z));
    float4 v111 = convert_float4(rsGetElementAt_uchar4(gCube, coord2.x, coord2.y, coord2.z));

    float4 yz00 = mix(v000, v100, f.x);
    float4 yz10 = mix(v010, v110, f.x);
    float4 yz01 = mix(v001, v101, f.x);
    float4 yz11 = mix(v011, v111, f.x);

    float4 z0 = mix(yz00, yz10, f.y);
    float4 z1 = mix(yz01, yz11, f.y);

    float4 v = mix(z0, z1, f.z);

    v = clamp(v, 0.f, 255.f);
    uchar4 o = convert_uchar4(v + 0.5f);
    o.w = in.w;
    return o;
}

