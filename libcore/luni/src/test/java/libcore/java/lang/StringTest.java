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

package libcore.java.lang;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Locale;
import junit.framework.TestCase;

public class StringTest extends TestCase {
    public void testIsEmpty() {
        assertTrue("".isEmpty());
        assertFalse("x".isEmpty());
    }

    // The evil decoder keeps hold of the CharBuffer it wrote to.
    private static final class EvilCharsetDecoder extends CharsetDecoder {
        private static char[] chars;
        public EvilCharsetDecoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            chars = out.array();
            int inLength = in.remaining();
            for (int i = 0; i < inLength; ++i) {
                in.put((byte) 'X');
                out.put('Y');
            }
            return CoderResult.UNDERFLOW;
        }
        public static void corrupt() {
            for (int i = 0; i < chars.length; ++i) {
                chars[i] = '$';
            }
        }
    }

    // The evil encoder tries to write to the CharBuffer it was given to
    // read from.
    private static final class EvilCharsetEncoder extends CharsetEncoder {
        public EvilCharsetEncoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }
        protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
            int inLength = in.remaining();
            for (int i = 0; i < inLength; ++i) {
                in.put('x');
                out.put((byte) 'y');
            }
            return CoderResult.UNDERFLOW;
        }
    }

    private static final Charset EVIL_CHARSET = new Charset("evil", null) {
        public boolean contains(Charset charset) { return false; }
        public CharsetEncoder newEncoder() { return new EvilCharsetEncoder(this); }
        public CharsetDecoder newDecoder() { return new EvilCharsetDecoder(this); }
    };

    public void testGetBytes_MaliciousCharset() {
        try {
            String s = "hi";
            // Check that our encoder can't write to the input CharBuffer
            // it was given.
            s.getBytes(EVIL_CHARSET);
            fail(); // We shouldn't have got here!
        } catch (ReadOnlyBufferException expected) {
            // We caught you trying to be naughty!
        }
    }

    public void testString_BII() throws Exception {
        byte[] bytes = "xa\u0666bx".getBytes("UTF-8");
        assertEquals("a\u0666b", new String(bytes, 1, bytes.length - 2));
    }

    public void testString_BIIString() throws Exception {
        byte[] bytes = "xa\u0666bx".getBytes("UTF-8");
        assertEquals("a\u0666b", new String(bytes, 1, bytes.length - 2, "UTF-8"));
    }

    public void testString_BIICharset() throws Exception {
        byte[] bytes = "xa\u0666bx".getBytes("UTF-8");
        assertEquals("a\u0666b", new String(bytes, 1, bytes.length - 2, Charset.forName("UTF-8")));
    }

    public void testString_BCharset() throws Exception {
        byte[] bytes = "a\u0666b".getBytes("UTF-8");
        assertEquals("a\u0666b", new String(bytes, Charset.forName("UTF-8")));
    }

    public void testStringFromCharset_MaliciousCharset() {
        Charset cs = EVIL_CHARSET;
        byte[] bytes = new byte[] {(byte) 'h', (byte) 'i'};
        final String result = new String(bytes, cs);
        assertEquals("YY", result); // (Our decoder always outputs 'Y's.)
        // Check that even if the decoder messes with the output CharBuffer
        // after we've created a string from it, it doesn't affect the string.
        EvilCharsetDecoder.corrupt();
        assertEquals("YY", result);
    }

    public void test_getBytes_bad() throws Exception {
        // Check that we use '?' as the replacement byte for invalid characters.
        assertEquals("[97, 63, 98]", Arrays.toString("a\u0666b".getBytes("US-ASCII")));
        assertEquals("[97, 63, 98]", Arrays.toString("a\u0666b".getBytes(Charset.forName("US-ASCII"))));
    }

    public void test_getBytes_UTF_8() {
        // We have a fast path implementation of String.getBytes for UTF-8.
        Charset cs = Charset.forName("UTF-8");

        // Test the empty string.
        assertEquals("[]", Arrays.toString("".getBytes(cs)));

        // Test one-byte characters.
        assertEquals("[0]", Arrays.toString("\u0000".getBytes(cs)));
        assertEquals("[127]", Arrays.toString("\u007f".getBytes(cs)));
        assertEquals("[104, 105]", Arrays.toString("hi".getBytes(cs)));

        // Test two-byte characters.
        assertEquals("[-62, -128]", Arrays.toString("\u0080".getBytes(cs)));
        assertEquals("[-39, -90]", Arrays.toString("\u0666".getBytes(cs)));
        assertEquals("[-33, -65]", Arrays.toString("\u07ff".getBytes(cs)));
        assertEquals("[104, -39, -90, 105]", Arrays.toString("h\u0666i".getBytes(cs)));

        // Test three-byte characters.
        assertEquals("[-32, -96, -128]", Arrays.toString("\u0800".getBytes(cs)));
        assertEquals("[-31, -120, -76]", Arrays.toString("\u1234".getBytes(cs)));
        assertEquals("[-17, -65, -65]", Arrays.toString("\uffff".getBytes(cs)));
        assertEquals("[104, -31, -120, -76, 105]", Arrays.toString("h\u1234i".getBytes(cs)));

        // Test supplementary characters.
        // Minimum supplementary character: U+10000
        assertEquals("[-16, -112, -128, -128]", Arrays.toString("\ud800\udc00".getBytes(cs)));
        // Random supplementary character: U+10381 Ugaritic letter beta
        assertEquals("[-16, -112, -114, -127]", Arrays.toString("\ud800\udf81".getBytes(cs)));
        // Maximum supplementary character: U+10FFFF
        assertEquals("[-12, -113, -65, -65]", Arrays.toString("\udbff\udfff".getBytes(cs)));
        // A high surrogate at end of string is an error replaced with '?'.
        assertEquals("[104, 63]", Arrays.toString("h\ud800".getBytes(cs)));
        // A high surrogate not followed by a low surrogate is an error replaced with '?'.
        assertEquals("[104, 63, 105]", Arrays.toString("h\ud800i".getBytes(cs)));
    }

    public void test_new_String_bad() throws Exception {
        // Check that we use U+FFFD as the replacement string for invalid bytes.
        assertEquals("a\ufffdb", new String(new byte[] { 97, -2, 98 }, "US-ASCII"));
        assertEquals("a\ufffdb", new String(new byte[] { 97, -2, 98 }, Charset.forName("US-ASCII")));
    }

    /**

     * Test that strings interned manually and then later loaded as literals
     * maintain reference equality. http://b/3098960
     */
    public void testInternBeforeLiteralIsLoaded() throws Exception{
        String programmatic = Arrays.asList("5058", "9962", "1563", "5744").toString().intern();
        String literal = (String) Class.forName("libcore.java.lang.StringTest$HasLiteral")
                .getDeclaredField("literal").get(null);
        assertEquals(System.identityHashCode(programmatic), System.identityHashCode(literal));
        assertSame(programmatic, literal);
    }

    static class HasLiteral {
        static String literal = "[5058, 9962, 1563, 5744]";
    }

    private static final String COMBINING_DOT_ABOVE = "\u0307";
    private static final String LATIN_CAPITAL_I = "I";
    private static final String LATIN_CAPITAL_I_WITH_DOT_ABOVE = "\u0130";
    private static final String LATIN_SMALL_I = "i";
    private static final String LATIN_SMALL_DOTLESS_I = "\u0131";

    private static final String[] LATIN_I_VARIANTS = {
        LATIN_SMALL_I,
        LATIN_SMALL_DOTLESS_I,
        LATIN_CAPITAL_I,
        LATIN_CAPITAL_I_WITH_DOT_ABOVE,
    };

    public void testCaseMapping_tr_TR() {
        Locale tr_TR = new Locale("tr", "TR");
        assertEquals(LATIN_SMALL_I, LATIN_SMALL_I.toLowerCase(tr_TR));
        assertEquals(LATIN_SMALL_I, LATIN_CAPITAL_I_WITH_DOT_ABOVE.toLowerCase(tr_TR));
        assertEquals(LATIN_SMALL_DOTLESS_I, LATIN_SMALL_DOTLESS_I.toLowerCase(tr_TR));

        assertEquals(LATIN_CAPITAL_I, LATIN_CAPITAL_I.toUpperCase(tr_TR));
        assertEquals(LATIN_CAPITAL_I_WITH_DOT_ABOVE, LATIN_CAPITAL_I_WITH_DOT_ABOVE.toUpperCase(tr_TR));
        assertEquals(LATIN_CAPITAL_I_WITH_DOT_ABOVE, LATIN_SMALL_I.toUpperCase(tr_TR));

        assertEquals(LATIN_CAPITAL_I, LATIN_SMALL_DOTLESS_I.toUpperCase(tr_TR));
        assertEquals(LATIN_SMALL_DOTLESS_I, LATIN_CAPITAL_I.toLowerCase(tr_TR));
    }

    public void testCaseMapping_en_US() {
        Locale en_US = new Locale("en", "US");
        assertEquals(LATIN_CAPITAL_I, LATIN_SMALL_I.toUpperCase(en_US));
        assertEquals(LATIN_CAPITAL_I, LATIN_CAPITAL_I.toUpperCase(en_US));
        assertEquals(LATIN_CAPITAL_I_WITH_DOT_ABOVE, LATIN_CAPITAL_I_WITH_DOT_ABOVE.toUpperCase(en_US));

        assertEquals(LATIN_SMALL_I, LATIN_SMALL_I.toLowerCase(en_US));
        assertEquals(LATIN_SMALL_I, LATIN_CAPITAL_I.toLowerCase(en_US));
        assertEquals(LATIN_SMALL_DOTLESS_I, LATIN_SMALL_DOTLESS_I.toLowerCase(en_US));

        assertEquals(LATIN_CAPITAL_I, LATIN_SMALL_DOTLESS_I.toUpperCase(en_US));
        // http://b/3325799: the RI fails this because it's using an obsolete version of the Unicode rules.
        // Android correctly preserves canonical equivalence. (See the separate test for tr_TR.)
        assertEquals(LATIN_SMALL_I + COMBINING_DOT_ABOVE, LATIN_CAPITAL_I_WITH_DOT_ABOVE.toLowerCase(en_US));
    }

    public void testCaseMapping_el() {
        Locale el_GR = new Locale("el", "GR");
        assertEquals("ΟΔΟΣ ΟΔΟΣ ΣΟ ΣΟ OΣ ΟΣ Σ ΕΞ", "ΟΔΌΣ Οδός Σο ΣΟ oΣ ΟΣ σ ἕξ".toUpperCase(el_GR));
        assertEquals("ΟΔΟΣ ΟΔΟΣ ΣΟ ΣΟ OΣ ΟΣ Σ ΕΞ", "ΟΔΌΣ Οδός Σο ΣΟ oΣ ΟΣ σ ἕξ".toUpperCase(el_GR));
        assertEquals("ΟΔΟΣ ΟΔΟΣ ΣΟ ΣΟ OΣ ΟΣ Σ ΕΞ", "ΟΔΌΣ Οδός Σο ΣΟ oΣ ΟΣ σ ἕξ".toUpperCase(el_GR));

        Locale en_US = new Locale("en", "US");
        assertEquals("ΟΔΌΣ ΟΔΌΣ ΣΟ ΣΟ OΣ ΟΣ Σ ἝΞ", "ΟΔΌΣ Οδός Σο ΣΟ oΣ ΟΣ σ ἕξ".toUpperCase(en_US));
    }

    public void testEqualsIgnoreCase_tr_TR() {
        testEqualsIgnoreCase(new Locale("tr", "TR"));
    }

    public void testEqualsIgnoreCase_en_US() {
        testEqualsIgnoreCase(new Locale("en", "US"));
    }

    /**
     * String.equalsIgnoreCase should not depend on the locale.
     */
    private void testEqualsIgnoreCase(Locale locale) {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            for (String a : LATIN_I_VARIANTS) {
                for (String b : LATIN_I_VARIANTS) {
                    if (!a.equalsIgnoreCase(b)) {
                        fail("Expected " + a + " to equal " + b + " in " +  locale);
                    }
                }
            }
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    public void testRegionMatches_ignoreCase_en_US() {
        testRegionMatches_ignoreCase(new Locale("en", "US"));
    }

    public void testRegionMatches_ignoreCase_tr_TR() {
        testRegionMatches_ignoreCase(new Locale("tr", "TR"));
    }

    private void testRegionMatches_ignoreCase(Locale locale) {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            for (String a : LATIN_I_VARIANTS) {
                for (String b : LATIN_I_VARIANTS) {
                    if (!a.regionMatches(true, 0, b, 0, b.length())) {
                        fail("Expected " + a + " to equal " + b + " in " +  locale);
                    }
                }
            }
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    // http://code.google.com/p/android/issues/detail?id=15266
    public void test_replaceAll() throws Exception {
        assertEquals("project_Id", "projectId".replaceAll("(?!^)(\\p{Upper})(?!$)", "_$1"));
    }

    // https://code.google.com/p/android/issues/detail?id=23831
    public void test_23831() throws Exception {
        byte[] bytes = { (byte) 0xf5, (byte) 0xa9, (byte) 0xea, (byte) 0x21 };
        String expected = "\ufffd\ufffd\u0021";

        // Since we use icu4c for CharsetDecoder...
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        assertEquals(expected, decoder.decode(ByteBuffer.wrap(bytes)).toString());

        // Our fast-path code in String should behave the same...
        assertEquals(expected, new String(bytes, "UTF-8"));
    }

    // https://code.google.com/p/android/issues/detail?id=55129
    public void test_55129() throws Exception {
        assertEquals("-h-e-l-l-o- -w-o-r-l-d-", "hello world".replace("", "-"));
        assertEquals("-w-o-r-l-d-", "hello world".substring(6).replace("", "-"));
        assertEquals("-*-w-*-o-*-r-*-l-*-d-*-", "hello world".substring(6).replace("", "-*-"));

        // Replace on an empty string with an empty target should insert the pattern
        // precisely once.
        assertEquals("", "".replace("", ""));
        assertEquals("food", "".replace("", "food"));
    }

    public void test_replace() {
        // Replace on an empty string is a no-op.
        assertEquals("", "".replace("foo", "bar"));
        // Replace on a string which doesn't contain the target sequence is a no-op.
        assertEquals("baz", "baz".replace("foo", "bar"));
        // Test that we iterate forward on the string.
        assertEquals("mmmba", "bababa".replace("baba", "mmm"));
        // Test replacements at the end of the string.
        assertEquals("foodie", "foolish".replace("lish", "die"));
        // Test a string that has multiple replacements.
        assertEquals("hahahaha", "kkkk".replace("k", "ha"));
    }

    public void test_String_getBytes() throws Exception {
        // http://b/11571917
        assertEquals("[-126, -96]", Arrays.toString("あ".getBytes("Shift_JIS")));
        assertEquals("[-126, -87]", Arrays.toString("か".getBytes("Shift_JIS")));
        assertEquals("[-105, 67]", Arrays.toString("佑".getBytes("Shift_JIS")));
        assertEquals("[36]", Arrays.toString("$".getBytes("Shift_JIS")));
        assertEquals("[-29, -127, -117]", Arrays.toString("か".getBytes("UTF-8")));

        // http://b/11639117
        assertEquals("[-79, -72, -70, -48]", Arrays.toString("구분".getBytes("EUC-KR")));


        // https://code.google.com/p/android/issues/detail?id=63188
        assertEquals("[-77, -10, -64, -76, -63, -53]", Arrays.toString("出来了".getBytes("gbk")));
        assertEquals("[-77, -10, -64, -76]", Arrays.toString("出来".getBytes("gbk")));
        assertEquals("[-77, -10]", Arrays.toString("出".getBytes("gbk")));
    }

    public void test_compareTo() throws Exception {
        // For strings where a character differs, the result is
        // the difference between the characters.
        assertEquals(-1, "a".compareTo("b"));
        assertEquals(-2, "a".compareTo("c"));
        assertEquals(1, "b".compareTo("a"));
        assertEquals(2, "c".compareTo("a"));

        // For strings where the characters match up to the length of the shorter,
        // the result is the difference between the strings' lengths.
        assertEquals(0, "a".compareTo("a"));
        assertEquals(-1, "a".compareTo("aa"));
        assertEquals(-1, "a".compareTo("az"));
        assertEquals(-2, "a".compareTo("aaa"));
        assertEquals(-2, "a".compareTo("azz"));
        assertEquals(-3, "a".compareTo("aaaa"));
        assertEquals(-3, "a".compareTo("azzz"));
        assertEquals(0, "a".compareTo("a"));
        assertEquals(1, "aa".compareTo("a"));
        assertEquals(1, "az".compareTo("a"));
        assertEquals(2, "aaa".compareTo("a"));
        assertEquals(2, "azz".compareTo("a"));
        assertEquals(3, "aaaa".compareTo("a"));
        assertEquals(3, "azzz".compareTo("a"));
    }

    public void test_compareToIgnoreCase() throws Exception {
        // For strings where a character differs, the result is
        // the difference between the characters.
        assertEquals(-1, "a".compareToIgnoreCase("b"));
        assertEquals(-1, "a".compareToIgnoreCase("B"));
        assertEquals(-2, "a".compareToIgnoreCase("c"));
        assertEquals(-2, "a".compareToIgnoreCase("C"));
        assertEquals(1, "b".compareToIgnoreCase("a"));
        assertEquals(1, "B".compareToIgnoreCase("a"));
        assertEquals(2, "c".compareToIgnoreCase("a"));
        assertEquals(2, "C".compareToIgnoreCase("a"));

        // For strings where the characters match up to the length of the shorter,
        // the result is the difference between the strings' lengths.
        assertEquals(0, "a".compareToIgnoreCase("a"));
        assertEquals(0, "a".compareToIgnoreCase("A"));
        assertEquals(0, "A".compareToIgnoreCase("a"));
        assertEquals(0, "A".compareToIgnoreCase("A"));
        assertEquals(-1, "a".compareToIgnoreCase("aa"));
        assertEquals(-1, "a".compareToIgnoreCase("aA"));
        assertEquals(-1, "a".compareToIgnoreCase("Aa"));
        assertEquals(-1, "a".compareToIgnoreCase("az"));
        assertEquals(-1, "a".compareToIgnoreCase("aZ"));
        assertEquals(-2, "a".compareToIgnoreCase("aaa"));
        assertEquals(-2, "a".compareToIgnoreCase("AAA"));
        assertEquals(-2, "a".compareToIgnoreCase("azz"));
        assertEquals(-2, "a".compareToIgnoreCase("AZZ"));
        assertEquals(-3, "a".compareToIgnoreCase("aaaa"));
        assertEquals(-3, "a".compareToIgnoreCase("AAAA"));
        assertEquals(-3, "a".compareToIgnoreCase("azzz"));
        assertEquals(-3, "a".compareToIgnoreCase("AZZZ"));
        assertEquals(1, "aa".compareToIgnoreCase("a"));
        assertEquals(1, "aA".compareToIgnoreCase("a"));
        assertEquals(1, "Aa".compareToIgnoreCase("a"));
        assertEquals(1, "az".compareToIgnoreCase("a"));
        assertEquals(2, "aaa".compareToIgnoreCase("a"));
        assertEquals(2, "azz".compareToIgnoreCase("a"));
        assertEquals(3, "aaaa".compareToIgnoreCase("a"));
        assertEquals(3, "azzz".compareToIgnoreCase("a"));
    }

    // http://b/25943996
    public void testSplit_trailingSeparators() {
        String[] splits = "test\0message\0\0\0\0\0\0".split("\0", -1);
        assertEquals("test", splits[0]);
        assertEquals("message", splits[1]);
        assertEquals("", splits[2]);
        assertEquals("", splits[3]);
        assertEquals("", splits[4]);
        assertEquals("", splits[5]);
        assertEquals("", splits[6]);
        assertEquals("", splits[7]);
    }

    // http://b/26126818
    public void testCodePointCount() {
        String hello = "Hello, fools";

        assertEquals(5, hello.codePointCount(0, 5));
        assertEquals(7, hello.codePointCount(5, 12));
        assertEquals(2, hello.codePointCount(10, 12));
    }

    // http://b/26444984
    public void testGetCharsOverflow() {
        int srcBegin = Integer.MAX_VALUE; //2147483647
        int srcEnd = srcBegin + 10;  //-2147483639
        try {
            // The output array size must be larger than |srcEnd - srcBegin|.
            "yes".getChars(srcBegin, srcEnd, new char[256], 0);
            fail();
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // http://b/28998511
    public void testGetCharsBoundsChecks() {
        // This is the explicit case from the bug: dstBegin == srcEnd - srcBegin
        assertGetCharsThrowsAIOOBException("abcd", 0, 4, new char[0], -4);

        // Some valid cases.
        char[] dst = new char[1];
        "abcd".getChars(0, 1, dst, 0);
        assertEquals('a', dst[0]);
        "abcd".getChars(3, 4, dst, 0);
        assertEquals('d', dst[0]);
        dst = new char[4];
        "abcd".getChars(0, 4, dst, 0);
        assertTrue(Arrays.equals("abcd".toCharArray(), dst));

        // Zero length src.
        "abcd".getChars(0, 0, new char[0], 0);  // dstBegin == 0 is ok if copying zero chars
        "abcd".getChars(0, 0, new char[1], 1);  // dstBegin == 1 is ok if copying zero chars
        "".getChars(0, 0, new char[0], 0);
        "abcd".getChars(1, 1, new char[1], 0);
        "abcd".getChars(1, 1, new char[1], 1);

        // Valid src args, invalid dst args.
        assertGetCharsThrowsAIOOBException("abcd", 3, 4, new char[1], 1); // Out of range dstBegin
        assertGetCharsThrowsAIOOBException("abcd", 0, 4, new char[3], 0); // Small dst
        assertGetCharsThrowsAIOOBException("abcd", 0, 4, new char[4], -1); // Negative dstBegin

        // dstBegin + (srcEnd - srcBegin) -> integer overflow OR dstBegin >= dst.length
        assertGetCharsThrowsAIOOBException("abcd", 0, 4, new char[4], Integer.MAX_VALUE - 1);

        // Invalid src args, valid dst args.
        assertGetCharsThrowsSIOOBException("abcd", 2, 1, new char[4], 0); // srcBegin > srcEnd
        assertGetCharsThrowsSIOOBException("abcd", -1, 3, new char[4], 0); // Negative srcBegin
        assertGetCharsThrowsSIOOBException("abcd", 0, 5, new char[4], 0); // Out of range srcEnd
        assertGetCharsThrowsSIOOBException("abcd", 0, -1, new char[4], 0); // Negative srcEnd

        // Valid src args, invalid dst args.
        assertGetCharsThrowsAIOOBException("abcd", 0, 4, new char[4], 1); // Bad dstBegin

        // Zero length src copy, invalid dst args.
        assertGetCharsThrowsAIOOBException("abcd", 0, 0, new char[4], -1); // Negative dstBegin
        assertGetCharsThrowsAIOOBException("abcd", 0, 0, new char[0], 1); // Out of range dstBegin
        assertGetCharsThrowsAIOOBException("abcd", 0, 0, new char[1], 2);  // Out of range dstBegin
        assertGetCharsThrowsAIOOBException("abcd", 0, 0, new char[4], 5); // Out of range dstBegin
    }

    private static void assertGetCharsThrowsAIOOBException(String s, int srcBegin, int srcEnd,
            char[] dst, int dstBegin) {
        try {
            s.getChars(srcBegin, srcEnd, dst, dstBegin);
            fail();
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    private static void assertGetCharsThrowsSIOOBException(String s, int srcBegin, int srcEnd,
            char[] dst, int dstBegin) {
        try {
            s.getChars(srcBegin, srcEnd, dst, dstBegin);
            fail();
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    public void testChars() {
        String s = "Hello\n\tworld";
        int[] expected = new int[s.length()];
        for (int i = 0; i < s.length(); ++i) {
            expected[i] = (int) s.charAt(i);
        }
        assertTrue(Arrays.equals(expected, s.chars().toArray()));

        // Surrogate code point
        char high = '\uD83D', low = '\uDE02';
        String surrogateCP = new String(new char[]{high, low, low});
        assertTrue(Arrays.equals(new int[]{high, low, low}, surrogateCP.chars().toArray()));
    }

    public void testCodePoints() {
        String s = "Hello\n\tworld";
        int[] expected = new int[s.length()];
        for (int i = 0; i < s.length(); ++i) {
            expected[i] = (int) s.charAt(i);
        }
        assertTrue(Arrays.equals(expected, s.codePoints().toArray()));

        // Surrogate code point
        char high = '\uD83D', low = '\uDE02';
        String surrogateCP = new String(new char[]{high, low, low, '0'});
        assertEquals(Character.toCodePoint(high, low), surrogateCP.codePoints().toArray()[0]);
        assertEquals((int) low, surrogateCP.codePoints().toArray()[1]); // Unmatched surrogate.
        assertEquals((int) '0', surrogateCP.codePoints().toArray()[2]);
    }

    public void testJoin_CharSequenceArray() {
        assertEquals("", String.join("-"));
        assertEquals("", String.join("-", ""));
        assertEquals("foo", String.join("-", "foo"));
        assertEquals("foo---bar---boo", String.join("---", "foo", "bar", "boo"));
        assertEquals("foobarboo", String.join("", "foo", "bar", "boo"));
        assertEquals("null-null", String.join("-", null, null));
        assertEquals("¯\\_(ツ)_/¯", String.join("(ツ)", "¯\\_", "_/¯"));
    }

    public void testJoin_CharSequenceArray_NPE() {
        try {
            String.join(null, "foo", "bar");
            fail();
        } catch (NullPointerException expected) {}
    }

    public void testJoin_Iterable() {
        ArrayList<String> iterable = new ArrayList<>();
        assertEquals("", String.join("-", iterable));

        iterable.add("foo");
        assertEquals("foo", String.join("-", iterable));

        iterable.add("bar");
        assertEquals("foo...bar", String.join("...", iterable));

        iterable.add("foo");
        assertEquals("foo-bar-foo", String.join("-", iterable));
        assertEquals("foobarfoo", String.join("", iterable));
    }

    public void testJoin_Iterable_NPE() {
        try {
            String.join(null, new ArrayList<String>());
            fail();
        } catch (NullPointerException expected) {}

        try {
            String.join("-", (Iterable<String>)null);
            fail();
        } catch (NullPointerException expected) {}
    }
}
