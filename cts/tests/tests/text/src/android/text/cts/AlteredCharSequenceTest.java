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
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.AlteredCharSequence;
import android.text.Spanned;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AlteredCharSequenceTest {
    private static final String SOURCE_STR = "This is a char sequence.";

    private AlteredCharSequence mAlteredCharSequence;

    @Test
    public void testCharAt() {
        mAlteredCharSequence = AlteredCharSequence.make("abcdefgh", new char[] {'i', 's'}, 0, 2);
        // chars in sub.
        assertEquals('i', mAlteredCharSequence.charAt(0));
        assertEquals('s', mAlteredCharSequence.charAt(1));
        // chars in source.
        assertEquals('c', mAlteredCharSequence.charAt(2));
        assertEquals('d', mAlteredCharSequence.charAt(3));
    }

    @Test(expected=StringIndexOutOfBoundsException.class)
    public void testCharAtTooLow() {
        mAlteredCharSequence = AlteredCharSequence.make("abcdefgh", new char[] {'i', 's'}, 0, 2);

        mAlteredCharSequence.charAt(-1);
    }

    @Test(expected=StringIndexOutOfBoundsException.class)
    public void testCharAtTooHigh() {
        mAlteredCharSequence = AlteredCharSequence.make("abcdefgh", new char[] {'i', 's'}, 0, 2);

        mAlteredCharSequence.charAt(mAlteredCharSequence.length() + 1);
    }

    @Test
    public void testGetChars() {
        char[] sub = { 'i', 's' };
        int start = 0;
        int end = 2;
        int off = 1;

        mAlteredCharSequence = AlteredCharSequence.make(SOURCE_STR, sub, 0, sub.length);
        char[] dest = new char[4];
        mAlteredCharSequence.getChars(start, end, dest, off);

        char[] expected = { 0, 'T', 'h', 0 };
        for (int i = off; i < end - start + off; i++) {
            assertEquals(expected[i], dest[i]);
        }
        end = 0;
        for (int i = 0; i < 4; i++) {
            dest[i] = 'a';
        }
        mAlteredCharSequence.getChars(start, end, dest, off);
        for (int i = off; i < end - start + off; i++) {
            assertEquals('a', dest[i]);
        }
        start = end + 1;
        try {
            mAlteredCharSequence.getChars(start, end, dest, off);
            fail("should raise a StringIndexOutOfBoundsException.");
        } catch (StringIndexOutOfBoundsException e) {
            // expected.
        }
    }

    @Test
    public void testLength() {
        char[] sub = { 'i', 's' };

        CharSequence source = SOURCE_STR;
        for (int i = 1; i < 10; i++) {
            source = source + "a";
            mAlteredCharSequence = AlteredCharSequence.make(source, sub, 0, sub.length);
            assertEquals(source.length(), mAlteredCharSequence.length());
        }
    }

    @Test
    public void testMake() {
        char[] sub = { 'i', 's' };

        CharSequence source = SOURCE_STR;
        mAlteredCharSequence = AlteredCharSequence.make(source, sub, 0, sub.length);
        assertNotNull(mAlteredCharSequence);
        assertEquals(source.toString(), mAlteredCharSequence.toString());
        String acsClassName = mAlteredCharSequence.getClass().getName();

        MockSpanned spanned = new MockSpanned("This is a spanned.");
        mAlteredCharSequence = AlteredCharSequence.make(spanned, sub, 0, sub.length);
        assertNotNull(mAlteredCharSequence);
        assertEquals(0, mAlteredCharSequence.length());
        String spanClassName = mAlteredCharSequence.getClass().getName();
        assertFalse(0 == acsClassName.compareTo(spanClassName));
    }

    @Test
    public void testSubSequence() {
        char[] sub = { 'i', 's' };

        CharSequence source = SOURCE_STR;
        mAlteredCharSequence = AlteredCharSequence.make(source, sub, 0, sub.length);
        assertEquals("Th", mAlteredCharSequence.subSequence(0, 2).toString());

        try {
            mAlteredCharSequence.subSequence(0, 100);
            fail("Should throw StringIndexOutOfBoundsException!");
        } catch (StringIndexOutOfBoundsException e) {
            // expected.
        }
    }

    @Test
    public void testToString() {
        char[] sub = { 'i', 's' };
        CharSequence source = SOURCE_STR;
        mAlteredCharSequence = AlteredCharSequence.make(source, sub, 0, sub.length);
        assertNotNull(mAlteredCharSequence.toString());
    }

    class MockSpanned implements Spanned {
        public MockSpanned(String sequence) {
        }

        public int getSpanEnd(Object tag) {
            return 0;
        }

        public int getSpanFlags(Object tag) {
            return 0;
        }

        public int getSpanStart(Object tag) {
            return 0;
        }

        public <T> T[] getSpans(int start, int end, Class<T> type) {
            return null;
        }

        public int nextSpanTransition(int start, int limit, Class type) {
            return 0;
        }

        public char charAt(int index) {
            return 0;
        }

        public int length() {
            return 0;
        }

        public CharSequence subSequence(int start, int end) {
            return null;
        }
    }
}

