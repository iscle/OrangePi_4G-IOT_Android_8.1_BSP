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

#define LOG_TAG "BroadcastRadioHidlHalTest"
#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <cutils/native_handle.h>
#include <cutils/properties.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/threads.h>

#include <android/hardware/broadcastradio/1.0/IBroadcastRadioFactory.h>
#include <android/hardware/broadcastradio/1.0/IBroadcastRadio.h>
#include <android/hardware/broadcastradio/1.0/ITuner.h>
#include <android/hardware/broadcastradio/1.0/ITunerCallback.h>
#include <android/hardware/broadcastradio/1.0/types.h>


using ::android::sp;
using ::android::Mutex;
using ::android::Condition;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::broadcastradio::V1_0::IBroadcastRadioFactory;
using ::android::hardware::broadcastradio::V1_0::IBroadcastRadio;
using ::android::hardware::broadcastradio::V1_0::ITuner;
using ::android::hardware::broadcastradio::V1_0::ITunerCallback;
using ::android::hardware::broadcastradio::V1_0::Result;
using ::android::hardware::broadcastradio::V1_0::Class;
using ::android::hardware::broadcastradio::V1_0::Properties;
using ::android::hardware::broadcastradio::V1_0::Band;
using ::android::hardware::broadcastradio::V1_0::BandConfig;
using ::android::hardware::broadcastradio::V1_0::Direction;
using ::android::hardware::broadcastradio::V1_0::ProgramInfo;
using ::android::hardware::broadcastradio::V1_0::MetaData;
using ::android::hardware::broadcastradio::V1_0::MetadataKey;
using ::android::hardware::broadcastradio::V1_0::MetadataType;

#define RETURN_IF_SKIPPED \
    if (skipped) { \
        std::cout << "[  SKIPPED ] This device class is not supported. " << std::endl; \
        return; \
    }

// The main test class for Broadcast Radio HIDL HAL.

