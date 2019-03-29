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


#ifndef ACTIVITYEVENTHANDLER_H_
#define ACTIVITYEVENTHANDLER_H_

namespace android {

/*
 * An interface that specifies an event handler for activity sensor events.
 */
class ActivityEventHandler {
  public:
    virtual ~ActivityEventHandler() {}

    // Invoked when an activity recognition event has occured.
    //
    // activityIndex - The index of the activity sensor.
    // eventIndex - The event index (enter/exit).
    // whenNs - The timestamp of when the event occured.
    virtual void OnActivityEvent(int sensorIndex, uint8_t eventIndex,
                                 uint64_t whenNs) = 0;

    // Invoked when an activity recognition flush is requested.
    virtual void OnFlush() = 0;

    // Invoked when a sensor hub reset has occured. This is used to reset any
    // internal state.
    virtual void OnSensorHubReset() = 0;
};

}  // namespace android

#endif  // ACTIVITYEVENTHANDLER_H_
