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

#include <sap_hidl_hal_utils.h>

/*
 * Test ISap.connectReq() for the response returned.
 */
TEST_F(SapHidlTest, connectReq) {
    int32_t token = GetRandomSerialNumber();
    int32_t maxMsgSize = 100;

    sap->connectReq(token, maxMsgSize);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);
}

/*
 * Test IRadio.disconnectReq() for the response returned
 */
TEST_F(SapHidlTest, disconnectReq) {
    int32_t token = GetRandomSerialNumber();

    sap->disconnectReq(token);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);
}

/*
 * Test IRadio.apduReq() for the response returned.
 */
TEST_F(SapHidlTest, apduReq) {
    int32_t token = GetRandomSerialNumber();
    SapApduType sapApduType = SapApduType::APDU;
    android::hardware::hidl_vec<uint8_t> command = {};

    sap->apduReq(token, sapApduType, command);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    ASSERT_TRUE(SapResultCode::GENERIC_FAILURE == sapCb->sapResultCode ||
                SapResultCode::CARD_NOT_ACCESSSIBLE == sapCb->sapResultCode ||
                SapResultCode::CARD_ALREADY_POWERED_OFF == sapCb->sapResultCode ||
                SapResultCode::CARD_REMOVED == sapCb->sapResultCode);
}

/*
 * Test IRadio.transferAtrReq() for the response returned.
 */
TEST_F(SapHidlTest, transferAtrReq) {
    int32_t token = GetRandomSerialNumber();

    sap->transferAtrReq(token);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    ASSERT_TRUE(SapResultCode::GENERIC_FAILURE == sapCb->sapResultCode ||
                SapResultCode::DATA_NOT_AVAILABLE == sapCb->sapResultCode ||
                SapResultCode::CARD_ALREADY_POWERED_OFF == sapCb->sapResultCode ||
                SapResultCode::CARD_REMOVED == sapCb->sapResultCode);
}

/*
 * Test IRadio.powerReq() for the response returned.
 */
TEST_F(SapHidlTest, powerReq) {
    int32_t token = GetRandomSerialNumber();
    bool state = true;

    sap->powerReq(token, state);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    ASSERT_TRUE(SapResultCode::GENERIC_FAILURE == sapCb->sapResultCode ||
                SapResultCode::CARD_NOT_ACCESSSIBLE == sapCb->sapResultCode ||
                SapResultCode::CARD_ALREADY_POWERED_OFF == sapCb->sapResultCode ||
                SapResultCode::CARD_REMOVED == sapCb->sapResultCode ||
                SapResultCode::CARD_ALREADY_POWERED_ON == sapCb->sapResultCode);
}

/*
 * Test IRadio.resetSimReq() for the response returned.
 */
TEST_F(SapHidlTest, resetSimReq) {
    int32_t token = GetRandomSerialNumber();

    sap->resetSimReq(token);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    ASSERT_TRUE(SapResultCode::GENERIC_FAILURE == sapCb->sapResultCode ||
                SapResultCode::CARD_NOT_ACCESSSIBLE == sapCb->sapResultCode ||
                SapResultCode::CARD_ALREADY_POWERED_OFF == sapCb->sapResultCode ||
                SapResultCode::CARD_REMOVED == sapCb->sapResultCode);
}

/*
 * Test IRadio.transferCardReaderStatusReq() for the response returned.
 */
TEST_F(SapHidlTest, transferCardReaderStatusReq) {
    int32_t token = GetRandomSerialNumber();

    sap->transferCardReaderStatusReq(token);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    ASSERT_TRUE(SapResultCode::GENERIC_FAILURE == sapCb->sapResultCode ||
                SapResultCode::DATA_NOT_AVAILABLE == sapCb->sapResultCode);
}

/*
 * Test IRadio.setTransferProtocolReq() for the response returned.
 */
TEST_F(SapHidlTest, setTransferProtocolReq) {
    int32_t token = GetRandomSerialNumber();
    SapTransferProtocol sapTransferProtocol = SapTransferProtocol::T0;

    sap->setTransferProtocolReq(token, sapTransferProtocol);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(sapCb->sapResponseToken, token);

    EXPECT_EQ(SapResultCode::NOT_SUPPORTED, sapCb->sapResultCode);
}
