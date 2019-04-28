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

#define LOG_TAG "wifi_offload_hidl_hal_test"

#include <android-base/logging.h>
#include <android/hardware/wifi/offload/1.0/IOffload.h>
#include <android/hardware/wifi/offload/1.0/IOffloadCallback.h>
#include <android/hardware/wifi/offload/1.0/types.h>

#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>

#include <vector>

#include "hidl_call_util.h"

using ::android::hardware::wifi::offload::V1_0::IOffload;
using ::android::hardware::wifi::offload::V1_0::IOffloadCallback;
using ::android::hardware::wifi::offload::V1_0::ScanResult;
using ::android::hardware::wifi::offload::V1_0::ScanParam;
using ::android::hardware::wifi::offload::V1_0::Ssid;
using ::android::hardware::wifi::offload::V1_0::NetworkInfo;
using ::android::hardware::wifi::offload::V1_0::ScanFilter;
using ::android::hardware::wifi::offload::V1_0::ScanStats;
using ::android::hardware::wifi::offload::V1_0::OffloadStatus;
using ::android::hardware::wifi::offload::V1_0::OffloadStatusCode;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::sp;

constexpr char kOffloadCallbackSendScanResult[] = "onScanResult";
constexpr char kOffloadCallbackSendError[] = "onError";

namespace {
const uint8_t kSsid1[] = {'G', 'o', 'o', 'g', 'l', 'e'};
const uint8_t kSsid2[] = {'X', 'f', 'i', 'n', 'i', 't', 'y'};
const uint8_t kBssid[6] = {0x12, 0xef, 0xa1, 0x2c, 0x97, 0x8b};
const int16_t kRssi = -60;
const uint32_t kFrequency = 2412;
const uint8_t kBssidSize = 6;
const uint64_t kTsf = 0;
const uint16_t kCapability = 0;
const uint8_t kNetworkFlags = 0;
const uint32_t kFrequency1 = 2412;
const uint32_t kFrequency2 = 2437;
const uint32_t kDisconnectedModeScanIntervalMs = 5000;
const int16_t kRssiThreshold = -76;
}

class OffloadCallbackArgs {
   public:
    hidl_vec<ScanResult> scan_results_;
    OffloadStatus error_code_;
};

// The main test class for WifiOffload HIDL HAL.
class WifiOffloadHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        wifi_offload_ =
            ::testing::VtsHalHidlTargetTestBase::getService<IOffload>();
        ASSERT_NE(wifi_offload_, nullptr);

        wifi_offload_cb_ = new OffloadCallback();
        ASSERT_NE(wifi_offload_cb_, nullptr);
    }

    virtual void TearDown() override {}

    /* Callback class for Offload HAL. */
    class OffloadCallback
        : public ::testing::VtsHalHidlTargetCallbackBase<OffloadCallbackArgs>,
          public IOffloadCallback {
       public:
        OffloadCallback(){};

        virtual ~OffloadCallback() = default;

        Return<void> onScanResult(
            const hidl_vec<ScanResult>& scan_result) override {
            OffloadCallbackArgs args;
            args.scan_results_ = scan_result;
            NotifyFromCallback(kOffloadCallbackSendScanResult, args);
            return Void();
        };

        Return<void> onError(const OffloadStatus& status) override {
            OffloadCallbackArgs args;
            args.error_code_ = status;
            NotifyFromCallback(kOffloadCallbackSendError, args);
            return Void();
        }
    };

    sp<IOffload> wifi_offload_;
    sp<OffloadCallback> wifi_offload_cb_;
};

/*
 * Verify that setEventCallback method returns without errors
 */
TEST_F(WifiOffloadHidlTest, setEventCallback) {
    auto returnObject = wifi_offload_->setEventCallback(wifi_offload_cb_);
    ASSERT_EQ(true, returnObject.isOk());
}

/*
 * Verify that subscribeScanResults method returns without errors
 */
TEST_F(WifiOffloadHidlTest, subscribeScanResults) {
    const auto& result = HIDL_INVOKE(wifi_offload_, subscribeScanResults, 0);
    ASSERT_EQ(OffloadStatusCode::OK, result.code);
}

