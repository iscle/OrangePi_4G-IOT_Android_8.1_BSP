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

package com.android.tv.dvr.ui.playback;

import android.media.tv.TvTrackInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;
import android.transition.Transition;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.util.TvSettings;

import java.util.List;
import java.util.Locale;

/**
 * Fragment for DVR playback closed-caption/multi-audio settings.
 */
public class DvrPlaybackSideFragment extends GuidedStepFragment {
    /**
     * The tag for passing track infos to side fragments.
     */
    public static final String TRACK_INFOS = "dvr_key_track_infos";
    /**
     * The tag for passing selected track's ID to side fragments.
     */
    public static final String SELECTED_TRACK_ID = "dvr_key_selected_track_id";

    private static final int ACTION_ID_NO_SUBTITLE = -1;
    private static final int CHECK_SET_ID = 1;

    private List<TvTrackInfo> mTrackInfos;
    private String mSelectedTrackId;
    private TvTrackInfo mSelectedTrack;
    private int mTrackType;
    private DvrPlaybackOverlayFragment mOverlayFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mTrackInfos = getArguments().getParcelableArrayList(TRACK_INFOS);
        mTrackType = mTrackInfos.get(0).getType();
        mSelectedTrackId = getArguments().getString(SELECTED_TRACK_ID);
        mOverlayFragment = ((DvrPlaybackOverlayFragment) getFragmentManager()
                .findFragmentById(R.id.dvr_playback_controls_fragment));
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View backgroundView = super.onCreateBackgroundView(inflater, container, savedInstanceState);
        backgroundView.setBackgroundColor(getResources()
                .getColor(R.color.lb_playback_controls_background_light));
        return backgroundView;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        if (mTrackType == TvTrackInfo.TYPE_SUBTITLE) {
            actions.add(new GuidedAction.Builder(getActivity())
                    .id(ACTION_ID_NO_SUBTITLE)
                    .title(getString(R.string.closed_caption_option_item_off))
                    .checkSetId(CHECK_SET_ID)
                    .checked(mSelectedTrackId == null)
                    .build());
        }
        for (int i = 0; i < mTrackInfos.size(); i++) {
            TvTrackInfo info = mTrackInfos.get(i);
            boolean checked = TextUtils.equals(info.getId(), mSelectedTrackId);
            GuidedAction action = new GuidedAction.Builder(getActivity())
                    .id(i)
                    .title(getTrackLabel(info, i))
                    .checkSetId(CHECK_SET_ID)
                    .checked(checked)
                    .build();
            actions.add(action);
            if (checked) {
                mSelectedTrack = info;
            }
        }
    }

    @Override
    public void onGuidedActionFocused(GuidedAction action) {
        int actionId = (int) action.getId();
        mOverlayFragment.selectTrack(mTrackType, actionId < 0 ? null : mTrackInfos.get(actionId));
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int actionId = (int) action.getId();
        mSelectedTrack = actionId < 0 ? null : mTrackInfos.get(actionId);
        TvSettings.setDvrPlaybackTrackSettings(getContext(), mTrackType, mSelectedTrack);
        getFragmentManager().popBackStack();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Workaround: when overlay fragment is faded out, any focus will lost due to overlay
        // fragment's implementation. So we disable overlay fragment's fading here to prevent
        // losing focus while users are interacting with the side fragment.
        mOverlayFragment.setFadingEnabled(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        // We disable fading of overlay fragment to prevent side fragment from losing focus,
        // therefore we should resume it here.
        mOverlayFragment.setFadingEnabled(true);
        mOverlayFragment.selectTrack(mTrackType, mSelectedTrack);
    }

    private String getTrackLabel(TvTrackInfo track, int trackIndex) {
        if (track.getLanguage() != null) {
            return new Locale(track.getLanguage()).getDisplayName();
        }
        return track.getType() == TvTrackInfo.TYPE_SUBTITLE ?
                getString(R.string.closed_caption_unknown_language, trackIndex + 1)
                : getString(R.string.multi_audio_unknown_language);
    }

    @Override
    protected void onProvideFragmentTransitions() {
        super.onProvideFragmentTransitions();
        // Excludes the background scrim from transition to prevent the blinking caused by
        // hiding the overlay fragment and sliding in the side fragment at the same time.
        Transition t = getEnterTransition();
        if (t != null) {
            t.excludeTarget(R.id.guidedstep_background, true);
        }
    }
}