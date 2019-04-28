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

#define LOG_TAG "AAudioService"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <iomanip>
#include <iostream>
#include <sstream>

#include <aaudio/AAudio.h>
#include <mediautils/SchedulingPolicyService.h>
#include <utils/String16.h>

#include "binding/AAudioServiceMessage.h"
#include "AAudioClientTracker.h"
#include "AAudioEndpointManager.h"
#include "AAudioService.h"
#include "AAudioServiceStreamMMAP.h"
#include "AAudioServiceStreamShared.h"
#include "AAudioServiceStreamMMAP.h"
#include "binding/IAAudioService.h"
#include "ServiceUtilities.h"

using namespace android;
using namespace aaudio;

#define MAX_STREAMS_PER_PROCESS   8

using android::AAudioService;

android::AAudioService::AAudioService()
    : BnAAudioService() {
    mAudioClient.clientUid = getuid();   // TODO consider using geteuid()
    mAudioClient.clientPid = getpid();
    mAudioClient.packageName = String16("");
    AAudioClientTracker::getInstance().setAAudioService(this);
}

AAudioService::~AAudioService() {
}

status_t AAudioService::dump(int fd, const Vector<String16>& args) {
    std::string result;

    if (!dumpAllowed()) {
        std::stringstream ss;
        ss << "Permission denial: can't dump AAudioService from pid="
                << IPCThreadState::self()->getCallingPid() << ", uid="
                << IPCThreadState::self()->getCallingUid() << "\n";
        result = ss.str();
        ALOGW("%s", result.c_str());
    } else {
        result = "------------ AAudio Service ------------\n"
                 + mStreamTracker.dump()
                 + AAudioClientTracker::getInstance().dump()
                 + AAudioEndpointManager::getInstance().dump();
    }
    (void)write(fd, result.c_str(), result.size());
    return NO_ERROR;
}

void AAudioService::registerClient(const sp<IAAudioClient>& client) {
    pid_t pid = IPCThreadState::self()->getCallingPid();
    AAudioClientTracker::getInstance().registerClient(pid, client);
}

aaudio_handle_t AAudioService::openStream(const aaudio::AAudioStreamRequest &request,
                                          aaudio::AAudioStreamConfiguration &configurationOutput) {
    aaudio_result_t result = AAUDIO_OK;
    sp<AAudioServiceStreamBase> serviceStream;
    const AAudioStreamConfiguration &configurationInput = request.getConstantConfiguration();
    bool sharingModeMatchRequired = request.isSharingModeMatchRequired();
    aaudio_sharing_mode_t sharingMode = configurationInput.getSharingMode();

    // Enforce limit on client processes.
    pid_t pid = request.getProcessId();
    if (pid != mAudioClient.clientPid) {
        int32_t count = AAudioClientTracker::getInstance().getStreamCount(pid);
        if (count >= MAX_STREAMS_PER_PROCESS) {
            ALOGE("AAudioService::openStream(): exceeded max streams per process %d >= %d",
                  count,  MAX_STREAMS_PER_PROCESS);
            return AAUDIO_ERROR_UNAVAILABLE;
        }
    }

    if (sharingMode != AAUDIO_SHARING_MODE_EXCLUSIVE && sharingMode != AAUDIO_SHARING_MODE_SHARED) {
        ALOGE("AAudioService::openStream(): unrecognized sharing mode = %d", sharingMode);
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }

    if (sharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE) {
        // only trust audioserver for in service indication
        bool inService = false;
        if (mAudioClient.clientPid == IPCThreadState::self()->getCallingPid() &&
                mAudioClient.clientUid == IPCThreadState::self()->getCallingUid()) {
            inService = request.isInService();
        }
        serviceStream = new AAudioServiceStreamMMAP(*this, inService);
        result = serviceStream->open(request);
        if (result != AAUDIO_OK) {
            // Clear it so we can possibly fall back to using a shared stream.
            ALOGW("AAudioService::openStream(), could not open in EXCLUSIVE mode");
            serviceStream.clear();
        }
    }

    // if SHARED requested or if EXCLUSIVE failed
    if (sharingMode == AAUDIO_SHARING_MODE_SHARED
         || (serviceStream.get() == nullptr && !sharingModeMatchRequired)) {
        serviceStream =  new AAudioServiceStreamShared(*this);
        result = serviceStream->open(request);
    }

    if (result != AAUDIO_OK) {
        serviceStream.clear();
        ALOGE("AAudioService::openStream(): failed, return %d = %s",
              result, AAudio_convertResultToText(result));
        return result;
    } else {
        aaudio_handle_t handle = mStreamTracker.addStreamForHandle(serviceStream.get());
        ALOGD("AAudioService::openStream(): handle = 0x%08X", handle);
        serviceStream->setHandle(handle);
        pid_t pid = request.getProcessId();
        AAudioClientTracker::getInstance().registerClientStream(pid, serviceStream);
        configurationOutput.copyFrom(*serviceStream);
        return handle;
    }
}

