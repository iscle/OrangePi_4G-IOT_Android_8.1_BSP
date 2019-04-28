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
import android.content.SharedPreferences;
import android.hardware.radio.RadioManager;
import android.os.AsyncTask;
import android.os.SystemProperties;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;
import com.android.car.radio.service.RadioStation;
import com.android.car.radio.demo.DemoRadioStations;
import com.android.car.radio.demo.RadioDemo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that manages persistent storage of various radio options.
 */
public class RadioStorage {
    private static final String TAG = "Em.RadioStorage";
    private static final String PREF_NAME = "com.android.car.radio.RadioStorage";

    // Keys used for storage in the SharedPreferences.
    private static final String PREF_KEY_RADIO_BAND = "radio_band";
    private static final String PREF_KEY_RADIO_CHANNEL_AM = "radio_channel_am";
    private static final String PREF_KEY_RADIO_CHANNEL_FM = "radio_channel_fm";

    public static final int INVALID_RADIO_CHANNEL = -1;
    public static final int INVALID_RADIO_BAND = -1;

    private static SharedPreferences sSharedPref;
    private static RadioStorage sInstance;
    private static RadioDatabase sRadioDatabase;

    /**
     * Listener that will be called when something in the radio storage changes.
     */
    public interface PresetsChangeListener {
        /**
         * Called when {@link #refreshPresets()} has completed.
         */
        void onPresetsRefreshed();
    }

    /**
     * Listener that will be called when something in the pre-scanned channels has changed.
     */
    public interface PreScannedChannelChangeListener {
        /**
         * Notifies that the pre-scanned channels for the given radio band has changed.
         *
         * @param radioBand One of the band values in {@link RadioManager}.
         */
        void onPreScannedChannelChange(int radioBand);
    }

    private Set<PresetsChangeListener> mPresetListeners = new HashSet<>();

    /**
     * Set of listeners that will be notified whenever pre-scanned channels have changed.
     *
     * <p>Note that this set is not initialized because pre-scanned channels are only needed if
     * dual-tuners exist in the current radio. Thus, this set is created conditionally.
     */
    private Set<PreScannedChannelChangeListener> mPreScannedListeners;

    private List<RadioStation> mPresets = new ArrayList<>();

    private RadioStorage(Context context) {
        if (sSharedPref == null) {
            sSharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        if (sRadioDatabase == null) {
            sRadioDatabase = new RadioDatabase(context);
        }
    }

    public static RadioStorage getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RadioStorage(context.getApplicationContext());

            // When the RadioStorage is first created, load the list of radio presets.
            sInstance.refreshPresets();
        }

