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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.text.Spannable;
import android.util.Log;

import com.hp.creals.CR;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This implements the calculator evaluation logic.
 * Logically this maintains a signed integer indexed set of expressions, one of which
 * is distinguished as the main expression.
 * The main expression is constructed and edited with append(), delete(), etc.
 * An evaluation an then be started with a call to evaluateAndNotify() or requireResult().
 * This starts an asynchronous computation, which requests display of the initial result, when
 * available.  When initial evaluation is complete, it calls the associated listener's
 * onEvaluate() method.  This occurs in a separate event, possibly quite a bit later.  Once a
 * result has been computed, and before the underlying expression is modified, the
 * getString(index) method may be used to produce Strings that represent approximations to various
 * precisions.
 *
 * Actual expressions being evaluated are represented as {@link CalculatorExpr}s.
 *
 * The Evaluator holds the expressions and all associated state needed for evaluating
 * them.  It provides functionality for saving and restoring this state.  However the underlying
 * CalculatorExprs are exposed to the client, and may be directly accessed after cancelling any
 * in-progress computations by invoking the cancelAll() method.
 *
 * When evaluation is requested, we invoke the eval() method on the CalculatorExpr from a
 * background AsyncTask.  A subsequent getString() call for the same expression index returns
 * immediately, though it may return a result containing placeholder ' ' characters.  If we had to
 * return palceholder characters, we start a background task, which invokes the onReevaluate()
 * callback when it completes.  In either case, the background task computes the appropriate
 * result digits by evaluating the UnifiedReal returned by CalculatorExpr.eval() to the required
 * precision.
 *
 * We cache the best decimal approximation we have already computed.  We compute generously to
 * allow for some scrolling without recomputation and to minimize the chance of digits flipping
 * from "0000" to "9999".  The best known result approximation is maintained as a string by
 * mResultString (and often in a different format by the CR representation of the result).  When
 * we are in danger of not having digits to display in response to further scrolling, we also
 * initiate a background computation to higher precision, as if we had generated placeholder
 * characters.
 *
 * The code is designed to ensure that the error in the displayed result (excluding any
 * placeholder characters) is always strictly less than 1 in the last displayed digit.  Typically
 * we actually display a prefix of a result that has this property and additionally is computed to
 * a significantly higher precision.  Thus we almost always round correctly towards zero.  (Fully
 * correct rounding towards zero is not computable, at least given our representation.)
 *
 * Initial expression evaluation may time out.  This may happen in the case of domain errors such
 * as division by zero, or for large computations.  We do not currently time out reevaluations to
 * higher precision, since the original evaluation precluded a domain error that could result in
 * non-termination.  (We may discover that a presumed zero result is actually slightly negative
 * when re-evaluated; but that results in an exception, which we can handle.)  The user can abort
 * either kind of computation.
 *
 * We ensure that only one evaluation of either kind (AsyncEvaluator or AsyncReevaluator) is
 * running at a time.
 */
public class Evaluator implements CalculatorExpr.ExprResolver {

    private static Evaluator evaluator;

    public static String TIMEOUT_DIALOG_TAG = "timeout";

    @NonNull
    public static Evaluator getInstance(Context context) {
        if (evaluator == null) {
            evaluator = new Evaluator(context.getApplicationContext());
        }
        return evaluator;
    }

    public interface EvaluationListener {
        /**
         * Called if evaluation was explicitly cancelled or evaluation timed out.
         */
        public void onCancelled(long index);
        /**
         * Called if evaluation resulted in an error.
         */
        public void onError(long index, int errorId);
        /**
         * Called if evaluation completed normally.
         * @param index index of expression whose evaluation completed
         * @param initPrecOffset the offset used for initial evaluation
         * @param msdIndex index of first non-zero digit in the computed result string
         * @param lsdOffset offset of last digit in result if result has finite decimal
         *        expansion
         * @param truncatedWholePart the integer part of the result
         */
        public void onEvaluate(long index, int initPrecOffset, int msdIndex, int lsdOffset,
                String truncatedWholePart);
        /**
         * Called in response to a reevaluation request, once more precision is available.
         * Typically the listener wil respond by calling getString() to retrieve the new
         * better approximation.
         */
        public void onReevaluate(long index);  // More precision is now available; please redraw.
    }

    /**
     * A query interface for derived information based on character widths.
     * This provides information we need to calculate the "preferred precision offset" used
     * to display the initial result. It's used to compute the number of digits we can actually
     * display. All methods are callable from any thread.
     */
    public interface CharMetricsInfo {
        /**
         * Return the maximum number of (adjusted, digit-width) characters that will fit in the
         * result display.  May be called asynchronously from non-UI thread.
         */
       public int getMaxChars();
        /**
         * Return the number of additional digit widths required to add digit separators to
         * the supplied string prefix.
         * The prefix consists of the first len characters of string s, which is presumed to
         * represent a whole number. Callable from non-UI thread.
         * Returns zero if metrics information is not yet available.
         */
        public float separatorChars(String s, int len);
        /**
         * Return extra width credit for presence of a decimal point, as fraction of a digit width.
         * May be called by non-UI thread.
         */
        public float getDecimalCredit();
        /**
         * Return extra width credit for absence of ellipsis, as fraction of a digit width.
         * May be called by non-UI thread.
         */
        public float getNoEllipsisCredit();
    }

    /**
     * A CharMetricsInfo that can be used when we are really only interested in computing
     * short representations to be embedded on formulas.
     */
    private class DummyCharMetricsInfo implements CharMetricsInfo {
        @Override
        public int getMaxChars() {
            return SHORT_TARGET_LENGTH + 10;
        }
        @Override
        public float separatorChars(String s, int len) {
            return 0;
        }
        @Override
        public float getDecimalCredit() {
            return 0;
        }
        @Override
        public float getNoEllipsisCredit() {
            return 0;
        }
    }

    private final DummyCharMetricsInfo mDummyCharMetricsInfo = new DummyCharMetricsInfo();

    public static final long MAIN_INDEX = 0;  // Index of main expression.
    // Once final evaluation of an expression is complete, or when we need to save
    // a partial result, we copy the main expression to a non-zero index.
    // At that point, the expression no longer changes, and is preserved
    // until the entire history is cleared. Only expressions at nonzero indices
    // may be embedded in other expressions.
    // Each expression index can only have one outstanding evaluation request at a time.
    // To avoid conflicts between the history and main View, we copy the main expression
    // to allow independent evaluation by both.
    public static final long HISTORY_MAIN_INDEX = -1;  // Read-only copy of main expression.
    // To update e.g. "memory" contents, we copy the corresponding expression to a permanent
    // index, and then remember that index.
    private long mSavedIndex;  // Index of "saved" expression mirroring clipboard. 0 if unused.
    private long mMemoryIndex;  // Index of "memory" expression. 0 if unused.

    // When naming variables and fields, "Offset" denotes a character offset in a string
    // representing a decimal number, where the offset is relative to the decimal point.  1 =
    // tenths position, -1 = units position.  Integer.MAX_VALUE is sometimes used for the offset
    // of the last digit in an a nonterminating decimal expansion.  We use the suffix "Index" to
    // denote a zero-based absolute index into such a string. (In other contexts, like above,
    // we also use "index" to refer to the key in mExprs below, the list of all known
    // expressions.)

    private static final String KEY_PREF_DEGREE_MODE = "degree_mode";
    private static final String KEY_PREF_SAVED_INDEX = "saved_index";
    private static final String KEY_PREF_MEMORY_INDEX = "memory_index";
    private static final String KEY_PREF_SAVED_NAME = "saved_name";

    // The minimum number of extra digits we always try to compute to improve the chance of
    // producing a correctly-rounded-towards-zero result.  The extra digits can be displayed to
    // avoid generating placeholder digits, but should only be displayed briefly while computing.
    private static final int EXTRA_DIGITS = 20;

    // We adjust EXTRA_DIGITS by adding the length of the previous result divided by
    // EXTRA_DIVISOR.  This helps hide recompute latency when long results are requested;
    // We start the recomputation substantially before the need is likely to be visible.
    private static final int EXTRA_DIVISOR = 5;

    // In addition to insisting on extra digits (see above), we minimize reevaluation
    // frequency by precomputing an extra PRECOMPUTE_DIGITS
    // + <current_precision_offset>/PRECOMPUTE_DIVISOR digits, whenever we are forced to
    // reevaluate.  The last term is dropped if prec < 0.
    private static final int PRECOMPUTE_DIGITS = 30;
    private static final int PRECOMPUTE_DIVISOR = 5;

    // Initial evaluation precision.  Enough to guarantee that we can compute the short
    // representation, and that we rarely have to evaluate nonzero results to MAX_MSD_PREC_OFFSET.
    // It also helps if this is at least EXTRA_DIGITS + display width, so that we don't
    // immediately need a second evaluation.
    private static final int INIT_PREC = 50;

    // The largest number of digits to the right of the decimal point to which we will evaluate to
    // compute proper scientific notation for values close to zero.  Chosen to ensure that we
    // always to better than IEEE double precision at identifying nonzeros. And then some.
    // This is used only when we cannot a priori determine the most significant digit position, as
    // we always can if we have a rational representation.
    private static final int MAX_MSD_PREC_OFFSET = 1100;

