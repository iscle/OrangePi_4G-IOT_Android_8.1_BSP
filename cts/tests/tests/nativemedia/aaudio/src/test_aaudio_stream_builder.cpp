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

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>

// Creates a builder, the caller takes ownership
static void create_stream_builder(AAudioStreamBuilder** aaudioBuilder) {
    aaudio_result_t result = AAudio_createStreamBuilder(aaudioBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_NE(nullptr, *aaudioBuilder);
}

enum class Expect { FAIL, SUCCEED, NOT_CRASH };

// Tries to open an audio stream using a primed Builder.
// Takes ownership of the Builder.
static void try_opening_audio_stream(AAudioStreamBuilder *aaudioBuilder, Expect expect) {
    // Create an AAudioStream using the Builder.
    AAudioStream *aaudioStream = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream);
    if (expect == Expect::FAIL) {
        ASSERT_NE(AAUDIO_OK, result);
        ASSERT_EQ(nullptr, aaudioStream);
    } else if (expect == Expect::SUCCEED) {
        ASSERT_EQ(AAUDIO_OK, result);
        ASSERT_NE(nullptr, aaudioStream);
    } else { // NOT_CRASH
        ASSERT_TRUE(((result < 0) && (aaudioStream == nullptr))
                || ((result == AAUDIO_OK) && (aaudioStream != nullptr)));
    }

    // Cleanup
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    if (aaudioStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }
}

// Test creating a default stream with specific devices
static void runtest_aaudio_devices(int32_t deviceId, Expect expect) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setDeviceId(aaudioBuilder, deviceId);
    try_opening_audio_stream(aaudioBuilder, expect);
}

TEST(test_aaudio, aaudio_stream_device_unspecified) {
    runtest_aaudio_devices(AAUDIO_UNSPECIFIED, Expect::NOT_CRASH);
}

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST(test_aaudio, aaudio_stream_device_absurd) {
    runtest_aaudio_devices(19736459, true);
}
*/
/* FIXME review
TEST(test_aaudio, aaudio_stream_device_reasonable) {
    runtest_aaudio_devices(1, false);
}
*/

/* FIXME - why can we open this device? What is an illegal deviceId?
TEST(test_aaudio, aaudio_stream_device_negative) {
    runtest_aaudio_devices(-765, true);
}
*/

// Test creating a default stream with everything unspecified.
TEST(test_aaudio, aaudio_stream_unspecified) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);

    // Create an AAudioStream using the Builder.
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
    ASSERT_NE(nullptr, aaudioStream);

    // Cleanup
    EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(aaudioBuilder));
    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
}

class AAudioStreamBuilderSamplingRateTest : public ::testing::TestWithParam<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidSamplingRate(int32_t sr) {
        return sr == AAUDIO_UNSPECIFIED || (sr >= 8000 && sr <= 1000000);
    }
};

