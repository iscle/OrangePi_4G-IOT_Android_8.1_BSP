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

#define LOG_TAG "broadcastradio.vts"

#include <VtsHalHidlTargetTestBase.h>
#include <android/hardware/broadcastradio/1.1/IBroadcastRadio.h>
#include <android/hardware/broadcastradio/1.1/IBroadcastRadioFactory.h>
#include <android/hardware/broadcastradio/1.1/ITuner.h>
#include <android/hardware/broadcastradio/1.1/ITunerCallback.h>
#include <android/hardware/broadcastradio/1.1/types.h>
#include <android-base/logging.h>
#include <broadcastradio-utils/Utils.h>
#include <broadcastradio-vts-utils/call-barrier.h>
#include <broadcastradio-vts-utils/mock-timeout.h>
#include <cutils/native_handle.h>
#include <cutils/properties.h>
#include <gmock/gmock.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/threads.h>

#include <chrono>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace vts {

using namespace std::chrono_literals;

using testing::_;
using testing::AnyNumber;
using testing::ByMove;
using testing::DoAll;
using testing::Invoke;
using testing::SaveArg;

using broadcastradio::vts::CallBarrier;
using V1_0::BandConfig;
using V1_0::Class;
using V1_0::MetaData;
using V1_0::MetadataKey;
using V1_0::MetadataType;

using std::chrono::steady_clock;
using std::this_thread::sleep_for;

static constexpr auto kConfigTimeout = 10s;
static constexpr auto kConnectModuleTimeout = 1s;
static constexpr auto kTuneTimeout = 30s;
static constexpr auto kEventPropagationTimeout = 1s;
static constexpr auto kFullScanTimeout = 1min;

static constexpr ProgramType kStandardProgramTypes[] = {
    ProgramType::AM,  ProgramType::FM,   ProgramType::AM_HD, ProgramType::FM_HD,
    ProgramType::DAB, ProgramType::DRMO, ProgramType::SXM};

static void printSkipped(std::string msg) {
    std::cout << "[  SKIPPED ] " << msg << std::endl;
}

struct TunerCallbackMock : public ITunerCallback {
    TunerCallbackMock() { EXPECT_CALL(*this, hardwareFailure()).Times(0); }

    MOCK_METHOD0(hardwareFailure, Return<void>());
    MOCK_TIMEOUT_METHOD2(configChange, Return<void>(Result, const BandConfig&));
    MOCK_METHOD2(tuneComplete, Return<void>(Result, const V1_0::ProgramInfo&));
    MOCK_TIMEOUT_METHOD2(tuneComplete_1_1, Return<void>(Result, const ProgramSelector&));
    MOCK_METHOD1(afSwitch, Return<void>(const V1_0::ProgramInfo&));
    MOCK_METHOD1(antennaStateChange, Return<void>(bool connected));
    MOCK_METHOD1(trafficAnnouncement, Return<void>(bool active));
    MOCK_METHOD1(emergencyAnnouncement, Return<void>(bool active));
    MOCK_METHOD3(newMetadata, Return<void>(uint32_t ch, uint32_t subCh, const hidl_vec<MetaData>&));
    MOCK_METHOD1(backgroundScanAvailable, Return<void>(bool));
    MOCK_TIMEOUT_METHOD1(backgroundScanComplete, Return<void>(ProgramListResult));
    MOCK_METHOD0(programListChanged, Return<void>());
    MOCK_TIMEOUT_METHOD1(currentProgramInfoChanged, Return<void>(const ProgramInfo&));
};

class BroadcastRadioHalTest : public ::testing::VtsHalHidlTargetTestBase,
                              public ::testing::WithParamInterface<Class> {
   protected:
    virtual void SetUp() override;
    virtual void TearDown() override;

    bool openTuner();
    bool nextBand();
    bool getProgramList(std::function<void(const hidl_vec<ProgramInfo>& list)> cb);

    Class radioClass;
    bool skipped = false;

    sp<IBroadcastRadio> mRadioModule;
    sp<ITuner> mTuner;
    sp<TunerCallbackMock> mCallback = new TunerCallbackMock();

   private:
    const BandConfig& getBand(unsigned idx);

    unsigned currentBandIndex = 0;
    hidl_vec<BandConfig> mBands;
};

