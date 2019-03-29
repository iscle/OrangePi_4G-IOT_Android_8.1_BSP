/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.sensors;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * A view class that draws the user prompt
 *
 * The following piece of code should show how to use this view.
 *
 *  public void testUI()  {
 *     final int MAX_TILT_ANGLE = 70; // +/- 70
 *
 *     final int TILT_ANGLE_STEP = 5; // 5 degree(s) per step
 *     final int YAW_ANGLE_STEP = 10; // 10 degree(s) per step
 *
 *     RangeCoveredRegister xCovered, yCovered, zCovered;
 *     xCovered = new RangeCoveredRegister(-MAX_TILT_ANGLE, +MAX_TILT_ANGLE, TILT_ANGLE_STEP);
 *
 *     yCovered = new RangeCoveredRegister(-MAX_TILT_ANGLE, +MAX_TILT_ANGLE, TILT_ANGLE_STEP);
 *     zCovered = new RangeCoveredRegister(YAW_ANGLE_STEP);
 *
 *     xCovered.update(40);
 *     xCovered.update(-40);
 *     xCovered.update(12);
 *
 *     yCovered.update(50);
 *     yCovered.update(-51);
 *
 *     zCovered.update(150);
 *     zCovered.update(42);
 *
 *     setDataProvider(xCovered, yCovered, zCovered);
 *     enableAxis(RVCVRecordActivity.AXIS_ALL); //debug mode, show all three axis
 * }
 */
public class MotionIndicatorView extends View {
    private final String TAG = "MotionIndicatorView";
    private final boolean LOCAL_LOGV = false;

    private Paint mCursorPaint;
    private Paint mLimitPaint;
    private Paint mCoveredPaint;
    private Paint mRangePaint;
    private Paint mEraserPaint;

    // UI settings
    private final int XBAR_WIDTH = 50;
    private final int XBAR_MARGIN = 50;
    private final int XBAR_CURSOR_ADD = 20;

    private final int YBAR_WIDTH = 50;
    private final int YBAR_MARGIN = 50;
    private final int YBAR_CURSOR_ADD = 20;

    private final int ZRING_WIDTH = 50;
    private final int ZRING_CURSOR_ADD = 30;


    private int mXSize, mYSize;
    private RectF mZBoundOut, mZBoundOut2, mZBoundIn, mZBoundIn2;

    private RangeCoveredRegister mXCovered, mYCovered, mZCovered;

    private boolean mXEnabled, mYEnabled, mZEnabled;

    /**
     * Constructor
     * @param context
     */
    public MotionIndicatorView(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor
     * @param context Application context
     * @param attrs
     */
    public MotionIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Initialize the Paint objects
     */
    private void init() {

        mCursorPaint = new Paint();
        mCursorPaint.setColor(Color.BLUE);

        mLimitPaint = new Paint();
        mLimitPaint.setColor(Color.YELLOW);

        mCoveredPaint = new Paint();
        mCoveredPaint.setColor(Color.CYAN);

        mRangePaint = new Paint();
        mRangePaint.setColor(Color.DKGRAY);

        mEraserPaint = new Paint();
        mEraserPaint.setColor(Color.TRANSPARENT);
        // ensure the erasing effect
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    }

    /**
     * Connect the view to certain data provider objects
     * @param x Data provider for x direction tilt angle
     * @param y Data provider for y direction tilt angle
     * @param z Data provider for z rotation
     */
    public void setDataProvider(RangeCoveredRegister x,
                                RangeCoveredRegister y,
                                RangeCoveredRegister z)    {
        mXCovered = x;
        mYCovered = y;
        mZCovered = z;
    }

    /**
     * Set the active axis for display
     *
     * @param axis AXIS_X, AXIS_Y, AXIS_Z for x, y, z axis indicators, or AXIS_ALL for all three.
     */
    public void enableAxis(int axis)  {
        mXEnabled = mYEnabled = mZEnabled = false;

        switch(axis)
        {
            case SensorManager.AXIS_X:
                mXEnabled = true;
                break;
            case SensorManager.AXIS_Y:
                mYEnabled = true;
                break;
            case SensorManager.AXIS_Z:
                mZEnabled = true;
                break;
            case RVCVRecordActivity.AXIS_ALL:
                mXEnabled = mYEnabled = mZEnabled = true;
        }
    }

    /**
     * Doing some pre-calculation that only changes when view dimensions are changed.
     * @param w
     * @param h
     * @param oldw
     * @param oldh
     */
    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        mXSize = w;
        mYSize = h;

        mZBoundOut = new RectF(w/2-w/2.5f, h/2-w/2.5f, w/2+w/2.5f, h/2+w/2.5f);
        mZBoundOut2 = new RectF(
                w/2-w/2.5f-ZRING_CURSOR_ADD, h/2-w/2.5f-ZRING_CURSOR_ADD,
                w/2+w/2.5f+ZRING_CURSOR_ADD, h/2+w/2.5f+ZRING_CURSOR_ADD);
        mZBoundIn = new RectF(
                w/2-w/2.5f+ZRING_WIDTH, h/2-w/2.5f+ZRING_WIDTH,
                w/2+w/2.5f-ZRING_WIDTH, h/2+w/2.5f-ZRING_WIDTH);
        mZBoundIn2 = new RectF(
                w/2-w/2.5f+ZRING_WIDTH+ZRING_CURSOR_ADD, h/2-w/2.5f+ZRING_WIDTH+ZRING_CURSOR_ADD,
                w/2+w/2.5f-ZRING_WIDTH-ZRING_CURSOR_ADD, h/2+w/2.5f-ZRING_WIDTH-ZRING_CURSOR_ADD);

        if (LOCAL_LOGV) Log.v(TAG, "New view size = ("+w+", "+h+")");
    }

