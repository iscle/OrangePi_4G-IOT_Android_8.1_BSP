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
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PathTest {

    // Test constants
    private static final float LEFT = 10.0f;
    private static final float RIGHT = 50.0f;
    private static final float TOP = 10.0f;
    private static final float BOTTOM = 50.0f;
    private static final float XCOORD = 40.0f;
    private static final float YCOORD = 40.0f;

    @Test
    public void testConstructor() {
        // new the Path instance
        new Path();

        // another the Path instance with different params
        new Path(new Path());
    }

    @Test
    public void testAddRect1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(rect, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRect2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.addRect(LEFT, TOP, RIGHT, BOTTOM, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testMoveTo() {
        Path path = new Path();
        path.moveTo(10.0f, 10.0f);
    }

    @Test
    public void testSet() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path path1 = new Path();
        addRectToPath(path1);
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testSetCleanOld() {
        Path path = new Path();
        addRectToPath(path);
        path.addRect(new RectF(0, 0, 10, 10), Path.Direction.CW);
        Path path1 = new Path();
        path1.addRect(new RectF(10, 10, 20, 20), Path.Direction.CW);
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testSetEmptyPath() {
        Path path = new Path();
        addRectToPath(path);
        Path path1 = new Path();
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testAccessFillType() {
        // set the expected value
        Path.FillType expected1 = Path.FillType.EVEN_ODD;
        Path.FillType expected2 = Path.FillType.INVERSE_EVEN_ODD;
        Path.FillType expected3 = Path.FillType.INVERSE_WINDING;
        Path.FillType expected4 = Path.FillType.WINDING;

        // new the Path instance
        Path path = new Path();
        // set FillType by {@link Path#setFillType(FillType)}
        path.setFillType(Path.FillType.EVEN_ODD);
        assertEquals(expected1, path.getFillType());
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        assertEquals(expected2, path.getFillType());
        path.setFillType(Path.FillType.INVERSE_WINDING);
        assertEquals(expected3, path.getFillType());
        path.setFillType(Path.FillType.WINDING);
        assertEquals(expected4, path.getFillType());
    }

    @Test
    public void testRQuadTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rQuadTo(5.0f, 5.0f, 10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testTransform1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path dst = new Path();
        addRectToPath(path);
        path.transform(new Matrix(), dst);
        assertFalse(dst.isEmpty());
    }

    @Test
    public void testLineTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.lineTo(XCOORD, YCOORD);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testClose() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        path.close();
    }

    @Test
    public void testQuadTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.quadTo(20.0f, 20.0f, 40.0f, 40.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddCircle() {
        // new the Path instance
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.addCircle(XCOORD, YCOORD, 10.0f, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testArcTo1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.arcTo(oval, 0.0f, 30.0f, true);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testArcTo2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.arcTo(oval, 0.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testComputeBounds1() {
        RectF expected = new RectF(0.0f, 0.0f, 0.0f, 0.0f);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds, false);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testComputeBounds2() {
        RectF expected = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(bounds, Path.Direction.CW);
        path.computeBounds(bounds, true);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds, false);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testSetLastPoint() {
        Path path = new Path();
        path.setLastPoint(10.0f, 10.0f);
    }

    @Test
    public void testRLineTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rLineTo(10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsEmpty() {

        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testRewind() {
        Path.FillType expected = Path.FillType.EVEN_ODD;

        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        path.rewind();
        path.setFillType(Path.FillType.EVEN_ODD);
        assertTrue(path.isEmpty());
        assertEquals(expected, path.getFillType());
    }

    @Test
    public void testAddOval() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addOval(oval, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsRect() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
    }

    @Test
    public void testAddPath1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        path.addPath(src, 10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddPath2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        path.addPath(src);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddPath3() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        Matrix matrix = new Matrix();
        path.addPath(src, matrix);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRoundRect1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRoundRect(rect, XCOORD, YCOORD, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRoundRect2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) {
            radii[i] = 10.0f + i * 5.0f;
        }
        path.addRoundRect(rect, radii, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsConvex1() {
        Path path = new Path();
        path.addRect(0, 0, 100, 10, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(0, 0, 10, 100, Path.Direction.CW);
        assertFalse(path.isConvex()); // path is concave
    }

    @Test
    public void testIsConvex2() {
        Path path = new Path();
        path.addRect(0, 0, 40, 40, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(10, 10, 30, 30, Path.Direction.CCW);
        assertFalse(path.isConvex()); // path has hole, isn't convex
    }

    @Test
    public void testIsConvex3() {
        Path path = new Path();
        path.addRect(0, 0, 10, 10, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(0, 20, 10, 10, Path.Direction.CW);
        assertFalse(path.isConvex()); // path isn't one convex shape
    }

    @Test
    public void testIsInverseFillType() {
        Path path = new Path();
        assertFalse(path.isInverseFillType());
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        assertTrue(path.isInverseFillType());
    }

    @Test
    public void testOffset1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        Path dst = new Path();
        path.offset(XCOORD, YCOORD, dst);
        assertFalse(dst.isEmpty());
    }

    @Test
    public void testCubicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.cubicTo(10.0f, 10.0f, 20.0f, 20.0f, 30.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testReset() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path path1 = new Path();
        addRectToPath(path1);
        path.set(path1);
        assertFalse(path.isEmpty());
        path.reset();
        assertTrue(path.isEmpty());
    }

    @Test
    public void testToggleInverseFillType() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.toggleInverseFillType();
        assertTrue(path.isInverseFillType());
    }

    @Test
    public void testAddArc() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addArc(oval, 0.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testRCubicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rCubicTo(10.0f, 10.0f, 11.0f, 11.0f, 12.0f, 12.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testOffsetTextPath() {
        Paint paint = new Paint();
        Path path = new Path();
        paint.setTextSize(20);
        String text = "abc";
        paint.getTextPath(text, 0, text.length() - 1, 0, 0, path);
        RectF expectedRect = new RectF();
        path.computeBounds(expectedRect, false);
        assertFalse(expectedRect.isEmpty());
        int offset = 10;
        expectedRect.offset(offset, offset);

        path.offset(offset, offset);
        RectF offsettedRect = new RectF();
        path.computeBounds(offsettedRect, false);
        assertEquals(expectedRect, offsettedRect);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testApproximate_lowError() {
        new Path().approximate(-0.1f);
    }

    @Test
    public void testApproximate_rect_cw() {
        Path path = new Path();
        path.addRect(0, 0, 100, 100, Path.Direction.CW);
        assertArrayEquals(new float[] {
                0, 0, 0,
                0.25f, 100, 0,
                0.50f, 100, 100,
                0.75f, 0, 100,
                1, 0, 0,
        }, path.approximate(1f), 0);
    }

    @Test
    public void testApproximate_rect_ccw() {
        Path path = new Path();
        path.addRect(0, 0, 100, 100, Path.Direction.CCW);
        assertArrayEquals(new float[] {
                0, 0, 0,
                0.25f, 0, 100,
                0.50f, 100, 100,
                0.75f, 100, 0,
                1, 0, 0,
        }, path.approximate(1f), 0);
    }

    @Test
    public void testApproximate_empty() {
        Path path = new Path();
        assertArrayEquals(new float[] {
                0, 0, 0,
                1, 0, 0,
        }, path.approximate(0.5f), 0);
    }

    @Test
    public void testApproximate_circle() {
        Path path = new Path();
        path.addCircle(0, 0, 50, Path.Direction.CW);
        assertTrue(path.approximate(0.25f).length > 20);
    }

    private static void verifyPathsAreEquivalent(Path actual, Path expected) {
        Bitmap actualBitmap = drawAndGetBitmap(actual);
        Bitmap expectedBitmap = drawAndGetBitmap(expected);
        assertTrue(actualBitmap.sameAs(expectedBitmap));
    }

    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;

    private static Bitmap drawAndGetBitmap(Path path) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawPath(path, paint);
        return bitmap;
    }

    private void addRectToPath(Path path) {
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(rect, Path.Direction.CW);
    }
}
