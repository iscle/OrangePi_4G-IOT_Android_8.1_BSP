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

#include <android-base/logging.h>
#include <VtsHalHidlTargetTestBase.h>

#include <android/hidl/manager/1.0/IServiceManager.h>
#include <android/hidl/manager/1.0/IServiceNotification.h>
#include <hidl/HidlTransportSupport.h>

#include <wifi_system/interface_tool.h>
#include <wifi_system/supplicant_manager.h>

#include "supplicant_hidl_test_utils.h"
#include "wifi_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::wifi::V1_0::ChipModeId;
using ::android::hardware::wifi::V1_0::IWifiChip;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicant;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface;
using ::android::hardware::wifi::supplicant::V1_0::IfaceType;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode;
using ::android::hidl::manager::V1_0::IServiceNotification;
using ::android::wifi_system::InterfaceTool;
using ::android::wifi_system::SupplicantManager;

namespace {
const char kSupplicantServiceName[] = "default";

// Helper function to initialize the driver and firmware to STA mode
// using the vendor HAL HIDL interface.
void initilializeDriverAndFirmware() {
    sp<IWifiChip> wifi_chip = getWifiChip();
    ChipModeId mode_id;
    EXPECT_TRUE(configureChipToSupportIfaceType(
        wifi_chip, ::android::hardware::wifi::V1_0::IfaceType::STA, &mode_id));
}

// Helper function to deinitialize the driver and firmware
// using the vendor HAL HIDL interface.
void deInitilializeDriverAndFirmware() { stopWifi(); }

// Helper function to find any iface of the desired type exposed.
bool findIfaceOfType(sp<ISupplicant> supplicant, IfaceType desired_type,
                     ISupplicant::IfaceInfo* out_info) {
    bool operation_failed = false;
    std::vector<ISupplicant::IfaceInfo> iface_infos;
    supplicant->listInterfaces([&](const SupplicantStatus& status,
                                   hidl_vec<ISupplicant::IfaceInfo> infos) {
        if (status.code != SupplicantStatusCode::SUCCESS) {
            operation_failed = true;
            return;
        }
        iface_infos = infos;
    });
    if (operation_failed) {
        return false;
    }
    for (const auto& info : iface_infos) {
        if (info.type == desired_type) {
            *out_info = info;
            return true;
        }
    }
    return false;
}
}  // namespace

// Utility class to wait for wpa_supplicant's HIDL service registration.
class ServiceNotificationListener : public IServiceNotification {
   public:
    Return<void> onRegistration(const hidl_string& fully_qualified_name,
                                const hidl_string& instance_name,
                                bool pre_existing) override {
        if (pre_existing) {
            return Void();
        }
        std::unique_lock<std::mutex> lock(mutex_);
        registered_.push_back(std::string(fully_qualified_name.c_str()) + "/" +
                              instance_name.c_str());
        lock.unlock();
        condition_.notify_one();
        return Void();
    }

    bool registerForHidlServiceNotifications(const std::string& instance_name) {
        if (!ISupplicant::registerForNotifications(instance_name, this)) {
            return false;
        }
        configureRpcThreadpool(2, false);
        return true;
    }

    bool waitForHidlService(uint32_t timeout_in_millis,
                            const std::string& instance_name) {
        std::unique_lock<std::mutex> lock(mutex_);
        condition_.wait_for(lock, std::chrono::milliseconds(timeout_in_millis),
                            [&]() { return registered_.size() >= 1; });
        if (registered_.size() != 1) {
            return false;
        }
        std::string exptected_registered =
            std::string(ISupplicant::descriptor) + "/" + instance_name;
        if (registered_[0] != exptected_registered) {
            LOG(ERROR) << "Expected: " << exptected_registered
                       << ", Got: " << registered_[0];
            return false;
        }
        return true;
    }

   private:
    std::vector<std::string> registered_{};
    std::mutex mutex_;
    std::condition_variable condition_;
};

