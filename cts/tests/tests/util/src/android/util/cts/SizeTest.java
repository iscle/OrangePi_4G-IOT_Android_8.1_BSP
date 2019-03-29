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

package android.util.cts;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Size;
import android.util.SizeF;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SizeTest {
    @Test
    public void testConstructors() {
        Size size = new Size(100, 200);
        assertEquals(100, size.getWidth());
        assertEquals(200, size.getHeight());

        SizeF sizeF = new SizeF(100, 200);
        assertEquals(100, sizeF.getWidth(), 0f);
        assertEquals(200, sizeF.getHeight(), 0f);
    }

    @Test(expected=NumberFormatException.class)
    public void testParseSizeInvalid() {
        Size.parseSize("2by4");
    }

    @Test
    public void testParseSize() {
        assertEquals(new Size(100, 200), Size.parseSize("100*200"));
        assertEquals(new Size(10, 20), Size.parseSize("10x20"));
        assertEquals(new SizeF(9999, 9999), SizeF.parseSizeF("9999x9999"));
    }

    @Test(expected=NumberFormatException.class)
    public void testParseSizeFInvalid() {
        SizeF.parseSizeF("2by4");
    }

    @Test
    public void testParseSizeF() {
        assertEquals(new SizeF(100f, 200f), SizeF.parseSizeF("100*200"));
        assertEquals(new SizeF(10f, 20f), SizeF.parseSizeF("10x20"));
        assertEquals(new SizeF(1000000f, 2.4f), SizeF.parseSizeF("1e6x2.4"));
    }
}
