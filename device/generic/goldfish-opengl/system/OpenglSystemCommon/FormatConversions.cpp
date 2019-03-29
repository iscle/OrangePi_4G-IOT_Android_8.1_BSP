/*
 * Copyright (C) 2016 Google, Inc.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#include "FormatConversions.h"

#include <cutils/log.h>
#include <string.h>

#define DEBUG 0

#if DEBUG
#define DD(...) ALOGD(...)
#else
#define DD(...)
#endif

void get_yv12_offsets(int width, int height,
                             uint32_t* yStride_out,
                             uint32_t* cStride_out,
                             uint32_t* totalSz_out) {
    uint32_t align = 16;
    uint32_t yStride = (width + (align - 1)) & ~(align-1);
    uint32_t uvStride = (yStride / 2 + (align - 1)) & ~(align-1);
    uint32_t uvHeight = height / 2;
    uint32_t sz = yStride * height + 2 * (uvHeight * uvStride);

    if (yStride_out) *yStride_out = yStride;
    if (cStride_out) *cStride_out = uvStride;
    if (totalSz_out) *totalSz_out = sz;
}

void get_yuv420p_offsets(int width, int height,
                                uint32_t* yStride_out,
                                uint32_t* cStride_out,
                                uint32_t* totalSz_out) {
    uint32_t align = 1;
    uint32_t yStride = (width + (align - 1)) & ~(align-1);
    uint32_t uvStride = (yStride / 2 + (align - 1)) & ~(align-1);
    uint32_t uvHeight = height / 2;
    uint32_t sz = yStride * height + 2 * (uvHeight * uvStride);

    if (yStride_out) *yStride_out = yStride;
    if (cStride_out) *cStride_out = uvStride;
    if (totalSz_out) *totalSz_out = sz;
}

signed clamp_rgb(signed value) {
    if (value > 255) {
        value = 255;
    } else if (value < 0) {
        value = 0;
    }
    return value;
}

void rgb565_to_yv12(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    int align = 16;
    int yStride = (width + (align -1)) & ~(align-1);
    int cStride = (yStride / 2 + (align - 1)) & ~(align-1);
    int yOffset = 0;
    int cSize = cStride * height/2;

    uint16_t *rgb_ptr0 = (uint16_t *)src;
    uint8_t *yv12_y0 = (uint8_t *)dest;
    uint8_t *yv12_v0 = yv12_y0 + yStride * height;
    uint8_t *yv12_u0 = yv12_v0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_v = yv12_v0 + (j/2) * cStride;
        uint8_t *yv12_u = yv12_v + cSize;
        uint16_t *rgb_ptr = rgb_ptr0 + j * width;
        bool jeven = (j & 1) == 0;
        for (int i = left; i <= right; ++i) {
            uint8_t r = ((rgb_ptr[i]) >> 11) & 0x01f;
            uint8_t g = ((rgb_ptr[i]) >> 5) & 0x03f;
            uint8_t b = (rgb_ptr[i]) & 0x01f;
            // convert to 8bits
            // http://stackoverflow.com/questions/2442576/how-does-one-convert-16-bit-rgb565-to-24-bit-rgb888
            uint8_t R = (r * 527 + 23) >> 6;
            uint8_t G = (g * 259 + 33) >> 6;
            uint8_t B = (b * 527 + 23) >> 6;
            // convert to YV12
            // frameworks/base/core/jni/android_hardware_camera2_legacy_LegacyCameraDevice.cpp
            yv12_y[i] = clamp_rgb((77 * R + 150 * G +  29 * B) >> 8);
            bool ieven = (i & 1) == 0;
            if (jeven && ieven) {
                yv12_u[i] = clamp_rgb((( -43 * R - 85 * G + 128 * B) >> 8) + 128);
                yv12_v[i] = clamp_rgb((( 128 * R - 107 * G - 21 * B) >> 8) + 128);
            }
        }
    }
}

void rgb888_to_yv12(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    DD("%s convert %d by %d", __func__, width, height);
    int align = 16;
    int yStride = (width + (align -1)) & ~(align-1);
    int cStride = (yStride / 2 + (align - 1)) & ~(align-1);
    int yOffset = 0;
    int cSize = cStride * height/2;
    int rgb_stride = 3;

    uint8_t *rgb_ptr0 = (uint8_t *)src;
    uint8_t *yv12_y0 = (uint8_t *)dest;
    uint8_t *yv12_v0 = yv12_y0 + yStride * height;
    uint8_t *yv12_u0 = yv12_v0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_v = yv12_v0 + (j/2) * cStride;
        uint8_t *yv12_u = yv12_v + cSize;
        uint8_t  *rgb_ptr = rgb_ptr0 + j * width*rgb_stride;
        bool jeven = (j & 1) == 0;
        for (int i = left; i <= right; ++i) {
            uint8_t R = rgb_ptr[i*rgb_stride];
            uint8_t G = rgb_ptr[i*rgb_stride+1];
            uint8_t B = rgb_ptr[i*rgb_stride+2];
            // convert to YV12
            // frameworks/base/core/jni/android_hardware_camera2_legacy_LegacyCameraDevice.cpp
            yv12_y[i] = clamp_rgb((77 * R + 150 * G +  29 * B) >> 8);
            bool ieven = (i & 1) == 0;
            if (jeven && ieven) {
                yv12_u[i] = clamp_rgb((( -43 * R - 85 * G + 128 * B) >> 8) + 128);
                yv12_v[i] = clamp_rgb((( 128 * R - 107 * G - 21 * B) >> 8) + 128);
            }
        }
    }
}

void rgb888_to_yuv420p(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    DD("%s convert %d by %d", __func__, width, height);
    int yStride = width;
    int cStride = yStride / 2;
    int yOffset = 0;
    int cSize = cStride * height/2;
    int rgb_stride = 3;

    uint8_t *rgb_ptr0 = (uint8_t *)src;
    uint8_t *yv12_y0 = (uint8_t *)dest;
    uint8_t *yv12_u0 = yv12_y0 + yStride * height;
    uint8_t *yv12_v0 = yv12_u0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_u = yv12_u0 + (j/2) * cStride;
        uint8_t *yv12_v = yv12_u + cStride;
        uint8_t  *rgb_ptr = rgb_ptr0 + j * width*rgb_stride;
        bool jeven = (j & 1) == 0;
        for (int i = left; i <= right; ++i) {
            uint8_t R = rgb_ptr[i*rgb_stride];
            uint8_t G = rgb_ptr[i*rgb_stride+1];
            uint8_t B = rgb_ptr[i*rgb_stride+2];
            // convert to YV12
            // frameworks/base/core/jni/android_hardware_camera2_legacy_LegacyCameraDevice.cpp
            yv12_y[i] = clamp_rgb((77 * R + 150 * G +  29 * B) >> 8);
            bool ieven = (i & 1) == 0;
            if (jeven && ieven) {
                yv12_u[i] = clamp_rgb((( -43 * R - 85 * G + 128 * B) >> 8) + 128);
                yv12_v[i] = clamp_rgb((( 128 * R - 107 * G - 21 * B) >> 8) + 128);
            }
        }
    }
}
// YV12 is aka YUV420Planar, or YUV420p; the only difference is that YV12 has
// certain stride requirements for Y and UV respectively.
void yv12_to_rgb565(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    DD("%s convert %d by %d", __func__, width, height);
    int align = 16;
    int yStride = (width + (align -1)) & ~(align-1);
    int cStride = (yStride / 2 + (align - 1)) & ~(align-1);
    int yOffset = 0;
    int cSize = cStride * height/2;

    uint16_t *rgb_ptr0 = (uint16_t *)dest;
    uint8_t *yv12_y0 = (uint8_t *)src;
    uint8_t *yv12_v0 = yv12_y0 + yStride * height;
    uint8_t *yv12_u0 = yv12_v0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_v = yv12_v0 + (j/2) * cStride;
        uint8_t *yv12_u = yv12_v + cSize;
        uint16_t *rgb_ptr = rgb_ptr0 + (j-top) * (right-left+1);
        for (int i = left; i <= right; ++i) {
            // convert to rgb
            // frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp
            signed y1 = (signed)yv12_y[i] - 16;
            signed u = (signed)yv12_u[i / 2] - 128;
            signed v = (signed)yv12_v[i / 2] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = clamp_rgb((tmp1 + u_b) / 256);
            signed g1 = clamp_rgb((tmp1 + v_g + u_g) / 256);
            signed r1 = clamp_rgb((tmp1 + v_r) / 256);

            uint16_t rgb1 = ((r1 >> 3) << 11) | ((g1 >> 2) << 5) | (b1 >> 3);

            rgb_ptr[i-left] = rgb1;
        }
    }
}

// YV12 is aka YUV420Planar, or YUV420p; the only difference is that YV12 has
// certain stride requirements for Y and UV respectively.
void yv12_to_rgb888(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    DD("%s convert %d by %d", __func__, width, height);
    int align = 16;
    int yStride = (width + (align -1)) & ~(align-1);
    int cStride = (yStride / 2 + (align - 1)) & ~(align-1);
    int yOffset = 0;
    int cSize = cStride * height/2;
    int rgb_stride = 3;

    uint8_t *rgb_ptr0 = (uint8_t *)dest;
    uint8_t *yv12_y0 = (uint8_t *)src;
    uint8_t *yv12_v0 = yv12_y0 + yStride * height;
    uint8_t *yv12_u0 = yv12_v0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_v = yv12_v0 + (j/2) * cStride;
        uint8_t *yv12_u = yv12_v + cSize;
        uint8_t *rgb_ptr = rgb_ptr0 + (j-top) * (right-left+1) * rgb_stride;
        for (int i = left; i <= right; ++i) {
            // convert to rgb
            // frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp
            signed y1 = (signed)yv12_y[i] - 16;
            signed u = (signed)yv12_u[i / 2] - 128;
            signed v = (signed)yv12_v[i / 2] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = clamp_rgb((tmp1 + u_b) / 256);
            signed g1 = clamp_rgb((tmp1 + v_g + u_g) / 256);
            signed r1 = clamp_rgb((tmp1 + v_r) / 256);

            rgb_ptr[(i-left)*rgb_stride] = r1;
            rgb_ptr[(i-left)*rgb_stride+1] = g1;
            rgb_ptr[(i-left)*rgb_stride+2] = b1;
        }
    }
}

// YV12 is aka YUV420Planar, or YUV420p; the only difference is that YV12 has
// certain stride requirements for Y and UV respectively.
void yuv420p_to_rgb888(char* dest, char* src, int width, int height,
        int left, int top, int right, int bottom) {
    DD("%s convert %d by %d", __func__, width, height);
    int yStride = width;
    int cStride = yStride / 2;
    int yOffset = 0;
    int cSize = cStride * height/2;
    int rgb_stride = 3;

    uint8_t *rgb_ptr0 = (uint8_t *)dest;
    uint8_t *yv12_y0 = (uint8_t *)src;
    uint8_t *yv12_u0 = yv12_y0 + yStride * height;
    uint8_t *yv12_v0 = yv12_u0 + cSize;

    for (int j = top; j <= bottom; ++j) {
        uint8_t *yv12_y = yv12_y0 + j * yStride;
        uint8_t *yv12_u = yv12_u0 + (j/2) * cStride;
        uint8_t *yv12_v = yv12_u + cSize;
        uint8_t *rgb_ptr = rgb_ptr0 + (j-top) * (right-left+1) * rgb_stride;
        for (int i = left; i <= right; ++i) {
            // convert to rgb
            // frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp
            signed y1 = (signed)yv12_y[i] - 16;
            signed u = (signed)yv12_u[i / 2] - 128;
            signed v = (signed)yv12_v[i / 2] - 128;

            signed u_b = u * 517;
            signed u_g = -u * 100;
            signed v_g = -v * 208;
            signed v_r = v * 409;

            signed tmp1 = y1 * 298;
            signed b1 = clamp_rgb((tmp1 + u_b) / 256);
            signed g1 = clamp_rgb((tmp1 + v_g + u_g) / 256);
            signed r1 = clamp_rgb((tmp1 + v_r) / 256);

            rgb_ptr[(i-left)*rgb_stride] = r1;
            rgb_ptr[(i-left)*rgb_stride+1] = g1;
            rgb_ptr[(i-left)*rgb_stride+2] = b1;
        }
    }
}

void copy_rgb_buffer_from_unlocked(
        char* _dst, char* raw_data,
        int unlockedWidth,
        int width, int height, int top, int left,
        int bpp) {
    char* dst = _dst;
    int dst_line_len = width * bpp;
    int src_line_len = unlockedWidth * bpp;
    char *src = (char *)raw_data + top*src_line_len + left*bpp;
    for (int y = 0; y < height; y++) {
        memcpy(dst, src, dst_line_len);
        src += src_line_len;
        dst += dst_line_len;
    }
}
