/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.hfp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import java.util.ArrayList;
import java.util.List;
import android.os.SystemProperties;

/**
 * Provides Bluetooth Headset and Handsfree profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class HeadsetService extends ProfileService {
    private static final boolean DBG = SystemProperties
                                       .get("persist.bluetooth.hostloglevel","").equals("sqc");
    private static final String TAG = "HeadsetService";
    private static final String MODIFY_PHONE_STATE = android.Manifest.permission.MODIFY_PHONE_STATE;

    private HeadsetStateMachine mStateMachine;
    private static HeadsetService sHeadsetService;

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothHeadsetBinder(this);
    }

    protected boolean start() {
        mStateMachine = HeadsetStateMachine.make(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        try {
            registerReceiver(mHeadsetReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "Unable to register headset receiver", e);
        }
        setHeadsetService(this);
        return true;
    }

    protected boolean stop() {
        try {
            unregisterReceiver(mHeadsetReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Unable to unregister headset receiver", e);
        }
        if (mStateMachine != null) {
            //M: inform Audio Service that BT SCO is disconnected
            if (mStateMachine.isAudioOn()) {
                AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                mAudioManager.setBluetoothScoOn(false);
            }
            mStateMachine.doQuit();
        }
        return true;
    }

    protected boolean cleanup() {
        if (mStateMachine != null) {
            mStateMachine.cleanup();
        }
        clearHeadsetService();
        return true;
    }

    private final BroadcastReceiver mHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mStateMachine.sendMessage(HeadsetStateMachine.INTENT_BATTERY_CHANGED, intent);
            } else if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_BLUETOOTH_SCO) {
                    mStateMachine.sendMessage(
                            HeadsetStateMachine.INTENT_SCO_VOLUME_CHANGED, intent);
                }
            } else if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {
                int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                        BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                if (requestType == BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS) {
                    Log.v(TAG, "Received BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY");
                    mStateMachine.handleAccessPermissionResult(intent);
                }
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothHeadsetBinder
            extends IBluetoothHeadset.Stub implements IProfileServiceBinder {
        private HeadsetService mService;

        public BluetoothHeadsetBinder(HeadsetService svc) {
            mService = svc;
        }
        public boolean cleanup() {
            mService = null;
            return true;
        }

        private HeadsetService getService() {
            if (!Utils.checkCallerAllowManagedProfiles(mService)) {
                Log.w(TAG, "Headset call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean connect(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            if (DBG) Log.d(TAG, "disconnect in HeadsetService");
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            HeadsetService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HeadsetService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        public boolean startVoiceRecognition(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.startVoiceRecognition(device);
        }

        public boolean stopVoiceRecognition(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.stopVoiceRecognition(device);
        }

        public boolean isAudioOn() {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.isAudioOn();
        }

        public boolean isAudioConnected(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.isAudioConnected(device);
        }

        public int getBatteryUsageHint(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return 0;
            return service.getBatteryUsageHint(device);
        }

        public boolean acceptIncomingConnect(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.acceptIncomingConnect(device);
        }

        public boolean rejectIncomingConnect(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.rejectIncomingConnect(device);
        }

        public int getAudioState(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return BluetoothHeadset.STATE_AUDIO_DISCONNECTED;
            return service.getAudioState(device);
        }

        public boolean connectAudio() {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.connectAudio();
        }

        public boolean disconnectAudio() {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.disconnectAudio();
        }

        public void setAudioRouteAllowed(boolean allowed) {
            HeadsetService service = getService();
            if (service == null) return;
            service.setAudioRouteAllowed(allowed);
        }

        public boolean getAudioRouteAllowed() {
            HeadsetService service = getService();
            if (service != null) {
                return service.getAudioRouteAllowed();
            }
            return false;
        }

        public void setForceScoAudio(boolean forced) {
            HeadsetService service = getService();
            if (service == null) return;
            service.setForceScoAudio(forced);
        }

        public boolean startScoUsingVirtualVoiceCall(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.startScoUsingVirtualVoiceCall(device);
        }

        public boolean stopScoUsingVirtualVoiceCall(BluetoothDevice device) {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.stopScoUsingVirtualVoiceCall(device);
        }

        public void phoneStateChanged(
                int numActive, int numHeld, int callState, String number, int type) {
            HeadsetService service = getService();
            if (service == null) return;
            service.phoneStateChanged(numActive, numHeld, callState, number, type);
        }

        public void clccResponse(int index, int direction, int status, int mode, boolean mpty,
                String number, int type) {
            HeadsetService service = getService();
            if (service == null) return;
            service.clccResponse(index, direction, status, mode, mpty, number, type);
        }

        public boolean sendVendorSpecificResultCode(
                BluetoothDevice device, String command, String arg) {
            HeadsetService service = getService();
            if (service == null) {
                return false;
            }
            return service.sendVendorSpecificResultCode(device, command, arg);
        }

        public boolean enableWBS() {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.enableWBS();
        }

        public boolean disableWBS() {
            HeadsetService service = getService();
            if (service == null) return false;
            return service.disableWBS();
        }

        public void bindResponse(int ind_id, boolean ind_status) {
            HeadsetService service = getService();
            if (service == null) return;
            service.bindResponse(ind_id, ind_status);
        }
    };

    // API methods
    public static synchronized HeadsetService getHeadsetService() {
        if (sHeadsetService != null && sHeadsetService.isAvailable()) {
            if (DBG) Log.d(TAG, "getHeadsetService(): returning " + sHeadsetService);
            return sHeadsetService;
        }
        if (DBG) {
            if (sHeadsetService == null) {
                Log.d(TAG, "getHeadsetService(): service is NULL");
            } else if (!(sHeadsetService.isAvailable())) {
                Log.d(TAG, "getHeadsetService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setHeadsetService(HeadsetService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setHeadsetService(): set to: " + sHeadsetService);
            sHeadsetService = instance;
        } else {
            if (DBG) {
                if (sHeadsetService == null) {
                    Log.d(TAG, "setHeadsetService(): service not available");
                } else if (!sHeadsetService.isAvailable()) {
                    Log.d(TAG, "setHeadsetService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearHeadsetService() {
        sHeadsetService = null;
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        Log.d(TAG, "connect: device=" + device);
        if (getPriority(device) == BluetoothProfile.PRIORITY_OFF) {
            Log.w(TAG, "connect: PRIORITY_OFF, device=" + device);
            return false;
        }
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState == BluetoothProfile.STATE_CONNECTED
                || connectionState == BluetoothProfile.STATE_CONNECTING) {
            Log.w(TAG,
                    "connect: already connected/connecting, connectionState=" + connectionState
                            + ", device=" + device);
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.CONNECT, device);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        Log.d(TAG, "disconnect: device=" + device);
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED
                && connectionState != BluetoothProfile.STATE_CONNECTING) {
            Log.w(TAG,
                    "disconnect: not connected/connecting, connectionState=" + connectionState
                            + ", device=" + device);
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT, device);
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getConnectedDevices();
    }

    private List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getDevicesMatchingConnectionStates(states);
    }

    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.getConnectionState(device);
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()), priority);
        if (DBG) Log.d(TAG, "Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()),
                BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    boolean startVoiceRecognition(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED
                && connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.VOICE_RECOGNITION_START);
        return true;
    }

    boolean stopVoiceRecognition(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        // It seem that we really need to check the AudioOn state.
        // But since we allow startVoiceRecognition in STATE_CONNECTED and
        // STATE_CONNECTING state, we do these 2 in this method
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED
                && connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.VOICE_RECOGNITION_STOP);
        // TODO is this return correct when the voice recognition is not on?
        return true;
    }

    boolean isAudioOn() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.isAudioOn();
    }

    boolean isAudioConnected(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mStateMachine.isAudioConnected(device);
    }

    int getBatteryUsageHint(BluetoothDevice device) {
        // TODO(BT) ask for BT stack support?
        return 0;
    }

    boolean acceptIncomingConnect(BluetoothDevice device) {
        // TODO(BT) remove it if stack does access control
        return false;
    }

    boolean rejectIncomingConnect(BluetoothDevice device) {
        // TODO(BT) remove it if stack does access control
        return false;
    }

    int getAudioState(BluetoothDevice device) {
        return mStateMachine.getAudioState(device);
    }

    public void setAudioRouteAllowed(boolean allowed) {
        mStateMachine.setAudioRouteAllowed(allowed);
    }

    public boolean getAudioRouteAllowed() {
        return mStateMachine.getAudioRouteAllowed();
    }

    public void setForceScoAudio(boolean forced) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        mStateMachine.setForceScoAudio(forced);
    }

    boolean connectAudio() {
        // TODO(BT) BLUETOOTH or BLUETOOTH_ADMIN permission
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (!mStateMachine.isConnected()) {
            Log.w(TAG, "connectAudio: profile not connected");
            return false;
        }
        if (mStateMachine.isAudioOn()) {
            Log.w(TAG, "connectAudio: audio is already ON");
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO);
        return true;
    }

    boolean disconnectAudio() {
        // TODO(BT) BLUETOOTH or BLUETOOTH_ADMIN permission
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (!mStateMachine.isAudioOn()) {
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.DISCONNECT_AUDIO);
        return true;
    }

    boolean startScoUsingVirtualVoiceCall(BluetoothDevice device) {
        /* Do not ignore request if HSM state is still Disconnected or
           Pending, it will be processed when transitioned to Connected */
        mStateMachine.sendMessage(HeadsetStateMachine.VIRTUAL_CALL_START, device);
        return true;
    }

    boolean stopScoUsingVirtualVoiceCall(BluetoothDevice device) {
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED
                && connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.VIRTUAL_CALL_STOP, device);
        return true;
    }

    private void phoneStateChanged(
            int numActive, int numHeld, int callState, String number, int type) {
        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        Message msg = mStateMachine.obtainMessage(HeadsetStateMachine.CALL_STATE_CHANGED);
        msg.obj = new HeadsetCallState(numActive, numHeld, callState, number, type);
        msg.arg1 = 0; // false
        mStateMachine.sendMessage(msg);
    }

    private void clccResponse(
            int index, int direction, int status, int mode, boolean mpty, String number, int type) {
        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        mStateMachine.sendMessage(HeadsetStateMachine.SEND_CCLC_RESPONSE,
                new HeadsetClccResponse(index, direction, status, mode, mpty, number, type));
    }

    private boolean sendVendorSpecificResultCode(
            BluetoothDevice device, String command, String arg) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int connectionState = mStateMachine.getConnectionState(device);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return false;
        }
        // Currently we support only "+ANDROID".
        if (!command.equals(BluetoothHeadset.VENDOR_RESULT_CODE_COMMAND_ANDROID)) {
            Log.w(TAG, "Disallowed unsolicited result code command: " + command);
            return false;
        }
        mStateMachine.sendMessage(HeadsetStateMachine.SEND_VENDOR_SPECIFIC_RESULT_CODE,
                new HeadsetVendorSpecificResultCode(device, command, arg));
        return true;
    }

    boolean enableWBS() {
        // TODO(BT) BLUETOOTH or BLUETOOTH_ADMIN permission
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (!mStateMachine.isConnected()) {
            return false;
        }
        if (mStateMachine.isAudioOn()) {
            return false;
        }

        for (BluetoothDevice device : getConnectedDevices()) {
            mStateMachine.sendMessage(HeadsetStateMachine.ENABLE_WBS, device);
        }

        return true;
    }

    boolean disableWBS() {
        // TODO(BT) BLUETOOTH or BLUETOOTH_ADMIN permission
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (!mStateMachine.isConnected()) {
            return false;
        }
        if (mStateMachine.isAudioOn()) {
            return false;
        }
        for (BluetoothDevice device : getConnectedDevices()) {
            mStateMachine.sendMessage(HeadsetStateMachine.DISABLE_WBS, device);
        }
        return true;
    }

    private boolean bindResponse(int ind_id, boolean ind_status) {
        for (BluetoothDevice device : getConnectedDevices()) {
            int connectionState = mStateMachine.getConnectionState(device);
            if (connectionState != BluetoothProfile.STATE_CONNECTED
                    && connectionState != BluetoothProfile.STATE_CONNECTING) {
                continue;
            }
            if (DBG) Log.d("Bind Response sent for", device.getAddress());
            Message msg = mStateMachine.obtainMessage(HeadsetStateMachine.BIND_RESPONSE);
            msg.obj = device;
            msg.arg1 = ind_id;
            msg.arg2 = ind_status ? 1 : 0;
            mStateMachine.sendMessage(msg);
            return true;
        }
        return false;
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        if (mStateMachine != null) {
            mStateMachine.dump(sb);
        }
    }
}
