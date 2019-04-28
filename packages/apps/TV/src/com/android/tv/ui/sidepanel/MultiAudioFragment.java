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

package com.android.tv.ui.sidepanel;

import android.media.tv.TvTrackInfo;
import android.text.TextUtils;
import android.view.KeyEvent;

import com.android.tv.R;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class MultiAudioFragment extends SideFragment {
    private static final String TRACKER_LABEL = "multi-audio";
    private int mInitialSelectedPosition = INVALID_POSITION;
    private String mSelectedTrackId;
    private String mFocusedTrackId;

    public MultiAudioFragment() {
        super(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_A);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.side_panel_title_multi_audio);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<TvTrackInfo> tracks = getMainActivity().getTracks(TvTrackInfo.TYPE_AUDIO);
        mSelectedTrackId = getMainActivity().getSelectedTrack(TvTrackInfo.TYPE_AUDIO);

        List<Item> items = new ArrayList<>();
        if (tracks != null) {
            boolean needToShowSampleRate = Utils.needToShowSampleRate(getActivity(), tracks);
            int pos = 0;
            for (final TvTrackInfo track : tracks) {
                RadioButtonItem item = new MultiAudioOptionItem(
                        Utils.getMultiAudioString(getActivity(), track, needToShowSampleRate),
                        track.getId());
                if (track.getId().equals(mSelectedTrackId)) {
                    item.setChecked(true);
                    mInitialSelectedPosition = pos;
                    mSelectedTrackId = mFocusedTrackId = track.getId();
                }
                items.add(item);
                ++pos;
            }
        }
        return items;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mInitialSelectedPosition != INVALID_POSITION) {
            setSelectedPosition(mInitialSelectedPosition);
        }
    }

    private class MultiAudioOptionItem extends RadioButtonItem {
        private final String mTrackId;

        private MultiAudioOptionItem(String title, String trackId) {
            super(title);
            mTrackId = trackId;
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            mSelectedTrackId = mFocusedTrackId = mTrackId;
            getMainActivity().selectAudioTrack(mTrackId);
            closeFragment();
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            mFocusedTrackId = mTrackId;
            getMainActivity().selectAudioTrack(mTrackId);
        }
    }

    @Override
    public void onDetach() {
        if (!TextUtils.equals(mSelectedTrackId, mFocusedTrackId)) {
            getMainActivity().selectAudioTrack(mSelectedTrackId);
        }
        super.onDetach();
    }
}
