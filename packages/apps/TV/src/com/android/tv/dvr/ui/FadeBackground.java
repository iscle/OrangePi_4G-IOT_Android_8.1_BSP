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

package com.android.tv.dvr.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.android.tv.R;

/**
 * This transition fades in/out of the background of the view by changing the background color.
 */
public class FadeBackground extends Transition {
    private final int mMode;

    public FadeBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FadeBackground);
        mMode = a.getInt(R.styleable.FadeBackground_fadingMode, Visibility.MODE_IN);
        a.recycle();
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) { }

    @Override
    public void captureEndValues(TransitionValues transitionValues) { }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }
        Drawable background = endValues.view.getBackground();
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            int transparentColor = Color.argb(0, Color.red(color), Color.green(color),
                    Color.blue(color));
            return mMode == Visibility.MODE_OUT
                    ? ObjectAnimator.ofArgb(background, "color", transparentColor)
                    : ObjectAnimator.ofArgb(background, "color", transparentColor, color);
        }
        return null;
    }
}
