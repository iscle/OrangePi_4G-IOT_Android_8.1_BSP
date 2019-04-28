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

#include "InstalldNativeService.h"

#define ATRACE_TAG ATRACE_TAG_PACKAGE_MANAGER

#include <errno.h>
#include <inttypes.h>
#include <fstream>
#include <fts.h>
#include <regex>
#include <stdlib.h>
#include <string.h>
#include <sys/capability.h>
#include <sys/file.h>
#include <sys/resource.h>
#include <sys/quota.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <unistd.h>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <cutils/fs.h>
#include <cutils/properties.h>
#include <cutils/sched_policy.h>
#include <log/log.h>               // TODO: Move everything to base/logging.
#include <logwrap/logwrap.h>
#include <private/android_filesystem_config.h>
#include <selinux/android.h>
#include <system/thread_defs.h>
#include <utils/Trace.h>

#include "dexopt.h"
#include "globals.h"
#include "installd_deps.h"
#include "otapreopt_utils.h"
#include "utils.h"

#include "CacheTracker.h"
#include "MatchExtensionGen.h"

#ifndef LOG_TAG
#define LOG_TAG "installd"
#endif

using android::base::StringPrintf;
using std::endl;

namespace android {
namespace installd {

static constexpr const char* kCpPath = "/system/bin/cp";
static constexpr const char* kXattrDefault = "user.default";

static constexpr const int MIN_RESTRICTED_HOME_SDK_VERSION = 24; // > M

static constexpr const char* PKG_LIB_POSTFIX = "/lib";
static constexpr const char* CACHE_DIR_POSTFIX = "/cache";
static constexpr const char* CODE_CACHE_DIR_POSTFIX = "/code_cache";

static constexpr const char *kIdMapPath = "/system/bin/idmap";
static constexpr const char* IDMAP_PREFIX = "/data/resource-cache/";
static constexpr const char* IDMAP_SUFFIX = "@idmap";

// NOTE: keep in sync with Installer
static constexpr int FLAG_CLEAR_CACHE_ONLY = 1 << 8;
static constexpr int FLAG_CLEAR_CODE_CACHE_ONLY = 1 << 9;
static constexpr int FLAG_USE_QUOTA = 1 << 12;
static constexpr int FLAG_FREE_CACHE_V2 = 1 << 13;
static constexpr int FLAG_FREE_CACHE_V2_DEFY_QUOTA = 1 << 14;
static constexpr int FLAG_FREE_CACHE_NOOP = 1 << 15;
static constexpr int FLAG_FORCE = 1 << 16;

namespace {

constexpr const char* kDump = "android.permission.DUMP";

static binder::Status ok() {
    return binder::Status::ok();
}

static binder::Status exception(uint32_t code, const std::string& msg) {
    return binder::Status::fromExceptionCode(code, String8(msg.c_str()));
}

static binder::Status error() {
    return binder::Status::fromServiceSpecificError(errno);
}

static binder::Status error(const std::string& msg) {
    PLOG(ERROR) << msg;
    return binder::Status::fromServiceSpecificError(errno, String8(msg.c_str()));
}

static binder::Status error(uint32_t code, const std::string& msg) {
    LOG(ERROR) << msg << " (" << code << ")";
    return binder::Status::fromServiceSpecificError(code, String8(msg.c_str()));
}

binder::Status checkPermission(const char* permission) {
    pid_t pid;
    uid_t uid;

    if (checkCallingPermission(String16(permission), reinterpret_cast<int32_t*>(&pid),
            reinterpret_cast<int32_t*>(&uid))) {
        return ok();
    } else {
        return exception(binder::Status::EX_SECURITY,
                StringPrintf("UID %d / PID %d lacks permission %s", uid, pid, permission));
    }
}

binder::Status checkUid(uid_t expectedUid) {
    uid_t uid = IPCThreadState::self()->getCallingUid();
    if (uid == expectedUid || uid == AID_ROOT) {
        return ok();
    } else {
        return exception(binder::Status::EX_SECURITY,
                StringPrintf("UID %d is not expected UID %d", uid, expectedUid));
    }
}

binder::Status checkArgumentUuid(const std::unique_ptr<std::string>& uuid) {
    if (!uuid || is_valid_filename(*uuid)) {
        return ok();
    } else {
        return exception(binder::Status::EX_ILLEGAL_ARGUMENT,
                StringPrintf("UUID %s is malformed", uuid->c_str()));
    }
}

binder::Status checkArgumentPackageName(const std::string& packageName) {
    if (is_valid_package_name(packageName.c_str())) {
        return ok();
    } else {
        return exception(binder::Status::EX_ILLEGAL_ARGUMENT,
                StringPrintf("Package name %s is malformed", packageName.c_str()));
    }
}

#define ENFORCE_UID(uid) {                                  \
    binder::Status status = checkUid((uid));                \
    if (!status.isOk()) {                                   \
        return status;                                      \
    }                                                       \
}

#define CHECK_ARGUMENT_UUID(uuid) {                         \
    binder::Status status = checkArgumentUuid((uuid));      \
    if (!status.isOk()) {                                   \
        return status;                                      \
    }                                                       \
}

#define CHECK_ARGUMENT_PACKAGE_NAME(packageName) {          \
    binder::Status status =                                 \
            checkArgumentPackageName((packageName));        \
    if (!status.isOk()) {                                   \
        return status;                                      \
    }                                                       \
}

}  // namespace

status_t InstalldNativeService::start() {
    IPCThreadState::self()->disableBackgroundScheduling(true);
    status_t ret = BinderService<InstalldNativeService>::publish();
    if (ret != android::OK) {
        return ret;
    }
    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();
    ps->giveThreadPoolName();
    return android::OK;
}

status_t InstalldNativeService::dump(int fd, const Vector<String16> & /* args */) {
    auto out = std::fstream(StringPrintf("/proc/self/fd/%d", fd));
    const binder::Status dump_permission = checkPermission(kDump);
    if (!dump_permission.isOk()) {
        out << dump_permission.toString8() << endl;
        return PERMISSION_DENIED;
    }
    std::lock_guard<std::recursive_mutex> lock(mLock);

    out << "installd is happy!" << endl;

    {
        std::lock_guard<std::recursive_mutex> lock(mMountsLock);
        out << endl << "Storage mounts:" << endl;
        for (const auto& n : mStorageMounts) {
            out << "    " << n.first << " = " << n.second << endl;
        }

        out << endl << "Quota reverse mounts:" << endl;
        for (const auto& n : mQuotaReverseMounts) {
            out << "    " << n.first << " = " << n.second << endl;
        }
    }

    {
        std::lock_guard<std::recursive_mutex> lock(mQuotasLock);
        out << endl << "Per-UID cache quotas:" << endl;
        for (const auto& n : mCacheQuotas) {
            out << "    " << n.first << " = " << n.second << endl;
        }
    }

    out << endl;
    out.flush();

    return NO_ERROR;
}

/**
 * Perform restorecon of the given path, but only perform recursive restorecon
 * if the label of that top-level file actually changed.  This can save us
 * significant time by avoiding no-op traversals of large filesystem trees.
 */
static int restorecon_app_data_lazy(const std::string& path, const std::string& seInfo, uid_t uid,
        bool existing) {
    int res = 0;
    char* before = nullptr;
    char* after = nullptr;

    // Note that SELINUX_ANDROID_RESTORECON_DATADATA flag is set by
    // libselinux. Not needed here.

    if (lgetfilecon(path.c_str(), &before) < 0) {
        PLOG(ERROR) << "Failed before getfilecon for " << path;
        goto fail;
    }
    if (selinux_android_restorecon_pkgdir(path.c_str(), seInfo.c_str(), uid, 0) < 0) {
        PLOG(ERROR) << "Failed top-level restorecon for " << path;
        goto fail;
    }
    if (lgetfilecon(path.c_str(), &after) < 0) {
        PLOG(ERROR) << "Failed after getfilecon for " << path;
        goto fail;
    }

    // If the initial top-level restorecon above changed the label, then go
    // back and restorecon everything recursively
    if (strcmp(before, after)) {
        if (existing) {
            LOG(DEBUG) << "Detected label change from " << before << " to " << after << " at "
                    << path << "; running recursive restorecon";
        }
        if (selinux_android_restorecon_pkgdir(path.c_str(), seInfo.c_str(), uid,
                SELINUX_ANDROID_RESTORECON_RECURSE) < 0) {
            PLOG(ERROR) << "Failed recursive restorecon for " << path;
            goto fail;
        }
    }

    goto done;
fail:
    res = -1;
done:
    free(before);
    free(after);
    return res;
}

static int restorecon_app_data_lazy(const std::string& parent, const char* name,
        const std::string& seInfo, uid_t uid, bool existing) {
    return restorecon_app_data_lazy(StringPrintf("%s/%s", parent.c_str(), name), seInfo, uid,
            existing);
}

static int prepare_app_dir(const std::string& path, mode_t target_mode, uid_t uid) {
    if (fs_prepare_dir_strict(path.c_str(), target_mode, uid, uid) != 0) {
        PLOG(ERROR) << "Failed to prepare " << path;
        return -1;
    }
    return 0;
}

/**
 * Ensure that we have a hard-limit quota to protect against abusive apps;
 * they should never use more than 90% of blocks or 50% of inodes.
 */
static int prepare_app_quota(const std::unique_ptr<std::string>& uuid, const std::string& device,
        uid_t uid) {
    if (device.empty()) return 0;

    struct dqblk dq;
    if (quotactl(QCMD(Q_GETQUOTA, USRQUOTA), device.c_str(), uid,
            reinterpret_cast<char*>(&dq)) != 0) {
        PLOG(WARNING) << "Failed to find quota for " << uid;
        return -1;
    }

#if APPLY_HARD_QUOTAS
    if ((dq.dqb_bhardlimit == 0) || (dq.dqb_ihardlimit == 0)) {
        auto path = create_data_path(uuid ? uuid->c_str() : nullptr);
        struct statvfs stat;
        if (statvfs(path.c_str(), &stat) != 0) {
            PLOG(WARNING) << "Failed to statvfs " << path;
            return -1;
        }

        dq.dqb_valid = QIF_LIMITS;
        dq.dqb_bhardlimit =
            (((static_cast<uint64_t>(stat.f_blocks) * stat.f_frsize) / 10) * 9) / QIF_DQBLKSIZE;
        dq.dqb_ihardlimit = (stat.f_files / 2);
        if (quotactl(QCMD(Q_SETQUOTA, USRQUOTA), device.c_str(), uid,
                reinterpret_cast<char*>(&dq)) != 0) {
            PLOG(WARNING) << "Failed to set hard quota for " << uid;
            return -1;
        } else {
            LOG(DEBUG) << "Applied hard quotas for " << uid;
            return 0;
        }
    } else {
        // Hard quota already set; assume it's reasonable
        return 0;
    }
#else
    // Hard quotas disabled
    return 0;
#endif
}

