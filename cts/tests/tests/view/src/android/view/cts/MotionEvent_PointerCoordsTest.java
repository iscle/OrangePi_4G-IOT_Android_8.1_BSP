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

package android.view.cts;

import static android.view.cts.MotionEventUtils.withCoords;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;
import android.view.cts.MotionEventUtils.PointerCoordsBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MotionEvent.PointerCoords}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MotionEvent_PointerCoordsTest {
    private PointerCoordsBuilder mBuilder;
    private MotionEvent.PointerCoords mPointerCoords;

    @Before
    public void setup() {
        mBuilder = withCoords(10.0f, 20.0f).withPressure(1.2f).withSize(2.0f).withTool(1.2f, 1.4f).
                withTouch(3.0f, 2.4f);
        mPointerCoords = mBuilder.build();
    }

    @Test
    public void testCreation() {
        mBuilder.verifyMatchesPointerCoords(mPointerCoords);
    }

    @Test
    public void testAxesModifications() {
        // Change value of X
        mPointerCoords.setAxisValue(MotionEvent.AXIS_X, 15.0f);
        withCoords(15.0f, 20.0f).withPressure(1.2f).withSize(2.0f).withTool(1.2f, 1.4f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of Y
        mPointerCoords.setAxisValue(MotionEvent.AXIS_Y, 25.0f);
        withCoords(15.0f, 25.0f).withPressure(1.2f).withSize(2.0f).withTool(1.2f, 1.4f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of pressure
        mPointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, 2.2f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(2.0f).withTool(1.2f, 1.4f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of size
        mPointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, 10.0f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(10.0f).withTool(1.2f, 1.4f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of tool major
        mPointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, 7.0f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(10.0f).withTool(7.0f, 1.4f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of tool minor
        mPointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, 2.0f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(10.0f).withTool(7.0f, 2.0f).
                withTouch(3.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of tool major
        mPointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, 5.0f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(10.0f).withTool(7.0f, 2.0f).
                withTouch(5.0f, 2.4f).verifyMatchesPointerCoords(mPointerCoords);

        // Change value of tool minor
        mPointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, 2.1f);
        withCoords(15.0f, 25.0f).withPressure(2.2f).withSize(10.0f).withTool(7.0f, 2.0f).
                withTouch(5.0f, 2.1f).verifyMatchesPointerCoords(mPointerCoords);
    }

    @Test
    public void testCopyFrom() {
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.copyFrom(mPointerCoords);
        mBuilder.verifyMatchesPointerCoords(pointerCoords);
    }

    @Test
    public void testClear() {
        mPointerCoords.clear();
        withCoords(0.0f, 0.0f).withPressure(0.0f).withSize(0.0f).withTool(0.0f, 0.0f).
                withTouch(0.0f, 0.0f).verifyMatchesPointerCoords(mPointerCoords);
    }
}
