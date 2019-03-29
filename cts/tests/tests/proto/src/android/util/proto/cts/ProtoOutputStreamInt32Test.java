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
 * Test the ProtoOutputStream class.
 */
public class ProtoOutputStreamInt32Test extends TestCase {

    // ----------------------------------------------------------------------
    //  writeInt32
    // ----------------------------------------------------------------------

    /**
     * Test writeInt32.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32;

        po.writeInt32(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeInt32(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeInt32(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeInt32(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeInt32(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, not written
                // 2 -> 1
                (byte)0x10,
                (byte)0x01,
                // 3 -> -1
                (byte)0x18,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 4 -> MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testWriteCompat() throws Exception {
        testWriteCompat(0);
        testWriteCompat(1);
        testWriteCompat(-1);
        testWriteCompat(Integer.MIN_VALUE);
        testWriteCompat(Integer.MAX_VALUE);
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(int val) throws Exception {
        final int fieldId = 30;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.int32Field = val;
        po.writeInt32(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertEquals(val, readback.int32Field);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedInt32
    // ----------------------------------------------------------------------

    /**
     * Test writeInt32.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_INT32;

        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);

        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x08,
                (byte)0x00,
                // 2 -> 1
                (byte)0x10,
                (byte)0x01,
                // 3 -> -1
                (byte)0x18,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 4 -> MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,

                // 1 -> 0 - default value, written when repeated
                (byte)0x08,
                (byte)0x00,
                // 2 -> 1
                (byte)0x10,
                (byte)0x01,
                // 3 -> -1
                (byte)0x18,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 4 -> MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        testRepeatedCompat(new int[0]);
        testRepeatedCompat(new int[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE });
    }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(int[] val) throws Exception {
        final int fieldId = 31;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_INT32;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.int32FieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.int32FieldRepeated);
        assertEquals(val.length, readback.int32FieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.int32FieldRepeated[i]);
        }
    }

    // ----------------------------------------------------------------------
    //  writePackedInt32
    // ----------------------------------------------------------------------

    /**
     * Test writeInt32.
     */
    public void testPacked() throws Exception {
        testPacked(0);
        testPacked(1);
        testPacked(5);
    }

    /**
     * Create an array of the val, and write it.
     */
    private void writePackedInt32(ProtoOutputStream po, int fieldId, int val) {
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32;
        po.writePackedInt32(ProtoOutputStream.makeFieldId(fieldId, fieldFlags),
                new int[] { val, val });
    }

    /**
     * Implementation of testPacked with a given chunkSize.
     */
    public void testPacked(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32;

        po.writePackedInt32(ProtoOutputStream.makeFieldId(1000, fieldFlags), null);
        po.writePackedInt32(ProtoOutputStream.makeFieldId(1001, fieldFlags), new int[0]);
        writePackedInt32(po, 1, 0);
        writePackedInt32(po, 2, 1);
        writePackedInt32(po, 3, -1);
        writePackedInt32(po, 4, Integer.MIN_VALUE);
        writePackedInt32(po, 5, Integer.MAX_VALUE);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x0a,
                (byte)0x02,
                (byte)0x00,
                (byte)0x00,
                // 2 -> 1
                (byte)0x12,
                (byte)0x02,
                (byte)0x01,
                (byte)0x01,
                // 3 -> -1
                (byte)0x1a,
                (byte)0x14,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                // 4 -> MIN_VALUE
                (byte)0x22,
                (byte)0x14,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                // 5 -> MAX_VALUE
                (byte)0x2a,
                (byte)0x0a,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,

                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testPackedCompat() throws Exception {
        testPackedCompat(new int[] {});
        testPackedCompat(new int[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE });
    }

    /**
     * Implementation of testPackedInt32Compat with a given value.
     */
    public void testPackedCompat(int[] val) throws Exception {
        final int fieldId = 32;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.int32FieldPacked = val;
        po.writePackedInt32(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        Assert.assertArrayEquals(val, readback.int32FieldPacked);
    }

    /**
     * Test that if you pass in the wrong type of fieldId, it throws.
     */
    public void testBadFieldIds() {
        // Single

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Packed

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new int[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedInt32(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32),
                    new int[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
