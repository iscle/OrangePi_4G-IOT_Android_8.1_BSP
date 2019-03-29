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

package com.android.cts.verifier.projection.touch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
/*
 * Simple view that draws a circle where a touch is registered
 */
public class TouchPointView extends View {
    @SuppressWarnings("unused")
    private static final String TAG = TouchPointView.class.getSimpleName();

    private final int[] mColors = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.BLACK,
            Color.DKGRAY
    };
    List<Finger> mFingers;
    Paint mPaint;

    public TouchPointView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchPointView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mFingers = new ArrayList<Finger>();

        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mFingers.clear();
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            invalidate();
            return true;
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            Finger finger = new Finger();
            finger.point =  new Point((int)event.getX(pointerIndex), (int)event.getY(pointerIndex));
            finger.pointerId = pointerId;

            mFingers.add(finger);
        }
        invalidate();
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        int radius = canvas.getWidth() / 20;
        for (int i = 0; i < mFingers.size(); i++) {
            Finger finger = mFingers.get(i);
            Point point = finger.point;
            int color = mColors[finger.pointerId % mColors.length];
            mPaint.setColor(color);
            canvas.drawCircle(point.x, point.y, radius, mPaint);
        }
    }

    private class Finger {
        public Point point;
        public int pointerId;
    }
}

