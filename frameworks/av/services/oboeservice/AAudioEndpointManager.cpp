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

#define LOG_TAG "AAudioEndpointManager"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <assert.h>
#include <functional>
#include <map>
#include <mutex>
#include <sstream>
#include <utility/AAudioUtilities.h>

#include "AAudioEndpointManager.h"
#include "AAudioServiceEndpointShared.h"
#include "AAudioServiceEndpointMMAP.h"
#include "AAudioServiceEndpointCapture.h"
#include "AAudioServiceEndpointPlay.h"

using namespace android;
using namespace aaudio;

ANDROID_SINGLETON_STATIC_INSTANCE(AAudioEndpointManager);

AAudioEndpointManager::AAudioEndpointManager()
        : Singleton<AAudioEndpointManager>()
        , mSharedStreams()
        , mExclusiveStreams() {
}

std::string AAudioEndpointManager::dump() const {
    std::stringstream result;
    int index = 0;

    result << "AAudioEndpointManager:" << "\n";

    const bool isSharedLocked = AAudio_tryUntilTrue(
            [this]()->bool { return mSharedLock.try_lock(); } /* f */,
            50 /* times */,
            20 /* sleepMs */);
    if (!isSharedLocked) {
        result << "AAudioEndpointManager Shared may be deadlocked\n";
    }

    {
        const bool isExclusiveLocked = AAudio_tryUntilTrue(
                [this]() -> bool { return mExclusiveLock.try_lock(); } /* f */,
                50 /* times */,
                20 /* sleepMs */);
        if (!isExclusiveLocked) {
            result << "AAudioEndpointManager Exclusive may be deadlocked\n";
        }

        result << "Exclusive MMAP Endpoints: " << mExclusiveStreams.size() << "\n";
        index = 0;
        for (const auto &output : mExclusiveStreams) {
            result << "  #" << index++ << ":";
            result << output->dump() << "\n";
        }

        if (isExclusiveLocked) {
            mExclusiveLock.unlock();
        }
    }

    result << "Shared Endpoints: " << mSharedStreams.size() << "\n";
    index = 0;
    for (const auto &input : mSharedStreams) {
        result << "  #" << index++ << ":";
        result << input->dump() << "\n";
    }

    if (isSharedLocked) {
        mSharedLock.unlock();
    }
    return result.str();
}


// Try to find an existing endpoint.
sp<AAudioServiceEndpoint> AAudioEndpointManager::findExclusiveEndpoint_l(
        const AAudioStreamConfiguration &configuration) {
    sp<AAudioServiceEndpoint> endpoint;
    for (const auto ep : mExclusiveStreams) {
        if (ep->matches(configuration)) {
            endpoint = ep;
            break;
        }
    }

    ALOGV("AAudioEndpointManager.findExclusiveEndpoint_l(), found %p for device = %d",
          endpoint.get(), configuration.getDeviceId());
    return endpoint;
}

// Try to find an existing endpoint.
sp<AAudioServiceEndpointShared> AAudioEndpointManager::findSharedEndpoint_l(
        const AAudioStreamConfiguration &configuration) {
    sp<AAudioServiceEndpointShared> endpoint;
    for (const auto ep  : mSharedStreams) {
        if (ep->matches(configuration)) {
            endpoint = ep;
            break;
        }
    }

    ALOGV("AAudioEndpointManager.findSharedEndpoint_l(), found %p for device = %d",
          endpoint.get(), configuration.getDeviceId());
    return endpoint;
}

sp<AAudioServiceEndpoint> AAudioEndpointManager::openEndpoint(AAudioService &audioService,
                                        const aaudio::AAudioStreamRequest &request,
                                        aaudio_sharing_mode_t sharingMode) {
    if (sharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE) {
        return openExclusiveEndpoint(audioService, request);
    } else {
        return openSharedEndpoint(audioService, request);
    }
}

sp<AAudioServiceEndpoint> AAudioEndpointManager::openExclusiveEndpoint(
        AAudioService &aaudioService __unused,
        const aaudio::AAudioStreamRequest &request) {

    std::lock_guard<std::mutex> lock(mExclusiveLock);

    const AAudioStreamConfiguration &configuration = request.getConstantConfiguration();

    // Try to find an existing endpoint.
    sp<AAudioServiceEndpoint> endpoint = findExclusiveEndpoint_l(configuration);

    // If we find an existing one then this one cannot be exclusive.
    if (endpoint.get() != nullptr) {
        ALOGE("AAudioEndpointManager.openExclusiveEndpoint() already in use");
        // Already open so do not allow a second stream.
        return nullptr;
    } else {
        sp<AAudioServiceEndpointMMAP> endpointMMap = new AAudioServiceEndpointMMAP();
        ALOGE("AAudioEndpointManager.openEndpoint(),created MMAP %p", endpointMMap.get());
        endpoint = endpointMMap;

        aaudio_result_t result = endpoint->open(request);
        if (result != AAUDIO_OK) {
            ALOGE("AAudioEndpointManager.openEndpoint(), open failed");
            endpoint.clear();
        } else {
            mExclusiveStreams.push_back(endpointMMap);
        }

        ALOGD("AAudioEndpointManager.openEndpoint(), created %p for device = %d",
              endpoint.get(), configuration.getDeviceId());
    }

    if (endpoint.get() != nullptr) {
        // Increment the reference count under this lock.
        endpoint->setOpenCount(endpoint->getOpenCount() + 1);
    }
    return endpoint;
}

