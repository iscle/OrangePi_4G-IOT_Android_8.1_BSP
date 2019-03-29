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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class View_IdsTest {
    @Rule
    public ActivityTestRule<UsingViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(UsingViewsCtsActivity.class);

    @UiThreadTest
    @Test
    public void testIds() {
        Activity activity;
        activity = mActivityRule.getActivity();

        EditText editText = (EditText) activity.findViewById(R.id.entry);
        Button buttonOk = (Button) activity.findViewById(R.id.ok);
        Button buttonCancel = (Button) activity.findViewById(R.id.cancel);
        TextView symbol = (TextView) activity.findViewById(R.id.symbolball);
        TextView warning = (TextView) activity.findViewById(R.id.warning);

        assertNotNull(editText);
        assertNotNull(buttonOk);
        assertNotNull(buttonCancel);
        assertNotNull(symbol);
        assertNotNull(warning);

        assertEquals(activity.getString(R.string.id_ok), buttonOk.getText().toString());
        assertEquals(activity.getString(R.string.id_cancel), buttonCancel.getText().toString());

        editText.setId(0x1111);
        assertEquals(0x1111, editText.getId());
        assertSame(editText, (EditText) activity.findViewById(0x1111));

        buttonCancel.setId(0x9999);
        assertEquals(0x9999, buttonCancel.getId());
        assertSame(buttonCancel, (Button) activity.findViewById(0x9999));
    }
}
