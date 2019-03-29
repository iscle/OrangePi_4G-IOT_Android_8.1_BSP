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

import android.util.Log;
import android.util.proto.ProtoOutputStream;
import static android.util.proto.ProtoOutputStream.*;

import com.google.protobuf.nano.MessageNano;
import junit.framework.TestCase;
import org.junit.Assert;

import java.util.HashMap;
import java.util.ArrayList;

/**
 * Tests that the write() functions produce the same values as their typed counterparts.
 */
public class ProtoOutputStreamSwitchedWriteTest extends TestCase {
    private static final String TAG = "ProtoOutputStreamSwitchedWriteTest";

    public static abstract class WriteTester {
        public final String name;

        public WriteTester(String n) {
            name = n;
        }

        abstract void write(Number val, long fieldId, ProtoOutputStream po);
    }

    private static final HashMap<Long,WriteTester> TYPED = new HashMap<Long,WriteTester>();
    private static final ArrayList<WriteTester> SWITCHED = new ArrayList<WriteTester>();

    static {
        TYPED.put(FIELD_TYPE_DOUBLE | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_DOUBLE | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeDouble(fieldId, val.doubleValue());
                    }
                });
        TYPED.put(FIELD_TYPE_FLOAT | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_FLOAT | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeFloat(fieldId, val.floatValue());
                    }
                });
        TYPED.put(FIELD_TYPE_INT32 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_INT32 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeInt32(fieldId, val.intValue());
                    }
                });
        TYPED.put(FIELD_TYPE_INT64 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_INT64 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeInt64(fieldId, val.longValue());
                    }
                });
        TYPED.put(FIELD_TYPE_UINT32 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_UINT32 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeUInt32(fieldId, val.intValue());
                    }
                });
        TYPED.put(FIELD_TYPE_UINT64 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_UINT64 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeUInt64(fieldId, val.longValue());
                    }
                });
        TYPED.put(FIELD_TYPE_SINT32 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_SINT32 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeSInt32(fieldId, val.intValue());
                    }
                });
        TYPED.put(FIELD_TYPE_SINT64 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_SINT64 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeSInt64(fieldId, val.longValue());
                    }
                });
        TYPED.put(FIELD_TYPE_FIXED32 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_FIXED32 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeFixed32(fieldId, val.intValue());
                    }
                });
        TYPED.put(FIELD_TYPE_FIXED64 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_FIXED64 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeFixed64(fieldId, val.longValue());
                    }
                });
        TYPED.put(FIELD_TYPE_SFIXED32 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_SFIXED32 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeSFixed32(fieldId, val.intValue());
                    }
                });
        TYPED.put(FIELD_TYPE_SFIXED64 | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_SFIXED64 | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeSFixed64(fieldId, val.longValue());
                    }
                });
        TYPED.put(FIELD_TYPE_BOOL | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_BOOL | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeBool(fieldId, val.longValue() != 0);
                    }
                });
        TYPED.put(FIELD_TYPE_ENUM | FIELD_COUNT_SINGLE,
                new WriteTester("FIELD_TYPE_ENUM | FIELD_COUNT_SINGLE") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.writeEnum(fieldId, val.intValue());
                    }
                });

        SWITCHED.add(new WriteTester("write(long, double)") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.write(fieldId, val.doubleValue());
                    }
                });
        SWITCHED.add(new WriteTester("write(long, float)") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.write(fieldId, val.floatValue());
                    }
                });
        SWITCHED.add(new WriteTester("write(long, int)") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.write(fieldId, val.intValue());
                    }
                });
        SWITCHED.add(new WriteTester("write(long, long)") {
                    @Override
                    public void write(Number val, long fieldId, ProtoOutputStream po) {
                        po.write(fieldId, val.longValue());
                    }
                });
    }

    private static void testAWrite(Number val, long fieldId,
            WriteTester typed, WriteTester switched) {
        final ProtoOutputStream switchedPo = new ProtoOutputStream();
        final ProtoOutputStream typedPo = new ProtoOutputStream();

        typed.write(val, fieldId, typedPo);
        switched.write(val, fieldId, switchedPo);

        final byte[] switchedResult = switchedPo.getBytes();
        final byte[] typedResult = typedPo.getBytes();

        try {
            Assert.assertArrayEquals(typedResult, switchedResult);
        } catch (Throwable ex) {
            throw new RuntimeException("Test for " + typed.name + " and "
                    + switched.name + " value=" + val + " (" + val.getClass().getSimpleName()
                    + ") failed: " + ex.getMessage(), ex);
        }
    }

    public static void testWrites(Number val) {
        for (HashMap.Entry<Long,WriteTester> entry: TYPED.entrySet()) {
            final long fieldId = ((long)entry.getKey()) | 1;
            final WriteTester typed = entry.getValue();

            for (WriteTester switched: SWITCHED) {
                testAWrite(val, fieldId, typed, switched);
            }
        }
    }
/**
     * Test double
     */
    public void testWriteDouble() {
        testWrites(new Double(0));
        testWrites(new Double(-1));
        testWrites(new Double(1));
        testWrites(new Double(100));
    }

    /**
     * Test float
     */
    public void testWriteFloat() {
        testWrites(new Float(0));
        testWrites(new Float(-1));
        testWrites(new Float(1));
        testWrites(new Float(100));
    }

    /**
     * Test int
     */
    public void testWriteInteger() {
        testWrites(new Integer(0));
        testWrites(new Integer(-1));
        testWrites(new Integer(1));
        testWrites(new Integer(100));
    }

    /**
     * Test long
     */
    public void testWriteLong() {
        testWrites(new Long(0));
        testWrites(new Long(-1));
        testWrites(new Long(1));
        testWrites(new Long(100));
    }

    /**
     * Test single strings
     */
    public void testWriteString() {
        final ProtoOutputStream typedPo = new ProtoOutputStream();
        final ProtoOutputStream switchedPo = new ProtoOutputStream();

        testString(1, "", typedPo, switchedPo);
        testString(2, null, typedPo, switchedPo);
        testString(3, "ABCD", typedPo, switchedPo);

        final byte[] typedResult = typedPo.getBytes();
        final byte[] switchedResult = switchedPo.getBytes();

        Assert.assertArrayEquals(typedResult, switchedResult);
    }

    private void testString(int id, String val,
            ProtoOutputStream typed, ProtoOutputStream switched) {
        switched.write(FIELD_TYPE_STRING | FIELD_COUNT_SINGLE | id, val);
        typed.writeString(FIELD_TYPE_STRING | FIELD_COUNT_SINGLE | id, val);
    }

    /**
     * Test repeated strings
     */
    public void testWriteRepeatedString() {
        final ProtoOutputStream typedPo = new ProtoOutputStream();
        final ProtoOutputStream switchedPo = new ProtoOutputStream();

        testRepeatedString(1, "", typedPo, switchedPo);
        testRepeatedString(2, null, typedPo, switchedPo);
        testRepeatedString(3, "ABCD", typedPo, switchedPo);

        final byte[] typedResult = typedPo.getBytes();
        final byte[] switchedResult = switchedPo.getBytes();

        Assert.assertArrayEquals(typedResult, switchedResult);
    }

    private void testRepeatedString(int id, String val,
            ProtoOutputStream typed, ProtoOutputStream switched) {
        switched.write(FIELD_TYPE_STRING | FIELD_COUNT_REPEATED | id, val);
        typed.writeRepeatedString(FIELD_TYPE_STRING | FIELD_COUNT_REPEATED | id, val);
    }

}
