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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout;
import android.text.TextPaint;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LayoutTest {

    @Test
    public void testGetDesiredWidthRange() {
        CharSequence textShort = "test";
        CharSequence textLonger = "test\ngetDesiredWidth";
        CharSequence textLongest = "test getDesiredWidth";
        TextPaint paint = new TextPaint();
        float widthShort = Layout.getDesiredWidth(textShort, 0, textShort.length(), paint);
        float widthLonger = Layout.getDesiredWidth(textLonger, 0, textLonger.length(), paint);
        float widthLongest = Layout.getDesiredWidth(textLongest, 0, textLongest.length(), paint);
        float widthPartShort = Layout.getDesiredWidth(textShort, 2, textShort.length(), paint);
        float widthZero = Layout.getDesiredWidth(textLonger, 5, textShort.length() - 3, paint);
        assertTrue(widthLonger > widthShort);
        assertTrue(widthLongest > widthLonger);
        assertEquals(0f, widthZero, 0.0f);
        assertTrue(widthShort > widthPartShort);
    }

    @Test
    public void testGetDesiredWidth() {
        CharSequence textShort = "test";
        CharSequence textLonger = "test\ngetDesiredWidth";
        CharSequence textLongest = "test getDesiredWidth";
        TextPaint paint = new TextPaint();
        float widthShort = Layout.getDesiredWidth(textShort, paint);
        float widthLonger = Layout.getDesiredWidth(textLonger, paint);
        float widthLongest = Layout.getDesiredWidth(textLongest, paint);
        assertTrue(widthLonger > widthShort);
        assertTrue(widthLongest > widthLonger);
    }
}
