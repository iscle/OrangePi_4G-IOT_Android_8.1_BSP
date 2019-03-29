/*
 * Copyright 2014 The Android Open Source Project
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

#include "common.rs"

// Must be YUV 420 888 (flexible YUV)
rs_allocation mInput;

// Input globals
uint32_t src_x;
uint32_t src_y;

// Crop each pixel from mInput
yuvx_444 RS_KERNEL crop(uint32_t x, uint32_t y) {

    uchar py = rsGetElementAtYuv_uchar_Y(mInput, x + src_x, y + src_y);
    uchar pu = rsGetElementAtYuv_uchar_U(mInput, x + src_x, y + src_y);
    uchar pv = rsGetElementAtYuv_uchar_V(mInput, x + src_x, y + src_y);

    yuvx_444 yuv = { py, pu, pv };

    return yuv;
}

