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

package com.android.server.wifi;

import static com.android.server.wifi.HalDeviceManager.START_HAL_RETRY_TIMES;

import static junit.framework.Assert.assertEquals;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.os.IHwBinder;
import android.os.test.TestLooper;
import android.util.Log;

import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unit test harness for HalDeviceManagerTest.
 */
public class HalDeviceManagerTest {
    private HalDeviceManager mDut;
    @Mock IServiceManager mServiceManagerMock;
    @Mock IWifi mWifiMock;
    @Mock HalDeviceManager.ManagerStatusListener mManagerStatusListenerMock;
    private TestLooper mTestLooper;
    private ArgumentCaptor<IHwBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private ArgumentCaptor<IWifiEventCallback> mWifiEventCallbackCaptor = ArgumentCaptor.forClass(
            IWifiEventCallback.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();
    private WifiStatus mStatusOk;
    private WifiStatus mStatusFail;

    private class HalDeviceManagerSpy extends HalDeviceManager {
        @Override
        protected IWifi getWifiServiceMockable() {
            return mWifiMock;
        }

        @Override
        protected IServiceManager getServiceManagerMockable() {
            return mServiceManagerMock;
        }
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();

        // initialize dummy status objects
        mStatusOk = getStatus(WifiStatusCode.SUCCESS);
        mStatusFail = getStatus(WifiStatusCode.ERROR_UNKNOWN);

        when(mServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong())).thenReturn(true);
        when(mServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mServiceManagerMock.getTransport(
                eq(IWifi.kInterfaceName), eq(HalDeviceManager.HAL_INSTANCE_NAME)))
                .thenReturn(IServiceManager.Transport.HWBINDER);
        when(mWifiMock.linkToDeath(any(IHwBinder.DeathRecipient.class), anyLong())).thenReturn(
                true);
        when(mWifiMock.registerEventCallback(any(IWifiEventCallback.class))).thenReturn(mStatusOk);
        when(mWifiMock.start()).thenReturn(mStatusOk);
        when(mWifiMock.stop()).thenReturn(mStatusOk);

        mDut = new HalDeviceManagerSpy();
    }

    /**
     * Print out the dump of the device manager after each test. Not used in test validation
     * (internal state) - but can help in debugging failed tests.
     */
    @After
    public void after() throws Exception {
        dumpDut("after: ");
    }

