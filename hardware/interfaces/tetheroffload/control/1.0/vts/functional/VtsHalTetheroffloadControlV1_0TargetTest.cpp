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

#define LOG_TAG "VtsOffloadControlV1_0TargetTest"

#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <android/hardware/tetheroffload/config/1.0/IOffloadConfig.h>
#include <android/hardware/tetheroffload/control/1.0/IOffloadControl.h>
#include <android/hardware/tetheroffload/control/1.0/types.h>
#include <linux/netfilter/nfnetlink.h>
#include <linux/netlink.h>
#include <log/log.h>
#include <net/if.h>
#include <sys/socket.h>
#include <unistd.h>
#include <set>

using android::base::StringPrintf;
using android::base::unique_fd;
using android::hardware::hidl_handle;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hardware::tetheroffload::config::V1_0::IOffloadConfig;
using android::hardware::tetheroffload::control::V1_0::IOffloadControl;
using android::hardware::tetheroffload::control::V1_0::IPv4AddrPortPair;
using android::hardware::tetheroffload::control::V1_0::ITetheringOffloadCallback;
using android::hardware::tetheroffload::control::V1_0::OffloadCallbackEvent;
using android::hardware::tetheroffload::control::V1_0::NatTimeoutUpdate;
using android::hardware::tetheroffload::control::V1_0::NetworkProtocol;
using android::hardware::Void;
using android::sp;

enum class ExpectBoolean {
    Ignored = -1,
    False = 0,
    True = 1,
};

constexpr const char* TEST_IFACE = "rmnet_data0";

// We use #defines here so as to get local lamba captures and error message line numbers
#define ASSERT_TRUE_CALLBACK                            \
    [&](bool success, std::string errMsg) {             \
        if (!success) {                                 \
            ALOGI("Error message: %s", errMsg.c_str()); \
        }                                               \
        ASSERT_TRUE(success);                           \
    }

#define ASSERT_FALSE_CALLBACK                           \
    [&](bool success, std::string errMsg) {             \
        if (!success) {                                 \
            ALOGI("Error message: %s", errMsg.c_str()); \
        }                                               \
        ASSERT_FALSE(success);                          \
    }

#define ASSERT_ZERO_BYTES_CALLBACK            \
    [&](uint64_t rxBytes, uint64_t txBytes) { \
        EXPECT_EQ(0ULL, rxBytes);             \
        EXPECT_EQ(0ULL, txBytes);             \
    }

inline const sockaddr* asSockaddr(const sockaddr_nl* nladdr) {
    return reinterpret_cast<const sockaddr*>(nladdr);
}

int conntrackSocket(unsigned groups) {
    unique_fd s(socket(AF_NETLINK, SOCK_DGRAM, NETLINK_NETFILTER));
    if (s.get() < 0) {
        return -errno;
    }

    const struct sockaddr_nl bind_addr = {
        .nl_family = AF_NETLINK, .nl_pad = 0, .nl_pid = 0, .nl_groups = groups,
    };
    if (::bind(s.get(), asSockaddr(&bind_addr), sizeof(bind_addr)) < 0) {
        return -errno;
    }

    const struct sockaddr_nl kernel_addr = {
        .nl_family = AF_NETLINK, .nl_pad = 0, .nl_pid = 0, .nl_groups = groups,
    };
    if (connect(s.get(), asSockaddr(&kernel_addr), sizeof(kernel_addr)) != 0) {
        return -errno;
    }

    return s.release();
}

constexpr char kCallbackOnEvent[] = "onEvent";
constexpr char kCallbackUpdateTimeout[] = "updateTimeout";

class TetheringOffloadCallbackArgs {
   public:
    OffloadCallbackEvent last_event;
    NatTimeoutUpdate last_params;
};

