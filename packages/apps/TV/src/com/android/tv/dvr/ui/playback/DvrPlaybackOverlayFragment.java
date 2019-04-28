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
 * limitations under the License
 */

package com.android.tv.dvr.ui.playback;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.tv.TvContentRating;
import android.media.tv.TvTrackInfo;
import android.os.Bundle;
import android.media.session.PlaybackState;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.support.v17.leanback.app.PlaybackFragment;
import android.support.v17.leanback.app.PlaybackFragmentGlueHost;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.BaseOnItemViewClickedListener;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SinglePresenterSelector;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.BaseProgram;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.SortedArrayAdapter;
import com.android.tv.dvr.ui.browse.DvrListRowPresenter;
import com.android.tv.dvr.ui.browse.RecordingCardView;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.util.TvSettings;
import com.android.tv.util.TvTrackInfoUtils;
import com.android.tv.util.Utils;

import java.util.List;
import java.util.ArrayList;

public class DvrPlaybackOverlayFragment extends PlaybackFragment {
    // TODO: Handles audio focus. Deals with block and ratings.
    private static final String TAG = "DvrPlaybackOverlayFrag";
    private static final boolean DEBUG = false;

    private static final String MEDIA_SESSION_TAG = "com.android.tv.dvr.mediasession";
    private static final float DISPLAY_ASPECT_RATIO_EPSILON = 0.01f;

