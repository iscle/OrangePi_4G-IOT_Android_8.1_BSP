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

#include <android-base/logging.h>

#include <VtsHalHidlTargetTestBase.h>
#include <chrono>
#include <condition_variable>
#include <mutex>

#include <android/hardware/radio/1.0/ISap.h>
#include <android/hardware/radio/1.0/ISapCallback.h>
#include <android/hardware/radio/1.0/types.h>

#include "vts_test_util.h"

using namespace ::android::hardware::radio::V1_0;

using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

#define TIMEOUT_PERIOD 40
#define SAP_SERVICE_NAME "slot1"

class SapHidlTest;

/* Callback class for sap response */
class SapCallback : public ISapCallback {
   private:
    SapHidlTest& parent;

   public:
    SapResultCode sapResultCode;
    int32_t sapResponseToken;

    SapCallback(SapHidlTest& parent);

    virtual ~SapCallback() = default;

    Return<void> connectResponse(int32_t token, SapConnectRsp sapConnectRsp, int32_t maxMsgSize);

    Return<void> disconnectResponse(int32_t token);

    Return<void> disconnectIndication(int32_t token, SapDisconnectType disconnectType);

    Return<void> apduResponse(int32_t token, SapResultCode resultCode,
                              const ::android::hardware::hidl_vec<uint8_t>& apduRsp);

    Return<void> transferAtrResponse(int32_t token, SapResultCode resultCode,
                                     const ::android::hardware::hidl_vec<uint8_t>& atr);

    Return<void> powerResponse(int32_t token, SapResultCode resultCode);

    Return<void> resetSimResponse(int32_t token, SapResultCode resultCode);

    Return<void> statusIndication(int32_t token, SapStatus status);

    Return<void> transferCardReaderStatusResponse(int32_t token, SapResultCode resultCode,
                                                  int32_t cardReaderStatus);

    Return<void> errorResponse(int32_t token);

    Return<void> transferProtocolResponse(int32_t token, SapResultCode resultCode);
};

// The main test class for Sap HIDL.
class SapHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   private:
    std::mutex mtx;
    std::condition_variable cv;
    int count;

   public:
    virtual void SetUp() override;

    virtual void TearDown() override;

    /* Used as a mechanism to inform the test about data/event callback */
    void notify();

    /* Test code calls this function to wait for response */
    std::cv_status wait();

    /* Sap service */
    sp<ISap> sap;

    /* Sap Callback object */
    sp<SapCallback> sapCb;
};

// A class for test environment setup
class SapHidlEnvironment : public ::testing::Environment {
   public:
    virtual void SetUp() {}
    virtual void TearDown() {}
};
