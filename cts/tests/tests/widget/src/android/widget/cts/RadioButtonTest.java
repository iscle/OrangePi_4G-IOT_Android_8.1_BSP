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
import android.widget.RadioButton;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RadioButtonTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private RadioButton mRadioButton;

    @Rule
    public ActivityTestRule<RadioButtonCtsActivity> mActivityRule =
            new ActivityTestRule<>(RadioButtonCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mRadioButton = (RadioButton) mActivity.findViewById(R.id.radio_button);
    }

    @Test
    public void testConstructor() {
        new RadioButton(mActivity);
        new RadioButton(mActivity, null);
        new RadioButton(mActivity, null, android.R.attr.radioButtonStyle);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_Material_CompoundButton_RadioButton);
        new RadioButton(mActivity, null, 0,
                android.R.style.Widget_Material_Light_CompoundButton_RadioButton);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new RadioButton(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new RadioButton(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new RadioButton(null, null, 0);
    }

    @UiThreadTest
    @Test
    public void testText() {
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.hello_world), mRadioButton.getText()));

        mRadioButton.setText("new text");
        assertTrue(TextUtils.equals("new text", mRadioButton.getText()));

        mRadioButton.setText(R.string.text_name);
        assertTrue(TextUtils.equals(
                mActivity.getString(R.string.text_name), mRadioButton.getText()));
    }

    @UiThreadTest
    @Test
    public void testAccessChecked() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // not checked -> not checked
        mRadioButton.setChecked(false);
        verifyZeroInteractions(mockCheckedChangeListener);
        assertFalse(mRadioButton.isChecked());

        // not checked -> checked
        mRadioButton.setChecked(true);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // checked -> checked
        mRadioButton.setChecked(true);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // checked -> not checked
        mRadioButton.setChecked(false);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, false);
        assertFalse(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @UiThreadTest
    @Test
    public void testToggleViaApi() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // toggle to checked
        mRadioButton.toggle();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // try toggle to not checked - this should leave the radio button in checked state
        mRadioButton.toggle();
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @Test
    public void testToggleViaEmulatedTap() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // tap to checked
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mRadioButton);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // tap to not checked - this should leave the radio button in checked state
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mRadioButton);
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }

    @UiThreadTest
    @Test
    public void testToggleViaPerformClick() {
        final RadioButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(RadioButton.OnCheckedChangeListener.class);
        mRadioButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        verifyZeroInteractions(mockCheckedChangeListener);

        assertFalse(mRadioButton.isChecked());

        // click to checked
        mRadioButton.performClick();
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mRadioButton, true);
        assertTrue(mRadioButton.isChecked());

        // click to not checked - this should leave the radio button in checked state
        mRadioButton.performClick();
        assertTrue(mRadioButton.isChecked());

        verifyNoMoreInteractions(mockCheckedChangeListener);
    }
}