binder::Status InstalldNativeService::createAppData(const std::unique_ptr<std::string>& uuid,
        const std::string& packageName, int32_t userId, int32_t flags, int32_t appId,
        const std::string& seInfo, int32_t targetSdkVersion, int64_t* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgname = packageName.c_str();

    // Assume invalid inode unless filled in below
    if (_aidl_return != nullptr) *_aidl_return = -1;

    int32_t uid = multiuser_get_uid(userId, appId);
    int32_t cacheGid = multiuser_get_cache_gid(userId, appId);
    mode_t targetMode = targetSdkVersion >= MIN_RESTRICTED_HOME_SDK_VERSION ? 0700 : 0751;

    // If UID doesn't have a specific cache GID, use UID value
    if (cacheGid == -1) {
        cacheGid = uid;
    }

    if (flags & FLAG_STORAGE_CE) {
        auto path = create_data_user_ce_package_path(uuid_, userId, pkgname);
        bool existing = (access(path.c_str(), F_OK) == 0);

        if (prepare_app_dir(path, targetMode, uid) ||
                prepare_app_cache_dir(path, "cache", 02771, uid, cacheGid) ||
                prepare_app_cache_dir(path, "code_cache", 02771, uid, cacheGid)) {
            return error("Failed to prepare " + path);
        }

        // Consider restorecon over contents if label changed
        if (restorecon_app_data_lazy(path, seInfo, uid, existing) ||
                restorecon_app_data_lazy(path, "cache", seInfo, uid, existing) ||
                restorecon_app_data_lazy(path, "code_cache", seInfo, uid, existing)) {
            return error("Failed to restorecon " + path);
        }

        // Remember inode numbers of cache directories so that we can clear
        // contents while CE storage is locked
        if (write_path_inode(path, "cache", kXattrInodeCache) ||
                write_path_inode(path, "code_cache", kXattrInodeCodeCache)) {
            return error("Failed to write_path_inode for " + path);
        }

        // And return the CE inode of the top-level data directory so we can
        // clear contents while CE storage is locked
        if ((_aidl_return != nullptr)
                && get_path_inode(path, reinterpret_cast<ino_t*>(_aidl_return)) != 0) {
            return error("Failed to get_path_inode for " + path);
        }
    }
    if (flags & FLAG_STORAGE_DE) {
        auto path = create_data_user_de_package_path(uuid_, userId, pkgname);
        bool existing = (access(path.c_str(), F_OK) == 0);

        if (prepare_app_dir(path, targetMode, uid) ||
                prepare_app_cache_dir(path, "cache", 02771, uid, cacheGid) ||
                prepare_app_cache_dir(path, "code_cache", 02771, uid, cacheGid)) {
            return error("Failed to prepare " + path);
        }

        // Consider restorecon over contents if label changed
        if (restorecon_app_data_lazy(path, seInfo, uid, existing) ||
                restorecon_app_data_lazy(path, "cache", seInfo, uid, existing) ||
                restorecon_app_data_lazy(path, "code_cache", seInfo, uid, existing)) {
            return error("Failed to restorecon " + path);
        }

        if (prepare_app_quota(uuid, findQuotaDeviceForUuid(uuid), uid)) {
            return error("Failed to set hard quota " + path);
        }

        if (property_get_bool("dalvik.vm.usejitprofiles", false)) {
            const std::string profile_dir =
                    create_primary_current_profile_package_dir_path(userId, pkgname);
            // read-write-execute only for the app user.
            if (fs_prepare_dir_strict(profile_dir.c_str(), 0700, uid, uid) != 0) {
                return error("Failed to prepare " + profile_dir);
            }
            const std::string profile_file = create_current_profile_path(userId, pkgname,
                    /*is_secondary_dex*/false);
            // read-write only for the app user.
            if (fs_prepare_file_strict(profile_file.c_str(), 0600, uid, uid) != 0) {
                return error("Failed to prepare " + profile_file);
            }
            const std::string ref_profile_path =
                    create_primary_reference_profile_package_dir_path(pkgname);
            // dex2oat/profman runs under the shared app gid and it needs to read/write reference
            // profiles.
            int shared_app_gid = multiuser_get_shared_gid(0, appId);
            if ((shared_app_gid != -1) && fs_prepare_dir_strict(
                    ref_profile_path.c_str(), 0700, shared_app_gid, shared_app_gid) != 0) {
                return error("Failed to prepare " + ref_profile_path);
            }
        }
    }
    return ok();
}

binder::Status InstalldNativeService::migrateAppData(const std::unique_ptr<std::string>& uuid,
        const std::string& packageName, int32_t userId, int32_t flags) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgname = packageName.c_str();

    // This method only exists to upgrade system apps that have requested
    // forceDeviceEncrypted, so their default storage always lives in a
    // consistent location.  This only works on non-FBE devices, since we
    // never want to risk exposing data on a device with real CE/DE storage.

    auto ce_path = create_data_user_ce_package_path(uuid_, userId, pkgname);
    auto de_path = create_data_user_de_package_path(uuid_, userId, pkgname);

    // If neither directory is marked as default, assume CE is default
    if (getxattr(ce_path.c_str(), kXattrDefault, nullptr, 0) == -1
            && getxattr(de_path.c_str(), kXattrDefault, nullptr, 0) == -1) {
        if (setxattr(ce_path.c_str(), kXattrDefault, nullptr, 0, 0) != 0) {
            return error("Failed to mark default storage " + ce_path);
        }
    }

    // Migrate default data location if needed
    auto target = (flags & FLAG_STORAGE_DE) ? de_path : ce_path;
    auto source = (flags & FLAG_STORAGE_DE) ? ce_path : de_path;

    if (getxattr(target.c_str(), kXattrDefault, nullptr, 0) == -1) {
        LOG(WARNING) << "Requested default storage " << target
                << " is not active; migrating from " << source;
        if (delete_dir_contents_and_dir(target) != 0) {
            return error("Failed to delete " + target);
        }
        if (rename(source.c_str(), target.c_str()) != 0) {
            return error("Failed to rename " + source + " to " + target);
        }
    }

    return ok();
}


binder::Status InstalldNativeService::clearAppProfiles(const std::string& packageName) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    binder::Status res = ok();
    if (!clear_primary_reference_profile(packageName)) {
        res = error("Failed to clear reference profile for " + packageName);
    }
    if (!clear_primary_current_profiles(packageName)) {
        res = error("Failed to clear current profiles for " + packageName);
    }
    return res;
}

binder::Status InstalldNativeService::clearAppData(const std::unique_ptr<std::string>& uuid,
        const std::string& packageName, int32_t userId, int32_t flags, int64_t ceDataInode) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgname = packageName.c_str();

    binder::Status res = ok();
    if (flags & FLAG_STORAGE_CE) {
        auto path = create_data_user_ce_package_path(uuid_, userId, pkgname, ceDataInode);
        if (flags & FLAG_CLEAR_CACHE_ONLY) {
            path = read_path_inode(path, "cache", kXattrInodeCache);
        } else if (flags & FLAG_CLEAR_CODE_CACHE_ONLY) {
            path = read_path_inode(path, "code_cache", kXattrInodeCodeCache);
        }
        if (access(path.c_str(), F_OK) == 0) {
            if (delete_dir_contents(path) != 0) {
                res = error("Failed to delete contents of " + path);
            }
        }
    }
    if (flags & FLAG_STORAGE_DE) {
        std::string suffix = "";
        bool only_cache = false;
        if (flags & FLAG_CLEAR_CACHE_ONLY) {
            suffix = CACHE_DIR_POSTFIX;
            only_cache = true;
        } else if (flags & FLAG_CLEAR_CODE_CACHE_ONLY) {
            suffix = CODE_CACHE_DIR_POSTFIX;
            only_cache = true;
        }

        auto path = create_data_user_de_package_path(uuid_, userId, pkgname) + suffix;
        if (access(path.c_str(), F_OK) == 0) {
            if (delete_dir_contents(path) != 0) {
                res = error("Failed to delete contents of " + path);
            }
        }
        if (!only_cache) {
            if (!clear_primary_current_profile(packageName, userId)) {
                res = error("Failed to clear current profile for " + packageName);
            }
        }
    }
    return res;
}

static int destroy_app_reference_profile(const std::string& pkgname) {
    return delete_dir_contents_and_dir(
        create_primary_reference_profile_package_dir_path(pkgname),
        /*ignore_if_missing*/ true);
}

static int destroy_app_current_profiles(const std::string& pkgname, userid_t userid) {
    return delete_dir_contents_and_dir(
        create_primary_current_profile_package_dir_path(userid, pkgname),
        /*ignore_if_missing*/ true);
}

binder::Status InstalldNativeService::destroyAppProfiles(const std::string& packageName) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    binder::Status res = ok();
    std::vector<userid_t> users = get_known_users(/*volume_uuid*/ nullptr);
    for (auto user : users) {
        if (destroy_app_current_profiles(packageName, user) != 0) {
            res = error("Failed to destroy current profiles for " + packageName);
        }
    }
    if (destroy_app_reference_profile(packageName) != 0) {
        res = error("Failed to destroy reference profile for " + packageName);
    }
    return res;
}

binder::Status InstalldNativeService::destroyAppData(const std::unique_ptr<std::string>& uuid,
        const std::string& packageName, int32_t userId, int32_t flags, int64_t ceDataInode) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgname = packageName.c_str();

    binder::Status res = ok();
    if (flags & FLAG_STORAGE_CE) {
        auto path = create_data_user_ce_package_path(uuid_, userId, pkgname, ceDataInode);
        if (delete_dir_contents_and_dir(path) != 0) {
            res = error("Failed to delete " + path);
        }
    }
    if (flags & FLAG_STORAGE_DE) {
        auto path = create_data_user_de_package_path(uuid_, userId, pkgname);
        if (delete_dir_contents_and_dir(path) != 0) {
            res = error("Failed to delete " + path);
        }
        destroy_app_current_profiles(packageName, userId);
        // TODO(calin): If the package is still installed by other users it's probably
        // beneficial to keep the reference profile around.
        // Verify if it's ok to do that.
        destroy_app_reference_profile(packageName);
    }
    return res;
}