TEST_P(AAudioStreamBuilderSamplingRateTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setSampleRate(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidSamplingRate(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SR, AAudioStreamBuilderSamplingRateTest,
        ::testing::Values(
                // Commonly used values
                AAUDIO_UNSPECIFIED, 8000, 11025, 16000, 22050, 44100, 48000, 88200, 96000,
                176400, 192000, 384000,
                // Odd values
                AAUDIO_UNSPECIFIED - 1, AAUDIO_UNSPECIFIED + 1, 1234, 10000000),
        &AAudioStreamBuilderSamplingRateTest::getTestName);

class AAudioStreamBuilderChannelCountTest : public ::testing::TestWithParam<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidChannelCount(int32_t cc) {
        return cc == AAUDIO_UNSPECIFIED || (cc >= 1 && cc <= 8);
    }
};

TEST_P(AAudioStreamBuilderChannelCountTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setChannelCount(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidChannelCount(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(CC, AAudioStreamBuilderChannelCountTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_UNSPECIFIED, 1, 2, 3, 4, 5, 6, 7, 8,
                // Odd values
                AAUDIO_UNSPECIFIED - 1, 9, 100, 1000000, 10000000),
        &AAudioStreamBuilderChannelCountTest::getTestName);

class AAudioStreamBuilderFormatTest : public ::testing::TestWithParam<aaudio_format_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_format_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidFormat(aaudio_format_t f) {
        switch (f) {
            case AAUDIO_FORMAT_UNSPECIFIED:
            case AAUDIO_FORMAT_PCM_I16:
            case AAUDIO_FORMAT_PCM_FLOAT:
                return true;
        }
        return false;
    }
};

TEST_P(AAudioStreamBuilderFormatTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setFormat(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidFormat(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(F, AAudioStreamBuilderFormatTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_FORMAT_UNSPECIFIED, AAUDIO_FORMAT_PCM_I16, AAUDIO_FORMAT_PCM_FLOAT,
                // Odd values
                AAUDIO_FORMAT_INVALID, AAUDIO_FORMAT_INVALID - 1, 100, 1000000, 10000000),
        &AAudioStreamBuilderFormatTest::getTestName);

class AAudioStreamBuilderSharingModeTest : public ::testing::TestWithParam<aaudio_sharing_mode_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_sharing_mode_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidSharingMode(aaudio_sharing_mode_t f) {
        return f == AAUDIO_SHARING_MODE_SHARED || f == AAUDIO_SHARING_MODE_EXCLUSIVE;
    }
};

TEST_P(AAudioStreamBuilderSharingModeTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setFormat(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidSharingMode(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SM, AAudioStreamBuilderSharingModeTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_SHARING_MODE_SHARED, AAUDIO_SHARING_MODE_EXCLUSIVE,
                // Odd values
                -1, 100, 1000000, 10000000),
        &AAudioStreamBuilderSharingModeTest::getTestName);

class AAudioStreamBuilderDirectionTest : public ::testing::TestWithParam<aaudio_direction_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_direction_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidSharingMode(aaudio_direction_t f) {
        return f == AAUDIO_DIRECTION_OUTPUT || f == AAUDIO_DIRECTION_INPUT;
    }
};

TEST_P(AAudioStreamBuilderDirectionTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setFormat(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidSharingMode(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(SD, AAudioStreamBuilderDirectionTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_DIRECTION_OUTPUT, AAUDIO_DIRECTION_INPUT,
                // Odd values
                -1, 100, 1000000, 10000000),
        &AAudioStreamBuilderDirectionTest::getTestName);

class AAudioStreamBuilderBufferCapacityTest : public ::testing::TestWithParam<int32_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<int32_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    // There is no hard defined limit, the actual maximum capacity depends
    // on the implementation.
    static bool isValidCapacity(int32_t bc) {
        return bc == AAUDIO_UNSPECIFIED || bc >= 0;
    }
};

TEST_P(AAudioStreamBuilderBufferCapacityTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setBufferCapacityInFrames(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidCapacity(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(BC, AAudioStreamBuilderBufferCapacityTest,
        ::testing::Values(
                // Reasonable values that should not fail
                AAUDIO_UNSPECIFIED, 8 * 192, 2 * 1024,
                // Odd values
                AAUDIO_UNSPECIFIED - 1),
        &AAudioStreamBuilderBufferCapacityTest::getTestName);

class AAudioStreamBuilderPerfModeTest : public ::testing::TestWithParam<aaudio_performance_mode_t> {
  public:
    static std::string getTestName(const ::testing::TestParamInfo<aaudio_performance_mode_t>& info) {
        return info.param >= 0 ? std::to_string(info.param) : "_" + std::to_string(-info.param);
    }
  protected:
    static bool isValidPerfMode(aaudio_performance_mode_t pm) {
        switch (pm) {
            case AAUDIO_PERFORMANCE_MODE_NONE:
            case AAUDIO_PERFORMANCE_MODE_POWER_SAVING:
            case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY:
                return true;
        }
        return false;
    }
};

TEST_P(AAudioStreamBuilderPerfModeTest, openStream) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    create_stream_builder(&aaudioBuilder);
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, GetParam());
    try_opening_audio_stream(
            aaudioBuilder, isValidPerfMode(GetParam()) ? Expect::SUCCEED : Expect::FAIL);
}

INSTANTIATE_TEST_CASE_P(PM, AAudioStreamBuilderPerfModeTest,
        ::testing::Values(
                // Reasonable values
                AAUDIO_PERFORMANCE_MODE_NONE,
                AAUDIO_PERFORMANCE_MODE_POWER_SAVING,
                AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
                // Odd values
                AAUDIO_UNSPECIFIED - 1, AAUDIO_UNSPECIFIED, 100, 1000000, 10000000),
        &AAudioStreamBuilderPerfModeTest::getTestName);
