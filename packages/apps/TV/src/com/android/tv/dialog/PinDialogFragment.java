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

package com.android.tv.dialog;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.tv.TvContentRating;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.util.TvSettings;

public class PinDialogFragment extends SafeDismissDialogFragment {
    private static final String TAG = "PinDialogFragment";
    private static final boolean DEBUG = true;

    /**
     * PIN code dialog for unlock channel
     */
    public static final int PIN_DIALOG_TYPE_UNLOCK_CHANNEL = 0;

    /**
     * PIN code dialog for unlock content.
     * Only difference between {@code PIN_DIALOG_TYPE_UNLOCK_CHANNEL} is it's title.
     */
    public static final int PIN_DIALOG_TYPE_UNLOCK_PROGRAM = 1;

    /**
     * PIN code dialog for change parental control settings
     */
    public static final int PIN_DIALOG_TYPE_ENTER_PIN = 2;

    /**
     * PIN code dialog for set new PIN
     */
    public static final int PIN_DIALOG_TYPE_NEW_PIN = 3;

    // PIN code dialog for checking old PIN. This is internal only.
    private static final int PIN_DIALOG_TYPE_OLD_PIN = 4;

    /**
     * PIN code dialog for unlocking DVR playback
     */
    public static final int PIN_DIALOG_TYPE_UNLOCK_DVR = 5;

    private static final int MAX_WRONG_PIN_COUNT = 5;
    private static final int DISABLE_PIN_DURATION_MILLIS = 60 * 1000; // 1 minute

    private static final String INITIAL_TEXT = "—";
    private static final String TRACKER_LABEL = "Pin dialog";
    private static final String ARGS_TYPE = "args_type";
    private static final String ARGS_RATING = "args_rating";

    public static final String DIALOG_TAG = PinDialogFragment.class.getName();

    private static final int NUMBER_PICKERS_RES_ID[] = {
            R.id.first, R.id.second, R.id.third, R.id.fourth };

    private int mType;
    private int mRequestType;
    private boolean mPinChecked;
    private boolean mDismissSilently;

    private TextView mWrongPinView;
    private View mEnterPinView;
    private TextView mTitleView;
    private PinNumberPicker[] mPickers;
    private SharedPreferences mSharedPreferences;
    private String mPrevPin;
    private String mPin;
    private String mRatingString;
    private int mWrongPinCount;
    private long mDisablePinUntil;
    private final Handler mHandler = new Handler();

    public static PinDialogFragment create(int type) {
        return create(type, null);
    }

