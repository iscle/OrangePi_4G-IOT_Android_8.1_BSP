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
package android.graphics.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Canvas.EdgeType;
import android.graphics.Canvas.VertexMode;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.DrawFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Picture;
import android.graphics.PorterDuff.Mode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region.Op;
import android.graphics.Shader;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.util.DisplayMetrics;

import com.android.compatibility.common.util.ColorUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CanvasTest {
    private final static int PAINT_COLOR = 0xff00ff00;
    private final static int BITMAP_WIDTH = 10;
    private final static int BITMAP_HEIGHT = 28;
    private final static int FLOAT_ARRAY_LEN = 9;

    // used for save related methods tests
    private final float[] values1 = {
            1, 2, 3, 4, 5, 6, 7, 8, 9
    };

    private final float[] values2 = {
            9, 8, 7, 6, 5, 4, 3, 2, 1
    };

    private Paint mPaint;
    private Canvas mCanvas;
    private Bitmap mImmutableBitmap;
    private Bitmap mMutableBitmap;

    @Before
    public void setup() {
        mPaint = new Paint();
        mPaint.setColor(PAINT_COLOR);

        final Resources res = InstrumentationRegistry.getTargetContext().getResources();
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inScaled = false; // bitmap will only be immutable if not scaled during load
        mImmutableBitmap = BitmapFactory.decodeResource(res, R.drawable.start, opt);
        assertFalse(mImmutableBitmap.isMutable());
        mMutableBitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Config.ARGB_8888);
        mCanvas = new Canvas(mMutableBitmap);
    }

    @Test
    public void testCanvas() {
        new Canvas();

        mMutableBitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Config.ARGB_8888);
        new Canvas(mMutableBitmap);
    }

    @Test(expected=IllegalStateException.class)
    public void testCanvasFromImmutableBitmap() {
        // Should throw out IllegalStateException when creating Canvas with an ImmutableBitmap
        new Canvas(mImmutableBitmap);
    }

    @Test(expected=RuntimeException.class)
    public void testCanvasFromRecycledBitmap() {
        // Should throw out RuntimeException when creating Canvas with a MutableBitmap which
        // is recycled
        mMutableBitmap.recycle();
        new Canvas(mMutableBitmap);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetBitmapToImmutableBitmap() {
        // Should throw out IllegalStateException when setting an ImmutableBitmap to a Canvas
        mCanvas.setBitmap(mImmutableBitmap);
    }

    @Test(expected=RuntimeException.class)
    public void testSetBitmapToRecycledBitmap() {
        // Should throw out RuntimeException when setting Bitmap which is recycled to a Canvas
        mMutableBitmap.recycle();
        mCanvas.setBitmap(mMutableBitmap);
    }

    @Test
    public void testSetBitmap() {
        mMutableBitmap = Bitmap.createBitmap(BITMAP_WIDTH, 31, Config.ARGB_8888);
        mCanvas.setBitmap(mMutableBitmap);
        assertEquals(BITMAP_WIDTH, mCanvas.getWidth());
        assertEquals(31, mCanvas.getHeight());
    }

    @Test
    public void testSetBitmapFromEmpty() {
        Canvas canvas = new Canvas();
        assertEquals(0, canvas.getWidth());
        assertEquals(0, canvas.getHeight());

        // now ensure that we can "grow" the canvas

        Bitmap normal = Bitmap.createBitmap(10, 10, Config.ARGB_8888);
        canvas.setBitmap(normal);
        assertEquals(10, canvas.getWidth());
        assertEquals(10, canvas.getHeight());

        // now draw, and check that the clip was "open"
        canvas.drawColor(0xFFFF0000);
        assertEquals(0xFFFF0000, normal.getPixel(5, 5));
    }

    @Test
    public void testSetBitmapCleanClip() {
        mCanvas.setBitmap(Bitmap.createBitmap(10, 10, Config.ARGB_8888));
        Rect r = new Rect(2, 2, 8, 8);
        mCanvas.save();
        mCanvas.clipRect(r);
        assertEquals(r, mCanvas.getClipBounds());

        // "reset" the canvas, and then check that the clip is wide open
        // and not the previous value

        mCanvas.setBitmap(Bitmap.createBitmap(20, 20, Config.ARGB_8888));
        r = new Rect(0, 0, 20, 20);
        assertEquals(r, mCanvas.getClipBounds());
    }

    @Test
    public void testSetBitmapSaveCount() {
        Canvas c = new Canvas(Bitmap.createBitmap(10, 10, Config.ARGB_8888));
        int initialSaveCount = c.getSaveCount();

        c.save();
        assertEquals(c.getSaveCount(), initialSaveCount + 1);

        // setBitmap should restore the saveCount to its original/base value
        c.setBitmap(Bitmap.createBitmap(10, 10, Config.ARGB_8888));
        assertEquals(c.getSaveCount(), initialSaveCount);
    }

    @Test
    public void testIsOpaque() {
        assertFalse(mCanvas.isOpaque());
    }

    @Test(expected=IllegalStateException.class)
    public void testRestoreWithoutSave() {
        // Should throw out IllegalStateException because cannot restore Canvas before save
        mCanvas.restore();
    }

    @Test
    public void testRestore() {
        mCanvas.save();
        mCanvas.restore();
    }

    @Test
    public void testSave1() {
        final Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.save();

        final Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        final float[] values3 = new float[FLOAT_ARRAY_LEN];
        final Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        final float[] values4 = new float[FLOAT_ARRAY_LEN];
        final Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testSave2() {
        // test save current matrix only
        Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.save(Canvas.MATRIX_SAVE_FLAG);

        Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        float[] values3 = new float[FLOAT_ARRAY_LEN];
        Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        float[] values4 = new float[FLOAT_ARRAY_LEN];
        Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save current clip only, don't know how to get clip saved,
        // but can make sure Matrix can't be saved in this case
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.save(Canvas.CLIP_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values2, values4, 0.0f);

        // test save everything
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.save(Canvas.ALL_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testSaveFlags1() {
        int[] flags = {
            Canvas.MATRIX_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags2() {
        int[] flags = {
            Canvas.CLIP_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags3() {
        int[] flags = {
            Canvas.ALL_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags4() {
        int[] flags = {
            Canvas.ALL_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags5() {
        int[] flags = {
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags6() {
        int[] flags = {
            Canvas.CLIP_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags7() {
        int[] flags = {
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.ALL_SAVE_FLAG,
            Canvas.ALL_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    @Test
    public void testSaveFlags8() {
        int[] flags = {
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.ALL_SAVE_FLAG,
            Canvas.MATRIX_SAVE_FLAG,
            Canvas.CLIP_SAVE_FLAG,
            Canvas.ALL_SAVE_FLAG,
        };
        verifySaveFlagsSequence(flags);
    }

    // This test exercises the saveLayer flag that preserves the clip
    // state across the matching restore call boundary. This is a vanilla
    // test and doesn't exercise any interaction between the clip stack
    // and SkCanvas' deferred save/restore system.
    @Test
    public void testSaveFlags9() {
        Rect clip0 = new Rect();
        assertTrue(mCanvas.getClipBounds(clip0));

        mCanvas.save(Canvas.MATRIX_SAVE_FLAG);

            // All clip elements should be preserved after restore
            mCanvas.clipRect(0, 0, BITMAP_WIDTH / 2, BITMAP_HEIGHT);
            Path path = new Path();
            path.addOval(0.25f * BITMAP_WIDTH, 0.25f * BITMAP_HEIGHT,
                         0.75f * BITMAP_WIDTH, 0.75f * BITMAP_HEIGHT,
                         Path.Direction.CW);
            mCanvas.clipPath(path);
            mCanvas.clipRect(0, 0, BITMAP_WIDTH, BITMAP_HEIGHT / 2);

            Rect clip1 = new Rect();
            assertTrue(mCanvas.getClipBounds(clip1));
            assertTrue(clip1 != clip0);
            assertTrue(clip0.contains(clip1));

        mCanvas.restore();

        Rect clip2 = new Rect();
        assertTrue(mCanvas.getClipBounds(clip2));
        assertEquals(clip2, clip1);
    }

    // This test exercises the saveLayer MATRIX_SAVE_FLAG flag and its
    // interaction with the clip stack and SkCanvas deferred save/restore
    // system.
    @Test
    public void testSaveFlags10() {
        RectF rect1 = new RectF(0, 0, BITMAP_WIDTH / 2, BITMAP_HEIGHT);
        RectF rect2 = new RectF(0, 0, BITMAP_WIDTH, BITMAP_HEIGHT / 2);
        Path path = new Path();
        path.addOval(0.25f * BITMAP_WIDTH, 0.25f * BITMAP_HEIGHT,
                     0.75f * BITMAP_WIDTH, 0.75f * BITMAP_HEIGHT,
                     Path.Direction.CW);

        Rect clip0 = new Rect();
        assertTrue(mCanvas.getClipBounds(clip0));

        // Exercise various Canvas lazy-save interactions.
        mCanvas.save();
            mCanvas.save();
                mCanvas.clipRect(rect1);
                mCanvas.clipPath(path);

                Rect clip1 = new Rect();
                assertTrue(mCanvas.getClipBounds(clip1));
                assertTrue(clip1 != clip0);

                mCanvas.save(Canvas.MATRIX_SAVE_FLAG);
                    mCanvas.save(Canvas.MATRIX_SAVE_FLAG);
                        mCanvas.clipRect(rect2);
                        mCanvas.clipPath(path);

                        Rect clip2 = new Rect();
                        assertTrue(mCanvas.getClipBounds(clip2));
                        assertTrue(clip2 != clip1);
                        assertTrue(clip2 != clip0);

                        mCanvas.save();
                            mCanvas.translate(10, 5);
                            mCanvas.save(Canvas.MATRIX_SAVE_FLAG);
                                // An uncommitted save/restore frame: exercises
                                // the partial save emulation, ensuring there
                                // are no side effects.
                                Rect clip3 = new Rect();
                                assertTrue(mCanvas.getClipBounds(clip3));
                                clip3.offset(10, 5); // adjust for local offset
                                assertEquals(clip3, clip2);
                            mCanvas.restore();

                            Rect clip4 = new Rect();
                            assertTrue(mCanvas.getClipBounds(clip4));
                            clip4.offset(10, 5); // adjust for local offset
                            assertEquals(clip4, clip2);
                        mCanvas.restore();

                        Rect clip5 = new Rect();
                        assertTrue(mCanvas.getClipBounds(clip5));
                        assertEquals(clip5, clip2);
                    mCanvas.restore();

                    // clip2 survives the preceding restore
                    Rect clip6 = new Rect();
                    assertTrue(mCanvas.getClipBounds(clip6));
                    assertEquals(clip6, clip2);
                mCanvas.restore();

                // clip2 also survives the preceding restore
                Rect clip7 = new Rect();
                assertTrue(mCanvas.getClipBounds(clip7));
                assertEquals(clip7, clip2);
            mCanvas.restore();

            // clip1 does _not_ survive the preceding restore
            Rect clip8 = new Rect();
            assertTrue(mCanvas.getClipBounds(clip8));
            assertEquals(clip8, clip0);
        mCanvas.restore();

        Rect clip9 = new Rect();
        assertTrue(mCanvas.getClipBounds(clip9));
        assertEquals(clip9, clip0);
    }

    @Test
    public void testSaveLayer1() {
        final Paint p = new Paint();
        final RectF rF = new RectF(0, 10, 31, 0);

        // test save current matrix only
        Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(rF, p, Canvas.MATRIX_SAVE_FLAG);

        Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        float[] values3 = new float[FLOAT_ARRAY_LEN];
        Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        float[] values4 = new float[FLOAT_ARRAY_LEN];
        Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save current clip flag only: this should save matrix as well
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(rF, p, Canvas.CLIP_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save everything
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(rF, p, Canvas.ALL_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testSaveLayer2() {
        final Paint p = new Paint();

        // test save current matrix only
        Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(10, 0, 0, 31, p, Canvas.MATRIX_SAVE_FLAG);

        Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        float[] values3 = new float[FLOAT_ARRAY_LEN];
        Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        float[] values4 = new float[FLOAT_ARRAY_LEN];
        Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save current clip flag only: this should save matrix as well
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(10, 0, 0, 31, p, Canvas.CLIP_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save everything
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayer(10, 0, 0, 31, p, Canvas.ALL_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testSaveLayerAlpha1() {
        final RectF rF = new RectF(0, 10, 31, 0);

        // test save current matrix only
        Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(rF, 0xff, Canvas.MATRIX_SAVE_FLAG);

        Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        float[] values3 = new float[FLOAT_ARRAY_LEN];
        Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        float[] values4 = new float[FLOAT_ARRAY_LEN];
        Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save current clip flag only: this should save matrix as well
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(rF, 0xff, Canvas.CLIP_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save everything
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(rF, 0xff, Canvas.ALL_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testSaveLayerAlpha2() {
        // test save current matrix only
        Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(0, 10, 31, 0, 0xff, Canvas.MATRIX_SAVE_FLAG);

        Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        float[] values3 = new float[FLOAT_ARRAY_LEN];
        Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        float[] values4 = new float[FLOAT_ARRAY_LEN];
        Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save current clip flag only: this should save matrix as well
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(0, 10, 31, 0, 0xff, Canvas.CLIP_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);

        // test save everything
        m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        mCanvas.saveLayerAlpha(0, 10, 31, 0, 0xff, Canvas.ALL_SAVE_FLAG);

        m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        values3 = new float[FLOAT_ARRAY_LEN];
        m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restore();
        values4 = new float[FLOAT_ARRAY_LEN];
        m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testGetSaveCount() {
        // why is 1 not 0
        assertEquals(1, mCanvas.getSaveCount());
        mCanvas.save();
        assertEquals(2, mCanvas.getSaveCount());
        mCanvas.save();
        assertEquals(3, mCanvas.getSaveCount());
        mCanvas.saveLayer(new RectF(), new Paint(), Canvas.ALL_SAVE_FLAG);
        assertEquals(4, mCanvas.getSaveCount());
        mCanvas.saveLayerAlpha(new RectF(), 0, Canvas.ALL_SAVE_FLAG);
        assertEquals(5, mCanvas.getSaveCount());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRestoreToCountIllegalSaveCount() {
        // Should throw out IllegalArgumentException because saveCount is less than 1
        mCanvas.restoreToCount(0);
    }

    @Test
    public void testRestoreToCountExceptionBehavior() {
        int restoreTo = mCanvas.save();
        mCanvas.save();
        int beforeCount = mCanvas.getSaveCount();

        boolean exceptionObserved = false;
        try {
            mCanvas.restoreToCount(restoreTo - 1);
        } catch (IllegalArgumentException e) {
            exceptionObserved = true;
        }

        // restore to count threw, AND did no restoring
        assertTrue(exceptionObserved);
        assertEquals(beforeCount, mCanvas.getSaveCount());
    }

    @Test
    public void testRestoreToCount() {
        final Matrix m1 = new Matrix();
        m1.setValues(values1);
        mCanvas.setMatrix(m1);
        final int count = mCanvas.save();
        assertTrue(count > 0);

        final Matrix m2 = new Matrix();
        m2.setValues(values2);
        mCanvas.setMatrix(m2);

        final float[] values3 = new float[FLOAT_ARRAY_LEN];
        final Matrix m3 = mCanvas.getMatrix();
        m3.getValues(values3);

        assertArrayEquals(values2, values3, 0.0f);

        mCanvas.restoreToCount(count);
        final float[] values4 = new float[FLOAT_ARRAY_LEN];
        final Matrix m4 = mCanvas.getMatrix();
        m4.getValues(values4);

        assertArrayEquals(values1, values4, 0.0f);
    }

    @Test
    public void testGetMatrix1() {
        final float[] f1 = {
                1, 2, 3, 4, 5, 6, 7, 8, 9
        };
        final Matrix m1 = new Matrix();
        m1.setValues(f1);
        mCanvas.setMatrix(m1);

        final Matrix m2 = new Matrix(m1);
        mCanvas.getMatrix(m2);

        assertTrue(m1.equals(m2));

        final float[] f2 = new float[FLOAT_ARRAY_LEN];
        m2.getValues(f2);

        assertArrayEquals(f1, f2, 0.0f);
    }

    @Test
    public void testGetMatrix2() {
        final float[] f1 = {
                1, 2, 3, 4, 5, 6, 7, 8, 9
        };
        final Matrix m1 = new Matrix();
        m1.setValues(f1);

        mCanvas.setMatrix(m1);
        final Matrix m2 = mCanvas.getMatrix();

        assertTrue(m1.equals(m2));

        final float[] f2 = new float[FLOAT_ARRAY_LEN];
        m2.getValues(f2);

        assertArrayEquals(f1, f2, 0.0f);
    }

    @Test
    public void testTranslate() {
        preCompare();

        mCanvas.translate(0.10f, 0.28f);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
            1.0f, 0.0f, 0.1f, 0.0f, 1.0f, 0.28f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testScale1() {
        preCompare();

        mCanvas.scale(0.5f, 0.5f);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testScale2() {
        preCompare();

        mCanvas.scale(3.0f, 3.0f, 1.0f, 1.0f);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                3.0f, 0.0f, -2.0f, 0.0f, 3.0f, -2.0f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testRotate1() {
        preCompare();

        mCanvas.rotate(90);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testRotate2() {
        preCompare();

        mCanvas.rotate(30, 1.0f, 0.0f);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                0.8660254f, -0.5f, 0.13397461f, 0.5f, 0.8660254f, -0.5f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testSkew() {
        preCompare();

        mCanvas.skew(1.0f, 3.0f);

        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                1.0f, 1.0f, 0.0f, 3.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    @Test
    public void testConcat() {
        preCompare();

        final Matrix m = new Matrix();
        final float[] values = {0, 1, 2, 3, 4, 5, 6, 7, 8};

        m.setValues(values);
        mCanvas.concat(m);

        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f
        }, values, 0.0f);
    }

    @Test
    public void testClipRectF() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(new RectF(0, 0, 10, 31), Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(new RectF(10, 31, 11, 32), Op.INTERSECT));
        // replace with clip that is larger than canvas
        assertTrue(mCanvas.clipRect(new RectF(0, 0, 10, 31), Op.REPLACE));
        // intersect with clip that covers top portion of canvas
        assertTrue(mCanvas.clipRect(new RectF(0, 0, 20, 10), Op.INTERSECT));
        // intersect with clip that covers bottom portion of canvas
        assertFalse(mCanvas.clipRect(new RectF(0, 10, 20, 32), Op.INTERSECT));
        // ensure that difference doesn't widen already closed clip
        assertFalse(mCanvas.clipRect(new RectF(0, 0, 10, 31), Op.DIFFERENCE));
    }

    @Test
    public void testClipRect() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(new Rect(0, 0, 10, 31), Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(new Rect(10, 31, 11, 32), Op.INTERSECT));
        // replace with clip that is larger than canvas
        assertTrue(mCanvas.clipRect(new Rect(0, 0, 10, 31), Op.REPLACE));
        // intersect with clip that covers top portion of canvas
        assertTrue(mCanvas.clipRect(new Rect(0, 0, 20, 10), Op.INTERSECT));
        // intersect with clip that covers bottom portion of canvas
        assertFalse(mCanvas.clipRect(new Rect(0, 10, 20, 32), Op.INTERSECT));
        // ensure that difference doesn't widen already closed clip
        assertFalse(mCanvas.clipRect(new Rect(0, 0, 10, 31), Op.DIFFERENCE));
    }

    @Test
    public void testClipRect4I() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(0, 0, 10, 31, Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(10, 31, 11, 32, Op.INTERSECT));
        // replace with clip that is larger than canvas
        assertTrue(mCanvas.clipRect(0, 0, 10, 31, Op.REPLACE));
        // intersect with clip that covers top portion of canvas
        assertTrue(mCanvas.clipRect(0, 0, 20, 10, Op.INTERSECT));
        // intersect with clip that covers bottom portion of canvas
        assertFalse(mCanvas.clipRect(0, 10, 20, 32, Op.INTERSECT));
        // ensure that difference doesn't widen already closed clip
        assertFalse(mCanvas.clipRect(0, 0, 10, 31, Op.DIFFERENCE));
    }

    @Test
    public void testClipRect4F() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(0f, 0f, 10f, 31f, Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(10f, 31f, 11f, 32f, Op.INTERSECT));
        // replace with clip that is larger than canvas
        assertTrue(mCanvas.clipRect(0f, 0f, 10f, 31f, Op.REPLACE));
        // intersect with clip that covers top portion of canvas
        assertTrue(mCanvas.clipRect(0f, 0f, 20f, 10f, Op.INTERSECT));
        // intersect with clip that covers bottom portion of canvas
        assertFalse(mCanvas.clipRect(0f, 10f, 20f, 32f, Op.INTERSECT));
        // ensure that difference doesn't widen already closed clip
        assertFalse(mCanvas.clipRect(0f, 0f, 10f, 31f, Op.DIFFERENCE));
    }

    @Test
    public void testClipOutRectF() {
        // remove center, clip not empty
        assertTrue(mCanvas.clipOutRect(new RectF(1, 1, 9, 27)));
        // replace clip, verify difference doesn't widen
        assertFalse(mCanvas.clipRect(new RectF(0, 0, 0, 0), Op.REPLACE));
        assertFalse(mCanvas.clipOutRect(new RectF(0, 0, 100, 100)));
    }

    @Test
    public void testClipOutRect() {
        // remove center, clip not empty
        assertTrue(mCanvas.clipOutRect(new Rect(1, 1, 9, 27)));
        // replace clip, verify difference doesn't widen
        assertFalse(mCanvas.clipRect(new Rect(0, 0, 0, 0), Op.REPLACE));
        assertFalse(mCanvas.clipOutRect(new Rect(0, 0, 100, 100)));
    }

    @Test
    public void testClipOutRect4I() {
        // remove center, clip not empty
        assertTrue(mCanvas.clipOutRect(1, 1, 9, 27));
        // replace clip, verify difference doesn't widen
        assertFalse(mCanvas.clipRect(0, 0, 0, 0, Op.REPLACE));
        assertFalse(mCanvas.clipOutRect(0, 0, 100, 100));
    }

    @Test
    public void testClipOutRect4F() {
        // remove center, clip not empty
        assertTrue(mCanvas.clipOutRect(1f, 1f, 9f, 27f));
        // replace clip, verify difference doesn't widen
        assertFalse(mCanvas.clipRect(0f, 0f, 0f, 0f, Op.REPLACE));
        assertFalse(mCanvas.clipOutRect(0f, 0f, 100f, 100f));
    }

    @Test
    public void testIntersectClipRectF() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(new RectF(0, 0, 10, 31)));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(new RectF(10, 31, 11, 32)));
    }

    @Test
    public void testIntersectClipRect() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(new Rect(0, 0, 10, 31)));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(new Rect(10, 31, 11, 32)));
    }

    @Test
    public void testIntersectClipRect4F() {
        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipRect(0, 0, 10, 31));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipRect(10, 31, 11, 32));
    }

    @Test
    public void testClipPath1() {
        final Path p = new Path();
        p.addRect(new RectF(0, 0, 10, 31), Direction.CCW);
        assertTrue(mCanvas.clipPath(p));
    }

    @Test
    public void testClipPath2() {
        final Path p = new Path();
        p.addRect(new RectF(0, 0, 10, 31), Direction.CW);

        final Path pIn = new Path();
        pIn.addOval(new RectF(0, 0, 20, 10), Direction.CW);

        final Path pOut = new Path();
        pOut.addRoundRect(new RectF(10, 31, 11, 32), 0.5f, 0.5f, Direction.CW);

        // intersect with clip larger than canvas
        assertTrue(mCanvas.clipPath(p, Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipPath(pOut, Op.INTERSECT));
        // replace with clip that is larger than canvas
        assertTrue(mCanvas.clipPath(p, Op.REPLACE));
        // intersect with clip that covers top portion of canvas
        assertTrue(mCanvas.clipPath(pIn, Op.INTERSECT));
        // intersect with clip outside of canvas bounds
        assertFalse(mCanvas.clipPath(pOut, Op.INTERSECT));
        // ensure that difference doesn't widen already closed clip
        assertFalse(mCanvas.clipPath(p, Op.DIFFERENCE));
    }

    @Test
    public void testClipOutPath() {
        final Path p = new Path();
        p.addRect(new RectF(5, 5, 10, 10), Direction.CW);
        assertTrue(mCanvas.clipOutPath(p));
    }

    @Test
    public void testClipInversePath() {
        final Path p = new Path();
        p.addRoundRect(new RectF(0, 0, 10, 10), 0.5f, 0.5f, Direction.CW);
        p.setFillType(Path.FillType.INVERSE_WINDING);
        assertTrue(mCanvas.clipPath(p, Op.INTERSECT));

        mCanvas.drawColor(PAINT_COLOR);

        assertEquals(Color.TRANSPARENT, mMutableBitmap.getPixel(0, 0));
        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 20));
    }

    @Test
    public void testGetDrawFilter() {
        assertNull(mCanvas.getDrawFilter());
        final DrawFilter dF = new DrawFilter();
        mCanvas.setDrawFilter(dF);

        assertTrue(dF.equals(mCanvas.getDrawFilter()));
    }

    @Test
    public void testQuickReject1() {
        assertFalse(mCanvas.quickReject(new RectF(0, 0, 10, 31), EdgeType.AA));
        assertFalse(mCanvas.quickReject(new RectF(0, 0, 10, 31), EdgeType.BW));
    }

    @Test
    public void testQuickReject2() {
        final Path p = new Path();
        p.addRect(new RectF(0, 0, 10, 31), Direction.CCW);

        assertFalse(mCanvas.quickReject(p, EdgeType.AA));
        assertFalse(mCanvas.quickReject(p, EdgeType.BW));
    }

    @Test
    public void testQuickReject3() {
        assertFalse(mCanvas.quickReject(0, 0, 10, 31, EdgeType.AA));
        assertFalse(mCanvas.quickReject(0, 0, 10, 31, EdgeType.BW));
    }

    @Test
    public void testGetClipBounds1() {
        final Rect r = new Rect();

        assertTrue(mCanvas.getClipBounds(r));
        assertEquals(BITMAP_WIDTH, r.width());
        assertEquals(BITMAP_HEIGHT, r.height());
    }

    @Test
    public void testGetClipBounds2() {
        final Rect r = mCanvas.getClipBounds();

        assertEquals(BITMAP_WIDTH, r.width());
        assertEquals(BITMAP_HEIGHT, r.height());
    }

    private void verifyDrewColor(int color) {
        assertEquals(color, mMutableBitmap.getPixel(0, 0));
        assertEquals(color, mMutableBitmap.getPixel(BITMAP_WIDTH / 2, BITMAP_HEIGHT / 2));
        assertEquals(color, mMutableBitmap.getPixel(BITMAP_WIDTH - 1, BITMAP_HEIGHT - 1));
    }

    @Test
    public void testDrawRGB() {
        final int alpha = 0xff;
        final int red = 0xff;
        final int green = 0xff;
        final int blue = 0xff;

        mCanvas.drawRGB(red, green, blue);

        final int color = alpha << 24 | red << 16 | green << 8 | blue;
        verifyDrewColor(color);
    }

    @Test
    public void testDrawARGB() {
        final int alpha = 0xff;
        final int red = 0x22;
        final int green = 0x33;
        final int blue = 0x44;

        mCanvas.drawARGB(alpha, red, green, blue);
        final int color = alpha << 24 | red << 16 | green << 8 | blue;
        verifyDrewColor(color);
    }

    @Test
    public void testDrawColor1() {
        final int color = Color.RED;

        mCanvas.drawColor(color);
        verifyDrewColor(color);
    }

    @Test
    public void testDrawColor2() {
        mCanvas.drawColor(Color.RED, Mode.CLEAR);
        mCanvas.drawColor(Color.RED, Mode.DARKEN);
        mCanvas.drawColor(Color.RED, Mode.DST);
        mCanvas.drawColor(Color.RED, Mode.DST_ATOP);
        mCanvas.drawColor(Color.RED, Mode.DST_IN);
        mCanvas.drawColor(Color.RED, Mode.DST_OUT);
        mCanvas.drawColor(Color.RED, Mode.DST_OVER);
        mCanvas.drawColor(Color.RED, Mode.LIGHTEN);
        mCanvas.drawColor(Color.RED, Mode.MULTIPLY);
        mCanvas.drawColor(Color.RED, Mode.SCREEN);
        mCanvas.drawColor(Color.RED, Mode.SRC);
        mCanvas.drawColor(Color.RED, Mode.SRC_ATOP);
        mCanvas.drawColor(Color.RED, Mode.SRC_IN);
        mCanvas.drawColor(Color.RED, Mode.SRC_OUT);
        mCanvas.drawColor(Color.RED, Mode.SRC_OVER);
        mCanvas.drawColor(Color.RED, Mode.XOR);
    }

    @Test
    public void testDrawPaint() {
        mCanvas.drawPaint(mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawPointsInvalidOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because of invalid offset
        mCanvas.drawPoints(new float[]{
                10.0f, 29.0f
        }, -1, 2, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawPointsInvalidCount() {
        // Should throw out ArrayIndexOutOfBoundsException because of invalid count
        mCanvas.drawPoints(new float[]{
                10.0f, 29.0f
        }, 0, 31, mPaint);
    }

    @Test
    public void testDrawPoints1() {
        // normal case
        mCanvas.drawPoints(new float[] {
                0, 0
        }, 0, 2, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test
    public void testDrawPoints2() {
        mCanvas.drawPoints(new float[]{0, 0}, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test
    public void testDrawPoint() {
        mCanvas.drawPoint(0, 0, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test
    public void testDrawLine() {
        mCanvas.drawLine(0, 0, 10, 12, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawLinesInvalidOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because of invalid offset
        mCanvas.drawLines(new float[]{
                0, 0, 10, 31
        }, 2, 4, new Paint());
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawLinesInvalidCount() {
        // Should throw out ArrayIndexOutOfBoundsException because of invalid count
        mCanvas.drawLines(new float[]{
                0, 0, 10, 31
        }, 0, 8, new Paint());
    }

    @Test
    public void testDrawLines1() {
        // normal case
        mCanvas.drawLines(new float[] {
                0, 0, 10, 12
        }, 0, 4, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    @Test
    public void testDrawLines2() {
        mCanvas.drawLines(new float[] {
                0, 0, 10, 12
        }, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
    }

    private void verifyDrewPaint() {
        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(0, 0));
        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(5, 6));
        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(9, 11));
    }

    @Test
    public void testDrawRect1() {
        mCanvas.drawRect(new RectF(0, 0, 10, 12), mPaint);

        verifyDrewPaint();
    }

    @Test
    public void testDrawRect2() {
        mCanvas.drawRect(new Rect(0, 0, 10, 12), mPaint);

        verifyDrewPaint();
    }

    @Test
    public void testDrawRect3() {
        mCanvas.drawRect(0, 0, 10, 12, mPaint);

        verifyDrewPaint();
    }

    @Test(expected=NullPointerException.class)
    public void testDrawOvalNull() {
        // Should throw out NullPointerException because oval is null
        mCanvas.drawOval(null, mPaint);
    }

    @Test
    public void testDrawOval() {
        // normal case
        mCanvas.drawOval(new RectF(0, 0, 10, 12), mPaint);
    }

    @Test
    public void testDrawCircle() {
        // special case: circle's radius <= 0
        mCanvas.drawCircle(10.0f, 10.0f, -1.0f, mPaint);

        // normal case
        mCanvas.drawCircle(10, 12, 3, mPaint);

        assertEquals(PAINT_COLOR, mMutableBitmap.getPixel(9, 11));
    }

    @Test(expected=NullPointerException.class)
    public void testDrawArcNullOval() {
        // Should throw NullPointerException because oval is null
        mCanvas.drawArc(null, 10.0f, 29.0f, true, mPaint);
    }

    @Test
    public void testDrawArc() {
        // normal case
        mCanvas.drawArc(new RectF(0, 0, 10, 12), 10, 11, false, mPaint);
        mCanvas.drawArc(new RectF(0, 0, 10, 12), 10, 11, true, mPaint);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawRoundRectNull() {
        // Should throw out NullPointerException because RoundRect is null
        mCanvas.drawRoundRect(null, 10.0f, 29.0f, mPaint);
    }

    @Test
    public void testDrawRoundRect() {
        mCanvas.drawRoundRect(new RectF(0, 0, 10, 12), 8, 8, mPaint);
    }

    @Test
    public void testDrawPath() {
        mCanvas.drawPath(new Path(), mPaint);
    }

    @Test(expected=RuntimeException.class)
    public void testDrawBitmapAtPointRecycled() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        b.recycle();

        // Should throw out RuntimeException because bitmap has been recycled
        mCanvas.drawBitmap(b, 10.0f, 29.0f, mPaint);
    }

    @Test
    public void testDrawBitmapAtPoint() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 12, Config.ARGB_8888);
        mCanvas.drawBitmap(b, 10, 12, null);
        mCanvas.drawBitmap(b, 5, 12, mPaint);
    }

    @Test(expected=RuntimeException.class)
    public void testDrawBitmapSrcDstFloatRecycled() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        b.recycle();

        // Should throw out RuntimeException because bitmap has been recycled
        mCanvas.drawBitmap(b, null, new RectF(), mPaint);
    }

    @Test
    public void testDrawBitmapSrcDstFloat() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        mCanvas.drawBitmap(b, new Rect(), new RectF(), null);
        mCanvas.drawBitmap(b, new Rect(), new RectF(), mPaint);
    }

    @Test(expected=RuntimeException.class)
    public void testDrawBitmapSrcDstIntRecycled() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        b.recycle();

        // Should throw out RuntimeException because bitmap has been recycled
        mCanvas.drawBitmap(b, null, new Rect(), mPaint);
    }

    @Test
    public void testDrawBitmapSrcDstInt() {
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        mCanvas.drawBitmap(b, new Rect(), new Rect(), null);
        mCanvas.drawBitmap(b, new Rect(), new Rect(), mPaint);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapIntsNegativeWidth() {
        // Should throw out IllegalArgumentException because width is less than 0
        mCanvas.drawBitmap(new int[2008], 10, 10, 10, 10, -1, 10, true, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapIntsNegativeHeight() {
        // Should throw out IllegalArgumentException because height is less than 0
        mCanvas.drawBitmap(new int[2008], 10, 10, 10, 10, 10, -1, true, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapIntsBadStride() {
        // Should throw out IllegalArgumentException because stride less than width and
        // bigger than -width
        mCanvas.drawBitmap(new int[2008], 10, 5, 10, 10, 10, 10, true, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapIntsNegativeOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because offset less than 0
        mCanvas.drawBitmap(new int[2008], -1, 10, 10, 10, 10, 10, true, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapIntsBadOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because sum of offset and width
        // is bigger than colors' length
        mCanvas.drawBitmap(new int[29], 10, 29, 10, 10, 20, 10, true, null);
    }

    @Test
    public void testDrawBitmapInts() {
        final int[] colors = new int[2008];

        // special case: width equals to 0
        mCanvas.drawBitmap(colors, 10, 10, 10, 10, 0, 10, true, null);

        // special case: height equals to 0
        mCanvas.drawBitmap(colors, 10, 10, 10, 10, 10, 0, true, null);

        // normal case
        mCanvas.drawBitmap(colors, 10, 10, 10, 10, 10, 29, true, null);
        mCanvas.drawBitmap(colors, 10, 10, 10, 10, 10, 29, true, mPaint);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapFloatsNegativeWidth() {
        // Should throw out IllegalArgumentException because width is less than 0
        mCanvas.drawBitmap(new int[2008], 10, 10, 10.0f, 10.0f, -1, 10, true, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapFloatsNegativeHeight() {
        // Should throw out IllegalArgumentException because height is less than 0
        mCanvas.drawBitmap(new int[2008], 10, 10, 10.0f, 10.0f, 10, -1, true, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDrawBitmapFloatsBadStride() {
        // Should throw out IllegalArgumentException because stride less than width and
        // bigger than -width
        mCanvas.drawBitmap(new int[2008], 10, 5, 10.0f, 10.0f, 10, 10, true, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapFloatsNegativeOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because offset less than 0
        mCanvas.drawBitmap(new int[2008], -1, 10, 10.0f, 10.0f, 10, 10, true, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapFloatsBadOffset() {
        // Should throw out ArrayIndexOutOfBoundsException because sum of offset and width
        // is bigger than colors' length
        mCanvas.drawBitmap(new int[29], 10, 29, 10.0f, 10.0f, 20, 10, true, null);
    }

    @Test
    public void testDrawBitmapFloats() {
        final int[] colors = new int[2008];

        // special case: width equals to 0
        mCanvas.drawBitmap(colors, 10, 10, 10.0f, 10.0f, 0, 10, true, null);

        // special case: height equals to 0
        mCanvas.drawBitmap(colors, 10, 10, 10.0f, 10.0f, 10, 0, true, null);

        // normal case
        mCanvas.drawBitmap(colors, 10, 10, 10.0f, 10.0f, 10, 29, true, null);
        mCanvas.drawBitmap(colors, 10, 10, 10.0f, 10.0f, 10, 29, true, mPaint);
    }

    @Test
    public void testDrawBitmapMatrix() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);
        mCanvas.drawBitmap(b, new Matrix(), null);
        mCanvas.drawBitmap(b, new Matrix(), mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshNegativeWidth() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because meshWidth less than 0
        mCanvas.drawBitmapMesh(b, -1, 10, null, 0, null, 0, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshNegativeHeight() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because meshHeight is less than 0
        mCanvas.drawBitmapMesh(b, 10, -1, null, 0, null, 0, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshNegativeVertOffset() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because vertOffset is less than 0
        mCanvas.drawBitmapMesh(b, 10, 10, null, -1, null, 0, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshNegativeColorOffset() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because colorOffset is less than 0
        mCanvas.drawBitmapMesh(b, 10, 10, null, 10, null, -1, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshTooFewVerts() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because verts' length is too short
        mCanvas.drawBitmapMesh(b, 10, 10, new float[] {
                10.0f, 29.0f
        }, 10, null, 10, null);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawBitmapMeshTooFewColors() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // Should throw out ArrayIndexOutOfBoundsException because colors' length is too short
        // abnormal case: colors' length is too short
        final float[] verts = new float[2008];
        mCanvas.drawBitmapMesh(b, 10, 10, verts, 10, new int[] {
                10, 29
        }, 10, null);
    }

    @Test
    public void testDrawBitmapMesh() {
        final Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, 29, Config.ARGB_8888);

        // special case: meshWidth equals to 0
        mCanvas.drawBitmapMesh(b, 0, 10, null, 10, null, 10, null);

        // special case: meshHeight equals to 0
        mCanvas.drawBitmapMesh(b, 10, 0, null, 10, null, 10, null);

        // normal case
        final float[] verts = new float[2008];
        final int[] colors = new int[2008];
        mCanvas.drawBitmapMesh(b, 10, 10, verts, 10, colors, 10, null);
        mCanvas.drawBitmapMesh(b, 10, 10, verts, 10, colors, 10, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawVerticesTooFewVerts() {
        final float[] verts = new float[10];
        final float[] texs = new float[10];
        final int[] colors = new int[10];
        final short[] indices = {
                0, 1, 2, 3, 4, 1
        };

        // Should throw out ArrayIndexOutOfBoundsException because sum of vertOffset and
        // vertexCount is bigger than verts' length
        mCanvas.drawVertices(VertexMode.TRIANGLES, 10, verts, 8, texs, 0, colors, 0, indices,
                0, 4, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawVerticesTooFewTexs() {
        final float[] verts = new float[10];
        final float[] texs = new float[10];
        final int[] colors = new int[10];
        final short[] indices = {
                0, 1, 2, 3, 4, 1
        };

        // Should throw out ArrayIndexOutOfBoundsException because sum of texOffset and
        // vertexCount is bigger thatn texs' length
        mCanvas.drawVertices(VertexMode.TRIANGLES, 10, verts, 0, texs, 30, colors, 0, indices,
                0, 4, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawVerticesTooFewColors() {
        final float[] verts = new float[10];
        final float[] texs = new float[10];
        final int[] colors = new int[10];
        final short[] indices = {
                0, 1, 2, 3, 4, 1
        };

        // Should throw out ArrayIndexOutOfBoundsException because sum of colorOffset and
        // vertexCount is bigger than colors' length
        mCanvas.drawVertices(VertexMode.TRIANGLES, 10, verts, 0, texs, 0, colors, 30, indices,
                0, 4, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawVerticesTooFewIndices() {
        final float[] verts = new float[10];
        final float[] texs = new float[10];
        final int[] colors = new int[10];
        final short[] indices = {
                0, 1, 2, 3, 4, 1
        };

        // Should throw out ArrayIndexOutOfBoundsException because sum of indexOffset and
        // indexCount is bigger than indices' length
        mCanvas.drawVertices(VertexMode.TRIANGLES, 10, verts, 0, texs, 0, colors, 0, indices,
                10, 30, mPaint);
    }

    @Test
    public void testDrawVertices() {
        final float[] verts = new float[10];
        final float[] texs = new float[10];
        final int[] colors = new int[10];
        final short[] indices = {
                0, 1, 2, 3, 4, 1
        };

        // special case: in texs, colors, indices, one of them, two of them and
        // all are null
        mCanvas.drawVertices(VertexMode.TRIANGLES, 0, verts, 0, null, 0, colors, 0, indices, 0, 0,
                mPaint);

        mCanvas.drawVertices(VertexMode.TRIANGLE_STRIP, 0, verts, 0, null, 0, null, 0, indices, 0,
                0, mPaint);

        mCanvas.drawVertices(VertexMode.TRIANGLE_FAN, 0, verts, 0, null, 0, null, 0, null, 0, 0,
                mPaint);

        // normal case: texs, colors, indices are not null
        mCanvas.drawVertices(VertexMode.TRIANGLES, 10, verts, 0, texs, 0, colors, 0, indices, 0, 6,
                mPaint);

        mCanvas.drawVertices(VertexMode.TRIANGLE_STRIP, 10, verts, 0, texs, 0, colors, 0, indices,
                0, 6, mPaint);

        mCanvas.drawVertices(VertexMode.TRIANGLE_FAN, 10, verts, 0, texs, 0, colors, 0, indices, 0,
                6, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawArrayTextNegativeIndex() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // Should throw out IndexOutOfBoundsException because index is less than 0
        mCanvas.drawText(text, -1, 7, 10, 10, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawArrayTextNegativeCount() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // Should throw out IndexOutOfBoundsException because count is less than 0
        mCanvas.drawText(text, 0, -1, 10, 10, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawArrayTextTextLengthTooSmall() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // Should throw out IndexOutOfBoundsException because sum of index and count
        // is bigger than text's length
        mCanvas.drawText(text, 0, 10, 10, 10, mPaint);
    }

    @Test
    public void testDrawArrayText() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // normal case
        mCanvas.drawText(text, 0, 7, 10, 10, mPaint);
    }

    @Test
    public void testDrawStringTextAtPosition() {
        mCanvas.drawText("android", 10, 30, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextTextAtPositionWithOffsetsNegativeStart() {
        // Should throw out IndexOutOfBoundsException because start is less than 0
        mCanvas.drawText("android", -1, 7, 10, 30, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextTextAtPositionWithOffsetsNegativeEnd() {
        // Should throw out IndexOutOfBoundsException because end is less than 0
        mCanvas.drawText("android", 0, -1, 10, 30, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextTextAtPositionWithOffsetsStartEndMismatch() {
        // Should throw out IndexOutOfBoundsException because start is bigger than end
        mCanvas.drawText("android", 3, 1, 10, 30, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextTextAtPositionWithOffsetsTextTooLong() {
        // Should throw out IndexOutOfBoundsException because end subtracts start should
        // bigger than text's length
        mCanvas.drawText("android", 0, 10, 10, 30, mPaint);
    }

    @Test
    public void testDrawTextTextAtPositionWithOffsets() {
        final String t1 = "android";
        mCanvas.drawText(t1, 0, 7, 10, 30, mPaint);

        final SpannedString t2 = new SpannedString(t1);
        mCanvas.drawText(t2, 0, 7, 10, 30, mPaint);

        final SpannableString t3 = new SpannableString(t2);
        mCanvas.drawText(t3, 0, 7, 10, 30, mPaint);

        final SpannableStringBuilder t4 = new SpannableStringBuilder(t1);
        mCanvas.drawText(t4, 0, 7, 10, 30, mPaint);

        final StringBuffer t5 = new StringBuffer(t1);
        mCanvas.drawText(t5, 0, 7, 10, 30, mPaint);
    }

    @Test
    public void testDrawTextRun() {
        final String text = "android";
        final Paint paint = new Paint();

        mCanvas.drawTextRun(text, 0, 0, 0, 0, 0.0f, 0.0f, false, paint);
        mCanvas.drawTextRun(text, 0, text.length(), 0, text.length(), 0.0f, 0.0f, false, paint);
        mCanvas.drawTextRun(text, text.length(), text.length(), text.length(), text.length(),
                0.0f, 0.0f, false, paint);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawTextRunNullCharArray() {
        // Should throw out NullPointerException because text is null
        mCanvas.drawTextRun((char[]) null, 0, 0, 0, 0, 0.0f, 0.0f, false, new Paint());
    }

    @Test(expected=NullPointerException.class)
    public void testDrawTextRunNullCharSequence() {
        // Should throw out NullPointerException because text is null
        mCanvas.drawTextRun((CharSequence) null, 0, 0, 0, 0, 0.0f, 0.0f, false, new Paint());
    }

    @Test(expected=NullPointerException.class)
    public void testDrawTextRunCharArrayNullPaint() {
        // Should throw out NullPointerException because paint is null
        mCanvas.drawTextRun("android".toCharArray(), 0, 0, 0, 0, 0.0f, 0.0f, false, null);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawTextRunCharSequenceNullPaint() {
        // Should throw out NullPointerException because paint is null
        mCanvas.drawTextRun("android", 0, 0, 0, 0, 0.0f, 0.0f, false, null);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunNegativeIndex() {
        final String text = "android";
        final Paint paint = new Paint();

        // Should throw out IndexOutOfBoundsException because index is less than 0
        mCanvas.drawTextRun(text.toCharArray(), -1, text.length(), 0, text.length(), 0.0f, 0.0f,
                false, new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunNegativeCount() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because count is less than 0
        mCanvas.drawTextRun(text.toCharArray(), 0, -1, 0, text.length(), 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunContestIndexTooLarge() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because contextIndex is bigger than index
        mCanvas.drawTextRun(text.toCharArray(), 0, text.length(), 1, text.length(), 0.0f, 0.0f,
                false, new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunContestIndexTooSmall() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because contextIndex + contextCount
        // is less than index + count
        mCanvas.drawTextRun(text, 0, text.length(), 0, text.length() - 1, 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunIndexTooLarge() {
        final String text = "android";
        final Paint paint = new Paint();

        // Should throw out IndexOutOfBoundsException because index + count is bigger than
        // text length
        mCanvas.drawTextRun(text.toCharArray(), 0, text.length() + 1, 0, text.length() + 1,
                0.0f, 0.0f, false, new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunNegativeContextStart() {
        final String text = "android";
        final Paint paint = new Paint();

        // Should throw out IndexOutOfBoundsException because contextStart is less than 0
        mCanvas.drawTextRun(text, 0, text.length(), -1, text.length(), 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunStartLessThanContextStart() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because start is less than contextStart
        mCanvas.drawTextRun(text, 0, text.length(), 1, text.length(), 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunEndLessThanStart() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because end is less than start
        mCanvas.drawTextRun(text, 1, 0, 0, text.length(), 0.0f, 0.0f, false, new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunContextEndLessThanEnd() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because contextEnd is less than end
        mCanvas.drawTextRun(text, 0, text.length(), 0, text.length() - 1, 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawTextRunContextEndLargerThanTextLength() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because contextEnd is bigger than
        // text length
        mCanvas.drawTextRun(text, 0, text.length(), 0, text.length() + 1, 0.0f, 0.0f, false,
                new Paint());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawPosTextWithIndexAndCountNegativeIndex() {
        final char[] text = {
                'a', 'n', 'd', 'r', 'o', 'i', 'd'
        };
        final float[] pos = new float[]{
                0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f, 5.0f, 6.0f, 6.0f,
                7.0f, 7.0f
        };

        // Should throw out IndexOutOfBoundsException because index is less than 0
        mCanvas.drawPosText(text, -1, 7, pos, mPaint);
    }


    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawPosTextWithIndexAndCountTextTooShort() {
        final char[] text = {
                'a', 'n', 'd', 'r', 'o', 'i', 'd'
        };
        final float[] pos = new float[]{
                0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f, 5.0f, 6.0f, 6.0f,
                7.0f, 7.0f
        };

        // Should throw out IndexOutOfBoundsException because sum of index and count is
        // bigger than text's length
        mCanvas.drawPosText(text, 1, 10, pos, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawPosTextWithIndexAndCountCountTooLarge() {
        final char[] text = {
                'a', 'n', 'd', 'r', 'o', 'i', 'd'
        };

        // Should throw out IndexOutOfBoundsException because 2 times of count is
        // bigger than pos' length
        mCanvas.drawPosText(text, 1, 10, new float[] {
                10.0f, 30.f
        }, mPaint);
    }

    @Test
    public void testDrawPosTextWithIndexAndCount() {
        final char[] text = {
                'a', 'n', 'd', 'r', 'o', 'i', 'd'
        };
        final float[] pos = new float[]{
                0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f, 5.0f, 6.0f, 6.0f,
                7.0f, 7.0f
        };

        // normal case
        mCanvas.drawPosText(text, 0, 7, pos, mPaint);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testDrawPosTextCountTooLarge() {
        final String text = "android";

        // Should throw out IndexOutOfBoundsException because 2 times of count is
        // bigger than pos' length
        mCanvas.drawPosText(text, new float[]{
                10.0f, 30.f
        }, mPaint);
    }

    @Test
    public void testDrawPosText() {
        final String text = "android";
        final float[] pos = new float[]{
                0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 3.0f, 4.0f, 4.0f, 5.0f, 5.0f, 6.0f, 6.0f,
                7.0f, 7.0f
        };
        // normal case
        mCanvas.drawPosText(text, pos, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawTextOnPathWithIndexAndCountNegativeIndex() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // Should throw out ArrayIndexOutOfBoundsException because index is smaller than 0
        mCanvas.drawTextOnPath(text, -1, 7, new Path(), 10.0f, 10.0f, mPaint);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testDrawTextOnPathWithIndexAndCountTextTooShort() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // Should throw out ArrayIndexOutOfBoundsException because sum of index and
        // count is bigger than text's length
        mCanvas.drawTextOnPath(text, 0, 10, new Path(), 10.0f, 10.0f, mPaint);
    }

    @Test
    public void testDrawTextOnPathWithIndexAndCount() {
        final char[] text = { 'a', 'n', 'd', 'r', 'o', 'i', 'd' };

        // normal case
        mCanvas.drawTextOnPath(text, 0, 7, new Path(), 10.0f, 10.0f, mPaint);
    }

    @Test
    public void testDrawTextOnPathtestDrawTextRunNegativeCount() {
        final Path path = new Path();

        // no character in text
        mCanvas.drawTextOnPath("", path, 10.0f, 10.0f, mPaint);

        // There are characters in text
        mCanvas.drawTextOnPath("android", path, 10.0f, 10.0f, mPaint);
    }

    @Test
    public void testDrawPicture1() {
        mCanvas.drawPicture(new Picture());
    }

    @Test
    public void testDrawPicture2() {
        final RectF dst = new RectF(0, 0, 10, 31);
        final Picture p = new Picture();

        // picture width or length not bigger than 0
        mCanvas.drawPicture(p, dst);

        p.beginRecording(10, 30);
        mCanvas.drawPicture(p, dst);
    }

    @Test
    public void testDrawPicture3() {
        final Rect dst = new Rect(0, 10, 30, 0);
        final Picture p = new Picture();

        // picture width or length not bigger than 0
        mCanvas.drawPicture(p, dst);

        p.beginRecording(10, 30);
        mCanvas.drawPicture(p, dst);
    }

    @Test
    public void testDensity() {
        // set Density
        mCanvas.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        assertEquals(DisplayMetrics.DENSITY_DEFAULT, mCanvas.getDensity());

        // set Density
        mCanvas.setDensity(DisplayMetrics.DENSITY_HIGH);
        assertEquals(DisplayMetrics.DENSITY_HIGH, mCanvas.getDensity());
    }

    @Test(expected = IllegalStateException.class)
    public void testDrawHwBitmapInSwCanvas() {
        Bitmap hwBitmap = mImmutableBitmap.copy(Config.HARDWARE, false);
        mCanvas.drawBitmap(hwBitmap, 0, 0, null);
    }

    @Test(expected = IllegalStateException.class)
    public void testHwBitmapShaderInSwCanvas1() {
        Bitmap hwBitmap = mImmutableBitmap.copy(Config.HARDWARE, false);
        BitmapShader bitmapShader = new BitmapShader(hwBitmap, Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);
        RadialGradient gradientShader = new RadialGradient(10, 10, 30, Color.BLACK, Color.CYAN,
                Shader.TileMode.REPEAT);
        Shader shader = new ComposeShader(gradientShader, bitmapShader, Mode.OVERLAY);
        Paint p = new Paint();
        p.setShader(shader);
        mCanvas.drawRect(0, 0, 10, 10, p);
    }

    @Test(expected = IllegalStateException.class)
    public void testHwBitmapShaderInSwCanvas2() {
        Bitmap hwBitmap = mImmutableBitmap.copy(Config.HARDWARE, false);
        BitmapShader bitmapShader = new BitmapShader(hwBitmap, Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);
        RadialGradient gradientShader = new RadialGradient(10, 10, 30, Color.BLACK, Color.CYAN,
                Shader.TileMode.REPEAT);
        Shader shader = new ComposeShader(bitmapShader, gradientShader, Mode.OVERLAY);
        Paint p = new Paint();
        p.setShader(shader);
        mCanvas.drawRect(0, 0, 10, 10, p);
    }

    private void preCompare() {
        final float[] values = new float[FLOAT_ARRAY_LEN];
        mCanvas.getMatrix().getValues(values);
        assertArrayEquals(new float[] {
                1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f
        }, values, 0.0f);
    }

    private RectF getDeviceClip() {
        final RectF clip = new RectF(mCanvas.getClipBounds());
        mCanvas.getMatrix().mapRect(clip);
        return clip;
    }

    // Loops through the passed flags, applying each in order with successive calls
    // to save, verifying the clip and matrix values when restoring.
    private void verifySaveFlagsSequence(int[] saveFlags) {
        final Vector<RectF> clips = new Vector<RectF>();
        final Vector<Matrix> matrices = new Vector<Matrix>();

        assertTrue(BITMAP_WIDTH > saveFlags.length);
        assertTrue(BITMAP_HEIGHT > saveFlags.length);

        for (int i = 0; i < saveFlags.length; ++i) {
            clips.add(getDeviceClip());
            matrices.add(mCanvas.getMatrix());
            mCanvas.save(saveFlags[i]);

            mCanvas.translate(1, 1);
            mCanvas.clipRect(0, 0, BITMAP_WIDTH - i - 1, BITMAP_HEIGHT - i - 1);

            if (i  > 0) {
                // We are mutating the state on each iteration.
                assertFalse(clips.elementAt(i).equals(clips.elementAt(i - 1)));
                assertFalse(matrices.elementAt(i).equals(matrices.elementAt(i - 1)));
            }
        }

        for (int i = saveFlags.length - 1; i >= 0; --i) {
            // If clip/matrix flags are not set, the associated state should be preserved.
            if ((saveFlags[i] & Canvas.CLIP_SAVE_FLAG) == 0) {
                clips.elementAt(i).set(getDeviceClip());
            }
            if ((saveFlags[i] & Canvas.MATRIX_SAVE_FLAG) == 0) {
                matrices.elementAt(i).set(mCanvas.getMatrix());
            }

            mCanvas.restore();
            assertEquals(clips.elementAt(i), getDeviceClip());
            assertEquals(matrices.elementAt(i), mCanvas.getMatrix());
        }
    }

    @Test
    public void testDrawBitmapColorBehavior() {
        try {
            // Create a wide gamut bitmap where the pixel value is slightly less than max red.
            Resources resources = InstrumentationRegistry.getTargetContext().getResources();
            InputStream in = resources.getAssets().open("almost-red-adobe.png");
            Bitmap bitmap = BitmapFactory.decodeStream(in);

            // Draw the bitmap to an sRGB canvas.
            Bitmap canvasBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(canvasBitmap);
            canvas.drawBitmap(bitmap, 0, 0, null);

            // Verify that the pixel is now max red.
            Assert.assertEquals(0xFFFF0000, canvasBitmap.getPixel(0, 0));
        } catch (IOException e) {
            Assert.fail();
        }
    }

    @Test
    public void testShadowLayer_paintColorPreserved() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        paint.setShadowLayer(5.0f, 10.0f, 10.0f, 0xFFFF0000);
        paint.setColor(0xFF0000FF);
        canvas.drawPaint(paint);

        // Since the shadow is in the background, the canvas should be blue.
        ColorUtils.verifyColor(0xFF0000FF, bitmap.getPixel(50, 50));
    }
}
