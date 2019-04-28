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

package com.android.managedprovisioning.analytics;

import static android.net.ConnectivityManager.TYPE_WIFI;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_NETWORK_TYPE;
import static com.android.managedprovisioning.analytics.NetworkTypeLogger.NETWORK_TYPE_NOT_CONNECTED;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.android.managedprovisioning.common.Utils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link NetworkTypeLogger}.
 */
@SmallTest
public class NetworkTypeLoggerTest extends AndroidTestCase {

    private static final NetworkInfo WIFI_NETWORK_INFO =
            new NetworkInfo(TYPE_WIFI, 2, "WIFI", "WIFI_SUBTYPE");

    @Mock private Context mContext;
    @Mock private MetricsLoggerWrapper mMetricsLoggerWrapper;
    @Mock private Utils mUtils;

    private NetworkTypeLogger mNetworkTypeLogger;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        mNetworkTypeLogger = new NetworkTypeLogger(mContext, mUtils, mMetricsLoggerWrapper);
    }

    @SmallTest
    public void test_NullNetworkInfo() {
        // GIVEN there is no network info present
        when(mUtils.getActiveNetworkInfo(mContext)).thenReturn(null);
        // WHEN network type is logged
        mNetworkTypeLogger.log();
        // THEN network type not connected should be logged
        verify(mMetricsLoggerWrapper).logAction(mContext, PROVISIONING_NETWORK_TYPE,
                NETWORK_TYPE_NOT_CONNECTED);
    }

    @SmallTest
    public void test_NetworkNotConnected() {
        // GIVEN there is a valid network info
        when(mUtils.getActiveNetworkInfo(mContext)).thenReturn(WIFI_NETWORK_INFO);
        // GIVEN that the device is not connected
        when(mUtils.isConnectedToNetwork(mContext)).thenReturn(false);
        // WHEN network type is logged
        mNetworkTypeLogger.log();
        // THEN network type not connected should be logged
        verify(mMetricsLoggerWrapper).logAction(mContext, PROVISIONING_NETWORK_TYPE,
                NETWORK_TYPE_NOT_CONNECTED);
    }

    @SmallTest
    public void test_NetworkTypeWifi() {
        // GIVEN the device is connected to a wifi network
        when(mUtils.getActiveNetworkInfo(mContext)).thenReturn(WIFI_NETWORK_INFO);
        when(mUtils.isConnectedToNetwork(mContext)).thenReturn(true);
        // WHEN network type is logged
        mNetworkTypeLogger.log();
        // THEN network type wifi should be logged
        verify(mMetricsLoggerWrapper).logAction(mContext, PROVISIONING_NETWORK_TYPE, TYPE_WIFI);
    }
}
