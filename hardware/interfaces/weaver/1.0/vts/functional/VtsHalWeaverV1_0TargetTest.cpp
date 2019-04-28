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

#include <android/hardware/weaver/1.0/IWeaver.h>

#include <limits>

#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::weaver::V1_0::IWeaver;
using ::android::hardware::weaver::V1_0::WeaverConfig;
using ::android::hardware::weaver::V1_0::WeaverReadStatus;
using ::android::hardware::weaver::V1_0::WeaverReadResponse;
using ::android::hardware::weaver::V1_0::WeaverStatus;
using ::android::hardware::Return;
using ::android::sp;

const std::vector<uint8_t> KEY{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
const std::vector<uint8_t> WRONG_KEY{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
const std::vector<uint8_t> VALUE{16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
const std::vector<uint8_t> OTHER_VALUE{0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 255, 255};

struct WeaverHidlTest : public ::testing::VtsHalHidlTargetTestBase {
    virtual void SetUp() override {
        weaver = ::testing::VtsHalHidlTargetTestBase::getService<IWeaver>();
        ASSERT_NE(weaver, nullptr);
    }

    virtual void TearDown() override {}

    sp<IWeaver> weaver;
};

/*
 * Checks config values are suitably large
 */
TEST_F(WeaverHidlTest, GetConfig) {
    WeaverStatus status;
    WeaverConfig config;

    bool callbackCalled = false;
    auto ret = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        callbackCalled = true;
        status = s;
        config = c;
    });
    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    ASSERT_EQ(status, WeaverStatus::OK);

    EXPECT_GE(config.slots, 16u);
    EXPECT_GE(config.keySize, 16u);
    EXPECT_GE(config.valueSize, 16u);
}

/*
 * Gets the config twice and checks they are the same
 */
TEST_F(WeaverHidlTest, GettingConfigMultipleTimesGivesSameResult) {
    WeaverConfig config1;
    WeaverConfig config2;

    WeaverStatus status;
    bool callbackCalled = false;
    auto ret = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        callbackCalled = true;
        status = s;
        config1 = c;
    });
    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    ASSERT_EQ(status, WeaverStatus::OK);

    callbackCalled = false;
    ret = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        callbackCalled = true;
        status = s;
        config2 = c;
    });
    ASSERT_TRUE(ret.isOk());
    ASSERT_TRUE(callbackCalled);
    ASSERT_EQ(status, WeaverStatus::OK);

    EXPECT_EQ(config1, config2);
}

/*
 * Gets the number of slots from the config and writes a key and value to the last one
 */
TEST_F(WeaverHidlTest, WriteToLastSlot) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    const uint32_t lastSlot = config.slots - 1;
    const auto writeRet = weaver->write(lastSlot, KEY, VALUE);
    ASSERT_TRUE(writeRet.isOk());
    ASSERT_EQ(writeRet, WeaverStatus::OK);
}

/*
 * Writes a key and value to a slot
 * Reads the slot with the same key and receives the value that was previously written
 */
TEST_F(WeaverHidlTest, WriteFollowedByReadGivesTheSameValue) {
    constexpr uint32_t slotId = 0;
    const auto ret = weaver->write(slotId, KEY, VALUE);
    ASSERT_TRUE(ret.isOk());
    ASSERT_EQ(ret, WeaverStatus::OK);

    bool callbackCalled = false;
    WeaverReadStatus status;
    std::vector<uint8_t> readValue;
    uint32_t timeout;
    const auto readRet = weaver->read(slotId, KEY, [&](WeaverReadStatus s, WeaverReadResponse r) {
        callbackCalled = true;
        status = s;
        readValue = r.value;
        timeout = r.timeout;
    });
    ASSERT_TRUE(readRet.isOk());
    ASSERT_TRUE(callbackCalled);
    ASSERT_EQ(status, WeaverReadStatus::OK);
    EXPECT_EQ(readValue, VALUE);
    EXPECT_EQ(timeout, 0u);
}

/*
 * Writes a key and value to a slot
 * Overwrites the slot with a new key and value
 * Reads the slot with the new key and receives the new value
 */
TEST_F(WeaverHidlTest, OverwritingSlotUpdatesTheValue) {
    constexpr uint32_t slotId = 0;
    const auto initialWriteRet = weaver->write(slotId, WRONG_KEY, VALUE);
    ASSERT_TRUE(initialWriteRet.isOk());
    ASSERT_EQ(initialWriteRet, WeaverStatus::OK);

    const auto overwriteRet = weaver->write(slotId, KEY, OTHER_VALUE);
    ASSERT_TRUE(overwriteRet.isOk());
    ASSERT_EQ(overwriteRet, WeaverStatus::OK);

    bool callbackCalled = false;
    WeaverReadStatus status;
    std::vector<uint8_t> readValue;
    uint32_t timeout;
    const auto readRet = weaver->read(slotId, KEY, [&](WeaverReadStatus s, WeaverReadResponse r) {
        callbackCalled = true;
        status = s;
        readValue = r.value;
        timeout = r.timeout;
    });
    ASSERT_TRUE(readRet.isOk());
    ASSERT_TRUE(callbackCalled);
    ASSERT_EQ(status, WeaverReadStatus::OK);
    EXPECT_EQ(readValue, OTHER_VALUE);
    EXPECT_EQ(timeout, 0u);
}

