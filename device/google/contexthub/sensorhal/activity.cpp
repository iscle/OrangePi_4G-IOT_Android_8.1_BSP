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

#include <cstdlib>
#include <inttypes.h>

#define LOG_TAG "ActivityRecognitionHAL"
#include <utils/Log.h>

#include <media/stagefright/foundation/ADebug.h>

#include "activity.h"

using namespace android;

static const int kVersionMajor = 1;
static const int kVersionMinor = 0;

// The maximum delta between events at which point their timestamps are to be
// considered equal.
static const int64_t kEventTimestampThresholdNanos = 100000000; // 100ms.
static const int64_t kMaxEventAgeNanos = 10000000000; // 10000ms.
static const useconds_t kFlushDelayMicros = 10000; // 10ms.

static const char *const kActivityList[] = {
    ACTIVITY_TYPE_IN_VEHICLE,
    ACTIVITY_TYPE_ON_BICYCLE,
    ACTIVITY_TYPE_WALKING,
    ACTIVITY_TYPE_RUNNING,
    ACTIVITY_TYPE_STILL,
    ACTIVITY_TYPE_TILTING
};

static const int kActivitySensorMap[ARRAY_SIZE(kActivityList)][2] = {
    { COMMS_SENSOR_ACTIVITY_IN_VEHICLE_START,
      COMMS_SENSOR_ACTIVITY_IN_VEHICLE_STOP, },
    { COMMS_SENSOR_ACTIVITY_ON_BICYCLE_START,
      COMMS_SENSOR_ACTIVITY_ON_BICYCLE_STOP, },
    { COMMS_SENSOR_ACTIVITY_WALKING_START,
      COMMS_SENSOR_ACTIVITY_WALKING_STOP, },
    { COMMS_SENSOR_ACTIVITY_RUNNING_START,
      COMMS_SENSOR_ACTIVITY_RUNNING_STOP, },
    { COMMS_SENSOR_ACTIVITY_STILL_START,
      COMMS_SENSOR_ACTIVITY_STILL_STOP, },
    { COMMS_SENSOR_ACTIVITY_TILTING,
      COMMS_SENSOR_ACTIVITY_TILTING, },
};

// The global ActivityContext singleton.
static ActivityContext *gActivityContext = NULL;

static int ActivityClose(struct hw_device_t *) {
    ALOGI("close_activity");
    delete gActivityContext;
    gActivityContext = NULL;
    return 0;
}

static void RegisterActivityCallbackWrapper(
        const struct activity_recognition_device *,
        const activity_recognition_callback_procs_t *callback) {
    gActivityContext->registerActivityCallback(callback);
}

static int EnableActivityEventWrapper(
        const struct activity_recognition_device *,
        uint32_t activity_handle,
        uint32_t event_type,
        int64_t max_batch_report_latency_ns) {
    return gActivityContext->enableActivityEvent(activity_handle, event_type,
                                                 max_batch_report_latency_ns);
}

static int DisableActivityEventWrapper(
        const struct activity_recognition_device *,
        uint32_t activity_handle,
        uint32_t event_type) {
    return gActivityContext->disableActivityEvent(activity_handle, event_type);
}

static int FlushWrapper(const struct activity_recognition_device *) {
    return gActivityContext->flush();
}

ActivityContext::ActivityContext(const struct hw_module_t *module)
    : mHubConnection(HubConnection::getInstance()),
      mCallback(NULL),
      mNewestPublishedEventIndexIsKnown(false),
      mNewestPublishedEventIndex(0),
      mNewestPublishedTimestamp(0),
      mOutstandingFlushEvents(0) {
    memset(&device, 0, sizeof(device));

    device.common.tag = HARDWARE_DEVICE_TAG;
    device.common.version = ACTIVITY_RECOGNITION_API_VERSION_0_1;
    device.common.module = const_cast<hw_module_t *>(module);
    device.common.close = ActivityClose;
    device.register_activity_callback = RegisterActivityCallbackWrapper;
    device.enable_activity_event = EnableActivityEventWrapper;
    device.disable_activity_event = DisableActivityEventWrapper;
    device.flush = FlushWrapper;

    if (getHubAlive()) {
        mHubConnection->setActivityCallback(this);

        // Reset the system to a known good state by disabling all transitions.
        for (int i = COMMS_SENSOR_ACTIVITY_FIRST;
                i <= COMMS_SENSOR_ACTIVITY_LAST; i++) {
            mHubConnection->queueActivate(i, false /* enable */);
        }
    }
}

