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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils.SimpleStringSplitter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;

/**
 * Test {@link SimpleStringSplitter}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextUtils_SimpleStringSplitterTest {
    @Test
    public void testConstructor() {
        new SimpleStringSplitter('|');

        new SimpleStringSplitter(Character.MAX_VALUE);

        new SimpleStringSplitter(Character.MIN_VALUE);
    }

    @Test
    public void testHasNext() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter('|');
        assertFalse(simpleStringSplitter.hasNext());

        simpleStringSplitter.setString("first|second");
        assertTrue(simpleStringSplitter.hasNext());

        simpleStringSplitter.next();
        assertTrue(simpleStringSplitter.hasNext());

        simpleStringSplitter.next();
        assertFalse(simpleStringSplitter.hasNext());

        simpleStringSplitter.setString("");
        assertFalse(simpleStringSplitter.hasNext());
    }

    @Test
    public void testIterator() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter('|');

        Iterator<String> iterator = simpleStringSplitter.iterator();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext());

        simpleStringSplitter.setString("hello|world");
        iterator = simpleStringSplitter.iterator();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertEquals("hello", iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals("world", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testNext1() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter(',');

        simpleStringSplitter.setString("first, second");
        assertEquals("first", simpleStringSplitter.next());
        assertEquals(" second", simpleStringSplitter.next());
        try {
            simpleStringSplitter.next();
            fail("Should throw StringIndexOutOfBoundsException!");
        } catch (StringIndexOutOfBoundsException e) {
        }
    }

    @Test
    public void testNext2() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter(',');

        simpleStringSplitter.setString(" ,");
        assertEquals(" ", simpleStringSplitter.next());
        // unexpected empty string
        assertEquals("", simpleStringSplitter.next());

        simpleStringSplitter.setString(",,,");
        assertEquals("", simpleStringSplitter.next());
        assertEquals("", simpleStringSplitter.next());
        assertEquals("", simpleStringSplitter.next());
        // unexpected empty string
        assertEquals("", simpleStringSplitter.next());
    }

    @Test
    public void testRemove() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter(',');

        try {
            simpleStringSplitter.remove();
            fail("Should throw UnsupportedOperationException!");
        } catch (UnsupportedOperationException e) {
        }
    }

    @Test
    public void testSetString() {
        SimpleStringSplitter simpleStringSplitter = new SimpleStringSplitter(',');

        assertFalse(simpleStringSplitter.hasNext());
        simpleStringSplitter.setString("text1");
        assertTrue(simpleStringSplitter.hasNext());
        assertEquals("text1", simpleStringSplitter.next());
        assertFalse(simpleStringSplitter.hasNext());

        simpleStringSplitter.setString("text2");
        assertTrue(simpleStringSplitter.hasNext());
        assertEquals("text2", simpleStringSplitter.next());

        try {
            simpleStringSplitter.setString(null);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }
}
