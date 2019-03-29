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

#pragma version(1)
#pragma rs java_package_name(android.renderscript.cts)

#pragma rs_fp_relaxed

rs_allocation gAllocIn;
rs_allocation gAllocOut;



#define COPY_2D(ty)                                                 \
    void __attribute__((kernel)) copy2d_##ty(int xy_v) {            \
        int lx = xy_v & 0xff;                                       \
        int vecsize = (xy_v & 0xff0000) >> 16;                      \
        switch(vecsize) {                                           \
        case 1: {                                                   \
                ty i = rsGetElementAt_##ty(gAllocIn, lx);           \
                rsSetElementAt_##ty(gAllocOut, i, lx);              \
            } break;                                                \
        case 2: {                                                   \
                ty##2 i = rsAllocationVLoadX_##ty##2(gAllocIn, lx); \
                rsAllocationVStoreX_##ty##2(gAllocOut, i, lx);      \
            } break;                                                \
        case 3: {                                                   \
                ty##3 i = rsAllocationVLoadX_##ty##3(gAllocIn, lx); \
                rsAllocationVStoreX_##ty##3(gAllocOut, i, lx);      \
            } break;                                                \
        case 4: {                                                   \
                ty##4 i = rsAllocationVLoadX_##ty##4(gAllocIn, lx); \
                rsAllocationVStoreX_##ty##4(gAllocOut, i, lx);      \
            } break;                                                \
        }                                                           \
    }

COPY_2D(char)
COPY_2D(uchar)
COPY_2D(short)
COPY_2D(ushort)
COPY_2D(int)
COPY_2D(uint)
COPY_2D(long)
COPY_2D(ulong)

COPY_2D(float)
COPY_2D(double)

