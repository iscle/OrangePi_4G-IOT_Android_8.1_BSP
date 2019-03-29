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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ClipDescription;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.R;
import android.view.inputmethod.cts.util.InputConnectionTestUtils;
import android.widget.EditText;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BaseInputConnectionTest {
    /** Extended timeout in case the system is sluggish */
    private static final long WINDOW_FOCUS_TIMEOUT = 10000;

    private Instrumentation mInstrumentation;
    private InputMethodCtsActivity mActivity;
    private Window mWindow;
    private EditText mView;
    private BaseInputConnection mConnection;

    @Rule
    public ActivityTestRule<InputMethodCtsActivity> mActivityRule =
            new ActivityTestRule<>(InputMethodCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(WINDOW_FOCUS_TIMEOUT, mActivity::hasWindowFocus);
        mWindow = mActivity.getWindow();
        mView = (EditText) mWindow.findViewById(R.id.entry);
        mConnection = new BaseInputConnection(mView, true);
    }

    @Test
    public void testDefaultMethods() {
        // These methods are default to return fixed result.

        assertFalse(mConnection.beginBatchEdit());
        assertFalse(mConnection.endBatchEdit());

        // only fit for test default implementation of commitCompletion.
        int completionId = 1;
        String completionString = "commitCompletion test";
        assertFalse(mConnection.commitCompletion(new CompletionInfo(completionId,
                0, completionString)));

        assertNull(mConnection.getExtractedText(new ExtractedTextRequest(), 0));

        // only fit for test default implementation of performEditorAction.
        int actionCode = 1;
        int actionId = 2;
        String action = "android.intent.action.MAIN";
        assertTrue(mConnection.performEditorAction(actionCode));
        assertFalse(mConnection.performContextMenuAction(actionId));
        assertFalse(mConnection.performPrivateCommand(action, new Bundle()));
    }

    @Test
    public void testOpComposingSpans() {
        Spannable text = new SpannableString("Test ComposingSpans");
        BaseInputConnection.setComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) > -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) > -1);
        BaseInputConnection.removeComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) == -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) == -1);
    }

    /**
     * getEditable: Return the target of edit operations. The default implementation
     *              returns its own fake editable that is just used for composing text.
     * clearMetaKeyStates: Default implementation uses
     *              MetaKeyKeyListener#clearMetaKeyState(long, int) to clear the state.
     *              BugId:1738511
     * commitText: 1. Default implementation replaces any existing composing text with the given
     *                text.
     *             2. In addition, only if dummy mode, a key event is sent for the new text and the
     *                current editable buffer cleared.
     * deleteSurroundingText: The default implementation performs the deletion around the current
     *              selection position of the editable text.
     * getCursorCapsMode: 1. The default implementation uses TextUtils.getCapsMode to get the
     *                  cursor caps mode for the current selection position in the editable text.
     *                  TextUtils.getCapsMode is tested fully in TextUtilsTest#testGetCapsMode.
     *                    2. In dummy mode in which case 0 is always returned.
     * getTextBeforeCursor, getTextAfterCursor: The default implementation performs the deletion
     *                          around the current selection position of the editable text.
     * setSelection: changes the selection position in the current editable text.
     */
    @Test
    public void testOpTextMethods() throws Throwable {
        // return is an default Editable instance with empty source
        final Editable text = mConnection.getEditable();
        assertNotNull(text);
        assertEquals(0, text.length());

        // Test commitText, not dummy mode
        CharSequence str = "TestCommit ";
        Editable inputText = Editable.Factory.getInstance().newEditable(str);
        mConnection.commitText(inputText, inputText.length());
        final Editable text2 = mConnection.getEditable();
        int strLength = str.length();
        assertEquals(strLength, text2.length());
        assertEquals(str.toString(), text2.toString());
        assertEquals(TextUtils.CAP_MODE_WORDS,
                mConnection.getCursorCapsMode(TextUtils.CAP_MODE_WORDS));
        int offLength = 3;
        CharSequence expected = str.subSequence(strLength - offLength, strLength);
        assertEquals(expected.toString(), mConnection.getTextBeforeCursor(offLength,
                BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        mConnection.setSelection(0, 0);
        expected = str.subSequence(0, offLength);
        assertEquals(expected.toString(), mConnection.getTextAfterCursor(offLength,
                BaseInputConnection.GET_TEXT_WITH_STYLES).toString());

        mActivityRule.runOnUiThread(() -> {
            assertTrue(mView.requestFocus());
            assertTrue(mView.isFocused());
        });

        // dummy mode
        BaseInputConnection dummyConnection = new BaseInputConnection(mView, false);
        dummyConnection.commitText(inputText, inputText.length());
        PollingCheck.waitFor(() -> text2.toString().equals(mView.getText().toString()));
        assertEquals(0, dummyConnection.getCursorCapsMode(TextUtils.CAP_MODE_WORDS));

        // Test deleteSurroundingText
        int end = text2.length();
        mConnection.setSelection(end, end);
        // Delete the ending space
        assertTrue(mConnection.deleteSurroundingText(1, 2));
        Editable text3 = mConnection.getEditable();
        assertEquals(strLength - 1, text3.length());
        String expectedDelString = "TestCommit";
        assertEquals(expectedDelString, text3.toString());
    }

    /**
     * finishComposingText: 1. The default implementation removes the composing state from the
     *                         current editable text.
     *                      2. In addition, only if dummy mode, a key event is sent for the new
     *                         text and the current editable buffer cleared.
     * setComposingText: The default implementation places the given text into the editable,
     *                  replacing any existing composing text
     */
    @Test
    public void testFinishComposingText() throws Throwable {
        CharSequence str = "TestFinish";
        Editable inputText = Editable.Factory.getInstance().newEditable(str);
        mConnection.commitText(inputText, inputText.length());
        final Editable text = mConnection.getEditable();
        // Test finishComposingText, not dummy mode
        BaseInputConnection.setComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) > -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) > -1);
        mConnection.finishComposingText();
        assertTrue(BaseInputConnection.getComposingSpanStart(text) == -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) == -1);

        mActivityRule.runOnUiThread(() -> {
            assertTrue(mView.requestFocus());
            assertTrue(mView.isFocused());
        });

        // dummy mode
        BaseInputConnection dummyConnection = new BaseInputConnection(mView, false);
        dummyConnection.setComposingText(str, str.length());
        dummyConnection.finishComposingText();
        PollingCheck.waitFor(() -> text.toString().equals(mView.getText().toString()));
    }

    /**
     * Provides standard implementation for sending a key event to the window
     * attached to the input connection's view
     */
    @Test
    public void testSendKeyEvent() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertTrue(mView.requestFocus());
            assertTrue(mView.isFocused());
        });

        // 12-key support
        KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            // 'Q' in case of 12-key(NUMERIC) keyboard
            mConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7));
            mConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7));
        } else {
            mInstrumentation.sendStringSync("q");
            mInstrumentation.waitForIdleSync();
        }
        PollingCheck.waitFor(() -> "q".equals(mView.getText().toString()));
    }

    /**
     * Updates InputMethodManager with the current fullscreen mode.
     */
    @Test
    public void testReportFullscreenMode() {
        InputMethodManager imManager = (InputMethodManager) mInstrumentation.getTargetContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mConnection.reportFullscreenMode(false);
        assertFalse(imManager.isFullscreenMode());
        mConnection.reportFullscreenMode(true);
        // Only IMEs are allowed to report full-screen mode.  Calling this method from the
        // application should have no effect.
        assertFalse(imManager.isFullscreenMode());
    }

    /**
     * An utility method to create an instance of {@link BaseInputConnection} in dummy mode with
     * an initial text and selection range.
     * @param view the {@link View} to be associated with the {@link BaseInputConnection}.
     * @param source the initial text.
     * @return {@link BaseInputConnection} instantiated in dummy mode with {@code source} and
     * selection range from {@code selectionStart} to {@code selectionEnd}
     */
    private static BaseInputConnection createDummyConnectionWithSelection(
            final View view, final CharSequence source) {
        final int selectionStart = Selection.getSelectionStart(source);
        final int selectionEnd = Selection.getSelectionEnd(source);
        final Editable editable = Editable.Factory.getInstance().newEditable(source);
        Selection.setSelection(editable, selectionStart, selectionEnd);
        return new BaseInputConnection(view, false) {
            @Override
            public Editable getEditable() {
                return editable;
            }
        };
    }

    private void verifyDeleteSurroundingTextMain(final String initialState,
            final int deleteBefore, final int deleteAfter, final String expectedState) {
        final CharSequence source = InputConnectionTestUtils.formatString(initialState);
        final BaseInputConnection ic = createDummyConnectionWithSelection(mView, source);
        ic.deleteSurroundingText(deleteBefore, deleteAfter);

        final CharSequence expectedString = InputConnectionTestUtils.formatString(expectedState);
        final int expectedSelectionStart = Selection.getSelectionStart(expectedString);
        final int expectedSelectionEnd = Selection.getSelectionEnd(expectedString);

        // It is sufficient to check the surrounding text up to source.length() characters, because
        // InputConnection.deleteSurroundingText() is not supposed to increase the text length.
        final int retrievalLength = source.length();
        if (expectedSelectionStart == 0) {
            assertTrue(TextUtils.isEmpty(ic.getTextBeforeCursor(retrievalLength, 0)));
        } else {
            assertEquals(expectedString.subSequence(0, expectedSelectionStart).toString(),
                    ic.getTextBeforeCursor(retrievalLength, 0).toString());
        }
        if (expectedSelectionStart == expectedSelectionEnd) {
            assertTrue(TextUtils.isEmpty(ic.getSelectedText(0)));  // null is allowed.
        } else {
            assertEquals(expectedString.subSequence(expectedSelectionStart,
                    expectedSelectionEnd).toString(), ic.getSelectedText(0).toString());
        }
        if (expectedSelectionEnd == expectedString.length()) {
            assertTrue(TextUtils.isEmpty(ic.getTextAfterCursor(retrievalLength, 0)));
        } else {
            assertEquals(expectedString.subSequence(expectedSelectionEnd,
                    expectedString.length()).toString(),
                    ic.getTextAfterCursor(retrievalLength, 0).toString());
        }
    }

    /**
     * Tests {@link BaseInputConnection#deleteSurroundingText(int, int)} comprehensively.
     */
    @Test
    public void testDeleteSurroundingText() throws Throwable {
        verifyDeleteSurroundingTextMain("012[]3456789", 0, 0, "012[]3456789");
        verifyDeleteSurroundingTextMain("012[]3456789", -1, -1, "012[]3456789");
        verifyDeleteSurroundingTextMain("012[]3456789", 1, 2, "01[]56789");
        verifyDeleteSurroundingTextMain("012[]3456789", 10, 1, "[]456789");
        verifyDeleteSurroundingTextMain("012[]3456789", 1, 10, "01[]");
        verifyDeleteSurroundingTextMain("[]0123456789", 3, 3, "[]3456789");
        verifyDeleteSurroundingTextMain("0123456789[]", 3, 3, "0123456[]");
        verifyDeleteSurroundingTextMain("012[345]6789", 0, 0, "012[345]6789");
        verifyDeleteSurroundingTextMain("012[345]6789", -1, -1, "012[345]6789");
        verifyDeleteSurroundingTextMain("012[345]6789", 1, 2, "01[345]89");
        verifyDeleteSurroundingTextMain("012[345]6789", 10, 1, "[345]789");
        verifyDeleteSurroundingTextMain("012[345]6789", 1, 10, "01[345]");
        verifyDeleteSurroundingTextMain("[012]3456789", 3, 3, "[012]6789");
        verifyDeleteSurroundingTextMain("0123456[789]", 3, 3, "0123[789]");
        verifyDeleteSurroundingTextMain("[0123456789]", 0, 0, "[0123456789]");
        verifyDeleteSurroundingTextMain("[0123456789]", 1, 1, "[0123456789]");

        // Surrogate characters do not have any special meanings.  Validating the character sequence
        // is beyond the goal of this API.
        verifyDeleteSurroundingTextMain("0<>[]3456789", 1, 0, "0<[]3456789");
        verifyDeleteSurroundingTextMain("0<>[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0<>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 1, "012[]>56789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 3, "012[]6789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 1, 0, "0<[]3456789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 1, "012[]<56789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 3, "012[]6789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 1, 0, "0>[]3456789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 1, "012[]>56789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 3, "012[]6789");
    }

    private void verifyDeleteSurroundingTextInCodePointsMain(final String initialState,
            final int deleteBeforeInCodePoints, final int deleteAfterInCodePoints,
            final String expectedState) {
        final CharSequence source = InputConnectionTestUtils.formatString(initialState);
        final BaseInputConnection ic = createDummyConnectionWithSelection(mView, source);
        ic.deleteSurroundingTextInCodePoints(deleteBeforeInCodePoints, deleteAfterInCodePoints);

        final CharSequence expectedString = InputConnectionTestUtils.formatString(expectedState);
        final int expectedSelectionStart = Selection.getSelectionStart(expectedString);
        final int expectedSelectionEnd = Selection.getSelectionEnd(expectedString);

        // It is sufficient to check the surrounding text up to source.length() characters, because
        // InputConnection.deleteSurroundingTextInCodePoints() is not supposed to increase the text
        // length.
        final int retrievalLength = source.length();
        if (expectedSelectionStart == 0) {
            assertTrue(TextUtils.isEmpty(ic.getTextBeforeCursor(retrievalLength, 0)));
        } else {
            assertEquals(expectedString.subSequence(0, expectedSelectionStart).toString(),
                    ic.getTextBeforeCursor(retrievalLength, 0).toString());
        }
        if (expectedSelectionStart == expectedSelectionEnd) {
            assertTrue(TextUtils.isEmpty(ic.getSelectedText(0)));  // null is allowed.
        } else {
            assertEquals(expectedString.subSequence(expectedSelectionStart,
                    expectedSelectionEnd).toString(), ic.getSelectedText(0).toString());
        }
        if (expectedSelectionEnd == expectedString.length()) {
            assertTrue(TextUtils.isEmpty(ic.getTextAfterCursor(retrievalLength, 0)));
        } else {
            assertEquals(expectedString.subSequence(expectedSelectionEnd,
                    expectedString.length()).toString(),
                    ic.getTextAfterCursor(retrievalLength, 0).toString());
        }
    }

    /**
     * Tests {@link BaseInputConnection#deleteSurroundingTextInCodePoints(int, int)}
     * comprehensively.
     */
    @Test
    public void testDeleteSurroundingTextInCodePoints() throws Throwable {
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 0, 0, "012[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", -1, -1, "012[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 1, 2, "01[]56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 10, 1, "[]456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 1, 10, "01[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]0123456789", 3, 3, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0123456789[]", 3, 3, "0123456[]");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 0, 0, "012[345]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", -1, -1, "012[345]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 1, 2, "01[345]89");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 10, 1, "[345]789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 1, 10, "01[345]");
        verifyDeleteSurroundingTextInCodePointsMain("[012]3456789", 3, 3, "[012]6789");
        verifyDeleteSurroundingTextInCodePointsMain("0123456[789]", 3, 3, "0123[789]");
        verifyDeleteSurroundingTextInCodePointsMain("[0123456789]", 0, 0, "[0123456789]");
        verifyDeleteSurroundingTextInCodePointsMain("[0123456789]", 1, 1, "[0123456789]");

        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 1, 0, "0[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 2, 0, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 1, "012[]56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 2, "012[]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 3, "012[]789");

        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 0, "[]<><><><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 1, "[]<><><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 2, "[]<><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 3, "[]<><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 4, "[]<>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 5, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 6, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 1000, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 0, 0, "<><><><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 1, 0, "<><><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 2, 0, "<><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 3, 0, "<><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 4, 0, "<>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 5, 0, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 6, 0, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 1000, 0, "[]");

        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 1, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 2, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 3, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 1, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 2, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 3, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 1, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 2, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 3, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 1, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 2, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 3, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 1, 0, "01<[]>456789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 0, 1, "01<[]>456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 1, 0, "<1[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 2, 0, "<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 3, 0, "<12[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 1, 0, "<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 2, 0, "<<>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 3, 0, "<<>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 1, "012[]4>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 2, "012[]>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 3, "012[]34>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 1, "012[]>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 2, "012[]<>>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 3, "012[]<>>6789");

        // Atomicity test.
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 1, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 2, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 3, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 1, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 2, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 3, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 1, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 2, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 3, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 1, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 2, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 3, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 1, 1, "01<[]>456789");

        // Do not verify the character sequences in the selected region.
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 1, 0, "0[><]456789");
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 0, 1, "01[><]56789");
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 1, 1, "0[><]56789");
    }

    @Test
    public void testCloseConnection() {
        final CharSequence source = "0123456789";
        mConnection.commitText(source, source.length());
        final Editable text = mConnection.getEditable();
        BaseInputConnection.setComposingSpans(text, 2, 5);
        assertEquals(2, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(text));

        // BaseInputConnection#closeConnection() must clear the on-going composition.
        mConnection.closeConnection();
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanEnd(text));
    }

    @Test
    public void testGetHandler() {
        // BaseInputConnection must not implement getHandler().
        assertNull(mConnection.getHandler());
    }

    @Test
    public void testCommitContent() {
        final InputContentInfo inputContentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("sample content", new String[]{"image/png"}),
                Uri.parse("https://example.com"));
        // The default implementation should do nothing and just return false.
        assertFalse(mConnection.commitContent(inputContentInfo, 0 /* flags */, null /* opts */));
    }
}
