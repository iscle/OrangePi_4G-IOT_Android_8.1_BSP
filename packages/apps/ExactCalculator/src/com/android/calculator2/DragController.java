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

package com.android.calculator2;

import android.animation.ArgbEvaluator;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

/**
 * Contains the logic for animating the recyclerview elements on drag.
 */
public final class DragController {

    private static final String TAG = "DragController";

    private static final ArgbEvaluator mColorEvaluator = new ArgbEvaluator();

    // References to views from the Calculator Display.
    private CalculatorFormula mDisplayFormula;
    private CalculatorResult mDisplayResult;
    private View mToolbar;

    private int mFormulaTranslationY;
    private int mFormulaTranslationX;
    private float mFormulaScale;
    private float mResultScale;

    private float mResultTranslationY;
    private int mResultTranslationX;

    private int mDisplayHeight;

    private int mFormulaStartColor;
    private int mFormulaEndColor;

    private int mResultStartColor;
    private int mResultEndColor;

    // The padding at the bottom of the RecyclerView itself.
    private int mBottomPaddingHeight;

    private boolean mAnimationInitialized;

    private boolean mOneLine;
    private boolean mIsDisplayEmpty;

    private AnimationController mAnimationController;

    private Evaluator mEvaluator;

    public void setEvaluator(Evaluator evaluator) {
        mEvaluator = evaluator;
    }

    public void initializeController(boolean isResult, boolean oneLine, boolean isDisplayEmpty) {
        mOneLine = oneLine;
        mIsDisplayEmpty = isDisplayEmpty;
        if (mIsDisplayEmpty) {
            // Empty display
            mAnimationController = new EmptyAnimationController();
        } else if (isResult) {
            // Result
            mAnimationController = new ResultAnimationController();
        } else {
            // There is something in the formula field. There may or may not be
            // a quick result.
            mAnimationController = new AnimationController();
        }
    }

    public void setDisplayFormula(CalculatorFormula formula) {
        mDisplayFormula = formula;
    }

    public void setDisplayResult(CalculatorResult result) {
        mDisplayResult = result;
    }

    public void setToolbar(View toolbar) {
        mToolbar = toolbar;
    }

