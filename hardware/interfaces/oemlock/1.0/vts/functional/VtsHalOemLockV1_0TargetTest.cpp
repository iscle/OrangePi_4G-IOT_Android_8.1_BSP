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

#include <android/hardware/oemlock/1.0/IOemLock.h>

#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::oemlock::V1_0::IOemLock;
using ::android::hardware::oemlock::V1_0::OemLockStatus;
using ::android::hardware::oemlock::V1_0::OemLockSecureStatus;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::sp;

struct OemLockHidlTest : public ::testing::VtsHalHidlTargetTestBase {
    virtual void SetUp() override {
        oemlock = ::testing::VtsHalHidlTargetTestBase::getService<IOemLock>();
        ASSERT_NE(oemlock, nullptr);
    }

    virtual void TearDown() override {}

    sp<IOemLock> oemlock;
};

/*
 * Check the name can be retrieved
 */
TEST_F(OemLockHidlTest, GetName) {
    std::string name;
    OemLockStatus status;

    bool callbackCalled = false;
    const auto ret = oemlock->getName([&](OemLockStatus s, hidl_string n) {
        callbackCalled = true;
        status = s;
        name = n.c_str();
    });

    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    EXPECT_EQ(status, OemLockStatus::OK);
    // Any value acceptable
};

/*
 * Check the unlock allowed by device state can be queried
 */
TEST_F(OemLockHidlTest, QueryUnlockAllowedByDevice) {
    bool allowed;
    OemLockStatus status;

    bool callbackCalled = false;
    const auto ret = oemlock->isOemUnlockAllowedByDevice([&](OemLockStatus s, bool a) {
        callbackCalled = true;
        status = s;
        allowed = a;
    });

    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    EXPECT_EQ(status, OemLockStatus::OK);
    // Any value acceptable
}

/*
 * Check unlock allowed by device state can be toggled
 */
TEST_F(OemLockHidlTest, AllowedByDeviceCanBeToggled) {
    bool allowed;
    OemLockStatus status;

    auto getAllowedCallback = [&](OemLockStatus s, bool a) {
        status = s;
        allowed = a;
    };

    // Get the original state so it can be restored
    const auto get_ret = oemlock->isOemUnlockAllowedByDevice(getAllowedCallback);
    ASSERT_TRUE(get_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);
    const bool originallyAllowed = allowed;

    // Toggle the state
    const auto set_ret = oemlock->setOemUnlockAllowedByDevice(!originallyAllowed);
    ASSERT_TRUE(set_ret.isOk());
    ASSERT_EQ(set_ret, OemLockStatus::OK);
    const auto check_set_ret = oemlock->isOemUnlockAllowedByDevice(getAllowedCallback);
    ASSERT_TRUE(check_set_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);
    ASSERT_EQ(allowed, !originallyAllowed);

    // Restore the state
    const auto restore_ret = oemlock->setOemUnlockAllowedByDevice(originallyAllowed);
    ASSERT_TRUE(restore_ret.isOk());
    ASSERT_EQ(restore_ret, OemLockStatus::OK);
    const auto check_restore_ret = oemlock->isOemUnlockAllowedByDevice(getAllowedCallback);
    ASSERT_TRUE(check_restore_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);
    ASSERT_EQ(allowed, originallyAllowed);
};

/*
 * Check the unlock allowed by device state can be queried
 */
TEST_F(OemLockHidlTest, QueryUnlockAllowedByCarrier) {
    bool allowed;
    OemLockStatus status;

    bool callbackCalled = false;
    const auto ret = oemlock->isOemUnlockAllowedByCarrier([&](OemLockStatus s, bool a) {
        callbackCalled = true;
        status = s;
        allowed = a;
    });

    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    EXPECT_EQ(status, OemLockStatus::OK);
    // Any value acceptable
}

/*
 * Attempt to check unlock allowed by carrier can be toggled
 *
 * The implementation may involve a signature which cannot be tested here. That
 * is a valid implementation so the test will pass. If there is no signature
 * required, the test will toggle the value.
 */
TEST_F(OemLockHidlTest, CarrierUnlock) {
    const hidl_vec<uint8_t> noSignature = {};
    bool allowed;
    OemLockStatus status;

    auto getAllowedCallback = [&](OemLockStatus s, bool a) {
        status = s;
        allowed = a;
    };

    // Get the original state so it can be restored
    const auto get_ret = oemlock->isOemUnlockAllowedByCarrier(getAllowedCallback);
    ASSERT_TRUE(get_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);
    const bool originallyAllowed = allowed;

    if (originallyAllowed) {
        // Only applied to locked devices
        return;
    }

    // Toggle the state
    const auto set_ret = oemlock->setOemUnlockAllowedByCarrier(!originallyAllowed, noSignature);
    ASSERT_TRUE(set_ret.isOk());
    ASSERT_NE(set_ret, OemLockSecureStatus::FAILED);
    const auto check_set_ret = oemlock->isOemUnlockAllowedByCarrier(getAllowedCallback);
    ASSERT_TRUE(check_set_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);

    if (set_ret == OemLockSecureStatus::INVALID_SIGNATURE) {
        // Signature is required so we cannot toggle the value in the test, but this is allowed
        ASSERT_EQ(allowed, originallyAllowed);
        return;
    }

    ASSERT_EQ(set_ret, OemLockSecureStatus::OK);
    ASSERT_EQ(allowed, !originallyAllowed);

    // Restore the state
    const auto restore_ret = oemlock->setOemUnlockAllowedByCarrier(originallyAllowed, noSignature);
    ASSERT_TRUE(restore_ret.isOk());
    ASSERT_EQ(restore_ret, OemLockSecureStatus::OK);
    const auto check_restore_ret = oemlock->isOemUnlockAllowedByCarrier(getAllowedCallback);
    ASSERT_TRUE(check_restore_ret.isOk());
    ASSERT_EQ(status, OemLockStatus::OK);
    ASSERT_EQ(allowed, originallyAllowed);
};
