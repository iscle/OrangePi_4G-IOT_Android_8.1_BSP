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

package com.android.tv.guide;

import android.content.Context;
import android.util.AttributeSet;

public class TimelineRow extends TimelineGridView {
    private static final float FADING_EDGE_STRENGTH_START = 1.0f;

    private int mScrollPosition;

    public TimelineRow(Context context) {
        this(context, null);
    }

    public TimelineRow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineRow(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void resetScroll() {
        getLayoutManager().scrollToPosition(0);
    }

    /**
     * Returns the current scroll position
     */
    public int getScrollOffset() {
        return Math.abs(mScrollPosition);
    }

    /**
     * Scrolls horizontally to the given position.
     */
    public void scrollTo(int scrollOffset, boolean smoothScroll) {
        int dx = (scrollOffset - getScrollOffset())
                * (getLayoutDirection() == LAYOUT_DIRECTION_LTR ? 1 : -1);
        if (smoothScroll) {
            smoothScrollBy(dx, 0);
        } else {
            scrollBy(dx, 0);
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        // Reset scroll
        if (isAttachedToWindow()) {
            scrollTo(getScrollOffset(), false);
        }
    }

    @Override
    public void onScrolled(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            mScrollPosition = 0;
        } else {
            mScrollPosition += dx;
        }
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_LTR) ? FADING_EDGE_STRENGTH_START : 0;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? FADING_EDGE_STRENGTH_START : 0;
    }
}
