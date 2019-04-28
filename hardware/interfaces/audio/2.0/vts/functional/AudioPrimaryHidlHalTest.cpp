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

#define LOG_TAG "VtsHalAudioV2_0TargetTest"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdio>
#include <limits>
#include <string>
#include <vector>

#include <VtsHalHidlTargetTestBase.h>

#include <android-base/logging.h>

#include <android/hardware/audio/2.0/IDevice.h>
#include <android/hardware/audio/2.0/IDevicesFactory.h>
#include <android/hardware/audio/2.0/IPrimaryDevice.h>
#include <android/hardware/audio/2.0/types.h>
#include <android/hardware/audio/common/2.0/types.h>

#include "utility/AssertOk.h"
#include "utility/Documentation.h"
#include "utility/EnvironmentTearDown.h"
#include "utility/PrettyPrintAudioTypes.h"
#include "utility/ReturnIn.h"

using std::string;
using std::to_string;
using std::vector;

using ::android::sp;
using ::android::hardware::Return;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::MQDescriptorSync;
using ::android::hardware::audio::V2_0::AudioDrain;
using ::android::hardware::audio::V2_0::DeviceAddress;
using ::android::hardware::audio::V2_0::IDevice;
using ::android::hardware::audio::V2_0::IPrimaryDevice;
using TtyMode = ::android::hardware::audio::V2_0::IPrimaryDevice::TtyMode;
using ::android::hardware::audio::V2_0::IDevicesFactory;
using ::android::hardware::audio::V2_0::IStream;
using ::android::hardware::audio::V2_0::IStreamIn;
using ::android::hardware::audio::V2_0::TimeSpec;
using ReadParameters = ::android::hardware::audio::V2_0::IStreamIn::ReadParameters;
using ReadStatus = ::android::hardware::audio::V2_0::IStreamIn::ReadStatus;
using ::android::hardware::audio::V2_0::IStreamOut;
using ::android::hardware::audio::V2_0::IStreamOutCallback;
using ::android::hardware::audio::V2_0::MmapBufferInfo;
using ::android::hardware::audio::V2_0::MmapPosition;
using ::android::hardware::audio::V2_0::ParameterValue;
using ::android::hardware::audio::V2_0::Result;
using ::android::hardware::audio::common::V2_0::AudioChannelMask;
using ::android::hardware::audio::common::V2_0::AudioConfig;
using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::audio::common::V2_0::AudioFormat;
using ::android::hardware::audio::common::V2_0::AudioHandleConsts;
using ::android::hardware::audio::common::V2_0::AudioInputFlag;
using ::android::hardware::audio::common::V2_0::AudioIoHandle;
using ::android::hardware::audio::common::V2_0::AudioMode;
using ::android::hardware::audio::common::V2_0::AudioOffloadInfo;
using ::android::hardware::audio::common::V2_0::AudioOutputFlag;
using ::android::hardware::audio::common::V2_0::AudioSource;
using ::android::hardware::audio::common::V2_0::ThreadInfo;

using namespace ::android::hardware::audio::common::test::utility;

// Instance to register global tearDown
static Environment* environment;

class HidlTest : public ::testing::VtsHalHidlTargetTestBase {
   protected:
    // Convenient member to store results
    Result res;
};

//////////////////////////////////////////////////////////////////////////////
////////////////////// getService audio_devices_factory //////////////////////
//////////////////////////////////////////////////////////////////////////////

// Test all audio devices
class AudioHidlTest : public HidlTest {
   public:
    void SetUp() override {
        ASSERT_NO_FATAL_FAILURE(HidlTest::SetUp());  // setup base

        if (devicesFactory == nullptr) {
            environment->registerTearDown([] { devicesFactory.clear(); });
            devicesFactory = ::testing::VtsHalHidlTargetTestBase::getService<
                IDevicesFactory>();
        }
        ASSERT_TRUE(devicesFactory != nullptr);
    }

   protected:
    // Cache the devicesFactory retrieval to speed up each test by ~0.5s
    static sp<IDevicesFactory> devicesFactory;
};
sp<IDevicesFactory> AudioHidlTest::devicesFactory;

TEST_F(AudioHidlTest, GetAudioDevicesFactoryService) {
    doc::test("test the getService (called in SetUp)");
}

TEST_F(AudioHidlTest, OpenDeviceInvalidParameter) {
    doc::test("test passing an invalid parameter to openDevice");
    IDevicesFactory::Result result;
    sp<IDevice> device;
    ASSERT_OK(devicesFactory->openDevice(IDevicesFactory::Device(-1),
                                         returnIn(result, device)));
    ASSERT_EQ(IDevicesFactory::Result::INVALID_ARGUMENTS, result);
    ASSERT_TRUE(device == nullptr);
}

//////////////////////////////////////////////////////////////////////////////
/////////////////////////////// openDevice primary ///////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Test the primary device
class AudioPrimaryHidlTest : public AudioHidlTest {
   public:
    /** Primary HAL test are NOT thread safe. */
    void SetUp() override {
        ASSERT_NO_FATAL_FAILURE(AudioHidlTest::SetUp());  // setup base

        if (device == nullptr) {
            IDevicesFactory::Result result;
            sp<IDevice> baseDevice;
            ASSERT_OK(
                devicesFactory->openDevice(IDevicesFactory::Device::PRIMARY,
                                           returnIn(result, baseDevice)));
            ASSERT_OK(result);
            ASSERT_TRUE(baseDevice != nullptr);

            environment->registerTearDown([] { device.clear(); });
            device = IPrimaryDevice::castFrom(baseDevice);
            ASSERT_TRUE(device != nullptr);
        }
    }

   protected:
    // Cache the device opening to speed up each test by ~0.5s
    static sp<IPrimaryDevice> device;
};
sp<IPrimaryDevice> AudioPrimaryHidlTest::device;

TEST_F(AudioPrimaryHidlTest, OpenPrimaryDevice) {
    doc::test("Test the openDevice (called in SetUp)");
}

TEST_F(AudioPrimaryHidlTest, Init) {
    doc::test("Test that the audio primary hal initialized correctly");
    ASSERT_OK(device->initCheck());
}

//////////////////////////////////////////////////////////////////////////////
///////////////////// {set,get}{Master,Mic}{Mute,Volume} /////////////////////
//////////////////////////////////////////////////////////////////////////////

