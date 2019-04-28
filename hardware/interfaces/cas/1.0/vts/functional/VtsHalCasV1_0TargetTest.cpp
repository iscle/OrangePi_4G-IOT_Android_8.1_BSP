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

#define LOG_TAG "mediacas_hidl_hal_test"

#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <android/hardware/cas/1.0/ICas.h>
#include <android/hardware/cas/1.0/ICasListener.h>
#include <android/hardware/cas/1.0/IDescramblerBase.h>
#include <android/hardware/cas/1.0/IMediaCasService.h>
#include <android/hardware/cas/1.0/types.h>
#include <android/hardware/cas/native/1.0/IDescrambler.h>
#include <android/hardware/cas/native/1.0/types.h>
#include <binder/MemoryDealer.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <hidl/Status.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>

#define CLEAR_KEY_SYSTEM_ID 0xF6D8
#define INVALID_SYSTEM_ID 0
#define WAIT_TIMEOUT 3000000000

#define PROVISION_STR                                      \
    "{                                                   " \
    "  \"id\": 21140844,                                 " \
    "  \"name\": \"Test Title\",                         " \
    "  \"lowercase_organization_name\": \"Android\",     " \
    "  \"asset_key\": {                                  " \
    "  \"encryption_key\": \"nezAr3CHFrmBR9R8Tedotw==\"  " \
    "  },                                                " \
    "  \"cas_type\": 1,                                  " \
    "  \"track_types\": [ ]                              " \
    "}                                                   "

using android::Condition;
using android::hardware::cas::V1_0::ICas;
using android::hardware::cas::V1_0::ICasListener;
using android::hardware::cas::V1_0::IDescramblerBase;
using android::hardware::cas::native::V1_0::IDescrambler;
using android::hardware::cas::native::V1_0::SubSample;
using android::hardware::cas::native::V1_0::SharedBuffer;
using android::hardware::cas::native::V1_0::DestinationBuffer;
using android::hardware::cas::native::V1_0::BufferType;
using android::hardware::cas::native::V1_0::ScramblingControl;
using android::hardware::cas::V1_0::IMediaCasService;
using android::hardware::cas::V1_0::HidlCasPluginDescriptor;
using android::hardware::Void;
using android::hardware::hidl_vec;
using android::hardware::hidl_string;
using android::hardware::hidl_handle;
using android::hardware::hidl_memory;
using android::hardware::Return;
using android::hardware::cas::V1_0::Status;
using android::IMemory;
using android::IMemoryHeap;
using android::MemoryDealer;
using android::Mutex;
using android::sp;

