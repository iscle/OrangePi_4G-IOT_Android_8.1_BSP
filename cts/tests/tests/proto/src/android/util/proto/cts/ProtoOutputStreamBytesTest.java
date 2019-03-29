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
 * Test the bytes methods on the ProtoOutputStream class.
 */
public class ProtoOutputStreamBytesTest extends TestCase {
    private static byte[] makeData() {
        final byte[] data = new byte[523];
        for (int i=0; i<data.length; i++) {
            data[i] = (byte)i;
        }
        return data;
    }

    // ----------------------------------------------------------------------
    //  writeBytes
    // ----------------------------------------------------------------------

    /**
     * Test writeBytes.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_BYTES;

        final byte[] data = makeData();

        po.writeBytes(ProtoOutputStream.makeFieldId(1, fieldFlags), data);

        final byte[] expected = new byte[data.length + 3];
        expected[0] = (byte)0x0a;
        expected[1] = (byte)0x8b;
        expected[2] = (byte)0x04;
        for (int i=0; i<data.length; i++) {
            expected[i+3] = (byte)i;
        }

        Assert.assertArrayEquals(expected, po.getBytes());
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testWriteCompat() throws Exception {
        // Nano doesn't work with null.
        // testWriteCompat(null);

        testWriteCompat(new byte[0]);
        testWriteCompat(new byte[] { 0 } );
        testWriteCompat(new byte[] { 1 } );
        testWriteCompat(makeData());
    }

    /**
     * Implementation of testWriteCompat with a given value.
     */
    public void testWriteCompat(byte[] val) throws Exception {
        final int fieldId = 150;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_BYTES;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.bytesField = val;
        po.writeBytes(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val);

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        Assert.assertArrayEquals(val, readback.bytesField);
    }

    // ----------------------------------------------------------------------
    //  writeRepeatedBytes
    // ----------------------------------------------------------------------

    /**
     * Test writeBytes.
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
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_BYTES;

        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(1, fieldFlags), null);
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(2, fieldFlags), new byte[0]);
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(3, fieldFlags),
                new byte[] { 0, 1, 2, 3, 4, 5 });
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(4, fieldFlags),
                new byte[] { (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc });

        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(1, fieldFlags), null);
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(2, fieldFlags), new byte[0]);
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(3, fieldFlags),
                new byte[] { 0, 1, 2, 3, 4, 5 });
        po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(4, fieldFlags),
                new byte[] { (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc });

        final byte[] result = po.getBytes();
        Assert.assertArrayEquals(new byte[] {
                // 1 -> null - default value, written when repeated
                (byte)0x0a,
                (byte)0x00,
                // 2 -> { } - default value, written when repeated
                (byte)0x12,
                (byte)0x00,
                // 3 -> { 0, 1, 2, 3, 4, 5 }
                (byte)0x1a,
                (byte)0x06,
                (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05,
                // 4 -> { (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc }
                (byte)0x22,
                (byte)0x04,
                (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc,

                // 1 -> null - default value, written when repeated
                (byte)0x0a,
                (byte)0x00,
                // 2 -> { } - default value, written when repeated
                (byte)0x12,
                (byte)0x00,
                // 3 -> { 0, 1, 2, 3, 4, 5 }
                (byte)0x1a,
                (byte)0x06,
                (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05,
                // 4 -> { (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc }
                (byte)0x22,
                (byte)0x04,
                (byte)0xff, (byte)0xfe, (byte)0xfd, (byte)0xfc,
            }, result);
    }

    /**
     * Test that writing a with ProtoOutputStream matches, and can be read by standard proto.
     */
    public void testRepeatedCompat() throws Exception {
        testRepeatedCompat(new byte[0][]);
        testRepeatedCompat(new byte[][] {
                    new byte[0],
                    new byte[] { 0 },
                    new byte[] { 1 },
                    makeData(),
                });
    }

    /**
     * Implementation of testRepeatedCompat with a given value.
     */
    public void testRepeatedCompat(byte[][] val) throws Exception {
        final int fieldId = 151;
        final long fieldFlags = ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_BYTES;

        final Test.All all = new Test.All();
        final ProtoOutputStream po = new ProtoOutputStream(0);

        all.bytesFieldRepeated = val;
        for (int i=0; i<val.length; i++) {
            po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(fieldId, fieldFlags), val[i]);
        }

        final byte[] result = po.getBytes();
        final byte[] expected = MessageNano.toByteArray(all);

        Assert.assertArrayEquals(expected, result);

        final Test.All readback = Test.All.parseFrom(result);

        assertNotNull(readback.bytesFieldRepeated);
        assertEquals(val.length, readback.bytesFieldRepeated.length);
        for (int i=0; i<val.length; i++) {
            Assert.assertArrayEquals(val[i], readback.bytesFieldRepeated[i]);
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
            po.writeBytes(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_SINGLE | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new byte[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeBytes(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_BYTES),
                    new byte[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Repeated

        // Good Count / Bad Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_REPEATED | ProtoOutputStream.FIELD_TYPE_DOUBLE),
                    new byte[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }

        // Bad Count / Good Type
        try {
            final ProtoOutputStream po = new ProtoOutputStream();
            po.writeRepeatedBytes(ProtoOutputStream.makeFieldId(1,
                        ProtoOutputStream.FIELD_COUNT_PACKED | ProtoOutputStream.FIELD_TYPE_BYTES),
                    new byte[0]);
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
