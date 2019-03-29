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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.service.autofill.LuhnChecksumValidator;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LuhnChecksumValidatorTest {

    @Test
    public void nullId() {
        assertThrows(NullPointerException.class,
                () -> new LuhnChecksumValidator((AutofillId[]) null));
    }

    @Test
    public void nullAndOtherId() {
        assertThrows(NullPointerException.class,
                () -> new LuhnChecksumValidator(new AutofillId(1), null));
    }

    @Test
    public void duplicateFields() {
        AutofillId id = new AutofillId(1);

        // duplicate fields are allowed
        LuhnChecksumValidator validator = new LuhnChecksumValidator(id, id);

        ValueFinder finder = mock(ValueFinder.class);

        // 5 is a valid checksum for 0005000
        when(finder.findByAutofillId(id)).thenReturn("0005");
        assertThat(validator.isValid(finder)).isTrue();

        // 6 is a not a valid checksum for 0006000
        when(finder.findByAutofillId(id)).thenReturn("0006");
        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void leadingZerosAreIgnored() {
        AutofillId id = new AutofillId(1);

        LuhnChecksumValidator validator = new LuhnChecksumValidator(id);

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(id)).thenReturn("7992739871-3");
        assertThat(validator.isValid(finder)).isTrue();

        when(finder.findByAutofillId(id)).thenReturn("07992739871-3");
        assertThat(validator.isValid(finder)).isTrue();
    }

    @Test
    public void onlyOneChecksumValid() {
        AutofillId id = new AutofillId(1);

        LuhnChecksumValidator validator = new LuhnChecksumValidator(id);

        ValueFinder finder = mock(ValueFinder.class);

        for (int i = 0; i < 10; i++) {
            when(finder.findByAutofillId(id)).thenReturn("7992739871-" + i);
            assertThat(validator.isValid(finder)).isEqualTo(i == 3);
        }
    }

    @Test
    public void nullAutofillValuesCauseFailure() {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);
        AutofillId id3 = new AutofillId(3);

        LuhnChecksumValidator validator = new LuhnChecksumValidator(id1, id2, id3);

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(id1)).thenReturn("7992739871");
        when(finder.findByAutofillId(id2)).thenReturn(null);
        when(finder.findByAutofillId(id3)).thenReturn("3");

        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void nonDigits() {
        AutofillId id = new AutofillId(1);

        LuhnChecksumValidator validator = new LuhnChecksumValidator(id);

        ValueFinder finder = mock(ValueFinder.class);
        when(finder.findByAutofillId(id)).thenReturn("a7B9^9\n2 7{3\b9\08\uD83C\uDF2D7-1_3$");
        assertThat(validator.isValid(finder)).isTrue();
    }

    @Test
    public void multipleFieldNumber() {
        AutofillId id1 = new AutofillId(1);
        AutofillId id2 = new AutofillId(2);

        LuhnChecksumValidator validator = new LuhnChecksumValidator(id1, id2);

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(id1)).thenReturn("7992739871");
        when(finder.findByAutofillId(id2)).thenReturn("3");
        assertThat(validator.isValid(finder)).isTrue();

        when(finder.findByAutofillId(id2)).thenReturn("2");
        assertThat(validator.isValid(finder)).isFalse();
    }
}
