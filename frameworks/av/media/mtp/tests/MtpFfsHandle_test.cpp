/*
 * Copyright 2016 The Android Open Source Project
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
#define LOG_TAG "MtpFfsHandle_test.cpp"

#include <android-base/unique_fd.h>
#include <android-base/test_utils.h>
#include <fcntl.h>
#include <gtest/gtest.h>
#include <memory>
#include <random>
#include <string>
#include <unistd.h>
#include <utils/Log.h>

#include "MtpFfsHandle.h"

namespace android {

constexpr int MAX_FILE_CHUNK_SIZE = 3 * 1024 * 1024;

constexpr int TEST_PACKET_SIZE = 512;
constexpr int SMALL_MULT = 30;
constexpr int MED_MULT = 510;

static const std::string dummyDataStr =
    "/*\n * Copyright 2015 The Android Open Source Project\n *\n * Licensed un"
    "der the Apache License, Version 2.0 (the \"License\");\n * you may not us"
    "e this file except in compliance with the License.\n * You may obtain a c"
    "opy of the License at\n *\n *      http://www.apache.org/licenses/LICENSE"
    "-2.0\n *\n * Unless required by applicable law or agreed to in writing, s"
    "oftware\n * distributed under the License is distributed on an \"AS IS\" "
    "BASIS,\n * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express o"
    "r implied.\n * Se";

/**
 * Functional tests for the MtpFfsHandle class. Ensures header and data integrity
 * by mocking ffs endpoints as pipes to capture input / output.
 */
class MtpFfsHandleTest : public ::testing::Test {
protected:
    std::unique_ptr<IMtpHandle> handle;

    // Pipes for reading endpoint data
    android::base::unique_fd bulk_in;
    android::base::unique_fd bulk_out;
    android::base::unique_fd intr;

    TemporaryFile dummy_file;

    MtpFfsHandleTest() {
        int fd[2];
        handle = std::unique_ptr<IMtpHandle>(get_ffs_handle());
        MtpFfsHandle *ffs_handle = static_cast<MtpFfsHandle*>(handle.get());
        EXPECT_TRUE(ffs_handle != NULL);

        EXPECT_EQ(pipe(fd), 0);
        EXPECT_EQ(fcntl(fd[0], F_SETPIPE_SZ, 1048576), 1048576);
        bulk_in.reset(fd[0]);
        ffs_handle->mBulkIn.reset(fd[1]);

        EXPECT_EQ(pipe(fd), 0);
        EXPECT_EQ(fcntl(fd[0], F_SETPIPE_SZ, 1048576), 1048576);
        bulk_out.reset(fd[1]);
        ffs_handle->mBulkOut.reset(fd[0]);

        EXPECT_EQ(pipe(fd), 0);
        intr.reset(fd[0]);
        ffs_handle->mIntr.reset(fd[1]);

        ffs_handle->mBuffer1.resize(MAX_FILE_CHUNK_SIZE);
        ffs_handle->mBuffer2.resize(MAX_FILE_CHUNK_SIZE);
    }

    ~MtpFfsHandleTest() {}
};

