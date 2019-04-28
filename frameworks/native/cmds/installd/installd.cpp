/*
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
#define LOG_TAG "installd"

#include <fcntl.h>
#include <selinux/android.h>
#include <selinux/avc.h>
#include <sys/capability.h>
#include <sys/fsuid.h>
#include <sys/prctl.h>
#include <sys/stat.h>

#include <android-base/logging.h>
#include <cutils/fs.h>
#include <cutils/properties.h>
#include <log/log.h>              // TODO: Move everything to base::logging.
#include <private/android_filesystem_config.h>

#include "InstalldNativeService.h"
#include "globals.h"
#include "installd_constants.h"
#include "installd_deps.h"  // Need to fill in requirements of commands.
#include "utils.h"

namespace android {
namespace installd {

// Check that installd-deps sizes match cutils sizes.
static_assert(kPropertyKeyMax == PROPERTY_KEY_MAX, "Size mismatch.");
static_assert(kPropertyValueMax == PROPERTY_VALUE_MAX, "Size mismatch.");

////////////////////////
// Plug-in functions. //
////////////////////////

int get_property(const char *key, char *value, const char *default_value) {
    return property_get(key, value, default_value);
}

// Compute the output path of
bool calculate_oat_file_path(char path[PKG_PATH_MAX],
                             const char *oat_dir,
                             const char *apk_path,
                             const char *instruction_set) {
    const char *file_name_start;
    const char *file_name_end;

    file_name_start = strrchr(apk_path, '/');
    if (file_name_start == NULL) {
        SLOGE("apk_path '%s' has no '/'s in it\n", apk_path);
        return false;
    }
    file_name_end = strrchr(apk_path, '.');
    if (file_name_end < file_name_start) {
        SLOGE("apk_path '%s' has no extension\n", apk_path);
        return false;
    }

    // Calculate file_name
    int file_name_len = file_name_end - file_name_start - 1;
    char file_name[file_name_len + 1];
    memcpy(file_name, file_name_start + 1, file_name_len);
    file_name[file_name_len] = '\0';

    // <apk_parent_dir>/oat/<isa>/<file_name>.odex
    snprintf(path, PKG_PATH_MAX, "%s/%s/%s.odex", oat_dir, instruction_set, file_name);
    return true;
}

/*
 * Computes the odex file for the given apk_path and instruction_set.
 * /system/framework/whatever.jar -> /system/framework/oat/<isa>/whatever.odex
 *
 * Returns false if it failed to determine the odex file path.
 */
bool calculate_odex_file_path(char path[PKG_PATH_MAX],
                              const char *apk_path,
                              const char *instruction_set) {
    if (strlen(apk_path) + strlen("oat/") + strlen(instruction_set)
            + strlen("/") + strlen("odex") + 1 > PKG_PATH_MAX) {
        SLOGE("apk_path '%s' may be too long to form odex file path.\n", apk_path);
        return false;
    }

    strcpy(path, apk_path);
    char *end = strrchr(path, '/');
    if (end == NULL) {
        SLOGE("apk_path '%s' has no '/'s in it?!\n", apk_path);
        return false;
    }
    const char *apk_end = apk_path + (end - path); // strrchr(apk_path, '/');

    strcpy(end + 1, "oat/");       // path = /system/framework/oat/\0
    strcat(path, instruction_set); // path = /system/framework/oat/<isa>\0
    strcat(path, apk_end);         // path = /system/framework/oat/<isa>/whatever.jar\0
    end = strrchr(path, '.');
    if (end == NULL) {
        SLOGE("apk_path '%s' has no extension.\n", apk_path);
        return false;
    }
    strcpy(end + 1, "odex");
    return true;
}

bool create_cache_path(char path[PKG_PATH_MAX],
                       const char *src,
                       const char *instruction_set) {
    /* demand that we are an absolute path */
    if ((src == nullptr) || (src[0] != '/') || strstr(src,"..")) {
        return false;
    }

    size_t srclen = strlen(src);

    if (srclen > PKG_PATH_MAX) {        // XXX: PKG_NAME_MAX?
        return false;
    }

    size_t dstlen =
        android_data_dir.len +
        strlen(DALVIK_CACHE) +
        1 +
        strlen(instruction_set) +
        srclen +
        strlen(DALVIK_CACHE_POSTFIX) + 2;

    if (dstlen > PKG_PATH_MAX) {
        return false;
    }

    sprintf(path,"%s%s/%s/%s",
            android_data_dir.path,
            DALVIK_CACHE,
            instruction_set,
            src + 1 /* skip the leading / */);

    char* tmp =
            path +
            android_data_dir.len +
            strlen(DALVIK_CACHE) +
            1 +
            strlen(instruction_set) + 1;

    for(; *tmp; tmp++) {
        if (*tmp == '/') {
            *tmp = '@';
        }
    }

    strcat(path, DALVIK_CACHE_POSTFIX);
    return true;
}

static bool initialize_globals() {
    const char* data_path = getenv("ANDROID_DATA");
    if (data_path == nullptr) {
        SLOGE("Could not find ANDROID_DATA");
        return false;
    }
    const char* root_path = getenv("ANDROID_ROOT");
    if (root_path == nullptr) {
        SLOGE("Could not find ANDROID_ROOT");
        return false;
    }

    return init_globals_from_data_and_root(data_path, root_path);
}

