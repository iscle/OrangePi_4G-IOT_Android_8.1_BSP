/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaCodecList"
#include <utils/Log.h>

#include "MediaCodecListOverrides.h"

#include <binder/IServiceManager.h>

#include <media/IMediaCodecList.h>
#include <media/IMediaPlayerService.h>
#include <media/IMediaCodecService.h>
#include <media/MediaCodecInfo.h>
#include <media/MediaDefs.h>

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaCodecList.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/OmxInfoBuilder.h>
#include <media/stagefright/omx/OMXUtils.h>
#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>

#include <sys/stat.h>
#include <utils/threads.h>

#include <cutils/properties.h>
#include <expat.h>

#include <algorithm>

namespace android {

namespace {

Mutex sInitMutex;

Mutex sRemoteInitMutex;

constexpr const char* kProfilingResults =
        MediaCodecsXmlParser::defaultProfilingResultsXmlPath;

bool isProfilingNeeded() {
    int8_t value = property_get_bool("debug.stagefright.profilecodec", 0);
    if (value == 0) {
        return false;
    }

    bool profilingNeeded = true;
    FILE *resultsFile = fopen(kProfilingResults, "r");
    if (resultsFile) {
        AString currentVersion = getProfilingVersionString();
        size_t currentVersionSize = currentVersion.size();
        char *versionString = new char[currentVersionSize + 1];
        fgets(versionString, currentVersionSize + 1, resultsFile);
        if (strcmp(versionString, currentVersion.c_str()) == 0) {
            // profiling result up to date
            profilingNeeded = false;
        }
        fclose(resultsFile);
        delete[] versionString;
    }
    return profilingNeeded;
}

OmxInfoBuilder sOmxInfoBuilder;

}  // unnamed namespace

// static
sp<IMediaCodecList> MediaCodecList::sCodecList;

// static
void *MediaCodecList::profilerThreadWrapper(void * /*arg*/) {
    ALOGV("Enter profilerThreadWrapper.");
    remove(kProfilingResults);  // remove previous result so that it won't be loaded to
                                // the new MediaCodecList
    sp<MediaCodecList> codecList(new MediaCodecList(&sOmxInfoBuilder));
    if (codecList->initCheck() != OK) {
        ALOGW("Failed to create a new MediaCodecList, skipping codec profiling.");
        return nullptr;
    }

    const auto& infos = codecList->mCodecInfos;
    ALOGV("Codec profiling started.");
    profileCodecs(infos, kProfilingResults);
    ALOGV("Codec profiling completed.");
    codecList = new MediaCodecList(&sOmxInfoBuilder);
    if (codecList->initCheck() != OK) {
        ALOGW("Failed to parse profiling results.");
        return nullptr;
    }

    {
        Mutex::Autolock autoLock(sInitMutex);
        sCodecList = codecList;
    }
    return nullptr;
}

// static
sp<IMediaCodecList> MediaCodecList::getLocalInstance() {
    Mutex::Autolock autoLock(sInitMutex);

    if (sCodecList == nullptr) {
        MediaCodecList *codecList = new MediaCodecList(&sOmxInfoBuilder);
        if (codecList->initCheck() == OK) {
            sCodecList = codecList;

            if (isProfilingNeeded()) {
                ALOGV("Codec profiling needed, will be run in separated thread.");
                pthread_t profiler;
                if (pthread_create(&profiler, nullptr, profilerThreadWrapper, nullptr) != 0) {
                    ALOGW("Failed to create thread for codec profiling.");
                }
            }
        } else {
            // failure to initialize may be temporary. retry on next call.
            delete codecList;
        }
    }

    return sCodecList;
}

sp<IMediaCodecList> MediaCodecList::sRemoteList;

sp<MediaCodecList::BinderDeathObserver> MediaCodecList::sBinderDeathObserver;

void MediaCodecList::BinderDeathObserver::binderDied(const wp<IBinder> &who __unused) {
    Mutex::Autolock _l(sRemoteInitMutex);
    sRemoteList.clear();
    sBinderDeathObserver.clear();
}

// static
sp<IMediaCodecList> MediaCodecList::getInstance() {
    Mutex::Autolock _l(sRemoteInitMutex);
    if (sRemoteList == nullptr) {
        sp<IBinder> binder =
            defaultServiceManager()->getService(String16("media.player"));
        sp<IMediaPlayerService> service =
            interface_cast<IMediaPlayerService>(binder);
        if (service.get() != nullptr) {
            sRemoteList = service->getCodecList();
            if (sRemoteList != nullptr) {
                sBinderDeathObserver = new BinderDeathObserver();
                binder->linkToDeath(sBinderDeathObserver.get());
            }
        }
        if (sRemoteList == nullptr) {
            // if failed to get remote list, create local list
            sRemoteList = getLocalInstance();
        }
    }
    return sRemoteList;
}

MediaCodecList::MediaCodecList(MediaCodecListBuilderBase* builder) {
    mGlobalSettings = new AMessage();
    mCodecInfos.clear();
    MediaCodecListWriter writer(this);
    mInitCheck = builder->buildMediaCodecList(&writer);
}

MediaCodecList::~MediaCodecList() {
}

status_t MediaCodecList::initCheck() const {
    return mInitCheck;
}

MediaCodecListWriter::MediaCodecListWriter(MediaCodecList* list) :
    mList(list) {
}

void MediaCodecListWriter::addGlobalSetting(
        const char* key, const char* value) {
    mList->mGlobalSettings->setString(key, value);
}

std::unique_ptr<MediaCodecInfoWriter>
        MediaCodecListWriter::addMediaCodecInfo() {
    sp<MediaCodecInfo> info = new MediaCodecInfo();
    mList->mCodecInfos.push_back(info);
    return std::unique_ptr<MediaCodecInfoWriter>(
            new MediaCodecInfoWriter(info.get()));
}

// legacy method for non-advanced codecs
ssize_t MediaCodecList::findCodecByType(
        const char *type, bool encoder, size_t startIndex) const {
    static const char *advancedFeatures[] = {
        "feature-secure-playback",
        "feature-tunneled-playback",
    };

    size_t numCodecInfos = mCodecInfos.size();
    for (; startIndex < numCodecInfos; ++startIndex) {
        const MediaCodecInfo &info = *mCodecInfos[startIndex];

        if (info.isEncoder() != encoder) {
            continue;
        }
        sp<MediaCodecInfo::Capabilities> capabilities = info.getCapabilitiesFor(type);
        if (capabilities == nullptr) {
            continue;
        }
        const sp<AMessage> &details = capabilities->getDetails();

        int32_t required;
        bool isAdvanced = false;
        for (size_t ix = 0; ix < ARRAY_SIZE(advancedFeatures); ix++) {
            if (details->findInt32(advancedFeatures[ix], &required) &&
                    required != 0) {
                isAdvanced = true;
                break;
            }
        }

        if (!isAdvanced) {
            return startIndex;
        }
    }

    return -ENOENT;
}

ssize_t MediaCodecList::findCodecByName(const char *name) const {
    for (size_t i = 0; i < mCodecInfos.size(); ++i) {
        if (strcmp(mCodecInfos[i]->getCodecName(), name) == 0) {
            return i;
        }
    }

    return -ENOENT;
}

size_t MediaCodecList::countCodecs() const {
    return mCodecInfos.size();
}

const sp<AMessage> MediaCodecList::getGlobalSettings() const {
    return mGlobalSettings;
}

//static
bool MediaCodecList::isSoftwareCodec(const AString &componentName) {
    return componentName.startsWithIgnoreCase("OMX.google.")
        || !componentName.startsWithIgnoreCase("OMX.");
}

static int compareSoftwareCodecsFirst(const AString *name1, const AString *name2) {
    // sort order 1: software codecs are first (lower)
    bool isSoftwareCodec1 = MediaCodecList::isSoftwareCodec(*name1);
    bool isSoftwareCodec2 = MediaCodecList::isSoftwareCodec(*name2);
    if (isSoftwareCodec1 != isSoftwareCodec2) {
        return isSoftwareCodec2 - isSoftwareCodec1;
    }

    // sort order 2: OMX codecs are first (lower)
    bool isOMX1 = name1->startsWithIgnoreCase("OMX.");
    bool isOMX2 = name2->startsWithIgnoreCase("OMX.");
    return isOMX2 - isOMX1;
}

//static
void MediaCodecList::findMatchingCodecs(
        const char *mime, bool encoder, uint32_t flags,
        Vector<AString> *matches, Vector<AString> *owners) {
    matches->clear();
    if (owners != nullptr) {
        owners->clear();
    }

    const sp<IMediaCodecList> list = getInstance();
    if (list == nullptr) {
        return;
    }

    size_t index = 0;
    for (;;) {
        ssize_t matchIndex =
            list->findCodecByType(mime, encoder, index);

        if (matchIndex < 0) {
            break;
        }

        index = matchIndex + 1;

        const sp<MediaCodecInfo> info = list->getCodecInfo(matchIndex);
        CHECK(info != nullptr);
        AString componentName = info->getCodecName();

        if ((flags & kHardwareCodecsOnly) && isSoftwareCodec(componentName)) {
            ALOGV("skipping SW codec '%s'", componentName.c_str());
        } else {
            matches->push(componentName);
            if (owners != nullptr) {
                owners->push(AString(info->getOwnerName()));
            }
            ALOGV("matching '%s'", componentName.c_str());
        }
    }

    if (flags & kPreferSoftwareCodecs ||
            property_get_bool("debug.stagefright.swcodec", false)) {
        matches->sort(compareSoftwareCodecsFirst);
    }
}

MediaCodecListBuilderBase::~MediaCodecListBuilderBase() {
}

}  // namespace android
