/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CharSequenceTransformationTest {

    @Test
    public void testAllNullBuilder() {
        assertThrows(NullPointerException.class,
                () ->  new CharSequenceTransformation.Builder(null, null, null));
    }

    @Test
    public void testNullAutofillIdBuilder() {
        assertThrows(NullPointerException.class,
                () -> new CharSequenceTransformation.Builder(null, Pattern.compile(""), ""));
    }

    @Test
    public void testNullRegexBuilder() {
        assertThrows(NullPointerException.class,
                () -> new CharSequenceTransformation.Builder(new AutofillId(1), null, ""));
    }

    @Test
    public void testNullSubstBuilder() {
        assertThrows(NullPointerException.class,
                () -> new CharSequenceTransformation.Builder(new AutofillId(1), Pattern.compile(""),
                        null));
    }

    @Test
    public void testBadSubst() {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);
        AutofillId id3 = new AutofillId(3);
        AutofillId id4 = new AutofillId(4);

        CharSequenceTransformation.Builder b = new CharSequenceTransformation.Builder(id1,
                Pattern.compile("(.)"), "1=$1");

        // bad subst: The regex has no capture groups
        b.addField(id2, Pattern.compile("."), "2=$1");

        // bad subst: The regex does not have enough capture groups
        b.addField(id3, Pattern.compile("(.)"), "3=$2");

        b.addField(id4, Pattern.compile("(.)"), "4=$1");

        CharSequenceTransformation trans = b.build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id1)).thenReturn("a");
        when(finder.findByAutofillId(id2)).thenReturn("b");
        when(finder.findByAutofillId(id3)).thenReturn("c");
        when(finder.findByAutofillId(id4)).thenReturn("d");

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> trans.apply(finder, template, 0));

        // fail one, fail all
        verify(template, never()).setCharSequence(eq(0), any(), any());
    }

    @Test
    public void testUnknownField() throws Exception {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);
        AutofillId unknownId = new AutofillId(42);

        CharSequenceTransformation.Builder b = new CharSequenceTransformation.Builder(id1,
                Pattern.compile(".*"), "1");

        // bad subst: The field will not be found
        b.addField(unknownId, Pattern.compile(".*"), "unknown");

        b.addField(id2, Pattern.compile(".*"), "2");

        CharSequenceTransformation trans = b.build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id1)).thenReturn("1");
        when(finder.findByAutofillId(id2)).thenReturn("2");
        when(finder.findByAutofillId(unknownId)).thenReturn(null);

        trans.apply(finder, template, 0);

        // if a view cannot be found, nothing is not, not even partial results
        verify(template, never()).setCharSequence(eq(0), any(), any());
    }

    @Test
    public void testCreditCardObfuscator() throws Exception {
        AutofillId creditCardFieldId = new AutofillId(1);
        CharSequenceTransformation trans = new CharSequenceTransformation
                .Builder(creditCardFieldId,
                        Pattern.compile("^\\s*\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?(\\d{4})\\s*$"),
                        "...$1")
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("1234 5678 9012 3456");

        trans.apply(finder, template, 0);

        verify(template).setCharSequence(eq(0), any(), argThat(new CharSequenceMatcher("...3456")));
    }

    @Test
    public void testReplaceAllByOne() throws Exception {
        AutofillId id = new AutofillId(1);
        CharSequenceTransformation trans = new CharSequenceTransformation
                .Builder(id, Pattern.compile("."), "*")
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("four");

        trans.apply(finder, template, 0);

        verify(template).setCharSequence(eq(0), any(), argThat(new CharSequenceMatcher("****")));
    }

    @Test
    public void testPartialMatchIsIgnored() throws Exception {
        AutofillId id = new AutofillId(1);
        CharSequenceTransformation trans = new CharSequenceTransformation
                .Builder(id, Pattern.compile("^MATCH$"), "*")
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("preMATCHpost");

        trans.apply(finder, template, 0);

        verify(template, never()).setCharSequence(eq(0), any(), any());
    }

    @Test
    public void userNameObfuscator() throws Exception {
        AutofillId userNameFieldId = new AutofillId(1);
        AutofillId passwordFieldId = new AutofillId(2);
        CharSequenceTransformation trans = new CharSequenceTransformation
                .Builder(userNameFieldId, Pattern.compile("(.*)"), "$1")
                .addField(passwordFieldId, Pattern.compile(".*(..)$"), "/..$1")
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(userNameFieldId)).thenReturn("myUserName");
        when(finder.findByAutofillId(passwordFieldId)).thenReturn("myPassword");

        trans.apply(finder, template, 0);

        verify(template).setCharSequence(eq(0), any(),
                argThat(new CharSequenceMatcher("myUserName/..rd")));
    }

    @Test
    public void testMismatch() throws Exception {
        AutofillId id1 = new AutofillId(1);
        CharSequenceTransformation.Builder b = new CharSequenceTransformation.Builder(id1,
                Pattern.compile("Who are you?"), "1");

        CharSequenceTransformation trans = b.build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id1)).thenReturn("I'm Batman!");

        trans.apply(finder, template, 0);

        // If the match fails, the view should not change.
        verify(template, never()).setCharSequence(eq(0), any(), any());
    }

    static class CharSequenceMatcher implements ArgumentMatcher<CharSequence> {
        private final CharSequence mExpected;

        public CharSequenceMatcher(CharSequence expected) {
            mExpected = expected;
        }

        @Override
        public boolean matches(CharSequence actual) {
            return actual.toString().equals(mExpected.toString());
        }
    }
}
