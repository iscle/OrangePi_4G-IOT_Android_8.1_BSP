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
package com.android.car.dialer;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A View that represents a single button on the dialpad. This View display a number above letters
 * or an image.
 */
public class DialpadButton extends FrameLayout {
    private static final int INVALID_IMAGE_RES = -1;

    private String mNumberText;
    private String mLetterText;
    private int mImageRes = INVALID_IMAGE_RES;

    public DialpadButton(Context context) {
        super(context);
        init(context, null);
    }

    public DialpadButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DialpadButton(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context, attrs);
    }

    public DialpadButton(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        inflate(context, R.layout.dialpad_button, this /* root */);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.DialpadButton);

        try {
            mNumberText = ta.getString(R.styleable.DialpadButton_numberText);
            mLetterText = ta.getString(R.styleable.DialpadButton_letterText);
            mImageRes = ta.getResourceId(R.styleable.DialpadButton_image, INVALID_IMAGE_RES);
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        // Using null check instead of a TextUtils.isEmpty() check so that an empty number/letter
        // can be used to keep the positioning of non-empty numbers/letters consistent.
        if (mNumberText != null) {
            TextView numberTextView = (TextView) findViewById(R.id.dialpad_number);
            numberTextView.setText(mNumberText);
            numberTextView.setVisibility(VISIBLE);
        }

        if (mLetterText != null) {
            TextView letterTextView = (TextView) findViewById(R.id.dialpad_letters);
            letterTextView.setText(mLetterText);
            letterTextView.setVisibility(VISIBLE);
        }

        if (mImageRes != INVALID_IMAGE_RES) {
            ImageView imageView = (ImageView) findViewById(R.id.dialpad_image);
            imageView.setImageResource(mImageRes);
            imageView.setVisibility(VISIBLE);
        }
    }
}
