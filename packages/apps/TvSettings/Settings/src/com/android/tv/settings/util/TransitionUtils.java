/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.util;

import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.util.TypedValue;

import com.android.tv.settings.R;

/**
 * Utility class for handling transition animations.
 */
public class TransitionUtils {

    /**
     * Creates an object animator that use implements the alpha fade-in for an
     * Activity's background.
     */
    public static ObjectAnimator createActivityFadeInAnimator(Resources res, boolean useFloats) {
        TypedValue startAlpha = new TypedValue();
        res.getValue(R.dimen.alpha_activity_in_bkg_start, startAlpha, true);

        TypedValue endAlpha = new TypedValue();
        res.getValue(R.dimen.alpha_activity_in_bkg_end, endAlpha, true);

        ObjectAnimator animator = new ObjectAnimator();
        animator.setPropertyName("alpha");
        if (useFloats) {
            animator.setFloatValues(startAlpha.getFloat(), endAlpha.getFloat());
        } else {
            animator.setIntValues(Float.valueOf(startAlpha.getFloat() * 255).intValue(),
                    Float.valueOf(endAlpha.getFloat() * 255).intValue());
        }
        animator.setDuration(res.getInteger(R.integer.alpha_activity_in_bkg_duration));
        animator.setStartDelay(res.getInteger(R.integer.alpha_activity_in_bkg_delay));
        return animator;
    }

    public static ObjectAnimator createActivityFadeOutAnimator(Resources res, boolean useFloats) {
        TypedValue startAlpha = new TypedValue();
        res.getValue(R.dimen.alpha_activity_out_bkg_start, startAlpha, true);

        TypedValue endAlpha = new TypedValue();
        res.getValue(R.dimen.alpha_activity_out_bkg_end, endAlpha, true);

        ObjectAnimator animator = new ObjectAnimator();
        animator.setPropertyName("alpha");
        if (useFloats) {
            animator.setFloatValues(startAlpha.getFloat(), endAlpha.getFloat());
        } else {
            animator.setIntValues(Float.valueOf(startAlpha.getFloat() * 255).intValue(),
                    Float.valueOf(endAlpha.getFloat() * 255).intValue());
        }
        animator.setDuration(res.getInteger(R.integer.alpha_activity_out_bkg_duration));
        animator.setStartDelay(res.getInteger(R.integer.alpha_activity_out_bkg_delay));
        return animator;
    }
}
