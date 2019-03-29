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

import static org.junit.Assert.assertNotNull;

import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Paint_FontMetricsIntTest {
    @Test
    public void testConstructor() {
        new Paint.FontMetricsInt();
    }

    @Test
    public void testToString() {
        // set the expected value
        int top = 1;
        int ascent = 2;
        int descent = 3;
        int bottom = 4;
        int leading = 5;

        FontMetricsInt fontMetricsInt = new FontMetricsInt();
        fontMetricsInt.top = top;
        fontMetricsInt.ascent = ascent;
        fontMetricsInt.descent = descent;
        fontMetricsInt.bottom = bottom;
        fontMetricsInt.leading = leading;

        assertNotNull(fontMetricsInt.toString());
    }
}
