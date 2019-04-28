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

package com.android.tv.settings.widget;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;

/**
 * SimpleScrollAdapterTransform is the default implementation of {@link ScrollAdapterTransform} used
 * by ScrollAdapterView. It uses two Animator objects to transform views.
 */
public class SimpleScrollAdapterTransform implements ScrollAdapterTransform {

    /** Animator for transform views on the right/down side of mScrollCenter */
    private Animator mHighItemTransform;

    /** for transform views on the left/up side of mScrollCenter */
    private Animator mLowItemTransform;

    private final DisplayMetrics mDisplayMetrics;

    public SimpleScrollAdapterTransform(Context context) {
        mDisplayMetrics = context.getResources().getDisplayMetrics();
    }

    @Override
    public void transform(View child, int distanceFromCenter, int distanceFromCenter2ndAxis) {
        if (mLowItemTransform == null && mHighItemTransform == null) {
            return;
        }
        int absDistance = Math.abs(distanceFromCenter) + Math.abs(distanceFromCenter2ndAxis);
        if (distanceFromCenter < 0) {
            applyTransformationRecursive(absDistance, mLowItemTransform, child);
        } else {
            applyTransformationRecursive(absDistance, mHighItemTransform, child);
        }
    }

    private void applyTransformationRecursive(
            int distanceFromCenter, Animator animator, View child) {
        if (animator instanceof AnimatorSet) {
            ArrayList<Animator> children = ((AnimatorSet) animator).getChildAnimations();
            for (int i = children.size() - 1; i >= 0; i--) {
                applyTransformationRecursive(distanceFromCenter, children.get(i), child);
            }
        } else if (animator instanceof ValueAnimator) {
            ValueAnimator valueAnim = ((ValueAnimator) animator);
            valueAnim.setTarget(child);
            long duration = valueAnim.getDuration();
            if (distanceFromCenter < duration) {
                valueAnim.setCurrentPlayTime(distanceFromCenter);
            } else {
                valueAnim.setCurrentPlayTime(duration);
            }
        }
    }

    private void initializeTransformationRecursive(Animator animator, long defaultDuration) {
        long duration = animator.getDuration();
        if (duration == 0) {
            duration = defaultDuration;
        }
        if (animator instanceof AnimatorSet) {
            ArrayList<Animator> children = ((AnimatorSet) animator).getChildAnimations();
            for (int i = children.size() - 1; i >= 0; i--) {
                initializeTransformationRecursive(children.get(i), duration);
            }
        } else if (animator instanceof ValueAnimator) {
            ValueAnimator valueAnim = ((ValueAnimator) animator);
            // DIP to pixel, save back to duration
            valueAnim.setDuration((long) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, duration, mDisplayMetrics));
        }
    }

    public Animator getHighItemTransform() {
        return mHighItemTransform;
    }

    public void setHighItemTransform(Animator highItemTransform) {
        mHighItemTransform = highItemTransform;
        initializeTransformationRecursive(mHighItemTransform, 0);
    }

    public Animator getLowItemTransform() {
        return mLowItemTransform;
    }

    public void setLowItemTransform(Animator lowItemTransform) {
        mLowItemTransform = lowItemTransform;
        initializeTransformationRecursive(mLowItemTransform, 0);
    }

}
