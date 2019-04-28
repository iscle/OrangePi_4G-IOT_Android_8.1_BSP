/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task.wifi;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link NetworkMonitor}.
 */
@SmallTest
public class NetworkMonitorTest {

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private NetworkMonitor.NetworkConnectedCallback mCallback;
    private NetworkMonitor mNetworkMonitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mNetworkMonitor = new NetworkMonitor(mContext, mUtils);
    }

    @Test
    public void testStartListening() {
        // WHEN starting to listen for connectivity changes
        mNetworkMonitor.startListening(mCallback);

        // THEN a broadcast receiver should be registered
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), eq(NetworkMonitor.FILTER));

        // WHEN connectivity is not obtained and a broadcast is received
        when(mUtils.isConnectedToWifi(mContext)).thenReturn(false);
        receiverCaptor.getValue().onReceive(mContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        // THEN no callback should be given
        verifyZeroInteractions(mCallback);

        // WHEN connectivity is obtained and a broadcast is received
        when(mUtils.isConnectedToWifi(mContext)).thenReturn(true);
        receiverCaptor.getValue().onReceive(mContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        // THEN a callback should be given
        verify(mCallback).onNetworkConnected();
    }

    @Test
    public void testStopListening() {
        // WHEN starting and stopping to listen for connectivity changes
        mNetworkMonitor.startListening(mCallback);
        mNetworkMonitor.stopListening();

        // THEN a broadcast receiver should be registered and later unregistered
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), eq(NetworkMonitor.FILTER));
        verify(mContext).unregisterReceiver(receiverCaptor.getValue());

        // WHEN connectivity is not obtained and a broadcast is received
        when(mUtils.isConnectedToWifi(mContext)).thenReturn(false);
        receiverCaptor.getValue().onReceive(mContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        // THEN no callback should be given
        verifyZeroInteractions(mCallback);

        // WHEN connectivity is obtained and a broadcast is received
        when(mUtils.isConnectedToWifi(mContext)).thenReturn(true);
        receiverCaptor.getValue().onReceive(mContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        // THEN no callback should be given
        verifyZeroInteractions(mCallback);
    }
}
