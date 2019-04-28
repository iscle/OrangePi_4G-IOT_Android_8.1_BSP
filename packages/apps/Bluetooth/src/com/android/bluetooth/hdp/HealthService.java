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

package com.android.bluetooth.hdp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHealth;
import android.bluetooth.BluetoothHealthAppConfiguration;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothHealth;
import android.bluetooth.IBluetoothHealthCallback;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.android.bluetooth.Utils;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * Provides Bluetooth Health Device profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class HealthService extends ProfileService {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private static final String TAG="HealthService";

    private List<HealthChannel> mHealthChannels;
    private Map <BluetoothHealthAppConfiguration, AppInfo> mApps;
    private Map <BluetoothDevice, Integer> mHealthDevices;
    private boolean mNativeAvailable;
    private HealthServiceMessageHandler mHandler;
    private static final int MESSAGE_REGISTER_APPLICATION = 1;
    private static final int MESSAGE_UNREGISTER_APPLICATION = 2;
    private static final int MESSAGE_CONNECT_CHANNEL = 3;
    private static final int MESSAGE_DISCONNECT_CHANNEL = 4;
    private static final int MESSAGE_APP_REGISTRATION_CALLBACK = 11;
    private static final int MESSAGE_CHANNEL_STATE_CALLBACK = 12;

    static {
        classInitNative();
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothHealthBinder(this);
    }

    protected boolean start() {
        mHealthChannels = Collections.synchronizedList(new ArrayList<HealthChannel>());
        mApps = Collections.synchronizedMap(new HashMap<BluetoothHealthAppConfiguration,
                                            AppInfo>());
        mHealthDevices = Collections.synchronizedMap(new HashMap<BluetoothDevice, Integer>());

        HandlerThread thread = new HandlerThread("BluetoothHdpHandler");
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new HealthServiceMessageHandler(looper);
        initializeNative();
        mNativeAvailable=true;
        return true;
    }

    protected boolean stop() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            Looper looper = mHandler.getLooper();
            if (looper != null) {
                looper.quit();
            }
        }
        cleanupApps();
        return true;
    }

    private void cleanupApps(){
        if (mApps != null) {
            Iterator<Map.Entry<BluetoothHealthAppConfiguration,AppInfo>> it
                        = mApps.entrySet().iterator();
            while (it.hasNext()) {
               Map.Entry<BluetoothHealthAppConfiguration,AppInfo> entry = it.next();
               AppInfo appInfo = entry.getValue();
               if (appInfo != null)
                   appInfo.cleanup();
               it.remove();
            }
        }
    }
    protected boolean cleanup() {
        mHandler = null;
        //Cleanup native
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }
        if(mHealthChannels != null) {
            mHealthChannels.clear();
        }
        if(mHealthDevices != null) {
            mHealthDevices.clear();
        }
        if(mApps != null) {
            mApps.clear();
        }
        return true;
    }

    private final class HealthServiceMessageHandler extends Handler {
        private HealthServiceMessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DBG) log("HealthService Handler msg: " + msg.what);
            switch (msg.what) {
                case MESSAGE_REGISTER_APPLICATION:
                {
                    BluetoothHealthAppConfiguration appConfig =
                        (BluetoothHealthAppConfiguration) msg.obj;
                    AppInfo appInfo = mApps.get(appConfig);
                    if (appInfo == null) break;
                    int halRole = convertRoleToHal(appConfig.getRole());
                    int halChannelType = convertChannelTypeToHal(appConfig.getChannelType());
                    if (VDBG) log("register datatype: " + appConfig.getDataType() + " role: " +
                                 halRole + " name: " + appConfig.getName() + " channeltype: " +
                                 halChannelType);
                    int appId = registerHealthAppNative(appConfig.getDataType(), halRole,
                                                        appConfig.getName(), halChannelType);
                    if (appId == -1) {
                        callStatusCallback(appConfig,
                                           BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE);
                        appInfo.cleanup();
                        mApps.remove(appConfig);
                    } else {
                        //link to death with a recipient object to implement binderDead()
                        appInfo.mRcpObj = new BluetoothHealthDeathRecipient(HealthService.this,appConfig);
                        IBinder binder = appInfo.mCallback.asBinder();
                        try {
                            binder.linkToDeath(appInfo.mRcpObj,0);
                        } catch (RemoteException e) {
                            Log.e(TAG,"LinktoDeath Exception:"+e);
                        }
                        appInfo.mAppId = appId;
                        callStatusCallback(appConfig,
                                           BluetoothHealth.APP_CONFIG_REGISTRATION_SUCCESS);
                    }
                }
                    break;
                case MESSAGE_UNREGISTER_APPLICATION:
                {
                    BluetoothHealthAppConfiguration appConfig =
                        (BluetoothHealthAppConfiguration) msg.obj;
                    int appId = (mApps.get(appConfig)).mAppId;
                    if (!unregisterHealthAppNative(appId)) {
                        Log.e(TAG, "Failed to unregister application: id: " + appId);
                        callStatusCallback(appConfig,
                                           BluetoothHealth.APP_CONFIG_UNREGISTRATION_FAILURE);
                    }
                }
                    break;
                case MESSAGE_CONNECT_CHANNEL:
                {
                    HealthChannel chan = (HealthChannel) msg.obj;
                    byte[] devAddr = Utils.getByteAddress(chan.mDevice);
                    int appId = (mApps.get(chan.mConfig)).mAppId;
                    chan.mChannelId = connectChannelNative(devAddr, appId);
                    if (chan.mChannelId == -1) {
                        callHealthChannelCallback(chan.mConfig, chan.mDevice,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTING,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTED,
                                                  chan.mChannelFd, chan.mChannelId);
                        callHealthChannelCallback(chan.mConfig, chan.mDevice,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTED,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTING,
                                                  chan.mChannelFd, chan.mChannelId);
                    }
                }
                    break;
                case MESSAGE_DISCONNECT_CHANNEL:
                {
                    HealthChannel chan = (HealthChannel) msg.obj;
                    if (!disconnectChannelNative(chan.mChannelId)) {
                        callHealthChannelCallback(chan.mConfig, chan.mDevice,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTING,
                                                  BluetoothHealth.STATE_CHANNEL_CONNECTED,
                                                  chan.mChannelFd, chan.mChannelId);
                        callHealthChannelCallback(chan.mConfig, chan.mDevice,
                                                  BluetoothHealth.STATE_CHANNEL_CONNECTED,
                                                  BluetoothHealth.STATE_CHANNEL_DISCONNECTING,
                                                  chan.mChannelFd, chan.mChannelId);
                    }
                }
                    break;
                case MESSAGE_APP_REGISTRATION_CALLBACK:
                {
                    BluetoothHealthAppConfiguration appConfig = findAppConfigByAppId(msg.arg1);
                    if (appConfig == null) break;

                    int regStatus = convertHalRegStatus(msg.arg2);
                    callStatusCallback(appConfig, regStatus);
                    if (regStatus == BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE ||
                        regStatus == BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS) {
                        //unlink to death once app is unregistered
                        AppInfo appInfo = mApps.get(appConfig);
                        appInfo.cleanup();
                        mApps.remove(appConfig);
                    }
                }
                    break;
                case MESSAGE_CHANNEL_STATE_CALLBACK:
                {
                    ChannelStateEvent channelStateEvent = (ChannelStateEvent) msg.obj;
                    HealthChannel chan = findChannelById(channelStateEvent.mChannelId);
                    BluetoothHealthAppConfiguration appConfig =
                            findAppConfigByAppId(channelStateEvent.mAppId);
                    int newState;
                    newState = convertHalChannelState(channelStateEvent.mState);
                    if (newState  ==  BluetoothHealth.STATE_CHANNEL_DISCONNECTED &&
                        appConfig == null) {
                        Log.e(TAG,"Disconnected for non existing app");
                        break;
                    }
                    if (chan == null) {
                        // incoming connection

                        BluetoothDevice device = getDevice(channelStateEvent.mAddr);
                        chan = new HealthChannel(device, appConfig, appConfig.getChannelType());
                        chan.mChannelId = channelStateEvent.mChannelId;
                        mHealthChannels.add(chan);
                    }
                    newState = convertHalChannelState(channelStateEvent.mState);
                    if (newState == BluetoothHealth.STATE_CHANNEL_CONNECTED) {
                        try {
                            chan.mChannelFd = ParcelFileDescriptor.dup(channelStateEvent.mFd);
                        } catch (IOException e) {
                            Log.e(TAG, "failed to dup ParcelFileDescriptor");
                            break;
                        }
                    }
                    /*set the channel fd to null if channel state isnot equal to connected*/
                    else{
                        chan.mChannelFd = null;
                    }
                    callHealthChannelCallback(chan.mConfig, chan.mDevice, newState,
                                              chan.mState, chan.mChannelFd, chan.mChannelId);
                    chan.mState = newState;
                    if (channelStateEvent.mState == CONN_STATE_DESTROYED ||
                        channelStateEvent.mState == CONN_STATE_DISCONNECTED) {
                        mHealthChannels.remove(chan);
                        chan = null;
                    }
                }
                    break;
            }
        }
    }

