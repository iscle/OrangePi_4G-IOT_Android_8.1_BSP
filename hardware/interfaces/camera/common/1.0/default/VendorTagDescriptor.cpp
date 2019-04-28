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

#define LOG_TAG "CamComm1.0-VTDesc"

#include <log/log.h>
#include <system/camera_metadata.h>
#include <camera_metadata_hidden.h>
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <utils/SortedVector.h>
#include <utils/Vector.h>

#include "VendorTagDescriptor.h"

#include <stdio.h>
#include <string.h>

namespace android {
namespace hardware {
namespace camera2 {
namespace params {

VendorTagDescriptor::~VendorTagDescriptor() {
    size_t len = mReverseMapping.size();
    for (size_t i = 0; i < len; ++i)  {
        delete mReverseMapping[i];
    }
}

VendorTagDescriptor::VendorTagDescriptor() :
        mTagCount(0),
        mVendorOps() {
}

VendorTagDescriptor::VendorTagDescriptor(const VendorTagDescriptor& src) {
    copyFrom(src);
}

VendorTagDescriptor& VendorTagDescriptor::operator=(const VendorTagDescriptor& rhs) {
    copyFrom(rhs);
    return *this;
}

void VendorTagDescriptor::copyFrom(const VendorTagDescriptor& src) {
    if (this == &src) return;

    size_t len = mReverseMapping.size();
    for (size_t i = 0; i < len; ++i) {
        delete mReverseMapping[i];
    }
    mReverseMapping.clear();

    len = src.mReverseMapping.size();
    // Have to copy KeyedVectors inside mReverseMapping
    for (size_t i = 0; i < len; ++i) {
        KeyedVector<String8, uint32_t>* nameMapper = new KeyedVector<String8, uint32_t>();
        *nameMapper = *(src.mReverseMapping.valueAt(i));
        mReverseMapping.add(src.mReverseMapping.keyAt(i), nameMapper);
    }
    // Everything else is simple
    mTagToNameMap = src.mTagToNameMap;
    mTagToSectionMap = src.mTagToSectionMap;
    mTagToTypeMap = src.mTagToTypeMap;
    mSections = src.mSections;
    mTagCount = src.mTagCount;
    mVendorOps = src.mVendorOps;
}

int VendorTagDescriptor::getTagCount() const {
    size_t size = mTagToNameMap.size();
    if (size == 0) {
        return VENDOR_TAG_COUNT_ERR;
    }
    return size;
}

void VendorTagDescriptor::getTagArray(uint32_t* tagArray) const {
    size_t size = mTagToNameMap.size();
    for (size_t i = 0; i < size; ++i) {
        tagArray[i] = mTagToNameMap.keyAt(i);
    }
}

const char* VendorTagDescriptor::getSectionName(uint32_t tag) const {
    ssize_t index = mTagToSectionMap.indexOfKey(tag);
    if (index < 0) {
        return VENDOR_SECTION_NAME_ERR;
    }
    return mSections[mTagToSectionMap.valueAt(index)].string();
}

ssize_t VendorTagDescriptor::getSectionIndex(uint32_t tag) const {
    return mTagToSectionMap.valueFor(tag);
}

const char* VendorTagDescriptor::getTagName(uint32_t tag) const {
    ssize_t index = mTagToNameMap.indexOfKey(tag);
    if (index < 0) {
        return VENDOR_TAG_NAME_ERR;
    }
    return mTagToNameMap.valueAt(index).string();
}

int VendorTagDescriptor::getTagType(uint32_t tag) const {
    ssize_t index = mTagToNameMap.indexOfKey(tag);
    if (index < 0) {
        return VENDOR_TAG_TYPE_ERR;
    }
    return mTagToTypeMap.valueFor(tag);
}

const SortedVector<String8>* VendorTagDescriptor::getAllSectionNames() const {
    return &mSections;
}

status_t VendorTagDescriptor::lookupTag(const String8& name, const String8& section, /*out*/uint32_t* tag) const {
    ssize_t index = mReverseMapping.indexOfKey(section);
    if (index < 0) {
        ALOGE("%s: Section '%s' does not exist.", __FUNCTION__, section.string());
        return BAD_VALUE;
    }

    ssize_t nameIndex = mReverseMapping[index]->indexOfKey(name);
    if (nameIndex < 0) {
        ALOGE("%s: Tag name '%s' does not exist.", __FUNCTION__, name.string());
        return BAD_VALUE;
    }

    if (tag != NULL) {
        *tag = mReverseMapping[index]->valueAt(nameIndex);
    }
    return OK;
}

void VendorTagDescriptor::dump(int fd, int verbosity, int indentation) const {

    size_t size = mTagToNameMap.size();
    if (size == 0) {
        dprintf(fd, "%*sDumping configured vendor tag descriptors: None set\n",
                indentation, "");
        return;
    }

    dprintf(fd, "%*sDumping configured vendor tag descriptors: %zu entries\n",
            indentation, "", size);
    for (size_t i = 0; i < size; ++i) {
        uint32_t tag =  mTagToNameMap.keyAt(i);

        if (verbosity < 1) {
            dprintf(fd, "%*s0x%x\n", indentation + 2, "", tag);
            continue;
        }
        String8 name = mTagToNameMap.valueAt(i);
        uint32_t sectionId = mTagToSectionMap.valueFor(tag);
        String8 sectionName = mSections[sectionId];
        int type = mTagToTypeMap.valueFor(tag);
        const char* typeName = (type >= 0 && type < NUM_TYPES) ?
                camera_metadata_type_names[type] : "UNKNOWN";
        dprintf(fd, "%*s0x%x (%s) with type %d (%s) defined in section %s\n", indentation + 2,
            "", tag, name.string(), type, typeName, sectionName.string());
    }

}

} // namespace params
} // namespace camera2

namespace camera {
namespace common {
namespace V1_0 {
namespace helper {

extern "C" {

static int vendor_tag_descriptor_get_tag_count(const vendor_tag_ops_t* v);
static void vendor_tag_descriptor_get_all_tags(const vendor_tag_ops_t* v, uint32_t* tagArray);
static const char* vendor_tag_descriptor_get_section_name(const vendor_tag_ops_t* v, uint32_t tag);
static const char* vendor_tag_descriptor_get_tag_name(const vendor_tag_ops_t* v, uint32_t tag);
static int vendor_tag_descriptor_get_tag_type(const vendor_tag_ops_t* v, uint32_t tag);

} /* extern "C" */

static Mutex sLock;
static sp<VendorTagDescriptor> sGlobalVendorTagDescriptor;

status_t VendorTagDescriptor::createDescriptorFromOps(const vendor_tag_ops_t* vOps,
            /*out*/
            sp<VendorTagDescriptor>& descriptor) {
    if (vOps == NULL) {
        ALOGE("%s: vendor_tag_ops argument was NULL.", __FUNCTION__);
        return BAD_VALUE;
    }

    int tagCount = vOps->get_tag_count(vOps);
    if (tagCount < 0 || tagCount > INT32_MAX) {
        ALOGE("%s: tag count %d from vendor ops is invalid.", __FUNCTION__, tagCount);
        return BAD_VALUE;
    }

    Vector<uint32_t> tagArray;
    LOG_ALWAYS_FATAL_IF(tagArray.resize(tagCount) != tagCount,
            "%s: too many (%u) vendor tags defined.", __FUNCTION__, tagCount);

    vOps->get_all_tags(vOps, /*out*/tagArray.editArray());

    sp<VendorTagDescriptor> desc = new VendorTagDescriptor();
    desc->mTagCount = tagCount;

    SortedVector<String8> sections;
    KeyedVector<uint32_t, String8> tagToSectionMap;

    for (size_t i = 0; i < static_cast<size_t>(tagCount); ++i) {
        uint32_t tag = tagArray[i];
        if (tag < CAMERA_METADATA_VENDOR_TAG_BOUNDARY) {
            ALOGE("%s: vendor tag %d not in vendor tag section.", __FUNCTION__, tag);
            return BAD_VALUE;
        }
        const char *tagName = vOps->get_tag_name(vOps, tag);
        if (tagName == NULL) {
            ALOGE("%s: no tag name defined for vendor tag %d.", __FUNCTION__, tag);
            return BAD_VALUE;
        }
        desc->mTagToNameMap.add(tag, String8(tagName));
        const char *sectionName = vOps->get_section_name(vOps, tag);
        if (sectionName == NULL) {
            ALOGE("%s: no section name defined for vendor tag %d.", __FUNCTION__, tag);
            return BAD_VALUE;
        }

        String8 sectionString(sectionName);

        sections.add(sectionString);
        tagToSectionMap.add(tag, sectionString);

        int tagType = vOps->get_tag_type(vOps, tag);
        if (tagType < 0 || tagType >= NUM_TYPES) {
            ALOGE("%s: tag type %d from vendor ops does not exist.", __FUNCTION__, tagType);
            return BAD_VALUE;
        }
        desc->mTagToTypeMap.add(tag, tagType);
    }

    desc->mSections = sections;

    for (size_t i = 0; i < static_cast<size_t>(tagCount); ++i) {
        uint32_t tag = tagArray[i];
        String8 sectionString = tagToSectionMap.valueFor(tag);

        // Set up tag to section index map
        ssize_t index = sections.indexOf(sectionString);
        LOG_ALWAYS_FATAL_IF(index < 0, "index %zd must be non-negative", index);
        desc->mTagToSectionMap.add(tag, static_cast<uint32_t>(index));

        // Set up reverse mapping
        ssize_t reverseIndex = -1;
        if ((reverseIndex = desc->mReverseMapping.indexOfKey(sectionString)) < 0) {
            KeyedVector<String8, uint32_t>* nameMapper = new KeyedVector<String8, uint32_t>();
            reverseIndex = desc->mReverseMapping.add(sectionString, nameMapper);
        }
        desc->mReverseMapping[reverseIndex]->add(desc->mTagToNameMap.valueFor(tag), tag);
    }

    descriptor = desc;
    return OK;
}

status_t VendorTagDescriptor::setAsGlobalVendorTagDescriptor(const sp<VendorTagDescriptor>& desc) {
    status_t res = OK;
    Mutex::Autolock al(sLock);
    sGlobalVendorTagDescriptor = desc;

    vendor_tag_ops_t* opsPtr = NULL;
    if (desc != NULL) {
        opsPtr = &(desc->mVendorOps);
        opsPtr->get_tag_count = vendor_tag_descriptor_get_tag_count;
        opsPtr->get_all_tags = vendor_tag_descriptor_get_all_tags;
        opsPtr->get_section_name = vendor_tag_descriptor_get_section_name;
        opsPtr->get_tag_name = vendor_tag_descriptor_get_tag_name;
        opsPtr->get_tag_type = vendor_tag_descriptor_get_tag_type;
    }
    if((res = set_camera_metadata_vendor_ops(opsPtr)) != OK) {
        ALOGE("%s: Could not set vendor tag descriptor, received error %s (%d)."
                , __FUNCTION__, strerror(-res), res);
    }
    return res;
}

void VendorTagDescriptor::clearGlobalVendorTagDescriptor() {
    Mutex::Autolock al(sLock);
    set_camera_metadata_vendor_ops(NULL);
    sGlobalVendorTagDescriptor.clear();
}

sp<VendorTagDescriptor> VendorTagDescriptor::getGlobalVendorTagDescriptor() {
    Mutex::Autolock al(sLock);
    return sGlobalVendorTagDescriptor;
}

extern "C" {

int vendor_tag_descriptor_get_tag_count(const vendor_tag_ops_t* /*v*/) {
    Mutex::Autolock al(sLock);
    if (sGlobalVendorTagDescriptor == NULL) {
        ALOGE("%s: Vendor tag descriptor not initialized.", __FUNCTION__);
        return VENDOR_TAG_COUNT_ERR;
    }
    return sGlobalVendorTagDescriptor->getTagCount();
}

void vendor_tag_descriptor_get_all_tags(const vendor_tag_ops_t* /*v*/, uint32_t* tagArray) {
    Mutex::Autolock al(sLock);
    if (sGlobalVendorTagDescriptor == NULL) {
        ALOGE("%s: Vendor tag descriptor not initialized.", __FUNCTION__);
        return;
    }
    sGlobalVendorTagDescriptor->getTagArray(tagArray);
}

const char* vendor_tag_descriptor_get_section_name(const vendor_tag_ops_t* /*v*/, uint32_t tag) {
    Mutex::Autolock al(sLock);
    if (sGlobalVendorTagDescriptor == NULL) {
        ALOGE("%s: Vendor tag descriptor not initialized.", __FUNCTION__);
        return VENDOR_SECTION_NAME_ERR;
    }
    return sGlobalVendorTagDescriptor->getSectionName(tag);
}

const char* vendor_tag_descriptor_get_tag_name(const vendor_tag_ops_t* /*v*/, uint32_t tag) {
    Mutex::Autolock al(sLock);
    if (sGlobalVendorTagDescriptor == NULL) {
        ALOGE("%s: Vendor tag descriptor not initialized.", __FUNCTION__);
        return VENDOR_TAG_NAME_ERR;
    }
    return sGlobalVendorTagDescriptor->getTagName(tag);
}

int vendor_tag_descriptor_get_tag_type(const vendor_tag_ops_t* /*v*/, uint32_t tag) {
    Mutex::Autolock al(sLock);
    if (sGlobalVendorTagDescriptor == NULL) {
        ALOGE("%s: Vendor tag descriptor not initialized.", __FUNCTION__);
        return VENDOR_TAG_TYPE_ERR;
    }
    return sGlobalVendorTagDescriptor->getTagType(tag);
}

} /* extern "C" */

} // namespace helper
} // namespace V1_0
} // namespace common
} // namespace camera
} // namespace hardware
} // namespace android
