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

import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.HIDE_SOFT_INPUT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_CREATE;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_DESTROY;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_FINISH_INPUT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_FINISH_INPUT_VIEW;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_START_INPUT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.ON_START_INPUT_VIEW;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.SHOW_SOFT_INPUT;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.cts.DeviceEvent;
import android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventTypeParam;
import android.inputmethodservice.cts.ime.ImeCommandReceiver.ImeCommandCallbacks;

import android.os.ResultReceiver;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.function.Consumer;

public abstract class CtsBaseInputMethod extends InputMethodService implements ImeCommandCallbacks {

    protected static final boolean DEBUG = false;

    private final ImeCommandReceiver<CtsBaseInputMethod> mImeCommandReceiver =
            new ImeCommandReceiver<>();
    private String mLogTag;

    private class CtsInputMethodImpl extends InputMethodImpl {
        @Override
        public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            sendEvent(DeviceEvent.builder().setType(SHOW_SOFT_INPUT));
            if (DEBUG) {
                Log.d(mLogTag, "showSoftInput called");
            }
            super.showSoftInput(flags, resultReceiver);
        }

        @Override
        public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            sendEvent(DeviceEvent.builder().setType(HIDE_SOFT_INPUT));
            if (DEBUG) {
                Log.d(mLogTag, "hideSoftInput called");
            }
            super.hideSoftInput(flags, resultReceiver);
        }
    }

    @Override
    public void onCreate() {
        mLogTag = getClass().getSimpleName();
        if (DEBUG) {
            Log.d(mLogTag, "onCreate:");
        }
        sendEvent(DeviceEvent.builder().setType(ON_CREATE));

        super.onCreate();

        mImeCommandReceiver.register(this /* ime */);
    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        if (DEBUG) {
            Log.d(mLogTag, "onStartInput:"
                    + " editorInfo=" + editorInfo
                    + " restarting=" + restarting);
        }

        sendEvent(DeviceEvent.builder()
                .setType(ON_START_INPUT)
                .with(DeviceEventTypeParam.ON_START_INPUT_RESTARTING, restarting));
        super.onStartInput(editorInfo, restarting);
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        if (DEBUG) {
            Log.d(mLogTag, "onStartInputView:"
                    + " editorInfo=" + editorInfo
                    + " restarting=" + restarting);
        }

        sendEvent(DeviceEvent.builder().setType(ON_START_INPUT_VIEW));

        super.onStartInputView(editorInfo, restarting);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (DEBUG) {
            Log.d(mLogTag, "onFinishInputView: finishingInput=" + finishingInput);
        }
        sendEvent(DeviceEvent.builder().setType(ON_FINISH_INPUT_VIEW));

        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        if (DEBUG) {
            Log.d(mLogTag, "onFinishInput:");
        }
        sendEvent(DeviceEvent.builder().setType(ON_FINISH_INPUT));

        super.onFinishInput();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(mLogTag, "onDestroy:");
        }
        sendEvent(DeviceEvent.builder().setType(ON_DESTROY));

        super.onDestroy();

        unregisterReceiver(mImeCommandReceiver);
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        final CtsInputMethodImpl inputMethod = new CtsInputMethodImpl();
        if (DEBUG) {
            Log.d(mLogTag, "onCreateInputMethodInterface");
        }

        return inputMethod;
    }

    //
    // Implementations of {@link ImeCommandCallbacks}.
    //

    @Override
    public void commandCommitText(final CharSequence text, final int newCursorPosition) {
        executeOnInputConnection(ic -> {
            // TODO: Log the return value of {@link InputConnection#commitText(CharSequence,int)}.
            ic.commitText(text, newCursorPosition);
        });
    }

    @Override
    public void commandSwitchInputMethod(final String imeId) {
        switchInputMethod(imeId);
    }

    @Override
    public void commandRequestHideSelf(final int flags) {
        requestHideSelf(flags);
    }

    private void executeOnInputConnection(final Consumer<InputConnection> consumer) {
        final InputConnection ic = getCurrentInputConnection();
        // TODO: Check and log whether {@code ic} is null or equals to
        // {@link #getCurrentInputBindin().getConnection()}.
        if (ic != null) {
            consumer.accept(ic);
        }
    }

   private void sendEvent(final DeviceEvent.IntentBuilder intentBuilder) {
        intentBuilder.setSender(getClass().getName());
        sendBroadcast(intentBuilder.build());
    }
}