    // If we can replace an exponent by this many leading zeroes, we do so.  Also used in
    // estimating exponent size for truncating short representation.
    private static final int EXP_COST = 3;

    // Listener that reports changes to the state (empty/filled) of memory. Protected for testing.
    private Callback mCallback;

    // Context for database helper.
    private Context mContext;

    //  A hopefully unique name associated with mSaved.
    private String mSavedName;

    // The main expression may have changed since the last evaluation in ways that would affect its
    // value.
    private boolean mChangedValue;

    // The main expression contains trig functions.
    private boolean mHasTrigFuncs;

    public static final int INVALID_MSD = Integer.MAX_VALUE;

    // Used to represent an erroneous result or a required evaluation. Not displayed.
    private static final String ERRONEOUS_RESULT = "ERR";

    /**
     * An individual CalculatorExpr, together with its evaluation state.
     * Only the main expression may be changed in-place. The HISTORY_MAIN_INDEX expression is
     * periodically reset to be a fresh immutable copy of the main expression.
     * All other expressions are only added and never removed. The expressions themselves are
     * never modified.
     * All fields other than mExpr and mVal are touched only by the UI thread.
     * For MAIN_INDEX, mExpr and mVal may change, but are also only ever touched by the UI thread.
     * For all other expressions, mExpr does not change once the ExprInfo has been (atomically)
     * added to mExprs. mVal may be asynchronously set by any thread, but we take care that it
     * does not change after that. mDegreeMode is handled exactly like mExpr.
     */
    private class ExprInfo {
        public CalculatorExpr mExpr;  // The expression itself.
        public boolean mDegreeMode;  // Evaluating in degree, not radian, mode.
        public ExprInfo(CalculatorExpr expr, boolean dm) {
            mExpr = expr;
            mDegreeMode = dm;
            mVal = new AtomicReference<UnifiedReal>();
        }

        // Currently running expression evaluator, if any.  This is either an AsyncEvaluator
        // (if mResultString == null or it's obsolete), or an AsyncReevaluator.
        // We arrange that only one evaluator is active at a time, in part by maintaining
        // two separate ExprInfo structure for the main and history view, so that they can
        // arrange for independent evaluators.
        public AsyncTask mEvaluator;

        // The remaining fields are valid only if an evaluation completed successfully.
        // mVal always points to an AtomicReference, but that may be null.
        public AtomicReference<UnifiedReal> mVal;
        // We cache the best known decimal result in mResultString.  Whenever that is
        // non-null, it is computed to exactly mResultStringOffset, which is always > 0.
        // Valid only if mResultString is non-null and (for the main expression) !mChangedValue.
        // ERRONEOUS_RESULT indicates evaluation resulted in an error.
        public String mResultString;
        public int mResultStringOffset = 0;
        // Number of digits to which (possibly incomplete) evaluation has been requested.
        // Only accessed by UI thread.
        public int mResultStringOffsetReq = 0;
        // Position of most significant digit in current cached result, if determined.  This is just
        // the index in mResultString holding the msd.
        public int mMsdIndex = INVALID_MSD;
        // Long timeout needed for evaluation?
        public boolean mLongTimeout = false;
        public long mTimeStamp;
    }

    private ConcurrentHashMap<Long, ExprInfo> mExprs = new ConcurrentHashMap<Long, ExprInfo>();

    // The database holding persistent expressions.
    private ExpressionDB mExprDB;

    private ExprInfo mMainExpr;  //  == mExprs.get(MAIN_INDEX)

    private SharedPreferences mSharedPrefs;

    private final Handler mTimeoutHandler;  // Used to schedule evaluation timeouts.

    private void setMainExpr(ExprInfo expr) {
        mMainExpr = expr;
        mExprs.put(MAIN_INDEX, expr);
    }

    Evaluator(Context context) {
        mContext = context;
        setMainExpr(new ExprInfo(new CalculatorExpr(), false));
        mSavedName = "none";
        mTimeoutHandler = new Handler();

        mExprDB = new ExpressionDB(context);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mMainExpr.mDegreeMode = mSharedPrefs.getBoolean(KEY_PREF_DEGREE_MODE, false);
        long savedIndex = mSharedPrefs.getLong(KEY_PREF_SAVED_INDEX, 0L);
        long memoryIndex = mSharedPrefs.getLong(KEY_PREF_MEMORY_INDEX, 0L);
        if (savedIndex != 0 && savedIndex != -1 /* Recover from old corruption */) {
            setSavedIndexWhenEvaluated(savedIndex);
        }
        if (memoryIndex != 0 && memoryIndex != -1) {
            setMemoryIndexWhenEvaluated(memoryIndex, false /* no need to persist again */);
        }
        mSavedName = mSharedPrefs.getString(KEY_PREF_SAVED_NAME, "none");
    }

    /**
     * Retrieve minimum expression index.
     * This is the minimum over all expressions, including uncached ones residing only
     * in the data base. If no expressions with negative indices were preserved, this will
     * return a small negative predefined constant.
     * May be called from any thread, but will block until the database is opened.
     */
    public long getMinIndex() {
        return mExprDB.getMinIndex();
    }

    /**
     * Retrieve maximum expression index.
     * This is the maximum over all expressions, including uncached ones residing only
     * in the data base. If no expressions with positive indices were preserved, this will
     * return 0.
     * May be called from any thread, but will block until the database is opened.
     */
    public long getMaxIndex() {
        return mExprDB.getMaxIndex();
    }

    /**
     * Set the Callback for showing dialogs and notifying the UI about memory state changes.
     * @param callback
     */
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Does the expression index refer to a transient and mutable expression?
     */
    private boolean isMutableIndex(long index) {
        return index == MAIN_INDEX || index == HISTORY_MAIN_INDEX;
    }

    /**
     * Result of initial asynchronous result computation.
     * Represents either an error or a result computed to an initial evaluation precision.
     */
    private static class InitialResult {
        public final int errorResourceId;    // Error string or INVALID_RES_ID.
        public final UnifiedReal val;        // Constructive real value.
        public final String newResultString;       // Null iff it can't be computed.
        public final int newResultStringOffset;
        public final int initDisplayOffset;
        InitialResult(UnifiedReal v, String s, int p, int idp) {
            errorResourceId = Calculator.INVALID_RES_ID;
            val = v;
            newResultString = s;
            newResultStringOffset = p;
            initDisplayOffset = idp;
        }
        InitialResult(int errorId) {
            errorResourceId = errorId;
            val = UnifiedReal.ZERO;
            newResultString = "BAD";
            newResultStringOffset = 0;
            initDisplayOffset = 0;
        }
        boolean isError() {
            return errorResourceId != Calculator.INVALID_RES_ID;
        }
    }

    private void displayCancelledMessage() {
        if (mCallback != null) {
            mCallback.showMessageDialog(0, R.string.cancelled, 0, null);
        }
    }

    // Timeout handling.
    // Expressions are evaluated with a sort timeout or a long timeout.
    // Each implies different maxima on both computation time and bit length.
    // We recheck bit length separetly to avoid wasting time on decimal conversions that are
    // destined to fail.

    /**
     * Return the timeout in milliseconds.
     * @param longTimeout a long timeout is in effect
     */
    private long getTimeout(boolean longTimeout) {
        return longTimeout ? 15000 : 2000;
        // Exceeding a few tens of seconds increases the risk of running out of memory
        // and impacting the rest of the system.
    }

    /**
     * Return the maximum number of bits in the result.  Longer results are assumed to time out.
     * @param longTimeout a long timeout is in effect
     */
    private int getMaxResultBits(boolean longTimeout) {
        return longTimeout ? 700000 : 240000;
    }

    /**
     * Timeout for unrequested, speculative evaluations, in milliseconds.
     */
    private static final long QUICK_TIMEOUT = 1000;

    /**
     * Timeout for non-MAIN expressions. Note that there may be many such evaluations in
     * progress on the same thread or core. Thus the evaluation latency may include that needed
     * to complete previously enqueued evaluations. Thus the longTimeout flag is not very
     * meaningful, and currently ignored.
     * Since this is only used for expressions that we have previously successfully evaluated,
     * these timeouts hsould never trigger.
     */
    private static final long NON_MAIN_TIMEOUT = 100000;

    /**
     * Maximum result bit length for unrequested, speculative evaluations.
     * Also used to bound evaluation precision for small non-zero fractions.
     */
    private static final int QUICK_MAX_RESULT_BITS = 150000;

    private void displayTimeoutMessage(boolean longTimeout) {
        if (mCallback != null) {
            mCallback.showMessageDialog(R.string.dialog_timeout, R.string.timeout,
                    longTimeout ? 0 : R.string.ok_remove_timeout, TIMEOUT_DIALOG_TAG);
        }
    }

    public void setLongTimeout() {
        mMainExpr.mLongTimeout = true;
    }

    /**
     * Compute initial cache contents and result when we're good and ready.
     * We leave the expression display up, with scrolling disabled, until this computation
     * completes.  Can result in an error display if something goes wrong.  By default we set a
     * timeout to catch runaway computations.
     */
    class AsyncEvaluator extends AsyncTask<Void, Void, InitialResult> {
        private boolean mDm;  // degrees
        public boolean mRequired; // Result was requested by user.
        private boolean mQuiet;  // Suppress cancellation message.
        private Runnable mTimeoutRunnable = null;
        private EvaluationListener mListener;  // Completion callback.
        private CharMetricsInfo mCharMetricsInfo;  // Where to get result size information.
        private long mIndex;  //  Expression index.
        private ExprInfo mExprInfo;  // Current expression.

