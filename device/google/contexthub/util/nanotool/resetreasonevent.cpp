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

#include "resetreasonevent.h"

#include "contexthub.h"
#include "log.h"

namespace android {

/* ResetReasonEvent *************************************************************/

std::unique_ptr<ResetReasonEvent> ResetReasonEvent::FromBytes(
        const std::vector<uint8_t>& buffer) {
    auto event = std::unique_ptr<ResetReasonEvent>(new ResetReasonEvent());
    event->Populate(buffer);

    return event;
}

uint32_t ResetReasonEvent::GetReason() const {
    // After the event type header (uint32_t), we should have the reset reason,
    // which is of type uint32_t
    if (event_data.size() < (sizeof(uint32_t) + sizeof(uint32_t))) {
        LOGW("Invalid/short ResetReason event of size %zu", event_data.size());
        return 0;
    } else {
        return *(uint32_t*)reinterpret_cast<const uint32_t*>(
            event_data.data() + sizeof(uint32_t));
    }
}

}  // namespace android
