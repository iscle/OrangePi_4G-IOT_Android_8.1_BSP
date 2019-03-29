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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CustomDrawableTest {
    @Test
    public void testInflation() {
        Drawable dr = InstrumentationRegistry.getTargetContext().getDrawable(
                R.drawable.custom_drawable);
        assertTrue(dr instanceof CustomDrawable);
        assertEquals(Color.RED, ((CustomDrawable) dr).getColor());
    }

    public static class CustomDrawable extends Drawable {
        private static final int[] ATTRS = new int[] { android.R.attr.color };

        private int mColor;

        @Override
        public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
                throws XmlPullParserException, IOException {
            super.inflate(r, parser, attrs, theme);

            final TypedArray ta;
            if (theme != null) {
                ta = theme.obtainStyledAttributes(attrs, ATTRS, 0, 0);
            } else {
                ta = r.obtainAttributes(attrs, ATTRS);
            }

            mColor = ta.getColor(0, Color.BLACK);
        }

        public int getColor() {
            return mColor;
        }

        @Override
        public void draw(Canvas canvas) {

        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
