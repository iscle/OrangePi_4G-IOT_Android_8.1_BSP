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

#define LOG_TAG "drm-vts-vendor-modules"

#include <dirent.h>
#include <dlfcn.h>
#include <log/log.h>
#include <memory>
#include <utils/String8.h>
#include <SharedLibrary.h>

#include "vendor_modules.h"

using std::string;
using std::vector;
using std::unique_ptr;
using ::android::String8;
using ::android::hardware::drm::V1_0::helper::SharedLibrary;

namespace drm_vts {
void VendorModules::scanModules(const std::string &directory) {
    DIR* dir = opendir(directory.c_str());
    if (dir == NULL) {
        ALOGE("Unable to open drm VTS vendor directory %s", directory.c_str());
    } else {
        struct dirent* entry;
        while ((entry = readdir(dir))) {
            ALOGD("checking file %s", entry->d_name);
            string fullpath = directory + "/" + entry->d_name;
            if (endsWith(fullpath, ".so")) {
                mPathList.push_back(fullpath);
            }
        }
        closedir(dir);
    }
}

DrmHalVTSVendorModule* VendorModules::getModule(const string& path) {
    if (mOpenLibraries.find(path) == mOpenLibraries.end()) {
        auto library = std::make_unique<SharedLibrary>(String8(path.c_str()));
        if (!library) {
            ALOGE("failed to map shared library %s", path.c_str());
            return NULL;
        }
        mOpenLibraries[path] = std::move(library);
    }
    const unique_ptr<SharedLibrary>& library = mOpenLibraries[path];
    void* symbol = library->lookup("vendorModuleFactory");
    if (symbol == NULL) {
        ALOGE("getVendorModule failed to lookup 'vendorModuleFactory' in %s: "
              "%s", path.c_str(), library->lastError());
        return NULL;
    }
    typedef DrmHalVTSVendorModule* (*ModuleFactory)();
    ModuleFactory moduleFactory = reinterpret_cast<ModuleFactory>(symbol);
    return (*moduleFactory)();
}
};
