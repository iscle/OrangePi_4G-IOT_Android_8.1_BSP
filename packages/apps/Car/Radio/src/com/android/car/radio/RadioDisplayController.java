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

package com.android.car.radio;

import android.content.Context;
import android.content.res.Resources;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Controller that controls the appearance state of various UI elements in the radio.
 */
public class RadioDisplayController {
    private final Context mContext;

    private TextView mChannelBand;
    private TextView mChannelNumber;

    private CarouselView mChannelList;

    private TextView mCurrentSongTitle;
    private TextView mCurrentSongArtistOrStation;

    private ImageView mBackwardSeekButton;
    private ImageView mForwardSeekButton;

    private PlayPauseButton mPlayButton;
    private PlayPauseButton mPresetPlayButton;

    private ImageView mPresetsListButton;
    private ImageView mAddPresetsButton;

    public RadioDisplayController(Context context) {
        mContext = context;
    }

    public void initialize(View container) {
        // Note that the band and channel number can exist without the stub
        // single_channel_view_stub. Refer to setSingleChannelDisplay() for more information.
        mChannelBand = container.findViewById(R.id.radio_station_band);
        mChannelNumber = container.findViewById(R.id.radio_station_channel);

        mCurrentSongTitle = container.findViewById(R.id.radio_station_song);
        mCurrentSongArtistOrStation = container.findViewById(R.id.radio_station_artist_or_station);

        mBackwardSeekButton = container.findViewById(R.id.radio_back_button);
        mForwardSeekButton = container.findViewById(R.id.radio_forward_button);

        mPlayButton = container.findViewById(R.id.radio_play_button);
        mPresetPlayButton = container.findViewById(R.id.preset_radio_play_button);

        mPresetsListButton = container.findViewById(R.id.radio_presets_list);
        mAddPresetsButton = container.findViewById(R.id.radio_add_presets_button);
    }

    /**
     * Sets this radio controller to display with a single box representing the current radio
     * station.
     */
    public void setSingleChannelDisplay(View container) {
        ViewStub stub = container.findViewById(R.id.single_channel_view_stub);

        if (stub != null) {
            container = stub.inflate();
        }

        // Update references to the band and channel number.
        mChannelBand = container.findViewById(R.id.radio_station_band);
        mChannelNumber = container.findViewById(R.id.radio_station_channel);
    }

    /**
     * Sets this controller to display a list of channels that include the current radio station as
     * well as pre-scanned stations for the current band.
     */
    public void setChannelListDisplay(View container, PrescannedRadioStationAdapter adapter) {
        ViewStub stub = container.findViewById(R.id.channel_list_view_stub);

        if (stub == null) {
            return;
        }

        mChannelList = (CarouselView) stub.inflate();
        mChannelList.setAdapter(adapter);

        Resources res = mContext.getResources();
        int topOffset = res.getDimensionPixelSize(R.dimen.lens_header_height)
                + res.getDimensionPixelSize(R.dimen.car_radio_container_top_padding)
                + res.getDimensionPixelSize(R.dimen.car_radio_station_top_margin);

        mChannelList.setTopOffset(topOffset);
    }

    /**
     * Set the given position as the radio station that should be be displayed first in the channel
     * list controlled by this class.
     */
    public void setCurrentStationInList(int position) {
        if (mChannelList != null) {
            mChannelList.shiftToPosition(position);
        }
    }

