/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.util.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.util.Rfc822Token;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Rfc822Token}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class Rfc822TokenTest {
    @Test
    public void testConstructor() {
        final String name = "John Doe";
        final String address = "jdoe@example.net";
        final String comment = "work";
        Rfc822Token rfc822Token1 = new Rfc822Token(name, address, comment);
        assertEquals(name, rfc822Token1.getName());
        assertEquals(address, rfc822Token1.getAddress());
        assertEquals(comment, rfc822Token1.getComment());

        Rfc822Token rfc822Token2 = new Rfc822Token(null, address, comment);
        assertNull(rfc822Token2.getName());
        assertEquals(address, rfc822Token2.getAddress());
        assertEquals(comment, rfc822Token2.getComment());

        Rfc822Token rfc822Token3 = new Rfc822Token(name, null, comment);
        assertEquals(name, rfc822Token3.getName());
        assertNull(rfc822Token3.getAddress());
        assertEquals(comment, rfc822Token3.getComment());

        Rfc822Token rfc822Token4 = new Rfc822Token(name, address, null);
        assertEquals(name, rfc822Token4.getName());
        assertEquals(address, rfc822Token4.getAddress());
        assertNull(rfc822Token4.getComment());
    }

    @Test
    public void testAccessName() {
        String name = "John Doe";
        final String address = "jdoe@example.net";
        final String comment = "work";
        Rfc822Token rfc822Token = new Rfc822Token(name, address, comment);
        assertEquals(name, rfc822Token.getName());

        name = "Ann Lee";
        rfc822Token.setName(name);
        assertEquals(name, rfc822Token.getName());

        name = "Charles Hanson";
        rfc822Token.setName(name);
        assertEquals(name, rfc822Token.getName());

        rfc822Token.setName(null);
        assertNull(rfc822Token.getName());
    }

    @Test
    public void testQuoteComment() {
        assertEquals("work", Rfc822Token.quoteComment("work"));

        assertEquals("\\\\\\(work\\)", Rfc822Token.quoteComment("\\(work)"));
    }

    @Test(expected=NullPointerException.class)
    public void testQuoteCommentNull() {
        Rfc822Token.quoteComment(null);
    }

    @Test
    public void testAccessComment() {
        final String name = "John Doe";
        final String address = "jdoe@example.net";
        String comment = "work";
        Rfc822Token rfc822Token = new Rfc822Token(name, address, comment);
        assertEquals(comment, rfc822Token.getComment());

        comment = "secret";
        rfc822Token.setComment(comment);
        assertEquals(comment, rfc822Token.getComment());

        comment = "";
        rfc822Token.setComment(comment);
        assertEquals(comment, rfc822Token.getComment());

        rfc822Token.setComment(null);
        assertNull(rfc822Token.getComment());
    }

    @Test
    public void testAccessAddress() {
        final String name = "John Doe";
        String address = "jdoe@example.net";
        final String comment = "work";
        Rfc822Token rfc822Token = new Rfc822Token(name, address, comment);
        assertEquals(address, rfc822Token.getAddress());

        address = "johndoe@example.com";
        rfc822Token.setAddress(address);
        assertEquals(address, rfc822Token.getAddress());

        address = "";
        rfc822Token.setAddress(address);
        assertEquals(address, rfc822Token.getAddress());

        rfc822Token.setAddress(null);
        assertNull(rfc822Token.getAddress());
    }

    @Test
    public void testToString() {
        Rfc822Token rfc822Token1 = new Rfc822Token("John Doe", "jdoe@example.net", "work");
        assertEquals("John Doe (work) <jdoe@example.net>", rfc822Token1.toString());

        Rfc822Token rfc822Token2 = new Rfc822Token("\"John Doe\"",
                "\\jdoe@example.net", "\\(work)");
        assertEquals("\"\\\"John Doe\\\"\" (\\\\\\(work\\)) <\\jdoe@example.net>",
                rfc822Token2.toString());

        Rfc822Token rfc822Token3 = new Rfc822Token(null, "jdoe@example.net", "");
        assertEquals("<jdoe@example.net>", rfc822Token3.toString());

        Rfc822Token rfc822Token4 = new Rfc822Token("John Doe", null, "work");
        assertEquals("John Doe (work) ", rfc822Token4.toString());

        Rfc822Token rfc822Token5 = new Rfc822Token("John Doe", "jdoe@example.net", null);
        assertEquals("John Doe <jdoe@example.net>", rfc822Token5.toString());

        Rfc822Token rfc822Token6 = new Rfc822Token(null, null, null);
        assertEquals("", rfc822Token6.toString());
    }

    @Test
    public void testQuoteNameIfNecessary() {
        assertEquals("UPPERlower space 0123456789",
                Rfc822Token.quoteNameIfNecessary("UPPERlower space 0123456789"));
        assertEquals("\"jdoe@example.net\"", Rfc822Token.quoteNameIfNecessary("jdoe@example.net"));
        assertEquals("\"*name\"", Rfc822Token.quoteNameIfNecessary("*name"));

        assertEquals("", Rfc822Token.quoteNameIfNecessary(""));
    }

    @Test(expected=NullPointerException.class)
    public void testQuoteNameIfNecessaryNull() {
        Rfc822Token.quoteNameIfNecessary(null);
    }

    @Test
    public void testQuoteName() {
        assertEquals("John Doe", Rfc822Token.quoteName("John Doe"));
        assertEquals("\\\"John Doe\\\"", Rfc822Token.quoteName("\"John Doe\""));
        assertEquals("\\\\\\\"John Doe\\\"", Rfc822Token.quoteName("\\\"John Doe\""));
    }

    @Test(expected=NullPointerException.class)
    public void testQuoteNameNull() {
        Rfc822Token.quoteName(null);
    }
}
