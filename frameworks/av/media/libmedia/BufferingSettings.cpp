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

#define LOG_TAG "BufferingSettings"
//#define LOG_NDEBUG 0

#include <binder/Parcel.h>

#include <media/BufferingSettings.h>

namespace android {

// static
bool BufferingSettings::IsValidBufferingMode(int mode) {
    return (mode >= BUFFERING_MODE_NONE && mode < BUFFERING_MODE_COUNT);
}

// static
bool BufferingSettings::IsTimeBasedBufferingMode(int mode) {
    return (mode == BUFFERING_MODE_TIME_ONLY || mode == BUFFERING_MODE_TIME_THEN_SIZE);
}

// static
bool BufferingSettings::IsSizeBasedBufferingMode(int mode) {
    return (mode == BUFFERING_MODE_SIZE_ONLY || mode == BUFFERING_MODE_TIME_THEN_SIZE);
}

BufferingSettings::BufferingSettings()
        : mInitialBufferingMode(BUFFERING_MODE_NONE),
          mRebufferingMode(BUFFERING_MODE_NONE),
          mInitialWatermarkMs(kNoWatermark),
          mInitialWatermarkKB(kNoWatermark),
          mRebufferingWatermarkLowMs(kNoWatermark),
          mRebufferingWatermarkHighMs(kNoWatermark),
          mRebufferingWatermarkLowKB(kNoWatermark),
          mRebufferingWatermarkHighKB(kNoWatermark) { }

status_t BufferingSettings::readFromParcel(const Parcel* parcel) {
    if (parcel == nullptr) {
        return BAD_VALUE;
    }
    mInitialBufferingMode = (BufferingMode)parcel->readInt32();
    mRebufferingMode = (BufferingMode)parcel->readInt32();
    mInitialWatermarkMs = parcel->readInt32();
    mInitialWatermarkKB = parcel->readInt32();
    mRebufferingWatermarkLowMs = parcel->readInt32();
    mRebufferingWatermarkHighMs = parcel->readInt32();
    mRebufferingWatermarkLowKB = parcel->readInt32();
    mRebufferingWatermarkHighKB = parcel->readInt32();

    return OK;
}

status_t BufferingSettings::writeToParcel(Parcel* parcel) const {
    if (parcel == nullptr) {
        return BAD_VALUE;
    }
    parcel->writeInt32(mInitialBufferingMode);
    parcel->writeInt32(mRebufferingMode);
    parcel->writeInt32(mInitialWatermarkMs);
    parcel->writeInt32(mInitialWatermarkKB);
    parcel->writeInt32(mRebufferingWatermarkLowMs);
    parcel->writeInt32(mRebufferingWatermarkHighMs);
    parcel->writeInt32(mRebufferingWatermarkLowKB);
    parcel->writeInt32(mRebufferingWatermarkHighKB);

    return OK;
}

String8 BufferingSettings::toString() const {
    String8 s;
    s.appendFormat("initialMode(%d), rebufferingMode(%d), "
            "initialMarks(%d ms, %d KB), rebufferingMarks(%d, %d)ms, (%d, %d)KB",
            mInitialBufferingMode, mRebufferingMode,
            mInitialWatermarkMs, mInitialWatermarkKB,
            mRebufferingWatermarkLowMs, mRebufferingWatermarkHighMs,
            mRebufferingWatermarkLowKB, mRebufferingWatermarkHighKB);
    return s;
}

} // namespace android
