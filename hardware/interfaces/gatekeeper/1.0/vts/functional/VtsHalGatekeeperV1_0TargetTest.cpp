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

#define LOG_TAG "gatekeeper_hidl_hal_test"

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

#include <inttypes.h>
#include <unistd.h>

#include <hardware/hw_auth_token.h>

#include <android/log.h>
#include <android/hardware/gatekeeper/1.0/IGatekeeper.h>
#include <android/hardware/gatekeeper/1.0/types.h>

#include <log/log.h>

#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::gatekeeper::V1_0::IGatekeeper;
using ::android::hardware::gatekeeper::V1_0::GatekeeperResponse;
using ::android::hardware::gatekeeper::V1_0::GatekeeperStatusCode;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct GatekeeperRequest {
  uint32_t uid;
  uint64_t challenge;
  hidl_vec<uint8_t> curPwdHandle;
  hidl_vec<uint8_t> curPwd;
  hidl_vec<uint8_t> newPwd;
  GatekeeperRequest() : uid(0), challenge(0) {}
};

// ASSERT_* macros generate return "void" internally
// we have to use EXPECT_* if we return anything but "void"
static const hw_auth_token_t *toAuthToken(GatekeeperResponse &rsp) {
  const hw_auth_token_t *auth_token =
      reinterpret_cast<hw_auth_token_t *>(rsp.data.data());
  const size_t auth_token_size = rsp.data.size();

  EXPECT_NE(nullptr, auth_token);
  EXPECT_EQ(sizeof(hw_auth_token_t), auth_token_size);

  if (auth_token != nullptr && auth_token_size >= sizeof(*auth_token)) {
    // these are in network order: translate to host
    uint32_t auth_type = ntohl(auth_token->authenticator_type);
    uint64_t auth_tstamp = ntohq(auth_token->timestamp);

    EXPECT_EQ(HW_AUTH_PASSWORD, auth_type);
    EXPECT_NE(UINT64_C(~0), auth_tstamp);
    EXPECT_EQ(HW_AUTH_TOKEN_VERSION, auth_token->version);
    //        EXPECT_NE(UINT64_C(0), auth_token->authenticator_id);
    ALOGI("Authenticator ID: %016" PRIX64, auth_token->authenticator_id);
    EXPECT_NE(UINT32_C(0), auth_token->user_id);
  }
  return auth_token;
}