    public static PinDialogFragment create(int type, String rating) {
        PinDialogFragment fragment = new PinDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARGS_TYPE, type);
        args.putString(ARGS_RATING, rating);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestType = getArguments().getInt(ARGS_TYPE, PIN_DIALOG_TYPE_ENTER_PIN);
        mType = mRequestType;
        mRatingString = getArguments().getString(ARGS_RATING);
        setStyle(STYLE_NO_TITLE, 0);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDisablePinUntil = TvSettings.getDisablePinUntil(getActivity());
        if (ActivityManager.isUserAMonkey()) {
            // Skip PIN dialog half the time for monkeys
            if (Math.random() < 0.5) {
                exit(true);
            }
        }
        mPinChecked = false;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dlg = super.onCreateDialog(savedInstanceState);
        dlg.getWindow().getAttributes().windowAnimations = R.style.pin_dialog_animation;
        PinNumberPicker.loadResources(dlg.getContext());
        return dlg;
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Dialog size is determined by its windows size, not inflated view size.
        // So apply view size to window after the DialogFragment.onStart() where dialog is shown.
        Dialog dlg = getDialog();
        if (dlg != null) {
            dlg.getWindow().setLayout(
                    getResources().getDimensionPixelSize(R.dimen.pin_dialog_width),
                    LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.pin_dialog, container, false);

        mWrongPinView = (TextView) v.findViewById(R.id.wrong_pin);
        mEnterPinView = v.findViewById(R.id.enter_pin);
        mTitleView = (TextView) mEnterPinView.findViewById(R.id.title);
        if (TextUtils.isEmpty(getPin())) {
            // If PIN isn't set, user should set a PIN.
            // Successfully setting a new set is considered as entering correct PIN.
            mType = PIN_DIALOG_TYPE_NEW_PIN;
        }
        switch (mType) {
            case PIN_DIALOG_TYPE_UNLOCK_CHANNEL:
                mTitleView.setText(R.string.pin_enter_unlock_channel);
                break;
            case PIN_DIALOG_TYPE_UNLOCK_PROGRAM:
                mTitleView.setText(R.string.pin_enter_unlock_program);
                break;
            case PIN_DIALOG_TYPE_UNLOCK_DVR:
                TvContentRating tvContentRating =
                        TvContentRating.unflattenFromString(mRatingString);
                if (TvContentRating.UNRATED.equals(tvContentRating)) {
                    mTitleView.setText(getString(R.string.pin_enter_unlock_dvr_unrated));
                } else {
                    mTitleView.setText(
                            getString(
                                    R.string.pin_enter_unlock_dvr,
                                    TvApplication.getSingletons(getContext())
                                            .getTvInputManagerHelper()
                                            .getContentRatingsManager()
                                            .getDisplayNameForRating(tvContentRating)));
                }
                break;
            case PIN_DIALOG_TYPE_ENTER_PIN:
                mTitleView.setText(R.string.pin_enter_pin);
                break;
            case PIN_DIALOG_TYPE_NEW_PIN:
                if (TextUtils.isEmpty(getPin())) {
                    mTitleView.setText(R.string.pin_enter_create_pin);
                } else {
                    mTitleView.setText(R.string.pin_enter_old_pin);
                    mType = PIN_DIALOG_TYPE_OLD_PIN;
                }
        }

        mPickers = new PinNumberPicker[NUMBER_PICKERS_RES_ID.length];
        for (int i = 0; i < NUMBER_PICKERS_RES_ID.length; i++) {
            mPickers[i] = (PinNumberPicker) v.findViewById(NUMBER_PICKERS_RES_ID[i]);
            mPickers[i].setValueRangeAndResetText(0, 9);
            mPickers[i].setPinDialogFragment(this);
            mPickers[i].updateFocus(false);
        }
        for (int i = 0; i < NUMBER_PICKERS_RES_ID.length - 1; i++) {
            mPickers[i].setNextNumberPicker(mPickers[i + 1]);
        }

        if (mType != PIN_DIALOG_TYPE_NEW_PIN) {
            updateWrongPin();
        }
        return v;
    }

    private final Runnable mUpdateEnterPinRunnable = new Runnable() {
        @Override
        public void run() {
            updateWrongPin();
        }
    };

    private void updateWrongPin() {
        if (getActivity() == null) {
            // The activity is already detached. No need to update.
            mHandler.removeCallbacks(null);
            return;
        }

        int remainingSeconds = (int) ((mDisablePinUntil - System.currentTimeMillis()) / 1000);
        boolean enabled = remainingSeconds < 1;
        if (enabled) {
            mWrongPinView.setVisibility(View.INVISIBLE);
            mEnterPinView.setVisibility(View.VISIBLE);
            mWrongPinCount = 0;
        } else {
            mEnterPinView.setVisibility(View.INVISIBLE);
            mWrongPinView.setVisibility(View.VISIBLE);
            mWrongPinView.setText(getResources().getQuantityString(R.plurals.pin_enter_countdown,
                    remainingSeconds, remainingSeconds));
            mHandler.postDelayed(mUpdateEnterPinRunnable, 1000);
        }
    }

    private void exit(boolean pinChecked) {
        mPinChecked = pinChecked;
        dismiss();
    }

