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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.PlaybackState;
import android.media.tv.TvTrackInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.leanback.media.PlaybackControlGlue;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRow.ClosedCaptioningAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.MultiAction;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import com.android.tv.R;
import com.android.tv.util.TimeShiftUtils;
import java.util.ArrayList;

/**
 * A helper class to assist {@link DvrPlaybackOverlayFragment} to manage its controls row and
 * send command to the media controller. It also helps to update playback states displayed in the
 * fragment according to information the media session provides.
 */
class DvrPlaybackControlHelper extends PlaybackControlGlue {
    private static final String TAG = "DvrPlaybackControlHelpr";
    private static final boolean DEBUG = false;

    private static final int AUDIO_ACTION_ID = 1001;

    private int mPlaybackState = PlaybackState.STATE_NONE;
    private int mPlaybackSpeedLevel;
    private int mPlaybackSpeedId;
    private boolean mReadyToControl;

    private final DvrPlaybackOverlayFragment mFragment;
    private final MediaController mMediaController;
    private final MediaController.Callback mMediaControllerCallback = new MediaControllerCallback();
    private final TransportControls mTransportControls;
    private final int mExtraPaddingTopForNoDescription;
    private final MultiAction mClosedCaptioningAction;
    private final MultiAction mMultiAudioAction;
    private ArrayObjectAdapter mSecondaryActionsAdapter;

    DvrPlaybackControlHelper(Activity activity, DvrPlaybackOverlayFragment overlayFragment) {
        super(activity, new int[TimeShiftUtils.MAX_SPEED_LEVEL + 1]);
        mFragment = overlayFragment;
        mMediaController = activity.getMediaController();
        mMediaController.registerCallback(mMediaControllerCallback);
        mTransportControls = mMediaController.getTransportControls();
        mExtraPaddingTopForNoDescription = activity.getResources()
                .getDimensionPixelOffset(R.dimen.dvr_playback_controls_extra_padding_top);
        mClosedCaptioningAction = new ClosedCaptioningAction(activity);
        mMultiAudioAction = new MultiAudioAction(activity);
        createControlsRowPresenter();
    }

    void createControlsRow() {
        PlaybackControlsRow controlsRow = new PlaybackControlsRow(this);
        setControlsRow(controlsRow);
        mSecondaryActionsAdapter = (ArrayObjectAdapter) controlsRow.getSecondaryActionsAdapter();
    }