/**
 * Clears strong pointer and waits until the object gets destroyed.
 *
 * @param ptr The pointer to get cleared.
 * @param timeout Time to wait for other references.
 */
template <typename T>
static void clearAndWait(sp<T>& ptr, std::chrono::milliseconds timeout) {
    wp<T> wptr = ptr;
    ptr.clear();
    auto limit = steady_clock::now() + timeout;
    while (wptr.promote() != nullptr) {
        constexpr auto step = 10ms;
        if (steady_clock::now() + step > limit) {
            FAIL() << "Pointer was not released within timeout";
            break;
        }
        sleep_for(step);
    }
}

void BroadcastRadioHalTest::SetUp() {
    radioClass = GetParam();

    // lookup HIDL service
    auto factory = getService<IBroadcastRadioFactory>();
    ASSERT_NE(nullptr, factory.get());

    // connect radio module
    Result connectResult;
    CallBarrier onConnect;
    factory->connectModule(radioClass, [&](Result ret, const sp<V1_0::IBroadcastRadio>& radio) {
        connectResult = ret;
        if (ret == Result::OK) mRadioModule = IBroadcastRadio::castFrom(radio);
        onConnect.call();
    });
    ASSERT_TRUE(onConnect.waitForCall(kConnectModuleTimeout));

    if (connectResult == Result::INVALID_ARGUMENTS) {
        printSkipped("This device class is not supported.");
        skipped = true;
        return;
    }
    ASSERT_EQ(connectResult, Result::OK);
    ASSERT_NE(nullptr, mRadioModule.get());

    // get module properties
    Properties prop11;
    auto& prop10 = prop11.base;
    auto propResult =
        mRadioModule->getProperties_1_1([&](const Properties& properties) { prop11 = properties; });

    ASSERT_TRUE(propResult.isOk());
    EXPECT_EQ(radioClass, prop10.classId);
    EXPECT_GT(prop10.numTuners, 0u);
    EXPECT_GT(prop11.supportedProgramTypes.size(), 0u);
    EXPECT_GT(prop11.supportedIdentifierTypes.size(), 0u);
    if (radioClass == Class::AM_FM) {
        EXPECT_GT(prop10.bands.size(), 0u);
    }
    mBands = prop10.bands;
}

void BroadcastRadioHalTest::TearDown() {
    mTuner.clear();
    mRadioModule.clear();
    clearAndWait(mCallback, 1s);
}

bool BroadcastRadioHalTest::openTuner() {
    EXPECT_EQ(nullptr, mTuner.get());

    if (radioClass == Class::AM_FM) {
        EXPECT_TIMEOUT_CALL(*mCallback, configChange, Result::OK, _);
    }

    Result halResult = Result::NOT_INITIALIZED;
    auto openCb = [&](Result result, const sp<V1_0::ITuner>& tuner) {
        halResult = result;
        if (result != Result::OK) return;
        mTuner = ITuner::castFrom(tuner);
    };
    currentBandIndex = 0;
    auto hidlResult = mRadioModule->openTuner(getBand(0), true, mCallback, openCb);

    EXPECT_TRUE(hidlResult.isOk());
    EXPECT_EQ(Result::OK, halResult);
    EXPECT_NE(nullptr, mTuner.get());
    if (radioClass == Class::AM_FM && mTuner != nullptr) {
        EXPECT_TIMEOUT_CALL_WAIT(*mCallback, configChange, kConfigTimeout);

        BandConfig halConfig;
        Result halResult = Result::NOT_INITIALIZED;
        mTuner->getConfiguration([&](Result result, const BandConfig& config) {
            halResult = result;
            halConfig = config;
        });
        EXPECT_EQ(Result::OK, halResult);
        EXPECT_TRUE(halConfig.antennaConnected);
    }

    EXPECT_NE(nullptr, mTuner.get());
    return nullptr != mTuner.get();
}