    /** Dismisses the pin dialog without calling activity listener. */
    public void dismissSilently() {
        mDismissSilently = true;
        dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (DEBUG) Log.d(TAG, "onDismiss: mPinChecked=" + mPinChecked);
        SoftPreconditions.checkState(getActivity() instanceof OnPinCheckedListener);
        if (!mDismissSilently && getActivity() instanceof OnPinCheckedListener) {
            ((OnPinCheckedListener) getActivity()).onPinChecked(
                    mPinChecked, mRequestType, mRatingString);
        }
        mDismissSilently = false;
    }

    private void handleWrongPin() {
        if (++mWrongPinCount >= MAX_WRONG_PIN_COUNT) {
            mDisablePinUntil = System.currentTimeMillis() + DISABLE_PIN_DURATION_MILLIS;
            TvSettings.setDisablePinUntil(getActivity(), mDisablePinUntil);
            updateWrongPin();
        } else {
            showToast(R.string.pin_toast_wrong);
        }
    }

    private void showToast(int resId) {
        Toast.makeText(getActivity(), resId, Toast.LENGTH_SHORT).show();
    }

    private void done(String pin) {
        if (DEBUG) Log.d(TAG, "done: mType=" + mType + " pin=" + pin + " stored=" + getPin());
        switch (mType) {
            case PIN_DIALOG_TYPE_UNLOCK_CHANNEL:
            case PIN_DIALOG_TYPE_UNLOCK_PROGRAM:
            case PIN_DIALOG_TYPE_UNLOCK_DVR:
            case PIN_DIALOG_TYPE_ENTER_PIN:
                if (TextUtils.isEmpty(getPin()) || pin.equals(getPin())) {
                    exit(true);
                } else {
                    resetPinInput();
                    handleWrongPin();
                }
                break;
            case PIN_DIALOG_TYPE_NEW_PIN:
                resetPinInput();
                if (mPrevPin == null) {
                    mPrevPin = pin;
                    mTitleView.setText(R.string.pin_enter_again);
                } else {
                    if (pin.equals(mPrevPin)) {
                        setPin(pin);
                        exit(true);
                    } else {
                        if (TextUtils.isEmpty(getPin())) {
                            mTitleView.setText(R.string.pin_enter_create_pin);
                        } else {
                            mTitleView.setText(R.string.pin_enter_new_pin);
                        }
                        mPrevPin = null;
                        showToast(R.string.pin_toast_not_match);
                    }
                }
                break;
            case PIN_DIALOG_TYPE_OLD_PIN:
                // Call resetPinInput() here because we'll get additional PIN input
                // regardless of the result.
                resetPinInput();
                if (pin.equals(getPin())) {
                    mType = PIN_DIALOG_TYPE_NEW_PIN;
                    mTitleView.setText(R.string.pin_enter_new_pin);
                } else {
                    handleWrongPin();
                }
                break;
        }
    }

    public int getType() {
        return mType;
    }

    private void setPin(String pin) {
        if (DEBUG) Log.d(TAG, "setPin: " + pin);
        mPin = pin;
        mSharedPreferences.edit().putString(TvSettings.PREF_PIN, pin).apply();
    }

    private String getPin() {
        if (mPin == null) {
            mPin = mSharedPreferences.getString(TvSettings.PREF_PIN, "");
        }
        return mPin;
    }

    private String getPinInput() {
        String result = "";
        try {
            for (PinNumberPicker pnp : mPickers) {
                pnp.updateText();
                result += pnp.getValue();
            }
        } catch (IllegalStateException e) {
            result = "";
        }
        return result;
    }

    private void resetPinInput() {
        for (PinNumberPicker pnp : mPickers) {
            pnp.setValueRangeAndResetText(0, 9);
        }
        mPickers[0].requestFocus();
    }

    public static class PinNumberPicker extends FrameLayout {
        private static final int NUMBER_VIEWS_RES_ID[] = {
            R.id.previous2_number,
            R.id.previous_number,
            R.id.current_number,
            R.id.next_number,
            R.id.next2_number };
        private static final int CURRENT_NUMBER_VIEW_INDEX = 2;
        private static final int NOT_INITIALIZED = Integer.MIN_VALUE;

