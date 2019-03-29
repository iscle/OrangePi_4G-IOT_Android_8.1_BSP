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

package android.text.method.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.Selection;
import android.text.method.CharacterPickerDialog;
import android.view.View;
import android.widget.Gallery;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CharacterPickerDialogTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        final CharSequence str = "123456";
        final Editable content = Editable.Factory.getInstance().newEditable(str);
        final View view = new TextViewNoIme(mActivity);
        new CharacterPickerDialog(view.getContext(), view, content, "\u00A1", false);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        final CharSequence str = "123456";
        final Editable content = Editable.Factory.getInstance().newEditable(str);
        final View view = new TextViewNoIme(mActivity);
        new CharacterPickerDialog(null, view, content, "\u00A1", false);
    }

    @UiThreadTest
    @Test
    public void testOnItemClick() {
        final Gallery parent = new Gallery(mActivity);
        final CharSequence str = "123456";
        Editable text = Editable.Factory.getInstance().newEditable(str);
        final View view = new TextViewNoIme(mActivity);
        CharacterPickerDialog replacePickerDialog =
                new CharacterPickerDialog(view.getContext(), view, text, "abc", false);

        // insert 'a' to the beginning of text
        replacePickerDialog.show();
        Selection.setSelection(text, 0, 0);
        assertEquals(str, text.toString());
        assertTrue(replacePickerDialog.isShowing());

        replacePickerDialog.onItemClick(parent, view, 0, 0);
        assertEquals("a123456", text.toString());
        assertFalse(replacePickerDialog.isShowing());

        // replace the second character '1' with 'c'
        replacePickerDialog.show();
        Selection.setSelection(text, 2, 2);
        assertTrue(replacePickerDialog.isShowing());

        replacePickerDialog.onItemClick(parent, view, 2, 0);
        assertEquals("ac23456", text.toString());
        assertFalse(replacePickerDialog.isShowing());

        // insert character 'c' between '2' and '3'
        text = Editable.Factory.getInstance().newEditable(str);
        CharacterPickerDialog insertPickerDialog =
            new CharacterPickerDialog(view.getContext(), view, text, "abc", true);
        Selection.setSelection(text, 2, 2);
        assertEquals(str, text.toString());
        insertPickerDialog.show();
        assertTrue(insertPickerDialog.isShowing());

        insertPickerDialog.onItemClick(parent, view, 2, 0);
        assertEquals("12c3456", text.toString());
        assertFalse(insertPickerDialog.isShowing());
    }

    @UiThreadTest
    @Test
    public void testOnClick() {
        final CharSequence str = "123456";
        final Editable content = Editable.Factory.getInstance().newEditable(str);
        final View view = new TextViewNoIme(mActivity);
        CharacterPickerDialog characterPickerDialog =
                new CharacterPickerDialog(view.getContext(), view, content, "\u00A1", false);

        characterPickerDialog.show();
        assertTrue(characterPickerDialog.isShowing());

        // nothing to test here, just make sure onClick does not throw exception
        characterPickerDialog.onClick(view);
    }
}