sp<AAudioServiceEndpoint> AAudioEndpointManager::openSharedEndpoint(
        AAudioService &aaudioService,
        const aaudio::AAudioStreamRequest &request) {

    std::lock_guard<std::mutex> lock(mSharedLock);

    const AAudioStreamConfiguration &configuration = request.getConstantConfiguration();
    aaudio_direction_t direction = configuration.getDirection();

    // Try to find an existing endpoint.
    sp<AAudioServiceEndpointShared> endpoint = findSharedEndpoint_l(configuration);

    // If we can't find an existing one then open a new one.
    if (endpoint.get() == nullptr) {
        // we must call openStream with audioserver identity
        int64_t token = IPCThreadState::self()->clearCallingIdentity();
        switch (direction) {
            case AAUDIO_DIRECTION_INPUT:
                endpoint = new AAudioServiceEndpointCapture(aaudioService);
                break;
            case AAUDIO_DIRECTION_OUTPUT:
                endpoint = new AAudioServiceEndpointPlay(aaudioService);
                break;
            default:
                break;
        }

        if (endpoint.get() != nullptr) {
            aaudio_result_t result = endpoint->open(request);
            if (result != AAUDIO_OK) {
                ALOGE("AAudioEndpointManager.openEndpoint(), open failed");
                endpoint.clear();
            } else {
                mSharedStreams.push_back(endpoint);
            }
        }
        ALOGD("AAudioEndpointManager.openSharedEndpoint(), created %p for device = %d, dir = %d",
              endpoint.get(), configuration.getDeviceId(), (int)direction);
        IPCThreadState::self()->restoreCallingIdentity(token);
    }

    if (endpoint.get() != nullptr) {
        // Increment the reference count under this lock.
        endpoint->setOpenCount(endpoint->getOpenCount() + 1);
    }
    return endpoint;
}

void AAudioEndpointManager::closeEndpoint(sp<AAudioServiceEndpoint>serviceEndpoint) {
    if (serviceEndpoint->getSharingMode() == AAUDIO_SHARING_MODE_EXCLUSIVE) {
        return closeExclusiveEndpoint(serviceEndpoint);
    } else {
        return closeSharedEndpoint(serviceEndpoint);
    }
}

void AAudioEndpointManager::closeExclusiveEndpoint(sp<AAudioServiceEndpoint> serviceEndpoint) {
    if (serviceEndpoint.get() == nullptr) {
        return;
    }

    // Decrement the reference count under this lock.
    std::lock_guard<std::mutex> lock(mExclusiveLock);
    int32_t newRefCount = serviceEndpoint->getOpenCount() - 1;
    serviceEndpoint->setOpenCount(newRefCount);

    // If no longer in use then close and delete it.
    if (newRefCount <= 0) {
        mExclusiveStreams.erase(
                std::remove(mExclusiveStreams.begin(), mExclusiveStreams.end(), serviceEndpoint),
                mExclusiveStreams.end());

        serviceEndpoint->close();
        ALOGD("AAudioEndpointManager::closeExclusiveEndpoint() %p for device %d",
              serviceEndpoint.get(), serviceEndpoint->getDeviceId());
    }
}

void AAudioEndpointManager::closeSharedEndpoint(sp<AAudioServiceEndpoint> serviceEndpoint) {
    if (serviceEndpoint.get() == nullptr) {
        return;
    }

    // Decrement the reference count under this lock.
    std::lock_guard<std::mutex> lock(mSharedLock);
    int32_t newRefCount = serviceEndpoint->getOpenCount() - 1;
    serviceEndpoint->setOpenCount(newRefCount);

    // If no longer in use then close and delete it.
    if (newRefCount <= 0) {
        mSharedStreams.erase(
                std::remove(mSharedStreams.begin(), mSharedStreams.end(), serviceEndpoint),
                mSharedStreams.end());

        serviceEndpoint->close();
        ALOGD("AAudioEndpointManager::closeSharedEndpoint() %p for device %d",
              serviceEndpoint.get(), serviceEndpoint->getDeviceId());
    }
}