// The main test class for Gatekeeper HIDL HAL.
class GatekeeperHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 protected:
  void setUid(uint32_t uid) { uid_ = uid; }

  void doEnroll(GatekeeperRequest &req, GatekeeperResponse &rsp) {
    while (true) {
      auto ret = gatekeeper_->enroll(
          uid_, req.curPwdHandle, req.curPwd, req.newPwd,
          [&rsp](const GatekeeperResponse &cbRsp) { rsp = cbRsp; });
      ASSERT_TRUE(ret.isOk());
      if (rsp.code != GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) break;
      ALOGI("%s: got retry code; retrying in 1 sec", __func__);
      sleep(1);
    }
  }

  void doVerify(GatekeeperRequest &req, GatekeeperResponse &rsp) {
    while (true) {
      auto ret = gatekeeper_->verify(
          uid_, req.challenge, req.curPwdHandle, req.newPwd,
          [&rsp](const GatekeeperResponse &cb_rsp) { rsp = cb_rsp; });
      ASSERT_TRUE(ret.isOk());
      if (rsp.code != GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) break;
      ALOGI("%s: got retry code; retrying in 1 sec", __func__);
      sleep(1);
    }
  }

  void doDeleteUser(GatekeeperResponse &rsp) {
    while (true) {
      auto ret = gatekeeper_->deleteUser(
          uid_, [&rsp](const GatekeeperResponse &cb_rsp) { rsp = cb_rsp; });
      ASSERT_TRUE(ret.isOk());
      if (rsp.code != GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) break;
      ALOGI("%s: got retry code; retrying in 1 sec", __func__);
      sleep(1);
    }
  }

  void doDeleteAllUsers(GatekeeperResponse &rsp) {
    while (true) {
      auto ret = gatekeeper_->deleteAllUsers(
          [&rsp](const GatekeeperResponse &cb_rsp) { rsp = cb_rsp; });
      ASSERT_TRUE(ret.isOk());
      if (rsp.code != GatekeeperStatusCode::ERROR_RETRY_TIMEOUT) break;
      ALOGI("%s: got retry code; retrying in 1 sec", __func__);
      sleep(1);
    }
  }

  void generatePassword(hidl_vec<uint8_t> &password, uint8_t seed) {
    password.resize(16);
    memset(password.data(), seed, password.size());
  }

  void checkEnroll(GatekeeperResponse &rsp, bool expectSuccess) {
    if (expectSuccess) {
      EXPECT_EQ(GatekeeperStatusCode::STATUS_OK, rsp.code);
      EXPECT_NE(nullptr, rsp.data.data());
      EXPECT_GT(rsp.data.size(), UINT32_C(0));
    } else {
      EXPECT_EQ(GatekeeperStatusCode::ERROR_GENERAL_FAILURE, rsp.code);
      EXPECT_EQ(UINT32_C(0), rsp.data.size());
    }
  }

  void checkVerify(GatekeeperResponse &rsp, uint64_t challenge,
                   bool expectSuccess) {
    if (expectSuccess) {
      EXPECT_GE(rsp.code, GatekeeperStatusCode::STATUS_OK);
      EXPECT_LE(rsp.code, GatekeeperStatusCode::STATUS_REENROLL);

      const hw_auth_token_t *auth_token = toAuthToken(rsp);
      ASSERT_NE(nullptr, auth_token);
      EXPECT_EQ(challenge, auth_token->challenge);
    } else {
      EXPECT_EQ(GatekeeperStatusCode::ERROR_GENERAL_FAILURE, rsp.code);
      EXPECT_EQ(UINT32_C(0), rsp.data.size());
    }
  }

  void enrollNewPassword(hidl_vec<uint8_t> &password, GatekeeperResponse &rsp,
                         bool expectSuccess) {
    GatekeeperRequest req;
    req.newPwd.setToExternal(password.data(), password.size());
    doEnroll(req, rsp);
    checkEnroll(rsp, expectSuccess);
  }

  void verifyPassword(hidl_vec<uint8_t> &password,
                      hidl_vec<uint8_t> &passwordHandle, uint64_t challenge,
                      GatekeeperResponse &verifyRsp, bool expectSuccess) {
    GatekeeperRequest verifyReq;

    // build verify request for the same password (we want it to succeed)
    verifyReq.newPwd = password;
    // use enrolled password handle we've got
    verifyReq.curPwdHandle = passwordHandle;
    verifyReq.challenge = challenge;
    doVerify(verifyReq, verifyRsp);
    checkVerify(verifyRsp, challenge, expectSuccess);
  }

 protected:
  sp<IGatekeeper> gatekeeper_;
  uint32_t uid_;

 public:
  GatekeeperHidlTest() : uid_(0) {}
  virtual void SetUp() override {
    GatekeeperResponse rsp;
    gatekeeper_ = ::testing::VtsHalHidlTargetTestBase::getService<IGatekeeper>();
    ASSERT_NE(nullptr, gatekeeper_.get());
    doDeleteAllUsers(rsp);
  }

  virtual void TearDown() override {
    GatekeeperResponse rsp;
    doDeleteAllUsers(rsp);
  }
};

/**
 * Ensure we can enroll new password
 */
TEST_F(GatekeeperHidlTest, EnrollSuccess) {
  hidl_vec<uint8_t> password;
  GatekeeperResponse rsp;
  ALOGI("Testing Enroll (expected success)");
  generatePassword(password, 0);
  enrollNewPassword(password, rsp, true);
  ALOGI("Testing Enroll done");
}

/**
 * Ensure we can not enroll empty password
 */
TEST_F(GatekeeperHidlTest, EnrollNoPassword) {
  hidl_vec<uint8_t> password;
  GatekeeperResponse rsp;
  ALOGI("Testing Enroll (expected failure)");
  enrollNewPassword(password, rsp, false);
  ALOGI("Testing Enroll done");
}

/**
 * Ensure we can successfully verify previously enrolled password
 */
TEST_F(GatekeeperHidlTest, VerifySuccess) {
  GatekeeperResponse enrollRsp;
  GatekeeperResponse verifyRsp;
  hidl_vec<uint8_t> password;

  ALOGI("Testing Enroll+Verify (expected success)");
  generatePassword(password, 0);
  enrollNewPassword(password, enrollRsp, true);
  verifyPassword(password, enrollRsp.data, 1, verifyRsp, true);
  ALOGI("Testing Enroll+Verify done");
}