        return sInstance;
    }

    /**
     * Registers the given {@link PresetsChangeListener} to be notified when any radio preset state
     * has changed.
     */
    public void addPresetsChangeListener(PresetsChangeListener listener) {
        mPresetListeners.add(listener);
    }

    /**
     * Unregisters the given {@link PresetsChangeListener}.
     */
    public void removePresetsChangeListener(PresetsChangeListener listener) {
        mPresetListeners.remove(listener);
    }

    /**
     * Registers the given {@link PreScannedChannelChangeListener} to be notified of changes to
     * pre-scanned channels.
     */
    public void addPreScannedChannelChangeListener(PreScannedChannelChangeListener listener) {
        if (mPreScannedListeners == null) {
            mPreScannedListeners = new HashSet<>();
        }

        mPreScannedListeners.add(listener);
    }

    /**
     * Unregisters the given {@link PreScannedChannelChangeListener}.
     */
    public void removePreScannedChannelChangeListener(PreScannedChannelChangeListener listener) {
        if (mPreScannedListeners == null) {
            return;
        }

        mPreScannedListeners.remove(listener);
    }

    /**
     * Requests a load of all currently stored presets. This operation runs asynchronously. When
     * the presets have been loaded, any registered {@link PresetsChangeListener}s are
     * notified via the {@link PresetsChangeListener#onPresetsRefreshed()} method.
     */
    private void refreshPresets() {
        new GetAllPresetsAsyncTask().execute();
    }

    /**
     * Returns all currently loaded presets. If there are no stored presets, this method will
     * return an empty {@link List}.
     *
     * <p>Register as a {@link PresetsChangeListener} to be notified of any changes in the
     * preset list.
     */
    public List<RadioStation> getPresets() {
        return mPresets;
    }

    /**
     * Convenience method for checking if a specific channel is a preset. This method will assume
     * the subchannel is 0.
     *
     * @see #isPreset(RadioStation)
     * @return {@code true} if the channel is a user saved preset.
     */
    public boolean isPreset(int channel, int radioBand) {
        return isPreset(new RadioStation(channel, 0 /* subchannel */, radioBand, null /* rds */));
    }

    /**
     * Returns {@code true} if the given {@link RadioStation} is a user saved preset.
     */
    public boolean isPreset(RadioStation station) {
        if (station == null) {
            return false;
        }

        // Just iterate through the list and match the station. If we anticipate this list growing
        // large, might have to change it to some sort of Set.
        for (RadioStation preset : mPresets) {
            if (preset.equals(station)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Stores that given {@link RadioStation} as a preset. This operation will override any
     * previously stored preset that matches the given preset.
     *
     * <p>Upon a successful store, the presets list will be refreshed via a call to
     * {@link #refreshPresets()}.
     *
     * @see #refreshPresets()
     */
    public void storePreset(RadioStation preset) {
        if (preset == null) {
            return;
        }

        new StorePresetAsyncTask().execute(preset);
    }

    /**
     * Removes the given {@link RadioStation} as a preset.
     *
     * <p>Upon a successful removal, the presets list will be refreshed via a call to
     * {@link #refreshPresets()}.
     *
     * @see #refreshPresets()
     */
    public void removePreset(RadioStation preset) {
        if (preset == null) {
            return;
        }

        new RemovePresetAsyncTask().execute(preset);
    }

    /**
     * Returns the stored radio band that was set in {@link #storeRadioBand(int)}. If a radio band
     * has not previously been stored, then {@link RadioManager#BAND_FM} is returned.
     *
     * @return One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
     * {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
     */
    public int getStoredRadioBand() {
        // No need to verify that the returned value is one of AM_BAND or FM_BAND because this is
        // done in storeRadioBand(int).
        return sSharedPref.getInt(PREF_KEY_RADIO_BAND, RadioManager.BAND_FM);
    }

    /**
     * Stores a radio band for later retrieval via {@link #getStoredRadioBand()}.
     */
    public void storeRadioBand(int radioBand) {
        // Ensure that an incorrect radio band is not stored. Currently only FM and AM supported.
        if (radioBand != RadioManager.BAND_FM && radioBand != RadioManager.BAND_AM) {
            return;
        }

        sSharedPref.edit().putInt(PREF_KEY_RADIO_BAND, radioBand).apply();
    }

    /**
     * Returns the stored radio channel that was set in {@link #storeRadioChannel(int, int)}. If a
     * radio channel for the given band has not been previously stored, then
     * {@link #INVALID_RADIO_CHANNEL} is returned.
     *
     * @param band One of the BAND_* values from {@link RadioManager}. For example,
     *             {@link RadioManager#BAND_AM}.
     */
    public int getStoredRadioChannel(int band) {
        switch (band) {
            case RadioManager.BAND_AM:
                return sSharedPref.getInt(PREF_KEY_RADIO_CHANNEL_AM, INVALID_RADIO_CHANNEL);

            case RadioManager.BAND_FM:
                return sSharedPref.getInt(PREF_KEY_RADIO_CHANNEL_FM, INVALID_RADIO_CHANNEL);

            default:
                return INVALID_RADIO_CHANNEL;
        }
    }

    /**
     * Stores a radio channel (i.e. the radio frequency) for a particular band so it can be later
     * retrieved via {@link #getStoredRadioChannel(int band)}.
     */
    public void storeRadioChannel(int band, int channel) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("storeRadioChannel(); band: %s, channel %s", band, channel));
        }

        if (channel <= 0) {
            return;
        }

        switch (band) {
            case RadioManager.BAND_AM:
                sSharedPref.edit().putInt(PREF_KEY_RADIO_CHANNEL_AM, channel).apply();
                break;

            case RadioManager.BAND_FM:
                sSharedPref.edit().putInt(PREF_KEY_RADIO_CHANNEL_FM, channel).apply();
                break;

            default:
                Log.w(TAG, "Attempting to store channel for invalid band: " + band);
        }
    }

    /**
     * Stores the list of {@link RadioStation}s as the pre-scanned stations for the given radio
     * band.
     *
     * @param radioBand One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
     * {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
     */
    public void storePreScannedStations(int radioBand, List<RadioStation> stations) {
        if (stations == null) {
            return;
        }

        // Converting to an array rather than passing a List to the execute to avoid any potential
        // heap pollution via AsyncTask's varargs.
        new StorePreScannedAsyncTask(radioBand).execute(
                stations.toArray(new RadioStation[stations.size()]));
    }

    /**
     * Returns the list of pre-scanned radio channels for the given band.
     */
    @NonNull
    @WorkerThread
    public List<RadioStation> getPreScannedStationsForBand(int radioBand) {
        if (SystemProperties.getBoolean(RadioDemo.DEMO_MODE_PROPERTY, false)) {
            switch (radioBand) {
                case RadioManager.BAND_AM:
                    return DemoRadioStations.getAmStations();

                case RadioManager.BAND_FM:
                default:
                    return DemoRadioStations.getFmStations();

            }
        }

        return sRadioDatabase.getAllPreScannedStationsForBand(radioBand);
    }

    /**
     * Calls {@link PresetsChangeListener#onPresetsRefreshed()} for all registered
     * {@link PresetsChangeListener}s.
     */
    private void notifyPresetsListeners() {
        for (PresetsChangeListener listener : mPresetListeners) {
            listener.onPresetsRefreshed();
        }
    }

    /**
     * Calls {@link PreScannedChannelChangeListener#onPreScannedChannelChange(int)} for all
     * registered {@link PreScannedChannelChangeListener}s.
     */
    private void notifyPreScannedListeners(int radioBand) {
        if (mPreScannedListeners == null) {
            return;
        }

        for (PreScannedChannelChangeListener listener : mPreScannedListeners) {
            listener.onPreScannedChannelChange(radioBand);
        }
    }

    /**
     * {@link AsyncTask} that will fetch all stored radio presets.
     */
    private class GetAllPresetsAsyncTask extends AsyncTask<Void, Void, Void> {
        private static final String TAG = "Em.GetAllPresetsAT";

        @Override
        protected Void doInBackground(Void... voids) {
            mPresets = sRadioDatabase.getAllPresets();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Loaded presets: " + mPresets);
            }

            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            notifyPresetsListeners();
        }
    }

    /**
     * {@link AsyncTask} that will store a single {@link RadioStation} that is passed to its
     * {@link AsyncTask#execute(Object[])}.
     */
    private class StorePresetAsyncTask extends AsyncTask<RadioStation, Void, Boolean> {
        private static final String TAG = "Em.StorePresetAT";

        @Override
        protected Boolean doInBackground(RadioStation... radioStations) {
            RadioStation presetToStore = radioStations[0];
            boolean result = sRadioDatabase.insertPreset(presetToStore);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Store preset success: " + result);
            }

            if (result) {
                // Refresh the presets list.
                mPresets = sRadioDatabase.getAllPresets();
            }

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                notifyPresetsListeners();
            }
        }
    }

    /**
     * {@link AsyncTask} that will remove a single {@link RadioStation} that is passed to its
     * {@link AsyncTask#execute(Object[])}.
     */
    private class RemovePresetAsyncTask extends AsyncTask<RadioStation, Void, Boolean> {
        private static final String TAG = "Em.RemovePresetAT";

        @Override
        protected Boolean doInBackground(RadioStation... radioStations) {
            RadioStation presetToStore = radioStations[0];
            boolean result = sRadioDatabase.deletePreset(presetToStore);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Remove preset success: " + result);
            }

            if (result) {
                // Refresh the presets list.
                mPresets = sRadioDatabase.getAllPresets();
            }

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                notifyPresetsListeners();
            }
        }
    }

    /**
     * {@link AsyncTask} that will store a list of pre-scanned {@link RadioStation}s that is passed
     * to its {@link AsyncTask#execute(Object[])}.
     */
    private class StorePreScannedAsyncTask extends AsyncTask<RadioStation, Void, Boolean> {
        private static final String TAG = "Em.StorePreScannedAT";
        private final int mRadioBand;

        public StorePreScannedAsyncTask(int radioBand) {
            mRadioBand = radioBand;
        }

        @Override
        protected Boolean doInBackground(RadioStation... radioStations) {
            boolean result = sRadioDatabase.insertPreScannedStations(mRadioBand,
                    Arrays.asList(radioStations));

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Store pre-scanned stations success: " + result);
            }

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                notifyPreScannedListeners(mRadioBand);
            }
        }
    }
}
