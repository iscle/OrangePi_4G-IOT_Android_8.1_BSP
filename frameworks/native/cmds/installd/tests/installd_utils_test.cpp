/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "InstalldNativeService.h"
#include "globals.h"
#include "utils.h"

#undef LOG_TAG
#define LOG_TAG "utils_test"

#define TEST_DATA_DIR "/data/"
#define TEST_APP_DIR "/data/app/"
#define TEST_APP_PRIVATE_DIR "/data/app-private/"
#define TEST_APP_EPHEMERAL_DIR "/data/app-ephemeral/"
#define TEST_ASEC_DIR "/mnt/asec/"
#define TEST_EXPAND_DIR "/mnt/expand/"

#define TEST_SYSTEM_DIR1 "/system/app/"
#define TEST_SYSTEM_DIR2 "/vendor/app/"

#define TEST_PROFILE_DIR "/data/misc/profiles"

namespace android {
namespace installd {

class UtilsTest : public testing::Test {
protected:
    virtual void SetUp() {
        android_app_dir.path = (char*) TEST_APP_DIR;
        android_app_dir.len = strlen(TEST_APP_DIR);

        android_app_private_dir.path = (char*) TEST_APP_PRIVATE_DIR;
        android_app_private_dir.len = strlen(TEST_APP_PRIVATE_DIR);

        android_app_ephemeral_dir.path = (char*) TEST_APP_EPHEMERAL_DIR;
        android_app_ephemeral_dir.len = strlen(TEST_APP_EPHEMERAL_DIR);

        android_data_dir.path = (char*) TEST_DATA_DIR;
        android_data_dir.len = strlen(TEST_DATA_DIR);

        android_asec_dir.path = (char*) TEST_ASEC_DIR;
        android_asec_dir.len = strlen(TEST_ASEC_DIR);

        android_mnt_expand_dir.path = (char*) TEST_EXPAND_DIR;
        android_mnt_expand_dir.len = strlen(TEST_EXPAND_DIR);

        android_system_dirs.count = 2;

        android_system_dirs.dirs = (dir_rec_t*) calloc(android_system_dirs.count, sizeof(dir_rec_t));
        android_system_dirs.dirs[0].path = (char*) TEST_SYSTEM_DIR1;
        android_system_dirs.dirs[0].len = strlen(TEST_SYSTEM_DIR1);

        android_system_dirs.dirs[1].path = (char*) TEST_SYSTEM_DIR2;
        android_system_dirs.dirs[1].len = strlen(TEST_SYSTEM_DIR2);

        android_profiles_dir.path = (char*) TEST_PROFILE_DIR;
        android_profiles_dir.len = strlen(TEST_PROFILE_DIR);
    }

    virtual void TearDown() {
        free(android_system_dirs.dirs);
    }