template <class Property>
class AccessorPrimaryHidlTest : public AudioPrimaryHidlTest {
   protected:
    /** Test a property getter and setter. */
    template <class Getter, class Setter>
    void testAccessors(const string& propertyName,
                       const vector<Property>& valuesToTest, Setter setter,
                       Getter getter,
                       const vector<Property>& invalidValues = {}) {
        Property initialValue;  // Save initial value to restore it at the end
                                // of the test
        ASSERT_OK((device.get()->*getter)(returnIn(res, initialValue)));
        ASSERT_OK(res);

        for (Property setValue : valuesToTest) {
            SCOPED_TRACE("Test " + propertyName + " getter and setter for " +
                         testing::PrintToString(setValue));
            ASSERT_OK((device.get()->*setter)(setValue));
            Property getValue;
            // Make sure the getter returns the same value just set
            ASSERT_OK((device.get()->*getter)(returnIn(res, getValue)));
            ASSERT_OK(res);
            EXPECT_EQ(setValue, getValue);
        }

        for (Property invalidValue : invalidValues) {
            SCOPED_TRACE("Try to set " + propertyName +
                         " with the invalid value " +
                         testing::PrintToString(invalidValue));
            EXPECT_RESULT(Result::INVALID_ARGUMENTS,
                          (device.get()->*setter)(invalidValue));
        }

        ASSERT_OK(
            (device.get()->*setter)(initialValue));  // restore initial value
    }

    /** Test the getter and setter of an optional feature. */
    template <class Getter, class Setter>
    void testOptionalAccessors(const string& propertyName,
                               const vector<Property>& valuesToTest,
                               Setter setter, Getter getter,
                               const vector<Property>& invalidValues = {}) {
        doc::test("Test the optional " + propertyName + " getters and setter");
        {
            SCOPED_TRACE("Test feature support by calling the getter");
            Property initialValue;
            ASSERT_OK((device.get()->*getter)(returnIn(res, initialValue)));
            if (res == Result::NOT_SUPPORTED) {
                doc::partialTest(propertyName + " getter is not supported");
                return;
            }
            ASSERT_OK(res);  // If it is supported it must succeed
        }
        // The feature is supported, test it
        testAccessors(propertyName, valuesToTest, setter, getter,
                      invalidValues);
    }
};

using BoolAccessorPrimaryHidlTest = AccessorPrimaryHidlTest<bool>;

TEST_F(BoolAccessorPrimaryHidlTest, MicMuteTest) {
    doc::test("Check that the mic can be muted and unmuted");
    testAccessors("mic mute", {true, false, true}, &IDevice::setMicMute,
                  &IDevice::getMicMute);
    // TODO: check that the mic is really muted (all sample are 0)
}

TEST_F(BoolAccessorPrimaryHidlTest, MasterMuteTest) {
    doc::test(
        "If master mute is supported, try to mute and unmute the master "
        "output");
    testOptionalAccessors("master mute", {true, false, true},
                          &IDevice::setMasterMute, &IDevice::getMasterMute);
    // TODO: check that the master volume is really muted
}

using FloatAccessorPrimaryHidlTest = AccessorPrimaryHidlTest<float>;
TEST_F(FloatAccessorPrimaryHidlTest, MasterVolumeTest) {
    doc::test("Test the master volume if supported");
    testOptionalAccessors("master volume", {0, 0.5, 1},
                          &IDevice::setMasterVolume, &IDevice::getMasterVolume,
                          {-0.1, 1.1, NAN, INFINITY, -INFINITY,
                           1 + std::numeric_limits<float>::epsilon()});
    // TODO: check that the master volume is really changed
}

//////////////////////////////////////////////////////////////////////////////
//////////////////////////////// AudioPatches ////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

class AudioPatchPrimaryHidlTest : public AudioPrimaryHidlTest {
   protected:
    bool areAudioPatchesSupported() {
        auto result = device->supportsAudioPatches();
        EXPECT_IS_OK(result);
        return result;
    }
};

TEST_F(AudioPatchPrimaryHidlTest, AudioPatches) {
    doc::test("Test if audio patches are supported");
    if (!areAudioPatchesSupported()) {
        doc::partialTest("Audio patches are not supported");
        return;
    }
    // TODO: test audio patches
}

//////////////////////////////////////////////////////////////////////////////
//////////////// Required and recommended audio format support ///////////////
// From:
// https://source.android.com/compatibility/android-cdd.html#5_4_audio_recording
// From:
// https://source.android.com/compatibility/android-cdd.html#5_5_audio_playback
/////////// TODO: move to the beginning of the file for easier update ////////
//////////////////////////////////////////////////////////////////////////////

class AudioConfigPrimaryTest : public AudioPatchPrimaryHidlTest {
   public:
    // Cache result ?
    static const vector<AudioConfig> getRequiredSupportPlaybackAudioConfig() {
        return combineAudioConfig(
            {AudioChannelMask::OUT_STEREO, AudioChannelMask::OUT_MONO},
            {8000, 11025, 16000, 22050, 32000, 44100},
            {AudioFormat::PCM_16_BIT});
    }

    static const vector<AudioConfig>
    getRecommendedSupportPlaybackAudioConfig() {
        return combineAudioConfig(
            {AudioChannelMask::OUT_STEREO, AudioChannelMask::OUT_MONO},
            {24000, 48000}, {AudioFormat::PCM_16_BIT});
    }

    static const vector<AudioConfig> getSupportedPlaybackAudioConfig() {
        // TODO: retrieve audio config supported by the platform
        // as declared in the policy configuration
        return {};
    }

    static const vector<AudioConfig> getRequiredSupportCaptureAudioConfig() {
        return combineAudioConfig({AudioChannelMask::IN_MONO},
                                  {8000, 11025, 16000, 44100},
                                  {AudioFormat::PCM_16_BIT});
    }
    static const vector<AudioConfig> getRecommendedSupportCaptureAudioConfig() {
        return combineAudioConfig({AudioChannelMask::IN_STEREO}, {22050, 48000},
                                  {AudioFormat::PCM_16_BIT});
    }
    static const vector<AudioConfig> getSupportedCaptureAudioConfig() {
        // TODO: retrieve audio config supported by the platform
        // as declared in the policy configuration
        return {};
    }

