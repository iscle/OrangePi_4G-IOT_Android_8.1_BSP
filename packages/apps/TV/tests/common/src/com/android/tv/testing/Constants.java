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
package com.android.tv.testing;

import android.media.tv.TvTrackInfo;

/**
 * Constants for testing.
 */
public final class Constants {
    public static final int FUNC_TEST_CHANNEL_COUNT = 100;
    public static final int UNIT_TEST_CHANNEL_COUNT = 4;
    public static final int JANK_TEST_CHANNEL_COUNT = 500; // TODO: increase to 1000 see b/23526997

    public static final TvTrackInfo EN_STEREO_AUDIO_TRACK = new TvTrackInfo.Builder(
            TvTrackInfo.TYPE_AUDIO, "English Stereo Audio").setLanguage("en")
            .setAudioChannelCount(2).build();
    public static final TvTrackInfo GENERIC_AUDIO_TRACK = new TvTrackInfo.Builder(
            TvTrackInfo.TYPE_AUDIO, "Generic Audio").build();

    public static final TvTrackInfo FHD1080P50_VIDEO_TRACK = new TvTrackInfo.Builder(
            TvTrackInfo.TYPE_VIDEO, "FHD Video").setVideoHeight(1080).setVideoWidth(1920)
            .setVideoFrameRate(50).build();
    public static final TvTrackInfo SVGA_VIDEO_TRACK = new TvTrackInfo.Builder(
            TvTrackInfo.TYPE_VIDEO, "SVGA Video").setVideoHeight(600).setVideoWidth(800).build();

    private Constants() {
    }
}
