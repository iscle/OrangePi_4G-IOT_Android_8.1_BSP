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

#define LOG_TAG "MetricsSummarizerPlayer"
#include <utils/Log.h>

#include <stdint.h>
#include <inttypes.h>

#include <utils/threads.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include <utils/List.h>

#include <media/IMediaAnalyticsService.h>

#include "MetricsSummarizer.h"
#include "MetricsSummarizerPlayer.h"




namespace android {

static const char *player_ignorable[] = {
    "android.media.mediaplayer.durationMs",
    "android.media.mediaplayer.playingMs",
    "android.media.mediaplayer.frames",
    "android.media.mediaplayer.dropped",
    0
};

MetricsSummarizerPlayer::MetricsSummarizerPlayer(const char *key)
    : MetricsSummarizer(key)
{
    ALOGV("MetricsSummarizerPlayer::MetricsSummarizerPlayer");
    setIgnorables(player_ignorable);
}

// NB: this is also called for the first time -- so summation == item
// Not sure if we need a flag for that or not.
// In this particular mergeRecord() code -- we're' ok for this.
void MetricsSummarizerPlayer::mergeRecord(MediaAnalyticsItem &summation, MediaAnalyticsItem &item) {

    ALOGV("MetricsSummarizerPlayer::mergeRecord()");


    int64_t duration = 0;
    if (item.getInt64("android.media.mediaplayer.durationMs", &duration)) {
        ALOGV("found durationMs of %" PRId64, duration);
        minMaxVar64(summation, "android.media.mediaplayer.durationMs", duration);
    }

    int64_t playing = 0;
    if (item.getInt64("android.media.mediaplayer.playingMs", &playing)) {
        ALOGV("found playingMs of %" PRId64, playing);
    }
    if (playing >= 0) {
        minMaxVar64(summation,"android.media.mediaplayer.playingMs",playing);
    }

    int64_t frames = 0;
    if (item.getInt64("android.media.mediaplayer.frames", &frames)) {
        ALOGV("found framess of %" PRId64, frames);
    }
    if (frames >= 0) {
        minMaxVar64(summation,"android.media.mediaplayer.frames",frames);
    }

    int64_t dropped = 0;
    if (item.getInt64("android.media.mediaplayer.dropped", &dropped)) {
        ALOGV("found dropped of %" PRId64, dropped);
    }
    if (dropped >= 0) {
        minMaxVar64(summation,"android.media.mediaplayer.dropped",dropped);
    }
}

} // namespace android
