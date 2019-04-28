/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef DEXOPT_H_
#define DEXOPT_H_

#include <sys/types.h>

#include <cutils/multiuser.h>

namespace android {
namespace installd {

/* dexopt needed flags matching those in dalvik.system.DexFile */
static constexpr int NO_DEXOPT_NEEDED            = 0;
static constexpr int DEX2OAT_FROM_SCRATCH        = 1;
static constexpr int DEX2OAT_FOR_BOOT_IMAGE      = 2;
static constexpr int DEX2OAT_FOR_FILTER          = 3;
static constexpr int DEX2OAT_FOR_RELOCATION      = 4;

// Clear the reference profile for the primary apk of the given package.
bool clear_primary_reference_profile(const std::string& pkgname);
// Clear the current profile for the primary apk of the given package and user.
bool clear_primary_current_profile(const std::string& pkgname, userid_t user);
// Clear all current profile for the primary apk of the given package.
bool clear_primary_current_profiles(const std::string& pkgname);

bool move_ab(const char* apk_path, const char* instruction_set, const char* output_path);

// Decide if profile guided compilation is needed or not based on existing profiles.
// The analysis is done for the primary apks (base + splits) of the given package.
// Returns true if there is enough information in the current profiles that makes it
// worth to recompile the package.
// If the return value is true all the current profiles would have been merged into
// the reference profiles accessible with open_reference_profile().
bool analyze_primary_profiles(uid_t uid, const std::string& pkgname);

bool dump_profiles(int32_t uid, const std::string& pkgname, const char* code_paths);

bool copy_system_profile(const std::string& system_profile,
                         uid_t packageUid,
                         const std::string& data_profile_location);

bool delete_odex(const char* apk_path, const char* instruction_set, const char* output_path);

bool reconcile_secondary_dex_file(const std::string& dex_path,
        const std::string& pkgname, int uid, const std::vector<std::string>& isas,
        const std::unique_ptr<std::string>& volumeUuid, int storage_flag,
        /*out*/bool* out_secondary_dex_exists);

int dexopt(const char *apk_path, uid_t uid, const char *pkgName, const char *instruction_set,
        int dexopt_needed, const char* oat_dir, int dexopt_flags, const char* compiler_filter,
        const char* volume_uuid, const char* class_loader_context, const char* se_info,
        bool downgrade);

}  // namespace installd
}  // namespace android

#endif  // DEXOPT_H_
