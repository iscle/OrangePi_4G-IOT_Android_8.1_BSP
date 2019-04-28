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

#define LOG_TAG "dumpstate_hidl_hal_test"

#include <android/hardware/dumpstate/1.0/IDumpstateDevice.h>
#include <cutils/native_handle.h>
#include <log/log.h>

#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::dumpstate::V1_0::IDumpstateDevice;
using ::android::hardware::Return;
using ::android::sp;

class DumpstateHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        dumpstate = ::testing::VtsHalHidlTargetTestBase::getService<IDumpstateDevice>();
        ASSERT_NE(dumpstate, nullptr) << "Could not get HIDL instance";
    }

    sp<IDumpstateDevice> dumpstate;
};

// Negative test: make sure dumpstateBoard() doesn't crash when passed a null pointer.
TEST_F(DumpstateHidlTest, TestNullHandle) {
    Return<void> status = dumpstate->dumpstateBoard(nullptr);

    ASSERT_TRUE(status.isOk()) << "Status should be ok: " << status.description();
}

// Negative test: make sure dumpstateBoard() ignores a handle with no FD.
TEST_F(DumpstateHidlTest, TestHandleWithNoFd) {
    native_handle_t* handle = native_handle_create(0, 0);
    ASSERT_NE(handle, nullptr) << "Could not create native_handle";

    Return<void> status = dumpstate->dumpstateBoard(handle);

    ASSERT_TRUE(status.isOk()) << "Status should be ok: " << status.description();

    native_handle_close(handle);
    native_handle_delete(handle);
}

// Positive test: make sure dumpstateBoard() writes something to the FD.
TEST_F(DumpstateHidlTest, TestOk) {
    FILE* file = tmpfile();

    ASSERT_NE(nullptr, file) << "Could not create temp file: " << strerror(errno);

    native_handle_t* handle = native_handle_create(1, 0);
    ASSERT_NE(handle, nullptr) << "Could not create native_handle";
    handle->data[0] = fileno(file);

    Return<void> status = dumpstate->dumpstateBoard(handle);
    ASSERT_TRUE(status.isOk()) << "Status should be ok: " << status.description();

    // Check that at least one byte was written
    rewind(file);  // can not fail
    char buff;
    int read = fread(&buff, sizeof(buff), 1, file);
    ASSERT_EQ(1, read) << "dumped nothing";

    EXPECT_EQ(0, fclose(file)) << errno;

    native_handle_close(handle);
    native_handle_delete(handle);
}

// Positive test: make sure dumpstateBoard() doesn't crash with two FDs.
TEST_F(DumpstateHidlTest, TestHandleWithTwoFds) {
    FILE* file1 = tmpfile();
    FILE* file2 = tmpfile();

    ASSERT_NE(nullptr, file1) << "Could not create temp file #1: " << strerror(errno);
    ASSERT_NE(nullptr, file2) << "Could not create temp file #2: " << strerror(errno);

    native_handle_t* handle = native_handle_create(2, 0);
    ASSERT_NE(handle, nullptr) << "Could not create native_handle";
    handle->data[0] = fileno(file1);
    handle->data[1] = fileno(file2);

    Return<void> status = dumpstate->dumpstateBoard(handle);
    ASSERT_TRUE(status.isOk()) << "Status should be ok: " << status.description();

    EXPECT_EQ(0, fclose(file1)) << errno;
    EXPECT_EQ(0, fclose(file2)) << errno;

    native_handle_close(handle);
    native_handle_delete(handle);
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    ALOGI("Test result = %d", status);
    return status;
}
