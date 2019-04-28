/*
 * Copyright (C) 2008 The Android Open Source Project
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

package mediatek.net.wifi;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.net.wifi.IWifiManager;
import android.net.wifi.WpsInfo;
import android.os.RemoteException;

import java.util.List;

import mediatek.net.wifi.HotspotClient;

/**
 * This class provides the API for  Wi-Fi Hotspot Manager.
 */
public class WifiHotspotManager {

    private static final String TAG = "WifiHotspotManager";

    IWifiManager mService;

    /**
     *  AP Client information of WIFI_HOTSPOT_CLIENTS_IP_READY_ACTION.
     *
     *  @hide
     */
    public static final String EXTRA_DEVICE_ADDRESS = "deviceAddress";

    /**
     *  AP Client information of WIFI_HOTSPOT_CLIENTS_IP_READY_ACTION.
     *
     *  @hide
     */
    public static final String EXTRA_IP_ADDRESS = "ipAddress";

    /**
     *  AP Client information of WIFI_HOTSPOT_CLIENTS_IP_READY_ACTION.
     *
     *  @hide
     */
    public static final String EXTRA_DEVICE_NAME = "deviceName";

    /**
     * Broadcast intent action indicating that WPS check pin fails.
     * @hide
     * @internal
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String WIFI_WPS_CHECK_PIN_FAIL_ACTION
            = "android.net.wifi.WIFI_WPS_CHECK_PIN_FAIL";

    /**
     * Broadcast intent action indicating that the hotspot clients changed.
     * @hide
     * @internal
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String WIFI_HOTSPOT_CLIENTS_CHANGED_ACTION
            = "android.net.wifi.WIFI_HOTSPOT_CLIENTS_CHANGED";

    /**
     * Broadcast intent action indicating that the hotspot overlap occurs.
     * @hide
     * @internal
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String WIFI_HOTSPOT_OVERLAP_ACTION
            = "android.net.wifi.WIFI_HOTSPOT_OVERLAP";

    /**
     * Create a new WifiHotspotManager instance.
     * @param service the Binder interface
     * @hide
     */
    public WifiHotspotManager(IWifiManager service) {
        mService = service;
    }

    /**
     * Start hotspot WPS function.
     * @param config WPS configuration
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean startApWps(WpsInfo config) {
        try {
            mService.startApWps(config);
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Return the hotspot clients.
     * @return a list of hotspot client in the form of a list
     * of {@link HotspotClient} objects.
     * @hide
     */
    public List<HotspotClient> getHotspotClients() {
        try {
            return mService.getHotspotClients();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * Return the IP address of the client.
     * @param deviceAddress The mac address of the hotspot client
     * @return the IP address of the client
     * @hide
     */
    public String getClientIp(String deviceAddress) {
        try {
            return mService.getClientIp(deviceAddress);
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * Return the device name of the client.
     * @param deviceAddress The mac address of the hotspot client
     * @return the device name of the client
     * @hide
     */
    public String getClientDeviceName(String deviceAddress) {
        try {
            return mService.getClientDeviceName(deviceAddress);
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * Block the client.
     * @param client The hotspot client to be blocked
     * @return {@code true} if the operation succeeds else {@code false}
     * @hide
     */
    public boolean blockClient(HotspotClient client) {
        try {
            return mService.blockClient(client);
        } catch (RemoteException e) {
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
        try {
            return mService.unblockClient(client);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Return whether all devices are allowed to connect.
     * @return {@code true} if all devices are allowed to connect else {@code false}
     * @hide
     */
    public boolean isAllDevicesAllowed() {
        try {
            return mService.isAllDevicesAllowed();
        } catch (RemoteException e) {
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
        try {
            return mService.setAllDevicesAllowed(enabled, allowAllConnectedDevices);
        } catch (RemoteException e) {
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
        try {
            return mService.allowDevice(deviceAddress, name);
        } catch (RemoteException e) {
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
        try {
            return mService.disallowDevice(deviceAddress);
        } catch (RemoteException e) {
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
        try {
            return mService.getAllowedDevices();
        } catch (RemoteException e) {
            return null;
        }
    }
}