ActivityContext::~ActivityContext() {
    mHubConnection->setActivityCallback(NULL);
}

/*
 * Obtain the activity handle for a given activity sensor index.
 */
static int GetActivityHandleFromSensorIndex(int sensorIndex) {
    int normalizedSensorIndex = sensorIndex - COMMS_SENSOR_ACTIVITY_FIRST;
    return normalizedSensorIndex / 2;
}

/*
 * Obtain the activity type for a given activity sensor index.
 */
static int GetActivityTypeFromSensorIndex(int sensorIndex) {
    int normalizedSensorIndex = sensorIndex - COMMS_SENSOR_ACTIVITY_FIRST;
    return (normalizedSensorIndex % 2) + 1;
}

void ActivityContext::PublishUnpublishedEvents() {
    if (mUnpublishedEvents.empty()) {
        return;
    }

    while (mUnpublishedEvents.size() > 0) {
        bool eventWasPublished = false;

        for (size_t i = 0; i < mUnpublishedEvents.size(); i++) {
            const ActivityEvent *event = &mUnpublishedEvents[i];
            if (event->eventIndex == (uint8_t)(mNewestPublishedEventIndex + 1)) {
                PublishEvent(*event);
                eventWasPublished = true;
                mUnpublishedEvents.removeAt(i);
                break;
            }
        }

        if (!eventWasPublished) {
            ALOGD("Waiting on unpublished events");
            break;
        }
    }
}

void ActivityContext::PublishEvent(const ActivityEvent& event) {
    activity_event_t halEvent;
    memset(&halEvent, 0, sizeof(halEvent));

    int64_t timestampDelta = event.whenNs - mNewestPublishedTimestamp;
    if (std::abs(timestampDelta) > kEventTimestampThresholdNanos) {
      mNewestPublishedTimestamp = event.whenNs;
    }

    halEvent.activity = GetActivityHandleFromSensorIndex(event.sensorIndex);
    halEvent.timestamp = mNewestPublishedTimestamp;

    if (event.sensorIndex == COMMS_SENSOR_ACTIVITY_TILTING) {
        ALOGD("Publishing tilt event (enter/exit)");

        // Publish two events (enter/exit) for TILTING events.
        halEvent.event_type = ACTIVITY_EVENT_ENTER;
        (*mCallback->activity_callback)(mCallback, &halEvent, 1);

        halEvent.event_type = ACTIVITY_EVENT_EXIT;
    } else {
        ALOGD("Publishing event - activity_handle: %d, event_type: %d"
              ", timestamp: %" PRIu64,
              halEvent.activity, halEvent.event_type, halEvent.timestamp);

        // Just a single event is required for all other activity types.
        halEvent.event_type = GetActivityTypeFromSensorIndex(event.sensorIndex);
    }

    (*mCallback->activity_callback)(mCallback, &halEvent, 1);
    mNewestPublishedEventIndex = event.eventIndex;
    mNewestPublishedEventIndexIsKnown = true;
}

