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

package com.android.bluetooth.mapclient;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothMapClient;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MapClientService extends ProfileService {
    private static final String TAG = "MapClientService";

    static final boolean DBG = false;
    static final boolean VDBG = false;

    private static final int MAXIMUM_CONNECTED_DEVICES = 1;

    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    MceStateMachine mMceStateMachine;
    private MnsService mMnsServer;
    private BluetoothAdapter mAdapter;
    private static MapClientService sMapClientService;


    public static synchronized MapClientService getMapClientService() {
        if (sMapClientService != null && sMapClientService.isAvailable()) {
            if (DBG) Log.d(TAG, "getMapClientService(): returning " + sMapClientService);
            return sMapClientService;
        }
        if (DBG) {
            if (sMapClientService == null) {
                Log.d(TAG, "getMapClientService(): service is NULL");
            } else if (!(sMapClientService.isAvailable())) {
                Log.d(TAG, "getMapClientService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setService(MapClientService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) Log.d(TAG, "setMapMceService(): replacing old instance: " + sMapClientService);
            sMapClientService = instance;
        } else {
            if (DBG) {
                if (sMapClientService == null) {
                    Log.d(TAG, "setA2dpService(): service not available");
                } else if (!sMapClientService.isAvailable()) {
                    Log.d(TAG, "setA2dpService(): service is cleaning up");
                }
            }
        }
    }

    public synchronized boolean connect(BluetoothDevice device) {
        Log.d(TAG, "MAP Mce connect " + device.toString());
        return mMceStateMachine.connect(device);
    }

    public synchronized boolean disconnect(BluetoothDevice device) {
        Log.d(TAG, "MAP Mce disconnect " + device.toString());
        return mMceStateMachine.disconnect(device);
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return getDevicesMatchingConnectionStates(new int[]{BluetoothAdapter.STATE_CONNECTED});
    }

    public synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        Log.d(TAG, "getDevicesMatchingConnectionStates" + Arrays.toString(states));
        List<BluetoothDevice> deviceList = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        for (BluetoothDevice device : bondedDevices) {
            connectionState = getConnectionState(device);
            Log.d(TAG, "Device: " + device + "State: " + connectionState);
            for (int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        Log.d(TAG, deviceList.toString());
        return deviceList;
    }

    public synchronized int getConnectionState(BluetoothDevice device) {
        if (mMceStateMachine != null && device.equals(mMceStateMachine.getDevice())) {
            return mMceStateMachine.getState();
        } else {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothMapClientPriorityKey(device.getAddress()),
                priority);
        if (VDBG) Log.v(TAG, "Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        int priority = Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothMapClientPriorityKey(device.getAddress()),
                BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    public synchronized boolean sendMessage(BluetoothDevice device, Uri[] contacts, String message,
            PendingIntent sentIntent, PendingIntent deliveredIntent) {
        if (mMceStateMachine != null && device.equals(mMceStateMachine.getDevice())) {
            return mMceStateMachine.sendMapMessage(contacts, message, sentIntent, deliveredIntent);
        } else {
            return false;
        }

    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new Binder(this);
    }

    @Override
    protected boolean start() {
        if (DBG) Log.d(TAG, "start()");
        setService(this);

        if (mMnsServer == null) {
            mMnsServer = new MnsService(this);
        }
        if (mMceStateMachine == null) {
            mMceStateMachine = new MceStateMachine(this);
        }

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mStartError = false;
        return !mStartError;
    }

    @Override
    protected synchronized boolean stop() {
        if (DBG) Log.d(TAG, "stop()");
        if (mMnsServer != null) {
            mMnsServer.stop();
        }
        if (mMceStateMachine.getState() == BluetoothAdapter.STATE_CONNECTED) {
            mMceStateMachine.disconnect(mMceStateMachine.getDevice());
        }
        mMceStateMachine.doQuit();
        return true;
    }

    public boolean cleanup() {
        if (DBG) Log.d(TAG, "cleanup()");
        return true;
    }

    public synchronized boolean getUnreadMessages(BluetoothDevice device) {
        if (mMceStateMachine != null && device.equals(mMceStateMachine.getDevice())) {
            return mMceStateMachine.getUnreadMessages();
        } else {
            return false;
        }
    }

    @Override
    public synchronized void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "StateMachine: " + mMceStateMachine.toString());
    }

    //Binder object: Must be static class or memory leak may occur
    /**
     * This class implements the IClient interface - or actually it validates the
     * preconditions for calling the actual functionality in the MapClientService, and calls it.
     */
    private static class Binder extends IBluetoothMapClient.Stub
            implements IProfileServiceBinder {
        private MapClientService mService;

        Binder(MapClientService service) {
            if (VDBG) Log.v(TAG, "Binder()");
            mService = service;
        }

        private MapClientService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "MAP call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                mService.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                        "Need BLUETOOTH permission");
                return mService;
            }
            return null;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        public boolean isConnected(BluetoothDevice device) {
            if (VDBG) Log.v(TAG, "isConnected()");
            MapClientService service = getService();
            if (service == null) return false;
            return service.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED;
        }

        public boolean connect(BluetoothDevice device) {
            if (VDBG) Log.v(TAG, "connect()");
            MapClientService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            if (VDBG) Log.v(TAG, "disconnect()");
            MapClientService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            if (VDBG) Log.v(TAG, "getConnectedDevices()");
            MapClientService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            if (VDBG) Log.v(TAG, "getDevicesMatchingConnectionStates()");
            MapClientService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            if (VDBG) Log.v(TAG, "getConnectionState()");
            MapClientService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            MapClientService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            MapClientService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }

        public boolean sendMessage(BluetoothDevice device, Uri[] contacts, String message,
                PendingIntent sentIntent, PendingIntent deliveredIntent) {
            MapClientService service = getService();
            if (service == null) return false;
            Log.d(TAG, "Checking Permission of sendMessage");
            mService.enforceCallingOrSelfPermission(Manifest.permission.SEND_SMS,
                    "Need SEND_SMS permission");

            return service.sendMessage(device, contacts, message, sentIntent, deliveredIntent);
        }

        public boolean getUnreadMessages(BluetoothDevice device) {
            MapClientService service = getService();
            if (service == null) return false;
            mService.enforceCallingOrSelfPermission(Manifest.permission.READ_SMS,
                    "Need READ_SMS permission");
            return service.getUnreadMessages(device);
        }
    }
}
