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

#include "AudioSource.h"

#include "SystemParams.h"

namespace ndkaudio {

//static const char* const TAG = "AudioSource";

AudioSource::AudioSource(int numChannels)
 : numChannels_(numChannels),
   lastReadSize_(0),
   numBuffFrames_(SystemParams::getNumBufferFrames())
{}

AudioSource::~AudioSource()
{}

} // namespace ndkaudio