    /**
     * Draw UI depends on the selected axis and registered value
     *
     * @param canvas the canvas to draw on
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int i,t;

        Paint p = new Paint();
        p.setColor(Color.YELLOW);
        canvas.drawRect(10,10, 50, 50, p);

        if (mXEnabled && mXCovered != null) {
            int xNStep = mXCovered.getNSteps() + 4; // two on each side as a buffer
            int xStepSize = mXSize * 3/4 / xNStep;
            int xLeft = mXSize * 1/8 + (mXSize * 3/4 % xNStep)/2;

            // base bar
            canvas.drawRect(xLeft, XBAR_MARGIN,
                    xLeft+xStepSize*xNStep-1, XBAR_WIDTH+XBAR_MARGIN, mRangePaint);

            // covered range
            for (i=0; i<mXCovered.getNSteps(); ++i) {
                if (mXCovered.isCovered(i)) {
                    canvas.drawRect(
                            xLeft+xStepSize*(i+2), XBAR_MARGIN,
                            xLeft+xStepSize*(i+3)-1, XBAR_WIDTH + XBAR_MARGIN,
                            mCoveredPaint);
                }
            }

            // limit
            canvas.drawRect(xLeft+xStepSize*2-4, XBAR_MARGIN,
                    xLeft+xStepSize*2+3, XBAR_WIDTH+XBAR_MARGIN, mLimitPaint);
            canvas.drawRect(xLeft+xStepSize*(xNStep-2)-4, XBAR_MARGIN,
                    xLeft+xStepSize*(xNStep-2)+3, XBAR_WIDTH+XBAR_MARGIN, mLimitPaint);

            // cursor
            t = (int)(xLeft+xStepSize*(mXCovered.getLastValue()+2));
            canvas.drawRect(t-4, XBAR_MARGIN-XBAR_CURSOR_ADD, t+3,
                    XBAR_WIDTH+XBAR_MARGIN+XBAR_CURSOR_ADD, mCursorPaint);
        }
        if (mYEnabled && mYCovered != null) {
            int yNStep = mYCovered.getNSteps() + 4; // two on each side as a buffer
            int yStepSize = mYSize * 3/4 / yNStep;
            int yLeft = mYSize * 1/8 + (mYSize * 3/4 % yNStep)/2;

            // base bar
            canvas.drawRect(YBAR_MARGIN, yLeft,
                    YBAR_WIDTH+YBAR_MARGIN, yLeft+yStepSize*yNStep-1, mRangePaint);

            // covered range
            for (i=0; i<mYCovered.getNSteps(); ++i) {
                if (mYCovered.isCovered(i)) {
                    canvas.drawRect(
                            YBAR_MARGIN, yLeft+yStepSize*(i+2),
                            YBAR_WIDTH + YBAR_MARGIN, yLeft+yStepSize*(i+3)-1,
                            mCoveredPaint);
                }
            }

            // limit
            canvas.drawRect(YBAR_MARGIN, yLeft + yStepSize * 2 - 4,
                    YBAR_WIDTH + YBAR_MARGIN, yLeft + yStepSize * 2 + 3, mLimitPaint);
            canvas.drawRect(YBAR_MARGIN, yLeft + yStepSize * (yNStep - 2) - 4,
                    YBAR_WIDTH + YBAR_MARGIN, yLeft + yStepSize * (yNStep - 2) + 3, mLimitPaint);

            // cursor
            t = (int)(yLeft+yStepSize*(mYCovered.getLastValue()+2));
            canvas.drawRect( YBAR_MARGIN-YBAR_CURSOR_ADD, t-4,
                    YBAR_WIDTH+YBAR_MARGIN+YBAR_CURSOR_ADD, t+3, mCursorPaint);
        }

        if (mZEnabled && mZCovered != null) {
            float stepSize  = 360.0f/mZCovered.getNSteps();

            // base bar
            canvas.drawArc(mZBoundOut,0, 360, true, mRangePaint);

            // covered range
            for (i=0; i<mZCovered.getNSteps(); ++i) {
                if (mZCovered.isCovered(i)) {
                    canvas.drawArc(mZBoundOut,i*stepSize-0.2f, stepSize+0.4f,
                            true, mCoveredPaint);
                }
            }
            // clear center
            canvas.drawArc(mZBoundIn, 0, 360, true, mEraserPaint);
            // cursor
            canvas.drawArc(mZBoundOut2, mZCovered.getLastValue()*stepSize- 1, 2,
                    true, mCursorPaint);
            canvas.drawArc(mZBoundIn2, mZCovered.getLastValue()*stepSize-1.5f, 3,
                    true, mEraserPaint);
        }
    }
}

/**
 *  A range register class for the RVCVRecord Activity
 */
class RangeCoveredRegister {
    enum MODE {
        LINEAR,
        ROTATE2D
    }

