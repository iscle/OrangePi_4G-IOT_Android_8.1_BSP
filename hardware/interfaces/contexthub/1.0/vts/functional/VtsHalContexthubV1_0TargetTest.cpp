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

#define LOG_TAG "contexthub_hidl_hal_test"

#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <android/hardware/contexthub/1.0/IContexthubCallback.h>
#include <android/hardware/contexthub/1.0/types.h>
#include <android/log.h>
#include <log/log.h>

#include <cinttypes>
#include <future>
#include <utility>

using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::contexthub::V1_0::AsyncEventType;
using ::android::hardware::contexthub::V1_0::ContextHub;
using ::android::hardware::contexthub::V1_0::ContextHubMsg;
using ::android::hardware::contexthub::V1_0::HubAppInfo;
using ::android::hardware::contexthub::V1_0::IContexthub;
using ::android::hardware::contexthub::V1_0::IContexthubCallback;
using ::android::hardware::contexthub::V1_0::NanoAppBinary;
using ::android::hardware::contexthub::V1_0::Result;
using ::android::hardware::contexthub::V1_0::TransactionResult;
using ::android::sp;

#define ASSERT_OK(result) ASSERT_EQ(result, Result::OK)
#define EXPECT_OK(result) EXPECT_EQ(result, Result::OK)

namespace {

// App ID with vendor "GoogT" (Google Testing), app identifier 0x555555. This
// app ID is reserved and must never appear in the list of loaded apps.
constexpr uint64_t kNonExistentAppId = 0x476f6f6754555555;

// Helper that does explicit conversion of an enum class to its underlying/base
// type. Useful for stream output of enum values.
template<typename EnumType>
constexpr typename std::underlying_type<EnumType>::type asBaseType(
    EnumType value) {
  return static_cast<typename std::underlying_type<EnumType>::type>(value);
}

// Synchronously queries IContexthub::getHubs() and returns the result
hidl_vec<ContextHub> getHubsSync(sp<IContexthub> hubApi) {
  hidl_vec<ContextHub> hubList;
  std::promise<void> barrier;

  hubApi->getHubs([&hubList, &barrier](const hidl_vec<ContextHub>& hubs) {
    hubList = hubs;
    barrier.set_value();
  });
  barrier.get_future().wait_for(std::chrono::seconds(1));

  return hubList;
}

// Gets a list of valid hub IDs in the system
std::vector<uint32_t> getHubIds() {
  static std::vector<uint32_t> hubIds;

  if (hubIds.size() == 0) {
    sp<IContexthub> hubApi = ::testing::VtsHalHidlTargetTestBase::getService<IContexthub>();

    if (hubApi != nullptr) {
      for (ContextHub hub : getHubsSync(hubApi)) {
        hubIds.push_back(hub.hubId);
      }
    }
  }

  ALOGD("Running tests against all %zu reported hubs", hubIds.size());
  return hubIds;
}

// Base test fixture that initializes the HAL and makes the context hub API
// handle available
class ContexthubHidlTestBase : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    hubApi = ::testing::VtsHalHidlTargetTestBase::getService<IContexthub>();
    ASSERT_NE(hubApi, nullptr);

    // getHubs() must be called at least once for proper initialization of the
    // HAL implementation
    getHubsSync(hubApi);
  }

  virtual void TearDown() override {}

  sp<IContexthub> hubApi;
};

// Test fixture parameterized by hub ID
class ContexthubHidlTest : public ContexthubHidlTestBase,
                           public ::testing::WithParamInterface<uint32_t> {
 public:
  uint32_t getHubId() {
    return GetParam();
  }

  Result registerCallback(sp<IContexthubCallback> cb) {
    Result result = hubApi->registerCallback(getHubId(), cb);
    ALOGD("Registered callback, result %" PRIu32, result);
    return result;
  }
};

// Base callback implementation that just logs all callbacks by default
class ContexthubCallbackBase : public IContexthubCallback {
 public:
  virtual Return<void> handleClientMsg(const ContextHubMsg& /*msg*/) override {
    ALOGD("Got client message callback");
    return Void();
  }

  virtual Return<void> handleTxnResult(
      uint32_t txnId, TransactionResult result) override {
    ALOGD("Got transaction result callback for txnId %" PRIu32 " with result %"
          PRId32, txnId, result);
    return Void();
  }

  virtual Return<void> handleHubEvent(AsyncEventType evt) override {
    ALOGD("Got hub event callback for event type %" PRIu32, evt);
    return Void();
  }

  virtual Return<void> handleAppAbort(uint64_t appId, uint32_t abortCode)
      override {
    ALOGD("Got app abort notification for appId 0x%" PRIx64 " with abort code "
          "0x%" PRIx32, appId, abortCode);
    return Void();
  }

