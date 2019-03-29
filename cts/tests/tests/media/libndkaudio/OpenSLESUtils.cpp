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

#include "OpenSLESUtils.h"

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

/*
 * OSLES Helpers
 */
static const char* errStrings[] = {
        "SL_RESULT_SUCCESS",	// 0)
        "SL_RESULT_PRECONDITIONS_VIOLATE",  // 1
        "SL_RESULT_PARAMETER_INVALID",      // 2
        "SL_RESULT_MEMORY_FAILURE",         // 3
        "SL_RESULT_RESOURCE_ERROR",         // 4
        "SL_RESULT_RESOURCE_LOST",          // 5
        "SL_RESULT_IO_ERROR",               // 6
        "SL_RESULT_BUFFER_INSUFFICIENT",    // 7
        "SL_RESULT_CONTENT_CORRUPTED",      // 8
        "SL_RESULT_CONTENT_UNSUPPORTED",    // 9
        "SL_RESULT_CONTENT_NOT_FOUND",      // 10
        "SL_RESULT_PERMISSION_DENIED",      // 11
        "SL_RESULT_FEATURE_UNSUPPORTED",    // 12
        "SL_RESULT_INTERNAL_ERROR",         // 13
        "SL_RESULT_UNKNOWN_ERROR",          // 14
        "SL_RESULT_OPERATION_ABORTED",      // 15
        "SL_RESULT_CONTROL_LOST"            // 16
};

const char * getSLErrStr(int code) {
    return errStrings[code];
}

// These will wind up in <SLES/OpenSLES_Android.h>
#define SL_ANDROID_SPEAKER_QUAD (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT \
 | SL_SPEAKER_BACK_LEFT | SL_SPEAKER_BACK_RIGHT)

#define SL_ANDROID_SPEAKER_5DOT1 (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT \
 | SL_SPEAKER_FRONT_CENTER  | SL_SPEAKER_LOW_FREQUENCY| SL_SPEAKER_BACK_LEFT \
 | SL_SPEAKER_BACK_RIGHT)

#define SL_ANDROID_SPEAKER_7DOT1 (SL_ANDROID_SPEAKER_5DOT1 | SL_SPEAKER_SIDE_LEFT \
 |SL_SPEAKER_SIDE_RIGHT)

int chanCountToChanMask(int chanCount) {
    int channelMask = 0;

    switch (chanCount) {
        case 1:
            channelMask = SL_SPEAKER_FRONT_CENTER;
            break;

        case 2:
            channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
            break;

        case 4:  // Quad
            channelMask = SL_ANDROID_SPEAKER_QUAD;
            break;

        case 6:  // 5.1
            channelMask = SL_ANDROID_SPEAKER_5DOT1;
            break;

        case 8:  // 7.1
            channelMask = SL_ANDROID_SPEAKER_7DOT1;
            break;
    }
    return channelMask;
}
