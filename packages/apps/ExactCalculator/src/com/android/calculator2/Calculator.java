/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO: Copy & more general paste in formula?  Note that this requires
//       great care: Currently the text version of a displayed formula
//       is not directly useful for re-evaluating the formula later, since
//       it contains ellipses representing subexpressions evaluated with
//       a different degree mode.  Rather than supporting copy from the
//       formula window, we may eventually want to support generation of a
//       more useful text version in a separate window.  It's not clear
//       this is worth the added (code and user) complexity.

package com.android.calculator2;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Property;
import android.view.ActionMode;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroupOverlay;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.calculator2.CalculatorFormula.OnTextSizeChangeListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.DecimalFormatSymbols;

import static com.android.calculator2.CalculatorFormula.OnFormulaContextMenuClickListener;

public class Calculator extends Activity
        implements OnTextSizeChangeListener, OnLongClickListener,
        AlertDialogFragment.OnClickListener, Evaluator.EvaluationListener /* for main result */,
        DragLayout.CloseCallback, DragLayout.DragCallback {

    private static final String TAG = "Calculator";
    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;

    private enum CalculatorState {
        INPUT,          // Result and formula both visible, no evaluation requested,
                        // Though result may be visible on bottom line.
        EVALUATE,       // Both visible, evaluation requested, evaluation/animation incomplete.
                        // Not used for instant result evaluation.
        INIT,           // Very temporary state used as alternative to EVALUATE
                        // during reinitialization.  Do not animate on completion.
        INIT_FOR_RESULT,  // Identical to INIT, but evaluation is known to terminate
                          // with result, and current expression has been copied to history.
        ANIMATE,        // Result computed, animation to enlarge result window in progress.
        RESULT,         // Result displayed, formula invisible.
                        // If we are in RESULT state, the formula was evaluated without
                        // error to initial precision.
                        // The current formula is now also the last history entry.
        ERROR           // Error displayed: Formula visible, result shows error message.
                        // Display similar to INPUT state.
    }
    // Normal transition sequence is
    // INPUT -> EVALUATE -> ANIMATE -> RESULT (or ERROR) -> INPUT
    // A RESULT -> ERROR transition is possible in rare corner cases, in which
    // a higher precision evaluation exposes an error.  This is possible, since we
    // initially evaluate assuming we were given a well-defined problem.  If we
    // were actually asked to compute sqrt(<extremely tiny negative number>) we produce 0
    // unless we are asked for enough precision that we can distinguish the argument from zero.
    // ERROR and RESULT are translated to INIT or INIT_FOR_RESULT state if the application
    // is restarted in that state.  This leads us to recompute and redisplay the result
    // ASAP. We avoid saving the ANIMATE state or activating history in that state.
    // In INIT_FOR_RESULT, and RESULT state, a copy of the current
    // expression has been saved in the history db; in the other non-ANIMATE states,
    // it has not.
    // TODO: Possibly save a bit more information, e.g. its initial display string
    // or most significant digit position, to speed up restart.

    private final Property<TextView, Integer> TEXT_COLOR =
            new Property<TextView, Integer>(Integer.class, "textColor") {
        @Override
        public Integer get(TextView textView) {
            return textView.getCurrentTextColor();
        }

        @Override
        public void set(TextView textView, Integer textColor) {
            textView.setTextColor(textColor);
        }
    };

    private static final String NAME = "Calculator";
    private static final String KEY_DISPLAY_STATE = NAME + "_display_state";
    private static final String KEY_UNPROCESSED_CHARS = NAME + "_unprocessed_chars";
    /**
     * Associated value is a byte array holding the evaluator state.
     */
    private static final String KEY_EVAL_STATE = NAME + "_eval_state";
    private static final String KEY_INVERSE_MODE = NAME + "_inverse_mode";
    /**
     * Associated value is an boolean holding the visibility state of the toolbar.
     */
    private static final String KEY_SHOW_TOOLBAR = NAME + "_show_toolbar";

    private final ViewTreeObserver.OnPreDrawListener mPreDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            mFormulaContainer.scrollTo(mFormulaText.getRight(), 0);
            final ViewTreeObserver observer = mFormulaContainer.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            return false;
        }
    };

    private final Evaluator.Callback mEvaluatorCallback = new Evaluator.Callback() {
        @Override
        public void onMemoryStateChanged() {
            mFormulaText.onMemoryStateChanged();
        }

        @Override
        public void showMessageDialog(@StringRes int title, @StringRes int message,
                @StringRes int positiveButtonLabel, String tag) {
            AlertDialogFragment.showMessageDialog(Calculator.this, title, message,
                    positiveButtonLabel, tag);

        }
    };

    private final OnDisplayMemoryOperationsListener mOnDisplayMemoryOperationsListener =
            new OnDisplayMemoryOperationsListener() {
        @Override
        public boolean shouldDisplayMemory() {
            return mEvaluator.getMemoryIndex() != 0;
        }
    };

    private final OnFormulaContextMenuClickListener mOnFormulaContextMenuClickListener =
            new OnFormulaContextMenuClickListener() {
        @Override
        public boolean onPaste(ClipData clip) {
            final ClipData.Item item = clip.getItemCount() == 0 ? null : clip.getItemAt(0);
            if (item == null) {
                // nothing to paste, bail early...
                return false;
            }

            // Check if the item is a previously copied result, otherwise paste as raw text.
            final Uri uri = item.getUri();
            if (uri != null && mEvaluator.isLastSaved(uri)) {
                clearIfNotInputState();
                mEvaluator.appendExpr(mEvaluator.getSavedIndex());
                redisplayAfterFormulaChange();
            } else {
                addChars(item.coerceToText(Calculator.this).toString(), false);
            }
            return true;
        }

        @Override
        public void onMemoryRecall() {
            clearIfNotInputState();
            long memoryIndex = mEvaluator.getMemoryIndex();
            if (memoryIndex != 0) {
                mEvaluator.appendExpr(mEvaluator.getMemoryIndex());
                redisplayAfterFormulaChange();
            }
        }
    };


    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            final ViewTreeObserver observer = mFormulaContainer.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(mPreDrawListener);
                observer.addOnPreDrawListener(mPreDrawListener);
            }
        }
    };

    private CalculatorState mCurrentState;
    private Evaluator mEvaluator;

    private CalculatorDisplay mDisplayView;
    private TextView mModeView;
    private CalculatorFormula mFormulaText;
    private CalculatorResult mResultText;
    private HorizontalScrollView mFormulaContainer;
    private DragLayout mDragLayout;

    private ViewPager mPadViewPager;
    private View mDeleteButton;
    private View mClearButton;
    private View mEqualButton;
    private View mMainCalculator;

    private TextView mInverseToggle;
    private TextView mModeToggle;

    private View[] mInvertibleButtons;
    private View[] mInverseButtons;

    private View mCurrentButton;
    private Animator mCurrentAnimator;

    // Characters that were recently entered at the end of the display that have not yet
    // been added to the underlying expression.
    private String mUnprocessedChars = null;

    // Color to highlight unprocessed characters from physical keyboard.
    // TODO: should probably match this to the error color?
    private ForegroundColorSpan mUnprocessedColorSpan = new ForegroundColorSpan(Color.RED);

    // Whether the display is one line.
    private boolean mIsOneLine;

    /**
     * Map the old saved state to a new state reflecting requested result reevaluation.
     */
    private CalculatorState mapFromSaved(CalculatorState savedState) {
        switch (savedState) {
            case RESULT:
            case INIT_FOR_RESULT:
                // Evaluation is expected to terminate normally.
                return CalculatorState.INIT_FOR_RESULT;
            case ERROR:
            case INIT:
                return CalculatorState.INIT;
            case EVALUATE:
            case INPUT:
                return savedState;
            default:  // Includes ANIMATE state.
                throw new AssertionError("Impossible saved state");
        }
    }

    /**
     * Restore Evaluator state and mCurrentState from savedInstanceState.
     * Return true if the toolbar should be visible.
     */
    private void restoreInstanceState(Bundle savedInstanceState) {
        final CalculatorState savedState = CalculatorState.values()[
                savedInstanceState.getInt(KEY_DISPLAY_STATE,
                        CalculatorState.INPUT.ordinal())];
        setState(savedState);
        CharSequence unprocessed = savedInstanceState.getCharSequence(KEY_UNPROCESSED_CHARS);
        if (unprocessed != null) {
            mUnprocessedChars = unprocessed.toString();
        }
        byte[] state = savedInstanceState.getByteArray(KEY_EVAL_STATE);
        if (state != null) {
            try (ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(state))) {
                mEvaluator.restoreInstanceState(in);
            } catch (Throwable ignored) {
                // When in doubt, revert to clean state
                mCurrentState = CalculatorState.INPUT;
                mEvaluator.clearMain();
            }
        }
        if (savedInstanceState.getBoolean(KEY_SHOW_TOOLBAR, true)) {
            showAndMaybeHideToolbar();
        } else {
            mDisplayView.hideToolbar();
        }
        onInverseToggled(savedInstanceState.getBoolean(KEY_INVERSE_MODE));
        // TODO: We're currently not saving and restoring scroll position.
        //       We probably should.  Details may require care to deal with:
        //         - new display size
        //         - slow recomputation if we've scrolled far.
    }

    private void restoreDisplay() {
        onModeChanged(mEvaluator.getDegreeMode(Evaluator.MAIN_INDEX));
        if (mCurrentState != CalculatorState.RESULT
            && mCurrentState != CalculatorState.INIT_FOR_RESULT) {
            redisplayFormula();
        }
        if (mCurrentState == CalculatorState.INPUT) {
            // This resultText will explicitly call evaluateAndNotify when ready.
            mResultText.setShouldEvaluateResult(CalculatorResult.SHOULD_EVALUATE, this);
        } else {
            // Just reevaluate.
            setState(mapFromSaved(mCurrentState));
            // Request evaluation when we know display width.
            mResultText.setShouldEvaluateResult(CalculatorResult.SHOULD_REQUIRE, this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calculator_main);
        setActionBar((Toolbar) findViewById(R.id.toolbar));

        // Hide all default options in the ActionBar.
        getActionBar().setDisplayOptions(0);

        // Ensure the toolbar stays visible while the options menu is displayed.
        getActionBar().addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                mDisplayView.setForceToolbarVisible(isVisible);
            }
        });

        mMainCalculator = findViewById(R.id.main_calculator);
        mDisplayView = (CalculatorDisplay) findViewById(R.id.display);
        mModeView = (TextView) findViewById(R.id.mode);
        mFormulaText = (CalculatorFormula) findViewById(R.id.formula);
        mResultText = (CalculatorResult) findViewById(R.id.result);
        mFormulaContainer = (HorizontalScrollView) findViewById(R.id.formula_container);
        mEvaluator = Evaluator.getInstance(this);
        mEvaluator.setCallback(mEvaluatorCallback);
        mResultText.setEvaluator(mEvaluator, Evaluator.MAIN_INDEX);
        KeyMaps.setActivity(this);

        mPadViewPager = (ViewPager) findViewById(R.id.pad_pager);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        final View numberPad = findViewById(R.id.pad_numeric);
        mEqualButton = numberPad.findViewById(R.id.eq);
        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }
        final TextView decimalPointButton = (TextView) numberPad.findViewById(R.id.dec_point);
        decimalPointButton.setText(getDecimalSeparator());

        mInverseToggle = (TextView) findViewById(R.id.toggle_inv);
        mModeToggle = (TextView) findViewById(R.id.toggle_mode);

        mIsOneLine = mResultText.getVisibility() == View.INVISIBLE;

        mInvertibleButtons = new View[] {
                findViewById(R.id.fun_sin),
                findViewById(R.id.fun_cos),
                findViewById(R.id.fun_tan),
                findViewById(R.id.fun_ln),
                findViewById(R.id.fun_log),
                findViewById(R.id.op_sqrt)
        };
        mInverseButtons = new View[] {
                findViewById(R.id.fun_arcsin),
                findViewById(R.id.fun_arccos),
                findViewById(R.id.fun_arctan),
                findViewById(R.id.fun_exp),
                findViewById(R.id.fun_10pow),
                findViewById(R.id.op_sqr)
        };

        mDragLayout = (DragLayout) findViewById(R.id.drag_layout);
        mDragLayout.removeDragCallback(this);
        mDragLayout.addDragCallback(this);
        mDragLayout.setCloseCallback(this);

        mFormulaText.setOnContextMenuClickListener(mOnFormulaContextMenuClickListener);
        mFormulaText.setOnDisplayMemoryOperationsListener(mOnDisplayMemoryOperationsListener);

        mFormulaText.setOnTextSizeChangeListener(this);
        mFormulaText.addTextChangedListener(mFormulaTextWatcher);
        mDeleteButton.setOnLongClickListener(this);

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            mCurrentState = CalculatorState.INPUT;
            mEvaluator.clearMain();
            showAndMaybeHideToolbar();
            onInverseToggled(false);
        }
        restoreDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDisplayView.isToolbarVisible()) {
            showAndMaybeHideToolbar();
        }
        // If HistoryFragment is showing, hide the main Calculator elements from accessibility.
        // This is because Talkback does not use visibility as a cue for RelativeLayout elements,
        // and RelativeLayout is the base class of DragLayout.
        // If we did not do this, it would be possible to traverse to main Calculator elements from
        // HistoryFragment.
        mMainCalculator.setImportantForAccessibility(
                mDragLayout.isOpen() ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        mEvaluator.cancelAll(true);
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);
        outState.putInt(KEY_DISPLAY_STATE, mCurrentState.ordinal());
        outState.putCharSequence(KEY_UNPROCESSED_CHARS, mUnprocessedChars);
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(byteArrayStream)) {
            mEvaluator.saveInstanceState(out);
        } catch (IOException e) {
            // Impossible; No IO involved.
            throw new AssertionError("Impossible IO exception", e);
        }
        outState.putByteArray(KEY_EVAL_STATE, byteArrayStream.toByteArray());
        outState.putBoolean(KEY_INVERSE_MODE, mInverseToggle.isSelected());
        outState.putBoolean(KEY_SHOW_TOOLBAR, mDisplayView.isToolbarVisible());
        // We must wait for asynchronous writes to complete, since outState may contain
        // references to expressions being written.
        mEvaluator.waitForWrites();
    }

    // Set the state, updating delete label and display colors.
    // This restores display positions on moving to INPUT.
    // But movement/animation for moving to RESULT has already been done.
    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            if (state == CalculatorState.INPUT) {
                // We'll explicitly request evaluation from now on.
                mResultText.setShouldEvaluateResult(CalculatorResult.SHOULD_NOT_EVALUATE, null);
                restoreDisplayPositions();
            }
            mCurrentState = state;

            if (mCurrentState == CalculatorState.RESULT) {
                // No longer do this for ERROR; allow mistakes to be corrected.
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (mIsOneLine) {
                if (mCurrentState == CalculatorState.RESULT
                        || mCurrentState == CalculatorState.EVALUATE
                        || mCurrentState == CalculatorState.ANIMATE) {
                    mFormulaText.setVisibility(View.VISIBLE);
                    mResultText.setVisibility(View.VISIBLE);
                } else if (mCurrentState == CalculatorState.ERROR) {
                    mFormulaText.setVisibility(View.INVISIBLE);
                    mResultText.setVisibility(View.VISIBLE);
                } else {
                    mFormulaText.setVisibility(View.VISIBLE);
                    mResultText.setVisibility(View.INVISIBLE);
                }
            }

            if (mCurrentState == CalculatorState.ERROR) {
                final int errorColor =
                        ContextCompat.getColor(this, R.color.calculator_error_color);
                mFormulaText.setTextColor(errorColor);
                mResultText.setTextColor(errorColor);
                getWindow().setStatusBarColor(errorColor);
            } else if (mCurrentState != CalculatorState.RESULT) {
                mFormulaText.setTextColor(
                        ContextCompat.getColor(this, R.color.display_formula_text_color));
                mResultText.setTextColor(
                        ContextCompat.getColor(this, R.color.display_result_text_color));
                getWindow().setStatusBarColor(
                        ContextCompat.getColor(this, R.color.calculator_statusbar_color));
            }

            invalidateOptionsMenu();
        }
    }

    public boolean isResultLayout() {
        if (mCurrentState == CalculatorState.ANIMATE) {
            throw new AssertionError("impossible state");
        }
        // Note that ERROR has INPUT, not RESULT layout.
        return mCurrentState == CalculatorState.INIT_FOR_RESULT
                || mCurrentState == CalculatorState.RESULT;
    }

    public boolean isOneLine() {
        return mIsOneLine;
    }

    @Override
    protected void onDestroy() {
        mDragLayout.removeDragCallback(this);
        super.onDestroy();
    }

    /**
     * Destroy the evaluator and close the underlying database.
     */
    public void destroyEvaluator() {
        mEvaluator.destroyEvaluator();
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        if (mode.getTag() == CalculatorFormula.TAG_ACTION_MODE) {
            mFormulaContainer.scrollTo(mFormulaText.getRight(), 0);
        }
    }

    /**
     * Stop any active ActionMode or ContextMenu for copy/paste actions.
     * Return true if there was one.
     */
    private boolean stopActionModeOrContextMenu() {
        return mResultText.stopActionModeOrContextMenu()
                || mFormulaText.stopActionModeOrContextMenu();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // If there's an animation in progress, end it immediately, so the user interaction can
        // be handled.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            stopActionModeOrContextMenu();

            final HistoryFragment historyFragment = getHistoryFragment();
            if (mDragLayout.isOpen() && historyFragment != null) {
                historyFragment.stopActionModeOrContextMenu();
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (!stopActionModeOrContextMenu()) {
            final HistoryFragment historyFragment = getHistoryFragment();
            if (mDragLayout.isOpen() && historyFragment != null) {
                if (!historyFragment.stopActionModeOrContextMenu()) {
                    removeHistoryFragment();
                }
                return;
            }
            if (mPadViewPager != null && mPadViewPager.getCurrentItem() != 0) {
                // Select the previous pad.
                mPadViewPager.setCurrentItem(mPadViewPager.getCurrentItem() - 1);
            } else {
                // If the user is currently looking at the first pad (or the pad is not paged),
                // allow the system to handle the Back button.
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Allow the system to handle special key codes (e.g. "BACK" or "DPAD").
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return super.onKeyUp(keyCode, event);
        }

        // Stop the action mode or context menu if it's showing.
        stopActionModeOrContextMenu();

        // Always cancel unrequested in-progress evaluation of the main expression, so that
        // we don't have to worry about subsequent asynchronous completion.
        // Requested in-progress evaluations are handled below.
        cancelUnrequested();

        switch (keyCode) {
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                mCurrentButton = mEqualButton;
                onEquals();
                return true;
            case KeyEvent.KEYCODE_DEL:
                mCurrentButton = mDeleteButton;
                onDelete();
                return true;
            case KeyEvent.KEYCODE_CLEAR:
                mCurrentButton = mClearButton;
                onClear();
                return true;
            default:
                cancelIfEvaluating(false);
                final int raw = event.getKeyCharacterMap().get(keyCode, event.getMetaState());
                if ((raw & KeyCharacterMap.COMBINING_ACCENT) != 0) {
                    return true; // discard
                }
                // Try to discard non-printing characters and the like.
                // The user will have to explicitly delete other junk that gets past us.
                if (Character.isIdentifierIgnorable(raw) || Character.isWhitespace(raw)) {
                    return true;
                }
                char c = (char) raw;
                if (c == '=') {
                    mCurrentButton = mEqualButton;
                    onEquals();
                } else {
                    addChars(String.valueOf(c), true);
                    redisplayAfterFormulaChange();
                }
                return true;
        }
    }

    /**
     * Invoked whenever the inverse button is toggled to update the UI.
     *
     * @param showInverse {@code true} if inverse functions should be shown
     */
    private void onInverseToggled(boolean showInverse) {
        mInverseToggle.setSelected(showInverse);
        if (showInverse) {
            mInverseToggle.setContentDescription(getString(R.string.desc_inv_on));
            for (View invertibleButton : mInvertibleButtons) {
                invertibleButton.setVisibility(View.GONE);
            }
            for (View inverseButton : mInverseButtons) {
                inverseButton.setVisibility(View.VISIBLE);
            }
        } else {
            mInverseToggle.setContentDescription(getString(R.string.desc_inv_off));
            for (View invertibleButton : mInvertibleButtons) {
                invertibleButton.setVisibility(View.VISIBLE);
            }
            for (View inverseButton : mInverseButtons) {
                inverseButton.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Invoked whenever the deg/rad mode may have changed to update the UI. Note that the mode has
     * not necessarily actually changed where this is invoked.
     *
     * @param degreeMode {@code true} if in degree mode
     */
    private void onModeChanged(boolean degreeMode) {
        if (degreeMode) {
            mModeView.setText(R.string.mode_deg);
            mModeView.setContentDescription(getString(R.string.desc_mode_deg));

            mModeToggle.setText(R.string.mode_rad);
            mModeToggle.setContentDescription(getString(R.string.desc_switch_rad));
        } else {
            mModeView.setText(R.string.mode_rad);
            mModeView.setContentDescription(getString(R.string.desc_mode_rad));

            mModeToggle.setText(R.string.mode_deg);
            mModeToggle.setContentDescription(getString(R.string.desc_switch_deg));
        }
    }

    private void removeHistoryFragment() {
        final FragmentManager manager = getFragmentManager();
        if (manager != null && !manager.isDestroyed()) {
            manager.popBackStack(HistoryFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        // When HistoryFragment is hidden, the main Calculator is important for accessibility again.
        mMainCalculator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    /**
     * Switch to INPUT from RESULT state in response to input of the specified button_id.
     * View.NO_ID is treated as an incomplete function id.
     */
    private void switchToInput(int button_id) {
        if (KeyMaps.isBinary(button_id) || KeyMaps.isSuffix(button_id)) {
            mEvaluator.collapse(mEvaluator.getMaxIndex() /* Most recent history entry */);
        } else {
            announceClearedForAccessibility();
            mEvaluator.clearMain();
        }
        setState(CalculatorState.INPUT);
    }

    // Add the given button id to input expression.
    // If appropriate, clear the expression before doing so.
    private void addKeyToExpr(int id) {
        if (mCurrentState == CalculatorState.ERROR) {
            setState(CalculatorState.INPUT);
        } else if (mCurrentState == CalculatorState.RESULT) {
            switchToInput(id);
        }
        if (!mEvaluator.append(id)) {
            // TODO: Some user visible feedback?
        }
    }

    /**
     * Add the given button id to input expression, assuming it was explicitly
     * typed/touched.
     * We perform slightly more aggressive correction than in pasted expressions.
     */
    private void addExplicitKeyToExpr(int id) {
        if (mCurrentState == CalculatorState.INPUT && id == R.id.op_sub) {
            mEvaluator.getExpr(Evaluator.MAIN_INDEX).removeTrailingAdditiveOperators();
        }
        addKeyToExpr(id);
    }

    public void evaluateInstantIfNecessary() {
        if (mCurrentState == CalculatorState.INPUT
                && mEvaluator.getExpr(Evaluator.MAIN_INDEX).hasInterestingOps()) {
            mEvaluator.evaluateAndNotify(Evaluator.MAIN_INDEX, this, mResultText);
        }
    }

    private void redisplayAfterFormulaChange() {
        // TODO: Could do this more incrementally.
        redisplayFormula();
        setState(CalculatorState.INPUT);
        mResultText.clear();
        if (haveUnprocessed()) {
            // Force reevaluation when text is deleted, even if expression is unchanged.
            mEvaluator.touch();
        } else {
            evaluateInstantIfNecessary();
        }
    }

    /**
     * Show the toolbar.
     * Automatically hide it again if it's not relevant to current formula.
     */
    private void showAndMaybeHideToolbar() {
        final boolean shouldBeVisible =
                mCurrentState == CalculatorState.INPUT && mEvaluator.hasTrigFuncs();
        mDisplayView.showToolbar(!shouldBeVisible);
    }

    /**
     * Display or hide the toolbar depending on calculator state.
     */
    private void showOrHideToolbar() {
        final boolean shouldBeVisible =
                mCurrentState == CalculatorState.INPUT && mEvaluator.hasTrigFuncs();
        if (shouldBeVisible) {
            mDisplayView.showToolbar(false);
        } else {
            mDisplayView.hideToolbar();
        }
    }

    public void onButtonClick(View view) {
        // Any animation is ended before we get here.
        mCurrentButton = view;
        stopActionModeOrContextMenu();

        // See onKey above for the rationale behind some of the behavior below:
        cancelUnrequested();

        final int id = view.getId();
        switch (id) {
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                return;  // Toolbar visibility adjusted at end of animation.
            case R.id.toggle_inv:
                final boolean selected = !mInverseToggle.isSelected();
                mInverseToggle.setSelected(selected);
                onInverseToggled(selected);
                if (mCurrentState == CalculatorState.RESULT) {
                    mResultText.redisplay();   // In case we cancelled reevaluation.
                }
                break;
            case R.id.toggle_mode:
                cancelIfEvaluating(false);
                final boolean mode = !mEvaluator.getDegreeMode(Evaluator.MAIN_INDEX);
                if (mCurrentState == CalculatorState.RESULT
                        && mEvaluator.getExpr(Evaluator.MAIN_INDEX).hasTrigFuncs()) {
                    // Capture current result evaluated in old mode.
                    mEvaluator.collapse(mEvaluator.getMaxIndex());
                    redisplayFormula();
                }
                // In input mode, we reinterpret already entered trig functions.
                mEvaluator.setDegreeMode(mode);
                onModeChanged(mode);
                // Show the toolbar to highlight the mode change.
                showAndMaybeHideToolbar();
                setState(CalculatorState.INPUT);
                mResultText.clear();
                if (!haveUnprocessed()) {
                    evaluateInstantIfNecessary();
                }
                return;
            default:
                cancelIfEvaluating(false);
                if (haveUnprocessed()) {
                    // For consistency, append as uninterpreted characters.
                    // This may actually be useful for a left parenthesis.
                    addChars(KeyMaps.toString(this, id), true);
                } else {
                    addExplicitKeyToExpr(id);
                    redisplayAfterFormulaChange();
                }
                break;
        }
        showOrHideToolbar();
    }

    void redisplayFormula() {
        SpannableStringBuilder formula
                = mEvaluator.getExpr(Evaluator.MAIN_INDEX).toSpannableStringBuilder(this);
        if (mUnprocessedChars != null) {
            // Add and highlight characters we couldn't process.
            formula.append(mUnprocessedChars, mUnprocessedColorSpan,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        mFormulaText.changeTextTo(formula);
        mFormulaText.setContentDescription(TextUtils.isEmpty(formula)
                ? getString(R.string.desc_formula) : null);
    }

    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;

        if (view.getId() == R.id.del) {
            onClear();
            return true;
        }
        return false;
    }

    // Initial evaluation completed successfully.  Initiate display.
    public void onEvaluate(long index, int initDisplayPrec, int msd, int leastDigPos,
            String truncatedWholeNumber) {
        if (index != Evaluator.MAIN_INDEX) {
            throw new AssertionError("Unexpected evaluation result index\n");
        }

        // Invalidate any options that may depend on the current result.
        invalidateOptionsMenu();

        mResultText.onEvaluate(index, initDisplayPrec, msd, leastDigPos, truncatedWholeNumber);
        if (mCurrentState != CalculatorState.INPUT) {
            // In EVALUATE, INIT, RESULT, or INIT_FOR_RESULT state.
            onResult(mCurrentState == CalculatorState.EVALUATE /* animate */,
                     mCurrentState == CalculatorState.INIT_FOR_RESULT
                    || mCurrentState == CalculatorState.RESULT /* previously preserved */);
        }
    }

    // Reset state to reflect evaluator cancellation.  Invoked by evaluator.
    public void onCancelled(long index) {
        // Index is Evaluator.MAIN_INDEX. We should be in EVALUATE state.
        setState(CalculatorState.INPUT);
        mResultText.onCancelled(index);
    }

    // Reevaluation completed; ask result to redisplay current value.
    public void onReevaluate(long index) {
        // Index is Evaluator.MAIN_INDEX.
        mResultText.onReevaluate(index);
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) {
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    /**
     * Cancel any in-progress explicitly requested evaluations.
     * @param quiet suppress pop-up message.  Explicit evaluation can change the expression
                    value, and certainly changes the display, so it seems reasonable to warn.
     * @return      true if there was such an evaluation
     */
    private boolean cancelIfEvaluating(boolean quiet) {
        if (mCurrentState == CalculatorState.EVALUATE) {
            mEvaluator.cancel(Evaluator.MAIN_INDEX, quiet);
            return true;
        } else {
            return false;
        }
    }


    private void cancelUnrequested() {
        if (mCurrentState == CalculatorState.INPUT) {
            mEvaluator.cancel(Evaluator.MAIN_INDEX, true);
        }
    }

    private boolean haveUnprocessed() {
        return mUnprocessedChars != null && !mUnprocessedChars.isEmpty();
    }

    private void onEquals() {
        // Ignore if in non-INPUT state, or if there are no operators.
        if (mCurrentState == CalculatorState.INPUT) {
            if (haveUnprocessed()) {
                setState(CalculatorState.EVALUATE);
                onError(Evaluator.MAIN_INDEX, R.string.error_syntax);
            } else if (mEvaluator.getExpr(Evaluator.MAIN_INDEX).hasInterestingOps()) {
                setState(CalculatorState.EVALUATE);
                mEvaluator.requireResult(Evaluator.MAIN_INDEX, this, mResultText);
            }
        }
    }

    private void onDelete() {
        // Delete works like backspace; remove the last character or operator from the expression.
        // Note that we handle keyboard delete exactly like the delete button.  For
        // example the delete button can be used to delete a character from an incomplete
        // function name typed on a physical keyboard.
        // This should be impossible in RESULT state.
        // If there is an in-progress explicit evaluation, just cancel it and return.
        if (cancelIfEvaluating(false)) return;
        setState(CalculatorState.INPUT);
        if (haveUnprocessed()) {
            mUnprocessedChars = mUnprocessedChars.substring(0, mUnprocessedChars.length() - 1);
        } else {
            mEvaluator.delete();
        }
        if (mEvaluator.getExpr(Evaluator.MAIN_INDEX).isEmpty() && !haveUnprocessed()) {
            // Resulting formula won't be announced, since it's empty.
            announceClearedForAccessibility();
        }
        redisplayAfterFormulaChange();
    }

    private void reveal(View sourceView, int colorRes, AnimatorListener listener) {
        final ViewGroupOverlay groupOverlay =
                (ViewGroupOverlay) getWindow().getDecorView().getOverlay();

        final Rect displayRect = new Rect();
        mDisplayView.getGlobalVisibleRect(displayRect);

        // Make reveal cover the display and status bar.
        final View revealView = new View(this);
        revealView.setBottom(displayRect.bottom);
        revealView.setLeft(displayRect.left);
        revealView.setRight(displayRect.right);
        revealView.setBackgroundColor(ContextCompat.getColor(this, colorRes));
        groupOverlay.add(revealView);

        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;

        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();

        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        final Animator revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));
        revealAnimator.addListener(listener);

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_mediumAnimTime));

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(revealAnimator).before(alphaAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                groupOverlay.remove(revealView);
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }

    private void announceClearedForAccessibility() {
        mResultText.announceForAccessibility(getResources().getString(R.string.cleared));
    }

    public void onClearAnimationEnd() {
         mUnprocessedChars = null;
         mResultText.clear();
         mEvaluator.clearMain();
         setState(CalculatorState.INPUT);
         redisplayFormula();
    }

    private void onClear() {
        if (mEvaluator.getExpr(Evaluator.MAIN_INDEX).isEmpty() && !haveUnprocessed()) {
            return;
        }
        cancelIfEvaluating(true);
        announceClearedForAccessibility();
        reveal(mCurrentButton, R.color.calculator_primary_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onClearAnimationEnd();
                showOrHideToolbar();
            }
        });
    }

    // Evaluation encountered en error.  Display the error.
    @Override
    public void onError(final long index, final int errorResourceId) {
        if (index != Evaluator.MAIN_INDEX) {
            throw new AssertionError("Unexpected error source");
        }
        if (mCurrentState == CalculatorState.EVALUATE) {
            setState(CalculatorState.ANIMATE);
            mResultText.announceForAccessibility(getResources().getString(errorResourceId));
            reveal(mCurrentButton, R.color.calculator_error_color,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                           setState(CalculatorState.ERROR);
                           mResultText.onError(index, errorResourceId);
                        }
                    });
        } else if (mCurrentState == CalculatorState.INIT
                || mCurrentState == CalculatorState.INIT_FOR_RESULT /* very unlikely */) {
            setState(CalculatorState.ERROR);
            mResultText.onError(index, errorResourceId);
        } else {
            mResultText.clear();
        }
    }

    // Animate movement of result into the top formula slot.
    // Result window now remains translated in the top slot while the result is displayed.
    // (We convert it back to formula use only when the user provides new input.)
    // Historical note: In the Lollipop version, this invisibly and instantaneously moved
    // formula and result displays back at the end of the animation.  We no longer do that,
    // so that we can continue to properly support scrolling of the result.
    // We assume the result already contains the text to be expanded.
    private void onResult(boolean animate, boolean resultWasPreserved) {
        // Calculate the textSize that would be used to display the result in the formula.
        // For scrollable results just use the minimum textSize to maximize the number of digits
        // that are visible on screen.
        float textSize = mFormulaText.getMinimumTextSize();
        if (!mResultText.isScrollable()) {
            textSize = mFormulaText.getVariableTextSize(mResultText.getText().toString());
        }

        // Scale the result to match the calculated textSize, minimizing the jump-cut transition
        // when a result is reused in a subsequent expression.
        final float resultScale = textSize / mResultText.getTextSize();

        // Set the result's pivot to match its gravity.
        mResultText.setPivotX(mResultText.getWidth() - mResultText.getPaddingRight());
        mResultText.setPivotY(mResultText.getHeight() - mResultText.getPaddingBottom());

        // Calculate the necessary translations so the result takes the place of the formula and
        // the formula moves off the top of the screen.
        final float resultTranslationY = (mFormulaContainer.getBottom() - mResultText.getBottom())
                - (mFormulaText.getPaddingBottom() - mResultText.getPaddingBottom());
        float formulaTranslationY = -mFormulaContainer.getBottom();
        if (mIsOneLine) {
            // Position the result text.
            mResultText.setY(mResultText.getBottom());
            formulaTranslationY = -(findViewById(R.id.toolbar).getBottom()
                    + mFormulaContainer.getBottom());
        }

        // Change the result's textColor to match the formula.
        final int formulaTextColor = mFormulaText.getCurrentTextColor();

        if (resultWasPreserved) {
            // Result was previously addded to history.
            mEvaluator.represerve();
        } else {
            // Add current result to history.
            mEvaluator.preserve(Evaluator.MAIN_INDEX, true);
        }

        if (animate) {
            mResultText.announceForAccessibility(getResources().getString(R.string.desc_eq));
            mResultText.announceForAccessibility(mResultText.getText());
            setState(CalculatorState.ANIMATE);
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofPropertyValuesHolder(mResultText,
                            PropertyValuesHolder.ofFloat(View.SCALE_X, resultScale),
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, resultScale),
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, resultTranslationY)),
                    ObjectAnimator.ofArgb(mResultText, TEXT_COLOR, formulaTextColor),
                    ObjectAnimator.ofFloat(mFormulaContainer, View.TRANSLATION_Y,
                            formulaTranslationY));
            animatorSet.setDuration(getResources().getInteger(
                    android.R.integer.config_longAnimTime));
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setState(CalculatorState.RESULT);
                    mCurrentAnimator = null;
                }
            });

            mCurrentAnimator = animatorSet;
            animatorSet.start();
        } else /* No animation desired; get there fast when restarting */ {
            mResultText.setScaleX(resultScale);
            mResultText.setScaleY(resultScale);
            mResultText.setTranslationY(resultTranslationY);
            mResultText.setTextColor(formulaTextColor);
            mFormulaContainer.setTranslationY(formulaTranslationY);
            setState(CalculatorState.RESULT);
        }
    }

    // Restore positions of the formula and result displays back to their original,
    // pre-animation state.
    private void restoreDisplayPositions() {
        // Clear result.
        mResultText.setText("");
        // Reset all of the values modified during the animation.
        mResultText.setScaleX(1.0f);
        mResultText.setScaleY(1.0f);
        mResultText.setTranslationX(0.0f);
        mResultText.setTranslationY(0.0f);
        mFormulaContainer.setTranslationY(0.0f);

        mFormulaText.requestFocus();
    }

    @Override
    public void onClick(AlertDialogFragment fragment, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (HistoryFragment.CLEAR_DIALOG_TAG.equals(fragment.getTag())) {
                // TODO: Try to preserve the current, saved, and memory expressions. How should we
                // handle expressions to which they refer?
                mEvaluator.clearEverything();
                // TODO: It's not clear what we should really do here. This is an initial hack.
                // May want to make onClearAnimationEnd() private if/when we fix this.
                onClearAnimationEnd();
                mEvaluatorCallback.onMemoryStateChanged();
                onBackPressed();
            } else if (Evaluator.TIMEOUT_DIALOG_TAG.equals(fragment.getTag())) {
                // Timeout extension request.
                mEvaluator.setLongTimeout();
            } else {
                Log.e(TAG, "Unknown AlertDialogFragment click:" + fragment.getTag());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.activity_calculator, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Show the leading option when displaying a result.
        menu.findItem(R.id.menu_leading).setVisible(mCurrentState == CalculatorState.RESULT);

        // Show the fraction option when displaying a rational result.
        boolean visible = mCurrentState == CalculatorState.RESULT;
        final UnifiedReal mainResult = mEvaluator.getResult(Evaluator.MAIN_INDEX);
        // mainResult should never be null, but it happens. Check as a workaround to protect
        // against crashes until we find the root cause (b/34763650).
        visible &= mainResult != null && mainResult.exactlyDisplayable();
        menu.findItem(R.id.menu_fraction).setVisible(visible);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_history:
                showHistoryFragment();
                return true;
            case R.id.menu_leading:
                displayFull();
                return true;
            case R.id.menu_fraction:
                displayFraction();
                return true;
            case R.id.menu_licenses:
                startActivity(new Intent(this, Licenses.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* Begin override CloseCallback method. */

    @Override
    public void onClose() {
        removeHistoryFragment();
    }

    /* End override CloseCallback method. */

    /* Begin override DragCallback methods */

    public void onStartDraggingOpen() {
        mDisplayView.hideToolbar();
        showHistoryFragment();
    }

    @Override
    public void onInstanceStateRestored(boolean isOpen) {
    }

    @Override
    public void whileDragging(float yFraction) {
    }

    @Override
    public boolean shouldCaptureView(View view, int x, int y) {
        return view.getId() == R.id.history_frame
            && (mDragLayout.isMoving() || mDragLayout.isViewUnder(view, x, y));
    }

    @Override
    public int getDisplayHeight() {
        return mDisplayView.getMeasuredHeight();
    }

    /* End override DragCallback methods */

    /**
     * Change evaluation state to one that's friendly to the history fragment.
     * Return false if that was not easily possible.
     */
    private boolean prepareForHistory() {
        if (mCurrentState == CalculatorState.ANIMATE) {
            throw new AssertionError("onUserInteraction should have ended animation");
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // Cancel current evaluation
            cancelIfEvaluating(true /* quiet */ );
            setState(CalculatorState.INPUT);
            return true;
        } else if (mCurrentState == CalculatorState.INIT) {
            // Easiest to just refuse.  Otherwise we can see a state change
            // while in history mode, which causes all sorts of problems.
            // TODO: Consider other alternatives. If we're just doing the decimal conversion
            // at the end of an evaluation, we could treat this as RESULT state.
            return false;
        }
        // We should be in INPUT, INIT_FOR_RESULT, RESULT, or ERROR state.
        return true;
    }

    private HistoryFragment getHistoryFragment() {
        final FragmentManager manager = getFragmentManager();
        if (manager == null || manager.isDestroyed()) {
            return null;
        }
        final Fragment fragment = manager.findFragmentByTag(HistoryFragment.TAG);
        return fragment == null || fragment.isRemoving() ? null : (HistoryFragment) fragment;
    }

    private void showHistoryFragment() {
        final FragmentManager manager = getFragmentManager();
        if (manager == null || manager.isDestroyed()) {
            return;
        }

        if (getHistoryFragment() != null || !prepareForHistory()) {
            return;
        }

        stopActionModeOrContextMenu();
        manager.beginTransaction()
                .replace(R.id.history_frame, new HistoryFragment(), HistoryFragment.TAG)
                .setTransition(FragmentTransaction.TRANSIT_NONE)
                .addToBackStack(HistoryFragment.TAG)
                .commit();

        // When HistoryFragment is visible, hide all descendants of the main Calculator view.
        mMainCalculator.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // TODO: pass current scroll position of result
    }

    private void displayMessage(String title, String message) {
        AlertDialogFragment.showMessageDialog(this, title, message, null, null /* tag */);
    }

    private void displayFraction() {
        UnifiedReal result = mEvaluator.getResult(Evaluator.MAIN_INDEX);
        displayMessage(getString(R.string.menu_fraction),
                KeyMaps.translateResult(result.toNiceString()));
    }

    // Display full result to currently evaluated precision
    private void displayFull() {
        Resources res = getResources();
        String msg = mResultText.getFullText(true /* withSeparators */) + " ";
        if (mResultText.fullTextIsExact()) {
            msg += res.getString(R.string.exact);
        } else {
            msg += res.getString(R.string.approximate);
        }
        displayMessage(getString(R.string.menu_leading), msg);
    }

    /**
     * Add input characters to the end of the expression.
     * Map them to the appropriate button pushes when possible.  Leftover characters
     * are added to mUnprocessedChars, which is presumed to immediately precede the newly
     * added characters.
     * @param moreChars characters to be added
     * @param explicit these characters were explicitly typed by the user, not pasted
     */
    private void addChars(String moreChars, boolean explicit) {
        if (mUnprocessedChars != null) {
            moreChars = mUnprocessedChars + moreChars;
        }
        int current = 0;
        int len = moreChars.length();
        boolean lastWasDigit = false;
        if (mCurrentState == CalculatorState.RESULT && len != 0) {
            // Clear display immediately for incomplete function name.
            switchToInput(KeyMaps.keyForChar(moreChars.charAt(current)));
        }
        char groupingSeparator = KeyMaps.translateResult(",").charAt(0);
        while (current < len) {
            char c = moreChars.charAt(current);
            if (Character.isSpaceChar(c) || c == groupingSeparator) {
                ++current;
                continue;
            }
            int k = KeyMaps.keyForChar(c);
            if (!explicit) {
                int expEnd;
                if (lastWasDigit && current !=
                        (expEnd = Evaluator.exponentEnd(moreChars, current))) {
                    // Process scientific notation with 'E' when pasting, in spite of ambiguity
                    // with base of natural log.
                    // Otherwise the 10^x key is the user's friend.
                    mEvaluator.addExponent(moreChars, current, expEnd);
                    current = expEnd;
                    lastWasDigit = false;
                    continue;
                } else {
                    boolean isDigit = KeyMaps.digVal(k) != KeyMaps.NOT_DIGIT;
                    if (current == 0 && (isDigit || k == R.id.dec_point)
                            && mEvaluator.getExpr(Evaluator.MAIN_INDEX).hasTrailingConstant()) {
                        // Refuse to concatenate pasted content to trailing constant.
                        // This makes pasting of calculator results more consistent, whether or
                        // not the old calculator instance is still around.
                        addKeyToExpr(R.id.op_mul);
                    }
                    lastWasDigit = (isDigit || lastWasDigit && k == R.id.dec_point);
                }
            }
            if (k != View.NO_ID) {
                mCurrentButton = findViewById(k);
                if (explicit) {
                    addExplicitKeyToExpr(k);
                } else {
                    addKeyToExpr(k);
                }
                if (Character.isSurrogate(c)) {
                    current += 2;
                } else {
                    ++current;
                }
                continue;
            }
            int f = KeyMaps.funForString(moreChars, current);
            if (f != View.NO_ID) {
                mCurrentButton = findViewById(f);
                if (explicit) {
                    addExplicitKeyToExpr(f);
                } else {
                    addKeyToExpr(f);
                }
                if (f == R.id.op_sqrt) {
                    // Square root entered as function; don't lose the parenthesis.
                    addKeyToExpr(R.id.lparen);
                }
                current = moreChars.indexOf('(', current) + 1;
                continue;
            }
            // There are characters left, but we can't convert them to button presses.
            mUnprocessedChars = moreChars.substring(current);
            redisplayAfterFormulaChange();
            showOrHideToolbar();
            return;
        }
        mUnprocessedChars = null;
        redisplayAfterFormulaChange();
        showOrHideToolbar();
    }

    private void clearIfNotInputState() {
        if (mCurrentState == CalculatorState.ERROR
                || mCurrentState == CalculatorState.RESULT) {
            setState(CalculatorState.INPUT);
            mEvaluator.clearMain();
        }
    }

    /**
     * Since we only support LTR format, using the RTL comma does not make sense.
     */
    private String getDecimalSeparator() {
        final char defaultSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        final char rtlComma = '\u066b';
        return defaultSeparator == rtlComma ? "," : String.valueOf(defaultSeparator);
    }

    /**
     * Clean up animation for context menu.
     */
    @Override
    public void onContextMenuClosed(Menu menu) {
        stopActionModeOrContextMenu();
    }

    public interface OnDisplayMemoryOperationsListener {
        boolean shouldDisplayMemory();
    }
}
