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

package com.android.car.stream.radio;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.android.car.radio.service.IRadioCallback;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.service.RadioRds;
import com.android.car.radio.service.RadioStation;
import com.android.car.stream.R;
import com.android.car.stream.StreamProducer;

/**
 * A {@link StreamProducer} that will connect to the {@link IRadioManager} and produce cards
 * corresponding to the currently playing radio station.
 */
public class RadioStreamProducer extends StreamProducer {
    private static final String TAG = "RadioStreamProducer";

    /**
     * The amount of time to wait before re-trying to connect to {@link IRadioManager}.
     */
    private static final int SERVICE_CONNECTION_RETRY_DELAY_MS = 5000;

    // Radio actions that are used by broadcasts that occur on interaction with the radio card.
    static final int ACTION_SEEK_FORWARD = 1;
    static final int ACTION_SEEK_BACKWARD = 2;
    static final int ACTION_PAUSE = 3;
    static final int ACTION_PLAY = 4;
    static final int ACTION_STOP = 5;

    /**
     * The action in an {@link Intent} that is meant to effect certain radio actions.
     */
    static final String RADIO_INTENT_ACTION =
            "com.android.car.stream.radio.RADIO_INTENT_ACTION";

    /**
     * The extra within the {@link Intent} that points to the specific action to be taken on the
     * radio.
     */
    static final String RADIO_ACTION_EXTRA = "radio_action_extra";

    private final Handler mHandler = new Handler();

    private IRadioManager mRadioManager;
    private RadioActionReceiver mReceiver;
    private final RadioConverter mConverter;

    /**
     * The number of times that this stream producer has attempted to reconnect to the
     * {@link IRadioManager} after a failure to bind.
     */
    private int mConnectionRetryCount;

    private int mCurrentChannelNumber;
    private int mCurrentBand;

    public RadioStreamProducer(Context context) {
        super(context);
        mConverter = new RadioConverter(context);
    }

    @Override
    public void start() {
        super.start();

        mReceiver = new RadioActionReceiver();
        mContext.registerReceiver(mReceiver, new IntentFilter(RADIO_INTENT_ACTION));

        bindRadioService();
    }

