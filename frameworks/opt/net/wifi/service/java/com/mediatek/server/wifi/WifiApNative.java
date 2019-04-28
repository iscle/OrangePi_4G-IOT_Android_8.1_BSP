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

package com.mediatek.server.wifi;

/**
 * M: WiFi hotspot manager
 * Native calls for sending requests to the hostapd daemon
 *
 * {@hide}
 */
public class WifiApNative {
    private final String mTAG;
    private final String mInterfaceName;
    private final HostapdIfaceHal mHostapdIfaceHal;

    public WifiApNative(String interfaceName, HostapdIfaceHal apIfaceHal) {
        mTAG = "WifiApNative-" + interfaceName;
        mInterfaceName = interfaceName;
        mHostapdIfaceHal = apIfaceHal;
    }

    public String getInterfaceName() {
        return mInterfaceName;
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public void enableVerboseLogging(int verbose) {
        mHostapdIfaceHal.enableVerboseLogging(verbose > 0);
    }

    /********************************************************
     * Hostapd operations
     ********************************************************/

    /**
     * This method is called repeatedly until the connection to hostapd is established.
     *
     * @return true if connection is established, false otherwise.
     */
    public boolean connectToHostapd() {
        // Start initialization if not already started.
        if (!mHostapdIfaceHal.isInitializationStarted()
                && !mHostapdIfaceHal.initialize()) {
            return false;
        }
        // Check if the initialization is complete.
        return mHostapdIfaceHal.isInitializationComplete();
    }

    /**
     * Close hostapd connection.
     */
    public void closeHostapdConnection() {
        // Nothing to do for HIDL.
    }

    /**
     * Initiate WPS Push Button setup.
     * @return true, if operation was successful.
     */
    public boolean startApWpsPbcCommand() {
        return mHostapdIfaceHal.startWpsPbc();
    }

    /**
     * Initiate WPS Pin Keypad setup.
     * @param pin 8 digit pin to be used.
     * @return true, if operation was successful.
     */
    public boolean startApWpsWithPinFromDeviceCommand(String pin) {
        return mHostapdIfaceHal.startWpsPinKeypad(pin);
    }

    /**
     * Initiate WPS Pin check.
     * @param pin 8 digit pin to be checked.
     * @return valid pin.
     */
    public String startApWpsCheckPinCommand(String pin) {
        return mHostapdIfaceHal.startWpsCheckPin(pin);
    }

    /**
     * Block client.
     * @param deviceAddress MAC address of client to be blocked.
     * @return true, if operation was successful.
     */
    public boolean blockClientCommand(String deviceAddress) {
        return mHostapdIfaceHal.blockClient(deviceAddress);
    }

    /**
     * Unblock client.
     * @param deviceAddress MAC address of client to be unblocked.
     * @return true, if operation was successful.
     */
    public boolean unblockClientCommand(String deviceAddress) {
        return mHostapdIfaceHal.unblockClient(deviceAddress);
    }

    /**
     * Update allowed list.
     * @param filePath File path of allowed list which wanna update.
     * @return true, if operation was successful.
     */
    public boolean updateAllowedListCommand(String filePath) {
        return mHostapdIfaceHal.updateAllowedList(filePath);
    }

    /**
     * Set all devices allowed.
     * @param enable true to enable, false to disable.
     * @return true, if operation was successful.
     */
    public boolean setAllDevicesAllowedCommand(boolean enable) {
        return mHostapdIfaceHal.setAllDevicesAllowed(enable);
    }
}
