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

#define LOG_TAG "fingerprint_hidl_hal_test"

#include <android-base/logging.h>
#include <android/hardware/biometrics/fingerprint/2.1/IBiometricsFingerprint.h>
#include <android/hardware/biometrics/fingerprint/2.1/IBiometricsFingerprintClientCallback.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <VtsHalHidlTargetTestBase.h>

#include <cinttypes>
#include <future>
#include <utility>

using android::Condition;
using android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprint;
using android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprintClientCallback;
using android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo;
using android::hardware::biometrics::fingerprint::V2_1::FingerprintError;
using android::hardware::biometrics::fingerprint::V2_1::RequestStatus;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::Mutex;
using android::sp;

namespace {

static const uint32_t kTimeout = 3;
static const std::chrono::seconds kTimeoutInSeconds = std::chrono::seconds(kTimeout);
static const uint32_t kGroupId = 99;
static const std::string kTmpDir = "/data/system/";
static const uint32_t kIterations = 1000;

// Wait for a callback to occur (signaled by the given future) up to the
// provided timeout. If the future is invalid or the callback does not come
// within the given time, returns false.
template<class ReturnType>
bool waitForCallback(
    std::future<ReturnType> future,
    std::chrono::milliseconds timeout = kTimeoutInSeconds) {
  auto expiration = std::chrono::system_clock::now() + timeout;

  EXPECT_TRUE(future.valid());
  if (future.valid()) {
    std::future_status status = future.wait_until(expiration);
    EXPECT_NE(std::future_status::timeout, status)
        << "Timed out waiting for callback";
    if (status == std::future_status::ready) {
      return true;
    }
  }

  return false;
}

// Base callback implementation that just logs all callbacks by default
class FingerprintCallbackBase : public IBiometricsFingerprintClientCallback {
 public:
  // implement methods of IBiometricsFingerprintClientCallback
  virtual Return<void> onEnrollResult(uint64_t, uint32_t, uint32_t, uint32_t)
      override {
    ALOGD("Enroll callback called.");
    return Return<void>();
  }

  virtual Return<void> onAcquired(uint64_t, FingerprintAcquiredInfo, int32_t)
      override {
    ALOGD("Acquired callback called.");
    return Return<void>();
  }

  virtual Return<void> onAuthenticated(uint64_t, uint32_t, uint32_t,
      const hidl_vec<uint8_t>&) override {
    ALOGD("Authenticated callback called.");
    return Return<void>();
  }

  virtual Return<void> onError(uint64_t, FingerprintError, int32_t)
      override {
    ALOGD("Error callback called.");
    EXPECT_TRUE(false);  // fail any test that triggers an error
    return Return<void>();
  }

  virtual Return<void> onRemoved(uint64_t, uint32_t, uint32_t, uint32_t)
      override {
    ALOGD("Removed callback called.");
    return Return<void>();
  }

  virtual Return<void> onEnumerate(uint64_t, uint32_t, uint32_t, uint32_t)
      override {
    ALOGD("Enumerate callback called.");
    return Return<void>();
  }
};

class EnumerateCallback : public FingerprintCallbackBase {
 public:
  virtual Return<void> onEnumerate(uint64_t deviceId, uint32_t fingerId,
      uint32_t groupId, uint32_t remaining) override {
    this->deviceId = deviceId;
    this->fingerId = fingerId;
    this->groupId = groupId;
    this->remaining = remaining;

    if(remaining == 0UL) {
      promise.set_value();
    }
    return Return<void>();
  }

  uint64_t deviceId;
  uint32_t fingerId;
  uint32_t groupId;
  uint32_t remaining;
  std::promise<void> promise;
};

class ErrorCallback : public FingerprintCallbackBase {
 public:
  ErrorCallback(
      bool filterErrors=false,
      FingerprintError errorType=FingerprintError::ERROR_NO_ERROR) {
    this->filterErrors = filterErrors;
    this->errorType = errorType;
  }

  virtual Return<void> onError(uint64_t deviceId, FingerprintError error,
      int32_t vendorCode) override {
    if ((this->filterErrors && this->errorType == error) || !this->filterErrors) {
      this->deviceId = deviceId;
      this->error = error;
      this->vendorCode = vendorCode;
      promise.set_value();
    }
    return Return<void>();
  }

  bool filterErrors;
  FingerprintError errorType;
  uint64_t deviceId;
  FingerprintError error;
  int32_t vendorCode;
  std::promise<void> promise;
};

class RemoveCallback : public FingerprintCallbackBase {
 public:
  RemoveCallback(uint32_t groupId) {
    this->removeGroupId = groupId;
  }

