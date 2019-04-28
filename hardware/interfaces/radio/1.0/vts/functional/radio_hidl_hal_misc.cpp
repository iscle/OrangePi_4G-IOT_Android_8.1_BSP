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

#include <radio_hidl_hal_utils_v1_0.h>

/*
 * Test IRadio.getSignalStrength() for the response returned.
 */
TEST_F(RadioHidlTest, getSignalStrength) {
    int serial = GetRandomSerialNumber();

    radio->getSignalStrength(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getVoiceRegistrationState() for the response returned.
 */
TEST_F(RadioHidlTest, getVoiceRegistrationState) {
    int serial = GetRandomSerialNumber();

    radio->getVoiceRegistrationState(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getOperator() for the response returned.
 */
TEST_F(RadioHidlTest, getOperator) {
    int serial = GetRandomSerialNumber();

    radio->getOperator(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setRadioPower() for the response returned.
 */
TEST_F(RadioHidlTest, setRadioPower) {
    int serial = GetRandomSerialNumber();

    radio->setRadioPower(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getNetworkSelectionMode() for the response returned.
 */
TEST_F(RadioHidlTest, getNetworkSelectionMode) {
    int serial = GetRandomSerialNumber();

    radio->getNetworkSelectionMode(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setNetworkSelectionModeAutomatic() for the response returned.
 */
TEST_F(RadioHidlTest, setNetworkSelectionModeAutomatic) {
    int serial = GetRandomSerialNumber();

    radio->setNetworkSelectionModeAutomatic(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::ILLEGAL_SIM_OR_ME ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::OPERATION_NOT_ALLOWED);
    }
}

/*
 * Test IRadio.setNetworkSelectionModeManual() for the response returned.
 */
TEST_F(RadioHidlTest, setNetworkSelectionModeManual) {
    int serial = GetRandomSerialNumber();

    radio->setNetworkSelectionModeManual(serial, "123456");
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::ILLEGAL_SIM_OR_ME ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE);
    }
}

/*
 * Test IRadio.getAvailableNetworks() for the response returned.
 */
TEST_F(RadioHidlTest, getAvailableNetworks) {
    int serial = GetRandomSerialNumber();

    radio->getAvailableNetworks(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);
    ASSERT_TRUE(radioRsp->rspInfo.type == RadioResponseType::SOLICITED ||
                radioRsp->rspInfo.type == RadioResponseType::SOLICITED_ACK_EXP);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::DEVICE_IN_USE ||
                    radioRsp->rspInfo.error == RadioError::CANCELLED ||
                    radioRsp->rspInfo.error == RadioError::OPERATION_NOT_ALLOWED ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
  }
}

/*
 * Test IRadio.getBasebandVersion() for the response returned.
 */
TEST_F(RadioHidlTest, getBasebandVersion) {
    int serial = GetRandomSerialNumber();

    radio->getBasebandVersion(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setBandMode() for the response returned.
 */
TEST_F(RadioHidlTest, setBandMode) {
    int serial = GetRandomSerialNumber();

    radio->setBandMode(serial, RadioBandMode::BAND_MODE_USA);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getAvailableBandModes() for the response returned.
 */
TEST_F(RadioHidlTest, getAvailableBandModes) {
    int serial = GetRandomSerialNumber();

    radio->getAvailableBandModes(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setPreferredNetworkType() for the response returned.
 */
TEST_F(RadioHidlTest, setPreferredNetworkType) {
    int serial = GetRandomSerialNumber();

    radio->setPreferredNetworkType(serial, PreferredNetworkType::GSM_ONLY);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getPreferredNetworkType() for the response returned.
 */
TEST_F(RadioHidlTest, getPreferredNetworkType) {
    int serial = GetRandomSerialNumber();

    radio->getPreferredNetworkType(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getNeighboringCids() for the response returned.
 */
TEST_F(RadioHidlTest, getNeighboringCids) {
    int serial = GetRandomSerialNumber();

    radio->getNeighboringCids(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.setLocationUpdates() for the response returned.
 */
TEST_F(RadioHidlTest, setLocationUpdates) {
    int serial = GetRandomSerialNumber();

    radio->setLocationUpdates(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.setCdmaRoamingPreference() for the response returned.
 */
TEST_F(RadioHidlTest, setCdmaRoamingPreference) {
    int serial = GetRandomSerialNumber();

    radio->setCdmaRoamingPreference(serial, CdmaRoamingType::HOME_NETWORK);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.getCdmaRoamingPreference() for the response returned.
 */
TEST_F(RadioHidlTest, getCdmaRoamingPreference) {
    int serial = GetRandomSerialNumber();

    radio->getCdmaRoamingPreference(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.getTTYMode() for the response returned.
 */
TEST_F(RadioHidlTest, getTTYMode) {
    int serial = GetRandomSerialNumber();

    radio->getTTYMode(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setTTYMode() for the response returned.
 */
TEST_F(RadioHidlTest, setTTYMode) {
    int serial = GetRandomSerialNumber();

    radio->setTTYMode(serial, TtyMode::OFF);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setPreferredVoicePrivacy() for the response returned.
 */
TEST_F(RadioHidlTest, setPreferredVoicePrivacy) {
    int serial = GetRandomSerialNumber();

    radio->setPreferredVoicePrivacy(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.getPreferredVoicePrivacy() for the response returned.
 */
TEST_F(RadioHidlTest, getPreferredVoicePrivacy) {
    int serial = GetRandomSerialNumber();

    radio->getPreferredVoicePrivacy(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.getCDMASubscription() for the response returned.
 */
TEST_F(RadioHidlTest, getCDMASubscription) {
    int serial = GetRandomSerialNumber();

    radio->getCDMASubscription(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.getDeviceIdentity() for the response returned.
 */
TEST_F(RadioHidlTest, getDeviceIdentity) {
    int serial = GetRandomSerialNumber();

    radio->getDeviceIdentity(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::EMPTY_RECORD);
    }
}

/*
 * Test IRadio.exitEmergencyCallbackMode() for the response returned.
 */
TEST_F(RadioHidlTest, exitEmergencyCallbackMode) {
    int serial = GetRandomSerialNumber();

    radio->exitEmergencyCallbackMode(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.getCdmaSubscriptionSource() for the response returned.
 */
TEST_F(RadioHidlTest, getCdmaSubscriptionSource) {
    int serial = GetRandomSerialNumber();

    radio->getCdmaSubscriptionSource(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.setCdmaSubscriptionSource() for the response returned.
 */
TEST_F(RadioHidlTest, setCdmaSubscriptionSource) {
    int serial = GetRandomSerialNumber();

    radio->setCdmaSubscriptionSource(serial, CdmaSubscriptionSource::RUIM_SIM);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        std::cout << static_cast<int>(radioRsp->rspInfo.error) << std::endl;
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::SIM_ABSENT ||
                    radioRsp->rspInfo.error == RadioError::SUBSCRIPTION_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getVoiceRadioTechnology() for the response returned.
 */
TEST_F(RadioHidlTest, getVoiceRadioTechnology) {
    int serial = GetRandomSerialNumber();

    radio->getVoiceRadioTechnology(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getCellInfoList() for the response returned.
 */
TEST_F(RadioHidlTest, getCellInfoList) {
    int serial = GetRandomSerialNumber();

    radio->getCellInfoList(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::NO_NETWORK_FOUND);
    }
}

/*
 * Test IRadio.setCellInfoListRate() for the response returned.
 */
TEST_F(RadioHidlTest, setCellInfoListRate) {
    int serial = GetRandomSerialNumber();

    // TODO(sanketpadawe): RIL crashes with value of rate = 10
    radio->setCellInfoListRate(serial, 10);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.nvReadItem() for the response returned.
 */
TEST_F(RadioHidlTest, nvReadItem) {
    int serial = GetRandomSerialNumber();

    radio->nvReadItem(serial, NvItem::LTE_BAND_ENABLE_25);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.nvWriteItem() for the response returned.
 */
TEST_F(RadioHidlTest, nvWriteItem) {
    int serial = GetRandomSerialNumber();
    NvWriteItem item;
    memset(&item, 0, sizeof(item));
    item.value = hidl_string();

    radio->nvWriteItem(serial, item);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.nvWriteCdmaPrl() for the response returned.
 */
TEST_F(RadioHidlTest, nvWriteCdmaPrl) {
    int serial = GetRandomSerialNumber();
    std::vector<uint8_t> prl = {1, 2, 3, 4, 5};

    radio->nvWriteCdmaPrl(serial, hidl_vec<uint8_t>(prl));
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.nvResetConfig() for the response returned.
 */
TEST_F(RadioHidlTest, nvResetConfig) {
    int serial = GetRandomSerialNumber();

    radio->nvResetConfig(++serial, ResetNvType::ERASE);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.setUiccSubscription() for the response returned.
 */
TEST_F(RadioHidlTest, setUiccSubscription) {
    int serial = GetRandomSerialNumber();
    SelectUiccSub item;
    memset(&item, 0, sizeof(item));

    radio->setUiccSubscription(serial, item);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::SUBSCRIPTION_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.getHardwareConfig() for the response returned.
 */
TEST_F(RadioHidlTest, getHardwareConfig) {
    int serial = GetRandomSerialNumber();

    radio->getHardwareConfig(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.requestShutdown() for the response returned.
 */
TEST_F(RadioHidlTest, requestShutdown) {
    int serial = GetRandomSerialNumber();

    radio->requestShutdown(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getRadioCapability() for the response returned.
 */
TEST_F(RadioHidlTest, getRadioCapability) {
    int serial = GetRandomSerialNumber();

    radio->getRadioCapability(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.setRadioCapability() for the response returned.
 */
TEST_F(RadioHidlTest, setRadioCapability) {
    int serial = GetRandomSerialNumber();
    RadioCapability rc;
    memset(&rc, 0, sizeof(rc));
    rc.logicalModemUuid = hidl_string();

    radio->setRadioCapability(serial, rc);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE);
    }
}

/*
 * Test IRadio.startLceService() for the response returned.
 */
TEST_F(RadioHidlTest, startLceService) {
    int serial = GetRandomSerialNumber();

    radio->startLceService(serial, 5, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        std::cout << static_cast<int>(radioRsp->rspInfo.error) << std::endl;
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::LCE_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::INTERNAL_ERR ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.stopLceService() for the response returned.
 */
TEST_F(RadioHidlTest, stopLceService) {
    int serial = GetRandomSerialNumber();

    radio->stopLceService(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::LCE_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.pullLceData() for the response returned.
 */
TEST_F(RadioHidlTest, pullLceData) {
    int serial = GetRandomSerialNumber();

    radio->pullLceData(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE || CheckOEMError() ||
                    radioRsp->rspInfo.error == RadioError::INTERNAL_ERR ||
                    radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getModemActivityInfo() for the response returned.
 */
TEST_F(RadioHidlTest, getModemActivityInfo) {
    int serial = GetRandomSerialNumber();

    radio->getModemActivityInfo(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.setAllowedCarriers() for the response returned.
 */
TEST_F(RadioHidlTest, setAllowedCarriers) {
    int serial = GetRandomSerialNumber();
    CarrierRestrictions carriers;
    memset(&carriers, 0, sizeof(carriers));
    carriers.allowedCarriers.resize(1);
    carriers.excludedCarriers.resize(0);
    carriers.allowedCarriers[0].mcc = hidl_string();
    carriers.allowedCarriers[0].mnc = hidl_string();
    carriers.allowedCarriers[0].matchType = CarrierMatchType::ALL;
    carriers.allowedCarriers[0].matchData = hidl_string();

    radio->setAllowedCarriers(serial, false, carriers);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }

    /* Reset back to no carrier restriction */
    memset(&carriers, 0, sizeof(carriers));
    carriers.allowedCarriers.resize(0);
    carriers.excludedCarriers.resize(0);

    radio->setAllowedCarriers(++serial, true, carriers);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.getAllowedCarriers() for the response returned.
 */
TEST_F(RadioHidlTest, getAllowedCarriers) {
    int serial = GetRandomSerialNumber();

    radio->getAllowedCarriers(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.sendDeviceState() for the response returned.
 */
TEST_F(RadioHidlTest, sendDeviceState) {
    int serial = GetRandomSerialNumber();

    radio->sendDeviceState(serial, DeviceStateType::POWER_SAVE_MODE, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    std::cout << static_cast<int>(radioRsp->rspInfo.error) << std::endl;

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.setIndicationFilter() for the response returned.
 */
TEST_F(RadioHidlTest, setIndicationFilter) {
    int serial = GetRandomSerialNumber();

    radio->setIndicationFilter(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    std::cout << static_cast<int>(radioRsp->rspInfo.error) << std::endl;

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}

/*
 * Test IRadio.setSimCardPower() for the response returned.
 */
TEST_F(RadioHidlTest, setSimCardPower) {
    int serial = GetRandomSerialNumber();

    radio->setSimCardPower(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}
