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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.util.DurationTimer;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelNumber;

import java.util.ArrayList;
import java.util.List;

public class KeypadChannelSwitchView extends LinearLayout implements
        TvTransitionManager.TransitionLayout {
    private static final String TAG = "KeypadChannelSwitchView";

    private static final int MAX_CHANNEL_NUMBER_DIGIT = 4;
    private static final int MAX_MINOR_CHANNEL_NUMBER_DIGIT = 3;
    private static final int MAX_CHANNEL_ITEM = 8;
    private static final String CHANNEL_DELIMITERS_REGEX = "[-\\.\\s]";
    public static final String SCREEN_NAME = "Channel switch";

    private final MainActivity mMainActivity;
    private final Tracker mTracker;
    private final DurationTimer mViewDurationTimer = new DurationTimer();
    private boolean mNavigated = false;
    @Nullable  //Once mChannels is set to null it should not be used again.
    private List<Channel> mChannels;
    private TextView mChannelNumberView;
    private ListView mChannelItemListView;
    private final ChannelNumber mTypedChannelNumber = new ChannelNumber();
    private final ArrayList<Channel> mChannelCandidates = new ArrayList<>();
    protected final ChannelItemAdapter mAdapter = new ChannelItemAdapter();
    private final LayoutInflater mLayoutInflater;
    private Channel mSelectedChannel;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mCurrentHeight = 0;
            if (mSelectedChannel != null) {
                mMainActivity.tuneToChannel(mSelectedChannel);
                mTracker.sendChannelNumberItemChosenByTimeout();
            } else {
                mMainActivity.getOverlayManager().hideOverlays(
                        TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_MENU
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT);
            }
        }
    };
    private final long mShowDurationMillis;
    private final long mRippleAnimDurationMillis;
    private final int mBaseViewHeight;
    private final int mItemHeight;
    private final int mResizeAnimDuration;
    private Animator mResizeAnimator;
    private final Interpolator mResizeInterpolator;
    // NOTE: getHeight() will be updated after layout() is called. mCurrentHeight is needed for
    // getting the latest updated value of the view height before layout().
    private int mCurrentHeight;

    public KeypadChannelSwitchView(Context context) {
        this(context, null, 0);
    }

    public KeypadChannelSwitchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeypadChannelSwitchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mMainActivity = (MainActivity) context;
        mTracker = TvApplication.getSingletons(context).getTracker();
        Resources resources = getResources();
        mLayoutInflater = LayoutInflater.from(context);
        mShowDurationMillis = resources.getInteger(R.integer.keypad_channel_switch_show_duration);
        mRippleAnimDurationMillis = resources.getInteger(
                R.integer.keypad_channel_switch_ripple_anim_duration);
        mBaseViewHeight = resources.getDimensionPixelSize(
                R.dimen.keypad_channel_switch_base_height);
        mItemHeight = resources.getDimensionPixelSize(R.dimen.keypad_channel_switch_item_height);
        mResizeAnimDuration = resources.getInteger(R.integer.keypad_channel_switch_anim_duration);
        mResizeInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.linear_out_slow_in);
    }

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();
        mChannelNumberView = (TextView) findViewById(R.id.channel_number);
        mChannelItemListView = (ListView) findViewById(R.id.channel_list);
        mChannelItemListView.setAdapter(mAdapter);
        mChannelItemListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= mAdapter.getCount()) {
                    // It can happen during closing.
                    return;
                }
                mChannelItemListView.setFocusable(false);
                final Channel channel = ((Channel) mAdapter.getItem(position));
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mChannelItemListView.setFocusable(true);
                        mMainActivity.tuneToChannel(channel);
                        mTracker.sendChannelNumberItemClicked();
                    }
                }, mRippleAnimDurationMillis);
            }
        });
        mChannelItemListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= mAdapter.getCount()) {
                    // It can happen during closing.
                    mSelectedChannel = null;
                } else {
                    mSelectedChannel = (Channel) mAdapter.getItem(position);
                }
                if (position != 0 && !mNavigated) {
                    mNavigated = true;
                    mTracker.sendChannelInputNavigated();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSelectedChannel = null;
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        scheduleHide();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        SoftPreconditions.checkNotNull(mChannels, TAG, "mChannels");
        if (isChannelNumberKey(keyCode)) {
            onNumberKeyUp(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if (ChannelNumber.isChannelNumberDelimiterKey(keyCode)) {
            onDelimiterKeyUp();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onEnterAction(boolean fromEmptyScene) {
        reset();
        if (fromEmptyScene) {
            ViewUtils.setTransitionAlpha(mChannelItemListView, 1f);
        }
        mNavigated = false;
        mViewDurationTimer.start();
        mTracker.sendShowChannelSwitch();
        mTracker.sendScreenView(SCREEN_NAME);
        updateView();
        scheduleHide();
    }

    @Override
    public void onExitAction() {
        mCurrentHeight = 0;
        mTracker.sendHideChannelSwitch(mViewDurationTimer.reset());
        cancelHide();
    }

    private void scheduleHide() {
        cancelHide();
        postDelayed(mHideRunnable, mShowDurationMillis);
    }

    private void cancelHide() {
        removeCallbacks(mHideRunnable);
    }

    private void reset() {
        mTypedChannelNumber.reset();
        mSelectedChannel = null;
        mChannelCandidates.clear();
        mAdapter.notifyDataSetChanged();
    }

    public void setChannels(@Nullable List<Channel> channels) {
        mChannels = channels;
    }

    public static boolean isChannelNumberKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
    }

    public void onNumberKeyUp(int num) {
        // Reset typed channel number in some cases.
        if (mTypedChannelNumber.majorNumber == null) {
            mTypedChannelNumber.reset();
        } else if (!mTypedChannelNumber.hasDelimiter
                && mTypedChannelNumber.majorNumber.length() >= MAX_CHANNEL_NUMBER_DIGIT) {
            mTypedChannelNumber.reset();
        } else if (mTypedChannelNumber.hasDelimiter
                && mTypedChannelNumber.minorNumber != null
                && mTypedChannelNumber.minorNumber.length() >= MAX_MINOR_CHANNEL_NUMBER_DIGIT) {
            mTypedChannelNumber.reset();
        }

        if (!mTypedChannelNumber.hasDelimiter) {
            mTypedChannelNumber.majorNumber += String.valueOf(num);
        } else {
            mTypedChannelNumber.minorNumber += String.valueOf(num);
        }
        mTracker.sendChannelNumberInput();
        updateView();
    }

    private void onDelimiterKeyUp() {
        if (mTypedChannelNumber.hasDelimiter || mTypedChannelNumber.majorNumber.length() == 0) {
            return;
        }
        mTypedChannelNumber.hasDelimiter = true;
        mTracker.sendChannelNumberInput();
        updateView();
    }

    private void updateView() {
        mChannelNumberView.setText(mTypedChannelNumber.toString() + "_");
        mChannelCandidates.clear();
        ArrayList<Channel> secondaryChannelCandidates = new ArrayList<>();
        for (Channel channel : mChannels) {
            ChannelNumber chNumber = ChannelNumber.parseChannelNumber(channel.getDisplayNumber());
            if (chNumber == null) {
                Log.i(TAG, "Malformed channel number (name=" + channel.getDisplayName()
                        + ", number=" + channel.getDisplayNumber() + ")");
                continue;
            }
            if (matchChannelNumber(mTypedChannelNumber, chNumber)) {
                mChannelCandidates.add(channel);
            } else if (!mTypedChannelNumber.hasDelimiter) {
                // Even if a user doesn't type '-', we need to match the typed number to not only
                // the major number but also the minor number. For example, when a user types '111'
                // without delimiter, it should be matched to '111', '1-11' and '11-1'.
                if (channel.getDisplayNumber().replaceAll(CHANNEL_DELIMITERS_REGEX, "")
                        .startsWith(mTypedChannelNumber.majorNumber)) {
                    secondaryChannelCandidates.add(channel);
                }
            }
        }
        mChannelCandidates.addAll(secondaryChannelCandidates);
        mAdapter.notifyDataSetChanged();
        if (mAdapter.getCount() > 0) {
            mChannelItemListView.requestFocus();
            mChannelItemListView.setSelection(0);
            mSelectedChannel = mChannelCandidates.get(0);
        }

        updateViewHeight();
    }

    private void updateViewHeight() {
        int itemListHeight = mItemHeight * Math.min(MAX_CHANNEL_ITEM, mAdapter.getCount());
        int targetHeight = mBaseViewHeight + itemListHeight;
        if (mResizeAnimator != null) {
            mResizeAnimator.cancel();
            mResizeAnimator = null;
        }

        if (mCurrentHeight == 0) {
            // Do not add the resize animation when the banner has not been shown before.
            mCurrentHeight = targetHeight;
            setViewHeight(this, targetHeight);
        } else if (mCurrentHeight != targetHeight){
            mResizeAnimator = createResizeAnimator(targetHeight);
            mResizeAnimator.start();
        }
    }

    private Animator createResizeAnimator(int targetHeight) {
        ValueAnimator animator = ValueAnimator.ofInt(mCurrentHeight, targetHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (Integer) animation.getAnimatedValue();
                setViewHeight(KeypadChannelSwitchView.this, value);
                mCurrentHeight = value;
            }
        });
        animator.setDuration(mResizeAnimDuration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mResizeAnimator = null;
            }
        });
        animator.setInterpolator(mResizeInterpolator);
        return animator;
    }

    private void setViewHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (height != layoutParams.height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
    }

    private static boolean matchChannelNumber(ChannelNumber typedChNumber, ChannelNumber chNumber) {
        if (!chNumber.majorNumber.equals(typedChNumber.majorNumber)) {
            return false;
        }
        if (typedChNumber.hasDelimiter) {
            if (!chNumber.hasDelimiter) {
                return false;
            }
            if (!chNumber.minorNumber.startsWith(typedChNumber.minorNumber)) {
                return false;
            }
        }
        return true;
    }

    class ChannelItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mChannelCandidates.size();
        }

        @Override
        public Object getItem(int position) {
            return mChannelCandidates.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Channel channel = mChannelCandidates.get(position);
            View v = convertView;
            if (v == null) {
                v = mLayoutInflater.inflate(R.layout.keypad_channel_switch_item, parent, false);
            }

            TextView channelNumberView = (TextView) v.findViewById(R.id.number);
            channelNumberView.setText(channel.getDisplayNumber());

            TextView channelNameView = (TextView) v.findViewById(R.id.name);
            channelNameView.setText(channel.getDisplayName());
            return v;
        }
    }
}
