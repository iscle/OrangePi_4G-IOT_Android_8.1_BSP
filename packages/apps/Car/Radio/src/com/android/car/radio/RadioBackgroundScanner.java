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
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.support.annotation.Nullable;
import android.util.Log;
import com.android.car.radio.service.RadioRds;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that will scan for valid radio stations in the background and store those stations into
 * the radio database so that available stations for a particular radio band can be pre-populated
 * for the user.
 */
public class RadioBackgroundScanner extends RadioTuner.Callback {
    private static final String TAG = "Em.BackgroundScanner";
    private static final int INVALID_RADIO_CHANNEL = -1;

    private RadioTuner mRadioTuner;

    private final RadioManager mRadioManager;
    private final RadioManager.AmBandConfig mAmConfig;
    private final RadioManager.FmBandConfig mFmConfig;
    private final RadioManager.ModuleProperties mRadioModule;
    private final RadioStorage mRadioStorage;

    private int mCurrentChannel;
    private int mCurrentBand;
    private int mStartingChannel = INVALID_RADIO_CHANNEL;

    private List<RadioStation> mScannedStations = new ArrayList<>();

    public RadioBackgroundScanner(Context context, RadioManager radioManager,
            RadioManager.AmBandConfig amConfig, RadioManager.FmBandConfig fmConfig,
            RadioManager.ModuleProperties module) {
        mRadioManager = radioManager;
        mAmConfig = amConfig;
        mFmConfig = fmConfig;
        mRadioModule = module;

        mRadioStorage = RadioStorage.getInstance(context);
    }

    /**
     * Notify this {@link RadioBackgroundScanner} that the current radio band has changed and
     * a new scan should start.
     */
    public void onRadioBandChanged(int radioBand) {
        mStartingChannel = INVALID_RADIO_CHANNEL;
        mCurrentBand = radioBand;
        mScannedStations.clear();

        RadioManager.BandConfig config = getRadioConfig(radioBand);

        if (config == null) {
            Log.w(TAG, "Cannot create config for radio band: " + radioBand);
            return;
        }

        if (mRadioTuner != null) {
            mRadioTuner.setConfiguration(config);
        } else {
            mRadioTuner = mRadioManager.openTuner(mRadioModule.getId(), config, true,
                    this /* callback */, null /* handler */);
        }
    }

    /**
     * Returns the proper {@link android.hardware.radio.RadioManager.BandConfig} for the given
     * radio band. {@code null} is returned if the band is not suppored.
     */
    @Nullable
    private RadioManager.BandConfig getRadioConfig(int selectedRadioBand) {
        switch (selectedRadioBand) {
            case RadioManager.BAND_AM:
                return mAmConfig;
            case RadioManager.BAND_FM:
                return mFmConfig;

            // TODO: Support BAND_FM_HD and BAND_AM_HD.

            default:
                return null;
        }
    }

    /**
     * Replaces the given station in {@link #mScannedStations} or adds it to the end of the list if
     * it does not exist.
     */
    private void addOrReplaceInScannedStations(RadioStation station) {
        int index = mScannedStations.indexOf(station);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Storing pre-scanned station: " + station);
        }

        if (index == -1) {
            mScannedStations.add(station);
        } else {
            mScannedStations.set(index, station);
        }
    }

    @Override
    public void onProgramInfoChanged(RadioManager.ProgramInfo info) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onProgramInfoChanged(); info: " + info);
        }

        if (info == null) {
            return;
        }

        mCurrentChannel = info.getChannel();

        if (mStartingChannel == INVALID_RADIO_CHANNEL) {
            mStartingChannel = mCurrentChannel;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Starting scan from channel: " + mStartingChannel);
            }
        }

        // Stop scanning if we have looped back to the starting station.
        if (mStartingChannel == mCurrentChannel) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Looped back around to starting channel %s; storing "
                        + "%d pre-scanned stations", mStartingChannel, mScannedStations.size()));
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                for (RadioStation station : mScannedStations) {
                    Log.v(TAG, station.toString());
                }
            }

            mStartingChannel = INVALID_RADIO_CHANNEL;

            // Close the RadioTuner so that this class no longer receives any callbacks and store
            // all scanned statiosn into the database.
            mRadioTuner.close();
            mRadioTuner = null;
            mRadioStorage.storePreScannedStations(mCurrentBand, mScannedStations);
            return;
        }

        RadioMetadata metadata = info.getMetadata();

        // If there is no metadata, then directly store the radio information into the database.
        // Otherwise, onMetadataChanged() can handle the storage.
        if (metadata != null) {
            onMetadataChanged(metadata);
        } else {
            RadioStation station = new RadioStation(mCurrentChannel, 0 /* subChannelNumber */,
                    mCurrentBand, null /* rds */);
            addOrReplaceInScannedStations(station);
        }

        // Initialize another seek to the next valid station.
        mRadioTuner.scan(RadioTuner.DIRECTION_UP, true);
    }

    @Override
    public void onMetadataChanged(RadioMetadata metadata) {
        if (metadata == null) {
            return;
        }

        String stationInfo = metadata.getString(RadioMetadata.METADATA_KEY_RDS_PS);
        RadioRds rds = new RadioRds(stationInfo, null /* songArtist */, null /* songTitle */);

        RadioStation station = new RadioStation(mCurrentChannel, 0 /* subChannelNumber */,
                mCurrentBand, rds);
        addOrReplaceInScannedStations(station);
    }

    @Override
    public void onConfigurationChanged(RadioManager.BandConfig config) {
        if (config == null) {
            return;
        }

        mCurrentBand = config.getType();
    }
}
