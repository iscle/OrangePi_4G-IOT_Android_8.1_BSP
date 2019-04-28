/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef UTILS_H_
#define UTILS_H_

#include <string>
#include <vector>

#include <dirent.h>
#include <inttypes.h>
#include <unistd.h>
#include <utime.h>

#include <cutils/multiuser.h>

#include <installd_constants.h>

#define MEASURE_DEBUG 0
#define FIXUP_DEBUG 0

#define BYPASS_QUOTA 0
#define BYPASS_SDCARDFS 0

#define APPLY_HARD_QUOTAS 1

namespace android {
namespace installd {

struct dir_rec_t;

constexpr const char* kXattrInodeCache = "user.inode_cache";
constexpr const char* kXattrInodeCodeCache = "user.inode_code_cache";
constexpr const char* kXattrCacheGroup = "user.cache_group";
constexpr const char* kXattrCacheTombstone = "user.cache_tombstone";

int create_pkg_path(char path[PKG_PATH_MAX],
                    const char *pkgname,
                    const char *postfix,
                    userid_t userid);

std::string create_data_path(const char* volume_uuid);

std::string create_data_app_path(const char* volume_uuid);
std::string create_data_app_package_path(const char* volume_uuid, const char* package_name);

std::string create_data_user_ce_path(const char* volume_uuid, userid_t userid);
std::string create_data_user_de_path(const char* volume_uuid, userid_t userid);

std::string create_data_user_ce_package_path(const char* volume_uuid,
        userid_t user, const char* package_name);
std::string create_data_user_ce_package_path(const char* volume_uuid,
        userid_t user, const char* package_name, ino_t ce_data_inode);
std::string create_data_user_de_package_path(const char* volume_uuid,
        userid_t user, const char* package_name);

std::string create_data_media_path(const char* volume_uuid, userid_t userid);
std::string create_data_media_obb_path(const char* volume_uuid, const char* package_name);
std::string create_data_media_package_path(const char* volume_uuid, userid_t userid,
        const char* data_type, const char* package_name);

std::string create_data_misc_legacy_path(userid_t userid);

std::string create_data_dalvik_cache_path();

std::string create_primary_cur_profile_dir_path(userid_t userid);
std::string create_primary_current_profile_package_dir_path(
        userid_t user, const std::string& package_name);

std::string create_primary_ref_profile_dir_path();
std::string create_primary_reference_profile_package_dir_path(const std::string& package_name);

std::string create_current_profile_path(
        userid_t user, const std::string& package_name, bool is_secondary_dex);
std::string create_reference_profile_path(
        const std::string& package_name, bool is_secondary_dex);

std::vector<userid_t> get_known_users(const char* volume_uuid);

int calculate_tree_size(const std::string& path, int64_t* size,
        int32_t include_gid = -1, int32_t exclude_gid = -1, bool exclude_apps = false);

int create_user_config_path(char path[PKG_PATH_MAX], userid_t userid);

int create_move_path(char path[PKG_PATH_MAX],
                     const char* pkgname,
                     const char* leaf,
                     userid_t userid);

bool is_valid_filename(const std::string& name);
bool is_valid_package_name(const std::string& packageName);

int delete_dir_contents(const std::string& pathname, bool ignore_if_missing = false);
int delete_dir_contents_and_dir(const std::string& pathname, bool ignore_if_missing = false);

int delete_dir_contents(const char *pathname,
                        int also_delete_dir,
                        int (*exclusion_predicate)(const char *name, const int is_dir),
                        bool ignore_if_missing = false);

int delete_dir_contents_fd(int dfd, const char *name);

int copy_dir_files(const char *srcname, const char *dstname, uid_t owner, gid_t group);

int64_t data_disk_free(const std::string& data_path);

int get_path_inode(const std::string& path, ino_t *inode);

int write_path_inode(const std::string& parent, const char* name, const char* inode_xattr);
std::string read_path_inode(const std::string& parent, const char* name, const char* inode_xattr);

int validate_system_app_path(const char* path);
bool validate_secondary_dex_path(const std::string& pkgname, const std::string& dex_path,
        const char* volume_uuid, int uid, int storage_flag, bool validate_package_path = true);

int get_path_from_env(dir_rec_t* rec, const char* var);

int get_path_from_string(dir_rec_t* rec, const char* path);

int copy_and_append(dir_rec_t* dst, const dir_rec_t* src, const char* suffix);

int validate_apk_path(const char *path);
int validate_apk_path_subdirs(const char *path);

int append_and_increment(char** dst, const char* src, size_t* dst_size);

char *build_string2(const char *s1, const char *s2);
char *build_string3(const char *s1, const char *s2, const char *s3);

int ensure_config_user_dirs(userid_t userid);

int wait_child(pid_t pid);

int prepare_app_cache_dir(const std::string& parent, const char* name, mode_t target_mode,
        uid_t uid, gid_t gid);

}  // namespace installd
}  // namespace android

#endif  // UTILS_H_