namespace {

const uint8_t kEcmBinaryBuffer[] = {
    0x00, 0x00, 0x01, 0xf0, 0x00, 0x50, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x46, 0x00,
    0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x27, 0x10, 0x02, 0x00,
    0x01, 0x77, 0x01, 0x42, 0x95, 0x6c, 0x0e, 0xe3, 0x91, 0xbc, 0xfd, 0x05, 0xb1, 0x60, 0x4f,
    0x17, 0x82, 0xa4, 0x86, 0x9b, 0x23, 0x56, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
    0x27, 0x10, 0x02, 0x00, 0x01, 0x77, 0x01, 0x42, 0x95, 0x6c, 0xd7, 0x43, 0x62, 0xf8, 0x1c,
    0x62, 0x19, 0x05, 0xc7, 0x3a, 0x42, 0xcd, 0xfd, 0xd9, 0x13, 0x48,
};

const SubSample kSubSamples[] = {{162, 0}, {0, 184}, {0, 184}};

const uint8_t kInBinaryBuffer[] = {
    0x00, 0x00, 0x00, 0x01, 0x09, 0xf0, 0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0xc0, 0x1e, 0xdb, 0x01,
    0x40, 0x16, 0xec, 0x04, 0x40, 0x00, 0x00, 0x03, 0x00, 0x40, 0x00, 0x00, 0x0f, 0x03, 0xc5, 0x8b,
    0xb8, 0x00, 0x00, 0x00, 0x01, 0x68, 0xca, 0x8c, 0xb2, 0x00, 0x00, 0x01, 0x06, 0x05, 0xff, 0xff,
    0x70, 0xdc, 0x45, 0xe9, 0xbd, 0xe6, 0xd9, 0x48, 0xb7, 0x96, 0x2c, 0xd8, 0x20, 0xd9, 0x23, 0xee,
    0xef, 0x78, 0x32, 0x36, 0x34, 0x20, 0x2d, 0x20, 0x63, 0x6f, 0x72, 0x65, 0x20, 0x31, 0x34, 0x32,
    0x20, 0x2d, 0x20, 0x48, 0x2e, 0x32, 0x36, 0x34, 0x2f, 0x4d, 0x50, 0x45, 0x47, 0x2d, 0x34, 0x20,
    0x41, 0x56, 0x43, 0x20, 0x63, 0x6f, 0x64, 0x65, 0x63, 0x20, 0x2d, 0x20, 0x43, 0x6f, 0x70, 0x79,
    0x6c, 0x65, 0x66, 0x74, 0x20, 0x32, 0x30, 0x30, 0x33, 0x2d, 0x32, 0x30, 0x31, 0x34, 0x20, 0x2d,
    0x20, 0x68, 0x74, 0x74, 0x70, 0x3a, 0x2f, 0x2f, 0x77, 0x77, 0x77, 0x2e, 0x76, 0x69, 0x64, 0x65,
    0x6f, 0x6c, 0x61, 0x6e, 0x2e, 0x6f, 0x72, 0x67, 0x2f, 0x78, 0x32, 0x36, 0x34, 0x2e, 0x68, 0x74,
    0x6d, 0x6c, 0x6e, 0x45, 0x21, 0x82, 0x38, 0xf0, 0x9d, 0x7d, 0x96, 0xe6, 0x94, 0xae, 0xe2, 0x87,
    0x8f, 0x04, 0x49, 0xe5, 0xf6, 0x8c, 0x8b, 0x9a, 0x10, 0x18, 0xba, 0x94, 0xe9, 0x22, 0x31, 0x04,
    0x7e, 0x60, 0x5b, 0xc4, 0x24, 0x00, 0x90, 0x62, 0x0d, 0xdc, 0x85, 0x74, 0x75, 0x78, 0xd0, 0x14,
    0x08, 0xcb, 0x02, 0x1d, 0x7d, 0x9d, 0x34, 0xe8, 0x81, 0xb9, 0xf7, 0x09, 0x28, 0x79, 0x29, 0x8d,
    0xe3, 0x14, 0xed, 0x5f, 0xca, 0xaf, 0xf4, 0x1c, 0x49, 0x15, 0xe1, 0x80, 0x29, 0x61, 0x76, 0x80,
    0x43, 0xf8, 0x58, 0x53, 0x40, 0xd7, 0x31, 0x6d, 0x61, 0x81, 0x41, 0xe9, 0x77, 0x9f, 0x9c, 0xe1,
    0x6d, 0xf2, 0xee, 0xd9, 0xc8, 0x67, 0xd2, 0x5f, 0x48, 0x73, 0xe3, 0x5c, 0xcd, 0xa7, 0x45, 0x58,
    0xbb, 0xdd, 0x28, 0x1d, 0x68, 0xfc, 0xb4, 0xc6, 0xf6, 0x92, 0xf6, 0x30, 0x03, 0xaa, 0xe4, 0x32,
    0xf6, 0x34, 0x51, 0x4b, 0x0f, 0x8c, 0xf9, 0xac, 0x98, 0x22, 0xfb, 0x49, 0xc8, 0xbf, 0xca, 0x8c,
    0x80, 0x86, 0x5d, 0xd7, 0xa4, 0x52, 0xb1, 0xd9, 0xa6, 0x04, 0x4e, 0xb3, 0x2d, 0x1f, 0xb8, 0x35,
    0xcc, 0x45, 0x6d, 0x9c, 0x20, 0xa7, 0xa4, 0x34, 0x59, 0x72, 0xe3, 0xae, 0xba, 0x49, 0xde, 0xd1,
    0xaa, 0xee, 0x3d, 0x77, 0xfc, 0x5d, 0xc6, 0x1f, 0x9d, 0xac, 0xc2, 0x15, 0x66, 0xb8, 0xe1, 0x54,
    0x4e, 0x74, 0x93, 0xdb, 0x9a, 0x24, 0x15, 0x6e, 0x20, 0xa3, 0x67, 0x3e, 0x5a, 0x24, 0x41, 0x5e,
    0xb0, 0xe6, 0x35, 0x87, 0x1b, 0xc8, 0x7a, 0xf9, 0x77, 0x65, 0xe0, 0x01, 0xf2, 0x4c, 0xe4, 0x2b,
    0xa9, 0x64, 0x96, 0x96, 0x0b, 0x46, 0xca, 0xea, 0x79, 0x0e, 0x78, 0xa3, 0x5f, 0x43, 0xfc, 0x47,
    0x6a, 0x12, 0xfa, 0xc4, 0x33, 0x0e, 0x88, 0x1c, 0x19, 0x3a, 0x00, 0xc3, 0x4e, 0xb5, 0xd8, 0xfa,
    0x8e, 0xf1, 0xbc, 0x3d, 0xb2, 0x7e, 0x50, 0x8d, 0x67, 0xc3, 0x6b, 0xed, 0xe2, 0xea, 0xa6, 0x1f,
    0x25, 0x24, 0x7c, 0x94, 0x74, 0x50, 0x49, 0xe3, 0xc6, 0x58, 0x2e, 0xfd, 0x28, 0xb4, 0xc6, 0x73,
    0xb1, 0x53, 0x74, 0x27, 0x94, 0x5c, 0xdf, 0x69, 0xb7, 0xa1, 0xd7, 0xf5, 0xd3, 0x8a, 0x2c, 0x2d,
    0xb4, 0x5e, 0x8a, 0x16, 0x14, 0x54, 0x64, 0x6e, 0x00, 0x6b, 0x11, 0x59, 0x8a, 0x63, 0x38, 0x80,
    0x76, 0xc3, 0xd5, 0x59, 0xf7, 0x3f, 0xd2, 0xfa, 0xa5, 0xca, 0x82, 0xff, 0x4a, 0x62, 0xf0, 0xe3,
    0x42, 0xf9, 0x3b, 0x38, 0x27, 0x8a, 0x89, 0xaa, 0x50, 0x55, 0x4b, 0x29, 0xf1, 0x46, 0x7c, 0x75,
    0xef, 0x65, 0xaf, 0x9b, 0x0d, 0x6d, 0xda, 0x25, 0x94, 0x14, 0xc1, 0x1b, 0xf0, 0xc5, 0x4c, 0x24,
    0x0e, 0x65,
};

const uint8_t kOutRefBinaryBuffer[] = {
    0x00, 0x00, 0x00, 0x01, 0x09, 0xf0, 0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0xc0, 0x1e, 0xdb, 0x01,
    0x40, 0x16, 0xec, 0x04, 0x40, 0x00, 0x00, 0x03, 0x00, 0x40, 0x00, 0x00, 0x0f, 0x03, 0xc5, 0x8b,
    0xb8, 0x00, 0x00, 0x00, 0x01, 0x68, 0xca, 0x8c, 0xb2, 0x00, 0x00, 0x01, 0x06, 0x05, 0xff, 0xff,
    0x70, 0xdc, 0x45, 0xe9, 0xbd, 0xe6, 0xd9, 0x48, 0xb7, 0x96, 0x2c, 0xd8, 0x20, 0xd9, 0x23, 0xee,
    0xef, 0x78, 0x32, 0x36, 0x34, 0x20, 0x2d, 0x20, 0x63, 0x6f, 0x72, 0x65, 0x20, 0x31, 0x34, 0x32,
    0x20, 0x2d, 0x20, 0x48, 0x2e, 0x32, 0x36, 0x34, 0x2f, 0x4d, 0x50, 0x45, 0x47, 0x2d, 0x34, 0x20,
    0x41, 0x56, 0x43, 0x20, 0x63, 0x6f, 0x64, 0x65, 0x63, 0x20, 0x2d, 0x20, 0x43, 0x6f, 0x70, 0x79,
    0x6c, 0x65, 0x66, 0x74, 0x20, 0x32, 0x30, 0x30, 0x33, 0x2d, 0x32, 0x30, 0x31, 0x34, 0x20, 0x2d,
    0x20, 0x68, 0x74, 0x74, 0x70, 0x3a, 0x2f, 0x2f, 0x77, 0x77, 0x77, 0x2e, 0x76, 0x69, 0x64, 0x65,
    0x6f, 0x6c, 0x61, 0x6e, 0x2e, 0x6f, 0x72, 0x67, 0x2f, 0x78, 0x32, 0x36, 0x34, 0x2e, 0x68, 0x74,
    0x6d, 0x6c, 0x20, 0x2d, 0x20, 0x6f, 0x70, 0x74, 0x69, 0x6f, 0x6e, 0x73, 0x3a, 0x20, 0x63, 0x61,
    0x62, 0x61, 0x63, 0x3d, 0x30, 0x20, 0x72, 0x65, 0x66, 0x3d, 0x32, 0x20, 0x64, 0x65, 0x62, 0x6c,
    0x6f, 0x63, 0x6b, 0x3d, 0x31, 0x3a, 0x30, 0x3a, 0x30, 0x20, 0x61, 0x6e, 0x61, 0x6c, 0x79, 0x73,
    0x65, 0x3d, 0x30, 0x78, 0x31, 0x3a, 0x30, 0x78, 0x31, 0x31, 0x31, 0x20, 0x6d, 0x65, 0x3d, 0x68,
    0x65, 0x78, 0x20, 0x73, 0x75, 0x62, 0x6d, 0x65, 0x3d, 0x37, 0x20, 0x70, 0x73, 0x79, 0x3d, 0x31,
    0x20, 0x70, 0x73, 0x79, 0x5f, 0x72, 0x64, 0x3d, 0x31, 0x2e, 0x30, 0x30, 0x3a, 0x30, 0x2e, 0x30,
    0x30, 0x20, 0x6d, 0x69, 0x78, 0x65, 0x64, 0x5f, 0x72, 0x65, 0x66, 0x3d, 0x31, 0x20, 0x6d, 0x65,
    0x5f, 0x72, 0x61, 0x6e, 0x67, 0x65, 0x3d, 0x31, 0x36, 0x20, 0x63, 0x68, 0x72, 0x6f, 0x6d, 0x61,
    0x5f, 0x6d, 0x65, 0x3d, 0x31, 0x20, 0x74, 0x72, 0x65, 0x6c, 0x6c, 0x69, 0x73, 0x3d, 0x31, 0x20,
    0x38, 0x78, 0x38, 0x64, 0x63, 0x74, 0x3d, 0x30, 0x20, 0x63, 0x71, 0x6d, 0x3d, 0x30, 0x20, 0x64,
    0x65, 0x61, 0x64, 0x7a, 0x6f, 0x6e, 0x65, 0x3d, 0x32, 0x31, 0x2c, 0x31, 0x31, 0x20, 0x66, 0x61,
    0x73, 0x74, 0x5f, 0x70, 0x73, 0x6b, 0x69, 0x70, 0x3d, 0x31, 0x20, 0x63, 0x68, 0x72, 0x6f, 0x6d,
    0x61, 0x5f, 0x71, 0x70, 0x5f, 0x6f, 0x66, 0x66, 0x73, 0x65, 0x74, 0x3d, 0x2d, 0x32, 0x20, 0x74,
    0x68, 0x72, 0x65, 0x61, 0x64, 0x73, 0x3d, 0x36, 0x30, 0x20, 0x6c, 0x6f, 0x6f, 0x6b, 0x61, 0x68,
    0x65, 0x61, 0x64, 0x5f, 0x74, 0x68, 0x72, 0x65, 0x61, 0x64, 0x73, 0x3d, 0x35, 0x20, 0x73, 0x6c,
    0x69, 0x63, 0x65, 0x64, 0x5f, 0x74, 0x68, 0x72, 0x65, 0x61, 0x64, 0x73, 0x3d, 0x30, 0x20, 0x6e,
    0x72, 0x3d, 0x30, 0x20, 0x64, 0x65, 0x63, 0x69, 0x6d, 0x61, 0x74, 0x65, 0x3d, 0x31, 0x20, 0x69,
    0x6e, 0x74, 0x65, 0x72, 0x6c, 0x61, 0x63, 0x65, 0x64, 0x3d, 0x30, 0x20, 0x62, 0x6c, 0x75, 0x72,
    0x61, 0x79, 0x5f, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x74, 0x3d, 0x30, 0x20, 0x63, 0x6f, 0x6e, 0x73,
    0x74, 0x72, 0x61, 0x69, 0x6e, 0x65, 0x64, 0x5f, 0x69, 0x6e, 0x74, 0x72, 0x61, 0x3d, 0x30, 0x20,
    0x62, 0x66, 0x72, 0x61, 0x6d, 0x65, 0x73, 0x3d, 0x30, 0x20, 0x77, 0x65, 0x69, 0x67, 0x68, 0x74,
    0x70, 0x3d, 0x30, 0x20, 0x6b, 0x65, 0x79, 0x69, 0x6e, 0x74, 0x3d, 0x32, 0x35, 0x30, 0x20, 0x6b,
    0x65, 0x79, 0x69, 0x6e, 0x74, 0x5f, 0x6d, 0x69, 0x6e, 0x3d, 0x32, 0x35, 0x20, 0x73, 0x63, 0x65,
    0x6e, 0x65,
};

class MediaCasListener : public ICasListener {
   public:
    virtual Return<void> onEvent(int32_t event, int32_t arg,
                                 const hidl_vec<uint8_t>& data) override {
        android::Mutex::Autolock autoLock(mMsgLock);
        mEvent = event;
        mEventArg = arg;
        mEventData = data;

        mEventReceived = true;
        mMsgCondition.signal();
        return Void();
    }

