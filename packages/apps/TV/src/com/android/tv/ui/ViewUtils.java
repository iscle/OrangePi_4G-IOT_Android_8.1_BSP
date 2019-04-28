/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A class that includes convenience methods for view classes.
 */
public class ViewUtils {
    private static final String TAG = "ViewUtils";

    private ViewUtils() {
        // Prevent instantiation.
    }

    public static void setTransitionAlpha(View v, float alpha) {
        Method method;
        try {
            method = View.class.getDeclaredMethod("setTransitionAlpha", Float.TYPE);
            method.invoke(v, alpha);
        } catch (NoSuchMethodException|IllegalAccessException|IllegalArgumentException
                |InvocationTargetException e) {
            Log.e(TAG, "Fail to call View.setTransitionAlpha", e);
        }
    }

    /**
     * Creates an animator in view's height
     * @param target the {@link view} animator performs on.
     */
    public static Animator createHeightAnimator(
            final View target, int initialHeight, int targetHeight) {
        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, targetHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (Integer) animation.getAnimatedValue();
                if (value == 0) {
                    if (target.getVisibility() != View.GONE) {
                        target.setVisibility(View.GONE);
                    }
                } else {
                    if (target.getVisibility() != View.VISIBLE) {
                        target.setVisibility(View.VISIBLE);
                    }
                    setLayoutHeight(target, value);
                }
            }
        });
        return animator;
    }

    /**
     * Gets view's layout height.
     */
    public static int getLayoutHeight(View view) {
        LayoutParams layoutParams = view.getLayoutParams();
        return layoutParams.height;
    }

    /**
     * Sets view's layout height.
     */
    public static void setLayoutHeight(View view, int height) {
        LayoutParams layoutParams = view.getLayoutParams();
        if (height != layoutParams.height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
    }
}