/*
 * Verify that unsubscribeScanResults method returns without errors
 */
TEST_F(WifiOffloadHidlTest, unsubscribeScanResults) {
    auto returnObject = wifi_offload_->unsubscribeScanResults();
    ASSERT_EQ(true, returnObject.isOk());
}

/*
 * Verify that configureScans method returns without errors
 */
TEST_F(WifiOffloadHidlTest, configureScans) {
    ScanParam* pScanParam = new ScanParam();
    std::vector<uint32_t> frequencyList = {kFrequency1, kFrequency2};
    pScanParam->disconnectedModeScanIntervalMs =
        kDisconnectedModeScanIntervalMs;
    pScanParam->frequencyList = frequencyList;
    std::vector<Ssid> ssidList;
    std::vector<std::vector<uint8_t>> ssids{kSsid1, kSsid2};
    for (const auto& ssid : ssids) {
        Ssid tmp = ssid;
        ssidList.push_back(tmp);
    }
    pScanParam->ssidList = ssidList;
    ScanFilter* pScanFilter = new ScanFilter();
    pScanFilter->rssiThreshold = kRssiThreshold;
    std::vector<std::vector<uint8_t>> match_ssids{kSsid1, kSsid2};
    std::vector<uint8_t> security_flags{kNetworkFlags, kNetworkFlags};
    std::vector<NetworkInfo> preferredNetworksList;
    for (size_t i = 0; i < security_flags.size(); i++) {
        NetworkInfo nwInfo;
        nwInfo.ssid = match_ssids[i];
        nwInfo.flags = security_flags[i];
        preferredNetworksList.push_back(nwInfo);
    }
    const auto& result =
        HIDL_INVOKE(wifi_offload_, configureScans, *pScanParam, *pScanFilter);
    ASSERT_EQ(OffloadStatusCode::OK, result.code);
}

/*
 * Verify that getScanStats returns without any errors
 */
TEST_F(WifiOffloadHidlTest, getScanStats) {
    const auto& result = HIDL_INVOKE(wifi_offload_, getScanStats);
    OffloadStatus status = result.first;
    ASSERT_EQ(OffloadStatusCode::OK, status.code);
}

/*
 * Verify that onScanResult callback is invoked
 */
TEST_F(WifiOffloadHidlTest, getScanResults) {
    wifi_offload_->setEventCallback(wifi_offload_cb_);
    std::vector<ScanResult> scan_results;
    std::vector<uint8_t> ssid(kSsid1, kSsid1 + sizeof(kSsid1));
    ScanResult scan_result;
    scan_result.tsf = kTsf;
    scan_result.rssi = kRssi;
    scan_result.frequency = kFrequency;
    scan_result.capability = kCapability;
    memcpy(&scan_result.bssid[0], &kBssid[0], kBssidSize);
    scan_result.networkInfo.ssid = ssid;
    scan_result.networkInfo.flags = kNetworkFlags;
    scan_results.push_back(scan_result);
    wifi_offload_cb_->onScanResult(scan_results);
    auto res =
        wifi_offload_cb_->WaitForCallback(kOffloadCallbackSendScanResult);
    ASSERT_EQ(true, res.no_timeout);
}

/*
 * Verify that onError callback is invoked
 */
TEST_F(WifiOffloadHidlTest, getError) {
    wifi_offload_->setEventCallback(wifi_offload_cb_);
    OffloadStatus status = {OffloadStatusCode::ERROR, ""};
    wifi_offload_cb_->onError(status);
    auto res = wifi_offload_cb_->WaitForCallback(kOffloadCallbackSendError);
    ASSERT_EQ(true, res.no_timeout);
}

// A class for test environment setup
class WifiOffloadHalHidlEnvironment : public ::testing::Environment {
   public:
    virtual void SetUp() {}
    virtual void TearDown() {}
};

int main(int argc, char** argv) {
    ::testing::AddGlobalTestEnvironment(new WifiOffloadHalHidlEnvironment);
    ::testing::InitGoogleTest(&argc, argv);

    int status = RUN_ALL_TESTS();
    LOG(INFO) << "Test result = " << status;

    return status;
}