    std::string create_too_long_path(const std::string& seed) {
        std::string result = seed;
        for (size_t i = seed.size(); i < PKG_PATH_MAX; i++) {
            result += "a";
        }
        return result;
    }
};

TEST_F(UtilsTest, IsValidApkPath_BadPrefix) {
    // Bad prefixes directories
    const char *badprefix1 = "/etc/passwd";
    EXPECT_EQ(-1, validate_apk_path(badprefix1))
            << badprefix1 << " should not be allowed as a valid path";

    const char *badprefix2 = "../.." TEST_APP_DIR "../../../blah";
    EXPECT_EQ(-1, validate_apk_path(badprefix2))
            << badprefix2 << " should not be allowed as a valid path";

    const char *badprefix3 = "init.rc";
    EXPECT_EQ(-1, validate_apk_path(badprefix3))
            << badprefix3 << " should not be allowed as a valid path";

    const char *badprefix4 = "/init.rc";
    EXPECT_EQ(-1, validate_apk_path(badprefix4))
            << badprefix4 << " should not be allowed as a valid path";
}

TEST_F(UtilsTest, IsValidApkPath_Internal) {
    // Internal directories
    const char *internal1 = TEST_APP_DIR "example.apk";
    EXPECT_EQ(0, validate_apk_path(internal1))
            << internal1 << " should be allowed as a valid path";

    // b/16888084
    const char *path2 = TEST_APP_DIR "example.com/example.apk";
    EXPECT_EQ(0, validate_apk_path(path2))
            << path2 << " should be allowed as a valid path";

    const char *badint1 = TEST_APP_DIR "../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badint1))
            << badint1 << " should be rejected as a invalid path";

    const char *badint2 = TEST_APP_DIR "/../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badint2))
            << badint2 << " should be rejected as a invalid path";

    // Only one subdir should be allowed.
    const char *bad_path3 = TEST_APP_DIR "example.com/subdir/pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path3))
            << bad_path3 << " should be rejected as a invalid path";

    const char *bad_path4 = TEST_APP_DIR "example.com/subdir/../pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path4))
            << bad_path4 << " should be rejected as a invalid path";

    const char *bad_path5 = TEST_APP_DIR "example.com1/../example.com2/pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path5))
            << bad_path5 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_Private) {
    // Internal directories
    const char *private1 = TEST_APP_PRIVATE_DIR "example.apk";
    EXPECT_EQ(0, validate_apk_path(private1))
            << private1 << " should be allowed as a valid path";

    // b/16888084
    const char *path2 = TEST_APP_DIR "example.com/example.apk";
    EXPECT_EQ(0, validate_apk_path(path2))
            << path2 << " should be allowed as a valid path";

    const char *badpriv1 = TEST_APP_PRIVATE_DIR "../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badpriv1))
            << badpriv1 << " should be rejected as a invalid path";

    const char *badpriv2 = TEST_APP_PRIVATE_DIR "/../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badpriv2))
            << badpriv2 << " should be rejected as a invalid path";

    // Only one subdir should be allowed.
    const char *bad_path3 = TEST_APP_PRIVATE_DIR "example.com/subdir/pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path3))
            << bad_path3 << " should be rejected as a invalid path";

    const char *bad_path4 = TEST_APP_PRIVATE_DIR "example.com/subdir/../pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path4))
            << bad_path4 << " should be rejected as a invalid path";

    const char *bad_path5 = TEST_APP_PRIVATE_DIR "example.com1/../example.com2/pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(bad_path5))
            << bad_path5 << " should be rejected as a invalid path";
}


TEST_F(UtilsTest, IsValidApkPath_AsecGood1) {
    const char *asec1 = TEST_ASEC_DIR "example.apk";
    EXPECT_EQ(0, validate_apk_path(asec1))
            << asec1 << " should be allowed as a valid path";
}

TEST_F(UtilsTest, IsValidApkPath_AsecGood2) {
    const char *asec2 = TEST_ASEC_DIR "com.example.asec/pkg.apk";
    EXPECT_EQ(0, validate_apk_path(asec2))
            << asec2 << " should be allowed as a valid path";
}

TEST_F(UtilsTest, IsValidApkPath_EscapeFail) {
    const char *badasec1 = TEST_ASEC_DIR "../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec1))
            << badasec1 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_DoubleSlashFail) {
    const char *badasec2 = TEST_ASEC_DIR "com.example.asec//pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec2))
            << badasec2 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_SubdirEscapeFail) {
    const char *badasec3 = TEST_ASEC_DIR "com.example.asec/../../../pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec3))
            << badasec3  << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_SlashEscapeFail) {
    const char *badasec4 = TEST_ASEC_DIR "/../example.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec4))
            << badasec4 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_CrazyDirFail) {
    const char *badasec5 = TEST_ASEC_DIR ".//../..";
    EXPECT_EQ(-1, validate_apk_path(badasec5))
            << badasec5 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_SubdirEscapeSingleFail) {
    const char *badasec6 = TEST_ASEC_DIR "com.example.asec/../pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec6))
            << badasec6 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, IsValidApkPath_TwoSubdirFail) {
    const char *badasec7 = TEST_ASEC_DIR "com.example.asec/subdir1/pkg.apk";
    EXPECT_EQ(-1, validate_apk_path(badasec7))
            << badasec7 << " should be rejected as a invalid path";
}

TEST_F(UtilsTest, CheckSystemApp_Dir1) {
    const char *sysapp1 = TEST_SYSTEM_DIR1 "Voice.apk";
    EXPECT_EQ(0, validate_system_app_path(sysapp1))
            << sysapp1 << " should be allowed as a system path";
}

TEST_F(UtilsTest, CheckSystemApp_Dir2) {
    const char *sysapp2 = TEST_SYSTEM_DIR2 "com.example.myapp.apk";
    EXPECT_EQ(0, validate_system_app_path(sysapp2))
            << sysapp2 << " should be allowed as a system path";
}

TEST_F(UtilsTest, CheckSystemApp_EscapeFail) {
    const char *badapp1 = TEST_SYSTEM_DIR1 "../com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp1))
            << badapp1 << " should be rejected not a system path";
}

TEST_F(UtilsTest, CheckSystemApp_DoubleEscapeFail) {
    const char *badapp2 = TEST_SYSTEM_DIR2 "/../../com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp2))
            << badapp2 << " should be rejected not a system path";
}

TEST_F(UtilsTest, CheckSystemApp_BadPathEscapeFail) {
    const char *badapp3 = TEST_APP_DIR "/../../com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp3))
            << badapp3 << " should be rejected not a system path";
}

TEST_F(UtilsTest, CheckSystemApp_Subdir) {
    const char *sysapp = TEST_SYSTEM_DIR1 "com.example/com.example.apk";
    EXPECT_EQ(0, validate_system_app_path(sysapp))
            << sysapp << " should be allowed as a system path";

    const char *badapp = TEST_SYSTEM_DIR1 "com.example/subdir/com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp))
            << badapp << " should be rejected not a system path";

    const char *badapp1 = TEST_SYSTEM_DIR1 "com.example/subdir/../com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp1))
            << badapp1 << " should be rejected not a system path";

    const char *badapp2 = TEST_SYSTEM_DIR1 "com.example1/../com.example2/com.example.apk";
    EXPECT_EQ(-1, validate_system_app_path(badapp2))
            << badapp2 << " should be rejected not a system path";
}

TEST_F(UtilsTest, GetPathFromString_NullPathFail) {
    dir_rec_t test1;
    EXPECT_EQ(-1, get_path_from_string(&test1, (const char *) NULL))
            << "Should not allow NULL as a path.";
}

TEST_F(UtilsTest, GetPathFromString_EmptyPathFail) {
    dir_rec_t test1;
    EXPECT_EQ(-1, get_path_from_string(&test1, ""))
            << "Should not allow empty paths.";
}

TEST_F(UtilsTest, GetPathFromString_RelativePathFail) {
    dir_rec_t test1;
    EXPECT_EQ(-1, get_path_from_string(&test1, "mnt/asec"))
            << "Should not allow relative paths.";
}

TEST_F(UtilsTest, GetPathFromString_NonCanonical) {
    dir_rec_t test1;

    EXPECT_EQ(0, get_path_from_string(&test1, "/mnt/asec"))
            << "Should be able to canonicalize directory /mnt/asec";
    EXPECT_STREQ("/mnt/asec/", test1.path)
            << "/mnt/asec should be canonicalized to /mnt/asec/";
    EXPECT_EQ(10, (ssize_t) test1.len)
            << "path len should be equal to the length of /mnt/asec/ (10)";
    free(test1.path);
}

TEST_F(UtilsTest, GetPathFromString_CanonicalPath) {
    dir_rec_t test3;
    EXPECT_EQ(0, get_path_from_string(&test3, "/data/app/"))
            << "Should be able to canonicalize directory /data/app/";
    EXPECT_STREQ("/data/app/", test3.path)
            << "/data/app/ should be canonicalized to /data/app/";
    EXPECT_EQ(10, (ssize_t) test3.len)
            << "path len should be equal to the length of /data/app/ (10)";
    free(test3.path);
}

TEST_F(UtilsTest, CreatePkgPath_LongPkgNameSuccess) {
    char path[PKG_PATH_MAX];

    // Create long packagename of "aaaaa..."
    size_t pkgnameSize = PKG_NAME_MAX;
    char pkgname[pkgnameSize + 1];
    memset(pkgname, 'a', pkgnameSize);
    pkgname[1] = '.';
    pkgname[pkgnameSize] = '\0';

    EXPECT_EQ(0, create_pkg_path(path, pkgname, "", 0))
            << "Should successfully be able to create package name.";

    std::string prefix = std::string(TEST_DATA_DIR) + PRIMARY_USER_PREFIX;
    size_t offset = prefix.length();

    EXPECT_STREQ(pkgname, path + offset)
             << "Package path should be a really long string of a's";
}

TEST_F(UtilsTest, CreatePkgPath_LongPostfixFail) {
    char path[PKG_PATH_MAX];

    // Create long packagename of "aaaaa..."
    size_t postfixSize = PKG_PATH_MAX;
    char postfix[postfixSize + 1];
    memset(postfix, 'a', postfixSize);
    postfix[postfixSize] = '\0';

    EXPECT_EQ(-1, create_pkg_path(path, "com.example.package", postfix, 0))
            << "Should return error because postfix is too long.";
}

TEST_F(UtilsTest, CreatePkgPath_PrimaryUser) {
    char path[PKG_PATH_MAX];

    EXPECT_EQ(0, create_pkg_path(path, "com.example.package", "", 0))
            << "Should return error because postfix is too long.";

    std::string p = std::string(TEST_DATA_DIR)
                    + PRIMARY_USER_PREFIX
                    + "com.example.package";
    EXPECT_STREQ(p.c_str(), path)
            << "Package path should be in /data/data/";
}

TEST_F(UtilsTest, CreatePkgPath_SecondaryUser) {
    char path[PKG_PATH_MAX];

    EXPECT_EQ(0, create_pkg_path(path, "com.example.package", "", 1))
            << "Should successfully create package path.";

    std::string p = std::string(TEST_DATA_DIR)
                    + SECONDARY_USER_PREFIX
                    + "1/com.example.package";
    EXPECT_STREQ(p.c_str(), path)
            << "Package path should be in /data/user/";
}

TEST_F(UtilsTest, CreateMovePath_Primary) {
    char path[PKG_PATH_MAX];

    EXPECT_EQ(0, create_move_path(path, "com.android.test", "shared_prefs", 0))
            << "Should be able to create move path for primary user";

    EXPECT_STREQ("/data/data/com.android.test/shared_prefs", path)
            << "Primary user package directory should be created correctly";
}


TEST_F(UtilsTest, CreateMovePath_Fail_AppTooLong) {
    char path[PKG_PATH_MAX];
    std::string really_long_app_name = create_too_long_path("com.example");
    EXPECT_EQ(-1, create_move_path(path, really_long_app_name.c_str(), "shared_prefs", 0))
            << "Should fail to create move path for primary user";
}

TEST_F(UtilsTest, CreateMovePath_Fail_LeafTooLong) {
    char path[PKG_PATH_MAX];
    std::string really_long_leaf_name = create_too_long_path("leaf_");
    EXPECT_EQ(-1, create_move_path(path, "com.android.test", really_long_leaf_name.c_str(), 0))
            << "Should fail to create move path for primary user";
}

TEST_F(UtilsTest, CopyAndAppend_Normal) {
    //int copy_and_append(dir_rec_t* dst, dir_rec_t* src, char* suffix)
    dir_rec_t dst;
    dir_rec_t src;

    src.path = (char*) "/data/";
    src.len = strlen(src.path);

    EXPECT_EQ(0, copy_and_append(&dst, &src, "app/"))
            << "Should return error because postfix is too long.";

    EXPECT_STREQ("/data/app/", dst.path)
            << "Appended path should be correct";

    EXPECT_EQ(10, (ssize_t) dst.len)
            << "Appended path should be length of '/data/app/' (10)";
}

TEST_F(UtilsTest, AppendAndIncrement_Normal) {
    size_t dst_size = 10;
    char dst[dst_size];
    char *dstp = dst;
    const char* src = "FOO";

    EXPECT_EQ(0, append_and_increment(&dstp, src, &dst_size))
            << "String should append successfully";

    EXPECT_STREQ("FOO", dst)
            << "String should append correctly";

    EXPECT_EQ(0, append_and_increment(&dstp, src, &dst_size))
            << "String should append successfully again";

    EXPECT_STREQ("FOOFOO", dst)
            << "String should append correctly again";
}

TEST_F(UtilsTest, AppendAndIncrement_TooBig) {
    size_t dst_size = 5;
    char dst[dst_size];
    char *dstp = dst;
    const char* src = "FOO";

    EXPECT_EQ(0, append_and_increment(&dstp, src, &dst_size))
            << "String should append successfully";

    EXPECT_STREQ("FOO", dst)
            << "String should append correctly";

    EXPECT_EQ(-1, append_and_increment(&dstp, src, &dst_size))
            << "String should fail because it's too large to fit";
}

TEST_F(UtilsTest, CreateDataPath) {
    EXPECT_EQ("/data", create_data_path(nullptr));
    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b",
            create_data_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b"));
}

TEST_F(UtilsTest, CreateDataAppPath) {
    EXPECT_EQ("/data/app", create_data_app_path(nullptr));

    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/app",
            create_data_app_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b"));
}

TEST_F(UtilsTest, CreateDataUserPath) {
    EXPECT_EQ("/data/data", create_data_user_ce_path(nullptr, 0));
    EXPECT_EQ("/data/user/10", create_data_user_ce_path(nullptr, 10));

    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/user/0",
            create_data_user_ce_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 0));
    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/user/10",
            create_data_user_ce_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 10));
}