static gid_t get_cache_gid(uid_t uid) {
    int32_t gid = multiuser_get_cache_gid(multiuser_get_user_id(uid), multiuser_get_app_id(uid));
    return (gid != -1) ? gid : uid;
}

binder::Status InstalldNativeService::fixupAppData(const std::unique_ptr<std::string>& uuid,
        int32_t flags) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    for (auto user : get_known_users(uuid_)) {
        ATRACE_BEGIN("fixup user");
        FTS* fts;
        FTSENT* p;
        auto ce_path = create_data_user_ce_path(uuid_, user);
        auto de_path = create_data_user_de_path(uuid_, user);
        char *argv[] = { (char*) ce_path.c_str(), (char*) de_path.c_str(), nullptr };
        if (!(fts = fts_open(argv, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, NULL))) {
            return error("Failed to fts_open");
        }
        while ((p = fts_read(fts)) != nullptr) {
            if (p->fts_info == FTS_D && p->fts_level == 1) {
                // Track down inodes of cache directories
                uint64_t raw = 0;
                ino_t inode_cache = 0;
                ino_t inode_code_cache = 0;
                if (getxattr(p->fts_path, kXattrInodeCache, &raw, sizeof(raw)) == sizeof(raw)) {
                    inode_cache = raw;
                }
                if (getxattr(p->fts_path, kXattrInodeCodeCache, &raw, sizeof(raw)) == sizeof(raw)) {
                    inode_code_cache = raw;
                }

                // Figure out expected GID of each child
                FTSENT* child = fts_children(fts, 0);
                while (child != nullptr) {
                    if ((child->fts_statp->st_ino == inode_cache)
                            || (child->fts_statp->st_ino == inode_code_cache)
                            || !strcmp(child->fts_name, "cache")
                            || !strcmp(child->fts_name, "code_cache")) {
                        child->fts_number = get_cache_gid(p->fts_statp->st_uid);
                    } else {
                        child->fts_number = p->fts_statp->st_uid;
                    }
                    child = child->fts_link;
                }
            } else if (p->fts_level >= 2) {
                if (p->fts_level > 2) {
                    // Inherit GID from parent once we're deeper into tree
                    p->fts_number = p->fts_parent->fts_number;
                }

                uid_t uid = p->fts_parent->fts_statp->st_uid;
                gid_t cache_gid = get_cache_gid(uid);
                gid_t expected = p->fts_number;
                gid_t actual = p->fts_statp->st_gid;
                if (actual == expected) {
#if FIXUP_DEBUG
                    LOG(DEBUG) << "Ignoring " << p->fts_path << " with expected GID " << expected;
#endif
                    if (!(flags & FLAG_FORCE)) {
                        fts_set(fts, p, FTS_SKIP);
                    }
                } else if ((actual == uid) || (actual == cache_gid)) {
                    // Only consider fixing up when current GID belongs to app
                    if (p->fts_info != FTS_D) {
                        LOG(INFO) << "Fixing " << p->fts_path << " with unexpected GID " << actual
                                << " instead of " << expected;
                    }
                    switch (p->fts_info) {
                    case FTS_DP:
                        // If we're moving towards cache GID, we need to set S_ISGID
                        if (expected == cache_gid) {
                            if (chmod(p->fts_path, 02771) != 0) {
                                PLOG(WARNING) << "Failed to chmod " << p->fts_path;
                            }
                        }
                        // Intentional fall through to also set GID
                    case FTS_F:
                        if (chown(p->fts_path, -1, expected) != 0) {
                            PLOG(WARNING) << "Failed to chown " << p->fts_path;
                        }
                        break;
                    case FTS_SL:
                    case FTS_SLNONE:
                        if (lchown(p->fts_path, -1, expected) != 0) {
                            PLOG(WARNING) << "Failed to chown " << p->fts_path;
                        }
                        break;
                    }
                } else {
                    // Ignore all other GID transitions, since they're kinda shady
                    LOG(WARNING) << "Ignoring " << p->fts_path << " with unexpected GID " << actual
                            << " instead of " << expected;
                }
            }
        }
        fts_close(fts);
        ATRACE_END();
    }
    return ok();
}

binder::Status InstalldNativeService::moveCompleteApp(const std::unique_ptr<std::string>& fromUuid,
        const std::unique_ptr<std::string>& toUuid, const std::string& packageName,
        const std::string& dataAppName, int32_t appId, const std::string& seInfo,
        int32_t targetSdkVersion) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(fromUuid);
    CHECK_ARGUMENT_UUID(toUuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* from_uuid = fromUuid ? fromUuid->c_str() : nullptr;
    const char* to_uuid = toUuid ? toUuid->c_str() : nullptr;
    const char* package_name = packageName.c_str();
    const char* data_app_name = dataAppName.c_str();

    binder::Status res = ok();
    std::vector<userid_t> users = get_known_users(from_uuid);

    // Copy app
    {
        auto from = create_data_app_package_path(from_uuid, data_app_name);
        auto to = create_data_app_package_path(to_uuid, data_app_name);
        auto to_parent = create_data_app_path(to_uuid);

        char *argv[] = {
            (char*) kCpPath,
            (char*) "-F", /* delete any existing destination file first (--remove-destination) */
            (char*) "-p", /* preserve timestamps, ownership, and permissions */
            (char*) "-R", /* recurse into subdirectories (DEST must be a directory) */
            (char*) "-P", /* Do not follow symlinks [default] */
            (char*) "-d", /* don't dereference symlinks */
            (char*) from.c_str(),
            (char*) to_parent.c_str()
        };

        LOG(DEBUG) << "Copying " << from << " to " << to;
        int rc = android_fork_execvp(ARRAY_SIZE(argv), argv, NULL, false, true);
        if (rc != 0) {
            res = error(rc, "Failed copying " + from + " to " + to);
            goto fail;
        }

        if (selinux_android_restorecon(to.c_str(), SELINUX_ANDROID_RESTORECON_RECURSE) != 0) {
            res = error("Failed to restorecon " + to);
            goto fail;
        }
    }

    // Copy private data for all known users
    for (auto user : users) {

        // Data source may not exist for all users; that's okay
        auto from_ce = create_data_user_ce_package_path(from_uuid, user, package_name);
        if (access(from_ce.c_str(), F_OK) != 0) {
            LOG(INFO) << "Missing source " << from_ce;
            continue;
        }

        if (!createAppData(toUuid, packageName, user, FLAG_STORAGE_CE | FLAG_STORAGE_DE, appId,
                seInfo, targetSdkVersion, nullptr).isOk()) {
            res = error("Failed to create package target");
            goto fail;
        }

        char *argv[] = {
            (char*) kCpPath,
            (char*) "-F", /* delete any existing destination file first (--remove-destination) */
            (char*) "-p", /* preserve timestamps, ownership, and permissions */
            (char*) "-R", /* recurse into subdirectories (DEST must be a directory) */
            (char*) "-P", /* Do not follow symlinks [default] */
            (char*) "-d", /* don't dereference symlinks */
            nullptr,
            nullptr
        };

        {
            auto from = create_data_user_de_package_path(from_uuid, user, package_name);
            auto to = create_data_user_de_path(to_uuid, user);
            argv[6] = (char*) from.c_str();
            argv[7] = (char*) to.c_str();

            LOG(DEBUG) << "Copying " << from << " to " << to;
            int rc = android_fork_execvp(ARRAY_SIZE(argv), argv, NULL, false, true);
            if (rc != 0) {
                res = error(rc, "Failed copying " + from + " to " + to);
                goto fail;
            }
        }
        {
            auto from = create_data_user_ce_package_path(from_uuid, user, package_name);
            auto to = create_data_user_ce_path(to_uuid, user);
            argv[6] = (char*) from.c_str();
            argv[7] = (char*) to.c_str();

            LOG(DEBUG) << "Copying " << from << " to " << to;
            int rc = android_fork_execvp(ARRAY_SIZE(argv), argv, NULL, false, true);
            if (rc != 0) {
                res = error(rc, "Failed copying " + from + " to " + to);
                goto fail;
            }
        }

        if (!restoreconAppData(toUuid, packageName, user, FLAG_STORAGE_CE | FLAG_STORAGE_DE,
                appId, seInfo).isOk()) {
            res = error("Failed to restorecon");
            goto fail;
        }
    }

    // We let the framework scan the new location and persist that before
    // deleting the data in the old location; this ordering ensures that
    // we can recover from things like battery pulls.
    return ok();

fail:
    // Nuke everything we might have already copied
    {
        auto to = create_data_app_package_path(to_uuid, data_app_name);
        if (delete_dir_contents(to.c_str(), 1, NULL) != 0) {
            LOG(WARNING) << "Failed to rollback " << to;
        }
    }
    for (auto user : users) {
        {
            auto to = create_data_user_de_package_path(to_uuid, user, package_name);
            if (delete_dir_contents(to.c_str(), 1, NULL) != 0) {
                LOG(WARNING) << "Failed to rollback " << to;
            }
        }
        {
            auto to = create_data_user_ce_package_path(to_uuid, user, package_name);
            if (delete_dir_contents(to.c_str(), 1, NULL) != 0) {
                LOG(WARNING) << "Failed to rollback " << to;
            }
        }
    }
    return res;
}

binder::Status InstalldNativeService::createUserData(const std::unique_ptr<std::string>& uuid,
        int32_t userId, int32_t userSerial ATTRIBUTE_UNUSED, int32_t flags) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    if (flags & FLAG_STORAGE_DE) {
        if (uuid_ == nullptr) {
            if (ensure_config_user_dirs(userId) != 0) {
                return error(StringPrintf("Failed to ensure dirs for %d", userId));
            }
        }
    }

    // Data under /data/media doesn't have an app, but we still want
    // to limit it to prevent abuse.
    if (prepare_app_quota(uuid, findQuotaDeviceForUuid(uuid),
            multiuser_get_uid(userId, AID_MEDIA_RW))) {
        return error("Failed to set hard quota for media_rw");
    }

    return ok();
}

