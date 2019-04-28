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

void RadioHidlTest::SetUp() {
    radio =
        ::testing::VtsHalHidlTargetTestBase::getService<IRadio>(hidl_string(RADIO_SERVICE_NAME));
    ASSERT_NE(nullptr, radio.get());

    radioRsp = new (std::nothrow) RadioResponse(*this);
    ASSERT_NE(nullptr, radioRsp.get());

    count = 0;

    radioInd = new (std::nothrow) RadioIndication(*this);
    ASSERT_NE(nullptr, radioInd.get());

    radio->setResponseFunctions(radioRsp, radioInd);

    int serial = GetRandomSerialNumber();
    radio->getIccCardStatus(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp->rspInfo.type);
    EXPECT_EQ(serial, radioRsp->rspInfo.serial);
    EXPECT_EQ(RadioError::NONE, radioRsp->rspInfo.error);

    /* Vts Testing with Sim Absent only. This needs to be removed later in P when sim present
     * scenarios will be tested. */
    EXPECT_EQ(CardState::ABSENT, cardStatus.cardState);
}

void RadioHidlTest::TearDown() {}

void RadioHidlTest::notify() {
    std::unique_lock<std::mutex> lock(mtx);
    count++;
    cv.notify_one();
}

std::cv_status RadioHidlTest::wait() {
    std::unique_lock<std::mutex> lock(mtx);

    std::cv_status status = std::cv_status::no_timeout;
    auto now = std::chrono::system_clock::now();
    while (count == 0) {
        status = cv.wait_until(lock, now + std::chrono::seconds(TIMEOUT_PERIOD));
        if (status == std::cv_status::timeout) {
            return status;
        }
    }
    count--;
    return status;
}

bool RadioHidlTest::CheckGeneralError() {
    return (radioRsp->rspInfo.error == RadioError::RADIO_NOT_AVAILABLE ||
            radioRsp->rspInfo.error == RadioError::NO_MEMORY ||
            radioRsp->rspInfo.error == RadioError::INTERNAL_ERR ||
            radioRsp->rspInfo.error == RadioError::SYSTEM_ERR ||
            radioRsp->rspInfo.error == RadioError::REQUEST_NOT_SUPPORTED ||
            radioRsp->rspInfo.error == RadioError::CANCELLED);
}

bool RadioHidlTest::CheckOEMError() {
    return (radioRsp->rspInfo.error >= RadioError::OEM_ERROR_1 &&
            radioRsp->rspInfo.error <= RadioError::OEM_ERROR_25);
}
