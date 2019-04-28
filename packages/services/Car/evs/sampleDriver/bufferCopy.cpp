/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "bufferCopy.h"


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


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

    return ((R & 0xFF))       |
           ((G & 0xFF) << 8)  |
           ((B & 0xFF) << 16) |
           0xFF000000;  // Fill the alpha channel with ones
}


void fillNV21FromNV21(const BufferDesc& tgtBuff, uint8_t* tgt, void* imgData, unsigned) {
    // The NV21 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 interleave U/V array.
    // It assumes an even width and height for the overall image, and a horizontal stride that is
    // an even multiple of 16 bytes for both the Y and UV arrays.

    // Target  and source image layout properties (They match since the formats match!)
    const unsigned strideLum = align<16>(tgtBuff.width);
    const unsigned sizeY = strideLum * tgtBuff.height;
    const unsigned strideColor = strideLum;   // 1/2 the samples, but two interleaved channels
    const unsigned sizeColor = strideColor * tgtBuff.height/2;
    const unsigned totalBytes = sizeY + sizeColor;

    // Simply copy the data byte for byte
    memcpy(tgt, imgData, totalBytes);
}


void fillNV21FromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt, void* imgData, unsigned imgStride) {
    // The YUYV format provides an interleaved array of pixel values with U and V subsampled in
    // the horizontal direction only.  Also known as interleaved 422 format.  A 4 byte
    // "macro pixel" provides the Y value for two adjacent pixels and the U and V values shared
    // between those two pixels.  The width of the image must be an even number.
    // We need to down sample the UV values and collect them together after all the packed Y values
    // to construct the NV21 format.
    // NV21 requires even width and height, so we assume that is the case for the incomming image
    // as well.
    uint32_t *srcDataYUYV = (uint32_t*)imgData;
    struct YUYVpixel {
        uint8_t Y1;
        uint8_t U;
        uint8_t Y2;
        uint8_t V;
    };

    // Target image layout properties
    const unsigned strideLum = align<16>(tgtBuff.width);
    const unsigned sizeY = strideLum * tgtBuff.height;
    const unsigned strideColor = strideLum;   // 1/2 the samples, but two interleaved channels

    // Source image layout properties
    const unsigned srcRowPixels = imgStride/4;  // imgStride is in units of bytes
    const unsigned srcRowDoubleStep = srcRowPixels * 2;
    uint32_t* topSrcRow =  srcDataYUYV;
    uint32_t* botSrcRow =  srcDataYUYV + srcRowPixels;

    // We're going to work on one 2x2 cell in the output image at at time
    for (unsigned cellRow = 0; cellRow < tgtBuff.height/2; cellRow++) {

        // Set up the output pointers
        uint8_t* yTopRow = tgt + (cellRow*2) * strideLum;
        uint8_t* yBotRow = yTopRow + strideLum;
        uint8_t* uvRow   = (tgt + sizeY) + cellRow * strideColor;

        for (unsigned cellCol = 0; cellCol < tgtBuff.width/2; cellCol++) {
            // Collect the values from the YUYV interleaved data
            const YUYVpixel* pTopMacroPixel = (YUYVpixel*)&topSrcRow[cellCol];
            const YUYVpixel* pBotMacroPixel = (YUYVpixel*)&botSrcRow[cellCol];

            // Down sample the U/V values by linear average between rows
            const uint8_t uValue = (pTopMacroPixel->U + pBotMacroPixel->U) >> 1;
            const uint8_t vValue = (pTopMacroPixel->V + pBotMacroPixel->V) >> 1;

            // Store the values into the NV21 layout
            yTopRow[cellCol*2]   = pTopMacroPixel->Y1;
            yTopRow[cellCol*2+1] = pTopMacroPixel->Y2;
            yBotRow[cellCol*2]   = pBotMacroPixel->Y1;
            yBotRow[cellCol*2+1] = pBotMacroPixel->Y2;
            uvRow[cellCol*2]     = uValue;
            uvRow[cellCol*2+1]   = vValue;
        }

        // Skipping two rows to get to the next set of two source rows
        topSrcRow += srcRowDoubleStep;
        botSrcRow += srcRowDoubleStep;
    }
}


void fillRGBAFromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt, void* imgData, unsigned imgStride) {
    unsigned width = tgtBuff.width;
    unsigned height = tgtBuff.height;
    uint32_t* src = (uint32_t*)imgData;
    uint32_t* dst = (uint32_t*)tgt;
    unsigned srcStridePixels = imgStride / 2;
    unsigned dstStridePixels = tgtBuff.stride;

    const int srcRowPadding32 = srcStridePixels/2 - width/2;  // 2 bytes per pixel, 4 bytes per word
    const int dstRowPadding32 = dstStridePixels   - width;    // 4 bytes per pixel, 4 bytes per word

    for (unsigned r=0; r<height; r++) {
        for (unsigned c=0; c<width/2; c++) {
            // Note:  we're walking two pixels at a time here (even/odd)
            uint32_t srcPixel = *src++;

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
        src += srcRowPadding32;
        dst += dstRowPadding32;
    }
}


void fillYUYVFromYUYV(const BufferDesc& tgtBuff, uint8_t* tgt, void* imgData, unsigned imgStride) {
    unsigned width = tgtBuff.width;
    unsigned height = tgtBuff.height;
    uint8_t* src = (uint8_t*)imgData;
    uint8_t* dst = (uint8_t*)tgt;
    unsigned srcStrideBytes = imgStride;
    unsigned dstStrideBytes = tgtBuff.stride * 2;

    for (unsigned r=0; r<height; r++) {
        // Copy a pixel row at a time (2 bytes per pixel, averaged over a YUYV macro pixel)
        memcpy(dst+r*dstStrideBytes, src+r*srcStrideBytes, width*2);
    }
}


void fillYUYVFromUYVY(const BufferDesc& tgtBuff, uint8_t* tgt, void* imgData, unsigned imgStride) {
    unsigned width = tgtBuff.width;
    unsigned height = tgtBuff.height;
    uint32_t* src = (uint32_t*)imgData;
    uint32_t* dst = (uint32_t*)tgt;
    unsigned srcStridePixels = imgStride / 2;
    unsigned dstStridePixels = tgtBuff.stride;

    const int srcRowPadding32 = srcStridePixels/2 - width/2;  // 2 bytes per pixel, 4 bytes per word
    const int dstRowPadding32 = dstStridePixels/2 - width/2;  // 2 bytes per pixel, 4 bytes per word

    for (unsigned r=0; r<height; r++) {
        for (unsigned c=0; c<width/2; c++) {
            // Note:  we're walking two pixels at a time here (even/odd)
            uint32_t srcPixel = *src++;

            uint8_t Y1 = (srcPixel)       & 0xFF;
            uint8_t U  = (srcPixel >> 8)  & 0xFF;
            uint8_t Y2 = (srcPixel >> 16) & 0xFF;
            uint8_t V  = (srcPixel >> 24) & 0xFF;

            // Now we write back the pair of pixels with the components swizzled
            *dst++ = (U)        |
                     (Y1 << 8)  |
                     (V  << 16) |
                     (Y2 << 24);
        }

        // Skip over any extra data or end of row alignment padding
        src += srcRowPadding32;
        dst += dstRowPadding32;
    }
}


} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android
