/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef ANDROID_IOMXSTORE_H_

#define ANDROID_IOMXSTORE_H_

#include <media/IOMX.h>
#include <android/hardware/media/omx/1.0/IOmxStore.h>

#include <binder/IInterface.h>
#include <binder/IBinder.h>

#include <vector>
#include <string>

namespace android {

using hardware::media::omx::V1_0::IOmxStore;

class IOMXStore : public IInterface {
public:
    DECLARE_META_INTERFACE(OMXStore);

    struct Attribute {
        std::string key;
        std::string value;
    };

    struct NodeInfo {
        std::string name;
        std::string owner;
        std::vector<Attribute> attributes;
    };

    struct RoleInfo {
        std::string role;
        std::string type;
        bool isEncoder;
        bool preferPlatformNodes;
        std::vector<NodeInfo> nodes;
    };

    virtual status_t listServiceAttributes(
            std::vector<Attribute>* attributes) = 0;

    virtual status_t getNodePrefix(std::string* prefix) = 0;

    virtual status_t listRoles(std::vector<RoleInfo>* roleList) = 0;

    virtual status_t getOmx(const std::string& name, sp<IOMX>* omx) = 0;
};


////////////////////////////////////////////////////////////////////////////////

class BnOMXStore : public BnInterface<IOMXStore> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
};

}  // namespace android

#endif  // ANDROID_IOMX_H_
