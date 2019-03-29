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

rs_allocation gAlloc1DIn;
rs_allocation gAlloc1DOut;
rs_allocation gAlloc2DIn;
rs_allocation gAlloc2DOut;
rs_allocation gAlloc3DIn;
rs_allocation gAlloc3DOut;

uint gWidth;
uint gHeight;




#define COPY_1D(ty)                                                 \
    void __attribute__((kernel)) copy1D_##ty(int idx) {             \
        ty i = rsGetElementAt_##ty(gAlloc1DIn, idx);                  \
        rsSetElementAt_##ty(gAlloc1DOut, i, idx);                     \
    }                                                               \
    void __attribute__((kernel)) copy1D_##ty##2(int idx) {          \
        ty##2 i = rsGetElementAt_##ty##2(gAlloc1DIn, idx);            \
        rsSetElementAt_##ty##2(gAlloc1DOut, i, idx);                  \
    }                                                               \
    void __attribute__((kernel)) copy1D_##ty##3(int idx) {          \
        ty##3 i = rsGetElementAt_##ty##3(gAlloc1DIn, idx);            \
        rsSetElementAt_##ty##3(gAlloc1DOut, i, idx);                  \
    }                                                               \
    void __attribute__((kernel)) copy1D_##ty##4(int idx) {          \
        ty##4 i = rsGetElementAt_##ty##4(gAlloc1DIn, idx);            \
        rsSetElementAt_##ty##4(gAlloc1DOut, i, idx);                  \
    }

COPY_1D(char)
COPY_1D(uchar)
COPY_1D(short)
COPY_1D(ushort)
COPY_1D(int)
COPY_1D(uint)
COPY_1D(long)
COPY_1D(ulong)
COPY_1D(float)
COPY_1D(double)



#define COPY_2D(ty)                                                 \
    void __attribute__((kernel)) copy2D_##ty(int idx) {             \
        uint x = idx % gWidth;                                      \
        uint y = idx / gWidth;                                      \
        ty i = rsGetElementAt_##ty(gAlloc2DIn, x, y);                 \
        rsSetElementAt_##ty(gAlloc2DOut, i, x, y);                    \
    }                                                               \
    void __attribute__((kernel)) copy2D_##ty##2(int idx) {          \
        uint realWidth = gWidth / 2;                                \
        uint x = idx % realWidth;                                      \
        uint y = idx / realWidth;                                      \
        ty##2 i = rsGetElementAt_##ty##2(gAlloc2DIn, x, y);           \
        rsSetElementAt_##ty##2(gAlloc2DOut, i, x, y);                 \
    }                                                               \
    void __attribute__((kernel)) copy2D_##ty##3(int idx) {          \
        uint realWidth = gWidth / 4;                                \
        uint x = idx % realWidth;                                      \
        uint y = idx / realWidth;                                      \
        ty##3 i = rsGetElementAt_##ty##3(gAlloc2DIn, x, y);           \
        rsSetElementAt_##ty##3(gAlloc2DOut, i, x, y);                 \
    }                                                               \
    void __attribute__((kernel)) copy2D_##ty##4(int idx) {          \
        uint realWidth = gWidth / 4;                                \
        uint x = idx % realWidth;                                      \
        uint y = idx / realWidth;                                      \
        ty##4 i = rsGetElementAt_##ty##4(gAlloc2DIn, x, y);           \
        rsSetElementAt_##ty##4(gAlloc2DOut, i, x, y);                 \
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



#define COPY_3D(ty)                                                 \
    void __attribute__((kernel)) copy3D_##ty(int idx) {             \
        uint x = idx % gWidth;                                      \
        uint y = (idx / gWidth) % gHeight;                          \
        uint z = idx / (gWidth * gHeight);                          \
        ty i = rsGetElementAt_##ty(gAlloc3DIn, x, y, z);              \
        rsSetElementAt_##ty(gAlloc3DOut, i, x, y, z);                 \
    }                                                               \
    void __attribute__((kernel)) copy3D_##ty##2(int idx) {          \
        uint realWidth = gWidth / 2;                                \
        uint x = idx % realWidth;                                   \
        uint y = (idx / realWidth) % gHeight;                       \
        uint z = idx / (realWidth * gHeight);                       \
        ty##2 i = rsGetElementAt_##ty##2(gAlloc3DIn, x, y, z);        \
        rsSetElementAt_##ty##2(gAlloc3DOut, i, x, y, z);              \
    }                                                               \
    void __attribute__((kernel)) copy3D_##ty##3(int idx) {          \
        uint realWidth = gWidth / 4;                                \
        uint x = idx % realWidth;                                   \
        uint y = (idx / realWidth) % gHeight;                       \
        uint z = idx / (realWidth * gHeight);                       \
        ty##3 i = rsGetElementAt_##ty##3(gAlloc3DIn, x, y, z);        \
        rsSetElementAt_##ty##3(gAlloc3DOut, i, x, y, z);              \
    }                                                               \
    void __attribute__((kernel)) copy3D_##ty##4(int idx) {          \
        uint realWidth = gWidth / 4;                                \
        uint x = idx % realWidth;                                   \
        uint y = (idx / realWidth) % gHeight;                       \
        uint z = idx / (realWidth * gHeight);                       \
        ty##4 i = rsGetElementAt_##ty##4(gAlloc3DIn, x, y, z);        \
        rsSetElementAt_##ty##4(gAlloc3DOut, i, x, y, z);              \
    }

COPY_3D(char)
COPY_3D(uchar)
COPY_3D(short)
COPY_3D(ushort)
COPY_3D(int)
COPY_3D(uint)
COPY_3D(long)
COPY_3D(ulong)
COPY_3D(float)
COPY_3D(double)