    void testEventEcho(sp<ICas>& mediaCas, int32_t& event, int32_t& eventArg,
                       hidl_vec<uint8_t>& eventData);

   private:
    int32_t mEvent = -1;
    int32_t mEventArg = -1;
    bool mEventReceived = false;
    hidl_vec<uint8_t> mEventData;
    android::Mutex mMsgLock;
    android::Condition mMsgCondition;
};

void MediaCasListener::testEventEcho(sp<ICas>& mediaCas, int32_t& event, int32_t& eventArg,
                                     hidl_vec<uint8_t>& eventData) {
    mEventReceived = false;
    auto returnStatus = mediaCas->sendEvent(event, eventArg, eventData);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    android::Mutex::Autolock autoLock(mMsgLock);
    while (!mEventReceived) {
        if (-ETIMEDOUT == mMsgCondition.waitRelative(mMsgLock, WAIT_TIMEOUT)) {
            EXPECT_TRUE(false) << "event not received within timeout";
            return;
        }
    }

    EXPECT_EQ(mEvent, event);
    EXPECT_EQ(mEventArg, eventArg);
    EXPECT_TRUE(mEventData == eventData);
}

class MediaCasHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        mService = ::testing::VtsHalHidlTargetTestBase::getService<IMediaCasService>();
        ASSERT_NE(mService, nullptr);
    }

    sp<IMediaCasService> mService;

   protected:
    static void description(const std::string& description) {
        RecordProperty("description", description);
    }

    sp<ICas> mMediaCas;
    sp<IDescramblerBase> mDescramblerBase;
    sp<MediaCasListener> mCasListener;

    ::testing::AssertionResult createCasPlugin(int32_t caSystemId);
    ::testing::AssertionResult openCasSession(std::vector<uint8_t>* sessionId);
    ::testing::AssertionResult descrambleTestInputBuffer(const sp<IDescrambler>& descrambler,
                                                         Status* descrambleStatus,
                                                         sp<IMemory>* hidlInMemory);
};

