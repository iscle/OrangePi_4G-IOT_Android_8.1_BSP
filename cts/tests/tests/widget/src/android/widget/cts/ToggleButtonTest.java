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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.widget.ToggleButton;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ToggleButton}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ToggleButtonTest {
    private static final String TEXT_OFF = "text off";
    private static final String TEXT_ON = "text on";

    private Activity mActivity;

    @Rule
    public ActivityTestRule<ToggleButtonCtsActivity> mActivityRule =
            new ActivityTestRule<>(ToggleButtonCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new ToggleButton(mActivity);
        new ToggleButton(mActivity, null);
        new ToggleButton(mActivity, null, android.R.attr.buttonStyleToggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Button_Toggle);
        new ToggleButton(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_Button_Toggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_Material_Button_Toggle);
        new ToggleButton(mActivity, null, 0, android.R.style.Widget_Material_Light_Button_Toggle);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new ToggleButton(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new ToggleButton(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new ToggleButton(null, null, -1);
    }

    @Test
    public void testAttributesFromStyle() {
        final ToggleButton toggleButton =
                (ToggleButton) mActivity.findViewById(R.id.toggle_with_style);
        assertEquals(mActivity.getString(R.string.toggle_text_on), toggleButton.getTextOn());
        assertEquals(mActivity.getString(R.string.toggle_text_off), toggleButton.getTextOff());
    }

    @Test
    public void testAttributesFromLayout() {
        final ToggleButton toggleButton =
                (ToggleButton) mActivity.findViewById(R.id.toggle_with_defaults);
        assertEquals(mActivity.getString(R.string.toggle_text_on_alt), toggleButton.getTextOn());
        assertEquals(mActivity.getString(R.string.toggle_text_off_alt), toggleButton.getTextOff());
    }

    @UiThreadTest
    @Test
    public void testAccessTextOff() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        toggleButton.setTextOff("android");
        assertEquals("android", toggleButton.getTextOff());
        toggleButton.setChecked(false);

        toggleButton.setTextOff(null);
        assertNull(toggleButton.getTextOff());

        toggleButton.setTextOff("");
        assertEquals("", toggleButton.getTextOff());
    }

    @UiThreadTest
    @Test
    public void testDrawableStateChanged() {
        final MockToggleButton toggleButton =
                (MockToggleButton) mActivity.findViewById(R.id.toggle_custom);

        // drawableStateChanged without any drawable.
        toggleButton.drawableStateChanged();

        final StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[] { android.R.attr.state_pressed },
                mActivity.getDrawable(R.drawable.scenery));
        drawable.addState(new int[] {},
                mActivity.getDrawable(R.drawable.scenery));

        // drawableStateChanged when CheckMarkDrawable is not null.
        toggleButton.setButtonDrawable(drawable);
        drawable.setState(null);
        assertNull(drawable.getState());

        toggleButton.drawableStateChanged();
        assertNotNull(drawable.getState());
        assertEquals(toggleButton.getDrawableState(), drawable.getState());
    }

    @UiThreadTest
    @Test
    public void testOnFinishInflate() {
        final MockToggleButton toggleButton =
                (MockToggleButton) mActivity.findViewById(R.id.toggle_custom);
        toggleButton.onFinishInflate();
    }

    @UiThreadTest
    @Test
    public void testSetChecked() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        assertFalse(toggleButton.isChecked());

        toggleButton.setChecked(true);
        assertTrue(toggleButton.isChecked());

        toggleButton.setChecked(false);
        assertFalse(toggleButton.isChecked());
    }

    @UiThreadTest
    @Test
    public void testToggleText() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        toggleButton.setText("default text");
        toggleButton.setTextOn(TEXT_ON);
        toggleButton.setTextOff(TEXT_OFF);
        toggleButton.setChecked(true);
        assertEquals(TEXT_ON, toggleButton.getText().toString());
        toggleButton.setChecked(false);
        assertFalse(toggleButton.isChecked());
        assertEquals(TEXT_OFF, toggleButton.getText().toString());

        // Set the current displaying text as TEXT_OFF.
        // Then set checked button, but textOn is null.
        toggleButton.setTextOff(TEXT_OFF);
        toggleButton.setChecked(false);
        toggleButton.setTextOn(null);
        toggleButton.setChecked(true);
        assertEquals(TEXT_OFF, toggleButton.getText().toString());

        // Set the current displaying text as TEXT_ON. Then set unchecked button,
        // but textOff is null.
        toggleButton.setTextOn(TEXT_ON);
        toggleButton.setChecked(true);
        toggleButton.setTextOff(null);
        toggleButton.setChecked(false);
        assertEquals(TEXT_ON, toggleButton.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testSetBackgroundDrawable() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        final Drawable drawable = mActivity.getDrawable(R.drawable.scenery);

        toggleButton.setBackgroundDrawable(drawable);
        assertSame(drawable, toggleButton.getBackground());

        // remove the background
        toggleButton.setBackgroundDrawable(null);
        assertNull(toggleButton.getBackground());
    }

    @UiThreadTest
    @Test
    public void testAccessTextOn() {
        final ToggleButton toggleButton = (ToggleButton) mActivity.findViewById(R.id.toggle1);
        toggleButton.setTextOn("cts");
        assertEquals("cts", toggleButton.getTextOn());

        toggleButton.setTextOn(null);
        assertNull(toggleButton.getTextOn());

        toggleButton.setTextOn("");
        assertEquals("", toggleButton.getTextOn());
    }

    /**
     * MockToggleButton class for testing.
     */
    public static final class MockToggleButton extends ToggleButton {
        public MockToggleButton(Context context) {
            super(context);
        }

        public MockToggleButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
        }
    }
}
