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

package android.util.proto.cts;

import android.util.proto.ProtoOutputStream;
import android.util.proto.cts.nano.Test;

import com.google.protobuf.nano.MessageNano;
import junit.framework.TestCase;
import org.junit.Assert;

/**
 * Test the object methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamObjectTest extends TestCase {

    // ----------------------------------------------------------------------
    //  Tokens
    // ----------------------------------------------------------------------

    /**
     * Test making the tokens for startObject.
     */
    public void testMakeToken() throws Exception {
        assertEquals(0xe000000000000000L, ProtoOutputStream.makeToken(0xffffffff, false, 0, 0, 0));
        assertEquals(0x1000000000000000L, ProtoOutputStream.makeToken(0, true, 0, 0, 0));
        assertEquals(0x0ff8000000000000L, ProtoOutputStream.makeToken(0, false, 0xffffffff, 0, 0));
        assertEquals(0x0007ffff00000000L, ProtoOutputStream.makeToken(0, false, 0, 0xffffffff, 0));
        assertEquals(0x00000000ffffffffL, ProtoOutputStream.makeToken(0, false, 0, 0, 0xffffffff));
    }

    /**
     * Test decoding the tokens.
     */
    public void testDecodeToken() throws Exception {
        assertEquals(0x07, ProtoOutputStream.getTagSizeFromToken(0xffffffffffffffffL));
        assertEquals(0, ProtoOutputStream.getTagSizeFromToken(0x1fffffffffffffffL));

        assertEquals(true, ProtoOutputStream.getRepeatedFromToken(0xffffffffffffffffL));
        assertEquals(false, ProtoOutputStream.getRepeatedFromToken(0xefffffffffffffffL));

        assertEquals(0x01ff, ProtoOutputStream.getDepthFromToken(0xffffffffffffffffL));
        assertEquals(0, ProtoOutputStream.getDepthFromToken(0xf005ffffffffffffL));

        assertEquals(0x07ffff, ProtoOutputStream.getObjectIdFromToken(0xffffffffffffffffL));
        assertEquals(0, ProtoOutputStream.getObjectIdFromToken(0xfff80000ffffffffL));

        assertEquals(0xffffffff, ProtoOutputStream.getSizePosFromToken(0xffffffffffffffffL));
        assertEquals(0, ProtoOutputStream.getSizePosFromToken(0xffffffff00000000L));
    }

    /**
     * Test writing an object with one char in it.
     */
    public void testObjectOneChar() {
        testObjectOneChar(0);
        testObjectOneChar(1);
        testObjectOneChar(5);
    }

    /**
     * Implementation of testObjectOneChar for a given chunkSize.
     */
    public void testObjectOneChar(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        long token = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');
        po.endObject(token);

        Assert.assertArrayEquals(new byte[] {
                (byte)0x0a, (byte)0x02, (byte)0x10, (byte)0x62,
            }, po.getBytes());
    }

    /**
     * Test writing an object with one multibyte unicode char in it.
     */
    public void testObjectOneLargeChar() {
        testObjectOneLargeChar(0);
        testObjectOneLargeChar(1);
        testObjectOneLargeChar(5);
    }

    /**
     * Implementation of testObjectOneLargeChar for a given chunkSize.
     */
    public void testObjectOneLargeChar(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        long token = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(5000,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                '\u3110');
        po.endObject(token);

        Assert.assertArrayEquals(new byte[] {
                (byte)0x0a, (byte)0x05, (byte)0xc0, (byte)0xb8,
                (byte)0x02, (byte)0x90, (byte)0x62,
            }, po.getBytes());
    }

    /**
     * Test writing a char, then an object, then a char.
     */
    public void testObjectAndTwoChars() {
        testObjectAndTwoChars(0);
        testObjectAndTwoChars(1);
        testObjectAndTwoChars(5);
    }

    /**
     * Implementation of testObjectAndTwoChars for a given chunkSize.
     */
    public void testObjectAndTwoChars(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        po.writeUInt32(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token = po.startObject(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');
        po.endObject(token);

        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'c');

        Assert.assertArrayEquals(new byte[] {
                // 1 -> 'a'
                (byte)0x08, (byte)0x61,
                // begin object 1
                (byte)0x12, (byte)0x02,
                    // 3 -> 'b'
                    (byte)0x18, (byte)0x62,
                // 4 -> 'c'
                (byte)0x20, (byte)0x63,
            }, po.getBytes());
    }

    /**
     * Test writing an object with nothing in it.
     */
    public void testEmptyObject() {
        testEmptyObject(0);
        testEmptyObject(1);
        testEmptyObject(5);
    }

    /**
     * Implementation of testEmptyObject for a given chunkSize.
     * Nothing should be written.
     */
    public void testEmptyObject(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        long token = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(token);

        Assert.assertArrayEquals(new byte[0], po.getBytes());
    }

    /**
     * Test writing 3 levels deep of objects with nothing in them.
     */
    public void testDeepEmptyObjects() {
        testDeepEmptyObjects(0);
        testDeepEmptyObjects(1);
        testDeepEmptyObjects(5);
    }

    /**
     * Implementation of testDeepEmptyObjects for a given chunkSize.
     */
    public void testDeepEmptyObjects(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        long token2 = po.startObject(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        long token3 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(token3);
        po.endObject(token2);
        po.endObject(token1);

        Assert.assertArrayEquals(new byte[0], po.getBytes());
    }

    /**
     * Test writing a char, then an object with nothing in it, then a char.
     */
    public void testEmptyObjectAndTwoChars() {
        testEmptyObjectAndTwoChars(0);
        testEmptyObjectAndTwoChars(1);
        testEmptyObjectAndTwoChars(5);
    }

    /**
     * Implementation of testEmptyObjectAndTwoChars for a given chunkSize.
     */
    public void testEmptyObjectAndTwoChars(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        po.writeUInt32(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token = po.startObject(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(token);

        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'c');

        Assert.assertArrayEquals(new byte[] {
                // 1 -> 'a'
                (byte)0x08, (byte)0x61,
                // 4 -> 'c'
                (byte)0x20, (byte)0x63,
            }, po.getBytes());
    }

    /**
     * Test empty repeated objects.  For repeated objects, we write an empty header.
     */
    public void testEmptyRepeatedObject() {
        testEmptyRepeatedObject(0);
        testEmptyRepeatedObject(1);
        testEmptyRepeatedObject(5);
    }

    /**
     * Implementation of testEmptyRepeatedObject for a given chunkSize.
     */
    public void testEmptyRepeatedObject(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        long token;

        token = po.startRepeatedObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endRepeatedObject(token);

        token = po.startRepeatedObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endRepeatedObject(token);

        Assert.assertArrayEquals(new byte[] {
                // 1 -> empty (tag, size)
                (byte)0x0a, (byte)0x00,
                // 1 -> empty (tag, size)
                (byte)0x0a, (byte)0x00,
            }, po.getBytes());
    }


    /**
     * Test writing a char, then an object with an int and a string in it, then a char.
     */
    public void testComplexObject() {
        testComplexObject(0);
        testComplexObject(1);
        testComplexObject(5);
    }

    /**
     * Implementation of testComplexObject for a given chunkSize.
     */
    public void testComplexObject(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        po.writeUInt32(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'x');

        long token = po.startObject(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'y');
        po.writeString(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_STRING),
                "abcdefghijkl");

        long tokenEmpty = po.startObject(ProtoOutputStream.makeFieldId(500,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(tokenEmpty);

        po.endObject(token);

        po.writeUInt32(ProtoOutputStream.makeFieldId(5,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'z');

        Assert.assertArrayEquals(new byte[] {
                // 1 -> 'x'
                (byte)0x08, (byte)0x78,
                // begin object 1
                (byte)0x12, (byte)0x10,
                    // 3 -> 'y'
                    (byte)0x18, (byte)0x79,
                    // 4 -> "abcdefghijkl"
                    (byte)0x22, (byte)0x0c,
                        (byte)0x61, (byte)0x62, (byte)0x63, (byte)0x64, (byte)0x65, (byte)0x66,
                        (byte)0x67, (byte)0x68, (byte)0x69, (byte)0x6a, (byte)0x6b, (byte)0x6c,
                // 4 -> 'z'
                (byte)0x28, (byte)0x7a,
            }, po.getBytes());
    }

    /**
     * Test writing 3 levels deep of objects.
     */
    public void testDeepObjects() {
        testDeepObjects(0);
        testDeepObjects(1);
        testDeepObjects(5);
    }

    /**
     * Implementation of testDeepObjects for a given chunkSize.
     */
    public void testDeepObjects(int chunkSize) {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');

        long token3 = po.startObject(ProtoOutputStream.makeFieldId(5,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(6,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'c');

        po.endObject(token3);
        po.endObject(token2);
        po.endObject(token1);

        Assert.assertArrayEquals(new byte[] {
                // begin object 1
                (byte)0x0a, (byte)0x0a,
                    // 2 -> 'a'
                    (byte)0x10, (byte)0x61,
                    // begin object 3
                    (byte)0x1a, (byte)0x06,
                        // 4 -> 'b'
                        (byte)0x20, (byte)0x62,
                        // begin object 5
                        (byte)0x2a, (byte)0x02,
                            // 6 -> 'c'
                            (byte)0x30, (byte)0x63,
            }, po.getBytes());
    }

    /**
     * Test mismatched startObject / endObject calls: too many endObject
     * with objects that have data.
     */
    public void testTooManyEndObjectsWithData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');

        po.endObject(token2);
        try {
            po.endObject(token2);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: too many endObject
     * with empty objects
     */
    public void testTooManyEndObjectsWithoutData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        po.endObject(token2);
        try {
            po.endObject(token2);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Trailing startObject
     * with objects that have data.
     */
    public void testTrailingStartObjectWithData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');

        po.endObject(token2);
        try {
            po.getBytes();
            throw new Exception("getBytes didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Trailing startObject
     * with empty objects
     */
    public void testTrailingStartObjectWithoutData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        po.endObject(token2);
        try {
            po.getBytes();
            throw new Exception("getBytes didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Extra startObject in the middle.
     * with objects that have data.
     */
    public void testExtraStartObjectInMiddleWithData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');

        try {
            po.endObject(token1);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Extra startObject in the middle.
     * with empty objects
     */
    public void testExtraStartObjectInMiddleWithoutData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        try {
            po.endObject(token1);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Two deep with swapped endObject.
     * with objects that have data.
     */
    public void testSwappedEndObjectWithData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(2,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'a');

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');
        po.endObject(token2);

        long token3 = po.startObject(ProtoOutputStream.makeFieldId(5,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeUInt32(ProtoOutputStream.makeFieldId(4,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT32),
                'b');

        try {
            po.endObject(token2);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Two deep with swapped endObject.
     * with empty objects
     */
    public void testSwappedEndObjectWithoutData() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(token2);

        long token3 = po.startObject(ProtoOutputStream.makeFieldId(5,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        try {
            po.endObject(token2);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Good
        }
    }

    /**
     * Test mismatched startObject / endObject calls: Two deep with swapped endObject.
     * with empty objects
     */
    public void testEndObjectMismatchError() throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream();

        long token1 = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        long token2 = po.startObject(ProtoOutputStream.makeFieldId(3,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.endObject(token2);

        long token3 = po.startObject(ProtoOutputStream.makeFieldId(5,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        try {
            po.endObject(token2);
            throw new Exception("endObject didn't throw");
        } catch (RuntimeException ex) {
            // Check this, because it's really useful, and if we lose the message it'll be
            // harder to debug typos.
            assertEquals("Mismatched startObject/endObject calls. Current depth 2"
                    + " token=Token(val=0x2017fffd0000000a depth=2 object=2 tagSize=1 sizePos=10)"
                    + " expectedToken=Token(val=0x2017fffc0000000a depth=2 object=3 tagSize=1"
                    + " sizePos=10)", ex.getMessage());
        }
    }

    /**
     * Test compatibility of nested objects.
     */
    public void testNestedCompat() throws Exception {
        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.nestedField = new Test.Nested();
        all.nestedField.data = 1;
        all.nestedField.nested = new Test.Nested();
        all.nestedField.nested.data = 2;
        all.nestedField.nested.nested = new Test.Nested();
        all.nestedField.nested.nested.data = 3;

        final long token1 = po.startObject(ProtoOutputStream.makeFieldId(170,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                1);
        final long token2 = po.startObject(ProtoOutputStream.makeFieldId(10002,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                2);
        final long token3 = po.startObject(ProtoOutputStream.makeFieldId(10002,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
        po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                3);
        po.endObject(token3);
        po.endObject(token2);
        po.endObject(token1);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);
    }

    /**
     * Test compatibility of repeated nested objects.
     */
    public void testRepeatedNestedCompat() throws Exception {
        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        final int N = 3;
        all.nestedFieldRepeated = new Test.Nested[N];
        for (int i=0; i<N; i++) {
            all.nestedFieldRepeated[i] = new Test.Nested();
            all.nestedFieldRepeated[i].data = 1;
            all.nestedFieldRepeated[i].nested = new Test.Nested();
            all.nestedFieldRepeated[i].nested.data = 2;
            all.nestedFieldRepeated[i].nested.nested = new Test.Nested();
            all.nestedFieldRepeated[i].nested.nested.data = 3;

            final long token1 = po.startObject(ProtoOutputStream.makeFieldId(171,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
            po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                    1);
            final long token2 = po.startObject(ProtoOutputStream.makeFieldId(10002,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
            po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                    2);
            final long token3 = po.startObject(ProtoOutputStream.makeFieldId(10002,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));
            po.writeInt32(ProtoOutputStream.makeFieldId(10001,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                    3);
            po.endObject(token3);
            po.endObject(token2);
            po.endObject(token1);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);
    }

    /**
     * Test that if you pass in the wrong type of fieldId, it throws.
     */
    public void testBadFieldIds() {
        // Single

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.startObject(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE));
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.startObject(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_OBJECT));
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.startRepeatedObject(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE));
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.startRepeatedObject(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_OBJECT));
        } catch (IllegalArgumentException ex) {
            // good
        }
    }

    /**
     * Test that if endRepeatedObject is called with a token from startObject that it fails.
     */
    public void testMismatchedEndObject() {
        final ProtoOutputStream po = new ProtoOutputStream();
        final long token = po.startObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT));

        try {
            po.endRepeatedObject(token);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }

    /**
     * Test that if endRepeatedObject is called with a token from startObject that it fails.
     */
    public void testMismatchedEndRepeatedObject() {
        final ProtoOutputStream po = new ProtoOutputStream();
        final long token = po.startRepeatedObject(ProtoOutputStream.makeFieldId(1,
                    ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_OBJECT));

        try {
            po.endObject(token);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }

    /**
     * Test writeObject, which takes a pre-encoded and compacted protobuf object and writes it into
     * a field.
     */
    public void testWriteObject() {
        byte[] innerRaw = new byte[] {
            // varint 1 -> 42
            (byte)0x08,
            (byte)0xd0, (byte)0x02,
            // string 2 -> "ab"
            (byte)0x12,
            (byte)0x02,
            (byte)0x62, (byte)0x63,
            // object 3 -> ...
            (byte)0x1a,
            (byte)0x4,
                // varint 4 -> 0
                (byte)0x20,
                (byte)0x00,
                // varint 4 --> 1
                (byte)0x20,
                (byte)0x01,
        };

        final ProtoOutputStream po = new ProtoOutputStream();
        po.writeObject(ProtoOutputStream.makeFieldId(10,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT),
                innerRaw);

        final byte[] result = po.getBytes();
        final byte[] expected = new byte[2 + innerRaw.length];
        expected[0] = (byte)0x52;
        expected[1] = (byte)0x0d;
        System.arraycopy(innerRaw, 0, expected, 2, innerRaw.length);

        Assert.assertArrayEquals(expected, result);
    }

    /**
     * Test writeObject, which takes a pre-encoded and compacted protobuf object and writes it into
     * a field.
     */
    public void testWriteObjectEmpty() {
        byte[] innerRaw = new byte[0];

        final ProtoOutputStream po = new ProtoOutputStream();
        po.writeObject(ProtoOutputStream.makeFieldId(10,
                    ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_OBJECT),
                innerRaw);

        final byte[] result = po.getBytes();

        Assert.assertEquals(0, result.length);
    }

    /**
     * Test writeObject, which takes a pre-encoded and compacted protobuf object and writes it into
     * a field.
     */
    public void testWriteObjectRepeated() {
        byte[] innerRaw = new byte[] {
            // varint 1 -> 42
            (byte)0x08,
            (byte)0xd0, (byte)0x02,
            // string 2 -> "ab"
            (byte)0x12,
            (byte)0x02,
            (byte)0x62, (byte)0x63,
            // object 3 -> ...
            (byte)0x1a,
            (byte)0x4,
                // varint 4 -> 0
                (byte)0x20,
                (byte)0x00,
                // varint 4 --> 1
                (byte)0x20,
                (byte)0x01,
        };

        final ProtoOutputStream po = new ProtoOutputStream();
        po.writeRepeatedObject(ProtoOutputStream.makeFieldId(10,
                    ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_OBJECT),
                innerRaw);

        final byte[] result = po.getBytes();
        final byte[] expected = new byte[2 + innerRaw.length];
        expected[0] = (byte)0x52;
        expected[1] = (byte)0x0d;
        System.arraycopy(innerRaw, 0, expected, 2, innerRaw.length);

        Assert.assertArrayEquals(expected, result);
    }

    /**
     * Test writeObject, which takes a pre-encoded and compacted protobuf object and writes it into
     * a field.
     */
    public void testWriteObjectRepeatedEmpty() {
        byte[] innerRaw = new byte[0];

        final ProtoOutputStream po = new ProtoOutputStream();
        po.writeRepeatedObject(ProtoOutputStream.makeFieldId(10,
                    ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_OBJECT),
                innerRaw);

        Assert.assertArrayEquals(new byte[] {
            (byte)0x52,
            (byte)0x00
        }, po.getBytes());
    }
}
