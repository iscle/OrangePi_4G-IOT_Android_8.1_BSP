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

#include <android/hardware/wifi/supplicant/1.0/ISupplicant.h>

#include "supplicant_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicant;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode;
using ::android::hardware::wifi::supplicant::V1_0::IfaceType;

class SupplicantHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        startSupplicantAndWaitForHidlService();
        supplicant_ = getSupplicant();
        ASSERT_NE(supplicant_.get(), nullptr);
    }

    virtual void TearDown() override { stopSupplicant(); }

   protected:
    // ISupplicant object used for all tests in this fixture.
    sp<ISupplicant> supplicant_;
};

/*
 * Create:
 * Ensures that an instance of the ISupplicant proxy object is
 * successfully created.
 */
TEST(SupplicantHidlTestNoFixture, Create) {
    startSupplicantAndWaitForHidlService();
    EXPECT_NE(nullptr, getSupplicant().get());
    stopSupplicant();
}

/*
 * ListInterfaces
 */
TEST_F(SupplicantHidlTest, ListInterfaces) {
    std::vector<ISupplicant::IfaceInfo> ifaces;
    supplicant_->listInterfaces(
        [&](const SupplicantStatus& status,
            const hidl_vec<ISupplicant::IfaceInfo>& hidl_ifaces) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            ifaces = hidl_ifaces;
        });

    EXPECT_NE(ifaces.end(),
              std::find_if(ifaces.begin(), ifaces.end(), [](const auto& iface) {
                  return iface.type == IfaceType::STA;
              }));
    EXPECT_NE(ifaces.end(),
              std::find_if(ifaces.begin(), ifaces.end(), [](const auto& iface) {
                  return iface.type == IfaceType::P2P;
              }));
}

/*
 * GetInterface
 */
TEST_F(SupplicantHidlTest, GetInterface) {
    std::vector<ISupplicant::IfaceInfo> ifaces;
    supplicant_->listInterfaces(
        [&](const SupplicantStatus& status,
            const hidl_vec<ISupplicant::IfaceInfo>& hidl_ifaces) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            ifaces = hidl_ifaces;
        });

    ASSERT_NE(0u, ifaces.size());
    supplicant_->getInterface(
        ifaces[0],
        [&](const SupplicantStatus& status, const sp<ISupplicantIface>& iface) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_NE(nullptr, iface.get());
        });
}

/*
 * SetDebugParams
 */
TEST_F(SupplicantHidlTest, SetDebugParams) {
    bool show_timestamp = true;
    bool show_keys = true;
    ISupplicant::DebugLevel level = ISupplicant::DebugLevel::EXCESSIVE;

    supplicant_->setDebugParams(level,
                                show_timestamp,  // show timestamps
                                show_keys,       // show keys
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
}

/*
 * GetDebugLevel
 */
TEST_F(SupplicantHidlTest, GetDebugLevel) {
    bool show_timestamp = true;
    bool show_keys = true;
    ISupplicant::DebugLevel level = ISupplicant::DebugLevel::EXCESSIVE;

    supplicant_->setDebugParams(level,
                                show_timestamp,  // show timestamps
                                show_keys,       // show keys
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
    EXPECT_EQ(level, supplicant_->getDebugLevel());
}

/*
 * IsDebugShowTimestampEnabled
 */
TEST_F(SupplicantHidlTest, IsDebugShowTimestampEnabled) {
    bool show_timestamp = true;
    bool show_keys = true;
    ISupplicant::DebugLevel level = ISupplicant::DebugLevel::EXCESSIVE;

    supplicant_->setDebugParams(level,
                                show_timestamp,  // show timestamps
                                show_keys,       // show keys
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
    EXPECT_EQ(show_timestamp, supplicant_->isDebugShowTimestampEnabled());
}

/*
 * IsDebugShowKeysEnabled
 */
TEST_F(SupplicantHidlTest, IsDebugShowKeysEnabled) {
    bool show_timestamp = true;
    bool show_keys = true;
    ISupplicant::DebugLevel level = ISupplicant::DebugLevel::EXCESSIVE;

    supplicant_->setDebugParams(level,
                                show_timestamp,  // show timestamps
                                show_keys,       // show keys
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
    EXPECT_EQ(show_keys, supplicant_->isDebugShowKeysEnabled());
}

/*
 * SetConcurrenyPriority
 */
TEST_F(SupplicantHidlTest, SetConcurrencyPriority) {
    supplicant_->setConcurrencyPriority(
        IfaceType::STA, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    supplicant_->setConcurrencyPriority(
        IfaceType::P2P, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}
