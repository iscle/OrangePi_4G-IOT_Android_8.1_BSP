/*
 * Copyright (c) 2014 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.IBluetoothHeadsetClient;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.hfpclient.connserv.HfpClientConnectionService;
import com.android.bluetooth.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides Bluetooth Headset Client (HF Role) profile, as a service in the
 * Bluetooth application.
 *
 * @hide
 */
public class HeadsetClientService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG = "HeadsetClientService";

    private HashMap<BluetoothDevice, HeadsetClientStateMachine> mStateMachineMap =
        new HashMap<>();
    private static HeadsetClientService sHeadsetClientService;
    private NativeInterface mNativeInterface = null;
    private HandlerThread mSmThread = null;
    private HeadsetClientStateMachineFactory mSmFactory = null;
    private AudioManager mAudioManager = null;
    // Maxinum number of devices we can try connecting to in one session
    private static final int MAX_STATE_MACHINES_POSSIBLE = 100;

    public static String HFP_CLIENT_STOP_TAG = "hfp_client_stop_tag";

    static {
        NativeInterface.classInitNative();
    }

    @Override
    protected String getName() {
        return TAG;
    }

    @Override
    public IProfileServiceBinder initBinder() {
        return new BluetoothHeadsetClientBinder(this);
    }

    @Override
    protected synchronized boolean start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }
        // Setup the JNI service
        NativeInterface.initializeNative();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mSmFactory = new HeadsetClientStateMachineFactory();
        mStateMachineMap.clear();

        IntentFilter filter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
        try {
            registerReceiver(mBroadcastReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "Unable to register broadcat receiver", e);
        }
        setHeadsetClientService(this);
        mNativeInterface = new NativeInterface();

        // Start the HfpClientConnectionService to create connection with telecom when HFP
        // connection is available.
        Intent startIntent = new Intent(this, HfpClientConnectionService.class);
        startService(startIntent);

        // Create the thread on which all State Machines will run
        mSmThread = new HandlerThread("HeadsetClient.SM");
        mSmThread.start();
        NativeInterface.initializeNative();

        return true;
    }

    @Override
    protected synchronized boolean stop() {
        try {
            unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Unable to unregister broadcast receiver", e);
        }

        for (Iterator<Map.Entry<BluetoothDevice, HeadsetClientStateMachine>> it =
                mStateMachineMap.entrySet().iterator(); it.hasNext(); ) {
            HeadsetClientStateMachine sm =
                mStateMachineMap.get((BluetoothDevice) it.next().getKey());
            sm.doQuit();
            it.remove();
        }

        // Stop the HfpClientConnectionService.
        Intent stopIntent = new Intent(this, HfpClientConnectionService.class);
        stopIntent.putExtra(HFP_CLIENT_STOP_TAG, true);
        startService(stopIntent);
        mNativeInterface = null;

        // Stop the handler thread
        mSmThread.quit();
        mSmThread = null;

        NativeInterface.cleanupNative();

        return true;
    }

    @Override
    protected boolean cleanup() {
        HeadsetClientStateMachine.cleanup();
        clearHeadsetClientService();
        return true;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // We handle the volume changes for Voice calls here since HFP audio volume control does
            // not go through audio manager (audio mixer). see
            // ({@link HeadsetClientStateMachine#SET_SPEAKER_VOLUME} in
            // {@link HeadsetClientStateMachine} for details.
            if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                if (DBG) {
                    Log.d(TAG,
                            "Volume changed for stream: "
                                    + intent.getExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE));
                }
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_VOICE_CALL) {
                    int streamValue = intent
                            .getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1);
                    int hfVol = HeadsetClientStateMachine.amToHfVol(streamValue);
                    if (DBG) {
                        Log.d(TAG,
                                "Setting volume to audio manager: " + streamValue
                                        + " hands free: " + hfVol);
                    }
                    mAudioManager.setParameters("hfp_volume=" + hfVol);
                    synchronized (this) {
                        for (HeadsetClientStateMachine sm : mStateMachineMap.values()) {
                            if (sm != null) {
                                sm.sendMessage(
                                        HeadsetClientStateMachine.SET_SPEAKER_VOLUME, streamValue);
                            }
                        }
                    }
                }
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothHeadsetClientBinder extends IBluetoothHeadsetClient.Stub
            implements IProfileServiceBinder {
        private HeadsetClientService mService;

        public BluetoothHeadsetClientBinder(HeadsetClientService svc) {
            mService = svc;
        }

        @Override
        public boolean cleanup() {
            mService = null;
            return true;
        }

        private HeadsetClientService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "HeadsetClient call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                return mService;
            }

            Log.e(TAG, "HeadsetClientService is not available.");
            return null;
        }

        @Override
        public boolean connect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.connect(device);
        }

        @Override
        public boolean disconnect(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.disconnect(device);
        }

        @Override
        public List<BluetoothDevice> getConnectedDevices() {
            HeadsetClientService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getConnectedDevices();
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HeadsetClientService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        @Override
        public int getConnectionState(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return service.getConnectionState(device);
        }

        @Override
        public boolean setPriority(BluetoothDevice device, int priority) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.setPriority(device, priority);
        }

        @Override
        public int getPriority(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }
            return service.getPriority(device);
        }

        @Override
        public boolean startVoiceRecognition(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.startVoiceRecognition(device);
        }

        @Override
        public boolean stopVoiceRecognition(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.stopVoiceRecognition(device);
        }

        @Override
        public int getAudioState(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
            }
            return service.getAudioState(device);
        }

        @Override
        public void setAudioRouteAllowed(BluetoothDevice device, boolean allowed) {
            Log.e(TAG, "setAudioRouteAllowed API not supported");
        }

        @Override
        public boolean getAudioRouteAllowed(BluetoothDevice device) {
            Log.e(TAG, "getAudioRouteAllowed API not supported");
            return false;
        }

        @Override
        public boolean connectAudio(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.connectAudio(device);
        }

        @Override
        public boolean disconnectAudio(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.disconnectAudio(device);
        }

        @Override
        public boolean acceptCall(BluetoothDevice device, int flag) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.acceptCall(device, flag);
        }

        @Override
        public boolean rejectCall(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.rejectCall(device);
        }

        @Override
        public boolean holdCall(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.holdCall(device);
        }

        @Override
        public boolean terminateCall(BluetoothDevice device, BluetoothHeadsetClientCall call) {
            HeadsetClientService service = getService();
            if (service == null) {
                Log.w(TAG, "service is null");
                return false;
            }
            return service.terminateCall(device, call != null ? call.getUUID() : null);
        }

        @Override
        public boolean explicitCallTransfer(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.explicitCallTransfer(device);
        }

        @Override
        public boolean enterPrivateMode(BluetoothDevice device, int index) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.enterPrivateMode(device, index);
        }

        @Override
        public BluetoothHeadsetClientCall dial(BluetoothDevice device, String number) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.dial(device, number);
        }

        @Override
        public List<BluetoothHeadsetClientCall> getCurrentCalls(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothHeadsetClientCall>();
            }
            return service.getCurrentCalls(device);
        }

        @Override
        public boolean sendDTMF(BluetoothDevice device, byte code) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.sendDTMF(device, code);
        }

        @Override
        public boolean getLastVoiceTagNumber(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return false;
            }
            return service.getLastVoiceTagNumber(device);
        }

        @Override
        public Bundle getCurrentAgEvents(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCurrentAgEvents(device);
        }

        @Override
        public Bundle getCurrentAgFeatures(BluetoothDevice device) {
            HeadsetClientService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCurrentAgFeatures(device);
        }
    };

    // API methods
    public static synchronized HeadsetClientService getHeadsetClientService() {
        if (sHeadsetClientService != null && sHeadsetClientService.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "getHeadsetClientService(): returning " + sHeadsetClientService);
            }
            return sHeadsetClientService;
        }
        if (DBG) {
            if (sHeadsetClientService == null) {
                Log.d(TAG, "getHeadsetClientService(): service is NULL");
            } else if (!(sHeadsetClientService.isAvailable())) {
                Log.d(TAG, "getHeadsetClientService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setHeadsetClientService(HeadsetClientService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "setHeadsetClientService(): set to: " + sHeadsetClientService);
            }
            sHeadsetClientService = instance;
        } else {
            if (DBG) {
                if (sHeadsetClientService == null) {
                    Log.d(TAG, "setHeadsetClientService(): service not available");
                } else if (!sHeadsetClientService.isAvailable()) {
                    Log.d(TAG, "setHeadsetClientService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearHeadsetClientService() {
        sHeadsetClientService = null;
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Log.d(TAG, "connect " + device);
        }
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        if (getPriority(device) == BluetoothProfile.PRIORITY_OFF) {
            Log.w(TAG, "Connection not allowed: <" + device.getAddress() + "> is PRIORITY_OFF");
            return false;
        }

        sm.sendMessage(HeadsetClientStateMachine.CONNECT, device);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                "Need BLUETOOTH ADMIN permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        sm.sendMessage(HeadsetClientStateMachine.DISCONNECT, device);
        return true;
    }

    public synchronized List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        ArrayList<BluetoothDevice> connectedDevices = new ArrayList<>();
        for (BluetoothDevice bd : mStateMachineMap.keySet()) {
            HeadsetClientStateMachine sm = mStateMachineMap.get(bd);
            if (sm != null && sm.getConnectionState(bd) == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(bd);
            }
        }
        return connectedDevices;
    }

    private synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        for (BluetoothDevice bd : mStateMachineMap.keySet()) {
            for (int state : states) {
                HeadsetClientStateMachine sm = mStateMachineMap.get(bd);
                if (sm != null && sm.getConnectionState(bd) == state) {
                    devices.add(bd);
                }
            }
        }
        return devices;
    }

    private synchronized int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = mStateMachineMap.get(device);
        if (sm != null) {
            return sm.getConnectionState(device);
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()),
                priority);
        if (DBG) {
            Log.d(TAG, "Saved priority " + device + " = " + priority);
        }
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()),
                BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    boolean startVoiceRecognition(BluetoothDevice device) {
        Log.e(TAG, "startVoiceRecognition API not available");
        return false;
    }

    boolean stopVoiceRecognition(BluetoothDevice device) {
        Log.e(TAG, "stopVoiceRecognition API not available");
        return false;
    }

    int getAudioState(BluetoothDevice device) {
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return -1;
        }

        return sm.getAudioState(device);
    }

    boolean connectAudio(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        if (!sm.isConnected()) {
            return false;
        }
        if (sm.isAudioOn()) {
            return false;
        }
        sm.sendMessage(HeadsetClientStateMachine.CONNECT_AUDIO);
        return true;
    }

    boolean disconnectAudio(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        if (!sm.isAudioOn()) {
            return false;
        }
        sm.sendMessage(HeadsetClientStateMachine.DISCONNECT_AUDIO);
        return true;
    }

    boolean holdCall(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        Message msg = sm.obtainMessage(HeadsetClientStateMachine.HOLD_CALL);
        sm.sendMessage(msg);
        return true;
    }

    boolean acceptCall(BluetoothDevice device, int flag) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        /* Phonecalls from a single device are supported, hang up any calls on the other phone */
        synchronized (this) {
            for (Map.Entry<BluetoothDevice, HeadsetClientStateMachine> entry :
                    mStateMachineMap.entrySet()) {
                if (entry.getValue() == null || entry.getKey().equals(device)) {
                    continue;
                }
                int connectionState = entry.getValue().getConnectionState(entry.getKey());
                if (DBG) {
                    Log.d(TAG, "Accepting a call on device " + device
                                    + ". Possibly disconnecting on " + entry.getValue());
                }
                if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                    entry.getValue()
                            .obtainMessage(HeadsetClientStateMachine.TERMINATE_CALL)
                            .sendToTarget();
                }
            }
        }
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return false;
        }
        Message msg = sm.obtainMessage(HeadsetClientStateMachine.ACCEPT_CALL);
        msg.arg1 = flag;
        sm.sendMessage(msg);
        return true;
    }

    boolean rejectCall(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        Message msg = sm.obtainMessage(HeadsetClientStateMachine.REJECT_CALL);
        sm.sendMessage(msg);
        return true;
    }

    boolean terminateCall(BluetoothDevice device, UUID uuid) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        Message msg = sm.obtainMessage(HeadsetClientStateMachine.TERMINATE_CALL);
        msg.obj = uuid;
        sm.sendMessage(msg);
        return true;
    }

    boolean enterPrivateMode(BluetoothDevice device, int index) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }

        Message msg = sm.obtainMessage(HeadsetClientStateMachine.ENTER_PRIVATE_MODE);
        msg.arg1 = index;
        sm.sendMessage(msg);
        return true;
    }

    BluetoothHeadsetClientCall dial(BluetoothDevice device, String number) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return null;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return null;
        }

        BluetoothHeadsetClientCall call = new BluetoothHeadsetClientCall(
            device, HeadsetClientStateMachine.HF_ORIGINATED_CALL_ID,
            BluetoothHeadsetClientCall.CALL_STATE_DIALING, number, false  /* multiparty */,
            true  /* outgoing */);
        Message msg = sm.obtainMessage(HeadsetClientStateMachine.DIAL_NUMBER);
        msg.obj = call;
        sm.sendMessage(msg);
        return call;
    }

    public boolean sendDTMF(BluetoothDevice device, byte code) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        Message msg = sm.obtainMessage(HeadsetClientStateMachine.SEND_DTMF);
        msg.arg1 = code;
        sm.sendMessage(msg);
        return true;
    }

    public boolean getLastVoiceTagNumber(BluetoothDevice device) {
        return false;
    }

    public List<BluetoothHeadsetClientCall> getCurrentCalls(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return null;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return null;
        }
        return sm.getCurrentCalls();
    }

    public boolean explicitCallTransfer(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return false;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED &&
                connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        Message msg = sm.obtainMessage(HeadsetClientStateMachine.EXPLICIT_CALL_TRANSFER);
        sm.sendMessage(msg);
        return true;
    }

    public Bundle getCurrentAgEvents(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return null;
        }

        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return null;
        }
        return sm.getCurrentAgEvents();
    }

    public Bundle getCurrentAgFeatures(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HeadsetClientStateMachine sm = getStateMachine(device);
        if (sm == null) {
            Log.e(TAG, "Cannot allocate SM for device " + device);
            return null;
        }
        int connectionState = sm.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return null;
        }
        return sm.getCurrentAgFeatures();
    }

    // Handle messages from native (JNI) to java
    public void messageFromNative(StackEvent stackEvent) {
        HeadsetClientStateMachine sm = getStateMachine(stackEvent.device);
        if (sm == null) {
            Log.w(TAG, "No SM found for event " + stackEvent);
        }

        sm.sendMessage(StackEvent.STACK_EVENT, stackEvent);
    }

    // State machine management
    private synchronized HeadsetClientStateMachine getStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getStateMachine failed: Device cannot be null");
            return null;
        }

        HeadsetClientStateMachine sm = mStateMachineMap.get(device);
        if (sm != null) {
            if (DBG) {
                Log.d(TAG, "Found SM for device " + device);
            }
            return sm;
        }

        // There is a possibility of a DOS attack if someone populates here with a lot of fake
        // BluetoothAddresses. If it so happens instead of blowing up we can atleast put a limit on
        // how long the attack would survive
        if (mStateMachineMap.keySet().size() > MAX_STATE_MACHINES_POSSIBLE) {
            Log.e(TAG, "Max state machines reached, possible DOS attack " +
                MAX_STATE_MACHINES_POSSIBLE);
            return null;
        }

        // Allocate a new SM
        Log.d(TAG, "Creating a new state machine");
        sm = mSmFactory.make(this, mSmThread);
        mStateMachineMap.put(device, sm);
        return sm;
    }

    // Check if any of the state machines have routed the SCO audio stream.
    synchronized boolean isScoRouted() {
        for (Map.Entry<BluetoothDevice, HeadsetClientStateMachine> entry :
                mStateMachineMap.entrySet()) {
            if (entry.getValue() != null) {
                int audioState = entry.getValue().getAudioState(entry.getKey());
                if (audioState == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                    if (DBG) {
                        Log.d(TAG, "Device " + entry.getKey() + " audio state " + audioState
                                        + " Connected");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized void dump(StringBuilder sb) {
        super.dump(sb);
        for (HeadsetClientStateMachine sm : mStateMachineMap.values()) {
            if (sm != null) {
                println(sb, "State machine:");
                println(sb, "=============");
                sm.dump(sb);
            }
        }
    }

    // For testing
    protected synchronized Map<BluetoothDevice, HeadsetClientStateMachine> getStateMachineMap() {
        return mStateMachineMap;
    }

    protected void setSMFactory(HeadsetClientStateMachineFactory factory) {
        mSmFactory = factory;
    }
}