   private:
    static const vector<AudioConfig> combineAudioConfig(
        vector<AudioChannelMask> channelMasks, vector<uint32_t> sampleRates,
        vector<AudioFormat> formats) {
        vector<AudioConfig> configs;
        for (auto channelMask : channelMasks) {
            for (auto sampleRate : sampleRates) {
                for (auto format : formats) {
                    AudioConfig config{};
                    // leave offloadInfo to 0
                    config.channelMask = channelMask;
                    config.sampleRateHz = sampleRate;
                    config.format = format;
                    // FIXME: leave frameCount to 0 ?
                    configs.push_back(config);
                }
            }
        }
        return configs;
    }
};

/** Generate a test name based on an audio config.
 *
 * As the only parameter changing are channel mask and sample rate,
 * only print those ones in the test name.
 */
static string generateTestName(
    const testing::TestParamInfo<AudioConfig>& info) {
    const AudioConfig& config = info.param;
    return to_string(info.index) + "__" + to_string(config.sampleRateHz) + "_" +
           // "MONO" is more clear than "FRONT_LEFT"
           ((config.channelMask == AudioChannelMask::OUT_MONO ||
             config.channelMask == AudioChannelMask::IN_MONO)
                ? "MONO"
                : toString(config.channelMask));
}

//////////////////////////////////////////////////////////////////////////////
///////////////////////////// getInputBufferSize /////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// FIXME: execute input test only if platform declares
// android.hardware.microphone
//        how to get this value ? is it a property ???

class AudioCaptureConfigPrimaryTest
    : public AudioConfigPrimaryTest,
      public ::testing::WithParamInterface<AudioConfig> {
   protected:
    void inputBufferSizeTest(const AudioConfig& audioConfig,
                             bool supportRequired) {
        uint64_t bufferSize;
        ASSERT_OK(
            device->getInputBufferSize(audioConfig, returnIn(res, bufferSize)));

        switch (res) {
            case Result::INVALID_ARGUMENTS:
                EXPECT_FALSE(supportRequired);
                break;
            case Result::OK:
                // Check that the buffer is of a sane size
                // For now only that it is > 0
                EXPECT_GT(bufferSize, uint64_t(0));
                break;
            default:
                FAIL() << "Invalid return status: "
                       << ::testing::PrintToString(res);
        }
    }
};

// Test that the required capture config and those declared in the policy are
// indeed supported
class RequiredInputBufferSizeTest : public AudioCaptureConfigPrimaryTest {};
TEST_P(RequiredInputBufferSizeTest, RequiredInputBufferSizeTest) {
    doc::test(
        "Input buffer size must be retrievable for a format with required "
        "support.");
    inputBufferSizeTest(GetParam(), true);
}
INSTANTIATE_TEST_CASE_P(
    RequiredInputBufferSize, RequiredInputBufferSizeTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRequiredSupportCaptureAudioConfig()),
    &generateTestName);
INSTANTIATE_TEST_CASE_P(
    SupportedInputBufferSize, RequiredInputBufferSizeTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getSupportedCaptureAudioConfig()),
    &generateTestName);

// Test that the recommended capture config are supported or lead to a
// INVALID_ARGUMENTS return
class OptionalInputBufferSizeTest : public AudioCaptureConfigPrimaryTest {};
TEST_P(OptionalInputBufferSizeTest, OptionalInputBufferSizeTest) {
    doc::test(
        "Input buffer size should be retrievable for a format with recommended "
        "support.");
    inputBufferSizeTest(GetParam(), false);
}
INSTANTIATE_TEST_CASE_P(
    RecommendedCaptureAudioConfigSupport, OptionalInputBufferSizeTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRecommendedSupportCaptureAudioConfig()),
    &generateTestName);

//////////////////////////////////////////////////////////////////////////////
/////////////////////////////// setScreenState ///////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_F(AudioPrimaryHidlTest, setScreenState) {
    doc::test("Check that the hal can receive the screen state");
    for (bool turnedOn : {false, true, true, false, false}) {
        auto ret = device->setScreenState(turnedOn);
        ASSERT_IS_OK(ret);
        Result result = ret;
        auto okOrNotSupported = {Result::OK, Result::NOT_SUPPORTED};
        ASSERT_RESULT(okOrNotSupported, result);
    }
}

//////////////////////////////////////////////////////////////////////////////
//////////////////////////// {get,set}Parameters /////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_F(AudioPrimaryHidlTest, getParameters) {
    doc::test("Check that the hal can set and get parameters");
    hidl_vec<hidl_string> keys;
    hidl_vec<ParameterValue> values;
    ASSERT_OK(device->getParameters(keys, returnIn(res, values)));
    ASSERT_OK(device->setParameters(values));
    values.resize(0);
    ASSERT_OK(device->setParameters(values));
}

//////////////////////////////////////////////////////////////////////////////
//////////////////////////////// debugDebug //////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

template <class DebugDump>
static void testDebugDump(DebugDump debugDump) {
    // Dump in a temporary file
    // Note that SELinux must be deactivate for this test to work
    FILE* file = tmpfile();
    ASSERT_NE(nullptr, file) << errno;

    // Wrap the temporary file file descriptor in a native handle
    auto* nativeHandle = native_handle_create(1, 0);
    ASSERT_NE(nullptr, nativeHandle);
    nativeHandle->data[0] = fileno(file);

    // Wrap this native handle in a hidl handle
    hidl_handle handle;
    handle.setTo(nativeHandle, true /*take ownership*/);

    ASSERT_OK(debugDump(handle));

    // Check that at least one bit was written by the hal
    // TODO: debugDump does not return a Result.
    // This mean that the hal can not report that it not implementing the
    // function.
    rewind(file);  // can not fail
    char buff;
    if (fread(&buff, sizeof(buff), 1, file) != 1) {
        doc::note("debugDump does not seem implemented");
    }
    EXPECT_EQ(0, fclose(file)) << errno;
}

TEST_F(AudioPrimaryHidlTest, DebugDump) {
    doc::test("Check that the hal can dump its state without error");
    testDebugDump([](const auto& handle) { return device->debugDump(handle); });
}

TEST_F(AudioPrimaryHidlTest, DebugDumpInvalidArguments) {
    doc::test("Check that the hal dump doesn't crash on invalid arguments");
    ASSERT_OK(device->debugDump(hidl_handle()));
}

//////////////////////////////////////////////////////////////////////////////
////////////////////////// open{Output,Input}Stream //////////////////////////
//////////////////////////////////////////////////////////////////////////////