TEST_F(UtilsTest, CreateDataMediaPath) {
    EXPECT_EQ("/data/media/0", create_data_media_path(nullptr, 0));
    EXPECT_EQ("/data/media/10", create_data_media_path(nullptr, 10));

    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/media/0",
            create_data_media_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 0));
    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/media/10",
            create_data_media_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 10));
}

TEST_F(UtilsTest, CreateDataAppPackagePath) {
    EXPECT_EQ("/data/app/com.example", create_data_app_package_path(nullptr, "com.example"));

    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/app/com.example",
            create_data_app_package_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", "com.example"));
}

TEST_F(UtilsTest, CreateDataUserPackagePath) {
    EXPECT_EQ("/data/data/com.example", create_data_user_ce_package_path(nullptr, 0, "com.example"));
    EXPECT_EQ("/data/user/10/com.example", create_data_user_ce_package_path(nullptr, 10, "com.example"));

    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/user/0/com.example",
            create_data_user_ce_package_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 0, "com.example"));
    EXPECT_EQ("/mnt/expand/57f8f4bc-abf4-655f-bf67-946fc0f9f25b/user/10/com.example",
            create_data_user_ce_package_path("57f8f4bc-abf4-655f-bf67-946fc0f9f25b", 10, "com.example"));
}

TEST_F(UtilsTest, IsValidPackageName) {
    EXPECT_EQ(true, is_valid_package_name("android"));
    EXPECT_EQ(true, is_valid_package_name("com.example"));
    EXPECT_EQ(true, is_valid_package_name("com.example-1"));
    EXPECT_EQ(true, is_valid_package_name("com.example-1024"));
    EXPECT_EQ(true, is_valid_package_name("com.example.foo---KiJFj4a_tePVw95pSrjg=="));
    EXPECT_EQ(true, is_valid_package_name("really_LONG.a1234.package_name"));

    EXPECT_EQ(false, is_valid_package_name("1234.package"));
    EXPECT_EQ(false, is_valid_package_name("com.1234.package"));
    EXPECT_EQ(false, is_valid_package_name(""));
    EXPECT_EQ(false, is_valid_package_name("."));
    EXPECT_EQ(false, is_valid_package_name(".."));
    EXPECT_EQ(false, is_valid_package_name("../"));
    EXPECT_EQ(false, is_valid_package_name("com.example/../com.evil/"));
    EXPECT_EQ(false, is_valid_package_name("com.example-1/../com.evil/"));
    EXPECT_EQ(false, is_valid_package_name("/com.evil"));
}

