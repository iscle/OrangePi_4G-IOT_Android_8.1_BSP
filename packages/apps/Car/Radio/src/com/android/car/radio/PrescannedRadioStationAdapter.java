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

import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.car.radio.service.RadioStation;

import java.util.List;

/**
 * A {@link com.android.car.radio.CarouselView.Adapter} that supplies the views to be displayed
 * from a list of {@link RadioStation}s.
 */
public class PrescannedRadioStationAdapter extends CarouselView.Adapter {
    private static final String TAG = "PreScanAdapter";

    private List<RadioStation> mStations;
    private int mCurrentPosition;

    /**
     * Sets the {@link RadioStation}s that will be used to bind to the views to be displayed.
     */
    public void setStations(List<RadioStation> stations) {
        mStations = stations;
        notifyDataSetChanged();
    }

    /**
     * Sets the the station within the list passed to {@link #setStations(List)} that should be
     * the first station displayed. A station is identified by the channel number and band.
     *
     * @return The index within the list of stations passed to {@link #setStations(List)} that the
     * starting station can be found at.
     */
    public int setStartingStation(int channelNumber, int band) {
        getIndexOrInsertForStation(channelNumber, band);
        return mCurrentPosition;
    }

    /**
     * Returns the station that is currently the first station to be displayed. This value can be
     * different from the value returned by {@link #setStartingStation(int, int)} if either
     * {@link #getPrevStation()} or {@link #getNextStation()} have been called.
     */
    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    /**
     * Returns the previous station in the list based off the value returned by
     * {@link #getCurrentPosition()}. After calling this method, the current position returned by
     * that method will be the index of the {@link RadioStation} returned by this method.
     */
    @Nullable
    public RadioStation getPrevStation() {
        if (mStations == null) {
            return null;
        }

        if (--mCurrentPosition < 0) {
            mCurrentPosition = mStations.size() - 1;
        }

        return mStations.get(mCurrentPosition);
    }

    /**
     * Returns the next station in the list based off the value returned by
     * {@link #getCurrentPosition()}. After calling this method, the current position returned by
     * that method will be the index of the {@link RadioStation} returned by this method.
     */
    @Nullable
    public RadioStation getNextStation() {
        if (mStations == null) {
            return null;
        }

        if (++mCurrentPosition >= mStations.size()) {
            mCurrentPosition = 0;
        }

        return mStations.get(mCurrentPosition);
    }

    /**
     * Returns the index in the list set in {@link #setStations(List)} that corresponds to the
     * given channel number and band. If the given combination does not exist within the list, then
     * it will be inserted in ascending order.
     *
     * @return An index into the list or -1 if no list of stations has been set.
     */
    public int getIndexOrInsertForStation(int channelNumber, int band) {
        if (mStations == null) {
            mCurrentPosition = -1;
            return -1;
        }

        int indexToInsert = 0;

        for (int i = 0, size = mStations.size(); i < size; i++) {
            RadioStation station = mStations.get(i);

            if (channelNumber >= station.getChannelNumber()) {
                // Need to add 1 to the index because the channel should be inserted after it.
                indexToInsert = i + 1;
            }

            if (station.getChannelNumber() == channelNumber && station.getRadioBand() == band) {
                mCurrentPosition = i;
                return i;
            }
        }

        // If this path is reached, that means an exact match for the station was not found in
        // the given list. Instead, insert the station into the list and return that index.
        RadioStation stationToInsert = new RadioStation(channelNumber, 0 /* subChannel */,
                band, null /* rds */);
        mStations.add(indexToInsert, stationToInsert);
        notifyDataSetChanged();

        mCurrentPosition = indexToInsert;
        return indexToInsert;
    }

    @Override
    public View createView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.radio_channel, parent,
                false);
    }

    @Override
    public void bindView(View view, int position, boolean isFirstView) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "bindView(); position: " + position + "; isFirstView: " + isFirstView);
        }

        if (mStations == null || position < 0 || position >= mStations.size()) {
            return;
        }

        TextView radioChannel = view.findViewById(R.id.radio_list_station_channel);
        TextView radioBandView = view.findViewById(R.id.radio_list_station_band);

        RadioStation station = mStations.get(position);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "binding station: " + station);
        }

        int radioBand = station.getRadioBand();

        radioChannel.setText(RadioChannelFormatter.formatRadioChannel(radioBand,
                station.getChannelNumber()));

        if (isFirstView) {
            radioBandView.setText(RadioChannelFormatter.formatRadioBand(view.getContext(),
                    radioBand));
        } else {
            radioBandView.setText(null);
        }
    }

    @Override
    public int getItemCount() {
        return mStations == null ? 0 : mStations.size();
    }
}