template <class Stream>
class OpenStreamTest : public AudioConfigPrimaryTest,
                       public ::testing::WithParamInterface<AudioConfig> {
   protected:
    template <class Open>
    void testOpen(Open openStream, const AudioConfig& config) {
        // FIXME: Open a stream without an IOHandle
        //        This is not required to be accepted by hal implementations
        AudioIoHandle ioHandle =
            (AudioIoHandle)AudioHandleConsts::AUDIO_IO_HANDLE_NONE;
        AudioConfig suggestedConfig{};
        ASSERT_OK(openStream(ioHandle, config,
                             returnIn(res, stream, suggestedConfig)));

        // TODO: only allow failure for RecommendedPlaybackAudioConfig
        switch (res) {
            case Result::OK:
                ASSERT_TRUE(stream != nullptr);
                audioConfig = config;
                break;
            case Result::INVALID_ARGUMENTS:
                ASSERT_TRUE(stream == nullptr);
                AudioConfig suggestedConfigRetry;
                // Could not open stream with config, try again with the
                // suggested one
                ASSERT_OK(
                    openStream(ioHandle, suggestedConfig,
                               returnIn(res, stream, suggestedConfigRetry)));
                // This time it must succeed
                ASSERT_OK(res);
                ASSERT_TRUE(stream != nullptr);
                audioConfig = suggestedConfig;
                break;
            default:
                FAIL() << "Invalid return status: "
                       << ::testing::PrintToString(res);
        }
        open = true;
    }

    Return<Result> closeStream() {
        open = false;
        return stream->close();
    }

   private:
    void TearDown() override {
        if (open) {
            ASSERT_OK(stream->close());
        }
    }

   protected:
    AudioConfig audioConfig;
    DeviceAddress address = {};
    sp<Stream> stream;
    bool open = false;
};

////////////////////////////// openOutputStream //////////////////////////////

class OutputStreamTest : public OpenStreamTest<IStreamOut> {
    virtual void SetUp() override {
        ASSERT_NO_FATAL_FAILURE(OpenStreamTest::SetUp());  // setup base
        address.device = AudioDevice::OUT_DEFAULT;
        const AudioConfig& config = GetParam();
        AudioOutputFlag flags =
            AudioOutputFlag::NONE;  // TODO: test all flag combination
        testOpen(
            [&](AudioIoHandle handle, AudioConfig config, auto cb) {
                return device->openOutputStream(handle, address, config, flags,
                                                cb);
            },
            config);
    }
};
TEST_P(OutputStreamTest, OpenOutputStreamTest) {
    doc::test(
        "Check that output streams can be open with the required and "
        "recommended config");
    // Open done in SetUp
}
INSTANTIATE_TEST_CASE_P(
    RequiredOutputStreamConfigSupport, OutputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRequiredSupportPlaybackAudioConfig()),
    &generateTestName);
INSTANTIATE_TEST_CASE_P(
    SupportedOutputStreamConfig, OutputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getSupportedPlaybackAudioConfig()),
    &generateTestName);

INSTANTIATE_TEST_CASE_P(
    RecommendedOutputStreamConfigSupport, OutputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRecommendedSupportPlaybackAudioConfig()),
    &generateTestName);

////////////////////////////// openInputStream //////////////////////////////

class InputStreamTest : public OpenStreamTest<IStreamIn> {
    virtual void SetUp() override {
        ASSERT_NO_FATAL_FAILURE(OpenStreamTest::SetUp());  // setup base
        address.device = AudioDevice::IN_DEFAULT;
        const AudioConfig& config = GetParam();
        AudioInputFlag flags =
            AudioInputFlag::NONE;  // TODO: test all flag combination
        AudioSource source =
            AudioSource::DEFAULT;  // TODO: test all flag combination
        testOpen(
            [&](AudioIoHandle handle, AudioConfig config, auto cb) {
                return device->openInputStream(handle, address, config, flags,
                                               source, cb);
            },
            config);
    }
};

TEST_P(InputStreamTest, OpenInputStreamTest) {
    doc::test(
        "Check that input streams can be open with the required and "
        "recommended config");
    // Open done in setup
}
INSTANTIATE_TEST_CASE_P(
    RequiredInputStreamConfigSupport, InputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRequiredSupportCaptureAudioConfig()),
    &generateTestName);
INSTANTIATE_TEST_CASE_P(
    SupportedInputStreamConfig, InputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getSupportedCaptureAudioConfig()),
    &generateTestName);

INSTANTIATE_TEST_CASE_P(
    RecommendedInputStreamConfigSupport, InputStreamTest,
    ::testing::ValuesIn(
        AudioConfigPrimaryTest::getRecommendedSupportCaptureAudioConfig()),
    &generateTestName);

//////////////////////////////////////////////////////////////////////////////
////////////////////////////// IStream getters ///////////////////////////////
//////////////////////////////////////////////////////////////////////////////

/** Unpack the provided result.
 * If the result is not OK, register a failure and return an undefined value. */
template <class R>
static R extract(Return<R> ret) {
    if (!ret.isOk()) {
        EXPECT_IS_OK(ret);
        return R{};
    }
    return ret;
}

/* Could not find a way to write a test for two parametrized class fixure
 * thus use this macro do duplicate tests for Input and Output stream */
#define TEST_IO_STREAM(test_name, documentation, code) \
    TEST_P(InputStreamTest, test_name) {               \
        doc::test(documentation);                      \
        code;                                          \
    }                                                  \
    TEST_P(OutputStreamTest, test_name) {              \
        doc::test(documentation);                      \
        code;                                          \
    }

TEST_IO_STREAM(
    GetFrameCount,
    "Check that the stream frame count == the one it was opened with",
    ASSERT_EQ(audioConfig.frameCount, extract(stream->getFrameCount())))

TEST_IO_STREAM(GetSampleRate, "Check that the stream sample rate == the one it was opened with",
               ASSERT_EQ(audioConfig.sampleRateHz, extract(stream->getSampleRate())))

TEST_IO_STREAM(GetChannelMask, "Check that the stream channel mask == the one it was opened with",
               ASSERT_EQ(audioConfig.channelMask, extract(stream->getChannelMask())))

TEST_IO_STREAM(GetFormat,
               "Check that the stream format == the one it was opened with",
               ASSERT_EQ(audioConfig.format, extract(stream->getFormat())))

// TODO: for now only check that the framesize is not incoherent
TEST_IO_STREAM(GetFrameSize,
               "Check that the stream frame size == the one it was opened with",
               ASSERT_GT(extract(stream->getFrameSize()), 0U))

