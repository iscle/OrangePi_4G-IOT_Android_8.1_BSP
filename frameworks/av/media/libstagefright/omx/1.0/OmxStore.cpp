/*
 * Copyright 2017, The Android Open Source Project
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

#include <ios>
#include <list>

#include <android-base/logging.h>

#include <media/stagefright/omx/1.0/Conversion.h>
#include <media/stagefright/omx/1.0/OmxStore.h>
#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {
namespace implementation {

OmxStore::OmxStore(
        const char* owner,
        const char* const* searchDirs,
        const char* mainXmlName,
        const char* performanceXmlName,
        const char* profilingResultsXmlPath) {
    MediaCodecsXmlParser parser(searchDirs,
            mainXmlName,
            performanceXmlName,
            profilingResultsXmlPath);
    mParsingStatus = toStatus(parser.getParsingStatus());

    const auto& serviceAttributeMap = parser.getServiceAttributeMap();
    mServiceAttributeList.resize(serviceAttributeMap.size());
    size_t i = 0;
    for (const auto& attributePair : serviceAttributeMap) {
        ServiceAttribute attribute;
        attribute.key = attributePair.first;
        attribute.value = attributePair.second;
        mServiceAttributeList[i] = std::move(attribute);
        ++i;
    }

    const auto& roleMap = parser.getRoleMap();
    mRoleList.resize(roleMap.size());
    i = 0;
    for (const auto& rolePair : roleMap) {
        RoleInfo role;
        role.role = rolePair.first;
        role.type = rolePair.second.type;
        role.isEncoder = rolePair.second.isEncoder;
        // TODO: Currently, preferPlatformNodes information is not available in
        // the xml file. Once we have a way to provide this information, it
        // should be parsed properly.
        // force preferPlatformNodes to 0, in order to use mediatek audio decoder
        //role.preferPlatformNodes = rolePair.first.compare(0, 5, "audio") == 0;
        role.preferPlatformNodes = 0;
        hidl_vec<NodeInfo>& nodeList = role.nodes;
        nodeList.resize(rolePair.second.nodeList.size());
        size_t j = 0;
        for (const auto& nodePair : rolePair.second.nodeList) {
            NodeInfo node;
            node.name = nodePair.second.name;
            node.owner = owner;
            hidl_vec<NodeAttribute>& attributeList = node.attributes;
            attributeList.resize(nodePair.second.attributeList.size());
            size_t k = 0;
            for (const auto& attributePair : nodePair.second.attributeList) {
                NodeAttribute attribute;
                attribute.key = attributePair.first;
                attribute.value = attributePair.second;
                attributeList[k] = std::move(attribute);
                ++k;
            }
            nodeList[j] = std::move(node);
            ++j;
        }
        mRoleList[i] = std::move(role);
        ++i;
    }

    mPrefix = parser.getCommonPrefix();
}

OmxStore::~OmxStore() {
}

Return<void> OmxStore::listServiceAttributes(listServiceAttributes_cb _hidl_cb) {
    if (mParsingStatus == Status::NO_ERROR) {
        _hidl_cb(Status::NO_ERROR, mServiceAttributeList);
    } else {
        _hidl_cb(mParsingStatus, hidl_vec<ServiceAttribute>());
    }
    return Void();
}

Return<void> OmxStore::getNodePrefix(getNodePrefix_cb _hidl_cb) {
    _hidl_cb(mPrefix);
    return Void();
}

Return<void> OmxStore::listRoles(listRoles_cb _hidl_cb) {
    _hidl_cb(mRoleList);
    return Void();
}

Return<sp<IOmx>> OmxStore::getOmx(hidl_string const& omxName) {
    return IOmx::tryGetService(omxName);
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android
