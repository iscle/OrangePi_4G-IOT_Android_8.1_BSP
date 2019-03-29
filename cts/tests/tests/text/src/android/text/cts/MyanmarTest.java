/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.text.cts;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MyanmarTest {
    /**
     * Tests Unicode composition semantics.
     */
    @UiThreadTest
    @Test
    public void testCompositionSemantics() {
        Context context = InstrumentationRegistry.getTargetContext();
        String textA = "\u1019\u102d\u102f";
        String textB = "\u1019\u102f\u102d"; // wrong order for Unicode

        CaptureTextView cviewA = new CaptureTextView(context);
        Bitmap bitmapA = cviewA.capture(textA);
        CaptureTextView cviewB = new CaptureTextView(context);
        Bitmap bitmapB = cviewB.capture(textB);
        if (bitmapA.sameAs(bitmapB)) {
            // if textA and textB render identically, test against replacement characters
            String textC = "\ufffd\ufffd\ufffd"; // replacement characters are acceptable
            CaptureTextView cviewC = new CaptureTextView(context);
            Bitmap bitmapC = cviewC.capture(textC);
            if (!bitmapA.sameAs(bitmapC)) {
                // ...or against blank/empty glyphs
                Bitmap bitmapD = Bitmap.createBitmap(bitmapC.getWidth(), bitmapC.getHeight(),
                        bitmapC.getConfig());
                assertTrue(bitmapA.sameAs(bitmapD));
            }
        }
    }

    private static class CaptureTextView extends TextView {

        CaptureTextView(Context context) {
            super(context);
        }

        Bitmap capture(String text) {
            setText(text);

            invalidate();

            setDrawingCacheEnabled(true);
            measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            layout(0, 0, 200,200);

            Bitmap bitmap = Bitmap.createBitmap(getDrawingCache());
            setDrawingCacheEnabled(false);
            return bitmap;
        }

    }
}
