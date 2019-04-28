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
package com.android.car.dialer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.car.dialer.telecom.PhoneLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 *
 * TODO(mcrico): Move to shared.
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = new ArrayList<>(MAX_CALL_TYPE_ICONS);
    private Resources mResources;
    private int mWidth;
    private int mHeight;

    public static final int MAX_CALL_TYPE_ICONS = 3;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = new Resources(context);
        mResources.voicemail.setColorFilter(
                getResources().getColor(R.color.car_tint), PorterDuff.Mode.SRC_IN);
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        requestLayout();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + mResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        requestLayout();
    }

    public int getCount() {
        return mCallTypes.size();
    }

    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case PhoneLoader.INCOMING_TYPE:
                return mResources.incoming;
            case PhoneLoader.OUTGOING_TYPE:
                return mResources.outgoing;
            case PhoneLoader.MISSED_TYPE:
                return mResources.missed;
            case PhoneLoader.VOICEMAIL_TYPE:
                return mResources.voicemail;
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return mResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + mResources.iconMargin;
        }
    }

    private static class Resources {
        public final Drawable incoming;
        public final Drawable outgoing;
        public final Drawable missed;
        public final Drawable voicemail;
        public final int iconMargin;

        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();
            incoming = r.getDrawable(R.drawable.ic_call_received);
            outgoing = r.getDrawable(R.drawable.ic_call_made);
            missed = r.getDrawable(R.drawable.ic_call_missed);
            voicemail = r.getDrawable(R.drawable.ic_call_voicemail);
            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }
    }
}