class BroadcastRadioHidlTest : public ::testing::VtsHalHidlTargetTestBase,
        public ::testing::WithParamInterface<Class> {
 protected:
    virtual void SetUp() override {
        ASSERT_EQ(nullptr, mRadio.get());

        radioClass = GetParam();
        skipped = false;

        sp<IBroadcastRadioFactory> factory =
              ::testing::VtsHalHidlTargetTestBase::getService<IBroadcastRadioFactory>();
        ASSERT_NE(nullptr, factory.get());

        Result connectResult;
        factory->connectModule(radioClass, [&](Result ret, const sp<IBroadcastRadio>& radio) {
            connectResult = ret;
            mRadio = radio;
            onCallback_l();
        });
        EXPECT_EQ(true, waitForCallback(kConnectCallbacktimeoutNs));
        mCallbackCalled = false;

        if (connectResult == Result::INVALID_ARGUMENTS) {
            skipped = true;
            return;
        }
        ASSERT_EQ(connectResult, Result::OK);

        mTunerCallback = new MyCallback(this);
        ASSERT_NE(nullptr, mRadio.get());
        ASSERT_NE(nullptr, mTunerCallback.get());
    }

    virtual void TearDown() override {
        mTuner.clear();
        mRadio.clear();
    }

    class MyCallback : public ITunerCallback {
     public:

        // ITunerCallback methods (see doc in ITunerCallback.hal)
        virtual Return<void> hardwareFailure() {
            ALOGI("%s", __FUNCTION__);
            mParentTest->onHwFailureCallback();
            return Void();
        }

        virtual Return<void> configChange(Result result, const BandConfig& config) {
            ALOGI("%s result %d", __FUNCTION__, result);
            mParentTest->onConfigChangeCallback(result, config);
            return Void();
        }

        virtual Return<void> tuneComplete(Result result, const ProgramInfo& info) {
            ALOGI("%s result %d", __FUNCTION__, result);
            mParentTest->onTuneCompleteCallback(result, info);
            return Void();
        }

        virtual Return<void> afSwitch(const ProgramInfo& info __unused) {
            return Void();
        }

        virtual Return<void> antennaStateChange(bool connected) {
            ALOGI("%s connected %d", __FUNCTION__, connected);
            return Void();
        }

        virtual Return<void> trafficAnnouncement(bool active) {
            ALOGI("%s active %d", __FUNCTION__, active);
            return Void();
        }

        virtual Return<void> emergencyAnnouncement(bool active) {
            ALOGI("%s active %d", __FUNCTION__, active);
            return Void();
        }

        virtual Return<void> newMetadata(uint32_t channel __unused, uint32_t subChannel __unused,
                           const ::android::hardware::hidl_vec<MetaData>& metadata __unused) {
            ALOGI("%s", __FUNCTION__);
            return Void();
        }

                MyCallback(BroadcastRadioHidlTest *parentTest) : mParentTest(parentTest) {}

     private:
        // BroadcastRadioHidlTest instance to which callbacks will be notified.
        BroadcastRadioHidlTest *mParentTest;
    };


    /**
     * Method called by MyCallback when a callback with no status or boolean value is received
     */
    void onCallback() {
        Mutex::Autolock _l(mLock);
        onCallback_l();
    }

    /**
     * Method called by MyCallback when hardwareFailure() callback is received
     */
    void onHwFailureCallback() {
        Mutex::Autolock _l(mLock);
        mHwFailure = true;
        onCallback_l();
    }

    /**
     * Method called by MyCallback when configChange() callback is received.
     */
    void onConfigChangeCallback(Result result, const BandConfig& config) {
        Mutex::Autolock _l(mLock);
        mResultCallbackData = result;
        mBandConfigCallbackData = config;
        onCallback_l();
    }

    /**
     * Method called by MyCallback when tuneComplete() callback is received.
     */
    void onTuneCompleteCallback(Result result, const ProgramInfo& info) {
        Mutex::Autolock _l(mLock);
        mResultCallbackData = result;
        mProgramInfoCallbackData = info;
        onCallback_l();
    }

    /**
     * Method called by MyCallback when a boolean indication is received
     */
    void onBoolCallback(bool result) {
        Mutex::Autolock _l(mLock);
        mBoolCallbackData = result;
        onCallback_l();
    }


    BroadcastRadioHidlTest()
        : mCallbackCalled(false), mBoolCallbackData(false), mResultCallbackData(Result::OK),
        mHwFailure(false) {}

    void onCallback_l() {
        if (!mCallbackCalled) {
            mCallbackCalled = true;
            mCallbackCond.broadcast();
        }
    }


    bool waitForCallback(nsecs_t reltime = 0) {
        Mutex::Autolock _l(mLock);
        nsecs_t endTime = systemTime() + reltime;
        while (!mCallbackCalled) {
            if (reltime == 0) {
                mCallbackCond.wait(mLock);
            } else {
                nsecs_t now = systemTime();
                if (now > endTime) {
                    return false;
                }
                mCallbackCond.waitRelative(mLock, endTime - now);
            }
        }
        return true;
    }

    bool getProperties();
    bool openTuner();
    bool checkAntenna();

    /**
     * Retrieves AM/FM band configuration from module properties.
     *
     * The configuration may not exist: if radio type is other than AM/FM
     * or provided index is out of bounds.
     * In such case, empty configuration is returned.
     *
     * @param idx Band index to retrieve.
     * @return Band configuration reference.
     */
    const BandConfig& getBand(unsigned idx);

    static const nsecs_t kConnectCallbacktimeoutNs = seconds_to_nanoseconds(1);
    static const nsecs_t kConfigCallbacktimeoutNs = seconds_to_nanoseconds(10);
    static const nsecs_t kTuneCallbacktimeoutNs = seconds_to_nanoseconds(30);

    Class radioClass;
    bool skipped;
    sp<IBroadcastRadio> mRadio;
    Properties mHalProperties;
    bool mHalPropertiesInitialized = false;
    sp<ITuner> mTuner;
    sp<MyCallback> mTunerCallback;
    Mutex mLock;
    Condition mCallbackCond;
    bool mCallbackCalled;
    bool mBoolCallbackData;
    Result mResultCallbackData;
    ProgramInfo mProgramInfoCallbackData;
    BandConfig mBandConfigCallbackData;
    bool mHwFailure;
};

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {

/**
 * Compares two BandConfig objects for testing purposes.
 */
static bool operator==(const BandConfig& l, const BandConfig& r) {
    if (l.type != r.type) return false;
    if (l.antennaConnected != r.antennaConnected) return false;
    if (l.lowerLimit != r.lowerLimit) return false;
    if (l.upperLimit != r.upperLimit) return false;
    if (l.spacings != r.spacings) return false;
    if (l.type == Band::AM || l.type == Band::AM_HD) {
        return l.ext.am == r.ext.am;
    } else if (l.type == Band::FM || l.type == Band::FM_HD) {
        return l.ext.fm == r.ext.fm;
    } else {
        // unsupported type
        return false;
    }
}

}  // V1_0
}  // broadcastradio
}  // hardware
}  // android

