/*
 * Copyright 2016 The Android Open Source Project
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

#include <memory>
#include <tuple>

#include <unistd.h>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>

#include "test_aaudio.h"
#include "utils.h"

using StreamTestParams = std::tuple<aaudio_sharing_mode_t, aaudio_performance_mode_t>;
enum {
    PARAM_SHARING_MODE = 0,
    PARAM_PERF_MODE
};

static std::string getTestName(const ::testing::TestParamInfo<StreamTestParams>& info) {
    return std::string() + sharingModeToString(std::get<PARAM_SHARING_MODE>(info.param)) +
            "__" + performanceModeToString(std::get<PARAM_PERF_MODE>(info.param));
}

template<typename T>
class AAudioStreamTest : public ::testing::TestWithParam<StreamTestParams> {
  protected:
    AAudioStreamBuilder* builder() const { return mHelper->builder(); }
    AAudioStream* stream() const { return mHelper->stream(); }
    const StreamBuilderHelper::Parameters& actual() const { return mHelper->actual(); }
    int32_t framesPerBurst() const { return mHelper->framesPerBurst(); }

    std::unique_ptr<T> mHelper;
    bool mSetupSuccesful = false;
    std::unique_ptr<int16_t[]> mData;
};


class AAudioInputStreamTest : public AAudioStreamTest<InputStreamBuilderHelper> {
  protected:
    void SetUp() override;

    int32_t mFramesPerRead;
};

void AAudioInputStreamTest::SetUp() {
    mHelper.reset(new InputStreamBuilderHelper(
                    std::get<PARAM_SHARING_MODE>(GetParam()),
                    std::get<PARAM_PERF_MODE>(GetParam())));
    mHelper->initBuilder();
    mSetupSuccesful = false;
    mHelper->createAndVerifyStream(&mSetupSuccesful);
    if (!mSetupSuccesful) return;

    mFramesPerRead = framesPerBurst();
    const int32_t framesPerMsec = actual().sampleRate / MILLIS_PER_SECOND;
    // Some DMA might use very short bursts of 16 frames. We don't need to read such small
    // buffers. But it helps to use a multiple of the burst size for predictable scheduling.
    while (mFramesPerRead < framesPerMsec) {
        mFramesPerRead *= 2;
    }
    mData.reset(new int16_t[mFramesPerRead * actual().channelCount]);
}

TEST_P(AAudioInputStreamTest, testReading) {
    if (!mSetupSuccesful) return;

    const int32_t framesToRecord = actual().sampleRate;  // 1 second
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    mHelper->startStream();
    // See b/62090113. For legacy path, the device is only known after
    // the stream has been started.
    ASSERT_NE(AAUDIO_UNSPECIFIED, AAudioStream_getDeviceId(stream()));
    for (int32_t framesLeft = framesToRecord; framesLeft > 0; ) {
        aaudio_result_t result = AAudioStream_read(
                stream(), &mData[0], std::min(framesToRecord, mFramesPerRead),
                DEFAULT_READ_TIMEOUT);
        ASSERT_GT(result, 0);
        framesLeft -= result;
    }
    mHelper->stopStream();
    EXPECT_GE(AAudioStream_getFramesRead(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getFramesWritten(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getXRunCount(stream()), 0);
}

TEST_P(AAudioInputStreamTest, testStartReadStop) {
    if (!mSetupSuccesful) return;

    // Use 1/8 second as start-stops take a lot more time than just recording.
    const int32_t framesToRecord = actual().sampleRate / 8;
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    for (int32_t framesLeft = framesToRecord; framesLeft > 0; ) {
        mHelper->startStream();
        aaudio_result_t result = AAudioStream_read(
                stream(), &mData[0], std::min(framesToRecord, mFramesPerRead),
                DEFAULT_READ_TIMEOUT);
        ASSERT_GT(result, 0);
        framesLeft -= result;
        mHelper->stopStream();
    }
    EXPECT_GE(AAudioStream_getFramesRead(stream()), framesToRecord);
    EXPECT_GE(AAudioStream_getFramesWritten(stream()), framesToRecord);
}

TEST_P(AAudioInputStreamTest, testReadCounterFreezeAfterStop) {
    if (!mSetupSuccesful) return;

    const int32_t framesToRecord = actual().sampleRate / 10;  // 1/10 second
    EXPECT_EQ(0, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(0, AAudioStream_getFramesWritten(stream()));
    mHelper->startStream();
    for (int32_t framesLeft = framesToRecord; framesLeft > 0; ) {
        aaudio_result_t result = AAudioStream_read(
                stream(), &mData[0], std::min(framesToRecord, mFramesPerRead),
                DEFAULT_READ_TIMEOUT);
        ASSERT_GT(result, 0);
        framesLeft -= result;
    }
    mHelper->stopStream();
    const int32_t framesReadAtStop = AAudioStream_getFramesRead(stream());
    const int32_t framesWrittenAtStop = AAudioStream_getFramesWritten(stream());
    ASSERT_EQ(0, TEMP_FAILURE_RETRY(usleep(100 * MICROS_PER_MILLISECOND)));
    EXPECT_EQ(framesReadAtStop, AAudioStream_getFramesRead(stream()));
    EXPECT_EQ(framesWrittenAtStop, AAudioStream_getFramesWritten(stream()));
}

TEST_P(AAudioInputStreamTest, testPauseAndFlushNotSupported) {
    if (!mSetupSuccesful) return;
    mHelper->startStream();
    EXPECT_EQ(AAUDIO_ERROR_UNIMPLEMENTED, AAudioStream_requestPause(stream()));
    EXPECT_EQ(AAUDIO_ERROR_UNIMPLEMENTED, AAudioStream_requestFlush(stream()));
    mHelper->stopStream();
}

INSTANTIATE_TEST_CASE_P(SPM, AAudioInputStreamTest,
        ::testing::Values(
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_NONE),
                // Recording in POWER_SAVING mode isn't supported, b/62291775.
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY),
                std::make_tuple(AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY)),
        &getTestName);


class AAudioOutputStreamTest : public AAudioStreamTest<OutputStreamBuilderHelper> {
  protected:
    void SetUp() override;
};

void AAudioOutputStreamTest::SetUp() {
    mHelper.reset(new OutputStreamBuilderHelper(
                    std::get<PARAM_SHARING_MODE>(GetParam()),
                    std::get<PARAM_PERF_MODE>(GetParam())));
    mHelper->initBuilder();

    mSetupSuccesful = false;
    mHelper->createAndVerifyStream(&mSetupSuccesful);
    if (!mSetupSuccesful) return;

    // Allocate a buffer for the audio data.
    // TODO handle possibility of other data formats
    size_t dataSizeSamples = framesPerBurst() * actual().channelCount;
    mData.reset(new int16_t[dataSizeSamples]);
    memset(&mData[0], 0, dataSizeSamples);
}

TEST_P(AAudioOutputStreamTest, testWriting) {
    if (!mSetupSuccesful) return;

    // Prime the buffer.
    int32_t framesWritten = 0;
    int64_t framesTotal = 0;
    int64_t timeoutNanos = 0;
    do {
        framesWritten = AAudioStream_write(
                stream(), &mData[0], framesPerBurst(), timeoutNanos);
        // There should be some room for priming the buffer.
        framesTotal += framesWritten;
        ASSERT_GE(framesWritten, 0);
        ASSERT_LE(framesWritten, framesPerBurst());
    } while (framesWritten > 0);
    ASSERT_TRUE(framesTotal > 0);

    int writeLoops = 0;
    int64_t aaudioFramesRead = 0;
    int64_t aaudioFramesReadPrev = 0;
    int64_t aaudioFramesReadFinal = 0;
    int64_t aaudioFramesWritten = 0;
    // Start/write/pause more than once to see if it fails after the first time.
    // Write some data and measure the rate to see if the timing is OK.
    for (int numLoops = 0; numLoops < 2; numLoops++) {
        mHelper->startStream();
        // See b/62090113. For legacy path, the device is only known after
        // the stream has been started.
        ASSERT_NE(AAUDIO_UNSPECIFIED, AAudioStream_getDeviceId(stream()));

        // Write some data while we are running. Read counter should be advancing.
        writeLoops = 1 * actual().sampleRate / framesPerBurst(); // 1 second
        ASSERT_LT(2, writeLoops); // detect absurdly high framesPerBurst
        timeoutNanos = 100 * (NANOS_PER_SECOND * framesPerBurst() /
                actual().sampleRate); // N bursts
        framesWritten = 1;
        aaudioFramesRead = AAudioStream_getFramesRead(stream());
        aaudioFramesReadPrev = aaudioFramesRead;
        int64_t beginTime = getNanoseconds(CLOCK_MONOTONIC);
        do {
            framesWritten = AAudioStream_write(
                    stream(), &mData[0], framesPerBurst(), timeoutNanos);
            EXPECT_EQ(framesPerBurst(), framesWritten);

            framesTotal += framesWritten;
            aaudioFramesWritten = AAudioStream_getFramesWritten(stream());
            EXPECT_EQ(framesTotal, aaudioFramesWritten);

            // Try to get a more accurate measure of the sample rate.
            if (beginTime == 0) {
                aaudioFramesRead = AAudioStream_getFramesRead(stream());
                if (aaudioFramesRead > aaudioFramesReadPrev) { // is read pointer advancing
                    beginTime = getNanoseconds(CLOCK_MONOTONIC);
                    aaudioFramesReadPrev = aaudioFramesRead;
                }
            }
        } while (framesWritten > 0 && writeLoops-- > 0);

        aaudioFramesReadFinal = AAudioStream_getFramesRead(stream());
        ASSERT_GT(aaudioFramesReadFinal, 0);
        EXPECT_GT(aaudioFramesReadFinal, aaudioFramesReadPrev);


        // TODO why is AudioTrack path so inaccurate?
        /* See b/38268547, there is no way to specify that MMAP mode needs to be used,
           even EXCLUSIVE mode may fall back to legacy
        const int64_t endTime = getNanoseconds(CLOCK_MONOTONIC);
        const double rateTolerance = 200.0; // arbitrary tolerance for sample rate
        if (std::get<PARAM_SHARING_MODE>(GetParam()) != AAUDIO_SHARING_MODE_SHARED) {
            // Calculate approximate sample rate and compare with stream rate.
            double seconds = (endTime - beginTime) / (double) NANOS_PER_SECOND;
            double measuredRate = (aaudioFramesReadFinal - aaudioFramesReadPrev) / seconds;
            ASSERT_NEAR(actual().sampleRate, measuredRate, rateTolerance);
        }
        */

        mHelper->pauseStream();
    }
    EXPECT_GE(AAudioStream_getXRunCount(stream()), 0);

    // Make sure the read counter is not advancing when we are paused.
    aaudioFramesRead = AAudioStream_getFramesRead(stream());
    ASSERT_GE(aaudioFramesRead, aaudioFramesReadFinal); // monotonic increase
    // Currently not possible to enforce for AAudio over AudioTrack (b/33354715).
    // ASSERT_EQ(0, TEMP_FAILURE_RETRY(usleep(100 * MICROS_PER_MILLISECOND)));
    // EXPECT_EQ(aaudioFramesRead, AAudioStream_getFramesRead(stream()));

    // ------------------- TEST FLUSH -----------------
    // Prime the buffer.
    timeoutNanos = 0;
    writeLoops = 1000;
    do {
        framesWritten = AAudioStream_write(
                stream(), &mData[0], framesPerBurst(), timeoutNanos);
        framesTotal += framesWritten;
    } while (framesWritten > 0 && writeLoops-- > 0);
    EXPECT_EQ(0, framesWritten);

    mHelper->flushStream();

    // After a flush, the read counter should be caught up with the write counter.
    aaudioFramesWritten = AAudioStream_getFramesWritten(stream());
    EXPECT_EQ(framesTotal, aaudioFramesWritten);
    aaudioFramesRead = AAudioStream_getFramesRead(stream());
    EXPECT_EQ(aaudioFramesWritten, aaudioFramesRead);

    sleep(1); // FIXME - The write returns 0 if we remove this sleep! Why?

    // The buffer should be empty after a flush so we should be able to write.
    framesWritten = AAudioStream_write(stream(), &mData[0], framesPerBurst(), timeoutNanos);
    // There should be some room for priming the buffer.
    ASSERT_GT(framesWritten, 0);
    ASSERT_LE(framesWritten, framesPerBurst());
}

// Note that the test for EXCLUSIVE sharing mode may fail gracefully if
// this mode isn't supported by the platform.
INSTANTIATE_TEST_CASE_P(SPM, AAudioOutputStreamTest,
        ::testing::Values(
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(AAUDIO_SHARING_MODE_SHARED, AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                // No test for SHARED / LOW_LATENCY, see b/62101041
                std::make_tuple(AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_NONE),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_POWER_SAVING),
                std::make_tuple(
                        AAUDIO_SHARING_MODE_EXCLUSIVE, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY)),
        &getTestName);


int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);

    return RUN_ALL_TESTS();
}
