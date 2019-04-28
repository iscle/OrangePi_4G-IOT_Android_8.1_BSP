/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.car.input;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.TextView;

/**
 * A special EditText for use in-car. This EditText:
 * <ul>
 *     <li>Disables selection</li>
 *     <li>Disables Cut/Copy/Paste</li>
 *     <li>Force-disables suggestions</li>
 * </ul>
 * @hide
 */
public class CarRestrictedEditText extends EditText implements CarEditable {

    private static final boolean SELECTION_CLAMPING_ENABLED = false;

    private int mLastSelEnd = 0;
    private int mLastSelStart = 0;
    private boolean mCursorClamped;

    private CarEditableListener mCarEditableListener;
    private KeyListener mListener;

    public interface KeyListener {
        void onKeyDown(int keyCode);
        void onKeyUp(int keyCode);
        void onCommitText(String input);
        void onCloseKeyboard();
        void onDelete();
    }

    public CarRestrictedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setTextIsSelectable(false);
        setSelection(getText().length());
        mCursorClamped = true;
        setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (mListener != null && actionId == EditorInfo.IME_ACTION_DONE) {
                    mListener.onCloseKeyboard();
                }
                // Return false because we don't want to hijack the default behavior.
                return false;
            }
        });
    }

    public void setKeyListener(KeyListener listener) {
        mListener = listener;
    }

    @SuppressWarnings("unused")
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (mCursorClamped && SELECTION_CLAMPING_ENABLED) {
            setSelection(mLastSelStart, mLastSelEnd);
            return;
        }
        if (mCarEditableListener != null) {
            mCarEditableListener.onUpdateSelection(mLastSelStart, mLastSelEnd, selStart, selEnd);
        }
        mLastSelStart = selStart;
        mLastSelEnd = selEnd;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        return null;
    }

    @Override
    public void setCarEditableListener(CarEditableListener listener) {
        mCarEditableListener = listener;
    }

    @Override
    public void setInputEnabled(boolean enabled) {
        mCursorClamped = !enabled;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
        return new InputConnectionWrapper(inputConnection, false) {
            @Override
            public boolean sendKeyEvent(android.view.KeyEvent event) {
                if (mListener != null) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        mListener.onKeyDown(event.getKeyCode());
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        mListener.onKeyUp(event.getKeyCode());

                        // InputMethodService#sendKeyChar doesn't call
                        // InputConnection#commitText for digit chars.
                        // TODO: fix projected IME to be in coherence with system IME.
                        char unicodeChar = (char) event.getUnicodeChar();
                        if (Character.isDigit(unicodeChar)) {
                            commitText(String.valueOf(unicodeChar), 1);
                        }
                    }
                    return true;
                } else {
                    return super.sendKeyEvent(event);
                }
            }

            @Override
            public boolean commitText(java.lang.CharSequence charSequence, int i) {
                if (mListener != null) {
                    mListener.onCommitText(charSequence.toString());
                    return true;
                }
                return super.commitText(charSequence, i);
            }

            @Override
            public boolean deleteSurroundingText(int i, int i1) {
                if (mListener != null) {
                    mListener.onDelete();
                    return true;
                }
                return super.deleteSurroundingText(i, i1);
            }
        };
    }
}
