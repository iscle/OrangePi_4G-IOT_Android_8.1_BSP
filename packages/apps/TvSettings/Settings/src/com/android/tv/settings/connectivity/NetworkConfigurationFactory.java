/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;

public class NetworkConfigurationFactory {

    public static final int TYPE_ETHERNET = ConnectivityManager.TYPE_ETHERNET;
    public static final int TYPE_WIFI = ConnectivityManager.TYPE_WIFI;

    /**
     * Create a NetworkConfiguration with the networkType
     *
     * @param context context to pass the constructors
     * @param networkType networkType to create
     * @return new NetworkConfiguration instance
     */
    public static NetworkConfiguration createNetworkConfiguration(
            Context context, int networkType) {
        switch (networkType) {
            case TYPE_ETHERNET:
                return new EthernetConfig(context);

            case TYPE_WIFI:
                return new WifiConfig(context);

            default:
                return null;
        }
    }

    private NetworkConfigurationFactory() {
        // Do not allow instantiation
    }
}
