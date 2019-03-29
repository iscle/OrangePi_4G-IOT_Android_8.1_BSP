/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.InputType;
import android.text.method.BaseKeyListener;
import android.view.KeyEvent;
import android.widget.TextView.BufferType;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test forward delete key handling of  {@link android.text.method.BaseKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ForwardDeleteTest extends KeyListenerTestCase {
    private static final BaseKeyListener mKeyListener = new BaseKeyListener() {
        public int getInputType() {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        }
    };

    // Sync the state to the TextView and call onKeyDown with KEYCODE_FORWARD_DEL key event.
    // Then update the state to the result of TextView.
    private void forwardDelete(final EditorState state, int modifiers) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mTextView.setText(state.mText, BufferType.EDITABLE);
            mTextView.setKeyListener(mKeyListener);
            mTextView.setSelection(state.mSelectionStart, state.mSelectionEnd);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.hasWindowFocus());

        final KeyEvent keyEvent = getKey(KeyEvent.KEYCODE_FORWARD_DEL, modifiers);
        mActivity.runOnUiThread(() -> mTextView.onKeyDown(keyEvent.getKeyCode(), keyEvent));
        mInstrumentation.waitForIdleSync();

        state.mText = mTextView.getText();
        state.mSelectionStart = mTextView.getSelectionStart();
        state.mSelectionEnd = mTextView.getSelectionEnd();
    }

    @Test
    public void testCRLF() throws Throwable {
        EditorState state = new EditorState();

        // U+000A is LINE FEED.
        state.setByString("| U+000A");
        forwardDelete(state, 0);
        state.assertEquals("|");

        // U+000D is CARRIAGE RETURN.
        state.setByString("| U+000D");
        forwardDelete(state, 0);
        state.assertEquals("|");

        state.setByString("| U+000D U+000A");
        forwardDelete(state, 0);
        state.assertEquals("|");

        state.setByString("| U+000A U+000D");
        forwardDelete(state, 0);
        state.assertEquals("| U+000D");
        forwardDelete(state, 0);
    }

    @Test
    public void testSurrogatePairs() throws Throwable {
        EditorState state = new EditorState();

        // U+1F441 is EYE
        state.setByString("| U+1F441");
        forwardDelete(state, 0);
        state.assertEquals("|");

        // U+1F5E8 is LEFT SPEECH BUBBLE
        state.setByString("| U+1F441 U+1F5E8");
        forwardDelete(state, 0);
        state.assertEquals("| U+1F5E8");
        forwardDelete(state, 0);
        state.assertEquals("|");
    }

    @Test
    public void testReplacementSpan() throws Throwable {
        EditorState state = new EditorState();

        state.setByString("| 'abc' ( 'de' ) 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("| 'bc' ( 'de' ) 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("| 'c' ( 'de' ) 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("| ( 'de' ) 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("| 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("| 'g'");
        forwardDelete(state, 0);
        state.assertEquals("|");

        state.setByString("'abc' [ ( 'de' ) ] 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("'abc' | 'fg'");
        forwardDelete(state, 0);
        state.assertEquals("'abc' | 'g'");
        forwardDelete(state, 0);
        state.assertEquals("'abc' |");
        forwardDelete(state, 0);
        state.assertEquals("'abc' |");

        state.setByString("'ab' [ 'c' ( 'de' ) 'f' ] 'g'");
        forwardDelete(state, 0);
        state.assertEquals("'ab' | 'g'");
        forwardDelete(state, 0);
        state.assertEquals("'ab' |");
        forwardDelete(state, 0);
        state.assertEquals("'ab' |");
    }

    @Test
    public void testCombiningEnclosingKeycaps() throws Throwable {
        EditorState state = new EditorState();

        // U+20E3 is COMBINING ENCLOSING KEYCAP.
        state.setByString("| '1' U+20E3");
        forwardDelete(state, 0);
        state.assertEquals("|");

        state.setByString("| '1' U+FE0F U+20E3");
        forwardDelete(state, 0);
        state.assertEquals("|");
    }

    @Test
    public void testVariationSelector() throws Throwable {
        EditorState state = new EditorState();

        // U+FE0F is VARIATION SELECTOR-16.
        state.setByString("| '#' U+FE0F");
        forwardDelete(state, 0);
        state.assertEquals("|");

        // U+E0100 is VARIATION SELECTOR-17.
        state.setByString("| U+845B U+E0100");
        forwardDelete(state, 0);
        state.assertEquals("|");
    }

    @Test
    public void testFlags() throws Throwable {
        EditorState state = new EditorState();

        // U+1F1FA is REGIONAL INDICATOR SYMBOL LETTER U.
        // U+1F1F8 is REGIONAL INDICATOR SYMBOL LETTER S.
        state.setByString("| U+1F1FA U+1F1F8");
        forwardDelete(state, 0);
        state.assertEquals("|");

        state.setByString("| U+1F1FA U+1F1F8 U+1F1FA U+1F1F8");
        forwardDelete(state, 0);
        state.assertEquals("| U+1F1FA U+1F1F8");
        forwardDelete(state, 0);
        state.assertEquals("|");

        // Single tag_base character
        // U+1F3F4 is WAVING BLACK FLAG. This can be a tag_base character.
        state.setByString("| 'a' U+1F3F4 U+1F3F4 'b'");
        forwardDelete(state, 0);
        state.assertEquals("| U+1F3F4 U+1F3F4 'b'");
        forwardDelete(state, 0);
        state.assertEquals("| U+1F3F4 'b'");
        forwardDelete(state, 0);
        state.assertEquals("| 'b'");

        // U+E0067 is TAG LATIN SMALL LETTER G. This can be a part of tag_spec.
        // U+E0062 is TAG LATIN SMALL LETTER B. This can be a part of tag_spec.
        // U+E0073 is TAG LATIN SMALL LETTER S. This can be a part of tag_spec.
        // U+E0063 is TAG LATIN SMALL LETTER C. This can be a part of tag_spec.
        // U+E0074 is TAG LATIN SMALL LETTER T. This can be a part of tag_spec.
        // U+E007F is CANCEL TAG. This is a tag_term character.
        final String scotland = "U+1F3F4 U+E0067 U+E0062 U+E0073 U+E0063 U+E0074 U+E007F ";

        state.setByString("| 'a' " + scotland + scotland + "'b'");
        forwardDelete(state, 0);
        state.assertEquals("| " + scotland + scotland + "'b'");
        forwardDelete(state, 0);
        state.assertEquals("| " + scotland + "'b'");
        forwardDelete(state, 0);
        state.assertEquals("| 'b'");
    }
}