class OffloadControlHidlTestBase : public testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        setupConfigHal();
        prepareControlHal();
    }

    virtual void TearDown() override {
        // For good measure, we should try stopOffload() once more. Since we
        // don't know where we are in HAL call test cycle we don't know what
        // return code to actually expect, so we just ignore it.
        stopOffload(ExpectBoolean::Ignored);
    }

    // The IOffloadConfig HAL is tested more thoroughly elsewhere. He we just
    // setup everything correctly and verify basic readiness.
    void setupConfigHal() {
        config = testing::VtsHalHidlTargetTestBase::getService<IOffloadConfig>();
        ASSERT_NE(nullptr, config.get()) << "Could not get HIDL instance";

        unique_fd fd1(conntrackSocket(NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY));
        if (fd1.get() < 0) {
            ALOGE("Unable to create conntrack handles: %d/%s", errno, strerror(errno));
            FAIL();
        }
        native_handle_t* const nativeHandle1 = native_handle_create(1, 0);
        nativeHandle1->data[0] = fd1.release();
        hidl_handle h1;
        h1.setTo(nativeHandle1, true);

        unique_fd fd2(conntrackSocket(NF_NETLINK_CONNTRACK_UPDATE | NF_NETLINK_CONNTRACK_DESTROY));
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

    void prepareControlHal() {
        control = testing::VtsHalHidlTargetTestBase::getService<IOffloadControl>();
        ASSERT_NE(nullptr, control.get()) << "Could not get HIDL instance";

        control_cb = new TetheringOffloadCallback();
        ASSERT_NE(nullptr, control_cb.get()) << "Could not get get offload callback";
    }

    void initOffload(const bool expected_result) {
        auto init_cb = [&](bool success, std::string errMsg) {
            if (!success) {
                ALOGI("Error message: %s", errMsg.c_str());
            }
            ASSERT_EQ(expected_result, success);
        };
        const Return<void> ret = control->initOffload(control_cb, init_cb);
        ASSERT_TRUE(ret.isOk());
    }

    void setupControlHal() {
        prepareControlHal();
        initOffload(true);
    }

    void stopOffload(const ExpectBoolean value) {
        auto cb = [&](bool success, const hidl_string& errMsg) {
            if (!success) {
                ALOGI("Error message: %s", errMsg.c_str());
            }
            switch (value) {
                case ExpectBoolean::False:
                    ASSERT_EQ(false, success);
                    break;
                case ExpectBoolean::True:
                    ASSERT_EQ(true, success);
                    break;
                case ExpectBoolean::Ignored:
                    break;
            }
        };
        const Return<void> ret = control->stopOffload(cb);
        ASSERT_TRUE(ret.isOk());
    }

    // Callback class for both events and NAT timeout updates.
    class TetheringOffloadCallback
        : public testing::VtsHalHidlTargetCallbackBase<TetheringOffloadCallbackArgs>,
          public ITetheringOffloadCallback {
       public:
        TetheringOffloadCallback() = default;
        virtual ~TetheringOffloadCallback() = default;

        Return<void> onEvent(OffloadCallbackEvent event) override {
            const TetheringOffloadCallbackArgs args{.last_event = event};
            NotifyFromCallback(kCallbackOnEvent, args);
            return Void();
        };

        Return<void> updateTimeout(const NatTimeoutUpdate& params) override {
            const TetheringOffloadCallbackArgs args{.last_params = params};
            NotifyFromCallback(kCallbackUpdateTimeout, args);
            return Void();
        };
    };

    sp<IOffloadConfig> config;
    sp<IOffloadControl> control;
    sp<TetheringOffloadCallback> control_cb;
};

// Call initOffload() multiple times. Check that non-first initOffload() calls return false.
TEST_F(OffloadControlHidlTestBase, AdditionalInitsWithoutStopReturnFalse) {
    initOffload(true);
    initOffload(false);
    initOffload(false);
    initOffload(false);
}

// Check that calling stopOffload() without first having called initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, MultipleStopsWithoutInitReturnFalse) {
    stopOffload(ExpectBoolean::False);
    stopOffload(ExpectBoolean::False);
    stopOffload(ExpectBoolean::False);
}

// Check whether the specified interface is up.
bool interfaceIsUp(const char* name) {
    if (name == nullptr) return false;
    struct ifreq ifr = {};
    strlcpy(ifr.ifr_name, name, sizeof(ifr.ifr_name));
    int sock = socket(AF_INET6, SOCK_DGRAM, 0);
    if (sock == -1) return false;
    int ret = ioctl(sock, SIOCGIFFLAGS, &ifr, sizeof(ifr));
    close(sock);
    return (ret == 0) && (ifr.ifr_flags & IFF_UP);
}

