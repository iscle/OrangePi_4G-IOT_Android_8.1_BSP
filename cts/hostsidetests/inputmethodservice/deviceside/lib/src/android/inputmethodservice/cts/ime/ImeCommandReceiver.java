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

package android.inputmethodservice.cts.ime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.cts.common.ImeCommandConstants;
import android.inputmethodservice.cts.ime.ImeCommandReceiver.ImeCommandCallbacks;
import android.util.Log;

/**
 * {@link ImeCommandConstants#ACTION_IME_COMMAND} intent receiver.
 */
final class ImeCommandReceiver<T extends InputMethodService & ImeCommandCallbacks>
        extends BroadcastReceiver {

    private static final boolean DEBUG = false;

    interface ImeCommandCallbacks {
        /**
         * Callback method for {@link ImeCommandConstants#COMMAND_COMMIT_TEXT} intent.
         *
         * @param text text to be committed via {@link android.view.inputmethod.InputConnection}.
         * @param newCursorPosition new cursor position after commit.
         */
        void commandCommitText(final CharSequence text, final int newCursorPosition);

        /**
         * Callback method for {@link ImeCommandConstants#COMMAND_SWITCH_INPUT_METHOD} intent.
         *
         * @param imeId IME id to switch.
         */
        void commandSwitchInputMethod(final String imeId);

        /**
         * Callback method for {@link ImeCommandConstants#COMMAND_REQUEST_HIDE_SELF} intent.
         */
        void commandRequestHideSelf(final int flags);
    }

    private T mIme;

    void register(final T ime) {
        mIme = ime;
        ime.registerReceiver(this, new IntentFilter(ImeCommandConstants.ACTION_IME_COMMAND));
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String command = intent.getStringExtra(ImeCommandConstants.EXTRA_COMMAND);
        if (DEBUG) {
            Log.d(mIme.getClass().getSimpleName(), "onReceive: command=" + command);
        }

        switch (command) {
            case ImeCommandConstants.COMMAND_COMMIT_TEXT: {
                final CharSequence text = getCharSequence1(intent);
                final int newCursorPosition = getInt1(intent);
                mIme.commandCommitText(text, newCursorPosition);
                return;
            }
            case ImeCommandConstants.COMMAND_SWITCH_INPUT_METHOD: {
                final String imeId = getString1(intent);
                mIme.commandSwitchInputMethod(imeId);
                return;
            }
            case ImeCommandConstants.COMMAND_REQUEST_HIDE_SELF: {
                final int flags = getInt1(intent);
                mIme.commandRequestHideSelf(flags);
                return;
            }
            default: {
                throw new UnsupportedOperationException("Unknown IME command: " + command);
            }
        }
    }

    private static CharSequence getCharSequence1(final Intent intent) {
        return intent.getCharSequenceExtra(ImeCommandConstants.EXTRA_ARG_CHARSEQUENCE1);
    }

    private static String getString1(final Intent intent) {
        return intent.getStringExtra(ImeCommandConstants.EXTRA_ARG_STRING1);
    }

    private static int getInt1(final Intent intent) {
        if (intent.hasExtra(ImeCommandConstants.EXTRA_ARG_INT1)) {
            return intent.getIntExtra(ImeCommandConstants.EXTRA_ARG_INT1, 0);
        }
        throw new IllegalArgumentException(
                "Needs " + ImeCommandConstants.EXTRA_ARG_INT1 + " in " + intent);
    }
}
