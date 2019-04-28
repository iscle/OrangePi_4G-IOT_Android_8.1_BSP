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
package com.android.car.radio.demo;

import android.car.hardware.radio.CarRadioManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.radio.RadioManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.support.car.Car;
import android.support.car.CarNotConnectedException;
import android.support.car.CarConnectionCallback;
import android.support.car.media.CarAudioManager;
import android.util.Log;
import com.android.car.radio.service.IRadioCallback;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A demo {@link IRadiomanager} that has a fixed set of AM and FM stations.
 */
public class RadioDemo implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "RadioDemo";

    /**
     * The property name to enable demo mode.
     */
    public static final String DEMO_MODE_PROPERTY = "com.android.car.radio.demo";

    /**
     * The property name to enable the radio in demo mode with dual tuners.
     */
    public static final String DUAL_DEMO_MODE_PROPERTY = "com.android.car.radio.demo.dual";

    private static RadioDemo sInstance;
    private List<IRadioCallback> mCallbacks = new ArrayList<>();

    private List<RadioStation> mCurrentStations = new ArrayList<>();
    private int mCurrentRadioBand = RadioManager.BAND_FM;

    private Car mCarApi;
    private CarAudioManager mCarAudioManager;
    private AudioAttributes mRadioAudioAttributes;

    private boolean mHasAudioFocus;

    private int mCurrentIndex;
    private boolean mIsMuted;

    private RadioDemo(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mCarApi = Car.createCar(context, mCarConnectionCallback);
            mCarApi.connect();
        }
    }

    /**
     * Returns a mock {@link IRadioManager} to use for demo purposes. The returned class will have
     * a fixed list of AM and FM changegs and support all the IRadioManager's functionality.
     */
    public IRadioManager.Stub createDemoManager() {
        return new IRadioManager.Stub() {
            @Override
            public void tune(RadioStation station) throws RemoteException {
                if (station == null || !requestAudioFocus()) {
                    return;
                }

                if (station.getRadioBand() != mCurrentRadioBand) {
                    switchRadioBand(station.getRadioBand());
                }

                boolean found = false;

                for (int i = 0, size = mCurrentStations.size(); i < size; i++) {
                    RadioStation storedStation = mCurrentStations.get(i);

                    if (storedStation.equals(station)) {
                        found = true;
                        mCurrentIndex = i;
                        break;
                    }
                }

                // If not found, then insert it into the list, sorted by the channel.
                if (!found) {
                    int indexToInsert = 0;

                    for (int i = 0, size = mCurrentStations.size(); i < size; i++) {
                        RadioStation storedStation = mCurrentStations.get(i);

                        if (station.getChannelNumber() >= storedStation.getChannelNumber()) {
                            indexToInsert = i + 1;
                            break;
                        }
                    }

                    RadioStation stationToInsert = new RadioStation(station.getChannelNumber(),
                            0 /* subChannel */, station.getRadioBand(), null /* rds */);
                    mCurrentStations.add(indexToInsert, stationToInsert);

                    mCurrentIndex = indexToInsert;
                }

                notifyCallbacks(station);
            }

            @Override
            public void seekForward() throws RemoteException {
                if (!requestAudioFocus()) {
                    return;
                }

                if (++mCurrentIndex >= mCurrentStations.size()) {
                    mCurrentIndex = 0;
                }

                notifyCallbacks(mCurrentStations.get(mCurrentIndex));
            }

            @Override
            public void seekBackward() throws RemoteException {
                if (!requestAudioFocus()) {
                    return;
                }

                if (--mCurrentIndex < 0){
                    mCurrentIndex = mCurrentStations.size() - 1;
                }

                notifyCallbacks(mCurrentStations.get(mCurrentIndex));
            }

            @Override
            public boolean mute() throws RemoteException {
                mIsMuted = true;
                notifyCallbacksMuteChanged(mIsMuted);
                return mIsMuted;
            }

            @Override
            public boolean unMute() throws RemoteException {
                requestAudioFocus();

                if (mHasAudioFocus) {
                    mIsMuted = false;
                }

                notifyCallbacksMuteChanged(mIsMuted);
                return !mIsMuted;
            }

            @Override
            public boolean isMuted() throws RemoteException {
                return mIsMuted;
            }

            @Override
            public int openRadioBand(int radioBand) throws RemoteException {
                if (!requestAudioFocus()) {
                    return RadioManager.STATUS_ERROR;
                }

                switchRadioBand(radioBand);
                notifyCallbacks(radioBand);
                return RadioManager.STATUS_OK;
            }

            @Override
            public void addRadioTunerCallback(IRadioCallback callback) throws RemoteException {
                mCallbacks.add(callback);
            }

            @Override
            public void removeRadioTunerCallback(IRadioCallback callback) throws RemoteException {
                mCallbacks.remove(callback);
            }

            @Override
            public RadioStation getCurrentRadioStation() throws RemoteException {
                return mCurrentStations.get(mCurrentIndex);
            }

            @Override
            public boolean isInitialized() throws RemoteException {
                return true;
            }

            @Override
            public boolean hasFocus() {
                return mHasAudioFocus;
            }

            @Override
            public boolean hasDualTuners() throws RemoteException {
                return SystemProperties.getBoolean(RadioDemo.DUAL_DEMO_MODE_PROPERTY, false);
            }
        };
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "focus change: " + focusChange);
        }

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                mHasAudioFocus = true;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mHasAudioFocus = false;
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                abandonAudioFocus();
                break;

            default:
                // Do nothing for all other cases.
        }
    }

    /**
     * Requests audio focus for the current application.
     *
     * @return {@code true} if the request succeeded.
     */
    private boolean requestAudioFocus() {
        if (mCarAudioManager == null) {
            return false;
        }

        int status = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        try {
            status = mCarAudioManager.requestAudioFocus(this, mRadioAudioAttributes,
                    AudioManager.AUDIOFOCUS_GAIN, 0);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "requestAudioFocus() failed", e);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "requestAudioFocus status: " + status);
        }

        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mHasAudioFocus = true;
        }

        return status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    /**
     * Abandons audio focus for the current application.
     *
     * @return {@code true} if the request succeeded.
     */
    private void abandonAudioFocus() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "abandonAudioFocus()");
        }

        if (mCarAudioManager == null) {
            return;
        }

        mCarAudioManager.abandonAudioFocus(this, mRadioAudioAttributes);
    }

    /**
     * {@link CarConnectionCallback} that retrieves the {@link CarRadioManager}.
     */
    private final CarConnectionCallback mCarConnectionCallback =
            new CarConnectionCallback() {
                @Override
                public void onConnected(Car car) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Car service connected.");
                    }
                    try {
                        // The CarAudioManager only needs to be retrieved once.
                        if (mCarAudioManager == null) {
                            mCarAudioManager = (CarAudioManager) mCarApi.getCarManager(
                                    android.car.Car.AUDIO_SERVICE);

                            mRadioAudioAttributes = mCarAudioManager.getAudioAttributesForCarUsage(
                                    CarAudioManager.CAR_AUDIO_USAGE_RADIO);
                        }
                    } catch (CarNotConnectedException e) {
                        //TODO finish
                        Log.e(TAG, "Car not connected");
                    }
                }

                @Override
                public void onDisconnected(Car car) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Car service disconnected.");
                    }
                }
            };

    /**
     * Switches to the corresponding radio band. This will update the list of current stations
     * as well as notify any callbacks.
     */
    private void switchRadioBand(int radioBand) {
        switch (radioBand) {
            case RadioManager.BAND_AM:
                mCurrentStations = DemoRadioStations.getAmStations();
                break;
            case RadioManager.BAND_FM:
                mCurrentStations = DemoRadioStations.getFmStations();
                break;
            default:
                mCurrentStations = new ArrayList<>();
        }

        mCurrentRadioBand = radioBand;
        mCurrentIndex = 0;

        notifyCallbacks(mCurrentRadioBand);
        notifyCallbacks(mCurrentStations.get(mCurrentIndex));
    }

    /**
     * Notifies any {@link IRadioCallback} that the mute state of the radio has changed.
     */
    private void notifyCallbacksMuteChanged(boolean isMuted) {
        for (IRadioCallback callback : mCallbacks) {
            try {
                callback.onRadioMuteChanged(isMuted);
            } catch (RemoteException e) {
                // Ignore.
            }
        }
    }

    /**
     * Notifies any {@link IRadioCallback}s that the radio band has changed.
     */
    private void notifyCallbacks(int radioBand) {
        for (IRadioCallback callback : mCallbacks) {
            try {
                callback.onRadioBandChanged(radioBand);
            } catch (RemoteException e) {
                // Ignore.
            }
        }
    }

    /**
     * Notifies any {@link IRadioCallback}s that the radio station has been changed to the given
     * {@link RadioStation}.
     */
    private void notifyCallbacks(RadioStation station) {
        for (IRadioCallback callback : mCallbacks) {
            try {
                callback.onRadioStationChanged(station);
            } catch (RemoteException e) {
                // Ignore.
            }
        }
    }

    public static RadioDemo getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RadioDemo(context);
        }

        return sInstance;
    }
}
