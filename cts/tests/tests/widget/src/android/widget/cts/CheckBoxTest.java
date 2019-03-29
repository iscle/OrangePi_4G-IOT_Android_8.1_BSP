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

package android.widget.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.widget.CheckBox;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CheckBoxTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private CheckBox mCheckBox;

    @Rule
    public ActivityTestRule<CheckBoxCtsActivity> mActivityRule =
            new ActivityTestRule<>(CheckBoxCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mCheckBox = (CheckBox) mActivity.findViewById(R.id.check_box);
    }

    @Test
    public void testConstructor() {
        new CheckBox(mActivity);
        new CheckBox(mActivity, null);
        new CheckBox(mActivity, null, android.R.attr.checkboxStyle);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_Material_CompoundButton_CheckBox);
        new CheckBox(mActivity, null, 0,
                android.R.style.Widget_Material_Light_CompoundButton_CheckBox);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new CheckBox(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new CheckBox(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new CheckBox(null, null, -1);
    }

    @UiThreadTest
    @Test
    public void testText() {
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.hello_world), mCheckBox.getText()));

        mCheckBox.setText("new text");
        assertTrue(TextUtils.equals("new text", mCheckBox.getText()));

        mCheckBox.setText(R.string.text_name);
        assertTrue(TextUtils.equals(mActivity.getString(R.string.text_name), mCheckBox.getText()));
    }

    @UiThreadTest
    @Test
    public void testAccessChecked() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // not checked -> not checked
        mCheckBox.setChecked(false);
        verifyZeroInteractions(mockCheckedChangeListener);
        assertFalse(mCheckBox.isChecked());

        // not checked -> checked
        mCheckBox.setChecked(true);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // checked -> checked
        mCheckBox.setChecked(true);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // checked -> not checked
        mCheckBox.setChecked(false);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @UiThreadTest
    @Test
    public void testToggleViaApi() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // toggle to checked
        mCheckBox.toggle();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // toggle to not checked
        mCheckBox.toggle();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @Test
    public void testToggleViaEmulatedTap() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // tap to checked
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mCheckBox);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // tap to not checked
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mCheckBox);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @UiThreadTest
    @Test
    public void testToggleViaPerformClick() {
        final CheckBox.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CheckBox.OnCheckedChangeListener.class);
        mCheckBox.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mCheckBox.isChecked());

        // click to checked
        mCheckBox.performClick();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, true);
        assertTrue(mCheckBox.isChecked());

        // click to not checked
        mCheckBox.performClick();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCheckBox, false);
        assertFalse(mCheckBox.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }
}
