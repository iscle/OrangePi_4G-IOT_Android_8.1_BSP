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
 * Test the uint64 methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamUInt64Test extends TestCase {

    // ----------------------------------------------------------------------
    //  writeUInt64
    // ----------------------------------------------------------------------

    /**
     * Test writeUInt64.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT64;

        po.writeUInt64(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeUInt64(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeUInt64(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeUInt64(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeUInt64(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);
        po.writeUInt64(ProtoOutputStream.makeFieldId(6, fieldFlags), Long.MIN_VALUE);
        po.writeUInt64(ProtoOutputStream.makeFieldId(7, fieldFlags), Long.MAX_VALUE);

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
                // 4 -> Integer.MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> Integer.MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
                // 6 -> Long.MIN_VALUE
                (byte)0x30,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x01,
                // 7 -> Long.MAX_VALUE
                (byte)0x38,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
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
        testWriteCompat(Long.MIN_VALUE);
        testWriteCompat(Long.MAX_VALUE);
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(long val) throws Exception {
        final int fieldId = 60;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT64;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.uint64Field = val;
        po.writeUInt64(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertEquals(val, readback.uint64Field);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedUInt64
    // ----------------------------------------------------------------------

    /**
     * Test writeUInt64.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_UINT64;

        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(6, fieldFlags), Long.MIN_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(7, fieldFlags), Long.MAX_VALUE);

        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(3, fieldFlags), -1);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(4, fieldFlags), Integer.MIN_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(5, fieldFlags), Integer.MAX_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(6, fieldFlags), Long.MIN_VALUE);
        po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(7, fieldFlags), Long.MAX_VALUE);

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
                // 4 -> Integer.MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> Integer.MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
                // 6 -> Long.MIN_VALUE
                (byte)0x30,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x01,
                // 7 -> Long.MAX_VALUE
                (byte)0x38,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,

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
                // 4 -> Integer.MIN_VALUE
                (byte)0x20,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,
                // 5 -> Integer.MAX_VALUE
                (byte)0x28,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,
                // 6 -> Long.MIN_VALUE
                (byte)0x30,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x01,
                // 7 -> Long.MAX_VALUE
                (byte)0x38,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        testRepeatedCompat(new long[0]);
        testRepeatedCompat(new long[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                Long.MIN_VALUE, Long.MAX_VALUE });
    }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(long[] val) throws Exception {
        final int fieldId = 61;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_UINT64;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.uint64FieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.uint64FieldRepeated);
        assertEquals(val.length, readback.uint64FieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.uint64FieldRepeated[i]);
        }
    }

    // ----------------------------------------------------------------------
    //  writePackedUInt64
    // ----------------------------------------------------------------------

    /**
     * Test writeUInt64.
     */
    public void testPacked() throws Exception {
        testPacked(0);
        testPacked(1);
        testPacked(5);
    }

    /**
     * Create an array of the val, and write it.
     */
    private void writePackedUInt64(ProtoOutputStream po, int fieldId, long val) {
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_UINT64;
        po.writePackedUInt64(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), new long[] { val, val });
    }

    /**
     * Implementation of testPacked with a given chunkSize.
     */
    public void testPacked(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_UINT64;

        po.writePackedUInt64(ProtoOutputStream.makeFieldId(1000, fieldFlags), null);
        po.writePackedUInt64(ProtoOutputStream.makeFieldId(1001, fieldFlags), new long[0]);
        writePackedUInt64(po, 1, 0);
        writePackedUInt64(po, 2, 1);
        writePackedUInt64(po, 3, -1);
        writePackedUInt64(po, 4, Integer.MIN_VALUE);
        writePackedUInt64(po, 5, Integer.MAX_VALUE);
        writePackedUInt64(po, 6, Long.MIN_VALUE);
        writePackedUInt64(po, 7, Long.MAX_VALUE);

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

                // 4 -> Integer.MIN_VALUE
                (byte)0x22,
                (byte)0x14,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0xf8,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x01,

                // 5 -> Integer.MAX_VALUE
                (byte)0x2a,
                (byte)0x0a,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,

                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x07,

                // 6 -> Long.MIN_VALUE
                (byte)0x32,
                (byte)0x14,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x01,

                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x01,

                // 7 -> Long.MAX_VALUE
                (byte)0x3a,
                (byte)0x12,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,

                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testPackedCompat() throws Exception {
        testPackedCompat(new long[] {});
        testPackedCompat(new long[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                Long.MIN_VALUE, Long.MAX_VALUE });
    }

    /**
     * Implementation of testPackedUInt64Compat with a given value.
     */
    public void testPackedCompat(long[] val) throws Exception {
        final int fieldId = 62;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_UINT64;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.uint64FieldPacked = val;
        po.writePackedUInt64(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        Assert.assertArrayEquals(val, readback.uint64FieldPacked);
    }

    /**
     * Test that if you pass in the wrong type of fieldId, it throws.
     */
    public void testBadFieldIds() {
        // Single

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_UINT64), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_UINT64), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Packed

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new long[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedUInt64(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_UINT64),
                    new long[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
