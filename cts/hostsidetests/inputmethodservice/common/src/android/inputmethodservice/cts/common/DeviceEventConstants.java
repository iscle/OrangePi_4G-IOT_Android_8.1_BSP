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

import android.inputmethodservice.cts.common.test.TestInfo;

/**
 * Constants of device event.
 */
public final class DeviceEventConstants {

    // This is constants holding class, can't instantiate.
    private DeviceEventConstants() {}

    /** Intent action in order to record IME events. */
    public static final String ACTION_DEVICE_EVENT =
            "android.inputmethodservice.cts.action.DEVICE_EVENT";

    /**
     * Intent receiver's package, class, and component name.
     */
    public static final String RECEIVER_PACKAGE = "android.inputmethodservice.cts.provider";
    public static final String RECEIVER_CLASS =
            "android.inputmethodservice.cts.receiver.EventReceiver";
    public static final String RECEIVER_COMPONENT = ComponentNameUtils.buildComponentName(
            RECEIVER_PACKAGE, RECEIVER_CLASS);

    /**
     * Intent extra key for who sends a device event.
     * Values are Input Method class name, for example {@link Ime1Constants#CLASS}, or device test
     * method name, for example {@link TestInfo#getTestName()}).
     *
     * @see android.content.Intent#putExtra(String,String)
     * @see android.content.Intent#getStringExtra(String)
     */
    public static final String EXTRA_EVENT_SENDER = "event_sender";

    /**
     * Intent extra key for Event parameters like
     * {@link DeviceEventTypeParam#ON_START_INPUT_RESTARTING}
     */
    public static final String EXTRA_EVENT_PARAMS = "event_params";

    /**
     * Intent extra key for what type a device event is. Values are {@link DeviceEventType#name()}.
     *
     * @see android.content.Intent#putExtra(String,String)
     * @see android.content.Intent#getStringExtra(String)
     */
    public static final String EXTRA_EVENT_TYPE = "event_type";

    /**
     * Intent extra key for at what time a device event happens. Value is taken from
     * {@code android.os.SystemClock.uptimeMillis()}.
     *
     * @see android.content.Intent#putExtra(String,long)
     * @see android.content.Intent#getLongExtra(String,long)
     */
    public static final String EXTRA_EVENT_TIME = "event_time";

    /**
     * Parameter for {@link DeviceEventType}.
     */
    public enum DeviceEventTypeParam {

        /**
         *  Param for {@link DeviceEventType#ON_START_INPUT}. Represents if IME is restarting.
         */
        ON_START_INPUT_RESTARTING(DeviceEventType.ON_START_INPUT, "onStartInput.restarting");

        private final DeviceEventType mType;
        private final String mName;

        DeviceEventTypeParam(DeviceEventType type, String name) {
            mType = type;
            mName = name;
        }

        public String getName() {
            return mName;
        }
    }

    /**
     * Types of device event, a value of {@link #EXTRA_EVENT_TYPE}.
     */
    public enum DeviceEventType {
        /**
         * {@link android.inputmethodservice.InputMethodService#onCreate() onCreate()} callback.
         */
        ON_CREATE,

        /**
         * {@link android.inputmethodservice.InputMethodService#onStartInput(android.view.inputmethod.EditorInfo,boolean) onStartInput(EditorInfo,boolean}
         * callback.
         */
        ON_START_INPUT,

        /**
         * {@link android.inputmethodservice.InputMethodService#onStartInputView(android.view.inputmethod.EditorInfo, boolean) onStartInputView(EditorInfo,boolean}
         */
        ON_START_INPUT_VIEW,

        /**
         * {@link android.inputmethodservice.InputMethodService#onFinishInputView(boolean) onFinishInputView(boolean)}
         * callback.
         */
        ON_FINISH_INPUT_VIEW,

        /**
         * {@link android.inputmethodservice.InputMethodService#onFinishInput() onFinishInput()}
         * callback.
         */
        ON_FINISH_INPUT,

        /**
         * {@link android.inputmethodservice.InputMethodService#onDestroy() onDestroy()} callback.
         */
        ON_DESTROY,

        /** Test start and end event types. */
        TEST_START,
        TEST_END,

        /**
         * {@link android.view.inputmethod.InputMethod#showSoftInput}
         */
        SHOW_SOFT_INPUT,

        /**
         * {@link android.view.inputmethod.InputMethod#hideSoftInput}
         */
        HIDE_SOFT_INPUT,
    }
}
