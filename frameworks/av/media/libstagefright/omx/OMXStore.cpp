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

//#define LOG_NDEBUG 0
#define LOG_TAG "OMXStore"
#include <utils/Log.h>

#include <media/stagefright/omx/OMXUtils.h>
#include <media/stagefright/omx/OMX.h>
#include <media/stagefright/omx/OMXStore.h>
#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>

#include <map>
#include <string>

namespace android {

namespace {
    struct RoleProperties {
        std::string type;
        bool isEncoder;
        bool preferPlatformNodes;
        std::multimap<size_t, IOMXStore::NodeInfo> nodeList;
    };
}  // Unnamed namespace

OMXStore::OMXStore(
        const char* owner,
        const char* const* searchDirs,
        const char* mainXmlName,
        const char* performanceXmlName,
        const char* profilingResultsXmlPath) {
    MediaCodecsXmlParser parser(
            searchDirs,
            mainXmlName,
            performanceXmlName,
            profilingResultsXmlPath);
    mParsingStatus = parser.getParsingStatus();

    const auto& serviceAttributeMap = parser.getServiceAttributeMap();
    mServiceAttributeList.reserve(serviceAttributeMap.size());
    for (const auto& attributePair : serviceAttributeMap) {
        Attribute attribute;
        attribute.key = attributePair.first;
        attribute.value = attributePair.second;
        mServiceAttributeList.push_back(std::move(attribute));
    }

    const auto& roleMap = parser.getRoleMap();
    mRoleList.reserve(roleMap.size());
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
        std::vector<NodeInfo>& nodeList = role.nodes;
        nodeList.reserve(rolePair.second.nodeList.size());
        for (const auto& nodePair : rolePair.second.nodeList) {
            NodeInfo node;
            node.name = nodePair.second.name;
            node.owner = owner;
            std::vector<Attribute>& attributeList = node.attributes;
            attributeList.reserve(nodePair.second.attributeList.size());
            for (const auto& attributePair : nodePair.second.attributeList) {
                Attribute attribute;
                attribute.key = attributePair.first;
                attribute.value = attributePair.second;
                attributeList.push_back(std::move(attribute));
            }
            nodeList.push_back(std::move(node));
        }
        mRoleList.push_back(std::move(role));
    }

    mPrefix = parser.getCommonPrefix();
}

status_t OMXStore::listServiceAttributes(std::vector<Attribute>* attributes) {
    *attributes = mServiceAttributeList;
    return mParsingStatus;
}

status_t OMXStore::getNodePrefix(std::string* prefix) {
    *prefix = mPrefix;
    return mParsingStatus;
}

status_t OMXStore::listRoles(std::vector<RoleInfo>* roleList) {
    *roleList = mRoleList;
    return mParsingStatus;
}

status_t OMXStore::getOmx(const std::string& name, sp<IOMX>* omx) {
    *omx = new OMX();
    return NO_ERROR;
}

OMXStore::~OMXStore() {
}

}  // namespace android

