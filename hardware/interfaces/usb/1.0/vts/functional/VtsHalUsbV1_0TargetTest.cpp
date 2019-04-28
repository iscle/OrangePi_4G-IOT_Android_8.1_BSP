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

#define LOG_TAG "VtsHalUsbV1_0TargetTest"
#include <android-base/logging.h>

#include <android/hardware/usb/1.0/IUsb.h>
#include <android/hardware/usb/1.0/IUsbCallback.h>
#include <android/hardware/usb/1.0/types.h>

#include <VtsHalHidlTargetTestBase.h>
#include <log/log.h>
#include <stdlib.h>
#include <chrono>
#include <condition_variable>
#include <mutex>

#define TIMEOUT_PERIOD 10

using ::android::hardware::usb::V1_0::IUsbCallback;
using ::android::hardware::usb::V1_0::IUsb;
using ::android::hardware::usb::V1_0::PortDataRole;
using ::android::hardware::usb::V1_0::PortMode;
using ::android::hardware::usb::V1_0::PortPowerRole;
using ::android::hardware::usb::V1_0::PortRole;
using ::android::hardware::usb::V1_0::PortRoleType;
using ::android::hardware::usb::V1_0::PortStatus;
using ::android::hardware::usb::V1_0::Status;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

// The main test class for the USB hidl HAL
class UsbHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  // Callback class for the USB HIDL hal.
  // Usb Hal will call this object upon role switch or port query.
  class UsbCallback : public IUsbCallback {
    UsbHidlTest& parent_;
    int cookie;

   public:
    UsbCallback(UsbHidlTest& parent, int cookie)
        : parent_(parent), cookie(cookie){};

    virtual ~UsbCallback() = default;

    // Callback method for the port status.
    Return<void> notifyPortStatusChange(
        const hidl_vec<PortStatus>& currentPortStatus, Status retval) override {
      if (retval == Status::SUCCESS) {
        parent_.usb_last_port_status.portName =
            currentPortStatus[0].portName.c_str();
        parent_.usb_last_port_status.currentDataRole =
            currentPortStatus[0].currentDataRole;
        parent_.usb_last_port_status.currentPowerRole =
            currentPortStatus[0].currentPowerRole;
        parent_.usb_last_port_status.currentMode =
            currentPortStatus[0].currentMode;
      }
      parent_.usb_last_cookie = cookie;
      parent_.notify();
      return Void();
    };

    // Callback method for the status of role switch operation.
    Return<void> notifyRoleSwitchStatus(const hidl_string& /*portName*/,
                                        const PortRole& newRole,
                                        Status retval) override {
      parent_.usb_last_status = retval;
      parent_.usb_last_cookie = cookie;
      parent_.usb_last_port_role = newRole;
      parent_.usb_role_switch_done = true;
      parent_.notify();
      return Void();
    };
  };

  virtual void SetUp() override {
    ALOGI("Setup");
    usb = ::testing::VtsHalHidlTargetTestBase::getService<IUsb>();
    ASSERT_NE(usb, nullptr);

    usb_cb_2 = new UsbCallback(*this, 2);
    ASSERT_NE(usb_cb_2, nullptr);
    Return<void> ret = usb->setCallback(usb_cb_2);
    ASSERT_TRUE(ret.isOk());
  }

  virtual void TearDown() override { ALOGI("Teardown"); }

  // Used as a mechanism to inform the test about data/event callback.
  inline void notify() {
    std::unique_lock<std::mutex> lock(usb_mtx);
    usb_count++;
    usb_cv.notify_one();
  }

  // Test code calls this function to wait for data/event callback.
  inline std::cv_status wait() {
    std::unique_lock<std::mutex> lock(usb_mtx);

    std::cv_status status = std::cv_status::no_timeout;
    auto now = std::chrono::system_clock::now();
    while (usb_count == 0) {
      status =
          usb_cv.wait_until(lock, now + std::chrono::seconds(TIMEOUT_PERIOD));
      if (status == std::cv_status::timeout) {
        ALOGI("timeout");
        return status;
      }
    }
    usb_count--;
    return status;
  }

  // USB hidl hal Proxy
  sp<IUsb> usb;

  // Callback objects for usb hidl
  // Methods of these objects are called to notify port status updates.
  sp<IUsbCallback> usb_cb_1, usb_cb_2;

  // The last conveyed status of the USB ports.
  // Stores information of currentt_data_role, power_role for all the USB ports
  PortStatus usb_last_port_status;

  // Status of the last role switch operation.
  Status usb_last_status;

  // Port role information of the last role switch operation.
  PortRole usb_last_port_role;

  // Flag to indicate the invocation of role switch callback.
  bool usb_role_switch_done;

  // Identifier for the usb callback object.
  // Stores the cookie of the last invoked usb callback object.
  int usb_last_cookie;

  // synchronization primitives to coordinate between main test thread
  // and the callback thread.
  std::mutex usb_mtx;
  std::condition_variable usb_cv;
  int usb_count;
};