    private void createControlsRowPresenter() {
        AbstractDetailsDescriptionPresenter detailsPresenter =
                new AbstractDetailsDescriptionPresenter() {
            @Override
            protected void onBindDescription(
                    AbstractDetailsDescriptionPresenter.ViewHolder viewHolder, Object object) {
                PlaybackControlGlue glue = (PlaybackControlGlue) object;
                if (glue.hasValidMedia()) {
                    viewHolder.getTitle().setText(glue.getMediaTitle());
                    viewHolder.getSubtitle().setText(glue.getMediaSubtitle());
                } else {
                    viewHolder.getTitle().setText("");
                    viewHolder.getSubtitle().setText("");
                }
                if (TextUtils.isEmpty(viewHolder.getSubtitle().getText())) {
                    viewHolder.view.setPadding(viewHolder.view.getPaddingLeft(),
                            mExtraPaddingTopForNoDescription,
                            viewHolder.view.getPaddingRight(), viewHolder.view.getPaddingBottom());
                }
            }
        };
        PlaybackControlsRowPresenter presenter =
                new PlaybackControlsRowPresenter(detailsPresenter) {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(DvrPlaybackControlHelper.this);
            }

            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };
        presenter.setProgressColor(getContext().getResources()
                .getColor(R.color.play_controls_progress_bar_watched));
        presenter.setBackgroundColor(getContext().getResources()
                .getColor(R.color.play_controls_body_background_enabled));
        setControlsRowPresenter(presenter);
    }

    @Override
    public void onActionClicked(Action action) {
        if (mReadyToControl) {
            int trackType;
            if (action.getId() == mClosedCaptioningAction.getId()) {
                trackType = TvTrackInfo.TYPE_SUBTITLE;
            } else if (action.getId() == AUDIO_ACTION_ID) {
                trackType = TvTrackInfo.TYPE_AUDIO;
            } else {
                super.onActionClicked(action);
                return;
            }
            ArrayList<TvTrackInfo> trackInfos = mFragment.getTracks(trackType);
            if (!trackInfos.isEmpty()) {
                showSideFragment(trackInfos, mFragment.getSelectedTrackId(trackType));
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return mReadyToControl && super.onKey(v, keyCode, event);
    }

    @Override
    public boolean hasValidMedia() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        return playbackState != null;
    }

    @Override
    public boolean isMediaPlaying() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        if (playbackState == null) {
            return false;
        }
        int state = playbackState.getState();
        return state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_CONNECTING
                && state != PlaybackState.STATE_PAUSED;
    }

    /**
     * Returns the ID of the media under playback.
     */
    public String getMediaId() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? null
                : mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
    }

    @Override
    public CharSequence getMediaTitle() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? ""
                : mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
    }

    @Override
    public CharSequence getMediaSubtitle() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? ""
                : mediaMetadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
    }

    @Override
    public int getMediaDuration() {
        MediaMetadata mediaMetadata = mMediaController.getMetadata();
        return mediaMetadata == null ? 0
                : (int) mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    @Override
    public Drawable getMediaArt() {
        // Do not show the poster art on control row.
        return null;
    }

    @Override
    public long getSupportedActions() {
        return ACTION_PLAY_PAUSE | ACTION_FAST_FORWARD | ACTION_REWIND;
    }

    @Override
    public int getCurrentSpeedId() {
        return mPlaybackSpeedId;
    }

    @Override
    public int getCurrentPosition() {
        PlaybackState playbackState = mMediaController.getPlaybackState();
        if (playbackState == null) {
            return 0;
        }
        return (int) playbackState.getPosition();
    }

    /**
     * Unregister media controller's callback.
     */
    void unregisterCallback() {
        mMediaController.unregisterCallback(mMediaControllerCallback);
    }

    /**
     * Update the secondary controls row.
     * @param hasClosedCaption {@code true} to show the closed caption selection button,
     *                         {@code false} to hide it.
     * @param hasMultiAudio {@code true} to show the audio track selection button,
     *                      {@code false} to hide it.
     */
    void updateSecondaryRow(boolean hasClosedCaption, boolean hasMultiAudio) {
        if (hasClosedCaption) {
            if (mSecondaryActionsAdapter.indexOf(mClosedCaptioningAction) < 0) {
                mSecondaryActionsAdapter.add(0, mClosedCaptioningAction);
            }
        } else {
            mSecondaryActionsAdapter.remove(mClosedCaptioningAction);
        }
        if (hasMultiAudio) {
            if (mSecondaryActionsAdapter.indexOf(mMultiAudioAction) < 0) {
                mSecondaryActionsAdapter.add(mMultiAudioAction);
            }
        } else {
            mSecondaryActionsAdapter.remove(mMultiAudioAction);
        }
        getHost().notifyPlaybackRowChanged();
    }

    @Nullable
    Boolean hasSecondaryRow() {
        if (mSecondaryActionsAdapter == null) {
            return null;
        }
        return mSecondaryActionsAdapter.size() != 0;
    }

    @Override
    public void play(int speedId) {
        if (getCurrentSpeedId() == speedId) {
            return;
        }
        if (speedId == PLAYBACK_SPEED_NORMAL) {
            mTransportControls.play();
        } else if (speedId <= -PLAYBACK_SPEED_FAST_L0) {
            mTransportControls.rewind();
        } else if (speedId >= PLAYBACK_SPEED_FAST_L0){
            mTransportControls.fastForward();
        }
    }

    @Override
    public void pause() {
        mTransportControls.pause();
    }

    /**
     * Notifies closed caption being enabled/disabled to update related UI.
     */
    void onSubtitleTrackStateChanged(boolean enabled) {
        mClosedCaptioningAction.setIndex(enabled ?
                ClosedCaptioningAction.ON : ClosedCaptioningAction.OFF);
    }

    private void onStateChanged(int state, long positionMs, int speedLevel) {
        if (DEBUG) Log.d(TAG, "onStateChanged");
        getControlsRow().setCurrentTime((int) positionMs);
        if (state == mPlaybackState && mPlaybackSpeedLevel == speedLevel) {
            // Only position is changed, no need to update controls row
            return;
        }
        // NOTICE: The below two variables should only be used in this method.
        // The only usage of them is to confirm if the state is changed or not.
        mPlaybackState = state;
        mPlaybackSpeedLevel = speedLevel;
        switch (state) {
            case PlaybackState.STATE_PLAYING:
                mPlaybackSpeedId = PLAYBACK_SPEED_NORMAL;
                setFadingEnabled(true);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_PAUSED:
                mPlaybackSpeedId = PLAYBACK_SPEED_PAUSED;
                setFadingEnabled(true);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_FAST_FORWARDING:
                mPlaybackSpeedId = PLAYBACK_SPEED_FAST_L0 + speedLevel;
                setFadingEnabled(false);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_REWINDING:
                mPlaybackSpeedId = -PLAYBACK_SPEED_FAST_L0 - speedLevel;
                setFadingEnabled(false);
                mReadyToControl = true;
                break;
            case PlaybackState.STATE_CONNECTING:
                setFadingEnabled(false);
                mReadyToControl = false;
                break;
            case PlaybackState.STATE_NONE:
                mReadyToControl = false;
                break;
            default:
                setFadingEnabled(true);
                break;
        }
        onStateChanged();
    }

    private void showSideFragment(ArrayList<TvTrackInfo> trackInfos, String selectedTrackId) {
        Bundle args = new Bundle();
        args.putParcelableArrayList(DvrPlaybackSideFragment.TRACK_INFOS, trackInfos);
        args.putString(DvrPlaybackSideFragment.SELECTED_TRACK_ID, selectedTrackId);
        DvrPlaybackSideFragment sideFragment = new DvrPlaybackSideFragment();
        sideFragment.setArguments(args);
        mFragment.getFragmentManager().beginTransaction()
                .hide(mFragment)
                .replace(R.id.dvr_playback_side_fragment, sideFragment)
                .addToBackStack(null)
                .commit();
    }

    private class MediaControllerCallback extends MediaController.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (DEBUG) Log.d(TAG, "Playback state changed: " + state.getState());
            onStateChanged(state.getState(), state.getPosition(), (int) state.getPlaybackSpeed());
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            DvrPlaybackControlHelper.this.onMetadataChanged();
        }
    }

    private static class MultiAudioAction extends MultiAction {
        MultiAudioAction(Context context) {
            super(AUDIO_ACTION_ID);
            setDrawables(new Drawable[]{context.getDrawable(R.drawable.ic_tvoption_multi_track)});
        }
    }
}