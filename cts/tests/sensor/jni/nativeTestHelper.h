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
 *
 */

#include <jni.h>

#include <android/log.h>
#define TAG "SensorNativeTest"
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define ASSERT(condition, format, args...) \
        if (!(condition)) { \
            fail(env, format, ## args); \
            return; \
        }

// gtest style assert
#define ASSERT_TRUE(a) ASSERT((a), "assert failed on (" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_FALSE(a) ASSERT(!(a), "assert failed on (!" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_EQ(a, b) \
        ASSERT((a) == (b), "assert failed on (" #a " == " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_NE(a, b) \
        ASSERT((a) != (b), "assert failed on (" #a " != " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_GT(a, b) \
        ASSERT((a) > (b), "assert failed on (" #a " > " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_GE(a, b) \
        ASSERT((a) >= (b), "assert failed on (" #a " >= " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_LT(a, b) \
        ASSERT((a) < (b), "assert failed on (" #a " < " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_LE(a, b) \
        ASSERT((a) <= (b), "assert failed on (" #a " <= " #b ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_NULL(a) \
        ASSERT((a) == nullptr, "assert failed on isNull(" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_NOT_NULL(a) \
        ASSERT((a) != nullptr, "assert failed on isNotNull(" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_NAN(a) \
        ASSERT(isnan(a), "assert failed on isNan(" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_EMPTY_CSTR(a) do { \
        const char *tmp = a; \
        ASSERT(tmp != nullptr, \
               "assert failed on (empty_cstr(" #a "): " #a " != nullptr) " \
               "at " __FILE__ ":%d", __LINE__); \
        ASSERT(tmp[0] == '\0', \
               "assert failed on (empty_cstr(" #a "): strlen() == 0) " \
               "at " __FILE__ ":%d", __LINE__); \
    } while (false)


void fail(JNIEnv* env, const char* format, ...);