void stopSupplicant() {
    SupplicantManager supplicant_manager;

    ASSERT_TRUE(supplicant_manager.StopSupplicant());
    deInitilializeDriverAndFirmware();
    ASSERT_FALSE(supplicant_manager.IsSupplicantRunning());
}

void startSupplicantAndWaitForHidlService() {
    initilializeDriverAndFirmware();

    android::sp<ServiceNotificationListener> notification_listener =
        new ServiceNotificationListener();
    ASSERT_TRUE(notification_listener->registerForHidlServiceNotifications(
        kSupplicantServiceName));

    SupplicantManager supplicant_manager;
    ASSERT_TRUE(supplicant_manager.StartSupplicant());
    ASSERT_TRUE(supplicant_manager.IsSupplicantRunning());

    ASSERT_TRUE(
        notification_listener->waitForHidlService(200, kSupplicantServiceName));
}

sp<ISupplicant> getSupplicant() {
    return ::testing::VtsHalHidlTargetTestBase::getService<ISupplicant>();
}

sp<ISupplicantStaIface> getSupplicantStaIface() {
    sp<ISupplicant> supplicant = getSupplicant();
    if (!supplicant.get()) {
        return nullptr;
    }
    ISupplicant::IfaceInfo info;
    if (!findIfaceOfType(supplicant, IfaceType::STA, &info)) {
        return nullptr;
    }
    bool operation_failed = false;
    sp<ISupplicantStaIface> sta_iface;
    supplicant->getInterface(info, [&](const SupplicantStatus& status,
                                       const sp<ISupplicantIface>& iface) {
        if (status.code != SupplicantStatusCode::SUCCESS) {
            operation_failed = true;
            return;
        }
        sta_iface = ISupplicantStaIface::castFrom(iface);
    });
    if (operation_failed) {
        return nullptr;
    }
    return sta_iface;
}

sp<ISupplicantStaNetwork> createSupplicantStaNetwork() {
    sp<ISupplicantStaIface> sta_iface = getSupplicantStaIface();
    if (!sta_iface.get()) {
        return nullptr;
    }
    bool operation_failed = false;
    sp<ISupplicantStaNetwork> sta_network;
    sta_iface->addNetwork([&](const SupplicantStatus& status,
                              const sp<ISupplicantNetwork>& network) {
        if (status.code != SupplicantStatusCode::SUCCESS) {
            operation_failed = true;
            return;
        }
        sta_network = ISupplicantStaNetwork::castFrom(network);
    });
    if (operation_failed) {
        return nullptr;
    }
    return sta_network;
}

sp<ISupplicantP2pIface> getSupplicantP2pIface() {
    sp<ISupplicant> supplicant = getSupplicant();
    if (!supplicant.get()) {
        return nullptr;
    }
    ISupplicant::IfaceInfo info;
    if (!findIfaceOfType(supplicant, IfaceType::P2P, &info)) {
        return nullptr;
    }
    bool operation_failed = false;
    sp<ISupplicantP2pIface> p2p_iface;
    supplicant->getInterface(info, [&](const SupplicantStatus& status,
                                       const sp<ISupplicantIface>& iface) {
        if (status.code != SupplicantStatusCode::SUCCESS) {
            operation_failed = true;
            return;
        }
        p2p_iface = ISupplicantP2pIface::castFrom(iface);
    });
    if (operation_failed) {
        return nullptr;
    }
    return p2p_iface;
}

bool turnOnExcessiveLogging() {
    sp<ISupplicant> supplicant = getSupplicant();
    if (!supplicant.get()) {
        return false;
    }
    bool operation_failed = false;
    supplicant->setDebugParams(
        ISupplicant::DebugLevel::EXCESSIVE,
        true,  // show timestamps
        true,  // show keys
        [&](const SupplicantStatus& status) {
            if (status.code != SupplicantStatusCode::SUCCESS) {
                operation_failed = true;
            }
        });
    return !operation_failed;
}
