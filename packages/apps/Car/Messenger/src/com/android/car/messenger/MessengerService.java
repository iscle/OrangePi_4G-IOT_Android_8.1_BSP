/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.messenger;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Background started service that hosts messaging components.
 * <p>
 * The MapConnector manages connecting to the BT MAP service and the MapMessageMonitor listens for
 * new incoming messages and publishes notifications. Actions in the notifications trigger command
 * intents to this service (e.g. auto-reply, play message).
 * <p>
 * This service and its helper components run entirely in the main thread.
 */
public class MessengerService extends Service {
    static final String TAG = "MessengerService";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // Used to start this service at boot-complete. Takes no arguments.
    static final String ACTION_START = "com.android.car.messenger.ACTION_START";
    // Used to auto-reply to messages from a sender (invoked from Notification).
    static final String ACTION_AUTO_REPLY = "com.android.car.messenger.ACTION_AUTO_REPLY";
    // Used to play-out messages from a sender (invoked from Notification).
    static final String ACTION_PLAY_MESSAGES = "com.android.car.messenger.ACTION_PLAY_MESSAGES";
    // Used to stop further audio notifications from the conversation.
    static final String ACTION_MUTE_CONVERSATION =
            "com.android.car.messenger.ACTION_MUTE_CONVERSATION";
    // Used to resume further audio notifications from the conversation.
    static final String ACTION_UNMUTE_CONVERSATION =
            "com.android.car.messenger.ACTION_UNMUTE_CONVERSATION";
    // Used to clear notification state when user dismisses notification.
    static final String ACTION_CLEAR_NOTIFICATION_STATE =
            "com.android.car.messenger.ACTION_CLEAR_NOTIFICATION_STATE";
    // Used to stop current play-out (invoked from Notification).
    static final String ACTION_STOP_PLAYOUT = "com.android.car.messenger.ACTION_STOP_PLAYOUT";

    // Common extra for ACTION_AUTO_REPLY and ACTION_PLAY_MESSAGES.
    static final String EXTRA_SENDER_KEY = "com.android.car.messenger.EXTRA_SENDER_KEY";

    static final String EXTRA_REPLY_MESSAGE = "com.android.car.messenger.EXTRA_REPLY_MESSAGE";

    // Used to notify that this service started to play out the messages.
    static final String ACTION_PLAY_MESSAGES_STARTED =
            "com.android.car.messenger.ACTION_PLAY_MESSAGES_STARTED";

    // Used to notify that this service finished playing out the messages.
    static final String ACTION_PLAY_MESSAGES_STOPPED =
            "com.android.car.messenger.ACTION_PLAY_MESSAGES_STOPPED";

