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

#include <stdlib.h>
#include <string.h>
#include <sys/statvfs.h>
#include <sys/xattr.h>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cutils/properties.h>
#include <gtest/gtest.h>

#include "InstalldNativeService.h"
#include "globals.h"
#include "utils.h"

using android::base::StringPrintf;

namespace android {
namespace installd {

constexpr const char* kTestUuid = "TEST";

static constexpr int FLAG_FORCE = 1 << 16;

int get_property(const char *key, char *value, const char *default_value) {
    return property_get(key, value, default_value);
}

bool calculate_oat_file_path(char path[PKG_PATH_MAX] ATTRIBUTE_UNUSED,
        const char *oat_dir ATTRIBUTE_UNUSED,
        const char *apk_path ATTRIBUTE_UNUSED,
        const char *instruction_set ATTRIBUTE_UNUSED) {
    return false;
}

bool calculate_odex_file_path(char path[PKG_PATH_MAX] ATTRIBUTE_UNUSED,
        const char *apk_path ATTRIBUTE_UNUSED,
        const char *instruction_set ATTRIBUTE_UNUSED) {
    return false;
}

bool create_cache_path(char path[PKG_PATH_MAX],
        const char *src,
        const char *instruction_set) {
    // Not really a valid path but it's good enough for testing.
    sprintf(path,"/data/dalvik-cache/%s/%s", instruction_set, src);
    return true;
}

static void mkdir(const char* path, uid_t owner, gid_t group, mode_t mode) {
    const char* fullPath = StringPrintf("/data/local/tmp/user/0/%s", path).c_str();
    ::mkdir(fullPath, mode);
    ::chown(fullPath, owner, group);
    ::chmod(fullPath, mode);
}

static void touch(const char* path, uid_t owner, gid_t group, mode_t mode) {
    int fd = ::open(StringPrintf("/data/local/tmp/user/0/%s", path).c_str(),
            O_RDWR | O_CREAT, mode);
    ::fchown(fd, owner, group);
    ::fchmod(fd, mode);
    ::close(fd);
}

static int stat_gid(const char* path) {
    struct stat buf;
    ::stat(StringPrintf("/data/local/tmp/user/0/%s", path).c_str(), &buf);
    return buf.st_gid;
}

static int stat_mode(const char* path) {
    struct stat buf;
    ::stat(StringPrintf("/data/local/tmp/user/0/%s", path).c_str(), &buf);
    return buf.st_mode & (S_IRWXU | S_IRWXG | S_IRWXO | S_ISGID);
}

class ServiceTest : public testing::Test {
protected:
    InstalldNativeService* service;
    std::unique_ptr<std::string> testUuid;

    virtual void SetUp() {
        setenv("ANDROID_LOG_TAGS", "*:v", 1);
        android::base::InitLogging(nullptr);

        service = new InstalldNativeService();
        testUuid = std::make_unique<std::string>();
        *testUuid = std::string(kTestUuid);
        system("mkdir -p /data/local/tmp/user/0");
    }

    virtual void TearDown() {
        delete service;
        system("rm -rf /data/local/tmp/user");
    }
};

TEST_F(ServiceTest, FixupAppData_Upgrade) {
    LOG(INFO) << "FixupAppData_Upgrade";

    mkdir("com.example", 10000, 10000, 0700);
    mkdir("com.example/normal", 10000, 10000, 0700);
    mkdir("com.example/cache", 10000, 10000, 0700);
    touch("com.example/cache/file", 10000, 10000, 0700);

    service->fixupAppData(testUuid, 0);

    EXPECT_EQ(10000, stat_gid("com.example/normal"));
    EXPECT_EQ(20000, stat_gid("com.example/cache"));
    EXPECT_EQ(20000, stat_gid("com.example/cache/file"));

    EXPECT_EQ(0700, stat_mode("com.example/normal"));
    EXPECT_EQ(02771, stat_mode("com.example/cache"));
    EXPECT_EQ(0700, stat_mode("com.example/cache/file"));
}

TEST_F(ServiceTest, FixupAppData_Moved) {
    LOG(INFO) << "FixupAppData_Moved";

    mkdir("com.example", 10000, 10000, 0700);
    mkdir("com.example/foo", 10000, 10000, 0700);
    touch("com.example/foo/file", 10000, 20000, 0700);
    mkdir("com.example/bar", 10000, 20000, 0700);
    touch("com.example/bar/file", 10000, 20000, 0700);

    service->fixupAppData(testUuid, 0);

    EXPECT_EQ(10000, stat_gid("com.example/foo"));
    EXPECT_EQ(20000, stat_gid("com.example/foo/file"));
    EXPECT_EQ(10000, stat_gid("com.example/bar"));
    EXPECT_EQ(10000, stat_gid("com.example/bar/file"));

    service->fixupAppData(testUuid, FLAG_FORCE);

    EXPECT_EQ(10000, stat_gid("com.example/foo"));
    EXPECT_EQ(10000, stat_gid("com.example/foo/file"));
    EXPECT_EQ(10000, stat_gid("com.example/bar"));
    EXPECT_EQ(10000, stat_gid("com.example/bar/file"));
}

TEST_F(ServiceTest, RmDexNoDalvikCache) {
    LOG(INFO) << "RmDexNoDalvikCache";

    // Try to remove a non existing dalvik cache dex. The call should be
    // successful because there's nothing to remove.
    EXPECT_TRUE(service->rmdex("com.example", "arm").isOk());
}

}  // namespace installd
}  // namespace android
