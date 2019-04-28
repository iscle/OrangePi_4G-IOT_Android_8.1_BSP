/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "VtsHalEvsTest"

#include "FormatConvert.h"


// Round up to the nearest multiple of the given alignment value
template<unsigned alignment>
int align(int value) {
    static_assert((alignment && !(alignment & (alignment - 1))),
                  "alignment must be a power of 2");

    unsigned mask = alignment - 1;
    return (value + mask) & ~mask;
}


// Limit the given value to the provided range.  :)
static inline float clamp(float v, float min, float max) {
    if (v < min) return min;
    if (v > max) return max;
    return v;
}


static uint32_t yuvToRgbx(const unsigned char Y, const unsigned char Uin, const unsigned char Vin) {
    // Don't use this if you want to see the best performance.  :)
    // Better to do this in a pixel shader if we really have to, but on actual
    // embedded hardware we expect to be able to texture directly from the YUV data
    float U = Uin - 128.0f;
    float V = Vin - 128.0f;

    float Rf = Y + 1.140f*V;
    float Gf = Y - 0.395f*U - 0.581f*V;
    float Bf = Y + 2.032f*U;
    unsigned char R = (unsigned char)clamp(Rf, 0.0f, 255.0f);
    unsigned char G = (unsigned char)clamp(Gf, 0.0f, 255.0f);
    unsigned char B = (unsigned char)clamp(Bf, 0.0f, 255.0f);

    return (R      ) |
           (G <<  8) |
           (B << 16) |
           0xFF000000;  // Fill the alpha channel with ones
}


void copyNV21toRGB32(unsigned width, unsigned height,
                     uint8_t* src,
                     uint32_t* dst, unsigned dstStridePixels)
{
    // The NV21 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 interleaved
    // U/V array.  It assumes an even width and height for the overall image, and a horizontal
    // stride that is an even multiple of 16 bytes for both the Y and UV arrays.
    unsigned strideLum = align<16>(width);
    unsigned sizeY = strideLum * height;
    unsigned strideColor = strideLum;   // 1/2 the samples, but two interleaved channels
    unsigned offsetUV = sizeY;

    uint8_t* srcY = src;
    uint8_t* srcUV = src+offsetUV;

    for (unsigned r = 0; r < height; r++) {
        // Note that we're walking the same UV row twice for even/odd luminance rows
        uint8_t* rowY  = srcY  + r*strideLum;
        uint8_t* rowUV = srcUV + (r/2 * strideColor);

        uint32_t* rowDest = dst + r*dstStridePixels;

        for (unsigned c = 0; c < width; c++) {
            unsigned uCol = (c & ~1);   // uCol is always even and repeats 1:2 with Y values
            unsigned vCol = uCol | 1;   // vCol is always odd
            rowDest[c] = yuvToRgbx(rowY[c], rowUV[uCol], rowUV[vCol]);
        }
    }
}


void copyYV12toRGB32(unsigned width, unsigned height,
                     uint8_t* src,
                     uint32_t* dst, unsigned dstStridePixels)
{
    // The YV12 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 U array, followed
    // by another 1/2 x 1/2 V array.  It assumes an even width and height for the overall image,
    // and a horizontal stride that is an even multiple of 16 bytes for each of the Y, U,
    // and V arrays.
    unsigned strideLum = align<16>(width);
    unsigned sizeY = strideLum * height;
    unsigned strideColor = align<16>(strideLum/2);
    unsigned sizeColor = strideColor * height/2;
    unsigned offsetU = sizeY;
    unsigned offsetV = sizeY + sizeColor;

    uint8_t* srcY = src;
    uint8_t* srcU = src+offsetU;
    uint8_t* srcV = src+offsetV;

    for (unsigned r = 0; r < height; r++) {
        // Note that we're walking the same U and V rows twice for even/odd luminance rows
        uint8_t* rowY = srcY + r*strideLum;
        uint8_t* rowU = srcU + (r/2 * strideColor);
        uint8_t* rowV = srcV + (r/2 * strideColor);

        uint32_t* rowDest = dst + r*dstStridePixels;

        for (unsigned c = 0; c < width; c++) {
            rowDest[c] = yuvToRgbx(rowY[c], rowU[c], rowV[c]);
        }
    }
}


void copyYUYVtoRGB32(unsigned width, unsigned height,
                     uint8_t* src, unsigned srcStridePixels,
                     uint32_t* dst, unsigned dstStridePixels)
{
    uint32_t* srcWords = (uint32_t*)src;

    const int srcRowPadding32 = srcStridePixels/2 - width/2;  // 2 bytes per pixel, 4 bytes per word
    const int dstRowPadding32 = dstStridePixels   - width;    // 4 bytes per pixel, 4 bytes per word

    for (unsigned r = 0; r < height; r++) {
        for (unsigned c = 0; c < width/2; c++) {
            // Note:  we're walking two pixels at a time here (even/odd)
            uint32_t srcPixel = *srcWords++;

            uint8_t Y1 = (srcPixel)       & 0xFF;
            uint8_t U  = (srcPixel >> 8)  & 0xFF;
            uint8_t Y2 = (srcPixel >> 16) & 0xFF;
            uint8_t V  = (srcPixel >> 24) & 0xFF;

            // On the RGB output, we're writing one pixel at a time
            *(dst+0) = yuvToRgbx(Y1, U, V);
            *(dst+1) = yuvToRgbx(Y2, U, V);
            dst += 2;
        }

        // Skip over any extra data or end of row alignment padding
        srcWords += srcRowPadding32;
        dst += dstRowPadding32;
    }
}


void copyMatchedInterleavedFormats(unsigned width, unsigned height,
                                   void* src, unsigned srcStridePixels,
                                   void* dst, unsigned dstStridePixels,
                                   unsigned pixelSize) {
    for (unsigned row = 0; row < height; row++) {
        // Copy the entire row of pixel data
        memcpy(dst, src, width * pixelSize);

        // Advance to the next row (keeping in mind that stride here is in units of pixels)
        src = (uint8_t*)src + srcStridePixels * pixelSize;
        dst = (uint8_t*)dst + dstStridePixels * pixelSize;
    }
}