::testing::AssertionResult MediaCasHidlTest::createCasPlugin(int32_t caSystemId) {
    auto status = mService->isSystemIdSupported(caSystemId);
    if (!status.isOk() || !status) {
        return ::testing::AssertionFailure();
    }
    status = mService->isDescramblerSupported(caSystemId);
    if (!status.isOk() || !status) {
        return ::testing::AssertionFailure();
    }

    mCasListener = new MediaCasListener();
    auto pluginStatus = mService->createPlugin(caSystemId, mCasListener);
    if (!pluginStatus.isOk()) {
        return ::testing::AssertionFailure();
    }
    mMediaCas = pluginStatus;
    if (mMediaCas == nullptr) {
        return ::testing::AssertionFailure();
    }

    auto descramblerStatus = mService->createDescrambler(caSystemId);
    if (!descramblerStatus.isOk()) {
        return ::testing::AssertionFailure();
    }
    mDescramblerBase = descramblerStatus;
    return ::testing::AssertionResult(mDescramblerBase != nullptr);
}

::testing::AssertionResult MediaCasHidlTest::openCasSession(std::vector<uint8_t>* sessionId) {
    Status sessionStatus;
    auto returnVoid = mMediaCas->openSession([&](Status status, const hidl_vec<uint8_t>& id) {
        sessionStatus = status;
        *sessionId = id;
    });
    return ::testing::AssertionResult(returnVoid.isOk() && (Status::OK == sessionStatus));
}

