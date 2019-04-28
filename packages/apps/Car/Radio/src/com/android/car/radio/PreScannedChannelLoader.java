/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.support.annotation.NonNull;
import com.android.car.radio.service.RadioStation;

import java.util.Collections;
import java.util.List;

/**
 * An {@link AsyncTaskLoader} that will load all the pre-scanned radio stations for a given band.
 */
public class PreScannedChannelLoader extends AsyncTaskLoader<List<RadioStation>> {
    private final static int INVALID_RADIO_BAND = -1;

    private final RadioStorage mRadioStorage;
    private int mCurrentRadioBand = INVALID_RADIO_BAND;

    public PreScannedChannelLoader(Context context) {
        super(context);
        mRadioStorage = RadioStorage.getInstance(context);
    }

    /**
     * Sets the radio band that this loader should load the pre-scanned stations for.
     *
     * @param radioBand One of the band values in {@link android.hardware.radio.RadioManager}. For
     *                  example, {@link android.hardware.radio.RadioManager#BAND_FM}.
     */
    public void setCurrentRadioBand(int radioBand) {
        mCurrentRadioBand = radioBand;
    }

    /**
     * Returns a list of {@link RadioStation}s representing the pre-scanned channels for the band
     * specified by {@link #INVALID_RADIO_BAND}. If no stations exist for the given band, then an
     * empty list is returned.
     */
    @NonNull
    @Override
    public List<RadioStation> loadInBackground() {
        if (mCurrentRadioBand == INVALID_RADIO_BAND) {
            return Collections.emptyList();
        }

        return mRadioStorage.getPreScannedStationsForBand(mCurrentRadioBand);
    }
}