//Handler for DeathReceipient
    private static class BluetoothHealthDeathRecipient implements IBinder.DeathRecipient{
        private BluetoothHealthAppConfiguration mConfig;
        private HealthService mService;

        public BluetoothHealthDeathRecipient(HealthService service, BluetoothHealthAppConfiguration config) {
            mService = service;
            mConfig = config;
        }

        public void binderDied() {
            if (DBG) Log.d(TAG,"Binder is dead.");
            mService.unregisterAppConfiguration(mConfig);
        }

        public void cleanup(){
            mService = null;
            mConfig = null;
        }
    }

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothHealthBinder extends IBluetoothHealth.Stub implements IProfileServiceBinder {
        private HealthService mService;

        public BluetoothHealthBinder(HealthService svc) {
            mService = svc;
        }

        public boolean cleanup()  {
            mService = null;
            return true;
        }

        private HealthService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"Health call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        public boolean registerAppConfiguration(BluetoothHealthAppConfiguration config,
                                                IBluetoothHealthCallback callback) {
            HealthService service = getService();
            if (service == null) return false;
            return service.registerAppConfiguration(config, callback);
        }

        public boolean unregisterAppConfiguration(BluetoothHealthAppConfiguration config) {
            HealthService service = getService();
            if (service == null) return false;
            return service.unregisterAppConfiguration(config);
        }

        public boolean connectChannelToSource(BluetoothDevice device,
                                              BluetoothHealthAppConfiguration config) {
            HealthService service = getService();
            if (service == null) return false;
            return service.connectChannelToSource(device, config);
        }

        public boolean connectChannelToSink(BluetoothDevice device,
                           BluetoothHealthAppConfiguration config, int channelType) {
            HealthService service = getService();
            if (service == null) return false;
            return service.connectChannelToSink(device, config, channelType);
        }

        public boolean disconnectChannel(BluetoothDevice device,
                                         BluetoothHealthAppConfiguration config, int channelId) {
            HealthService service = getService();
            if (service == null) return false;
            return service.disconnectChannel(device, config, channelId);
        }

        public ParcelFileDescriptor getMainChannelFd(BluetoothDevice device,
                                                     BluetoothHealthAppConfiguration config) {
            HealthService service = getService();
            if (service == null) return null;
            return service.getMainChannelFd(device, config);
        }

        public int getHealthDeviceConnectionState(BluetoothDevice device) {
            HealthService service = getService();
            if (service == null) return BluetoothHealth.STATE_DISCONNECTED;
            return service.getHealthDeviceConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedHealthDevices() {
            HealthService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice> (0);
            return service.getConnectedHealthDevices();
        }

        public List<BluetoothDevice> getHealthDevicesMatchingConnectionStates(int[] states) {
            HealthService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice> (0);
            return service.getHealthDevicesMatchingConnectionStates(states);
        }
    };

    boolean registerAppConfiguration(BluetoothHealthAppConfiguration config,
            IBluetoothHealthCallback callback) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                "Need BLUETOOTH permission");

        if (config == null) {
            Log.e(TAG, "Trying to use a null config for registration");
            return false;
        }

        if (mApps.get(config) != null) {
            if (DBG) Log.d(TAG, "Config has already been registered");
            return false;
        }
        mApps.put(config, new AppInfo(callback));
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_APPLICATION,config);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean unregisterAppConfiguration(BluetoothHealthAppConfiguration config) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (mApps.get(config) == null) {
            if (DBG) Log.d(TAG,"unregisterAppConfiguration: no app found");
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_APPLICATION,config);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean connectChannelToSource(BluetoothDevice device,
                                          BluetoothHealthAppConfiguration config) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return connectChannel(device, config, BluetoothHealth.CHANNEL_TYPE_ANY);
    }

    boolean connectChannelToSink(BluetoothDevice device,
                       BluetoothHealthAppConfiguration config, int channelType) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return connectChannel(device, config, channelType);
    }

    boolean disconnectChannel(BluetoothDevice device,
                                     BluetoothHealthAppConfiguration config, int channelId) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HealthChannel chan = findChannelById(channelId);
        if (chan == null) {
            if (DBG) Log.d(TAG,"disconnectChannel: no channel found");
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT_CHANNEL,chan);
        mHandler.sendMessage(msg);
        return true;
    }

    ParcelFileDescriptor getMainChannelFd(BluetoothDevice device,
                                                 BluetoothHealthAppConfiguration config) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        HealthChannel healthChan = null;
        for (HealthChannel chan: mHealthChannels) {
            if (chan.mDevice.equals(device) && chan.mConfig.equals(config)) {
                healthChan = chan;
            }
        }
        if (healthChan == null) {
            Log.e(TAG, "No channel found for device: " + device + " config: " + config);
            return null;
        }
        return healthChan.mChannelFd;
    }

    int getHealthDeviceConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return getConnectionState(device);
    }

    List<BluetoothDevice> getConnectedHealthDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = lookupHealthDevicesMatchingStates(
                new int[] {BluetoothHealth.STATE_CONNECTED});
        return devices;
    }

    List<BluetoothDevice> getHealthDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = lookupHealthDevicesMatchingStates(states);
        return devices;
    }

    private void onAppRegistrationState(int appId, int state) {
        Message msg = mHandler.obtainMessage(MESSAGE_APP_REGISTRATION_CALLBACK);
        msg.arg1 = appId;
        msg.arg2 = state;
        mHandler.sendMessage(msg);
    }

    private void onChannelStateChanged(int appId, byte[] addr, int cfgIndex,
                                       int channelId, int state, FileDescriptor pfd) {
        Message msg = mHandler.obtainMessage(MESSAGE_CHANNEL_STATE_CALLBACK);
        ChannelStateEvent channelStateEvent = new ChannelStateEvent(appId, addr, cfgIndex,
                                                                    channelId, state, pfd);
        msg.obj = channelStateEvent;
        mHandler.sendMessage(msg);
    }

    private String getStringChannelType(int type) {
        if (type == BluetoothHealth.CHANNEL_TYPE_RELIABLE) {
            return "Reliable";
        } else if (type == BluetoothHealth.CHANNEL_TYPE_STREAMING) {
            return "Streaming";
        } else {
            return "Any";
        }
    }

    private void callStatusCallback(BluetoothHealthAppConfiguration config, int status) {
        if (VDBG) log ("Health Device Application: " + config + " State Change: status:" + status);
        IBluetoothHealthCallback callback = (mApps.get(config)).mCallback;
        if (callback == null) {
            Log.e(TAG, "Callback object null");
        }

        try {
            callback.onHealthAppConfigurationStatusChange(config, status);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote Exception:" + e);
        }
    }

    private BluetoothHealthAppConfiguration findAppConfigByAppId(int appId) {
        BluetoothHealthAppConfiguration appConfig = null;
        for (Entry<BluetoothHealthAppConfiguration, AppInfo> e : mApps.entrySet()) {
            if (appId == (e.getValue()).mAppId) {
                appConfig = e.getKey();
                break;
            }
        }
        if (appConfig == null) {
            Log.e(TAG, "No appConfig found for " + appId);
        }
        return appConfig;
    }

    private int convertHalRegStatus(int halRegStatus) {
        switch (halRegStatus) {
            case APP_REG_STATE_REG_SUCCESS:
                return BluetoothHealth.APP_CONFIG_REGISTRATION_SUCCESS;
            case APP_REG_STATE_REG_FAILED:
                return BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE;
            case APP_REG_STATE_DEREG_SUCCESS:
                return BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS;
            case APP_REG_STATE_DEREG_FAILED:
                return BluetoothHealth.APP_CONFIG_UNREGISTRATION_FAILURE;
        }
        Log.e(TAG, "Unexpected App Registration state: " + halRegStatus);
        return BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE;
    }

    private int convertHalChannelState(int halChannelState) {
        switch (halChannelState) {
            case CONN_STATE_CONNECTED:
                return BluetoothHealth.STATE_CHANNEL_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothHealth.STATE_CHANNEL_CONNECTING;
            case CONN_STATE_DISCONNECTING:
                return BluetoothHealth.STATE_CHANNEL_DISCONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothHealth.STATE_CHANNEL_DISCONNECTED;
            case CONN_STATE_DESTROYED:
                // TODO(BT) add BluetoothHealth.STATE_CHANNEL_DESTROYED;
                return BluetoothHealth.STATE_CHANNEL_DISCONNECTED;
            default:
                Log.e(TAG, "Unexpected channel state: " + halChannelState);
                return BluetoothHealth.STATE_CHANNEL_DISCONNECTED;
        }
    }

    private boolean connectChannel(BluetoothDevice device,
                                   BluetoothHealthAppConfiguration config, int channelType) {
        if (mApps.get(config) == null) {
            Log.e(TAG, "connectChannel fail to get a app id from config");
            return false;
        }

        HealthChannel chan = new HealthChannel(device, config, channelType);

        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_CHANNEL);
        msg.obj = chan;
        mHandler.sendMessage(msg);

        return true;
    }

    private void callHealthChannelCallback(BluetoothHealthAppConfiguration config,
            BluetoothDevice device, int state, int prevState, ParcelFileDescriptor fd, int id) {
        broadcastHealthDeviceStateChange(device, state);

        log("Health Device Callback: " + device + " State Change: " + prevState + "->" +
                     state + ", config: " + config);

        ParcelFileDescriptor dupedFd = null;
        if (fd != null) {
            try {
                dupedFd = fd.dup();
            } catch (IOException e) {
                dupedFd = null;
                Log.e(TAG, "Exception while duping: " + e);
            }
        }

        IBluetoothHealthCallback callback = null;
        if (mApps.get(config) != null) {
            callback = (mApps.get(config)).mCallback;
        } else {
            log("Health Device Callback: mApps.get(config) is null");
            return;
        }

        if (callback == null) {
            Log.e(TAG, "No callback found for config: " + config);
            return;
        }

        try {
            callback.onHealthChannelStateChange(config, device, prevState, state, dupedFd, id);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote Exception:" + e);
        }
    }

    /**
     * This function sends the intent for the updates on the connection status to the remote device.
     * Note that multiple channels can be connected to the remote device by multiple applications.
     * This sends an intent for the update to the device connection status and not the channel
     * connection status. Only the following state transitions are possible:
     *
     * {@link BluetoothHealth#STATE_DISCONNECTED} to {@link BluetoothHealth#STATE_CONNECTING}
     * {@link BluetoothHealth#STATE_CONNECTING} to {@link BluetoothHealth#STATE_CONNECTED}
     * {@link BluetoothHealth#STATE_CONNECTED} to {@link BluetoothHealth#STATE_DISCONNECTING}
     * {@link BluetoothHealth#STATE_DISCONNECTING} to {@link BluetoothHealth#STATE_DISCONNECTED}
     * {@link BluetoothHealth#STATE_DISCONNECTED} to {@link BluetoothHealth#STATE_CONNECTED}
     * {@link BluetoothHealth#STATE_CONNECTED} to {@link BluetoothHealth#STATE_DISCONNECTED}
     * {@link BluetoothHealth#STATE_CONNECTING} to {{@link BluetoothHealth#STATE_DISCONNECTED}
     *
     * @param device
     * @param prevChannelState
     * @param newChannelState
     * @hide
     */
    private void broadcastHealthDeviceStateChange(BluetoothDevice device, int newChannelState) {
        if (mHealthDevices.get(device) == null) {
            mHealthDevices.put(device, BluetoothHealth.STATE_DISCONNECTED);
        }

        int currDeviceState = mHealthDevices.get(device);
        int newDeviceState = convertState(newChannelState);

        if (currDeviceState == newDeviceState) return;

        boolean sendIntent = false;
        List<HealthChannel> chan;
        switch (currDeviceState) {
            case BluetoothHealth.STATE_DISCONNECTED:
                // there was no connection or connect/disconnect attemp with the remote device
                sendIntent = true;
                break;
            case BluetoothHealth.STATE_CONNECTING:
                // there was no connection, there was a connecting attempt going on

                // Channel got connected.
                if (newDeviceState == BluetoothHealth.STATE_CONNECTED) {
                    sendIntent = true;
                } else {
                    // Channel got disconnected
                    chan = findChannelByStates(device, new int [] {
                            BluetoothHealth.STATE_CHANNEL_CONNECTING,
                            BluetoothHealth.STATE_CHANNEL_DISCONNECTING});
                    if (chan.isEmpty()) {
                        sendIntent = true;
                    }
                }
                break;
            case BluetoothHealth.STATE_CONNECTED:
                // there was at least one connection

                // Channel got disconnected or is in disconnecting state.
                chan = findChannelByStates(device, new int [] {
                        BluetoothHealth.STATE_CHANNEL_CONNECTING,
                        BluetoothHealth.STATE_CHANNEL_DISCONNECTED});
                if (chan.isEmpty()) {
                    sendIntent = true;
                }
                break;
            case BluetoothHealth.STATE_DISCONNECTING:
                // there was no connected channel with the remote device
                // We were disconnecting all the channels with the remote device

                // Channel got disconnected.
                chan = findChannelByStates(device, new int [] {
                        BluetoothHealth.STATE_CHANNEL_CONNECTING,
                        BluetoothHealth.STATE_CHANNEL_DISCONNECTING});
                if (chan.isEmpty()) {
                    updateAndSendIntent(device, newDeviceState, currDeviceState);
                }
                break;
        }
        if (sendIntent)
            updateAndSendIntent(device, newDeviceState, currDeviceState);
    }

    private void updateAndSendIntent(BluetoothDevice device, int newDeviceState,
            int prevDeviceState) {
        if (newDeviceState == BluetoothHealth.STATE_DISCONNECTED) {
            mHealthDevices.remove(device);
        } else {
            mHealthDevices.put(device, newDeviceState);
        }
    }

    /**
     * This function converts the channel connection state to device connection state.
     *
     * @param state
     * @return
     */
    private int convertState(int state) {
        switch (state) {
            case BluetoothHealth.STATE_CHANNEL_CONNECTED:
                return BluetoothHealth.STATE_CONNECTED;
            case BluetoothHealth.STATE_CHANNEL_CONNECTING:
                return BluetoothHealth.STATE_CONNECTING;
            case BluetoothHealth.STATE_CHANNEL_DISCONNECTING:
                return BluetoothHealth.STATE_DISCONNECTING;
            case BluetoothHealth.STATE_CHANNEL_DISCONNECTED:
                return BluetoothHealth.STATE_DISCONNECTED;
        }
        Log.e(TAG, "Mismatch in Channel and Health Device State: " + state);
        return BluetoothHealth.STATE_DISCONNECTED;
    }

    private int convertRoleToHal(int role) {
        if (role == BluetoothHealth.SOURCE_ROLE) return MDEP_ROLE_SOURCE;
        if (role == BluetoothHealth.SINK_ROLE) return MDEP_ROLE_SINK;
        Log.e(TAG, "unkonw role: " + role);
        return MDEP_ROLE_SINK;
    }

    private int convertChannelTypeToHal(int channelType) {
        if (channelType == BluetoothHealth.CHANNEL_TYPE_RELIABLE) return CHANNEL_TYPE_RELIABLE;
        if (channelType == BluetoothHealth.CHANNEL_TYPE_STREAMING) return CHANNEL_TYPE_STREAMING;
        if (channelType == BluetoothHealth.CHANNEL_TYPE_ANY) return CHANNEL_TYPE_ANY;
        Log.e(TAG, "unkonw channel type: " + channelType);
        return CHANNEL_TYPE_ANY;
    }

    private HealthChannel findChannelById(int id) {
        for (HealthChannel chan : mHealthChannels) {
            if (chan.mChannelId == id) return chan;
        }
        Log.e(TAG, "No channel found by id: " + id);
        return null;
    }

    private List<HealthChannel> findChannelByStates(BluetoothDevice device, int[] states) {
        List<HealthChannel> channels = new ArrayList<HealthChannel>();
        for (HealthChannel chan: mHealthChannels) {
            if (chan.mDevice.equals(device)) {
                for (int state : states) {
                    if (chan.mState == state) {
                        channels.add(chan);
                    }
                }
            }
        }
        return channels;
    }

    private int getConnectionState(BluetoothDevice device) {
        if (mHealthDevices.get(device) == null) {
            return BluetoothHealth.STATE_DISCONNECTED;
        }
        return mHealthDevices.get(device);
    }

    List<BluetoothDevice> lookupHealthDevicesMatchingStates(int[] states) {
        List<BluetoothDevice> healthDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mHealthDevices.keySet()) {
            int healthDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == healthDeviceState) {
                    healthDevices.add(device);
                    break;
                }
            }
        }
        return healthDevices;
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "mHealthChannels:");
        for (HealthChannel channel : mHealthChannels) {
            println(sb, "  " + channel);
        }
        println(sb, "mApps:");
        for (BluetoothHealthAppConfiguration conf : mApps.keySet()) {
            println(sb, "  " + conf + " : " + mApps.get(conf));
        }
        println(sb, "mHealthDevices:");
        for (BluetoothDevice device : mHealthDevices.keySet()) {
            println(sb, "  " + device + " : " + mHealthDevices.get(device));
        }
    }

    private static class AppInfo {
        private IBluetoothHealthCallback mCallback;
        private BluetoothHealthDeathRecipient mRcpObj;
        private int mAppId;

        private AppInfo(IBluetoothHealthCallback callback) {
            mCallback = callback;
            mRcpObj = null;
            mAppId = -1;
        }

        private void cleanup(){
            if(mCallback != null){
                if(mRcpObj != null){
                    IBinder binder = mCallback.asBinder();
                    try{
                        binder.unlinkToDeath(mRcpObj,0);
                    }catch(NoSuchElementException e){
                        Log.e(TAG,"No death recipient registered"+e);
                    }
                    mRcpObj.cleanup();
                    mRcpObj = null;
                }
                mCallback = null;
            }
            else if(mRcpObj != null){
                    mRcpObj.cleanup();
                    mRcpObj = null;
            }
       }
    }

    private class HealthChannel {
        private ParcelFileDescriptor mChannelFd;
        private BluetoothDevice mDevice;
        private BluetoothHealthAppConfiguration mConfig;
        // BluetoothHealth channel state
        private int mState;
        private int mChannelType;
        private int mChannelId;

        private HealthChannel(BluetoothDevice device, BluetoothHealthAppConfiguration config,
                      int channelType) {
             mChannelFd = null;
             mDevice = device;
             mConfig = config;
             mState = BluetoothHealth.STATE_CHANNEL_DISCONNECTED;
             mChannelType = channelType;
             mChannelId = -1;
        }
    }

    // Channel state event from Hal
    private class ChannelStateEvent {
        int mAppId;
        byte[] mAddr;
        int mCfgIndex;
        int mChannelId;
        int mState;
        FileDescriptor mFd;

        private ChannelStateEvent(int appId, byte[] addr, int cfgIndex,
                                  int channelId, int state, FileDescriptor fileDescriptor) {
            mAppId = appId;
            mAddr = addr;
            mCfgIndex = cfgIndex;
            mState = state;
            mChannelId = channelId;
            mFd = fileDescriptor;
        }
    }

    // Constants matching Hal header file bt_hl.h
    // bthl_app_reg_state_t
    private static final int APP_REG_STATE_REG_SUCCESS = 0;
    private static final int APP_REG_STATE_REG_FAILED = 1;
    private static final int APP_REG_STATE_DEREG_SUCCESS = 2;
    private static final int APP_REG_STATE_DEREG_FAILED = 3;

    // bthl_channel_state_t
    private static final int CONN_STATE_CONNECTING = 0;
    private static final int CONN_STATE_CONNECTED = 1;
    private static final int CONN_STATE_DISCONNECTING = 2;
    private static final int CONN_STATE_DISCONNECTED = 3;
    private static final int CONN_STATE_DESTROYED = 4;

    // bthl_mdep_role_t
    private static final int MDEP_ROLE_SOURCE = 0;
    private static final int MDEP_ROLE_SINK = 1;

    // bthl_channel_type_t
    private static final int CHANNEL_TYPE_RELIABLE = 0;
    private static final int CHANNEL_TYPE_STREAMING = 1;
    private static final int CHANNEL_TYPE_ANY =2;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native int registerHealthAppNative(int dataType, int role, String name, int channelType);
    private native boolean unregisterHealthAppNative(int appId);
    private native int connectChannelNative(byte[] btAddress, int appId);
    private native boolean disconnectChannelNative(int channelId);

}