::testing::AssertionResult MediaCasHidlTest::descrambleTestInputBuffer(
    const sp<IDescrambler>& descrambler, Status* descrambleStatus, sp<IMemory>* inMemory) {
    hidl_vec<SubSample> hidlSubSamples;
    hidlSubSamples.setToExternal(const_cast<SubSample*>(kSubSamples),
                                 (sizeof(kSubSamples) / sizeof(SubSample)), false /*own*/);

    sp<MemoryDealer> dealer = new MemoryDealer(sizeof(kInBinaryBuffer), "vts-cas");
    if (nullptr == dealer.get()) {
        ALOGE("couldn't get MemoryDealer!");
        return ::testing::AssertionFailure();
    }

    sp<IMemory> mem = dealer->allocate(sizeof(kInBinaryBuffer));
    if (nullptr == mem.get()) {
        ALOGE("couldn't allocate IMemory!");
        return ::testing::AssertionFailure();
    }
    *inMemory = mem;

    // build hidl_memory from memory heap
    ssize_t offset;
    size_t size;
    sp<IMemoryHeap> heap = mem->getMemory(&offset, &size);
    if (nullptr == heap.get()) {
        ALOGE("couldn't get memory heap!");
        return ::testing::AssertionFailure();
    }

    native_handle_t* nativeHandle = native_handle_create(1, 0);
    if (!nativeHandle) {
        ALOGE("failed to create native handle!");
        return ::testing::AssertionFailure();
    }
    nativeHandle->data[0] = heap->getHeapID();

    uint8_t* ipBuffer = static_cast<uint8_t*>(static_cast<void*>(mem->pointer()));
    memcpy(ipBuffer, kInBinaryBuffer, sizeof(kInBinaryBuffer));

    SharedBuffer srcBuffer = {
            .heapBase = hidl_memory("ashmem", hidl_handle(nativeHandle), heap->getSize()),
            .offset = (uint64_t) offset,
            .size = (uint64_t) size
    };

    DestinationBuffer dstBuffer;
    dstBuffer.type = BufferType::SHARED_MEMORY;
    dstBuffer.nonsecureMemory = srcBuffer;

    uint32_t outBytes;
    hidl_string detailedError;
    auto returnVoid = descrambler->descramble(
        ScramblingControl::EVENKEY /*2*/, hidlSubSamples, srcBuffer, 0, dstBuffer, 0,
        [&](Status status, uint32_t bytesWritten, const hidl_string& detailedErr) {
            *descrambleStatus = status;
            outBytes = bytesWritten;
            detailedError = detailedErr;
        });
    if (!returnVoid.isOk() || *descrambleStatus != Status::OK) {
        ALOGI("descramble failed, trans=%s, status=%d, outBytes=%u, error=%s",
              returnVoid.description().c_str(), *descrambleStatus, outBytes, detailedError.c_str());
    }
    return ::testing::AssertionResult(returnVoid.isOk());
}

