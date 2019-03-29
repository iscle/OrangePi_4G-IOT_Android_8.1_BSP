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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PictureTest {
    private static final int TEST_WIDTH = 4; // must be >= 2
    private static final int TEST_HEIGHT = 3; // must >= 2

    private final Rect mClipRect = new Rect(0, 0, 2, 2);

    // This method tests out some edge cases w.r.t. Picture creation.
    // In particular, this test verifies that, in the following situations,
    // the created picture (effectively) has balanced saves and restores:
    //   - copy constructed picture from actively recording picture
    //   - writeToStream/createFromStream created picture from actively recording picture
    //   - actively recording picture after draw call
    @Test
    public void testSaveRestoreBalance() {
        Picture original = new Picture();
        Canvas canvas = original.beginRecording(TEST_WIDTH, TEST_HEIGHT);
        assertNotNull(canvas);
        createImbalance(canvas);

        int expectedSaveCount = canvas.getSaveCount();

        Picture copy = new Picture(original);
        verifyBalance(copy);

        assertEquals(expectedSaveCount, canvas.getSaveCount());

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        original.writeToStream(bout);

        assertEquals(expectedSaveCount, canvas.getSaveCount());

        Picture serialized = Picture.createFromStream(new ByteArrayInputStream(bout.toByteArray()));
        // The serialization/deserialization process will balance the saves and restores
        verifyBalance(serialized);

        assertEquals(expectedSaveCount, canvas.getSaveCount());

        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas drawDest = new Canvas(bitmap);
        original.draw(drawDest);
        verifyBalance(original);
    }

    // Add an extra save with a transform and clip
    private void createImbalance(Canvas canvas) {
        canvas.save();
        canvas.clipRect(mClipRect, Op.REPLACE);
        canvas.translate(1.0f, 1.0f);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        canvas.drawRect(0, 0, 10, 10, paint);
    }

    private void verifyBalance(Picture picture) {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int beforeSaveCount = canvas.getSaveCount();

        final Matrix beforeMatrix = canvas.getMatrix();

        Rect beforeClip = new Rect();
        assertTrue(canvas.getClipBounds(beforeClip));

        canvas.drawPicture(picture);

        assertEquals(beforeSaveCount, canvas.getSaveCount());

        assertTrue(beforeMatrix.equals(canvas.getMatrix()));

        Rect afterClip = new Rect();

        assertTrue(canvas.getClipBounds(afterClip));
        assertEquals(beforeClip, afterClip);
    }

    @Test
    public void testPicture() {
        Picture picture = new Picture();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        Canvas canvas = picture.beginRecording(TEST_WIDTH, TEST_HEIGHT);
        assertNotNull(canvas);
        drawPicture(canvas);
        picture.endRecording();

        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        picture.draw(canvas);
        verifySize(picture);
        verifyBitmap(bitmap);

        picture.writeToStream(bout);
        picture = Picture.createFromStream(new ByteArrayInputStream(bout.toByteArray()));

        // create a new Canvas with a new bitmap
        bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        picture.draw(canvas);
        verifySize(picture);
        verifyBitmap(bitmap);

        Picture pic = new Picture(picture);
        bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        pic.draw(canvas);
        verifySize(pic);
        verifyBitmap(bitmap);
    }

    private void verifySize(Picture picture) {
        assertEquals(TEST_WIDTH, picture.getWidth());
        assertEquals(TEST_HEIGHT, picture.getHeight());
    }

    private void drawPicture(Canvas canvas) {
        Paint paint = new Paint();
        // GREEN rectangle covering the entire canvas
        paint.setColor(Color.GREEN);
        paint.setStyle(Style.FILL);
        canvas.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, paint);
        // horizontal red line starting from (0,0); overwrites first line of the rectangle
        paint.setColor(Color.RED);
        canvas.drawLine(0, 0, TEST_WIDTH, 0, paint);
        // overwrite (0,0) with a blue dot
        paint.setColor(Color.BLUE);
        canvas.drawPoint(0, 0, paint);
    }

    private void verifyBitmap(Bitmap bitmap) {
        // first pixel is BLUE, rest of the line is RED
        assertEquals(Color.BLUE, bitmap.getPixel(0, 0));
        for (int x = 1; x < TEST_WIDTH; x++) {
            assertEquals(Color.RED, bitmap.getPixel(x, 0));
        }
        // remaining lines are all green
        for (int y = 1; y < TEST_HEIGHT; y++) {
            for (int x = 0; x < TEST_WIDTH; x++) {
                assertEquals(Color.GREEN, bitmap.getPixel(x, y));
            }
        }
    }
}
