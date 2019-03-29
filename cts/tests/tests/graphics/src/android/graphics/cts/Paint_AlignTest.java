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
import android.graphics.Paint.Align;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Paint_AlignTest {
    @Test
    public void testValueOf() {
        assertEquals(Align.LEFT, Align.valueOf("LEFT"));
        assertEquals(Align.CENTER, Align.valueOf("CENTER"));
        assertEquals(Align.RIGHT, Align.valueOf("RIGHT"));
    }

    @Test
    public void testValues() {
        // set the actual value
        Align[] actual = Align.values();

        assertEquals(3, actual.length);
        assertEquals(Align.LEFT, actual[0]);
        assertEquals(Align.CENTER, actual[1]);
        assertEquals(Align.RIGHT, actual[2]);

        // Here we use Align as the param of setTextAlign
        // and get the setting result by getTextalign
        Paint p = new Paint();
        p.setTextAlign(actual[0]);
        assertEquals(Align.LEFT, p.getTextAlign());
        p.setTextAlign(actual[1]);
        assertEquals(Align.CENTER, p.getTextAlign());
        p.setTextAlign(actual[2]);
        assertEquals(Align.RIGHT, p.getTextAlign());
    }
}
