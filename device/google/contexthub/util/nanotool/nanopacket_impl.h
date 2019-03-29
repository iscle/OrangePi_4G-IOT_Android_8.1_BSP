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

#include "nanopacket.h"

#include <stdio.h>

namespace android {

template<typename T>
bool NanoPacket::DeserializeWord(T *destination, uint8_t byte) {
    *destination |= byte << (8 * parsing_progress_);
    parsing_progress_++;

    if (parsing_progress_ == sizeof(T)) {
        parsing_progress_ = 0;
        return true;
    }

    return false;
}

}  // namespace android