void ActivityContext::DiscardExpiredUnpublishedEvents(uint64_t whenNs) {
    // Determine the current oldest buffered event.
    uint64_t oldestEventTimestamp = UINT64_MAX;
    for (size_t i = 0; i < mUnpublishedEvents.size(); i++) {
        const ActivityEvent *event = &mUnpublishedEvents[i];
        if (event->whenNs < oldestEventTimestamp) {
            oldestEventTimestamp = event->whenNs;
        }
    }

    // If the age of the oldest buffered event is too large an AR sample
    // has been lost. When this happens all AR transitions are set to
    // ACTIVITY_EVENT_EXIT and the event ordering logic is reset.
    if (oldestEventTimestamp != UINT64_MAX
        && (whenNs - oldestEventTimestamp) > kMaxEventAgeNanos) {
        ALOGD("Lost event detected, discarding buffered events");

        // Publish stop events for all activity types except for TILTING.
        for (uint32_t activity = 0;
             activity < (ARRAY_SIZE(kActivityList) - 1); activity++) {
            activity_event_t halEvent;
            memset(&halEvent, 0, sizeof(halEvent));

            halEvent.activity = activity;
            halEvent.timestamp = oldestEventTimestamp;
            halEvent.event_type = ACTIVITY_EVENT_EXIT;
            (*mCallback->activity_callback)(mCallback, &halEvent, 1);
        }

        // Reset the event reordering logic.
        OnSensorHubReset();
    }
}

void ActivityContext::OnActivityEvent(int sensorIndex, uint8_t eventIndex,
                                      uint64_t whenNs) {
    ALOGD("OnActivityEvent sensorIndex = %d, eventIndex = %" PRIu8
          ", whenNs = %" PRIu64, sensorIndex, eventIndex, whenNs);

    Mutex::Autolock autoLock(mCallbackLock);
    if (!mCallback) {
        return;
    }

    DiscardExpiredUnpublishedEvents(whenNs);

    ActivityEvent event = {
        .eventIndex = eventIndex,
        .sensorIndex = sensorIndex,
        .whenNs = whenNs,
    };

    if (!mNewestPublishedEventIndexIsKnown
            || eventIndex == (uint8_t)(mNewestPublishedEventIndex + 1)) {
        PublishEvent(event);
        PublishUnpublishedEvents();
    } else {
        ALOGD("OnActivityEvent out of order, pushing back");
        mUnpublishedEvents.push(event);
    }
}

void ActivityContext::OnFlush() {
    // Once the number of outstanding flush events has reached zero, publish an
    // event via the AR HAL.
    Mutex::Autolock autoLock(mCallbackLock);
    if (!mCallback) {
        return;
    }

    // For each flush event from the sensor hub, decrement the counter of
    // outstanding flushes.
    mOutstandingFlushEvents--;
    if (mOutstandingFlushEvents > 0) {
        ALOGV("OnFlush with %d outstanding flush events", mOutstandingFlushEvents);
        return;
    } else if (mOutstandingFlushEvents < 0) {
        // This can happen on app start.
        ALOGD("more flush events received than requested");
        mOutstandingFlushEvents = 0;
    }

    activity_event_t ev = {
        .event_type = ACTIVITY_EVENT_FLUSH_COMPLETE,
        .activity = 0,
        .timestamp = 0ll,
    };

    (*mCallback->activity_callback)(mCallback, &ev, 1);
    ALOGD("OnFlush published");
}

void ActivityContext::OnSensorHubReset() {
    // Reset the unpublished event queue and clear the last known published
    // event index.
    mUnpublishedEvents.clear();
    mNewestPublishedEventIndexIsKnown = false;
    mOutstandingFlushEvents = 0;
    mNewestPublishedTimestamp = 0;
}

void ActivityContext::registerActivityCallback(
        const activity_recognition_callback_procs_t *callback) {
    ALOGI("registerActivityCallback");

    Mutex::Autolock autoLock(mCallbackLock);
    mCallback = callback;
}

/*
 * Returns a sensor index for a given activity handle and transition type.
 */
