/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car;


import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPbapClient;
import android.car.ICarBluetoothUserService;
import android.util.Log;

import java.util.Arrays;
import java.util.List;


public class CarBluetoothUserService extends ICarBluetoothUserService.Stub {
    private static final boolean DBG = true;
    private static final String TAG = "CarBluetoothUsrSvc";
    private BluetoothAdapter mBluetoothAdapter = null;
    private final PerUserCarService mService;
    // Profile Proxies.
    private BluetoothA2dpSink mBluetoothA2dpSink = null;
    private BluetoothHeadsetClient mBluetoothHeadsetClient = null;
    private BluetoothPbapClient mBluetoothPbapClient = null;
    private BluetoothMapClient mBluetoothMapClient = null;
    private List<Integer> mProfilesToConnect;

    public CarBluetoothUserService(PerUserCarService service) {
        mService = service;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mProfilesToConnect = Arrays.asList(
                BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.PBAP_CLIENT,
                BluetoothProfile.A2DP_SINK,
                BluetoothProfile.MAP_CLIENT);
    }

    /**
     * Setup connections to the profile proxy object to talk to the Bluetooth profile services
     */
    @Override
    public void setupBluetoothConnectionProxy() {
        if (DBG) {
            Log.d(TAG, "setupProfileProxy()");
        }
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "Null BT Adapter");
            return;
        }
        for (Integer profile : mProfilesToConnect) {
            mBluetoothAdapter.getProfileProxy(mService.getApplicationContext(), mProfileListener,
                    profile);
        }
    }

    /**
     * Close connections to the profile proxy object
     */
    @Override
    public void closeBluetoothConnectionProxy() {
        if (mBluetoothAdapter == null) {
            return;
        }
        if (DBG) {
            Log.d(TAG, "closeProfileProxy()");
        }
        // Close those profile proxy objects for profiles that have not yet disconnected
        if (mBluetoothA2dpSink != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
        }
        if (mBluetoothHeadsetClient != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT,
                    mBluetoothHeadsetClient);
        }
        if (mBluetoothPbapClient != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.PBAP_CLIENT, mBluetoothPbapClient);
        }
        if (mBluetoothMapClient != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mBluetoothMapClient);
        }
    }

    /**
     * Check if a proxy is available for the given profile to talk to the Profile's bluetooth
     * service.
     * @param profile - Bluetooth profile to check for
     * @return - true if proxy available, false if not.
     */
    @Override
    public boolean isBluetoothConnectionProxyAvailable(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                if (mBluetoothA2dpSink != null) {
                    return true;
                }
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                if (mBluetoothHeadsetClient != null) {
                    return true;
                }
                break;
            case BluetoothProfile.PBAP_CLIENT:
                if (mBluetoothPbapClient != null) {
                    return true;
                }
                break;
            case BluetoothProfile.MAP_CLIENT:
                if (mBluetoothMapClient != null) {
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public void bluetoothConnectToProfile(int profile, BluetoothDevice device) {
        if (!isBluetoothConnectionProxyAvailable(profile)) {
            Log.e(TAG, "Cannot connect to Profile. Proxy Unavailable");
            return;
        }
        if (DBG) {
            Log.d(TAG, "Trying to connect to " + device.getName() + " Profile: " + profile);
        }
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                mBluetoothA2dpSink.connect(device);
                break;

            case BluetoothProfile.HEADSET_CLIENT:
                mBluetoothHeadsetClient.connect(device);
                break;

            case BluetoothProfile.MAP_CLIENT:
                mBluetoothMapClient.connect(device);
                break;

            case BluetoothProfile.PBAP_CLIENT:
                mBluetoothPbapClient.connect(device);
                break;

            default:
                Log.d(TAG, "Unknown profile");
                break;
        }
        return;
    }

    /**
     * Set the priority of the given Bluetooth profile for the given remote device
     * @param profile - Bluetooth profile
     * @param device - remote Bluetooth device
     * @param priority - priority to set
     */
    @Override
    public void setProfilePriority(int profile, BluetoothDevice device, int priority) {
        if (!isBluetoothConnectionProxyAvailable(profile)) {
            Log.e(TAG, "Cannot connect to Profile. Proxy Unavailable");
            return;
        }
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                mBluetoothA2dpSink.setPriority(device, priority);
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                mBluetoothHeadsetClient.setPriority(device, priority);
                break;
            case BluetoothProfile.MAP_CLIENT:
                mBluetoothMapClient.setPriority(device, priority);
                break;
            case BluetoothProfile.PBAP_CLIENT:
                mBluetoothPbapClient.setPriority(device, priority);
                break;
            default:
                Log.d(TAG, "Unknown Profile");
                break;
        }
    }
    /**
     * All the BluetoothProfile.ServiceListeners to get the Profile Proxy objects
     */
    private BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (DBG) {
                        Log.d(TAG, "OnServiceConnected profile: " + profile);
                    }
                    switch (profile) {
                        case BluetoothProfile.A2DP_SINK:
                            mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
                            break;

                        case BluetoothProfile.HEADSET_CLIENT:
                            mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                            break;

                        case BluetoothProfile.PBAP_CLIENT:
                            mBluetoothPbapClient = (BluetoothPbapClient) proxy;
                            break;

                        case BluetoothProfile.MAP_CLIENT:
                            mBluetoothMapClient = (BluetoothMapClient) proxy;
                            break;

                        default:
                            if (DBG) {
                                Log.d(TAG, "Unhandled profile");
                            }
                            break;
                    }
                }

                public void onServiceDisconnected(int profile) {
                    if (DBG) {
                        Log.d(TAG, "onServiceDisconnected profile: " + profile);
                    }
                    switch (profile) {
                        case BluetoothProfile.A2DP_SINK:
                            mBluetoothA2dpSink = null;
                            break;

                        case BluetoothProfile.HEADSET_CLIENT:
                            mBluetoothHeadsetClient = null;
                            break;

                        case BluetoothProfile.PBAP_CLIENT:
                            mBluetoothPbapClient = null;
                            break;

                        case BluetoothProfile.MAP_CLIENT:
                            mBluetoothMapClient = null;
                            break;

                        default:
                            if (DBG) {
                                Log.d(TAG, "Unhandled profile");
                            }
                            break;
                    }
                }
            };
}
