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

package android.uirendering.cts.testclasses;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PictureTest extends ActivityTestBase {

    private static final Rect sRect = new Rect(0, 0, 40, 40);
    private static final Rect sOffsetRect = new Rect(40, 0, 80, 40);

    private static Picture greenSquare() {
        Paint pt = new Paint();
        pt.setColor(Color.GREEN);
        Picture pic = new Picture();
        Canvas subcanvas = pic.beginRecording(
                ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        subcanvas.drawRect(sRect, pt);
        pic.endRecording();

        return pic;
    }

    @Test
    public void testPictureRespectsClip() throws Exception {
        createTest()
            .addCanvasClient(
                    (canvas, width, height) -> {
                        Picture pic = greenSquare();
                        canvas.clipRect(sOffsetRect);
                        pic.draw(canvas);  // should be clipped out
                    }
            ).runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    @Test
    public void testPictureRespectsTranslate() throws Exception {
        createTest()
            .addCanvasClient(
                    (canvas, width, height) -> {
                        Picture pic = greenSquare();
                        canvas.translate(40, 0);
                        pic.draw(canvas);  // should be offset
                    }
            ).runWithVerifier(
                new RectVerifier(Color.WHITE, Color.GREEN, sOffsetRect));
    }
}

