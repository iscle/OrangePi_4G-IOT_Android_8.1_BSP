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

import android.graphics.Region.Op;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Region_OpTest {
    @Test
    public void testValueOf() {
        assertEquals(Op.DIFFERENCE, Op.valueOf("DIFFERENCE"));
        assertEquals(Op.INTERSECT, Op.valueOf("INTERSECT"));
        assertEquals(Op.UNION, Op.valueOf("UNION"));
        assertEquals(Op.XOR, Op.valueOf("XOR"));
        assertEquals(Op.REVERSE_DIFFERENCE, Op.valueOf("REVERSE_DIFFERENCE"));
        assertEquals(Op.REPLACE, Op.valueOf("REPLACE"));
        // Every Op element has been tested in testOp1(), testOp2(), testOp3(),
        // testOp4() and testOp5() in the {@link RegionTest}.
        // Every Op element indicates one operation type.
    }

    @Test
    public void testValues() {
        // set the expected value
        Op[] expected = {
                Op.DIFFERENCE,
                Op.INTERSECT,
                Op.UNION,
                Op.XOR,
                Op.REVERSE_DIFFERENCE,
                Op.REPLACE };
        Op[] actual = Op.values();
        assertEquals(expected.length, actual.length);
        assertEquals(expected[0], actual[0]);
        assertEquals(expected[1], actual[1]);
        assertEquals(expected[2], actual[2]);
        assertEquals(expected[3], actual[3]);
        assertEquals(expected[4], actual[4]);
        assertEquals(expected[5], actual[5]);
    }
}
