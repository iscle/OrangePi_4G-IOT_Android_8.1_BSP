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

package com.android.car.obd2.test;

import static org.junit.Assert.*;

import com.android.car.obd2.IntegerArrayStream;
import org.junit.Test;

/** Tests for IntegerArrayStream */
public class IntegerArrayStreamTest {
    private static int[] DATA_SET = new int[] {1, 2, 3, 4, 5, 6};

    @Test
    public void testPeekConsume() {
        IntegerArrayStream stream = new IntegerArrayStream(DATA_SET);
        assertEquals(1, stream.peek());
        assertEquals(1, stream.consume());
        assertEquals(2, stream.peek());
        assertEquals(2, stream.consume());
    }

    @Test
    public void testResidualLength() {
        IntegerArrayStream stream = new IntegerArrayStream(DATA_SET);
        assertEquals(DATA_SET.length, stream.residualLength());
        stream.consume();
        assertEquals(DATA_SET.length - 1, stream.residualLength());
    }

    @Test
    public void testHasAtLeast() {
        IntegerArrayStream stream = new IntegerArrayStream(DATA_SET);
        assertTrue(stream.hasAtLeast(1));
        assertTrue(stream.hasAtLeast(DATA_SET.length));
        assertFalse(stream.hasAtLeast(100));
        assertTrue(stream.hasAtLeast(1, theStream -> true));
        assertFalse(stream.hasAtLeast(100, theStream -> true, theStream -> false));
    }

    @Test
    public void testExpect() {
        IntegerArrayStream stream = new IntegerArrayStream(DATA_SET);
        assertTrue(stream.expect(1, 2, 3));
        assertFalse(stream.expect(4, 6));
        assertEquals(5, stream.peek());
    }

    @Test
    public void testIsEmpty() {
        IntegerArrayStream stream = new IntegerArrayStream(DATA_SET);
        assertFalse(stream.isEmpty());
        stream.expect(1, 2, 3, 4, 5);
        assertFalse(stream.isEmpty());
        stream.consume();
        assertTrue(stream.isEmpty());
    }
}