static int initialize_directories() {
    int res = -1;

    // Read current filesystem layout version to handle upgrade paths
    char version_path[PATH_MAX];
    snprintf(version_path, PATH_MAX, "%s.layout_version", android_data_dir.path);

    int oldVersion;
    if (fs_read_atomic_int(version_path, &oldVersion) == -1) {
        oldVersion = 0;
    }
    int version = oldVersion;

    if (version < 2) {
        SLOGD("Assuming that device has multi-user storage layout; upgrade no longer supported");
        version = 2;
    }

    if (ensure_config_user_dirs(0) == -1) {
        SLOGE("Failed to setup misc for user 0");
        goto fail;
    }

    if (version == 2) {
        SLOGD("Upgrading to /data/misc/user directories");

        char misc_dir[PATH_MAX];
        snprintf(misc_dir, PATH_MAX, "%smisc", android_data_dir.path);

        char keychain_added_dir[PATH_MAX];
        snprintf(keychain_added_dir, PATH_MAX, "%s/keychain/cacerts-added", misc_dir);

        char keychain_removed_dir[PATH_MAX];
        snprintf(keychain_removed_dir, PATH_MAX, "%s/keychain/cacerts-removed", misc_dir);

        DIR *dir;
        struct dirent *dirent;
        dir = opendir("/data/user");
        if (dir != NULL) {
            while ((dirent = readdir(dir))) {
                const char *name = dirent->d_name;

                // skip "." and ".."
                if (name[0] == '.') {
                    if (name[1] == 0) continue;
                    if ((name[1] == '.') && (name[2] == 0)) continue;
                }

                uint32_t user_id = atoi(name);

                // /data/misc/user/<user_id>
                if (ensure_config_user_dirs(user_id) == -1) {
                    goto fail;
                }

                char misc_added_dir[PATH_MAX];
                snprintf(misc_added_dir, PATH_MAX, "%s/user/%s/cacerts-added", misc_dir, name);

                char misc_removed_dir[PATH_MAX];
                snprintf(misc_removed_dir, PATH_MAX, "%s/user/%s/cacerts-removed", misc_dir, name);

                uid_t uid = multiuser_get_uid(user_id, AID_SYSTEM);
                gid_t gid = uid;
                if (access(keychain_added_dir, F_OK) == 0) {
                    if (copy_dir_files(keychain_added_dir, misc_added_dir, uid, gid) != 0) {
                        SLOGE("Some files failed to copy");
                    }
                }
                if (access(keychain_removed_dir, F_OK) == 0) {
                    if (copy_dir_files(keychain_removed_dir, misc_removed_dir, uid, gid) != 0) {
                        SLOGE("Some files failed to copy");
                    }
                }
            }
            closedir(dir);

            if (access(keychain_added_dir, F_OK) == 0) {
                delete_dir_contents(keychain_added_dir, 1, 0);
            }
            if (access(keychain_removed_dir, F_OK) == 0) {
                delete_dir_contents(keychain_removed_dir, 1, 0);
            }
        }

        version = 3;
    }

    // Persist layout version if changed
    if (version != oldVersion) {
        if (fs_write_atomic_int(version_path, version) == -1) {
            SLOGE("Failed to save version to %s: %s", version_path, strerror(errno));
            goto fail;
        }
    }

    // Success!
    res = 0;

fail:
    return res;
}

static int log_callback(int type, const char *fmt, ...) {
    va_list ap;
    int priority;

    switch (type) {
    case SELINUX_WARNING:
        priority = ANDROID_LOG_WARN;
        break;
    case SELINUX_INFO:
        priority = ANDROID_LOG_INFO;
        break;
    default:
        priority = ANDROID_LOG_ERROR;
        break;
    }
    va_start(ap, fmt);
    LOG_PRI_VA(priority, "SELinux", fmt, ap);
    va_end(ap);
    return 0;
}

static int installd_main(const int argc ATTRIBUTE_UNUSED, char *argv[]) {
    int ret;
    int selinux_enabled = (is_selinux_enabled() > 0);

    setenv("ANDROID_LOG_TAGS", "*:v", 1);
    android::base::InitLogging(argv);

    SLOGI("installd firing up");

    union selinux_callback cb;
    cb.func_log = log_callback;
    selinux_set_callback(SELINUX_CB_LOG, cb);

    if (!initialize_globals()) {
        SLOGE("Could not initialize globals; exiting.\n");
        exit(1);
    }

    if (initialize_directories() < 0) {
        SLOGE("Could not create directories; exiting.\n");
        exit(1);
    }

    if (selinux_enabled && selinux_status_open(true) < 0) {
        SLOGE("Could not open selinux status; exiting.\n");
        exit(1);
    }

    if ((ret = InstalldNativeService::start()) != android::OK) {
        SLOGE("Unable to start InstalldNativeService: %d", ret);
        exit(1);
    }

    IPCThreadState::self()->joinThreadPool();

    LOG(INFO) << "installd shutting down";

    return 0;
}

}  // namespace installd
}  // namespace android

int main(const int argc, char *argv[]) {
    return android::installd::installd_main(argc, argv);
}
