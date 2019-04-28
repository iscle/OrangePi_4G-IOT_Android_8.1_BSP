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

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.car.radio.service.RadioStation;
import com.android.car.view.PagedListView;

import java.util.List;

/**
 * Adapter that will display a list of radio stations that represent the user's presets.
 */
public class PresetsAdapter extends RecyclerView.Adapter<PresetsViewHolder>
        implements PresetsViewHolder.OnPresetClickListener, PagedListView.ItemCap {
    private static final String TAG = "Em.PresetsAdapter";

    // Only one type of view in this adapter.
    private static final int PRESETS_VIEW_TYPE = 0;

    private RadioStation mActiveRadioStation;

    private List<RadioStation> mPresets;
    private OnPresetItemClickListener mPresetClickListener;

    /**
     * Interface for a listener that will be notified when an item in the presets list has been
     * clicked.
     */
    public interface OnPresetItemClickListener {
        /**
         * Method called when an item in the preset list has been clicked.
         *
         * @param radioStation The {@link RadioStation} corresponding to the clicked preset.
         */
        void onPresetItemClicked(RadioStation radioStation);
    }

    /**
     * Set a listener to be notified whenever a preset card is pressed.
     */
    public void setOnPresetItemClickListener(OnPresetItemClickListener listener) {
        mPresetClickListener = listener;
    }

    /**
     * Sets the given list as the list of presets to display.
     */
    public void setPresets(List<RadioStation> presets) {
        mPresets = presets;
        notifyDataSetChanged();
    }

    /**
     * Indicates which radio station is the active one inside the list of presets that are set on
     * this adapter. This will cause that station to be highlighted in the list. If the station
     * passed to this method does not match any of the presets, then none will be highlighted.
     */
    public void setActiveRadioStation(RadioStation station) {
        mActiveRadioStation = station;
        notifyDataSetChanged();
    }

    @Override
    public PresetsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.radio_preset_stream_card, parent, false);

        return new PresetsViewHolder(view, this /* listener */);
    }

    @Override
    public void onBindViewHolder(PresetsViewHolder holder, int position) {
        RadioStation station = mPresets.get(position);
        boolean isActiveStation = station.equals(mActiveRadioStation);

        holder.bindPreset(station, isActiveStation, getItemCount());
    }

    @Override
    public void onPresetClicked(int position) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("onPresetClicked(); item count: %d; position: %d",
                    getItemCount(), position));
        }

        if (mPresetClickListener != null && getItemCount() > position) {
            mPresetClickListener.onPresetItemClicked(mPresets.get(position));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return PRESETS_VIEW_TYPE;
    }

    @Override
    public int getItemCount() {
        return mPresets == null ? 0 : mPresets.size();
    }

    @Override
    public void setMaxItems(int max) {
        // No-op. A PagedListView needs the ItemCap interface to be implemented. However, the
        // list of presets should not be limited.
    }
}
