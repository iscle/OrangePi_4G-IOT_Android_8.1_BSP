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
 * Test IRadio.getCurrentCalls() for the response returned.
 */
TEST_F(RadioHidlTest, getCurrentCalls) {
    int serial = GetRandomSerialNumber();

    radio->getCurrentCalls(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.dial() for the response returned.
 */
TEST_F(RadioHidlTest, dial) {
    int serial = GetRandomSerialNumber();

    Dial dialInfo;
    memset(&dialInfo, 0, sizeof(dialInfo));
    dialInfo.address = hidl_string("123456789");

    radio->dial(serial, dialInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::FDN_CHECK_FAILURE ||
                    radioRsp->rspInfo.error == RadioError::NO_SUBSCRIPTION ||
                    radioRsp->rspInfo.error == RadioError::NO_NETWORK_FOUND ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::DEVICE_IN_USE ||
                    radioRsp->rspInfo.error == RadioError::OPERATION_NOT_ALLOWED ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE ||
                    radioRsp->rspInfo.error == RadioError::CANCELLED);
    }
}

/*
 * Test IRadio.hangup() for the response returned.
 */
TEST_F(RadioHidlTest, hangup) {
    int serial = GetRandomSerialNumber();

    radio->hangup(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.hangupWaitingOrBackground() for the response returned.
 */
TEST_F(RadioHidlTest, hangupWaitingOrBackground) {
    int serial = GetRandomSerialNumber();

    radio->hangupWaitingOrBackground(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.hangupForegroundResumeBackground() for the response returned.
 */
TEST_F(RadioHidlTest, hangupForegroundResumeBackground) {
    int serial = GetRandomSerialNumber();

    radio->hangupForegroundResumeBackground(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.switchWaitingOrHoldingAndActive() for the response returned.
 */
TEST_F(RadioHidlTest, switchWaitingOrHoldingAndActive) {
    int serial = GetRandomSerialNumber();

    radio->switchWaitingOrHoldingAndActive(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.conference() for the response returned.
 */
TEST_F(RadioHidlTest, conference) {
    int serial = GetRandomSerialNumber();

    radio->conference(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.rejectCall() for the response returned.
 */
TEST_F(RadioHidlTest, rejectCall) {
    int serial = GetRandomSerialNumber();

    radio->rejectCall(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.getLastCallFailCause() for the response returned.
 */
TEST_F(RadioHidlTest, getLastCallFailCause) {
    int serial = GetRandomSerialNumber();

    radio->getLastCallFailCause(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.sendUssd() for the response returned.
 */
TEST_F(RadioHidlTest, sendUssd) {
    int serial = GetRandomSerialNumber();
    radio->sendUssd(serial, hidl_string("test"));
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.cancelPendingUssd() for the response returned.
 */
TEST_F(RadioHidlTest, cancelPendingUssd) {
    int serial = GetRandomSerialNumber();

    radio->cancelPendingUssd(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getCallForwardStatus() for the response returned.
 */
TEST_F(RadioHidlTest, getCallForwardStatus) {
    int serial = GetRandomSerialNumber();
    CallForwardInfo callInfo;
    memset(&callInfo, 0, sizeof(callInfo));
    callInfo.number = hidl_string();

    radio->getCallForwardStatus(serial, callInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setCallForward() for the response returned.
 */
TEST_F(RadioHidlTest, setCallForward) {
    int serial = GetRandomSerialNumber();
    CallForwardInfo callInfo;
    memset(&callInfo, 0, sizeof(callInfo));
    callInfo.number = hidl_string();

    radio->setCallForward(serial, callInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.getCallWaiting() for the response returned.
 */
TEST_F(RadioHidlTest, getCallWaiting) {
    int serial = GetRandomSerialNumber();

    radio->getCallWaiting(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.setCallWaiting() for the response returned.
 */
TEST_F(RadioHidlTest, setCallWaiting) {
    int serial = GetRandomSerialNumber();

    radio->setCallWaiting(serial, true, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.acceptCall() for the response returned.
 */
TEST_F(RadioHidlTest, acceptCall) {
    int serial = GetRandomSerialNumber();

    radio->acceptCall(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.separateConnection() for the response returned.
 */
TEST_F(RadioHidlTest, separateConnection) {
    int serial = GetRandomSerialNumber();

    radio->separateConnection(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.explicitCallTransfer() for the response returned.
 */
TEST_F(RadioHidlTest, explicitCallTransfer) {
    int serial = GetRandomSerialNumber();

    radio->explicitCallTransfer(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR);
    }
}

/*
 * Test IRadio.sendCDMAFeatureCode() for the response returned.
 */
TEST_F(RadioHidlTest, sendCDMAFeatureCode) {
    int serial = GetRandomSerialNumber();

    radio->sendCDMAFeatureCode(serial, hidl_string());
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        std::cout << static_cast<int>(radioRsp->rspInfo.error) << std::endl;
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::OPERATION_NOT_ALLOWED);
    }
}

/*
 * Test IRadio.sendDtmf() for the response returned.
 */
TEST_F(RadioHidlTest, sendDtmf) {
    int serial = GetRandomSerialNumber();

    radio->sendDtmf(serial, "1");
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE);
    }
}

/*
 * Test IRadio.startDtmf() for the response returned.
 */
TEST_F(RadioHidlTest, startDtmf) {
    int serial = GetRandomSerialNumber();

    radio->startDtmf(serial, "1");
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE);
    }
}

/*
 * Test IRadio.stopDtmf() for the response returned.
 */
TEST_F(RadioHidlTest, stopDtmf) {
    int serial = GetRandomSerialNumber();

    radio->stopDtmf(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() || radioRsp->rspInfo.error == RadioError::NONE ||
                    radioRsp->rspInfo.error == RadioError::INVALID_CALL_ID ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::INVALID_MODEM_STATE);
    }
}

/*
 * Test IRadio.setMute() for the response returned.
 */
TEST_F(RadioHidlTest, setMute) {
    int serial = GetRandomSerialNumber();

    radio->setMute(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.getMute() for the response returned.
 */
TEST_F(RadioHidlTest, getMute) {
    int serial = GetRandomSerialNumber();

    radio->getMute(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(radioRsp->rspInfo.error == RadioError::NONE);
    }
}

/*
 * Test IRadio.sendBurstDtmf() for the response returned.
 */
TEST_F(RadioHidlTest, sendBurstDtmf) {
    int serial = GetRandomSerialNumber();

    radio->sendBurstDtmf(serial, "1", 0, 0);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);

    if (cardStatus.cardState == CardState::ABSENT) {
        ASSERT_TRUE(CheckGeneralError() ||
                    radioRsp->rspInfo.error == RadioError::INVALID_ARGUMENTS ||
                    radioRsp->rspInfo.error == RadioError::INVALID_STATE ||
                    radioRsp->rspInfo.error == RadioError::MODEM_ERR ||
                    radioRsp->rspInfo.error == RadioError::OPERATION_NOT_ALLOWED);
    }
}