    private boolean[] mCovered;
    private MODE mMode;
    private int mStep;
    private int mLow, mHigh;
    private int mLastData;

    // high is not inclusive
    RangeCoveredRegister(int low, int high, int step) {
        mMode = MODE.LINEAR;
        mStep = step;
        mLow = low;
        mHigh = high;
        init();
    }

    RangeCoveredRegister(int step) {
        mMode = MODE.ROTATE2D;
        mStep = step;
        mLow = 0;
        mHigh = 360;
        init();
    }

    private void init() {
        if (mMode == MODE.LINEAR) {
            mCovered = new boolean[(mHigh-mLow)/mStep];
        }else {
            mCovered = new boolean[360/mStep];
        }
    }

    /**
     * Test if the range specified by (low, high) is covered.
     *
     * If it is LINEAR mode, the range will be quantized to nearest step boundary. If it is the
     * ROTATE2D mode, it is the same as isFullyCovered().
     *
     * @param low The low end of the range.
     * @param high The high end of the range.
     * @return if the specified range is covered, return true; otherwise false.
     */
    public boolean isRangeCovered(int low, int high) {
        if (mMode == MODE.LINEAR) {
            int iLow = Math.max(Math.round((low - mLow) / mStep), 0);
            int iHigh = Math.min(Math.round((high - mLow) / mStep), mCovered.length-1);

            for (int i = iLow; i <= iHigh; ++i) {
                if (!mCovered[i]) {
                    return false;
                }
            }
            return true;

        } else {
            return isFullyCovered();
        }
    }

    /**
     * Test if the range defined is fully covered.
     *
     * @return if the range is fully covered, return true; otherwise false.
     */
    public boolean isFullyCovered() {
        for (boolean i : mCovered) {
            if (!i) return false;
        }
        return true;
    }

    /**
     * Test if a specific step is covered.
     *
     * @param i the step number
     * @return if the step specified is covered, return true; otherwise false.
     */
    public boolean isCovered(int i) {
        return mCovered[i];
    }

    /**
     *
     *
     * @param data
     * @return if this update changes the status of
     */
    public boolean update(int data) {
        mLastData = data;

        if (mMode == MODE.ROTATE2D) {
            data %= 360;
        }

        int iStep = (data - mLow)/mStep;

        if (iStep>=0 && iStep<getNSteps()) {
            // only record valid data
            mLastData = data;

            if (mCovered[iStep]) {
                return false;
            } else {
                mCovered[iStep] = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Get the number of steps in this register
     *
     * @return The number of steps in this register
     */
    public int getNSteps() {
        //if (mCovered == null) {
        //return 0;
        //}
        return mCovered.length;
    }

    /**
     * Get the last value updated
     *
     * @return The last value updated
     */
    public float getLastValue() {
        // ensure float division
        return ((float)(mLastData - mLow))/mStep;
    }
}
