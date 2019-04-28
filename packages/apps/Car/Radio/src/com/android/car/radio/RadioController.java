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

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.car.radio.service.IRadioCallback;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.service.RadioRds;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller that handles the display of metadata on the current radio station.
 */
public class RadioController implements
        RadioStorage.PresetsChangeListener,
        RadioStorage.PreScannedChannelChangeListener,
        LoaderManager.LoaderCallbacks<List<RadioStation>> {
    private static final String TAG = "Em.RadioController";
    private static final int CHANNEL_LOADER_ID = 0;

    /**
     * The percentage by which to darken the color that should be set on the status bar.
     * This darkening gives the status bar the illusion that it is transparent.
     *
     * @see RadioController#setShouldColorStatusBar(boolean)
     */
    private static final float STATUS_BAR_DARKEN_PERCENTAGE = 0.4f;

    /**
     * The animation time for when the background of the radio shifts to a different color.
     */
    private static final int BACKGROUND_CHANGE_ANIM_TIME_MS = 450;
    private static final int INVALID_BACKGROUND_COLOR = 0;

    private static final int CHANNEL_CHANGE_DURATION_MS = 200;

    private int mCurrentChannelNumber = RadioStorage.INVALID_RADIO_CHANNEL;

    private final Activity mActivity;
    private IRadioManager mRadioManager;

    private View mRadioBackground;
    private boolean mShouldColorStatusBar;

    /**
     * An additional layer on top of the background that should match the color of
     * {@link #mRadioBackground}. This view should only exist in the preset list. The reason this
     * layer cannot be transparent is because it needs to be elevated, and elevation does not
     * work if the background is undefined or transparent.
     */
    private View mRadioPresetBackground;

    private View mRadioErrorDisplay;

    private final RadioChannelColorMapper mColorMapper;
    @ColorInt private int mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

    private PrescannedRadioStationAdapter mAdapter;
    private PreScannedChannelLoader mChannelLoader;

    private final RadioDisplayController mRadioDisplayController;
    private boolean mHasDualTuners;

    /**
     * Keeps track of if the user has manually muted the radio. This value is used to determine
     * whether or not to un-mute the radio after an {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT}
     * event has been received.
     */
    private boolean mUserHasMuted;

    private final RadioStorage mRadioStorage;

    /**
     * The current radio band. This value is one of the BAND_* values from {@link RadioManager}.
     * For example, {@link RadioManager#BAND_FM}.
     */
    private int mCurrentRadioBand = RadioStorage.INVALID_RADIO_BAND;
    private final String mAmBandString;
    private final String mFmBandString;

    private RadioRds mCurrentRds;

    private RadioStationChangeListener mStationChangeListener;

    /**
     * Interface for a class that will be notified when the current radio station has been changed.
     */
    public interface RadioStationChangeListener {
        /**
         * Called when the current radio station has changed in the radio.
         *
         * @param station The current radio station.
         */
        void onRadioStationChanged(RadioStation station);
    }

    public RadioController(Activity activity) {
        mActivity = activity;

        mRadioDisplayController = new RadioDisplayController(mActivity);
        mColorMapper = RadioChannelColorMapper.getInstance(mActivity);

        mAmBandString = mActivity.getString(R.string.radio_am_text);
        mFmBandString = mActivity.getString(R.string.radio_fm_text);

        mRadioStorage = RadioStorage.getInstance(mActivity);
        mRadioStorage.addPresetsChangeListener(this);
    }

    /**
     * Initializes this {@link RadioController} to control the UI whose root is the given container.
     */
    public void initialize(View container) {
        mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

        mRadioDisplayController.initialize(container);

        mRadioDisplayController.setBackwardSeekButtonListener(mBackwardSeekClickListener);
        mRadioDisplayController.setForwardSeekButtonListener(mForwardSeekClickListener);
        mRadioDisplayController.setPlayButtonListener(mPlayPauseClickListener);
        mRadioDisplayController.setAddPresetButtonListener(mPresetButtonClickListener);

        mRadioBackground = container;
        mRadioPresetBackground = container.findViewById(R.id.preset_current_card_container);

        mRadioErrorDisplay = container.findViewById(R.id.radio_error_display);

        updateRadioDisplay();
    }

    /**
     * Set whether or not this controller should also update the color of the status bar to match
     * the current background color of the radio. The color that will be set on the status bar
     * will be slightly darker, giving the illusion that the status bar is transparent.
     *
     * <p>This method is needed because of scene transitions. Scene transitions do not take into
     * account padding that is added programmatically. Since there is no way to get the height of
     * the status bar and set it in XML, it needs to be done in code. This breaks the scene
     * transition.
     *
     * <p>To make this work, the status bar is not actually translucent; it is colored to appear
     * that way via this method.
     */
    public void setShouldColorStatusBar(boolean shouldColorStatusBar) {
       mShouldColorStatusBar = shouldColorStatusBar;
    }

    /**
     * Sets the listener that will be notified whenever the radio station changes.
     */
    public void setRadioStationChangeListener(RadioStationChangeListener listener) {
        mStationChangeListener = listener;
    }

    /**
     * Starts the controller to handle radio tuning. This method should be called to begin
     * radio playback.
     */
    public void start() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "starting radio");
        }

        Intent bindIntent = new Intent(mActivity, RadioService.class);
        if (!mActivity.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to connect to RadioService.");
        }

        updateRadioDisplay();
    }

    /**
     * Retrieves information about the current radio station from {@link #mRadioManager} and updates
     * the display of that information accordingly.
     */
    private void updateRadioDisplay() {
        if (mRadioManager == null) {
            return;
        }

        try {
            RadioStation station = mRadioManager.getCurrentRadioStation();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateRadioDisplay(); current station: " + station);
            }

            mHasDualTuners = mRadioManager.hasDualTuners();

            if (mHasDualTuners) {
                initializeDualTunerController();
            } else {
                mRadioDisplayController.setSingleChannelDisplay(mRadioBackground);
            }

            // Update the AM/FM band display.
            mCurrentRadioBand = station.getRadioBand();
            updateAmFmDisplayState();

            // Update the channel number.
            setRadioChannel(station.getChannelNumber());

            // Ensure the play button properly reflects the current mute state.
            mRadioDisplayController.setPlayPauseButtonState(mRadioManager.isMuted());

            mCallback.onRadioMetadataChanged(station.getRds());

            if (mStationChangeListener != null) {
                mStationChangeListener.onRadioStationChanged(station);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "updateRadioDisplay(); remote exception: " + e.getMessage());
        }
    }

    /**
     * Tunes the radio to the given channel if it is valid and a {@link RadioTuner} has been opened.
     */
    public void tuneToRadioChannel(RadioStation radioStation) {
        if (mRadioManager == null) {
            return;
        }

        try {
            mRadioManager.tune(radioStation);
        } catch (RemoteException e) {
            Log.e(TAG, "tuneToRadioChannel(); remote exception: " + e.getMessage());
        }
    }

    /**
     * Returns the band this radio is currently tuned to.
     */
    public int getCurrentRadioBand() {
        return mCurrentRadioBand;
    }

    /**
     * Returns the radio station that is currently playing on the radio. If this controller is
     * not connected to the {@link RadioService} or a radio station cannot be retrieved, then
     * {@code null} is returned.
     */
    @Nullable
    public RadioStation getCurrentRadioStation() {
        if (mRadioManager == null) {
            return null;
        }

        try {
            return mRadioManager.getCurrentRadioStation();
        } catch (RemoteException e) {
            Log.e(TAG, "getCurrentRadioStation(); error retrieving current station: "
                    + e.getMessage());
        }

        return null;
    }

    /**
     * Opens the given current radio band. Currently, this only supports FM and AM bands.
     *
     * @param radioBand One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
     *                  {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
     */
    public void openRadioBand(int radioBand) {
        if (mRadioManager == null || radioBand == mCurrentRadioBand) {
            return;
        }

        // Reset the channel number so that we do not animate number changes between band changes.
        mCurrentChannelNumber = RadioStorage.INVALID_RADIO_CHANNEL;

        setCurrentRadioBand(radioBand);
        mRadioStorage.storeRadioBand(mCurrentRadioBand);

        try {
            mRadioManager.openRadioBand(radioBand);

            updateAmFmDisplayState();

            // Sets the initial mute state. This will resolve the mute state should be if an
            // {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT} event is received followed by an
            // {@link AudioManager#AUDIOFOCUS_GAIN} event. In this case, the radio will un-mute itself
            // if the user has not muted beforehand.
            if (mUserHasMuted) {
                mRadioManager.mute();
            }

            // Ensure the play button properly reflects the current mute state.
            mRadioDisplayController.setPlayPauseButtonState(mRadioManager.isMuted());

            maybeTuneToStoredRadioChannel();
        } catch (RemoteException e) {
            Log.e(TAG, "openRadioBand(); remote exception: " + e.getMessage());
        }
    }

    /**
     * Attempts to tune to the last played radio channel for a particular band. For example, if
     * the user switches to the AM band from FM, this method will attempt to tune to the last
     * AM band that the user was on.
     *
     * <p>If a stored radio station cannot be found, then this method will initiate a seek so that
     * the radio is always on a valid radio station.
     */
    private void maybeTuneToStoredRadioChannel() {
        mCurrentChannelNumber = mRadioStorage.getStoredRadioChannel(mCurrentRadioBand);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("maybeTuneToStoredRadioChannel(); band: %s, channel %s",
                    mCurrentRadioBand, mCurrentChannelNumber));
        }

        // Tune to a stored radio channel if it exists.
        if (mCurrentChannelNumber != RadioStorage.INVALID_RADIO_CHANNEL) {
            RadioStation station = new RadioStation(mCurrentChannelNumber, 0 /* subchannel */,
                    mCurrentRadioBand, mCurrentRds);
            tuneToRadioChannel(station);
        } else {
            // Otherwise, ensure that the radio is on a valid radio station (i.e. it will not
            // start playing static) by initiating a seek.
            try {
                mRadioManager.seekForward();
            } catch (RemoteException e) {
                Log.e(TAG, "maybeTuneToStoredRadioChannel(); remote exception: " + e.getMessage());
            }
        }
    }

    /**
     * Delegates to the {@link RadioDisplayController} to highlight the radio band that matches
     * up to {@link #mCurrentRadioBand}.
     */
    private void updateAmFmDisplayState() {
        switch (mCurrentRadioBand) {
            case RadioManager.BAND_FM:
                mRadioDisplayController.setChannelBand(mFmBandString);
                break;

            case RadioManager.BAND_AM:
                mRadioDisplayController.setChannelBand(mAmBandString);
                break;

            // TODO: Support BAND_FM_HD and BAND_AM_HD.

            default:
                mRadioDisplayController.setChannelBand(null);
        }
    }

    /**
     * Sets the radio channel to display.
     * @param channel The radio channel frequency in Hz.
     */
    private void setRadioChannel(int channel) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Setting radio channel: " + channel);
        }

        if (channel <= 0) {
            mCurrentChannelNumber = channel;
            mRadioDisplayController.setChannelNumber("");
            return;
        }

        if (mHasDualTuners) {
            int position = mAdapter.getIndexOrInsertForStation(channel, mCurrentRadioBand);
            mRadioDisplayController.setCurrentStationInList(position);
        }

        switch (mCurrentRadioBand) {
            case RadioManager.BAND_FM:
                setRadioChannelForFm(channel);
                break;

            case RadioManager.BAND_AM:
                setRadioChannelForAm(channel);
                break;

            // TODO: Support BAND_FM_HD and BAND_AM_HD.

            default:
                // Do nothing and don't check presets, so return here.
                return;
        }

        mCurrentChannelNumber = channel;

        mRadioDisplayController.setChannelIsPreset(
                mRadioStorage.isPreset(channel, mCurrentRadioBand));

        mRadioStorage.storeRadioChannel(mCurrentRadioBand, mCurrentChannelNumber);

        maybeUpdateBackgroundColor();
    }

    private void setRadioChannelForAm(int channel) {
        // No need for animation if radio channel has never been set.
        if (mCurrentChannelNumber == RadioStorage.INVALID_RADIO_CHANNEL) {
            mRadioDisplayController.setChannelNumber(
                    RadioChannelFormatter.AM_FORMATTER.format(channel));
            return;
        }

        animateRadioChannelChange(mCurrentChannelNumber, channel, mAmAnimatorListener);
    }

    private void setRadioChannelForFm(int channel) {
        // FM channels are displayed in Khz. e.g. 88500 is displayed as 88.5.
        float channelInKHz = (float) channel / 1000;

        // No need for animation if radio channel has never been set.
        if (mCurrentChannelNumber == RadioStorage.INVALID_RADIO_CHANNEL) {
            mRadioDisplayController.setChannelNumber(
                    RadioChannelFormatter.FM_FORMATTER.format(channelInKHz));
            return;
        }

        float startChannelNumber = (float) mCurrentChannelNumber / 1000;
        animateRadioChannelChange(startChannelNumber, channelInKHz, mFmAnimatorListener);
    }

    /**
     * Checks if the color of the radio background should be changed, and if so, animates that
     * color change.
     */
    private void maybeUpdateBackgroundColor() {
        if (mRadioBackground == null) {
            return;
        }

        int newColor = mColorMapper.getColorForStation(mCurrentRadioBand, mCurrentChannelNumber);

        // No animation required if the colors are the same.
        if (newColor == mCurrentBackgroundColor) {
            return;
        }

        // If the current background color is invalid, then just set as the new color without any
        // animation.
        if (mCurrentBackgroundColor == INVALID_BACKGROUND_COLOR) {
            mCurrentBackgroundColor = newColor;
            setBackgroundColor(newColor);
        }

        // Otherwise, animate the background color change.
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                mCurrentBackgroundColor, newColor);
        colorAnimation.setDuration(BACKGROUND_CHANGE_ANIM_TIME_MS);
        colorAnimation.addUpdateListener(mBackgroundColorUpdater);
        colorAnimation.start();

        mCurrentBackgroundColor = newColor;
    }

    private void setBackgroundColor(int backgroundColor) {
        mRadioBackground.setBackgroundColor(backgroundColor);

        if (mRadioPresetBackground != null) {
            mRadioPresetBackground.setBackgroundColor(backgroundColor);
        }

        if (mShouldColorStatusBar) {
            int red = darkenColor(Color.red(backgroundColor));
            int green = darkenColor(Color.green(backgroundColor));
            int blue = darkenColor(Color.blue(backgroundColor));
            int alpha = Color.alpha(backgroundColor);

            mActivity.getWindow().setStatusBarColor(
                    Color.argb(alpha, red, green, blue));
        }
    }

    /**
     * Darkens the given color by {@link #STATUS_BAR_DARKEN_PERCENTAGE}.
     */
    private int darkenColor(int color) {
        return (int) Math.max(color - (color * STATUS_BAR_DARKEN_PERCENTAGE), 0);
    }

    /**
     * Animates the text in channel number from the given starting value to the given
     * end value.
     */
    private void animateRadioChannelChange(float startValue, float endValue,
            ValueAnimator.AnimatorUpdateListener listener) {
        ValueAnimator animator = new ValueAnimator();
        animator.setObjectValues(startValue, endValue);
        animator.setDuration(CHANNEL_CHANGE_DURATION_MS);
        animator.addUpdateListener(listener);
        animator.start();
    }

    /**
     * Clears all metadata including song title, artist and station information.
     */
    private void clearMetadataDisplay() {
        mCurrentRds = null;

        mRadioDisplayController.setCurrentSongArtistOrStation(null);
        mRadioDisplayController.setCurrentSongTitle(null);
    }

    /**
     * Sets the internal {@link #mCurrentRadioBand} to be the given radio band. Will also take care
     * of restarting a load of the pre-scanned radio stations for the given band if there are dual
     * tuners on the device.
     */
    private void setCurrentRadioBand(int radioBand) {
        if (mCurrentRadioBand == radioBand) {
            return;
        }

        mCurrentRadioBand = radioBand;

        if (mChannelLoader != null) {
            mAdapter.setStations(new ArrayList<>());
            mChannelLoader.setCurrentRadioBand(radioBand);
            mChannelLoader.forceLoad();
        }
    }

    /**
     * Closes any active {@link RadioTuner}s and releases audio focus.
     */
    private void close() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "close()");
        }

        // Lost focus, so display that the radio is not playing anymore.
        mRadioDisplayController.setPlayPauseButtonState(true);
    }

    /**
     * Closes all active connections in the {@link RadioController}.
     */
    public void shutdown() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "shutdown()");
        }

        mActivity.unbindService(mServiceConnection);
        mRadioStorage.removePresetsChangeListener(this);
        mRadioStorage.removePreScannedChannelChangeListener(this);

        if (mRadioManager != null) {
            try {
                mRadioManager.removeRadioTunerCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "tuneToRadioChannel(); remote exception: " + e.getMessage());
            }
        }

        close();
    }

    /**
     * Initializes all the extra components that are needed if this radio has dual tuners.
     */
    private void initializeDualTunerController() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "initializeDualTunerController()");
        }

        mRadioStorage.addPreScannedChannelChangeListener(RadioController.this);

        if (mAdapter == null) {
            mAdapter = new PrescannedRadioStationAdapter();
        }

        mRadioDisplayController.setChannelListDisplay(mRadioBackground, mAdapter);

        // Initialize the loader that will load the pre-scanned channels for the current band.
        mActivity.getLoaderManager().initLoader(CHANNEL_LOADER_ID, null /* args */,
                RadioController.this /* callback */).forceLoad();
    }

    @Override
    public void onPresetsRefreshed() {
        // Check if the current channel's preset status has changed.
        mRadioDisplayController.setChannelIsPreset(
                mRadioStorage.isPreset(mCurrentChannelNumber, mCurrentRadioBand));
    }

    @Override
    public void onPreScannedChannelChange(int radioBand) {
        // If pre-scanned channels have changed for the current radio band, then refresh the list
        // that is currently being displayed.
        if (radioBand == mCurrentRadioBand && mChannelLoader != null) {
            mChannelLoader.forceLoad();
        }
    }

    @Override
    public Loader<List<RadioStation>> onCreateLoader(int id, Bundle args) {
        // Only one loader, so no need to check for id.
        mChannelLoader = new PreScannedChannelLoader(mActivity /* context */);
        mChannelLoader.setCurrentRadioBand(mCurrentRadioBand);

        return mChannelLoader;
    }

    @Override
    public void onLoadFinished(Loader<List<RadioStation>> loader,
            List<RadioStation> preScannedStations) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            int size = preScannedStations == null ? 0 : preScannedStations.size();
            Log.d(TAG, "onLoadFinished(); number of pre-scanned stations: " + size);
        }

        if (Log.isLoggable(TAG, Log.VERBOSE) && preScannedStations != null) {
            for (RadioStation station : preScannedStations) {
                Log.v(TAG, "station: " + station.toString());
            }
        }

        mAdapter.setStations(preScannedStations);

        int position = mAdapter.setStartingStation(mCurrentChannelNumber, mCurrentRadioBand);
        mRadioDisplayController.setCurrentStationInList(position);
    }

    @Override
    public void onLoaderReset(Loader<List<RadioStation>> loader) {}

    /**
     * Value animator for AM values.
     */
    private ValueAnimator.AnimatorUpdateListener mAmAnimatorListener =
            new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    mRadioDisplayController.setChannelNumber(
                            RadioChannelFormatter.AM_FORMATTER.format(
                                    animation.getAnimatedValue()));
                }
            };

    /**
     * Value animator for FM values.
     */
    private ValueAnimator.AnimatorUpdateListener mFmAnimatorListener =
            new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    mRadioDisplayController.setChannelNumber(
                            RadioChannelFormatter.FM_FORMATTER.format(
                                    animation.getAnimatedValue()));
                }
            };

    private final IRadioCallback.Stub mCallback = new IRadioCallback.Stub() {
        @Override
        public void onRadioStationChanged(RadioStation station) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioStationChanged: " + station);
            }

            if (station == null) {
                return;
            }

            if (mCurrentChannelNumber != station.getChannelNumber()) {
                setRadioChannel(station.getChannelNumber());
            }

            onRadioMetadataChanged(station.getRds());

            // Notify that the current radio station has changed.
            if (mStationChangeListener != null) {
                try {
                    mStationChangeListener.onRadioStationChanged(
                            mRadioManager.getCurrentRadioStation());
                } catch (RemoteException e) {
                    Log.e(TAG, "tuneToRadioChannel(); remote exception: " + e.getMessage());
                }
            }
        }

        /**
         * Updates radio information based on the given {@link RadioRds}.
         */
        @Override
        public void onRadioMetadataChanged(RadioRds radioRds) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onMetadataChanged(); metadata: " + radioRds);
            }

            clearMetadataDisplay();

            if (radioRds == null) {
                return;
            }

            mCurrentRds = radioRds;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "mCurrentRds: " + mCurrentRds);
            }

            String programService = radioRds.getProgramService();
            String artistMetadata = radioRds.getSongArtist();

            mRadioDisplayController.setCurrentSongArtistOrStation(
                    TextUtils.isEmpty(artistMetadata) ? programService : artistMetadata);
            mRadioDisplayController.setCurrentSongTitle(radioRds.getSongTitle());

            // Since new metadata exists, update the preset that is stored in the database if
            // it exists.
            if (TextUtils.isEmpty(programService)) {
                return;
            }

            RadioStation station = new RadioStation(mCurrentChannelNumber, 0 /* subchannel */,
                    mCurrentRadioBand, radioRds);
            boolean isPreset = mRadioStorage.isPreset(station);

            if (isPreset) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Current channel is a preset; updating metadata in the database.");
                }

                mRadioStorage.storePreset(station);
            }
        }

        @Override
        public void onRadioBandChanged(int radioBand) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioBandChanged: " + radioBand);
            }

            setCurrentRadioBand(radioBand);
            updateAmFmDisplayState();

            // Check that the radio channel is being correctly formatted.
            setRadioChannel(mCurrentChannelNumber);
        }

        @Override
        public void onRadioMuteChanged(boolean isMuted) {
            mRadioDisplayController.setPlayPauseButtonState(isMuted);
        }

        @Override
        public void onError(int status) {
            Log.e(TAG, "Radio callback error with status: " + status);
            close();
        }
    };

    private final View.OnClickListener mBackwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRadioManager == null) {
                return;
            }

            clearMetadataDisplay();

            if (!mHasDualTuners) {
                try {
                    mRadioManager.seekBackward();
                } catch (RemoteException e) {
                    Log.e(TAG, "backwardSeek(); remote exception: " + e.getMessage());
                }
                return;
            }

            RadioStation prevStation = mAdapter.getPrevStation();

            if (prevStation != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Seek backwards to station: " + prevStation);
                }

                // Tune to the previous station, and then update the UI to reflect that tune.
                try {
                    mRadioManager.tune(prevStation);
                } catch (RemoteException e) {
                    Log.e(TAG, "backwardSeek(); remote exception: " + e.getMessage());
                }

                int position = mAdapter.getCurrentPosition();
                mRadioDisplayController.setCurrentStationInList(position);
            }
        }
    };

    private final View.OnClickListener mForwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRadioManager == null) {
                return;
            }

            clearMetadataDisplay();

            if (!mHasDualTuners) {
                try {
                    mRadioManager.seekForward();
                } catch (RemoteException e) {
                    Log.e(TAG, "forwardSeek(); remote exception: " + e.getMessage());
                }
                return;
            }

            RadioStation nextStation = mAdapter.getNextStation();

            if (nextStation != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Seek forward to station: " + nextStation);
                }

                // Tune to the next station, and then update the UI to reflect that tune.
                try {
                    mRadioManager.tune(nextStation);
                } catch (RemoteException e) {
                    Log.e(TAG, "forwardSeek(); remote exception: " + e.getMessage());
                }

                int position = mAdapter.getCurrentPosition();
                mRadioDisplayController.setCurrentStationInList(position);
            }
        }
    };

    /**
     * Click listener for the play/pause button. Currently, all this does is mute/unmute the radio
     * because the {@link RadioManager} does not support the ability to pause/start again.
     */
    private final View.OnClickListener mPlayPauseClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRadioManager == null) {
                return;
            }

            try {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Play button clicked. Currently muted: " + mRadioManager.isMuted());
                }

                if (mRadioManager.isMuted()) {
                    mRadioManager.unMute();
                } else {
                    mRadioManager.mute();
                }

                boolean isMuted = mRadioManager.isMuted();

                mUserHasMuted = isMuted;
                mRadioDisplayController.setPlayPauseButtonState(isMuted);
            } catch (RemoteException e) {
                Log.e(TAG, "playPauseClickListener(); remote exception: " + e.getMessage());
            }
        }
    };

    private final View.OnClickListener mPresetButtonClickListener = new View.OnClickListener() {
        // TODO: Maybe add a check to send a store/remove preset event after a delay so that
        // there aren't multiple writes if the user presses the button quickly.
        @Override
        public void onClick(View v) {
            if (mCurrentChannelNumber == RadioStorage.INVALID_RADIO_CHANNEL) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Attempting to store invalid radio station as a preset. Ignoring");
                }

                return;
            }

            RadioStation station = new RadioStation(mCurrentChannelNumber, 0 /* subchannel */,
                    mCurrentRadioBand, mCurrentRds);
            boolean isPreset = mRadioStorage.isPreset(station);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Toggling preset for " + station
                        + "\n\tIs currently a preset: " + isPreset);
            }

            if (isPreset) {
                mRadioStorage.removePreset(station);
            } else {
                mRadioStorage.storePreset(station);
            }

            // Update the UI immediately. If the preset failed for some reason, the RadioStorage
            // will notify us and UI update will happen then.
            mRadioDisplayController.setChannelIsPreset(!isPreset);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            mRadioManager = ((IRadioManager) binder);

            try {
                if (mRadioManager == null || !mRadioManager.isInitialized()) {
                    mRadioDisplayController.setEnabled(false);

                    if (mRadioErrorDisplay != null) {
                        mRadioErrorDisplay.setVisibility(View.VISIBLE);
                    }

                    return;
                }

                mRadioDisplayController.setEnabled(true);

                if (mRadioErrorDisplay != null) {
                    mRadioErrorDisplay.setVisibility(View.GONE);
                }

                mHasDualTuners = mRadioManager.hasDualTuners();

                if (mHasDualTuners) {
                    initializeDualTunerController();
                } else {
                    mRadioDisplayController.setSingleChannelDisplay(mRadioBackground);
                }

                mRadioManager.addRadioTunerCallback(mCallback);

                int radioBand = mRadioStorage.getStoredRadioBand();

                // Upon successful connection, open the radio.
                openRadioBand(radioBand);
                maybeTuneToStoredRadioChannel();

                if (mStationChangeListener != null) {
                    mStationChangeListener.onRadioStationChanged(
                            mRadioManager.getCurrentRadioStation());
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected(); remote exception: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mRadioManager = null;
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mBackgroundColorUpdater =
            animator -> {
                int backgroundColor = (int) animator.getAnimatedValue();
                setBackgroundColor(backgroundColor);
            };
}
