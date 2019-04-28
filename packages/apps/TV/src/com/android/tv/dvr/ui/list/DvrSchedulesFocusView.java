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
 * limitations under the License
 */

package com.android.tv.dvr.ui.list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.tv.R;

/**
 * A view used for focus in schedules list.
 */
public class DvrSchedulesFocusView extends View {
    private final Paint mPaint;
    private final RectF mRoundRectF = new RectF();
    private final int mRoundRectRadius;

    private final String mViewTag;
    private final String mHeaderFocusViewTag;
    private final String mItemFocusViewTag;

    public DvrSchedulesFocusView(Context context) {
        this(context, null, 0);
    }

    public DvrSchedulesFocusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DvrSchedulesFocusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHeaderFocusViewTag = getContext().getString(R.string.dvr_schedules_header_focus_view);
        mItemFocusViewTag = getContext().getString(R.string.dvr_schedules_item_focus_view);
        mViewTag = (String) getTag();
        mPaint = createPaint(context);
        mRoundRectRadius = getRoundRectRadius();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (TextUtils.equals(mViewTag, mHeaderFocusViewTag)) {
            mRoundRectF.set(0, 0, getWidth(), getHeight());
        } else if (TextUtils.equals(mViewTag, mItemFocusViewTag)) {
            int drawHeight = 2 * mRoundRectRadius;
            int drawOffset = (drawHeight - getHeight()) / 2;
            mRoundRectF.set(0, -drawOffset, getWidth(), getHeight() + drawOffset);
        }
        canvas.drawRoundRect(mRoundRectF, mRoundRectRadius, mRoundRectRadius, mPaint);
    }

    private Paint createPaint(Context context) {
        Paint paint = new Paint();
        paint.setColor(context.getColor(R.color.dvr_schedules_list_item_selector));
        return paint;
    }

    private int getRoundRectRadius() {
        if (TextUtils.equals(mViewTag, mHeaderFocusViewTag)) {
            return getResources().getDimensionPixelSize(
                    R.dimen.dvr_schedules_header_selector_radius);
        } else if (TextUtils.equals(mViewTag, mItemFocusViewTag)) {
            return getResources().getDimensionPixelSize(R.dimen.dvr_schedules_selector_radius);
        }
        return 0;
    }
}