  virtual Return<void> handleAppsInfo(const hidl_vec<HubAppInfo>& /*appInfo*/)
      override {
    ALOGD("Got app info callback");
    return Void();
  }
};

// Wait for a callback to occur (signaled by the given future) up to the
// provided timeout. If the future is invalid or the callback does not come
// within the given time, returns false.
template<class ReturnType>
bool waitForCallback(
    std::future<ReturnType> future,
    ReturnType *result,
    std::chrono::milliseconds timeout = std::chrono::seconds(5)) {
  auto expiration = std::chrono::system_clock::now() + timeout;

  EXPECT_NE(result, nullptr);
  EXPECT_TRUE(future.valid());
  if (result != nullptr && future.valid()) {
    std::future_status status = future.wait_until(expiration);
    EXPECT_NE(status, std::future_status::timeout)
        << "Timed out waiting for callback";

    if (status == std::future_status::ready) {
      *result = future.get();
      return true;
    }
  }

  return false;
}

// Ensures that the metadata reported in getHubs() is sane
TEST_F(ContexthubHidlTestBase, TestGetHubs) {
  hidl_vec<ContextHub> hubs = getHubsSync(hubApi);
  ALOGD("System reports %zu hubs", hubs.size());

  for (ContextHub hub : hubs) {
    ALOGD("Checking hub ID %" PRIu32, hub.hubId);

    EXPECT_FALSE(hub.name.empty());
    EXPECT_FALSE(hub.vendor.empty());
    EXPECT_FALSE(hub.toolchain.empty());
    EXPECT_GT(hub.peakMips, 0);
    EXPECT_GE(hub.stoppedPowerDrawMw, 0);
    EXPECT_GE(hub.sleepPowerDrawMw, 0);
    EXPECT_GT(hub.peakPowerDrawMw, 0);

    // Minimum 128 byte MTU as required by CHRE API v1.0
    EXPECT_GE(hub.maxSupportedMsgLen, UINT32_C(128));
  }
}

TEST_P(ContexthubHidlTest, TestRegisterCallback) {
  ALOGD("TestRegisterCallback called, hubId %" PRIu32, getHubId());
  ASSERT_OK(registerCallback(new ContexthubCallbackBase()));
}

TEST_P(ContexthubHidlTest, TestRegisterNullCallback) {
  ALOGD("TestRegisterNullCallback called, hubId %" PRIu32, getHubId());
  ASSERT_OK(registerCallback(nullptr));
}

// Helper callback that puts the async appInfo callback data into a promise
class QueryAppsCallback : public ContexthubCallbackBase {
 public:
  virtual Return<void> handleAppsInfo(const hidl_vec<HubAppInfo>& appInfo)
      override {
    ALOGD("Got app info callback with %zu apps", appInfo.size());
    promise.set_value(appInfo);
    return Void();
  }

  std::promise<hidl_vec<HubAppInfo>> promise;
};

// Calls queryApps() and checks the returned metadata
TEST_P(ContexthubHidlTest, TestQueryApps) {
  ALOGD("TestQueryApps called, hubId %u", getHubId());
  sp<QueryAppsCallback> cb = new QueryAppsCallback();
  ASSERT_OK(registerCallback(cb));

  Result result = hubApi->queryApps(getHubId());
  ASSERT_OK(result);

  ALOGD("Waiting for app info callback");
  hidl_vec<HubAppInfo> appList;
  ASSERT_TRUE(waitForCallback(cb->promise.get_future(), &appList));
  for (const HubAppInfo &appInfo : appList) {
    EXPECT_NE(appInfo.appId, UINT64_C(0));
    EXPECT_NE(appInfo.appId, kNonExistentAppId);
  }
}

// Helper callback that puts the TransactionResult for the expectedTxnId into a
// promise
class TxnResultCallback : public ContexthubCallbackBase {
 public:
  virtual Return<void> handleTxnResult(
      uint32_t txnId, TransactionResult result) override {
    ALOGD("Got transaction result callback for txnId %" PRIu32 " (expecting %"
          PRIu32 ") with result %" PRId32, txnId, expectedTxnId, result);
    if (txnId == expectedTxnId) {
      promise.set_value(result);
    }
    return Void();
  }

  uint32_t expectedTxnId = 0;
  std::promise<TransactionResult> promise;
};

// Parameterized fixture that sets the callback to TxnResultCallback
class ContexthubTxnTest : public ContexthubHidlTest {
 public:
  virtual void SetUp() override {
    ContexthubHidlTest::SetUp();
    ASSERT_OK(registerCallback(cb));
  }