    private MapMessageMonitor mMessageMonitor;
    private MapDeviceMonitor mDeviceMonitor;
    private BluetoothMapClient mMapClient;
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        MessengerService getService() {
            return MessengerService.this;
        }
    }

    @Override
    public void onCreate() {
        if (DBG) {
            Log.d(TAG, "onCreate");
        }

        mMessageMonitor = new MapMessageMonitor(this);
        mDeviceMonitor = new MapDeviceMonitor();
        connectToMap();
    }

    private void connectToMap() {
        if (DBG) {
            Log.d(TAG, "Connecting to MAP service");
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            // This *should* never happen. Unless there's some severe internal error?
            Log.wtf(TAG, "BluetoothAdapter is null! Internal error?");
            return;
        }

        if (!adapter.getProfileProxy(this, mMapServiceListener, BluetoothProfile.MAP_CLIENT)) {
            // This *should* never happen.  Unless arguments passed are incorrect somehow...
            Log.wtf(TAG, "Unable to get MAP profile! Possible programmer error?");
            return;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) {
            Log.d(TAG, "Handling intent: " + intent);
        }

        // Service will be restarted even if its killed/dies. It will never stop itself.
        // It may be restarted with null intent or one of the other intents e.g. REPLY, PLAY etc.
        final int result = START_STICKY;

        if (intent == null || ACTION_START.equals(intent.getAction())) {
            // These are NO-OP's since they're just used to bring up this service.
            return result;
        }

        if (!hasRequiredArgs(intent)) {
            return result;
        }
        switch (intent.getAction()) {
            case ACTION_AUTO_REPLY:
                boolean success;
                if (mMapClient != null) {
                    success = mMessageMonitor.sendAutoReply(
                            intent.getParcelableExtra(EXTRA_SENDER_KEY),
                            mMapClient,
                            intent.getStringExtra(EXTRA_REPLY_MESSAGE));
                } else {
                    Log.e(TAG, "Unable to send reply; MAP profile disconnected!");
                    success = false;
                }
                if (!success) {
                    Toast.makeText(this, R.string.auto_reply_failed_message, Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            case ACTION_PLAY_MESSAGES:
                mMessageMonitor.playMessages(intent.getParcelableExtra(EXTRA_SENDER_KEY));
                break;
            case ACTION_MUTE_CONVERSATION:
                mMessageMonitor.toggleMuteConversation(
                        intent.getParcelableExtra(EXTRA_SENDER_KEY), true);
                break;
            case ACTION_UNMUTE_CONVERSATION:
                mMessageMonitor.toggleMuteConversation(
                        intent.getParcelableExtra(EXTRA_SENDER_KEY), false);
                break;
            case ACTION_STOP_PLAYOUT:
                mMessageMonitor.stopPlayout();
                break;
            case ACTION_CLEAR_NOTIFICATION_STATE:
                mMessageMonitor.clearNotificationState(intent.getParcelableExtra(EXTRA_SENDER_KEY));
                break;
            default:
                Log.e(TAG, "Ignoring unknown intent: " + intent.getAction());
        }
        return result;
    }

    /**
     * @return {code true} if the service is playing the TTS of the message.
     */
    public boolean isPlaying() {
        return mMessageMonitor.isPlaying();
    }

    private boolean hasRequiredArgs(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_AUTO_REPLY:
            case ACTION_PLAY_MESSAGES:
            case ACTION_MUTE_CONVERSATION:
            case ACTION_CLEAR_NOTIFICATION_STATE:
                if (!intent.hasExtra(EXTRA_SENDER_KEY)) {
                    Log.w(TAG, "Intent is missing sender-key extra: " + intent.getAction());
                    return false;
                }
                return true;
            case ACTION_STOP_PLAYOUT:
                // No args.
                return true;
            default:
                // For unknown actions, default to true. We'll report error on these later.
                return true;
        }
    }

    @Override
    public void onDestroy() {
        if (DBG) {
            Log.d(TAG, "onDestroy");
        }
        if (mMapClient != null) {
            mMapClient.close();
        }
        mDeviceMonitor.cleanup();
        mMessageMonitor.cleanup();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // NOTE: These callbacks are invoked on the main thread.
    private final BluetoothProfile.ServiceListener mMapServiceListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mMapClient = (BluetoothMapClient) proxy;
            if (MessengerService.DBG) {
                Log.d(TAG, "Connected to MAP service!");
            }

            // Since we're connected, we will received broadcasts for any new messages
            // in the MapMessageMonitor.
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (MessengerService.DBG) {
                Log.d(TAG, "Disconnected from MAP service!");
            }
            mMapClient = null;
            mMessageMonitor.handleMapDisconnect();
        }
    };

    private class MapDeviceMonitor extends BroadcastReceiver {
        MapDeviceMonitor() {
            if (DBG) {
                Log.d(TAG, "Registering Map device monitor");
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED);
            registerReceiver(this, intentFilter, android.Manifest.permission.BLUETOOTH, null);
        }

        void cleanup() {
            unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            int previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (state == -1 || previousState == -1 || device == null) {
                Log.w(TAG, "Skipping broadcast, missing required extra");
                return;
            }
            if (previousState == BluetoothProfile.STATE_CONNECTED
                    && state != BluetoothProfile.STATE_CONNECTED) {
                if (DBG) {
                    Log.d(TAG, "Device losing MAP connection: " + device);
                }
                mMessageMonitor.handleDeviceDisconnect(device);
            }
        }
    }
}
