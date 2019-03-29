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

#ifndef RESET_REASON_EVENT_H_
#define RESET_REASON_EVENT_H_

#include "nanomessage.h"

namespace android {

/*
 * These classes represent events sent with event type EVT_RESET_REASON. The
 * platform-specific reset reason is sent at each boot of the sensor hub.
 */

class ResetReasonEvent : public ReadEventResponse {
  public:
    /*
     * Constructs and populates a ResetReasonEvent instance. Returns nullptr if
     * the packet is malformed. The rest of the methods in this class are not
     * guaranteed to be safe unless the object is constructed from this
     * function.
     */
    static std::unique_ptr<ResetReasonEvent> FromBytes(
        const std::vector<uint8_t>& buffer);

    // Returns the 32-bit field that contains the platform-specific reset reason
    uint32_t GetReason() const;
};

}  // namespace android

#endif // RESET_REASON_EVENT_H_