  sp<TxnResultCallback> cb = new TxnResultCallback();
};


// Checks cases where the hub implementation is expected to return an error, but
// that error can be returned either synchronously or in the asynchronous
// transaction callback. Returns an AssertionResult that can be used in
// ASSERT/EXPECT_TRUE. Allows checking the sync result against 1 additional
// allowed error code apart from OK and TRANSACTION_FAILED, which are always
// allowed.
::testing::AssertionResult checkFailureSyncOrAsync(
    Result result, Result allowedSyncResult,
    std::future<TransactionResult>&& future) {
  if (result == Result::OK) {
    // No error reported synchronously - this is OK, but then we should get an
    // async callback with a failure status
    TransactionResult asyncResult;
    if (!waitForCallback(std::forward<std::future<TransactionResult>>(future),
                         &asyncResult)) {
      return ::testing::AssertionFailure()
          << "Got successful sync result, then failed to receive async cb";
    } else if (asyncResult == TransactionResult::SUCCESS) {
      return ::testing::AssertionFailure()
          << "Got successful sync result, then unexpected successful async "
             "result";
    }
  } else if (result != allowedSyncResult &&
             result != Result::TRANSACTION_FAILED) {
    return ::testing::AssertionFailure() << "Got sync result "
        << asBaseType(result) << ", expected TRANSACTION_FAILED or "
        << asBaseType(allowedSyncResult);
  }

  return ::testing::AssertionSuccess();
}

TEST_P(ContexthubTxnTest, TestSendMessageToNonExistentNanoApp) {
  ContextHubMsg msg;
  msg.appName = kNonExistentAppId;
  msg.msgType = 1;
  msg.msg.resize(4);
  std::fill(msg.msg.begin(), msg.msg.end(), 0);

  ALOGD("Sending message to non-existent nanoapp");
  Result result = hubApi->sendMessageToHub(getHubId(), msg);
  if (result != Result::OK &&
      result != Result::BAD_PARAMS &&
      result != Result::TRANSACTION_FAILED) {
    FAIL() << "Got result " << asBaseType(result) << ", expected OK, BAD_PARAMS"
        << ", or TRANSACTION_FAILED";
  }
}

TEST_P(ContexthubTxnTest, TestLoadEmptyNanoApp) {
  cb->expectedTxnId = 0123;
  NanoAppBinary emptyApp;

  emptyApp.appId = kNonExistentAppId;
  emptyApp.appVersion = 1;
  emptyApp.flags = 0;
  emptyApp.targetChreApiMajorVersion = 1;
  emptyApp.targetChreApiMinorVersion = 0;

  ALOGD("Loading empty nanoapp");
  Result result = hubApi->loadNanoApp(getHubId(), emptyApp, cb->expectedTxnId);
  EXPECT_TRUE(checkFailureSyncOrAsync(result, Result::BAD_PARAMS,
                                      cb->promise.get_future()));
}

TEST_P(ContexthubTxnTest, TestUnloadNonexistentNanoApp) {
  cb->expectedTxnId = 1234;

  ALOGD("Unloading nonexistent nanoapp");
  Result result = hubApi->unloadNanoApp(getHubId(), kNonExistentAppId,
                                        cb->expectedTxnId);
  EXPECT_TRUE(checkFailureSyncOrAsync(result, Result::BAD_PARAMS,
                                      cb->promise.get_future()));
}

TEST_P(ContexthubTxnTest, TestEnableNonexistentNanoApp) {
  cb->expectedTxnId = 2345;

  ALOGD("Enabling nonexistent nanoapp");
  Result result = hubApi->enableNanoApp(getHubId(), kNonExistentAppId,
                                        cb->expectedTxnId);
  EXPECT_TRUE(checkFailureSyncOrAsync(result, Result::BAD_PARAMS,
                                      cb->promise.get_future()));
}

TEST_P(ContexthubTxnTest, TestDisableNonexistentNanoApp) {
  cb->expectedTxnId = 3456;

  ALOGD("Disabling nonexistent nanoapp");
  Result result = hubApi->disableNanoApp(getHubId(), kNonExistentAppId,
                                         cb->expectedTxnId);
  EXPECT_TRUE(checkFailureSyncOrAsync(result, Result::BAD_PARAMS,
                                      cb->promise.get_future()));
}

// Parameterize all SingleContexthubTest tests against each valid hub ID
INSTANTIATE_TEST_CASE_P(HubIdSpecificTests, ContexthubHidlTest,
                        ::testing::ValuesIn(getHubIds()));
INSTANTIATE_TEST_CASE_P(HubIdSpecificTests, ContexthubTxnTest,
                        ::testing::ValuesIn(getHubIds()));

} // anonymous namespace

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}