aaudio_result_t AAudioService::closeStream(aaudio_handle_t streamHandle) {
    // Check permission and ownership first.
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::closeStream(0x%0x), illegal stream handle", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }

    ALOGD("AAudioService.closeStream(0x%08X)", streamHandle);
    // Remove handle from tracker so that we cannot look up the raw address any more.
    // removeStreamByHandle() uses a lock so that if there are two simultaneous closes
    // then only one will get the pointer and do the close.
    serviceStream = mStreamTracker.removeStreamByHandle(streamHandle);
    if (serviceStream.get() != nullptr) {
        serviceStream->close();
        pid_t pid = serviceStream->getOwnerProcessId();
        AAudioClientTracker::getInstance().unregisterClientStream(pid, serviceStream);
        return AAUDIO_OK;
    } else {
        ALOGW("AAudioService::closeStream(0x%0x) being handled by another thread", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
}


sp<AAudioServiceStreamBase> AAudioService::convertHandleToServiceStream(
        aaudio_handle_t streamHandle) {
    sp<AAudioServiceStreamBase> serviceStream = mStreamTracker.getStreamByHandle(streamHandle);
    if (serviceStream.get() != nullptr) {
        // Only allow owner or the aaudio service to access the stream.
        const uid_t callingUserId = IPCThreadState::self()->getCallingUid();
        const uid_t ownerUserId = serviceStream->getOwnerUserId();
        bool callerOwnsIt = callingUserId == ownerUserId;
        bool serverCalling = callingUserId == mAudioClient.clientUid;
        bool serverOwnsIt = ownerUserId == mAudioClient.clientUid;
        bool allowed = callerOwnsIt || serverCalling || serverOwnsIt;
        if (!allowed) {
            ALOGE("AAudioService: calling uid %d cannot access stream 0x%08X owned by %d",
                  callingUserId, streamHandle, ownerUserId);
            serviceStream = nullptr;
        }
    }
    return serviceStream;
}

aaudio_result_t AAudioService::getStreamDescription(
                aaudio_handle_t streamHandle,
                aaudio::AudioEndpointParcelable &parcelable) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::getStreamDescription(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }

    aaudio_result_t result = serviceStream->getDescription(parcelable);
    // parcelable.dump();
    return result;
}

aaudio_result_t AAudioService::startStream(aaudio_handle_t streamHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::startStream(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }

    return serviceStream->start();
}

aaudio_result_t AAudioService::pauseStream(aaudio_handle_t streamHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::pauseStream(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    aaudio_result_t result = serviceStream->pause();
    return result;
}

aaudio_result_t AAudioService::stopStream(aaudio_handle_t streamHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::stopStream(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    aaudio_result_t result = serviceStream->stop();
    return result;
}

aaudio_result_t AAudioService::flushStream(aaudio_handle_t streamHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::flushStream(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    return serviceStream->flush();
}

aaudio_result_t AAudioService::registerAudioThread(aaudio_handle_t streamHandle,
                                                   pid_t clientThreadId,
                                                   int64_t periodNanoseconds) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::registerAudioThread(), illegal stream handle = 0x%0x", streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    if (serviceStream->getRegisteredThread() != AAudioServiceStreamBase::ILLEGAL_THREAD_ID) {
        ALOGE("AAudioService::registerAudioThread(), thread already registered");
        return AAUDIO_ERROR_INVALID_STATE;
    }

    const pid_t ownerPid = IPCThreadState::self()->getCallingPid(); // TODO review
    serviceStream->setRegisteredThread(clientThreadId);
    int err = android::requestPriority(ownerPid, clientThreadId,
                                       DEFAULT_AUDIO_PRIORITY, true /* isForApp */);
    if (err != 0){
        ALOGE("AAudioService::registerAudioThread(%d) failed, errno = %d, priority = %d",
              clientThreadId, errno, DEFAULT_AUDIO_PRIORITY);
        return AAUDIO_ERROR_INTERNAL;
    } else {
        return AAUDIO_OK;
    }
}

aaudio_result_t AAudioService::unregisterAudioThread(aaudio_handle_t streamHandle,
                                                     pid_t clientThreadId) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::unregisterAudioThread(), illegal stream handle = 0x%0x",
              streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    if (serviceStream->getRegisteredThread() != clientThreadId) {
        ALOGE("AAudioService::unregisterAudioThread(), wrong thread");
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }
    serviceStream->setRegisteredThread(0);
    return AAUDIO_OK;
}

aaudio_result_t AAudioService::startClient(aaudio_handle_t streamHandle,
                                  const android::AudioClient& client,
                                  audio_port_handle_t *clientHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::startClient(), illegal stream handle = 0x%0x",
              streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    return serviceStream->startClient(client, clientHandle);
}

aaudio_result_t AAudioService::stopClient(aaudio_handle_t streamHandle,
                                          audio_port_handle_t clientHandle) {
    sp<AAudioServiceStreamBase> serviceStream = convertHandleToServiceStream(streamHandle);
    if (serviceStream.get() == nullptr) {
        ALOGE("AAudioService::stopClient(), illegal stream handle = 0x%0x",
              streamHandle);
        return AAUDIO_ERROR_INVALID_HANDLE;
    }
    return serviceStream->stopClient(clientHandle);
}
