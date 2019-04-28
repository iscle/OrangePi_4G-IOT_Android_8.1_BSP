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

import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.radio.service.RadioStation;

/**
 * A fragment that allows the user to manually input a radio station to tune to.
 */
public class ManualTunerFragment extends Fragment implements
        ManualTunerController.ManualTunerClickListener {
    public static final String RADIO_BAND_ARG = "radio_band_arg";

    private static final int DEFAULT_RADIO_BAND = RadioManager.BAND_FM;

    private ManualTunerController mController;
    private ManualTunerCompletionListener mListener;

    /**
     * Interface for a class that will notified on completion of a manual tune.
     */
    public interface ManualTunerCompletionListener {
        /**
         * Called when the user has finished selected a radio station on the manual tuner. If the
         * user exits the manual tuner without a station being selected, then {@code null} will
         * be passed to this method.
         *
         * @param station The {@link RadioStation} that was selected or {@code null} if the user
         *                exits before selecting one.
         */
        void onStationSelected(@Nullable RadioStation station);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.manual_tuner, container, false);

        int radioBand = getArguments().getInt(RADIO_BAND_ARG, DEFAULT_RADIO_BAND);

        mController = new ManualTunerController(getContext(), view, radioBand);
        mController.setDoneButtonListener(this);

        return view;
    }

    /**
     * Sets the listener that will be notified upon completion of manual tuning functions.
     */
    public void setManualTunerCompletionListener(ManualTunerCompletionListener listener) {
        mListener = listener;
    }

    @Override
    public void onBack() {
       if (mListener != null) {
           mListener.onStationSelected(null);
       }
    }

    @Override
    public void onDone(RadioStation station) {
        if (mListener != null) {
            mListener.onStationSelected(station);
        }
    }

    /**
     * Creates a new {@link ManualTunerFragment} that is defaulted to the given radio band.
     */
    public static ManualTunerFragment newInstance(int radioBand) {
        ManualTunerFragment fragment = new ManualTunerFragment();

        Bundle args = new Bundle();
        args.putInt(RADIO_BAND_ARG, radioBand);
        fragment.setArguments(args);

        return fragment;
    }
}
