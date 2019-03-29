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
 * Test the float methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamFloatTest extends TestCase {

    /**
     * Compare if the values are identical (including handling for matching isNaN).
     */
    public void assertEquals(float expected, float actual) {
        if (Float.isNaN(expected)) {
            if (!Float.isNaN(actual)) {
                throw new RuntimeException("expected NaN, actual " + actual);
            }
        } else {
            if (expected != actual) {
                throw new RuntimeException("expected " + expected + ", actual " + actual);
            }
        }
    }

    // ----------------------------------------------------------------------
    //  writeFloat
    // ----------------------------------------------------------------------

    /**
     * Test writeFloat.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_FLOAT;

        po.writeFloat(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeFloat(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeFloat(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432f);
        po.writeFloat(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42f);
        po.writeFloat(ProtoOutputStream.makeFieldId(5, fieldFlags), Float.MIN_NORMAL);
        po.writeFloat(ProtoOutputStream.makeFieldId(6, fieldFlags), Float.MIN_VALUE);
        po.writeFloat(ProtoOutputStream.makeFieldId(7, fieldFlags), Float.NEGATIVE_INFINITY);
        po.writeFloat(ProtoOutputStream.makeFieldId(8, fieldFlags), Float.NaN);
        po.writeFloat(ProtoOutputStream.makeFieldId(9, fieldFlags), Float.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, not written
                // 2 -> 1
                (byte)0x15,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x1d,
                (byte)0xd3, (byte)0x4d, (byte)0x9a, (byte)0xc4,
                // 4 -> 42.42
                (byte)0x25,
                (byte)0x14, (byte)0xae, (byte)0x29, (byte)0x42,
                // 5 -> Float.MIN_NORMAL
                (byte)0x2d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x35,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Float.NEGATIVE_INFINITY
                (byte)0x3d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0xff,
                // 8 -> Float.NaN
                (byte)0x45,
                (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x7f,
                // 9 -> Float.POSITIVE_INFINITY
                (byte)0x4d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x7f,

            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testWriteCompat() throws Exception {
        testWriteCompat(0);
        testWriteCompat(1);
        testWriteCompat(-1234.432f);
        testWriteCompat(42.42f);
        testWriteCompat(Float.MIN_NORMAL);
        testWriteCompat(Float.MIN_VALUE);
        testWriteCompat(Float.NEGATIVE_INFINITY);
        testWriteCompat(Float.NaN);
        testWriteCompat(Float.POSITIVE_INFINITY);
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(float val) throws Exception {
        final int fieldId = 20;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_FLOAT;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.floatField = val;
        po.writeFloat(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertEquals(val, readback.floatField);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedFloat
    // ----------------------------------------------------------------------

    /**
     * Test writeFloat.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_FLOAT;

        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432f);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42f);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(5, fieldFlags), Float.MIN_NORMAL);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(6, fieldFlags), Float.MIN_VALUE);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(7, fieldFlags), Float.NEGATIVE_INFINITY);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(8, fieldFlags), Float.NaN);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(9, fieldFlags), Float.POSITIVE_INFINITY);

        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432f);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42f);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(5, fieldFlags), Float.MIN_NORMAL);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(6, fieldFlags), Float.MIN_VALUE);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(7, fieldFlags), Float.NEGATIVE_INFINITY);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(8, fieldFlags), Float.NaN);
        po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(9, fieldFlags), Float.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x0d,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x15,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x1d,
                (byte)0xd3, (byte)0x4d, (byte)0x9a, (byte)0xc4,
                // 4 -> 42.42
                (byte)0x25,
                (byte)0x14, (byte)0xae, (byte)0x29, (byte)0x42,
                // 5 -> Float.MIN_NORMAL
                (byte)0x2d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x35,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Float.NEGATIVE_INFINITY
                (byte)0x3d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0xff,
                // 8 -> Float.NaN
                (byte)0x45,
                (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x7f,
                // 9 -> Float.POSITIVE_INFINITY
                (byte)0x4d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x7f,

                // 1 -> 0 - default value, written when repeated
                (byte)0x0d,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x15,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x1d,
                (byte)0xd3, (byte)0x4d, (byte)0x9a, (byte)0xc4,
                // 4 -> 42.42
                (byte)0x25,
                (byte)0x14, (byte)0xae, (byte)0x29, (byte)0x42,
                // 5 -> Float.MIN_NORMAL
                (byte)0x2d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x35,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Float.NEGATIVE_INFINITY
                (byte)0x3d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0xff,
                // 8 -> Float.NaN
                (byte)0x45,
                (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x7f,
                // 9 -> Float.POSITIVE_INFINITY
                (byte)0x4d,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        testRepeatedCompat(new float[0]);
        testRepeatedCompat(new float[] { 0, 1, -1234.432f, 42.42f,
                    Float.MIN_NORMAL, Float.MIN_VALUE, Float.NEGATIVE_INFINITY, Float.NaN,
                    Float.POSITIVE_INFINITY,
                });
        }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(float[] val) throws Exception {
        final int fieldId = 21;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_FLOAT;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.floatFieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.floatFieldRepeated);
        assertEquals(val.length, readback.floatFieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.floatFieldRepeated[i]);
        }
    }

    // ----------------------------------------------------------------------
    //  writePackedFloat
    // ----------------------------------------------------------------------

    /**
     * Test writeFloat.
     */
    public void testPacked() throws Exception {
        testPacked(0);
        testPacked(1);
        testPacked(5);
    }

    /**
     * Create an array of the val, and write it.
     */
    private void writePackedFloat(ProtoOutputStream po, int fieldId, float val) {
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_FLOAT;
        po.writePackedFloat(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), new float[] { val, val });
    }

    /**
     * Implementation of testPacked with a given chunkSize.
     */
    public void testPacked(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_FLOAT;

        po.writePackedFloat(ProtoOutputStream.makeFieldId(1000, fieldFlags), null);
        po.writePackedFloat(ProtoOutputStream.makeFieldId(1001, fieldFlags), new float[0]);
        writePackedFloat(po, 1, 0);
        writePackedFloat(po, 2, 1);
        writePackedFloat(po, 3, -1234.432f);
        writePackedFloat(po, 4, 42.42f);
        writePackedFloat(po, 5, Float.MIN_NORMAL);
        writePackedFloat(po, 6, Float.MIN_VALUE);
        writePackedFloat(po, 7, Float.NEGATIVE_INFINITY);
        writePackedFloat(po, 8, Float.NaN);
        writePackedFloat(po, 9, Float.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x0a,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x12,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x1a,
                (byte)0x08,
                (byte)0xd3, (byte)0x4d, (byte)0x9a, (byte)0xc4,
                (byte)0xd3, (byte)0x4d, (byte)0x9a, (byte)0xc4,
                // 4 -> 42.42
                (byte)0x22,
                (byte)0x08,
                (byte)0x14, (byte)0xae, (byte)0x29, (byte)0x42,
                (byte)0x14, (byte)0xae, (byte)0x29, (byte)0x42,
                // 5 -> Float.MIN_NORMAL
                (byte)0x2a,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x32,
                (byte)0x08,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Float.NEGATIVE_INFINITY
                (byte)0x3a,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0xff,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0xff,
                // 8 -> Float.NaN
                (byte)0x42,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x7f,
                (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x7f,
                // 9 -> Float.POSITIVE_INFINITY
                (byte)0x4a,
                (byte)0x08,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x7f,
                (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testPackedCompat() throws Exception {
        testPackedCompat(new float[] {});
        testPackedCompat(new float[] { 0, 1, -1234.432f, 42.42f,
                    Float.MIN_NORMAL, Float.MIN_VALUE, Float.NEGATIVE_INFINITY, Float.NaN,
                    Float.POSITIVE_INFINITY,
                });
        }

    /**
     * Implementation of testPackedFloatCompat with a given value.
     */
    public void testPackedCompat(float[] val) throws Exception {
        final int fieldId = 22;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_FLOAT;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.floatFieldPacked = val;
        po.writePackedFloat(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.floatFieldPacked);
        assertEquals(val.length, readback.floatFieldPacked.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.floatFieldPacked[i]);
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
            po.writeFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_FLOAT), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_FLOAT), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Packed

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new float[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedFloat(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_FLOAT),
                    new float[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