    public void animateViews(float yFraction, RecyclerView recyclerView) {
        if (mDisplayFormula == null
                || mDisplayResult == null
                || mToolbar == null
                || mEvaluator == null) {
            // Bail if we aren't yet initialized.
            return;
        }

        final HistoryAdapter.ViewHolder vh =
                (HistoryAdapter.ViewHolder) recyclerView.findViewHolderForAdapterPosition(0);
        if (yFraction > 0 && vh != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (vh != null && !mIsDisplayEmpty
                && vh.getItemViewType() == HistoryAdapter.HISTORY_VIEW_TYPE) {
            final AlignedTextView formula = vh.getFormula();
            final CalculatorResult result = vh.getResult();
            final TextView date = vh.getDate();
            final View divider = vh.getDivider();

            if (!mAnimationInitialized) {
                mBottomPaddingHeight = recyclerView.getPaddingBottom();

                mAnimationController.initializeScales(formula, result);

                mAnimationController.initializeColorAnimators(formula, result);

                mAnimationController.initializeFormulaTranslationX(formula);

                mAnimationController.initializeFormulaTranslationY(formula, result);

                mAnimationController.initializeResultTranslationX(result);

                mAnimationController.initializeResultTranslationY(result);

                mAnimationInitialized = true;
            }

            result.setScaleX(mAnimationController.getResultScale(yFraction));
            result.setScaleY(mAnimationController.getResultScale(yFraction));

            formula.setScaleX(mAnimationController.getFormulaScale(yFraction));
            formula.setScaleY(mAnimationController.getFormulaScale(yFraction));

            formula.setPivotX(formula.getWidth() - formula.getPaddingEnd());
            formula.setPivotY(formula.getHeight() - formula.getPaddingBottom());

            result.setPivotX(result.getWidth() - result.getPaddingEnd());
            result.setPivotY(result.getHeight() - result.getPaddingBottom());

            formula.setTranslationX(mAnimationController.getFormulaTranslationX(yFraction));
            formula.setTranslationY(mAnimationController.getFormulaTranslationY(yFraction));

            result.setTranslationX(mAnimationController.getResultTranslationX(yFraction));
            result.setTranslationY(mAnimationController.getResultTranslationY(yFraction));

            formula.setTextColor((int) mColorEvaluator.evaluate(yFraction, mFormulaStartColor,
                    mFormulaEndColor));

            result.setTextColor((int) mColorEvaluator.evaluate(yFraction, mResultStartColor,
                    mResultEndColor));

            date.setTranslationY(mAnimationController.getDateTranslationY(yFraction));
            divider.setTranslationY(mAnimationController.getDateTranslationY(yFraction));
        } else if (mIsDisplayEmpty) {
            // There is no current expression but we still need to collect information
            // to translate the other viewholders.
            if (!mAnimationInitialized) {
                mAnimationController.initializeDisplayHeight();
                mAnimationInitialized = true;
            }
        }

        // Move up all ViewHolders above the current expression; if there is no current expression,
        // we're translating all the viewholders.
        for (int i = recyclerView.getChildCount() - 1;
             i >= mAnimationController.getFirstTranslatedViewHolderIndex();
             --i) {
            final RecyclerView.ViewHolder vh2 =
                    recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (vh2 != null) {
                final View view = vh2.itemView;
                if (view != null) {
                    view.setTranslationY(
                        mAnimationController.getHistoryElementTranslationY(yFraction));
                }
            }
        }
    }

    /**
     * Reset all initialized values.
     */
    public void initializeAnimation(boolean isResult, boolean oneLine, boolean isDisplayEmpty) {
        mAnimationInitialized = false;
        initializeController(isResult, oneLine, isDisplayEmpty);
    }

    public interface AnimateTextInterface {

        void initializeDisplayHeight();

        void initializeColorAnimators(AlignedTextView formula, CalculatorResult result);

        void initializeScales(AlignedTextView formula, CalculatorResult result);

        void initializeFormulaTranslationX(AlignedTextView formula);

        void initializeFormulaTranslationY(AlignedTextView formula, CalculatorResult result);

        void initializeResultTranslationX(CalculatorResult result);

        void initializeResultTranslationY(CalculatorResult result);

        float getResultTranslationX(float yFraction);

        float getResultTranslationY(float yFraction);

        float getResultScale(float yFraction);

        float getFormulaScale(float yFraction);

        float getFormulaTranslationX(float yFraction);

        float getFormulaTranslationY(float yFraction);

        float getDateTranslationY(float yFraction);

        float getHistoryElementTranslationY(float yFraction);

        // Return the lowest index of the first Viewholder to be translated upwards.
        // If there is no current expression, we translate all the viewholders; otherwise,
        // we start at index 1.
        int getFirstTranslatedViewHolderIndex();
    }

    // The default AnimationController when Display is in INPUT state and DisplayFormula is not
    // empty. There may or may not be a quick result.
    public class AnimationController implements DragController.AnimateTextInterface {

        public void initializeDisplayHeight() {
            // no-op
        }

        public void initializeColorAnimators(AlignedTextView formula, CalculatorResult result) {
            mFormulaStartColor = mDisplayFormula.getCurrentTextColor();
            mFormulaEndColor = formula.getCurrentTextColor();

            mResultStartColor = mDisplayResult.getCurrentTextColor();
            mResultEndColor = result.getCurrentTextColor();
        }

        public void initializeScales(AlignedTextView formula, CalculatorResult result) {
            // Calculate the scale for the text
            mFormulaScale = mDisplayFormula.getTextSize() / formula.getTextSize();
        }

        public void initializeFormulaTranslationY(AlignedTextView formula,
                CalculatorResult result) {
            if (mOneLine) {
                // Disregard result since we set it to GONE in the one-line case.
                mFormulaTranslationY =
                        mDisplayFormula.getPaddingBottom() - formula.getPaddingBottom()
                        - mBottomPaddingHeight;
            } else {
                // Baseline of formula moves by the difference in formula bottom padding and the
                // difference in result height.
                mFormulaTranslationY =
                        mDisplayFormula.getPaddingBottom() - formula.getPaddingBottom()
                                + mDisplayResult.getHeight() - result.getHeight()
                                - mBottomPaddingHeight;
            }
        }

        public void initializeFormulaTranslationX(AlignedTextView formula) {
            // Right border of formula moves by the difference in formula end padding.
            mFormulaTranslationX = mDisplayFormula.getPaddingEnd() - formula.getPaddingEnd();
        }

        public void initializeResultTranslationY(CalculatorResult result) {
            // Baseline of result moves by the difference in result bottom padding.
            mResultTranslationY = mDisplayResult.getPaddingBottom() - result.getPaddingBottom()
            - mBottomPaddingHeight;
        }

        public void initializeResultTranslationX(CalculatorResult result) {
            mResultTranslationX = mDisplayResult.getPaddingEnd() - result.getPaddingEnd();
        }

        public float getResultTranslationX(float yFraction) {
            return mResultTranslationX * (yFraction - 1f);
        }

        public float getResultTranslationY(float yFraction) {
            return mResultTranslationY * (yFraction - 1f);
        }

        public float getResultScale(float yFraction) {
            return 1f;
        }

        public float getFormulaScale(float yFraction) {
            return mFormulaScale + (1f - mFormulaScale) * yFraction;
        }

        public float getFormulaTranslationX(float yFraction) {
            return mFormulaTranslationX * (yFraction - 1f);
        }

        public float getFormulaTranslationY(float yFraction) {
            // Scale linearly between -FormulaTranslationY and 0.
            return mFormulaTranslationY * (yFraction - 1f);
        }

        public float getDateTranslationY(float yFraction) {
            // We also want the date to start out above the visible screen with
            // this distance decreasing as it's pulled down.
            // Account for the scaled formula height.
            return -mToolbar.getHeight() * (1f - yFraction)
                    + getFormulaTranslationY(yFraction)
                    - mDisplayFormula.getHeight() /getFormulaScale(yFraction) * (1f - yFraction);
        }

        public float getHistoryElementTranslationY(float yFraction) {
            return getDateTranslationY(yFraction);
        }

        public int getFirstTranslatedViewHolderIndex() {
            return 1;
        }
    }

    // The default AnimationController when Display is in RESULT state.
    public class ResultAnimationController extends AnimationController
            implements DragController.AnimateTextInterface {
        @Override
        public void initializeScales(AlignedTextView formula, CalculatorResult result) {
            final float textSize = mDisplayResult.getTextSize() * mDisplayResult.getScaleX();
            mResultScale = textSize / result.getTextSize();
            mFormulaScale = 1f;
        }

        @Override
        public void initializeFormulaTranslationY(AlignedTextView formula,
                CalculatorResult result) {
            // Baseline of formula moves by the difference in formula bottom padding and the
            // difference in the result height.
            mFormulaTranslationY = mDisplayFormula.getPaddingBottom() - formula.getPaddingBottom()
                            + mDisplayResult.getHeight() - result.getHeight()
                            - mBottomPaddingHeight;
        }

        @Override
        public void initializeFormulaTranslationX(AlignedTextView formula) {
            // Right border of formula moves by the difference in formula end padding.
            mFormulaTranslationX = mDisplayFormula.getPaddingEnd() - formula.getPaddingEnd();
        }

        @Override
        public void initializeResultTranslationY(CalculatorResult result) {
            // Baseline of result moves by the difference in result bottom padding.
            mResultTranslationY =  mDisplayResult.getPaddingBottom() - result.getPaddingBottom()
                    - mDisplayResult.getTranslationY()
                    - mBottomPaddingHeight;
        }

        @Override
        public void initializeResultTranslationX(CalculatorResult result) {
            mResultTranslationX = mDisplayResult.getPaddingEnd() - result.getPaddingEnd();
        }

        @Override
        public float getResultTranslationX(float yFraction) {
            return (mResultTranslationX * yFraction) - mResultTranslationX;
        }

        @Override
        public float getResultTranslationY(float yFraction) {
            return (mResultTranslationY * yFraction) - mResultTranslationY;
        }

        @Override
        public float getFormulaTranslationX(float yFraction) {
            return (mFormulaTranslationX * yFraction) -
                    mFormulaTranslationX;
        }

        @Override
        public float getFormulaTranslationY(float yFraction) {
            return getDateTranslationY(yFraction);
        }

        @Override
        public float getResultScale(float yFraction) {
            return mResultScale - (mResultScale * yFraction) + yFraction;
        }

        @Override
        public float getFormulaScale(float yFraction) {
            return 1f;
        }

        @Override
        public float getDateTranslationY(float yFraction) {
            // We also want the date to start out above the visible screen with
            // this distance decreasing as it's pulled down.
            return -mToolbar.getHeight() * (1f - yFraction)
                    + (mResultTranslationY * yFraction) - mResultTranslationY
                    - mDisplayFormula.getPaddingTop() +
                    (mDisplayFormula.getPaddingTop() * yFraction);
        }

        @Override
        public int getFirstTranslatedViewHolderIndex() {
            return 1;
        }
    }

    // The default AnimationController when Display is completely empty.
    public class EmptyAnimationController extends AnimationController
            implements DragController.AnimateTextInterface {
        @Override
        public void initializeDisplayHeight() {
            mDisplayHeight = mToolbar.getHeight() + mDisplayResult.getHeight()
                    + mDisplayFormula.getHeight();
        }

        @Override
        public void initializeScales(AlignedTextView formula, CalculatorResult result) {
            // no-op
        }

        @Override
        public void initializeFormulaTranslationY(AlignedTextView formula,
                CalculatorResult result) {
            // no-op
        }

        @Override
        public void initializeFormulaTranslationX(AlignedTextView formula) {
            // no-op
        }

        @Override
        public void initializeResultTranslationY(CalculatorResult result) {
            // no-op
        }

        @Override
        public void initializeResultTranslationX(CalculatorResult result) {
            // no-op
        }

        @Override
        public float getResultTranslationX(float yFraction) {
            return 0f;
        }

        @Override
        public float getResultTranslationY(float yFraction) {
            return 0f;
        }

        @Override
        public float getFormulaScale(float yFraction) {
            return 1f;
        }

        @Override
        public float getDateTranslationY(float yFraction) {
            return 0f;
        }

        @Override
        public float getHistoryElementTranslationY(float yFraction) {
            return -mDisplayHeight * (1f - yFraction) - mBottomPaddingHeight;
        }

        @Override
        public int getFirstTranslatedViewHolderIndex() {
            return 0;
        }
    }
}