/*
 * Test to see if setCallback succeeds.
 * Callback oject is created and registered.
 * Check to see if the hidl transaction succeeded.
 */
TEST_F(UsbHidlTest, setCallback) {
  usb_cb_1 = new UsbCallback(*this, 1);
  ASSERT_NE(usb_cb_1, nullptr);
  Return<void> ret = usb->setCallback(usb_cb_1);
  ASSERT_TRUE(ret.isOk());
}

/*
 * Check to see if querying type-c
 * port status succeeds.
 */
TEST_F(UsbHidlTest, queryPortStatus) {
  Return<void> ret = usb->queryPortStatus();
  ASSERT_TRUE(ret.isOk());
  EXPECT_EQ(std::cv_status::no_timeout, wait());
  EXPECT_EQ(2, usb_last_cookie);
  ALOGI("rightafter: %s", usb_last_port_status.portName.c_str());
}

/*
 * Trying to switch a non-existent port should fail.
 * This test case tried to switch the port with empty
 * name which is expected to fail.
 */
TEST_F(UsbHidlTest, switchEmptyPort) {
  struct PortRole role;
  role.type = PortRoleType::DATA_ROLE;

  Return<void> ret = usb->switchRole("", role);
  ASSERT_TRUE(ret.isOk());
  EXPECT_EQ(std::cv_status::no_timeout, wait());
  EXPECT_EQ(Status::ERROR, usb_last_status);
  EXPECT_EQ(2, usb_last_cookie);
}

/*
 * Test switching the mode of usb port.
 * Test case queries the usb ports present in device.
 * If there is atleast one usb port, a mode switch
 * to DFP is attempted for the port.
 * The callback parametes are checked to see if the mode
 * switch was successfull. Upon success, Status::SUCCESS
 * is expected to be returned.
 */
TEST_F(UsbHidlTest, switchModetoDFP) {
  struct PortRole role;
  role.type = PortRoleType::MODE;
  role.role = static_cast<uint32_t>(PortMode::DFP);

  Return<void> ret = usb->queryPortStatus();
  ASSERT_TRUE(ret.isOk());
  EXPECT_EQ(std::cv_status::no_timeout, wait());
  EXPECT_EQ(2, usb_last_cookie);

  if (!usb_last_port_status.portName.empty()) {
    hidl_string portBeingSwitched = usb_last_port_status.portName;
    ALOGI("mode portname:%s", portBeingSwitched.c_str());
    usb_role_switch_done = false;
    Return<void> ret = usb->switchRole(portBeingSwitched.c_str(), role);
    ASSERT_TRUE(ret.isOk());

    std::cv_status waitStatus = wait();
    while (waitStatus == std::cv_status::no_timeout &&
           usb_role_switch_done == false)
      waitStatus = wait();

    EXPECT_EQ(std::cv_status::no_timeout, waitStatus);
    EXPECT_EQ(2, usb_last_cookie);

    EXPECT_EQ(static_cast<uint32_t>(PortRoleType::MODE),
              static_cast<uint32_t>(usb_last_port_role.type));
    if (usb_last_status == Status::SUCCESS) {
      EXPECT_EQ(static_cast<uint32_t>(PortMode::DFP),
                static_cast<uint32_t>(usb_last_port_role.role));
    } else {
      EXPECT_NE(static_cast<uint32_t>(PortMode::UFP),
                static_cast<uint32_t>(usb_last_port_role.role));
    }
  }
}

