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

package com.android.tv.guide;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.support.v17.leanback.widget.SearchOrbView;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;

import com.android.tv.ChannelTuner;
import com.android.tv.Features;
import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.util.DurationTimer;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.GenreItems;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.ui.HardwareLayerAnimatorListenerAdapter;
import com.android.tv.ui.ViewUtils;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The program guide.
 */
public class ProgramGuide implements ProgramGrid.ChildFocusListener {
    private static final String TAG = "ProgramGuide";
    private static final boolean DEBUG = false;

    // Whether we should show the guide partially. The first time the user enters the program guide,
    // we show the grid partially together with the genre side panel on the left. Next time
    // the program guide is entered, we recover the previous state (partial or full).
    private static final String KEY_SHOW_GUIDE_PARTIAL = "show_guide_partial";
    private static final long TIME_INDICATOR_UPDATE_FREQUENCY = TimeUnit.SECONDS.toMillis(1);
    private static final long HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final long HALF_HOUR_IN_MILLIS = HOUR_IN_MILLIS / 2;
    // We keep the duration between mStartTime and the current time larger than this value.
    // We clip out the first program entry in ProgramManager, if it does not have enough width.
    // In order to prevent from clipping out the current program, this value need be larger than
    // or equal to ProgramManager.FIRST_ENTRY_MIN_DURATION.
    private static final long MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME
            = ProgramManager.FIRST_ENTRY_MIN_DURATION;

    private static final int MSG_PROGRAM_TABLE_FADE_IN_ANIM = 1000;

    private static final String SCREEN_NAME = "EPG";

    private final MainActivity mActivity;
    private final ProgramManager mProgramManager;
    private final AccessibilityManager mAccessibilityManager;
    private final ChannelTuner mChannelTuner;
    private final Tracker mTracker;
    private final DurationTimer mVisibleDuration = new DurationTimer();
    private final Runnable mPreShowRunnable;
    private final Runnable mPostHideRunnable;

    private final int mWidthPerHour;
    private final long mViewPortMillis;
    private final int mRowHeight;
    private final int mDetailHeight;
    private final int mSelectionRow;  // Row that is focused
    private final int mTableFadeAnimDuration;
    private final int mAnimationDuration;
    private final int mDetailPadding;
    private final SearchOrbView mSearchOrb;
    private int mCurrentTimeIndicatorWidth;

    private final View mContainer;
    private final View mSidePanel;
    private final VerticalGridView mSidePanelGridView;
    private final View mTable;
    private final TimelineRow mTimelineRow;
    private final ProgramGrid mGrid;
    private final TimeListAdapter mTimeListAdapter;
    private final View mCurrentTimeIndicator;

    private final Animator mShowAnimatorFull;
    private final Animator mShowAnimatorPartial;
    // mHideAnimatorFull and mHideAnimatorPartial are created from the same animation xmls.
    // When we share the one animator for two different animations, the starting value
    // is broken, even though the starting value is not defined in XML.
    private final Animator mHideAnimatorFull;
    private final Animator mHideAnimatorPartial;
    private final Animator mPartialToFullAnimator;
    private final Animator mFullToPartialAnimator;
    private final Animator mProgramTableFadeOutAnimator;
    private final Animator mProgramTableFadeInAnimator;

    // When the program guide is popped up, we keep the previous state of the guide.
    private boolean mShowGuidePartial;
    private final SharedPreferences mSharedPreference;
    private View mSelectedRow;
    private Animator mDetailOutAnimator;
    private Animator mDetailInAnimator;

    private long mStartUtcTime;
    private boolean mTimelineAnimation;
    private int mLastRequestedGenreId = GenreItems.ID_ALL_CHANNELS;
    private boolean mIsDuringResetRowSelection;
    private final Handler mHandler = new ProgramGuideHandler(this);
    private boolean mActive;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    private final long mShowDurationMillis;
    private ViewTreeObserver.OnGlobalLayoutListener mOnLayoutListenerForShow;

    private final ProgramManagerListener mProgramManagerListener = new ProgramManagerListener();

    private final Runnable mUpdateTimeIndicator = new Runnable() {
        @Override
        public void run() {
            positionCurrentTimeIndicator();
            mHandler.postAtTime(this,
                    Utils.ceilTime(SystemClock.uptimeMillis(), TIME_INDICATOR_UPDATE_FREQUENCY));
        }
    };

