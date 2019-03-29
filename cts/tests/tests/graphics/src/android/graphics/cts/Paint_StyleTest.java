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

import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Paint_StyleTest {
    @Test
    public void testValueOf() {
        assertEquals(Style.FILL, Style.valueOf("FILL"));
        assertEquals(Style.STROKE, Style.valueOf("STROKE"));
        assertEquals(Style.FILL_AND_STROKE, Style.valueOf("FILL_AND_STROKE"));
    }

    @Test
    public void testValues() {
        // set the actual value
        Style[] actual = Style.values();

        assertEquals(3, actual.length);
        assertEquals(Style.FILL, actual[0]);
        assertEquals(Style.STROKE, actual[1]);
        assertEquals(Style.FILL_AND_STROKE, actual[2]);

        // Here we use Style as the param of setStyle
        // and get the setting result by getStyle
        Paint p = new Paint();
        p.setStyle(actual[0]);
        assertEquals(Style.FILL, p.getStyle());
        p.setStyle(actual[1]);
        assertEquals(Style.STROKE, p.getStyle());
        p.setStyle(actual[2]);
        assertEquals(Style.FILL_AND_STROKE, p.getStyle());
    }
}
