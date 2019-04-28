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

package com.android.car.stream.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

/**
 * A {@link CardView} whose width can be specified by the number of columns that it will span.
 *
 * @see {@link ColumnCalculator}
 */
public final class StreamCardView extends CardView {
    private static final String TAG = "Em.StreamCardView";

    private ColumnCalculator mColumnCalculator;
    private int mColumnSpan;

    /**
     * The default number of columns that this {@link StreamCardView} spans. This number is used
     * if {@link #setColumnSpan(int)} is not called or {@code columnSpan} is not defined in an
     * XML layout.
     */
    private int mDefaultColumnSpan;

    public StreamCardView(Context context) {
        super(context);
        init(context, null, 0 /* defStyleAttrs */);
    }

    public StreamCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0 /* defStyleAttrs */);
    }

    public StreamCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttrs) {
        mColumnCalculator = ColumnCalculator.getInstance(context);

        mDefaultColumnSpan = getResources().getInteger(R.integer.stream_card_default_column_span);
        mColumnSpan = mDefaultColumnSpan;

        TypedArray ta = null;

        try {
            ta = context.obtainStyledAttributes(attrs, R.styleable.StreamCardView, defStyleAttrs,
                    0 /* defStyleRes */);
            mColumnSpan = ta.getInteger(R.styleable.StreamCardView_columnSpan, mDefaultColumnSpan);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mColumnSpan: " + mColumnSpan);
            }
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        // Override any given layoutParams so that the width is one that is calculated based on
        // column and gutter span.
        params.width = mColumnCalculator.getSizeForColumnSpan(mColumnSpan);

        // Then, set the LayoutParams normally.
        super.setLayoutParams(params);
    }

    /**
     * Sets the number of columns that this {@link StreamCardView} will span. The given span is
     * ignored if it is less than 0 or greater than the number of columns that fit on screen.
     */
    public void setColumnSpan(int columnSpan) {
        if (columnSpan <= 0 || columnSpan > mColumnCalculator.getNumOfColumns()) {
            return;
        }

        mColumnSpan = columnSpan;

        // Re-initialize the LayoutParams so that the width of this card is updated.
        if (getLayoutParams() != null) {
            setLayoutParams(getLayoutParams());
        }
    }

    /**
     * Returns the currently number of columns that this StreamCardView spans.
     */
    public int getColumnSpan() {
        return mColumnSpan;
    }
}
