/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.inputmethodservice.cts.common;

/**
 * Constants of IME command android.content.Intent.
 */
public final class ImeCommandConstants {

    // This is constants holding class, can't instantiate.
    private ImeCommandConstants() {}

    /** Intent action in order to record IME events. */
    public static final String ACTION_IME_COMMAND =
            "android.inputmethodservice.cts.action.IME_COMMAND";

    public static final String EXTRA_COMMAND = "command";

    public static final String EXTRA_ARG_CHARSEQUENCE1 = "arg_charsequence1";
    public static final String EXTRA_ARG_STRING1 = "arg_string1";
    public static final String EXTRA_ARG_INT1 = "arg_int1";

    /**
     * This command has the mock IME call {@link android.view.inputmethod.InputConnection#commitText(CharSequence,int) InputConnection#commitText(CharSequence text, int newCursorPosition)}.
     * <ul>
     * <li>argument {@code text} needs to be specified by {@link #EXTRA_ARG_CHARSEQUENCE1}.</li>
     * <li>argument {@code newCursorPosition} needs to be specified by {@link #EXTRA_ARG_INT1}.</li>
     * </ul>
     */
    public static final String COMMAND_COMMIT_TEXT = "commitText";

    /**
     * This command has the mock IME call {@link android.inputmethodservice.InputMethodService#switchInputMethod(String)} InputMethodService#switchInputMethod(String imeId)}.
     * <ul>
     * <li>argument {@code imeId} needs to be specified by {@link #EXTRA_ARG_STRING1}.</li>
     * </ul>
     */
    public static final String COMMAND_SWITCH_INPUT_METHOD = "switchInputMethod";

    /**
     * This command has the mock IME call {@link android.inputmethodservice.InputMethodService#requestHideSelf(int)} InputMethodService#requestHideSelf(int flags)}.
     * <ul>
     * <li>argument {@code flags} needs to be specified by {@link #EXTRA_ARG_INT1}.</li>
     * </ul>
     */
    public static final String COMMAND_REQUEST_HIDE_SELF = "requestHideSelf";
}
