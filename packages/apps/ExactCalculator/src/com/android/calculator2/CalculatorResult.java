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

package com.android.calculator2;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.BuildCompat;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;
import android.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// A text widget that is "infinitely" scrollable to the right,
// and obtains the text to display via a callback to Logic.
public class CalculatorResult extends AlignedTextView implements MenuItem.OnMenuItemClickListener,
        Evaluator.EvaluationListener, Evaluator.CharMetricsInfo {
    static final int MAX_RIGHT_SCROLL = 10000000;
    static final int INVALID = MAX_RIGHT_SCROLL + 10000;
        // A larger value is unlikely to avoid running out of space
    final OverScroller mScroller;
    final GestureDetector mGestureDetector;
    private long mIndex;  // Index of expression we are displaying.
    private Evaluator mEvaluator;
    private boolean mScrollable = false;
                            // A scrollable result is currently displayed.
    private boolean mValid = false;
                            // The result holds a valid number (not an error message).
    // A suffix of "Pos" denotes a pixel offset.  Zero represents a scroll position
    // in which the decimal point is just barely visible on the right of the display.
    private int mCurrentPos;// Position of right of display relative to decimal point, in pixels.
                            // Large positive values mean the decimal point is scrolled off the
                            // left of the display.  Zero means decimal point is barely displayed
                            // on the right.
    private int mLastPos;   // Position already reflected in display. Pixels.
    private int mMinPos;    // Minimum position to avoid unnecessary blanks on the left. Pixels.
    private int mMaxPos;    // Maximum position before we start displaying the infinite
                            // sequence of trailing zeroes on the right. Pixels.
    private int mWholeLen;  // Length of the whole part of current result.
    // In the following, we use a suffix of Offset to denote a character position in a numeric
    // string relative to the decimal point.  Positive is to the right and negative is to
    // the left. 1 = tenths position, -1 = units.  Integer.MAX_VALUE is sometimes used
    // for the offset of the last digit in an a nonterminating decimal expansion.
    // We use the suffix "Index" to denote a zero-based index into a string representing a
    // result.
    private int mMaxCharOffset;  // Character offset from decimal point of rightmost digit
                                 // that should be displayed, plus the length of any exponent
                                 // needed to display that digit.
                                 // Limited to MAX_RIGHT_SCROLL. Often the same as:
    private int mLsdOffset;      // Position of least-significant digit in result
    private int mLastDisplayedOffset; // Offset of last digit actually displayed after adding
                                      // exponent.
    private boolean mWholePartFits;  // Scientific notation not needed for initial display.
    private float mNoExponentCredit;
                            // Fraction of digit width saved by avoiding scientific notation.
                            // Only accessed from UI thread.
    private boolean mAppendExponent;
                            // The result fits entirely in the display, even with an exponent,
                            // but not with grouping separators. Since the result is not
                            // scrollable, and we do not add the exponent to max. scroll position,
                            // append an exponent insteadd of replacing trailing digits.
    private final Object mWidthLock = new Object();
                            // Protects the next five fields.  These fields are only
                            // updated by the UI thread, and read accesses by the UI thread
                            // sometimes do not acquire the lock.
    private int mWidthConstraint = 0;
                            // Our total width in pixels minus space for ellipsis.
                            // 0 ==> uninitialized.
    private float mCharWidth = 1;
                            // Maximum character width. For now we pretend that all characters
                            // have this width.
                            // TODO: We're not really using a fixed width font.  But it appears
                            // to be close enough for the characters we use that the difference
                            // is not noticeable.
    private float mGroupingSeparatorWidthRatio;
                            // Fraction of digit width occupied by a digit separator.
    private float mDecimalCredit;
                            // Fraction of digit width saved by replacing digit with decimal point.
    private float mNoEllipsisCredit;
                            // Fraction of digit width saved by both replacing ellipsis with digit
                            // and avoiding scientific notation.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SHOULD_REQUIRE, SHOULD_EVALUATE, SHOULD_NOT_EVALUATE})
    public @interface EvaluationRequest {}
    public static final int SHOULD_REQUIRE = 2;
    public static final int SHOULD_EVALUATE = 1;
    public static final int SHOULD_NOT_EVALUATE = 0;
    @EvaluationRequest private int mEvaluationRequest = SHOULD_REQUIRE;
                            // Should we evaluate when layout completes, and how?
    private Evaluator.EvaluationListener mEvaluationListener = this;
                            // Listener to use if/when evaluation is requested.
    public static final int MAX_LEADING_ZEROES = 6;
                            // Maximum number of leading zeroes after decimal point before we
                            // switch to scientific notation with negative exponent.
    public static final int MAX_TRAILING_ZEROES = 6;
                            // Maximum number of trailing zeroes before the decimal point before
                            // we switch to scientific notation with positive exponent.
    private static final int SCI_NOTATION_EXTRA = 1;
                            // Extra digits for standard scientific notation.  In this case we
                            // have a decimal point and no ellipsis.
                            // We assume that we do not drop digits to make room for the decimal
                            // point in ordinary scientific notation. Thus >= 1.
    private static final int MAX_COPY_EXTRA = 100;
                            // The number of extra digits we are willing to compute to copy
                            // a result as an exact number.
    private static final int MAX_RECOMPUTE_DIGITS = 2000;
                            // The maximum number of digits we're willing to recompute in the UI
                            // thread.  We only do this for known rational results, where we
                            // can bound the computation cost.
    private final ForegroundColorSpan mExponentColorSpan;
    private final BackgroundColorSpan mHighlightSpan;

    private ActionMode mActionMode;
    private ActionMode.Callback mCopyActionModeCallback;
    private ContextMenu mContextMenu;

    // The user requested that the result currently being evaluated should be stored to "memory".
    private boolean mStoreToMemoryRequested = false;

    public CalculatorResult(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new OverScroller(context);
        mHighlightSpan = new BackgroundColorSpan(getHighlightColor());
        mExponentColorSpan = new ForegroundColorSpan(
                ContextCompat.getColor(context, R.color.display_result_exponent_text_color));
        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                        float velocityY) {
                    if (!mScroller.isFinished()) {
                        mCurrentPos = mScroller.getFinalX();
                    }
                    mScroller.forceFinished(true);
                    stopActionModeOrContextMenu();
                    CalculatorResult.this.cancelLongPress();
                    // Ignore scrolls of error string, etc.
                    if (!mScrollable) return true;
                    mScroller.fling(mCurrentPos, 0, - (int) velocityX, 0  /* horizontal only */,
                                    mMinPos, mMaxPos, 0, 0);
                    postInvalidateOnAnimation();
                    return true;
                }
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                        float distanceY) {
                    int distance = (int)distanceX;
                    if (!mScroller.isFinished()) {
                        mCurrentPos = mScroller.getFinalX();
                    }
                    mScroller.forceFinished(true);
                    stopActionModeOrContextMenu();
                    CalculatorResult.this.cancelLongPress();
                    if (!mScrollable) return true;
                    if (mCurrentPos + distance < mMinPos) {
                        distance = mMinPos - mCurrentPos;
                    } else if (mCurrentPos + distance > mMaxPos) {
                        distance = mMaxPos - mCurrentPos;
                    }
                    int duration = (int)(e2.getEventTime() - e1.getEventTime());
                    if (duration < 1 || duration > 100) duration = 10;
                    mScroller.startScroll(mCurrentPos, 0, distance, 0, (int)duration);
                    postInvalidateOnAnimation();
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    if (mValid) {
                        performLongClick();
                    }
                }
            });

        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOnTouchListener(new View.OnTouchListener() {

            // Used to determine whether a touch event should be intercepted.
            private float mInitialDownX;
            private float mInitialDownY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getActionMasked();

                final float x = event.getX();
                final float y = event.getY();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        mInitialDownX = x;
                        mInitialDownY = y;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        final float deltaX = Math.abs(x - mInitialDownX);
                        final float deltaY = Math.abs(y - mInitialDownY);
                        if (deltaX > slop && deltaX > deltaY) {
                            // Prevent the DragLayout from intercepting horizontal scrolls.
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                }
                return mGestureDetector.onTouchEvent(event);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setupActionMode();
        } else {
            setupContextMenu();
        }

        setCursorVisible(false);
        setLongClickable(false);
        setContentDescription(context.getString(R.string.desc_result));
    }

    void setEvaluator(Evaluator evaluator, long index) {
        mEvaluator = evaluator;
        mIndex = index;
        requestLayout();
    }

    // Compute maximum digit width the hard way.
    private static float getMaxDigitWidth(TextPaint paint) {
        // Compute the maximum advance width for each digit, thus accounting for between-character
        // spaces. If we ever support other kinds of digits, we may have to avoid kerning effects
        // that could reduce the advance width within this particular string.
        final String allDigits = "0123456789";
        final float[] widths = new float[allDigits.length()];
        paint.getTextWidths(allDigits, widths);
        float maxWidth = 0;
        for (float x : widths) {
            maxWidth = Math.max(x, maxWidth);
        }
        return maxWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!isLaidOut()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            // Set a minimum height so scaled error messages won't affect our layout.
            setMinimumHeight(getLineHeight() + getCompoundPaddingBottom()
                    + getCompoundPaddingTop());
        }

        final TextPaint paint = getPaint();
        final Context context = getContext();
        final float newCharWidth = getMaxDigitWidth(paint);
        // Digits are presumed to have no more than newCharWidth.
        // There are two instances when we know that the result is otherwise narrower than
        // expected:
        // 1. For standard scientific notation (our type 1), we know that we have a norrow decimal
        // point and no (usually wide) ellipsis symbol. We allow one extra digit
        // (SCI_NOTATION_EXTRA) to compensate, and consider that in determining available width.
        // 2. If we are using digit grouping separators and a decimal point, we give ourselves
        // a fractional extra space for those separators, the value of which depends on whether
        // there is also an ellipsis.
        //
        // Maximum extra space we need in various cases:
        // Type 1 scientific notation, assuming ellipsis, minus sign and E are wider than a digit:
        //    Two minus signs + "E" + "." - 3 digits.
        // Type 2 scientific notation:
        //    Ellipsis + "E" + "-" - 3 digits.
        // In the absence of scientific notation, we may need a little less space.
        // We give ourselves a bit of extra credit towards comma insertion and give
        // ourselves more if we have either
        //    No ellipsis, or
        //    A decimal separator.

        // Calculate extra space we need to reserve, in addition to character count.
        final float decimalSeparatorWidth = Layout.getDesiredWidth(
                context.getString(R.string.dec_point), paint);
        final float minusWidth = Layout.getDesiredWidth(context.getString(R.string.op_sub), paint);
        final float minusExtraWidth = Math.max(minusWidth - newCharWidth, 0.0f);
        final float ellipsisWidth = Layout.getDesiredWidth(KeyMaps.ELLIPSIS, paint);
        final float ellipsisExtraWidth =  Math.max(ellipsisWidth - newCharWidth, 0.0f);
        final float expWidth = Layout.getDesiredWidth(KeyMaps.translateResult("e"), paint);
        final float expExtraWidth =  Math.max(expWidth - newCharWidth, 0.0f);
        final float type1Extra = 2 * minusExtraWidth + expExtraWidth + decimalSeparatorWidth;
        final float type2Extra = ellipsisExtraWidth + expExtraWidth + minusExtraWidth;
        final float extraWidth = Math.max(type1Extra, type2Extra);
        final int intExtraWidth = (int) Math.ceil(extraWidth) + 1 /* to cover rounding sins */;
        final int newWidthConstraint = MeasureSpec.getSize(widthMeasureSpec)
                - (getPaddingLeft() + getPaddingRight()) - intExtraWidth;

        // Calculate other width constants we need to handle grouping separators.
        final float groupingSeparatorW =
                Layout.getDesiredWidth(KeyMaps.translateResult(","), paint);
        // Credits in the absence of any scientific notation:
        float noExponentCredit = extraWidth - Math.max(ellipsisExtraWidth, minusExtraWidth);
        final float noEllipsisCredit = extraWidth - minusExtraWidth;  // includes noExponentCredit.
        final float decimalCredit = Math.max(newCharWidth - decimalSeparatorWidth, 0.0f);

        mNoExponentCredit = noExponentCredit / newCharWidth;
        synchronized(mWidthLock) {
            mWidthConstraint = newWidthConstraint;
            mCharWidth = newCharWidth;
            mNoEllipsisCredit = noEllipsisCredit / newCharWidth;
            mDecimalCredit = decimalCredit / newCharWidth;
            mGroupingSeparatorWidthRatio = groupingSeparatorW / newCharWidth;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mEvaluator != null && mEvaluationRequest != SHOULD_NOT_EVALUATE) {
            final CalculatorExpr expr = mEvaluator.getExpr(mIndex);
            if (expr != null && expr.hasInterestingOps()) {
                if (mEvaluationRequest == SHOULD_REQUIRE) {
                    mEvaluator.requireResult(mIndex, mEvaluationListener, this);
                } else {
                    mEvaluator.evaluateAndNotify(mIndex, mEvaluationListener, this);
                }
            }
        }
    }

    /**
     * Specify whether we should evaluate result on layout.
     * @param should one of SHOULD_REQUIRE, SHOULD_EVALUATE, SHOULD_NOT_EVALUATE
     */
    public void setShouldEvaluateResult(@EvaluationRequest int request,
            Evaluator.EvaluationListener listener) {
        mEvaluationListener = listener;
        mEvaluationRequest = request;
    }

    // From Evaluator.CharMetricsInfo.
    @Override
    public float separatorChars(String s, int len) {
        int start = 0;
        while (start < len && !Character.isDigit(s.charAt(start))) {
            ++start;
        }
        // We assume the rest consists of digits, and for consistency with the rest
        // of the code, we assume all digits have width mCharWidth.
        final int nDigits = len - start;
        // We currently insert a digit separator every three digits.
        final int nSeparators = (nDigits - 1) / 3;
        synchronized(mWidthLock) {
            // Always return an upper bound, even in the presence of rounding errors.
            return nSeparators * mGroupingSeparatorWidthRatio;
        }
    }

    // From Evaluator.CharMetricsInfo.
    @Override
    public float getNoEllipsisCredit() {
        synchronized(mWidthLock) {
            return mNoEllipsisCredit;
        }
    }

    // From Evaluator.CharMetricsInfo.
    @Override
    public float getDecimalCredit() {
        synchronized(mWidthLock) {
            return mDecimalCredit;
        }
    }

    // Return the length of the exponent representation for the given exponent, in
    // characters.
    private final int expLen(int exp) {
        if (exp == 0) return 0;
        final int abs_exp_digits = (int) Math.ceil(Math.log10(Math.abs((double)exp))
                + 0.0000000001d /* Round whole numbers to next integer */);
        return abs_exp_digits + (exp >= 0 ? 1 : 2);
    }

    /**
     * Initiate display of a new result.
     * Only called from UI thread.
     * The parameters specify various properties of the result.
     * @param index Index of expression that was just evaluated. Currently ignored, since we only
     *            expect notification for the expression result being displayed.
     * @param initPrec Initial display precision computed by evaluator. (1 = tenths digit)
     * @param msd Position of most significant digit.  Offset from left of string.
                  Evaluator.INVALID_MSD if unknown.
     * @param leastDigPos Position of least significant digit (1 = tenths digit)
     *                    or Integer.MAX_VALUE.
     * @param truncatedWholePart Result up to but not including decimal point.
                                 Currently we only use the length.
     */
    @Override
    public void onEvaluate(long index, int initPrec, int msd, int leastDigPos,
            String truncatedWholePart) {
        initPositions(initPrec, msd, leastDigPos, truncatedWholePart);

        if (mStoreToMemoryRequested) {
            mEvaluator.copyToMemory(index);
            mStoreToMemoryRequested = false;
        }
        redisplay();
    }

    /**
     * Store the result for this index if it is available.
     * If it is unavailable, set mStoreToMemoryRequested to indicate that we should store
     * when evaluation is complete.
     */
    public void onMemoryStore() {
        if (mEvaluator.hasResult(mIndex)) {
            mEvaluator.copyToMemory(mIndex);
        } else {
            mStoreToMemoryRequested = true;
            mEvaluator.requireResult(mIndex, this /* listener */, this /* CharMetricsInfo */);
        }
    }

    /**
     * Add the result to the value currently in memory.
     */
    public void onMemoryAdd() {
        mEvaluator.addToMemory(mIndex);
    }

    /**
     * Subtract the result from the value currently in memory.
     */
    public void onMemorySubtract() {
        mEvaluator.subtractFromMemory(mIndex);
    }

    /**
     * Set up scroll bounds (mMinPos, mMaxPos, etc.) and determine whether the result is
     * scrollable, based on the supplied information about the result.
     * This is unfortunately complicated because we need to predict whether trailing digits
     * will eventually be replaced by an exponent.
     * Just appending the exponent during formatting would be simpler, but would produce
     * jumpier results during transitions.
     * Only called from UI thread.
     */
    private void initPositions(int initPrecOffset, int msdIndex, int lsdOffset,
            String truncatedWholePart) {
        int maxChars = getMaxChars();
        mWholeLen = truncatedWholePart.length();
        // Allow a tiny amount of slop for associativity/rounding differences in length
        // calculation.  If getPreferredPrec() decided it should fit, we want to make it fit, too.
        // We reserved one extra pixel, so the extra length is OK.
        final int nSeparatorChars = (int) Math.ceil(
                separatorChars(truncatedWholePart, truncatedWholePart.length())
                - getNoEllipsisCredit() - 0.0001f);
        mWholePartFits = mWholeLen + nSeparatorChars <= maxChars;
        mLastPos = INVALID;
        mLsdOffset = lsdOffset;
        mAppendExponent = false;
        // Prevent scrolling past initial position, which is calculated to show leading digits.
        mCurrentPos = mMinPos = (int) Math.round(initPrecOffset * mCharWidth);
        if (msdIndex == Evaluator.INVALID_MSD) {
            // Possible zero value
            if (lsdOffset == Integer.MIN_VALUE) {
                // Definite zero value.
                mMaxPos = mMinPos;
                mMaxCharOffset = (int) Math.round(mMaxPos/mCharWidth);
                mScrollable = false;
            } else {
                // May be very small nonzero value.  Allow user to find out.
                mMaxPos = mMaxCharOffset = MAX_RIGHT_SCROLL;
                mMinPos -= mCharWidth;  // Allow for future minus sign.
                mScrollable = true;
            }
            return;
        }
        int negative = truncatedWholePart.charAt(0) == '-' ? 1 : 0;
        if (msdIndex > mWholeLen && msdIndex <= mWholeLen + 3) {
            // Avoid tiny negative exponent; pretend msdIndex is just to the right of decimal point.
            msdIndex = mWholeLen - 1;
        }
        // Set to position of leftmost significant digit relative to dec. point. Usually negative.
        int minCharOffset = msdIndex - mWholeLen;
        if (minCharOffset > -1 && minCharOffset < MAX_LEADING_ZEROES + 2) {
            // Small number of leading zeroes, avoid scientific notation.
            minCharOffset = -1;
        }
        if (lsdOffset < MAX_RIGHT_SCROLL) {
            mMaxCharOffset = lsdOffset;
            if (mMaxCharOffset < -1 && mMaxCharOffset > -(MAX_TRAILING_ZEROES + 2)) {
                mMaxCharOffset = -1;
            }
            // lsdOffset is positive or negative, never 0.
            int currentExpLen = 0;  // Length of required standard scientific notation exponent.
            if (mMaxCharOffset < -1) {
                currentExpLen = expLen(-minCharOffset - 1);
            } else if (minCharOffset > -1 || mMaxCharOffset >= maxChars) {
                // Number is either entirely to the right of decimal point, or decimal point is
                // not visible when scrolled to the right.
                currentExpLen = expLen(-minCharOffset);
            }
            // Exponent length does not included added decimal point.  But whenever we add a
            // decimal point, we allow an extra character (SCI_NOTATION_EXTRA).
            final int separatorLength = mWholePartFits && minCharOffset < -3 ? nSeparatorChars : 0;
            mScrollable = (mMaxCharOffset + currentExpLen + separatorLength - minCharOffset
                    + negative >= maxChars);
            // Now adjust mMaxCharOffset for any required exponent.
            int newMaxCharOffset;
            if (currentExpLen > 0) {
                if (mScrollable) {
                    // We'll use exponent corresponding to leastDigPos when scrolled to right.
                    newMaxCharOffset = mMaxCharOffset + expLen(-lsdOffset);
                } else {
                    newMaxCharOffset = mMaxCharOffset + currentExpLen;
                }
                if (mMaxCharOffset <= -1 && newMaxCharOffset > -1) {
                    // Very unlikely; just drop exponent.
                    mMaxCharOffset = -1;
                } else {
                    mMaxCharOffset = Math.min(newMaxCharOffset, MAX_RIGHT_SCROLL);
                }
                mMaxPos = Math.min((int) Math.round(mMaxCharOffset * mCharWidth),
                        MAX_RIGHT_SCROLL);
            } else if (!mWholePartFits && !mScrollable) {
                // Corner case in which entire number fits, but not with grouping separators.  We
                // will use an exponent in un-scrolled position, which may hide digits.  Scrolling
                // by one character will remove the exponent and reveal the last digits.  Note
                // that in the forced scientific notation case, the exponent length is not
                // factored into mMaxCharOffset, since we do not want such an increase to impact
                // scrolling behavior.  In the unscrollable case, we thus have to append the
                // exponent at the end using the forcePrecision argument to formatResult, in order
                // to ensure that we get the entire result.
                mScrollable = (mMaxCharOffset + expLen(-minCharOffset - 1) - minCharOffset
                        + negative >= maxChars);
                if (mScrollable) {
                    mMaxPos = (int) Math.ceil(mMinPos + mCharWidth);
                    // Single character scroll will remove exponent and show remaining piece.
                } else {
                    mMaxPos = mMinPos;
                    mAppendExponent = true;
                }
            } else {
                mMaxPos = Math.min((int) Math.round(mMaxCharOffset * mCharWidth),
                        MAX_RIGHT_SCROLL);
            }
            if (!mScrollable) {
                // Position the number consistently with our assumptions to make sure it
                // actually fits.
                mCurrentPos = mMaxPos;
            }
        } else {
            mMaxPos = mMaxCharOffset = MAX_RIGHT_SCROLL;
            mScrollable = true;
        }
    }

    /**
     * Display error message indicated by resourceId.
     * UI thread only.
     */
    @Override
    public void onError(long index, int resourceId) {
        mStoreToMemoryRequested = false;
        mValid = false;
        setLongClickable(false);
        mScrollable = false;
        final String msg = getContext().getString(resourceId);
        final float measuredWidth = Layout.getDesiredWidth(msg, getPaint());
        if (measuredWidth > mWidthConstraint) {
            // Multiply by .99 to avoid rounding effects.
            final float scaleFactor = 0.99f * mWidthConstraint / measuredWidth;
            final RelativeSizeSpan smallTextSpan = new RelativeSizeSpan(scaleFactor);
            final SpannableString scaledMsg = new SpannableString(msg);
            scaledMsg.setSpan(smallTextSpan, 0, msg.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(scaledMsg);
        } else {
            setText(msg);
        }
    }

    private final int MAX_COPY_SIZE = 1000000;

    /*
     * Return the most significant digit position in the given string or Evaluator.INVALID_MSD.
     * Unlike Evaluator.getMsdIndexOf, we treat a final 1 as significant.
     * Pure function; callable from anywhere.
     */
    public static int getNaiveMsdIndexOf(String s) {
        final int len = s.length();
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (c != '-' && c != '.' && c != '0') {
                return i;
            }
        }
        return Evaluator.INVALID_MSD;
    }

    /**
     * Format a result returned by Evaluator.getString() into a single line containing ellipses
     * (if appropriate) and an exponent (if appropriate).
     * We add two distinct kinds of exponents:
     * (1) If the final result contains the leading digit we use standard scientific notation.
     * (2) If not, we add an exponent corresponding to an interpretation of the final result as
     *     an integer.
     * We add an ellipsis on the left if the result was truncated.
     * We add ellipses and exponents in a way that leaves most digits in the position they
     * would have been in had we not done so. This minimizes jumps as a result of scrolling.
     * Result is NOT internationalized, uses "E" for exponent.
     * Called only from UI thread; We sometimes omit locking for fields.
     * @param precOffset The value that was passed to getString. Identifies the significance of
                the rightmost digit. A value of 1 means the rightmost digits corresponds to tenths.
     * @param maxDigs The maximum number of characters in the result
     * @param truncated The in parameter was already truncated, beyond possibly removing the
                minus sign.
     * @param negative The in parameter represents a negative result. (Minus sign may be removed
                without setting truncated.)
     * @param lastDisplayedOffset  If not null, we set lastDisplayedOffset[0] to the offset of
                the last digit actually appearing in the display.
     * @param forcePrecision If true, we make sure that the last displayed digit corresponds to
                precOffset, and allow maxDigs to be exceeded in adding the exponent and commas.
     * @param forceSciNotation Force scientific notation. May be set because we don't have
                space for grouping separators, but whole number otherwise fits.
     * @param insertCommas Insert commas (literally, not internationalized) as digit separators.
                We only ever do this for the integral part of a number, and only when no
                exponent is displayed in the initial position. The combination of which means
                that we only do it when no exponent is displayed.
                We insert commas in a way that does consider the width of the actual localized digit
                separator. Commas count towards maxDigs as the appropriate fraction of a digit.
     */
    private String formatResult(String in, int precOffset, int maxDigs, boolean truncated,
            boolean negative, int lastDisplayedOffset[], boolean forcePrecision,
            boolean forceSciNotation, boolean insertCommas) {
        final int minusSpace = negative ? 1 : 0;
        final int msdIndex = truncated ? -1 : getNaiveMsdIndexOf(in);  // INVALID_MSD is OK.
        String result = in;
        boolean needEllipsis = false;
        if (truncated || (negative && result.charAt(0) != '-')) {
            needEllipsis = true;
            result = KeyMaps.ELLIPSIS + result.substring(1, result.length());
            // Ellipsis may be removed again in the type(1) scientific notation case.
        }
        final int decIndex = result.indexOf('.');
        if (lastDisplayedOffset != null) {
            lastDisplayedOffset[0] = precOffset;
        }
        if (forceSciNotation || (decIndex == -1 || msdIndex != Evaluator.INVALID_MSD
                && msdIndex - decIndex > MAX_LEADING_ZEROES + 1) &&  precOffset != -1) {
            // Either:
            // 1) No decimal point displayed, and it's not just to the right of the last digit, or
            // 2) we are at the front of a number whos integral part is too large to allow
            // comma insertion, or
            // 3) we should suppress leading zeroes.
            // Add an exponent to let the user track which digits are currently displayed.
            // Start with type (2) exponent if we dropped no digits. -1 accounts for decimal point.
            // We currently never show digit separators together with an exponent.
            final int initExponent = precOffset > 0 ? -precOffset : -precOffset - 1;
            int exponent = initExponent;
            boolean hasPoint = false;
            if (!truncated && msdIndex < maxDigs - 1
                    && result.length() - msdIndex + 1 + minusSpace
                    <= maxDigs + SCI_NOTATION_EXTRA) {
                // Type (1) exponent computation and transformation:
                // Leading digit is in display window. Use standard calculator scientific notation
                // with one digit to the left of the decimal point. Insert decimal point and
                // delete leading zeroes.
                // We try to keep leading digits roughly in position, and never
                // lengthen the result by more than SCI_NOTATION_EXTRA.
                if (decIndex > msdIndex) {
                    // In the forceSciNotation, we can have a decimal point in the relevant digit
                    // range. Remove it.
                    result = result.substring(0, decIndex)
                            + result.substring(decIndex + 1, result.length());
                    // msdIndex and precOffset unaffected.
                }
                final int resLen = result.length();
                String fraction = result.substring(msdIndex + 1, resLen);
                result = (negative ? "-" : "") + result.substring(msdIndex, msdIndex + 1)
                        + "." + fraction;
                // Original exp was correct for decimal point at right of fraction.
                // Adjust by length of fraction.
                exponent = initExponent + resLen - msdIndex - 1;
                hasPoint = true;
            }
            // Exponent can't be zero.
            // Actually add the exponent of either type:
            if (!forcePrecision) {
                int dropDigits;  // Digits to drop to make room for exponent.
                if (hasPoint) {
                    // Type (1) exponent.
                    // Drop digits even if there is room. Otherwise the scrolling gets jumpy.
                    dropDigits = expLen(exponent);
                    if (dropDigits >= result.length() - 1) {
                        // Jumpy is better than no mantissa.  Probably impossible anyway.
                        dropDigits = Math.max(result.length() - 2, 0);
                    }
                } else {
                    // Type (2) exponent.
                    // Exponent depends on the number of digits we drop, which depends on
                    // exponent ...
                    for (dropDigits = 2; expLen(initExponent + dropDigits) > dropDigits;
                            ++dropDigits) {}
                    exponent = initExponent + dropDigits;
                    if (precOffset - dropDigits > mLsdOffset) {
                        // This can happen if e.g. result = 10^40 + 10^10
                        // It turns out we would otherwise display ...10e9 because it takes
                        // the same amount of space as ...1e10 but shows one more digit.
                        // But we don't want to display a trailing zero, even if it's free.
                        ++dropDigits;
                        ++exponent;
                    }
                }
                if (dropDigits >= result.length() - 1) {
                    // Display too small to show meaningful result.
                    return KeyMaps.ELLIPSIS + "E" + KeyMaps.ELLIPSIS;
                }
                result = result.substring(0, result.length() - dropDigits);
                if (lastDisplayedOffset != null) {
                    lastDisplayedOffset[0] -= dropDigits;
                }
            }
            result = result + "E" + Integer.toString(exponent);
        } else if (insertCommas) {
            // Add commas to the whole number section, and then truncate on left to fit,
            // counting commas as a fractional digit.
            final int wholeStart = needEllipsis ? 1 : 0;
            int orig_length = result.length();
            final float nCommaChars;
            if (decIndex != -1) {
                nCommaChars = separatorChars(result, decIndex);
                result = StringUtils.addCommas(result, wholeStart, decIndex)
                        + result.substring(decIndex, orig_length);
            } else {
                nCommaChars = separatorChars(result, orig_length);
                result = StringUtils.addCommas(result, wholeStart, orig_length);
            }
            if (needEllipsis) {
                orig_length -= 1;  // Exclude ellipsis.
            }
            final float len = orig_length + nCommaChars;
            int deletedChars = 0;
            final float ellipsisCredit = getNoEllipsisCredit();
            final float decimalCredit = getDecimalCredit();
            final float effectiveLen = len - (decIndex == -1 ? 0 : getDecimalCredit());
            final float ellipsisAdjustment =
                    needEllipsis ? mNoExponentCredit : getNoEllipsisCredit();
            // As above, we allow for a tiny amount of extra length here, for consistency with
            // getPreferredPrec().
            if (effectiveLen - ellipsisAdjustment > (float) (maxDigs - wholeStart) + 0.0001f
                && !forcePrecision) {
                float deletedWidth = 0.0f;
                while (effectiveLen - mNoExponentCredit - deletedWidth
                        > (float) (maxDigs - 1 /* for ellipsis */)) {
                    if (result.charAt(deletedChars) == ',') {
                        deletedWidth += mGroupingSeparatorWidthRatio;
                    } else {
                        deletedWidth += 1.0f;
                    }
                    deletedChars++;
                }
            }
            if (deletedChars > 0) {
                result = KeyMaps.ELLIPSIS + result.substring(deletedChars, result.length());
            } else if (needEllipsis) {
                result = KeyMaps.ELLIPSIS + result;
            }
        }
        return result;
    }

    /**
     * Get formatted, but not internationalized, result from mEvaluator.
     * @param precOffset requested position (1 = tenths) of last included digit
     * @param maxSize maximum number of characters (more or less) in result
     * @param lastDisplayedOffset zeroth entry is set to actual offset of last included digit,
     *                            after adjusting for exponent, etc.  May be null.
     * @param forcePrecision Ensure that last included digit is at pos, at the expense
     *                       of treating maxSize as a soft limit.
     * @param forceSciNotation Force scientific notation, even if not required by maxSize.
     * @param insertCommas Insert commas as digit separators.
     */
    private String getFormattedResult(int precOffset, int maxSize, int lastDisplayedOffset[],
            boolean forcePrecision, boolean forceSciNotation, boolean insertCommas) {
        final boolean truncated[] = new boolean[1];
        final boolean negative[] = new boolean[1];
        final int requestedPrecOffset[] = {precOffset};
        final String rawResult = mEvaluator.getString(mIndex, requestedPrecOffset, mMaxCharOffset,
                maxSize, truncated, negative, this);
        return formatResult(rawResult, requestedPrecOffset[0], maxSize, truncated[0], negative[0],
                lastDisplayedOffset, forcePrecision, forceSciNotation, insertCommas);
   }

    /**
     * Return entire result (within reason) up to current displayed precision.
     * @param withSeparators  Add digit separators
     */
    public String getFullText(boolean withSeparators) {
        if (!mValid) return "";
        if (!mScrollable) return getText().toString();
        return KeyMaps.translateResult(getFormattedResult(mLastDisplayedOffset, MAX_COPY_SIZE,
                null, true /* forcePrecision */, false /* forceSciNotation */, withSeparators));
    }

    /**
     * Did the above produce a correct result?
     * UI thread only.
     */
    public boolean fullTextIsExact() {
        return !mScrollable || (getCharOffset(mMaxPos) == getCharOffset(mCurrentPos)
                && mMaxCharOffset != MAX_RIGHT_SCROLL);
    }

    /**
     * Get entire result up to current displayed precision, or up to MAX_COPY_EXTRA additional
     * digits, if it will lead to an exact result.
     */
    public String getFullCopyText() {
        if (!mValid
                || mLsdOffset == Integer.MAX_VALUE
                || fullTextIsExact()
                || mWholeLen > MAX_RECOMPUTE_DIGITS
                || mWholeLen + mLsdOffset > MAX_RECOMPUTE_DIGITS
                || mLsdOffset - mLastDisplayedOffset > MAX_COPY_EXTRA) {
            return getFullText(false /* withSeparators */);
        }
        // It's reasonable to compute and copy the exact result instead.
        int fractionLsdOffset = Math.max(0, mLsdOffset);
        String rawResult = mEvaluator.getResult(mIndex).toStringTruncated(fractionLsdOffset);
        if (mLsdOffset <= -1) {
            // Result has trailing decimal point. Remove it.
            rawResult = rawResult.substring(0, rawResult.length() - 1);
            fractionLsdOffset = -1;
        }
        final String formattedResult = formatResult(rawResult, fractionLsdOffset, MAX_COPY_SIZE,
                false, rawResult.charAt(0) == '-', null, true /* forcePrecision */,
                false /* forceSciNotation */, false /* insertCommas */);
        return KeyMaps.translateResult(formattedResult);
    }

    /**
     * Return the maximum number of characters that will fit in the result display.
     * May be called asynchronously from non-UI thread. From Evaluator.CharMetricsInfo.
     * Returns zero if measurement hasn't completed.
     */
    @Override
    public int getMaxChars() {
        int result;
        synchronized(mWidthLock) {
            return (int) Math.floor(mWidthConstraint / mCharWidth);
        }
    }

    /**
     * @return {@code true} if the currently displayed result is scrollable
     */
    public boolean isScrollable() {
        return mScrollable;
    }

    /**
     * Map pixel position to digit offset.
     * UI thread only.
     */
    int getCharOffset(int pos) {
        return (int) Math.round(pos / mCharWidth);  // Lock not needed.
    }

    void clear() {
        mValid = false;
        mScrollable = false;
        setText("");
        setLongClickable(false);
    }

    @Override
    public void onCancelled(long index) {
        clear();
        mStoreToMemoryRequested = false;
    }

    /**
     * Refresh display.
     * Only called in UI thread. Index argument is currently ignored.
     */
    @Override
    public void onReevaluate(long index) {
        redisplay();
    }

    public void redisplay() {
        int maxChars = getMaxChars();
        if (maxChars < 4) {
            // Display currently too small to display a reasonable result. Punt to avoid crash.
            return;
        }
        if (mScroller.isFinished() && length() > 0) {
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        int currentCharOffset = getCharOffset(mCurrentPos);
        int lastDisplayedOffset[] = new int[1];
        String result = getFormattedResult(currentCharOffset, maxChars, lastDisplayedOffset,
                mAppendExponent /* forcePrecision; preserve entire result */,
                !mWholePartFits
                &&  currentCharOffset == getCharOffset(mMinPos) /* forceSciNotation */,
                mWholePartFits /* insertCommas */ );
        int expIndex = result.indexOf('E');
        result = KeyMaps.translateResult(result);
        if (expIndex > 0 && result.indexOf('.') == -1) {
          // Gray out exponent if used as position indicator
            SpannableString formattedResult = new SpannableString(result);
            formattedResult.setSpan(mExponentColorSpan, expIndex, result.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(formattedResult);
        } else {
            setText(result);
        }
        mLastDisplayedOffset = lastDisplayedOffset[0];
        mValid = true;
        setLongClickable(true);
    }

    @Override
    protected void onTextChanged(java.lang.CharSequence text, int start, int lengthBefore,
            int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        if (!mScrollable || mScroller.isFinished()) {
            if (lengthBefore == 0 && lengthAfter > 0) {
                setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
                setContentDescription(null);
            } else if (lengthBefore > 0 && lengthAfter == 0) {
                setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_NONE);
                setContentDescription(getContext().getString(R.string.desc_result));
            }
        }
    }

    @Override
    public void computeScroll() {
        if (!mScrollable) {
            return;
        }

        if (mScroller.computeScrollOffset()) {
            mCurrentPos = mScroller.getCurrX();
            if (getCharOffset(mCurrentPos) != getCharOffset(mLastPos)) {
                mLastPos = mCurrentPos;
                redisplay();
            }
        }

        if (!mScroller.isFinished()) {
                postInvalidateOnAnimation();
                setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_NONE);
        } else if (length() > 0){
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        }
    }

    /**
     * Use ActionMode for copy/memory support on M and higher.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void setupActionMode() {
        mCopyActionModeCallback = new ActionMode.Callback2() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                return createContextMenu(inflater, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // Return false if nothing is done
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (onMenuItemClick(item)) {
                    mode.finish();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                unhighlightResult();
                mActionMode = null;
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                super.onGetContentRect(mode, view, outRect);

                outRect.left += view.getPaddingLeft();
                outRect.top += view.getPaddingTop();
                outRect.right -= view.getPaddingRight();
                outRect.bottom -= view.getPaddingBottom();
                final int width = (int) Layout.getDesiredWidth(getText(), getPaint());
                if (width < outRect.width()) {
                    outRect.left = outRect.right - width;
                }

                if (!BuildCompat.isAtLeastN()) {
                    // The CAB (prior to N) only takes the translation of a view into account, so
                    // if a scale is applied to the view then the offset outRect will end up being
                    // positioned incorrectly. We workaround that limitation by manually applying
                    // the scale to the outRect, which the CAB will then offset to the correct
                    // position.
                    final float scaleX = view.getScaleX();
                    final float scaleY = view.getScaleY();
                    outRect.left *= scaleX;
                    outRect.right *= scaleX;
                    outRect.top *= scaleY;
                    outRect.bottom *= scaleY;
                }
            }
        };
        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mValid) {
                    mActionMode = startActionMode(mCopyActionModeCallback,
                            ActionMode.TYPE_FLOATING);
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Use ContextMenu for copy/memory support on L and lower.
     */
    private void setupContextMenu() {
        setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu contextMenu, View view,
                    ContextMenu.ContextMenuInfo contextMenuInfo) {
                final MenuInflater inflater = new MenuInflater(getContext());
                createContextMenu(inflater, contextMenu);
                mContextMenu = contextMenu;
                for (int i = 0; i < contextMenu.size(); i ++) {
                    contextMenu.getItem(i).setOnMenuItemClickListener(CalculatorResult.this);
                }
            }
        });
        setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mValid) {
                    return showContextMenu();
                }
                return false;
            }
        });
    }

    private boolean createContextMenu(MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.menu_result, menu);
        final boolean displayMemory = mEvaluator.getMemoryIndex() != 0;
        final MenuItem memoryAddItem = menu.findItem(R.id.memory_add);
        final MenuItem memorySubtractItem = menu.findItem(R.id.memory_subtract);
        memoryAddItem.setEnabled(displayMemory);
        memorySubtractItem.setEnabled(displayMemory);
        highlightResult();
        return true;
    }

    public boolean stopActionModeOrContextMenu() {
        if (mActionMode != null) {
            mActionMode.finish();
            return true;
        }
        if (mContextMenu != null) {
            unhighlightResult();
            mContextMenu.close();
            return true;
        }
        return false;
    }

    private void highlightResult() {
        final Spannable text = (Spannable) getText();
        text.setSpan(mHighlightSpan, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void unhighlightResult() {
        final Spannable text = (Spannable) getText();
        text.removeSpan(mHighlightSpan);
    }

    private void setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                                               getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
    }

    private void copyContent() {
        final CharSequence text = getFullCopyText();
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        // We include a tag URI, to allow us to recognize our own results and handle them
        // specially.
        ClipData.Item newItem = new ClipData.Item(text, null, mEvaluator.capture(mIndex));
        String[] mimeTypes = new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData cd = new ClipData("calculator result", mimeTypes, newItem);
        clipboard.setPrimaryClip(cd);
        Toast.makeText(getContext(), R.string.text_copied_toast, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.memory_add:
                onMemoryAdd();
                return true;
            case R.id.memory_subtract:
                onMemorySubtract();
                return true;
            case R.id.memory_store:
                onMemoryStore();
                return true;
            case R.id.menu_copy:
                if (mEvaluator.evaluationInProgress(mIndex)) {
                    // Refuse to copy placeholder characters.
                    return false;
                } else {
                    copyContent();
                    unhighlightResult();
                    return true;
                }
            default:
                return false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopActionModeOrContextMenu();
        super.onDetachedFromWindow();
    }
}
