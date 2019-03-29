/*
 * Copyright 2017 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <dlfcn.h>
#include <unistd.h>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>
#include <sys/system_properties.h>

#include "test_aaudio.h"
#include "utils.h"

/* These definitions are from aaudio/AAudioTesting.h */
enum {
    AAUDIO_POLICY_NEVER = 1,
    AAUDIO_POLICY_AUTO,
    AAUDIO_POLICY_ALWAYS
};
typedef int32_t aaudio_policy_t;

static aaudio_result_t (*s_setMMapPolicy)(aaudio_policy_t policy) = nullptr;
static aaudio_policy_t (*s_getMMapPolicy)() = nullptr;

/**
 * @return integer value or -1 on error
 */
static int getSystemPropertyInt(const char *propName, int defaultValue) {
    char valueText[PROP_VALUE_MAX] = {'\0'};
    if (__system_property_get(propName, valueText) <= 0) {
        return defaultValue;
    }
    char *endptr = nullptr;
    int value = strtol(valueText, &endptr, 10);
    if (endptr == nullptr || *endptr != '\0') {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                "getSystemPropertyInt() - non-integer value = %s", valueText);
        return -1;
    } else {
        return value;
    }
}

static int getSystemMMapPolicy() {
    return getSystemPropertyInt("aaudio.mmap_policy", AAUDIO_UNSPECIFIED);
}

// Test allowed values of policy.
TEST(test_aaudio_mmap, testCurrentPolicy) {
    aaudio_policy_t policy = getSystemMMapPolicy();

    // It must be one of these defined enum values.
    EXPECT_TRUE(policy == AAUDIO_UNSPECIFIED
        || policy == AAUDIO_POLICY_NEVER
        || policy == AAUDIO_POLICY_AUTO
        || policy == AAUDIO_POLICY_ALWAYS);
    // But some defined values are not allowed in some versions of the SDK.

    // AAUDIO_POLICY_ALWAYS is only for testing during development.
    // It forces MMAP mode for all streams, which will fail for some stream settings.
    EXPECT_NE(AAUDIO_POLICY_ALWAYS, policy);
}

// Link to test functions in shared library.
static void loadMMapTestFunctions() {
    if (s_setMMapPolicy != nullptr) return; // already loaded

    void *handle;
    handle = dlopen("libaaudio.so", RTLD_NOW);
    EXPECT_NE(nullptr, handle);
    s_setMMapPolicy = (int (*)(int)) dlsym(handle, "AAudio_setMMapPolicy");
    EXPECT_NE(nullptr, s_setMMapPolicy);
    s_getMMapPolicy = (int (*)()) dlsym(handle, "AAudio_getMMapPolicy");
    EXPECT_NE(nullptr, s_getMMapPolicy);
}

// An application should not be able to create an MMAP stream
// by enabling MMAP when the system "aaudio.mmap_policy" says not to.
TEST(test_aaudio_mmap, testElevatingMMapPolicy) {
    aaudio_result_t result = AAUDIO_OK;
    AAudioStreamBuilder *builder = nullptr;
    AAudioStream *stream = nullptr;

    aaudio_policy_t policy = getSystemMMapPolicy();
    bool mmapAllowed = (policy == AAUDIO_POLICY_AUTO || policy == AAUDIO_POLICY_ALWAYS);
    if (mmapAllowed) return;
    // Try to enable MMAP when not allowed.

    loadMMapTestFunctions();

    EXPECT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&builder));

    // LOW_LATENCY required for MMAP
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);

    // Force policy to create an MMAP stream or fail.
    aaudio_policy_t originalPolicy = s_getMMapPolicy();
    s_setMMapPolicy(AAUDIO_POLICY_ALWAYS); // try to enable MMAP mode
    result = AAudioStreamBuilder_openStream(builder, &stream);
    s_setMMapPolicy(originalPolicy);

    // openStream should have failed.
    EXPECT_NE(AAUDIO_OK, result);

    // These should not crash if NULL.
    AAudioStream_close(stream);
    AAudioStreamBuilder_delete(builder);
}
