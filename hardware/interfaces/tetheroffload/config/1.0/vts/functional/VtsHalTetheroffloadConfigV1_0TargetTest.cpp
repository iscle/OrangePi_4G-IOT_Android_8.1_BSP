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

#define LOG_TAG "VtsOffloadConfigV1_0TargetTest"

#include <VtsHalHidlTargetTestBase.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <android/hardware/tetheroffload/config/1.0/IOffloadConfig.h>
#include <linux/netfilter/nfnetlink.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <log/log.h>
#include <sys/socket.h>
#include <unistd.h>
#include <set>

using android::base::StringPrintf;
using android::base::unique_fd;
using android::hardware::hidl_handle;
using android::hardware::hidl_string;
using android::hardware::Return;
using android::hardware::tetheroffload::config::V1_0::IOffloadConfig;
using android::hardware::Void;
using android::sp;

#define ASSERT_TRUE_CALLBACK \
    [&](bool success, const hidl_string& errMsg) { ASSERT_TRUE(success) << errMsg.c_str(); }

#define ASSERT_FALSE_CALLBACK \
    [&](bool success, const hidl_string& errMsg) { ASSERT_FALSE(success) << errMsg.c_str(); }

const unsigned kFd1Groups = NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY;
const unsigned kFd2Groups = NF_NETLINK_CONNTRACK_UPDATE | NF_NETLINK_CONNTRACK_DESTROY;

inline const sockaddr* asSockaddr(const sockaddr_nl* nladdr) {
    return reinterpret_cast<const sockaddr*>(nladdr);
}

int netlinkSocket(int protocol, unsigned groups) {
    unique_fd s(socket(AF_NETLINK, SOCK_DGRAM, protocol));
    if (s.get() < 0) {
        return -errno;
    }

    const struct sockaddr_nl bind_addr = {
        .nl_family = AF_NETLINK, .nl_pad = 0, .nl_pid = 0, .nl_groups = groups,
    };
    if (::bind(s.get(), asSockaddr(&bind_addr), sizeof(bind_addr)) != 0) {
        return -errno;
    }

    const struct sockaddr_nl kernel_addr = {
        .nl_family = AF_NETLINK, .nl_pad = 0, .nl_pid = 0, .nl_groups = groups,
    };
    if (::connect(s.get(), asSockaddr(&kernel_addr), sizeof(kernel_addr)) != 0) {
        return -errno;
    }

    return s.release();
}

int netlinkSocket(unsigned groups) {
    return netlinkSocket(NETLINK_NETFILTER, groups);
}

class OffloadConfigHidlTest : public testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        config = testing::VtsHalHidlTargetTestBase::getService<IOffloadConfig>();
        ASSERT_NE(nullptr, config.get()) << "Could not get HIDL instance";
    }

    virtual void TearDown() override {}

    sp<IOffloadConfig> config;
};

// Ensure handles can be set with correct socket options.
TEST_F(OffloadConfigHidlTest, TestSetHandles) {
    // Try multiple times in a row to see if it provokes file descriptor leaks.
    for (int i = 0; i < 1024; i++) {
        unique_fd fd1(netlinkSocket(kFd1Groups));
        if (fd1.get() < 0) {
            ALOGE("Unable to create conntrack handles: %d/%s", errno, strerror(errno));
            FAIL();
        }
        native_handle_t* const nativeHandle1 = native_handle_create(1, 0);
        nativeHandle1->data[0] = fd1.release();
        hidl_handle h1;
        h1.setTo(nativeHandle1, true);

        unique_fd fd2(netlinkSocket(kFd2Groups));
        if (fd2.get() < 0) {
            ALOGE("Unable to create conntrack handles: %d/%s", errno, strerror(errno));
            FAIL();
        }
        native_handle_t* const nativeHandle2 = native_handle_create(1, 0);
        nativeHandle2->data[0] = fd2.release();
        hidl_handle h2;
        h2.setTo(nativeHandle2, true);

        const Return<void> ret = config->setHandles(h1, h2, ASSERT_TRUE_CALLBACK);
        ASSERT_TRUE(ret.isOk());
    }
}

// Passing a handle without an associated file descriptor should return an error
// (e.g. "Failed Input Checks"). Check that this occurs when both FDs are empty.
TEST_F(OffloadConfigHidlTest, TestSetHandleNone) {
    native_handle_t* const nativeHandle1 = native_handle_create(0, 0);
    hidl_handle h1;
    h1.setTo(nativeHandle1, true);
    native_handle_t* const nativeHandle2 = native_handle_create(0, 0);
    hidl_handle h2;
    h2.setTo(nativeHandle2, true);

    const Return<void> ret = config->setHandles(h1, h2, ASSERT_FALSE_CALLBACK);
    ASSERT_TRUE(ret.isOk());
}

// Passing a handle without an associated file descriptor should return an error
// (e.g. "Failed Input Checks"). Check that this occurs when FD2 is empty.
TEST_F(OffloadConfigHidlTest, TestSetHandle1Only) {
    unique_fd fd1(netlinkSocket(kFd1Groups));
    if (fd1.get() < 0) {
        ALOGE("Unable to create conntrack handles: %d/%s", errno, strerror(errno));
        FAIL();
    }
    native_handle_t* const nativeHandle1 = native_handle_create(1, 0);
    nativeHandle1->data[0] = fd1.release();
    hidl_handle h1;
    h1.setTo(nativeHandle1, true);

    native_handle_t* const nativeHandle2 = native_handle_create(0, 0);
    hidl_handle h2;
    h2.setTo(nativeHandle2, true);

    const Return<void> ret = config->setHandles(h1, h2, ASSERT_FALSE_CALLBACK);
    ASSERT_TRUE(ret.isOk());
}

// Passing a handle without an associated file descriptor should return an error
// (e.g. "Failed Input Checks"). Check that this occurs when FD1 is empty.
TEST_F(OffloadConfigHidlTest, TestSetHandle2OnlyNotOk) {
    native_handle_t* const nativeHandle1 = native_handle_create(0, 0);
    hidl_handle h1;
    h1.setTo(nativeHandle1, true);

    unique_fd fd2(netlinkSocket(kFd2Groups));
    if (fd2.get() < 0) {
        ALOGE("Unable to create conntrack handles: %d/%s", errno, strerror(errno));
        FAIL();
    }
    native_handle_t* const nativeHandle2 = native_handle_create(1, 0);
    nativeHandle2->data[0] = fd2.release();
    hidl_handle h2;
    h2.setTo(nativeHandle2, true);

    const Return<void> ret = config->setHandles(h1, h2, ASSERT_FALSE_CALLBACK);
    ASSERT_TRUE(ret.isOk());
}

int main(int argc, char** argv) {
    testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    ALOGE("Test result with status=%d", status);
    return status;
}
