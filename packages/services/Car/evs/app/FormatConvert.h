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

#ifndef EVS_VTS_FORMATCONVERT_H
#define EVS_VTS_FORMATCONVERT_H

#include <queue>
#include <stdint.h>


// Given an image buffer in NV21 format (HAL_PIXEL_FORMAT_YCRCB_420_SP), output 32bit RGBx values.
// The NV21 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 interleaved
// U/V array.  It assumes an even width and height for the overall image, and a horizontal
// stride that is an even multiple of 16 bytes for both the Y and UV arrays.
void copyNV21toRGB32(unsigned width, unsigned height,
                     uint8_t* src,
                     uint32_t* dst, unsigned dstStridePixels);


// Given an image buffer in YV12 format (HAL_PIXEL_FORMAT_YV12), output 32bit RGBx values.
// The YV12 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 U array, followed
// by another 1/2 x 1/2 V array.  It assumes an even width and height for the overall image,
// and a horizontal stride that is an even multiple of 16 bytes for each of the Y, U,
// and V arrays.
void copyYV12toRGB32(unsigned width, unsigned height,
                     uint8_t* src,
                     uint32_t* dst, unsigned dstStridePixels);


// Given an image buffer in YUYV format (HAL_PIXEL_FORMAT_YCBCR_422_I), output 32bit RGBx values.
// The NV21 format provides a Y array of 8bit values, followed by a 1/2 x 1/2 interleaved
// U/V array.  It assumes an even width and height for the overall image, and a horizontal
// stride that is an even multiple of 16 bytes for both the Y and UV arrays.
void copyYUYVtoRGB32(unsigned width, unsigned height,
                     uint8_t* src, unsigned srcStrideBytes,
                     uint32_t* dst, unsigned dstStrideBytes);


// Given an simple rectangular image buffer with an integer number of bytes per pixel,
// copy the pixel values into a new rectangular buffer (potentially with a different stride).
// This is typically used to copy RGBx data into an RGBx output buffer.
void copyMatchedInterleavedFormats(unsigned width, unsigned height,
                                   void* src, unsigned srcStridePixels,
                                   void* dst, unsigned dstStridePixels,
                                   unsigned pixelSize);

#endif // EVS_VTS_FORMATCONVERT_H
