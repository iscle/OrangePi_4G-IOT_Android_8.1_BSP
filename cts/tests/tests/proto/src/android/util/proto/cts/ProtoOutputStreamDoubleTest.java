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
 * Test the double methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamDoubleTest extends TestCase {
    /**
     * Compare if the values are identical (including handling for matching isNaN).
     */
    public void assertEquals(double expected, double actual) {
        if (Double.isNaN(expected)) {
            if (!Double.isNaN(actual)) {
                throw new RuntimeException("expected NaN, actual " + actual);
            }
        } else {
            if (expected != actual) {
                throw new RuntimeException("expected " + expected + ", actual " + actual);
            }
        }
    }

    // ----------------------------------------------------------------------
    //  writeDouble
    // ----------------------------------------------------------------------

    /**
     * Test writeDouble.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE;

        po.writeDouble(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeDouble(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeDouble(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432);
        po.writeDouble(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42);
        po.writeDouble(ProtoOutputStream.makeFieldId(5, fieldFlags), Double.MIN_NORMAL);
        po.writeDouble(ProtoOutputStream.makeFieldId(6, fieldFlags), Double.MIN_VALUE);
        po.writeDouble(ProtoOutputStream.makeFieldId(7, fieldFlags), Double.NEGATIVE_INFINITY);
        po.writeDouble(ProtoOutputStream.makeFieldId(8, fieldFlags), Double.NaN);
        po.writeDouble(ProtoOutputStream.makeFieldId(9, fieldFlags), Double.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, not written
                // 2 -> 1
                (byte)0x11,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x19,
                (byte)0x7d, (byte)0x3f, (byte)0x35, (byte)0x5e,
                (byte)0xba, (byte)0x49, (byte)0x93, (byte)0xc0,
                // 4 -> 42.42
                (byte)0x21,
                (byte)0xf6, (byte)0x28, (byte)0x5c, (byte)0x8f,
                (byte)0xc2, (byte)0x35, (byte)0x45, (byte)0x40,
                // 5 -> Double.MIN_NORMAL
                (byte)0x29,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x10, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x31,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Double.NEGATIVE_INFINITY
                (byte)0x39,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0xff,
                // 8 -> Double.NaN
                (byte)0x41,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf8, (byte)0x7f,
                // 9 -> Double.POSITIVE_INFINITY
                (byte)0x49,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testWriteCompat() throws Exception {
        testWriteCompat(0);
        testWriteCompat(1);
        testWriteCompat(-1234.432);
        testWriteCompat(42.42);
        testWriteCompat(Double.MIN_NORMAL);
        testWriteCompat(Double.MIN_VALUE);
        testWriteCompat(Double.NEGATIVE_INFINITY);
        testWriteCompat(Double.NaN);
        testWriteCompat(Double.POSITIVE_INFINITY);
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(double val) throws Exception {
        final int fieldId = 10;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.doubleField = val;
        po.writeDouble(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertEquals(val, readback.doubleField);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedDouble
    // ----------------------------------------------------------------------

    /**
     * Test writeDouble.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE;

        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(5, fieldFlags), Double.MIN_NORMAL);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(6, fieldFlags), Double.MIN_VALUE);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(7, fieldFlags), Double.NEGATIVE_INFINITY);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(8, fieldFlags), Double.NaN);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(9, fieldFlags), Double.POSITIVE_INFINITY);

        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(1, fieldFlags), 0);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(2, fieldFlags), 1);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(3, fieldFlags), -1234.432);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(4, fieldFlags), 42.42);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(5, fieldFlags), Double.MIN_NORMAL);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(6, fieldFlags), Double.MIN_VALUE);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(7, fieldFlags), Double.NEGATIVE_INFINITY);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(8, fieldFlags), Double.NaN);
        po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(9, fieldFlags), Double.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x09,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x11,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x19,
                (byte)0x7d, (byte)0x3f, (byte)0x35, (byte)0x5e,
                (byte)0xba, (byte)0x49, (byte)0x93, (byte)0xc0,
                // 4 -> 42.42
                (byte)0x21,
                (byte)0xf6, (byte)0x28, (byte)0x5c, (byte)0x8f,
                (byte)0xc2, (byte)0x35, (byte)0x45, (byte)0x40,
                // 5 -> Double.MIN_NORMAL
                (byte)0x29,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x10, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x31,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Double.NEGATIVE_INFINITY
                (byte)0x39,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0xff,
                // 8 -> Double.NaN
                (byte)0x41,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf8, (byte)0x7f,
                // 9 -> Double.POSITIVE_INFINITY
                (byte)0x49,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x7f,

                // 1 -> 0 - default value, written when repeated
                (byte)0x09,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x11,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x19,
                (byte)0x7d, (byte)0x3f, (byte)0x35, (byte)0x5e,
                (byte)0xba, (byte)0x49, (byte)0x93, (byte)0xc0,
                // 4 -> 42.42
                (byte)0x21,
                (byte)0xf6, (byte)0x28, (byte)0x5c, (byte)0x8f,
                (byte)0xc2, (byte)0x35, (byte)0x45, (byte)0x40,
                // 5 -> Double.MIN_NORMAL
                (byte)0x29,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x10, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x31,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Double.NEGATIVE_INFINITY
                (byte)0x39,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0xff,
                // 8 -> Double.NaN
                (byte)0x41,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf8, (byte)0x7f,
                // 9 -> Double.POSITIVE_INFINITY
                (byte)0x49,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        testRepeatedCompat(new double[0]);
        testRepeatedCompat(new double[] { 0, 1, -1234.432, 42.42,
                    Double.MIN_NORMAL, Double.MIN_VALUE, Double.NEGATIVE_INFINITY, Double.NaN,
                    Double.POSITIVE_INFINITY,
                });
        }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(double[] val) throws Exception {
        final int fieldId = 11;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.doubleFieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.doubleFieldRepeated);
        assertEquals(val.length, readback.doubleFieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.doubleFieldRepeated[i]);
        }
    }

    // ----------------------------------------------------------------------
    //  writePackedDouble
    // ----------------------------------------------------------------------

    /**
     * Test writeDouble.
     */
    public void testPacked() throws Exception {
        testPacked(0);
        testPacked(1);
        testPacked(5);
    }

    /**
     * Create an array of the val, and write it.
     */
    private void writePackedDouble(ProtoOutputStream po, int fieldId, double val) {
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE;
        po.writePackedDouble(ProtoOutputStream.makeFieldId(fieldId, fieldFlags),
                new double[] { val, val });
    }

    /**
     * Implementation of testPacked with a given chunkSize.
     */
    public void testPacked(int chunkSize) throws Exception {
        final ProtoOutputStream po = new ProtoOutputStream(chunkSize);

        writePackedDouble(po, 1, 0);
        writePackedDouble(po, 2, 1);
        writePackedDouble(po, 3, -1234.432);
        writePackedDouble(po, 4, 42.42);
        writePackedDouble(po, 5, Double.MIN_NORMAL);
        writePackedDouble(po, 6, Double.MIN_VALUE);
        writePackedDouble(po, 7, Double.NEGATIVE_INFINITY);
        writePackedDouble(po, 8, Double.NaN);
        writePackedDouble(po, 9, Double.POSITIVE_INFINITY);

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> 0 - default value, written when repeated
                (byte)0x0a,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 2 -> 1
                (byte)0x12,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x3f,
                // 3 -> -1234.432
                (byte)0x1a,
                (byte)0x10,
                (byte)0x7d, (byte)0x3f, (byte)0x35, (byte)0x5e,
                (byte)0xba, (byte)0x49, (byte)0x93, (byte)0xc0,
                (byte)0x7d, (byte)0x3f, (byte)0x35, (byte)0x5e,
                (byte)0xba, (byte)0x49, (byte)0x93, (byte)0xc0,
                // 4 -> 42.42
                (byte)0x22,
                (byte)0x10,
                (byte)0xf6, (byte)0x28, (byte)0x5c, (byte)0x8f,
                (byte)0xc2, (byte)0x35, (byte)0x45, (byte)0x40,
                (byte)0xf6, (byte)0x28, (byte)0x5c, (byte)0x8f,
                (byte)0xc2, (byte)0x35, (byte)0x45, (byte)0x40,
                // 5 -> Double.MIN_NORMAL
                (byte)0x2a,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x10, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x10, (byte)0x00,
                // 6 -> DOUBLE.MIN_VALUE
                (byte)0x32,
                (byte)0x10,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                // 7 -> Double.NEGATIVE_INFINITY
                (byte)0x3a,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0xff,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0xff,
                // 8 -> Double.NaN
                (byte)0x42,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf8, (byte)0x7f,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf8, (byte)0x7f,
                // 9 -> Double.POSITIVE_INFINITY
                (byte)0x4a,
                (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x7f,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x7f,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testPackedCompat() throws Exception {
        testPackedCompat(new double[] {});
        testPackedCompat(new double[] { 0, 1, -1234.432, 42.42,
                    Double.MIN_NORMAL, Double.MIN_VALUE, Double.NEGATIVE_INFINITY, Double.NaN,
                    Double.POSITIVE_INFINITY,
                });
    }

    /**
     * Implementation of testPackedDoubleCompat with a given value.
     */
    public void testPackedCompat(double[] val) throws Exception {
        final int fieldId = 12;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.doubleFieldPacked = val;
        po.writePackedDouble(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.doubleFieldPacked);
        assertEquals(val.length, readback.doubleFieldPacked.length);
        for (int i=0; i<val.length; i++) {
            assertEquals(val[i], readback.doubleFieldPacked[i]);
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
            po.writeDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_INT32), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_INT32), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_DOUBLE), 0);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Packed

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_INT32),
                    new double[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writePackedDouble(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new double[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
