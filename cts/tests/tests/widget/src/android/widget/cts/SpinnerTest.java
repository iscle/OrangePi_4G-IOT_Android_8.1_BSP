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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Spinner}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SpinnerTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private Spinner mSpinnerDialogMode;
    private Spinner mSpinnerDropdownMode;

    @Rule
    public ActivityTestRule<SpinnerCtsActivity> mActivityRule =
            new ActivityTestRule<>(SpinnerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mSpinnerDialogMode = (Spinner) mActivity.findViewById(R.id.spinner_dialog_mode);
        mSpinnerDropdownMode = (Spinner) mActivity.findViewById(R.id.spinner_dropdown_mode);
    }

    @Test
    public void testConstructor() {
        new Spinner(mActivity);

        new Spinner(mActivity, null);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle);

        new Spinner(mActivity, Spinner.MODE_DIALOG);

        new Spinner(mActivity, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner_Underlined,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner_Underlined,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner_Underlined,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner_Underlined,
                Spinner.MODE_DROPDOWN);

        final Resources.Theme popupTheme = mActivity.getResources().newTheme();
        popupTheme.applyStyle(android.R.style.Theme_Material, true);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG,
                popupTheme);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DROPDOWN,
                popupTheme);
    }

    private void verifyGetBaseline(Spinner spinner) throws Throwable {
        assertEquals(-1, spinner.getBaseline());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> {
            spinner.setAdapter(adapter);
            assertTrue(spinner.getBaseline() > 0);
        });
    }

    @Test
    public void testGetBaseline() throws Throwable {
        verifyGetBaseline(mSpinnerDialogMode);
        verifyGetBaseline(mSpinnerDropdownMode);
    }

    private void verifySetOnItemClickListener(Spinner spinner) {
        try {
            spinner.setOnItemClickListener(null);
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }

        try {
            spinner.setOnItemClickListener(mock(Spinner.OnItemClickListener.class));
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }
    }

    @Test
    public void testSetOnItemClickListener() {
        verifySetOnItemClickListener(mSpinnerDialogMode);
        verifySetOnItemClickListener(mSpinnerDropdownMode);
    }

    private void verifyPerformClick(Spinner spinner) throws Throwable {
        mActivityRule.runOnUiThread(() -> assertTrue(spinner.performClick()));
    }

    @Test
    public void testPerformClick() throws Throwable {
        verifyPerformClick(mSpinnerDialogMode);
        verifyPerformClick(mSpinnerDropdownMode);
    }

    private void verifyOnClick(Spinner spinner) {
        // normal value
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        AlertDialog alertDialog = builder.show();
        assertTrue(alertDialog.isShowing());

        spinner.onClick(alertDialog, 10);
        assertEquals(10, spinner.getSelectedItemPosition());
        assertFalse(alertDialog.isShowing());

        // exceptional
        try {
            spinner.onClick(null, 10);
            fail("did not throw NullPointerException");
        } catch (NullPointerException e) {
        }

        Dialog dialog = new Dialog(mActivity);
        dialog.show();
        assertTrue(dialog.isShowing());

        spinner.onClick(dialog, -10);
        assertEquals(-10, spinner.getSelectedItemPosition());
        assertFalse(dialog.isShowing());
    }

    @UiThreadTest
    @Test
    public void testOnClick() {
        verifyOnClick(mSpinnerDialogMode);
        verifyOnClick(mSpinnerDropdownMode);
    }

    private void verifyAccessPrompt(Spinner spinner) throws Throwable {
        final String initialPrompt = mActivity.getString(R.string.text_view_hello);
        assertEquals(initialPrompt, spinner.getPrompt());

        final String promptText = "prompt text";

        mActivityRule.runOnUiThread(() -> spinner.setPrompt(promptText));
        assertEquals(promptText, spinner.getPrompt());

        spinner.setPrompt(null);
        assertNull(spinner.getPrompt());
    }

    @Test
    public void testAccessPrompt() throws Throwable {
        verifyAccessPrompt(mSpinnerDialogMode);
        verifyAccessPrompt(mSpinnerDropdownMode);
    }

    private void verifySetPromptId(Spinner spinner) throws Throwable {
        mActivityRule.runOnUiThread(() -> spinner.setPromptId(R.string.hello_world));
        assertEquals(mActivity.getString(R.string.hello_world), spinner.getPrompt());

        try {
            spinner.setPromptId(-1);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }

        try {
            spinner.setPromptId(Integer.MAX_VALUE);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }
    }

    @Test
    public void testSetPromptId() throws Throwable {
        verifySetPromptId(mSpinnerDialogMode);
        verifySetPromptId(mSpinnerDropdownMode);
    }

    @UiThreadTest
    @Test
    public void testGetPopupContext() {
        Theme theme = mActivity.getResources().newTheme();
        Spinner themeSpinner = new Spinner(mActivity, null,
                android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG, theme);
        assertNotSame(mActivity, themeSpinner.getPopupContext());
        assertSame(theme, themeSpinner.getPopupContext().getTheme());

        ContextThemeWrapper context = (ContextThemeWrapper)themeSpinner.getPopupContext();
        assertSame(mActivity, context.getBaseContext());
    }

    private void verifyGravity(Spinner spinner) throws Throwable {
        // Note that here we're using a custom layout for the spinner's selected item
        // that doesn't span the whole width of the parent. That way we're exercising the
        // relevant path in spinner's layout pass that handles the currently set gravity
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, R.layout.simple_spinner_item_layout);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> spinner.setAdapter(adapter));

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, spinner, () -> {
            spinner.setSelection(1);
            spinner.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            spinner.requestLayout();
        });

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.LEFT));
        assertEquals(Gravity.LEFT, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.CENTER_HORIZONTAL));
        assertEquals(Gravity.CENTER_HORIZONTAL, spinner.getGravity());

        mActivityRule.runOnUiThread((() -> spinner.setGravity(Gravity.RIGHT)));
        assertEquals(Gravity.RIGHT, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.START));
        assertEquals(Gravity.START, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.END));
        assertEquals(Gravity.END, spinner.getGravity());
    }

    @Test
    public void testGravity() throws Throwable {
        verifyGravity(mSpinnerDialogMode);
        verifyGravity(mSpinnerDropdownMode);
    }

    @Test
    public void testDropDownMetricsDropdownMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> mSpinnerDropdownMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        mActivityRule.runOnUiThread(() -> {
            mSpinnerDropdownMode.setDropDownWidth(dropDownWidth);
            mSpinnerDropdownMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDropdownMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());

        // And test its attributes
        assertEquals(dropDownWidth, mSpinnerDropdownMode.getDropDownWidth());
        // TODO: restore when b/28089349 is addressed
        // assertEquals(dropDownOffsetHorizontal,
        //      mSpinnerDropdownMode.getDropDownHorizontalOffset());
        assertEquals(dropDownOffsetVertical, mSpinnerDropdownMode.getDropDownVerticalOffset());
    }

    @Test
    public void testDropDownMetricsDialogMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> mSpinnerDialogMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        mActivityRule.runOnUiThread(() -> {
            // These are all expected to be no-ops
            mSpinnerDialogMode.setDropDownWidth(dropDownWidth);
            mSpinnerDialogMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDialogMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());

        // And test its attributes. Note that we are not testing the result of getDropDownWidth
        // for this mode
        assertEquals(0, mSpinnerDialogMode.getDropDownHorizontalOffset());
        assertEquals(0, mSpinnerDialogMode.getDropDownVerticalOffset());
    }

    @Test
    public void testDropDownBackgroundDropdownMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> mSpinnerDropdownMode.setAdapter(adapter));

        // Set blue background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDropdownMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());
        // And test its fill
        Drawable dropDownBackground = mSpinnerDropdownMode.getPopupBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be blue", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.BLUE, 1, true);

        // Dismiss the popup with the emulated back key
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        mInstrumentation.waitForIdleSync();
        // Verify that we're not showing the popup
        assertFalse(mSpinnerDropdownMode.isPopupShowing());

        // Set yellow background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDropdownMode.setPopupBackgroundDrawable(
                        mActivity.getDrawable(R.drawable.yellow_fill)));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDropdownMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDropdownMode.isPopupShowing());
        // And test its fill
        dropDownBackground = mSpinnerDropdownMode.getPopupBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be yellow", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.YELLOW, 1, true);
    }

    @Test
    public void testDropDownBackgroundDialogMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mActivityRule.runOnUiThread(() -> mSpinnerDialogMode.setAdapter(adapter));

        // Set blue background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDialogMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());
        // And test that getPopupBackground returns null
        assertNull(mSpinnerDialogMode.getPopupBackground());

        // Dismiss the popup with the emulated back key
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        mInstrumentation.waitForIdleSync();
        // Verify that we're not showing the popup
        assertFalse(mSpinnerDialogMode.isPopupShowing());

        // Set yellow background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDialogMode.setPopupBackgroundDrawable(
                        mActivity.getDrawable(R.drawable.yellow_fill)));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mSpinnerDialogMode);
        // Verify that we're showing the popup
        assertTrue(mSpinnerDialogMode.isPopupShowing());
        // And test that getPopupBackground returns null
        assertNull(mSpinnerDialogMode.getPopupBackground());
    }
}
