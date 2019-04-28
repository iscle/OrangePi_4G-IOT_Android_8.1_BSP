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

#ifndef ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_H
#define ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/threads.h>
#include <utils/RefBase.h>

// for enums
#include "vehicle-network-audio-helper-for-c.h"

namespace android {

class VehicleNetworkAudioFocusListener : public virtual RefBase {
public:
    virtual void onFocusChange(int32_t activeStreams) = 0;
};

// ----------------------------------------------------------------------------

class VehicleNetwork;
class VehicleNetworkListener;
class LocalListener;

class VehicleNetworkAudioHelper : public VehicleNetworkListener {
public:

    VehicleNetworkAudioHelper(int64_t timeoutNs = FOCUS_WAIT_DEFAULT_TIMEOUT_NS);
    VehicleNetworkAudioHelper(int64_t timeoutNs, sp<VehicleNetworkAudioFocusListener> listener);
    virtual ~VehicleNetworkAudioHelper();

    status_t init();

    void release();

    void notifyStreamStarted(int32_t stream);
    void notifyStreamStopped(int32_t stream);

    vehicle_network_audio_helper_focus_state getStreamFocusState(int32_t stream);

    bool waitForStreamFocus(int32_t stream, nsecs_t waitTimeNs);

    // from VehicleNetworkListener
    void onEvents(sp<VehiclePropValueListHolder>& events) override;
    void onHalError(int32_t errorCode, int32_t property, int32_t operation) override;
    void onHalRestart(bool inMocking) override;
    void onPropertySet(const vehicle_prop_value_t& value) override;
private:
    void updatePropertiesLocked();

    class StreamState {
    public:
        int64_t timeoutStartNs;
        bool started;
        StreamState()
         : timeoutStartNs(0),
           started(false) { };
    };

    StreamState& getStreamStateLocked(int32_t streamNumber);

private:
    const int64_t mTimeoutNs;
    sp<VehicleNetworkAudioFocusListener> mListener;
    Mutex mLock;
    Condition mFocusWait;
    sp<VehicleNetwork> mService;
    bool mHasFocusProperty;
    int32_t mAllowedStreams;
    vehicle_prop_value_t mScratchValueFocus;
    vehicle_prop_value_t mScratchValueStreamState;
    Vector<StreamState> mStreamStates;
};

}; // namespace android
#endif /*ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_H*/