TEST_F(UtilsTest, CreateDataUserProfilePath) {
    EXPECT_EQ("/data/misc/profiles/cur/0", create_primary_cur_profile_dir_path(0));
    EXPECT_EQ("/data/misc/profiles/cur/1", create_primary_cur_profile_dir_path(1));
}

TEST_F(UtilsTest, CreateDataUserProfilePackagePath) {
    EXPECT_EQ("/data/misc/profiles/cur/0/com.example",
            create_primary_current_profile_package_dir_path(0, "com.example"));
    EXPECT_EQ("/data/misc/profiles/cur/1/com.example",
            create_primary_current_profile_package_dir_path(1, "com.example"));
}

TEST_F(UtilsTest, CreateDataRefProfilePath) {
    EXPECT_EQ("/data/misc/profiles/ref", create_primary_ref_profile_dir_path());
}

TEST_F(UtilsTest, CreateDataRefProfilePackagePath) {
    EXPECT_EQ("/data/misc/profiles/ref/com.example",
        create_primary_reference_profile_package_dir_path("com.example"));
}

TEST_F(UtilsTest, CreatePrimaryCurrentProfile) {
    std::string expected =
        create_primary_current_profile_package_dir_path(0, "com.example") + "/primary.prof";
    EXPECT_EQ(expected,
            create_current_profile_path(/*user*/0, "com.example", /*is_secondary*/false));
}

