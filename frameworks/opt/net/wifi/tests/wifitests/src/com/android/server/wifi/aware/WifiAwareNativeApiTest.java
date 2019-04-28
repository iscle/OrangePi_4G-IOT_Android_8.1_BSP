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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanConfigRequest;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.wifi.aware.ConfigRequest;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;

/**
 * Unit test harness for WifiAwareNativeApi
 */
public class WifiAwareNativeApiTest {
    @Mock WifiAwareNativeManager mWifiAwareNativeManagerMock;
    @Mock IWifiNanIface mIWifiNanIfaceMock;

    @Rule public ErrorCollector collector = new ErrorCollector();

    private WifiAwareNativeApi mDut;

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mWifiAwareNativeManagerMock.getWifiNanIface()).thenReturn(mIWifiNanIfaceMock);

        WifiStatus status = new WifiStatus();
        status.code = WifiStatusCode.SUCCESS;
        when(mIWifiNanIfaceMock.enableRequest(anyShort(), any())).thenReturn(status);
        when(mIWifiNanIfaceMock.configRequest(anyShort(), any())).thenReturn(status);

        mDut = new WifiAwareNativeApi(mWifiAwareNativeManagerMock);
    }

    /**
     * Test that the set parameter shell command executor works when parameters are valid.
     */
    @Test
    public void testSetParameterShellCommandSuccess() {
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_IDLE_5GHZ, Integer.toString(1), true);
    }

    /**
     * Test that the set parameter shell command executor fails on incorrect name.
     */
    @Test
    public void testSetParameterShellCommandInvalidParameterName() {
        setSettableParam("XXX", Integer.toString(1), false);
    }

    /**
     * Test that the set parameter shell command executor fails on invalid value (not convertible
     * to an int).
     */
    @Test
    public void testSetParameterShellCommandInvalidValue() {
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_IDLE_5GHZ, "garbage", false);
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior is
     * using default values at the default power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsDefaults() throws RemoteException {
        NanConfigRequest config = validateEnableAndConfigure((short) 10,
                new ConfigRequest.Builder().build(), true, true, true, false);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", false,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", false,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior is
     * using the specified non-interactive values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsNoneInteractive() throws RemoteException {
        byte interactive5 = 2;
        byte interactive24 = 3;

        setPowerConfigurationParams(interactive5, interactive24, (byte) -1, (byte) -1);
        NanConfigRequest config = validateEnableAndConfigure((short) 10,
                new ConfigRequest.Builder().build(), false, false, false, false);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", interactive5,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", interactive24,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior is
     * using the specified idle (doze) values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsIdle() throws RemoteException {
        byte idle5 = 2;
        byte idle24 = -1;

        setPowerConfigurationParams((byte) -1, (byte) -1, idle5, idle24);
        NanConfigRequest config = validateEnableAndConfigure((short) 10,
                new ConfigRequest.Builder().build(), false, true, false, true);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", idle5,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", false,
                equalTo(config.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
    }

    // utilities

    private void setPowerConfigurationParams(byte interactive5, byte interactive24, byte idle5,
            byte idle24) {
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_INACTIVE_5GHZ,
                Integer.toString(interactive5), true);
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_INACTIVE_24GHZ,
                Integer.toString(interactive24), true);
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_IDLE_5GHZ, Integer.toString(idle5), true);
        setSettableParam(WifiAwareNativeApi.PARAM_DW_ON_IDLE_24GHZ, Integer.toString(idle24), true);
    }

    private void setSettableParam(String name, String value, boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set").thenReturn(name).thenReturn(
                value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }

    private NanConfigRequest validateEnableAndConfigure(short transactionId,
            ConfigRequest configRequest, boolean notifyIdentityChange, boolean initialConfiguration,
            boolean isInteractive, boolean isIdle) throws RemoteException {
        mDut.enableAndConfigure(transactionId, configRequest, notifyIdentityChange,
                initialConfiguration, isInteractive, isIdle);

        ArgumentCaptor<NanEnableRequest> enableReqCaptor = ArgumentCaptor.forClass(
                NanEnableRequest.class);
        ArgumentCaptor<NanConfigRequest> configReqCaptor = ArgumentCaptor.forClass(
                NanConfigRequest.class);
        NanConfigRequest config;

        if (initialConfiguration) {
            verify(mIWifiNanIfaceMock).enableRequest(eq(transactionId), enableReqCaptor.capture());
            config = enableReqCaptor.getValue().configParams;
        } else {
            verify(mIWifiNanIfaceMock).configRequest(eq(transactionId), configReqCaptor.capture());
            config = configReqCaptor.getValue();
        }

        collector.checkThat("disableDiscoveryAddressChangeIndication", !notifyIdentityChange,
                equalTo(config.disableDiscoveryAddressChangeIndication));
        collector.checkThat("disableStartedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableStartedClusterIndication));
        collector.checkThat("disableJoinedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableJoinedClusterIndication));

        return config;
    }
}