const BandConfig& BroadcastRadioHalTest::getBand(unsigned idx) {
    static const BandConfig dummyBandConfig = {};

    if (radioClass != Class::AM_FM) {
        ALOGD("Not AM/FM radio, returning dummy band config");
        return dummyBandConfig;
    }

    EXPECT_GT(mBands.size(), idx);
    if (mBands.size() <= idx) {
        ALOGD("Band index out of bound, returning dummy band config");
        return dummyBandConfig;
    }

    auto& band = mBands[idx];
    ALOGD("Returning %s band", toString(band.type).c_str());
    return band;
}

bool BroadcastRadioHalTest::nextBand() {
    if (currentBandIndex + 1 >= mBands.size()) return false;
    currentBandIndex++;

    BandConfig bandCb;
    EXPECT_TIMEOUT_CALL(*mCallback, configChange, Result::OK, _)
        .WillOnce(DoAll(SaveArg<1>(&bandCb), testing::Return(ByMove(Void()))));
    auto hidlResult = mTuner->setConfiguration(getBand(currentBandIndex));
    EXPECT_EQ(Result::OK, hidlResult);
    EXPECT_TIMEOUT_CALL_WAIT(*mCallback, configChange, kConfigTimeout);
    EXPECT_EQ(getBand(currentBandIndex), bandCb);

    return true;
}

bool BroadcastRadioHalTest::getProgramList(
    std::function<void(const hidl_vec<ProgramInfo>& list)> cb) {
    ProgramListResult getListResult = ProgramListResult::NOT_INITIALIZED;
    bool isListEmpty = true;
    auto getListCb = [&](ProgramListResult result, const hidl_vec<ProgramInfo>& list) {
        ALOGD("getListCb(%s, ProgramInfo[%zu])", toString(result).c_str(), list.size());
        getListResult = result;
        if (result != ProgramListResult::OK) return;
        isListEmpty = (list.size() == 0);
        if (!isListEmpty) cb(list);
    };

    // first try...
    EXPECT_TIMEOUT_CALL(*mCallback, backgroundScanComplete, ProgramListResult::OK)
        .Times(AnyNumber());
    auto hidlResult = mTuner->getProgramList({}, getListCb);
    EXPECT_TRUE(hidlResult.isOk());
    if (!hidlResult.isOk()) return false;

    if (getListResult == ProgramListResult::NOT_STARTED) {
        auto result = mTuner->startBackgroundScan();
        EXPECT_EQ(ProgramListResult::OK, result);
        getListResult = ProgramListResult::NOT_READY;  // continue as in NOT_READY case
    }
    if (getListResult == ProgramListResult::NOT_READY) {
        EXPECT_TIMEOUT_CALL_WAIT(*mCallback, backgroundScanComplete, kFullScanTimeout);

        // second (last) try...
        hidlResult = mTuner->getProgramList({}, getListCb);
        EXPECT_TRUE(hidlResult.isOk());
        if (!hidlResult.isOk()) return false;
        EXPECT_EQ(ProgramListResult::OK, getListResult);
    }

    return !isListEmpty;
}

/**
 * Test IBroadcastRadio::openTuner() method called twice.
 *
 * Verifies that:
 *  - the openTuner method succeeds when called for the second time without
 *    deleting previous ITuner instance.
 *
 * This is a more strict requirement than in 1.0, where a second openTuner
 * might fail.
 */
TEST_P(BroadcastRadioHalTest, OpenTunerTwice) {
    if (skipped) return;

    ASSERT_TRUE(openTuner());

    auto secondTuner = mTuner;
    mTuner.clear();

    ASSERT_TRUE(openTuner());
}

