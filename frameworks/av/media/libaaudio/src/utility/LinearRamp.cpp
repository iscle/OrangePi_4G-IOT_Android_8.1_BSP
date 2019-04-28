/*
 * Copyright 2017 The Android Open Source Project
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

#include "LinearRamp.h"

bool LinearRamp::isRamping() {
    float target = mTarget.load();
    if (target != mLevelTo) {
        // Update target. Continue from previous level.
        mLevelTo = target;
        mRemaining = mLengthInFrames;
        return true;
    } else {
        return mRemaining > 0;
    }
}

bool LinearRamp::nextSegment(int32_t frames, float *levelFrom, float *levelTo) {
    bool ramping = isRamping();
    *levelFrom = mLevelFrom;
    if (ramping) {
        float level;
        if (frames >= mRemaining) {
            level = mLevelTo;
            mRemaining = 0;
        } else {
            // Interpolate to a point along the full ramp.
            level = mLevelFrom + (frames * (mLevelTo - mLevelFrom) / mRemaining);
            mRemaining -= frames;
        }
        mLevelFrom = level; // for next ramp
        *levelTo = level;
    } else {
        *levelTo = mLevelTo;
    }
    return ramping;
}