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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Adapter for RecyclerView of HistoryItems.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private static final String TAG = "HistoryAdapter";

    private static final int EMPTY_VIEW_TYPE = 0;
    public static final int HISTORY_VIEW_TYPE = 1;

    private Evaluator mEvaluator;

    private final Calendar mCalendar = Calendar.getInstance();

    private List<HistoryItem> mDataSet;

    private boolean mIsResultLayout;
    private boolean mIsOneLine;
    private boolean mIsDisplayEmpty;

    public HistoryAdapter(ArrayList<HistoryItem> dataSet) {
        mDataSet = dataSet;
        setHasStableIds(true);
    }

    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View v;
        if (viewType == HISTORY_VIEW_TYPE) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.history_item, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.empty_history_view, parent, false);
        }
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(final HistoryAdapter.ViewHolder holder, int position) {
        final HistoryItem item = getItem(position);

        if (item.isEmptyView()) {
            return;
        }

        holder.mFormula.setText(item.getFormula());
        // Note: HistoryItems that are not the current expression will always have interesting ops.
        holder.mResult.setEvaluator(mEvaluator, item.getEvaluatorIndex());
        if (item.getEvaluatorIndex() == Evaluator.HISTORY_MAIN_INDEX) {
            holder.mDate.setText(R.string.title_current_expression);
            holder.mResult.setVisibility(mIsOneLine ? View.GONE : View.VISIBLE);
        } else {
            // If the previous item occurred on the same date, the current item does not need
            // a date header.
            if (shouldShowHeader(position, item)) {
                holder.mDate.setText(item.getDateString());
                // Special case -- very first item should not have a divider above it.
                holder.mDivider.setVisibility(position == getItemCount() - 1
                        ? View.GONE : View.VISIBLE);
            } else {
                holder.mDate.setVisibility(View.GONE);
                holder.mDivider.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (holder.getItemViewType() == EMPTY_VIEW_TYPE) {
            return;
        }
        mEvaluator.cancel(holder.getItemId(), true);

        holder.mDate.setVisibility(View.VISIBLE);
        holder.mDivider.setVisibility(View.VISIBLE);
        holder.mDate.setText(null);
        holder.mFormula.setText(null);
        holder.mResult.setText(null);

        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getEvaluatorIndex();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isEmptyView() ? EMPTY_VIEW_TYPE : HISTORY_VIEW_TYPE;
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public void setDataSet(ArrayList<HistoryItem> dataSet) {
        mDataSet = dataSet;
    }

    public void setIsResultLayout(boolean isResult) {
        mIsResultLayout = isResult;
    }

    public void setIsOneLine(boolean isOneLine) {
        mIsOneLine = isOneLine;
    }

    public void setIsDisplayEmpty(boolean isDisplayEmpty) {
        mIsDisplayEmpty = isDisplayEmpty;
    }

    public void setEvaluator(Evaluator evaluator) {
        mEvaluator = evaluator;
    }

    private int getEvaluatorIndex(int position) {
        if (mIsDisplayEmpty || mIsResultLayout) {
            return (int) (mEvaluator.getMaxIndex() - position);
        } else {
            // Account for the additional "Current Expression" with the +1.
            return (int) (mEvaluator.getMaxIndex() - position + 1);
        }
    }

    private boolean shouldShowHeader(int position, HistoryItem item) {
        if (position == getItemCount() - 1) {
            // First/oldest element should always show the header.
            return true;
        }
        final HistoryItem prevItem = getItem(position + 1);
        // We need to use Calendars to determine this because of Daylight Savings.
        mCalendar.setTimeInMillis(item.getTimeInMillis());
        final int year = mCalendar.get(Calendar.YEAR);
        final int day = mCalendar.get(Calendar.DAY_OF_YEAR);
        mCalendar.setTimeInMillis(prevItem.getTimeInMillis());
        final int prevYear = mCalendar.get(Calendar.YEAR);
        final int prevDay = mCalendar.get(Calendar.DAY_OF_YEAR);
        return year != prevYear || day != prevDay;
    }

    /**
     * Gets the HistoryItem from mDataSet, lazy-filling the dataSet if necessary.
     */
    private HistoryItem getItem(int position) {
        HistoryItem item = mDataSet.get(position);
        // Lazy-fill the data set.
        if (item == null) {
            final int evaluatorIndex = getEvaluatorIndex(position);
            item = new HistoryItem(evaluatorIndex,
                    mEvaluator.getTimeStamp(evaluatorIndex),
                    mEvaluator.getExprAsSpannable(evaluatorIndex));
            mDataSet.set(position, item);
        }
        return item;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mDate;
        private AlignedTextView mFormula;
        private CalculatorResult mResult;
        private View mDivider;

        public ViewHolder(View v, int viewType) {
            super(v);
            if (viewType == EMPTY_VIEW_TYPE) {
                return;
            }
            mDate = (TextView) v.findViewById(R.id.history_date);
            mFormula = (AlignedTextView) v.findViewById(R.id.history_formula);
            mResult = (CalculatorResult) v.findViewById(R.id.history_result);
            mDivider = v.findViewById(R.id.history_divider);
        }

        public AlignedTextView getFormula() {
            return mFormula;
        }

        public CalculatorResult getResult() {
            return mResult;
        }

        public TextView getDate() {
            return mDate;
        }

        public View getDivider() {
            return mDivider;
        }
    }
}