/**
 * Ensure we can securely update password (keep the same
 * secure user_id) if we prove we know old password
 */
TEST_F(GatekeeperHidlTest, TrustedReenroll) {
  GatekeeperResponse enrollRsp;
  GatekeeperRequest reenrollReq;
  GatekeeperResponse reenrollRsp;
  GatekeeperResponse verifyRsp;
  GatekeeperResponse reenrollVerifyRsp;
  hidl_vec<uint8_t> password;
  hidl_vec<uint8_t> newPassword;

  generatePassword(password, 0);

  ALOGI("Testing Trusted Reenroll (expected success)");
  enrollNewPassword(password, enrollRsp, true);
  verifyPassword(password, enrollRsp.data, 0, verifyRsp, true);
  ALOGI("Primary Enroll+Verify done");

  generatePassword(newPassword, 1);
  reenrollReq.newPwd.setToExternal(newPassword.data(), newPassword.size());
  reenrollReq.curPwd.setToExternal(password.data(), password.size());
  reenrollReq.curPwdHandle.setToExternal(enrollRsp.data.data(),
                                         enrollRsp.data.size());

  doEnroll(reenrollReq, reenrollRsp);
  checkEnroll(reenrollRsp, true);
  verifyPassword(newPassword, reenrollRsp.data, 0, reenrollVerifyRsp, true);
  ALOGI("Trusted ReEnroll+Verify done");

  const hw_auth_token_t *first = toAuthToken(verifyRsp);
  const hw_auth_token_t *second = toAuthToken(reenrollVerifyRsp);
  if (first != nullptr && second != nullptr) {
    EXPECT_EQ(first->user_id, second->user_id);
  }
  ALOGI("Testing Trusted Reenroll done");
}

/**
 * Ensure we can update password (and get new
 * secure user_id) if we don't know old password
 */
TEST_F(GatekeeperHidlTest, UntrustedReenroll) {
  GatekeeperResponse enrollRsp;
  GatekeeperResponse reenrollRsp;
  GatekeeperResponse verifyRsp;
  GatekeeperResponse reenrollVerifyRsp;
  hidl_vec<uint8_t> password;
  hidl_vec<uint8_t> newPassword;

  ALOGI("Testing Untrusted Reenroll (expected success)");
  generatePassword(password, 0);
  enrollNewPassword(password, enrollRsp, true);
  verifyPassword(password, enrollRsp.data, 0, verifyRsp, true);
  ALOGI("Primary Enroll+Verify done");

  generatePassword(newPassword, 1);
  enrollNewPassword(newPassword, reenrollRsp, true);
  verifyPassword(newPassword, reenrollRsp.data, 0, reenrollVerifyRsp, true);
  ALOGI("Untrusted ReEnroll+Verify done");

  const hw_auth_token_t *first = toAuthToken(verifyRsp);
  const hw_auth_token_t *second = toAuthToken(reenrollVerifyRsp);
  if (first != nullptr && second != nullptr) {
    EXPECT_NE(first->user_id, second->user_id);
  }
  ALOGI("Testing Untrusted Reenroll done");
}

/**
 * Ensure we dont get successful verify with invalid data
 */
TEST_F(GatekeeperHidlTest, VerifyNoData) {
  hidl_vec<uint8_t> password;
  hidl_vec<uint8_t> passwordHandle;
  GatekeeperResponse verifyRsp;

  ALOGI("Testing Verify (expected failure)");
  verifyPassword(password, passwordHandle, 0, verifyRsp, false);
  EXPECT_EQ(GatekeeperStatusCode::ERROR_GENERAL_FAILURE, verifyRsp.code);
  ALOGI("Testing Verify done");
}

/**
 * Ensure we can not verify password after we enrolled it and then deleted user
 */
