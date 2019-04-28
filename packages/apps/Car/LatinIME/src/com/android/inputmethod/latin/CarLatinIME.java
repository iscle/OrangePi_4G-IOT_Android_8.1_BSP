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
package com.android.inputmethod.latin;

import android.car.CarNotConnectedException;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.car.Car;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import com.android.inputmethod.latin.car.KeyboardView;

import java.lang.ref.WeakReference;
import java.util.Locale;

import javax.annotation.concurrent.GuardedBy;

/**
 * IME for car use case. 2 features are added compared to the original IME.
 * <ul>
 *     <li> Monitor driving status, and put a lockout screen on top of the current keyboard if
 *          keyboard input is not allowed.
 *     <li> Add a close keyboard button so that user dismiss the keyboard when "back" button is not
 *          present in the system navigation bar.
 * </ul>
 */
public class CarLatinIME extends InputMethodService {
    private static final String TAG = "CarLatinIME";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String LAYOUT_XML = "input_keyboard_layout";
    private static final String SYMBOL_LAYOUT_XML = "input_keyboard_layout_symbol";

    private static final int KEYCODE_ENTER = '\n';
    private static final int IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1;
    private static final int MSG_ENABLE_KEYBOARD = 0;
    private static final int KEYCODE_CYCLE_CHAR = -7;
    private static final int KEYCODE_MAIN_KEYBOARD = -8;
    private static final int KEYCODE_NUM_KEYBOARD = -9;
    private static final int KEYCODE_ALPHA_KEYBOARD = -10;
    private static final int KEYCODE_CLOSE_KEYBOARD = -99;

    private Keyboard mQweKeyboard;
    private Keyboard mSymbolKeyboard;
    private Car mCar;
    private CarSensorManager mSensorManager;

    private View mLockoutView;
    private KeyboardView mPopupKeyboardView;

    @GuardedBy("this")
    private boolean mKeyboardEnabled = true;
    private KeyboardView mKeyboardView;
    private Locale mLocale;
    private final Handler mHandler;

    private FrameLayout mKeyboardWrapper;
    private EditorInfo mEditorInfo;

