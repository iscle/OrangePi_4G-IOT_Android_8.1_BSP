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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.net.wifi.IApInterface;
import android.net.wifi.IWificond;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.INetworkManagementService;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link com.android.server.wifi.WifiStateMachinePrime}.
 */
@SmallTest
public class WifiStateMachinePrimeTest {
    public static final String TAG = "WifiStateMachinePrimeTest";

    private static final String CLIENT_MODE_STATE_STRING = "ClientModeState";
    private static final String SCAN_ONLY_MODE_STATE_STRING = "ScanOnlyModeState";
    private static final String SOFT_AP_MODE_STATE_STRING = "SoftAPModeState";
    private static final String WIFI_DISABLED_STATE_STRING = "WifiDisabledState";
    private static final String CLIENT_MODE_ACTIVE_STATE_STRING = "ClientModeActiveState";
    private static final String SCAN_ONLY_MODE_ACTIVE_STATE_STRING = "ScanOnlyModeActiveState";
    private static final String SOFT_AP_MODE_ACTIVE_STATE_STRING = "SoftAPModeActiveState";

    @Mock WifiInjector mWifiInjector;
    @Mock WifiApConfigStore mWifiApConfigStore;
    TestLooper mLooper;
    @Mock IWificond mWificond;
    @Mock IApInterface mApInterface;
    @Mock INetworkManagementService mNMService;
    @Mock SoftApManager mSoftApManager;
    SoftApManager.Listener mSoftApListener;
    WifiStateMachinePrime mWifiStateMachinePrime;