        private static Animator sFocusedNumberEnterAnimator;
        private static Animator sFocusedNumberExitAnimator;
        private static Animator sAdjacentNumberEnterAnimator;
        private static Animator sAdjacentNumberExitAnimator;

        private static float sAlphaForFocusedNumber;
        private static float sAlphaForAdjacentNumber;

        private int mMinValue;
        private int mMaxValue;
        private int mCurrentValue;
        // a value for setting mCurrentValue at the end of scroll animation.
        private int mNextValue;
        private final int mNumberViewHeight;
        private PinDialogFragment mDialog;
        private PinNumberPicker mNextNumberPicker;
        private boolean mCancelAnimation;

        private final View mNumberViewHolder;
        // When the PinNumberPicker has focus, mBackgroundView will show the focused background.
        // Also, this view is used for handling the text change animation of the current number
        // view which is required when the current number view text is changing from INITIAL_TEXT
        // to "0".
        private final TextView mBackgroundView;
        private final TextView[] mNumberViews;
        private final AnimatorSet mFocusGainAnimator;
        private final AnimatorSet mFocusLossAnimator;
        private final AnimatorSet mScrollAnimatorSet;

        public PinNumberPicker(Context context) {
            this(context, null);
        }

        public PinNumberPicker(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PinNumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public PinNumberPicker(Context context, AttributeSet attrs, int defStyleAttr,
                int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            View view = inflate(context, R.layout.pin_number_picker, this);
            mNumberViewHolder = view.findViewById(R.id.number_view_holder);
            mBackgroundView = (TextView) view.findViewById(R.id.focused_background);
            mNumberViews = new TextView[NUMBER_VIEWS_RES_ID.length];
            for (int i = 0; i < NUMBER_VIEWS_RES_ID.length; ++i) {
                mNumberViews[i] = (TextView) view.findViewById(NUMBER_VIEWS_RES_ID[i]);
            }
            Resources resources = context.getResources();
            mNumberViewHeight = resources.getDimensionPixelSize(
                    R.dimen.pin_number_picker_text_view_height);

            mNumberViewHolder.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateFocus(true);
                }
            });