  virtual Return<void> onRemoved(uint64_t, uint32_t, uint32_t groupId,
      uint32_t remaining) override {
    EXPECT_EQ(this->removeGroupId, groupId);
    if(remaining == 0UL) {
      promise.set_value();
    }
    return Return<void>();
  }

  uint32_t removeGroupId;
  std::promise<void> promise;
};

class FingerprintHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    mService = ::testing::VtsHalHidlTargetTestBase::getService<IBiometricsFingerprint>();
    ASSERT_FALSE(mService == nullptr);

    // Create an active group
    // FP service can only write to /data/system due to
    // SELinux Policy and Linux Dir Permissions
    Return<RequestStatus> res = mService->setActiveGroup(kGroupId, kTmpDir);
    ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));
  }

  virtual void TearDown() override {}

  sp<IBiometricsFingerprint> mService;
};


// The service should be reachable.
TEST_F(FingerprintHidlTest, ConnectTest) {
  sp<FingerprintCallbackBase> cb = new FingerprintCallbackBase();
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));
}

// Starting the service with null callback should succeed.
TEST_F(FingerprintHidlTest, ConnectNullTest) {
  Return<uint64_t> rc = mService->setNotify(NULL);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));
}

// Pre-enroll should always return unique, cryptographically secure, non-zero number
TEST_F(FingerprintHidlTest, PreEnrollTest) {
  std::map<uint64_t, uint64_t> m;

  for(unsigned int i = 0; i < kIterations; ++i) {
    uint64_t res = static_cast<uint64_t>(mService->preEnroll());
    EXPECT_NE(0UL, res);
    m[res]++;
    EXPECT_EQ(1UL, m[res]);
  }
}

// Enroll with an invalid (all zeroes) HAT should fail.
TEST_F(FingerprintHidlTest, EnrollInvalidHatTest) {
  sp<ErrorCallback> cb = new ErrorCallback();
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  uint8_t token[69];
  for(int i=0; i<69; i++) {
    token[i] = 0;
  }

  Return<RequestStatus> res = mService->enroll(token, kGroupId, kTimeout);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // At least one call to onError should occur
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
  ASSERT_NE(FingerprintError::ERROR_NO_ERROR, cb->error);
}

// Enroll with an invalid (null) HAT should fail.
TEST_F(FingerprintHidlTest, EnrollNullTest) {
  sp<ErrorCallback> cb = new ErrorCallback();
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  uint8_t token[69];
  Return<RequestStatus> res = mService->enroll(token, kGroupId, kTimeout);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // At least one call to onError should occur
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
  ASSERT_NE(FingerprintError::ERROR_NO_ERROR, cb->error);
}

// PostEnroll should always return within 3s
TEST_F(FingerprintHidlTest, PostEnrollTest) {
  sp<FingerprintCallbackBase> cb = new FingerprintCallbackBase();
  Return<uint64_t> rc = mService->setNotify(cb);

  auto start = std::chrono::system_clock::now();
  Return<RequestStatus> res = mService->postEnroll();
  auto elapsed = std::chrono::system_clock::now() - start;
  ASSERT_GE(kTimeoutInSeconds, elapsed);
}

// getAuthenticatorId should always return non-zero numbers
TEST_F(FingerprintHidlTest, GetAuthenticatorIdTest) {
  Return<uint64_t> res = mService->getAuthenticatorId();
  EXPECT_NE(0UL, static_cast<uint64_t>(res));
}

// Enumerate should always trigger onEnumerated(fid=0, rem=0) when there are no fingerprints
TEST_F(FingerprintHidlTest, EnumerateTest) {
  sp<EnumerateCallback> cb = new EnumerateCallback();
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  // Callback will return when rem=0 is found
  Return<RequestStatus> res = mService->enumerate();
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
  EXPECT_EQ(0UL, cb->fingerId);
  EXPECT_EQ(0UL, cb->remaining);

}

// Remove should succeed on any inputs
// At least one callback with "remaining=0" should occur
TEST_F(FingerprintHidlTest, RemoveFingerprintTest) {
  // Register callback
  sp<RemoveCallback> cb = new RemoveCallback(kGroupId);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  // Remove a fingerprint
  Return<RequestStatus> res = mService->remove(kGroupId, 1);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // At least one call to onRemove with remaining=0 should occur
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
}

// Remove should accept 0 to delete all fingerprints
// At least one callback with "remaining=0" should occur.
TEST_F(FingerprintHidlTest, RemoveAllFingerprintsTest) {
  // Register callback
  sp<RemoveCallback> cb = new RemoveCallback(kGroupId);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  // Remove all fingerprints
  Return<RequestStatus> res = mService->remove(kGroupId, 0);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
}