bool BroadcastRadioHidlTest::getProperties()
{
    if (mHalPropertiesInitialized) return true;

    Result halResult = Result::NOT_INITIALIZED;
    auto hidlReturn = mRadio->getProperties([&](Result result, const Properties& properties) {
        halResult = result;
        if (result == Result::OK) {
            mHalProperties = properties;
        }
    });

    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_EQ(Result::OK, halResult);
    EXPECT_EQ(radioClass, mHalProperties.classId);
    EXPECT_GT(mHalProperties.numTuners, 0u);
    if (radioClass == Class::AM_FM) {
        EXPECT_GT(mHalProperties.bands.size(), 0u);
    }

    if (hidlReturn.isOk() && halResult == Result::OK) {
        mHalPropertiesInitialized = true;
        return true;
    }
    return false;
}

bool BroadcastRadioHidlTest::openTuner()
{
    if (!getProperties()) {
        return false;
    }
    if (mTuner.get() == nullptr) {
        Result halResult = Result::NOT_INITIALIZED;
        auto openCb = [&](Result result, const sp<ITuner>& tuner) {
            halResult = result;
            if (result == Result::OK) {
                mTuner = tuner;
            }
        };
        auto hidlReturn = mRadio->openTuner(getBand(0), true, mTunerCallback, openCb);
        EXPECT_TRUE(hidlReturn.isOk());
        EXPECT_EQ(Result::OK, halResult);
        if (radioClass == Class::AM_FM) {
            EXPECT_EQ(true, waitForCallback(kConfigCallbacktimeoutNs));
        }
    }
    EXPECT_NE(nullptr, mTuner.get());
    return nullptr != mTuner.get();
}

bool BroadcastRadioHidlTest::checkAntenna()
{
    if (radioClass != Class::AM_FM) return true;

    BandConfig halConfig;
    Result halResult = Result::NOT_INITIALIZED;
    Return<void> hidlReturn =
            mTuner->getConfiguration([&](Result result, const BandConfig& config) {
                halResult = result;
                if (result == Result::OK) {
                    halConfig = config;
                }
            });

    return ((halResult == Result::OK) && (halConfig.antennaConnected == true));
}

const BandConfig& BroadcastRadioHidlTest::getBand(unsigned idx) {
    static BandConfig dummyBandConfig = {};
    if (radioClass == Class::AM_FM) {
        EXPECT_GT(mHalProperties.bands.size(), idx);
        if (mHalProperties.bands.size() > idx) {
            return mHalProperties.bands[idx];
        } else {
            return dummyBandConfig;
        }
    } else {
        return dummyBandConfig;
    }
}

/**
 * Test IBroadcastRadio::getProperties() method
 *
 * Verifies that:
 *  - the HAL implements the method
 *  - the method returns 0 (no error)
 *  - the implementation class is radioClass
 *  - the implementation supports at least one tuner
 *  - the implementation supports at one band
 */
TEST_P(BroadcastRadioHidlTest, GetProperties) {
    RETURN_IF_SKIPPED;
    EXPECT_EQ(true, getProperties());
}