TEST_F(UtilsTest, CreatePrimaryReferenceProfile) {
    std::string expected =
        create_primary_reference_profile_package_dir_path("com.example") + "/primary.prof";
    EXPECT_EQ(expected,
            create_reference_profile_path("com.example", /*is_secondary*/false));
}

TEST_F(UtilsTest, CreateSecondaryCurrentProfile) {
    EXPECT_EQ("/data/user/0/com.example/oat/secondary.dex.cur.prof",
            create_current_profile_path(/*user*/0,
                    "/data/user/0/com.example/secondary.dex", /*is_secondary*/true));
}

TEST_F(UtilsTest, CreateSecondaryReferenceProfile) {
    EXPECT_EQ("/data/user/0/com.example/oat/secondary.dex.prof",
            create_reference_profile_path(
                    "/data/user/0/com.example/secondary.dex", /*is_secondary*/true));
}

static void pass_secondary_dex_validation(const std::string& package_name,
        const std::string& dex_path, int uid, int storage_flag) {
    EXPECT_TRUE(validate_secondary_dex_path(package_name, dex_path, /*volume_uuid*/ nullptr, uid,
            storage_flag))
            << dex_path << " should be allowed as a valid secondary dex path";
}

static void fail_secondary_dex_validation(const std::string& package_name,
        const std::string& dex_path, int uid, int storage_flag) {
    EXPECT_FALSE(validate_secondary_dex_path(package_name, dex_path, /*volume_uuid*/ nullptr, uid,
            storage_flag))
            << dex_path << " should not be allowed as a valid secondary dex path";
}