TEST_F(MediaCasHidlTest, EnumeratePlugins) {
    description("Test enumerate plugins");
    hidl_vec<HidlCasPluginDescriptor> descriptors;
    EXPECT_TRUE(mService
                    ->enumeratePlugins([&descriptors](
                        hidl_vec<HidlCasPluginDescriptor> const& desc) { descriptors = desc; })
                    .isOk());

    if (descriptors.size() == 0) {
        ALOGW("[   WARN   ] enumeratePlugins list empty");
        return;
    }

    sp<MediaCasListener> casListener = new MediaCasListener();
    for (size_t i = 0; i < descriptors.size(); i++) {
        int32_t caSystemId = descriptors[i].caSystemId;

        ASSERT_TRUE(createCasPlugin(caSystemId));
    }
}

TEST_F(MediaCasHidlTest, TestInvalidSystemIdFails) {
    description("Test failure for invalid system ID");
    sp<MediaCasListener> casListener = new MediaCasListener();

    ASSERT_FALSE(mService->isSystemIdSupported(INVALID_SYSTEM_ID));
    ASSERT_FALSE(mService->isDescramblerSupported(INVALID_SYSTEM_ID));

    auto pluginStatus = mService->createPlugin(INVALID_SYSTEM_ID, casListener);
    ASSERT_TRUE(pluginStatus.isOk());
    sp<ICas> mediaCas = pluginStatus;
    EXPECT_EQ(mediaCas, nullptr);

    auto descramblerStatus = mService->createDescrambler(INVALID_SYSTEM_ID);
    ASSERT_TRUE(descramblerStatus.isOk());
    sp<IDescramblerBase> descramblerBase = descramblerStatus;
    EXPECT_EQ(descramblerBase, nullptr);
}

TEST_F(MediaCasHidlTest, TestClearKeyPluginInstalled) {
    description("Test if ClearKey plugin is installed");
    hidl_vec<HidlCasPluginDescriptor> descriptors;
    EXPECT_TRUE(mService
                    ->enumeratePlugins([&descriptors](
                        hidl_vec<HidlCasPluginDescriptor> const& desc) { descriptors = desc; })
                    .isOk());

    if (descriptors.size() == 0) {
        ALOGW("[   WARN   ] enumeratePlugins list empty");
    }

    for (size_t i = 0; i < descriptors.size(); i++) {
        int32_t caSystemId = descriptors[i].caSystemId;
        if (CLEAR_KEY_SYSTEM_ID == caSystemId) {
            return;
        }
    }

    ASSERT_TRUE(false) << "ClearKey plugin not installed";
}

