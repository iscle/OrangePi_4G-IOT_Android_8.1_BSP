/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.mediatek.server.wifi;

import android.content.Context;
import android.net.wifi.WpsInfo;
import android.os.Message;
import android.util.Log;

import com.android.internal.util.AsyncChannel;
import com.android.server.wifi.SoftApManager;
import com.android.server.wifi.WifiStateMachine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import mediatek.net.wifi.HotspotClient;

/**
 * The WifiApStateMachine handles wifi hotspot manager operations.
 */
public class WifiApStateMachine {
    private static final String TAG = "WifiApStateMachine";

    private static final int SUCCESS = 1;
    private static final int FAILURE = -1;

    private static WifiStateMachine mWifiStateMachine;

    public WifiApStateMachine(WifiStateMachine wifiStateMachine) {
        mWifiStateMachine = wifiStateMachine;
    }

    static public boolean processDefaultStateMessage(Message message, Context context) {
        try {
            Method replyToMessage1 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class);
            replyToMessage1.setAccessible(true);
            Method replyToMessage2 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class, int.class);
            replyToMessage2.setAccessible(true);
            Method replyToMessage3 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class, Object.class);
            replyToMessage3.setAccessible(true);

            switch (message.what) {
                case MtkSoftApManager.M_CMD_BLOCK_CLIENT:
                case MtkSoftApManager.M_CMD_UNBLOCK_CLIENT:
                case MtkSoftApManager.M_CMD_START_AP_WPS:
                    replyToMessage2.invoke(mWifiStateMachine, message, message.what, FAILURE);
                    return true;
                case MtkSoftApManager.M_CMD_GET_CLIENTS_LIST:
                    List<HotspotClient> clients = new ArrayList<HotspotClient>();
                    replyToMessage3.invoke(mWifiStateMachine, message, message.what,
                                            (List<HotspotClient>) clients);
                    return true;
                case MtkSoftApManager.M_CMD_IS_ALL_DEVICES_ALLOWED:
                    boolean resultValue = MtkSoftApManager.isAllDevicesAllowed(context);
                    replyToMessage2.invoke(mWifiStateMachine, message, message.what,
                                            resultValue ? 1 : 0);
                    return true;
                case MtkSoftApManager.M_CMD_SET_ALL_DEVICES_ALLOWED:
                    boolean enabled = (message.arg1 == 1);
                    MtkSoftApManager.writeAllDevicesAllowed(context, enabled);
                    replyToMessage2.invoke(mWifiStateMachine, message, message.what, SUCCESS);
                    return true;
                case MtkSoftApManager.M_CMD_ALLOW_DEVICE:
                    MtkSoftApManager.addDeviceToAllowedList((HotspotClient) message.obj);
                    replyToMessage1.invoke(mWifiStateMachine, message, message.what);
                    return true;
                case MtkSoftApManager.M_CMD_DISALLOW_DEVICE:
                    MtkSoftApManager.removeDeviceFromAllowedList((String) message.obj);
                    replyToMessage1.invoke(mWifiStateMachine, message, message.what);
                    return true;
                case MtkSoftApManager.M_CMD_GET_ALLOWED_DEVICES:
                    List<HotspotClient> clientList = MtkSoftApManager.getAllowedDevices();
                    replyToMessage3.invoke(mWifiStateMachine, message, message.what, clientList);
                    return true;
                default:
                    return false;
            }
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    static public boolean processSoftApStateMessage(
            Message message, Context context, SoftApManager softApManager) {
        try {
            MtkSoftApManager mtkSoftApManager = (MtkSoftApManager) softApManager;
            Method replyToMessage1 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class);
            replyToMessage1.setAccessible(true);
            Method replyToMessage2 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class, int.class);
            replyToMessage2.setAccessible(true);
            Method replyToMessage3 = mWifiStateMachine.getClass().getDeclaredMethod(
                                        "replyToMessage", Message.class, int.class, Object.class);
            replyToMessage3.setAccessible(true);

            switch (message.what) {
                case MtkSoftApManager.M_CMD_BLOCK_CLIENT:
                    Message newBlockMsg = mWifiStateMachine.obtainMessage();
                    newBlockMsg.copyFrom(message);
                    mtkSoftApManager.syncBlockClient(newBlockMsg);
                    return true;
                case MtkSoftApManager.M_CMD_UNBLOCK_CLIENT:
                    Message newUnblockMsg = mWifiStateMachine.obtainMessage();
                    newUnblockMsg.copyFrom(message);
                    mtkSoftApManager.syncUnblockClient(newUnblockMsg);
                    return true;
                case MtkSoftApManager.M_CMD_GET_CLIENTS_LIST:
                    List<HotspotClient> clientList = mtkSoftApManager.getHotspotClientsList();
                    replyToMessage3.invoke(mWifiStateMachine, message, message.what,
                                            (List<HotspotClient>) clientList);
                    return true;
                case MtkSoftApManager.M_CMD_START_AP_WPS:
                    Message newWpsMsg = mWifiStateMachine.obtainMessage();
                    newWpsMsg.copyFrom(message);
                    mtkSoftApManager.startApWpsCommand(newWpsMsg);
                    return true;
                case MtkSoftApManager.M_CMD_SET_ALL_DEVICES_ALLOWED:
                    boolean enabled = (message.arg1 == 1);
                    boolean allowAllConnectedDevices = (message.arg2 == 1);
                    MtkSoftApManager.writeAllDevicesAllowed(context, enabled);
                    mtkSoftApManager.syncSetAllDevicesAllowed(enabled, allowAllConnectedDevices);
                    replyToMessage3.invoke(mWifiStateMachine, message, message.what, true);
                    return true;
                case MtkSoftApManager.M_CMD_ALLOW_DEVICE:
                    HotspotClient device = (HotspotClient) message.obj;
                    MtkSoftApManager.addDeviceToAllowedList(device);
                    mtkSoftApManager.syncAllowDevice(device.deviceAddress);
                    replyToMessage1.invoke(mWifiStateMachine, message, message.what);
                    return true;
                case MtkSoftApManager.M_CMD_DISALLOW_DEVICE:
                    String address = (String) message.obj;
                    MtkSoftApManager.removeDeviceFromAllowedList(address);
                    mtkSoftApManager.syncDisallowDevice(address);
                    replyToMessage1.invoke(mWifiStateMachine, message, message.what);
                    return true;
                default:
                    return false;
            }
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
    }

    static public String smToString(int what) {
        String s;
        switch (what) {
            case MtkSoftApManager.M_CMD_BLOCK_CLIENT:
                s = "M_CMD_BLOCK_CLIENT";
                break;
            case MtkSoftApManager.M_CMD_UNBLOCK_CLIENT:
                s = "M_CMD_UNBLOCK_CLIENT";
                break;
            case MtkSoftApManager.M_CMD_GET_CLIENTS_LIST:
                s = "M_CMD_GET_CLIENTS_LIST";
                break;
            case MtkSoftApManager.M_CMD_START_AP_WPS:
                s = "M_CMD_START_AP_WPS";
                break;
            case MtkSoftApManager.M_CMD_IS_ALL_DEVICES_ALLOWED:
                s = "M_CMD_IS_ALL_DEVICES_ALLOWED";
                break;
            case MtkSoftApManager.M_CMD_SET_ALL_DEVICES_ALLOWED:
                s = "M_CMD_SET_ALL_DEVICES_ALLOWED";
                break;
            case MtkSoftApManager.M_CMD_ALLOW_DEVICE:
                s = "M_CMD_ALLOW_DEVICE";
                break;
            case MtkSoftApManager.M_CMD_DISALLOW_DEVICE:
                s = "M_CMD_DISALLOW_DEVICE";
                break;
            case MtkSoftApManager.M_CMD_GET_ALLOWED_DEVICES:
                s = "M_CMD_GET_ALLOWED_DEVICES";
                break;
            default:
                s = null;
                break;
        }
        return s;
    }

    public void startApWpsCommand(WpsInfo config) {
        mWifiStateMachine.sendMessage(
                mWifiStateMachine.obtainMessage(MtkSoftApManager.M_CMD_START_AP_WPS, config));
    }

    public List<HotspotClient> syncGetHotspotClientsList(AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_GET_CLIENTS_LIST);

        List<HotspotClient> result = (List<HotspotClient>) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public boolean syncBlockClient(AsyncChannel channel, HotspotClient client) {
        if (client == null || client.deviceAddress == null) {
            Log.e(TAG, "Client is null!");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_BLOCK_CLIENT,
                                            client);
        boolean result = (resultMsg.arg1 != FAILURE);
        resultMsg.recycle();
        return result;
    }

    public boolean syncUnblockClient(AsyncChannel channel, HotspotClient client) {
        if (client == null || client.deviceAddress == null) {
            Log.e(TAG, "Client is null!");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_UNBLOCK_CLIENT,
                                            client);
        boolean result = (resultMsg.arg1 != FAILURE);
        resultMsg.recycle();
        return result;
    }

    public boolean syncIsAllDevicesAllowed(AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_IS_ALL_DEVICES_ALLOWED);
        boolean result = (resultMsg.arg1 == 1);
        resultMsg.recycle();

        return result;
    }

    public boolean syncSetAllDevicesAllowed(AsyncChannel channel, boolean enabled,
                                               boolean allowAllConnectedDevices) {
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_SET_ALL_DEVICES_ALLOWED,
                                            enabled ? 1 : 0, allowAllConnectedDevices ? 1 : 0);
        boolean result = (resultMsg.arg1 != FAILURE);
        resultMsg.recycle();
        return result;
    }

    public boolean syncAllowDevice(AsyncChannel channel, String deviceAddress, String name) {
        if (deviceAddress == null) {
            Log.e(TAG, "deviceAddress is null!");
            return false;
        }

        HotspotClient device = new HotspotClient(deviceAddress, false, name);
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_ALLOW_DEVICE,
                                            device);
        boolean result = (resultMsg.arg1 != FAILURE);
        resultMsg.recycle();
        return result;
    }

    public boolean syncDisallowDevice(AsyncChannel channel, String deviceAddress) {
        if (deviceAddress == null) {
            Log.e(TAG, "deviceAddress is null!");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_DISALLOW_DEVICE,
                                            deviceAddress);
        boolean result = (resultMsg.arg1 != FAILURE);
        resultMsg.recycle();
        return result;
    }

    public List<HotspotClient> syncGetAllowedDevices(AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously(
                                            MtkSoftApManager.M_CMD_GET_ALLOWED_DEVICES);

        List<HotspotClient> result = (List<HotspotClient>) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }
}

