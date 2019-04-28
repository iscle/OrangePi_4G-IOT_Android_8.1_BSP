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

package com.android.bluetooth.hid;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothInputDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides Bluetooth Hid Host profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class HidService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "HidService";

    private Map<BluetoothDevice, Integer> mInputDevices;
    private boolean mNativeAvailable;
    private static HidService sHidService;
    private BluetoothDevice mTargetDevice = null;

    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 3;
    private static final int MESSAGE_GET_PROTOCOL_MODE = 4;
    private static final int MESSAGE_VIRTUAL_UNPLUG = 5;
    private static final int MESSAGE_ON_GET_PROTOCOL_MODE = 6;
    private static final int MESSAGE_SET_PROTOCOL_MODE = 7;
    private static final int MESSAGE_GET_REPORT = 8;
    private static final int MESSAGE_ON_GET_REPORT = 9;
    private static final int MESSAGE_SET_REPORT = 10;
    private static final int MESSAGE_SEND_DATA = 11;
    private static final int MESSAGE_ON_VIRTUAL_UNPLUG = 12;
    private static final int MESSAGE_ON_HANDSHAKE = 13;
    private static final int MESSAGE_GET_IDLE_TIME = 14;
    private static final int MESSAGE_ON_GET_IDLE_TIME = 15;
    private static final int MESSAGE_SET_IDLE_TIME = 16;

    static {
        classInitNative();
    }

    public String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothInputDeviceBinder(this);
    }

    protected boolean start() {
        mInputDevices = Collections.synchronizedMap(new HashMap<BluetoothDevice, Integer>());
        initializeNative();
        mNativeAvailable=true;
        setHidService(this);
        return true;
    }

    protected boolean stop() {
        if (DBG) log("Stopping Bluetooth HidService");
        return true;
    }

    protected boolean cleanup() {
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }

        if(mInputDevices != null) {
            for (BluetoothDevice device : mInputDevices.keySet()) {
                int inputDeviceState = getConnectionState(device);
                if (inputDeviceState != BluetoothProfile.STATE_DISCONNECTED) {
                    broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                }
            }
            mInputDevices.clear();
        }
        clearHidService();
        return true;
    }

    public static synchronized HidService getHidService(){
        if (sHidService != null && sHidService.isAvailable()) {
            if (DBG) Log.d(TAG, "getHidService(): returning " + sHidService);
            return sHidService;
        }
        if (DBG)  {
            if (sHidService == null) {
                Log.d(TAG, "getHidService(): service is NULL");
            } else if (!(sHidService.isAvailable())) {
                Log.d(TAG,"getHidService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setHidService(HidService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setHidService(): set to: " + sHidService);
            sHidService = instance;
        } else {
            if (DBG)  {
                if (sHidService == null) {
                    Log.d(TAG, "setHidService(): service not available");
                } else if (!sHidService.isAvailable()) {
                    Log.d(TAG,"setHidService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearHidService() {
        sHidService = null;
    }


    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            /// M : ALPS02877642 Ignore incoming events BT is off or turning-off @ {
            if (!isAvailable()) {
                Log.e(TAG, "handleMessage: service is null or unavailable");
                return;
            }
            /// @}
            switch (msg.what) {
                case MESSAGE_CONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!connectHidNative(Utils.getByteAddress(device)) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }
                    mTargetDevice = device;
                }
                    break;
                case MESSAGE_DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!disconnectHidNative(Utils.getByteAddress(device)) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING);
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }
                }
                    break;
                case MESSAGE_CONNECT_STATE_CHANGED:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int halState = msg.arg1;
                    Integer prevStateInteger = mInputDevices.get(device);
                    int prevState = (prevStateInteger == null) ?
                        BluetoothInputDevice.STATE_DISCONNECTED :prevStateInteger;
                    if(DBG) Log.d(TAG, "MESSAGE_CONNECT_STATE_CHANGED newState:"+
                        convertHalState(halState)+", prevState:"+prevState);
                    /**M:fix bug:ALPS01008739 @{*/
                    if (okToBroadcastConnectState(device, halState, prevState)) {
                        broadcastConnectionState(device, convertHalState(halState));
                    }
                    /**@}*/
                    if (halState == CONN_STATE_CONNECTED &&
                        (mTargetDevice != null && mTargetDevice.equals(device))) {
                        mTargetDevice = null;
                        // local device originated connection to hid device, move out
                        // of quiet mode
                        AdapterService adapterService = AdapterService.getAdapterService();
                        adapterService.enable(false);
                    }
                }
                    break;
                case MESSAGE_GET_PROTOCOL_MODE:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if(!getProtocolModeNative(Utils.getByteAddress(device)) ) {
                        Log.e(TAG, "Error: get protocol mode native returns false");
                    }
                }
                break;

                case MESSAGE_ON_GET_PROTOCOL_MODE:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int protocolMode = msg.arg1;
                    broadcastProtocolMode(device, protocolMode);
                }
                break;
                case MESSAGE_VIRTUAL_UNPLUG:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if(!virtualUnPlugNative(Utils.getByteAddress(device))) {
                        Log.e(TAG, "Error: virtual unplug native returns false");
                    }
                }
                break;
                case MESSAGE_SET_PROTOCOL_MODE:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    byte protocolMode = (byte) msg.arg1;
                    log("sending set protocol mode(" + protocolMode + ")");
                    if(!setProtocolModeNative(Utils.getByteAddress(device), protocolMode)) {
                        Log.e(TAG, "Error: set protocol mode native returns false");
                    }
                }
                break;
                case MESSAGE_GET_REPORT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    Bundle data = msg.getData();
                    byte reportType = data.getByte(BluetoothInputDevice.EXTRA_REPORT_TYPE);
                    byte reportId = data.getByte(BluetoothInputDevice.EXTRA_REPORT_ID);
                    int bufferSize = data.getInt(BluetoothInputDevice.EXTRA_REPORT_BUFFER_SIZE);
                    if(!getReportNative(Utils.getByteAddress(device), reportType, reportId, bufferSize)) {
                        Log.e(TAG, "Error: get report native returns false");
                    }
                }
                break;
                case MESSAGE_ON_GET_REPORT:
                {
                    BluetoothDevice device = getDevice((byte[])msg.obj);
                    Bundle data = msg.getData();
                    byte[] report = data.getByteArray(BluetoothInputDevice.EXTRA_REPORT);
                    int bufferSize = data.getInt(BluetoothInputDevice.EXTRA_REPORT_BUFFER_SIZE);
                    broadcastReport(device, report, bufferSize);
                }
                break;
                case MESSAGE_ON_HANDSHAKE:
                {
                    BluetoothDevice device = getDevice((byte[])msg.obj);
                    int status = msg.arg1;
                    broadcastHandshake(device, status);
                }
                break;
                case MESSAGE_SET_REPORT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    Bundle data = msg.getData();
                    byte reportType = data.getByte(BluetoothInputDevice.EXTRA_REPORT_TYPE);
                    String report = data.getString(BluetoothInputDevice.EXTRA_REPORT);
                    if(!setReportNative(Utils.getByteAddress(device), reportType, report)) {
                        Log.e(TAG, "Error: set report native returns false");
                    }
                }
                break;
                case MESSAGE_SEND_DATA:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    Bundle data = msg.getData();
                    String report = data.getString(BluetoothInputDevice.EXTRA_REPORT);
                    if(!sendDataNative(Utils.getByteAddress(device), report)) {
                        Log.e(TAG, "Error: send data native returns false");
                    }
                }
                break;
                case MESSAGE_ON_VIRTUAL_UNPLUG:
                {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int status = msg.arg1;
                    broadcastVirtualUnplugStatus(device, status);
                }
                break;
                case MESSAGE_GET_IDLE_TIME: {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!getIdleTimeNative(Utils.getByteAddress(device))) {
                        Log.e(TAG, "Error: get idle time native returns false");
                    }
                } break;
                case MESSAGE_ON_GET_IDLE_TIME: {
                    BluetoothDevice device = getDevice((byte[]) msg.obj);
                    int idleTime = msg.arg1;
                    broadcastIdleTime(device, idleTime);
                } break;
                case MESSAGE_SET_IDLE_TIME: {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    Bundle data = msg.getData();
                    byte idleTime = data.getByte(BluetoothInputDevice.EXTRA_IDLE_TIME);
                    if (!setIdleTimeNative(Utils.getByteAddress(device), idleTime)) {
                        Log.e(TAG, "Error: get idle time native returns false");
                    }
                } break;
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothInputDeviceBinder extends IBluetoothInputDevice.Stub implements IProfileServiceBinder{
        private HidService mService;
        public BluetoothInputDeviceBinder(HidService svc) {
            mService = svc;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        private HidService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"InputDevice call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean connect(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public int getConnectionState(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return BluetoothInputDevice.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return getDevicesMatchingConnectionStates(
                    new int[] {BluetoothProfile.STATE_CONNECTED});
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            HidService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            HidService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        /* The following APIs regarding test app for compliance */
        public boolean getProtocolMode(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return false;
            return service.getProtocolMode(device);
        }

        public boolean virtualUnplug(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return false;
            return service.virtualUnplug(device);
        }

        public boolean setProtocolMode(BluetoothDevice device, int protocolMode) {
            HidService service = getService();
            if (service == null) return false;
            return service.setProtocolMode(device, protocolMode);
        }
        
        public boolean getReport(BluetoothDevice device, byte reportType, byte reportId, int bufferSize) {
            HidService service = getService();
            if (service == null) return false;
            return service.getReport(device, reportType, reportId, bufferSize) ;
        }

        public boolean setReport(BluetoothDevice device, byte reportType, String report) {
            HidService service = getService();
            if (service == null) return false;
            return service.setReport(device, reportType, report);
        }

        public boolean sendData(BluetoothDevice device, String report) {
            HidService service = getService();
            if (service == null) return false;
            return service.sendData(device, report);
        }

        public boolean setIdleTime(BluetoothDevice device, byte idleTime) {
            HidService service = getService();
            if (service == null) return false;
            return service.setIdleTime(device, idleTime);
        }

        public boolean getIdleTime(BluetoothDevice device) {
            HidService service = getService();
            if (service == null) return false;
            return service.getIdleTime(device);
        }
    };

    //APIs
    boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (getConnectionState(device) != BluetoothInputDevice.STATE_DISCONNECTED) {
            Log.e(TAG, "Hid Device not disconnected: " + device);
            return false;
        }
        if (getPriority(device) == BluetoothInputDevice.PRIORITY_OFF) {
            Log.e(TAG, "Hid Device PRIORITY_OFF: " + device);
            return false;
        }

        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT, device);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    int getConnectionState(BluetoothDevice device) {
        if (mInputDevices.get(device) == null) {
            return BluetoothInputDevice.STATE_DISCONNECTED;
        }
        return mInputDevices.get(device);
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> inputDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mInputDevices.keySet()) {
            int inputDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == inputDeviceState) {
                    inputDevices.add(device);
                    break;
                }
            }
        }
        return inputDevices;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothInputDevicePriorityKey(device.getAddress()),
            priority);
        if (DBG) Log.d(TAG,"Saved priority " + device + " = " + priority);
        return true;
    }

    public  int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothInputDevicePriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    /* The following APIs regarding test app for compliance */
    boolean getProtocolMode(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_PROTOCOL_MODE,device);
        mHandler.sendMessage(msg);
        return true;
        /* String objectPath = getObjectPathFromAddress(device.getAddress());
            return getProtocolModeInputDeviceNative(objectPath);*/
    }

    boolean virtualUnplug(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_VIRTUAL_UNPLUG,device);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean setProtocolMode(BluetoothDevice device, int protocolMode) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_SET_PROTOCOL_MODE);
        msg.obj = device;
        msg.arg1 = protocolMode;
        mHandler.sendMessage(msg);
        return true ;
    }

    boolean getReport(BluetoothDevice device, byte reportType, byte reportId, int bufferSize) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                       "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_REPORT);
        msg.obj = device;
        Bundle data = new Bundle();
        data.putByte(BluetoothInputDevice.EXTRA_REPORT_TYPE, reportType);
        data.putByte(BluetoothInputDevice.EXTRA_REPORT_ID, reportId);
        data.putInt(BluetoothInputDevice.EXTRA_REPORT_BUFFER_SIZE, bufferSize);
        msg.setData(data);
        mHandler.sendMessage(msg);
        return true ;
    }

    boolean setReport(BluetoothDevice device, byte reportType, String report) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                   "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_SET_REPORT);
        msg.obj = device;
        Bundle data = new Bundle();
        data.putByte(BluetoothInputDevice.EXTRA_REPORT_TYPE, reportType);
        data.putString(BluetoothInputDevice.EXTRA_REPORT, report);
        msg.setData(data);
        mHandler.sendMessage(msg);
        return true ;

    }

    boolean sendData(BluetoothDevice device, String report) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                   "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }

        return sendDataNative(Utils.getByteAddress(device), report);
    }

    boolean getIdleTime(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_IDLE_TIME, device);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean setIdleTime(BluetoothDevice device, byte idleTime) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int state = this.getConnectionState(device);
        if (state != BluetoothInputDevice.STATE_CONNECTED) {
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_SET_IDLE_TIME);
        msg.obj = device;
        Bundle data = new Bundle();
        data.putByte(BluetoothInputDevice.EXTRA_IDLE_TIME, idleTime);
        msg.setData(data);
        mHandler.sendMessage(msg);
        return true;
    }

    private void onGetProtocolMode(byte[] address, int mode) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_GET_PROTOCOL_MODE);
        msg.obj = address;
        msg.arg1 = mode;
        mHandler.sendMessage(msg);
    }

    private void onGetIdleTime(byte[] address, int idleTime) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_GET_IDLE_TIME);
        msg.obj = address;
        msg.arg1 = idleTime;
        mHandler.sendMessage(msg);
    }

    private void onGetReport(byte[] address, byte[] report, int rpt_size) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_GET_REPORT);
        msg.obj = address;
        Bundle data = new Bundle();
        data.putByteArray(BluetoothInputDevice.EXTRA_REPORT, report);
        data.putInt(BluetoothInputDevice.EXTRA_REPORT_BUFFER_SIZE, rpt_size);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void onHandshake(byte[] address, int status) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_HANDSHAKE);
        msg.obj = address;
        msg.arg1 = status;
        mHandler.sendMessage(msg);
    }

    private void onVirtualUnplug(byte[] address, int status) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_VIRTUAL_UNPLUG);
        msg.obj = address;
        msg.arg1 = status;
        mHandler.sendMessage(msg);
    }

    private void onConnectStateChanged(byte[] address, int state) {
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED);
        msg.obj = address;
        msg.arg1 = state;
        mHandler.sendMessage(msg);
    }

    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device, int newState) {
        Integer prevStateInteger = mInputDevices.get(device);
        int prevState = (prevStateInteger == null) ? BluetoothInputDevice.STATE_DISCONNECTED : 
                                                     prevStateInteger;
        if (prevState == newState) {
            Log.w(TAG, "no state change: " + newState);
            return;
        }
        mInputDevices.put(device, newState);

        /* Notifying the connection state change of the profile before sending the intent for
           connection state change, as it was causing a race condition, with the UI not being
           updated with the correct connection state. */
        log("Connection state " + device + ": " + prevState + "->" + newState);
        Intent intent = new Intent(BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void broadcastHandshake(BluetoothDevice device, int status) {
        Intent intent = new Intent(BluetoothInputDevice.ACTION_HANDSHAKE);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothInputDevice.EXTRA_STATUS, status);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void broadcastProtocolMode(BluetoothDevice device, int protocolMode) {
        Intent intent = new Intent(BluetoothInputDevice.ACTION_PROTOCOL_MODE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothInputDevice.EXTRA_PROTOCOL_MODE, protocolMode);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
        if (DBG) log("Protocol Mode (" + device + "): " + protocolMode);
    }

    private void broadcastReport(BluetoothDevice device, byte[] report, int rpt_size) {
        Intent intent = new Intent(BluetoothInputDevice.ACTION_REPORT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothInputDevice.EXTRA_REPORT, report);
        intent.putExtra(BluetoothInputDevice.EXTRA_REPORT_BUFFER_SIZE, rpt_size);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void broadcastVirtualUnplugStatus(BluetoothDevice device, int status) {
        Intent intent = new Intent(BluetoothInputDevice.ACTION_VIRTUAL_UNPLUG_STATUS);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothInputDevice.EXTRA_VIRTUAL_UNPLUG_STATUS, status);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void broadcastIdleTime(BluetoothDevice device, int idleTime) {
        Intent intent = new Intent(BluetoothInputDevice.ACTION_IDLE_TIME_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothInputDevice.EXTRA_IDLE_TIME, idleTime);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, BLUETOOTH_PERM);
        if (DBG) log("Idle time (" + device + "): " + idleTime);
    }

    private boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        //check if it is inbound connection in Quiet mode, priority and Bond status
        //to decide if its ok to allow this connection
        if((adapterService == null)||
           ((adapterService.isQuietModeEnabled()) &&(mTargetDevice == null)) ||
           (BluetoothProfile.PRIORITY_OFF == getPriority(device)) ||
           (device.getBondState() == BluetoothDevice.BOND_NONE))
            return false;

        return true;
    }
    /**M:fix bug:ALPS01008739 @{*/
    //In the case unCheck the input device check box in setting,input device
    //should not be allowed to connect success,but now input device can connect success through
    //authorize,so in this case,should disconnect the improper connection
    private boolean okToBroadcastConnectState(BluetoothDevice device, int halState, int prevState) {
        if (halState == CONN_STATE_CONNECTED
                && (prevState == BluetoothInputDevice.STATE_DISCONNECTED
                    || prevState == BluetoothInputDevice.STATE_CONNECTING)
                && (!okToConnect(device))) {
            if (DBG) {
                Log.i(TAG, "Incoming HID connection rejected");
            }
            disconnectHidNative(Utils.getByteAddress(device));
            return false;
        } else if (halState == BluetoothInputDevice.STATE_CONNECTING &&
                   BluetoothProfile.PRIORITY_OFF == getPriority(device)) {
            if (DBG) {
                Log.i(TAG, "when authorize priority off:not show connecting");
            }
            return false;
        } else if (halState == BluetoothInputDevice.STATE_DISCONNECTING
                && BluetoothProfile.PRIORITY_OFF == getPriority(device)) {
            if (DBG) {
              Log.i(TAG, "when authorize priority off:not show disconnecting");
            }
            return false;

        } else {
            return true;
        }
    }
    /**@}*/
    private static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "bad hid connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "mTargetDevice: " + mTargetDevice);
        println(sb, "mInputDevices:");
        for (BluetoothDevice device : mInputDevices.keySet()) {
            println(sb, "  " + device + " : " + mInputDevices.get(device));
        }
    }

    // Constants matching Hal header file bt_hh.h
    // bthh_connection_state_t
    private final static int CONN_STATE_CONNECTED = 0;
    private final static int CONN_STATE_CONNECTING = 1;
    private final static int CONN_STATE_DISCONNECTED = 2;
    private final static int CONN_STATE_DISCONNECTING = 3;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean connectHidNative(byte[] btAddress);
    private native boolean disconnectHidNative(byte[] btAddress);
    private native boolean getProtocolModeNative(byte[] btAddress);
    private native boolean virtualUnPlugNative(byte[] btAddress);
    private native boolean setProtocolModeNative(byte[] btAddress, byte protocolMode);
    private native boolean getReportNative(byte[]btAddress, byte reportType, byte reportId, int bufferSize);
    private native boolean setReportNative(byte[] btAddress, byte reportType, String report);
    private native boolean sendDataNative(byte[] btAddress, String report);
    private native boolean setIdleTimeNative(byte[] btAddress, byte idleTime);
    private native boolean getIdleTimeNative(byte[] btAddress);
}
