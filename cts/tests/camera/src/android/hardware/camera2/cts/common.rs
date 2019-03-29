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

#ifndef ANDROID_HARDWARE_CAMERA2_CTS_COMMON_RS
#define ANDROID_HARDWARE_CAMERA2_CTS_COMMON_RS

#pragma version(1)
#pragma rs java_package_name(android.hardware.camera2.cts)
#pragma rs_fp_relaxed

typedef uchar3 yuvx_444; // interleaved YUV. (y,u,v) per pixel. use .x/.y/.z to read
typedef uchar3 yuvf_420; // flexible YUV (4:2:0). use rsGetElementAtYuv to read.

#define convert_yuvx_444 convert_uchar3
#define convert_yuvf_420 __error_cant_output_flexible_yuv__

#define rsGetElementAt_yuvx_444 rsGetElementAt_uchar3
#define rsGetElementAt_yuvf_420 __error_cant_output_flexible_yuv__

#define RS_KERNEL __attribute__((kernel))

#ifndef LOG_DEBUG
#define LOG_DEBUG 0
#endif

#if LOG_DEBUG
#define LOGD(string, expr) rsDebug((string), (expr))
#else
#define LOGD(string, expr) if (0) rsDebug((string), (expr))
#endif

#endif // header guard