TEST_F(MtpFfsHandleTest, testRead) {
    EXPECT_EQ(write(bulk_out, dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    EXPECT_EQ(handle->read(buf, TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(MtpFfsHandleTest, testWrite) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    EXPECT_EQ(handle->write(dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_EQ(read(bulk_in, buf, TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(MtpFfsHandleTest, testReceiveFileSmall) {
    std::stringstream ss;
    mtp_file_range mfr;
    int size = TEST_PACKET_SIZE * SMALL_MULT;
    char buf[size + 1];
    buf[size] = '\0';

    mfr.length = size;
    mfr.fd = dummy_file.fd;
    for (int i = 0; i < SMALL_MULT; i++)
        ss << dummyDataStr;

    EXPECT_EQ(write(bulk_out, ss.str().c_str(), size), size);
    EXPECT_EQ(handle->receiveFile(mfr, false), 0);

    EXPECT_EQ(read(dummy_file.fd, buf, size), size);

    EXPECT_STREQ(buf, ss.str().c_str());
}

TEST_F(MtpFfsHandleTest, testReceiveFileMed) {
    std::stringstream ss;
    mtp_file_range mfr;
    int size = TEST_PACKET_SIZE * MED_MULT;
    char buf[size + 1];
    buf[size] = '\0';

    mfr.length = size;
    mfr.fd = dummy_file.fd;
    for (int i = 0; i < MED_MULT; i++)
        ss << dummyDataStr;

    EXPECT_EQ(write(bulk_out, ss.str().c_str(), size), size);
    EXPECT_EQ(handle->receiveFile(mfr, false), 0);

    EXPECT_EQ(read(dummy_file.fd, buf, size), size);

    EXPECT_STREQ(buf, ss.str().c_str());
}

TEST_F(MtpFfsHandleTest, testSendFileSmall) {
    std::stringstream ss;
    mtp_file_range mfr;
    mfr.command = 42;
    mfr.transaction_id = 1337;
    mfr.offset = 0;
    int size = TEST_PACKET_SIZE * SMALL_MULT;
    char buf[size + sizeof(mtp_data_header) + 1];
    buf[size + sizeof(mtp_data_header)] = '\0';

    mfr.length = size;
    mfr.fd = dummy_file.fd;
    for (int i = 0; i < SMALL_MULT; i++)
        ss << dummyDataStr;

    EXPECT_EQ(write(dummy_file.fd, ss.str().c_str(), size), size);
    EXPECT_EQ(handle->sendFile(mfr), 0);

    EXPECT_EQ(read(bulk_in, buf, size + sizeof(mtp_data_header)),
            static_cast<long>(size + sizeof(mtp_data_header)));

    struct mtp_data_header *header = reinterpret_cast<struct mtp_data_header*>(buf);
    EXPECT_STREQ(buf + sizeof(mtp_data_header), ss.str().c_str());
    EXPECT_EQ(header->length, static_cast<unsigned int>(size + sizeof(mtp_data_header)));
    EXPECT_EQ(header->type, static_cast<unsigned int>(2));
    EXPECT_EQ(header->command, static_cast<unsigned int>(42));
    EXPECT_EQ(header->transaction_id, static_cast<unsigned int>(1337));
}

TEST_F(MtpFfsHandleTest, testSendFileMed) {
    std::stringstream ss;
    mtp_file_range mfr;
    mfr.command = 42;
    mfr.transaction_id = 1337;
    mfr.offset = 0;
    int size = TEST_PACKET_SIZE * MED_MULT;
    char buf[size + sizeof(mtp_data_header) + 1];
    buf[size + sizeof(mtp_data_header)] = '\0';

    mfr.length = size;
    mfr.fd = dummy_file.fd;
    for (int i = 0; i < MED_MULT; i++)
        ss << dummyDataStr;

    EXPECT_EQ(write(dummy_file.fd, ss.str().c_str(), size), size);
    EXPECT_EQ(handle->sendFile(mfr), 0);

    EXPECT_EQ(read(bulk_in, buf, size + sizeof(mtp_data_header)),
            static_cast<long>(size + sizeof(mtp_data_header)));

    struct mtp_data_header *header = reinterpret_cast<struct mtp_data_header*>(buf);
    EXPECT_STREQ(buf + sizeof(mtp_data_header), ss.str().c_str());
    EXPECT_EQ(header->length, static_cast<unsigned int>(size + sizeof(mtp_data_header)));
    EXPECT_EQ(header->type, static_cast<unsigned int>(2));
    EXPECT_EQ(header->command, static_cast<unsigned int>(42));
    EXPECT_EQ(header->transaction_id, static_cast<unsigned int>(1337));
}

TEST_F(MtpFfsHandleTest, testSendFileMedPartial) {
    std::stringstream ss;
    mtp_file_range mfr;
    mfr.fd = dummy_file.fd;
    mfr.command = 42;
    mfr.transaction_id = 1337;
    int size = TEST_PACKET_SIZE * MED_MULT;
    char buf[size + 1];
    buf[size] = '\0';

    for (int i = 0; i < MED_MULT; i++)
        ss << dummyDataStr;

    EXPECT_EQ(write(dummy_file.fd, ss.str().c_str(), size), size);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(1, TEST_PACKET_SIZE);
    int offset = 0;
    while (offset != size) {
        mfr.offset = offset;
        int length = std::min(size - offset, dis(gen));
        mfr.length = length;
        char temp_buf[length + sizeof(mtp_data_header)];
        EXPECT_EQ(handle->sendFile(mfr), 0);

        EXPECT_EQ(read(bulk_in, temp_buf, length + sizeof(mtp_data_header)),
                static_cast<long>(length + sizeof(mtp_data_header)));

        struct mtp_data_header *header = reinterpret_cast<struct mtp_data_header*>(temp_buf);
        EXPECT_EQ(header->length, static_cast<unsigned int>(length + sizeof(mtp_data_header)));
        EXPECT_EQ(header->type, static_cast<unsigned int>(2));
        EXPECT_EQ(header->command, static_cast<unsigned int>(42));
        EXPECT_EQ(header->transaction_id, static_cast<unsigned int>(1337));
        memcpy(buf + offset, temp_buf + sizeof(mtp_data_header), length);
        offset += length;
    }
    EXPECT_STREQ(buf, ss.str().c_str());
}

TEST_F(MtpFfsHandleTest, testSendFileEmpty) {
    mtp_file_range mfr;
    mfr.command = 42;
    mfr.transaction_id = 1337;
    mfr.offset = 0;
    int size = 0;
    char buf[size + sizeof(mtp_data_header) + 1];
    buf[size + sizeof(mtp_data_header)] = '\0';

    mfr.length = size;
    mfr.fd = dummy_file.fd;

    EXPECT_EQ(handle->sendFile(mfr), 0);

    EXPECT_EQ(read(bulk_in, buf, size + sizeof(mtp_data_header)),
            static_cast<long>(size + sizeof(mtp_data_header)));

    struct mtp_data_header *header = reinterpret_cast<struct mtp_data_header*>(buf);
    EXPECT_EQ(header->length, static_cast<unsigned int>(size + sizeof(mtp_data_header)));
    EXPECT_EQ(header->type, static_cast<unsigned int>(2));
    EXPECT_EQ(header->command, static_cast<unsigned int>(42));
    EXPECT_EQ(header->transaction_id, static_cast<unsigned int>(1337));
}

TEST_F(MtpFfsHandleTest, testSendEvent) {
    struct mtp_event event;
    event.length = TEST_PACKET_SIZE;
    event.data = const_cast<char*>(dummyDataStr.c_str());
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';

    handle->sendEvent(event);
    read(intr, buf, TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

} // namespace android
