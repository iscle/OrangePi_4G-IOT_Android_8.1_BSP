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


#define LOG_TAG "AAudioService"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <assert.h>
#include <binder/IPCThreadState.h>
#include <map>
#include <mutex>
#include <utils/Singleton.h>

#include "utility/AAudioUtilities.h"
#include "AAudioEndpointManager.h"
#include "AAudioServiceEndpoint.h"
#include "AAudioClientTracker.h"

using namespace android;
using namespace aaudio;

ANDROID_SINGLETON_STATIC_INSTANCE(AAudioClientTracker);

AAudioClientTracker::AAudioClientTracker()
        : Singleton<AAudioClientTracker>() {
}


std::string AAudioClientTracker::dump() const {
    std::stringstream result;
    const bool isLocked = AAudio_tryUntilTrue(
            [this]()->bool { return mLock.try_lock(); } /* f */,
            50 /* times */,
            20 /* sleepMs */);
    if (!isLocked) {
        result << "AAudioClientTracker may be deadlocked\n";
    }

    result << "AAudioClientTracker:\n";
    for (const auto&  it : mNotificationClients) {
        result << it.second->dump();
    }

    if (isLocked) {
        mLock.unlock();
    }
    return result.str();
}

// Create a tracker for the client.
aaudio_result_t AAudioClientTracker::registerClient(pid_t pid,
                                         const sp<IAAudioClient>& client) {
    ALOGV("AAudioClientTracker::registerClient(), calling pid = %d, getpid() = %d\n",
          pid, getpid());

    std::lock_guard<std::mutex> lock(mLock);
    if (mNotificationClients.count(pid) == 0) {
        sp<NotificationClient> notificationClient = new NotificationClient(pid);
        mNotificationClients[pid] = notificationClient;

        sp<IBinder> binder = IInterface::asBinder(client);
        status_t status = binder->linkToDeath(notificationClient);
        ALOGW_IF(status != NO_ERROR,
                 "AAudioClientTracker::registerClient() linkToDeath = %d\n", status);
        return AAudioConvert_androidToAAudioResult(status);
    } else {
        ALOGW("AAudioClientTracker::registerClient(%d) already registered!", pid);
        return AAUDIO_OK; // TODO should this be considered an error
    }
}

void AAudioClientTracker::unregisterClient(pid_t pid) {
    ALOGV("AAudioClientTracker::unregisterClient(), calling pid = %d, getpid() = %d\n",
          pid, getpid());
    std::lock_guard<std::mutex> lock(mLock);
    mNotificationClients.erase(pid);
}

int32_t AAudioClientTracker::getStreamCount(pid_t pid) {
    std::lock_guard<std::mutex> lock(mLock);
    auto it = mNotificationClients.find(pid);
    if (it != mNotificationClients.end()) {
        return it->second->getStreamCount();
    } else {
        return 0; // no existing client
    }
}

aaudio_result_t
AAudioClientTracker::registerClientStream(pid_t pid, sp<AAudioServiceStreamBase> serviceStream) {
    aaudio_result_t result = AAUDIO_OK;
    ALOGV("AAudioClientTracker::registerClientStream(%d, %p)\n", pid, serviceStream.get());
    std::lock_guard<std::mutex> lock(mLock);
    sp<NotificationClient> notificationClient = mNotificationClients[pid];
    if (notificationClient == 0) {
        // This will get called the first time the audio server registers an internal stream.
        ALOGV("AAudioClientTracker::registerClientStream(%d,) unrecognized pid\n", pid);
        notificationClient = new NotificationClient(pid);
        mNotificationClients[pid] = notificationClient;
    }
    notificationClient->registerClientStream(serviceStream);
    return result;
}

// Find the tracker for this process and remove it.
aaudio_result_t
AAudioClientTracker::unregisterClientStream(pid_t pid,
                                            sp<AAudioServiceStreamBase> serviceStream) {
    ALOGV("AAudioClientTracker::unregisterClientStream(%d, %p)\n", pid, serviceStream.get());
    std::lock_guard<std::mutex> lock(mLock);
    auto it = mNotificationClients.find(pid);
    if (it != mNotificationClients.end()) {
        ALOGV("AAudioClientTracker::unregisterClientStream(%d, %p) found NotificationClient\n",
              pid, serviceStream.get());
        it->second->unregisterClientStream(serviceStream);
    } else {
        ALOGE("AAudioClientTracker::unregisterClientStream(%d, %p) missing NotificationClient\n",
              pid, serviceStream.get());
    }
    return AAUDIO_OK;
}

AAudioClientTracker::NotificationClient::NotificationClient(pid_t pid)
        : mProcessId(pid) {
    //ALOGD("AAudioClientTracker::NotificationClient(%d) created %p\n", pid, this);
}

AAudioClientTracker::NotificationClient::~NotificationClient() {
    //ALOGD("AAudioClientTracker::~NotificationClient() destroyed %p\n", this);
}

int32_t AAudioClientTracker::NotificationClient::getStreamCount() {
    std::lock_guard<std::mutex> lock(mLock);
    return mStreams.size();
}

aaudio_result_t AAudioClientTracker::NotificationClient::registerClientStream(
        sp<AAudioServiceStreamBase> serviceStream) {
    std::lock_guard<std::mutex> lock(mLock);
    mStreams.insert(serviceStream);
    return AAUDIO_OK;
}

aaudio_result_t AAudioClientTracker::NotificationClient::unregisterClientStream(
        sp<AAudioServiceStreamBase> serviceStream) {
    std::lock_guard<std::mutex> lock(mLock);
    mStreams.erase(serviceStream);
    return AAUDIO_OK;
}

// Close any open streams for the client.
void AAudioClientTracker::NotificationClient::binderDied(const wp<IBinder>& who __unused) {
    AAudioService *aaudioService = AAudioClientTracker::getInstance().getAAudioService();
    if (aaudioService != nullptr) {
        // Copy the current list of streams to another vector because closing them below
        // will cause unregisterClientStream() calls back to this object.
        std::set<sp<AAudioServiceStreamBase>>  streamsToClose;

        {
            std::lock_guard<std::mutex> lock(mLock);
            for (auto serviceStream : mStreams) {
                streamsToClose.insert(serviceStream);
            }
        }

        for (auto serviceStream : streamsToClose) {
            aaudio_handle_t handle = serviceStream->getHandle();
            ALOGW("AAudioClientTracker::binderDied() close abandoned stream 0x%08X\n", handle);
            aaudioService->closeStream(handle);
        }
        // mStreams should be empty now
    }
    sp<NotificationClient> keep(this);
    AAudioClientTracker::getInstance().unregisterClient(mProcessId);
}


std::string AAudioClientTracker::NotificationClient::dump() const {
    std::stringstream result;
    const bool isLocked = AAudio_tryUntilTrue(
            [this]()->bool { return mLock.try_lock(); } /* f */,
            50 /* times */,
            20 /* sleepMs */);
    if (!isLocked) {
        result << "AAudioClientTracker::NotificationClient may be deadlocked\n";
    }

    result << "  client: pid = " << mProcessId << " has " << mStreams.size() << " streams\n";
    for (auto serviceStream : mStreams) {
        result << "     stream: 0x" << std::hex << serviceStream->getHandle() << std::dec << "\n";
    }

    if (isLocked) {
        mLock.unlock();
    }
    return result.str();
}