    private static final class HideKeyboardHandler extends Handler {
        private final WeakReference<CarLatinIME> mIME;
        public HideKeyboardHandler(CarLatinIME ime) {
            mIME = new WeakReference<CarLatinIME>(ime);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_KEYBOARD:
                    if (mIME.get() != null) {
                        mIME.get().updateKeyboardState(msg.arg1 == 1);
                    }
                    break;
            }
        }
    }

    private final ServiceConnection mCarConnectionListener =
            new ServiceConnection() {
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "Car Service connected");
                    try {
                        mSensorManager = (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
                        mSensorManager.registerListener(mCarSensorListener,
                                CarSensorManager.SENSOR_TYPE_DRIVING_STATUS,
                                CarSensorManager.SENSOR_RATE_FASTEST);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "car not connected", e);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.e(TAG, "CarService: onServiceDisconnedted " + name);
                }
            };

    private final CarSensorManager.OnSensorChangedListener mCarSensorListener =
            new CarSensorManager.OnSensorChangedListener() {
                @Override
                public void onSensorChanged(CarSensorEvent event) {
                    if (event.sensorType != CarSensorManager.SENSOR_TYPE_DRIVING_STATUS) {
                        return;
                    }
                    int drivingStatus = event.getDrivingStatusData(null).status;

                    boolean keyboardEnabled =
                            (drivingStatus & CarSensorEvent.DRIVE_STATUS_NO_KEYBOARD_INPUT) == 0;
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_ENABLE_KEYBOARD, keyboardEnabled ? 1 : 0, 0, null));
                }
            };

    public CarLatinIME() {
        super();
        mHandler = new HideKeyboardHandler(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCar = Car.createCar(this, mCarConnectionListener);
        mCar.connect();

        mQweKeyboard = createKeyboard(LAYOUT_XML);
        mSymbolKeyboard = createKeyboard(SYMBOL_LAYOUT_XML);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    @Override
    public View onCreateInputView() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreateInputView");
        }
        super.onCreateInputView();

        View v = LayoutInflater.from(this).inflate(R.layout.input_keyboard, null);
        mKeyboardView = (KeyboardView) v.findViewById(R.id.keyboard);

        mLockoutView = v.findViewById(R.id.lockout);
        mPopupKeyboardView = (KeyboardView) v.findViewById(R.id.popup_keyboard);
        mKeyboardView.setPopupKeyboardView(mPopupKeyboardView);
        mKeyboardWrapper = (FrameLayout) v.findViewById(R.id.keyboard_wrapper);
        mLockoutView.setBackgroundResource(R.color.ime_background_letters);

        synchronized (this) {
            updateKeyboardStateLocked();
        }
        return v;
    }



    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean reastarting) {
        super.onStartInputView(editorInfo, reastarting);
        mEditorInfo = editorInfo;
        mKeyboardView.setKeyboard(mQweKeyboard, getLocale());
        mKeyboardWrapper.setPadding(0,
                getResources().getDimensionPixelSize(R.dimen.keyboard_padding_vertical), 0, 0);
        mKeyboardView.setOnKeyboardActionListener(mKeyboardActionListener);
        mPopupKeyboardView.setOnKeyboardActionListener(mPopupKeyboardActionListener);
        mKeyboardView.setShifted(mKeyboardView.isShifted());
        updateCapitalization();
    }

    public Locale getLocale() {
        if (mLocale == null) {
            mLocale = this.getResources().getConfiguration().locale;
        }
        return mLocale;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    private Keyboard createKeyboard(String layoutXml) {
        Resources res = this.getResources();
        Configuration configuration = res.getConfiguration();
        Locale oldLocale = configuration.locale;
        configuration.locale = new Locale(DEFAULT_LANGUAGE);
        res.updateConfiguration(configuration, res.getDisplayMetrics());
        Keyboard ret = new Keyboard(
                this, res.getIdentifier(layoutXml, "xml", getPackageName()));
        mLocale = configuration.locale;
        configuration.locale = oldLocale;
        return ret;
    }

    public void updateKeyboardState(boolean enabled) {
        synchronized (this) {
            mKeyboardEnabled = enabled;
            updateKeyboardStateLocked();
        }
    }

    private void updateKeyboardStateLocked() {
        if (mLockoutView == null) {
            return;
        }
        mLockoutView.setVisibility(mKeyboardEnabled ? View.GONE : View.VISIBLE);
    }

    private void toggleCapitalization() {
        mKeyboardView.setShifted(!mKeyboardView.isShifted());
    }

    private void updateCapitalization() {
        boolean shouldCapitalize =
                getCurrentInputConnection().getCursorCapsMode(mEditorInfo.inputType) != 0;
        mKeyboardView.setShifted(shouldCapitalize);
    }

    private final KeyboardView.OnKeyboardActionListener mKeyboardActionListener =
            new KeyboardView.OnKeyboardActionListener() {
                @Override
                public void onPress(int primaryCode) {
                }

                @Override
                public void onRelease(int primaryCode) {
                }

                @Override
                public void onKey(int primaryCode, int[] keyCodes) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                      Log.d(TAG, "onKey " + primaryCode);
                    }
                    InputConnection inputConnection = getCurrentInputConnection();
                    switch (primaryCode) {
                        case Keyboard.KEYCODE_SHIFT:
                            toggleCapitalization();
                            break;
                        case Keyboard.KEYCODE_MODE_CHANGE:
                            if (mKeyboardView.getKeyboard() == mQweKeyboard) {
                                mKeyboardView.setKeyboard(mSymbolKeyboard, getLocale());
                            } else {
                                mKeyboardView.setKeyboard(mQweKeyboard, getLocale());
                            }
                            break;
                        case Keyboard.KEYCODE_DONE:
                            int action = mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
                            inputConnection.performEditorAction(action);
                            break;
                        case Keyboard.KEYCODE_DELETE:
                            inputConnection.deleteSurroundingText(1, 0);
                            updateCapitalization();
                            break;
                        case KEYCODE_MAIN_KEYBOARD:
                            mKeyboardView.setKeyboard(mQweKeyboard, getLocale());
                            break;
                        case KEYCODE_NUM_KEYBOARD:
                            // No number keyboard layout support.
                            break;
                        case KEYCODE_ALPHA_KEYBOARD:
                            //loadKeyboard(ALPHA_LAYOUT_XML);
                            break;
                        case KEYCODE_CLOSE_KEYBOARD:
                            hideWindow();
                            break;
                        case KEYCODE_CYCLE_CHAR:
                            CharSequence text = inputConnection.getTextBeforeCursor(1, 0);
                            if (TextUtils.isEmpty(text)) {
                                break;
                            }

                            char currChar = text.charAt(0);
                            char altChar = cycleCharacter(currChar);
                            // Don't modify text if there is no alternate.
                            if (currChar != altChar) {
                                inputConnection.deleteSurroundingText(1, 0);
                                inputConnection.commitText(String.valueOf(altChar), 1);
                            }
                            break;
                        case KEYCODE_ENTER:
                            final int imeOptionsActionId = getImeOptionsActionIdFromEditorInfo(mEditorInfo);
                            if (IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                                // Either we have an actionLabel and we should performEditorAction with
                                // actionId regardless of its value.
                                inputConnection.performEditorAction(mEditorInfo.actionId);
                            } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                                // We didn't have an actionLabel, but we had another action to execute.
                                // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                                // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                                // means there should be an action and the app didn't bother to set a specific
                                // code for it - presumably it only handles one. It does not have to be treated
                                // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                                // performEditorAction.
                                inputConnection.performEditorAction(imeOptionsActionId);
                            } else {
                                // No action label, and the action from imeOptions is NONE: this is a regular
                                // enter key that should input a carriage return.
                                String txt = Character.toString((char) primaryCode);
                                if (mKeyboardView.isShifted()) {
                                    txt = txt.toUpperCase(mLocale);
                                }
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "commitText " + txt);
                                }
                                inputConnection.commitText(txt, 1);
                                updateCapitalization();
                            }
                            break;
                        default:
                            String commitText = Character.toString((char) primaryCode);
                            // Chars always come through as lowercase, so we have to explicitly
                            // uppercase them if the keyboard is shifted.
                            if (mKeyboardView.isShifted()) {
                                commitText = commitText.toUpperCase(mLocale);
                            }
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                              Log.d(TAG, "commitText " + commitText);
                            }
                            inputConnection.commitText(commitText, 1);
                            updateCapitalization();
                    }
                }

                @Override
                public void onText(CharSequence text) {
                }

                @Override
                public void swipeLeft() {
                }

                @Override
                public void swipeRight() {
                }

                @Override
                public void swipeDown() {
                }

                @Override
                public void swipeUp() {
                }

                @Override
                public void stopInput() {
                    hideWindow();
                }
            };

    private final KeyboardView.OnKeyboardActionListener mPopupKeyboardActionListener =
            new KeyboardView.OnKeyboardActionListener() {
                @Override
                public void onPress(int primaryCode) {
                }

                @Override
                public void onRelease(int primaryCode) {
                }

                @Override
                public void onKey(int primaryCode, int[] keyCodes) {
                    InputConnection inputConnection = getCurrentInputConnection();
                    String commitText = Character.toString((char) primaryCode);
                    // Chars always come through as lowercase, so we have to explicitly
                    // uppercase them if the keyboard is shifted.
                    if (mKeyboardView.isShifted()) {
                        commitText = commitText.toUpperCase(mLocale);
                    }
                    inputConnection.commitText(commitText, 1);
                    updateCapitalization();
                    mKeyboardView.dismissPopupKeyboard();
                }

                @Override
                public void onText(CharSequence text) {
                }

                @Override
                public void swipeLeft() {
                }

                @Override
                public void swipeRight() {
                }

                @Override
                public void swipeDown() {
                }

                @Override
                public void swipeUp() {
                }

                @Override
                public void stopInput() {
                    hideWindow();
                }
            };

    /**
     * Cycle through alternate characters of the given character. Return the same character if
     * there is no alternate.
     */
    private char cycleCharacter(char current) {
        if (Character.isUpperCase(current)) {
            return String.valueOf(current).toLowerCase(mLocale).charAt(0);
        } else {
            return String.valueOf(current).toUpperCase(mLocale).charAt(0);
        }
    }

    private int getImeOptionsActionIdFromEditorInfo(final EditorInfo editorInfo) {
        if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        } else if (editorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL;
        } else {
            // Note: this is different from editorInfo.actionId, hence "ImeOptionsActionId"
            return editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        }
    }
}
