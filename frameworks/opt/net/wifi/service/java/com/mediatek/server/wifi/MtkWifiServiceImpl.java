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
import android.net.wifi.IWifiManager;
import android.net.wifi.WpsInfo;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.util.AsyncChannel;
import com.android.server.wifi.WifiInjector;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import mediatek.net.wifi.HotspotClient;

/**
 * WifiService handles remote WiFi operation requests by implementing
 * the IWifiManager interface for WiFi hotspot manager.
 *
 * @hide
 */
public abstract class MtkWifiServiceImpl extends IWifiManager.Stub {
    private static final String TAG = "WifiService";

    private final Context mContext;
    private final WifiInjector mWifiInjector;
    private AsyncChannel mWifiStateMachineChannel;
    /// M: Hotspot manager implementation
    private final WifiApStateMachine mWifiApStateMachine;

    public MtkWifiServiceImpl(
            Context context, WifiInjector wifiInjector, AsyncChannel asyncChannel) {
        mContext = context;
        mWifiInjector = wifiInjector;
        mWifiStateMachineChannel = asyncChannel;
        /// M: Hotspot manager implementation
        mWifiApStateMachine = new WifiApStateMachine(wifiInjector.getWifiStateMachine());
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE,
                "WifiService");
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE,
                "WifiService");
    }

    /// M: Hotspot manager implementation @{
    /**
     * Start hotspot WPS function.
     * @param config WPS configuration
     * @hide
     */
    public void startApWps(WpsInfo config) {
        enforceChangePermission();
        Slog.d(TAG, "startApWps config = " + config);
        mWifiApStateMachine.startApWpsCommand(config);
    }

    /**
     * Return the hotspot clients.
     * @return a list of hotspot client in the form of a list
     * of {@link HotspotClient} objects.
     * @hide
     */
    public List<HotspotClient> getHotspotClients() {
        enforceAccessPermission();
        return mWifiApStateMachine.syncGetHotspotClientsList(mWifiStateMachineChannel);
    }

    private ArrayList<String> readClientList(String filename) {
        FileInputStream fstream = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            fstream = new FileInputStream(filename);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String s;
            // throw away the title line
            while (((s = br.readLine()) != null) && (s.length() != 0)) {
                list.add(s);
            }
        } catch (IOException ex) {
            // return current list, possibly empty
            Slog.e(TAG, "IOException:" + ex);
        } finally {
          if (fstream != null) {
                try {
                    fstream.close();
                } catch (IOException ex) {
                    Slog.e(TAG, "IOException:" + ex);
                }
            }
        }
        return list;
    }

    /**
     * Return the IP address of the client.
     * @param deviceAddress The mac address of the hotspot client
     * @return the IP address of the client
     * @hide
     */
    public String getClientIp(String deviceAddress) {
        enforceAccessPermission();
        Slog.d(TAG, "getClientIp deviceAddress = " + deviceAddress);
        if (TextUtils.isEmpty(deviceAddress)) {
            return null;
        }
        final String LEASES_FILE = "/data/misc/dhcp/dnsmasq.leases";

        for (String s : readClientList(LEASES_FILE)) {
            if (s.indexOf(deviceAddress) != -1) {
                String[] fields = s.split(" ");
                if (fields.length > 3) {
                    return fields[2];
                }
            }
        }
        return null;
    }

    /**
     * Return the device name of the client.
     * @param deviceAddress The mac address of the hotspot client
     * @return the device name of the client
     * @hide
     */
    public String getClientDeviceName(String deviceAddress) {
        enforceAccessPermission();
        Slog.d(TAG, "getClientDeviceName deviceAddress = " + deviceAddress);
        if (TextUtils.isEmpty(deviceAddress)) {
            return null;
        }
        final String LEASES_FILE = "/data/misc/dhcp/dnsmasq.leases";

        for (String s : readClientList(LEASES_FILE)) {
            if (s.indexOf(deviceAddress) != -1) {
                String[] fields = s.split(" ");
                if (fields.length > 4) {
                    return fields[3];
                }
            }
        }
        return null;
    }

    /**
     * Block the client.
     * @param client The hotspot client to be blocked
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean blockClient(HotspotClient client) {
        enforceChangePermission();
        Slog.d(TAG, "blockClient client = " + client);
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncBlockClient(mWifiStateMachineChannel, client);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Unblock the client.
     * @param client The hotspot client to be unblocked
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean unblockClient(HotspotClient client) {
        enforceChangePermission();
        Slog.d(TAG, "unblockClient client = " + client);
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncUnblockClient(mWifiStateMachineChannel, client);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Return whether all devices are allowed to connect.
     * @return {@code true} if all devices are allowed to connect else {@code false}
     * @hide
     */
    public boolean isAllDevicesAllowed() {
        enforceAccessPermission();
        Slog.d(TAG, "isAllDevicesAllowed");
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncIsAllDevicesAllowed(mWifiStateMachineChannel);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Enable or disable allow all devices.
     * @param enabled {@code true} to enable, {@code false} to disable
     * @param allowAllConnectedDevices {@code true} to add all connected devices to allowed list
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean setAllDevicesAllowed(boolean enabled, boolean allowAllConnectedDevices) {
        enforceChangePermission();
        Slog.d(TAG, "setAllDevicesAllowed enabled = " + enabled
               + " allowAllConnectedDevices = " + allowAllConnectedDevices);
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncSetAllDevicesAllowed(mWifiStateMachineChannel, enabled,
                                                                  allowAllConnectedDevices);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Allow the specified device to connect Hotspot and update the allowed list
     * with MAC address and name as well
     * @param deviceAddress the MAC address of the device
     * @param name the name of the device
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean allowDevice(String deviceAddress, String name) {
        enforceChangePermission();
        Slog.d(TAG, "allowDevice address = " + deviceAddress + ", name = " + name
               + "is null?" + (name == null));
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncAllowDevice(
                    mWifiStateMachineChannel, deviceAddress, name);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Disallow the specified device to connect Hotspot and update the allowed list.
     * If current setting is to allow all devices, it only updates the list.
     * @param deviceAddress the MAC address of the device
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean disallowDevice(String deviceAddress) {
        enforceChangePermission();
        Slog.d(TAG, "disallowDevice address = " + deviceAddress);
        if (mWifiStateMachineChannel != null) {
            return mWifiApStateMachine.syncDisallowDevice(mWifiStateMachineChannel, deviceAddress);
        } else {
            Slog.e(TAG, "mWifiStateMachineChannel is not initialized!");
            return false;
        }
    }

    /**
     * Return the allowed devices.
     * @return a list of hotspot client in the form of a list
     * of {@link HotspotClient} objects.
     * @hide
     */
    public List<HotspotClient> getAllowedDevices() {
        enforceAccessPermission();
        Slog.d(TAG, "getAllowedDevices");
        return mWifiApStateMachine.syncGetAllowedDevices(mWifiStateMachineChannel);
    }
    /// @}
}