TEST_F(UtilsTest, ValidateSecondaryDexFilesPath) {
    std::string package_name = "com.test.app";
    std::string app_dir_ce_user_0 = "/data/data/" + package_name;
    std::string app_dir_ce_user_10 = "/data/user/10/" + package_name;

    std::string app_dir_de_user_0 = "/data/user_de/0/" + package_name;
    std::string app_dir_de_user_10 = "/data/user_de/10/" + package_name;

    EXPECT_EQ(app_dir_ce_user_0,
            create_data_user_ce_package_path(nullptr, 0, package_name.c_str()));
    EXPECT_EQ(app_dir_ce_user_10,
            create_data_user_ce_package_path(nullptr, 10, package_name.c_str()));

    EXPECT_EQ(app_dir_de_user_0,
            create_data_user_de_package_path(nullptr, 0, package_name.c_str()));
    EXPECT_EQ(app_dir_de_user_10,
            create_data_user_de_package_path(nullptr, 10, package_name.c_str()));

    uid_t app_uid_for_user_0 = multiuser_get_uid(/*user_id*/0, /*app_id*/ 1234);
    uid_t app_uid_for_user_10 = multiuser_get_uid(/*user_id*/10, /*app_id*/ 1234);

    // Standard path for user 0 on CE storage.
    pass_secondary_dex_validation(
        package_name, app_dir_ce_user_0 + "/ce0.dex", app_uid_for_user_0, FLAG_STORAGE_CE);
    // Standard path for user 10 on CE storage.
    pass_secondary_dex_validation(
        package_name, app_dir_ce_user_10 + "/ce10.dex", app_uid_for_user_10, FLAG_STORAGE_CE);

    // Standard path for user 0 on DE storage.
    pass_secondary_dex_validation(
        package_name, app_dir_de_user_0 + "/de0.dex", app_uid_for_user_0, FLAG_STORAGE_DE);
    // Standard path for user 10 on DE storage.
    pass_secondary_dex_validation(
        package_name, app_dir_de_user_10 + "/de0.dex", app_uid_for_user_10, FLAG_STORAGE_DE);

    // Dex path for user 0 accessed from user 10.
    fail_secondary_dex_validation(
        package_name, app_dir_ce_user_0 + "/path0_from10.dex",
        app_uid_for_user_10, FLAG_STORAGE_CE);

    // Dex path for CE storage accessed with DE.
    fail_secondary_dex_validation(
        package_name, app_dir_ce_user_0 + "/ce_from_de.dex", app_uid_for_user_0, FLAG_STORAGE_DE);

    // Dex path for DE storage accessed with CE.
    fail_secondary_dex_validation(
        package_name, app_dir_de_user_0 + "/de_from_ce.dex", app_uid_for_user_0, FLAG_STORAGE_CE);

    // Location which does not start with '/'.
    fail_secondary_dex_validation(
        package_name, "without_slash.dex", app_uid_for_user_10, FLAG_STORAGE_DE);

    // The dex file is not in the specified package directory.
    fail_secondary_dex_validation(
        "another.package", app_dir_ce_user_0 + "/for_another_package.dex",
        app_uid_for_user_0, FLAG_STORAGE_DE);

    // The dex path contains indirect directories.
    fail_secondary_dex_validation(
        package_name, app_dir_ce_user_0 + "/1/../foo.dex", app_uid_for_user_0, FLAG_STORAGE_CE);
    fail_secondary_dex_validation(
        package_name, app_dir_ce_user_0 + "/1/./foo.dex", app_uid_for_user_0, FLAG_STORAGE_CE);

    // Super long path.
    std::string too_long = create_too_long_path("too_long_");
    fail_secondary_dex_validation(
        package_name, app_dir_ce_user_10 + "/" + too_long, app_uid_for_user_10, FLAG_STORAGE_CE);
}

}  // namespace installd
}  // namespace android
