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
 * Test IRadio.getDataRegistrationState() for the response returned.
 */
TEST_F(RadioHidlTest, getDataRegistrationState) {
    int serial = GetRandomSerialNumber();

    radio->getDataRegistrationState(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp->rspInfo.error);
    }
}

/*
 * Test IRadio.setupDataCall() for the response returned.
 */
TEST_F(RadioHidlTest, setupDataCall) {
    int serial = GetRandomSerialNumber();

    RadioTechnology radioTechnology = RadioTechnology::LTE;

    DataProfileInfo dataProfileInfo;
    memset(&dataProfileInfo, 0, sizeof(dataProfileInfo));
    dataProfileInfo.profileId = DataProfileId::IMS;
    dataProfileInfo.apn = hidl_string("VZWIMS");
    dataProfileInfo.protocol = hidl_string("IPV4V6");
    dataProfileInfo.roamingProtocol = hidl_string("IPV6");
    dataProfileInfo.authType = ApnAuthType::NO_PAP_NO_CHAP;
    dataProfileInfo.user = "";
    dataProfileInfo.password = "";
    dataProfileInfo.type = DataProfileInfoType::THREE_GPP2;
    dataProfileInfo.maxConnsTime = 300;
    dataProfileInfo.maxConns = 20;
    dataProfileInfo.waitTime = 0;
    dataProfileInfo.enabled = true;
    dataProfileInfo.supportedApnTypesBitmap = 320;
    dataProfileInfo.bearerBitmap = 161543;
    dataProfileInfo.mtu = 0;
    dataProfileInfo.mvnoType = MvnoType::NONE;
    dataProfileInfo.mvnoMatchData = hidl_string();

    bool modemCognitive = false;
    bool roamingAllowed = false;
    bool isRoaming = false;

    radio->setupDataCall(serial, radioTechnology, dataProfileInfo, modemCognitive, roamingAllowed,
                         isRoaming);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::OP_NOT_ALLOWED_BEFORE_REG_TO_NW ||
                    radioRsp->rspInfo.error == RadioError::OP_NOT_ALLOWED_DURING_VOICE_CALL ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT || CheckOEMError());
    }
}

/*
 * Test IRadio.deactivateDataCall() for the response returned.
 */
TEST_F(RadioHidlTest, deactivateDataCall) {
    int serial = GetRandomSerialNumber();
    int cid = 1;
    bool reasonRadioShutDown = false;

    radio->deactivateDataCall(serial, cid, reasonRadioShutDown);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT || CheckOEMError() ||
                    radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE);
    }
}

/*
 * Test IRadio.getDataCallList() for the response returned.
 */
TEST_F(RadioHidlTest, getDataCallList) {
    int serial = GetRandomSerialNumber();

    radio->getDataCallList(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
    }
}

/*
 * Test IRadio.setInitialAttachApn() for the response returned.
 */
TEST_F(RadioHidlTest, setInitialAttachApn) {
    int serial = GetRandomSerialNumber();

    DataProfileInfo dataProfileInfo;
    memset(&dataProfileInfo, 0, sizeof(dataProfileInfo));
    dataProfileInfo.profileId = DataProfileId::IMS;
    dataProfileInfo.apn = hidl_string("VZWIMS");
    dataProfileInfo.protocol = hidl_string("IPV4V6");
    dataProfileInfo.roamingProtocol = hidl_string("IPV6");
    dataProfileInfo.authType = ApnAuthType::NO_PAP_NO_CHAP;
    dataProfileInfo.user = "";
    dataProfileInfo.password = "";
    dataProfileInfo.type = DataProfileInfoType::THREE_GPP2;
    dataProfileInfo.maxConnsTime = 300;
    dataProfileInfo.maxConns = 20;
    dataProfileInfo.waitTime = 0;
    dataProfileInfo.enabled = true;
    dataProfileInfo.supportedApnTypesBitmap = 320;
    dataProfileInfo.bearerBitmap = 161543;
    dataProfileInfo.mtu = 0;
    dataProfileInfo.mvnoType = MvnoType::NONE;
    dataProfileInfo.mvnoMatchData = hidl_string();

    bool modemCognitive = true;
    bool isRoaming = false;

    radio->setInitialAttachApn(serial, dataProfileInfo, modemCognitive, isRoaming);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::SUBSCRIPTION_NOT_AVAILABLE ||
                    CheckOEMError());
    }
}

/*
 * Test IRadio.setDataAllowed() for the response returned.
 */
TEST_F(RadioHidlTest, setDataAllowed) {
    int serial = GetRandomSerialNumber();
    bool allow = true;

    radio->setDataAllowed(serial, allow);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp->rspInfo.error);
    }
}

/*
 * Test IRadio.setDataProfile() for the response returned.
 */
TEST_F(RadioHidlTest, setDataProfile) {
    int serial = GetRandomSerialNumber();

    // Create a dataProfileInfo
    DataProfileInfo dataProfileInfo;
    memset(&dataProfileInfo, 0, sizeof(dataProfileInfo));
    dataProfileInfo.profileId = DataProfileId::IMS;
    dataProfileInfo.apn = hidl_string("VZWIMS");
    dataProfileInfo.protocol = hidl_string("IPV4V6");
    dataProfileInfo.roamingProtocol = hidl_string("IPV6");
    dataProfileInfo.authType = ApnAuthType::NO_PAP_NO_CHAP;
    dataProfileInfo.user = "";
    dataProfileInfo.password = "";
    dataProfileInfo.type = DataProfileInfoType::THREE_GPP2;
    dataProfileInfo.maxConnsTime = 300;
    dataProfileInfo.maxConns = 20;
    dataProfileInfo.waitTime = 0;
    dataProfileInfo.enabled = true;
    dataProfileInfo.supportedApnTypesBitmap = 320;
    dataProfileInfo.bearerBitmap = 161543;
    dataProfileInfo.mtu = 0;
    dataProfileInfo.mvnoType = MvnoType::NONE;
    dataProfileInfo.mvnoMatchData = hidl_string();

    // Create a dataProfileInfoList
    android::hardware::hidl_vec<DataProfileInfo> dataProfileInfoList = {dataProfileInfo};

    bool isRoadming = false;

    radio->setDataProfile(serial, dataProfileInfoList, isRoadming);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
                    radioRsp->rspInfo.error == RadioError::SIM_ABSENT ||
                    radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED);
    }
}