        AsyncEvaluator(long index, EvaluationListener listener, CharMetricsInfo cmi, boolean dm,
                boolean required) {
            mIndex = index;
            mListener = listener;
            mCharMetricsInfo = cmi;
            mDm = dm;
            mRequired = required;
            mQuiet = !required || mIndex != MAIN_INDEX;
            mExprInfo = mExprs.get(mIndex);
            if (mExprInfo.mEvaluator != null) {
                throw new AssertionError("Evaluation already in progress!");
            }
        }

        private void handleTimeout() {
            // Runs in UI thread.
            boolean running = (getStatus() != AsyncTask.Status.FINISHED);
            if (running && cancel(true)) {
                mExprs.get(mIndex).mEvaluator = null;
                if (mRequired && mIndex == MAIN_INDEX) {
                    // Replace mExpr with clone to avoid races if task still runs for a while.
                    mMainExpr.mExpr = (CalculatorExpr)mMainExpr.mExpr.clone();
                    suppressCancelMessage();
                    displayTimeoutMessage(mExprInfo.mLongTimeout);
                }
            }
        }

        private void suppressCancelMessage() {
            mQuiet = true;
        }

        @Override
        protected void onPreExecute() {
            long timeout = mRequired ? getTimeout(mExprInfo.mLongTimeout) : QUICK_TIMEOUT;
            if (mIndex != MAIN_INDEX) {
                // We evaluated the expression before with the current timeout, so this shouldn't
                // ever time out. We evaluate it with a ridiculously long timeout to avoid running
                // down the battery if something does go wrong. But we only log such timeouts, and
                // invoke the listener with onCancelled.
                timeout = NON_MAIN_TIMEOUT;
            }
            mTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    handleTimeout();
                }
            };
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutHandler.postDelayed(mTimeoutRunnable, timeout);
        }

        /**
         * Is a computed result too big for decimal conversion?
         */
        private boolean isTooBig(UnifiedReal res) {
            final int maxBits = mRequired ? getMaxResultBits(mExprInfo.mLongTimeout)
                    : QUICK_MAX_RESULT_BITS;
            return res.approxWholeNumberBitsGreaterThan(maxBits);
        }

        @Override
        protected InitialResult doInBackground(Void... nothing) {
            try {
                // mExpr does not change while we are evaluating; thus it's OK to read here.
                UnifiedReal res = mExprInfo.mVal.get();
                if (res == null) {
                    try {
                        res = mExprInfo.mExpr.eval(mDm, Evaluator.this);
                        if (isCancelled()) {
                            // TODO: This remains very slightly racey. Fix this.
                            throw new CR.AbortedException();
                        }
                        res = putResultIfAbsent(mIndex, res);
                    } catch (StackOverflowError e) {
                        // Absurdly large integer exponents can cause this. There might be other
                        // examples as well. Treat it as a timeout.
                        return new InitialResult(R.string.timeout);
                    }
                }
                if (isTooBig(res)) {
                    // Avoid starting a long uninterruptible decimal conversion.
                    return new InitialResult(R.string.timeout);
                }
                int precOffset = INIT_PREC;
                String initResult = res.toStringTruncated(precOffset);
                int msd = getMsdIndexOf(initResult);
                if (msd == INVALID_MSD) {
                    int leadingZeroBits = res.leadingBinaryZeroes();
                    if (leadingZeroBits < QUICK_MAX_RESULT_BITS) {
                        // Enough initial nonzero digits for most displays.
                        precOffset = 30 +
                                (int)Math.ceil(Math.log(2.0d) / Math.log(10.0d) * leadingZeroBits);
                        initResult = res.toStringTruncated(precOffset);
                        msd = getMsdIndexOf(initResult);
                        if (msd == INVALID_MSD) {
                            throw new AssertionError("Impossible zero result");
                        }
                    } else {
                        // Just try once more at higher fixed precision.
                        precOffset = MAX_MSD_PREC_OFFSET;
                        initResult = res.toStringTruncated(precOffset);
                        msd = getMsdIndexOf(initResult);
                    }
                }
                final int lsdOffset = getLsdOffset(res, initResult, initResult.indexOf('.'));
                final int initDisplayOffset = getPreferredPrec(initResult, msd, lsdOffset,
                        mCharMetricsInfo);
                final int newPrecOffset = initDisplayOffset + EXTRA_DIGITS;
                if (newPrecOffset > precOffset) {
                    precOffset = newPrecOffset;
                    initResult = res.toStringTruncated(precOffset);
                }
                return new InitialResult(res, initResult, precOffset, initDisplayOffset);
            } catch (CalculatorExpr.SyntaxException e) {
                return new InitialResult(R.string.error_syntax);
            } catch (UnifiedReal.ZeroDivisionException e) {
                return new InitialResult(R.string.error_zero_divide);
            } catch(ArithmeticException e) {
                return new InitialResult(R.string.error_nan);
            } catch(CR.PrecisionOverflowException e) {
                // Extremely unlikely unless we're actually dividing by zero or the like.
                return new InitialResult(R.string.error_overflow);
            } catch(CR.AbortedException e) {
                return new InitialResult(R.string.error_aborted);
            }
        }

        @Override
        protected void onPostExecute(InitialResult result) {
            mExprInfo.mEvaluator = null;
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            if (result.isError()) {
                if (result.errorResourceId == R.string.timeout) {
                    // Emulating timeout due to large result.
                    if (mRequired && mIndex == MAIN_INDEX) {
                        displayTimeoutMessage(mExprs.get(mIndex).mLongTimeout);
                    }
                    mListener.onCancelled(mIndex);
                } else {
                    if (mRequired) {
                        mExprInfo.mResultString = ERRONEOUS_RESULT;
                    }
                    mListener.onError(mIndex, result.errorResourceId);
                }
                return;
            }
            // mExprInfo.mVal was already set asynchronously by child thread.
            mExprInfo.mResultString = result.newResultString;
            mExprInfo.mResultStringOffset = result.newResultStringOffset;
            final int dotIndex = mExprInfo.mResultString.indexOf('.');
            String truncatedWholePart = mExprInfo.mResultString.substring(0, dotIndex);
            // Recheck display precision; it may change, since display dimensions may have been
            // unknow the first time.  In that case the initial evaluation precision should have
            // been conservative.
            // TODO: Could optimize by remembering display size and checking for change.
            int initPrecOffset = result.initDisplayOffset;
            mExprInfo.mMsdIndex = getMsdIndexOf(mExprInfo.mResultString);
            final int leastDigOffset = getLsdOffset(result.val, mExprInfo.mResultString,
                    dotIndex);
            final int newInitPrecOffset = getPreferredPrec(mExprInfo.mResultString,
                    mExprInfo.mMsdIndex, leastDigOffset, mCharMetricsInfo);
            if (newInitPrecOffset < initPrecOffset) {
                initPrecOffset = newInitPrecOffset;
            } else {
                // They should be equal.  But nothing horrible should happen if they're not. e.g.
                // because CalculatorResult.MAX_WIDTH was too small.
            }
            mListener.onEvaluate(mIndex, initPrecOffset, mExprInfo.mMsdIndex, leastDigOffset,
                    truncatedWholePart);
        }

        @Override
        protected void onCancelled(InitialResult result) {
            // Invoker resets mEvaluator.
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            if (!mQuiet) {
                displayCancelledMessage();
            } // Otherwise, if mRequired, timeout processing displayed message.
            mListener.onCancelled(mIndex);
            // Just drop the evaluation; Leave expression displayed.
            return;
        }
    }

    /**
     * Check whether a new higher precision result flips previously computed trailing 9s
     * to zeroes.  If so, flip them back.  Return the adjusted result.
     * Assumes newPrecOffset >= oldPrecOffset > 0.
     * Since our results are accurate to < 1 ulp, this can only happen if the true result
     * is less than the new result with trailing zeroes, and thus appending 9s to the
     * old result must also be correct.  Such flips are impossible if the newly computed
     * digits consist of anything other than zeroes.
     * It is unclear that there are real cases in which this is necessary,
     * but we have failed to prove there aren't such cases.
     */
    @VisibleForTesting
    public static String unflipZeroes(String oldDigs, int oldPrecOffset, String newDigs,
            int newPrecOffset) {
        final int oldLen = oldDigs.length();
        if (oldDigs.charAt(oldLen - 1) != '9') {
            return newDigs;
        }
        final int newLen = newDigs.length();
        final int precDiff = newPrecOffset - oldPrecOffset;
        final int oldLastInNew = newLen - 1 - precDiff;
        if (newDigs.charAt(oldLastInNew) != '0') {
            return newDigs;
        }
        // Earlier digits could not have changed without a 0 to 9 or 9 to 0 flip at end.
        // The former is OK.
        if (!newDigs.substring(newLen - precDiff).equals(StringUtils.repeat('0', precDiff))) {
            throw new AssertionError("New approximation invalidates old one!");
        }
        return oldDigs + StringUtils.repeat('9', precDiff);
    }

    /**
     * Result of asynchronous reevaluation.
     */
    private static class ReevalResult {
        public final String newResultString;
        public final int newResultStringOffset;
        ReevalResult(String s, int p) {
            newResultString = s;
            newResultStringOffset = p;
        }
    }

    /**
     * Compute new mResultString contents to prec digits to the right of the decimal point.
     * Ensure that onReevaluate() is called after doing so.  If the evaluation fails for reasons
     * other than a timeout, ensure that onError() is called.
     * This assumes that initial evaluation of the expression has been successfully
     * completed.
     */
    private class AsyncReevaluator extends AsyncTask<Integer, Void, ReevalResult> {
        private long mIndex;  // Index of expression to evaluate.
        private EvaluationListener mListener;
        private ExprInfo mExprInfo;

        AsyncReevaluator(long index, EvaluationListener listener) {
            mIndex = index;
            mListener = listener;
            mExprInfo = mExprs.get(mIndex);
        }

        @Override
        protected ReevalResult doInBackground(Integer... prec) {
            try {
                final int precOffset = prec[0].intValue();
                return new ReevalResult(mExprInfo.mVal.get().toStringTruncated(precOffset),
                        precOffset);
            } catch(ArithmeticException e) {
                return null;
            } catch(CR.PrecisionOverflowException e) {
                return null;
            } catch(CR.AbortedException e) {
                // Should only happen if the task was cancelled, in which case we don't look at
                // the result.
                return null;
            }
        }

        @Override
        protected void onPostExecute(ReevalResult result) {
            if (result == null) {
                // This should only be possible in the extremely rare case of encountering a
                // domain error while reevaluating or in case of a precision overflow.  We don't
                // know of a way to get the latter with a plausible amount of user input.
                mExprInfo.mResultString = ERRONEOUS_RESULT;
                mListener.onError(mIndex, R.string.error_nan);
            } else {
                if (result.newResultStringOffset < mExprInfo.mResultStringOffset) {
                    throw new AssertionError("Unexpected onPostExecute timing");
                }
                mExprInfo.mResultString = unflipZeroes(mExprInfo.mResultString,
                        mExprInfo.mResultStringOffset, result.newResultString,
                        result.newResultStringOffset);
                mExprInfo.mResultStringOffset = result.newResultStringOffset;
                mListener.onReevaluate(mIndex);
            }
            mExprInfo.mEvaluator = null;
        }
        // On cancellation we do nothing; invoker should have left no trace of us.
    }

    /**
     * If necessary, start an evaluation of the expression at the given index to precOffset.
     * If we start an evaluation the listener is notified on completion.
     * Only called if prior evaluation succeeded.
     */
    private void ensureCachePrec(long index, int precOffset, EvaluationListener listener) {
        ExprInfo ei = mExprs.get(index);
        if (ei.mResultString != null && ei.mResultStringOffset >= precOffset
                || ei.mResultStringOffsetReq >= precOffset) return;
        if (ei.mEvaluator != null) {
            // Ensure we only have one evaluation running at a time.
            ei.mEvaluator.cancel(true);
            ei.mEvaluator = null;
        }
        AsyncReevaluator reEval = new AsyncReevaluator(index, listener);
        ei.mEvaluator = reEval;
        ei.mResultStringOffsetReq = precOffset + PRECOMPUTE_DIGITS;
        if (ei.mResultString != null) {
            ei.mResultStringOffsetReq += ei.mResultStringOffsetReq / PRECOMPUTE_DIVISOR;
        }
        reEval.execute(ei.mResultStringOffsetReq);
    }

    /**
     * Return the rightmost nonzero digit position, if any.
     * @param val UnifiedReal value of result.
     * @param cache Current cached decimal string representation of result.
     * @param decIndex Index of decimal point in cache.
     * @result Position of rightmost nonzero digit relative to decimal point.
     *         Integer.MIN_VALUE if we cannot determine.  Integer.MAX_VALUE if there is no lsd,
     *         or we cannot determine it.
     */
    static int getLsdOffset(UnifiedReal val, String cache, int decIndex) {
        if (val.definitelyZero()) return Integer.MIN_VALUE;
        int result = val.digitsRequired();
        if (result == 0) {
            int i;
            for (i = -1; decIndex + i > 0 && cache.charAt(decIndex + i) == '0'; --i) { }
            result = i;
        }
        return result;
    }

    // TODO: We may want to consistently specify the position of the current result
    // window using the left-most visible digit index instead of the offset for the rightmost one.
    // It seems likely that would simplify the logic.

    /**
     * Retrieve the preferred precision "offset" for the currently displayed result.
     * May be called from non-UI thread.
     * @param cache Current approximation as string.
     * @param msd Position of most significant digit in result.  Index in cache.
     *            Can be INVALID_MSD if we haven't found it yet.
     * @param lastDigitOffset Position of least significant digit (1 = tenths digit)
     *                  or Integer.MAX_VALUE.
     */
    private static int getPreferredPrec(String cache, int msd, int lastDigitOffset,
            CharMetricsInfo cm) {
        final int lineLength = cm.getMaxChars();
        final int wholeSize = cache.indexOf('.');
        final float rawSepChars = cm.separatorChars(cache, wholeSize);
        final float rawSepCharsNoDecimal = rawSepChars - cm.getNoEllipsisCredit();
        final float rawSepCharsWithDecimal = rawSepCharsNoDecimal - cm.getDecimalCredit();
        final int sepCharsNoDecimal = (int) Math.ceil(Math.max(rawSepCharsNoDecimal, 0.0f));
        final int sepCharsWithDecimal = (int) Math.ceil(Math.max(rawSepCharsWithDecimal, 0.0f));
        final int negative = cache.charAt(0) == '-' ? 1 : 0;
        // Don't display decimal point if result is an integer.
        if (lastDigitOffset == 0) {
            lastDigitOffset = -1;
        }
        if (lastDigitOffset != Integer.MAX_VALUE) {
            if (wholeSize <= lineLength - sepCharsNoDecimal && lastDigitOffset <= 0) {
                // Exact integer.  Prefer to display as integer, without decimal point.
                return -1;
            }
            if (lastDigitOffset >= 0
                    && wholeSize + lastDigitOffset + 1 /* decimal pt. */
                    <= lineLength - sepCharsWithDecimal) {
                // Display full exact number without scientific notation.
                return lastDigitOffset;
            }
        }
        if (msd > wholeSize && msd <= wholeSize + EXP_COST + 1) {
            // Display number without scientific notation.  Treat leading zero as msd.
            msd = wholeSize - 1;
        }
        if (msd > QUICK_MAX_RESULT_BITS) {
            // Display a probable but uncertain 0 as "0.000000000", without exponent.  That's a
            // judgment call, but less likely to confuse naive users.  A more informative and
            // confusing option would be to use a large negative exponent.
            // Treat extremely large msd values as unknown to avoid slow computations.
            return lineLength - 2;
        }
        // Return position corresponding to having msd at left, effectively presuming scientific
        // notation that preserves the left part of the result.
        // After adjustment for the space required by an exponent, evaluating to the resulting
        // precision should not overflow the display.
        int result = msd - wholeSize + lineLength - negative - 1;
        if (wholeSize <= lineLength - sepCharsNoDecimal) {
            // Fits without scientific notation; will need space for separators.
            if (wholeSize < lineLength - sepCharsWithDecimal) {
                result -= sepCharsWithDecimal;
            } else {
                result -= sepCharsNoDecimal;
            }
        }
        return result;
    }

    private static final int SHORT_TARGET_LENGTH  = 8;
    private static final String SHORT_UNCERTAIN_ZERO = "0.00000" + KeyMaps.ELLIPSIS;

    /**
     * Get a short representation of the value represented by the string cache.
     * We try to match the CalculatorResult code when the result is finite
     * and small enough to suit our needs.
     * The result is not internationalized.
     * @param cache String approximation of value.  Assumed to be long enough
     *              that if it doesn't contain enough significant digits, we can
     *              reasonably abbreviate as SHORT_UNCERTAIN_ZERO.
     * @param msdIndex Index of most significant digit in cache, or INVALID_MSD.
     * @param lsdOffset Position of least significant digit in finite representation,
     *            relative to decimal point, or MAX_VALUE.
     */
    private static String getShortString(String cache, int msdIndex, int lsdOffset) {
        // This somewhat mirrors the display formatting code, but
        // - The constants are different, since we don't want to use the whole display.
        // - This is an easier problem, since we don't support scrolling and the length
        //   is a bit flexible.
        // TODO: Think about refactoring this to remove partial redundancy with CalculatorResult.
        final int dotIndex = cache.indexOf('.');
        final int negative = cache.charAt(0) == '-' ? 1 : 0;
        final String negativeSign = negative == 1 ? "-" : "";

        // Ensure we don't have to worry about running off the end of cache.
        if (msdIndex >= cache.length() - SHORT_TARGET_LENGTH) {
            msdIndex = INVALID_MSD;
        }
        if (msdIndex == INVALID_MSD) {
            if (lsdOffset < INIT_PREC) {
                return "0";
            } else {
                return SHORT_UNCERTAIN_ZERO;
            }
        }
        // Avoid scientific notation for small numbers of zeros.
        // Instead stretch significant digits to include decimal point.
        if (lsdOffset < -1 && dotIndex - msdIndex + negative <= SHORT_TARGET_LENGTH
            && lsdOffset >= -CalculatorResult.MAX_TRAILING_ZEROES - 1) {
            // Whole number that fits in allotted space.
            // CalculatorResult would not use scientific notation either.
            lsdOffset = -1;
        }
        if (msdIndex > dotIndex) {
            if (msdIndex <= dotIndex + EXP_COST + 1) {
                // Preferred display format in this case is with leading zeroes, even if
                // it doesn't fit entirely.  Replicate that here.
                msdIndex = dotIndex - 1;
            } else if (lsdOffset <= SHORT_TARGET_LENGTH - negative - 2
                    && lsdOffset <= CalculatorResult.MAX_LEADING_ZEROES + 1) {
                // Fraction that fits entirely in allotted space.
                // CalculatorResult would not use scientific notation either.
                msdIndex = dotIndex -1;
            }
        }
        int exponent = dotIndex - msdIndex;
        if (exponent > 0) {
            // Adjust for the fact that the decimal point itself takes space.
            exponent--;
        }
        if (lsdOffset != Integer.MAX_VALUE) {
            final int lsdIndex = dotIndex + lsdOffset;
            final int totalDigits = lsdIndex - msdIndex + negative + 1;
            if (totalDigits <= SHORT_TARGET_LENGTH && dotIndex > msdIndex && lsdOffset >= -1) {
                // Fits, no exponent needed.
                final String wholeWithCommas = StringUtils.addCommas(cache, msdIndex, dotIndex);
                return negativeSign + wholeWithCommas + cache.substring(dotIndex, lsdIndex + 1);
            }
            if (totalDigits <= SHORT_TARGET_LENGTH - 3) {
                return negativeSign + cache.charAt(msdIndex) + "."
                        + cache.substring(msdIndex + 1, lsdIndex + 1) + "E" + exponent;
            }
        }
        // We need to abbreviate.
        if (dotIndex > msdIndex && dotIndex < msdIndex + SHORT_TARGET_LENGTH - negative - 1) {
            final String wholeWithCommas = StringUtils.addCommas(cache, msdIndex, dotIndex);
            return negativeSign + wholeWithCommas
                    + cache.substring(dotIndex, msdIndex + SHORT_TARGET_LENGTH - negative - 1)
                    + KeyMaps.ELLIPSIS;
        }
        // Need abbreviation + exponent
        return negativeSign + cache.charAt(msdIndex) + "."
                + cache.substring(msdIndex + 1, msdIndex + SHORT_TARGET_LENGTH - negative - 4)
                + KeyMaps.ELLIPSIS + "E" + exponent;
    }

    /**
     * Return the most significant digit index in the given numeric string.
     * Return INVALID_MSD if there are not enough digits to prove the numeric value is
     * different from zero.  As usual, we assume an error of strictly less than 1 ulp.
     */
    public static int getMsdIndexOf(String s) {
        final int len = s.length();
        int nonzeroIndex = -1;
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (c != '-' && c != '.' && c != '0') {
                nonzeroIndex = i;
                break;
            }
        }
        if (nonzeroIndex >= 0 && (nonzeroIndex < len - 1 || s.charAt(nonzeroIndex) != '1')) {
            return nonzeroIndex;
        } else {
            return INVALID_MSD;
        }
    }

    /**
     * Return most significant digit index for the result of the expressin at the given index.
     * Returns an index in the result character array.  Return INVALID_MSD if the current result
     * is too close to zero to determine the result.
     * Result is almost consistent through reevaluations: It may increase by one, once.
     */
    private int getMsdIndex(long index) {
        ExprInfo ei = mExprs.get(index);
        if (ei.mMsdIndex != INVALID_MSD) {
            // 0.100000... can change to 0.0999999...  We may have to correct once by one digit.
            if (ei.mResultString.charAt(ei.mMsdIndex) == '0') {
                ei.mMsdIndex++;
            }
            return ei.mMsdIndex;
        }
        if (ei.mVal.get().definitelyZero()) {
            return INVALID_MSD;  // None exists
        }
        int result = INVALID_MSD;
        if (ei.mResultString != null) {
            result = ei.mMsdIndex = getMsdIndexOf(ei.mResultString);
        }
        return result;
    }

    // Refuse to scroll past the point at which this many digits from the whole number
    // part of the result are still displayed.  Avoids sily displays like 1E1.
    private static final int MIN_DISPLAYED_DIGS = 5;

    /**
     * Return result to precOffset[0] digits to the right of the decimal point.
     * PrecOffset[0] is updated if the original value is out of range.  No exponent or other
     * indication of precision is added.  The result is returned immediately, based on the current
     * cache contents, but it may contain blanks for unknown digits.  It may also use
     * uncertain digits within EXTRA_DIGITS.  If either of those occurred, schedule a reevaluation
     * and redisplay operation.  Uncertain digits never appear to the left of the decimal point.
     * PrecOffset[0] may be negative to only retrieve digits to the left of the decimal point.
     * (precOffset[0] = 0 means we include the decimal point, but nothing to the right.
     * precOffset[0] = -1 means we drop the decimal point and start at the ones position.  Should
     * not be invoked before the onEvaluate() callback is received.  This essentially just returns
     * a substring of the full result; a leading minus sign or leading digits can be dropped.
     * Result uses US conventions; is NOT internationalized.  Use getResult() and UnifiedReal
     * operations to determine whether the result is exact, or whether we dropped trailing digits.
     *
     * @param index Index of expression to approximate
     * @param precOffset Zeroth element indicates desired and actual precision
     * @param maxPrecOffset Maximum adjusted precOffset[0]
     * @param maxDigs Maximum length of result
     * @param truncated Zeroth element is set if leading nonzero digits were dropped
     * @param negative Zeroth element is set of the result is negative.
     * @param listener EvaluationListener to notify when reevaluation is complete.
     */
    public String getString(long index, int[] precOffset, int maxPrecOffset, int maxDigs,
            boolean[] truncated, boolean[] negative, EvaluationListener listener) {
        ExprInfo ei = mExprs.get(index);
        int currentPrecOffset = precOffset[0];
        // Make sure we eventually get a complete answer
        if (ei.mResultString == null) {
            ensureCachePrec(index, currentPrecOffset + EXTRA_DIGITS, listener);
            // Nothing else to do now; seems to happen on rare occasion with weird user input
            // timing; Will repair itself in a jiffy.
            return " ";
        } else {
            ensureCachePrec(index, currentPrecOffset + EXTRA_DIGITS + ei.mResultString.length()
                    / EXTRA_DIVISOR, listener);
        }
        // Compute an appropriate substring of mResultString.  Pad if necessary.
        final int len = ei.mResultString.length();
        final boolean myNegative = ei.mResultString.charAt(0) == '-';
        negative[0] = myNegative;
        // Don't scroll left past leftmost digits in mResultString unless that still leaves an
        // integer.
            int integralDigits = len - ei.mResultStringOffset;
                            // includes 1 for dec. pt
            if (myNegative) {
                --integralDigits;
            }
            int minPrecOffset = Math.min(MIN_DISPLAYED_DIGS - integralDigits, -1);
            currentPrecOffset = Math.min(Math.max(currentPrecOffset, minPrecOffset),
                    maxPrecOffset);
            precOffset[0] = currentPrecOffset;
        int extraDigs = ei.mResultStringOffset - currentPrecOffset; // trailing digits to drop
        int deficit = 0;  // The number of digits we're short
        if (extraDigs < 0) {
            extraDigs = 0;
            deficit = Math.min(currentPrecOffset - ei.mResultStringOffset, maxDigs);
        }
        int endIndex = len - extraDigs;
        if (endIndex < 1) {
            return " ";
        }
        int startIndex = Math.max(endIndex + deficit - maxDigs, 0);
        truncated[0] = (startIndex > getMsdIndex(index));
        String result = ei.mResultString.substring(startIndex, endIndex);
        if (deficit > 0) {
            result += StringUtils.repeat(' ', deficit);
            // Blank character is replaced during translation.
            // Since we always compute past the decimal point, this never fills in the spot
            // where the decimal point should go, and we can otherwise treat placeholders
            // as though they were digits.
        }
        return result;
    }

    /**
     * Clear the cache for the main expression.
     */
    private void clearMainCache() {
        mMainExpr.mVal.set(null);
        mMainExpr.mResultString = null;
        mMainExpr.mResultStringOffset = mMainExpr.mResultStringOffsetReq = 0;
        mMainExpr.mMsdIndex = INVALID_MSD;
    }


    public void clearMain() {
        mMainExpr.mExpr.clear();
        mHasTrigFuncs = false;
        clearMainCache();
        mMainExpr.mLongTimeout = false;
    }

    public void clearEverything() {
        boolean dm = mMainExpr.mDegreeMode;
        cancelAll(true);
        setSavedIndex(0);
        setMemoryIndex(0);
        mExprDB.eraseAll();
        mExprs.clear();
        setMainExpr(new ExprInfo(new CalculatorExpr(), dm));
    }

    /**
     * Start asynchronous evaluation.
     * Invoke listener on successful completion. If the result is required, invoke
     * onCancelled() if cancelled.
     * @param index index of expression to be evaluated.
     * @param required result was explicitly requested by user.
     */
    private void evaluateResult(long index, EvaluationListener listener, CharMetricsInfo cmi,
            boolean required) {
        ExprInfo ei = mExprs.get(index);
        if (index == MAIN_INDEX) {
            clearMainCache();
        }  // Otherwise the expression is immutable.
        AsyncEvaluator eval =  new AsyncEvaluator(index, listener, cmi, ei.mDegreeMode, required);
        ei.mEvaluator = eval;
        eval.execute();
        if (index == MAIN_INDEX) {
            mChangedValue = false;
        }
    }

    /**
     * Notify listener of a previously completed evaluation.
     */
    void notifyImmediately(long index, ExprInfo ei, EvaluationListener listener,
            CharMetricsInfo cmi) {
        final int dotIndex = ei.mResultString.indexOf('.');
        final String truncatedWholePart = ei.mResultString.substring(0, dotIndex);
        final int leastDigOffset = getLsdOffset(ei.mVal.get(), ei.mResultString, dotIndex);
        final int msdIndex = getMsdIndex(index);
        final int preferredPrecOffset = getPreferredPrec(ei.mResultString, msdIndex,
                leastDigOffset, cmi);
        listener.onEvaluate(index, preferredPrecOffset, msdIndex, leastDigOffset,
                truncatedWholePart);
    }

    /**
     * Start optional evaluation of expression and display when ready.
     * @param index of expression to be evaluated.
     * Can quietly time out without a listener callback.
     * No-op if cmi.getMaxChars() == 0.
     */
    public void evaluateAndNotify(long index, EvaluationListener listener, CharMetricsInfo cmi) {
        if (cmi.getMaxChars() == 0) {
            // Probably shouldn't happen. If it does, we didn't promise to do anything anyway.
            return;
        }
        ExprInfo ei = ensureExprIsCached(index);
        if (ei.mResultString != null && ei.mResultString != ERRONEOUS_RESULT
                && !(index == MAIN_INDEX && mChangedValue)) {
            // Already done. Just notify.
            notifyImmediately(MAIN_INDEX, mMainExpr, listener, cmi);
            return;
        } else if (ei.mEvaluator != null) {
            // We only allow a single listener per expression, so this request must be redundant.
            return;
        }
        evaluateResult(index, listener, cmi, false);
    }

    /**
     * Start required evaluation of expression at given index and call back listener when ready.
     * If index is MAIN_INDEX, we may also directly display a timeout message.
     * Uses longer timeouts than optional evaluation.
     * Requires cmi.getMaxChars() != 0.
     */
    public void requireResult(long index, EvaluationListener listener, CharMetricsInfo cmi) {
        if (cmi.getMaxChars() == 0) {
            throw new AssertionError("requireResult called too early");
        }
        ExprInfo ei = ensureExprIsCached(index);
        if (ei.mResultString == null || (index == MAIN_INDEX && mChangedValue)) {
            if (index == HISTORY_MAIN_INDEX) {
                // We don't want to compute a result for HISTORY_MAIN_INDEX that was
                // not already computed for the main expression. Pretend we timed out.
                // The error case doesn't get here.
                listener.onCancelled(index);
            } else if ((ei.mEvaluator instanceof AsyncEvaluator)
                    && ((AsyncEvaluator)(ei.mEvaluator)).mRequired) {
                // Duplicate request; ignore.
            } else {
                // (Re)start evaluator in requested mode, i.e. with longer timeout.
                cancel(ei, true);
                evaluateResult(index, listener, cmi, true);
            }
        } else if (ei.mResultString == ERRONEOUS_RESULT) {
            // Just re-evaluate to generate a new notification.
            cancel(ei, true);
            evaluateResult(index, listener, cmi, true);
        } else {
            notifyImmediately(index, ei, listener, cmi);
        }
    }

    /**
     * Whether this expression has explicitly been evaluated (User pressed "=")
     */
    public boolean hasResult(long index) {
        final ExprInfo ei = ensureExprIsCached(index);
        return ei.mResultString != null;
    }

    /**
     * Is a reevaluation still in progress?
     */
    public boolean evaluationInProgress(long index) {
        ExprInfo ei = mExprs.get(index);
        return ei != null && ei.mEvaluator != null;
    }

    /**
     * Cancel any current background task associated with the given ExprInfo.
     * @param quiet suppress cancellation message
     * @return true if we cancelled an initial evaluation
     */
    private boolean cancel(ExprInfo expr, boolean quiet) {
        if (expr.mEvaluator != null) {
            if (quiet && (expr.mEvaluator instanceof AsyncEvaluator)) {
                ((AsyncEvaluator)(expr.mEvaluator)).suppressCancelMessage();
            }
            // Reevaluation in progress.
            if (expr.mVal.get() != null) {
                expr.mEvaluator.cancel(true);
                expr.mResultStringOffsetReq = expr.mResultStringOffset;
                // Backgound computation touches only constructive reals.
                // OK not to wait.
                expr.mEvaluator = null;
            } else {
                expr.mEvaluator.cancel(true);
                if (expr == mMainExpr) {
                    // The expression is modifiable, and the AsyncTask is reading it.
                    // There seems to be no good way to wait for cancellation.
                    // Give ourselves a new copy to work on instead.
                    mMainExpr.mExpr = (CalculatorExpr)mMainExpr.mExpr.clone();
                    // Approximation of constructive reals should be thread-safe,
                    // so we can let that continue until it notices the cancellation.
                    mChangedValue = true;    // Didn't do the expected evaluation.
                }
                expr.mEvaluator = null;
                return true;
            }
        }
        return false;
    }

    /**
     * Cancel any current background task associated with the given ExprInfo.
     * @param quiet suppress cancellation message
     * @return true if we cancelled an initial evaluation
     */
    public boolean cancel(long index, boolean quiet)
    {
        ExprInfo ei = mExprs.get(index);
        if (ei == null) {
            return false;
        } else {
            return cancel(ei, quiet);
        }
    }

    public void cancelAll(boolean quiet) {
        // TODO: May want to keep active evaluators in a HashSet to avoid traversing
        // all expressions we've looked at.
        for (ExprInfo expr: mExprs.values()) {
            cancel(expr, quiet);
        }
    }

    /**
     * Quietly cancel all evaluations associated with expressions other than the main one.
     * These are currently the evaluations associated with the history fragment.
     */
    public void cancelNonMain() {
        // TODO: May want to keep active evaluators in a HashSet to avoid traversing
        // all expressions we've looked at.
        for (ExprInfo expr: mExprs.values()) {
            if (expr != mMainExpr) {
                cancel(expr, true);
            }
        }
    }

    /**
     * Restore the evaluator state, including the current expression.
     */
    public void restoreInstanceState(DataInput in) {
        mChangedValue = true;
        try {
            mMainExpr.mDegreeMode = in.readBoolean();
            mMainExpr.mLongTimeout = in.readBoolean();
            mMainExpr.mExpr = new CalculatorExpr(in);
            mHasTrigFuncs = hasTrigFuncs();
        } catch (IOException e) {
            Log.v("Calculator", "Exception while restoring:\n" + e);
        }
    }

    /**
     * Save the evaluator state, including the expression and any saved value.
     */
    public void saveInstanceState(DataOutput out) {
        try {
            out.writeBoolean(mMainExpr.mDegreeMode);
            out.writeBoolean(mMainExpr.mLongTimeout);
            mMainExpr.mExpr.write(out);
        } catch (IOException e) {
            Log.v("Calculator", "Exception while saving state:\n" + e);
        }
    }


    /**
     * Append a button press to the main expression.
     * @param id Button identifier for the character or operator to be added.
     * @return false if we rejected the insertion due to obvious syntax issues, and the expression
     * is unchanged; true otherwise
     */
    public boolean append(int id) {
        if (id == R.id.fun_10pow) {
            add10pow();  // Handled as macro expansion.
            return true;
        } else {
            mChangedValue = mChangedValue || !KeyMaps.isBinary(id);
            if (mMainExpr.mExpr.add(id)) {
                if (!mHasTrigFuncs) {
                    mHasTrigFuncs = KeyMaps.isTrigFunc(id);
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Delete last taken from main expression.
     */
    public void delete() {
        mChangedValue = true;
        mMainExpr.mExpr.delete();
        if (mMainExpr.mExpr.isEmpty()) {
            mMainExpr.mLongTimeout = false;
        }
        mHasTrigFuncs = hasTrigFuncs();
    }

    /**
     * Set degree mode for main expression.
     */
    public void setDegreeMode(boolean degreeMode) {
        mChangedValue = true;
        mMainExpr.mDegreeMode = degreeMode;

        mSharedPrefs.edit()
                .putBoolean(KEY_PREF_DEGREE_MODE, degreeMode)
                .apply();
    }

    /**
     * Return an ExprInfo for a copy of the expression with the given index.
     * We remove trailing binary operators in the copy.
     * mTimeStamp is not copied.
     */
    private ExprInfo copy(long index, boolean copyValue) {
        ExprInfo fromEi = mExprs.get(index);
        ExprInfo ei = new ExprInfo((CalculatorExpr)fromEi.mExpr.clone(), fromEi.mDegreeMode);
        while (ei.mExpr.hasTrailingBinary()) {
            ei.mExpr.delete();
        }
        if (copyValue) {
            ei.mVal = new AtomicReference<UnifiedReal>(fromEi.mVal.get());
            ei.mResultString = fromEi.mResultString;
            ei.mResultStringOffset = ei.mResultStringOffsetReq = fromEi.mResultStringOffset;
            ei.mMsdIndex = fromEi.mMsdIndex;
        }
        ei.mLongTimeout = fromEi.mLongTimeout;
        return ei;
    }

    /**
     * Return an ExprInfo corresponding to the sum of the expressions at the
     * two indices.
     * index1 should correspond to an immutable expression, and should thus NOT
     * be MAIN_INDEX. Index2 may be MAIN_INDEX. Both expressions are presumed
     * to have been evaluated.  The result is unevaluated.
     * Can return null if evaluation resulted in an error (a very unlikely case).
     */
    private ExprInfo sum(long index1, long index2) {
        return generalized_sum(index1, index2, R.id.op_add);
    }

    /**
     * Return an ExprInfo corresponding to the subtraction of the value at the subtrahend index
     * from value at the minuend index (minuend - subtrahend = result). Both are presumed to have
     * been previously evaluated. The result is unevaluated. Can return null.
     */
    private ExprInfo difference(long minuendIndex, long subtrahendIndex) {
        return generalized_sum(minuendIndex, subtrahendIndex, R.id.op_sub);
    }

    private ExprInfo generalized_sum(long index1, long index2, int op) {
        // TODO: Consider not collapsing expr2, to save database space.
        // Note that this is a bit tricky, since our expressions can contain unbalanced lparens.
        CalculatorExpr result = new CalculatorExpr();
        CalculatorExpr collapsed1 = getCollapsedExpr(index1);
        CalculatorExpr collapsed2 = getCollapsedExpr(index2);
        if (collapsed1 == null || collapsed2 == null) {
            return null;
        }
        result.append(collapsed1);
        result.add(op);
        result.append(collapsed2);
        ExprInfo resultEi = new ExprInfo(result, false /* dont care about degrees/radians */);
        resultEi.mLongTimeout = mExprs.get(index1).mLongTimeout
                || mExprs.get(index2).mLongTimeout;
        return resultEi;
    }

    /**
     * Add the expression described by the argument to the database.
     * Returns the new row id in the database.
     * Fills in timestamp in ei, if it was not previously set.
     * If in_history is true, add it with a positive index, so it will appear in the history.
     */
    private long addToDB(boolean in_history, ExprInfo ei) {
        byte[] serializedExpr = ei.mExpr.toBytes();
        ExpressionDB.RowData rd = new ExpressionDB.RowData(serializedExpr, ei.mDegreeMode,
                ei.mLongTimeout, 0);
        long resultIndex = mExprDB.addRow(!in_history, rd);
        if (mExprs.get(resultIndex) != null) {
            throw new AssertionError("result slot already occupied! + Slot = " + resultIndex);
        }
        // Add newly assigned date to the cache.
        ei.mTimeStamp = rd.mTimeStamp;
        if (resultIndex == MAIN_INDEX) {
            throw new AssertionError("Should not store main expression");
        }
        mExprs.put(resultIndex, ei);
        return resultIndex;
    }

    /**
     * Preserve a copy of the expression at old_index at a new index.
     * This is useful only of old_index is MAIN_INDEX or HISTORY_MAIN_INDEX.
     * This assumes that initial evaluation completed suceessfully.
     * @param in_history use a positive index so the result appears in the history.
     * @return the new index
     */
    public long preserve(long old_index, boolean in_history) {
        ExprInfo ei = copy(old_index, true);
        if (ei.mResultString == null || ei.mResultString == ERRONEOUS_RESULT) {
            throw new AssertionError("Preserving unevaluated expression");
        }
        return addToDB(in_history, ei);
    }

    /**
     * Preserve a copy of the current main expression as the most recent history entry,
     * assuming it is already in the database, but may have been lost from the cache.
     */
    public void represerve() {
        long resultIndex = getMaxIndex();
        // This requires database access only if the local state was preserved, but we
        // recreated the Evaluator.  That excludes the common cases of device rotation, etc.
        // TODO: Revisit once we deal with database failures. We could just copy from
        // MAIN_INDEX instead, but that loses the timestamp.
        ensureExprIsCached(resultIndex);
    }

    /**
     * Discard previous expression in HISTORY_MAIN_INDEX and replace it by a fresh copy
     * of the main expression. Note that the HISTORY_MAIN_INDEX expresssion is not preserved
     * in the database or anywhere else; it is always reconstructed when needed.
     */
    public void copyMainToHistory() {
        cancel(HISTORY_MAIN_INDEX, true /* quiet */);
        ExprInfo ei = copy(MAIN_INDEX, true);
        mExprs.put(HISTORY_MAIN_INDEX, ei);
    }

    /**
     * @return the {@link CalculatorExpr} representation of the result of the given
     * expression.
     * The resulting expression contains a single "token" with the pre-evaluated result.
     * The client should ensure that this is never invoked unless initial evaluation of the
     * expression has been completed.
     */
    private CalculatorExpr getCollapsedExpr(long index) {
        long real_index = isMutableIndex(index) ? preserve(index, false) : index;
        final ExprInfo ei = mExprs.get(real_index);
        final String rs = ei.mResultString;
        // An error can occur here only under extremely unlikely conditions.
        // Check anyway, and just refuse.
        // rs *should* never be null, but it happens. Check as a workaround to protect against
        // crashes until we find the root cause (b/34801142)
        if (rs == ERRONEOUS_RESULT || rs == null) {
            return null;
        }
        final int dotIndex = rs.indexOf('.');
        final int leastDigOffset = getLsdOffset(ei.mVal.get(), rs, dotIndex);
        return ei.mExpr.abbreviate(real_index,
                getShortString(rs, getMsdIndexOf(rs), leastDigOffset));
    }

    /**
     * Abbreviate the indicated expression to a pre-evaluated expression node,
     * and use that as the new main expression.
     * This should not be called unless the expression was previously evaluated and produced a
     * non-error result.  Pre-evaluated expressions can never represent an expression for which
     * evaluation to a constructive real diverges.  Subsequent re-evaluation will also not
     * diverge, though it may generate errors of various kinds.  E.g.  sqrt(-10^-1000) .
     */
    public void collapse(long index) {
        final boolean longTimeout = mExprs.get(index).mLongTimeout;
        final CalculatorExpr abbrvExpr = getCollapsedExpr(index);
        clearMain();
        mMainExpr.mExpr.append(abbrvExpr);
        mMainExpr.mLongTimeout = longTimeout;
        mChangedValue = true;
        mHasTrigFuncs = false;  // Degree mode no longer affects expression value.
    }

    /**
     * Mark the expression as changed, preventing next evaluation request from being ignored.
     */
    public void touch() {
        mChangedValue = true;
    }

    private abstract class SetWhenDoneListener implements EvaluationListener {
        private void badCall() {
            throw new AssertionError("unexpected callback");
        }
        abstract void setNow();
        @Override
        public void onCancelled(long index) {}  // Extremely unlikely; leave unset.
        @Override
        public void onError(long index, int errorId) {}  // Extremely unlikely; leave unset.
        @Override
        public void onEvaluate(long index, int initPrecOffset, int msdIndex, int lsdOffset,
                String truncatedWholePart) {
            setNow();
        }
        @Override
        public void onReevaluate(long index) {
            badCall();
        }
    }

    private class SetMemoryWhenDoneListener extends SetWhenDoneListener {
        final long mIndex;
        final boolean mPersist;
        SetMemoryWhenDoneListener(long index, boolean persist) {
            mIndex = index;
            mPersist = persist;
        }
        @Override
        void setNow() {
            if (mMemoryIndex != 0) {
                throw new AssertionError("Overwriting nonzero memory index");
            }
            if (mPersist) {
                setMemoryIndex(mIndex);
            } else {
                mMemoryIndex = mIndex;
            }
        }
    }

    private class SetSavedWhenDoneListener extends SetWhenDoneListener {
        final long mIndex;
        SetSavedWhenDoneListener(long index) {
            mIndex = index;
        }
        @Override
        void setNow() {
            mSavedIndex = mIndex;
        }
    }

    /**
     * Set the local and persistent memory index.
     */
    private void setMemoryIndex(long index) {
        mMemoryIndex = index;
        mSharedPrefs.edit()
                .putLong(KEY_PREF_MEMORY_INDEX, index)
                .apply();

        if (mCallback != null) {
            mCallback.onMemoryStateChanged();
        }
    }

    /**
     * Set the local and persistent saved index.
     */
    private void setSavedIndex(long index) {
        mSavedIndex = index;
        mSharedPrefs.edit()
                .putLong(KEY_PREF_SAVED_INDEX, index)
                .apply();
    }

    /**
     * Set mMemoryIndex (possibly including the persistent version) to index when we finish
     * evaluating the corresponding expression.
     */
    void setMemoryIndexWhenEvaluated(long index, boolean persist) {
        requireResult(index, new SetMemoryWhenDoneListener(index, persist), mDummyCharMetricsInfo);
    }

    /**
     * Set mSavedIndex (not the persistent version) to index when we finish evaluating
     * the corresponding expression.
     */
    void setSavedIndexWhenEvaluated(long index) {
        requireResult(index, new SetSavedWhenDoneListener(index), mDummyCharMetricsInfo);
    }

    /**
     * Save an immutable version of the expression at the given index as the saved value.
     * mExpr is left alone.  Return false if result is unavailable.
     */
    private boolean copyToSaved(long index) {
        if (mExprs.get(index).mResultString == null
                || mExprs.get(index).mResultString == ERRONEOUS_RESULT) {
            return false;
        }
        setSavedIndex(isMutableIndex(index) ? preserve(index, false) : index);
        return true;
    }

    /**
     * Save an immutable version of the expression at the given index as the "memory" value.
     * The expression at index is presumed to have been evaluated.
     */
    public void copyToMemory(long index) {
        setMemoryIndex(isMutableIndex(index) ? preserve(index, false) : index);
    }

    /**
     * Save an an expression representing the sum of "memory" and the expression with the
     * given index. Make mMemoryIndex point to it when we complete evaluating.
     */
    public void addToMemory(long index) {
        ExprInfo newEi = sum(mMemoryIndex, index);
        if (newEi != null) {
            long newIndex = addToDB(false, newEi);
            mMemoryIndex = 0;  // Invalidate while we're evaluating.
            setMemoryIndexWhenEvaluated(newIndex, true /* persist */);
        }
    }

    /**
     * Save an an expression representing the subtraction of the expression with the given index
     * from "memory." Make mMemoryIndex point to it when we complete evaluating.
     */
    public void subtractFromMemory(long index) {
        ExprInfo newEi = difference(mMemoryIndex, index);
        if (newEi != null) {
            long newIndex = addToDB(false, newEi);
            mMemoryIndex = 0;  // Invalidate while we're evaluating.
            setMemoryIndexWhenEvaluated(newIndex, true /* persist */);
        }
    }

    /**
     * Return index of "saved" expression, or 0.
     */
    public long getSavedIndex() {
        return mSavedIndex;
    }

    /**
     * Return index of "memory" expression, or 0.
     */
    public long getMemoryIndex() {
        return mMemoryIndex;
    }

    private Uri uriForSaved() {
        return new Uri.Builder().scheme("tag")
                                .encodedOpaquePart(mSavedName)
                                .build();
    }

    /**
     * Save the index expression as the saved location and return a URI describing it.
     * The URI is used to distinguish this particular result from others we may generate.
     */
    public Uri capture(long index) {
        if (!copyToSaved(index)) return null;
        // Generate a new (entirely private) URI for this result.
        // Attempt to conform to RFC4151, though it's unclear it matters.
        final TimeZone tz = TimeZone.getDefault();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(tz);
        final String isoDate = df.format(new Date());
        mSavedName = "calculator2.android.com," + isoDate + ":"
                + (new Random().nextInt() & 0x3fffffff);
        mSharedPrefs.edit()
                .putString(KEY_PREF_SAVED_NAME, mSavedName)
                .apply();
        return uriForSaved();
    }

    public boolean isLastSaved(Uri uri) {
        return mSavedIndex != 0 && uri.equals(uriForSaved());
    }

    /**
     * Append the expression at index as a pre-evaluated expression to the main expression.
     */
    public void appendExpr(long index) {
        ExprInfo ei = mExprs.get(index);
        mChangedValue = true;
        mMainExpr.mLongTimeout |= ei.mLongTimeout;
        CalculatorExpr collapsed = getCollapsedExpr(index);
        if (collapsed != null) {
            mMainExpr.mExpr.append(getCollapsedExpr(index));
        }
    }

    /**
     * Add the power of 10 operator to the main expression.
     * This is treated essentially as a macro expansion.
     */
    private void add10pow() {
        CalculatorExpr ten = new CalculatorExpr();
        ten.add(R.id.digit_1);
        ten.add(R.id.digit_0);
        mChangedValue = true;  // For consistency.  Reevaluation is probably not useful.
        mMainExpr.mExpr.append(ten);
        mMainExpr.mExpr.add(R.id.op_pow);
    }

    /**
     * Ensure that the expression with the given index is in mExprs.
     * We assume that if it's either already in mExprs or mExprDB.
     * When we're done, the expression in mExprs may still contain references to other
     * subexpressions that are not yet cached.
     */
    private ExprInfo ensureExprIsCached(long index) {
        ExprInfo ei = mExprs.get(index);
        if (ei != null) {
            return ei;
        }
        if (index == MAIN_INDEX) {
            throw new AssertionError("Main expression should be cached");
        }
        ExpressionDB.RowData row = mExprDB.getRow(index);
        DataInputStream serializedExpr =
                new DataInputStream(new ByteArrayInputStream(row.mExpression));
        try {
            ei = new ExprInfo(new CalculatorExpr(serializedExpr), row.degreeMode());
            ei.mTimeStamp = row.mTimeStamp;
            ei.mLongTimeout = row.longTimeout();
        } catch(IOException e) {
            throw new AssertionError("IO Exception without real IO:" + e);
        }
        ExprInfo newEi = mExprs.putIfAbsent(index, ei);
        return newEi == null ? ei : newEi;
    }

    @Override
    public CalculatorExpr getExpr(long index) {
        return ensureExprIsCached(index).mExpr;
    }

    /*
     * Return timestamp associated with the expression in milliseconds since epoch.
     * Yields zero if the expression has not been written to or read from the database.
     */
    public long getTimeStamp(long index) {
        return ensureExprIsCached(index).mTimeStamp;
    }

    @Override
    public boolean getDegreeMode(long index) {
        return ensureExprIsCached(index).mDegreeMode;
    }

    @Override
    public UnifiedReal getResult(long index) {
        return ensureExprIsCached(index).mVal.get();
    }

    @Override
    public UnifiedReal putResultIfAbsent(long index, UnifiedReal result) {
        ExprInfo ei = mExprs.get(index);
        if (ei.mVal.compareAndSet(null, result)) {
            return result;
        } else {
            // Cannot change once non-null.
            return ei.mVal.get();
        }
    }

    /**
     * Does the current main expression contain trig functions?
     * Might its value depend on DEG/RAD mode?
     */
    public boolean hasTrigFuncs() {
        return mHasTrigFuncs;
    }

    /**
     * Maximum number of characters in a scientific notation exponent.
     */
    private static final int MAX_EXP_CHARS = 8;

    /**
     * Return the index of the character after the exponent starting at s[offset].
     * Return offset if there is no exponent at that position.
     * Exponents have syntax E[-]digit* .  "E2" and "E-2" are valid.  "E+2" and "e2" are not.
     * We allow any Unicode digits, and either of the commonly used minus characters.
     */
    public static int exponentEnd(String s, int offset) {
        int i = offset;
        int len = s.length();
        if (i >= len - 1 || s.charAt(i) != 'E') {
            return offset;
        }
        ++i;
        if (KeyMaps.keyForChar(s.charAt(i)) == R.id.op_sub) {
            ++i;
        }
        if (i == len || !Character.isDigit(s.charAt(i))) {
            return offset;
        }
        ++i;
        while (i < len && Character.isDigit(s.charAt(i))) {
            ++i;
            if (i > offset + MAX_EXP_CHARS) {
                return offset;
            }
        }
        return i;
    }

    /**
     * Add the exponent represented by s[begin..end) to the constant at the end of current
     * expression.
     * The end of the current expression must be a constant.  Exponents have the same syntax as
     * for exponentEnd().
     */
    public void addExponent(String s, int begin, int end) {
        int sign = 1;
        int exp = 0;
        int i = begin + 1;
        // We do the decimal conversion ourselves to exactly match exponentEnd() conventions
        // and handle various kinds of digits on input.  Also avoids allocation.
        if (KeyMaps.keyForChar(s.charAt(i)) == R.id.op_sub) {
            sign = -1;
            ++i;
        }
        for (; i < end; ++i) {
            exp = 10 * exp + Character.digit(s.charAt(i), 10);
        }
        mMainExpr.mExpr.addExponent(sign * exp);
        mChangedValue = true;
    }

    /**
     * Generate a String representation of the expression at the given index.
     * This has the side effect of adding the expression to mExprs.
     * The expression must exist in the database.
     */
    public String getExprAsString(long index) {
        return getExprAsSpannable(index).toString();
    }

    public Spannable getExprAsSpannable(long index) {
        return getExpr(index).toSpannableStringBuilder(mContext);
    }

    /**
     * Generate a String representation of all expressions in the database.
     * Debugging only.
     */
    public String historyAsString() {
        final long startIndex = getMinIndex();
        final long endIndex = getMaxIndex();
        final StringBuilder sb = new StringBuilder();
        for (long i = getMinIndex(); i < ExpressionDB.MAXIMUM_MIN_INDEX; ++i) {
            sb.append(i).append(": ").append(getExprAsString(i)).append("\n");
        }
        for (long i = 1; i < getMaxIndex(); ++i) {
            sb.append(i).append(": ").append(getExprAsString(i)).append("\n");
        }
        sb.append("Memory index = ").append(getMemoryIndex());
        sb.append(" Saved index = ").append(getSavedIndex()).append("\n");
        return sb.toString();
    }

    /**
     * Wait for pending writes to the database to complete.
     */
    public void waitForWrites() {
        mExprDB.waitForWrites();
    }

    /**
     * Destroy the current evaluator, forcing getEvaluator to allocate a new one.
     * This is needed for testing, since Robolectric apparently doesn't let us preserve
     * an open databse across tests. Cf. https://github.com/robolectric/robolectric/issues/1890 .
     */
    public void destroyEvaluator() {
        mExprDB.close();
        evaluator = null;
    }

    public interface Callback {
        void onMemoryStateChanged();
        void showMessageDialog(@StringRes int title, @StringRes int message,
                @StringRes int positiveButtonLabel, String tag);
    }
}