TEST_F(GatekeeperHidlTest, DeleteUserTest) {
  hidl_vec<uint8_t> password;
  GatekeeperResponse enrollRsp;
  GatekeeperResponse verifyRsp;
  GatekeeperResponse delRsp;
  ALOGI("Testing deleteUser (expected success)");
  setUid(10001);
  generatePassword(password, 0);
  enrollNewPassword(password, enrollRsp, true);
  verifyPassword(password, enrollRsp.data, 0, verifyRsp, true);
  ALOGI("Enroll+Verify done");
  doDeleteUser(delRsp);
  EXPECT_EQ(UINT32_C(0), delRsp.data.size());
  EXPECT_TRUE(delRsp.code == GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED ||
              delRsp.code == GatekeeperStatusCode::STATUS_OK);
  ALOGI("DeleteUser done");
  if (delRsp.code == GatekeeperStatusCode::STATUS_OK) {
    verifyPassword(password, enrollRsp.data, 0, verifyRsp, false);
    EXPECT_EQ(GatekeeperStatusCode::ERROR_GENERAL_FAILURE, verifyRsp.code);
    ALOGI("Verify after Delete done (must fail)");
  }
  ALOGI("Testing deleteUser done: rsp=%" PRIi32, delRsp.code);
}

/**
 * Ensure we can not delete a user that does not exist
 */
TEST_F(GatekeeperHidlTest, DeleteInvalidUserTest) {
  hidl_vec<uint8_t> password;
  GatekeeperResponse enrollRsp;
  GatekeeperResponse verifyRsp;
  GatekeeperResponse delRsp1;
  GatekeeperResponse delRsp2;
  ALOGI("Testing deleteUser (expected failure)");
  setUid(10002);
  generatePassword(password, 0);
  enrollNewPassword(password, enrollRsp, true);
  verifyPassword(password, enrollRsp.data, 0, verifyRsp, true);
  ALOGI("Enroll+Verify done");

  // Delete the user
  doDeleteUser(delRsp1);
  EXPECT_EQ(UINT32_C(0), delRsp1.data.size());
  EXPECT_TRUE(delRsp1.code == GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED ||
              delRsp1.code == GatekeeperStatusCode::STATUS_OK);

  // Delete the user again
  doDeleteUser(delRsp2);
  EXPECT_EQ(UINT32_C(0), delRsp2.data.size());
  EXPECT_TRUE(delRsp2.code == GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED ||
              delRsp2.code == GatekeeperStatusCode::ERROR_GENERAL_FAILURE);
  ALOGI("DeleteUser done");
  ALOGI("Testing deleteUser done: rsp=%" PRIi32, delRsp2.code);
}

/**
 * Ensure we can not verify passwords after we enrolled them and then deleted
 * all users
 */
TEST_F(GatekeeperHidlTest, DeleteAllUsersTest) {
  struct UserData {
    uint32_t userId;
    hidl_vec<uint8_t> password;
    GatekeeperResponse enrollRsp;
    GatekeeperResponse verifyRsp;
    UserData(int id) { userId = id; }
  } users[3]{10001, 10002, 10003};
  GatekeeperResponse delAllRsp;
  ALOGI("Testing deleteAllUsers (expected success)");

  // enroll multiple users
  for (size_t i = 0; i < sizeof(users) / sizeof(users[0]); ++i) {
    setUid(users[i].userId);
    generatePassword(users[i].password, (i % 255) + 1);
    enrollNewPassword(users[i].password, users[i].enrollRsp, true);
  }
  ALOGI("Multiple users enrolled");

  // verify multiple users
  for (size_t i = 0; i < sizeof(users) / sizeof(users[0]); ++i) {
    setUid(users[i].userId);
    verifyPassword(users[i].password, users[i].enrollRsp.data, 0,
                   users[i].verifyRsp, true);
  }
  ALOGI("Multiple users verified");

  doDeleteAllUsers(delAllRsp);
  EXPECT_EQ(UINT32_C(0), delAllRsp.data.size());
  EXPECT_TRUE(delAllRsp.code == GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED ||
              delAllRsp.code == GatekeeperStatusCode::STATUS_OK);
  ALOGI("All users deleted");

  if (delAllRsp.code == GatekeeperStatusCode::STATUS_OK) {
    // verify multiple users after they are deleted; all must fail
    for (size_t i = 0; i < sizeof(users) / sizeof(users[0]); ++i) {
      setUid(users[i].userId);
      verifyPassword(users[i].password, users[i].enrollRsp.data, 0,
                     users[i].verifyRsp, false);
      EXPECT_EQ(GatekeeperStatusCode::ERROR_GENERAL_FAILURE,
                users[i].verifyRsp.code);
    }
    ALOGI("Multiple users verified after delete (all must fail)");
  }

  ALOGI("Testing deleteAllUsers done: rsp=%" PRIi32, delAllRsp.code);
}

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