    /**
     * Set up the test environment.
     */
    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "Setting up ...");

        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();

        mWifiInjector = mock(WifiInjector.class);
        mWifiStateMachinePrime = createWifiStateMachinePrime();
    }

    private WifiStateMachinePrime createWifiStateMachinePrime() {
        when(mWifiInjector.makeWificond()).thenReturn(null);
        return new WifiStateMachinePrime(mWifiInjector, mLooper.getLooper(), mNMService);
    }

    /**
     * Clean up after tests - explicitly set tested object to null.
     */
    @After
    public void cleanUp() throws Exception {
        mWifiStateMachinePrime = null;
    }

    private void enterSoftApActiveMode() throws Exception {
        enterSoftApActiveMode(null);
    }

    /**
     * Helper method to enter the SoftApActiveMode for WifiStateMachinePrime.
     *
     * This method puts the test object into the correct state and verifies steps along the way.
     */
    private void enterSoftApActiveMode(WifiConfiguration wifiConfig) throws Exception {
        String fromState = mWifiStateMachinePrime.getCurrentMode();
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWificond.createApInterface()).thenReturn(mApInterface);
        doAnswer(
                new Answer<Object>() {
                    public SoftApManager answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        assertEquals(mNMService, (INetworkManagementService) args[0]);
                        mSoftApListener = (SoftApManager.Listener) args[1];
                        assertEquals(mApInterface, (IApInterface) args[2]);
                        assertEquals(wifiConfig, (WifiConfiguration) args[3]);
                        return mSoftApManager;
                    }
                }).when(mWifiInjector).makeSoftApManager(any(INetworkManagementService.class),
                                                         any(SoftApManager.Listener.class),
                                                         any(IApInterface.class),
                                                         any());
        mWifiStateMachinePrime.enterSoftAPMode(wifiConfig);
        mLooper.dispatchAll();
        Log.e("WifiStateMachinePrimeTest", "check fromState: " + fromState);
        if (!fromState.equals(WIFI_DISABLED_STATE_STRING)) {
            verify(mWificond).tearDownInterfaces();
        }
        assertEquals(SOFT_AP_MODE_ACTIVE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        verify(mSoftApManager).start();
    }

    /**
     * Test that when a new instance of WifiStateMachinePrime is created, any existing interfaces in
     * the retrieved Wificond instance are cleaned up.
     * Expectations:  When the new WifiStateMachinePrime instance is created a call to
     * Wificond.tearDownInterfaces() is made.
     */
    @Test
    public void testWificondExistsOnStartup() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        WifiStateMachinePrime testWifiStateMachinePrime =
                new WifiStateMachinePrime(mWifiInjector, mLooper.getLooper(), mNMService);
        verify(mWificond).tearDownInterfaces();
    }

    /**
     * Test that WifiStateMachinePrime properly enters the SoftApModeActiveState from the
     * WifiDisabled state.
     */
    @Test
    public void testEnterSoftApModeFromDisabled() throws Exception {
        enterSoftApActiveMode();
    }

    /**
     * Test that WifiStateMachinePrime properly enters the SoftApModeActiveState from another state.
     * Expectations: When going from one state to another, any interfaces that are still up are torn
     * down.
     */
    @Test
    public void testEnterSoftApModeFromDifferentState() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        mWifiStateMachinePrime.enterClientMode();
        mLooper.dispatchAll();
        assertEquals(CLIENT_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        enterSoftApActiveMode();
    }

    /**
     * Test that we can disable wifi fully from the SoftApModeActiveState.
     */
    @Test
    public void testDisableWifiFromSoftApModeActiveState() throws Exception {
        enterSoftApActiveMode();

        mWifiStateMachinePrime.disableWifi();
        mLooper.dispatchAll();
        verify(mSoftApManager).stop();
        verify(mWificond).tearDownInterfaces();
        assertEquals(WIFI_DISABLED_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we can disable wifi fully from the SoftApModeState.
     */
    @Test
    public void testDisableWifiFromSoftApModeState() throws Exception {
        // Use a failure getting wificond to stay in the SoftAPModeState
        when(mWifiInjector.makeWificond()).thenReturn(null);
        mWifiStateMachinePrime.enterSoftAPMode(null);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());

        mWifiStateMachinePrime.disableWifi();
        mLooper.dispatchAll();
        // mWificond will be null due to this test, no call to tearDownInterfaces here.
        assertEquals(WIFI_DISABLED_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we can switch from SoftApActiveMode to another mode.
     * Expectation: When switching out of SoftApModeActiveState we stop the SoftApManager and tear
     * down existing interfaces.
     */
    @Test
    public void testSwitchModeWhenSoftApActiveMode() throws Exception {
        enterSoftApActiveMode();

        mWifiStateMachinePrime.enterClientMode();
        mLooper.dispatchAll();
        verify(mSoftApManager).stop();
        verify(mWificond).tearDownInterfaces();
        assertEquals(CLIENT_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we do not attempt to enter SoftApModeActiveState when we cannot get a reference to
     * wificond.
     * Expectations: After a failed attempt to get wificond from WifiInjector, we should remain in
     * the SoftApModeState.
     */
    @Test
    public void testWificondNullWhenSwitchingToApMode() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);
        mWifiStateMachinePrime.enterSoftAPMode(null);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we do not attempt to enter SoftApModeActiveState when we cannot get an ApInterface
     * from wificond.
     * Expectations: After a failed attempt to get an ApInterface from WifiInjector, we should
     * remain in the SoftApModeState.
     */
    @Test
    public void testAPInterfaceFailedWhenSwitchingToApMode() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWificond.createApInterface()).thenReturn(null);
        mWifiStateMachinePrime.enterSoftAPMode(null);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we do can enter the SoftApModeActiveState if we are already in the SoftApModeState.
     * Expectations: We should exit the current SoftApModeState and re-enter before successfully
     * entering the SoftApModeActiveState.
     */
    @Test
    public void testEnterSoftApModeActiveWhenAlreadyInSoftApMode() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWificond.createApInterface()).thenReturn(null);
        mWifiStateMachinePrime.enterSoftAPMode(null);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());

        enterSoftApActiveMode();
    }

    /**
     * Test that we return to the SoftApModeState after a failure is reported when in the
     * SoftApModeActiveState.
     * Expectations: We should exit the SoftApModeActiveState and stop the SoftApManager.
     */
    @Test
    public void testSoftApFailureWhenActive() throws Exception {
        enterSoftApActiveMode();
        // now inject failure through the SoftApManager.Listener
        mSoftApListener.onStateChanged(WifiManager.WIFI_AP_STATE_FAILED, 0);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        verify(mSoftApManager).stop();
    }

    /**
     * Test that we return to the SoftApModeState after the SoftApManager is stopped in the
     * SoftApModeActiveState.
     * Expectations: We should exit the SoftApModeActiveState and stop the SoftApManager.
     */
    @Test
    public void testSoftApDisabledWhenActive() throws Exception {
        enterSoftApActiveMode();
        // now inject failure through the SoftApManager.Listener
        mSoftApListener.onStateChanged(WifiManager.WIFI_AP_STATE_FAILED, 0);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        verify(mSoftApManager).stop();
    }

    /**
     * Test that a config passed in to the call to enterSoftApMode is used to create the new
     * SoftApManager.
     * Expectations: We should create a SoftApManager in WifiInjector with the config passed in to
     * WifiStateMachinePrime to switch to SoftApMode.
     */
    @Test
    public void testConfigIsPassedToWifiInjector() throws Exception {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "ThisIsAConfig";
        enterSoftApActiveMode(config);
    }

    /**
     * Test that when enterSoftAPMode is called with a null config, we pass a null config to
     * WifiInjector.makeSoftApManager.
     *
     * Passing a null config to SoftApManager indicates that the default config should be used.
     *
     * Expectations: WifiInjector should be called with a null config.
     */
    @Test
    public void testNullConfigIsPassedToWifiInjector() throws Exception {
        enterSoftApActiveMode(null);
    }

    /**
     * Test that the proper config is used if a prior attempt fails without using the config.
     * Expectations: A call to start softap with a null config fails, but a second call has a set
     * config - this second call should use the correct config.
     */
    @Test
    public void testNullConfigFailsSecondCallWithConfigSuccessful() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWificond.createApInterface()).thenReturn(null);
        mWifiStateMachinePrime.enterSoftAPMode(null);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "ThisIsAConfig";
        enterSoftApActiveMode(config);
    }

    /**
     * Test that a failed call to start softap with a valid config has the config saved for future
     * calls to enable softap.
     *
     * Expectations: A call to start SoftAPMode with a config should write out the config if we
     * did not create a SoftApManager.
     */
    @Test
    public void testValidConfigIsSavedOnFailureToStart() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);
        when(mWifiInjector.getWifiApConfigStore()).thenReturn(mWifiApConfigStore);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "ThisIsAConfig";
        mWifiStateMachinePrime.enterSoftAPMode(config);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
        verify(mWifiApConfigStore).setApConfiguration(eq(config));
    }

    /**
     * Thest that two calls to switch to SoftAPMode in succession ends up with the correct config.
     *
     * Expectation: we should end up in SoftAPMode state configured with the second config.
     */
    @Test
    public void testStartSoftApModeTwiceWithTwoConfigs() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWificond.createApInterface()).thenReturn(mApInterface);
        when(mWifiInjector.getWifiApConfigStore()).thenReturn(mWifiApConfigStore);
        WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = "ThisIsAConfig";
        WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = "ThisIsASecondConfig";

        when(mWifiInjector.makeSoftApManager(any(INetworkManagementService.class),
                                             any(SoftApManager.Listener.class),
                                             any(IApInterface.class),
                                             eq(config1)))
                .thenReturn(mSoftApManager);
        when(mWifiInjector.makeSoftApManager(any(INetworkManagementService.class),
                                             any(SoftApManager.Listener.class),
                                             any(IApInterface.class),
                                             eq(config2)))
                .thenReturn(mSoftApManager);


        mWifiStateMachinePrime.enterSoftAPMode(config1);
        mWifiStateMachinePrime.enterSoftAPMode(config2);
        mLooper.dispatchAll();
        assertEquals(SOFT_AP_MODE_ACTIVE_STATE_STRING, mWifiStateMachinePrime.getCurrentMode());
    }

    /**
     * Test that we safely disable wifi if it is already disabled.
     * Expectations: We should not interact with wificond since we should have already cleaned up
     * everything.
     */
    @Test
    public void disableWifiWhenAlreadyOff() throws Exception {
        verifyNoMoreInteractions(mWificond);
        mWifiStateMachinePrime.disableWifi();
    }
}