binder::Status InstalldNativeService::destroyUserData(const std::unique_ptr<std::string>& uuid,
        int32_t userId, int32_t flags) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    binder::Status res = ok();
    if (flags & FLAG_STORAGE_DE) {
        auto path = create_data_user_de_path(uuid_, userId);
        if (delete_dir_contents_and_dir(path, true) != 0) {
            res = error("Failed to delete " + path);
        }
        if (uuid_ == nullptr) {
            path = create_data_misc_legacy_path(userId);
            if (delete_dir_contents_and_dir(path, true) != 0) {
                res = error("Failed to delete " + path);
            }
            path = create_primary_cur_profile_dir_path(userId);
            if (delete_dir_contents_and_dir(path, true) != 0) {
                res = error("Failed to delete " + path);
            }
        }
    }
    if (flags & FLAG_STORAGE_CE) {
        auto path = create_data_user_ce_path(uuid_, userId);
        if (delete_dir_contents_and_dir(path, true) != 0) {
            res = error("Failed to delete " + path);
        }
        path = findDataMediaPath(uuid, userId);
        if (delete_dir_contents_and_dir(path, true) != 0) {
            res = error("Failed to delete " + path);
        }
    }
    return res;
}

binder::Status InstalldNativeService::freeCache(const std::unique_ptr<std::string>& uuid,
        int64_t targetFreeBytes, int64_t cacheReservedBytes, int32_t flags) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    auto data_path = create_data_path(uuid_);
    auto device = findQuotaDeviceForUuid(uuid);
    auto noop = (flags & FLAG_FREE_CACHE_NOOP);

    int64_t free = data_disk_free(data_path);
    if (free < 0) {
        return error("Failed to determine free space for " + data_path);
    }

    int64_t cleared = 0;
    int64_t needed = targetFreeBytes - free;
    LOG(DEBUG) << "Device " << data_path << " has " << free << " free; requested "
            << targetFreeBytes << "; needed " << needed;

    if (free >= targetFreeBytes) {
        return ok();
    }

    if (flags & FLAG_FREE_CACHE_V2) {
        // This new cache strategy fairly removes files from UIDs by deleting
        // files from the UIDs which are most over their allocated quota

        // 1. Create trackers for every known UID
        ATRACE_BEGIN("create");
        std::unordered_map<uid_t, std::shared_ptr<CacheTracker>> trackers;
        for (auto user : get_known_users(uuid_)) {
            FTS *fts;
            FTSENT *p;
            auto ce_path = create_data_user_ce_path(uuid_, user);
            auto de_path = create_data_user_de_path(uuid_, user);
            auto media_path = findDataMediaPath(uuid, user) + "/Android/data/";
            char *argv[] = { (char*) ce_path.c_str(), (char*) de_path.c_str(),
                    (char*) media_path.c_str(), nullptr };
            if (!(fts = fts_open(argv, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, NULL))) {
                return error("Failed to fts_open");
            }
            while ((p = fts_read(fts)) != NULL) {
                if (p->fts_info == FTS_D && p->fts_level == 1) {
                    uid_t uid = p->fts_statp->st_uid;
                    if (multiuser_get_app_id(uid) == AID_MEDIA_RW) {
                        uid = (multiuser_get_app_id(p->fts_statp->st_gid) - AID_EXT_GID_START)
                                + AID_APP_START;
                    }
                    auto search = trackers.find(uid);
                    if (search != trackers.end()) {
                        search->second->addDataPath(p->fts_path);
                    } else {
                        auto tracker = std::shared_ptr<CacheTracker>(new CacheTracker(
                                multiuser_get_user_id(uid), multiuser_get_app_id(uid), device));
                        tracker->addDataPath(p->fts_path);
                        {
                            std::lock_guard<std::recursive_mutex> lock(mQuotasLock);
                            tracker->cacheQuota = mCacheQuotas[uid];
                        }
                        if (tracker->cacheQuota == 0) {
#if MEASURE_DEBUG
                            LOG(WARNING) << "UID " << uid << " has no cache quota; assuming 64MB";
#endif
                            tracker->cacheQuota = 67108864;
                        }
                        trackers[uid] = tracker;
                    }
                    fts_set(fts, p, FTS_SKIP);
                }
            }
            fts_close(fts);
        }
        ATRACE_END();

        // 2. Populate tracker stats and insert into priority queue
        ATRACE_BEGIN("populate");
        int64_t cacheTotal = 0;
        auto cmp = [](std::shared_ptr<CacheTracker> left, std::shared_ptr<CacheTracker> right) {
            return (left->getCacheRatio() < right->getCacheRatio());
        };
        std::priority_queue<std::shared_ptr<CacheTracker>,
                std::vector<std::shared_ptr<CacheTracker>>, decltype(cmp)> queue(cmp);
        for (const auto& it : trackers) {
            it.second->loadStats();
            queue.push(it.second);
            cacheTotal += it.second->cacheUsed;
        }
        ATRACE_END();

        // 3. Bounce across the queue, freeing items from whichever tracker is
        // the most over their assigned quota
        ATRACE_BEGIN("bounce");
        std::shared_ptr<CacheTracker> active;
        while (active || !queue.empty()) {
            // Only look at apps under quota when explicitly requested
            if (active && (active->getCacheRatio() < 10000)
                    && !(flags & FLAG_FREE_CACHE_V2_DEFY_QUOTA)) {
                LOG(DEBUG) << "Active ratio " << active->getCacheRatio()
                        << " isn't over quota, and defy not requested";
                break;
            }

            // Only keep clearing when we haven't pushed into reserved area
            if (cacheReservedBytes > 0 && cleared >= (cacheTotal - cacheReservedBytes)) {
                LOG(DEBUG) << "Refusing to clear cached data in reserved space";
                break;
            }

            // Find the best tracker to work with; this might involve swapping
            // if the active tracker is no longer the most over quota
            bool nextBetter = active && !queue.empty()
                    && active->getCacheRatio() < queue.top()->getCacheRatio();
            if (!active || nextBetter) {
                if (active) {
                    // Current tracker still has items, so we'll consider it
                    // again later once it bubbles up to surface
                    queue.push(active);
                }
                active = queue.top(); queue.pop();
                active->ensureItems();
                continue;
            }

            // If no items remain, go find another tracker
            if (active->items.empty()) {
                active = nullptr;
                continue;
            } else {
                auto item = active->items.back();
                active->items.pop_back();

                LOG(DEBUG) << "Purging " << item->toString() << " from " << active->toString();
                if (!noop) {
                    item->purge();
                }
                active->cacheUsed -= item->size;
                needed -= item->size;
                cleared += item->size;
            }

            // Verify that we're actually done before bailing, since sneaky
            // apps might be using hardlinks
            if (needed <= 0) {
                free = data_disk_free(data_path);
                needed = targetFreeBytes - free;
                if (needed <= 0) {
                    break;
                } else {
                    LOG(WARNING) << "Expected to be done but still need " << needed;
                }
            }
        }
        ATRACE_END();

    } else {
        return error("Legacy cache logic no longer supported");
    }

    free = data_disk_free(data_path);
    if (free >= targetFreeBytes) {
        return ok();
    } else {
        return error(StringPrintf("Failed to free up %" PRId64 " on %s; final free space %" PRId64,
                targetFreeBytes, data_path.c_str(), free));
    }
}

binder::Status InstalldNativeService::rmdex(const std::string& codePath,
        const std::string& instructionSet) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    char dex_path[PKG_PATH_MAX];

    const char* path = codePath.c_str();
    const char* instruction_set = instructionSet.c_str();

    if (validate_apk_path(path) && validate_system_app_path(path)) {
        return error("Invalid path " + codePath);
    }

    if (!create_cache_path(dex_path, path, instruction_set)) {
        return error("Failed to create cache path for " + codePath);
    }

    ALOGV("unlink %s\n", dex_path);
    if (unlink(dex_path) < 0) {
        // It's ok if we don't have a dalvik cache path. Report error only when the path exists
        // but could not be unlinked.
        if (errno != ENOENT) {
            return error(StringPrintf("Failed to unlink %s", dex_path));
        }
    }
    return ok();
}

struct stats {
    int64_t codeSize;
    int64_t dataSize;
    int64_t cacheSize;
};

#if MEASURE_DEBUG
static std::string toString(std::vector<int64_t> values) {
    std::stringstream res;
    res << "[";
    for (size_t i = 0; i < values.size(); i++) {
        res << values[i];
        if (i < values.size() - 1) {
            res << ",";
        }
    }
    res << "]";
    return res.str();
}
#endif

static void collectQuotaStats(const std::string& device, int32_t userId,
        int32_t appId, struct stats* stats, struct stats* extStats) {
    if (device.empty()) return;

    struct dqblk dq;

    if (stats != nullptr) {
        uid_t uid = multiuser_get_uid(userId, appId);
        if (quotactl(QCMD(Q_GETQUOTA, USRQUOTA), device.c_str(), uid,
                reinterpret_cast<char*>(&dq)) != 0) {
            if (errno != ESRCH) {
                PLOG(ERROR) << "Failed to quotactl " << device << " for UID " << uid;
            }
        } else {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for UID " << uid << " " << dq.dqb_curspace;
#endif
            stats->dataSize += dq.dqb_curspace;
        }

        int cacheGid = multiuser_get_cache_gid(userId, appId);
        if (cacheGid != -1) {
            if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), cacheGid,
                    reinterpret_cast<char*>(&dq)) != 0) {
                if (errno != ESRCH) {
                    PLOG(ERROR) << "Failed to quotactl " << device << " for GID " << cacheGid;
                }
            } else {
#if MEASURE_DEBUG
                LOG(DEBUG) << "quotactl() for GID " << cacheGid << " " << dq.dqb_curspace;
#endif
                stats->cacheSize += dq.dqb_curspace;
            }
        }

        int sharedGid = multiuser_get_shared_gid(0, appId);
        if (sharedGid != -1) {
            if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), sharedGid,
                    reinterpret_cast<char*>(&dq)) != 0) {
                if (errno != ESRCH) {
                    PLOG(ERROR) << "Failed to quotactl " << device << " for GID " << sharedGid;
                }
            } else {
#if MEASURE_DEBUG
                LOG(DEBUG) << "quotactl() for GID " << sharedGid << " " << dq.dqb_curspace;
#endif
                stats->codeSize += dq.dqb_curspace;
            }
        }
    }

    if (extStats != nullptr) {
        int extGid = multiuser_get_ext_gid(userId, appId);
        if (extGid != -1) {
            if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), extGid,
                    reinterpret_cast<char*>(&dq)) != 0) {
                if (errno != ESRCH) {
                    PLOG(ERROR) << "Failed to quotactl " << device << " for GID " << extGid;
                }
            } else {
#if MEASURE_DEBUG
                LOG(DEBUG) << "quotactl() for GID " << extGid << " " << dq.dqb_curspace;
#endif
                extStats->dataSize += dq.dqb_curspace;
            }
        }

        int extCacheGid = multiuser_get_ext_cache_gid(userId, appId);
        if (extCacheGid != -1) {
            if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), extCacheGid,
                    reinterpret_cast<char*>(&dq)) != 0) {
                if (errno != ESRCH) {
                    PLOG(ERROR) << "Failed to quotactl " << device << " for GID " << extCacheGid;
                }
            } else {
#if MEASURE_DEBUG
                LOG(DEBUG) << "quotactl() for GID " << extCacheGid << " " << dq.dqb_curspace;
#endif
                extStats->dataSize += dq.dqb_curspace;
                extStats->cacheSize += dq.dqb_curspace;
            }
        }
    }
}