TEST_F(MediaCasHidlTest, TestClearKeyApis) {
    description("Test that valid call sequences succeed");

    ASSERT_TRUE(createCasPlugin(CLEAR_KEY_SYSTEM_ID));

    auto returnStatus = mMediaCas->provision(hidl_string(PROVISION_STR));
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    hidl_vec<uint8_t> hidlPvtData;
    hidlPvtData.resize(256);
    returnStatus = mMediaCas->setPrivateData(hidlPvtData);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    std::vector<uint8_t> sessionId;
    ASSERT_TRUE(openCasSession(&sessionId));
    returnStatus = mMediaCas->setSessionPrivateData(sessionId, hidlPvtData);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    std::vector<uint8_t> streamSessionId;
    ASSERT_TRUE(openCasSession(&streamSessionId));
    returnStatus = mMediaCas->setSessionPrivateData(streamSessionId, hidlPvtData);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    returnStatus = mDescramblerBase->setMediaCasSession(sessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    returnStatus = mDescramblerBase->setMediaCasSession(streamSessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    hidl_vec<uint8_t> hidlNullPtr;
    hidlNullPtr.setToExternal(static_cast<uint8_t*>(nullptr), 0);
    returnStatus = mMediaCas->refreshEntitlements(3, hidlNullPtr);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    uint8_t refreshData[] = {0, 1, 2, 3};
    hidl_vec<uint8_t> hidlRefreshData;
    hidlRefreshData.setToExternal(static_cast<uint8_t*>(refreshData), sizeof(refreshData));
    returnStatus = mMediaCas->refreshEntitlements(10, hidlRefreshData);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    int32_t eventID = 1;
    int32_t eventArg = 2;
    mCasListener->testEventEcho(mMediaCas, eventID, eventArg, hidlNullPtr);

    eventID = 3;
    eventArg = 4;
    uint8_t eventData[] = {'e', 'v', 'e', 'n', 't', 'd', 'a', 't', 'a'};
    hidl_vec<uint8_t> hidlEventData;
    hidlEventData.setToExternal(static_cast<uint8_t*>(eventData), sizeof(eventData));
    mCasListener->testEventEcho(mMediaCas, eventID, eventArg, hidlEventData);

    uint8_t clearKeyEmmData[] = {'c', 'l', 'e', 'a', 'r', 'k', 'e', 'y', 'e', 'm', 'm'};
    hidl_vec<uint8_t> hidlClearKeyEmm;
    hidlClearKeyEmm.setToExternal(static_cast<uint8_t*>(clearKeyEmmData), sizeof(clearKeyEmmData));
    returnStatus = mMediaCas->processEmm(hidlClearKeyEmm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    hidl_vec<uint8_t> hidlEcm;
    hidlEcm.setToExternal(const_cast<uint8_t*>(kEcmBinaryBuffer), sizeof(kEcmBinaryBuffer));
    returnStatus = mMediaCas->processEcm(sessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);
    returnStatus = mMediaCas->processEcm(streamSessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    EXPECT_FALSE(mDescramblerBase->requiresSecureDecoderComponent("video/avc"));

    sp<IDescrambler> descrambler;
    descrambler = IDescrambler::castFrom(mDescramblerBase);
    ASSERT_NE(descrambler, nullptr);

    Status descrambleStatus = Status::OK;
    sp<IMemory> dataMemory;

    ASSERT_TRUE(descrambleTestInputBuffer(descrambler, &descrambleStatus, &dataMemory));
    EXPECT_EQ(Status::OK, descrambleStatus);

    ASSERT_NE(nullptr, dataMemory.get());
    uint8_t* opBuffer = static_cast<uint8_t*>(static_cast<void*>(dataMemory->pointer()));

    int compareResult =
        memcmp(static_cast<const void*>(opBuffer), static_cast<const void*>(kOutRefBinaryBuffer),
               sizeof(kOutRefBinaryBuffer));
    EXPECT_EQ(0, compareResult);

    returnStatus = mDescramblerBase->release();
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    returnStatus = mMediaCas->release();
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);
}

TEST_F(MediaCasHidlTest, TestClearKeySessionClosedAfterRelease) {
    description("Test that all sessions are closed after a MediaCas object is released");

    ASSERT_TRUE(createCasPlugin(CLEAR_KEY_SYSTEM_ID));

    auto returnStatus = mMediaCas->provision(hidl_string(PROVISION_STR));
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    std::vector<uint8_t> sessionId;
    ASSERT_TRUE(openCasSession(&sessionId));
    std::vector<uint8_t> streamSessionId;
    ASSERT_TRUE(openCasSession(&streamSessionId));

    returnStatus = mMediaCas->release();
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    returnStatus = mDescramblerBase->setMediaCasSession(sessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_SESSION_NOT_OPENED, returnStatus);

    returnStatus = mDescramblerBase->setMediaCasSession(streamSessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_SESSION_NOT_OPENED, returnStatus);
}

TEST_F(MediaCasHidlTest, TestClearKeyErrors) {
    description("Test that invalid call sequences fail with expected error codes");

    ASSERT_TRUE(createCasPlugin(CLEAR_KEY_SYSTEM_ID));

    /*
     * Test MediaCas error codes
     */
    // Provision should fail with an invalid asset string
    auto returnStatus = mMediaCas->provision(hidl_string("invalid asset string"));
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_NO_LICENSE, returnStatus);

    // Open a session, then close it so that it should become invalid
    std::vector<uint8_t> invalidSessionId;
    ASSERT_TRUE(openCasSession(&invalidSessionId));
    returnStatus = mMediaCas->closeSession(invalidSessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    // processEcm should fail with an invalid session id
    hidl_vec<uint8_t> hidlEcm;
    hidlEcm.setToExternal(const_cast<uint8_t*>(kEcmBinaryBuffer), sizeof(kEcmBinaryBuffer));
    returnStatus = mMediaCas->processEcm(invalidSessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_SESSION_NOT_OPENED, returnStatus);

    std::vector<uint8_t> sessionId;
    ASSERT_TRUE(openCasSession(&sessionId));

    // processEcm should fail without provisioning
    hidlEcm.setToExternal(const_cast<uint8_t*>(kEcmBinaryBuffer), sizeof(kEcmBinaryBuffer));
    returnStatus = mMediaCas->processEcm(sessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_NOT_PROVISIONED, returnStatus);

    returnStatus = mMediaCas->provision(hidl_string(PROVISION_STR));
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    // processEcm should fail with ecm buffer that's too short
    hidlEcm.setToExternal(const_cast<uint8_t*>(kEcmBinaryBuffer), 8);
    returnStatus = mMediaCas->processEcm(sessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::BAD_VALUE, returnStatus);

    // processEcm should fail with ecm with bad descriptor count
    uint8_t badDescriptor[sizeof(kEcmBinaryBuffer)];
    memcpy(badDescriptor, kEcmBinaryBuffer, sizeof(kEcmBinaryBuffer));
    badDescriptor[17] = 0x03;  // change the descriptor count field to 3 (invalid)
    hidlEcm.setToExternal(static_cast<uint8_t*>(badDescriptor), sizeof(badDescriptor));
    returnStatus = mMediaCas->processEcm(sessionId, hidlEcm);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_UNKNOWN, returnStatus);

    /*
     * Test MediaDescrambler error codes
     */
    // setMediaCasSession should fail with an invalid session id
    returnStatus = mDescramblerBase->setMediaCasSession(invalidSessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::ERROR_CAS_SESSION_NOT_OPENED, returnStatus);

    // descramble should fail without a valid session
    sp<IDescrambler> descrambler;
    descrambler = IDescrambler::castFrom(mDescramblerBase);
    ASSERT_NE(descrambler, nullptr);

    Status descrambleStatus = Status::OK;
    sp<IMemory> dataMemory;

    ASSERT_TRUE(descrambleTestInputBuffer(descrambler, &descrambleStatus, &dataMemory));
    EXPECT_EQ(Status::ERROR_CAS_DECRYPT_UNIT_NOT_INITIALIZED, descrambleStatus);

    // Now set a valid session, should still fail because no valid ecm is processed
    returnStatus = mDescramblerBase->setMediaCasSession(sessionId);
    EXPECT_TRUE(returnStatus.isOk());
    EXPECT_EQ(Status::OK, returnStatus);

    ASSERT_TRUE(descrambleTestInputBuffer(descrambler, &descrambleStatus, &dataMemory));
    EXPECT_EQ(Status::ERROR_CAS_DECRYPT, descrambleStatus);

    // Verify that requiresSecureDecoderComponent handles empty mime
    EXPECT_FALSE(mDescramblerBase->requiresSecureDecoderComponent(""));

    // Verify that requiresSecureDecoderComponent handles invalid mime
    EXPECT_FALSE(mDescramblerBase->requiresSecureDecoderComponent("bad"));
}

}  // anonymous namespace

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    LOG(INFO) << "Test result = " << status;
    return status;
}