TEST_IO_STREAM(GetBufferSize,
               "Check that the stream buffer size== the one it was opened with",
               ASSERT_GE(extract(stream->getBufferSize()),
                         extract(stream->getFrameSize())));

template <class Property, class CapabilityGetter, class Getter, class Setter>
static void testCapabilityGetter(const string& name, IStream* stream,
                                 Property currentValue,
                                 CapabilityGetter capablityGetter,
                                 Getter getter, Setter setter) {
    hidl_vec<Property> capabilities;
    ASSERT_OK((stream->*capablityGetter)(returnIn(capabilities)));
    if (capabilities.size() == 0) {
        // The default hal should probably return a NOT_SUPPORTED if the hal
        // does not expose
        // capability retrieval. For now it returns an empty list if not
        // implemented
        doc::partialTest(name + " is not supported");
        return;
    };
    // TODO: This code has never been tested on a hal that supports
    // getSupportedSampleRates
    EXPECT_NE(std::find(capabilities.begin(), capabilities.end(), currentValue),
              capabilities.end())
        << "current " << name << " is not in the list of the supported ones "
        << toString(capabilities);

    // Check that all declared supported values are indeed supported
    for (auto capability : capabilities) {
        ASSERT_OK((stream->*setter)(capability));
        ASSERT_EQ(capability, extract((stream->*getter)()));
    }
}

TEST_IO_STREAM(SupportedSampleRate,
               "Check that the stream sample rate is declared as supported",
               testCapabilityGetter("getSupportedSampleRate", stream.get(),
                                    extract(stream->getSampleRate()),
                                    &IStream::getSupportedSampleRates,
                                    &IStream::getSampleRate,
                                    &IStream::setSampleRate))

TEST_IO_STREAM(SupportedChannelMask,
               "Check that the stream channel mask is declared as supported",
               testCapabilityGetter("getSupportedChannelMask", stream.get(),
                                    extract(stream->getChannelMask()),
                                    &IStream::getSupportedChannelMasks,
                                    &IStream::getChannelMask,
                                    &IStream::setChannelMask))

TEST_IO_STREAM(SupportedFormat,
               "Check that the stream format is declared as supported",
               testCapabilityGetter("getSupportedFormat", stream.get(),
                                    extract(stream->getFormat()),
                                    &IStream::getSupportedFormats,
                                    &IStream::getFormat, &IStream::setFormat))

static void testGetDevice(IStream* stream, AudioDevice expectedDevice) {
    // Unfortunately the interface does not allow the implementation to return
    // NOT_SUPPORTED
    // Thus allow NONE as signaling that the call is not supported.
    auto ret = stream->getDevice();
    ASSERT_IS_OK(ret);
    AudioDevice device = ret;
    ASSERT_TRUE(device == expectedDevice || device == AudioDevice::NONE)
        << "Expected: " << ::testing::PrintToString(expectedDevice)
        << "\n  Actual: " << ::testing::PrintToString(device);
}

TEST_IO_STREAM(GetDevice,
               "Check that the stream device == the one it was opened with",
               areAudioPatchesSupported()
                   ? doc::partialTest("Audio patches are supported")
                   : testGetDevice(stream.get(), address.device))

static void testSetDevice(IStream* stream, const DeviceAddress& address) {
    DeviceAddress otherAddress = address;
    otherAddress.device = (address.device & AudioDevice::BIT_IN) == 0
                              ? AudioDevice::OUT_SPEAKER
                              : AudioDevice::IN_BUILTIN_MIC;
    EXPECT_OK(stream->setDevice(otherAddress));

    ASSERT_OK(stream->setDevice(address));  // Go back to the original value
}

TEST_IO_STREAM(
    SetDevice,
    "Check that the stream can be rerouted to SPEAKER or BUILTIN_MIC",
    areAudioPatchesSupported() ? doc::partialTest("Audio patches are supported")
                               : testSetDevice(stream.get(), address))

static void testGetAudioProperties(IStream* stream, AudioConfig expectedConfig) {
    uint32_t sampleRateHz;
    AudioChannelMask mask;
    AudioFormat format;

    stream->getAudioProperties(returnIn(sampleRateHz, mask, format));

    // FIXME: the qcom hal it does not currently negotiate the sampleRate &
    // channel mask
    EXPECT_EQ(expectedConfig.sampleRateHz, sampleRateHz);
    EXPECT_EQ(expectedConfig.channelMask, mask);
    EXPECT_EQ(expectedConfig.format, format);
}

TEST_IO_STREAM(GetAudioProperties,
               "Check that the stream audio properties == the ones it was opened with",
               testGetAudioProperties(stream.get(), audioConfig))

static void testConnectedState(IStream* stream) {
    DeviceAddress address = {};
    using AD = AudioDevice;
    for (auto device :
         {AD::OUT_HDMI, AD::OUT_WIRED_HEADPHONE, AD::IN_USB_HEADSET}) {
        address.device = device;

        ASSERT_OK(stream->setConnectedState(address, true));
        ASSERT_OK(stream->setConnectedState(address, false));
    }
}
TEST_IO_STREAM(SetConnectedState,
               "Check that the stream can be notified of device connection and "
               "deconnection",
               testConnectedState(stream.get()))

static auto invalidArgsOrNotSupportedOrOK = {Result::INVALID_ARGUMENTS,
                                             Result::NOT_SUPPORTED, Result::OK};
TEST_IO_STREAM(SetHwAvSync, "Try to set hardware sync to an invalid value",
               ASSERT_RESULT(invalidArgsOrNotSupportedOrOK,
                             stream->setHwAvSync(666)))

TEST_IO_STREAM(GetHwAvSync, "Get hardware sync can not fail",
               ASSERT_IS_OK(device->getHwAvSync()));

static void checkGetNoParameter(IStream* stream, hidl_vec<hidl_string> keys,
                                vector<Result> expectedResults) {
    hidl_vec<ParameterValue> parameters;
    Result res;
    ASSERT_OK(stream->getParameters(keys, returnIn(res, parameters)));
    ASSERT_RESULT(expectedResults, res);
    if (res == Result::OK) {
        for (auto& parameter : parameters) {
            ASSERT_EQ(0U, parameter.value.size()) << toString(parameter);
        }
    }
}

/* Get/Set parameter is intended to be an opaque channel between vendors app and
 * their HALs.
 * Thus can not be meaningfully tested.
 */
TEST_IO_STREAM(getEmptySetParameter, "Retrieve the values of an empty set",
               checkGetNoParameter(stream.get(), {} /* keys */, {Result::OK}))

