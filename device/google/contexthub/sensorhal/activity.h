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

#ifndef ACTIVITY_H_

#define ACTIVITY_H_

#include <hardware/activity_recognition.h>
#include <media/stagefright/foundation/ABase.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>

#include "activityeventhandler.h"
#include "hubconnection.h"

namespace android {

class ActivityContext : public ActivityEventHandler {
  public:
    activity_recognition_device_t device;

    explicit ActivityContext(const struct hw_module_t *module);
    ~ActivityContext();

    bool getHubAlive();

    void registerActivityCallback(
            const activity_recognition_callback_procs_t *callback);

    int enableActivityEvent(uint32_t activity_handle,
        uint32_t event_type, int64_t max_report_latency_ns);

    int disableActivityEvent(uint32_t activity_handle, uint32_t event_type);

    int flush();

    // ActivityEventHandler interface.
    virtual void OnActivityEvent(int sensorIndex, uint8_t eventIndex,
                                 uint64_t whenNs) override;
    virtual void OnFlush() override;
    virtual void OnSensorHubReset() override;

  private:
    android::sp<android::HubConnection> mHubConnection;

    android::Mutex mCallbackLock;
    const activity_recognition_callback_procs_t *mCallback;

    struct ActivityEvent {
        uint8_t eventIndex;
        int sensorIndex;
        uint64_t whenNs;
    };

    // Whether or not the newest published event index is known. When the AR HAL
    // is initially started this is set to false to allow any event index from
    // the sensor hub. It is also set to false when a hub reset occurs.
    bool mNewestPublishedEventIndexIsKnown;

    // The index of the newest published event. The next event from the sensor
    // hub must follow this event or else it will be pushed into a list of
    // events to be published once the gap in events has been received.
    uint8_t mNewestPublishedEventIndex;

    // The timestamp of the most recently published event. If the absolute value
    // of the delta of the next timestamp to the current timestamp is below some
    // threshold, this timestamp will be reused. This is used to ensure that
    // activity transitions share the same timestamp and works around agressive
    // AP->ContextHub time synchronization mechansims.
    uint64_t mNewestPublishedTimestamp;

    // The list of unpublished events. These are published once the next
    // event arrives and is greater than mNewestPublishedEventIndex by 1
    // (wrapping across 255).
    Vector<ActivityEvent> mUnpublishedEvents;

    // Track the number of flush events sent to the sensor hub.
    int mOutstandingFlushEvents;

    // Publishes remaining unpublished events.
    void PublishUnpublishedEvents();

    // Publishes an AR event to the AR HAL client.
    void PublishEvent(const ActivityEvent& event);

    // Searches for very old AR events, discards them and publishes EVENT_EXIT
    // transitions for all activities.
    void DiscardExpiredUnpublishedEvents(uint64_t whenNs);

    DISALLOW_EVIL_CONSTRUCTORS(ActivityContext);
};

}  // namespace android

#endif  // ACTIVITY_H_