// Check that calling stopOffload() after a complete init/stop cycle returns false.
TEST_F(OffloadControlHidlTestBase, AdditionalStopsWithInitReturnFalse) {
    initOffload(true);
    // Call setUpstreamParameters() so that "offload" can be reasonably said
    // to be both requested and operational.
    const hidl_string v4Addr("192.0.0.2");
    const hidl_string v4Gw("192.0.0.1");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1"), hidl_string("fe80::db8:2")};
    const Return<void> upstream =
        control->setUpstreamParameters(TEST_IFACE, v4Addr, v4Gw, v6Gws, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(upstream.isOk());
    if (!interfaceIsUp(TEST_IFACE)) {
        return;
    }
    stopOffload(ExpectBoolean::True);  // balance out initOffload(true)
    stopOffload(ExpectBoolean::False);
    stopOffload(ExpectBoolean::False);
}

// Check that calling setLocalPrefixes() without first having called initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, SetLocalPrefixesWithoutInitReturnsFalse) {
    const vector<hidl_string> prefixes{hidl_string("2001:db8::/64")};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling getForwardedStats() without first having called initOffload()
// returns zero bytes statistics.
TEST_F(OffloadControlHidlTestBase, GetForwardedStatsWithoutInitReturnsZeroValues) {
    const hidl_string upstream(TEST_IFACE);
    const Return<void> ret = control->getForwardedStats(upstream, ASSERT_ZERO_BYTES_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling setDataLimit() without first having called initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, SetDataLimitWithoutInitReturnsFalse) {
    const hidl_string upstream(TEST_IFACE);
    const uint64_t limit = 5000ULL;
    const Return<void> ret = control->setDataLimit(upstream, limit, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling setUpstreamParameters() without first having called initOffload()
// returns false.
TEST_F(OffloadControlHidlTestBase, SetUpstreamParametersWithoutInitReturnsFalse) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("192.0.2.0/24");
    const hidl_string v4Gw("192.0.2.1");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1")};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling addDownstream() with an IPv4 prefix without first having called
// initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, AddIPv4DownstreamWithoutInitReturnsFalse) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string prefix("192.0.2.0/24");
    const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling addDownstream() with an IPv6 prefix without first having called
// initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, AddIPv6DownstreamWithoutInitReturnsFalse) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string prefix("2001:db8::/64");
    const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling removeDownstream() with an IPv4 prefix without first having called
// initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, RemoveIPv4DownstreamWithoutInitReturnsFalse) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string prefix("192.0.2.0/24");
    const Return<void> ret = control->removeDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Check that calling removeDownstream() with an IPv6 prefix without first having called
// initOffload() returns false.
TEST_F(OffloadControlHidlTestBase, RemoveIPv6DownstreamWithoutInitReturnsFalse) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string prefix("2001:db8::/64");
    const Return<void> ret = control->removeDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

class OffloadControlHidlTest : public OffloadControlHidlTestBase {
   public:
    virtual void SetUp() override {
        setupConfigHal();
        setupControlHal();
    }

    virtual void TearDown() override {
        // For good measure, we should try stopOffload() once more. Since we
        // don't know where we are in HAL call test cycle we don't know what
        // return code to actually expect, so we just ignore it.
        stopOffload(ExpectBoolean::Ignored);
    }
};

/*
 * Tests for IOffloadControl::setLocalPrefixes().
 */

// Test setLocalPrefixes() accepts an IPv4 address.
TEST_F(OffloadControlHidlTest, SetLocalPrefixesIPv4AddressOk) {
    const vector<hidl_string> prefixes{hidl_string("192.0.2.1")};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test setLocalPrefixes() accepts an IPv6 address.
TEST_F(OffloadControlHidlTest, SetLocalPrefixesIPv6AddressOk) {
    const vector<hidl_string> prefixes{hidl_string("fe80::1")};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test setLocalPrefixes() accepts both IPv4 and IPv6 prefixes.
TEST_F(OffloadControlHidlTest, SetLocalPrefixesIPv4v6PrefixesOk) {
    const vector<hidl_string> prefixes{hidl_string("192.0.2.0/24"), hidl_string("fe80::/64")};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test that setLocalPrefixes() fails given empty input. There is always
// a non-empty set of local prefixes; when all networking interfaces are down
// we still apply {127.0.0.0/8, ::1/128, fe80::/64} here.
TEST_F(OffloadControlHidlTest, SetLocalPrefixesEmptyFails) {
    const vector<hidl_string> prefixes{};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test setLocalPrefixes() fails on incorrectly formed input strings.
TEST_F(OffloadControlHidlTest, SetLocalPrefixesInvalidFails) {
    const vector<hidl_string> prefixes{hidl_string("192.0.2.0/24"), hidl_string("invalid")};
    const Return<void> ret = control->setLocalPrefixes(prefixes, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

/*
 * Tests for IOffloadControl::getForwardedStats().
 */

// Test that getForwardedStats() for a non-existent upstream yields zero bytes statistics.
TEST_F(OffloadControlHidlTest, GetForwardedStatsInvalidUpstreamIface) {
    const hidl_string upstream("invalid");
    const Return<void> ret = control->getForwardedStats(upstream, ASSERT_ZERO_BYTES_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, GetForwardedStatsDummyIface) {
    const hidl_string upstream(TEST_IFACE);
    const Return<void> ret = control->getForwardedStats(upstream, ASSERT_ZERO_BYTES_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

/*
 * Tests for IOffloadControl::setDataLimit().
 */

// Test that setDataLimit() for an empty interface name fails.
TEST_F(OffloadControlHidlTest, SetDataLimitEmptyUpstreamIfaceFails) {
    const hidl_string upstream("");
    const uint64_t limit = 5000ULL;
    const Return<void> ret = control->setDataLimit(upstream, limit, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetDataLimitNonZeroOk) {
    const hidl_string upstream(TEST_IFACE);
    const uint64_t limit = 5000ULL;
    const Return<void> ret = control->setDataLimit(upstream, limit, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetDataLimitZeroOk) {
    const hidl_string upstream(TEST_IFACE);
    const uint64_t limit = 0ULL;
    const Return<void> ret = control->setDataLimit(upstream, limit, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

/*
 * Tests for IOffloadControl::setUpstreamParameters().
 */

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersIPv6OnlyOk) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("");
    const hidl_string v4Gw("");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1"), hidl_string("fe80::db8:2")};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersAlternateIPv6OnlyOk) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr;
    const hidl_string v4Gw;
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1"), hidl_string("fe80::db8:3")};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersIPv4OnlyOk) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("192.0.2.2");
    const hidl_string v4Gw("192.0.2.1");
    const vector<hidl_string> v6Gws{};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// TEST_IFACE is presumed to exist on the device and be up. No packets
// are ever actually caused to be forwarded.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersIPv4v6Ok) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("192.0.2.2");
    const hidl_string v4Gw("192.0.2.1");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1"), hidl_string("fe80::db8:2")};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test that setUpstreamParameters() fails when all parameters are empty.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersEmptyFails) {
    const hidl_string iface("");
    const hidl_string v4Addr("");
    const hidl_string v4Gw("");
    const vector<hidl_string> v6Gws{};
    const Return<void> ret =
        control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test that setUpstreamParameters() fails when given empty or non-existent interface names.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersBogusIfaceFails) {
    const hidl_string v4Addr("192.0.2.2");
    const hidl_string v4Gw("192.0.2.1");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1")};
    for (const auto& bogus : {"", "invalid"}) {
        SCOPED_TRACE(StringPrintf("iface='%s'", bogus));
        const hidl_string iface(bogus);
        const Return<void> ret =
            control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

// Test that setUpstreamParameters() fails when given unparseable IPv4 addresses.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersInvalidIPv4AddrFails) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Gw("192.0.2.1");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1")};
    for (const auto& bogus : {"invalid", "192.0.2"}) {
        SCOPED_TRACE(StringPrintf("v4addr='%s'", bogus));
        const hidl_string v4Addr(bogus);
        const Return<void> ret =
            control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

// Test that setUpstreamParameters() fails when given unparseable IPv4 gateways.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersInvalidIPv4GatewayFails) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("192.0.2.2");
    const vector<hidl_string> v6Gws{hidl_string("fe80::db8:1")};
    for (const auto& bogus : {"invalid", "192.0.2"}) {
        SCOPED_TRACE(StringPrintf("v4gateway='%s'", bogus));
        const hidl_string v4Gw(bogus);
        const Return<void> ret =
            control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

// Test that setUpstreamParameters() fails when given unparseable IPv6 gateways.
TEST_F(OffloadControlHidlTest, SetUpstreamParametersBadIPv6GatewaysFail) {
    const hidl_string iface(TEST_IFACE);
    const hidl_string v4Addr("192.0.2.2");
    const hidl_string v4Gw("192.0.2.1");
    for (const auto& bogus : {"", "invalid", "fe80::bogus", "192.0.2.66"}) {
        SCOPED_TRACE(StringPrintf("v6gateway='%s'", bogus));
        const vector<hidl_string> v6Gws{hidl_string("fe80::1"), hidl_string(bogus)};
        const Return<void> ret =
            control->setUpstreamParameters(iface, v4Addr, v4Gw, v6Gws, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

/*
 * Tests for IOffloadControl::addDownstream().
 */

// Test addDownstream() works given an IPv4 prefix.
TEST_F(OffloadControlHidlTest, AddDownstreamIPv4) {
    const hidl_string iface("dummy0");
    const hidl_string prefix("192.0.2.0/24");
    const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test addDownstream() works given an IPv6 prefix.
TEST_F(OffloadControlHidlTest, AddDownstreamIPv6) {
    const hidl_string iface("dummy0");
    const hidl_string prefix("2001:db8::/64");
    const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test addDownstream() fails given all empty parameters.
TEST_F(OffloadControlHidlTest, AddDownstreamEmptyFails) {
    const hidl_string iface("");
    const hidl_string prefix("");
    const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test addDownstream() fails given empty or non-existent interface names.
TEST_F(OffloadControlHidlTest, AddDownstreamInvalidIfaceFails) {
    const hidl_string prefix("192.0.2.0/24");
    for (const auto& bogus : {"", "invalid"}) {
        SCOPED_TRACE(StringPrintf("iface='%s'", bogus));
        const hidl_string iface(bogus);
        const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

// Test addDownstream() fails given unparseable prefix arguments.
TEST_F(OffloadControlHidlTest, AddDownstreamBogusPrefixFails) {
    const hidl_string iface("dummy0");
    for (const auto& bogus : {"", "192.0.2/24", "2001:db8/64"}) {
        SCOPED_TRACE(StringPrintf("prefix='%s'", bogus));
        const hidl_string prefix(bogus);
        const Return<void> ret = control->addDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

/*
 * Tests for IOffloadControl::removeDownstream().
 */

// Test removeDownstream() works given an IPv4 prefix.
TEST_F(OffloadControlHidlTest, RemoveDownstreamIPv4) {
    const hidl_string iface("dummy0");
    const hidl_string prefix("192.0.2.0/24");
    // First add the downstream, otherwise removeDownstream logic can reasonably
    // return false for downstreams not previously added.
    const Return<void> add = control->addDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(add.isOk());
    const Return<void> del = control->removeDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(del.isOk());
}

// Test removeDownstream() works given an IPv6 prefix.
TEST_F(OffloadControlHidlTest, RemoveDownstreamIPv6) {
    const hidl_string iface("dummy0");
    const hidl_string prefix("2001:db8::/64");
    // First add the downstream, otherwise removeDownstream logic can reasonably
    // return false for downstreams not previously added.
    const Return<void> add = control->addDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(add.isOk());
    const Return<void> del = control->removeDownstream(iface, prefix, ASSERT_TRUE_CALLBACK);
    EXPECT_TRUE(del.isOk());
}

// Test removeDownstream() fails given all empty parameters.
TEST_F(OffloadControlHidlTest, RemoveDownstreamEmptyFails) {
    const hidl_string iface("");
    const hidl_string prefix("");
    const Return<void> ret = control->removeDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
    EXPECT_TRUE(ret.isOk());
}

// Test removeDownstream() fails given empty or non-existent interface names.
TEST_F(OffloadControlHidlTest, RemoveDownstreamBogusIfaceFails) {
    const hidl_string prefix("192.0.2.0/24");
    for (const auto& bogus : {"", "invalid"}) {
        SCOPED_TRACE(StringPrintf("iface='%s'", bogus));
        const hidl_string iface(bogus);
        const Return<void> ret = control->removeDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

// Test removeDownstream() fails given unparseable prefix arguments.
TEST_F(OffloadControlHidlTest, RemoveDownstreamBogusPrefixFails) {
    const hidl_string iface("dummy0");
    for (const auto& bogus : {"", "192.0.2/24", "2001:db8/64"}) {
        SCOPED_TRACE(StringPrintf("prefix='%s'", bogus));
        const hidl_string prefix(bogus);
        const Return<void> ret = control->removeDownstream(iface, prefix, ASSERT_FALSE_CALLBACK);
        EXPECT_TRUE(ret.isOk());
    }
}

int main(int argc, char** argv) {
    testing::InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    ALOGE("Test result with status=%d", status);
    return status;
}
