/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.ViewConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ViewConfiguration}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewConfigurationTest {
    @Test
    public void testStaticValues() {
        ViewConfiguration.getScrollBarSize();
        ViewConfiguration.getFadingEdgeLength();
        ViewConfiguration.getPressedStateDuration();
        ViewConfiguration.getLongPressTimeout();
        ViewConfiguration.getTapTimeout();
        ViewConfiguration.getJumpTapTimeout();
        ViewConfiguration.getEdgeSlop();
        ViewConfiguration.getTouchSlop();
        ViewConfiguration.getWindowTouchSlop();
        ViewConfiguration.getMinimumFlingVelocity();
        ViewConfiguration.getMaximumFlingVelocity();
        ViewConfiguration.getMaximumDrawingCacheSize();
        ViewConfiguration.getZoomControlsTimeout();
        ViewConfiguration.getGlobalActionKeyTimeout();
        ViewConfiguration.getScrollFriction();
        ViewConfiguration.getScrollBarFadeDuration();
        ViewConfiguration.getScrollDefaultDelay();
        ViewConfiguration.getDoubleTapTimeout();
        ViewConfiguration.getKeyRepeatTimeout();
        ViewConfiguration.getKeyRepeatDelay();
        ViewConfiguration.getDefaultActionModeHideDuration();
    }

    @Test
    public void testConstructor() {
        new ViewConfiguration();
    }

    @Test
    public void testInstanceValues() {
        ViewConfiguration vc = ViewConfiguration.get(InstrumentationRegistry.getTargetContext());
        assertNotNull(vc);

        vc.getScaledDoubleTapSlop();
        vc.getScaledEdgeSlop();
        vc.getScaledFadingEdgeLength();
        vc.getScaledMaximumDrawingCacheSize();
        vc.getScaledMaximumFlingVelocity();
        vc.getScaledMinimumFlingVelocity();
        vc.getScaledOverflingDistance();
        vc.getScaledOverscrollDistance();
        vc.getScaledPagingTouchSlop();
        vc.getScaledScrollBarSize();
        vc.getScaledHorizontalScrollFactor();
        vc.getScaledVerticalScrollFactor();
        vc.getScaledTouchSlop();
        vc.getScaledWindowTouchSlop();
        vc.hasPermanentMenuKey();
    }
}
