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
package android.uirendering.cts.testclasses;

import static org.junit.Assume.assumeNotNull;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AutofillHighlightTests extends ActivityTestBase {
    @Test
    public void testHighlightedFrameLayout() {
        Bitmap goldenBitmap = Bitmap.createBitmap(ActivityTestBase.TEST_WIDTH,
                ActivityTestBase.TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        goldenBitmap.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(goldenBitmap);

        TypedArray a = getActivity().getTheme().obtainStyledAttributes(
                new int[]{android.R.attr.autofilledHighlight});
        int attributeResourceId = a.getResourceId(0, 0);
        Drawable autofilledDrawable = getActivity().getDrawable(attributeResourceId);
        a.recycle();

        // Test does not make sense if autofill highlight is not set
        assumeNotNull(autofilledDrawable);

        autofilledDrawable.setBounds(0, 0, ActivityTestBase.TEST_WIDTH,
                ActivityTestBase.TEST_HEIGHT);
        autofilledDrawable.draw(canvas);

        createTest().addLayout(R.layout.simple_white_layout, view -> view.setAutofilled(true))
                .runWithVerifier(new GoldenImageVerifier(goldenBitmap, new MSSIMComparer(0.99)));
    }
}

