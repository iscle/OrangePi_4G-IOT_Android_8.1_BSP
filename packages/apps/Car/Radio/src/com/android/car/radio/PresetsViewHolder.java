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
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.android.car.radio.service.RadioStation;

/**
 * A {@link RecyclerView.ViewHolder} that can bind a {@link RadioStation} to the layout
 * {@code R.layout.radio_preset_item}.
 */
public class PresetsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    private static final String TAG = "Em.PresetVH";

    private final RadioChannelColorMapper mColorMapper;

    private final OnPresetClickListener mPresetClickListener;

    private final Context mContext;
    private final View mPresetsCard;
    private GradientDrawable mPresetItemChannelBg;
    private final TextView mPresetItemChannel;
    private final TextView mPresetItemMetadata;
    private final View mEqualizer;

    /**
     * Interface for a listener when the View held by this ViewHolder has been clicked.
     */
    public interface OnPresetClickListener {
        /**
         * Method to be called when the View in this ViewHolder has been clicked.
         *
         * @param position The position of the View within the RecyclerView this ViewHolder is
         *                 populating.
         */
        void onPresetClicked(int position);
    }

    /**
     * @param presetsView A view that contains the layout {@code R.layout.radio_preset_item}.
     */
    public PresetsViewHolder(@NonNull View presetsView, @NonNull OnPresetClickListener listener) {
        super(presetsView);

        mContext = presetsView.getContext();

        mPresetsCard = presetsView.findViewById(R.id.preset_card);;
        mPresetsCard.setOnClickListener(this);

        mColorMapper = RadioChannelColorMapper.getInstance(mContext);
        mPresetClickListener = listener;

        mPresetItemChannel = presetsView.findViewById(R.id.preset_station_channel);
        mPresetItemMetadata = presetsView.findViewById(R.id.preset_item_metadata);
        mEqualizer = presetsView.findViewById(R.id.preset_equalizer);

        mPresetItemChannelBg = (GradientDrawable) mPresetItemChannel.getBackground();
    }

    @Override
    public void onClick(View view) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onClick() for view at position: " + getAdapterPosition());
        }

        mPresetClickListener.onPresetClicked(getAdapterPosition());
    }

    /**
     * Binds the given {@link RadioStation} to this View within this ViewHolder.
     */
    public void bindPreset(RadioStation preset, boolean isActiveStation, int itemCount) {
        // If the preset is null, clear any existing text.
        if (preset == null) {
            mPresetItemChannel.setText(null);
            mPresetItemMetadata.setText(null);
            mPresetItemChannelBg.setColor(mColorMapper.getDefaultColor());
            return;
        }

        setPresetCardBackground(itemCount);

        String channelNumber = RadioChannelFormatter.formatRadioChannel(preset.getRadioBand(),
                preset.getChannelNumber());

        mPresetItemChannel.setText(channelNumber);

        mEqualizer.setVisibility(isActiveStation ? View.VISIBLE : View.GONE);

        mPresetItemChannelBg.setColor(mColorMapper.getColorForStation(preset));

        String metadata = preset.getRds() == null ? null : preset.getRds().getProgramService();

        if (TextUtils.isEmpty(metadata)) {
            // If there is no metadata text, then use text to indicate the favorite number to the
            // user so that list does not appear empty.
            mPresetItemMetadata.setText(mContext.getString(
                    R.string.radio_default_preset_metadata_text, getAdapterPosition() + 1));
        } else {
            mPresetItemMetadata.setText(metadata.trim());
        }
    }

    /**
     * Sets the appropriate background on the card containing the preset information. The cards
     * need to have rounded corners depending on its position in the list and the number of items
     * in the list.
     */
    private void setPresetCardBackground(int itemCount) {
        int position = getAdapterPosition();

        // Correctly set the background for each card. Only the top and last card should
        // have rounded corners.
        if (itemCount == 1) {
            // One card - all corners are rounded
            mPresetsCard.setBackgroundResource(R.drawable.preset_item_card_rounded_bg);
        } else if (position == 0) {
            // First card gets rounded top
            mPresetsCard.setBackgroundResource(R.drawable.preset_item_card_rounded_top_bg);
        } else if (position == itemCount - 1) {
            // Last one has a rounded bottom
            mPresetsCard.setBackgroundResource(R.drawable.preset_item_card_rounded_bottom_bg);
        } else {
            // Middle have no rounded corners
            mPresetsCard.setBackgroundResource(R.color.car_card);
        }
    }
}