            mNumberViewHolder.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_DPAD_UP:
                            case KeyEvent.KEYCODE_DPAD_DOWN: {
                                if (mCancelAnimation) {
                                    mScrollAnimatorSet.end();
                                }
                                if (!mScrollAnimatorSet.isRunning()) {
                                    mCancelAnimation = false;
                                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                        mNextValue = adjustValueInValidRange(mCurrentValue + 1);
                                        startScrollAnimation(true);
                                    } else {
                                        mNextValue = adjustValueInValidRange(mCurrentValue - 1);
                                        startScrollAnimation(false);
                                    }
                                }
                                return true;
                            }
                        }
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_DPAD_UP:
                            case KeyEvent.KEYCODE_DPAD_DOWN: {
                                mCancelAnimation = true;
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
            mNumberViewHolder.setScrollY(mNumberViewHeight);

            mFocusGainAnimator = new AnimatorSet();
            mFocusGainAnimator.playTogether(
                    ObjectAnimator.ofFloat(mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1],
                            "alpha", 0f, sAlphaForAdjacentNumber),
                    ObjectAnimator.ofFloat(mNumberViews[CURRENT_NUMBER_VIEW_INDEX],
                            "alpha", sAlphaForFocusedNumber, 0f),
                    ObjectAnimator.ofFloat(mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1],
                            "alpha", 0f, sAlphaForAdjacentNumber),
                    ObjectAnimator.ofFloat(mBackgroundView, "alpha", 0f, 1f));
            mFocusGainAnimator.setDuration(context.getResources().getInteger(
                    android.R.integer.config_shortAnimTime));
            mFocusGainAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX].setText(mBackgroundView.getText());
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX].setAlpha(sAlphaForFocusedNumber);
                    mBackgroundView.setText("");
                }
            });

            mFocusLossAnimator = new AnimatorSet();
            mFocusLossAnimator.playTogether(
                    ObjectAnimator.ofFloat(mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1],
                            "alpha", sAlphaForAdjacentNumber, 0f),
                    ObjectAnimator.ofFloat(mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1],
                            "alpha", sAlphaForAdjacentNumber, 0f),
                    ObjectAnimator.ofFloat(mBackgroundView, "alpha", 1f, 0f));
            mFocusLossAnimator.setDuration(context.getResources().getInteger(
                    android.R.integer.config_shortAnimTime));

            mScrollAnimatorSet = new AnimatorSet();
            mScrollAnimatorSet.setDuration(context.getResources().getInteger(
                    R.integer.pin_number_scroll_duration));
            mScrollAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Set mCurrent value when scroll animation is finished.
                    mCurrentValue = mNextValue;
                    updateText();
                    mNumberViewHolder.setScrollY(mNumberViewHeight);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1].setAlpha(sAlphaForAdjacentNumber);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX].setAlpha(sAlphaForFocusedNumber);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1].setAlpha(sAlphaForAdjacentNumber);
                }
            });
        }

        static void loadResources(Context context) {
            if (sFocusedNumberEnterAnimator == null) {
                TypedValue outValue = new TypedValue();
                context.getResources().getValue(
                        R.dimen.pin_alpha_for_focused_number, outValue, true);
                sAlphaForFocusedNumber = outValue.getFloat();
                context.getResources().getValue(
                        R.dimen.pin_alpha_for_adjacent_number, outValue, true);
                sAlphaForAdjacentNumber = outValue.getFloat();

                sFocusedNumberEnterAnimator = AnimatorInflater.loadAnimator(context,
                        R.animator.pin_focused_number_enter);
                sFocusedNumberExitAnimator = AnimatorInflater.loadAnimator(context,
                        R.animator.pin_focused_number_exit);
                sAdjacentNumberEnterAnimator = AnimatorInflater.loadAnimator(context,
                        R.animator.pin_adjacent_number_enter);
                sAdjacentNumberExitAnimator = AnimatorInflater.loadAnimator(context,
                        R.animator.pin_adjacent_number_exit);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                int keyCode = event.getKeyCode();
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    mNextValue = adjustValueInValidRange(keyCode - KeyEvent.KEYCODE_0);
                    updateFocus(false);
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (mNextNumberPicker == null) {
                        String pin = mDialog.getPinInput();
                        if (!TextUtils.isEmpty(pin)) {
                            mDialog.done(pin);
                        }
                    } else {
                        mNextNumberPicker.requestFocus();
                    }
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        void startScrollAnimation(boolean scrollUp) {
            mFocusGainAnimator.end();
            mFocusLossAnimator.end();
            final ValueAnimator scrollAnimator = ValueAnimator.ofInt(
                    0, scrollUp ? mNumberViewHeight : -mNumberViewHeight);
            scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (Integer) animation.getAnimatedValue();
                    mNumberViewHolder.setScrollY(value + mNumberViewHeight);
                }
            });
            scrollAnimator.setDuration(
                    getResources().getInteger(R.integer.pin_number_scroll_duration));

            if (scrollUp) {
                sAdjacentNumberExitAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1]);
                sFocusedNumberExitAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX]);
                sFocusedNumberEnterAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1]);
                sAdjacentNumberEnterAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 2]);
            } else {
                sAdjacentNumberEnterAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 2]);
                sFocusedNumberEnterAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1]);
                sFocusedNumberExitAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX]);
                sAdjacentNumberExitAnimator.setTarget(mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1]);
            }

            mScrollAnimatorSet.playTogether(scrollAnimator,
                    sAdjacentNumberExitAnimator, sFocusedNumberExitAnimator,
                    sFocusedNumberEnterAnimator, sAdjacentNumberEnterAnimator);
            mScrollAnimatorSet.start();
        }

        void setValueRangeAndResetText(int min, int max) {
            if (min > max) {
                throw new IllegalArgumentException(
                        "The min value should be greater than or equal to the max value");
            } else if (min == NOT_INITIALIZED) {
                throw new IllegalArgumentException(
                        "The min value should be greater than Integer.MIN_VALUE.");
            }
            mMinValue = min;
            mMaxValue = max;
            mNextValue = mCurrentValue = NOT_INITIALIZED;
            for (int i = 0; i < NUMBER_VIEWS_RES_ID.length; ++i) {
                mNumberViews[i].setText(i == CURRENT_NUMBER_VIEW_INDEX ? INITIAL_TEXT : "");
            }
            mBackgroundView.setText(INITIAL_TEXT);
        }

        void setPinDialogFragment(PinDialogFragment dlg) {
            mDialog = dlg;
        }

        void setNextNumberPicker(PinNumberPicker picker) {
            mNextNumberPicker = picker;
        }

        int getValue() {
            if (mCurrentValue < mMinValue || mCurrentValue > mMaxValue) {
                throw new IllegalStateException("Value is not set");
            }
            return mCurrentValue;
        }

        void updateFocus(boolean withAnimation) {
            mScrollAnimatorSet.end();
            mFocusGainAnimator.end();
            mFocusLossAnimator.end();
            updateText();
            if (mNumberViewHolder.isFocused()) {
                if (withAnimation) {
                    mBackgroundView.setText(String.valueOf(mCurrentValue));
                    mFocusGainAnimator.start();
                } else {
                    mBackgroundView.setAlpha(1f);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1].setAlpha(sAlphaForAdjacentNumber);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1].setAlpha(sAlphaForAdjacentNumber);
                }
            } else {
                if (withAnimation) {
                    mFocusLossAnimator.start();
                } else {
                    mBackgroundView.setAlpha(0f);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX - 1].setAlpha(0f);
                    mNumberViews[CURRENT_NUMBER_VIEW_INDEX + 1].setAlpha(0f);
                }
                mNumberViewHolder.setScrollY(mNumberViewHeight);
            }
        }

        private void updateText() {
            boolean wasNotInitialized = false;
            if (mNumberViewHolder.isFocused() && mCurrentValue == NOT_INITIALIZED) {
                mNextValue = mCurrentValue = mMinValue;
                wasNotInitialized = true;
            }
            if (mCurrentValue >= mMinValue && mCurrentValue <= mMaxValue) {
                for (int i = 0; i < NUMBER_VIEWS_RES_ID.length; ++i) {
                    if (wasNotInitialized && i == CURRENT_NUMBER_VIEW_INDEX) {
                        // In order to show the text change animation, keep the text of
                        // mNumberViews[CURRENT_NUMBER_VIEW_INDEX].
                    } else {
                        mNumberViews[i].setText(String.valueOf(adjustValueInValidRange(
                                mCurrentValue - CURRENT_NUMBER_VIEW_INDEX + i)));
                    }
                }
            }
        }

        private int adjustValueInValidRange(int value) {
            int interval = mMaxValue - mMinValue + 1;
            if (value < mMinValue - interval || value > mMaxValue + interval) {
                throw new IllegalArgumentException("The value( " + value
                        + ") is too small or too big to adjust");
            }
            return (value < mMinValue) ? value + interval
                    : (value > mMaxValue) ? value - interval : value;
        }
    }

    /**
     * A listener to the result of {@link PinDialogFragment}. Any activity requiring pin code
     * checking should implement this listener to receive the result.
     */
    public interface OnPinCheckedListener {
        /**
         * Called when {@link PinDialogFragment} is dismissed.
         *
         * @param checked {@code true} if the pin code entered is checked to be correct,
         *                otherwise {@code false}.
         * @param type The dialog type regarding to what pin entering is for.
         * @param rating The target rating to unblock for.
         */
        void onPinChecked(boolean checked, int type, String rating);
    }
}
