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

package android.app.cts;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.stubs.DialogStubActivity;
import android.content.DialogInterface;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;
import android.widget.Button;

import com.android.compatibility.common.util.PollingCheck;

import android.app.stubs.R;
/*
 * Test AlertDialog
 */
@SmallTest
public class AlertDialogTest extends ActivityInstrumentationTestCase2<DialogStubActivity> {
    private Instrumentation mInstrumentation;
    private DialogStubActivity mActivity;
    private Button mPositiveButton;
    private Button mNegativeButton;
    private Button mNeutralButton;

    public AlertDialogTest() {
        super("android.app.stubs", DialogStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
    }

    protected void startDialogActivity(int dialogNumber) {
        mActivity = DialogStubActivity.startDialogActivity(this, dialogNumber);

        PollingCheck.waitFor(() -> mActivity.getDialog().isShowing());
    }

    public void testAlertDialog() throws Throwable {
        doTestAlertDialog(DialogStubActivity.TEST_ALERTDIALOG);
    }

    private void doTestAlertDialog(int index) throws Throwable {
        startDialogActivity(index);
        assertTrue(mActivity.getDialog().isShowing());

        mPositiveButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_POSITIVE);
        assertNotNull(mPositiveButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_positive),
                mPositiveButton.getText());
        mNeutralButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_NEUTRAL);
        assertNotNull(mNeutralButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_neutral),
                mNeutralButton.getText());
        mNegativeButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_NEGATIVE);
        assertNotNull(mNegativeButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_negative),
                mNegativeButton.getText());

        assertFalse(mActivity.isPositiveButtonClicked);
        performClick(mPositiveButton);
        assertTrue(mActivity.isPositiveButtonClicked);

        assertFalse(mActivity.isNegativeButtonClicked);
        performClick(mNegativeButton);
        assertTrue(mActivity.isNegativeButtonClicked);

        assertFalse(mActivity.isNeutralButtonClicked);
        performClick(mNeutralButton);
        assertTrue(mActivity.isNeutralButtonClicked);
    }

    public void testAlertDialogDeprecatedAPI() throws Throwable {
        doTestAlertDialog(DialogStubActivity.TEST_ALERTDIALOG_DEPRECATED);
    }

    private void testAlertDialogAPIWithMessage(final boolean useDeprecatedAPIs) throws Throwable {
        startDialogActivity(useDeprecatedAPIs
                ? DialogStubActivity.TEST_ALERTDIALOG_DEPRECATED_WITH_MESSAGE
                : DialogStubActivity.TEST_ALERTDIALOG_WITH_MESSAGE);

        assertTrue(mActivity.getDialog().isShowing());

        mPositiveButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_POSITIVE);
        assertNotNull(mPositiveButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_positive),
                mPositiveButton.getText());
        mNegativeButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_NEGATIVE);
        assertNotNull(mNegativeButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_negative),
                mNegativeButton.getText());
        mNeutralButton = ((AlertDialog) (mActivity.getDialog())).getButton(
                DialogInterface.BUTTON_NEUTRAL);
        assertNotNull(mNeutralButton);
        assertEquals(mActivity.getString(R.string.alert_dialog_neutral),
                mNeutralButton.getText());

        DialogStubActivity.buttonIndex = 0;
        performClick(mPositiveButton);
        assertEquals(DialogInterface.BUTTON_POSITIVE, DialogStubActivity.buttonIndex);

        DialogStubActivity.buttonIndex = 0;
        performClick(mNeutralButton);
        assertEquals(DialogInterface.BUTTON_NEUTRAL, DialogStubActivity.buttonIndex);

        DialogStubActivity.buttonIndex = 0;
        performClick(mNegativeButton);
        assertEquals(DialogInterface.BUTTON_NEGATIVE, DialogStubActivity.buttonIndex);
    }

    public void testAlertDialogAPIWithMessageDeprecated() throws Throwable {
        testAlertDialogAPIWithMessage(true);
    }

    public void testAlertDialogAPIWithMessageNotDeprecated() throws Throwable {
        testAlertDialogAPIWithMessage(false);
    }

    private void performClick(final Button button) throws Throwable {
        runTestOnUiThread(() -> button.performClick());
        mInstrumentation.waitForIdleSync();
    }

    public void testCustomAlertDialog() {
        startDialogActivity(DialogStubActivity.TEST_CUSTOM_ALERTDIALOG);
        assertTrue(mActivity.getDialog().isShowing());
    }

    public void testCustomAlertDialogView() {
        startDialogActivity(DialogStubActivity.TEST_CUSTOM_ALERTDIALOG_VIEW);
        assertTrue(mActivity.getDialog().isShowing());
    }

    public void testCallback() {
        startDialogActivity(DialogStubActivity.TEST_ALERTDIALOG_CALLBACK);
        assertTrue(mActivity.onCreateCalled);

        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_0);
        assertTrue(mActivity.onKeyDownCalled);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_0);
        assertTrue(mActivity.onKeyUpCalled);
    }

    public void testAlertDialogTheme() throws Exception {
        startDialogActivity(DialogStubActivity.TEST_ALERTDIALOG_THEME);
        assertTrue(mActivity.getDialog().isShowing());
    }

    public void testAlertDialogCancelable() throws Exception {
        startDialogActivity(DialogStubActivity.TEST_ALERTDIALOG_CANCELABLE);
        assertTrue(mActivity.getDialog().isShowing());
        assertFalse(mActivity.onCancelCalled);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        mInstrumentation.waitForIdleSync();
        assertTrue(mActivity.onCancelCalled);
    }

    public void testAlertDialogNotCancelable() throws Exception {
        startDialogActivity(DialogStubActivity.TEST_ALERTDIALOG_NOT_CANCELABLE);
        assertTrue(mActivity.getDialog().isShowing());
        assertFalse(mActivity.onCancelCalled);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        assertFalse(mActivity.onCancelCalled);
    }

    public void testAlertDialogIconDrawable() {
        startDialogActivity(DialogStubActivity.TEST_ALERT_DIALOG_ICON_DRAWABLE);
        assertTrue(mActivity.getDialog().isShowing());
    }

    public void testAlertDialogIconAttribute() {
        startDialogActivity(DialogStubActivity.TEST_ALERT_DIALOG_ICON_ATTRIBUTE);
        assertTrue(mActivity.getDialog().isShowing());
    }
}