static void collectManualStats(const std::string& path, struct stats* stats) {
    DIR *d;
    int dfd;
    struct dirent *de;
    struct stat s;

    d = opendir(path.c_str());
    if (d == nullptr) {
        if (errno != ENOENT) {
            PLOG(WARNING) << "Failed to open " << path;
        }
        return;
    }
    dfd = dirfd(d);
    while ((de = readdir(d))) {
        const char *name = de->d_name;

        int64_t size = 0;
        if (fstatat(dfd, name, &s, AT_SYMLINK_NOFOLLOW) == 0) {
            size = s.st_blocks * 512;
        }

        if (de->d_type == DT_DIR) {
            if (!strcmp(name, ".")) {
                // Don't recurse, but still count node size
            } else if (!strcmp(name, "..")) {
                // Don't recurse or count node size
                continue;
            } else {
                // Measure all children nodes
                size = 0;
                calculate_tree_size(StringPrintf("%s/%s", path.c_str(), name), &size);
            }

            if (!strcmp(name, "cache") || !strcmp(name, "code_cache")) {
                stats->cacheSize += size;
            }
        }

        // Legacy symlink isn't owned by app
        if (de->d_type == DT_LNK && !strcmp(name, "lib")) {
            continue;
        }

        // Everything found inside is considered data
        stats->dataSize += size;
    }
    closedir(d);
}

static void collectManualStatsForUser(const std::string& path, struct stats* stats,
        bool exclude_apps = false) {
    DIR *d;
    int dfd;
    struct dirent *de;
    struct stat s;

    d = opendir(path.c_str());
    if (d == nullptr) {
        if (errno != ENOENT) {
            PLOG(WARNING) << "Failed to open " << path;
        }
        return;
    }
    dfd = dirfd(d);
    while ((de = readdir(d))) {
        if (de->d_type == DT_DIR) {
            const char *name = de->d_name;
            if (fstatat(dfd, name, &s, AT_SYMLINK_NOFOLLOW) != 0) {
                continue;
            }
            int32_t user_uid = multiuser_get_app_id(s.st_uid);
            if (!strcmp(name, ".") || !strcmp(name, "..")) {
                continue;
            } else if (exclude_apps && (user_uid >= AID_APP_START && user_uid <= AID_APP_END)) {
                continue;
            } else {
                collectManualStats(StringPrintf("%s/%s", path.c_str(), name), stats);
            }
        }
    }
    closedir(d);
}

static void collectManualExternalStatsForUser(const std::string& path, struct stats* stats) {
    FTS *fts;
    FTSENT *p;
    char *argv[] = { (char*) path.c_str(), nullptr };
    if (!(fts = fts_open(argv, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, NULL))) {
        PLOG(ERROR) << "Failed to fts_open " << path;
        return;
    }
    while ((p = fts_read(fts)) != NULL) {
        p->fts_number = p->fts_parent->fts_number;
        switch (p->fts_info) {
        case FTS_D:
            if (p->fts_level == 4
                    && !strcmp(p->fts_name, "cache")
                    && !strcmp(p->fts_parent->fts_parent->fts_name, "data")
                    && !strcmp(p->fts_parent->fts_parent->fts_parent->fts_name, "Android")) {
                p->fts_number = 1;
            }
            // Fall through to count the directory
        case FTS_DEFAULT:
        case FTS_F:
        case FTS_SL:
        case FTS_SLNONE:
            int64_t size = (p->fts_statp->st_blocks * 512);
            if (p->fts_number == 1) {
                stats->cacheSize += size;
            }
            stats->dataSize += size;
            break;
        }
    }
    fts_close(fts);
}

binder::Status InstalldNativeService::getAppSize(const std::unique_ptr<std::string>& uuid,
        const std::vector<std::string>& packageNames, int32_t userId, int32_t flags,
        int32_t appId, const std::vector<int64_t>& ceDataInodes,
        const std::vector<std::string>& codePaths, std::vector<int64_t>* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    for (auto packageName : packageNames) {
        CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    }
    // NOTE: Locking is relaxed on this method, since it's limited to
    // read-only measurements without mutation.

    // When modifying this logic, always verify using tests:
    // runtest -x frameworks/base/services/tests/servicestests/src/com/android/server/pm/InstallerTest.java -m testGetAppSize

#if MEASURE_DEBUG
    LOG(INFO) << "Measuring user " << userId << " app " << appId;
#endif

    // Here's a summary of the common storage locations across the platform,
    // and how they're each tagged:
    //
    // /data/app/com.example                           UID system
    // /data/app/com.example/oat                       UID system
    // /data/user/0/com.example                        UID u0_a10      GID u0_a10
    // /data/user/0/com.example/cache                  UID u0_a10      GID u0_a10_cache
    // /data/media/0/foo.txt                           UID u0_media_rw
    // /data/media/0/bar.jpg                           UID u0_media_rw GID u0_media_image
    // /data/media/0/Android/data/com.example          UID u0_media_rw GID u0_a10_ext
    // /data/media/0/Android/data/com.example/cache    UID u0_media_rw GID u0_a10_ext_cache
    // /data/media/obb/com.example                     UID system

    struct stats stats;
    struct stats extStats;
    memset(&stats, 0, sizeof(stats));
    memset(&extStats, 0, sizeof(extStats));

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;

    auto device = findQuotaDeviceForUuid(uuid);
    if (device.empty()) {
        flags &= ~FLAG_USE_QUOTA;
    }

    ATRACE_BEGIN("obb");
    for (auto packageName : packageNames) {
        auto obbCodePath = create_data_media_obb_path(uuid_, packageName.c_str());
        calculate_tree_size(obbCodePath, &extStats.codeSize);
    }
    ATRACE_END();

    if (flags & FLAG_USE_QUOTA && appId >= AID_APP_START) {
        ATRACE_BEGIN("code");
        for (auto codePath : codePaths) {
            calculate_tree_size(codePath, &stats.codeSize, -1,
                    multiuser_get_shared_gid(0, appId));
        }
        ATRACE_END();

        ATRACE_BEGIN("quota");
        collectQuotaStats(device, userId, appId, &stats, &extStats);
        ATRACE_END();
    } else {
        ATRACE_BEGIN("code");
        for (auto codePath : codePaths) {
            calculate_tree_size(codePath, &stats.codeSize);
        }
        ATRACE_END();

        for (size_t i = 0; i < packageNames.size(); i++) {
            const char* pkgname = packageNames[i].c_str();

            ATRACE_BEGIN("data");
            auto cePath = create_data_user_ce_package_path(uuid_, userId, pkgname, ceDataInodes[i]);
            collectManualStats(cePath, &stats);
            auto dePath = create_data_user_de_package_path(uuid_, userId, pkgname);
            collectManualStats(dePath, &stats);
            ATRACE_END();

            if (!uuid) {
                ATRACE_BEGIN("profiles");
                calculate_tree_size(
                        create_primary_current_profile_package_dir_path(userId, pkgname),
                        &stats.dataSize);
                calculate_tree_size(
                        create_primary_reference_profile_package_dir_path(pkgname),
                        &stats.codeSize);
                ATRACE_END();
            }

            ATRACE_BEGIN("external");
            auto extPath = create_data_media_package_path(uuid_, userId, "data", pkgname);
            collectManualStats(extPath, &extStats);
            auto mediaPath = create_data_media_package_path(uuid_, userId, "media", pkgname);
            calculate_tree_size(mediaPath, &extStats.dataSize);
            ATRACE_END();
        }

        if (!uuid) {
            ATRACE_BEGIN("dalvik");
            int32_t sharedGid = multiuser_get_shared_gid(0, appId);
            if (sharedGid != -1) {
                calculate_tree_size(create_data_dalvik_cache_path(), &stats.codeSize,
                        sharedGid, -1);
            }
            ATRACE_END();
        }
    }

    std::vector<int64_t> ret;
    ret.push_back(stats.codeSize);
    ret.push_back(stats.dataSize);
    ret.push_back(stats.cacheSize);
    ret.push_back(extStats.codeSize);
    ret.push_back(extStats.dataSize);
    ret.push_back(extStats.cacheSize);
#if MEASURE_DEBUG
    LOG(DEBUG) << "Final result " << toString(ret);
#endif
    *_aidl_return = ret;
    return ok();
}

