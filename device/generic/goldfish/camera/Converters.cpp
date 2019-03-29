/*
 * Copyright (C) 2011 The Android Open Source Project
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

/*
 * Contains implemenation of framebuffer conversion routines.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCamera_Converter"
#include <cutils/log.h>
#include "Converters.h"

#include "Alignment.h"

namespace android {

static void _YUV420SToRGB565(const uint8_t* Y,
                             const uint8_t* U,
                             const uint8_t* V,
                             int dUV,
                             uint16_t* rgb,
                             int width,
                             int height,
                             int y_stride,
                             int uv_stride)
{
    const uint8_t* Y_pos = Y;
    const uint8_t* U_pos = U;
    const uint8_t* V_pos = V;

    for (int y = 0; y < height; y++) {
        Y = Y_pos + y_stride * y;
        U = U_pos + uv_stride * (y / 2);
        V = V_pos + uv_stride * (y / 2);
        for (int x = 0; x < width; x += 2, U += dUV, V += dUV) {
            const uint8_t nU = *U;
            const uint8_t nV = *V;
            *rgb = YUVToRGB565(*Y, nU, nV);
            Y++; rgb++;
            *rgb = YUVToRGB565(*Y, nU, nV);
            Y++; rgb++;
        }
    }
}

static void _YUV420SToRGB32(const uint8_t* Y,
                            const uint8_t* U,
                            const uint8_t* V,
                            int dUV,
                            uint32_t* rgb,
                            int width,
                            int height,
                            int y_stride,
                            int uv_stride)
{
    const uint8_t* Y_pos = Y;
    const uint8_t* U_pos = U;
    const uint8_t* V_pos = V;

    for (int y = 0; y < height; y++) {
        Y = Y_pos + y_stride * y;
        U = U_pos + uv_stride * (y / 2);
        V = V_pos + uv_stride * (y / 2);
        for (int x = 0; x < width; x += 2, U += dUV, V += dUV) {
            const uint8_t nU = *U;
            const uint8_t nV = *V;
            *rgb = YUVToRGB32(*Y, nU, nV);
            Y++; rgb++;
            *rgb = YUVToRGB32(*Y, nU, nV);
            Y++; rgb++;
        }
    }
}

/* The YV12 and YU12 formats require that the row strides are aligned to 16 byte
 * boundaries as per the format specification at:
 * https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
 *
 * This means that we can't just use the width or assume that pixels are
 * tightly packed, we have to calculate aligned strides and use them to find the
 * next row.
 */
void YV12ToRGB565(const void* yv12, void* rgb, int width, int height)
{
    // See note above about alignment
    const int y_stride = align(width, 16);
    const int uv_stride = align(y_stride / 2, 16);
    const uint8_t* Y = reinterpret_cast<const uint8_t*>(yv12);
    const uint8_t* U = Y + y_stride * height;
    const uint8_t* V = U + uv_stride * (height / 2);
    _YUV420SToRGB565(Y, U, V, 1, reinterpret_cast<uint16_t*>(rgb),
                     width, height, y_stride, uv_stride);
}

void YV12ToRGB32(const void* yv12, void* rgb, int width, int height)
{
    // See note above about alignment
    const int y_stride = align(width, 16);
    const int uv_stride = align(y_stride / 2, 16);
    const uint8_t* Y = reinterpret_cast<const uint8_t*>(yv12);
    const uint8_t* V = Y + y_stride * height;
    const uint8_t* U = V + uv_stride * (height / 2);
    _YUV420SToRGB32(Y, U, V, 1, reinterpret_cast<uint32_t*>(rgb), width, height,
                    y_stride, uv_stride);
}

void YU12ToRGB32(const void* yu12, void* rgb, int width, int height)
{
    // See note above about alignment
    const int y_stride = align(width, 16);
    const int uv_stride = align(y_stride / 2, 16);
    const uint8_t* Y = reinterpret_cast<const uint8_t*>(yu12);
    const uint8_t* U = Y + y_stride * height;
    const uint8_t* V = U + uv_stride * (height / 2);
    _YUV420SToRGB32(Y, U, V, 1, reinterpret_cast<uint32_t*>(rgb), width, height,
                    y_stride, uv_stride);
}

/* Common converter for YUV 4:2:0 interleaved to RGB565.
 * y, u, and v point to Y,U, and V panes, where U and V values are interleaved.
 */
static void _NVXXToRGB565(const uint8_t* Y,
                          const uint8_t* U,
                          const uint8_t* V,
                          uint16_t* rgb,
                          int width,
                          int height)
{
    // The UV stride for NV21 and NV12 is the same as the width because the
    // U and V values are interleaved, making each row twice as wide even though
    // each value covers a two pixel wide area. These formats do not require any
    // kind of alignment.
    int y_stride = width;
    int uv_stride = width;
    _YUV420SToRGB565(Y, U, V, 2, rgb, width, height, y_stride, uv_stride);
}

/* Common converter for YUV 4:2:0 interleaved to RGB32.
 * y, u, and v point to Y,U, and V panes, where U and V values are interleaved.
 */
static void _NVXXToRGB32(const uint8_t* Y,
                         const uint8_t* U,
                         const uint8_t* V,
                         uint32_t* rgb,
                         int width,
                         int height)
{
    // The UV stride for NV21 and NV12 is the same as the width because the
    // U and V values are interleaved, making each row twice as wide even though
    // each value covers a two pixel wide area. These formats do not require any
    // kind of alignment.
    int y_stride = width;
    int uv_stride = width;
    _YUV420SToRGB32(Y, U, V, 2, rgb, width, height, y_stride, uv_stride);
}

void NV12ToRGB565(const void* nv12, void* rgb, int width, int height)
{
    const int pix_total = width * height;
    const uint8_t* y = reinterpret_cast<const uint8_t*>(nv12);
    _NVXXToRGB565(y, y + pix_total, y + pix_total + 1,
                  reinterpret_cast<uint16_t*>(rgb), width, height);
}

void NV12ToRGB32(const void* nv12, void* rgb, int width, int height)
{
    const int pix_total = width * height;
    const uint8_t* y = reinterpret_cast<const uint8_t*>(nv12);
    _NVXXToRGB32(y, y + pix_total, y + pix_total + 1,
                 reinterpret_cast<uint32_t*>(rgb), width, height);
}

void NV21ToRGB565(const void* nv21, void* rgb, int width, int height)
{
    const int pix_total = width * height;
    const uint8_t* y = reinterpret_cast<const uint8_t*>(nv21);
    _NVXXToRGB565(y, y + pix_total + 1, y + pix_total,
                  reinterpret_cast<uint16_t*>(rgb), width, height);
}

void NV21ToRGB32(const void* nv21, void* rgb, int width, int height)
{
    const int pix_total = width * height;
    const uint8_t* y = reinterpret_cast<const uint8_t*>(nv21);
    _NVXXToRGB32(y, y + pix_total + 1, y + pix_total,
                 reinterpret_cast<uint32_t*>(rgb), width, height);
}

}; /* namespace android */
