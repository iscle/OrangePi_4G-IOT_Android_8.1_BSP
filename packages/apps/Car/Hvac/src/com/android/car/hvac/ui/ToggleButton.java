/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

/**
 * A toggle button that has two states, each with a different drawable icon.
 */
public class ToggleButton extends ImageButton {
    /**
     * A listener that is notified when the button is toggled.
     */
    public interface ToggleListener {
        void onToggled(boolean isOn);
    }

    private boolean mIsOn;

    private Drawable mDrawableOff;
    private Drawable mDrawableOn;
    private ToggleListener mListener;

    public ToggleButton(Context context) {
        super(context);
    }

    public ToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsOn) {
                    setImageDrawable(mDrawableOff);
                    if (mListener != null) {
                        mListener.onToggled(false);
                    }
                    mIsOn = false;
                } else {
                    setImageDrawable(mDrawableOn);
                    if (mListener != null) {
                        mListener.onToggled(true);
                    }
                    mIsOn = true;
                }
            }
        });
    }

    public void setToggleListener(ToggleListener listener) {
        mListener = listener;
    }

    public void setToggleIcons(Drawable on, Drawable off) {
        mDrawableOff = off;
        mDrawableOn = on;
        setImageDrawable(mIsOn ? mDrawableOn : mDrawableOff);
    }

    public void setIsOn(boolean on) {
        mIsOn = on;
        if (mIsOn) {
            setImageDrawable(mDrawableOn);
        } else {
            setImageDrawable(mDrawableOff);
        }
    }
}