binder::Status InstalldNativeService::getUserSize(const std::unique_ptr<std::string>& uuid,
        int32_t userId, int32_t flags, const std::vector<int32_t>& appIds,
        std::vector<int64_t>* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    // NOTE: Locking is relaxed on this method, since it's limited to
    // read-only measurements without mutation.

    // When modifying this logic, always verify using tests:
    // runtest -x frameworks/base/services/tests/servicestests/src/com/android/server/pm/InstallerTest.java -m testGetUserSize

#if MEASURE_DEBUG
    LOG(INFO) << "Measuring user " << userId;
#endif

    struct stats stats;
    struct stats extStats;
    memset(&stats, 0, sizeof(stats));
    memset(&extStats, 0, sizeof(extStats));

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;

    auto device = findQuotaDeviceForUuid(uuid);
    if (device.empty()) {
        flags &= ~FLAG_USE_QUOTA;
    }

    if (flags & FLAG_USE_QUOTA) {
        struct dqblk dq;

        ATRACE_BEGIN("obb");
        if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), AID_MEDIA_OBB,
                reinterpret_cast<char*>(&dq)) != 0) {
            if (errno != ESRCH) {
                PLOG(ERROR) << "Failed to quotactl " << device << " for GID " << AID_MEDIA_OBB;
            }
        } else {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for GID " << AID_MEDIA_OBB << " " << dq.dqb_curspace;
#endif
            extStats.codeSize += dq.dqb_curspace;
        }
        ATRACE_END();

        ATRACE_BEGIN("code");
        calculate_tree_size(create_data_app_path(uuid_), &stats.codeSize, -1, -1, true);
        ATRACE_END();

        ATRACE_BEGIN("data");
        auto cePath = create_data_user_ce_path(uuid_, userId);
        collectManualStatsForUser(cePath, &stats, true);
        auto dePath = create_data_user_de_path(uuid_, userId);
        collectManualStatsForUser(dePath, &stats, true);
        ATRACE_END();

        if (!uuid) {
            ATRACE_BEGIN("profile");
            auto userProfilePath = create_primary_cur_profile_dir_path(userId);
            calculate_tree_size(userProfilePath, &stats.dataSize, -1, -1, true);
            auto refProfilePath = create_primary_ref_profile_dir_path();
            calculate_tree_size(refProfilePath, &stats.codeSize, -1, -1, true);
            ATRACE_END();
        }

        ATRACE_BEGIN("external");
        uid_t uid = multiuser_get_uid(userId, AID_MEDIA_RW);
        if (quotactl(QCMD(Q_GETQUOTA, USRQUOTA), device.c_str(), uid,
                reinterpret_cast<char*>(&dq)) != 0) {
            if (errno != ESRCH) {
                PLOG(ERROR) << "Failed to quotactl " << device << " for UID " << uid;
            }
        } else {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for UID " << uid << " " << dq.dqb_curspace;
#endif
            extStats.dataSize += dq.dqb_curspace;
        }
        ATRACE_END();

        if (!uuid) {
            ATRACE_BEGIN("dalvik");
            calculate_tree_size(create_data_dalvik_cache_path(), &stats.codeSize,
                    -1, -1, true);
            calculate_tree_size(create_primary_cur_profile_dir_path(userId), &stats.dataSize,
                    -1, -1, true);
            ATRACE_END();
        }

        ATRACE_BEGIN("quota");
        int64_t dataSize = extStats.dataSize;
        for (auto appId : appIds) {
            if (appId >= AID_APP_START) {
                collectQuotaStats(device, userId, appId, &stats, &extStats);

#if MEASURE_DEBUG
                // Sleep to make sure we don't lose logs
                usleep(1);
#endif
            }
        }
        extStats.dataSize = dataSize;
        ATRACE_END();
    } else {
        ATRACE_BEGIN("obb");
        auto obbPath = create_data_path(uuid_) + "/media/obb";
        calculate_tree_size(obbPath, &extStats.codeSize);
        ATRACE_END();

        ATRACE_BEGIN("code");
        calculate_tree_size(create_data_app_path(uuid_), &stats.codeSize);
        ATRACE_END();

        ATRACE_BEGIN("data");
        auto cePath = create_data_user_ce_path(uuid_, userId);
        collectManualStatsForUser(cePath, &stats);
        auto dePath = create_data_user_de_path(uuid_, userId);
        collectManualStatsForUser(dePath, &stats);
        ATRACE_END();

        if (!uuid) {
            ATRACE_BEGIN("profile");
            auto userProfilePath = create_primary_cur_profile_dir_path(userId);
            calculate_tree_size(userProfilePath, &stats.dataSize);
            auto refProfilePath = create_primary_ref_profile_dir_path();
            calculate_tree_size(refProfilePath, &stats.codeSize);
            ATRACE_END();
        }

        ATRACE_BEGIN("external");
        auto dataMediaPath = create_data_media_path(uuid_, userId);
        collectManualExternalStatsForUser(dataMediaPath, &extStats);
#if MEASURE_DEBUG
        LOG(DEBUG) << "Measured external data " << extStats.dataSize << " cache "
                << extStats.cacheSize;
#endif
        ATRACE_END();

        if (!uuid) {
            ATRACE_BEGIN("dalvik");
            calculate_tree_size(create_data_dalvik_cache_path(), &stats.codeSize);
            calculate_tree_size(create_primary_cur_profile_dir_path(userId), &stats.dataSize);
            ATRACE_END();
        }
    }

    std::vector<int64_t> ret;
    ret.push_back(stats.codeSize);
    ret.push_back(stats.dataSize);
    ret.push_back(stats.cacheSize);
    ret.push_back(extStats.codeSize);
    ret.push_back(extStats.dataSize);
    ret.push_back(extStats.cacheSize);
#if MEASURE_DEBUG
    LOG(DEBUG) << "Final result " << toString(ret);
#endif
    *_aidl_return = ret;
    return ok();
}

binder::Status InstalldNativeService::getExternalSize(const std::unique_ptr<std::string>& uuid,
        int32_t userId, int32_t flags, const std::vector<int32_t>& appIds,
        std::vector<int64_t>* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    // NOTE: Locking is relaxed on this method, since it's limited to
    // read-only measurements without mutation.

    // When modifying this logic, always verify using tests:
    // runtest -x frameworks/base/services/tests/servicestests/src/com/android/server/pm/InstallerTest.java -m testGetExternalSize

#if MEASURE_DEBUG
    LOG(INFO) << "Measuring external " << userId;
#endif

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;

    int64_t totalSize = 0;
    int64_t audioSize = 0;
    int64_t videoSize = 0;
    int64_t imageSize = 0;
    int64_t appSize = 0;
    int64_t obbSize = 0;

    auto device = findQuotaDeviceForUuid(uuid);
    if (device.empty()) {
        flags &= ~FLAG_USE_QUOTA;
    }

    if (flags & FLAG_USE_QUOTA) {
        struct dqblk dq;

        ATRACE_BEGIN("quota");
        uid_t uid = multiuser_get_uid(userId, AID_MEDIA_RW);
        if (quotactl(QCMD(Q_GETQUOTA, USRQUOTA), device.c_str(), uid,
                reinterpret_cast<char*>(&dq)) != 0) {
            if (errno != ESRCH) {
                PLOG(ERROR) << "Failed to quotactl " << device << " for UID " << uid;
            }
        } else {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for UID " << uid << " " << dq.dqb_curspace;
#endif
            totalSize = dq.dqb_curspace;
        }

        gid_t audioGid = multiuser_get_uid(userId, AID_MEDIA_AUDIO);
        if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), audioGid,
                reinterpret_cast<char*>(&dq)) == 0) {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for GID " << audioGid << " " << dq.dqb_curspace;
#endif
            audioSize = dq.dqb_curspace;
        }
        gid_t videoGid = multiuser_get_uid(userId, AID_MEDIA_VIDEO);
        if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), videoGid,
                reinterpret_cast<char*>(&dq)) == 0) {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for GID " << videoGid << " " << dq.dqb_curspace;
#endif
            videoSize = dq.dqb_curspace;
        }
        gid_t imageGid = multiuser_get_uid(userId, AID_MEDIA_IMAGE);
        if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), imageGid,
                reinterpret_cast<char*>(&dq)) == 0) {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for GID " << imageGid << " " << dq.dqb_curspace;
#endif
            imageSize = dq.dqb_curspace;
        }
        if (quotactl(QCMD(Q_GETQUOTA, GRPQUOTA), device.c_str(), AID_MEDIA_OBB,
                reinterpret_cast<char*>(&dq)) == 0) {
#if MEASURE_DEBUG
            LOG(DEBUG) << "quotactl() for GID " << AID_MEDIA_OBB << " " << dq.dqb_curspace;
#endif
            obbSize = dq.dqb_curspace;
        }
        ATRACE_END();

        ATRACE_BEGIN("apps");
        struct stats extStats;
        memset(&extStats, 0, sizeof(extStats));
        for (auto appId : appIds) {
            if (appId >= AID_APP_START) {
                collectQuotaStats(device, userId, appId, nullptr, &extStats);
            }
        }
        appSize = extStats.dataSize;
        ATRACE_END();
    } else {
        ATRACE_BEGIN("manual");
        FTS *fts;
        FTSENT *p;
        auto path = create_data_media_path(uuid_, userId);
        char *argv[] = { (char*) path.c_str(), nullptr };
        if (!(fts = fts_open(argv, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, NULL))) {
            return error("Failed to fts_open " + path);
        }
        while ((p = fts_read(fts)) != NULL) {
            char* ext;
            int64_t size = (p->fts_statp->st_blocks * 512);
            switch (p->fts_info) {
            case FTS_F:
                // Only categorize files not belonging to apps
                if (p->fts_parent->fts_number == 0) {
                    ext = strrchr(p->fts_name, '.');
                    if (ext != nullptr) {
                        switch (MatchExtension(++ext)) {
                        case AID_MEDIA_AUDIO: audioSize += size; break;
                        case AID_MEDIA_VIDEO: videoSize += size; break;
                        case AID_MEDIA_IMAGE: imageSize += size; break;
                        }
                    }
                }
                // Fall through to always count against total
            case FTS_D:
                // Ignore data belonging to specific apps
                p->fts_number = p->fts_parent->fts_number;
                if (p->fts_level == 1 && !strcmp(p->fts_name, "Android")) {
                    p->fts_number = 1;
                }
            case FTS_DEFAULT:
            case FTS_SL:
            case FTS_SLNONE:
                if (p->fts_parent->fts_number == 1) {
                    appSize += size;
                }
                totalSize += size;
                break;
            }
        }
        fts_close(fts);
        ATRACE_END();

        ATRACE_BEGIN("obb");
        auto obbPath = create_data_media_obb_path(uuid_, "");
        calculate_tree_size(obbPath, &obbSize);
        ATRACE_END();
    }

    std::vector<int64_t> ret;
    ret.push_back(totalSize);
    ret.push_back(audioSize);
    ret.push_back(videoSize);
    ret.push_back(imageSize);
    ret.push_back(appSize);
    ret.push_back(obbSize);
#if MEASURE_DEBUG
    LOG(DEBUG) << "Final result " << toString(ret);
#endif
    *_aidl_return = ret;
    return ok();
}

binder::Status InstalldNativeService::setAppQuota(const std::unique_ptr<std::string>& uuid,
        int32_t userId, int32_t appId, int64_t cacheQuota) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    std::lock_guard<std::recursive_mutex> lock(mQuotasLock);

    int32_t uid = multiuser_get_uid(userId, appId);
    mCacheQuotas[uid] = cacheQuota;

    return ok();
}