    // mProgram is only used to store program from intent. Don't use it elsewhere.
    private RecordedProgram mProgram;
    private DvrPlayer mDvrPlayer;
    private DvrPlaybackMediaSessionHelper mMediaSessionHelper;
    private DvrPlaybackControlHelper mPlaybackControlHelper;
    private ArrayObjectAdapter mRowsAdapter;
    private SortedArrayAdapter<BaseProgram> mRelatedRecordingsRowAdapter;
    private DvrPlaybackCardPresenter mRelatedRecordingCardPresenter;
    private DvrDataManager mDvrDataManager;
    private ContentRatingsManager mContentRatingsManager;
    private TvView mTvView;
    private View mBlockScreenView;
    private ListRow mRelatedRecordingsRow;
    private int mVerticalPaddingBase;
    private int mPaddingWithoutRelatedRow;
    private int mPaddingWithoutSecondaryRow;
    private int mWindowWidth;
    private int mWindowHeight;
    private float mAppliedAspectRatio;
    private float mWindowAspectRatio;
    private boolean mPinChecked;
    private boolean mStarted;
    private DvrPlayer.OnTrackSelectedListener mOnSubtitleTrackSelectedListener =
            new DvrPlayer.OnTrackSelectedListener() {
                @Override
                public void onTrackSelected(String selectedTrackId) {
                    mPlaybackControlHelper.onSubtitleTrackStateChanged(selectedTrackId != null);
                    mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mVerticalPaddingBase = getActivity().getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_base);
        mPaddingWithoutRelatedRow = getActivity().getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_no_related_row);
        mPaddingWithoutSecondaryRow = getActivity().getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_no_secondary_row);
        mDvrDataManager = TvApplication.getSingletons(getActivity()).getDvrDataManager();
        mContentRatingsManager = TvApplication.getSingletons(getContext())
                .getTvInputManagerHelper().getContentRatingsManager();
        if (!mDvrDataManager.isRecordedProgramLoadFinished()) {
            mDvrDataManager.addRecordedProgramLoadFinishedListener(
                    new DvrDataManager.OnRecordedProgramLoadFinishedListener() {
                        @Override
                        public void onRecordedProgramLoadFinished() {
                            mDvrDataManager.removeRecordedProgramLoadFinishedListener(this);
                            if (handleIntent(getActivity().getIntent(), true)) {
                                setUpRows();
                                preparePlayback(getActivity().getIntent());
                            }
                        }
                    }
            );
        } else if (!handleIntent(getActivity().getIntent(), true)) {
            return;
        }
        Point size = new Point();
        ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE))
                .getDisplay(Display.DEFAULT_DISPLAY).getSize(size);
        mWindowWidth = size.x;
        mWindowHeight = size.y;
        mWindowAspectRatio = mAppliedAspectRatio = (float) mWindowWidth / mWindowHeight;
        setBackgroundType(PlaybackFragment.BG_LIGHT);
        setFadingEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStarted = true;
        updateVerticalPosition();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mTvView = (TvView) getActivity().findViewById(R.id.dvr_tv_view);
        mBlockScreenView = getActivity().findViewById(R.id.block_screen);
        mDvrPlayer = new DvrPlayer(mTvView);
        mMediaSessionHelper = new DvrPlaybackMediaSessionHelper(
                getActivity(), MEDIA_SESSION_TAG, mDvrPlayer, this);
        mPlaybackControlHelper = new DvrPlaybackControlHelper(getActivity(), this);
        mRelatedRecordingsRow = getRelatedRecordingsRow();
        mDvrPlayer.setOnTracksAvailabilityChangedListener(
                new DvrPlayer.OnTracksAvailabilityChangedListener() {
                    @Override
                    public void onTracksAvailabilityChanged(boolean hasClosedCaption,
                            boolean hasMultiAudio) {
                        mPlaybackControlHelper.updateSecondaryRow(hasClosedCaption, hasMultiAudio);
                        if (hasClosedCaption) {
                            mDvrPlayer.setOnTrackSelectedListener(TvTrackInfo.TYPE_SUBTITLE,
                                    mOnSubtitleTrackSelectedListener);
                            selectBestMatchedTrack(TvTrackInfo.TYPE_SUBTITLE);
                        } else {
                            mDvrPlayer.setOnTrackSelectedListener(TvTrackInfo.TYPE_SUBTITLE, null);
                        }
                        if (hasMultiAudio) {
                            selectBestMatchedTrack(TvTrackInfo.TYPE_AUDIO);
                        }
                        updateVerticalPosition();
                        mPlaybackControlHelper.getHost().notifyPlaybackRowChanged();
                    }
        });
        mDvrPlayer.setOnAspectRatioChangedListener(new DvrPlayer.OnAspectRatioChangedListener() {
            @Override
            public void onAspectRatioChanged(float videoAspectRatio) {
                updateAspectRatio(videoAspectRatio);
            }
        });
        mPinChecked = getActivity().getIntent()
                .getBooleanExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED, false);
        mDvrPlayer.setOnContentBlockedListener(
                new DvrPlayer.OnContentBlockedListener() {
                    @Override
                    public void onContentBlocked(TvContentRating contentRating) {
                        if (mPinChecked) {
                            mTvView.unblockContent(contentRating);
                            return;
                        }
                        mBlockScreenView.setVisibility(View.VISIBLE);
                        getActivity().getMediaController().getTransportControls().pause();
                        ((DvrPlaybackActivity) getActivity())
                                .setOnPinCheckListener(
                                        new PinDialogFragment.OnPinCheckedListener() {
                                            @Override
                                            public void onPinChecked(
                                                    boolean checked, int type, String rating) {
                                                ((DvrPlaybackActivity) getActivity())
                                                        .setOnPinCheckListener(null);
                                                if (checked) {
                                                    mPinChecked = true;
                                                    mTvView.unblockContent(contentRating);
                                                    mBlockScreenView.setVisibility(View.GONE);
                                                    getActivity()
                                                            .getMediaController()
                                                            .getTransportControls()
                                                            .play();
                                                }
                                            }
                                        });
                        PinDialogFragment.create(
                                        PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_DVR,
                                        contentRating.flattenToString())
                                .show(
                                        getActivity().getFragmentManager(),
                                        PinDialogFragment.DIALOG_TAG);
                    }
                });
        setOnItemViewClickedListener(new BaseOnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Object row) {
                if (itemViewHolder.view instanceof RecordingCardView) {
                    setFadingEnabled(false);
                    long programId = ((RecordedProgram) itemViewHolder.view.getTag()).getId();
                    if (DEBUG) Log.d(TAG, "Play Related Recording:" + programId);
                    Intent intent = new Intent(getContext(), DvrPlaybackActivity.class);
                    intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, programId);
                    getContext().startActivity(intent);
                }
            }
        });
        if (mProgram != null) {
            setUpRows();
            preparePlayback(getActivity().getIntent());
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        super.onPause();
        if (mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_FAST_FORWARDING
                || mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_REWINDING) {
            getActivity().getMediaController().getTransportControls().pause();
        }
        if (mMediaSessionHelper.getPlaybackState() == PlaybackState.STATE_NONE) {
            getActivity().requestVisibleBehind(false);
        } else {
            getActivity().requestVisibleBehind(true);
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        mPlaybackControlHelper.unregisterCallback();
        mMediaSessionHelper.release();
        mRelatedRecordingCardPresenter.unbindAllViewHolders();
        super.onDestroy();
    }

    /**
     * Passes the intent to the fragment.
     */
    public void onNewIntent(Intent intent) {
        if (mDvrDataManager.isRecordedProgramLoadFinished() && handleIntent(intent, false)) {
            preparePlayback(intent);
        }
    }

    /**
     * Should be called when windows' size is changed in order to notify DVR player
     * to update it's view width/height and position.
     */
    public void onWindowSizeChanged(final int windowWidth, final int windowHeight) {
        mWindowWidth = windowWidth;
        mWindowHeight = windowHeight;
        mWindowAspectRatio = (float) mWindowWidth / mWindowHeight;
        updateAspectRatio(mAppliedAspectRatio);
    }

    /**
     * Returns next recorded episode in the same series as now playing program.
     */
    public RecordedProgram getNextEpisode(RecordedProgram program) {
        int position = mRelatedRecordingsRowAdapter.findInsertPosition(program);
        if (position == mRelatedRecordingsRowAdapter.size()) {
            return null;
        } else {
            return (RecordedProgram) mRelatedRecordingsRowAdapter.get(position);
        }
    }

    /**
     * Returns the tracks of the give type of the current playback.

     * @param trackType Should be {@link TvTrackInfo#TYPE_SUBTITLE}
     *                  or {@link TvTrackInfo#TYPE_AUDIO}. Or returns {@code null}.
     */
    public ArrayList<TvTrackInfo> getTracks(int trackType) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            return mDvrPlayer.getAudioTracks();
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            return mDvrPlayer.getSubtitleTracks();
        }
        return null;
    }

    /**
     * Returns the ID of the selected track of the given type.
     */
    public String getSelectedTrackId(int trackType) {
        return mDvrPlayer.getSelectedTrackId(trackType);
    }

    /**
     * Returns the language setting of the given track type.

     * @param trackType Should be {@link TvTrackInfo#TYPE_SUBTITLE}
     *                  or {@link TvTrackInfo#TYPE_AUDIO}.
     * @return {@code null} if no language has been set for the given track type.
     */
    TvTrackInfo getTrackSetting(int trackType) {
        return TvSettings.getDvrPlaybackTrackSettings(getContext(), trackType);
    }

    /**
     * Selects the given audio or subtitle track for DVR playback.
     * @param trackType Should be {@link TvTrackInfo#TYPE_SUBTITLE}
     *                  or {@link TvTrackInfo#TYPE_AUDIO}.
     * @param selectedTrack {@code null} to disable the audio or subtitle track according to
     *                      trackType.
     */
    void selectTrack(int trackType, TvTrackInfo selectedTrack) {
        if (mDvrPlayer.isPlaybackPrepared()) {
            mDvrPlayer.selectTrack(trackType, selectedTrack);
        }
    }

    private boolean handleIntent(Intent intent, boolean finishActivity) {
        mProgram = getProgramFromIntent(intent);
        if (mProgram == null) {
            Toast.makeText(getActivity(), getString(R.string.dvr_program_not_found),
                    Toast.LENGTH_SHORT).show();
            if (finishActivity) {
                getActivity().finish();
            }
            return false;
        }
        return true;
    }

    private void selectBestMatchedTrack(int trackType) {
        TvTrackInfo selectedTrack = getTrackSetting(trackType);
        if (selectedTrack != null) {
            TvTrackInfo bestMatchedTrack = TvTrackInfoUtils.getBestTrackInfo(getTracks(trackType),
                    selectedTrack.getId(), selectedTrack.getLanguage(),
                    trackType == TvTrackInfo.TYPE_AUDIO ? selectedTrack.getAudioChannelCount() : 0);
            if (bestMatchedTrack != null && (trackType == TvTrackInfo.TYPE_AUDIO || Utils
                    .isEqualLanguage(bestMatchedTrack.getLanguage(),
                            selectedTrack.getLanguage()))) {
                selectTrack(trackType, bestMatchedTrack);
                return;
            }
        }
        if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            // Disables closed captioning if there's no matched language.
            selectTrack(TvTrackInfo.TYPE_SUBTITLE, null);
        }
    }

    private void updateAspectRatio(float videoAspectRatio) {
        if (videoAspectRatio <= 0) {
            // We don't have video's width or height information, use window's aspect ratio.
            videoAspectRatio = mWindowAspectRatio;
        }
        if (Math.abs(mAppliedAspectRatio - videoAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON) {
            // No need to change
            return;
        }
        if (Math.abs(mWindowAspectRatio - videoAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON) {
            ((ViewGroup) mTvView.getParent()).setPadding(0, 0, 0, 0);
        } else if (videoAspectRatio < mWindowAspectRatio) {
            int newPadding = (mWindowWidth - Math.round(mWindowHeight * videoAspectRatio)) / 2;
            ((ViewGroup) mTvView.getParent()).setPadding(newPadding, 0, newPadding, 0);
        } else {
            int newPadding = (mWindowHeight - Math.round(mWindowWidth / videoAspectRatio)) / 2;
            ((ViewGroup) mTvView.getParent()).setPadding(0, newPadding, 0, newPadding);
        }
        mAppliedAspectRatio = videoAspectRatio;
    }

    private void preparePlayback(Intent intent) {
        mMediaSessionHelper.setupPlayback(mProgram, getSeekTimeFromIntent(intent));
        mPlaybackControlHelper.updateSecondaryRow(false, false);
        getActivity().getMediaController().getTransportControls().prepare();
        updateRelatedRecordingsRow();
    }

    private void updateRelatedRecordingsRow() {
        boolean wasEmpty = (mRelatedRecordingsRowAdapter.size() == 0);
        mRelatedRecordingsRowAdapter.clear();
        long programId = mProgram.getId();
        String seriesId = mProgram.getSeriesId();
        SeriesRecording seriesRecording = mDvrDataManager.getSeriesRecording(seriesId);
        if (seriesRecording != null) {
            if (DEBUG) Log.d(TAG, "Update related recordings with:" + seriesId);
            List<RecordedProgram> relatedPrograms =
                    mDvrDataManager.getRecordedPrograms(seriesRecording.getId());
            for (RecordedProgram program : relatedPrograms) {
                if (programId != program.getId()) {
                    mRelatedRecordingsRowAdapter.add(program);
                }
            }
        }
        if (mRelatedRecordingsRowAdapter.size() == 0) {
            mRowsAdapter.remove(mRelatedRecordingsRow);
        } else if (wasEmpty){
            mRowsAdapter.add(mRelatedRecordingsRow);
        }
        updateVerticalPosition();
        mRowsAdapter.notifyArrayItemRangeChanged(1, 1);
    }

    private void setUpRows() {
        mPlaybackControlHelper.createControlsRow();
        mPlaybackControlHelper.setHost(new PlaybackFragmentGlueHost(this));
        mRowsAdapter = (ArrayObjectAdapter) getAdapter();
        ClassPresenterSelector selector =
                (ClassPresenterSelector) mRowsAdapter.getPresenterSelector();
        selector.addClassPresenter(ListRow.class, new DvrListRowPresenter(getContext()));
        mRowsAdapter.setPresenterSelector(selector);
        if (mStarted) {
            // If it's started before setting up rows, vertical position has not been updated and
            // should be updated here.
            updateVerticalPosition();
        }
    }

    private ListRow getRelatedRecordingsRow() {
        mRelatedRecordingCardPresenter = new DvrPlaybackCardPresenter(getActivity());
        mRelatedRecordingsRowAdapter = new RelatedRecordingsAdapter(mRelatedRecordingCardPresenter);
        HeaderItem header = new HeaderItem(0,
                getActivity().getString(R.string.dvr_playback_related_recordings));
        return new ListRow(header, mRelatedRecordingsRowAdapter);
    }

    private RecordedProgram getProgramFromIntent(Intent intent) {
        long programId = intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, -1);
        return mDvrDataManager.getRecordedProgram(programId);
    }

    private long getSeekTimeFromIntent(Intent intent) {
        return intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME,
                TvInputManager.TIME_SHIFT_INVALID_TIME);
    }

    private void updateVerticalPosition() {
        Boolean hasSecondaryRow = mPlaybackControlHelper.hasSecondaryRow();
        if (hasSecondaryRow == null) {
            return;
        }

        int verticalPadding = mVerticalPaddingBase;
        if (mRelatedRecordingsRowAdapter.size() == 0) {
            verticalPadding += mPaddingWithoutRelatedRow;
        }
        if (!hasSecondaryRow) {
            verticalPadding += mPaddingWithoutSecondaryRow;
        }
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.playback_controls_dock);
        View view = fragment == null ? null : fragment.getView();
        if (view != null) {
            view.setTranslationY(verticalPadding);
        }
    }

    private class RelatedRecordingsAdapter extends SortedArrayAdapter<BaseProgram> {
        RelatedRecordingsAdapter(DvrPlaybackCardPresenter presenter) {
            super(new SinglePresenterSelector(presenter), BaseProgram.EPISODE_COMPARATOR);
        }

        @Override
        public long getId(BaseProgram item) {
            return item.getId();
        }
    }
}