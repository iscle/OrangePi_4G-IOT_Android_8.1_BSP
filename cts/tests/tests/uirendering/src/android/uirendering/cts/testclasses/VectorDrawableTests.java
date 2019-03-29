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
import android.graphics.Rect;
import android.graphics.drawable.VectorDrawable;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class VectorDrawableTests extends ActivityTestBase {
    @Test
    public void testScaleDown() {
        VectorDrawable vd = (VectorDrawable) getActivity().getResources().getDrawable(
                R.drawable.rectangle, null);
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.scale(0.5f, 0.5f);
                    vd.setBounds(new Rect(0, 0, 50, 50));
                    vd.draw(canvas);
                })
                .runWithVerifier(
                        new RectVerifier(Color.WHITE, Color.RED, new Rect(0, 0, 25, 25)));
    }
}

