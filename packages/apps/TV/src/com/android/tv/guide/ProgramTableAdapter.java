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

import static com.android.tv.util.ImageLoader.ImageLoaderCallback;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.RecycledViewPool;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.TvCommonUtils;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.data.Program.CriticScore;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.guide.ProgramManager.TableEntriesUpdatedListener;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.ui.HardwareLayerAnimatorListenerAdapter;
import com.android.tv.util.ImageCache;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.ImageLoader.LoadTvInputLogoTask;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the {@link ProgramListAdapter} list to the body of the program guide table.
 */
class ProgramTableAdapter extends RecyclerView.Adapter<ProgramTableAdapter.ProgramRowViewHolder>
        implements ProgramManager.TableEntryChangedListener {
    private static final String TAG = "ProgramTableAdapter";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final TvInputManagerHelper mTvInputManagerHelper;
    private final DvrManager mDvrManager;
    private final DvrDataManager mDvrDataManager;
    private final ProgramManager mProgramManager;
    private final AccessibilityManager mAccessibilityManager;
    private final ProgramGuide mProgramGuide;
    private final Handler mHandler = new Handler();
    private final List<ProgramListAdapter> mProgramListAdapters = new ArrayList<>();
    private final RecycledViewPool mRecycledViewPool;
    // views to be be reused when displaying critic scores
    private final List<LinearLayout> mCriticScoreViews;

    private final int mChannelLogoWidth;
    private final int mChannelLogoHeight;
    private final int mImageWidth;
    private final int mImageHeight;
    private final String mProgramTitleForNoInformation;
    private final String mProgramTitleForBlockedChannel;
    private final int mChannelTextColor;
    private final int mChannelBlockedTextColor;
    private final int mDetailTextColor;
    private final int mDetailGrayedTextColor;
    private final int mAnimationDuration;
    private final int mDetailPadding;
    private final TextAppearanceSpan mEpisodeTitleStyle;
    private final String mProgramRecordableText;
    private final String mRecordingScheduledText;
    private final String mRecordingConflictText;
    private final String mRecordingFailedText;
    private final String mRecordingInProgressText;
    private final int mDvrPaddingStartWithTrack;
    private final int mDvrPaddingStartWithOutTrack;

    ProgramTableAdapter(Context context, ProgramGuide programGuide) {
        mContext = context;
        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mTvInputManagerHelper = TvApplication.getSingletons(context).getTvInputManagerHelper();
        if (CommonFeatures.DVR.isEnabled(context)) {
            mDvrManager = TvApplication.getSingletons(context).getDvrManager();
            mDvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        } else {
            mDvrManager = null;
            mDvrDataManager = null;
        }
        mProgramGuide = programGuide;
        mProgramManager = programGuide.getProgramManager();

        Resources res = context.getResources();
        mChannelLogoWidth = res.getDimensionPixelSize(
                R.dimen.program_guide_table_header_column_channel_logo_width);
        mChannelLogoHeight = res.getDimensionPixelSize(
                R.dimen.program_guide_table_header_column_channel_logo_height);
        mImageWidth = res.getDimensionPixelSize(
                R.dimen.program_guide_table_detail_image_width);
        mImageHeight = res.getDimensionPixelSize(
                R.dimen.program_guide_table_detail_image_height);
        mProgramTitleForNoInformation = res.getString(
                R.string.program_title_for_no_information);
        mProgramTitleForBlockedChannel = res.getString(
                R.string.program_title_for_blocked_channel);
        mChannelTextColor = res.getColor(
                R.color.program_guide_table_header_column_channel_number_text_color, null);
        mChannelBlockedTextColor = res.getColor(
                R.color.program_guide_table_header_column_channel_number_blocked_text_color, null);
        mDetailTextColor = res.getColor(
                R.color.program_guide_table_detail_title_text_color, null);
        mDetailGrayedTextColor = res.getColor(
                R.color.program_guide_table_detail_title_grayed_text_color, null);
        mAnimationDuration =
                res.getInteger(R.integer.program_guide_table_detail_fade_anim_duration);
        mDetailPadding = res.getDimensionPixelOffset(
                R.dimen.program_guide_table_detail_padding);
        mProgramRecordableText = res.getString(R.string.dvr_epg_program_recordable);
        mRecordingScheduledText = res.getString(R.string.dvr_epg_program_recording_scheduled);
        mRecordingConflictText = res.getString(R.string.dvr_epg_program_recording_conflict);
        mRecordingFailedText = res.getString(R.string.dvr_epg_program_recording_failed);
        mRecordingInProgressText = res.getString(R.string.dvr_epg_program_recording_in_progress);
        mDvrPaddingStartWithTrack = res.getDimensionPixelOffset(
                R.dimen.program_guide_table_detail_dvr_margin_start);
        mDvrPaddingStartWithOutTrack = res.getDimensionPixelOffset(
                R.dimen.program_guide_table_detail_dvr_margin_start_without_track);

        int episodeTitleSize = res.getDimensionPixelSize(
                R.dimen.program_guide_table_detail_episode_title_text_size);
        ColorStateList episodeTitleColor = ColorStateList.valueOf(
                res.getColor(R.color.program_guide_table_detail_episode_title_text_color, null));
        mEpisodeTitleStyle = new TextAppearanceSpan(null, 0, episodeTitleSize,
                episodeTitleColor, null);

        mCriticScoreViews = new ArrayList<>();
        mRecycledViewPool = new RecycledViewPool();
        mRecycledViewPool.setMaxRecycledViews(R.layout.program_guide_table_item,
                context.getResources().getInteger(
                        R.integer.max_recycled_view_pool_epg_table_item));
        mProgramManager.addListener(new ProgramManager.ListenerAdapter() {
            @Override
            public void onChannelsUpdated() {
                update();
            }
        });
        update();
        mProgramManager.addTableEntryChangedListener(this);
    }

    private void update() {
        if (DEBUG) Log.d(TAG, "update " + mProgramManager.getChannelCount() + " channels");
        for (TableEntriesUpdatedListener listener : mProgramListAdapters) {
            mProgramManager.removeTableEntriesUpdatedListener(listener);
        }
        mProgramListAdapters.clear();
        for (int i = 0; i < mProgramManager.getChannelCount(); i++) {
            ProgramListAdapter listAdapter = new ProgramListAdapter(mContext.getResources(),
                    mProgramGuide, i);
            mProgramManager.addTableEntriesUpdatedListener(listAdapter);
            mProgramListAdapters.add(listAdapter);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mProgramListAdapters.size();
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.program_guide_table_row;
    }

    @Override
    public void onBindViewHolder(ProgramRowViewHolder holder, int position) {
        holder.onBind(position);
    }

    @Override
    public void onBindViewHolder(ProgramRowViewHolder holder, int position, List<Object> payloads) {
        if (!payloads.isEmpty()) {
            holder.updateDetailView();
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public ProgramRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        ProgramRow programRow = (ProgramRow) itemView.findViewById(R.id.row);
        programRow.setRecycledViewPool(mRecycledViewPool);
        return new ProgramRowViewHolder(itemView);
    }

    @Override
    public void onTableEntryChanged(ProgramManager.TableEntry tableEntry) {
        int channelIndex = mProgramManager.getChannelIndex(tableEntry.channelId);
        int pos = mProgramManager.getProgramIdIndex(tableEntry.channelId, tableEntry.getId());
        if (DEBUG) Log.d(TAG, "update(" + channelIndex + ", " + pos + ")");
        mProgramListAdapters.get(channelIndex).notifyItemChanged(pos, tableEntry);
        notifyItemChanged(channelIndex, true);
    }

    class ProgramRowViewHolder extends RecyclerView.ViewHolder
            implements ProgramRow.ChildFocusListener {

        private final ViewGroup mContainer;
        private final ProgramRow mProgramRow;
        private ProgramManager.TableEntry mSelectedEntry;
        private Animator mDetailOutAnimator;
        private Animator mDetailInAnimator;
        private final Runnable mDetailInStarter = new Runnable() {
            @Override
            public void run() {
                mProgramRow.removeOnScrollListener(mOnScrollListener);
                if (mDetailInAnimator != null) {
                    mDetailInAnimator.start();
                }
            }
        };
        private final Runnable mUpdateDetailViewRunnable = new Runnable() {
            @Override
            public void run() {
                updateDetailView();
            }
        };

        private final RecyclerView.OnScrollListener mOnScrollListener =
                new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                onHorizontalScrolled();
            }
        };

        private final ViewTreeObserver.OnGlobalFocusChangeListener mGlobalFocusChangeListener =
                new ViewTreeObserver.OnGlobalFocusChangeListener() {
                    @Override
                    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                        onChildFocus(
                                GuideUtils.isDescendant(mContainer, oldFocus) ? oldFocus : null,
                                GuideUtils.isDescendant(mContainer, newFocus) ? newFocus : null);
                    }
                };

        // Members of Program Details
        private final ViewGroup mDetailView;
        private final ImageView mImageView;
        private final ImageView mBlockView;
        private final TextView mTitleView;
        private final TextView mTimeView;
        private final LinearLayout mCriticScoresLayout;
        private final TextView mDescriptionView;
        private final TextView mAspectRatioView;
        private final TextView mResolutionView;
        private final ImageView mDvrIconView;
        private final TextView mDvrTextIconView;
        private final TextView mDvrStatusView;
        private final ViewGroup mDvrIndicator;

        // Members of Channel Header
        private Channel mChannel;
        private final View mChannelHeaderView;
        private final TextView mChannelNumberView;
        private final TextView mChannelNameView;
        private final ImageView mChannelLogoView;
        private final ImageView mChannelBlockView;
        private final ImageView mInputLogoView;

        private boolean mIsInputLogoVisible;
        private AccessibilityStateChangeListener mAccessibilityStateChangeListener =
                new AccessibilityManager.AccessibilityStateChangeListener() {
                    @Override
                    public void onAccessibilityStateChanged(boolean enable) {
                        enable &= !TvCommonUtils.isRunningInTest();
                        mDetailView.setFocusable(enable);
                        mChannelHeaderView.setFocusable(enable);
                    }
                };

        ProgramRowViewHolder(View itemView) {
            super(itemView);

            mContainer = (ViewGroup) itemView;
            mContainer.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            mContainer.getViewTreeObserver()
                                    .addOnGlobalFocusChangeListener(mGlobalFocusChangeListener);
                            mAccessibilityManager.addAccessibilityStateChangeListener(
                                    mAccessibilityStateChangeListener);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            mContainer.getViewTreeObserver()
                                    .removeOnGlobalFocusChangeListener(mGlobalFocusChangeListener);
                            mAccessibilityManager.removeAccessibilityStateChangeListener(
                                    mAccessibilityStateChangeListener);
                        }
                    });
            mProgramRow = (ProgramRow) mContainer.findViewById(R.id.row);

            mDetailView = (ViewGroup) mContainer.findViewById(R.id.detail);
            mImageView = (ImageView) mDetailView.findViewById(R.id.image);
            mBlockView = (ImageView) mDetailView.findViewById(R.id.block);
            mTitleView = (TextView) mDetailView.findViewById(R.id.title);
            mTimeView = (TextView) mDetailView.findViewById(R.id.time);
            mDescriptionView = (TextView) mDetailView.findViewById(R.id.desc);
            mAspectRatioView = (TextView) mDetailView.findViewById(R.id.aspect_ratio);
            mResolutionView = (TextView) mDetailView.findViewById(R.id.resolution);
            mDvrIconView = (ImageView) mDetailView.findViewById(R.id.dvr_icon);
            mDvrTextIconView = (TextView) mDetailView.findViewById(R.id.dvr_text_icon);
            mDvrStatusView = (TextView) mDetailView.findViewById(R.id.dvr_status);
            mDvrIndicator = (ViewGroup) mContainer.findViewById(R.id.dvr_indicator);
            mCriticScoresLayout = (LinearLayout) mDetailView.findViewById(R.id.critic_scores);

            mChannelHeaderView = mContainer.findViewById(R.id.header_column);
            mChannelNumberView = (TextView) mContainer.findViewById(R.id.channel_number);
            mChannelNameView = (TextView) mContainer.findViewById(R.id.channel_name);
            mChannelLogoView = (ImageView) mContainer.findViewById(R.id.channel_logo);
            mChannelBlockView = (ImageView) mContainer.findViewById(R.id.channel_block);
            mInputLogoView = (ImageView) mContainer.findViewById(R.id.input_logo);

            boolean accessibilityEnabled = mAccessibilityManager.isEnabled()
                    && !TvCommonUtils.isRunningInTest();
            mDetailView.setFocusable(accessibilityEnabled);
            mChannelHeaderView.setFocusable(accessibilityEnabled);
        }

        public void onBind(int position) {
            onBindChannel(mProgramManager.getChannel(position));

            mProgramRow.swapAdapter(mProgramListAdapters.get(position), true);
            mProgramRow.setProgramGuide(mProgramGuide);
            mProgramRow.setChannel(mProgramManager.getChannel(position));
            mProgramRow.setChildFocusListener(this);
            mProgramRow.resetScroll(mProgramGuide.getTimelineRowScrollOffset());

            mDetailView.setVisibility(View.GONE);

            // The bottom-left of the last channel header view will have a rounded corner.
            mChannelHeaderView.setBackgroundResource((position < mProgramListAdapters.size() - 1)
                    ? R.drawable.program_guide_table_header_column_item_background
                    : R.drawable.program_guide_table_header_column_last_item_background);
        }

        private void onBindChannel(Channel channel) {
            if (DEBUG) Log.d(TAG, "onBindChannel " + channel);

            mChannel = channel;
            mInputLogoView.setVisibility(View.GONE);
            mIsInputLogoVisible = false;
            if (channel == null) {
                mChannelNumberView.setVisibility(View.GONE);
                mChannelNameView.setVisibility(View.GONE);
                mChannelLogoView.setVisibility(View.GONE);
                mChannelBlockView.setVisibility(View.GONE);
                return;
            }

            String displayNumber = channel.getDisplayNumber();
            if (displayNumber == null) {
                mChannelNumberView.setVisibility(View.GONE);
            } else {
                int size;
                if (displayNumber.length() <= 4) {
                    size = R.dimen.program_guide_table_header_column_channel_number_large_font_size;
                } else {
                    size = R.dimen.program_guide_table_header_column_channel_number_small_font_size;
                }
                mChannelNumberView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        mChannelNumberView.getContext().getResources().getDimension(size));
                mChannelNumberView.setText(displayNumber);
                mChannelNumberView.setVisibility(View.VISIBLE);
            }
            mChannelNumberView.setTextColor(
                    isChannelLocked(channel) ? mChannelBlockedTextColor : mChannelTextColor);

            mChannelLogoView.setImageBitmap(null);
            mChannelLogoView.setVisibility(View.GONE);
            if (isChannelLocked(channel)) {
                mChannelNameView.setVisibility(View.GONE);
                mChannelBlockView.setVisibility(View.VISIBLE);
            } else {
                mChannelNameView.setText(channel.getDisplayName());
                mChannelNameView.setVisibility(View.VISIBLE);
                mChannelBlockView.setVisibility(View.GONE);

                mChannel.loadBitmap(itemView.getContext(), Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO,
                        mChannelLogoWidth, mChannelLogoHeight,
                        createChannelLogoLoadedCallback(this, channel.getId()));
            }
        }

        @Override
        public void onChildFocus(View oldFocus, View newFocus) {
            if (newFocus == null) {
                return;
            }            // When the accessibility service is enabled, focus might be put on channel's header or
            // detail view, besides program items.
            if (newFocus == mChannelHeaderView) {
                mSelectedEntry = ((ProgramItemView) mProgramRow.getChildAt(0)).getTableEntry();
            } else if (newFocus == mDetailView) {
                return;
            } else {
                mSelectedEntry = ((ProgramItemView) newFocus).getTableEntry();
            }
            if (oldFocus == null) {
                // Focus moved from other row.
                if (mProgramGuide.getProgramGrid().isInLayout()) {
                    // We need to post runnable to avoid updating detail view when
                    // the recycler view is in layout, which may cause detail view not
                    // laid out according to the updated contents.
                    mHandler.post(mUpdateDetailViewRunnable);
                } else {
                    updateDetailView();
                }
                return;
            }

            if (Program.isValid(mSelectedEntry.program)) {
                Program program = mSelectedEntry.program;
                if (getProgramBlock(program) == null) {
                    program.prefetchPosterArt(itemView.getContext(), mImageWidth, mImageHeight);
                }
            }

            // -1 means the selection goes rightwards and 1 goes leftwards
            int direction = oldFocus.getLeft() < newFocus.getLeft() ? -1 : 1;
            View detailContentView = mDetailView.findViewById(R.id.detail_content);

            if (mDetailInAnimator == null) {
                mDetailOutAnimator = ObjectAnimator.ofPropertyValuesHolder(detailContentView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X,
                                0f, direction * mDetailPadding));
                mDetailOutAnimator.setDuration(mAnimationDuration);
                mDetailOutAnimator.addListener(
                        new HardwareLayerAnimatorListenerAdapter(detailContentView) {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                super.onAnimationEnd(animator);
                                mDetailOutAnimator = null;
                                mHandler.removeCallbacks(mDetailInStarter);
                                mHandler.postDelayed(mDetailInStarter, mAnimationDuration);
                            }
                        });

                mProgramRow.addOnScrollListener(mOnScrollListener);
                mDetailOutAnimator.start();
            } else {
                if (mDetailInAnimator.isStarted()) {
                    mDetailInAnimator.cancel();
                    detailContentView.setAlpha(0);
                }

                mHandler.removeCallbacks(mDetailInStarter);
                mHandler.postDelayed(mDetailInStarter, mAnimationDuration);
            }

            mDetailInAnimator = ObjectAnimator.ofPropertyValuesHolder(detailContentView,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X,
                            direction * -mDetailPadding, 0f));
            mDetailInAnimator.setDuration(mAnimationDuration);
            mDetailInAnimator.addListener(
                    new HardwareLayerAnimatorListenerAdapter(detailContentView) {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            super.onAnimationStart(animator);
                            updateDetailView();
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            super.onAnimationEnd(animator);
                            mDetailInAnimator = null;
                        }
                    });
        }

        private void updateDetailView() {
            if (mSelectedEntry == null) {
                // The view holder is never on focus before.
                return;
            }
            if (DEBUG) Log.d(TAG, "updateDetailView");
            mCriticScoresLayout.removeAllViews();
            if (Program.isValid(mSelectedEntry.program)) {
                mTitleView.setTextColor(mDetailTextColor);
                Context context = itemView.getContext();
                Program program = mSelectedEntry.program;

                TvContentRating blockedRating = getProgramBlock(program);

                updatePosterArt(null);
                if (blockedRating == null) {
                    program.loadPosterArt(context, mImageWidth, mImageHeight,
                            createProgramPosterArtCallback(this, program));
                }

                String episodeTitle = program.getEpisodeDisplayTitle(mContext);
                if (TextUtils.isEmpty(episodeTitle)) {
                    mTitleView.setText(program.getTitle());
                } else {
                    String title = program.getTitle();
                    String fullTitle = title + "  " + episodeTitle;

                    SpannableString text = new SpannableString(fullTitle);
                    text.setSpan(mEpisodeTitleStyle,
                            fullTitle.length() - episodeTitle.length(), fullTitle.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mTitleView.setText(text);
                }

                updateTextView(mTimeView, Utils.getDurationString(context,
                        program.getStartTimeUtcMillis(),
                        program.getEndTimeUtcMillis(), false));

                boolean trackMetaDataVisible = updateTextView(mAspectRatioView, Utils
                        .getAspectRatioString(program.getVideoWidth(), program.getVideoHeight()));

                int videoDefinitionLevel = Utils.getVideoDefinitionLevelFromSize(
                        program.getVideoWidth(), program.getVideoHeight());
                trackMetaDataVisible |=
                        updateTextView(mResolutionView, Utils.getVideoDefinitionLevelString(
                        context, videoDefinitionLevel));

                if (mDvrManager != null && mDvrManager.isProgramRecordable(program)) {
                    ScheduledRecording scheduledRecording =
                            mDvrDataManager.getScheduledRecordingForProgramId(program.getId());
                    String statusText = mProgramRecordableText;
                    int iconResId = 0;
                    if (scheduledRecording != null) {
                        if (mDvrManager.isConflicting(scheduledRecording)) {
                            iconResId = R.drawable.ic_warning_white_12dp;
                            statusText = mRecordingConflictText;
                        } else {
                            switch (scheduledRecording.getState()) {
                                case ScheduledRecording.STATE_RECORDING_IN_PROGRESS:
                                    iconResId = R.drawable.ic_recording_program;
                                    statusText = mRecordingInProgressText;
                                    break;
                                case ScheduledRecording.STATE_RECORDING_NOT_STARTED:
                                    iconResId = R.drawable.ic_scheduled_white;
                                    statusText = mRecordingScheduledText;
                                    break;
                                case ScheduledRecording.STATE_RECORDING_FAILED:
                                    iconResId = R.drawable.ic_warning_white_12dp;
                                    statusText = mRecordingFailedText;
                                    break;
                                default:
                                    iconResId = 0;
                            }
                        }
                    }
                    if (iconResId == 0) {
                        mDvrIconView.setVisibility(View.GONE);
                        mDvrTextIconView.setVisibility(View.VISIBLE);
                    } else {
                        mDvrTextIconView.setVisibility(View.GONE);
                        mDvrIconView.setImageResource(iconResId);
                        mDvrIconView.setVisibility(View.VISIBLE);
                    }
                    if (!trackMetaDataVisible) {
                        mDvrIndicator.setPaddingRelative(mDvrPaddingStartWithOutTrack, 0, 0, 0);
                    } else {
                        mDvrIndicator.setPaddingRelative(mDvrPaddingStartWithTrack, 0, 0, 0);
                    }
                    mDvrIndicator.setVisibility(View.VISIBLE);
                    mDvrStatusView.setText(statusText);
                } else {
                    mDvrIndicator.setVisibility(View.GONE);
                }


                if (blockedRating == null) {
                    mBlockView.setVisibility(View.GONE);
                    updateTextView(mDescriptionView, program.getDescription());
                } else {
                    mBlockView.setVisibility(View.VISIBLE);
                    updateTextView(mDescriptionView, getBlockedDescription(blockedRating));
                }
            } else {
                mTitleView.setTextColor(mDetailGrayedTextColor);
                if (mSelectedEntry.isBlocked()) {
                    updateTextView(mTitleView, mProgramTitleForBlockedChannel);
                } else {
                    updateTextView(mTitleView, mProgramTitleForNoInformation);
                }
                mImageView.setVisibility(View.GONE);
                mBlockView.setVisibility(View.GONE);
                mTimeView.setVisibility(View.GONE);
                mDvrIndicator.setVisibility(View.GONE);
                mDescriptionView.setVisibility(View.GONE);
                mAspectRatioView.setVisibility(View.GONE);
                mResolutionView.setVisibility(View.GONE);
            }
        }

        private TvContentRating getProgramBlock(Program program) {
            ParentalControlSettings parental = mTvInputManagerHelper.getParentalControlSettings();
            if (!parental.isParentalControlsEnabled()) {
                return null;
            }
            return parental.getBlockedRating(program.getContentRatings());
        }

        private boolean isChannelLocked(Channel channel) {
            return mTvInputManagerHelper.getParentalControlSettings().isParentalControlsEnabled()
                    && channel.isLocked();
        }

        private String getBlockedDescription(TvContentRating blockedRating) {
            String name = mTvInputManagerHelper.getContentRatingsManager()
                    .getDisplayNameForRating(blockedRating);
            if (TextUtils.isEmpty(name)) {
                return mContext.getString(R.string.program_guide_content_locked);
            } else {
                return TvContentRating.UNRATED.equals(blockedRating)
                        ? mContext.getString(R.string.program_guide_content_locked_unrated)
                        : mContext.getString(R.string.program_guide_content_locked_format, name);
            }
        }

        /**
         * Update tv input logo. It should be called when the visible child item in ProgramGrid
         * changed.
         */
        void updateInputLogo(int lastPosition, boolean forceShow) {
            if (mChannel == null) {
                mInputLogoView.setVisibility(View.GONE);
                mIsInputLogoVisible = false;
                return;
            }

            boolean showLogo = forceShow;
            if (!showLogo) {
                Channel lastChannel = mProgramManager.getChannel(lastPosition);
                if (lastChannel == null
                        || !mChannel.getInputId().equals(lastChannel.getInputId())) {
                    showLogo = true;
                }
            }

            if (showLogo) {
                if (!mIsInputLogoVisible) {
                    mIsInputLogoVisible = true;
                    TvInputInfo info = mTvInputManagerHelper.getTvInputInfo(mChannel.getInputId());
                    if (info != null) {
                        LoadTvInputLogoTask task = new LoadTvInputLogoTask(
                                itemView.getContext(), ImageCache.getInstance(), info);
                        ImageLoader.loadBitmap(createTvInputLogoLoadedCallback(info, this), task);
                    }
                }
            } else {
                mInputLogoView.setVisibility(View.GONE);
                mInputLogoView.setImageDrawable(null);
                mIsInputLogoVisible = false;
            }
        }

        // The return value of this method will indicate the target view is visible (true)
        // or gone (false).
        private boolean updateTextView(TextView textView, String text) {
            if (!TextUtils.isEmpty(text)) {
                textView.setVisibility(View.VISIBLE);
                textView.setText(text);
                return true;
            } else {
                textView.setVisibility(View.GONE);
                return false;
            }
        }

        private void updatePosterArt(@Nullable Bitmap posterArt) {
            mImageView.setImageBitmap(posterArt);
            mImageView.setVisibility(posterArt == null ? View.GONE : View.VISIBLE);
        }

        private void updateChannelLogo(@Nullable Bitmap logo) {
            mChannelLogoView.setImageBitmap(logo);
            mChannelNameView.setVisibility(View.GONE);
            mChannelLogoView.setVisibility(View.VISIBLE);
        }

        private void updateInputLogoInternal(@NonNull Bitmap tvInputLogo) {
            if (!mIsInputLogoVisible) {
                return;
            }
            mInputLogoView.setImageBitmap(tvInputLogo);
            mInputLogoView.setVisibility(View.VISIBLE);
        }

        private void updateCriticScoreView(ProgramRowViewHolder holder, final long programId,
                CriticScore criticScore, View view) {
            TextView criticScoreSource = (TextView) view.findViewById(R.id.critic_score_source);
            TextView criticScoreText = (TextView) view.findViewById(R.id.critic_score_score);
            ImageView criticScoreLogo = (ImageView) view.findViewById(R.id.critic_score_logo);

            //set the appropriate information in the views
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                criticScoreSource.setText(Html.fromHtml(criticScore.source,
                        Html.FROM_HTML_MODE_LEGACY));
            } else {
                criticScoreSource.setText(Html.fromHtml(criticScore.source));
            }
            criticScoreText.setText(criticScore.score);
            criticScoreSource.setVisibility(View.VISIBLE);
            criticScoreText.setVisibility(View.VISIBLE);
            ImageLoader.loadBitmap(mContext, criticScore.logoUrl,
                    createCriticScoreLogoCallback(holder, programId, criticScoreLogo));
        }

        private void onHorizontalScrolled() {
            if (mDetailInAnimator != null) {
                mHandler.removeCallbacks(mDetailInStarter);
                mHandler.postDelayed(mDetailInStarter, mAnimationDuration);
            }
        }
    }

    private static ImageLoaderCallback<ProgramRowViewHolder> createCriticScoreLogoCallback(
            ProgramRowViewHolder holder, final long programId, ImageView logoView) {
        return new ImageLoaderCallback<ProgramRowViewHolder>(holder) {
            @Override
            public void onBitmapLoaded(ProgramRowViewHolder holder, @Nullable Bitmap logoImage) {
                if (logoImage == null || holder.mSelectedEntry == null
                        || holder.mSelectedEntry.program == null
                        || holder.mSelectedEntry.program.getId() != programId) {
                    logoView.setVisibility(View.GONE);
                } else {
                    logoView.setImageBitmap(logoImage);
                    logoView.setVisibility(View.VISIBLE);
                }
            }
        };
    }

    private static ImageLoaderCallback<ProgramRowViewHolder> createProgramPosterArtCallback(
            ProgramRowViewHolder holder, final Program program) {
        return new ImageLoaderCallback<ProgramRowViewHolder>(holder) {
            @Override
            public void onBitmapLoaded(ProgramRowViewHolder holder, @Nullable Bitmap posterArt) {
                if (posterArt == null || holder.mSelectedEntry == null
                        || holder.mSelectedEntry.program == null) {
                    return;
                }
                String posterArtUri = holder.mSelectedEntry.program.getPosterArtUri();
                if (posterArtUri == null || !posterArtUri.equals(program.getPosterArtUri())) {
                    return;
                }
                holder.updatePosterArt(posterArt);
            }
        };
    }

    private static ImageLoaderCallback<ProgramRowViewHolder> createChannelLogoLoadedCallback(
            ProgramRowViewHolder holder, final long channelId) {
        return new ImageLoaderCallback<ProgramRowViewHolder>(holder) {
            @Override
            public void onBitmapLoaded(ProgramRowViewHolder holder, @Nullable Bitmap logo) {
                if (logo == null || holder.mChannel == null
                        || holder.mChannel.getId() != channelId) {
                    return;
                }
                holder.updateChannelLogo(logo);
            }
        };
    }

    private static ImageLoaderCallback<ProgramRowViewHolder> createTvInputLogoLoadedCallback(
            final TvInputInfo info, ProgramRowViewHolder holder) {
        return new ImageLoaderCallback<ProgramRowViewHolder>(holder) {
            @Override
            public void onBitmapLoaded(ProgramRowViewHolder holder, @Nullable Bitmap logo) {
                if (logo != null && holder.mChannel != null && info.getId()
                        .equals(holder.mChannel.getInputId())) {
                    holder.updateInputLogoInternal(logo);
                }
            }
        };
    }
}
