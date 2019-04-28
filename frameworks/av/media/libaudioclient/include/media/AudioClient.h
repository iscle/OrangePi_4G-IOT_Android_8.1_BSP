/*
 * Copyright (C) 2017 The Android Open Source Project
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


#ifndef ANDROID_AUDIO_CLIENT_H
#define ANDROID_AUDIO_CLIENT_H

#include <system/audio.h>
#include <utils/String16.h>

namespace android {

class AudioClient {
 public:
    AudioClient() :
        clientUid(-1), clientPid(-1), packageName("") {}

    uid_t clientUid;
    pid_t clientPid;
    String16 packageName;
};

}; // namespace android

#endif  // ANDROID_AUDIO_CLIENT_H
