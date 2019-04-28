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

package com.mediatek.server.wifi;

import android.os.SystemProperties;

/**
 *  WiFi dependency injector. To be used for accessing various WiFi class instances.
 */
public class WifiApInjector {
    static WifiApInjector sWifiApInjector = null;

    private final WifiApNative mWifiApNative;
    private final WifiApMonitor mWifiApMonitor;
    private final HostapdIfaceHal mHostapdIfaceHal;

    public WifiApInjector() {
        sWifiApInjector = this;

        mWifiApMonitor = new WifiApMonitor(this);
        mHostapdIfaceHal = new HostapdIfaceHal(mWifiApMonitor);
        mWifiApNative = new WifiApNative(SystemProperties.get("wifi.tethering.interface", "ap0"),
                mHostapdIfaceHal);
    }

    /**
     *  Obtain an instance of the WifiApInjector class.
     *
     *  This is the generic method to get an instance of the class.
     */
    public static WifiApInjector getInstance() {
        return sWifiApInjector;
    }

    public WifiApNative getWifiApNative() {
        return mWifiApNative;
    }

    public WifiApMonitor getWifiApMonitor() {
        return mWifiApMonitor;
    }
}
