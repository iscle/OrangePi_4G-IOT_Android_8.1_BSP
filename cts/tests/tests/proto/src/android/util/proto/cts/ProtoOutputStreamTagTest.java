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
 * Test tag related methods on ProtoOutputStream.
 */
public class ProtoOutputStreamTagTest extends TestCase {
    /**
     * Test that check field ID matches exactly the field Id.
     */
    public void testCheckFieldIdTypes() throws Exception {
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_TYPE_DOUBLE, ProtoOutputStream.FIELD_TYPE_INT32);
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_TYPE_ENUM, ProtoOutputStream.FIELD_TYPE_ENUM);
    }

    /**
     * Test that check field ID matches the table of count checks.
     */
    public void testCheckFieldIdCounts() throws Exception {
        // UNKNOWN provided
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_COUNT_UNKNOWN,
                ProtoOutputStream.FIELD_COUNT_UNKNOWN);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_UNKNOWN, ProtoOutputStream.FIELD_COUNT_SINGLE);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_UNKNOWN, ProtoOutputStream.FIELD_COUNT_REPEATED);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_UNKNOWN, ProtoOutputStream.FIELD_COUNT_PACKED);

        // SINGLE provided
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_COUNT_SINGLE,
                ProtoOutputStream.FIELD_COUNT_SINGLE);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_SINGLE, ProtoOutputStream.FIELD_COUNT_REPEATED);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_SINGLE, ProtoOutputStream.FIELD_COUNT_PACKED);

        // REPEATED provided
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_REPEATED, ProtoOutputStream.FIELD_COUNT_SINGLE);
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_COUNT_REPEATED,
                ProtoOutputStream.FIELD_COUNT_REPEATED);
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_REPEATED, ProtoOutputStream.FIELD_COUNT_PACKED);

        // PACKED provided
        assertCheckFieldIdThrows(ProtoOutputStream.FIELD_COUNT_PACKED, ProtoOutputStream.FIELD_COUNT_SINGLE);
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_COUNT_PACKED,
                ProtoOutputStream.FIELD_COUNT_REPEATED);
        ProtoOutputStream.checkFieldId(1 | ProtoOutputStream.FIELD_COUNT_PACKED,
                ProtoOutputStream.FIELD_COUNT_PACKED);
    }

    /**
     * Test that the error message for types works.
     */
    public void testCheckFieldIdErrorMessage() throws Exception {
        checkMessageForType(ProtoOutputStream.FIELD_TYPE_DOUBLE, "Double");

        checkMessageForCount(ProtoOutputStream.FIELD_COUNT_SINGLE, "");
        checkMessageForCount(ProtoOutputStream.FIELD_COUNT_REPEATED, "Repeated");
        checkMessageForCount(ProtoOutputStream.FIELD_COUNT_PACKED, "Packed");
    }

    /**
     * Check that the exception message properly gets the type.
     */
    private void checkMessageForType(long fieldType, String string) {
        final long badType = fieldType == ProtoOutputStream.FIELD_TYPE_DOUBLE
                ? ProtoOutputStream.FIELD_TYPE_INT32
                : ProtoOutputStream.FIELD_TYPE_DOUBLE;
        final String badTypeString = badType == ProtoOutputStream.FIELD_TYPE_DOUBLE
                ? "Double"
                : "Int32";
        final long goodCount = ProtoOutputStream.FIELD_COUNT_REPEATED;

        // Try it in the provided name
        try {
            ProtoOutputStream.checkFieldId(42 | goodCount | badType, goodCount | fieldType);
        } catch (IllegalArgumentException ex) {
            assertEquals("writeRepeated" + string
                        + " called for field 42 which should be used"
                        + " with writeRepeated" + badTypeString + ".",
                    ex.getMessage());
        }

        // Try it in the expected name
        try {
            ProtoOutputStream.checkFieldId(43 | goodCount | fieldType, goodCount | badType);
        } catch (IllegalArgumentException ex) {
            assertEquals("writeRepeated" + badTypeString
                        + " called for field 43 which should be used"
                        + " with writeRepeated" + string + ".",
                    ex.getMessage());
        }
    }


    /**
     * Check that the exception message properly gets the count.
     */
    private void checkMessageForCount(long fieldCount, String string) {
        final long badCount = fieldCount == ProtoOutputStream.FIELD_COUNT_SINGLE
                ? ProtoOutputStream.FIELD_COUNT_REPEATED
                : ProtoOutputStream.FIELD_COUNT_SINGLE;
        final String badCountString = badCount == ProtoOutputStream.FIELD_COUNT_SINGLE
                ? ""
                : "Repeated";
        final long goodType = ProtoOutputStream.FIELD_TYPE_FIXED32;

        // Try it in the provided name
        try {
            ProtoOutputStream.checkFieldId(44 | badCount | goodType, fieldCount | goodType);
        } catch (IllegalArgumentException ex) {
            assertEquals("write" + string
                    + "Fixed32 called for field 44 which should be used"
                    + " with write" + badCountString + "Fixed32.",
                    ex.getMessage());
        }

        // Try it in the expected name
        try {
            ProtoOutputStream.checkFieldId(45 | fieldCount | goodType, badCount | goodType);
        } catch (IllegalArgumentException ex) {
            String extraString = "";
            if (fieldCount == ProtoOutputStream.FIELD_COUNT_PACKED) {
                extraString = " or writeRepeatedFixed32";
            }
            assertEquals("write" + badCountString
                    + "Fixed32 called for field 45 which should be used"
                    + " with write" + string + "Fixed32" + extraString + ".",
                    ex.getMessage());
        }
    }

    /**
     * Validate one call to checkFieldId that is expected to throw.
     */
    public void assertCheckFieldIdThrows(long fieldId, long expectedFlags) 
            throws Exception {
        try {
            ProtoOutputStream.checkFieldId(fieldId, expectedFlags);
            throw new Exception("checkFieldId(0x" + Long.toHexString(fieldId)
                    + ", 0x" + Long.toHexString(expectedFlags) + ") did not throw.");
        } catch (IllegalArgumentException ex) {
            // good
        }
    }
}
