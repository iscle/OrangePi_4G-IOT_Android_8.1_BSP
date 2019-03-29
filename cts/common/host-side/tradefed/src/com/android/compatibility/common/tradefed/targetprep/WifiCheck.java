/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * This preparer ensures that the device is connected to a network.
 * The options "wifi-ssid" and "wifi-psk" allow users of the preparer to attempt connection
 * to a network. If the options are provided, the preparer disconnects any existing network
 * connection, and attempts to connect with the options provided.
 *
 * @throws TargetSetupError if device is not connected to a network and no options are given, or
 * if the device fails to connect to the network specified in the options
 */
@OptionClass(alias="wifi-check")
public class WifiCheck extends PreconditionPreparer {

    private static final String WIFI_FEATURE = "android.hardware.wifi";

    @Option(name = "wifi-ssid", description = "Name of the WiFi network with which to connect")
    protected String mWifiSsid = null;

    @Option(name = "wifi-psk",
            description = "The WPA-PSK associated with the wifi-ssid option")
    protected String mWifiPsk = null;

    private boolean hasWifiFeature(ITestDevice device) throws DeviceNotAvailableException {
        String pmFeatures = device.executeShellCommand("pm list features");
        return pmFeatures.contains(WIFI_FEATURE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        if(!hasWifiFeature(device)) {
            return; // skip this precondition check if device doesn't support wifi
        }

        if (mWifiSsid == null) { // no connection to create, check for existing connectivity
            if (!device.checkConnectivity()) {
                logError("Device has no network connection, no ssid provided, some modules " +
                        "of CTS require an active network connection");
                return;
            }
        } else { // network provided in options, attempt to create new connection
            logInfo("Attempting connection to \"%s\"", mWifiSsid);
            if (!device.connectToWifiNetwork(mWifiSsid, mWifiPsk)) { // attempt connection
                logError("Unable to connect to network \"%s\", some modules of CTS" +
                        "require an active network connection", mWifiSsid);
                return;
            }
        }
        logInfo("Wifi is connected");
    }
}