// Dumps the contents of a profile file, using pkgname's dex files for pretty
// printing the result.
binder::Status InstalldNativeService::dumpProfiles(int32_t uid, const std::string& packageName,
        const std::string& codePaths, bool* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* pkgname = packageName.c_str();
    const char* code_paths = codePaths.c_str();

    *_aidl_return = dump_profiles(uid, pkgname, code_paths);
    return ok();
}

// Copy the contents of a system profile over the data profile.
binder::Status InstalldNativeService::copySystemProfile(const std::string& systemProfile,
        int32_t packageUid, const std::string& packageName, bool* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);
    *_aidl_return = copy_system_profile(systemProfile, packageUid, packageName);
    return ok();
}

// TODO: Consider returning error codes.
binder::Status InstalldNativeService::mergeProfiles(int32_t uid, const std::string& packageName,
        bool* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    *_aidl_return = analyze_primary_profiles(uid, packageName);
    return ok();
}

binder::Status InstalldNativeService::dexopt(const std::string& apkPath, int32_t uid,
        const std::unique_ptr<std::string>& packageName, const std::string& instructionSet,
        int32_t dexoptNeeded, const std::unique_ptr<std::string>& outputPath, int32_t dexFlags,
        const std::string& compilerFilter, const std::unique_ptr<std::string>& uuid,
        const std::unique_ptr<std::string>& classLoaderContext,
        const std::unique_ptr<std::string>& seInfo, bool downgrade) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    if (packageName && *packageName != "*") {
        CHECK_ARGUMENT_PACKAGE_NAME(*packageName);
    }
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* apk_path = apkPath.c_str();
    const char* pkgname = packageName ? packageName->c_str() : "*";
    const char* instruction_set = instructionSet.c_str();
    const char* oat_dir = outputPath ? outputPath->c_str() : nullptr;
    const char* compiler_filter = compilerFilter.c_str();
    const char* volume_uuid = uuid ? uuid->c_str() : nullptr;
    const char* class_loader_context = classLoaderContext ? classLoaderContext->c_str() : nullptr;
    const char* se_info = seInfo ? seInfo->c_str() : nullptr;
    int res = android::installd::dexopt(apk_path, uid, pkgname, instruction_set, dexoptNeeded,
            oat_dir, dexFlags, compiler_filter, volume_uuid, class_loader_context, se_info,
            downgrade);
    return res ? error(res, "Failed to dexopt") : ok();
}

binder::Status InstalldNativeService::markBootComplete(const std::string& instructionSet) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* instruction_set = instructionSet.c_str();

    char boot_marker_path[PKG_PATH_MAX];
    sprintf(boot_marker_path,
          "%s/%s/%s/.booting",
          android_data_dir.path,
          DALVIK_CACHE,
          instruction_set);

    ALOGV("mark_boot_complete : %s", boot_marker_path);
    if (unlink(boot_marker_path) != 0) {
        return error(StringPrintf("Failed to unlink %s", boot_marker_path));
    }
    return ok();
}

void mkinnerdirs(char* path, int basepos, mode_t mode, int uid, int gid,
        struct stat* statbuf)
{
    while (path[basepos] != 0) {
        if (path[basepos] == '/') {
            path[basepos] = 0;
            if (lstat(path, statbuf) < 0) {
                ALOGV("Making directory: %s\n", path);
                if (mkdir(path, mode) == 0) {
                    chown(path, uid, gid);
                } else {
                    ALOGW("Unable to make directory %s: %s\n", path, strerror(errno));
                }
            }
            path[basepos] = '/';
            basepos++;
        }
        basepos++;
    }
}

binder::Status InstalldNativeService::linkNativeLibraryDirectory(
        const std::unique_ptr<std::string>& uuid, const std::string& packageName,
        const std::string& nativeLibPath32, int32_t userId) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgname = packageName.c_str();
    const char* asecLibDir = nativeLibPath32.c_str();
    struct stat s, libStat;
    binder::Status res = ok();

    auto _pkgdir = create_data_user_ce_package_path(uuid_, userId, pkgname);
    auto _libsymlink = _pkgdir + PKG_LIB_POSTFIX;

    const char* pkgdir = _pkgdir.c_str();
    const char* libsymlink = _libsymlink.c_str();

    if (stat(pkgdir, &s) < 0) {
        return error("Failed to stat " + _pkgdir);
    }

    if (chown(pkgdir, AID_INSTALL, AID_INSTALL) < 0) {
        return error("Failed to chown " + _pkgdir);
    }

    if (chmod(pkgdir, 0700) < 0) {
        res = error("Failed to chmod " + _pkgdir);
        goto out;
    }

    if (lstat(libsymlink, &libStat) < 0) {
        if (errno != ENOENT) {
            res = error("Failed to stat " + _libsymlink);
            goto out;
        }
    } else {
        if (S_ISDIR(libStat.st_mode)) {
            if (delete_dir_contents(libsymlink, 1, NULL) < 0) {
                res = error("Failed to delete " + _libsymlink);
                goto out;
            }
        } else if (S_ISLNK(libStat.st_mode)) {
            if (unlink(libsymlink) < 0) {
                res = error("Failed to unlink " + _libsymlink);
                goto out;
            }
        }
    }

    if (symlink(asecLibDir, libsymlink) < 0) {
        res = error("Failed to symlink " + _libsymlink + " to " + nativeLibPath32);
        goto out;
    }

out:
    if (chmod(pkgdir, s.st_mode) < 0) {
        auto msg = "Failed to cleanup chmod " + _pkgdir;
        if (res.isOk()) {
            res = error(msg);
        } else {
            PLOG(ERROR) << msg;
        }
    }

    if (chown(pkgdir, s.st_uid, s.st_gid) < 0) {
        auto msg = "Failed to cleanup chown " + _pkgdir;
        if (res.isOk()) {
            res = error(msg);
        } else {
            PLOG(ERROR) << msg;
        }
    }

    return res;
}

static void run_idmap(const char *target_apk, const char *overlay_apk, int idmap_fd)
{
    execl(kIdMapPath, kIdMapPath, "--fd", target_apk, overlay_apk,
            StringPrintf("%d", idmap_fd).c_str(), (char*)NULL);
    PLOG(ERROR) << "execl (" << kIdMapPath << ") failed";
}

static void run_verify_idmap(const char *target_apk, const char *overlay_apk, int idmap_fd)
{
    execl(kIdMapPath, kIdMapPath, "--verify", target_apk, overlay_apk,
            StringPrintf("%d", idmap_fd).c_str(), (char*)NULL);
    PLOG(ERROR) << "execl (" << kIdMapPath << ") failed";
}

static bool delete_stale_idmap(const char* target_apk, const char* overlay_apk,
        const char* idmap_path, int32_t uid) {
    int idmap_fd = open(idmap_path, O_RDWR);
    if (idmap_fd < 0) {
        PLOG(ERROR) << "idmap open failed: " << idmap_path;
        unlink(idmap_path);
        return true;
    }

    pid_t pid;
    pid = fork();
    if (pid == 0) {
        /* child -- drop privileges before continuing */
        if (setgid(uid) != 0) {
            LOG(ERROR) << "setgid(" << uid << ") failed during idmap";
            exit(1);
        }
        if (setuid(uid) != 0) {
            LOG(ERROR) << "setuid(" << uid << ") failed during idmap";
            exit(1);
        }
        if (flock(idmap_fd, LOCK_EX | LOCK_NB) != 0) {
            PLOG(ERROR) << "flock(" << idmap_path << ") failed during idmap";
            exit(1);
        }

        run_verify_idmap(target_apk, overlay_apk, idmap_fd);
        exit(1); /* only if exec call to deleting stale idmap failed */
    } else {
        int status = wait_child(pid);
        close(idmap_fd);

        if (status != 0) {
            // Failed on verifying if idmap is made from target_apk and overlay_apk.
            LOG(DEBUG) << "delete stale idmap: " << idmap_path;
            unlink(idmap_path);
            return true;
        }
    }
    return false;
}

// Transform string /a/b/c.apk to (prefix)/a@b@c.apk@(suffix)
// eg /a/b/c.apk to /data/resource-cache/a@b@c.apk@idmap
static int flatten_path(const char *prefix, const char *suffix,
        const char *overlay_path, char *idmap_path, size_t N)
{
    if (overlay_path == NULL || idmap_path == NULL) {
        return -1;
    }
    const size_t len_overlay_path = strlen(overlay_path);
    // will access overlay_path + 1 further below; requires absolute path
    if (len_overlay_path < 2 || *overlay_path != '/') {
        return -1;
    }
    const size_t len_idmap_root = strlen(prefix);
    const size_t len_suffix = strlen(suffix);
    if (SIZE_MAX - len_idmap_root < len_overlay_path ||
            SIZE_MAX - (len_idmap_root + len_overlay_path) < len_suffix) {
        // additions below would cause overflow
        return -1;
    }
    if (N < len_idmap_root + len_overlay_path + len_suffix) {
        return -1;
    }
    memset(idmap_path, 0, N);
    snprintf(idmap_path, N, "%s%s%s", prefix, overlay_path + 1, suffix);
    char *ch = idmap_path + len_idmap_root;
    while (*ch != '\0') {
        if (*ch == '/') {
            *ch = '@';
        }
        ++ch;
    }
    return 0;
}

