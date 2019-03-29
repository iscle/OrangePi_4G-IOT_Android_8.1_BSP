/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TransitionDrawableTest {
    private static final int COLOR1 = Color.BLUE;

    private static final int COLOR0 = Color.RED;

    private static final int CANVAS_WIDTH = 10;

    private static final int CANVAS_HEIGHT = 10;

    private Context mContext;
    private TransitionDrawable mTransitionDrawable;
    private Bitmap mBitmap;
    private Canvas mCanvas;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mTransitionDrawable = (TransitionDrawable) mContext.getDrawable(R.drawable.transition_test);
        mTransitionDrawable.setBounds(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        mBitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
    }

    @Test
    public void testConstructor() {
        Drawable[] drawables = new Drawable[] {
                mContext.getDrawable(R.drawable.testimage),
                mContext.getDrawable(R.drawable.levellistdrawable)
        };
        new TransitionDrawable(drawables);
    }

    @Test
    public void testStartTransition() {
        Drawable.Callback cb = mock(Drawable.Callback.class);
        mTransitionDrawable.setCallback(cb);

        // start when there is no transition
        mTransitionDrawable.startTransition(2000);
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransition(COLOR0, COLOR1, 2000);

        // start when there is a transition in progress
        makeTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.startTransition(2000);
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransition(COLOR0, COLOR1, 2000);

        // start when there is a reverse transition in progress
        makeReverseTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.startTransition(2000);
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransition(COLOR0, COLOR1, 2000);

        // should not accept negative duration
        mTransitionDrawable.startTransition(-1);
    }

    @Test
    public void testResetTransition() {
        Drawable.Callback cb = mock(Drawable.Callback.class);
        mTransitionDrawable.setCallback(cb);

        // reset when there is no transition
        mTransitionDrawable.resetTransition();
        verify(cb, times(1)).invalidateDrawable(any());

        // reset when there is a transition in progress
        makeTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.resetTransition();
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransitionStart(COLOR0);
        verifyTransitionEnd(COLOR0, 2000);

        // reset when there is a reverse transition in progress
        makeReverseTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.resetTransition();
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransitionStart(COLOR0);
        verifyTransitionEnd(COLOR0, 2000);
    }

    @Test
    public void testReverseTransition() {
        Drawable.Callback cb = mock(Drawable.Callback.class);
        mTransitionDrawable.setCallback(cb);

        // reverse when there is no transition
        mTransitionDrawable.reverseTransition(2000);
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransition(COLOR0, COLOR1, 2000);

        // reverse after the other transition ends
        reset(cb);
        mTransitionDrawable.reverseTransition(2000);
        verify(cb, times(1)).invalidateDrawable(any());
        verifyTransition(COLOR1, COLOR0, 2000);

        // reverse when there is a transition in progress
        makeTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.reverseTransition(20000);
        verify(cb, never()).invalidateDrawable(any());
        int colorFrom = mBitmap.getPixel(0, 0);
        verifyTransition(colorFrom, COLOR0, 1500);

        // reverse when there is a reverse transition in progress
        makeReverseTransitionInProgress(2000, 1000);
        reset(cb);
        mTransitionDrawable.reverseTransition(20000);
        verify(cb, never()).invalidateDrawable(any());
        colorFrom = mBitmap.getPixel(0, 0);
        verifyTransition(colorFrom, COLOR1, 1500);

        // should not accept negative duration
        mTransitionDrawable.reverseTransition(-1);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawWithNullCanvas() {
        mTransitionDrawable.draw(null);
    }

    //  This boolean takes effect when the drawable is drawn and the effect can not be tested.
    @Test
    public void testAccessCrossFadeEnabled() {
        assertFalse(mTransitionDrawable.isCrossFadeEnabled());

        mTransitionDrawable.setCrossFadeEnabled(true);
        assertTrue(mTransitionDrawable.isCrossFadeEnabled());

        mTransitionDrawable.setCrossFadeEnabled(false);
        assertFalse(mTransitionDrawable.isCrossFadeEnabled());
    }

    private void verifyTransition(int colorFrom, int colorTo, long delay) {
        verifyTransitionStart(colorFrom);
        verifyTransitionInProgress(colorFrom, colorTo, delay / 2);
        verifyTransitionEnd(colorTo, delay);
    }

    private void verifyTransitionStart(int colorFrom) {
        mBitmap.eraseColor(Color.TRANSPARENT);
        mTransitionDrawable.draw(mCanvas);
        verifyColorFillRect(mBitmap, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, colorFrom);
    }

    private void verifyTransitionInProgress(int colorFrom, int colorTo, long delay) {
        drawAfterDelaySync(delay);
        verifyColorNotFillRect(mBitmap, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, colorFrom);
        verifyColorNotFillRect(mBitmap, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, colorTo);
    }

    private void verifyTransitionEnd(int colorTo, long delay) {
        drawAfterDelaySync(delay);
        verifyColorFillRect(mBitmap, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, colorTo);
    }

    private void verifyColorFillRect(Bitmap bmp, int x, int y, int w, int h, int color) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                assertEquals(color, bmp.getPixel(i, j));
            }
        }
    }

    private void verifyColorNotFillRect(Bitmap bmp, int x, int y, int w, int h, int color) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                assertTrue(color != bmp.getPixel(i, j));
            }
        }
    }

    private void makeReverseTransitionInProgress(int duration, int delay) {
        mTransitionDrawable.resetTransition();
        mTransitionDrawable.startTransition(2000);
        verifyTransition(COLOR0, COLOR1, 2000);
        mTransitionDrawable.reverseTransition(duration);
        verifyTransitionStart(COLOR1);
        verifyTransitionInProgress(COLOR1, COLOR0, delay);
    }

    private void makeTransitionInProgress(int duration, int delay) {
        mTransitionDrawable.resetTransition();
        mTransitionDrawable.startTransition(duration);
        verifyTransitionStart(COLOR0);
        verifyTransitionInProgress(COLOR0, COLOR1, delay);
    }

    private void drawAfterDelaySync(long delay) {
        Thread t = new Thread(() -> {
            mBitmap.eraseColor(Color.TRANSPARENT);
            mTransitionDrawable.draw(mCanvas);
        });
        try {
            Thread.sleep(delay);
            t.start();
            t.join();
        } catch (InterruptedException e) {
            // catch and fail, because propagating this all the way up is messy
            fail(e.getMessage());
        }
    }
}
