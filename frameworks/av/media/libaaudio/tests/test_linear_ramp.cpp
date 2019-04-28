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

#include <iostream>

#include <gtest/gtest.h>

#include "utility/AAudioUtilities.h"
#include "utility/LinearRamp.h"


TEST(test_linear_ramp, linear_ramp_segments) {
    LinearRamp ramp;
    const float source[4] = {1.0f, 1.0f, 1.0f, 1.0f };
    float destination[4] = {1.0f, 1.0f, 1.0f, 1.0f };

    float levelFrom = -1.0f;
    float levelTo = -1.0f;
    ramp.setLengthInFrames(8);
    ramp.setTarget(8.0f);

    ASSERT_EQ(8, ramp.getLengthInFrames());

    bool ramping = ramp.nextSegment(4, &levelFrom, &levelTo);
    ASSERT_EQ(1, ramping);
    ASSERT_EQ(0.0f, levelFrom);
    ASSERT_EQ(4.0f, levelTo);

    AAudio_linearRamp(source, destination, 4, 1, levelFrom, levelTo);
    ASSERT_EQ(0.0f, destination[0]);
    ASSERT_EQ(1.0f, destination[1]);
    ASSERT_EQ(2.0f, destination[2]);
    ASSERT_EQ(3.0f, destination[3]);

    ramping = ramp.nextSegment(4, &levelFrom, &levelTo);
    ASSERT_EQ(1, ramping);
    ASSERT_EQ(4.0f, levelFrom);
    ASSERT_EQ(8.0f, levelTo);

    AAudio_linearRamp(source, destination, 4, 1, levelFrom, levelTo);
    ASSERT_EQ(4.0f, destination[0]);
    ASSERT_EQ(5.0f, destination[1]);
    ASSERT_EQ(6.0f, destination[2]);
    ASSERT_EQ(7.0f, destination[3]);

    ramping = ramp.nextSegment(4, &levelFrom, &levelTo);
    ASSERT_EQ(0, ramping);
    ASSERT_EQ(8.0f, levelFrom);
    ASSERT_EQ(8.0f, levelTo);

    AAudio_linearRamp(source, destination, 4, 1, levelFrom, levelTo);
    ASSERT_EQ(8.0f, destination[0]);
    ASSERT_EQ(8.0f, destination[1]);
    ASSERT_EQ(8.0f, destination[2]);
    ASSERT_EQ(8.0f, destination[3]);

};


TEST(test_linear_ramp, linear_ramp_forced) {
    LinearRamp ramp;
    const float source[4] = {1.0f, 1.0f, 1.0f, 1.0f };
    float destination[4] = {1.0f, 1.0f, 1.0f, 1.0f };

    float levelFrom = -1.0f;
    float levelTo = -1.0f;
    ramp.setLengthInFrames(4);
    ramp.setTarget(8.0f);
    ramp.forceCurrent(4.0f);
    ASSERT_EQ(4.0f, ramp.getCurrent());

    bool ramping = ramp.nextSegment(4, &levelFrom, &levelTo);
    ASSERT_EQ(1, ramping);
    ASSERT_EQ(4.0f, levelFrom);
    ASSERT_EQ(8.0f, levelTo);

    AAudio_linearRamp(source, destination, 4, 1, levelFrom, levelTo);
    ASSERT_EQ(4.0f, destination[0]);
    ASSERT_EQ(5.0f, destination[1]);
    ASSERT_EQ(6.0f, destination[2]);
    ASSERT_EQ(7.0f, destination[3]);

    ramping = ramp.nextSegment(4, &levelFrom, &levelTo);
    ASSERT_EQ(0, ramping);
    ASSERT_EQ(8.0f, levelFrom);
    ASSERT_EQ(8.0f, levelTo);

    AAudio_linearRamp(source, destination, 4, 1, levelFrom, levelTo);
    ASSERT_EQ(8.0f, destination[0]);
    ASSERT_EQ(8.0f, destination[1]);
    ASSERT_EQ(8.0f, destination[2]);
    ASSERT_EQ(8.0f, destination[3]);

};

