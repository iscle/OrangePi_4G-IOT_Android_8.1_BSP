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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyChar;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.LoginFilter;
import android.text.LoginFilter.UsernameFilterGeneric;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LoginFilterTest {
    @Test
    public void testFilter() {
        CharSequence result;
        LoginFilter loginFilter = spy(new UsernameFilterGeneric());
        Spanned dest1 = new SpannedString("dest_without_invalid_char");
        Spanned dest2 = new SpannedString("&*dest_with_invalid_char#$");
        String source1 = "source_without_invalid_char";
        String source2 = "+=source_with_invalid_char%!";
        Spanned spannedSource = new SpannedString("&*spanned_source_with_invalid_char#$");

        verify(loginFilter, never()).onStart();
        verify(loginFilter, never()).onStop();
        verify(loginFilter, never()).onInvalidCharacter(anyChar());

        assertNull(loginFilter.filter(source1, 0, source1.length(), dest1, 0, dest1.length()));
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, never()).onInvalidCharacter(anyChar());

        reset(loginFilter);
        assertNull(loginFilter.filter(source1, 0, source1.length(), dest2, 5, 6));
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, times(4)).onInvalidCharacter(anyChar());

        loginFilter = spy(new UsernameFilterGeneric(true));
        assertNull(loginFilter.filter(source2, 0, source2.length(),
                dest1, 0, dest1.length()));
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, times(3)).onInvalidCharacter(anyChar());

        reset(loginFilter);
        assertNull(loginFilter.filter(spannedSource, 0, spannedSource.length(),
                dest1, 0, dest1.length()));
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, times(4)).onInvalidCharacter(anyChar());

        loginFilter = spy(new UsernameFilterGeneric(false));
        result = loginFilter.filter(source2, 0, source2.length(), dest1, 0, dest1.length());
        assertFalse(result instanceof SpannableString);
        assertEquals("+source_with_invalid_char", result.toString());
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, times(3)).onInvalidCharacter(anyChar());

        reset(loginFilter);
        result = loginFilter.filter(spannedSource, 0, spannedSource.length(),
                dest1, 0, dest1.length());
        assertEquals("spanned_source_with_invalid_char", result.toString());
        verify(loginFilter, times(1)).onStart();
        verify(loginFilter, times(1)).onStop();
        verify(loginFilter, times(4)).onInvalidCharacter(anyChar());

        try {
            loginFilter.filter(null, 0, source1.length(), dest1, 0, dest1.length());
            fail("should throw NullPointerException when source is null");
        } catch (NullPointerException e) {
        }

        try {
            // start and end are out of bound.
            loginFilter.filter(source1, -1, source1.length() + 1, dest1, 0, dest1.length());
            fail("should throw StringIndexOutOfBoundsException" +
                    " when start and end are out of bound");
        } catch (StringIndexOutOfBoundsException e) {
        }

        // start is larger than end.
        assertNull("should return null when start is larger than end",
                loginFilter.filter(source1, source1.length(), 0, dest1, 0, dest1.length()));

        try {
            loginFilter.filter(source1, 0, source1.length(), null, 2, dest1.length());
            fail("should throw NullPointerException when dest is null");
        } catch (NullPointerException e) {
        }

        // dstart and dend are out of bound.
        loginFilter.filter(source1, 0, source1.length(), dest1, -1, dest1.length() + 1);

        // dstart is larger than dend.
        loginFilter.filter(source1, 0, source1.length(), dest1, dest1.length(),  0);
    }

    // This method does nothing. we only test onInvalidCharacter function here,
    // the callback should be tested in testFilter()
    @Test
    public void testOnInvalidCharacter() {
        LoginFilter loginFilter = new UsernameFilterGeneric();
        loginFilter.onInvalidCharacter('a');
    }

    // This method does nothing. we only test onStop function here,
    // the callback should be tested in testFilter()
    @Test
    public void testOnStop() {
        LoginFilter loginFilter = new UsernameFilterGeneric();
        loginFilter.onStop();
    }

    // This method does nothing. we only test onStart function here,
    // the callback should be tested in testFilter()
    @Test
    public void testOnStart() {
        LoginFilter loginFilter = new UsernameFilterGeneric();
        loginFilter.onStart();
    }
}
