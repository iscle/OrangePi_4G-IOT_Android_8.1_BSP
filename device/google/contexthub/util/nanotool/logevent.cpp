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

#include "log.h"
#include "logevent.h"

namespace android {

/* LogEvent *******************************************************************/

std::unique_ptr<LogEvent> LogEvent::FromBytes(
        const std::vector<uint8_t>& buffer) {
    auto event = std::unique_ptr<LogEvent>(new LogEvent());
    event->Populate(buffer);

    return event;
}

std::string LogEvent::GetMessage() const {
    constexpr size_t kHeaderSize = sizeof(uint32_t) // Message type.
        + sizeof(char) // Log level.
        + sizeof(char); // Beginning of log message.

    if (event_data.size() < kHeaderSize) {
        LOGW("Invalid/short LogEvent event of size %zu", event_data.size());
        return std::string();
    } else {
        const char *message = reinterpret_cast<const char *>(
            event_data.data() + sizeof(uint32_t));
        return std::string(message);
    }
}

}  // namespace android