int GetActivitySensorForHandleAndType(uint32_t activity_handle,
                                      uint32_t event_type) {
    // Ensure that the requested activity index is valid.
    if (activity_handle >= ARRAY_SIZE(kActivityList)) {
        return 0;
    }

    // Ensure that the event type is either an ENTER or EXIT.
    if (event_type < ACTIVITY_EVENT_ENTER || event_type > ACTIVITY_EVENT_EXIT) {
        return 0;
    }

    return kActivitySensorMap[activity_handle][event_type - 1];
}

int ActivityContext::enableActivityEvent(uint32_t activity_handle,
        uint32_t event_type, int64_t max_report_latency_ns) {
    ALOGI("enableActivityEvent - activity_handle: %" PRIu32
          ", event_type: %" PRIu32 ", latency: %" PRId64,
          activity_handle, event_type, max_report_latency_ns);

    int sensor_index = GetActivitySensorForHandleAndType(activity_handle,
                                                         event_type);
    if (sensor_index <= 0) {
        ALOGE("Enabling invalid activity_handle: %" PRIu32
              ", event_type: %" PRIu32, activity_handle, event_type);
        return 1;
    }

    mHubConnection->queueBatch(sensor_index, 1000000, max_report_latency_ns);
    mHubConnection->queueActivate(sensor_index, true /* enable */);
    return 0;
}

int ActivityContext::disableActivityEvent(uint32_t activity_handle,
                                          uint32_t event_type) {
    ALOGI("disableActivityEvent");

    // Obtain the sensor index for the requested activity and transition types.
    int sensor_index = kActivitySensorMap[activity_handle][event_type - 1];
    if (sensor_index > 0) {
        mHubConnection->queueActivate(sensor_index, false /* enable */);
    } else {
        ALOGE("Disabling invalid activity_handle: %" PRIu32
              ", event_type: %" PRIu32, activity_handle, event_type);
    }

    return 0;
}

int ActivityContext::flush() {
    {
        // Aquire a lock for the mOutstandingFlushEvents shared state. OnFlush
        // modifies this value as flush results are returned. Nested scope is
        // used here to control the lifecycle of the lock as OnFlush may be
        // invoked before this method returns.
        Mutex::Autolock autoLock(mCallbackLock);
        mOutstandingFlushEvents +=
            (COMMS_SENSOR_ACTIVITY_LAST - COMMS_SENSOR_ACTIVITY_FIRST) + 1;
    }

    // Flush all activity sensors.
    for (int i = COMMS_SENSOR_ACTIVITY_FIRST;
            i <= COMMS_SENSOR_ACTIVITY_LAST; i++) {
        mHubConnection->queueFlush(i);
        usleep(kFlushDelayMicros);
    }

    return 0;
}

bool ActivityContext::getHubAlive() {
    return mHubConnection->initCheck() == OK
        && mHubConnection->getAliveCheck() == OK;
}

////////////////////////////////////////////////////////////////////////////////

static int open_activity(
        const struct hw_module_t *module,
        const char *,
        struct hw_device_t **dev) {
    ALOGI("open_activity");

    gActivityContext = new ActivityContext(module);
    *dev = &gActivityContext->device.common;
    return 0;
}

static struct hw_module_methods_t activity_module_methods = {
    .open = open_activity
};

static int get_activity_list(struct activity_recognition_module *,
                             char const* const **activity_list) {
    ALOGI("get_activity_list");

    if (gActivityContext != NULL && gActivityContext->getHubAlive()) {
        *activity_list = kActivityList;
        return sizeof(kActivityList) / sizeof(kActivityList[0]);
    } else {
        *activity_list = {};
        return 0;
    }
}

struct activity_recognition_module HAL_MODULE_INFO_SYM = {
        .common = {
                .tag = HARDWARE_MODULE_TAG,
                .version_major = kVersionMajor,
                .version_minor = kVersionMinor,
                .id = ACTIVITY_RECOGNITION_HARDWARE_MODULE_ID,
                .name = "Google Activity Recognition module",
                .author = "Google",
                .methods = &activity_module_methods,
                .dso  = NULL,
                .reserved = {0},
        },
        .get_supported_activities_list = get_activity_list,
};
