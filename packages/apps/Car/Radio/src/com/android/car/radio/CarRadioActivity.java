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

import android.content.Intent;
import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The main activity for the radio. This activity initializes the radio controls and listener for
 * radio changes.
 */
public class CarRadioActivity extends CarDrawerActivity implements
        RadioPresetsFragment.PresetListExitListener,
        MainRadioFragment.RadioPresetListClickListener,
        ManualTunerFragment.ManualTunerCompletionListener {
    private static final String TAG = "Em.RadioActivity";
    private static final String MANUAL_TUNER_BACKSTACK = "MANUAL_TUNER_BACKSTACK";
    private static final String CONTENT_FRAGMENT_TAG = "CONTENT_FRAGMENT_TAG";

    private static final int[] SUPPORTED_RADIO_BANDS = new int[] {
        RadioManager.BAND_AM, RadioManager.BAND_FM };

    /**
     * Intent action for notifying that the radio state has changed.
     */
    private static final String ACTION_RADIO_APP_STATE_CHANGE
            = "android.intent.action.RADIO_APP_STATE_CHANGE";

    /**
     * Boolean Intent extra indicating if the radio is the currently in the foreground.
     */
    private static final String EXTRA_RADIO_APP_FOREGROUND
            = "android.intent.action.RADIO_APP_STATE";

    /**
     * Whether or not it is safe to make transactions on the
     * {@link android.support.v4.app.FragmentManager}. This variable prevents a possible exception
     * when calling commit() on the FragmentManager.
     *
     * <p>The default value is {@code true} because it is only after
     * {@link #onSaveInstanceState(Bundle)} has been called that fragment commits are not allowed.
     */
    private boolean mAllowFragmentCommits = true;

    private RadioController mRadioController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRadioController = new RadioController(this);
        setContentFragment(
                MainRadioFragment.newInstance(mRadioController, this /* clickListener */));

    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return new RadioDrawerAdapter();
    }

    @Override
    public void onPresetListClicked() {
        setContentFragment(
                RadioPresetsFragment.newInstance(mRadioController, this /* existListener */));
    }

    @Override
    public void OnPresetListExit() {
        setContentFragment(
                MainRadioFragment.newInstance(mRadioController, this /* clickListener */));
    }

    private void startManualTuner() {
        if (!mAllowFragmentCommits || getSupportFragmentManager().getBackStackEntryCount() > 0) {
            return;
        }

        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof FragmentWithFade) {
            ((FragmentWithFade) currentFragment).fadeOutContent();
        }

        ManualTunerFragment tunerFragment =
                ManualTunerFragment.newInstance(mRadioController.getCurrentRadioBand());
        tunerFragment.setManualTunerCompletionListener(this);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down,
                        R.anim.slide_up, R.anim.slide_down)
                .add(getContentContainerId(), tunerFragment)
                .addToBackStack(MANUAL_TUNER_BACKSTACK)
                .commit();
    }

    @Override
    public void onStationSelected(RadioStation station) {
        maybeDismissManualTuner();

        Fragment fragment = getCurrentFragment();
        if (fragment instanceof FragmentWithFade) {
            ((FragmentWithFade) fragment).fadeInContent();
        }

        if (station != null) {
            mRadioController.tuneToRadioChannel(station);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStart");
        }

        // Fragment commits are not allowed once the Activity's state has been saved. Once
        // onStart() has been called, the FragmentManager should now allow commits.
        mAllowFragmentCommits = true;

        mRadioController.start();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, true);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStop");
        }

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, false);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDestroy");
        }

        mRadioController.shutdown();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // A transaction can only be committed with this method prior to its containing activity
        // saving its state.
        mAllowFragmentCommits = false;
        super.onSaveInstanceState(outState);
    }

    /**
     * Checks if the manual tuner is currently being displayed. If it is, then dismiss it.
     */
    private void maybeDismissManualTuner() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            // A station can only be selected if the manual tuner fragment has been shown; so, remove
            // that here.
            getSupportFragmentManager().popBackStack();
        }
    }

    private void setContentFragment(Fragment fragment) {
        if (!mAllowFragmentCommits) {
            return;
        }

        getSupportFragmentManager().beginTransaction()
                .replace(getContentContainerId(), fragment, CONTENT_FRAGMENT_TAG)
                .commitNow();
    }

    /**
     * Returns the fragment that is currently being displayed as the content view. Note that this
     * is not necessarily the fragment that is visible. The manual tuner fragment can be displayed
     * on top of this content fragment.
     */
    @Nullable
    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentByTag(CONTENT_FRAGMENT_TAG);
    }

    /**
     * An adapter that is responsible for populating the Radio drawer with the available bands to
     * select, as well as the option for opening the manual tuner.
     */
    private class RadioDrawerAdapter extends CarDrawerAdapter {
        private final List<String> mDrawerOptions =
                new ArrayList<>(SUPPORTED_RADIO_BANDS.length + 1);

        RadioDrawerAdapter() {
            super(CarRadioActivity.this, false /* showDisabledListOnEmpty */);
            setTitle(getString(R.string.app_name));
            // The ordering of options is hardcoded. The click handler below depends on it.
            for (int band : SUPPORTED_RADIO_BANDS) {
                String bandText =
                        RadioChannelFormatter.formatRadioBand(CarRadioActivity.this, band);
                mDrawerOptions.add(bandText);
            }
            mDrawerOptions.add(getString(R.string.manual_tuner_drawer_entry));
        }

        @Override
        protected int getActualItemCount() {
            return mDrawerOptions.size();
        }

        @Override
        public void populateViewHolder(DrawerItemViewHolder holder, int position) {
            holder.getTitle().setText(mDrawerOptions.get(position));
        }

        @Override
        public void onItemClick(int position) {
            closeDrawer();
            if (position < SUPPORTED_RADIO_BANDS.length) {
                mRadioController.openRadioBand(SUPPORTED_RADIO_BANDS[position]);
            } else if (position == SUPPORTED_RADIO_BANDS.length) {
                startManualTuner();
            } else {
                Log.w(TAG, "Unexpected position: " + position);
            }
        }
    }
}