TEST_IO_STREAM(getNonExistingParameter,
               "Retrieve the values of an non existing parameter",
               checkGetNoParameter(stream.get(),
                                   {"Non existing key"} /* keys */,
                                   {Result::NOT_SUPPORTED}))

TEST_IO_STREAM(setEmptySetParameter,
               "Set the values of an empty set of parameters",
               ASSERT_RESULT(Result::OK, stream->setParameters({})))

TEST_IO_STREAM(
    setNonExistingParameter, "Set the values of an non existing parameter",
    // Unfortunately, the set_parameter legacy interface did not return any
    // error code when a key is not supported.
    // To allow implementation to just wrapped the legacy one, consider OK as a
    // valid result for setting a non existing parameter.
    ASSERT_RESULT(invalidArgsOrNotSupportedOrOK,
                  stream->setParameters({{"non existing key", "0"}})))

TEST_IO_STREAM(DebugDump,
               "Check that a stream can dump its state without error",
               testDebugDump([this](const auto& handle) {
                   return stream->debugDump(handle);
               }))

TEST_IO_STREAM(DebugDumpInvalidArguments,
               "Check that the stream dump doesn't crash on invalid arguments",
               ASSERT_OK(stream->debugDump(hidl_handle())))

//////////////////////////////////////////////////////////////////////////////
////////////////////////////// addRemoveEffect ///////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_IO_STREAM(AddNonExistingEffect, "Adding a non existing effect should fail",
               ASSERT_RESULT(Result::INVALID_ARGUMENTS, stream->addEffect(666)))
TEST_IO_STREAM(RemoveNonExistingEffect,
               "Removing a non existing effect should fail",
               ASSERT_RESULT(Result::INVALID_ARGUMENTS,
                             stream->removeEffect(666)))

// TODO: positive tests

//////////////////////////////////////////////////////////////////////////////
/////////////////////////////// Control ////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_IO_STREAM(standby, "Make sure the stream can be put in stanby",
               ASSERT_OK(stream->standby()))  // can not fail

static vector<Result> invalidStateOrNotSupported = {Result::INVALID_STATE,
                                                    Result::NOT_SUPPORTED};

TEST_IO_STREAM(startNoMmap,
               "Starting a mmaped stream before mapping it should fail",
               ASSERT_RESULT(invalidStateOrNotSupported, stream->start()))

TEST_IO_STREAM(stopNoMmap,
               "Stopping a mmaped stream before mapping it should fail",
               ASSERT_RESULT(invalidStateOrNotSupported, stream->stop()))

TEST_IO_STREAM(getMmapPositionNoMmap,
               "Get a stream Mmap position before mapping it should fail",
               ASSERT_RESULT(invalidStateOrNotSupported, stream->stop()))

TEST_IO_STREAM(close, "Make sure a stream can be closed",
               ASSERT_OK(closeStream()))
TEST_IO_STREAM(closeTwice, "Make sure a stream can not be closed twice",
               ASSERT_OK(closeStream());
               ASSERT_RESULT(Result::INVALID_STATE, closeStream()))

static auto invalidArgsOrNotSupported = {Result::INVALID_ARGUMENTS,
                                         Result::NOT_SUPPORTED};
static void testCreateTooBigMmapBuffer(IStream* stream) {
    MmapBufferInfo info;
    Result res;
    // Assume that int max is a value too big to be allocated
    // This is true currently with a 32bit media server, but might not when it
    // will run in 64 bit
    auto minSizeFrames = std::numeric_limits<int32_t>::max();
    ASSERT_OK(stream->createMmapBuffer(minSizeFrames, returnIn(res, info)));
    ASSERT_RESULT(invalidArgsOrNotSupported, res);
}

TEST_IO_STREAM(CreateTooBigMmapBuffer, "Create mmap buffer too big should fail",
               testCreateTooBigMmapBuffer(stream.get()))

static void testGetMmapPositionOfNonMmapedStream(IStream* stream) {
    Result res;
    MmapPosition position;
    ASSERT_OK(stream->getMmapPosition(returnIn(res, position)));
    ASSERT_RESULT(invalidArgsOrNotSupported, res);
}

TEST_IO_STREAM(
    GetMmapPositionOfNonMmapedStream,
    "Retrieving the mmap position of a non mmaped stream should fail",
    testGetMmapPositionOfNonMmapedStream(stream.get()))

//////////////////////////////////////////////////////////////////////////////
///////////////////////////////// StreamIn ///////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_P(InputStreamTest, GetAudioSource) {
    doc::test(
        "Retrieving the audio source of an input stream should always succeed");
    AudioSource source;
    ASSERT_OK(stream->getAudioSource(returnIn(res, source)));
    if (res == Result::NOT_SUPPORTED) {
        doc::partialTest("getAudioSource is not supported");
        return;
    }
    ASSERT_OK(res);
    ASSERT_EQ(AudioSource::DEFAULT, source);
}

static void testUnitaryGain(std::function<Return<Result>(float)> setGain) {
    for (float value :
         (float[]){-INFINITY, -1.0, 1.0 + std::numeric_limits<float>::epsilon(),
                   2.0, INFINITY, NAN}) {
        EXPECT_RESULT(Result::INVALID_ARGUMENTS, setGain(value)) << "value="
                                                                 << value;
    }
    // Do not consider -0.0 as an invalid value as it is == with 0.0
    for (float value : {-0.0, 0.0, 0.01, 0.5, 0.09, 1.0 /* Restore volume*/}) {
        EXPECT_OK(setGain(value)) << "value=" << value;
    }
}

static void testOptionalUnitaryGain(
    std::function<Return<Result>(float)> setGain, string debugName) {
    auto result = setGain(1);
    ASSERT_IS_OK(result);
    if (result == Result::NOT_SUPPORTED) {
        doc::partialTest(debugName + " is not supported");
        return;
    }
    testUnitaryGain(setGain);
}

TEST_P(InputStreamTest, SetGain) {
    doc::test("The gain of an input stream should only be set between [0,1]");
    testOptionalUnitaryGain(
        [this](float volume) { return stream->setGain(volume); },
        "InputStream::setGain");
}

static void testPrepareForReading(IStreamIn* stream, uint32_t frameSize,
                                  uint32_t framesCount) {
    Result res;
    // Ignore output parameters as the call should fail
    ASSERT_OK(stream->prepareForReading(
        frameSize, framesCount,
        [&res](auto r, auto&, auto&, auto&, auto&) { res = r; }));
    EXPECT_RESULT(Result::INVALID_ARGUMENTS, res);
}

