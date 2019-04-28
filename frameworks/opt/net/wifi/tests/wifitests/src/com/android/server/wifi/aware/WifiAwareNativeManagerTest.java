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
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;

import com.android.server.wifi.HalDeviceManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for WifiAwareNativeManager.
 */
public class WifiAwareNativeManagerTest {
    private WifiAwareNativeManager mDut;
    @Mock private WifiAwareStateManager mWifiAwareStateManagerMock;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock private WifiAwareNativeCallback mWifiAwareNativeCallback;
    @Mock private IWifiNanIface mWifiNanIfaceMock;
    private ArgumentCaptor<HalDeviceManager.ManagerStatusListener> mManagerStatusListenerCaptor =
            ArgumentCaptor.forClass(HalDeviceManager.ManagerStatusListener.class);
    private ArgumentCaptor<HalDeviceManager.InterfaceDestroyedListener>
            mDestroyedListenerCaptor = ArgumentCaptor.forClass(
            HalDeviceManager.InterfaceDestroyedListener.class);
    private ArgumentCaptor<HalDeviceManager.InterfaceAvailableForRequestListener>
            mAvailListenerCaptor = ArgumentCaptor.forClass(
            HalDeviceManager.InterfaceAvailableForRequestListener.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();

    private WifiStatus mStatusOk;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStatusOk = new WifiStatus();
        mStatusOk.code = WifiStatusCode.SUCCESS;

        when(mWifiNanIfaceMock.registerEventCallback(any())).thenReturn(mStatusOk);

        mDut = new WifiAwareNativeManager(mWifiAwareStateManagerMock,
                mHalDeviceManager, mWifiAwareNativeCallback);
        mDut.start();

        mInOrder = inOrder(mWifiAwareStateManagerMock, mHalDeviceManager);
    }

    /**
     * Test the control flow of the manager:
     * 1. onStatusChange (ready/started)
     * 2. null NAN iface
     * 3. onAvailableForRequest
     * 4. non-null NAN iface -> enableUsage
     * 5. onStatusChange (!started) -> disableUsage
     * 6. onStatusChange (ready/started)
     * 7. non-null NAN iface -> enableUsage
     * 8. onDestroyed -> disableUsage
     * 9. onStatusChange (!started)
     */
    @Test
    public void testControlFlow() {
        // configure HalDeviceManager as ready/wifi started
        when(mHalDeviceManager.isReady()).thenReturn(true);
        when(mHalDeviceManager.isStarted()).thenReturn(true);

        // validate (and capture) that register manage status callback
        mInOrder.verify(mHalDeviceManager).registerStatusListener(
                mManagerStatusListenerCaptor.capture(), any());

        // 1 & 2 onStatusChange (ready/started): validate that trying to get a NAN interface
        // (make sure gets a NULL)
        when(mHalDeviceManager.createNanIface(any(), any())).thenReturn(null);

        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mHalDeviceManager).registerInterfaceAvailableForRequestListener(
                eq(IfaceType.NAN), mAvailListenerCaptor.capture(), any());
        mAvailListenerCaptor.getValue().onAvailableForRequest();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), any());
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 3 & 4 onAvailableForRequest + non-null return value: validate that enables usage
        when(mHalDeviceManager.createNanIface(any(), any())).thenReturn(mWifiNanIfaceMock);

        mAvailListenerCaptor.getValue().onAvailableForRequest();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), any());
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 5 onStatusChange (!started): disable usage
        when(mHalDeviceManager.isStarted()).thenReturn(false);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 6 & 7 onStatusChange (ready/started) + non-null NAN interface: enable usage
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mHalDeviceManager).registerInterfaceAvailableForRequestListener(
                eq(IfaceType.NAN), mAvailListenerCaptor.capture(), any());
        mAvailListenerCaptor.getValue().onAvailableForRequest();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), any());
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 8 onDestroyed: disable usage
        mDestroyedListenerCaptor.getValue().onDestroyed();

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 9 onStatusChange (!started): nothing more happens
        when(mHalDeviceManager.isStarted()).thenReturn(false);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        verifyNoMoreInteractions(mWifiAwareStateManagerMock);
    }
}