    public ProgramGuide(MainActivity activity, ChannelTuner channelTuner,
            TvInputManagerHelper tvInputManagerHelper, ChannelDataManager channelDataManager,
            ProgramDataManager programDataManager, @Nullable DvrDataManager dvrDataManager,
            @Nullable DvrScheduleManager dvrScheduleManager, Tracker tracker,
            Runnable preShowRunnable, Runnable postHideRunnable) {
        mActivity = activity;
        mProgramManager = new ProgramManager(tvInputManagerHelper, channelDataManager,
                programDataManager, dvrDataManager, dvrScheduleManager);
        mChannelTuner = channelTuner;
        mTracker = tracker;
        mPreShowRunnable = preShowRunnable;
        mPostHideRunnable = postHideRunnable;

        Resources res = activity.getResources();

        mWidthPerHour = res.getDimensionPixelSize(R.dimen.program_guide_table_width_per_hour);
        GuideUtils.setWidthPerHour(mWidthPerHour);

        Point displaySize = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int gridWidth = displaySize.x
                - res.getDimensionPixelOffset(R.dimen.program_guide_table_margin_start)
                - res.getDimensionPixelSize(R.dimen.program_guide_table_header_column_width);
        mViewPortMillis = (gridWidth * HOUR_IN_MILLIS) / mWidthPerHour;

        mRowHeight = res.getDimensionPixelSize(R.dimen.program_guide_table_item_row_height);
        mDetailHeight = res.getDimensionPixelSize(R.dimen.program_guide_table_detail_height);
        mSelectionRow = res.getInteger(R.integer.program_guide_selection_row);
        mTableFadeAnimDuration =
                res.getInteger(R.integer.program_guide_table_detail_fade_anim_duration);
        mShowDurationMillis = res.getInteger(R.integer.program_guide_show_duration);
        mAnimationDuration =
                res.getInteger(R.integer.program_guide_table_detail_toggle_anim_duration);
        mDetailPadding = res.getDimensionPixelOffset(R.dimen.program_guide_table_detail_padding);

        mContainer = mActivity.findViewById(R.id.program_guide);
        ViewTreeObserver.OnGlobalFocusChangeListener globalFocusChangeListener
                = new GlobalFocusChangeListener();
        mContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusChangeListener);

        GenreListAdapter genreListAdapter = new GenreListAdapter(mActivity, mProgramManager, this);
        mSidePanel = mContainer.findViewById(R.id.program_guide_side_panel);
        mSidePanelGridView = (VerticalGridView) mContainer.findViewById(
                R.id.program_guide_side_panel_grid_view);
        mSidePanelGridView.getRecycledViewPool().setMaxRecycledViews(
                R.layout.program_guide_side_panel_row,
                res.getInteger(R.integer.max_recycled_view_pool_epg_side_panel_row));
        mSidePanelGridView.setAdapter(genreListAdapter);
        mSidePanelGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mSidePanelGridView.setWindowAlignmentOffset(mActivity.getResources()
                .getDimensionPixelOffset(R.dimen.program_guide_side_panel_alignment_y));
        mSidePanelGridView.setWindowAlignmentOffsetPercent(
                VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);

