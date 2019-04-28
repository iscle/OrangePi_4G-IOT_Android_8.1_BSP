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

#ifndef ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_FOR_C_H
#define ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_FOR_C_H

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>
#include <errno.h>

#include <hardware/hardware.h>
#include <utils/Timers.h>

__BEGIN_DECLS

/**
 * Container to hold all objects / bookkeeping stuffs.
 * Audio HAL is not supposed to touch the contents.
 */
typedef struct vehicle_network_audio_helper
{
    void* obj;
} vehicle_network_audio_helper_t;

enum vehicle_network_audio_helper_stream {
    VEHICLE_NETWORK_AUDIO_HELPER_STREAM_0 = 0x1,
    VEHICLE_NETWORK_AUDIO_HELPER_STREAM_1 = 0x2,
    VEHICLE_NETWORK_AUDIO_HELPER_STREAM_2 = 0x4,
    VEHICLE_NETWORK_AUDIO_HELPER_STREAM_3 = 0x8,
};

#define FOCUS_WAIT_DEFAULT_TIMEOUT_NS 1000000000

/**
 * Create helper instance with default timeout. Timer is reset when
 * vehicle_network_audio_helper_notify_stream_started is called, and subsequent call to
 * vehicle_network_audio_helper_get_stream_focus_state can return timeout if focus is not
 * granted within given time.
 * Timeout timer will also reset if focus is taken away while having focus and stream is started.
 */
vehicle_network_audio_helper_t* vehicle_network_audio_helper_create(int64_t timeout);

vehicle_network_audio_helper_t* vehicle_network_audio_helper_create_with_default_timeout();

void vehicle_network_audio_helper_destroy(vehicle_network_audio_helper_t* helper);

/**
 * Notify stream start and reset focus timeout timer if it is not reset already.
 */
void vehicle_network_audio_helper_notify_stream_started(vehicle_network_audio_helper_t* helper,
        int32_t stream);

/**
 * Notify stream stop.
 */
void vehicle_network_audio_helper_notify_stream_stopped(vehicle_network_audio_helper_t* helper,
        int32_t stream);

enum vehicle_network_audio_helper_focus_state {
    VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_TIMEOUT = -1,
    VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_NO_FOCUS = 0,
    VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_FOCUS = 1,
};

/**
 * Check if target stream has focus or not. This function also checks if default timeout has passed
 * since stream was started or since focus was lost last time.
 * @return VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_FOCUS if there is focus.
 *         VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_NO_FOCUS no focus, no timeout
 *         VEHICLE_NETWORK_AUDIO_HELPER_FOCUS_STATE_FOCUS no focus, timed out
 */
int vehicle_network_audio_helper_get_stream_focus_state(
        vehicle_network_audio_helper_t* helper, int32_t stream);

/**
 * Wait for focus until given timeout. It will return immediately if given stream has focus.
 * Otherwise, it will be waiting for focus for given waitTimeNs.
 * @return 1 if focus is available
 *         0 if focus is not available and timeout has happened.
 */
int vehicle_network_audio_helper_wait_for_stream_focus(vehicle_network_audio_helper_t* helper,
        int32_t stream, nsecs_t waitTimeNs);

__END_DECLS

#endif /* ANDROID_VEHICLE_NETWORK_AUDIO_HELPER_FOR_C_H */
