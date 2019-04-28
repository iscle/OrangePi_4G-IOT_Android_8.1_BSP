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

#ifndef AAUDIO_LINEAR_RAMP_H
#define AAUDIO_LINEAR_RAMP_H

#include <atomic>
#include <stdint.h>

/**
 * Generate segments along a linear ramp.
 * The ramp target can be updated from another thread.
 * When the target is updated, a new ramp is started from the current position.
 *
 * The first ramp starts at 0.0.
 *
 */
class LinearRamp {
public:
    LinearRamp() {
        mTarget.store(1.0f);
    }

    void setLengthInFrames(int32_t frames) {
        mLengthInFrames = frames;
    }

    int32_t getLengthInFrames() {
        return mLengthInFrames;
    }

    /**
     * This may be called by another thread.
     * @param target
     */
    void setTarget(float target) {
        mTarget.store(target);
    }

    float getTarget() {
        return mTarget.load();
    }

    /**
     * Force the nextSegment to start from this level.
     *
     * WARNING: this can cause a discontinuity if called while the ramp is being used.
     * Only call this when setting the initial ramp.
     *
     * @param level
     */
    void forceCurrent(float level) {
        mLevelFrom = level;
        mLevelTo = level; // forces a ramp if it does not match target
    }

    float getCurrent() {
        return mLevelFrom;
    }

    /**
     * Get levels for next ramp segment.
     *
     * @param frames number of frames in the segment
     * @param levelFrom pointer to starting amplitude
     * @param levelTo pointer to ending amplitude
     * @return true if ramp is still moving towards the target
     */
    bool nextSegment(int32_t frames, float *levelFrom, float *levelTo);

private:

    bool isRamping();

    std::atomic<float>   mTarget;

    int32_t mLengthInFrames  = 48000 / 50; // 20 msec at 48000 Hz
    int32_t mRemaining       = 0;
    float   mLevelFrom       = 0.0f;
    float   mLevelTo         = 0.0f;
};


#endif //AAUDIO_LINEAR_RAMP_H
