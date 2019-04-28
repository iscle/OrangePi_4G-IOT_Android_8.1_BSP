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

package com.android.tv.ui;

import android.content.Context;
import android.content.res.Resources;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.util.DurationTimer;
import com.android.tv.analytics.Tracker;
import com.android.tv.data.Channel;
import com.android.tv.util.TvInputManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectInputView extends VerticalGridView implements
        TvTransitionManager.TransitionLayout {
    private static final String TAG = "SelectInputView";
    private static final boolean DEBUG = false;
    public static final String SCREEN_NAME = "Input selection";
    private static final int TUNER_INPUT_POSITION = 0;

    private final TvInputManagerHelper mTvInputManagerHelper;
    private final List<TvInputInfo> mInputList = new ArrayList<>();
    private final TvInputManagerHelper.HardwareInputComparator mComparator;
    private final Tracker mTracker;
    private final DurationTimer mViewDurationTimer = new DurationTimer();
    private final TvInputCallback mTvInputCallback = new TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            buildInputListAndNotify();
            updateSelectedPositionIfNeeded();
        }

        @Override
        public void onInputRemoved(String inputId) {
            buildInputListAndNotify();
            updateSelectedPositionIfNeeded();
        }

        @Override
        public void onInputUpdated(String inputId) {
            buildInputListAndNotify();
            updateSelectedPositionIfNeeded();
        }

        @Override
        public void onInputStateChanged(String inputId, int state) {
            buildInputListAndNotify();
            updateSelectedPositionIfNeeded();
        }

        private void updateSelectedPositionIfNeeded() {
            if (!isFocusable() || mSelectedInput == null) {
                return;
            }
            if (!isInputEnabled(mSelectedInput)) {
                setSelectedPosition(TUNER_INPUT_POSITION);
                return;
            }
            if (getInputPosition(mSelectedInput.getId()) != getSelectedPosition()) {
                setSelectedPosition(getInputPosition(mSelectedInput.getId()));
            }
        }
    };

    private Channel mCurrentChannel;
    private OnInputSelectedCallback mCallback;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSelectedInput == null) {
                return;
            }
            // TODO: pass english label to tracker http://b/22355024
            final String label = mSelectedInput.loadLabel(getContext()).toString();
            mTracker.sendInputSelected(label);
            if (mCallback != null) {
                if (mSelectedInput.isPassthroughInput()) {
                    mCallback.onPassthroughInputSelected(mSelectedInput);
                } else {
                    mCallback.onTunerInputSelected();
                }
            }
        }
    };

    private final int mInputItemHeight;
    private final long mShowDurationMillis;
    private final long mRippleAnimDurationMillis;
    private final int mTextColorPrimary;
    private final int mTextColorSecondary;
    private final int mTextColorDisabled;
    private final View mItemViewForMeasure;

    private boolean mResetTransitionAlpha;
    private TvInputInfo mSelectedInput;
    private int mMaxItemWidth;

    public SelectInputView(Context context) {
        this(context, null, 0);
    }

    public SelectInputView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SelectInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAdapter(new InputListAdapter());

        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mTracker = appSingletons.getTracker();
        mTvInputManagerHelper = appSingletons.getTvInputManagerHelper();
        mComparator =
                new TvInputManagerHelper.HardwareInputComparator(context, mTvInputManagerHelper);

        Resources resources = context.getResources();
        mInputItemHeight = resources.getDimensionPixelSize(R.dimen.input_banner_item_height);
        mShowDurationMillis = resources.getInteger(R.integer.select_input_show_duration);
        mRippleAnimDurationMillis = resources.getInteger(
                R.integer.select_input_ripple_anim_duration);
        mTextColorPrimary = resources.getColor(R.color.select_input_text_color_primary, null);
        mTextColorSecondary = resources.getColor(R.color.select_input_text_color_secondary, null);
        mTextColorDisabled = resources.getColor(R.color.select_input_text_color_disabled, null);

        mItemViewForMeasure = LayoutInflater.from(context).inflate(
                R.layout.select_input_item, this, false);
        buildInputListAndNotify();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (DEBUG) Log.d(TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");
        scheduleHide();

        if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            // Go down to the next available input.
            int currentPosition = mInputList.indexOf(mSelectedInput);
            int nextPosition = currentPosition;
            while (true) {
                nextPosition = (nextPosition + 1) % mInputList.size();
                if (isInputEnabled(mInputList.get(nextPosition))) {
                    break;
                }
                if (nextPosition == currentPosition) {
                    nextPosition = 0;
                    break;
                }
            }
            setSelectedPosition(nextPosition);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onEnterAction(boolean fromEmptyScene) {
        mTracker.sendShowInputSelection();
        mTracker.sendScreenView(SCREEN_NAME);
        mViewDurationTimer.start();
        scheduleHide();

        mResetTransitionAlpha = fromEmptyScene;
        buildInputListAndNotify();
        mTvInputManagerHelper.addCallback(mTvInputCallback);
        String currentInputId = mCurrentChannel != null && mCurrentChannel.isPassthrough() ?
                mCurrentChannel.getInputId() : null;
        if (currentInputId != null
                && !isInputEnabled(mTvInputManagerHelper.getTvInputInfo(currentInputId))) {
            // If current input is disabled, the tuner input will be focused.
            setSelectedPosition(TUNER_INPUT_POSITION);
        } else {
            setSelectedPosition(getInputPosition(currentInputId));
        }
        setFocusable(true);
        requestFocus();
    }

    private int getInputPosition(String inputId) {
        if (inputId != null) {
            for (int i = 0; i < mInputList.size(); ++i) {
                if (TextUtils.equals(mInputList.get(i).getId(), inputId)) {
                    return i;
                }
            }
        }
        return TUNER_INPUT_POSITION;
    }

    @Override
    public void onExitAction() {
        mTracker.sendHideInputSelection(mViewDurationTimer.reset());
        mTvInputManagerHelper.removeCallback(mTvInputCallback);
        removeCallbacks(mHideRunnable);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = mInputItemHeight * mInputList.size();
        super.onMeasure(MeasureSpec.makeMeasureSpec(mMaxItemWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private void scheduleHide() {
        removeCallbacks(mHideRunnable);
        postDelayed(mHideRunnable, mShowDurationMillis);
    }

    private void buildInputListAndNotify() {
        mInputList.clear();
        Map<String, TvInputInfo> inputMap = new HashMap<>();
        boolean foundTuner = false;
        for (TvInputInfo input : mTvInputManagerHelper.getTvInputInfos(false, false)) {
            if (input.isPassthroughInput()) {
                if (!input.isHidden(getContext())) {
                    mInputList.add(input);
                    inputMap.put(input.getId(), input);
                }
            } else if (!foundTuner) {
                foundTuner = true;
                mInputList.add(input);
            }
        }
        // Do not show HDMI ports if a CEC device is directly connected to the port.
        for (TvInputInfo input : inputMap.values()) {
            if (input.getParentId() != null && !input.isConnectedToHdmiSwitch()) {
                mInputList.remove(inputMap.get(input.getParentId()));
            }
        }
        Collections.sort(mInputList, mComparator);

        // Update the max item width.
        mMaxItemWidth = 0;
        for (TvInputInfo input : mInputList) {
            setItemViewText(mItemViewForMeasure, input);
            mItemViewForMeasure.measure(0, 0);
            int width = mItemViewForMeasure.getMeasuredWidth();
            if (width > mMaxItemWidth) {
                mMaxItemWidth = width;
            }
        }

        getAdapter().notifyDataSetChanged();
    }

    private void setItemViewText(View v, TvInputInfo input) {
        TextView inputLabelView = (TextView) v.findViewById(R.id.input_label);
        TextView secondaryInputLabelView = (TextView) v.findViewById(R.id.secondary_input_label);
        CharSequence customLabel = input.loadCustomLabel(getContext());
        CharSequence label = input.loadLabel(getContext());
        if (TextUtils.isEmpty(customLabel) || customLabel.equals(label)) {
            inputLabelView.setText(label);
            secondaryInputLabelView.setVisibility(View.GONE);
        } else {
            inputLabelView.setText(customLabel);
            secondaryInputLabelView.setText(label);
            secondaryInputLabelView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isInputEnabled(TvInputInfo input) {
        return mTvInputManagerHelper.getInputState(input)
                != TvInputManager.INPUT_STATE_DISCONNECTED;
    }

    /**
     * Sets a callback which receives the notifications of input selection.
     */
    public void setOnInputSelectedCallback(OnInputSelectedCallback callback) {
        mCallback = callback;
    }

    /**
     * Sets the current channel. The initial selection will be the input which contains the
     * {@code channel}.
     */
    public void setCurrentChannel(Channel channel) {
        mCurrentChannel = channel;
    }

    class InputListAdapter extends RecyclerView.Adapter<InputListAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.select_input_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            TvInputInfo input = mInputList.get(position);
            if (input.isPassthroughInput()) {
                if (isInputEnabled(input)) {
                    holder.itemView.setFocusable(true);
                    holder.inputLabelView.setTextColor(mTextColorPrimary);
                    holder.secondaryInputLabelView.setTextColor(mTextColorSecondary);
                } else {
                    holder.itemView.setFocusable(false);
                    holder.inputLabelView.setTextColor(mTextColorDisabled);
                    holder.secondaryInputLabelView.setTextColor(mTextColorDisabled);
                }
                setItemViewText(holder.itemView, input);
            } else {
                holder.itemView.setFocusable(true);
                holder.inputLabelView.setTextColor(mTextColorPrimary);
                holder.inputLabelView.setText(R.string.input_long_label_for_tuner);
                holder.secondaryInputLabelView.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedInput = mInputList.get(position);
                    // The user made a selection. Hide this view after the ripple animation. But
                    // first, disable focus to avoid any further focus change during the animation.
                    setFocusable(false);
                    removeCallbacks(mHideRunnable);
                    postDelayed(mHideRunnable, mRippleAnimDurationMillis);
                }
            });
            holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        mSelectedInput = mInputList.get(position);
                    }
                }
            });

            if (mResetTransitionAlpha) {
                ViewUtils.setTransitionAlpha(holder.itemView, 1f);
            }
        }

        @Override
        public int getItemCount() {
            return mInputList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView inputLabelView;
            final TextView secondaryInputLabelView;

            ViewHolder(View v) {
                super(v);
                inputLabelView = (TextView) v.findViewById(R.id.input_label);
                secondaryInputLabelView = (TextView) v.findViewById(R.id.secondary_input_label);
            }
        }
    }

    /**
     * A callback interface for the input selection.
     */
    public interface OnInputSelectedCallback {
        /**
         * Called when the tuner input is selected.
         */
        void onTunerInputSelected();

        /**
         * Called when the passthrough input is selected.
         */
        void onPassthroughInputSelected(@NonNull TvInputInfo input);
    }
}
