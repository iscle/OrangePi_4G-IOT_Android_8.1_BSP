/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef ANDROID_BUFFERING_SETTINGS_H
#define ANDROID_BUFFERING_SETTINGS_H

#include <binder/Parcelable.h>

namespace android {

enum BufferingMode : int {
    // Do not support buffering.
    BUFFERING_MODE_NONE             = 0,
    // Support only time based buffering.
    BUFFERING_MODE_TIME_ONLY        = 1,
    // Support only size based buffering.
    BUFFERING_MODE_SIZE_ONLY        = 2,
    // Support both time and size based buffering, time based calculation precedes size based.
    // Size based calculation will be used only when time information is not available for
    // the stream.
    BUFFERING_MODE_TIME_THEN_SIZE   = 3,
    // Number of modes.
    BUFFERING_MODE_COUNT            = 4,
};

struct BufferingSettings : public Parcelable {
    static const int kNoWatermark = -1;

    static bool IsValidBufferingMode(int mode);
    static bool IsTimeBasedBufferingMode(int mode);
    static bool IsSizeBasedBufferingMode(int mode);

    BufferingMode mInitialBufferingMode;  // for prepare
    BufferingMode mRebufferingMode;  // for playback

    int mInitialWatermarkMs;  // time based
    int mInitialWatermarkKB;  // size based

    // When cached data is below this mark, playback will be paused for buffering
    // till data reach |mRebufferingWatermarkHighMs| or end of stream.
    int mRebufferingWatermarkLowMs;
    // When cached data is above this mark, buffering will be paused.
    int mRebufferingWatermarkHighMs;

    // When cached data is below this mark, playback will be paused for buffering
    // till data reach |mRebufferingWatermarkHighKB| or end of stream.
    int mRebufferingWatermarkLowKB;
    // When cached data is above this mark, buffering will be paused.
    int mRebufferingWatermarkHighKB;

    BufferingSettings();

    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

    String8 toString() const;
};

} // namespace android

// ---------------------------------------------------------------------------

#endif // ANDROID_BUFFERING_SETTINGS_H
