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

using namespace ::android::hardware::radio::V1_0;

/*
 * Test IRadio.getClir() for the response returned.
 */
TEST_F(RadioHidlTest, getClir) {
    int serial = GetRandomSerialNumber();

    radio->getClir(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setClir() for the response returned.
 */
TEST_F(RadioHidlTest, setClir) {
    int serial = GetRandomSerialNumber();
    int32_t status = 1;

    radio->setClir(serial, status);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp->rspInfo.error);
    }
}

/*
 * Test IRadio.getFacilityLockForApp() for the response returned.
 */
TEST_F(RadioHidlTest, getFacilityLockForApp) {
    int serial = GetRandomSerialNumber();
    std::string facility = "";
    std::string password = "";
    int32_t serviceClass = 1;
    std::string appId = "";

    radio->getFacilityLockForApp(serial, facility, password, serviceClass, appId);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setFacilityLockForApp() for the response returned.
 */
TEST_F(RadioHidlTest, setFacilityLockForApp) {
    int serial = GetRandomSerialNumber();
    std::string facility = "";
    bool lockState = false;
    std::string password = "";
    int32_t serviceClass = 1;
    std::string appId = "";

    radio->setFacilityLockForApp(serial, facility, lockState, password, serviceClass, appId);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setBarringPassword() for the response returned.
 */
TEST_F(RadioHidlTest, setBarringPassword) {
    int serial = GetRandomSerialNumber();
    std::string facility = "";
    std::string oldPassword = "";
    std::string newPassword = "";

    radio->setBarringPassword(serial, facility, oldPassword, newPassword);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::FDN_CHECK_FAILURE);
    }
}

/*
 * Test IRadio.getClip() for the response returned.
 */
TEST_F(RadioHidlTest, getClip) {
    int serial = GetRandomSerialNumber();

    radio->getClip(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setSuppServiceNotifications() for the response returned.
 */
TEST_F(RadioHidlTest, setSuppServiceNotifications) {
    int serial = GetRandomSerialNumber();
    bool enable = false;

    radio->setSuppServiceNotifications(serial, enable);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.requestIsimAuthentication() for the response returned.
 */
TEST_F(RadioHidlTest, requestIsimAuthentication) {
    int serial = GetRandomSerialNumber();
    std::string challenge = "";

    radio->requestIsimAuthentication(serial, challenge);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.getImsRegistrationState() for the response returned.
 */
TEST_F(RadioHidlTest, getImsRegistrationState) {
    int serial = GetRandomSerialNumber();

    radio->getImsRegistrationState(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE);
    }
}
