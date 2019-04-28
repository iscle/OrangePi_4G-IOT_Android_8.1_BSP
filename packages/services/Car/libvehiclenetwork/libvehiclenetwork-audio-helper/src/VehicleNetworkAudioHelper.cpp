/*
 * Copyright (C) 2015 The Android Open Source Project
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
#define LOG_TAG "VehicleNetworkAudioHelper"

#include <VehicleNetwork.h>
#include <vehicle-internal.h>
#include <utils/SystemClock.h>
#include "VehicleNetworkAudioHelper.h"

//#define DBG
#ifdef DBG
#define LOGD(x...) ALOGD(x)
#else
#define LOGD(x...)
#endif
namespace android {

// ----------------------------------------------------------------------------

VehicleNetworkAudioHelper::VehicleNetworkAudioHelper(int64_t timeoutNs)
    : mTimeoutNs(timeoutNs),
      mListener(NULL),
      mHasFocusProperty(false) {
}

VehicleNetworkAudioHelper::VehicleNetworkAudioHelper(int64_t timeoutNs,
        sp<VehicleNetworkAudioFocusListener> listener)
    : mTimeoutNs(timeoutNs),
      mListener(listener),
      mHasFocusProperty(false) {
}

VehicleNetworkAudioHelper::~VehicleNetworkAudioHelper() {
    // nothing to do
}

status_t VehicleNetworkAudioHelper::init() {
    Mutex::Autolock autoLock(mLock);
    sp<VehicleNetworkListener> listener(this);
    mService = VehicleNetwork::createVehicleNetwork(listener);
    mScratchValueStreamState.prop = VEHICLE_PROPERTY_INTERNAL_AUDIO_STREAM_STATE;
    mScratchValueStreamState.value_type = VEHICLE_VALUE_TYPE_INT32_VEC2;
    mScratchValueStreamState.timestamp = 0;
    mScratchValueFocus.prop = VEHICLE_PROPERTY_AUDIO_FOCUS;
    mScratchValueFocus.value_type = VEHICLE_VALUE_TYPE_INT32_VEC4;
    mScratchValueFocus.timestamp = 0;
    updatePropertiesLocked();
    return NO_ERROR;
}

void VehicleNetworkAudioHelper::updatePropertiesLocked() {
    sp<VehiclePropertiesHolder> holder = mService->listProperties(VEHICLE_PROPERTY_AUDIO_FOCUS);
    if (holder.get() != NULL && holder->getList().size() == 1) {
        mHasFocusProperty = true;
        mService->subscribe(VEHICLE_PROPERTY_AUDIO_FOCUS, 0);
        mService->getProperty(&mScratchValueFocus);
        mAllowedStreams = mScratchValueFocus.value.int32_array[VEHICLE_AUDIO_FOCUS_INDEX_STREAMS];
        ALOGI("initial focus state 0x%x", mAllowedStreams);
    } else {
        ALOGW("No focus property, assume focus always granted");
        mHasFocusProperty = false;
        mAllowedStreams = 0xffffffff;
    }
    for (size_t i = 0; i < mStreamStates.size(); i++) {
        mStreamStates.editItemAt(i).timeoutStartNs = 0;
    }
}

void VehicleNetworkAudioHelper::release() {
    Mutex::Autolock autoLock(mLock);
    if (mService.get() == NULL) {
        return;
    }
    mService = NULL;
}

static int32_t streamFlagToStreamNumber(int32_t streamFlag) {
    int32_t flag = 0x1;
    for (int32_t i = 0; i < 32; i++) {
        if ((flag & streamFlag) != 0) {
            return i;
        }
        flag = flag << 1;
    }
    return -1;
}

void VehicleNetworkAudioHelper::notifyStreamStarted(int32_t stream) {
    Mutex::Autolock autoLock(mLock);
    if (!mHasFocusProperty) {
        return;
    }
    int32_t streamNumber = streamFlagToStreamNumber(stream);
    if (streamNumber < 0) {
        ALOGE("notifyStreamStarted, wrong stream:0x%x", stream);
        return;
    }
    StreamState& state = getStreamStateLocked(streamNumber);
    if (state.started) {
        return;
    }
    state.started = true;
    state.timeoutStartNs = elapsedRealtimeNano();
    mScratchValueStreamState.value.int32_array[VEHICLE_AUDIO_STREAM_STATE_INDEX_STATE] =
            VEHICLE_AUDIO_STREAM_STATE_STARTED;
    mScratchValueStreamState.value.int32_array[VEHICLE_AUDIO_STREAM_STATE_INDEX_STREAM] =
            streamNumber;
    mScratchValueStreamState.timestamp = android::elapsedRealtimeNano();
    mService->setProperty(mScratchValueStreamState);
}

void VehicleNetworkAudioHelper::notifyStreamStopped(int32_t stream) {
    Mutex::Autolock autoLock(mLock);
    if (!mHasFocusProperty) {
        return;
    }
    int32_t streamNumber = streamFlagToStreamNumber(stream);
    if (streamNumber < 0) {
        ALOGE("notifyStreamStopped, wrong stream:0x%x", stream);
        return;
    }
    StreamState& state = getStreamStateLocked(streamNumber);
    if (!state.started) {
        return;
    }
    state.started = false;
    state.timeoutStartNs = 0;
    mScratchValueStreamState.value.int32_array[VEHICLE_AUDIO_STREAM_STATE_INDEX_STATE] =
            VEHICLE_AUDIO_STREAM_STATE_STOPPED;
    mScratchValueStreamState.value.int32_array[VEHICLE_AUDIO_STREAM_STATE_INDEX_STREAM] =
            streamNumber;
    mScratchValueStreamState.timestamp = android::elapsedRealtimeNano();
    mService->setProperty(mScratchValueStreamState);
}

VehicleNetworkAudioHelper::StreamState& VehicleNetworkAudioHelper::getStreamStateLocked(
        int32_t streamNumber) {
    if (streamNumber >= (int32_t) mStreamStates.size()) {
        mStreamStates.insertAt(mStreamStates.size(), streamNumber - mStreamStates.size() + 1);
    }
    return mStreamStates.editItemAt(streamNumber);
}

vehicle_network_audio_helper_focus_state VehicleNetworkAudioHelper::getStreamFocusState(
        int32_t stream) {
    Mutex::Autolock autoLock(mLock);
    if ((mAllowedStreams & stream) == stream) {
        return VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_FOCUS;
    }
    int32_t streamNumber = streamFlagToStreamNumber(stream);
    if (streamNumber < 0) {
        ALOGE("getStreamFocusState, wrong stream:0x%x", stream);
        return VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_TIMEOUT;
    }
    StreamState& state = getStreamStateLocked(streamNumber);
    if (state.timeoutStartNs == 0) {
        if (state.started) {
            state.timeoutStartNs = elapsedRealtimeNano();
        }
    } else {
        int64_t now = elapsedRealtimeNano();
        if ((state.timeoutStartNs + mTimeoutNs) < now) {
            return VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_TIMEOUT;
        }
    }
    return VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_NO_FOCUS;
}

bool VehicleNetworkAudioHelper::waitForStreamFocus(int32_t stream, nsecs_t waitTimeNs) {
    LOGD("waitForStreamFocus");
    Mutex::Autolock autoLock(mLock);
    int64_t currentTime = android::elapsedRealtimeNano();
    int64_t finishTime = currentTime + waitTimeNs;
    while (true) {
        if ((stream & mAllowedStreams) == stream) {
            LOGD("waitForStreamFocus, has focus");
            return true;
        }
        currentTime = android::elapsedRealtimeNano();
        if (currentTime >= finishTime) {
            break;
        }
        nsecs_t waitTime = finishTime - currentTime;
        mFocusWait.waitRelative(mLock, waitTime);
    }
    LOGD("waitForStreamFocus, no focus");
    return false;
}

void VehicleNetworkAudioHelper::onEvents(sp<VehiclePropValueListHolder>& events) {
    sp<VehicleNetworkAudioFocusListener> listener;
    int32_t allowedStreams;
    bool changed = false;
    do {
        Mutex::Autolock autoLock(mLock);
        if (mService.get() == NULL) { // already released
            return;
        }
        for (vehicle_prop_value_t* value : events->getList()) {
            if (value->prop == VEHICLE_PROPERTY_AUDIO_FOCUS) {
                mAllowedStreams = value->value.int32_array[VEHICLE_AUDIO_FOCUS_INDEX_STREAMS];
                ALOGI("audio focus change 0x%x", mAllowedStreams);
                changed = true;
            }
        }
        listener = mListener;
        allowedStreams = mAllowedStreams;
        if (changed) {
            mFocusWait.signal();
        }
    } while (false);
    if (listener.get() != NULL && changed) {
        listener->onFocusChange(allowedStreams);
    }
}

void VehicleNetworkAudioHelper::onHalError(int32_t /*errorCode*/, int32_t /*property*/,
        int32_t /*operation*/) {
    // not used
}

void VehicleNetworkAudioHelper::onHalRestart(bool /*inMocking*/) {
    LOGD("onHalRestart");
    Mutex::Autolock autoLock(mLock);
    if (mService.get() == NULL) { // already released
        return;
    }
    updatePropertiesLocked();
    mFocusWait.signal();
}

void VehicleNetworkAudioHelper::onPropertySet(const vehicle_prop_value_t &value __attribute__((unused))) {
}

}; // namespace android
