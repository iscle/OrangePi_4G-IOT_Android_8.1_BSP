/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License", ENUM_CANNOT_CHANGE);
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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>

// Make sure enums do not change value.
TEST(test_aaudio_misc, aaudio_freeze_enums) {

#define ENUM_CANNOT_CHANGE "enum in API cannot change"

    static_assert(0 == AAUDIO_DIRECTION_OUTPUT, ENUM_CANNOT_CHANGE);
    static_assert(1 == AAUDIO_DIRECTION_INPUT, ENUM_CANNOT_CHANGE);

    static_assert(-1 == AAUDIO_FORMAT_INVALID, ENUM_CANNOT_CHANGE);
    static_assert(0 == AAUDIO_FORMAT_UNSPECIFIED, ENUM_CANNOT_CHANGE);
    static_assert(1 == AAUDIO_FORMAT_PCM_I16, ENUM_CANNOT_CHANGE);
    static_assert(2 == AAUDIO_FORMAT_PCM_FLOAT, ENUM_CANNOT_CHANGE);

    static_assert(0 == AAUDIO_OK, ENUM_CANNOT_CHANGE);
    static_assert(-900 == AAUDIO_ERROR_BASE, ENUM_CANNOT_CHANGE);
    static_assert(-899 == AAUDIO_ERROR_DISCONNECTED, ENUM_CANNOT_CHANGE);
    static_assert(-898 == AAUDIO_ERROR_ILLEGAL_ARGUMENT, ENUM_CANNOT_CHANGE);
    // reserved
    static_assert(-896 == AAUDIO_ERROR_INTERNAL, ENUM_CANNOT_CHANGE);
    static_assert(-895 == AAUDIO_ERROR_INVALID_STATE, ENUM_CANNOT_CHANGE);
    // reserved
    // reserved
    static_assert(-892 == AAUDIO_ERROR_INVALID_HANDLE, ENUM_CANNOT_CHANGE);
    // reserved
    static_assert(-890 == AAUDIO_ERROR_UNIMPLEMENTED, ENUM_CANNOT_CHANGE);
    static_assert(-889 == AAUDIO_ERROR_UNAVAILABLE, ENUM_CANNOT_CHANGE);
    static_assert(-888 == AAUDIO_ERROR_NO_FREE_HANDLES, ENUM_CANNOT_CHANGE);
    static_assert(-887 == AAUDIO_ERROR_NO_MEMORY, ENUM_CANNOT_CHANGE);
    static_assert(-886 == AAUDIO_ERROR_NULL, ENUM_CANNOT_CHANGE);
    static_assert(-885 == AAUDIO_ERROR_TIMEOUT, ENUM_CANNOT_CHANGE);
    static_assert(-884 == AAUDIO_ERROR_WOULD_BLOCK, ENUM_CANNOT_CHANGE);
    static_assert(-883 == AAUDIO_ERROR_INVALID_FORMAT, ENUM_CANNOT_CHANGE);
    static_assert(-882 == AAUDIO_ERROR_OUT_OF_RANGE, ENUM_CANNOT_CHANGE);
    static_assert(-881 == AAUDIO_ERROR_NO_SERVICE, ENUM_CANNOT_CHANGE);

    static_assert(0 == AAUDIO_STREAM_STATE_UNINITIALIZED, ENUM_CANNOT_CHANGE);
    static_assert(1 == AAUDIO_STREAM_STATE_UNKNOWN, ENUM_CANNOT_CHANGE);
    static_assert(2 == AAUDIO_STREAM_STATE_OPEN, ENUM_CANNOT_CHANGE);
    static_assert(3 == AAUDIO_STREAM_STATE_STARTING, ENUM_CANNOT_CHANGE);
    static_assert(4 == AAUDIO_STREAM_STATE_STARTED, ENUM_CANNOT_CHANGE);
    static_assert(5 == AAUDIO_STREAM_STATE_PAUSING, ENUM_CANNOT_CHANGE);
    static_assert(6 == AAUDIO_STREAM_STATE_PAUSED, ENUM_CANNOT_CHANGE);
    static_assert(7 == AAUDIO_STREAM_STATE_FLUSHING, ENUM_CANNOT_CHANGE);
    static_assert(8 == AAUDIO_STREAM_STATE_FLUSHED, ENUM_CANNOT_CHANGE);
    static_assert(9 == AAUDIO_STREAM_STATE_STOPPING, ENUM_CANNOT_CHANGE);
    static_assert(10 == AAUDIO_STREAM_STATE_STOPPED, ENUM_CANNOT_CHANGE);
    static_assert(11 == AAUDIO_STREAM_STATE_CLOSING, ENUM_CANNOT_CHANGE);
    static_assert(12 == AAUDIO_STREAM_STATE_CLOSED, ENUM_CANNOT_CHANGE);

    static_assert(0 == AAUDIO_SHARING_MODE_EXCLUSIVE, ENUM_CANNOT_CHANGE);
    static_assert(1 == AAUDIO_SHARING_MODE_SHARED, ENUM_CANNOT_CHANGE);

    static_assert(0 == AAUDIO_CALLBACK_RESULT_CONTINUE, ENUM_CANNOT_CHANGE);
    static_assert(1 == AAUDIO_CALLBACK_RESULT_STOP, ENUM_CANNOT_CHANGE);
}
