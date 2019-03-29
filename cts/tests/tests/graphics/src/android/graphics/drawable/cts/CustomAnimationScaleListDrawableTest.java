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

package android.graphics.drawable.cts;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.cts.R;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * This test is used to verify that the CustomAnimationScaleListDrawable's current drawable depends
 * on animation duration scale. When the scale is 0, it is a static drawable, otherwise, it is an
 * animatable drawable.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CustomAnimationScaleListDrawableTest {
    private static final int DRAWABLE_ID = R.drawable.custom_animation_scale_list_drawable;
    private Context mContext;
    private float mOriginalScale;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mOriginalScale = ValueAnimator.getDurationScale();
    }

    @After
    public void teardown() {
        // Restore the original duration scale.
        ValueAnimator.setDurationScale(mOriginalScale);
    }

    @Test
    public void testNonZeroDurationScale() {
        // Set the duration scale to a non-zero value will cause the AnimationScaleListDrawable's
        // current drawable choose the animatable one.
        ValueAnimator.setDurationScale(2.0f);
        Drawable dr = mContext.getDrawable(DRAWABLE_ID);
        assertTrue(dr instanceof DrawableContainer);
        assertTrue(dr.getCurrent() instanceof Animatable);
    }

    @Test
    public void testZeroDurationScale() {
        // Set the duration scale to zero will cause the AnimationScaleListDrawable's current
        // drawable choose the static one.
        ValueAnimator.setDurationScale(0f);
        Drawable dr = mContext.getDrawable(DRAWABLE_ID);
        assertTrue(dr instanceof DrawableContainer);
        assertFalse(dr.getCurrent() instanceof Animatable);
    }
}
