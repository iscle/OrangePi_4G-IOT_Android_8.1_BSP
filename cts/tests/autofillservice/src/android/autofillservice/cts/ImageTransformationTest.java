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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.service.autofill.ImageTransformation;
import android.service.autofill.ValueFinder;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class ImageTransformationTest {

    @Test
    public void testAllNullBuilder() {
        assertThrows(NullPointerException.class,
                () ->  new ImageTransformation.Builder(null, null, 0));
    }

    @Test
    public void testNullAutofillIdBuilder() {
        assertThrows(NullPointerException.class,
                () ->  new ImageTransformation.Builder(null, Pattern.compile(""), 1));
    }

    @Test
    public void testNullRegexBuilder() {
        assertThrows(NullPointerException.class,
                () ->  new ImageTransformation.Builder(new AutofillId(1), null, 1));
    }

    @Test
    public void testNullSubstBuilder() {
        assertThrows(IllegalArgumentException.class,
                () ->  new ImageTransformation.Builder(new AutofillId(1), Pattern.compile(""), 0));
    }

    @Test
    public void fieldCannotBeFound() throws Exception {
        AutofillId unknownId = new AutofillId(42);

        ImageTransformation trans = new ImageTransformation
                .Builder(unknownId, Pattern.compile("val"), 1)
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(unknownId)).thenReturn(null);

        trans.apply(finder, template, 0);

        // if a view cannot be found, nothing is set
        verify(template, never()).setImageViewResource(anyInt(), anyInt());
    }

    @Test
    public void theOneOptionsMatches() throws Exception {
        AutofillId id = new AutofillId(1);
        ImageTransformation trans = new ImageTransformation
                .Builder(id, Pattern.compile(".*"), 42)
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("val");

        trans.apply(finder, template, 0);

        verify(template).setImageViewResource(0, 42);
    }

    @Test
    public void noOptionsMatches() throws Exception {
        AutofillId id = new AutofillId(1);
        ImageTransformation trans = new ImageTransformation
                .Builder(id, Pattern.compile("val"), 42)
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("bad-val");

        trans.apply(finder, template, 0);

        verify(template, never()).setImageViewResource(anyInt(), anyInt());
    }

    @Test
    public void multipleOptionsOneMatches() throws Exception {
        AutofillId id = new AutofillId(1);
        ImageTransformation trans = new ImageTransformation
                .Builder(id, Pattern.compile(".*1"), 1)
                .addOption(Pattern.compile(".*2"), 2)
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("val-2");

        trans.apply(finder, template, 0);

        verify(template).setImageViewResource(0, 2);
    }

    @Test
    public void twoOptionsMatch() throws Exception {
        AutofillId id = new AutofillId(1);
        ImageTransformation trans = new ImageTransformation
                .Builder(id, Pattern.compile(".*a.*"), 1)
                .addOption(Pattern.compile(".*b.*"), 2)
                .build();

        ValueFinder finder = mock(ValueFinder.class);
        RemoteViews template = mock(RemoteViews.class);

        when(finder.findByAutofillId(id)).thenReturn("ab");

        trans.apply(finder, template, 0);

        // If two options match, the first one is picked
        verify(template, only()).setImageViewResource(0, 1);
    }
}