/**
 * Test IBroadcastRadio::openTuner() method
 *
 * Verifies that:
 *  - the HAL implements the method
 *  - the method returns 0 (no error) and a valid ITuner interface
 */
TEST_P(BroadcastRadioHidlTest, OpenTuner) {
    RETURN_IF_SKIPPED;
    EXPECT_EQ(true, openTuner());
}

/**
 * Test IBroadcastRadio::openTuner() after ITuner disposal.
 *
 * Verifies that:
 *  - ITuner destruction gets propagated through HAL
 *  - the openTuner method works well when called for the second time
 */
TEST_P(BroadcastRadioHidlTest, ReopenTuner) {
    RETURN_IF_SKIPPED;
    EXPECT_TRUE(openTuner());
    mTuner.clear();
    EXPECT_TRUE(openTuner());
}

/**
 * Test IBroadcastRadio::openTuner() method called twice.
 *
 * Verifies that:
 *  - the openTuner method fails with INVALID_STATE or succeeds when called for the second time
 *    without deleting previous ITuner instance
 */
TEST_P(BroadcastRadioHidlTest, OpenTunerTwice) {
    RETURN_IF_SKIPPED;
    EXPECT_TRUE(openTuner());

    Result halResult = Result::NOT_INITIALIZED;
    auto openCb = [&](Result result, const sp<ITuner>&) { halResult = result; };
    auto hidlReturn = mRadio->openTuner(getBand(0), true, mTunerCallback, openCb);
    EXPECT_TRUE(hidlReturn.isOk());
    if (halResult == Result::OK) {
        if (radioClass == Class::AM_FM) {
            EXPECT_TRUE(waitForCallback(kConfigCallbacktimeoutNs));
        }
    } else {
        EXPECT_EQ(Result::INVALID_STATE, halResult);
    }
}

/**
 * Test ITuner::setConfiguration() and getConfiguration methods
 *
 * Verifies that:
 *  - the HAL implements both methods
 *  - the methods return 0 (no error)
 *  - the configuration callback is received within kConfigCallbacktimeoutNs ns
 *  - the configuration read back from HAl has the same class Id
 *
 * Skipped for other radio classes than AM/FM, because setConfiguration
 * applies only for these bands.
 */
TEST_P(BroadcastRadioHidlTest, SetAndGetConfiguration) {
    if (radioClass != Class::AM_FM) skipped = true;
    RETURN_IF_SKIPPED;
    ASSERT_EQ(true, openTuner());
    // test setConfiguration
    mCallbackCalled = false;
    Return<Result> hidlResult = mTuner->setConfiguration(getBand(1));
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kConfigCallbacktimeoutNs));
    EXPECT_EQ(Result::OK, mResultCallbackData);
    EXPECT_EQ(getBand(1), mBandConfigCallbackData);

    // test getConfiguration
    BandConfig halConfig;
    Result halResult;
    Return<void> hidlReturn =
            mTuner->getConfiguration([&](Result result, const BandConfig& config) {
                halResult = result;
                if (result == Result::OK) {
                    halConfig = config;
                }
            });
    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_EQ(Result::OK, halResult);
    EXPECT_EQ(getBand(1), halConfig);
}

/**
 * Test ITuner::setConfiguration() with invalid arguments.
 *
 * Verifies that:
 *  - the methods returns INVALID_ARGUMENTS on invalid arguments
 *  - the method recovers and succeeds after passing correct arguments
 *
 * Skipped for other radio classes than AM/FM, because setConfiguration
 * applies only for these bands.
 */