/**
 * Test tuning to program list entry.
 *
 * Verifies that:
 *  - getProgramList either succeeds or returns NOT_STARTED/NOT_READY status;
 *  - if the program list is NOT_STARTED, startBackgroundScan makes it completed
 *    within a full scan timeout and the next getProgramList call succeeds;
 *  - if the program list is not empty, tuneByProgramSelector call succeeds;
 *  - getProgramInformation_1_1 returns the same selector as returned in tuneComplete_1_1 call.
 */
TEST_P(BroadcastRadioHalTest, TuneFromProgramList) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    ProgramInfo firstProgram;
    bool foundAny = false;
    do {
        auto getCb = [&](const hidl_vec<ProgramInfo>& list) {
            // don't copy the whole list out, it might be heavy
            firstProgram = list[0];
        };
        if (getProgramList(getCb)) foundAny = true;
    } while (nextBand());
    if (HasFailure()) return;
    if (!foundAny) {
        printSkipped("Program list is empty.");
        return;
    }

    ProgramInfo infoCb;
    ProgramSelector selCb;
    EXPECT_CALL(*mCallback, tuneComplete(_, _)).Times(0);
    EXPECT_TIMEOUT_CALL(*mCallback, tuneComplete_1_1, Result::OK, _)
        .WillOnce(DoAll(SaveArg<1>(&selCb), testing::Return(ByMove(Void()))));
    EXPECT_TIMEOUT_CALL(*mCallback, currentProgramInfoChanged, _)
        .WillOnce(DoAll(SaveArg<0>(&infoCb), testing::Return(ByMove(Void()))));
    auto tuneResult = mTuner->tuneByProgramSelector(firstProgram.selector);
    ASSERT_EQ(Result::OK, tuneResult);
    EXPECT_TIMEOUT_CALL_WAIT(*mCallback, tuneComplete_1_1, kTuneTimeout);
    EXPECT_TIMEOUT_CALL_WAIT(*mCallback, currentProgramInfoChanged, kEventPropagationTimeout);
    EXPECT_EQ(firstProgram.selector.primaryId, selCb.primaryId);
    EXPECT_EQ(infoCb.selector, selCb);

    bool called = false;
    auto getResult = mTuner->getProgramInformation_1_1([&](Result result, ProgramInfo info) {
        called = true;
        EXPECT_EQ(Result::OK, result);
        EXPECT_EQ(selCb, info.selector);
    });
    ASSERT_TRUE(getResult.isOk());
    ASSERT_TRUE(called);
}

/**
 * Test that primary vendor identifier isn't used for standard program types.
 *
 * Verifies that:
 *  - tuneByProgramSelector fails when VENDORn_PRIMARY is set as a primary
 *    identifier for program types other than VENDORn.
 */
TEST_P(BroadcastRadioHalTest, TuneFailsForPrimaryVendor) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    for (auto ptype : kStandardProgramTypes) {
        ALOGD("Checking %s...", toString(ptype).c_str());
        ProgramSelector sel = {};
        sel.programType = static_cast<uint32_t>(ptype);
        sel.primaryId.type = static_cast<uint32_t>(IdentifierType::VENDOR_PRIMARY_START);

        auto tuneResult = mTuner->tuneByProgramSelector(sel);
        ASSERT_NE(Result::OK, tuneResult);
    }
}

/**
 * Test that tune with unknown program type fails.
 *
 * Verifies that:
 *  - tuneByProgramSelector fails with INVALID_ARGUMENT when unknown program type is passed.
 */
TEST_P(BroadcastRadioHalTest, TuneFailsForUnknownProgram) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    // Program type is 1-based, so 0 will be always invalid.
    ProgramSelector sel = {};
    auto tuneResult = mTuner->tuneByProgramSelector(sel);
    ASSERT_EQ(Result::INVALID_ARGUMENTS, tuneResult);
}

/**
 * Test cancelling announcement.
 *
 * Verifies that:
 *  - cancelAnnouncement succeeds either when there is an announcement or there is none.
 */
