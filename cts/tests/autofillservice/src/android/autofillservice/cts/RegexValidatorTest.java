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

import android.service.autofill.RegexValidator;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class RegexValidatorTest {

    @Test
    public void allNullConstructor() {
        assertThrows(NullPointerException.class, () -> new RegexValidator(null, null));
    }

    @Test
    public void nullRegexConstructor() {
        assertThrows(NullPointerException.class,
                () -> new RegexValidator(new AutofillId(1), null));
    }

    @Test
    public void nullAutofillIdConstructor() {
        assertThrows(NullPointerException.class,
                () -> new RegexValidator(null, Pattern.compile(".")));
    }

    @Test
    public void unknownField() {
        AutofillId unknownId = new AutofillId(42);

        RegexValidator validator = new RegexValidator(unknownId, Pattern.compile(".*"));

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(unknownId)).thenReturn(null);
        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void singleFieldValid() {
        AutofillId creditCardFieldId = new AutofillId(1);
        RegexValidator validator = new RegexValidator(creditCardFieldId,
                Pattern.compile("^\\s*\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?(\\d{4})\\s*$"));

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("1234 5678 9012 3456");
        assertThat(validator.isValid(finder)).isTrue();

        when(finder.findByAutofillId(creditCardFieldId)).thenReturn("invalid");
        assertThat(validator.isValid(finder)).isFalse();
    }

    @Test
    public void singleFieldInvalid() {
        AutofillId id = new AutofillId(1);
        RegexValidator validator = new RegexValidator(id, Pattern.compile("\\d*"));

        ValueFinder finder = mock(ValueFinder.class);

        when(finder.findByAutofillId(id)).thenReturn("123a456");

        // Regex has to match the whole value
        assertThat(validator.isValid(finder)).isFalse();
    }
}