// Active group should successfully set to a writable location.
TEST_F(FingerprintHidlTest, SetActiveGroupTest) {
  // Create an active group
  Return<RequestStatus> res = mService->setActiveGroup(2, kTmpDir);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // Reset active group
  res = mService->setActiveGroup(kGroupId, kTmpDir);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));
}

// Active group should fail to set to an unwritable location.
TEST_F(FingerprintHidlTest, SetActiveGroupUnwritableTest) {
  // Create an active group to an unwritable location (device root dir)
  Return<RequestStatus> res = mService->setActiveGroup(3, "/");
  ASSERT_NE(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // Reset active group
  res = mService->setActiveGroup(kGroupId, kTmpDir);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));
}

// Active group should fail to set to a null location.
TEST_F(FingerprintHidlTest, SetActiveGroupNullTest) {
  // Create an active group to a null location.
  Return<RequestStatus> res = mService->setActiveGroup(4, nullptr);
  ASSERT_NE(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // Reset active group
  res = mService->setActiveGroup(kGroupId, kTmpDir);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));
}

// Cancel should always return ERROR_CANCELED from any starting state including
// the IDLE state.
TEST_F(FingerprintHidlTest, CancelTest) {
  sp<ErrorCallback> cb = new ErrorCallback(true, FingerprintError::ERROR_CANCELED);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0UL, static_cast<uint64_t>(rc));

  Return<RequestStatus> res = mService->cancel();
  // check that we were able to make an IPC request successfully
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // make sure callback was invoked within kTimeoutInSeconds
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));
  // check error should be ERROR_CANCELED
  ASSERT_EQ(FingerprintError::ERROR_CANCELED, cb->error);
}

// A call to cancel should succeed during enroll.
TEST_F(FingerprintHidlTest, CancelEnrollTest) {
  Return<RequestStatus> res = mService->setActiveGroup(kGroupId, kTmpDir);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  sp<ErrorCallback> cb = new ErrorCallback(true, FingerprintError::ERROR_CANCELED);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0U, static_cast<uint64_t>(rc));

  uint8_t token[69];
  res = mService->enroll(token, kGroupId, kTimeout);
  // check that we were able to make an IPC request successfully
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  res = mService->cancel();
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // make sure callback was invoked within kTimeoutInSeconds
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));

  // check error should be ERROR_CANCELED
  ASSERT_EQ(FingerprintError::ERROR_CANCELED, cb->error);
}

// A call to cancel should succeed during authentication.
TEST_F(FingerprintHidlTest, CancelAuthTest) {
  sp<ErrorCallback> cb = new ErrorCallback(true, FingerprintError::ERROR_CANCELED);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0U, static_cast<uint64_t>(rc));

  Return<RequestStatus> res = mService->authenticate(0, kGroupId);
  // check that we were able to make an IPC request successfully
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  res = mService->cancel();
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // make sure callback was invoked within kTimeoutInSeconds
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));

  // check error should be ERROR_CANCELED
  ASSERT_EQ(FingerprintError::ERROR_CANCELED, cb->error);
}

// A call to cancel should succeed during authentication.
TEST_F(FingerprintHidlTest, CancelRemoveTest) {
  sp<ErrorCallback> cb = new ErrorCallback(true, FingerprintError::ERROR_CANCELED);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0U, static_cast<uint64_t>(rc));

  // Remove a fingerprint
  Return<RequestStatus> res = mService->remove(kGroupId, 1);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  res = mService->cancel();
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // make sure callback was invoked within kTimeoutInSeconds
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));

  // check error should be ERROR_CANCELED
  ASSERT_EQ(FingerprintError::ERROR_CANCELED, cb->error);
}

// A call to cancel should succeed during authentication.
TEST_F(FingerprintHidlTest, CancelRemoveAllTest) {
  sp<ErrorCallback> cb = new ErrorCallback(true, FingerprintError::ERROR_CANCELED);
  Return<uint64_t> rc = mService->setNotify(cb);
  ASSERT_NE(0U, static_cast<uint64_t>(rc));

  // Remove a fingerprint
  Return<RequestStatus> res = mService->remove(kGroupId, 0);
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  res = mService->cancel();
  ASSERT_EQ(RequestStatus::SYS_OK, static_cast<RequestStatus>(res));

  // make sure callback was invoked within kTimeoutInSeconds
  ASSERT_TRUE(waitForCallback(cb->promise.get_future()));

  // check error should be ERROR_CANCELED
  ASSERT_EQ(FingerprintError::ERROR_CANCELED, cb->error);
}
}  // anonymous namespace

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  LOG(INFO) << "Test result = " << status;
  return status;
}

