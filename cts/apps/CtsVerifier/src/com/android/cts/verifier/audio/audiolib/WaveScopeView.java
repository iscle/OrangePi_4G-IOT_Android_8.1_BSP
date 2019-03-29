/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio.audiolib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class WaveScopeView extends View {
    @SuppressWarnings("unused")
    private static final String TAG = "WaveScopeView";

    private final Paint mPaint = new Paint();

    private int mBackgroundColor = Color.WHITE;
    private int mTraceColor = Color.BLACK;

    private short[] mPCM16Buffer;
    private float[] mPCMFloatBuffer;

    private int mNumChannels = 2;
    private int mNumFrames = 0;

    private float[] mPointsBuffer;

    // Horrible kludge
    private static int mCachedWidth = 0;

    public WaveScopeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setBackgroundColor(int color) { mBackgroundColor = color; }

    public void setTraceColor(int color) { mTraceColor = color; }

    public void setPCM16Buff(short[] smpl16Buff, int numChans, int numFrames) {
        mPCM16Buffer = smpl16Buff;
        mPCMFloatBuffer = null;

        mNumChannels = numChans;
        mNumFrames = numFrames;

        setupPointBuffer();

        invalidate();
    }

    public void setPCMFloatBuff(float[] smplFloatBuff, int numChans, int numFrames) {
        mPCMFloatBuffer = smplFloatBuff;
        mPCM16Buffer = null;

        mNumChannels = numChans;
        mNumFrames = numFrames;

        setupPointBuffer();

        invalidate();
    }

    private void setupPointBuffer() {
        int width = getWidth();

        // Horrible kludge
        if (width == 0) {
            width = mCachedWidth;
        } else {
            mCachedWidth = width;
        }

        // Canvas.drawLines() uses 2 points (float pairs) per line-segment
        mPointsBuffer = new float[mNumFrames * 4];

        float xIncr = (float) width / (float) mNumFrames;

        float X = 0;
        int len = mPointsBuffer.length;
        for (int pntIndex = 0; pntIndex < len;) {
            mPointsBuffer[pntIndex] = X;
            pntIndex += 2; // skip Y

            X += xIncr;

            mPointsBuffer[pntIndex] = X;
            pntIndex += 2; // skip Y
        }
    }

    /**
     * Draws 1 channel of an interleaved block of SMPL16 samples.
     * @param cvs The Canvas to draw into.
     * @param samples The (potentially) multi-channel sample block.
     * @param numFrames The number of FRAMES in the specified sample block.
     * @param numChans The number of interleaved channels in the specified sample block.
     * @param chanIndex The (0-based) index of the channel to draw.
     * @param zeroY The Y-coordinate of sample value 0 (zero).
     */
    private void drawChannel16(Canvas cvs, short[] samples, int numFrames, int numChans,
            int chanIndex, float zeroY) {
        float yScale = getHeight() / (float) (Short.MAX_VALUE * 2 * numChans);
        int pntIndex = 1; // of the first Y coordinate
        float Y = zeroY;
        int smpl = chanIndex;
        for (int frame = 0; frame < numFrames; frame++) {
            mPointsBuffer[pntIndex] = Y;
            pntIndex += 2;

            Y = zeroY - (samples[smpl] * yScale);

            mPointsBuffer[pntIndex] = Y;
            pntIndex += 2;

            smpl += numChans;
        }
        cvs.drawLines(mPointsBuffer, mPaint);
    }

    /**
     * Draws 1 channel of an interleaved block of FLOAT samples.
     * @param cvs The Canvas to draw into.
     * @param samples The (potentially) multi-channel sample block.
     * @param numFrames The number of FRAMES in the specified sample block.
     * @param numChans The number of interleaved channels in the specified sample block.
     * @param chanIndex The (0-based) index of the channel to draw.
     * @param zeroY The Y-coordinate of sample value 0 (zero).
     */
    private void drawChannelFloat(Canvas cvs, float[] samples, int numFrames, int numChans,
            int chanIndex, float zeroY) {
        float yScale = getHeight() / (float) (2 * numChans);
        int pntIndex = 1; // of the first Y coordinate
        float Y = zeroY;
        int smpl = chanIndex;
        for (int frame = 0; frame < numFrames; frame++) {
            mPointsBuffer[pntIndex] = Y;
            pntIndex += 2;

            Y = zeroY - (samples[smpl] * yScale);

            mPointsBuffer[pntIndex] = Y;
            pntIndex += 2;

            smpl += numChans;
        }
        cvs.drawLines(mPointsBuffer, mPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int height = getHeight();
        mPaint.setColor(mBackgroundColor);
        canvas.drawRect(0, 0, getWidth(), height, mPaint);

        mPaint.setColor(mTraceColor);
        if (mPCM16Buffer != null) {
            float yOffset = height / (2.0f * mNumChannels);
            float yDelta = height / (float) mNumChannels;
            for(int channel = 0; channel < mNumChannels; channel++) {
                drawChannel16(canvas, mPCM16Buffer, mNumFrames, mNumChannels, channel, yOffset);
                yOffset += yDelta;
            }
        } else if (mPCMFloatBuffer != null) {
            float yOffset = height / (2.0f * mNumChannels);
            float yDelta = height / (float) mNumChannels;
            for(int channel = 0; channel < mNumChannels; channel++) {
                drawChannelFloat(canvas, mPCMFloatBuffer, mNumFrames, mNumChannels, channel, yOffset);
                yOffset += yDelta;
            }
        }
        // Log.i("WaveView", "onDraw() - done");
    }
}
