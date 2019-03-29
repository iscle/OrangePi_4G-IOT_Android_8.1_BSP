/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.util.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.MalformedJsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class JsonReaderTest {

    private static final int READER_BUFFER_SIZE = 1024;

    @Test
    public void testReadArray() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true, true]"));
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        assertEquals(true, reader.nextBoolean());
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testReadEmptyArray() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[]"));
        reader.beginArray();
        assertFalse(reader.hasNext());
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testReadObject() throws IOException {
        JsonReader reader = new JsonReader(new StringReader(
                "{\"a\": \"android\", \"b\": \"banana\"}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        assertEquals("android", reader.nextString());
        assertEquals("b", reader.nextName());
        assertEquals("banana", reader.nextString());
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testReadEmptyObject() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{}"));
        reader.beginObject();
        assertFalse(reader.hasNext());
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testSkipObject() throws IOException {
        JsonReader reader = new JsonReader(new StringReader(
                "{\"a\": { \"c\": [], \"d\": [true, true, {}] }, \"b\": \"banana\"}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        reader.skipValue();
        assertEquals("b", reader.nextName());
        reader.skipValue();
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test(expected=IllegalStateException.class)
    public void testSkipBeforeEndOfObject() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{}"));
        reader.beginObject();
        // Should not be possible to skip without elements
        reader.skipValue();
    }

    @Test(expected=IllegalStateException.class)
    public void testSkipBeforeEndOfArray() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[]"));
        reader.beginArray();
        // Should not be possible to skip without elements
        reader.skipValue();
    }

    @Test
    public void testSkipAfterEndOfDocument() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{}"));
        reader.beginObject();
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
        try {
            reader.skipValue();
            fail("Should not be possible to skip without elements.");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testHelloWorld() throws IOException {
        String json = "{\n" +
                "   \"hello\": true,\n" +
                "   \"foo\": [\"world\"]\n" +
                "}";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginObject();
        assertEquals("hello", reader.nextName());
        assertEquals(true, reader.nextBoolean());
        assertEquals("foo", reader.nextName());
        reader.beginArray();
        assertEquals("world", reader.nextString());
        reader.endArray();
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test(expected=NullPointerException.class)
    public void testNulls() {
        new JsonReader(null);
    }

    @Test(expected=IOException.class)
    public void testEmptyString1() throws IOException {
        new JsonReader(new StringReader("")).beginArray();
    }

    @Test(expected=IOException.class)
    public void testEmptyString2() throws IOException {
        new JsonReader(new StringReader("")).beginObject();
    }

    @Test(expected=IOException.class)
    public void testNoTopLevelObject() throws IOException {
        new JsonReader(new StringReader("true")).nextBoolean();
    }

    @Test
    public void testCharacterUnescaping() throws IOException {
        String json = "[\"a\","
                + "\"a\\\"\","
                + "\"\\\"\","
                + "\":\","
                + "\",\","
                + "\"\\b\","
                + "\"\\f\","
                + "\"\\n\","
                + "\"\\r\","
                + "\"\\t\","
                + "\" \","
                + "\"\\\\\","
                + "\"{\","
                + "\"}\","
                + "\"[\","
                + "\"]\","
                + "\"\\u0000\","
                + "\"\\u0019\","
                + "\"\\u20AC\""
                + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        assertEquals("a", reader.nextString());
        assertEquals("a\"", reader.nextString());
        assertEquals("\"", reader.nextString());
        assertEquals(":", reader.nextString());
        assertEquals(",", reader.nextString());
        assertEquals("\b", reader.nextString());
        assertEquals("\f", reader.nextString());
        assertEquals("\n", reader.nextString());
        assertEquals("\r", reader.nextString());
        assertEquals("\t", reader.nextString());
        assertEquals(" ", reader.nextString());
        assertEquals("\\", reader.nextString());
        assertEquals("{", reader.nextString());
        assertEquals("}", reader.nextString());
        assertEquals("[", reader.nextString());
        assertEquals("]", reader.nextString());
        assertEquals("\0", reader.nextString());
        assertEquals("\u0019", reader.nextString());
        assertEquals("\u20AC", reader.nextString());
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testIntegersWithFractionalPartSpecified() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[1.0,1.0,1.0]"));
        reader.beginArray();
        assertEquals(1.0, reader.nextDouble(), 0.0f);
        assertEquals(1, reader.nextInt());
        assertEquals(1L, reader.nextLong());
    }

    @Test
    public void testDoubles() throws IOException {
        String json = "[-0.0,"
                + "1.0,"
                + "1.7976931348623157E308,"
                + "4.9E-324,"
                + "0.0,"
                + "-0.5,"
                + "2.2250738585072014E-308,"
                + "3.141592653589793,"
                + "2.718281828459045,"
                + "\"1.0\","
                + "\"011.0\","
                + "\"NaN\","
                + "\"Infinity\","
                + "\"-Infinity\""
                + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        assertEquals(-0.0, reader.nextDouble(), 0.0f);
        assertEquals(1.0, reader.nextDouble(), 0.0f);
        assertEquals(1.7976931348623157E308, reader.nextDouble(), 0.0f);
        assertEquals(4.9E-324, reader.nextDouble(), 0.0f);
        assertEquals(0.0, reader.nextDouble(), 0.0f);
        assertEquals(-0.5, reader.nextDouble(), 0.0f);
        assertEquals(2.2250738585072014E-308, reader.nextDouble(), 0.0f);
        assertEquals(3.141592653589793, reader.nextDouble(), 0.0f);
        assertEquals(2.718281828459045, reader.nextDouble(), 0.0f);
        assertEquals(1.0, reader.nextDouble(), 0.0f);
        assertEquals(11.0, reader.nextDouble(), 0.0f);
        assertTrue(Double.isNaN(reader.nextDouble()));
        assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0.0f);
        assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0.0f);
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testLenientDoubles() throws IOException {
        String json = "["
                + "011.0,"
                + "NaN,"
                + "NAN,"
                + "Infinity,"
                + "INFINITY,"
                + "-Infinity"
                + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(11.0, reader.nextDouble(), 0.0f);
        assertTrue(Double.isNaN(reader.nextDouble()));
        try {
            reader.nextDouble();
            fail();
        } catch (NumberFormatException expected) {
        }
        assertEquals("NAN", reader.nextString());
        assertEquals(Double.POSITIVE_INFINITY, reader.nextDouble(), 0.0f);
        try {
            reader.nextDouble();
            fail();
        } catch (NumberFormatException expected) {
        }
        assertEquals("INFINITY", reader.nextString());
        assertEquals(Double.NEGATIVE_INFINITY, reader.nextDouble(), 0.0f);
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testBufferBoundary() throws IOException {
        char[] pad = new char[READER_BUFFER_SIZE - 8];
        Arrays.fill(pad, '5');
        String json = "[\"" + new String(pad) + "\",33333]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        assertEquals(JsonToken.STRING, reader.peek());
        assertEquals(new String(pad), reader.nextString());
        assertEquals(JsonToken.NUMBER, reader.peek());
        assertEquals(33333, reader.nextInt());
    }

    @Test
    public void testTruncatedBufferBoundary() throws IOException {
        char[] pad = new char[READER_BUFFER_SIZE - 8];
        Arrays.fill(pad, '5');
        String json = "[\"" + new String(pad) + "\",33333";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(JsonToken.STRING, reader.peek());
        assertEquals(new String(pad), reader.nextString());
        assertEquals(JsonToken.NUMBER, reader.peek());
        assertEquals(33333, reader.nextInt());
        try {
            reader.endArray();
            fail();
        } catch (IOException e) {
        }
    }

    @Test
    public void testLongestSupportedNumericLiterals() throws IOException {
        verifyLongNumericLiterals(READER_BUFFER_SIZE - 1, JsonToken.NUMBER);
    }

    @Test
    public void testLongerNumericLiterals() throws IOException {
        verifyLongNumericLiterals(READER_BUFFER_SIZE, JsonToken.STRING);
    }

    private void verifyLongNumericLiterals(int length, JsonToken expectedToken) throws IOException {
        char[] longNumber = new char[length];
        Arrays.fill(longNumber, '9');
        longNumber[0] = '1';
        longNumber[1] = '.';

        String json = "[" + new String(longNumber) + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(expectedToken, reader.peek());
        assertEquals(2.0d, reader.nextDouble(), 0.0f);
        reader.endArray();
    }

    @Test
    public void testLongs() throws IOException {
        String json = "[0,0,0,"
                + "1,1,1,"
                + "-1,-1,-1,"
                + "-9223372036854775808,"
                + "9223372036854775807,"
                + "5.0,"
                + "1.0e2,"
                + "\"011\","
                + "\"5.0\","
                + "\"1.0e2\""
                + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        assertEquals(0L, reader.nextLong());
        assertEquals(0, reader.nextInt());
        assertEquals(0.0, reader.nextDouble(), 0.0f);
        assertEquals(1L, reader.nextLong());
        assertEquals(1, reader.nextInt());
        assertEquals(1.0, reader.nextDouble(), 0.0f);
        assertEquals(-1L, reader.nextLong());
        assertEquals(-1, reader.nextInt());
        assertEquals(-1.0, reader.nextDouble(), 0.0f);
        try {
            reader.nextInt();
            fail();
        } catch (NumberFormatException expected) {
        }
        assertEquals(Long.MIN_VALUE, reader.nextLong());
        try {
            reader.nextInt();
            fail();
        } catch (NumberFormatException expected) {
        }
        assertEquals(Long.MAX_VALUE, reader.nextLong());
        assertEquals(5, reader.nextLong());
        assertEquals(100, reader.nextLong());
        assertEquals(11, reader.nextLong());
        assertEquals(5, reader.nextLong());
        assertEquals(100, reader.nextLong());
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testHighPrecisionDouble_losesPrecision() throws IOException {
        // The presence of a fractional part forces us to use Double.parseDouble
        // instead of Long.parseLong (even though the fractional part is 0).
        //
        // A 52 bit mantissa isn't sufficient to precisely represent any of these
        // values, so we will lose some precision, thereby storing it as
        // ~(9.223372036854776E18). This value is then implicitly converted into
        // a long and is required by the JLS to be clamped to Long.MAX_VALUE since
        // it's larger than the largest long.
        String json = "["
                + "9223372036854775806.000,"  // Long.MAX_VALUE - 1
                + "9223372036854775807.000,"  // Long.MAX_VALUE
                + "9223372036854775808.000"   // Long.MAX_VALUE + 1
                + "]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        assertEquals(Long.MAX_VALUE, reader.nextLong());
        assertEquals(Long.MAX_VALUE, reader.nextLong());
        assertEquals(Long.MAX_VALUE, reader.nextLong());
        reader.endArray();
    }

    @Test
    public void testMatchingValidNumbers() throws IOException {
        String json = "[-1,99,-0,0,0e1,0e+1,0e-1,0E1,0E+1,0E-1,0.0,1.0,-1.0,1.0e0,1.0e+1,1.0e-1]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        for (int i = 0; i < 16; i++) {
            assertEquals(JsonToken.NUMBER, reader.peek());
            reader.nextDouble();
        }
        reader.endArray();
    }

    @Test
    public void testRecognizingInvalidNumbers() throws IOException {
        String json = "[-00,00,001,+1,1f,0x,0xf,0x0,0f1,0ee1,1..0,1e0.1,1.-01,1.+1,1.0x,1.0+]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        reader.beginArray();
        for (int i = 0; i < 16; i++) {
            assertEquals(JsonToken.STRING, reader.peek());
            reader.nextString();
        }
        reader.endArray();
    }

    @Test
    public void testNonFiniteDouble() throws IOException {
        String json = "[NaN]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        try {
            reader.nextDouble();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testNumberWithHexPrefix() throws IOException {
        String json = "[0x11]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        try {
            reader.nextLong();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testNumberWithOctalPrefix() throws IOException {
        String json = "[01]";
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        try {
            reader.nextInt();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testBooleans() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true,false]"));
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        assertEquals(false, reader.nextBoolean());
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testMixedCaseLiterals() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[True,TruE,False,FALSE,NULL,nulL]"));
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        assertEquals(true, reader.nextBoolean());
        assertEquals(false, reader.nextBoolean());
        assertEquals(false, reader.nextBoolean());
        reader.nextNull();
        reader.nextNull();
        reader.endArray();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testMissingValue() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\":}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        try {
            reader.nextString();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testPrematureEndOfInput() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\":true,"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        assertEquals(true, reader.nextBoolean());
        try {
            reader.nextName();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testPrematurelyClosed() throws IOException {
        try {
            JsonReader reader = new JsonReader(new StringReader("{\"a\":[]}"));
            reader.beginObject();
            reader.close();
            reader.nextName();
            fail();
        } catch (IllegalStateException expected) {
        }

        try {
            JsonReader reader = new JsonReader(new StringReader("{\"a\":[]}"));
            reader.close();
            reader.beginObject();
            fail();
        } catch (IllegalStateException expected) {
        }

        try {
            JsonReader reader = new JsonReader(new StringReader("{\"a\":true}"));
            reader.beginObject();
            reader.nextName();
            reader.peek();
            reader.close();
            reader.nextBoolean();
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testNextFailuresDoNotAdvance() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\":true}"));
        reader.beginObject();
        try {
            reader.nextString();
            fail();
        } catch (IllegalStateException expected) {
        }
        assertEquals("a", reader.nextName());
        try {
            reader.nextName();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.beginArray();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.endArray();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.beginObject();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.endObject();
            fail();
        } catch (IllegalStateException expected) {
        }
        assertEquals(true, reader.nextBoolean());
        try {
            reader.nextString();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.nextName();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.beginArray();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            reader.endArray();
            fail();
        } catch (IllegalStateException expected) {
        }
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
        reader.close();
    }

    @Test(expected=IllegalStateException.class)
    public void testStringNullIsNotNull() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[\"null\"]"));
        reader.beginArray();
        reader.nextNull();
    }

    @Test(expected=IllegalStateException.class)
    public void testNullLiteralIsNotAString() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[null]"));
        reader.beginArray();
        reader.nextString();
    }

    @Test
    public void testStrictNameValueSeparator() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\"=true}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("{\"a\"=>true}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientNameValueSeparator() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\"=true}"));
        reader.setLenient(true);
        reader.beginObject();
        assertEquals("a", reader.nextName());
        assertEquals(true, reader.nextBoolean());

        reader = new JsonReader(new StringReader("{\"a\"=>true}"));
        reader.setLenient(true);
        reader.beginObject();
        assertEquals("a", reader.nextName());
        assertEquals(true, reader.nextBoolean());
    }

    @Test
    public void testStrictComments() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[// comment \n true]"));
        reader.beginArray();
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("[# comment \n true]"));
        reader.beginArray();
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("[/* comment */ true]"));
        reader.beginArray();
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientComments() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[// comment \n true]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());

        reader = new JsonReader(new StringReader("[# comment \n true]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());

        reader = new JsonReader(new StringReader("[/* comment */ true]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
    }

    @Test
    public void testStrictUnquotedNames() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{a:true}"));
        reader.beginObject();
        try {
            reader.nextName();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientUnquotedNames() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{a:true}"));
        reader.setLenient(true);
        reader.beginObject();
        assertEquals("a", reader.nextName());
    }

    @Test
    public void testStrictSingleQuotedNames() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{'a':true}"));
        reader.beginObject();
        try {
            reader.nextName();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientSingleQuotedNames() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{'a':true}"));
        reader.setLenient(true);
        reader.beginObject();
        assertEquals("a", reader.nextName());
    }

    @Test(expected=MalformedJsonException.class)
    public void testStrictUnquotedStrings() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[a]"));
        reader.beginArray();
        reader.nextString();
    }

    @Test
    public void testLenientUnquotedStrings() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[a]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals("a", reader.nextString());
    }

    @Test
    public void testStrictSingleQuotedStrings() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("['a']"));
        reader.beginArray();
        try {
            reader.nextString();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientSingleQuotedStrings() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("['a']"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals("a", reader.nextString());
    }

    @Test
    public void testStrictSemicolonDelimitedArray() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true;true]"));
        reader.beginArray();
        try {
            reader.nextBoolean();
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientSemicolonDelimitedArray() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true;true]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        assertEquals(true, reader.nextBoolean());
    }

    @Test
    public void testStrictSemicolonDelimitedNameValuePair() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\":true;\"b\":true}"));
        reader.beginObject();
        assertEquals("a", reader.nextName());
        try {
            reader.nextBoolean();
            reader.nextName();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientSemicolonDelimitedNameValuePair() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("{\"a\":true;\"b\":true}"));
        reader.setLenient(true);
        reader.beginObject();
        assertEquals("a", reader.nextName());
        assertEquals(true, reader.nextBoolean());
        assertEquals("b", reader.nextName());
    }

    @Test
    public void testStrictUnnecessaryArraySeparators() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true,,true]"));
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        try {
            reader.nextNull();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("[,true]"));
        reader.beginArray();
        try {
            reader.nextNull();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("[true,]"));
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        try {
            reader.nextNull();
            fail();
        } catch (IOException expected) {
        }

        reader = new JsonReader(new StringReader("[,]"));
        reader.beginArray();
        try {
            reader.nextNull();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientUnnecessaryArraySeparators() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[true,,true]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        reader.nextNull();
        assertEquals(true, reader.nextBoolean());
        reader.endArray();

        reader = new JsonReader(new StringReader("[,true]"));
        reader.setLenient(true);
        reader.beginArray();
        reader.nextNull();
        assertEquals(true, reader.nextBoolean());
        reader.endArray();

        reader = new JsonReader(new StringReader("[true,]"));
        reader.setLenient(true);
        reader.beginArray();
        assertEquals(true, reader.nextBoolean());
        reader.nextNull();
        reader.endArray();

        reader = new JsonReader(new StringReader("[,]"));
        reader.setLenient(true);
        reader.beginArray();
        reader.nextNull();
        reader.nextNull();
        reader.endArray();
    }

    @Test
    public void testStrictMultipleTopLevelValues() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[] []"));
        reader.beginArray();
        reader.endArray();
        try {
            reader.peek();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientMultipleTopLevelValues() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[] true {}"));
        reader.setLenient(true);
        reader.beginArray();
        reader.endArray();
        assertEquals(true, reader.nextBoolean());
        reader.beginObject();
        reader.endObject();
        assertEquals(JsonToken.END_DOCUMENT, reader.peek());
    }

    @Test
    public void testStrictTopLevelValueType() {
        JsonReader reader = new JsonReader(new StringReader("true"));
        try {
            reader.nextBoolean();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testLenientTopLevelValueType() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("true"));
        reader.setLenient(true);
        assertEquals(true, reader.nextBoolean());
    }

    @Test
    public void testStrictNonExecutePrefix() {
        JsonReader reader = new JsonReader(new StringReader(")]}'\n []"));
        try {
            reader.beginArray();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testBomIgnoredAsFirstCharacterOfDocument() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("\ufeff[]"));
        reader.beginArray();
        reader.endArray();
    }

    @Test
    public void testBomForbiddenAsOtherCharacterInDocument() throws IOException {
        JsonReader reader = new JsonReader(new StringReader("[\ufeff]"));
        reader.beginArray();
        try {
            reader.endArray();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testFailWithPosition() throws IOException {
        verifyFailWithPosition("Expected literal value at line 6 column 3",
                "[\n\n\n\n\n0,}]");
    }

    @Test
    public void testFailWithPositionIsOffsetByBom() throws IOException {
        verifyFailWithPosition("Expected literal value at line 1 column 4",
                "\ufeff[0,}]");
    }

    @Test
    public void testFailWithPositionGreaterThanBufferSize() throws IOException {
        String spaces = repeat(' ', 8192);
        verifyFailWithPosition("Expected literal value at line 6 column 3",
                "[\n\n" + spaces + "\n\n\n0,}]");
    }

    private void verifyFailWithPosition(String message, String json) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.beginArray();
        reader.nextInt();
        try {
            reader.peek();
            fail();
        } catch (IOException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    private String repeat(char c, int count) {
        char[] array = new char[count];
        Arrays.fill(array, c);
        return new String(array);
    }
}
