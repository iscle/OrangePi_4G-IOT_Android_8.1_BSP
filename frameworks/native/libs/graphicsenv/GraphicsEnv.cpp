/*
 * Copyright 2017 The Android Open Source Project
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

//#define LOG_NDEBUG 1
#define LOG_TAG "GraphicsEnv"
#include <graphicsenv/GraphicsEnv.h>

#include <mutex>

#include <log/log.h>
#include <nativeloader/dlext_namespaces.h>

// TODO(b/37049319) Get this from a header once one exists
extern "C" {
  android_namespace_t* android_get_exported_namespace(const char*);
}

namespace android {

/*static*/ GraphicsEnv& GraphicsEnv::getInstance() {
    static GraphicsEnv env;
    return env;
}

void GraphicsEnv::setDriverPath(const std::string path) {
    if (!mDriverPath.empty()) {
        ALOGV("ignoring attempt to change driver path from '%s' to '%s'",
                mDriverPath.c_str(), path.c_str());
        return;
    }
    ALOGV("setting driver path to '%s'", path.c_str());
    mDriverPath = path;
}

android_namespace_t* GraphicsEnv::getDriverNamespace() {
    static std::once_flag once;
    std::call_once(once, [this]() {
        if (mDriverPath.empty())
            return;
        // If the sphal namespace isn't configured for a device, don't support updatable drivers.
        // We need a parent namespace to inherit the default search path from.
        auto sphalNamespace = android_get_exported_namespace("sphal");
        if (!sphalNamespace) return;
        mDriverNamespace = android_create_namespace("gfx driver",
                                                    nullptr,             // ld_library_path
                                                    mDriverPath.c_str(), // default_library_path
                                                    ANDROID_NAMESPACE_TYPE_SHARED |
                                                            ANDROID_NAMESPACE_TYPE_ISOLATED,
                                                    nullptr, // permitted_when_isolated_path
                                                    sphalNamespace);
    });
    return mDriverNamespace;
}

} // namespace android

extern "C" android_namespace_t* android_getDriverNamespace() {
    return android::GraphicsEnv::getInstance().getDriverNamespace();
}