TEST_P(BroadcastRadioHidlTest, SetConfigurationFails) {
    if (radioClass != Class::AM_FM) skipped = true;
    RETURN_IF_SKIPPED;
    ASSERT_EQ(true, openTuner());

    // Let's define a config that's bad for sure.
    BandConfig badConfig = {};
    badConfig.type = Band::FM;
    badConfig.lowerLimit = 0xFFFFFFFF;
    badConfig.upperLimit = 0;
    badConfig.spacings = (std::vector<uint32_t>){ 0 };

    // Test setConfiguration failing on bad data.
    mCallbackCalled = false;
    auto setResult = mTuner->setConfiguration(badConfig);
    EXPECT_TRUE(setResult.isOk());
    EXPECT_EQ(Result::INVALID_ARGUMENTS, setResult);

    // Test setConfiguration recovering after passing good data.
    mCallbackCalled = false;
    setResult = mTuner->setConfiguration(getBand(0));
    EXPECT_TRUE(setResult.isOk());
    EXPECT_EQ(Result::OK, setResult);
    EXPECT_EQ(true, waitForCallback(kConfigCallbacktimeoutNs));
    EXPECT_EQ(Result::OK, mResultCallbackData);
}

/**
 * Test ITuner::scan
 *
 * Verifies that:
 *  - the HAL implements the method
 *  - the method returns 0 (no error)
 *  - the tuned callback is received within kTuneCallbacktimeoutNs ns
 *  - skipping sub-channel or not does not fail the call
 */
TEST_P(BroadcastRadioHidlTest, Scan) {
    RETURN_IF_SKIPPED;
    ASSERT_EQ(true, openTuner());
    ASSERT_TRUE(checkAntenna());
    // test scan UP
    mCallbackCalled = false;
    Return<Result> hidlResult = mTuner->scan(Direction::UP, true);
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));

    // test scan DOWN
    mCallbackCalled = false;
    hidlResult = mTuner->scan(Direction::DOWN, false);
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));
}

/**
 * Test ITuner::step
 *
 * Verifies that:
 *  - the HAL implements the method
 *  - the method returns 0 (no error)
 *  - the tuned callback is received within kTuneCallbacktimeoutNs ns
 *  - skipping sub-channel or not does not fail the call
 *
 * Skipped for other radio classes than AM/FM, because step is not possible
 * on DAB nor satellite.
 */
TEST_P(BroadcastRadioHidlTest, Step) {
    if (radioClass != Class::AM_FM) skipped = true;
    RETURN_IF_SKIPPED;
    ASSERT_EQ(true, openTuner());
    ASSERT_TRUE(checkAntenna());
    // test step UP
    mCallbackCalled = false;
    Return<Result> hidlResult = mTuner->step(Direction::UP, false);
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));

    // test step DOWN
    mCallbackCalled = false;
    hidlResult = mTuner->step(Direction::DOWN, true);
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));
}

/**
 * Test ITuner::tune,  getProgramInformation and cancel methods
 *
 * Verifies that:
 *  - the HAL implements the methods
 *  - the methods return 0 (no error)
 *  - the tuned callback is received within kTuneCallbacktimeoutNs ns after tune()
 *
 * Skipped for other radio classes than AM/FM, because tune to frequency
 * is not possible on DAB nor satellite.
 */
TEST_P(BroadcastRadioHidlTest, TuneAndGetProgramInformationAndCancel) {
    if (radioClass != Class::AM_FM) skipped = true;
    RETURN_IF_SKIPPED;
    ASSERT_EQ(true, openTuner());
    ASSERT_TRUE(checkAntenna());

    auto& band = getBand(0);

    // test tune
    ASSERT_GT(band.spacings.size(), 0u);
    ASSERT_GT(band.upperLimit, band.lowerLimit);

    // test scan UP
    uint32_t lowerLimit = band.lowerLimit;
    uint32_t upperLimit = band.upperLimit;
    uint32_t spacing = band.spacings[0];

    uint32_t channel =
            lowerLimit + (((upperLimit - lowerLimit) / 2 + spacing - 1) / spacing) * spacing;
    mCallbackCalled = false;
    mResultCallbackData = Result::NOT_INITIALIZED;
    Return<Result> hidlResult = mTuner->tune(channel, 0);
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));
    EXPECT_EQ(channel, mProgramInfoCallbackData.channel);

    // test getProgramInformation
    ProgramInfo halInfo;
    Result halResult = Result::NOT_INITIALIZED;
    Return<void> hidlReturn = mTuner->getProgramInformation(
        [&](Result result, const ProgramInfo& info) {
            halResult = result;
            if (result == Result::OK) {
                halInfo = info;
            }
        });
    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_EQ(Result::OK, halResult);
    if (mResultCallbackData == Result::OK) {
        EXPECT_LE(halInfo.channel, upperLimit);
        EXPECT_GE(halInfo.channel, lowerLimit);
    }

    // test cancel
    mTuner->tune(lowerLimit, 0);
    hidlResult = mTuner->cancel();
    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, hidlResult);
}

