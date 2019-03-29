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

import static android.view.cts.MotionEventUtils.withProperties;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MotionEvent.PointerProperties}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MotionEvent_PointerPropertiesTest {
    private MotionEventUtils.PointerPropertiesBuilder mBuilder;
    private MotionEvent.PointerProperties mPointerProperties;

    @Before
    public void setup() {
        mBuilder = withProperties(3, MotionEvent.TOOL_TYPE_MOUSE);
        mPointerProperties = mBuilder.build();
    }

    @Test
    public void testCreation() {
        mBuilder.verifyMatchesPointerProperties(mPointerProperties);
    }

    @Test
    public void testCopyFrom() {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.copyFrom(mPointerProperties);
        mBuilder.verifyMatchesPointerProperties(pointerProperties);
    }

    @Test
    public void testClear() {
        mPointerProperties.clear();
        withProperties(MotionEvent.INVALID_POINTER_ID, MotionEvent.TOOL_TYPE_UNKNOWN).
                verifyMatchesPointerProperties(mPointerProperties);
    }
}