/*
 * Writes a key and value to a slot
 * Reads the slot with a different key so does not receive the value
 */
TEST_F(WeaverHidlTest, WriteFollowedByReadWithWrongKeyDoesNotGiveTheValue) {
    constexpr uint32_t slotId = 0;
    const auto ret = weaver->write(slotId, KEY, VALUE);
    ASSERT_TRUE(ret.isOk());
    ASSERT_EQ(ret, WeaverStatus::OK);

    bool callbackCalled = false;
    WeaverReadStatus status;
    std::vector<uint8_t> readValue;
    const auto readRet =
        weaver->read(slotId, WRONG_KEY, [&](WeaverReadStatus s, WeaverReadResponse r) {
            callbackCalled = true;
            status = s;
            readValue = r.value;
        });
    ASSERT_TRUE(callbackCalled);
    ASSERT_TRUE(readRet.isOk());
    ASSERT_EQ(status, WeaverReadStatus::INCORRECT_KEY);
    EXPECT_TRUE(readValue.empty());
}

/*
 * Writing to an invalid slot fails
 */
TEST_F(WeaverHidlTest, WritingToInvalidSlotFails) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    if (config.slots == std::numeric_limits<uint32_t>::max()) {
        // If there are no invalid slots then pass
        return;
    }

    const auto writeRet = weaver->write(config.slots, KEY, VALUE);
    ASSERT_TRUE(writeRet.isOk());
    ASSERT_EQ(writeRet, WeaverStatus::FAILED);
}

/*
 * Reading from an invalid slot fails rather than incorrect key
 */
TEST_F(WeaverHidlTest, ReadingFromInvalidSlotFails) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    if (config.slots == std::numeric_limits<uint32_t>::max()) {
        // If there are no invalid slots then pass
        return;
    }

    bool callbackCalled = false;
    WeaverReadStatus readStatus;
    std::vector<uint8_t> readValue;
    uint32_t timeout;
    const auto readRet =
        weaver->read(config.slots, KEY, [&](WeaverReadStatus s, WeaverReadResponse r) {
            callbackCalled = true;
            readStatus = s;
            readValue = r.value;
            timeout = r.timeout;
        });
    ASSERT_TRUE(callbackCalled);
    ASSERT_TRUE(readRet.isOk());
    ASSERT_EQ(readStatus, WeaverReadStatus::FAILED);
    EXPECT_TRUE(readValue.empty());
    EXPECT_EQ(timeout, 0u);
}

/*
 * Writing a key that is too large fails
 */
TEST_F(WeaverHidlTest, WriteWithTooLargeKeyFails) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    std::vector<uint8_t> bigKey(config.keySize + 1);

    constexpr uint32_t slotId = 0;
    const auto writeRet = weaver->write(slotId, bigKey, VALUE);
    ASSERT_TRUE(writeRet.isOk());
    ASSERT_EQ(writeRet, WeaverStatus::FAILED);
}

/*
 * Writing a value that is too large fails
 */
TEST_F(WeaverHidlTest, WriteWithTooLargeValueFails) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    std::vector<uint8_t> bigValue(config.valueSize + 1);

    constexpr uint32_t slotId = 0;
    const auto writeRet = weaver->write(slotId, KEY, bigValue);
    ASSERT_TRUE(writeRet.isOk());
    ASSERT_EQ(writeRet, WeaverStatus::FAILED);
}

/*
 * Reading with a key that is loo large fails
 */
TEST_F(WeaverHidlTest, ReadWithTooLargeKeyFails) {
    WeaverStatus status;
    WeaverConfig config;
    const auto configRet = weaver->getConfig([&](WeaverStatus s, WeaverConfig c) {
        status = s;
        config = c;
    });
    ASSERT_TRUE(configRet.isOk());
    ASSERT_EQ(status, WeaverStatus::OK);

    std::vector<uint8_t> bigKey(config.keySize + 1);

    constexpr uint32_t slotId = 0;
    bool callbackCalled = false;
    WeaverReadStatus readStatus;
    std::vector<uint8_t> readValue;
    uint32_t timeout;
    const auto readRet =
        weaver->read(slotId, bigKey, [&](WeaverReadStatus s, WeaverReadResponse r) {
            callbackCalled = true;
            readStatus = s;
            readValue = r.value;
            timeout = r.timeout;
        });
    ASSERT_TRUE(callbackCalled);
    ASSERT_TRUE(readRet.isOk());
    ASSERT_EQ(readStatus, WeaverReadStatus::FAILED);
    EXPECT_TRUE(readValue.empty());
    EXPECT_EQ(timeout, 0u);
}
