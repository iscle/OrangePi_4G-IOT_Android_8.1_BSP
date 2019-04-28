/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.automatic;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=23)
public class JobPreconditionsTest {
    // TODO: Instead of mocking, use ShadowConnectivityManager. Right now, using it causes a crash.
    //       Use the shadow once we get it working.
    @Mock ConnectivityManager mConnectivityManager;
    @Mock BatteryManager mBatteryManager;
    @Mock Network mWifiNetwork;
    @Mock NetworkInfo mWifiNetworkInfo;
    private Context mContext;
    private ArrayList<Network> mNetworkList;
    private HashMap<Network, NetworkInfo> mNetworkMap;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mNetworkList = new ArrayList<>();
        mNetworkMap = new HashMap<>();
        shadowOf(RuntimeEnvironment.application).setSystemService(Context.CONNECTIVITY_SERVICE,
                mConnectivityManager);
        shadowOf(RuntimeEnvironment.application).setSystemService(Context.BATTERY_SERVICE,
                mBatteryManager);
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void testNoNetworks() {
        hookUpMocks();

        assertThat(JobPreconditions.isWifiConnected(mContext)).isFalse();
    }

    @Test
    public void testValidWifiNetworkConnected() {
        // Connect the fake Wi-Fi.
        mNetworkList.add(mWifiNetwork);
        mNetworkMap.put(mWifiNetwork, mWifiNetworkInfo);
        when(mWifiNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(mWifiNetworkInfo.isConnected()).thenReturn(true);
        hookUpMocks();

        assertThat(JobPreconditions.isWifiConnected(mContext)).isTrue();
        assertThat(JobPreconditions.isNetworkMetered(mContext)).isFalse();
    }

    @Test
    public void testBatteryConnected() {
        when(mBatteryManager.isCharging()).thenReturn(true);

        assertThat(JobPreconditions.isCharging(mContext)).isTrue();
    }

    @Test
    public void testBatteryDisconnected() {
        when(mBatteryManager.isCharging()).thenReturn(false);

        assertThat(JobPreconditions.isCharging(mContext)).isFalse();
    }

    // TODO(b/31224380): Checking if a network is metered relies on a hidden API which we cannot
    //                   mock or shadow in the current API. Find a way to test the metered check.

    /**
     * hookUpMocks takes the local list of networks and hooks it up to the mock ConnectivityManager.
     * It mocks the getAllNetworks() method to return the list of Networks as an array and the
     * getNetworkInfo(Network) method to return a lookup in our local network -> network info map.
     */
    private void hookUpMocks() {
        doReturn(mNetworkList.toArray(new Network[mNetworkList.size()]))
                .when(mConnectivityManager).getAllNetworks();
        when(mConnectivityManager.getNetworkInfo(any(Network.class))).thenAnswer(
                new Answer<NetworkInfo>() {
                    @Override
                    public NetworkInfo answer(InvocationOnMock invocation) {
                        Network network = (Network) invocation.getArguments()[0];
                        return mNetworkMap.get(network);
                    }
                }
        );
    }
}
