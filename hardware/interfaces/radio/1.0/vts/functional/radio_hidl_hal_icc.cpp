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
 * Test IRadio.getIccCardStatus() for the response returned.
 */
TEST_F(RadioHidlTest, getIccCardStatus) {
    EXPECT_LE(cardStatus.applications.size(), (unsigned int)RadioConst::CARD_MAX_APPS);
    EXPECT_LT(cardStatus.gsmUmtsSubscriptionAppIndex, (int)RadioConst::CARD_MAX_APPS);
    EXPECT_LT(cardStatus.cdmaSubscriptionAppIndex, (int)RadioConst::CARD_MAX_APPS);
    EXPECT_LT(cardStatus.imsSubscriptionAppIndex, (int)RadioConst::CARD_MAX_APPS);
    }

    /*
     * Test IRadio.supplyIccPinForApp() for the response returned
     */
    TEST_F(RadioHidlTest, supplyIccPinForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->supplyIccPinForApp(serial, hidl_string("test1"),
                                          cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.supplyIccPukForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, supplyIccPukForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->supplyIccPukForApp(serial, hidl_string("test1"), hidl_string("test2"),
                                          cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.supplyIccPin2ForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, supplyIccPin2ForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->supplyIccPin2ForApp(serial, hidl_string("test1"),
                                           cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.supplyIccPuk2ForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, supplyIccPuk2ForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->supplyIccPuk2ForApp(serial, hidl_string("test1"), hidl_string("test2"),
                                           cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.changeIccPinForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, changeIccPinForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->changeIccPinForApp(serial, hidl_string("test1"), hidl_string("test2"),
                                          cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.changeIccPin2ForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, changeIccPin2ForApp) {
        int serial = GetRandomSerialNumber();

        // Pass wrong password and check PASSWORD_INCORRECT returned for 3GPP and
        // 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->changeIccPin2ForApp(serial, hidl_string("test1"), hidl_string("test2"),
                                           cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(RadioError::PASSWORD_INCORRECT, radioRsp->rspInfo.error);
            }
        }
    }

    /*
     * Test IRadio.getImsiForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, getImsiForApp) {
        int serial = GetRandomSerialNumber();

        // Check success returned while getting imsi for 3GPP and 3GPP2 apps only
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            if (cardStatus.applications[i].appType == AppType::SIM ||
                cardStatus.applications[i].appType == AppType::USIM ||
                cardStatus.applications[i].appType == AppType::RUIM ||
                cardStatus.applications[i].appType == AppType::CSIM) {
                radio->getImsiForApp(serial, cardStatus.applications[i].aidPtr);
                EXPECT_EQ(std::cv_status::no_timeout, wait());
                EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
                EXPECT_EQ(serial, radioRsp->rspInfo.serial);
                EXPECT_EQ(RadioError::NONE, radioRsp->rspInfo.error);

                // IMSI (MCC+MNC+MSIN) is at least 6 digits, but not more than 15
                if (radioRsp->rspInfo.error == RadioError::NONE) {
                    EXPECT_NE(radioRsp->imsi, hidl_string());
                    EXPECT_GE((int)(radioRsp->imsi).size(), 6);
                    EXPECT_LE((int)(radioRsp->imsi).size(), 15);
                }
            }
        }
    }

    /*
     * Test IRadio.iccIOForApp() for the response returned.
     */
    TEST_F(RadioHidlTest, iccIOForApp) {
        int serial = GetRandomSerialNumber();

        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            IccIo iccIo;
            iccIo.command = 0xc0;
            iccIo.fileId = 0x6f11;
            iccIo.path = hidl_string("3F007FFF");
            iccIo.p1 = 0;
            iccIo.p2 = 0;
            iccIo.p3 = 0;
            iccIo.data = hidl_string();
            iccIo.pin2 = hidl_string();
            iccIo.aid = cardStatus.applications[i].aidPtr;

            radio->iccIOForApp(serial, iccIo);
            EXPECT_EQ(std::cv_status::no_timeout, wait());
            EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
            EXPECT_EQ(serial, radioRsp->rspInfo.serial);
        }
    }

    /*
     * Test IRadio.iccTransmitApduBasicChannel() for the response returned.
     */
    TEST_F(RadioHidlTest, iccTransmitApduBasicChannel) {
        int serial = GetRandomSerialNumber();
        SimApdu msg;
        memset(&msg, 0, sizeof(msg));
        msg.data = hidl_string();

        radio->iccTransmitApduBasicChannel(serial, msg);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
        EXPECT_EQ(serial, radioRsp->rspInfo.serial);

        // TODO(sanketpadawe): Add test for error code
    }

    /*
     * Test IRadio.iccOpenLogicalChannel() for the response returned.
     */
    TEST_F(RadioHidlTest, iccOpenLogicalChannel) {
        int serial = GetRandomSerialNumber();
        int p2 = 0x04;
        // Specified in ISO 7816-4 clause 7.1.1 0x04 means that FCP template is requested.
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            radio->iccOpenLogicalChannel(serial, cardStatus.applications[i].aidPtr, p2);
            EXPECT_EQ(std::cv_status::no_timeout, wait());
            EXPECT_EQ(serial, radioRsp->rspInfo.serial);
            EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
        }
    }

    /*
     * Test IRadio.iccCloseLogicalChannel() for the response returned.
     */
    TEST_F(RadioHidlTest, iccCloseLogicalChannel) {
        int serial = GetRandomSerialNumber();
        // Try closing invalid channel and check INVALID_ARGUMENTS returned as error
        radio->iccCloseLogicalChannel(serial, 0);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
        EXPECT_EQ(serial, radioRsp->rspInfo.serial);

        EXPECT_EQ(RadioError::INVALID_ARGUMENTS, radioRsp->rspInfo.error);
    }

    /*
     * Test IRadio.iccTransmitApduLogicalChannel() for the response returned.
     */
    TEST_F(RadioHidlTest, iccTransmitApduLogicalChannel) {
        int serial = GetRandomSerialNumber();
        SimApdu msg;
        memset(&msg, 0, sizeof(msg));
        msg.data = hidl_string();

        radio->iccTransmitApduLogicalChannel(serial, msg);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
        EXPECT_EQ(serial, radioRsp->rspInfo.serial);

        // TODO(sanketpadawe): Add test for error code
    }

    /*
     * Test IRadio.requestIccSimAuthentication() for the response returned.
     */
    TEST_F(RadioHidlTest, requestIccSimAuthentication) {
        int serial = GetRandomSerialNumber();

        // Pass wrong challenge string and check RadioError::INVALID_ARGUMENTS
        // returned as error.
        for (int i = 0; i < (int)cardStatus.applications.size(); i++) {
            radio->requestIccSimAuthentication(serial, 0, hidl_string("test"),
                                               cardStatus.applications[i].aidPtr);
            EXPECT_EQ(std::cv_status::no_timeout, wait());
            EXPECT_EQ(serial, radioRsp->rspInfo.serial);
            EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
            EXPECT_EQ(RadioError::INVALID_ARGUMENTS, radioRsp->rspInfo.error);
        }
    }

    /*
     * Test IRadio.supplyNetworkDepersonalization() for the response returned.
     */
    TEST_F(RadioHidlTest, supplyNetworkDepersonalization) {
        int serial = GetRandomSerialNumber();

        radio->supplyNetworkDepersonalization(serial, hidl_string("test"));
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
        EXPECT_EQ(serial, radioRsp->rspInfo.serial);

        if (cardStatus.cardState == CardState::ABSENT) {
            ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE ||
                        radioRsp->rspInfo.error == RadioError::SYSTEM_ERR ||
                        radioRsp->rspInfo.error == RadioError::NO_MEMORY ||
                        radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                        radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                        radioRsp->rspInfo.error == RadioError::INVALID_SIM_STATE ||
                        radioRsp->rspInfo.error == RadioError::PASSWORD_INCORRECT ||
                        radioRsp->rspInfo.error == RadioError::INTERNAL_ERR ||
                        radioRsp->rspInfo.error == RadioError::SIM_ABSENT);
        }
    }