    /**
     * Set whether or not the buttons controlled by this controller are enabled. If {@code false}
     * is passed to this method, then no {@link View.OnClickListener}s will be
     * triggered when the buttons are pressed. In addition, the look of the button wil be updated
     * to reflect their disabled state.
     */
    public void setEnabled(boolean enabled) {
        // Color the buttons so that they are grey in appearance if they are disabled.
        int tint = enabled
                ? mContext.getColor(R.color.car_radio_control_button)
                : mContext.getColor(R.color.car_radio_control_button_disabled);

        if (mPlayButton != null) {
            // No need to tint the play button because its drawable already contains a disabled
            // state.
            mPlayButton.setEnabled(enabled);
        }

        if (mPresetPlayButton != null) {
            // No need to tint the play button because its drawable already contains a disabled
            // state.
            mPresetPlayButton.setEnabled(enabled);
        }

        if (mForwardSeekButton != null) {
            mForwardSeekButton.setEnabled(enabled);
            mForwardSeekButton.setColorFilter(tint);
        }

        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setEnabled(enabled);
            mBackwardSeekButton.setColorFilter(tint);
        }

        if (mPresetsListButton != null) {
            mPresetsListButton.setEnabled(enabled);
            mPresetsListButton.setColorFilter(tint);
        }

        if (mAddPresetsButton != null) {
            mAddPresetsButton.setEnabled(enabled);
            mAddPresetsButton.setColorFilter(tint);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the backwards seek button.
     */
    public void setBackwardSeekButtonListener(View.OnClickListener listener) {
        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the forward seek button.
     */
    public void setForwardSeekButtonListener(View.OnClickListener listener) {
        if (mForwardSeekButton != null) {
            mForwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the play button. Clicking on this
     * button should toggle the radio from muted to un-muted.
     */
    public void setPlayButtonListener(View.OnClickListener listener) {
        if (mPlayButton != null) {
            mPlayButton.setOnClickListener(listener);
        }

        if (mPresetPlayButton != null) {
            mPresetPlayButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the button that will add the current
     * radio station to a list of stored presets.
     */
    public void setAddPresetButtonListener(View.OnClickListener listener) {
        if (mAddPresetsButton != null) {
            mAddPresetsButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the current radio channel (e.g. 88.5).
     */
    public void setChannelNumber(String channel) {
        if (mChannelNumber != null) {
            mChannelNumber.setText(channel);
        }
    }

    /**
     * Sets the radio channel band (e.g. FM).
     */
    public void setChannelBand(String channelBand) {
        if (mChannelBand != null) {
            mChannelBand.setText(channelBand);
            mChannelBand.setVisibility(
                    !TextUtils.isEmpty(channelBand) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Sets the title of the currently playing song.
     */
    public void setCurrentSongTitle(String songTitle) {
        if (mCurrentSongTitle != null) {
            boolean isEmpty = TextUtils.isEmpty(songTitle);
            mCurrentSongTitle.setText(isEmpty ? null : songTitle.trim());
            mCurrentSongTitle.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Sets the artist(s) of the currently playing song or current radio station information
     * (e.g. KOIT).
     */
    public void setCurrentSongArtistOrStation(String songArtist) {
        if (mCurrentSongArtistOrStation != null) {
            boolean isEmpty = TextUtils.isEmpty(songArtist);
            mCurrentSongArtistOrStation.setText(isEmpty ? null : songArtist.trim());
            mCurrentSongArtistOrStation.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Sets the current state of the play button. If the given {@code muted} value is {@code true},
     * then the button display a play icon. If {@code false}, then the button will display a
     * pause icon.
     */
    public void setPlayPauseButtonState(boolean muted) {
        if (mPlayButton != null) {
            mPlayButton.setPlayState(muted
                    ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING);
            mPlayButton.refreshDrawableState();
        }

        if (mPresetPlayButton != null) {
            mPresetPlayButton.setPlayState(muted
                    ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING);
            mPresetPlayButton.refreshDrawableState();
        }
    }

    /**
     * Sets whether or not the current channel that is playing is a preset. If it is, then the
     * icon in {@link #mPresetsListButton} will be updatd to reflect this state.
     */
    public void setChannelIsPreset(boolean isPreset) {
        if (mAddPresetsButton != null) {
            mAddPresetsButton.setImageResource(isPreset
                    ? R.drawable.ic_star_filled
                    : R.drawable.ic_star_empty);
        }
    }
}
