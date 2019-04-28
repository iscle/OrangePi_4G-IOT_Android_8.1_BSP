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

#define LOG_TAG "VehicleNetworkAudioHelper-C"

#include <VehicleNetwork.h>
#include <vehicle-internal.h>
#include <utils/SystemClock.h>
#include "VehicleNetworkAudioHelper.h"
#include "vehicle-network-audio-helper-for-c.h"

extern "C" {

vehicle_network_audio_helper_t* vehicle_network_audio_helper_create(nsecs_t timeout) {
    android::status_t r;
    android::VehicleNetworkAudioHelper* helperObj = new android::VehicleNetworkAudioHelper(timeout);
    if (helperObj == NULL) {
        return NULL;
    }
    vehicle_network_audio_helper_t *helper = new vehicle_network_audio_helper_t();
    if (helper == NULL) {
        goto error;
    }
    r = helperObj->init();
    if (r != android::NO_ERROR) {
        goto error;
    }
    helper->obj = helperObj;
    return helper;

error:
    delete helperObj;
    delete helper;
    return NULL;
}

vehicle_network_audio_helper_t* vehicle_network_audio_helper_create_with_default_timeout() {
    return vehicle_network_audio_helper_create(FOCUS_WAIT_DEFAULT_TIMEOUT_NS);
}

void vehicle_network_audio_helper_destroy(vehicle_network_audio_helper_t* helper) {
    android::VehicleNetworkAudioHelper* helperObj =
            (android::VehicleNetworkAudioHelper*) helper->obj;
    helperObj->release();
    delete helperObj;
    delete helper;
}

void vehicle_network_audio_helper_notify_stream_started(vehicle_network_audio_helper_t* helper,
        int32_t stream) {
    android::VehicleNetworkAudioHelper* helperObj =
            (android::VehicleNetworkAudioHelper*) helper->obj;
    helperObj->notifyStreamStarted(stream);
}

void vehicle_network_audio_helper_notify_stream_stopped(vehicle_network_audio_helper_t* helper,
        int32_t stream) {
    android::VehicleNetworkAudioHelper* helperObj =
            (android::VehicleNetworkAudioHelper*) helper->obj;
    helperObj->notifyStreamStopped(stream);
}

int vehicle_network_audio_helper_get_stream_focus_state(
        vehicle_network_audio_helper_t* helper, int32_t stream) {
    android::VehicleNetworkAudioHelper* helperObj =
            (android::VehicleNetworkAudioHelper*) helper->obj;
    return helperObj->getStreamFocusState(stream);
}

int vehicle_network_audio_helper_wait_for_stream_focus(vehicle_network_audio_helper_t* helper,
        int32_t stream, nsecs_t waitTimeNs) {
    android::VehicleNetworkAudioHelper* helperObj =
            (android::VehicleNetworkAudioHelper*) helper->obj;
    if (helperObj->waitForStreamFocus(stream, waitTimeNs)) {
        return 1;
    }
    return 0;
}

}