        if (Features.EPG_SEARCH.isEnabled(mActivity)) {
            mSearchOrb = (SearchOrbView) mContainer.findViewById(
                    R.id.program_guide_side_panel_search_orb);
            mSearchOrb.setVisibility(View.VISIBLE);

            mSearchOrb.setOnOrbClickedListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    hide();
                    mActivity.showProgramGuideSearchFragment();
                }
            });
            mSidePanelGridView.setOnChildSelectedListener(
                    new android.support.v17.leanback.widget.OnChildSelectedListener() {
                @Override
                public void onChildSelected(ViewGroup viewGroup, View view, int i, long l) {
                    mSearchOrb.animate().alpha(i == 0 ? 1.0f : 0.0f);
                }
            });
        } else {
            mSearchOrb = null;
        }

        mTable = mContainer.findViewById(R.id.program_guide_table);

        mTimelineRow = (TimelineRow) mTable.findViewById(R.id.time_row);
        mTimeListAdapter = new TimeListAdapter(res);
        mTimelineRow.getRecycledViewPool().setMaxRecycledViews(
                R.layout.program_guide_table_header_row_item,
                res.getInteger(R.integer.max_recycled_view_pool_epg_header_row_item));
        mTimelineRow.setAdapter(mTimeListAdapter);

        ProgramTableAdapter programTableAdapter = new ProgramTableAdapter(mActivity, this);
        programTableAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                // It is usually called when Genre is changed.
                // Reset selection of ProgramGrid
                resetRowSelection();
                updateGuidePosition();
            }
        });

        mGrid = (ProgramGrid) mTable.findViewById(R.id.grid);
        mGrid.initialize(mProgramManager);
        mGrid.getRecycledViewPool().setMaxRecycledViews(
                R.layout.program_guide_table_row,
                res.getInteger(R.integer.max_recycled_view_pool_epg_table_row));
        mGrid.setAdapter(programTableAdapter);

        mGrid.setChildFocusListener(this);
        mGrid.setOnChildSelectedListener(new OnChildSelectedListener() {
            @Override
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                if (mIsDuringResetRowSelection) {
                    // Ignore if it's during the first resetRowSelection, because onChildSelected
                    // will be called again when rows are bound to the program table. if selectRow
                    // is called here, mSelectedRow is set and the second selectRow call doesn't
                    // work as intended.
                    mIsDuringResetRowSelection = false;
                    return;
                }
                selectRow(view);
            }
        });
        mGrid.setFocusScrollStrategy(ProgramGrid.FOCUS_SCROLL_ALIGNED);
        mGrid.setWindowAlignmentOffset(mSelectionRow * mRowHeight);
        mGrid.setWindowAlignmentOffsetPercent(ProgramGrid.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mGrid.setItemAlignmentOffset(0);
        mGrid.setItemAlignmentOffsetPercent(ProgramGrid.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);

        RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                onHorizontalScrolled(dx);
            }
        };
        mTimelineRow.addOnScrollListener(onScrollListener);

        mCurrentTimeIndicator = mTable.findViewById(R.id.current_time_indicator);

        mShowAnimatorFull = createAnimator(
                R.animator.program_guide_side_panel_enter_full,
                0,
                R.animator.program_guide_table_enter_full);

        mShowAnimatorPartial = createAnimator(
                R.animator.program_guide_side_panel_enter_partial,
                0,
                R.animator.program_guide_table_enter_partial);
        mShowAnimatorPartial.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mSidePanelGridView.setVisibility(View.VISIBLE);
                mSidePanelGridView.setAlpha(1.0f);
            }
        });

        mHideAnimatorFull = createAnimator(
                R.animator.program_guide_side_panel_exit,
                0,
                R.animator.program_guide_table_exit);
        mHideAnimatorFull.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContainer.setVisibility(View.GONE);
            }
        });
        mHideAnimatorPartial = createAnimator(
                R.animator.program_guide_side_panel_exit,
                0,
                R.animator.program_guide_table_exit);
        mHideAnimatorPartial.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContainer.setVisibility(View.GONE);
            }
        });

        mPartialToFullAnimator = createAnimator(
                R.animator.program_guide_side_panel_hide,
                R.animator.program_guide_side_panel_grid_fade_out,
                R.animator.program_guide_table_partial_to_full);
        mFullToPartialAnimator = createAnimator(
                R.animator.program_guide_side_panel_reveal,
                R.animator.program_guide_side_panel_grid_fade_in,
                R.animator.program_guide_table_full_to_partial);

        mProgramTableFadeOutAnimator = AnimatorInflater.loadAnimator(mActivity,
                R.animator.program_guide_table_fade_out);
        mProgramTableFadeOutAnimator.setTarget(mTable);
        mProgramTableFadeOutAnimator.addListener(new HardwareLayerAnimatorListenerAdapter(mTable) {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (!isActive()) {
                    return;
                }
                mProgramManager.resetChannelListWithGenre(mLastRequestedGenreId);
                resetTimelineScroll();
                if (!mHandler.hasMessages(MSG_PROGRAM_TABLE_FADE_IN_ANIM)) {
                    mHandler.sendEmptyMessage(MSG_PROGRAM_TABLE_FADE_IN_ANIM);
                }
            }
        });
        mProgramTableFadeInAnimator = AnimatorInflater.loadAnimator(mActivity,
                R.animator.program_guide_table_fade_in);
        mProgramTableFadeInAnimator.setTarget(mTable);
        mProgramTableFadeInAnimator.addListener(new HardwareLayerAnimatorListenerAdapter(mTable));
        mSharedPreference = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mAccessibilityManager =
                (AccessibilityManager) mActivity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mShowGuidePartial = mAccessibilityManager.isEnabled()
                || mSharedPreference.getBoolean(KEY_SHOW_GUIDE_PARTIAL, true);
    }

    @Override
    public void onRequestChildFocus(View oldFocus, View newFocus) {
        if (oldFocus != null && newFocus != null) {
            int selectionRowOffset = mSelectionRow * mRowHeight;
            if (oldFocus.getTop() < newFocus.getTop()) {
                // Selection moves downwards
                // Adjust scroll offset to be at the bottom of the target row and to expand up. This
                // will set the scroll target to be one row height up from its current position.
                mGrid.setWindowAlignmentOffset(selectionRowOffset + mRowHeight + mDetailHeight);
                mGrid.setItemAlignmentOffsetPercent(100);
            } else if (oldFocus.getTop() > newFocus.getTop()) {
                // Selection moves upwards
                // Adjust scroll offset to be at the top of the target row and to expand down. This
                // will set the scroll target to be one row height down from its current position.
                mGrid.setWindowAlignmentOffset(selectionRowOffset);
                mGrid.setItemAlignmentOffsetPercent(0);
            }
        }
    }

    /**
     * Show the program guide.  This reveals the side panel, and the program guide table is shown
     * partially.
     *
     * <p>Note: the animation which starts together with ProgramGuide showing animation needs to
     * be initiated in {@code runnableAfterAnimatorReady}. If the animation starts together
     * with show(), the animation may drop some frames.
     */
    public void show(final Runnable runnableAfterAnimatorReady) {
        if (mContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        mTracker.sendShowEpg();
        mTracker.sendScreenView(SCREEN_NAME);
        if (mPreShowRunnable != null) {
            mPreShowRunnable.run();
        }
        mVisibleDuration.start();

        mProgramManager.programGuideVisibilityChanged(true);
        mStartUtcTime = Utils.floorTime(
                System.currentTimeMillis() - MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME,
                HALF_HOUR_IN_MILLIS);
        mProgramManager.updateInitialTimeRange(mStartUtcTime, mStartUtcTime + mViewPortMillis);
        mProgramManager.addListener(mProgramManagerListener);
        mLastRequestedGenreId = GenreItems.ID_ALL_CHANNELS;
        mTimeListAdapter.update(mStartUtcTime);
        mTimelineRow.resetScroll();

        mContainer.setVisibility(View.VISIBLE);
        mActive = true;
        if (!mShowGuidePartial) {
            mTable.requestFocus();
        }
        positionCurrentTimeIndicator();
        mSidePanelGridView.setSelectedPosition(0);
        if (DEBUG) {
            Log.d(TAG, "show()");
        }
        mOnLayoutListenerForShow = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mTable.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mSidePanelGridView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mTable.buildLayer();
                mSidePanelGridView.buildLayer();
                mOnLayoutListenerForShow = null;
                mTimelineAnimation = true;
                // Make sure that time indicator update starts after animation is finished.
                startCurrentTimeIndicator(TIME_INDICATOR_UPDATE_FREQUENCY);
                if (DEBUG) {
                    mContainer.getViewTreeObserver().addOnDrawListener(
                            new ViewTreeObserver.OnDrawListener() {
                                long time = System.currentTimeMillis();
                                int count = 0;

                                @Override
                                public void onDraw() {
                                    long curtime = System.currentTimeMillis();
                                    Log.d(TAG, "onDraw " + count++ + " " + (curtime - time) + "ms");
                                    time = curtime;
                                    if (count > 10) {
                                        mContainer.getViewTreeObserver().removeOnDrawListener(this);
                                    }
                                }
                            });
                }
                updateGuidePosition();
                runnableAfterAnimatorReady.run();
                if (mShowGuidePartial) {
                    mShowAnimatorPartial.start();
                } else {
                    mShowAnimatorFull.start();
                }
            }
        };
        mContainer.getViewTreeObserver().addOnGlobalLayoutListener(mOnLayoutListenerForShow);
        scheduleHide();
    }

    /**
     * Hide the program guide.
     */
    public void hide() {
        if (!isActive()) {
            return;
        }
        if (mOnLayoutListenerForShow != null) {
            mContainer.getViewTreeObserver().removeOnGlobalLayoutListener(mOnLayoutListenerForShow);
            mOnLayoutListenerForShow = null;
        }
        mTracker.sendHideEpg(mVisibleDuration.reset());
        cancelHide();
        mProgramManager.programGuideVisibilityChanged(false);
        mProgramManager.removeListener(mProgramManagerListener);
        mActive = false;
        if (!mShowGuidePartial) {
            mHideAnimatorFull.start();
        } else {
            mHideAnimatorPartial.start();
        }

        // Clears fade-out/in animation for genre change
        if (mProgramTableFadeOutAnimator.isRunning()) {
            mProgramTableFadeOutAnimator.cancel();
        }
        if (mProgramTableFadeInAnimator.isRunning()) {
            mProgramTableFadeInAnimator.cancel();
        }
        mHandler.removeMessages(MSG_PROGRAM_TABLE_FADE_IN_ANIM);
        mTable.setAlpha(1.0f);

        mTimelineAnimation = false;
        stopCurrentTimeIndicator();
        if (mPostHideRunnable != null) {
            mPostHideRunnable.run();
        }
    }

    /**
     * Schedules hiding the program guide.
     */
    public void scheduleHide() {
        cancelHide();
        mHandler.postDelayed(mHideRunnable, mShowDurationMillis);
    }

    /**
     * Cancels hiding the program guide.
     */
    public void cancelHide() {
        mHandler.removeCallbacks(mHideRunnable);
    }

    /**
     * Process the {@code KEYCODE_BACK} key event.
     */
    public void onBackPressed() {
        hide();
    }

    /**
     * Returns {@code true} if the program guide should process the input events.
     */
    public boolean isActive() {
        return mActive;
    }

    /**
     * Returns {@code true} if the program guide is shown, i.e. showing animation is done and
     * hiding animation is not started yet.
     */
    public boolean isRunningAnimation() {
        return mShowAnimatorPartial.isStarted() || mShowAnimatorFull.isStarted()
                || mHideAnimatorPartial.isStarted() || mHideAnimatorFull.isStarted();
    }

    /** Returns if program table is in full screen mode. **/
    boolean isFull() {
        return !mShowGuidePartial;
    }

    /**
     * Requests change genre to {@code genreId}.
     */
    void requestGenreChange(int genreId) {
        if (mLastRequestedGenreId == genreId) {
            // When Recycler.onLayout() removes its children to recycle,
            // View tries to find next focus candidate immediately
            // so GenreListAdapter can take focus back while it's hiding.
            // Returns early here to prevent re-entrance.
            return;
        }
        mLastRequestedGenreId = genreId;
        if (mProgramTableFadeOutAnimator.isStarted()) {
            // When requestGenreChange is called repeatedly in short time, we keep the fade-out
            // state for mTableFadeAnimDuration from now. Without it, we'll see blinks.
            mHandler.removeMessages(MSG_PROGRAM_TABLE_FADE_IN_ANIM);
            mHandler.sendEmptyMessageDelayed(MSG_PROGRAM_TABLE_FADE_IN_ANIM,
                    mTableFadeAnimDuration);
            return;
        }
        if (mHandler.hasMessages(MSG_PROGRAM_TABLE_FADE_IN_ANIM)) {
            mProgramManager.resetChannelListWithGenre(mLastRequestedGenreId);
            mHandler.removeMessages(MSG_PROGRAM_TABLE_FADE_IN_ANIM);
            mHandler.sendEmptyMessageDelayed(MSG_PROGRAM_TABLE_FADE_IN_ANIM,
                    mTableFadeAnimDuration);
            return;
        }
        if (mProgramTableFadeInAnimator.isStarted()) {
            mProgramTableFadeInAnimator.cancel();
        }

        mProgramTableFadeOutAnimator.start();
    }

    /**
     * Returns the scroll offset of the time line row in pixels.
     */
    int getTimelineRowScrollOffset() {
        return mTimelineRow.getScrollOffset();
    }

    /** Returns the program grid view that hold all component views. */
    ProgramGrid getProgramGrid() {
        return mGrid;
    }

    /**
     * Gets {@link VerticalGridView} for "genre select" side panel.
     */
    VerticalGridView getSidePanel() {
        return mSidePanelGridView;
    }

    /** Returns the program manager the program guide is using to provide program information. */
    ProgramManager getProgramManager() {
        return mProgramManager;
    }

    private void updateGuidePosition() {
        // Align EPG at vertical center, if EPG table height is less than the screen size.
        Resources res = mActivity.getResources();
        int screenHeight = mContainer.getHeight();
        if (screenHeight <= 0) {
            // mContainer is not initialized yet.
            return;
        }
        int startPadding = res.getDimensionPixelOffset(R.dimen.program_guide_table_margin_start);
        int topPadding = res.getDimensionPixelOffset(R.dimen.program_guide_table_margin_top);
        int bottomPadding = res.getDimensionPixelOffset(R.dimen.program_guide_table_margin_bottom);
        int tableHeight = res.getDimensionPixelOffset(R.dimen.program_guide_table_header_row_height)
                + mDetailHeight + mRowHeight * mGrid.getAdapter().getItemCount() + topPadding
                + bottomPadding;
        if (tableHeight > screenHeight) {
            // EPG height is longer that the screen height.
            mTable.setPaddingRelative(startPadding, topPadding, 0, 0);
            LayoutParams layoutParams = mTable.getLayoutParams();
            layoutParams.height = LayoutParams.WRAP_CONTENT;
            mTable.setLayoutParams(layoutParams);
        } else {
            mTable.setPaddingRelative(startPadding, topPadding, 0, bottomPadding);
            LayoutParams layoutParams = mTable.getLayoutParams();
            layoutParams.height = tableHeight;
            mTable.setLayoutParams(layoutParams);
        }
    }

    private Animator createAnimator(int sidePanelAnimResId, int sidePanelGridAnimResId,
            int tableAnimResId) {
        List<Animator> animatorList = new ArrayList<>();

        Animator sidePanelAnimator = AnimatorInflater.loadAnimator(mActivity, sidePanelAnimResId);
        sidePanelAnimator.setTarget(mSidePanel);
        animatorList.add(sidePanelAnimator);

        if (sidePanelGridAnimResId != 0) {
            Animator sidePanelGridAnimator = AnimatorInflater.loadAnimator(mActivity,
                    sidePanelGridAnimResId);
            sidePanelGridAnimator.setTarget(mSidePanelGridView);
            sidePanelGridAnimator.addListener(
                    new HardwareLayerAnimatorListenerAdapter(mSidePanelGridView));
            animatorList.add(sidePanelGridAnimator);
        }
        Animator tableAnimator = AnimatorInflater.loadAnimator(mActivity, tableAnimResId);
        tableAnimator.setTarget(mTable);
        tableAnimator.addListener(new HardwareLayerAnimatorListenerAdapter(mTable));
        animatorList.add(tableAnimator);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animatorList);
        return set;
    }

    private void startFull() {
        if (!mShowGuidePartial || mAccessibilityManager.isEnabled()) {
            // If accessibility service is enabled, focus cannot be moved to side panel due to it's
            // hidden. Therefore, we don't hide side panel when accessibility service is enabled.
            return;
        }
        mShowGuidePartial = false;
        mSharedPreference.edit().putBoolean(KEY_SHOW_GUIDE_PARTIAL, mShowGuidePartial).apply();
        mPartialToFullAnimator.start();
    }

    private void startPartial() {
        if (mShowGuidePartial) {
            return;
        }
        mShowGuidePartial = true;
        mSharedPreference.edit().putBoolean(KEY_SHOW_GUIDE_PARTIAL, mShowGuidePartial).apply();
        mFullToPartialAnimator.start();
    }

    private void startCurrentTimeIndicator(long initialDelay) {
        mHandler.postDelayed(mUpdateTimeIndicator, initialDelay);
    }

    private void stopCurrentTimeIndicator() {
        mHandler.removeCallbacks(mUpdateTimeIndicator);
    }

    private void positionCurrentTimeIndicator() {
        int offset = GuideUtils.convertMillisToPixel(mStartUtcTime, System.currentTimeMillis())
                - mTimelineRow.getScrollOffset();
        if (offset < 0) {
            mCurrentTimeIndicator.setVisibility(View.GONE);
        } else {
            if (mCurrentTimeIndicatorWidth == 0) {
                mCurrentTimeIndicator.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                mCurrentTimeIndicatorWidth = mCurrentTimeIndicator.getMeasuredWidth();
            }
            mCurrentTimeIndicator.setPaddingRelative(
                    offset - mCurrentTimeIndicatorWidth / 2, 0, 0, 0);
            mCurrentTimeIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void resetTimelineScroll() {
        if (mProgramManager.getFromUtcMillis() != mStartUtcTime) {
            boolean timelineAnimation = mTimelineAnimation;
            mTimelineAnimation = false;
            // mProgramManagerListener.onTimeRangeUpdated() will be called by shiftTime().
            mProgramManager.shiftTime(mStartUtcTime - mProgramManager.getFromUtcMillis());
            mTimelineAnimation = timelineAnimation;
        }
    }

    private void onHorizontalScrolled(int dx) {
        if (DEBUG) Log.d(TAG, "onHorizontalScrolled(dx=" + dx + ")");
        positionCurrentTimeIndicator();
        for (int i = 0, n = mGrid.getChildCount(); i < n; ++i) {
            mGrid.getChildAt(i).findViewById(R.id.row).scrollBy(dx, 0);
        }
    }

    private void resetRowSelection() {
        if (mDetailOutAnimator != null) {
            mDetailOutAnimator.end();
        }
        if (mDetailInAnimator != null) {
            mDetailInAnimator.cancel();
        }
        mSelectedRow = null;
        mIsDuringResetRowSelection = true;
        mGrid.setSelectedPosition(
                Math.max(mProgramManager.getChannelIndex(mChannelTuner.getCurrentChannel()),
                        0));
        mGrid.resetFocusState();
        mGrid.onItemSelectionReset();
        mIsDuringResetRowSelection = false;
    }

    private void selectRow(View row) {
        if (row == null || row == mSelectedRow) {
            return;
        }
        if (mSelectedRow == null
                || mGrid.getChildAdapterPosition(mSelectedRow) == RecyclerView.NO_POSITION) {
            if (mSelectedRow != null) {
                View oldDetailView = mSelectedRow.findViewById(R.id.detail);
                oldDetailView.setVisibility(View.GONE);
            }
            View detailView = row.findViewById(R.id.detail);
            detailView.findViewById(R.id.detail_content_full).setAlpha(1);
            detailView.findViewById(R.id.detail_content_full).setTranslationY(0);
            ViewUtils.setLayoutHeight(detailView, mDetailHeight);
            detailView.setVisibility(View.VISIBLE);

            final ProgramRow programRow = (ProgramRow) row.findViewById(R.id.row);
            programRow.post(new Runnable() {
                @Override
                public void run() {
                    programRow.focusCurrentProgram();
                }
            });
        } else {
            animateRowChange(mSelectedRow, row);
        }
        mSelectedRow = row;
    }

    private void animateRowChange(View outRow, View inRow) {
        if (mDetailOutAnimator != null) {
            mDetailOutAnimator.end();
        }
        if (mDetailInAnimator != null) {
            mDetailInAnimator.cancel();
        }

        int operationDirection = mGrid.getLastUpDownDirection();
        int animationPadding = 0;
        if (operationDirection == View.FOCUS_UP) {
            animationPadding = mDetailPadding;
        } else if (operationDirection == View.FOCUS_DOWN) {
            animationPadding = -mDetailPadding;
        }

        View outDetail = outRow != null ? outRow.findViewById(R.id.detail) : null;
        if (outDetail != null && outDetail.isShown()) {
            final View outDetailContent = outDetail.findViewById(R.id.detail_content_full);

            Animator fadeOutAnimator = ObjectAnimator.ofPropertyValuesHolder(outDetailContent,
                    PropertyValuesHolder.ofFloat(View.ALPHA, outDetail.getAlpha(), 0f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                            outDetailContent.getTranslationY(), animationPadding));
            fadeOutAnimator.setStartDelay(0);
            fadeOutAnimator.setDuration(mAnimationDuration);
            fadeOutAnimator.addListener(new HardwareLayerAnimatorListenerAdapter(outDetailContent));

            Animator collapseAnimator = ViewUtils
                    .createHeightAnimator(outDetail, ViewUtils.getLayoutHeight(outDetail), 0);
            collapseAnimator.setStartDelay(mAnimationDuration);
            collapseAnimator.setDuration(mTableFadeAnimDuration);
            collapseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    outDetailContent.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    outDetailContent.setVisibility(View.VISIBLE);
                }
            });

            AnimatorSet outAnimator = new AnimatorSet();
            outAnimator.playTogether(fadeOutAnimator, collapseAnimator);
            outAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    mDetailOutAnimator = null;
                }
            });
            mDetailOutAnimator = outAnimator;
            outAnimator.start();
        }

        View inDetail = inRow != null ? inRow.findViewById(R.id.detail) : null;
        if (inDetail != null) {
            final View inDetailContent = inDetail.findViewById(R.id.detail_content_full);

            Animator expandAnimator = ViewUtils.createHeightAnimator(inDetail, 0, mDetailHeight);
            expandAnimator.setStartDelay(mAnimationDuration);
            expandAnimator.setDuration(mTableFadeAnimDuration);
            expandAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    inDetailContent.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    inDetailContent.setVisibility(View.VISIBLE);
                    inDetailContent.setAlpha(0);
                }
            });
            Animator fadeInAnimator = ObjectAnimator.ofPropertyValuesHolder(inDetailContent,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -animationPadding, 0f));
            fadeInAnimator.setDuration(mAnimationDuration);
            fadeInAnimator.addListener(new HardwareLayerAnimatorListenerAdapter(inDetailContent));

            AnimatorSet inAnimator = new AnimatorSet();
            inAnimator.playSequentially(expandAnimator, fadeInAnimator);
            inAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    mDetailInAnimator = null;
                }
            });
            mDetailInAnimator = inAnimator;
            inAnimator.start();
        }
    }

    private class GlobalFocusChangeListener implements
            ViewTreeObserver.OnGlobalFocusChangeListener {
        private static final int UNKNOWN = 0;
        private static final int SIDE_PANEL = 1;
        private static final int PROGRAM_TABLE = 2;

        @Override
        public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            if (DEBUG) Log.d(TAG, "onGlobalFocusChanged " + oldFocus + " -> " + newFocus);
            if (!isActive()) {
                return;
            }
            int fromLocation = getLocation(oldFocus);
            int toLocation = getLocation(newFocus);
            if (fromLocation == SIDE_PANEL && toLocation == PROGRAM_TABLE) {
                startFull();
            } else if (fromLocation == PROGRAM_TABLE && toLocation == SIDE_PANEL) {
                startPartial();
            }
        }

        private int getLocation(View view) {
            if (view == null) {
                return UNKNOWN;
            }
            for (Object obj = view; obj instanceof View; obj = ((View) obj).getParent()) {
                if (obj == mSidePanel) {
                    return SIDE_PANEL;
                } else if (obj == mGrid) {
                    return PROGRAM_TABLE;
                }
            }
            return UNKNOWN;
        }
    }

    private class ProgramManagerListener extends ProgramManager.ListenerAdapter {
        @Override
        public void onTimeRangeUpdated() {
            int scrollOffset = (int) (mWidthPerHour * mProgramManager.getShiftedTime()
                    / HOUR_IN_MILLIS);
            if (DEBUG) {
                Log.d(TAG, "Horizontal scroll to " + scrollOffset + " pixels ("
                        + mProgramManager.getShiftedTime() + " millis)");
            }
            mTimelineRow.scrollTo(scrollOffset, mTimelineAnimation);
        }
    }

    private static class ProgramGuideHandler extends WeakHandler<ProgramGuide> {
        ProgramGuideHandler(ProgramGuide ref) {
            super(ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull ProgramGuide programGuide) {
            if (msg.what == MSG_PROGRAM_TABLE_FADE_IN_ANIM) {
                programGuide.mProgramTableFadeInAnimator.start();
            }
        }
    }
}
