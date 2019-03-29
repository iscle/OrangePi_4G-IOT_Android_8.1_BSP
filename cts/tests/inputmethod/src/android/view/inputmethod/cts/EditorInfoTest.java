/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.MoreAsserts;
import android.text.TextUtils;
import android.util.Printer;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EditorInfoTest {
    @Test
    public void testEditorInfo() {
        EditorInfo info = new EditorInfo();

        info.actionId = 1;
        info.actionLabel = "actionLabel";
        info.fieldId = 2;
        info.fieldName = "fieldName";
        info.hintText = "hintText";
        info.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        info.initialCapsMode = TextUtils.CAP_MODE_CHARACTERS;
        info.initialSelEnd = 10;
        info.initialSelStart = 0;
        info.inputType = EditorInfo.TYPE_MASK_CLASS;
        info.label = "label";
        info.packageName = "android.view.cts";
        info.privateImeOptions = "privateIme";
        Bundle b = new Bundle();
        String key = "bundleKey";
        String value = "bundleValue";
        b.putString(key, value);
        info.extras = b;
        info.hintLocales = LocaleList.forLanguageTags("en-PH,en-US");
        info.contentMimeTypes = new String[]{"image/gif", "image/png"};

        assertEquals(0, info.describeContents());

        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        EditorInfo targetInfo = EditorInfo.CREATOR.createFromParcel(p);
        p.recycle();
        assertEquals(info.actionId, targetInfo.actionId);
        assertEquals(info.fieldId, targetInfo.fieldId);
        assertEquals(info.fieldName, targetInfo.fieldName);
        assertEquals(info.imeOptions, targetInfo.imeOptions);
        assertEquals(info.initialCapsMode, targetInfo.initialCapsMode);
        assertEquals(info.initialSelEnd, targetInfo.initialSelEnd);
        assertEquals(info.initialSelStart, targetInfo.initialSelStart);
        assertEquals(info.inputType, targetInfo.inputType);
        assertEquals(info.packageName, targetInfo.packageName);
        assertEquals(info.privateImeOptions, targetInfo.privateImeOptions);
        assertEquals(info.hintText.toString(), targetInfo.hintText.toString());
        assertEquals(info.actionLabel.toString(), targetInfo.actionLabel.toString());
        assertEquals(info.label.toString(), targetInfo.label.toString());
        assertEquals(info.extras.getString(key), targetInfo.extras.getString(key));
        assertEquals(info.hintLocales, targetInfo.hintLocales);
        MoreAsserts.assertEquals(info.contentMimeTypes, targetInfo.contentMimeTypes);

        Printer printer = mock(Printer.class);
        String prefix = "TestEditorInfo";
        info.dump(printer, prefix);
        verify(printer, atLeastOnce()).println(anyString());
    }

    @Test
    public void testNullHintLocals() {
        EditorInfo info = new EditorInfo();
        info.hintLocales = null;
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        EditorInfo targetInfo = EditorInfo.CREATOR.createFromParcel(p);
        p.recycle();
        assertNull(targetInfo.hintLocales);
    }
}