    @Override
    public void stop() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stop()");
        }

        mHandler.removeCallbacks(mServiceConnectionRetry);

        mContext.unregisterReceiver(mReceiver);
        mReceiver = null;

        mContext.unbindService(mServiceConnection);
        super.stop();
    }

    /**
     * Binds to the RadioService and returns {@code true} if the connection was successful.
     */
    private boolean bindRadioService() {
        Intent radioService = new Intent();
        radioService.setComponent(new ComponentName(
                mContext.getString(R.string.car_radio_component_package),
                mContext.getString(R.string.car_radio_component_service)));

        boolean bound =
                !mContext.bindService(radioService, mServiceConnection, Context.BIND_AUTO_CREATE);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "bindRadioService(). Connected to radio service: " + bound);
        }

        return bound;
    }

    /**
     * A {@link BroadcastReceiver} that listens for Intents that have the action
     * {@link #RADIO_INTENT_ACTION} and corresponding parses the action event within it to effect
     * radio playback.
     */
    private class RadioActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mRadioManager == null || !RADIO_INTENT_ACTION.equals(intent.getAction())) {
                return;
            }

            int radioAction = intent.getIntExtra(RADIO_ACTION_EXTRA, -1);
            if (radioAction == -1) {
                return;
            }

            switch (radioAction) {
                case ACTION_SEEK_FORWARD:
                    try {
                        mRadioManager.seekForward();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Seek forward exception: " + e.getMessage());
                    }
                    break;

                case ACTION_SEEK_BACKWARD:
                    try {
                        mRadioManager.seekBackward();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Seek backward exception: " + e.getMessage());
                    }
                    break;

                case ACTION_PLAY:
                    try {
                        mRadioManager.unMute();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Radio play exception: " + e.getMessage());
                    }
                    break;

                case ACTION_STOP:
                case ACTION_PAUSE:
                    try {
                        mRadioManager.mute();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Radio pause exception: " + e.getMessage());
                    }
                    break;

                default:
                    // Do nothing.
            }
        }
    }

    /**
     * A {@link IRadioCallback} that will be notified of various state changes in the radio station.
     * Upon these changes, it will push a new {@link com.android.car.stream.StreamCard} to the
     * Stream service.
     */
    private final IRadioCallback.Stub mCallback = new IRadioCallback.Stub() {
        @Override
        public void onRadioStationChanged(RadioStation station) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioStationChanged: " + station);
            }

            mCurrentBand = station.getRadioBand();
            mCurrentChannelNumber = station.getChannelNumber();

            if (mRadioManager == null) {
                return;
            }

            try {
                boolean isPlaying = !mRadioManager.isMuted();
                postCard(mConverter.convert(station, isPlaying));
            } catch (RemoteException e) {
                Log.e(TAG, "Post radio station changed error: " + e.getMessage());
            }
        }

        @Override
        public void onRadioMetadataChanged(RadioRds rds) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioMetadataChanged: " + rds);
            }

            // Ignore metadata changes because this will overwhelm the notifications. Instead,
            // Only display the metadata that is retrieved in onRadioStationChanged().
        }

        @Override
        public void onRadioBandChanged(int radioBand) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioBandChanged: " + radioBand);
            }

            if (mRadioManager == null) {
                return;
            }

            try {
                RadioStation station = new RadioStation(mCurrentChannelNumber,
                        0 /* subChannelNumber */, mCurrentBand, null /* rds */);
                boolean isPlaying = !mRadioManager.isMuted();

                postCard(mConverter.convert(station, isPlaying));
            } catch (RemoteException e) {
                Log.e(TAG, "Post radio station changed error: " + e.getMessage());
            }
        }

        @Override
        public void onRadioMuteChanged(boolean isMuted) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onRadioMuteChanged(): " + isMuted);
            }

            RadioStation station = new RadioStation(mCurrentChannelNumber,
                    0 /* subChannelNumber */, mCurrentBand, null /* rds */);

            postCard(mConverter.convert(station, !isMuted));
        }

        @Override
        public void onError(int status) {
            Log.e(TAG, "Radio error: " + status);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mConnectionRetryCount = 0;

            mRadioManager = IRadioManager.Stub.asInterface(binder);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onSeviceConnected(): " + mRadioManager);
            }

            try {
                mRadioManager.addRadioTunerCallback(mCallback);

                if (mRadioManager.isInitialized() && mRadioManager.hasFocus()) {
                    boolean isPlaying = !mRadioManager.isMuted();
                    postCard(mConverter.convert(mRadioManager.getCurrentRadioStation(), isPlaying));
                }
            } catch (RemoteException e) {
                Log.e(TAG, "addRadioTunerCallback() error: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceDisconnected(): " + name);
            }
            mRadioManager = null;

            // If the service has been disconnected, attempt to reconnect.
            mHandler.removeCallbacks(mServiceConnectionRetry);
            mHandler.postDelayed(mServiceConnectionRetry, SERVICE_CONNECTION_RETRY_DELAY_MS);
        }
    };

    /**
     * A {@link Runnable} that is responsible for attempting to reconnect to {@link IRadioManager}.
     */
    private Runnable mServiceConnectionRetry = new Runnable() {
        @Override
        public void run() {
            if (mRadioManager != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "RadioService rebound by framework, no need to bind again");
                }
                return;
            }

            mConnectionRetryCount++;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Rebinding disconnected RadioService, retry count: "
                        + mConnectionRetryCount);
            }

            if (!bindRadioService()) {
                mHandler.postDelayed(mServiceConnectionRetry,
                        mConnectionRetryCount * SERVICE_CONNECTION_RETRY_DELAY_MS);
            }
        }
    };
}