TEST_P(InputStreamTest, PrepareForReadingWithZeroBuffer) {
    doc::test(
        "Preparing a stream for reading with a 0 sized buffer should fail");
    testPrepareForReading(stream.get(), 0, 0);
}

TEST_P(InputStreamTest, PrepareForReadingWithHugeBuffer) {
    doc::test(
        "Preparing a stream for reading with a 2^32 sized buffer should fail");
    testPrepareForReading(stream.get(), 1,
                          std::numeric_limits<uint32_t>::max());
}

TEST_P(InputStreamTest, PrepareForReadingCheckOverflow) {
    doc::test(
        "Preparing a stream for reading with a overflowing sized buffer should "
        "fail");
    auto uintMax = std::numeric_limits<uint32_t>::max();
    testPrepareForReading(stream.get(), uintMax, uintMax);
}

TEST_P(InputStreamTest, GetInputFramesLost) {
    doc::test(
        "The number of frames lost on a never started stream should be 0");
    auto ret = stream->getInputFramesLost();
    ASSERT_IS_OK(ret);
    uint32_t framesLost{ret};
    ASSERT_EQ(0U, framesLost);
}

TEST_P(InputStreamTest, getCapturePosition) {
    doc::test(
        "The capture position of a non prepared stream should not be "
        "retrievable");
    uint64_t frames;
    uint64_t time;
    ASSERT_OK(stream->getCapturePosition(returnIn(res, frames, time)));
    ASSERT_RESULT(invalidStateOrNotSupported, res);
}

//////////////////////////////////////////////////////////////////////////////
///////////////////////////////// StreamIn ///////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_P(OutputStreamTest, getLatency) {
    doc::test("Make sure latency is over 0");
    auto result = stream->getLatency();
    ASSERT_IS_OK(result);
    ASSERT_GT(result, 0U);
}

TEST_P(OutputStreamTest, setVolume) {
    doc::test("Try to set the output volume");
    testOptionalUnitaryGain(
        [this](float volume) { return stream->setVolume(volume, volume); },
        "setVolume");
}

static void testPrepareForWriting(IStreamOut* stream, uint32_t frameSize,
                                  uint32_t framesCount) {
    Result res;
    // Ignore output parameters as the call should fail
    ASSERT_OK(stream->prepareForWriting(
        frameSize, framesCount,
        [&res](auto r, auto&, auto&, auto&, auto&) { res = r; }));
    EXPECT_RESULT(Result::INVALID_ARGUMENTS, res);
}

TEST_P(OutputStreamTest, PrepareForWriteWithZeroBuffer) {
    doc::test(
        "Preparing a stream for writing with a 0 sized buffer should fail");
    testPrepareForWriting(stream.get(), 0, 0);
}

TEST_P(OutputStreamTest, PrepareForWriteWithHugeBuffer) {
    doc::test(
        "Preparing a stream for writing with a 2^32 sized buffer should fail");
    testPrepareForWriting(stream.get(), 1,
                          std::numeric_limits<uint32_t>::max());
}

TEST_P(OutputStreamTest, PrepareForWritingCheckOverflow) {
    doc::test(
        "Preparing a stream for writing with a overflowing sized buffer should "
        "fail");
    auto uintMax = std::numeric_limits<uint32_t>::max();
    testPrepareForWriting(stream.get(), uintMax, uintMax);
}

struct Capability {
    Capability(IStreamOut* stream) {
        EXPECT_OK(stream->supportsPauseAndResume(returnIn(pause, resume)));
        auto ret = stream->supportsDrain();
        EXPECT_IS_OK(ret);
        if (ret.isOk()) {
            drain = ret;
        }
    }
    bool pause = false;
    bool resume = false;
    bool drain = false;
};

TEST_P(OutputStreamTest, SupportsPauseAndResumeAndDrain) {
    doc::test(
        "Implementation must expose pause, resume and drain capabilities");
    Capability(stream.get());
}

template <class Value>
static void checkInvalidStateOr0(Result res, Value value) {
    switch (res) {
        case Result::INVALID_STATE:
            break;
        case Result::OK:
            ASSERT_EQ(0U, value);
            break;
        default:
            FAIL() << "Unexpected result " << toString(res);
    }
}

TEST_P(OutputStreamTest, GetRenderPosition) {
    doc::test("A new stream render position should be 0 or INVALID_STATE");
    uint32_t dspFrames;
    ASSERT_OK(stream->getRenderPosition(returnIn(res, dspFrames)));
    if (res == Result::NOT_SUPPORTED) {
        doc::partialTest("getRenderPosition is not supported");
        return;
    }
    checkInvalidStateOr0(res, dspFrames);
}

TEST_P(OutputStreamTest, GetNextWriteTimestamp) {
    doc::test("A new stream next write timestamp should be 0 or INVALID_STATE");
    uint64_t timestampUs;
    ASSERT_OK(stream->getNextWriteTimestamp(returnIn(res, timestampUs)));
    if (res == Result::NOT_SUPPORTED) {
        doc::partialTest("getNextWriteTimestamp is not supported");
        return;
    }
    checkInvalidStateOr0(res, timestampUs);
}

/** Stub implementation of out stream callback. */
class MockOutCallbacks : public IStreamOutCallback {
    Return<void> onWriteReady() override { return {}; }
    Return<void> onDrainReady() override { return {}; }
    Return<void> onError() override { return {}; }
};

static bool isAsyncModeSupported(IStreamOut* stream) {
    auto res = stream->setCallback(new MockOutCallbacks);
    stream->clearCallback();  // try to restore the no callback state, ignore
                              // any error
    auto okOrNotSupported = {Result::OK, Result::NOT_SUPPORTED};
    EXPECT_RESULT(okOrNotSupported, res);
    return res.isOk() ? res == Result::OK : false;
}

TEST_P(OutputStreamTest, SetCallback) {
    doc::test(
        "If supported, registering callback for async operation should never "
        "fail");
    if (!isAsyncModeSupported(stream.get())) {
        doc::partialTest("The stream does not support async operations");
        return;
    }
    ASSERT_OK(stream->setCallback(new MockOutCallbacks));
    ASSERT_OK(stream->setCallback(new MockOutCallbacks));
}

