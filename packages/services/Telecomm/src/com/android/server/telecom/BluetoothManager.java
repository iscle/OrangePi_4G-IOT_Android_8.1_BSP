/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.Log;
import android.telecom.Logging.Runnable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.List;

/**
 * Listens to and caches bluetooth headset state.  Used By the CallAudioManager for maintaining
 * overall audio state. Also provides method for connecting the bluetooth headset to the phone call.
 */
public class BluetoothManager {
    public static final int BLUETOOTH_UNINITIALIZED = 0;
    public static final int BLUETOOTH_DISCONNECTED = 1;
    public static final int BLUETOOTH_DEVICE_CONNECTED = 2;
    public static final int BLUETOOTH_AUDIO_PENDING = 3;
    public static final int BLUETOOTH_AUDIO_CONNECTED = 4;

    public interface BluetoothStateListener {
        void onBluetoothStateChange(int oldState, int newState);
    }

    private final BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.startSession("BMSL.oSC");
                    try {
                        if (profile == BluetoothProfile.HEADSET) {
                            mBluetoothHeadset = new BluetoothHeadsetProxy((BluetoothHeadset) proxy);
                            Log.v(this, "- Got BluetoothHeadset: " + mBluetoothHeadset);
                        } else {
                            Log.w(this, "Connected to non-headset bluetooth service. Not changing" +
                                    " bluetooth headset.");
                        }
                        updateListenerOfBluetoothState(true);
                    } finally {
                        Log.endSession();
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    Log.startSession("BMSL.oSD");
                    try {
                        mBluetoothHeadset = null;
                        Log.v(this, "Lost BluetoothHeadset: " + mBluetoothHeadset);
                        updateListenerOfBluetoothState(false);
                    } finally {
                        Log.endSession();
                    }
                }
           };

    /**
     * Receiver for misc intent broadcasts the BluetoothManager cares about.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("BM.oR");
            try {
                String action = intent.getAction();

                if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                    int bluetoothHeadsetState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                            BluetoothHeadset.STATE_DISCONNECTED);
                    Log.i(this, "mReceiver: HEADSET_STATE_CHANGED_ACTION");
                    Log.i(this, "==> new state: %s ", bluetoothHeadsetState);
                    updateListenerOfBluetoothState(
                            bluetoothHeadsetState == BluetoothHeadset.STATE_CONNECTING);
                } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
                    int bluetoothHeadsetAudioState =
                            intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                    BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                    Log.i(this, "mReceiver: HEADSET_AUDIO_STATE_CHANGED_ACTION");
                    Log.i(this, "==> new state: %s", bluetoothHeadsetAudioState);
                    updateListenerOfBluetoothState(
                            bluetoothHeadsetAudioState ==
                                    BluetoothHeadset.STATE_AUDIO_CONNECTING
                            || bluetoothHeadsetAudioState ==
                                    BluetoothHeadset.STATE_AUDIO_CONNECTED);
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final BluetoothAdapterProxy mBluetoothAdapter;
    private BluetoothStateListener mBluetoothStateListener;

    private BluetoothHeadsetProxy mBluetoothHeadset;
    private long mBluetoothConnectionRequestTime;
    private final Runnable mBluetoothConnectionTimeout = new Runnable("BM.cBA", null /*lock*/) {
        @Override
        public void loggedRun() {
            if (!isBluetoothAudioConnected()) {
                Log.v(this, "Bluetooth audio inexplicably disconnected within 5 seconds of " +
                        "connection. Updating UI.");
            }
            updateListenerOfBluetoothState(false);
        }
    };

    private final Runnable mRetryConnectAudio = new Runnable("BM.rCA", null /*lock*/) {
        @Override
        public void loggedRun() {
            Log.i(this, "Retrying connecting to bluetooth audio.");
            if (!mBluetoothHeadset.connectAudio()) {
                Log.w(this, "Retry of bluetooth audio connection failed. Giving up.");
            } else {
                setBluetoothStatePending();
            }
        }
    };

    private final Context mContext;
    private int mBluetoothState = BLUETOOTH_UNINITIALIZED;

    public BluetoothManager(Context context, BluetoothAdapterProxy bluetoothAdapterProxy) {
        mBluetoothAdapter = bluetoothAdapterProxy;
        mContext = context;

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.getProfileProxy(context, mBluetoothProfileServiceListener,
                                    BluetoothProfile.HEADSET);
        }

        // Register for misc other intent broadcasts.
        IntentFilter intentFilter =
                new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
    }

    public void setBluetoothStateListener(BluetoothStateListener bluetoothStateListener) {
        mBluetoothStateListener = bluetoothStateListener;
    }

    //
    // Bluetooth helper methods.
    //
    // - BluetoothAdapter is the Bluetooth system service.  If
    //   getDefaultAdapter() returns null
    //   then the device is not BT capable.  Use BluetoothDevice.isEnabled()
    //   to see if BT is enabled on the device.
    //
    // - BluetoothHeadset is the API for the control connection to a
    //   Bluetooth Headset.  This lets you completely connect/disconnect a
    //   headset (which we don't do from the Phone UI!) but also lets you
    //   get the address of the currently active headset and see whether
    //   it's currently connected.

    /**
     * @return true if the Bluetooth on/off switch in the UI should be
     *         available to the user (i.e. if the device is BT-capable
     *         and a headset is connected.)
     */
    @VisibleForTesting
    public boolean isBluetoothAvailable() {
        Log.v(this, "isBluetoothAvailable()...");

        // There's no need to ask the Bluetooth system service if BT is enabled:
        //
        //    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        //    if ((adapter == null) || !adapter.isEnabled()) {
        //        Log.d(this, "  ==> FALSE (BT not enabled)");
        //        return false;
        //    }
        //    Log.d(this, "  - BT enabled!  device name " + adapter.getName()
        //                 + ", address " + adapter.getAddress());
        //
        // ...since we already have a BluetoothHeadset instance.  We can just
        // call isConnected() on that, and assume it'll be false if BT isn't
        // enabled at all.

        // Check if there's a connected headset, using the BluetoothHeadset API.
        boolean isConnected = false;
        if (mBluetoothHeadset != null) {
            List<BluetoothDevice> deviceList = mBluetoothHeadset.getConnectedDevices();

            if (deviceList.size() > 0) {
                isConnected = true;
                for (int i = 0; i < deviceList.size(); i++) {
                    BluetoothDevice device = deviceList.get(i);
                    Log.v(this, "state = " + mBluetoothHeadset.getConnectionState(device)
                            + "for headset: " + device);
                }
            }
        }

        Log.v(this, "  ==> " + isConnected);
        return isConnected;
    }

    /**
     * @return true if a BT Headset is available, and its audio is currently connected.
     */
    @VisibleForTesting
    public boolean isBluetoothAudioConnected() {
        if (mBluetoothHeadset == null) {
            Log.v(this, "isBluetoothAudioConnected: ==> FALSE (null mBluetoothHeadset)");
            return false;
        }
        List<BluetoothDevice> deviceList = mBluetoothHeadset.getConnectedDevices();

        if (deviceList.isEmpty()) {
            return false;
        }
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            boolean isAudioOn = mBluetoothHeadset.isAudioConnected(device);
            Log.v(this, "isBluetoothAudioConnected: ==> isAudioOn = " + isAudioOn
                    + "for headset: " + device);
            if (isAudioOn) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method used to control the onscreen "Bluetooth" indication;
     *
     * @return true if a BT device is available and its audio is currently connected,
     *              <b>or</b> if we issued a BluetoothHeadset.connectAudio()
     *              call within the last 5 seconds (which presumably means
     *              that the BT audio connection is currently being set
     *              up, and will be connected soon.)
     */
    @VisibleForTesting
    public boolean isBluetoothAudioConnectedOrPending() {
        if (isBluetoothAudioConnected()) {
            Log.v(this, "isBluetoothAudioConnectedOrPending: ==> TRUE (really connected)");
            return true;
        }

        // If we issued a connectAudio() call "recently enough", even
        // if BT isn't actually connected yet, let's still pretend BT is
        // on.  This makes the onscreen indication more responsive.
        if (isBluetoothAudioPending()) {
            long timeSinceRequest =
                    SystemClock.elapsedRealtime() - mBluetoothConnectionRequestTime;
            Log.v(this, "isBluetoothAudioConnectedOrPending: ==> TRUE (requested "
                    + timeSinceRequest + " msec ago)");
            return true;
        }

        Log.v(this, "isBluetoothAudioConnectedOrPending: ==> FALSE");
        return false;
    }

    private boolean isBluetoothAudioPending() {
        return mBluetoothState == BLUETOOTH_AUDIO_PENDING;
    }

    /**
     * Notified audio manager of a change to the bluetooth state.
     */
    private void updateListenerOfBluetoothState(boolean canBePending) {
        int newState;
        if (isBluetoothAudioConnected()) {
            newState = BLUETOOTH_AUDIO_CONNECTED;
        } else if (canBePending && isBluetoothAudioPending()) {
            newState = BLUETOOTH_AUDIO_PENDING;
        } else if (isBluetoothAvailable()) {
            newState = BLUETOOTH_DEVICE_CONNECTED;
        } else {
            newState = BLUETOOTH_DISCONNECTED;
        }
        if (mBluetoothState != newState) {
            mBluetoothStateListener.onBluetoothStateChange(mBluetoothState, newState);
            mBluetoothState = newState;
        }
    }

    @VisibleForTesting
    public void connectBluetoothAudio() {
        Log.v(this, "connectBluetoothAudio()...");
        if (mBluetoothHeadset != null) {
            if (!mBluetoothHeadset.connectAudio()) {
                mHandler.postDelayed(mRetryConnectAudio.prepare(),
                        Timeouts.getRetryBluetoothConnectAudioBackoffMillis(
                                mContext.getContentResolver()));
            }
        }
        // The call to connectAudio is asynchronous and may take some time to complete. However,
        // if connectAudio() returns false, we know that it has failed and therefore will
        // schedule a retry to happen some time later. We set bluetooth state to pending now and
        // show bluetooth as connected in the UI, but confirmation that we are connected will
        // arrive through mReceiver.
        setBluetoothStatePending();
    }

    private void setBluetoothStatePending() {
        mBluetoothState = BLUETOOTH_AUDIO_PENDING;
        mBluetoothConnectionRequestTime = SystemClock.elapsedRealtime();
        mHandler.removeCallbacks(mBluetoothConnectionTimeout.getRunnableToCancel());
        mBluetoothConnectionTimeout.cancel();
        // If the mBluetoothConnectionTimeout runnable has run, the session had been cleared...
        // Create a new Session before putting it back in the queue to possibly run again.
        mHandler.postDelayed(mBluetoothConnectionTimeout.prepare(),
                Timeouts.getBluetoothPendingTimeoutMillis(mContext.getContentResolver()));
    }

    @VisibleForTesting
    public void disconnectBluetoothAudio() {
        Log.v(this, "disconnectBluetoothAudio()...");
        if (mBluetoothHeadset != null) {
            mBluetoothState = BLUETOOTH_DEVICE_CONNECTED;
            mBluetoothHeadset.disconnectAudio();
        } else {
            mBluetoothState = BLUETOOTH_DISCONNECTED;
        }
        mHandler.removeCallbacks(mBluetoothConnectionTimeout.getRunnableToCancel());
        mBluetoothConnectionTimeout.cancel();
    }

    /**
     * Dumps the state of the {@link BluetoothManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("isBluetoothAvailable: " + isBluetoothAvailable());
        pw.println("isBluetoothAudioConnected: " + isBluetoothAudioConnected());
        pw.println("isBluetoothAudioConnectedOrPending: " + isBluetoothAudioConnectedOrPending());

        if (mBluetoothAdapter != null) {
            if (mBluetoothHeadset != null) {
                List<BluetoothDevice> deviceList = mBluetoothHeadset.getConnectedDevices();

                if (deviceList.size() > 0) {
                    BluetoothDevice device = deviceList.get(0);
                    pw.println("BluetoothHeadset.getCurrentDevice: " + device);
                    pw.println("BluetoothHeadset.State: "
                            + mBluetoothHeadset.getConnectionState(device));
                    pw.println("BluetoothHeadset audio connected: " +
                            mBluetoothHeadset.isAudioConnected(device));
                }
            } else {
                pw.println("mBluetoothHeadset is null");
            }
        } else {
            pw.println("mBluetoothAdapter is null; device is not BT capable");
        }
    }

    /**
     * Set the bluetooth headset proxy for testing purposes.
     * @param bluetoothHeadsetProxy
     */
    @VisibleForTesting
    public void setBluetoothHeadsetForTesting(BluetoothHeadsetProxy bluetoothHeadsetProxy) {
        mBluetoothHeadset = bluetoothHeadsetProxy;
    }

    /**
     * Set mBluetoothState for testing.
     * @param state
     */
    @VisibleForTesting
    public void setInternalBluetoothState(int state) {
        mBluetoothState = state;
    }
}