binder::Status InstalldNativeService::idmap(const std::string& targetApkPath,
        const std::string& overlayApkPath, int32_t uid) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* target_apk = targetApkPath.c_str();
    const char* overlay_apk = overlayApkPath.c_str();
    ALOGV("idmap target_apk=%s overlay_apk=%s uid=%d\n", target_apk, overlay_apk, uid);

    int idmap_fd = -1;
    char idmap_path[PATH_MAX];
    struct stat idmap_stat;
    bool outdated = false;

    if (flatten_path(IDMAP_PREFIX, IDMAP_SUFFIX, overlay_apk,
                idmap_path, sizeof(idmap_path)) == -1) {
        ALOGE("idmap cannot generate idmap path for overlay %s\n", overlay_apk);
        goto fail;
    }

    if (stat(idmap_path, &idmap_stat) < 0) {
        outdated = true;
    } else {
        outdated = delete_stale_idmap(target_apk, overlay_apk, idmap_path, uid);
    }

    if (outdated) {
        idmap_fd = open(idmap_path, O_RDWR | O_CREAT | O_EXCL, 0644);
    } else {
        idmap_fd = open(idmap_path, O_RDWR);
    }

    if (idmap_fd < 0) {
        ALOGE("idmap cannot open '%s' for output: %s\n", idmap_path, strerror(errno));
        goto fail;
    }
    if (fchown(idmap_fd, AID_SYSTEM, uid) < 0) {
        ALOGE("idmap cannot chown '%s'\n", idmap_path);
        goto fail;
    }
    if (fchmod(idmap_fd, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH) < 0) {
        ALOGE("idmap cannot chmod '%s'\n", idmap_path);
        goto fail;
    }

    if (!outdated) {
        close(idmap_fd);
        return ok();
    }

    pid_t pid;
    pid = fork();
    if (pid == 0) {
        /* child -- drop privileges before continuing */
        if (setgid(uid) != 0) {
            ALOGE("setgid(%d) failed during idmap\n", uid);
            exit(1);
        }
        if (setuid(uid) != 0) {
            ALOGE("setuid(%d) failed during idmap\n", uid);
            exit(1);
        }
        if (flock(idmap_fd, LOCK_EX | LOCK_NB) != 0) {
            ALOGE("flock(%s) failed during idmap: %s\n", idmap_path, strerror(errno));
            exit(1);
        }

        run_idmap(target_apk, overlay_apk, idmap_fd);
        exit(1); /* only if exec call to idmap failed */
    } else {
        int status = wait_child(pid);
        if (status != 0) {
            ALOGE("idmap failed, status=0x%04x\n", status);
            goto fail;
        }
    }

    close(idmap_fd);
    return ok();
fail:
    if (idmap_fd >= 0) {
        close(idmap_fd);
        unlink(idmap_path);
    }
    return error();
}

binder::Status InstalldNativeService::removeIdmap(const std::string& overlayApkPath) {
    const char* overlay_apk = overlayApkPath.c_str();
    char idmap_path[PATH_MAX];

    if (flatten_path(IDMAP_PREFIX, IDMAP_SUFFIX, overlay_apk,
                idmap_path, sizeof(idmap_path)) == -1) {
        ALOGE("idmap cannot generate idmap path for overlay %s\n", overlay_apk);
        return error();
    }
    if (unlink(idmap_path) < 0) {
        ALOGE("couldn't unlink idmap file %s\n", idmap_path);
        return error();
    }
    return ok();
}

binder::Status InstalldNativeService::restoreconAppData(const std::unique_ptr<std::string>& uuid,
        const std::string& packageName, int32_t userId, int32_t flags, int32_t appId,
        const std::string& seInfo) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(uuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    binder::Status res = ok();

    // SELINUX_ANDROID_RESTORECON_DATADATA flag is set by libselinux. Not needed here.
    unsigned int seflags = SELINUX_ANDROID_RESTORECON_RECURSE;
    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    const char* pkgName = packageName.c_str();
    const char* seinfo = seInfo.c_str();

    uid_t uid = multiuser_get_uid(userId, appId);
    if (flags & FLAG_STORAGE_CE) {
        auto path = create_data_user_ce_package_path(uuid_, userId, pkgName);
        if (selinux_android_restorecon_pkgdir(path.c_str(), seinfo, uid, seflags) < 0) {
            res = error("restorecon failed for " + path);
        }
    }
    if (flags & FLAG_STORAGE_DE) {
        auto path = create_data_user_de_package_path(uuid_, userId, pkgName);
        if (selinux_android_restorecon_pkgdir(path.c_str(), seinfo, uid, seflags) < 0) {
            res = error("restorecon failed for " + path);
        }
    }
    return res;
}

binder::Status InstalldNativeService::createOatDir(const std::string& oatDir,
        const std::string& instructionSet) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* oat_dir = oatDir.c_str();
    const char* instruction_set = instructionSet.c_str();
    char oat_instr_dir[PKG_PATH_MAX];

    if (validate_apk_path(oat_dir)) {
        return error("Invalid path " + oatDir);
    }
    if (fs_prepare_dir(oat_dir, S_IRWXU | S_IRWXG | S_IXOTH, AID_SYSTEM, AID_INSTALL)) {
        return error("Failed to prepare " + oatDir);
    }
    if (selinux_android_restorecon(oat_dir, 0)) {
        return error("Failed to restorecon " + oatDir);
    }
    snprintf(oat_instr_dir, PKG_PATH_MAX, "%s/%s", oat_dir, instruction_set);
    if (fs_prepare_dir(oat_instr_dir, S_IRWXU | S_IRWXG | S_IXOTH, AID_SYSTEM, AID_INSTALL)) {
        return error(StringPrintf("Failed to prepare %s", oat_instr_dir));
    }
    return ok();
}

binder::Status InstalldNativeService::rmPackageDir(const std::string& packageDir) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    if (validate_apk_path(packageDir.c_str())) {
        return error("Invalid path " + packageDir);
    }
    if (delete_dir_contents_and_dir(packageDir) != 0) {
        return error("Failed to delete " + packageDir);
    }
    return ok();
}

binder::Status InstalldNativeService::linkFile(const std::string& relativePath,
        const std::string& fromBase, const std::string& toBase) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* relative_path = relativePath.c_str();
    const char* from_base = fromBase.c_str();
    const char* to_base = toBase.c_str();
    char from_path[PKG_PATH_MAX];
    char to_path[PKG_PATH_MAX];
    snprintf(from_path, PKG_PATH_MAX, "%s/%s", from_base, relative_path);
    snprintf(to_path, PKG_PATH_MAX, "%s/%s", to_base, relative_path);

    if (validate_apk_path_subdirs(from_path)) {
        return error(StringPrintf("Invalid from path %s", from_path));
    }

    if (validate_apk_path_subdirs(to_path)) {
        return error(StringPrintf("Invalid to path %s", to_path));
    }

    if (link(from_path, to_path) < 0) {
        return error(StringPrintf("Failed to link from %s to %s", from_path, to_path));
    }

    return ok();
}

binder::Status InstalldNativeService::moveAb(const std::string& apkPath,
        const std::string& instructionSet, const std::string& outputPath) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* apk_path = apkPath.c_str();
    const char* instruction_set = instructionSet.c_str();
    const char* oat_dir = outputPath.c_str();

    bool success = move_ab(apk_path, instruction_set, oat_dir);
    return success ? ok() : error();
}

binder::Status InstalldNativeService::deleteOdex(const std::string& apkPath,
        const std::string& instructionSet, const std::unique_ptr<std::string>& outputPath) {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mLock);

    const char* apk_path = apkPath.c_str();
    const char* instruction_set = instructionSet.c_str();
    const char* oat_dir = outputPath ? outputPath->c_str() : nullptr;

    bool res = delete_odex(apk_path, instruction_set, oat_dir);
    return res ? ok() : error();
}

binder::Status InstalldNativeService::reconcileSecondaryDexFile(
        const std::string& dexPath, const std::string& packageName, int32_t uid,
        const std::vector<std::string>& isas, const std::unique_ptr<std::string>& volumeUuid,
        int32_t storage_flag, bool* _aidl_return) {
    ENFORCE_UID(AID_SYSTEM);
    CHECK_ARGUMENT_UUID(volumeUuid);
    CHECK_ARGUMENT_PACKAGE_NAME(packageName);

    std::lock_guard<std::recursive_mutex> lock(mLock);
    bool result = android::installd::reconcile_secondary_dex_file(
            dexPath, packageName, uid, isas, volumeUuid, storage_flag, _aidl_return);
    return result ? ok() : error();
}

binder::Status InstalldNativeService::invalidateMounts() {
    ENFORCE_UID(AID_SYSTEM);
    std::lock_guard<std::recursive_mutex> lock(mMountsLock);

    mStorageMounts.clear();
    mQuotaReverseMounts.clear();

    std::ifstream in("/proc/mounts");
    if (!in.is_open()) {
        return error("Failed to read mounts");
    }

    std::string source;
    std::string target;
    std::string ignored;
    while (!in.eof()) {
        std::getline(in, source, ' ');
        std::getline(in, target, ' ');
        std::getline(in, ignored);

#if !BYPASS_SDCARDFS
        if (target.compare(0, 21, "/mnt/runtime/default/") == 0) {
            LOG(DEBUG) << "Found storage mount " << source << " at " << target;
            mStorageMounts[source] = target;
        }
#endif

#if !BYPASS_QUOTA
        if (source.compare(0, 11, "/dev/block/") == 0) {
            struct dqblk dq;
            if (quotactl(QCMD(Q_GETQUOTA, USRQUOTA), source.c_str(), 0,
                    reinterpret_cast<char*>(&dq)) == 0) {
                LOG(DEBUG) << "Found quota mount " << source << " at " << target;
                mQuotaReverseMounts[target] = source;

                // ext4 only enables DQUOT_USAGE_ENABLED by default, so we
                // need to kick it again to enable DQUOT_LIMITS_ENABLED.
                if (quotactl(QCMD(Q_QUOTAON, USRQUOTA), source.c_str(), QFMT_VFS_V1, nullptr) != 0
                        && errno != EBUSY) {
                    PLOG(ERROR) << "Failed to enable USRQUOTA on " << source;
                }
                if (quotactl(QCMD(Q_QUOTAON, GRPQUOTA), source.c_str(), QFMT_VFS_V1, nullptr) != 0
                        && errno != EBUSY) {
                    PLOG(ERROR) << "Failed to enable GRPQUOTA on " << source;
                }
            }
        }
#endif
    }
    return ok();
}

std::string InstalldNativeService::findDataMediaPath(
        const std::unique_ptr<std::string>& uuid, userid_t userid) {
    std::lock_guard<std::recursive_mutex> lock(mMountsLock);
    const char* uuid_ = uuid ? uuid->c_str() : nullptr;
    auto path = StringPrintf("%s/media", create_data_path(uuid_).c_str());
    auto resolved = mStorageMounts[path];
    if (resolved.empty()) {
        LOG(WARNING) << "Failed to find storage mount for " << path;
        resolved = path;
    }
    return StringPrintf("%s/%u", resolved.c_str(), userid);
}

std::string InstalldNativeService::findQuotaDeviceForUuid(
        const std::unique_ptr<std::string>& uuid) {
    std::lock_guard<std::recursive_mutex> lock(mMountsLock);
    auto path = create_data_path(uuid ? uuid->c_str() : nullptr);
    return mQuotaReverseMounts[path];
}

binder::Status InstalldNativeService::isQuotaSupported(
        const std::unique_ptr<std::string>& volumeUuid, bool* _aidl_return) {
    *_aidl_return = !findQuotaDeviceForUuid(volumeUuid).empty();
    return ok();
}

}  // namespace installd
}  // namespace android