TEST_P(OutputStreamTest, clearCallback) {
    doc::test(
        "If supported, clearing a callback to go back to sync operation should "
        "not fail");
    if (!isAsyncModeSupported(stream.get())) {
        doc::partialTest("The stream does not support async operations");
        return;
    }
    // TODO: Clarify if clearing a non existing callback should fail
    ASSERT_OK(stream->setCallback(new MockOutCallbacks));
    ASSERT_OK(stream->clearCallback());
}

TEST_P(OutputStreamTest, Resume) {
    doc::test(
        "If supported, a stream should fail to resume if not previously "
        "paused");
    if (!Capability(stream.get()).resume) {
        doc::partialTest("The output stream does not support resume");
        return;
    }
    ASSERT_RESULT(Result::INVALID_STATE, stream->resume());
}

TEST_P(OutputStreamTest, Pause) {
    doc::test(
        "If supported, a stream should fail to pause if not previously "
        "started");
    if (!Capability(stream.get()).pause) {
        doc::partialTest("The output stream does not support pause");
        return;
    }
    ASSERT_RESULT(Result::INVALID_STATE, stream->resume());
}

static void testDrain(IStreamOut* stream, AudioDrain type) {
    if (!Capability(stream).drain) {
        doc::partialTest("The output stream does not support drain");
        return;
    }
    ASSERT_RESULT(Result::OK, stream->drain(type));
}

TEST_P(OutputStreamTest, DrainAll) {
    doc::test("If supported, a stream should always succeed to drain");
    testDrain(stream.get(), AudioDrain::ALL);
}

TEST_P(OutputStreamTest, DrainEarlyNotify) {
    doc::test("If supported, a stream should always succeed to drain");
    testDrain(stream.get(), AudioDrain::EARLY_NOTIFY);
}

TEST_P(OutputStreamTest, FlushStop) {
    doc::test("If supported, a stream should always succeed to flush");
    auto ret = stream->flush();
    ASSERT_IS_OK(ret);
    if (ret == Result::NOT_SUPPORTED) {
        doc::partialTest("Flush is not supported");
        return;
    }
    ASSERT_OK(ret);
}

TEST_P(OutputStreamTest, GetPresentationPositionStop) {
    doc::test(
        "If supported, a stream should always succeed to retrieve the "
        "presentation position");
    uint64_t frames;
    TimeSpec mesureTS;
    ASSERT_OK(stream->getPresentationPosition(returnIn(res, frames, mesureTS)));
    if (res == Result::NOT_SUPPORTED) {
        doc::partialTest("getpresentationPosition is not supported");
        return;
    }
    ASSERT_EQ(0U, frames);

    if (mesureTS.tvNSec == 0 && mesureTS.tvSec == 0) {
        // As the stream has never written a frame yet,
        // the timestamp does not really have a meaning, allow to return 0
        return;
    }

    // Make sure the return measure is not more than 1s old.
    struct timespec currentTS;
    ASSERT_EQ(0, clock_gettime(CLOCK_MONOTONIC, &currentTS)) << errno;

    auto toMicroSec = [](uint64_t sec, auto nsec) {
        return sec * 1e+6 + nsec / 1e+3;
    };
    auto currentTime = toMicroSec(currentTS.tv_sec, currentTS.tv_nsec);
    auto mesureTime = toMicroSec(mesureTS.tvSec, mesureTS.tvNSec);
    ASSERT_PRED2([](auto c, auto m) { return c - m < 1e+6; }, currentTime,
                 mesureTime);
}

//////////////////////////////////////////////////////////////////////////////
/////////////////////////////// PrimaryDevice ////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

TEST_F(AudioPrimaryHidlTest, setVoiceVolume) {
    doc::test("Make sure setVoiceVolume only succeed if volume is in [0,1]");
    testUnitaryGain([](float volume) { return device->setVoiceVolume(volume); });
}

TEST_F(AudioPrimaryHidlTest, setMode) {
    doc::test(
        "Make sure setMode always succeeds if mode is valid "
        "and fails otherwise");
    // Test Invalid values
    for (AudioMode mode :
         {AudioMode::INVALID, AudioMode::CURRENT, AudioMode::CNT}) {
        SCOPED_TRACE("mode=" + toString(mode));
        ASSERT_RESULT(Result::INVALID_ARGUMENTS, device->setMode(mode));
    }
    // Test valid values
    for (AudioMode mode :
         {AudioMode::IN_CALL, AudioMode::IN_COMMUNICATION, AudioMode::RINGTONE,
          AudioMode::NORMAL /* Make sure to leave the test in normal mode */}) {
        SCOPED_TRACE("mode=" + toString(mode));
        ASSERT_OK(device->setMode(mode));
    }
}

TEST_F(BoolAccessorPrimaryHidlTest, BtScoNrecEnabled) {
    doc::test("Query and set the BT SCO NR&EC state");
    testOptionalAccessors("BtScoNrecEnabled", {true, false, true},
                          &IPrimaryDevice::setBtScoNrecEnabled,
                          &IPrimaryDevice::getBtScoNrecEnabled);
}

TEST_F(BoolAccessorPrimaryHidlTest, setGetBtScoWidebandEnabled) {
    doc::test("Query and set the SCO whideband state");
    testOptionalAccessors("BtScoWideband", {true, false, true},
                          &IPrimaryDevice::setBtScoWidebandEnabled,
                          &IPrimaryDevice::getBtScoWidebandEnabled);
}

using TtyModeAccessorPrimaryHidlTest = AccessorPrimaryHidlTest<TtyMode>;
TEST_F(TtyModeAccessorPrimaryHidlTest, setGetTtyMode) {
    doc::test("Query and set the TTY mode state");
    testOptionalAccessors(
        "TTY mode", {TtyMode::OFF, TtyMode::HCO, TtyMode::VCO, TtyMode::FULL},
        &IPrimaryDevice::setTtyMode, &IPrimaryDevice::getTtyMode);
}

TEST_F(BoolAccessorPrimaryHidlTest, setGetHac) {
    doc::test("Query and set the HAC state");
    testOptionalAccessors("HAC", {true, false, true},
                          &IPrimaryDevice::setHacEnabled,
                          &IPrimaryDevice::getHacEnabled);
}

//////////////////////////////////////////////////////////////////////////////
//////////////////// Clean caches on global tear down ////////////////////////
//////////////////////////////////////////////////////////////////////////////

int main(int argc, char** argv) {
    environment = new Environment;
    ::testing::AddGlobalTestEnvironment(environment);
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    return status;
}
