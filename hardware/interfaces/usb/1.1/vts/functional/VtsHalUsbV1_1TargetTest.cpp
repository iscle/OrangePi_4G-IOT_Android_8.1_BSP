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

#include <android/hardware/usb/1.0/types.h>
#include <android/hardware/usb/1.1/IUsb.h>
#include <android/hardware/usb/1.1/IUsbCallback.h>
#include <android/hardware/usb/1.1/types.h>

#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>
#include <log/log.h>
#include <stdlib.h>
#include <chrono>
#include <condition_variable>
#include <mutex>

using ::android::hardware::usb::V1_1::IUsbCallback;
using ::android::hardware::usb::V1_0::IUsb;
using ::android::hardware::usb::V1_0::PortDataRole;
using ::android::hardware::usb::V1_0::PortMode;
using ::android::hardware::usb::V1_1::PortMode_1_1;
using ::android::hardware::usb::V1_0::PortPowerRole;
using ::android::hardware::usb::V1_0::PortRole;
using ::android::hardware::usb::V1_0::PortRoleType;
using ::android::hardware::usb::V1_0::PortStatus;
using ::android::hardware::usb::V1_1::PortStatus_1_1;
using ::android::hardware::usb::V1_0::Status;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

constexpr char kCallbackNameNotifyPortStatusChange_1_1[] = "notifyPortStatusChange_1_1";

// Worst case wait time 20secs
#define WAIT_FOR_TIMEOUT std::chrono::milliseconds(20000)

class UsbClientCallbackArgs {
   public:
    // The last conveyed status of the USB ports.
    // Stores information of currentt_data_role, power_role for all the USB ports
    PortStatus_1_1 usb_last_port_status;

    // Status of the last role switch operation.
    Status usb_last_status;

    // Identifier for the usb callback object.
    // Stores the cookie of the last invoked usb callback object.
    int last_usb_cookie;
};

// Callback class for the USB HIDL hal.
// Usb Hal will call this object upon role switch or port query.
class UsbCallback : public ::testing::VtsHalHidlTargetCallbackBase<UsbClientCallbackArgs>,
                    public IUsbCallback {
    int cookie;

   public:
    UsbCallback(int cookie) : cookie(cookie){};

    virtual ~UsbCallback() = default;

    // V1_0 Callback method for the port status.
    // This should not be called so not signalling the Test here assuming that
    // the test thread will timeout
    Return<void> notifyPortStatusChange(const hidl_vec<PortStatus>& /* currentPortStatus */,
                                        Status /*retval*/) override {
        return Void();
    };

    // This callback methode should be used.
    Return<void> notifyPortStatusChange_1_1(const hidl_vec<PortStatus_1_1>& currentPortStatus,
                                            Status retval) override {
        UsbClientCallbackArgs arg;
        if (retval == Status::SUCCESS) {
            arg.usb_last_port_status.status.supportedModes =
                currentPortStatus[0].status.supportedModes;
            arg.usb_last_port_status.status.currentMode = currentPortStatus[0].status.currentMode;
        }
        arg.usb_last_status = retval;
        arg.last_usb_cookie = cookie;

        NotifyFromCallback(kCallbackNameNotifyPortStatusChange_1_1, arg);
        return Void();
    }

    // Callback method for the status of role switch operation.
    // RoleSwitch operation has not changed since V1_0 so leaving
    // the callback blank here.
    Return<void> notifyRoleSwitchStatus(const hidl_string& /*portName*/,
                                        const PortRole& /*newRole*/, Status /*retval*/) override {
        return Void();
    };
};

// The main test class for the USB hidl HAL
class UsbHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        ALOGI(__FUNCTION__);
        usb = ::testing::VtsHalHidlTargetTestBase::getService<IUsb>();
        ASSERT_NE(usb, nullptr);

        usb_cb_2 = new UsbCallback(2);
        ASSERT_NE(usb_cb_2, nullptr);
        usb_cb_2->SetWaitTimeout(kCallbackNameNotifyPortStatusChange_1_1, WAIT_FOR_TIMEOUT);
        Return<void> ret = usb->setCallback(usb_cb_2);
        ASSERT_TRUE(ret.isOk());
    }

    virtual void TearDown() override { ALOGI("Teardown"); }

    // USB hidl hal Proxy
    sp<IUsb> usb;

    // Callback objects for usb hidl
    // Methods of these objects are called to notify port status updates.
    sp<UsbCallback> usb_cb_1;
    sp<UsbCallback> usb_cb_2;
};

/*
 * Test to see if setCallback on V1_1 callback object succeeds.
 * Callback oject is created and registered.
 * Check to see if the hidl transaction succeeded.
 */
TEST_F(UsbHidlTest, setCallback) {
    usb_cb_1 = new UsbCallback(1);
    ASSERT_NE(usb_cb_1, nullptr);
    Return<void> ret = usb->setCallback(usb_cb_1);
    ASSERT_TRUE(ret.isOk());
}

/*
 * Check to see if querying type-c
 * port status succeeds.
 * HAL service should call notifyPortStatusChange_1_1
 * instead of notifyPortStatusChange of V1_0 interface
 */
TEST_F(UsbHidlTest, queryPortStatus) {
    Return<void> ret = usb->queryPortStatus();
    ASSERT_TRUE(ret.isOk());
    auto res = usb_cb_2->WaitForCallback(kCallbackNameNotifyPortStatusChange_1_1);
    EXPECT_TRUE(res.no_timeout);
    EXPECT_EQ(2, res.args->last_usb_cookie);
    EXPECT_EQ(PortMode::NONE, res.args->usb_last_port_status.status.currentMode);
    EXPECT_EQ(PortMode::NONE, res.args->usb_last_port_status.status.supportedModes);
    EXPECT_EQ(Status::SUCCESS, res.args->usb_last_status);
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    ALOGI("Test result = %d", status);
    return status;
}
