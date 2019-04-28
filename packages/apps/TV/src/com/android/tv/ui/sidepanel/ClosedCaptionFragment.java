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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.util.CaptionSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClosedCaptionFragment extends SideFragment {
    private static final String TRACKER_LABEL ="closed caption" ;
    private boolean mResetClosedCaption;
    private int mClosedCaptionOption;
    private String mClosedCaptionLanguage;
    private String mClosedCaptionTrackId;
    private ClosedCaptionOptionItem mSelectedItem;

    public ClosedCaptionFragment() {
        super(KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_S);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.side_panel_title_closed_caption);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        CaptionSettings captionSettings = getMainActivity().getCaptionSettings();
        mResetClosedCaption = true;
        mClosedCaptionOption = captionSettings.getEnableOption();
        mClosedCaptionLanguage = captionSettings.getLanguage();
        mClosedCaptionTrackId = captionSettings.getTrackId();

        List<Item> items = new ArrayList<>();
        mSelectedItem = null;

        List<TvTrackInfo> tracks = getMainActivity().getTracks(TvTrackInfo.TYPE_SUBTITLE);
        if (tracks != null && !tracks.isEmpty()) {
            String selectedTrackId = captionSettings.isEnabled() ?
                    getMainActivity().getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE) : null;
            ClosedCaptionOptionItem item = new ClosedCaptionOptionItem(null, null);
            items.add(item);
            if (selectedTrackId == null) {
                mSelectedItem = item;
                item.setChecked(true);
                setSelectedPosition(0);
            }
            for (int i = 0; i < tracks.size(); i++) {
                item = new ClosedCaptionOptionItem(tracks.get(i), i);
                if (TextUtils.equals(selectedTrackId, tracks.get(i).getId())) {
                    mSelectedItem = item;
                    item.setChecked(true);
                    setSelectedPosition(i + 1);
                }
                items.add(item);
            }
        }
        if (getMainActivity().hasCaptioningSettingsActivity()) {
            items.add(new ActionItem(getString(R.string.closed_caption_system_settings),
                    getString(R.string.closed_caption_system_settings_description)) {
                @Override
                protected void onSelected() {
                    getMainActivity().startSystemCaptioningSettingsActivity();
                }

                @Override
                protected void onFocused() {
                    super.onFocused();
                    if (mSelectedItem != null) {
                        getMainActivity().selectSubtitleTrack(
                                mSelectedItem.mOption, mSelectedItem.mTrackId);
                    }
                }
            });
        }
        return items;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        if (mResetClosedCaption) {
            getMainActivity().selectSubtitleLanguage(mClosedCaptionOption, mClosedCaptionLanguage,
                    mClosedCaptionTrackId);
        }
        super.onDestroyView();
    }

    private String getLabel(TvTrackInfo track, Integer trackIndex) {
        if (track == null) {
            return getString(R.string.closed_caption_option_item_off);
        } else if (track.getLanguage() != null) {
            return new Locale(track.getLanguage()).getDisplayName();
        }
        return getString(R.string.closed_caption_unknown_language, trackIndex + 1);
    }

    private class ClosedCaptionOptionItem extends RadioButtonItem {
        private final int mOption;
        private final String mTrackId;

        private ClosedCaptionOptionItem(TvTrackInfo track, Integer trackIndex) {
            super(getLabel(track, trackIndex));
            if (track == null) {
                mOption = CaptionSettings.OPTION_OFF;
                mTrackId = null;
            } else {
                mOption = CaptionSettings.OPTION_ON;
                mTrackId = track.getId();
            }
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            mSelectedItem = this;
            getMainActivity().selectSubtitleTrack(mOption, mTrackId);
            mResetClosedCaption = false;
            closeFragment();
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            getMainActivity().selectSubtitleTrack(mOption, mTrackId);
        }
    }
}
