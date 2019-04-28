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

package com.android.tv.data;

import android.media.tv.TvContentRating;

public interface StreamInfo {
    int VIDEO_DEFINITION_LEVEL_UNKNOWN = 0;
    int VIDEO_DEFINITION_LEVEL_SD = 1;
    int VIDEO_DEFINITION_LEVEL_HD = 2;
    int VIDEO_DEFINITION_LEVEL_FULL_HD = 3;
    int VIDEO_DEFINITION_LEVEL_ULTRA_HD = 4;

    int AUDIO_CHANNEL_COUNT_UNKNOWN = 0;

    Channel getCurrentChannel();
    TvContentRating getBlockedContentRating();

    int getVideoWidth();
    int getVideoHeight();
    float getVideoFrameRate();
    float getVideoDisplayAspectRatio();
    int getVideoDefinitionLevel();
    int getAudioChannelCount();
    boolean hasClosedCaption();
    boolean isVideoAvailable();
    /**
     *  Returns true, if video or audio is available.
     */
    boolean isVideoOrAudioAvailable();
    int getVideoUnavailableReason();
}
