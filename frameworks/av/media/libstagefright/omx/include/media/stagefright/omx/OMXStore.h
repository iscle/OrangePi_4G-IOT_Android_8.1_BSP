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

#ifndef ANDROID_OMXSTORE_H_
#define ANDROID_OMXSTORE_H_

#include <media/IOMXStore.h>
#include <media/IOMX.h>
#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>

#include <vector>
#include <string>

namespace android {

class OMXStore : public BnOMXStore {
public:
    OMXStore(
            const char* owner = "default",
            const char* const* searchDirs
                = MediaCodecsXmlParser::defaultSearchDirs,
            const char* mainXmlName
                = MediaCodecsXmlParser::defaultMainXmlName,
            const char* performanceXmlName
                = MediaCodecsXmlParser::defaultPerformanceXmlName,
            const char* profilingResultsXmlPath
                = MediaCodecsXmlParser::defaultProfilingResultsXmlPath);

    status_t listServiceAttributes(
            std::vector<Attribute>* attributes) override;

    status_t getNodePrefix(std::string* prefix) override;

    status_t listRoles(std::vector<RoleInfo>* roleList) override;

    status_t getOmx(const std::string& name, sp<IOMX>* omx) override;

    ~OMXStore() override;

protected:
    status_t mParsingStatus;
    std::string mPrefix;
    std::vector<Attribute> mServiceAttributeList;
    std::vector<RoleInfo> mRoleList;
};

}  // namespace android

#endif  // ANDROID_OMXSTORE_H_
