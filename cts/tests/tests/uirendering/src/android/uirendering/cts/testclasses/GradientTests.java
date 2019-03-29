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
 * limitations under the License.
 */

package android.uirendering.cts.testclasses;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class GradientTests extends ActivityTestBase {
    @Test
    public void testAlphaPreMultiplication() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();

                    // Add a red background to cover the activity's
                    paint.setColor(Color.RED);
                    canvas.drawRect(0.0f, 0.0f, width, height, paint);

                    paint.setColor(Color.WHITE);
                    paint.setShader(new LinearGradient(
                            0.0f, 0.0f, 0.0f, 40.0f,
                            0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP)
                    );
                    canvas.drawRect(0.0f, 0.0f, width, height, paint);
                }, true)
                .runWithVerifier(new SamplePointVerifier(new Point[] {
                        new Point(0, 0), new Point(0, 39)
                }, new int[] {
                        // Opaque white on red, result is white
                        0xffffffff,
                        // Transparent white on red, result is red
                        // This means the source color (0x00ffffff) was
                        // properly pre-multiplied
                        0xffff0000
                }, 20)); // Tolerance set to account for dithering and interpolation
    }
}
