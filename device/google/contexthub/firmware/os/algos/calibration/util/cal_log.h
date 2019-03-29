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

///////////////////////////////////////////////////////////////
/*
 * Logging macros for printing formatted strings to Nanohub.
 */
///////////////////////////////////////////////////////////////
#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_UTIL_CAL_LOG_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_UTIL_CAL_LOG_H_

#ifdef GCC_DEBUG_LOG
# include <stdio.h>
# define CAL_DEBUG_LOG(tag, fmt, ...) \
   printf("%s " fmt "\n", tag, ##__VA_ARGS__);
#elif _OS_BUILD_
# include <seos.h>
# ifndef LOG_FUNC
#  define LOG_FUNC(level, fmt, ...) osLog(level, fmt, ##__VA_ARGS__)
# endif  // LOG_FUNC
# define LOGD_TAG(tag, fmt, ...) \
   LOG_FUNC(LOG_DEBUG, "%s " fmt "\n", tag, ##__VA_ARGS__)
# define CAL_DEBUG_LOG(tag, fmt, ...) \
   osLog(LOG_DEBUG, "%s " fmt, tag, ##__VA_ARGS__);
#else  // _OS_BUILD_
# include <chre.h>
# define CAL_DEBUG_LOG(tag, fmt, ...) \
   chreLog(CHRE_LOG_INFO, "%s " fmt, tag, ##__VA_ARGS__)
#endif  // GCC_DEBUG_LOG

#ifdef __cplusplus
extern "C" {
#endif

// Using this macro because floorf() is not currently implemented by the Nanohub
// firmware.
#define CAL_FLOOR(x) ((int)(x) - ((x) < (int)(x)))  // NOLINT

// Macro used to print floating point numbers with a specified number of digits.
#define CAL_ENCODE_FLOAT(x, num_digits) \
  ((x < 0) ? "-" : ""),                 \
  (int)CAL_FLOOR(fabsf(x)), (int)((fabsf(x) - CAL_FLOOR(fabsf(x))) * powf(10, num_digits))  // NOLINT

// Helper definitions for CAL_ENCODE_FLOAT to specify the print format with
// desired significant digits.
#define CAL_FORMAT_3DIGITS "%s%d.%03d"
#define CAL_FORMAT_6DIGITS "%s%d.%06d"
#define CAL_FORMAT_3DIGITS_TRIPLET "%s%d.%03d, %s%d.%03d, %s%d.%03d"
#define CAL_FORMAT_6DIGITS_TRIPLET "%s%d.%06d, %s%d.%06d, %s%d.%06d"

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_UTIL_CAL_LOG_H_