TEST_P(BroadcastRadioHalTest, CancelAnnouncement) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    auto hidlResult = mTuner->cancelAnnouncement();
    EXPECT_EQ(Result::OK, hidlResult);
}

/**
 * Test getImage call with invalid image ID.
 *
 * Verifies that:
 * - getImage call handles argument 0 gracefully.
 */
TEST_P(BroadcastRadioHalTest, GetNoImage) {
    if (skipped) return;

    size_t len = 0;
    auto hidlResult =
        mRadioModule->getImage(0, [&](hidl_vec<uint8_t> rawImage) { len = rawImage.size(); });

    ASSERT_TRUE(hidlResult.isOk());
    ASSERT_EQ(0u, len);
}

/**
 * Test proper image format in metadata.
 *
 * Verifies that:
 * - all images in metadata are provided out-of-band (by id, not as a binary blob);
 * - images are available for getImage call.
 */
TEST_P(BroadcastRadioHalTest, OobImagesOnly) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    std::vector<int> imageIds;

    do {
        auto getCb = [&](const hidl_vec<ProgramInfo>& list) {
            for (auto&& program : list) {
                for (auto&& entry : program.base.metadata) {
                    EXPECT_NE(MetadataType::RAW, entry.type);
                    if (entry.key != MetadataKey::ICON && entry.key != MetadataKey::ART) continue;
                    EXPECT_NE(0, entry.intValue);
                    EXPECT_EQ(0u, entry.rawValue.size());
                    if (entry.intValue != 0) imageIds.push_back(entry.intValue);
                }
            }
        };
        getProgramList(getCb);
    } while (nextBand());

    if (imageIds.size() == 0) {
        printSkipped("No images found");
        return;
    }

    for (auto id : imageIds) {
        ALOGD("Checking image %d", id);

        size_t len = 0;
        auto hidlResult =
            mRadioModule->getImage(id, [&](hidl_vec<uint8_t> rawImage) { len = rawImage.size(); });

        ASSERT_TRUE(hidlResult.isOk());
        ASSERT_GT(len, 0u);
    }
}

/**
 * Test AnalogForced switch.
 *
 * Verifies that:
 * - setAnalogForced results either with INVALID_STATE, or isAnalogForced replying the same.
 */
TEST_P(BroadcastRadioHalTest, AnalogForcedSwitch) {
    if (skipped) return;
    ASSERT_TRUE(openTuner());

    bool forced;
    Result halIsResult;
    auto isCb = [&](Result result, bool isForced) {
        halIsResult = result;
        forced = isForced;
    };

    // set analog mode
    auto setResult = mTuner->setAnalogForced(true);
    ASSERT_TRUE(setResult.isOk());
    if (Result::INVALID_STATE == setResult) {
        // if setter fails, getter should fail too - it means the switch is not supported at all
        auto isResult = mTuner->isAnalogForced(isCb);
        ASSERT_TRUE(isResult.isOk());
        EXPECT_EQ(Result::INVALID_STATE, halIsResult);
        return;
    }
    ASSERT_EQ(Result::OK, setResult);

    // check, if it's analog
    auto isResult = mTuner->isAnalogForced(isCb);
    ASSERT_TRUE(isResult.isOk());
    EXPECT_EQ(Result::OK, halIsResult);
    ASSERT_TRUE(forced);

    // set digital mode
    setResult = mTuner->setAnalogForced(false);
    ASSERT_EQ(Result::OK, setResult);

    // check, if it's digital
    isResult = mTuner->isAnalogForced(isCb);
    ASSERT_TRUE(isResult.isOk());
    EXPECT_EQ(Result::OK, halIsResult);
    ASSERT_FALSE(forced);
}

INSTANTIATE_TEST_CASE_P(BroadcastRadioHalTestCases, BroadcastRadioHalTest,
                        ::testing::Values(Class::AM_FM, Class::SAT, Class::DT));

}  // namespace vts
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
