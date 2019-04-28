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
#define LOG_TAG "AsyncIO_test.cpp"

#include <android-base/test_utils.h>
#include <fcntl.h>
#include <gtest/gtest.h>
#include <string>
#include <unistd.h>
#include <utils/Log.h>

#include "AsyncIO.h"

namespace android {

constexpr int TEST_PACKET_SIZE = 512;
constexpr int POOL_COUNT = 10;

static const std::string dummyDataStr =
    "/*\n * Copyright 2015 The Android Open Source Project\n *\n * Licensed un"
    "der the Apache License, Version 2.0 (the \"License\");\n * you may not us"
    "e this file except in compliance with the License.\n * You may obtain a c"
    "opy of the License at\n *\n *      http://www.apache.org/licenses/LICENSE"
    "-2.0\n *\n * Unless required by applicable law or agreed to in writing, s"
    "oftware\n * distributed under the License is distributed on an \"AS IS\" "
    "BASIS,\n * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express o"
    "r implied.\n * Se";


class AsyncIOTest : public ::testing::Test {
protected:
    TemporaryFile dummy_file;

    AsyncIOTest() {}
    ~AsyncIOTest() {}
};

TEST_F(AsyncIOTest, testRead) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    EXPECT_EQ(write(dummy_file.fd, dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    struct aiocb aio;
    struct aiocb *aiol[] = {&aio};
    aio.aio_fildes = dummy_file.fd;
    aio.aio_buf = buf;
    aio.aio_offset = 0;
    aio.aio_nbytes = TEST_PACKET_SIZE;

    EXPECT_EQ(aio_read(&aio), 0);
    EXPECT_EQ(aio_suspend(aiol, 1, nullptr), 0);
    EXPECT_EQ(aio_return(&aio), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(AsyncIOTest, testWrite) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    struct aiocb aio;
    struct aiocb *aiol[] = {&aio};
    aio.aio_fildes = dummy_file.fd;
    aio.aio_buf = const_cast<char*>(dummyDataStr.c_str());
    aio.aio_offset = 0;
    aio.aio_nbytes = TEST_PACKET_SIZE;

    EXPECT_EQ(aio_write(&aio), 0);
    EXPECT_EQ(aio_suspend(aiol, 1, nullptr), 0);
    EXPECT_EQ(aio_return(&aio), TEST_PACKET_SIZE);
    EXPECT_EQ(read(dummy_file.fd, buf, TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(AsyncIOTest, testError) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    struct aiocb aio;
    struct aiocb *aiol[] = {&aio};
    aio.aio_fildes = -1;
    aio.aio_buf = const_cast<char*>(dummyDataStr.c_str());
    aio.aio_offset = 0;
    aio.aio_nbytes = TEST_PACKET_SIZE;

    EXPECT_EQ(aio_write(&aio), 0);
    EXPECT_EQ(aio_suspend(aiol, 1, nullptr), 0);
    EXPECT_EQ(aio_return(&aio), -1);
    EXPECT_EQ(aio_error(&aio), EBADF);
}

TEST_F(AsyncIOTest, testSpliceRead) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    int pipeFd[2];
    EXPECT_EQ(pipe(pipeFd), 0);
    EXPECT_EQ(write(dummy_file.fd, dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    struct aiocb aio;
    struct aiocb *aiol[] = {&aio};
    aio.aio_fildes = dummy_file.fd;
    aio.aio_sink = pipeFd[1];
    aio.aio_offset = 0;
    aio.aio_nbytes = TEST_PACKET_SIZE;

    EXPECT_EQ(aio_splice_read(&aio), 0);
    EXPECT_EQ(aio_suspend(aiol, 1, nullptr), 0);
    EXPECT_EQ(aio_return(&aio), TEST_PACKET_SIZE);

    EXPECT_EQ(read(pipeFd[0], buf, TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(AsyncIOTest, testSpliceWrite) {
    char buf[TEST_PACKET_SIZE + 1];
    buf[TEST_PACKET_SIZE] = '\0';
    int pipeFd[2];
    EXPECT_EQ(pipe(pipeFd), 0);
    EXPECT_EQ(write(pipeFd[1], dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    struct aiocb aio;
    struct aiocb *aiol[] = {&aio};
    aio.aio_fildes = pipeFd[0];
    aio.aio_sink = dummy_file.fd;
    aio.aio_offset = 0;
    aio.aio_nbytes = TEST_PACKET_SIZE;

    EXPECT_EQ(aio_splice_write(&aio), 0);
    EXPECT_EQ(aio_suspend(aiol, 1, nullptr), 0);
    EXPECT_EQ(aio_return(&aio), TEST_PACKET_SIZE);
    EXPECT_EQ(read(dummy_file.fd, buf, TEST_PACKET_SIZE), TEST_PACKET_SIZE);
    EXPECT_STREQ(buf, dummyDataStr.c_str());
}

TEST_F(AsyncIOTest, testPoolWrite) {
    aio_pool_write_init();
    char buf[TEST_PACKET_SIZE * POOL_COUNT + 1];
    buf[TEST_PACKET_SIZE * POOL_COUNT] = '\0';

    for (int i = 0; i < POOL_COUNT; i++) {
        struct aiocb *aiop = new struct aiocb;
        aiop->aio_fildes = dummy_file.fd;
        aiop->aio_pool_buf = std::unique_ptr<char[]>(new char[TEST_PACKET_SIZE]);
        memcpy(aiop->aio_pool_buf.get(), dummyDataStr.c_str(), TEST_PACKET_SIZE);
        aiop->aio_offset = i * TEST_PACKET_SIZE;
        aiop->aio_nbytes = TEST_PACKET_SIZE;
        EXPECT_EQ(aio_pool_write(aiop), 0);
    }
    aio_pool_end();
    EXPECT_EQ(read(dummy_file.fd, buf, TEST_PACKET_SIZE * POOL_COUNT), TEST_PACKET_SIZE * POOL_COUNT);

    std::stringstream ss;
    for (int i = 0; i < POOL_COUNT; i++)
        ss << dummyDataStr;

    EXPECT_STREQ(buf, ss.str().c_str());
}

TEST_F(AsyncIOTest, testSplicePoolWrite) {
    aio_pool_splice_init();
    char buf[TEST_PACKET_SIZE * POOL_COUNT + 1];
    buf[TEST_PACKET_SIZE * POOL_COUNT] = '\0';

    for (int i = 0; i < POOL_COUNT; i++) {
        int pipeFd[2];
        EXPECT_EQ(pipe(pipeFd), 0);
        EXPECT_EQ(write(pipeFd[1], dummyDataStr.c_str(), TEST_PACKET_SIZE), TEST_PACKET_SIZE);
        struct aiocb *aiop = new struct aiocb;
        aiop->aio_fildes = pipeFd[0];
        aiop->aio_sink = dummy_file.fd;
        aiop->aio_offset = i * TEST_PACKET_SIZE;
        aiop->aio_nbytes = TEST_PACKET_SIZE;
        EXPECT_EQ(aio_pool_write(aiop), 0);
    }
    aio_pool_end();
    EXPECT_EQ(read(dummy_file.fd, buf, TEST_PACKET_SIZE * POOL_COUNT), TEST_PACKET_SIZE * POOL_COUNT);

    std::stringstream ss;
    for (int i = 0; i < POOL_COUNT; i++)
        ss << dummyDataStr;

    EXPECT_STREQ(buf, ss.str().c_str());
}

} // namespace android
