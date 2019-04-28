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

#ifndef VENDOR_MODULES_H
#define VENDOR_MODULES_H

#include <map>
#include <vector>
#include <string>

#include <SharedLibrary.h>

using ::android::hardware::drm::V1_0::helper::SharedLibrary;

class DrmHalVTSVendorModule;

namespace drm_vts {
class VendorModules {
   public:
    /**
     * Initialize with a file system path where the shared libraries
     * are to be found.
     */
    explicit VendorModules(const std::string& dir) {
        scanModules(dir);
    }
    ~VendorModules() {}

    /**
     * Retrieve a DrmHalVTSVendorModule given its full path.  The
     * getAPIVersion method can be used to determine the versioned
     * subclass type.
     */
    DrmHalVTSVendorModule* getModule(const std::string& path);

    /**
     * Return the list of paths to available vendor modules.
     */
    std::vector<std::string> getPathList() const {return mPathList;}

   private:
    std::vector<std::string> mPathList;
    std::map<std::string, std::unique_ptr<SharedLibrary>> mOpenLibraries;

    /**
     * Scan the list of paths to available vendor modules.
     */
    void scanModules(const std::string& dir);

    inline bool endsWith(const std::string& str, const std::string& suffix) const {
        if (suffix.size() > str.size()) return false;
        return std::equal(suffix.rbegin(), suffix.rend(), str.rbegin());
    }

    VendorModules(const VendorModules&) = delete;
    void operator=(const VendorModules&) = delete;
};
};

#endif  // VENDOR_MODULES_H
