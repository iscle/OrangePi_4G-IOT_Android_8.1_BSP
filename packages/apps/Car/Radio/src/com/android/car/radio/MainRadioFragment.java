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

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment that functions as the main display of the information relating to the current radio
 * station. It also displays controls that allows the user to switch to different radio stations.
 */
public class MainRadioFragment extends Fragment implements FragmentWithFade {
    private static final FastOutSlowInInterpolator sInterpolator = new FastOutSlowInInterpolator();
    private static final int FADE_OUT_START_DELAY_MS = 150;
    private static final int FADE_ANIM_TIME_MS = 100;

    private RadioController mRadioController;
    private RadioPresetListClickListener mPresetListListener;

    private View mRootView;
    private View mMainDisplay;

    /**
     * Interface for a class that will be notified when the button to open the list of the user's
     * favorite radio stations has been clicked.
     */
    public interface RadioPresetListClickListener {
        /**
         * Method that will be called when the preset list button has been clicked. Clicking this
         * button should open a display of the user's presets.
         */
        void onPresetListClicked();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.radio_fragment, container, false);

        mMainDisplay = mRootView.findViewById(R.id.radio_station_display_container);

        mRootView.findViewById(R.id.radio_presets_list).setOnClickListener(v -> {
            if (mPresetListListener != null) {
                mPresetListListener.onPresetListClicked();
            }
        });

        return mRootView;
    }

    @Override
    public void fadeOutContent() {
        ObjectAnimator containerAlphaAnimator =
                ObjectAnimator.ofFloat(mMainDisplay, View.ALPHA, 1f, 0f);
        containerAlphaAnimator.setInterpolator(sInterpolator);
        containerAlphaAnimator.setStartDelay(FADE_OUT_START_DELAY_MS);
        containerAlphaAnimator.setDuration(FADE_ANIM_TIME_MS);
        containerAlphaAnimator.start();
    }

    @Override
    public void fadeInContent() {
        ObjectAnimator containerAlphaAnimator =
                ObjectAnimator.ofFloat(mMainDisplay, View.ALPHA, 0f, 1f);
        containerAlphaAnimator.setInterpolator(sInterpolator);
        containerAlphaAnimator.setDuration(FADE_ANIM_TIME_MS);
        containerAlphaAnimator.start();
    }

    @Override
    public void onStart() {
        super.onStart();

        mRadioController.initialize(mRootView);
        mRadioController.setShouldColorStatusBar(true);

        fadeInContent();
    }

    @Override
    public void onStop() {
        super.onStop();
        fadeOutContent();
    }

    @Override
    public void onDestroy() {
        mPresetListListener = null;
        super.onDestroy();
    }

    /**
     * Returns a new instance of the {@link MainRadioFragment}.
     *
     * @param radioController The {@link RadioController} that is responsible for updating the UI
     *                        of the returned fragment.
     */
    static MainRadioFragment newInstance(RadioController radioController,
            RadioPresetListClickListener clickListener) {
        MainRadioFragment fragment = new MainRadioFragment();
        fragment.mRadioController = radioController;
        fragment.mPresetListListener = clickListener;

        return fragment;
    }
}