    /**
     * Test basic startup flow:
     * - IServiceManager registrations
     * - IWifi registrations
     * - IWifi startup delayed
     * - Start Wi-Fi -> onStart
     * - Stop Wi-Fi -> onStop
     */
    @Test
    public void testStartStopFlow() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: onStop called
        mInOrder.verify(mWifiMock).stop();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Test the service manager notification coming in after
     * {@link HalDeviceManager#initIWifiIfNecessary()} is already invoked as a part of
     * {@link HalDeviceManager#initialize()}.
     */
    @Test
    public void testServiceRegisterationAfterInitialize() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // This should now be ignored since IWifi is already non-null.
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", true);

        verifyNoMoreInteractions(mManagerStatusListenerMock, mWifiMock, mServiceManagerMock);
    }

    /**
     * Validate that multiple callback registrations are called and that duplicate ones are
     * only called once.
     */
    @Test
    public void testMultipleCallbackRegistrations() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();

        // register another 2 callbacks - one of them twice
        HalDeviceManager.ManagerStatusListener callback1 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        HalDeviceManager.ManagerStatusListener callback2 = mock(
                HalDeviceManager.ManagerStatusListener.class);
        mDut.registerStatusListener(callback2, mTestLooper.getLooper());
        mDut.registerStatusListener(callback1, mTestLooper.getLooper());
        mDut.registerStatusListener(callback2, mTestLooper.getLooper());

        // startup
        executeAndValidateStartupSequence();

        // verify
        verify(callback1).onStatusChanged();
        verify(callback2).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, callback1, callback2);
    }

    /**
     * Validate IWifi death listener and registration flow.
     */
    @Test
    public void testWifiDeathAndRegistration() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: IWifi service death
        mDeathRecipientCaptor.getValue().serviceDied(0);
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: service startup
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", false);

        // verify: initialization of IWifi
        mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
        mInOrder.verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());

        // act: start
        collector.checkThat(mDut.start(), equalTo(true));
        mWifiEventCallbackCaptor.getValue().onStart();
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate IWifi onFailure causes notification
     */
    @Test
    public void testWifiFail() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // act: IWifi failure
        mWifiEventCallbackCaptor.getValue().onFailure(mStatusFail);
        mTestLooper.dispatchAll();

        // verify: getting onStop
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        // act: start again
        collector.checkThat(mDut.start(), equalTo(true));
        mWifiEventCallbackCaptor.getValue().onStart();
        mTestLooper.dispatchAll();

        // verify: service and callback calls
        mInOrder.verify(mWifiMock).start();
        mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock);
    }

    /**
     * Validate creation of STA interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateStaInterfaceNoInitMode() throws Exception {
        final String name = "sta0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiStaIface iface = (IWifiStaIface) validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        mInOrder.verify(chipMock.chip).removeStaIface(name);
        verify(idl).onDestroyed();
        verify(iafrl).onAvailableForRequest();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate creation of AP interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateApInterfaceNoInitMode() throws Exception {
        final String name = "ap0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiApIface iface = (IWifiApIface) validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.AP, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        mInOrder.verify(chipMock.chip).removeApIface(name);
        verify(idl).onDestroyed();
        verify(iafrl).onAvailableForRequest();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate creation of P2P interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateP2pInterfaceNoInitMode() throws Exception {
        final String name = "p2p0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiP2pIface iface = (IWifiP2pIface) validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.P2P, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        mInOrder.verify(chipMock.chip).removeP2pIface(name);
        verify(idl).onDestroyed();
        verify(iafrl).onAvailableForRequest();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate creation of NAN interface from blank start-up. The remove interface.
     */
    @Test
    public void testCreateNanInterfaceNoInitMode() throws Exception {
        final String name = "nan0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiNanIface iface = (IWifiNanIface) validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.NAN, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: remove interface
        mDut.removeIface(iface);
        mTestLooper.dispatchAll();

        // verify: callback triggered
        mInOrder.verify(chipMock.chip).removeNanIface(name);
        verify(idl).onDestroyed();
        verify(iafrl).onAvailableForRequest();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate creation of AP interface when in STA mode - but with no interface created. Expect
     * a change in chip mode.
     */
    @Test
    public void testCreateApWithStaModeUp() throws Exception {
        final String name = "ap0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiApIface iface = (IWifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.AP, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed();
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate creation of AP interface when in AP mode - but with no interface created. Expect
     * no change in chip mode.
     */
    @Test
    public void testCreateApWithApModeUp() throws Exception {
        final String name = "ap0";

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener idl = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener iafrl = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        IWifiApIface iface = (IWifiApIface) validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.AP_CHIP_MODE_ID, // chipModeId
                IfaceType.AP, // ifaceTypeToCreate
                name, // ifaceName
                BaselineChip.AP_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                idl, // destroyedListener
                iafrl // availableListener
        );
        collector.checkThat("allocated interface", iface, IsNull.notNullValue());

        // act: stop Wi-Fi
        mDut.stop();
        mTestLooper.dispatchAll();

        // verify: callback triggered
        verify(idl).onDestroyed();
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();

        verifyNoMoreInteractions(mManagerStatusListenerMock, idl, iafrl);
    }

    /**
     * Validate AP up/down creation of AP interface when a STA already created. Expect:
     * - STA created
     * - P2P created
     * - When AP requested:
     *   - STA & P2P torn down
     *   - AP created
     * - P2P creation refused
     * - Request STA: will tear down AP
     * - When AP destroyed:
     *   - Get p2p available listener callback
     *   - Can create P2P when requested
     * - Create P2P
     * - Request NAN: will get refused
     * - Tear down P2P:
     *    - should get nan available listener callback
     *    - Can create NAN when requested
     */
    @Test
    public void testCreateSameAndDiffPriorities() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener2 = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);

        HalDeviceManager.InterfaceDestroyedListener apDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener apAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener p2pDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener p2pAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener p2pDestroyedListener2 = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);

        HalDeviceManager.InterfaceDestroyedListener nanDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener nanAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                staAvailListener // availableListener
        );
        collector.checkThat("allocated STA interface", staIface, IsNull.notNullValue());

        // register additional InterfaceDestroyedListeners - including a duplicate (verify that
        // only called once!)
        mDut.registerDestroyedListener(staIface, staDestroyedListener2, mTestLooper.getLooper());
        mDut.registerDestroyedListener(staIface, staDestroyedListener, mTestLooper.getLooper());

        // Request P2P
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener, // destroyedListener
                p2pAvailListener // availableListener
        );
        collector.checkThat("allocated P2P interface", p2pIface, IsNull.notNullValue());

        // Request AP
        IWifiIface apIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.AP, // ifaceTypeToCreate
                "ap0", // ifaceName
                BaselineChip.AP_CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{staIface, p2pIface}, // tearDownList
                apDestroyedListener, // destroyedListener
                apAvailListener, // availableListener
                // destroyedInterfacesDestroyedListeners...
                staDestroyedListener, staDestroyedListener2, p2pDestroyedListener
        );
        collector.checkThat("allocated AP interface", apIface, IsNull.notNullValue());

        // Request P2P: expect failure
        p2pIface = mDut.createP2pIface(p2pDestroyedListener, mTestLooper.getLooper());
        collector.checkThat("P2P can't be created", p2pIface, IsNull.nullValue());

        // Request STA: expect success
        staIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.AP_CHIP_MODE_ID, // chipModeId
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                staAvailListener, // availableListener
                apDestroyedListener // destroyedInterfacesDestroyedListeners...
        );
        collector.checkThat("allocated STA interface", staIface, IsNull.notNullValue());

        mTestLooper.dispatchAll();
        verify(apDestroyedListener).onDestroyed();

        // Request P2P: expect success now
        p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                p2pDestroyedListener2, // destroyedListener
                p2pAvailListener // availableListener
        );

        // Request NAN: should fail
        IWifiIface nanIface = mDut.createNanIface(nanDestroyedListener, mTestLooper.getLooper());
        mDut.registerInterfaceAvailableForRequestListener(IfaceType.NAN, nanAvailListener,
                mTestLooper.getLooper());
        collector.checkThat("NAN can't be created", nanIface, IsNull.nullValue());

        // Tear down P2P
        mDut.removeIface(p2pIface);
        mTestLooper.dispatchAll();

        verify(chipMock.chip, times(2)).removeP2pIface("p2p0");
        verify(p2pDestroyedListener2).onDestroyed();

        // Should now be able to request and get NAN
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.NAN, // ifaceTypeToCreate
                "nan0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                nanAvailListener // availableListener
        );
        collector.checkThat("allocated NAN interface", nanIface, IsNull.notNullValue());

        // available callback verification
        verify(staAvailListener).onAvailableForRequest();
        verify(apAvailListener, times(4)).onAvailableForRequest();
        verify(p2pAvailListener, times(3)).onAvailableForRequest();
        verify(nanAvailListener).onAvailableForRequest();

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener, staAvailListener,
                staDestroyedListener2, apDestroyedListener, apAvailListener, p2pDestroyedListener,
                nanDestroyedListener, nanAvailListener, p2pDestroyedListener2);
    }

    /**
     * Validate P2P and NAN interactions. Expect:
     * - STA created
     * - NAN created
     * - When P2P requested:
     *   - NAN torn down
     *   - P2P created
     * - NAN creation refused
     * - When P2P destroyed:
     *   - get nan available listener
     *   - Can create NAN when requested
     */
    @Test
    public void testP2pAndNanInteractions() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener nanDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener nanAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener p2pDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener p2pAvailListener = null;

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                staAvailListener // availableListener
        );

        // Request NAN
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.NAN, // ifaceTypeToCreate
                "nan0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                nanAvailListener // availableListener
        );

        // Request P2P
        IWifiIface p2pIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.P2P, // ifaceTypeToCreate
                "p2p0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                new IWifiIface[]{nanIface}, // tearDownList
                p2pDestroyedListener, // destroyedListener
                p2pAvailListener, // availableListener
                nanDestroyedListener // destroyedInterfacesDestroyedListeners...
        );

        // Request NAN: expect failure
        nanIface = mDut.createNanIface(nanDestroyedListener, mTestLooper.getLooper());
        mDut.registerInterfaceAvailableForRequestListener(IfaceType.NAN, nanAvailListener,
                mTestLooper.getLooper());
        collector.checkThat("NAN can't be created", nanIface, IsNull.nullValue());

        // Destroy P2P interface
        boolean status = mDut.removeIface(p2pIface);
        mInOrder.verify(chipMock.chip).removeP2pIface("p2p0");
        collector.checkThat("P2P removal success", status, equalTo(true));

        mTestLooper.dispatchAll();
        verify(p2pDestroyedListener).onDestroyed();
        verify(nanAvailListener).onAvailableForRequest();

        // Request NAN: expect success now
        nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.NAN, // ifaceTypeToCreate
                "nan0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                nanAvailListener // availableListener
        );

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener, staAvailListener,
                nanDestroyedListener, nanAvailListener, p2pDestroyedListener);
    }

    /**
     * Validates that when (for some reason) the cache is out-of-sync with the actual chip status
     * then Wi-Fi is shut-down.
     */
    @Test
    public void testCacheMismatchError() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener nanDestroyedListener = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener nanAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        // Request STA
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener, // destroyedListener
                staAvailListener // availableListener
        );

        // Request NAN
        IWifiIface nanIface = validateInterfaceSequence(chipMock,
                true, // chipModeValid
                BaselineChip.STA_CHIP_MODE_ID, // chipModeId
                IfaceType.NAN, // ifaceTypeToCreate
                "nan0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                nanDestroyedListener, // destroyedListener
                nanAvailListener // availableListener
        );

        // fiddle with the "chip" by removing the STA
        chipMock.interfaceNames.get(IfaceType.STA).remove("sta0");

        // now try to request another NAN
        nanIface = mDut.createNanIface(nanDestroyedListener, mTestLooper.getLooper());
        mDut.registerInterfaceAvailableForRequestListener(IfaceType.NAN, nanAvailListener,
                mTestLooper.getLooper());
        collector.checkThat("NAN can't be created", nanIface, IsNull.nullValue());

        // verify that Wi-Fi is shut-down: should also get all onDestroyed messages that are
        // registered (even if they seem out-of-sync to chip)
        mTestLooper.dispatchAll();
        verify(mWifiMock, times(2)).stop();
        verify(mManagerStatusListenerMock, times(2)).onStatusChanged();
        verify(staDestroyedListener).onDestroyed();
        verify(nanDestroyedListener).onDestroyed();

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener, staAvailListener,
                nanDestroyedListener, nanAvailListener);
    }

    /**
     * Validates that trying to allocate a STA and then another STA fails. Only one STA at a time
     * is permitted (by baseline chip).
     */
    @Test
    public void testDuplicateStaRequests() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener1 = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);
        HalDeviceManager.InterfaceAvailableForRequestListener staAvailListener1 = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        HalDeviceManager.InterfaceDestroyedListener staDestroyedListener2 = mock(
                HalDeviceManager.InterfaceDestroyedListener.class);

        // get STA interface
        IWifiIface staIface1 = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                staDestroyedListener1, // destroyedListener
                staAvailListener1 // availableListener
        );
        collector.checkThat("STA created", staIface1, IsNull.notNullValue());

        // get STA interface again
        IWifiIface staIface2 = mDut.createStaIface(staDestroyedListener2, mTestLooper.getLooper());
        collector.checkThat("STA created", staIface2, IsNull.nullValue());

        verifyNoMoreInteractions(mManagerStatusListenerMock, staDestroyedListener1,
                staAvailListener1, staDestroyedListener2);
    }

    /**
     * Validates that a duplicate registration of the same InterfaceAvailableForRequestListener
     * listener will result in a single callback.
     *
     * Also validates that get an immediate call on registration if available.
     */
    @Test
    public void testDuplicateAvailableRegistrations() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        HalDeviceManager.InterfaceAvailableForRequestListener staAvailListener = mock(
                HalDeviceManager.InterfaceAvailableForRequestListener.class);

        // get STA interface
        IWifiIface staIface = validateInterfaceSequence(chipMock,
                false, // chipModeValid
                -1000, // chipModeId (only used if chipModeValid is true)
                IfaceType.STA, // ifaceTypeToCreate
                "sta0", // ifaceName
                BaselineChip.STA_CHIP_MODE_ID, // finalChipMode
                null, // tearDownList
                null, // destroyedListener
                null // availableListener
        );
        collector.checkThat("STA created", staIface, IsNull.notNullValue());

        // act: register the same listener twice
        mDut.registerInterfaceAvailableForRequestListener(IfaceType.STA, staAvailListener,
                mTestLooper.getLooper());
        mDut.registerInterfaceAvailableForRequestListener(IfaceType.STA, staAvailListener,
                mTestLooper.getLooper());
        mTestLooper.dispatchAll();

        // remove STA interface -> should trigger callbacks
        mDut.removeIface(staIface);
        mTestLooper.dispatchAll();

        // verify: only a single trigger
        verify(staAvailListener).onAvailableForRequest();

        verifyNoMoreInteractions(staAvailListener);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for all chips.
     */
    @Test
    public void testGetSupportedIfaceTypesAll() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that the getSupportedIfaceTypes API works when requesting for a specific chip.
     */
    @Test
    public void testGetSupportedIfaceTypesOneChip() throws Exception {
        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence();

        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes(chipMock.chip);

        // verify results
        Set<Integer> correctResults = new HashSet<>();
        correctResults.add(IfaceType.AP);
        correctResults.add(IfaceType.STA);
        correctResults.add(IfaceType.P2P);
        correctResults.add(IfaceType.NAN);

        assertEquals(correctResults, results);
    }

    /**
     * Validate that when no chip info is found an empty list is returned.
     */
    @Test
    public void testGetSupportedIfaceTypesError() throws Exception {
        // try API
        Set<Integer> results = mDut.getSupportedIfaceTypes();

        // verify results
        assertEquals(0, results.size());
    }

    /**
     * Test start HAL can retry upon failure.
     */
    @Test
    public void testStartHalRetryUponNotAvailableFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start())
            .thenReturn(getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE))
            .thenReturn(mStatusOk);

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip,
                mManagerStatusListenerMock);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(2, true);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     */
    @Test
    public void testStartHalRetryFailUponMultipleNotAvailableFailures() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE));

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(START_HAL_RETRY_TIMES + 1, false);
    }

    /**
     * Test start HAL fails after multiple retry failures.
     */
    @Test
    public void testStartHalRetryFailUponTrueFailure() throws Exception {
        // Override the stubbing for mWifiMock in before().
        when(mWifiMock.start()).thenReturn(getStatus(WifiStatusCode.ERROR_UNKNOWN));

        BaselineChip chipMock = new BaselineChip();
        chipMock.initialize();
        mInOrder = inOrder(mServiceManagerMock, mWifiMock, chipMock.chip);
        executeAndValidateInitializationSequence();
        executeAndValidateStartupSequence(1, false);
    }

    /**
     * Validate that isSupported() returns true when IServiceManager finds the vendor HAL daemon in
     * the VINTF.
     */
    @Test
    public void testIsSupportedTrue() throws Exception {
        mInOrder = inOrder(mServiceManagerMock, mWifiMock);
        executeAndValidateInitializationSequence();
        assertTrue(mDut.isSupported());
    }

    /**
     * Validate that isSupported() returns false when IServiceManager does not find the vendor HAL
     * daemon in the VINTF.
     */
    @Test
    public void testIsSupportedFalse() throws Exception {
        when(mServiceManagerMock.getTransport(
                eq(IWifi.kInterfaceName), eq(HalDeviceManager.HAL_INSTANCE_NAME)))
                .thenReturn(IServiceManager.Transport.EMPTY);
        mInOrder = inOrder(mServiceManagerMock, mWifiMock);
        executeAndValidateInitializationSequence(false);
        assertFalse(mDut.isSupported());
    }

    // utilities
    private void dumpDut(String prefix) {
        StringWriter sw = new StringWriter();
        mDut.dump(null, new PrintWriter(sw), null);
        Log.e("HalDeviceManager", prefix + sw.toString());
    }

    private void executeAndValidateInitializationSequence() throws Exception {
        executeAndValidateInitializationSequence(true);
    }

    private void executeAndValidateInitializationSequence(boolean isSupported) throws Exception {
        // act:
        mDut.initialize();

        // verify: service manager initialization sequence
        mInOrder.verify(mServiceManagerMock).linkToDeath(any(IHwBinder.DeathRecipient.class),
                anyLong());
        mInOrder.verify(mServiceManagerMock).registerForNotifications(eq(IWifi.kInterfaceName),
                eq(""), mServiceNotificationCaptor.capture());

        // The service should already be up at this point.
        mInOrder.verify(mServiceManagerMock).getTransport(eq(IWifi.kInterfaceName),
                eq(HalDeviceManager.HAL_INSTANCE_NAME));

        // verify: wifi initialization sequence if vendor HAL is supported.
        if (isSupported) {
            mInOrder.verify(mWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
            mInOrder.verify(mWifiMock).registerEventCallback(mWifiEventCallbackCaptor.capture());
            // verify: onStop called as a part of initialize.
            mInOrder.verify(mWifiMock).stop();
            collector.checkThat("isReady is true", mDut.isReady(), equalTo(true));
        } else {
            collector.checkThat("isReady is false", mDut.isReady(), equalTo(false));
        }
    }

    private void executeAndValidateStartupSequence()throws Exception {
        executeAndValidateStartupSequence(1, true);
    }

    private void executeAndValidateStartupSequence(int numAttempts, boolean success)
            throws Exception {
        // act: register listener & start Wi-Fi
        mDut.registerStatusListener(mManagerStatusListenerMock, mTestLooper.getLooper());
        collector.checkThat(mDut.start(), equalTo(success));

        // verify
        mInOrder.verify(mWifiMock, times(numAttempts)).start();

        if (success) {
            // act: trigger onStart callback of IWifiEventCallback
            mWifiEventCallbackCaptor.getValue().onStart();
            mTestLooper.dispatchAll();

            // verify: onStart called on registered listener
            mInOrder.verify(mManagerStatusListenerMock).onStatusChanged();
        }
    }

    private IWifiIface validateInterfaceSequence(ChipMockBase chipMock,
            boolean chipModeValid, int chipModeId,
            int ifaceTypeToCreate, String ifaceName, int finalChipMode, IWifiIface[] tearDownList,
            HalDeviceManager.InterfaceDestroyedListener destroyedListener,
            HalDeviceManager.InterfaceAvailableForRequestListener availableListener,
            HalDeviceManager.InterfaceDestroyedListener... destroyedInterfacesDestroyedListeners)
            throws Exception {
        // configure chip mode response
        chipMock.chipModeValid = chipModeValid;
        chipMock.chipModeId = chipModeId;

        IWifiIface iface = null;

        // configure: interface to be created
        // act: request the interface
        switch (ifaceTypeToCreate) {
            case IfaceType.STA:
                iface = mock(IWifiStaIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.STA)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createStaIface(any(IWifiChip.createStaIfaceCallback.class));

                mDut.createStaIface(destroyedListener, mTestLooper.getLooper());
                break;
            case IfaceType.AP:
                iface = mock(IWifiApIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.AP)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createApIface(any(IWifiChip.createApIfaceCallback.class));

                mDut.createApIface(destroyedListener, mTestLooper.getLooper());
                break;
            case IfaceType.P2P:
                iface = mock(IWifiP2pIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.P2P)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createP2pIface(any(IWifiChip.createP2pIfaceCallback.class));

                mDut.createP2pIface(destroyedListener, mTestLooper.getLooper());
                break;
            case IfaceType.NAN:
                iface = mock(IWifiNanIface.class);
                doAnswer(new GetNameAnswer(ifaceName)).when(iface).getName(
                        any(IWifiIface.getNameCallback.class));
                doAnswer(new GetTypeAnswer(IfaceType.NAN)).when(iface).getType(
                        any(IWifiIface.getTypeCallback.class));
                doAnswer(new CreateXxxIfaceAnswer(chipMock, mStatusOk, iface)).when(
                        chipMock.chip).createNanIface(any(IWifiChip.createNanIfaceCallback.class));

                mDut.createNanIface(destroyedListener, mTestLooper.getLooper());
                break;
        }
        if (availableListener != null) {
            mDut.registerInterfaceAvailableForRequestListener(ifaceTypeToCreate, availableListener,
                    mTestLooper.getLooper());
        }

        // validate: optional tear down of interfaces
        if (tearDownList != null) {
            for (IWifiIface tearDownIface: tearDownList) {
                switch (getType(tearDownIface)) {
                    case IfaceType.STA:
                        mInOrder.verify(chipMock.chip).removeStaIface(getName(tearDownIface));
                        break;
                    case IfaceType.AP:
                        mInOrder.verify(chipMock.chip).removeApIface(getName(tearDownIface));
                        break;
                    case IfaceType.P2P:
                        mInOrder.verify(chipMock.chip).removeP2pIface(getName(tearDownIface));
                        break;
                    case IfaceType.NAN:
                        mInOrder.verify(chipMock.chip).removeNanIface(getName(tearDownIface));
                        break;
                }
            }
        }

        // validate: optional switch to the requested mode
        if (!chipModeValid || chipModeId != finalChipMode) {
            mInOrder.verify(chipMock.chip).configureChip(finalChipMode);
        } else {
            mInOrder.verify(chipMock.chip, times(0)).configureChip(anyInt());
        }

        // validate: create interface
        switch (ifaceTypeToCreate) {
            case IfaceType.STA:
                mInOrder.verify(chipMock.chip).createStaIface(
                        any(IWifiChip.createStaIfaceCallback.class));
                break;
            case IfaceType.AP:
                mInOrder.verify(chipMock.chip).createApIface(
                        any(IWifiChip.createApIfaceCallback.class));
                break;
            case IfaceType.P2P:
                mInOrder.verify(chipMock.chip).createP2pIface(
                        any(IWifiChip.createP2pIfaceCallback.class));
                break;
            case IfaceType.NAN:
                mInOrder.verify(chipMock.chip).createNanIface(
                        any(IWifiChip.createNanIfaceCallback.class));
                break;
        }

        // verify: callbacks on deleted interfaces
        mTestLooper.dispatchAll();
        for (int i = 0; i < destroyedInterfacesDestroyedListeners.length; ++i) {
            verify(destroyedInterfacesDestroyedListeners[i]).onDestroyed();
        }

        return iface;
    }

    private int getType(IWifiIface iface) throws Exception {
        Mutable<Integer> typeResp = new Mutable<>();
        iface.getType((WifiStatus status, int type) -> {
            typeResp.value = type;
        });
        return typeResp.value;
    }

    private String getName(IWifiIface iface) throws Exception {
        Mutable<String> nameResp = new Mutable<>();
        iface.getName((WifiStatus status, String name) -> {
            nameResp.value = name;
        });
        return nameResp.value;
    }

    private WifiStatus getStatus(int code) {
        WifiStatus status = new WifiStatus();
        status.code = code;
        return status;
    }

    private static class Mutable<E> {
        public E value;

        Mutable() {
            value = null;
        }

        Mutable(E value) {
            this.value = value;
        }
    }

    // Answer objects
    private class GetChipIdsAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private ArrayList<Integer> mChipIds;

        GetChipIdsAnswer(WifiStatus status, ArrayList<Integer> chipIds) {
            mStatus = status;
            mChipIds = chipIds;
        }

        public void answer(IWifi.getChipIdsCallback cb) {
            cb.onValues(mStatus, mChipIds);
        }
    }

    private class GetChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private WifiStatus mStatus;
        private IWifiChip mChip;

        GetChipAnswer(WifiStatus status, IWifiChip chip) {
            mStatus = status;
            mChip = chip;
        }

        public void answer(int chipId, IWifi.getChipCallback cb) {
            cb.onValues(mStatus, mChip);
        }
    }

    private class GetIdAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetIdAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getIdCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.chipId);
        }
    }

    private class GetAvailableModesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetAvailableModesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getAvailableModesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.availableModes);
        }
    }

    private class GetModeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetModeAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getModeCallback cb) {
            cb.onValues(mChipMockBase.chipModeValid ? mStatusOk
                    : getStatus(WifiStatusCode.ERROR_NOT_AVAILABLE), mChipMockBase.chipModeId);
        }
    }

    private class ConfigureChipAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        ConfigureChipAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public WifiStatus answer(int chipMode) {
            mChipMockBase.chipModeId = chipMode;
            return mStatusOk;
        }
    }

    private class GetXxxIfaceNamesAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetXxxIfaceNamesAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(IWifiChip.getStaIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.STA));
        }

        public void answer(IWifiChip.getApIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.AP));
        }

        public void answer(IWifiChip.getP2pIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.P2P));
        }

        public void answer(IWifiChip.getNanIfaceNamesCallback cb) {
            cb.onValues(mStatusOk, mChipMockBase.interfaceNames.get(IfaceType.NAN));
        }
    }

    private class GetXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;

        GetXxxIfaceAnswer(ChipMockBase chipMockBase) {
            mChipMockBase = chipMockBase;
        }

        public void answer(String name, IWifiChip.getStaIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.STA).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiStaIface) iface);
        }

        public void answer(String name, IWifiChip.getApIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.AP).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiApIface) iface);
        }

        public void answer(String name, IWifiChip.getP2pIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.P2P).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiP2pIface) iface);
        }

        public void answer(String name, IWifiChip.getNanIfaceCallback cb) {
            IWifiIface iface = mChipMockBase.interfacesByName.get(IfaceType.NAN).get(name);
            cb.onValues(iface != null ? mStatusOk : mStatusFail, (IWifiNanIface) iface);
        }
    }

    private class CreateXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private WifiStatus mStatus;
        private IWifiIface mWifiIface;

        CreateXxxIfaceAnswer(ChipMockBase chipMockBase, WifiStatus status, IWifiIface wifiIface) {
            mChipMockBase = chipMockBase;
            mStatus = status;
            mWifiIface = wifiIface;
        }

        private void addInterfaceInfo(int type) {
            if (mStatus.code == WifiStatusCode.SUCCESS) {
                try {
                    mChipMockBase.interfaceNames.get(type).add(getName(mWifiIface));
                    mChipMockBase.interfacesByName.get(type).put(getName(mWifiIface), mWifiIface);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }

        public void answer(IWifiChip.createStaIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiStaIface) mWifiIface);
            addInterfaceInfo(IfaceType.STA);
        }

        public void answer(IWifiChip.createApIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiApIface) mWifiIface);
            addInterfaceInfo(IfaceType.AP);
        }

        public void answer(IWifiChip.createP2pIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiP2pIface) mWifiIface);
            addInterfaceInfo(IfaceType.P2P);
        }

        public void answer(IWifiChip.createNanIfaceCallback cb) {
            cb.onValues(mStatus, (IWifiNanIface) mWifiIface);
            addInterfaceInfo(IfaceType.NAN);
        }
    }

    private class RemoveXxxIfaceAnswer extends MockAnswerUtil.AnswerWithArguments {
        private ChipMockBase mChipMockBase;
        private int mType;

        RemoveXxxIfaceAnswer(ChipMockBase chipMockBase, int type) {
            mChipMockBase = chipMockBase;
            mType = type;
        }

        private WifiStatus removeIface(int type, String ifname) {
            try {
                if (!mChipMockBase.interfaceNames.get(type).remove(ifname)) {
                    return mStatusFail;
                }
                if (mChipMockBase.interfacesByName.get(type).remove(ifname) == null) {
                    return mStatusFail;
                }
            } catch (Exception e) {
                return mStatusFail;
            }
            return mStatusOk;
        }

        public WifiStatus answer(String ifname) {
            return removeIface(mType, ifname);
        }
    }

    private class GetNameAnswer extends MockAnswerUtil.AnswerWithArguments {
        private String mName;

        GetNameAnswer(String name) {
            mName = name;
        }

        public void answer(IWifiIface.getNameCallback cb) {
            cb.onValues(mStatusOk, mName);
        }
    }

    private class GetTypeAnswer extends MockAnswerUtil.AnswerWithArguments {
        private int mType;

        GetTypeAnswer(int type) {
            mType = type;
        }

        public void answer(IWifiIface.getTypeCallback cb) {
            cb.onValues(mStatusOk, mType);
        }
    }

    // chip configuration

    private class ChipMockBase {
        public IWifiChip chip;
        public int chipId;
        public boolean chipModeValid = false;
        public int chipModeId = -1000;
        public Map<Integer, ArrayList<String>> interfaceNames = new HashMap<>();
        public Map<Integer, Map<String, IWifiIface>> interfacesByName = new HashMap<>();

        public ArrayList<IWifiChip.ChipMode> availableModes;

        void initialize() throws Exception {
            chip = mock(IWifiChip.class);

            interfaceNames.put(IfaceType.STA, new ArrayList<>());
            interfaceNames.put(IfaceType.AP, new ArrayList<>());
            interfaceNames.put(IfaceType.P2P, new ArrayList<>());
            interfaceNames.put(IfaceType.NAN, new ArrayList<>());

            interfacesByName.put(IfaceType.STA, new HashMap<>());
            interfacesByName.put(IfaceType.AP, new HashMap<>());
            interfacesByName.put(IfaceType.P2P, new HashMap<>());
            interfacesByName.put(IfaceType.NAN, new HashMap<>());

            when(chip.registerEventCallback(any(IWifiChipEventCallback.class))).thenReturn(
                    mStatusOk);
            when(chip.configureChip(anyInt())).thenAnswer(new ConfigureChipAnswer(this));
            doAnswer(new GetIdAnswer(this)).when(chip).getId(any(IWifiChip.getIdCallback.class));
            doAnswer(new GetModeAnswer(this)).when(chip).getMode(
                    any(IWifiChip.getModeCallback.class));
            GetXxxIfaceNamesAnswer getXxxIfaceNamesAnswer = new GetXxxIfaceNamesAnswer(this);
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getStaIfaceNames(
                    any(IWifiChip.getStaIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getApIfaceNames(
                    any(IWifiChip.getApIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getP2pIfaceNames(
                    any(IWifiChip.getP2pIfaceNamesCallback.class));
            doAnswer(getXxxIfaceNamesAnswer).when(chip).getNanIfaceNames(
                    any(IWifiChip.getNanIfaceNamesCallback.class));
            GetXxxIfaceAnswer getXxxIfaceAnswer = new GetXxxIfaceAnswer(this);
            doAnswer(getXxxIfaceAnswer).when(chip).getStaIface(anyString(),
                    any(IWifiChip.getStaIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getApIface(anyString(),
                    any(IWifiChip.getApIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getP2pIface(anyString(),
                    any(IWifiChip.getP2pIfaceCallback.class));
            doAnswer(getXxxIfaceAnswer).when(chip).getNanIface(anyString(),
                    any(IWifiChip.getNanIfaceCallback.class));
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.STA)).when(chip).removeStaIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.AP)).when(chip).removeApIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.P2P)).when(chip).removeP2pIface(
                    anyString());
            doAnswer(new RemoveXxxIfaceAnswer(this, IfaceType.NAN)).when(chip).removeNanIface(
                    anyString());
        }
    }

    // emulate baseline/legacy config:
    // mode: STA + NAN || P2P
    // mode: NAN
    private class BaselineChip extends ChipMockBase {
        static final int STA_CHIP_MODE_ID = 0;
        static final int AP_CHIP_MODE_ID = 1;

        void initialize() throws Exception {
            super.initialize();

            // chip Id configuration
            ArrayList<Integer> chipIds;
            chipId = 10;
            chipIds = new ArrayList<>();
            chipIds.add(chipId);
            doAnswer(new GetChipIdsAnswer(mStatusOk, chipIds)).when(mWifiMock).getChipIds(
                    any(IWifi.getChipIdsCallback.class));

            doAnswer(new GetChipAnswer(mStatusOk, chip)).when(mWifiMock).getChip(eq(10),
                    any(IWifi.getChipCallback.class));

            // initialize dummy chip modes
            IWifiChip.ChipMode cm;
            IWifiChip.ChipIfaceCombination cic;
            IWifiChip.ChipIfaceCombinationLimit cicl;

            //   Mode 0: 1xSTA + 1x{P2P,NAN}
            //   Mode 1: 1xAP
            availableModes = new ArrayList<>();
            cm = new IWifiChip.ChipMode();
            cm.id = STA_CHIP_MODE_ID;

            cic = new IWifiChip.ChipIfaceCombination();

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.STA);
            cic.limits.add(cicl);

            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.P2P);
            cicl.types.add(IfaceType.NAN);
            cic.limits.add(cicl);
            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            cm = new IWifiChip.ChipMode();
            cm.id = AP_CHIP_MODE_ID;
            cic = new IWifiChip.ChipIfaceCombination();
            cicl = new IWifiChip.ChipIfaceCombinationLimit();
            cicl.maxIfaces = 1;
            cicl.types.add(IfaceType.AP);
            cic.limits.add(cicl);
            cm.availableCombinations.add(cic);
            availableModes.add(cm);

            doAnswer(new GetAvailableModesAnswer(this)).when(chip)
                    .getAvailableModes(any(IWifiChip.getAvailableModesCallback.class));
        }
    }
}