/*
 * Test switching the power role of usb port.
 * Test case queries the usb ports present in device.
 * If there is atleast one usb port, a power role switch
 * to SOURCE is attempted for the port.
 * The callback parametes are checked to see if the power role
 * switch was successfull. Upon success, Status::SUCCESS
 * is expected to be returned.
 */

TEST_F(UsbHidlTest, switchPowerRole) {
  struct PortRole role;
  role.type = PortRoleType::POWER_ROLE;
  role.role = static_cast<uint32_t>(PortPowerRole::SOURCE);

  Return<void> ret = usb->queryPortStatus();
  ASSERT_TRUE(ret.isOk());
  EXPECT_EQ(std::cv_status::no_timeout, wait());
  EXPECT_EQ(2, usb_last_cookie);

  if (!usb_last_port_status.portName.empty()) {
    hidl_string portBeingSwitched = usb_last_port_status.portName;
    ALOGI("switchPower role portname:%s", portBeingSwitched.c_str());
    usb_role_switch_done = false;
    Return<void> ret = usb->switchRole(portBeingSwitched.c_str(), role);
    ASSERT_TRUE(ret.isOk());

    std::cv_status waitStatus = wait();
    while (waitStatus == std::cv_status::no_timeout &&
           usb_role_switch_done == false)
      waitStatus = wait();

    EXPECT_EQ(std::cv_status::no_timeout, waitStatus);
    EXPECT_EQ(2, usb_last_cookie);

    EXPECT_EQ(static_cast<uint32_t>(PortRoleType::POWER_ROLE),
              static_cast<uint32_t>(usb_last_port_role.type));
    if (usb_last_status == Status::SUCCESS) {
      EXPECT_EQ(static_cast<uint32_t>(PortPowerRole::SOURCE),
                static_cast<uint32_t>(usb_last_port_role.role));
    } else {
      EXPECT_NE(static_cast<uint32_t>(PortPowerRole::SINK),
                static_cast<uint32_t>(usb_last_port_role.role));
    }
  }
}

/*
 * Test switching the data role of usb port.
 * Test case queries the usb ports present in device.
 * If there is atleast one usb port, a power role switch
 * to HOST is attempted for the port.
 * The callback parametes are checked to see if the power role
 * switch was successfull. Upon success, Status::SUCCESS
 * is expected to be returned.
 */
TEST_F(UsbHidlTest, switchDataRole) {
  struct PortRole role;
  role.type = PortRoleType::DATA_ROLE;
  role.role = static_cast<uint32_t>(PortDataRole::HOST);

  Return<void> ret = usb->queryPortStatus();
  ASSERT_TRUE(ret.isOk());
  EXPECT_EQ(std::cv_status::no_timeout, wait());
  EXPECT_EQ(2, usb_last_cookie);

  if (!usb_last_port_status.portName.empty()) {
    hidl_string portBeingSwitched = usb_last_port_status.portName;
    ALOGI("portname:%s", portBeingSwitched.c_str());
    usb_role_switch_done = false;
    Return<void> ret = usb->switchRole(portBeingSwitched.c_str(), role);
    ASSERT_TRUE(ret.isOk());

    std::cv_status waitStatus = wait();
    while (waitStatus == std::cv_status::no_timeout &&
           usb_role_switch_done == false)
      waitStatus = wait();

    EXPECT_EQ(std::cv_status::no_timeout, waitStatus);
    EXPECT_EQ(2, usb_last_cookie);

    EXPECT_EQ(static_cast<uint32_t>(PortRoleType::DATA_ROLE),
              static_cast<uint32_t>(usb_last_port_role.type));
    if (usb_last_status == Status::SUCCESS) {
      EXPECT_EQ(static_cast<uint32_t>(PortDataRole::HOST),
                static_cast<uint32_t>(usb_last_port_role.role));
    } else {
      EXPECT_NE(static_cast<uint32_t>(PortDataRole::DEVICE),
                static_cast<uint32_t>(usb_last_port_role.role));
    }
  }
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
