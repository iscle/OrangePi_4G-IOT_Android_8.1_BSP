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
 * Test the string methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamStringTest extends TestCase {

    // ----------------------------------------------------------------------
    //  writeString
    // ----------------------------------------------------------------------

    private static String makeLongString() {
        final StringBuilder sb = new StringBuilder(0x9fff-0x4E00);
        // All of the unicode unified CJK characters
        for (int i=0x4E00; i<=0x9fff; i++) {
            sb.append((char)(0x4E00 + i));
        }
        return sb.toString();
    }

    private static final String LONG_STRING = makeLongString();

    public void testWriteLongString() throws Exception {
        testWriteLongString(0);
        testWriteLongString(1);
        testWriteLongString(5);
        testWriteLongString(1024 * 1024);
    }

    public void testWriteLongString(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_STRING;

        final String string = LONG_STRING;

        po.writeString(ProtoOutputStream.makeFieldId(1, fieldFlags), string);

        final byte[] utf8 = string.getBytes("UTF-8");
        byte[] expected = new byte[utf8.length + 4];

        // tag
        expected[0] = (byte)0x0a;
        // size
        expected[1] = (byte)0x82;
        expected[2] = (byte)0xcc;
        expected[3] = (byte)0x03;
        // data
        System.arraycopy(utf8, 0, expected, 4, utf8.length);

        Assert.assertArrayEquals(expected, po.getBytes());
    }

    /**
     * Test writeString.
     */
    public void testWrite() throws Exception {
        testWrite(0);
        testWrite(1);
        testWrite(5);
    }

    /**
     * Implementation of testWrite with a given chunkSize.
     */
    public void testWrite(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_STRING;

        po.writeString(ProtoOutputStream.makeFieldId(1, fieldFlags), null);
        po.writeString(ProtoOutputStream.makeFieldId(2, fieldFlags), "");
        po.writeString(ProtoOutputStream.makeFieldId(3, fieldFlags), "abcd\u3110!");
        po.writeString(ProtoOutputStream.makeFieldId(4, fieldFlags), "Hi");

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> null - default value, not written
                // 2 -> "" - default value, not written
                // 3 -> "abcd\u3110!"
                (byte)0x1a,
                (byte)0x08,
                (byte)0x61, (byte)0x62, (byte)0x63, (byte)0x64,
                (byte)0xe3, (byte)0x84, (byte)0x90, (byte)0x21,
                // 4 -> "Hi"
                (byte)0x22,
                (byte)0x02,
                (byte)0x48, (byte)0x69,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testWriteCompat() throws Exception {
        // Nano doesn't work with null.
        // testWriteCompat(null);

        testWriteCompat("");
        testWriteCompat("abcd\u3110!");
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(String val) throws Exception {
        final int fieldId = 140;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_STRING;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.stringField = val;
        po.writeString(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertEquals(val, readback.stringField);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedString
    // ----------------------------------------------------------------------

    /**
     * Test writeString.
     */
    public void testRepeated() throws Exception {
        testRepeated(0);
        testRepeated(1);
        testRepeated(5);
    }

    /**
     * Implementation of testRepeated with a given chunkSize.
     */
    public void testRepeated(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_STRING;

        po.writeRepeatedString(ProtoOutputStream.makeFieldId(1, fieldFlags), null);
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(2, fieldFlags), "");
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(3, fieldFlags), "abcd\u3110!");
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(4, fieldFlags), "Hi");

        po.writeRepeatedString(ProtoOutputStream.makeFieldId(1, fieldFlags), null);
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(2, fieldFlags), "");
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(3, fieldFlags), "abcd\u3110!");
        po.writeRepeatedString(ProtoOutputStream.makeFieldId(4, fieldFlags), "Hi");

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> null - default value, written when repeated
                (byte)0x0a,
                (byte)0x00,
                // 2 -> "" - default value, written when repeated
                (byte)0x12,
                (byte)0x00,
                // 3 -> "abcd\u3110!"
                (byte)0x1a,
                (byte)0x08,
                (byte)0x61, (byte)0x62, (byte)0x63, (byte)0x64,
                (byte)0xe3, (byte)0x84, (byte)0x90, (byte)0x21,
                // 4 -> "Hi"
                (byte)0x22,
                (byte)0x02,
                (byte)0x48, (byte)0x69,

                // 1 -> null - default value, written when repeated
                (byte)0x0a,
                (byte)0x00,
                // 2 -> "" - default value, written when repeated
                (byte)0x12,
                (byte)0x00,
                // 3 -> "abcd\u3110!"
                (byte)0x1a,
                (byte)0x08,
                (byte)0x61, (byte)0x62, (byte)0x63, (byte)0x64,
                (byte)0xe3, (byte)0x84, (byte)0x90, (byte)0x21,
                // 4 -> "Hi"
                (byte)0x22,
                (byte)0x02,
                (byte)0x48, (byte)0x69,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        // Nano doesn't work with null.
        testRepeatedCompat(new String[0]);
        testRepeatedCompat(new String[] { "", "abcd\u3110!", "Hi", });
    }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(String[] val) throws Exception {
        final int fieldId = 141;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_STRING;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.stringFieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedString(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.stringFieldRepeated);
        assertEquals(val.length, readback.stringFieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.stringFieldRepeated[i]);
        }
    }

    /**
     * Test that if you pass in the wrong type of fieldId, it throws.
     */
    public void testBadFieldIds() {
        // Single

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeString(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE), "");
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeString(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_STRING), "");
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedString(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE), "");
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedString(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_STRING), "");
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