/**
 * Test ITuner::tune failing when channel out of the range is provided.
 *
 * Verifies that:
 *  - the method returns INVALID_ARGUMENTS when applicable
 *  - the method recovers and succeeds after passing correct arguments
 *
 * Skipped for other radio classes than AM/FM, because tune to frequency
 * is not possible on DAB nor satellite.
 */
TEST_P(BroadcastRadioHidlTest, TuneFailsOutOfBounds) {
    if (radioClass != Class::AM_FM) skipped = true;
    RETURN_IF_SKIPPED;
    ASSERT_TRUE(openTuner());
    ASSERT_TRUE(checkAntenna());

    // get current channel bounds
    BandConfig halConfig;
    Result halResult;
    auto configResult = mTuner->getConfiguration([&](Result result, const BandConfig& config) {
        halResult = result;
        halConfig = config;
    });
    ASSERT_TRUE(configResult.isOk());
    ASSERT_EQ(Result::OK, halResult);

    // try to tune slightly above the limit and expect to fail
    auto badChannel = halConfig.upperLimit + halConfig.spacings[0];
    auto tuneResult = mTuner->tune(badChannel, 0);
    EXPECT_TRUE(tuneResult.isOk());
    EXPECT_EQ(Result::INVALID_ARGUMENTS, tuneResult);
    EXPECT_TRUE(waitForCallback(kTuneCallbacktimeoutNs));

    // tuning exactly at the limit should succeed
    auto goodChannel = halConfig.upperLimit;
    tuneResult = mTuner->tune(goodChannel, 0);
    EXPECT_TRUE(tuneResult.isOk());
    EXPECT_EQ(Result::OK, tuneResult);
    EXPECT_TRUE(waitForCallback(kTuneCallbacktimeoutNs));
}

/**
 * Test proper image format in metadata.
 *
 * Verifies that:
 * - all images in metadata are provided in-band (as a binary blob, not by id)
 *
 * This is a counter-test for OobImagesOnly from 1.1 VTS.
 */
TEST_P(BroadcastRadioHidlTest, IbImagesOnly) {
    RETURN_IF_SKIPPED;
    ASSERT_TRUE(openTuner());
    ASSERT_TRUE(checkAntenna());

    bool firstScan = true;
    uint32_t firstChannel, prevChannel;
    while (true) {
        mCallbackCalled = false;
        auto hidlResult = mTuner->scan(Direction::UP, true);
        ASSERT_TRUE(hidlResult.isOk());
        if (hidlResult == Result::TIMEOUT) {
            ALOGI("Got timeout on scan operation");
            break;
        }
        ASSERT_EQ(Result::OK, hidlResult);
        ASSERT_EQ(true, waitForCallback(kTuneCallbacktimeoutNs));

        if (firstScan) {
            firstScan = false;
            firstChannel = mProgramInfoCallbackData.channel;
        } else {
            // scanned the whole band
            if (mProgramInfoCallbackData.channel >= firstChannel && prevChannel <= firstChannel) {
                break;
            }
        }
        prevChannel = mProgramInfoCallbackData.channel;

        for (auto&& entry : mProgramInfoCallbackData.metadata) {
            if (entry.key != MetadataKey::ICON && entry.key != MetadataKey::ART) continue;
            EXPECT_EQ(MetadataType::RAW, entry.type);
            EXPECT_EQ(0, entry.intValue);
            EXPECT_GT(entry.rawValue.size(), 0u);
        }
    }
}

INSTANTIATE_TEST_CASE_P(
    BroadcastRadioHidlTestCases,
    BroadcastRadioHidlTest,
    ::testing::Values(Class::AM_FM, Class::SAT, Class::DT));

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
