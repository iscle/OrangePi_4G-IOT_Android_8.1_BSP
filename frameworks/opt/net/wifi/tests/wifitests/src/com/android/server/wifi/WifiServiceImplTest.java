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

import static android.net.wifi.WifiManager.HOTSPOT_FAILED;
import static android.net.wifi.WifiManager.HOTSPOT_STARTED;
import static android.net.wifi.WifiManager.HOTSPOT_STOPPED;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_LOCAL_ONLY;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_TETHERED;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL;
import static android.net.wifi.WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED;
import static android.net.wifi.WifiManager.SAP_START_FAILURE_GENERAL;
import static android.net.wifi.WifiManager.SAP_START_FAILURE_NO_CHANNEL;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
import static android.provider.Settings.Secure.LOCATION_MODE_OFF;

import static com.android.server.wifi.LocalOnlyHotspotRequestInfo.HOTSPOT_NO_ERROR;
import static com.android.server.wifi.WifiController.CMD_SET_AP;
import static com.android.server.wifi.WifiController.CMD_WIFI_TOGGLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.IpConfiguration;
import android.net.wifi.ScanSettings;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserManager;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.util.AsyncChannel;
import com.android.server.wifi.WifiServiceImpl.LocalOnlyRequestorCallback;
import com.android.server.wifi.util.WifiAsyncChannel;
import com.android.server.wifi.util.WifiPermissionsUtil;


import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Unit tests for {@link WifiServiceImpl}.
 *
 * Note: this is intended to build up over time and will not immediately cover the entire file.
 */
@SmallTest
public class WifiServiceImplTest {

    private static final String TAG = "WifiServiceImplTest";
    private static final String SCAN_PACKAGE_NAME = "scanPackage";
    private static final String WHITE_LIST_SCAN_PACKAGE_NAME = "whiteListScanPackage";
    private static final int DEFAULT_VERBOSE_LOGGING = 0;
    private static final long WIFI_BACKGROUND_SCAN_INTERVAL = 10000;
    private static final String ANDROID_SYSTEM_PACKAGE = "android";
    private static final String TEST_PACKAGE_NAME = "TestPackage";
    private static final String SYSUI_PACKAGE_NAME = "com.android.systemui";
    private static final int TEST_PID = 6789;
    private static final int TEST_PID2 = 9876;
    private static final String WIFI_IFACE_NAME = "wlan0";

    private WifiServiceImpl mWifiServiceImpl;
    private TestLooper mLooper;
    private PowerManager mPowerManager;
    private Handler mHandler;
    private Messenger mAppMessenger;
    private int mPid;
    private int mPid2 = Process.myPid();

    final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    final ArgumentCaptor<IntentFilter> mIntentFilterCaptor =
            ArgumentCaptor.forClass(IntentFilter.class);

    final ArgumentCaptor<Message> mMessageCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<SoftApModeConfiguration> mSoftApModeConfigCaptor =
            ArgumentCaptor.forClass(SoftApModeConfiguration.class);

    @Mock Context mContext;
    @Mock WifiInjector mWifiInjector;
    @Mock Clock mClock;
    @Mock WifiController mWifiController;
    @Mock WifiTrafficPoller mWifiTrafficPoller;
    @Mock WifiStateMachine mWifiStateMachine;
    @Mock HandlerThread mHandlerThread;
    @Mock AsyncChannel mAsyncChannel;
    @Mock Resources mResources;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock WifiLockManager mLockManager;
    @Mock WifiMulticastLockManager mWifiMulticastLockManager;
    @Mock WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock WifiBackupRestore mWifiBackupRestore;
    @Mock WifiMetrics mWifiMetrics;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock WifiSettingsStore mSettingsStore;
    @Mock ContentResolver mContentResolver;
    @Mock PackageManager mPackageManager;
    @Mock UserManager mUserManager;
    @Mock WifiConfiguration mApConfig;
    @Mock ActivityManager mActivityManager;
    @Mock AppOpsManager mAppOpsManager;
    @Mock IBinder mAppBinder;
    @Mock LocalOnlyHotspotRequestInfo mRequestInfo;
    @Mock LocalOnlyHotspotRequestInfo mRequestInfo2;

    @Spy FakeWifiLog mLog;

    private class WifiAsyncChannelTester {
        private static final String TAG = "WifiAsyncChannelTester";
        public static final int CHANNEL_STATE_FAILURE = -1;
        public static final int CHANNEL_STATE_DISCONNECTED = 0;
        public static final int CHANNEL_STATE_HALF_CONNECTED = 1;
        public static final int CHANNEL_STATE_FULLY_CONNECTED = 2;

        private int mState = CHANNEL_STATE_DISCONNECTED;
        private WifiAsyncChannel mChannel;
        private WifiLog mAsyncTestLog;

        WifiAsyncChannelTester(WifiInjector wifiInjector) {
            mAsyncTestLog = wifiInjector.makeLog(TAG);
        }

        public int getChannelState() {
            return mState;
        }

        public void connect(final Looper looper, final Messenger messenger,
                final Handler incomingMessageHandler) {
            assertEquals("AsyncChannel must be in disconnected state",
                    CHANNEL_STATE_DISCONNECTED, mState);
            mChannel = new WifiAsyncChannel(TAG);
            mChannel.setWifiLog(mLog);
            Handler handler = new Handler(mLooper.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                            if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                                mChannel.sendMessage(AsyncChannel.CMD_CHANNEL_FULL_CONNECTION);
                                mState = CHANNEL_STATE_HALF_CONNECTED;
                            } else {
                                mState = CHANNEL_STATE_FAILURE;
                            }
                            break;
                        case AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED:
                            mState = CHANNEL_STATE_FULLY_CONNECTED;
                            break;
                        case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                            mState = CHANNEL_STATE_DISCONNECTED;
                            break;
                        default:
                            incomingMessageHandler.handleMessage(msg);
                            break;
                    }
                }
            };
            mChannel.connect(null, handler, messenger);
        }

        private Message sendMessageSynchronously(Message request) {
            return mChannel.sendMessageSynchronously(request);
        }

        private void sendMessage(Message request) {
            mChannel.sendMessage(request);
        }
    }

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mHandler = spy(new Handler(mLooper.getLooper()));
        mAppMessenger = new Messenger(mHandler);

        when(mRequestInfo.getPid()).thenReturn(mPid);
        when(mRequestInfo2.getPid()).thenReturn(mPid2);
        when(mWifiInjector.getUserManager()).thenReturn(mUserManager);
        when(mWifiInjector.getWifiController()).thenReturn(mWifiController);
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        when(mWifiInjector.getWifiStateMachine()).thenReturn(mWifiStateMachine);
        when(mWifiStateMachine.syncInitialize(any())).thenReturn(true);
        when(mWifiInjector.getWifiServiceHandlerThread()).thenReturn(mHandlerThread);
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        doNothing().when(mFrameworkFacade).registerContentObserver(eq(mContext), any(),
                anyBoolean(), any());
        when(mContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(mFrameworkFacade.getLongSetting(
                eq(mContext),
                eq(Settings.Global.WIFI_SCAN_BACKGROUND_THROTTLE_INTERVAL_MS),
                anyLong()))
                .thenReturn(WIFI_BACKGROUND_SCAN_INTERVAL);
        when(mFrameworkFacade.getStringSetting(
                eq(mContext),
                eq(Settings.Global.WIFI_SCAN_BACKGROUND_THROTTLE_PACKAGE_WHITELIST)))
                .thenReturn(WHITE_LIST_SCAN_PACKAGE_NAME);
        IPowerManager powerManagerService = mock(IPowerManager.class);
        mPowerManager = new PowerManager(mContext, powerManagerService, new Handler());
        when(mContext.getSystemServiceName(PowerManager.class)).thenReturn(Context.POWER_SERVICE);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        WifiAsyncChannel wifiAsyncChannel = new WifiAsyncChannel("WifiServiceImplTest");
        wifiAsyncChannel.setWifiLog(mLog);
        when(mFrameworkFacade.makeWifiAsyncChannel(anyString())).thenReturn(wifiAsyncChannel);
        when(mWifiInjector.getFrameworkFacade()).thenReturn(mFrameworkFacade);
        when(mWifiInjector.getWifiLockManager()).thenReturn(mLockManager);
        when(mWifiInjector.getWifiMulticastLockManager()).thenReturn(mWifiMulticastLockManager);
        when(mWifiInjector.getWifiLastResortWatchdog()).thenReturn(mWifiLastResortWatchdog);
        when(mWifiInjector.getWifiBackupRestore()).thenReturn(mWifiBackupRestore);
        when(mWifiInjector.makeLog(anyString())).thenReturn(mLog);
        WifiTrafficPoller wifiTrafficPoller = new WifiTrafficPoller(mContext,
                mLooper.getLooper(), "mockWlan");
        when(mWifiInjector.getWifiTrafficPoller()).thenReturn(wifiTrafficPoller);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiInjector.getWifiSettingsStore()).thenReturn(mSettingsStore);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        mWifiServiceImpl = new WifiServiceImpl(mContext, mWifiInjector, mAsyncChannel);
        mWifiServiceImpl.setWifiHandlerLogForTest(mLog);
    }

    private WifiAsyncChannelTester verifyAsyncChannelHalfConnected() {
        WifiAsyncChannelTester channelTester = new WifiAsyncChannelTester(mWifiInjector);
        Handler handler = mock(Handler.class);
        TestLooper looper = new TestLooper();
        channelTester.connect(looper.getLooper(), mWifiServiceImpl.getWifiServiceMessenger(),
                handler);
        mLooper.dispatchAll();
        assertEquals("AsyncChannel must be half connected",
                WifiAsyncChannelTester.CHANNEL_STATE_HALF_CONNECTED,
                channelTester.getChannelState());
        return channelTester;
    }

    /**
     * Verifies that any operations on WifiServiceImpl without setting up the WifiStateMachine
     * channel would fail.
     */
    @Test
    public void testRemoveNetworkUnknown() {
        assertFalse(mWifiServiceImpl.removeNetwork(-1));
        verify(mWifiStateMachine, never()).syncRemoveNetwork(any(), anyInt());
    }

    /**
     * Tests whether we're able to set up an async channel connection with WifiServiceImpl.
     * This is the path used by some WifiManager public API calls.
     */
    @Test
    public void testAsyncChannelHalfConnected() {
        verifyAsyncChannelHalfConnected();
    }

    /**
     * Tests the isValid() check for StaticIpConfigurations, ensuring that configurations with null
     * ipAddress are rejected, and configurations with ipAddresses are valid.
     */
    @Test
    public void testStaticIpConfigurationValidityCheck() {
        WifiConfiguration conf = WifiConfigurationTestUtil.createOpenNetwork();
        IpConfiguration ipConf =
                WifiConfigurationTestUtil.createStaticIpConfigurationWithStaticProxy();
        conf.setIpConfiguration(ipConf);
        // Ensure staticIpConfiguration with IP Address is valid
        assertTrue(mWifiServiceImpl.isValid(conf));
        ipConf.staticIpConfiguration.ipAddress = null;
        // Ensure staticIpConfiguration with null IP Address it is not valid
        conf.setIpConfiguration(ipConf);
        assertFalse(mWifiServiceImpl.isValid(conf));
    }

    /**
     * Ensure WifiMetrics.dump() is the only dump called when 'dumpsys wifi WifiMetricsProto' is
     * called. This is required to support simple metrics collection via dumpsys
     */
    @Test
    public void testWifiMetricsDump() {
        mWifiServiceImpl.dump(new FileDescriptor(), new PrintWriter(new StringWriter()),
                new String[]{mWifiMetrics.PROTO_DUMP_ARG});
        verify(mWifiMetrics)
                .dump(any(FileDescriptor.class), any(PrintWriter.class), any(String[].class));
        verify(mWifiStateMachine, never())
                .dump(any(FileDescriptor.class), any(PrintWriter.class), any(String[].class));
    }


    /**
     * Ensure WifiServiceImpl.dump() doesn't throw an NPE when executed with null args
     */
    @Test
    public void testDumpNullArgs() {
        mWifiServiceImpl.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
    }

    /**
     * Verify that wifi can be enabled by a caller with WIFI_STATE_CHANGE permission when wifi is
     * off (no hotspot, no airplane mode).
     */
    @Test
    public void testSetWifiEnabledSuccess() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mWifiController).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that the CMD_TOGGLE_WIFI message won't be sent if wifi is already on.
     */
    @Test
    public void testSetWifiEnabledNoToggle() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mWifiController, never()).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify a SecurityException is thrown if a caller does not have the correct permission to
     * toggle wifi.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiEnableWithoutPermission() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true);
        verify(mWifiStateMachine, never()).syncGetWifiApState();
    }

    /**
     * Verify that a call from an app with the NETWORK_SETTINGS permission can enable wifi if we
     * are in airplane mode.
     */
    @Test
    public void testSetWifiEnabledFromNetworkSettingsHolderWhenInAirplaneMode() throws Exception {
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                        .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(mWifiServiceImpl.setWifiEnabled(SYSUI_PACKAGE_NAME, true));
        verify(mWifiController).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that a caller without the NETWORK_SETTINGS permission can't enable wifi
     * if we are in airplane mode.
     */
    @Test
    public void testSetWifiEnabledFromAppFailsWhenInAirplaneMode() throws Exception {
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                        .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mWifiController, never()).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that a call from an app with the NETWORK_SETTINGS permission can enable wifi if we
     * are in softap mode.
     */
    @Test
    public void testSetWifiEnabledFromNetworkSettingsHolderWhenApEnabled() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        when(mSettingsStore.handleWifiToggled(eq(true))).thenReturn(true);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                        .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(SYSUI_PACKAGE_NAME, true));
        verify(mWifiController).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that a call from an app cannot enable wifi if we are in softap mode.
     */
    @Test
    public void testSetWifiEnabledFromAppFailsWhenApEnabled() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);
        when(mContext.checkPermission(
                eq(android.Manifest.permission.NETWORK_SETTINGS), anyInt(), anyInt()))
                        .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mSettingsStore.isAirplaneModeOn()).thenReturn(false);
        assertFalse(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, true));
        verify(mSettingsStore, never()).handleWifiToggled(anyBoolean());
        verify(mWifiController, never()).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that wifi can be disabled by a caller with WIFI_STATE_CHANGE permission when wifi is
     * on.
     */
    @Test
    public void testSetWifiDisabledSuccess() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(true);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mWifiController).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify that CMD_TOGGLE_WIFI message won't be sent if wifi is already off.
     */
    @Test
    public void testSetWifiDisabledNoToggle() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        when(mSettingsStore.handleWifiToggled(eq(false))).thenReturn(false);
        assertTrue(mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false));
        verify(mWifiController, never()).sendMessage(eq(CMD_WIFI_TOGGLED));
    }

    /**
     * Verify a SecurityException is thrown if a caller does not have the correct permission to
     * toggle wifi.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiDisabledWithoutPermission() throws Exception {
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        mWifiServiceImpl.setWifiEnabled(TEST_PACKAGE_NAME, false);
    }

    /**
     * Ensure unpermitted callers cannot write the SoftApConfiguration.
     *
     * @throws SecurityException
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiApConfigurationNotSavedWithoutPermission() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        WifiConfiguration apConfig = new WifiConfiguration();
        mWifiServiceImpl.setWifiApConfiguration(apConfig);
        verify(mWifiStateMachine, never()).setWifiApConfiguration(eq(apConfig));
    }

    /**
     * Ensure softap config is written when the caller has the correct permission.
     */
    @Test
    public void testSetWifiApConfigurationSuccess() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        WifiConfiguration apConfig = new WifiConfiguration();
        mWifiServiceImpl.setWifiApConfiguration(apConfig);
        verify(mWifiStateMachine).setWifiApConfiguration(eq(apConfig));
    }

    /**
     * Ensure that a null config does not overwrite the saved ap config.
     */
    @Test
    public void testSetWifiApConfigurationNullConfigNotSaved() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        mWifiServiceImpl.setWifiApConfiguration(null);
        verify(mWifiStateMachine, never()).setWifiApConfiguration(isNull(WifiConfiguration.class));
    }

    /**
     * Ensure unpermitted callers are not able to retrieve the softap config.
     *
     * @throws SecurityException
     */
    @Test(expected = SecurityException.class)
    public void testGetWifiApConfigurationNotReturnedWithoutPermission() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(false);
        mWifiServiceImpl.getWifiApConfiguration();
        verify(mWifiStateMachine, never()).syncGetWifiApConfiguration();
    }

    /**
     * Ensure permitted callers are able to retrieve the softap config.
     */
    @Test
    public void testGetWifiApConfigurationSuccess() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        WifiConfiguration apConfig = new WifiConfiguration();
        when(mWifiStateMachine.syncGetWifiApConfiguration()).thenReturn(apConfig);
        assertEquals(apConfig, mWifiServiceImpl.getWifiApConfiguration());
    }

    /**
     * Make sure we do not start wifi if System services have to be restarted to decrypt the device.
     */
    @Test
    public void testWifiControllerDoesNotStartWhenDeviceTriggerResetMainAtBoot() {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        verify(mWifiController, never()).start();
    }

    /**
     * Make sure we do start WifiController (wifi disabled) if the device is already decrypted.
     */
    @Test
    public void testWifiControllerStartsWhenDeviceIsDecryptedAtBootWithWifiDisabled() {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        verify(mWifiController).start();
        verify(mWifiController, never()).sendMessage(CMD_WIFI_TOGGLED);
    }

    /**
     * Make sure we do start WifiController (wifi enabled) if the device is already decrypted.
     */
    @Test
    public void testWifiFullyStartsWhenDeviceIsDecryptedAtBootWithWifiEnabled() {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.handleWifiToggled(true)).thenReturn(true);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(true);
        when(mWifiStateMachine.syncGetWifiState()).thenReturn(WIFI_STATE_DISABLED);
        when(mWifiStateMachine.syncGetWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        when(mContext.getPackageName()).thenReturn(ANDROID_SYSTEM_PACKAGE);
        mWifiServiceImpl.checkAndStartWifi();
        verify(mWifiController).start();
        verify(mWifiController).sendMessage(CMD_WIFI_TOGGLED);
    }

    /**
     * Verify setWifiApEnabled works with the correct permissions and a null config.
     */
    @Test
    public void testSetWifiApEnabledWithProperPermissionsWithNullConfig() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_CONFIG_TETHERING)))
                .thenReturn(false);
        mWifiServiceImpl.setWifiApEnabled(null, true);
        verify(mWifiController)
                .sendMessage(eq(CMD_SET_AP), eq(1), eq(0), mSoftApModeConfigCaptor.capture());
        assertNull(mSoftApModeConfigCaptor.getValue().getWifiConfiguration());
    }

    /**
     * Verify setWifiApEnabled works with correct permissions and a valid config.
     *
     * TODO: should really validate that ap configs have a set of basic config settings b/37280779
     */
    @Test
    public void testSetWifiApEnabledWithProperPermissionsWithValidConfig() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_CONFIG_TETHERING)))
                .thenReturn(false);
        WifiConfiguration apConfig = new WifiConfiguration();
        mWifiServiceImpl.setWifiApEnabled(apConfig, true);
        verify(mWifiController).sendMessage(
                eq(CMD_SET_AP), eq(1), eq(0), mSoftApModeConfigCaptor.capture());
        assertEquals(apConfig, mSoftApModeConfigCaptor.getValue().getWifiConfiguration());
    }

    /**
     * Verify setWifiApEnabled when disabling softap with correct permissions sends the correct
     * message to WifiController.
     */
    @Test
    public void testSetWifiApEnabledFalseWithProperPermissionsWithNullConfig() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_CONFIG_TETHERING)))
                .thenReturn(false);
        mWifiServiceImpl.setWifiApEnabled(null, false);
        verify(mWifiController)
                .sendMessage(eq(CMD_SET_AP), eq(0), eq(0), mSoftApModeConfigCaptor.capture());
        assertNull(mSoftApModeConfigCaptor.getValue().getWifiConfiguration());
    }

    /**
     * setWifiApEnabled should fail if the provided config is not valid.
     */
    @Test
    public void testSetWifiApEnabledWithProperPermissionInvalidConfigFails() {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_CONFIG_TETHERING)))
                .thenReturn(false);
        // mApConfig is a mock and the values are not set - triggering the invalid config.  Testing
        // will be improved when we actually do test softap configs in b/37280779
        mWifiServiceImpl.setWifiApEnabled(mApConfig, true);
        verify(mWifiController, never())
                .sendMessage(eq(CMD_SET_AP), eq(1), eq(0), any(SoftApModeConfiguration.class));
    }

    /**
     * setWifiApEnabled should throw a security exception when the caller does not have the correct
     * permissions.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiApEnabledThrowsSecurityExceptionWithoutConfigOverridePermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                        eq("WifiService"));
        mWifiServiceImpl.setWifiApEnabled(null, true);
    }

    /**
     * setWifiApEnabled should throw a SecurityException when disallow tethering is set for the
     * user.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiApEnabledThrowsSecurityExceptionWithDisallowTethering()
            throws Exception {
        when(mWifiPermissionsUtil.checkConfigOverridePermission(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_CONFIG_TETHERING)))
                .thenReturn(true);
        mWifiServiceImpl.setWifiApEnabled(null, true);

    }

    /**
     * Verify caller with proper permission can call startSoftAp.
     */
    @Test
    public void testStartSoftApWithPermissionsAndNullConfig() {
        boolean result = mWifiServiceImpl.startSoftAp(null);
        assertTrue(result);
        verify(mWifiController)
                .sendMessage(eq(CMD_SET_AP), eq(1), eq(0), mSoftApModeConfigCaptor.capture());
        assertNull(mSoftApModeConfigCaptor.getValue().getWifiConfiguration());
    }

    /**
     * Verify caller with proper permissions but an invalid config does not start softap.
     */
    @Test
    public void testStartSoftApWithPermissionsAndInvalidConfig() {
        boolean result = mWifiServiceImpl.startSoftAp(mApConfig);
        assertFalse(result);
        verifyZeroInteractions(mWifiController);
    }

    /**
     * Verify caller with proper permission and valid config does start softap.
     */
    @Test
    public void testStartSoftApWithPermissionsAndValidConfig() {
        WifiConfiguration config = new WifiConfiguration();
        boolean result = mWifiServiceImpl.startSoftAp(config);
        assertTrue(result);
        verify(mWifiController)
                .sendMessage(eq(CMD_SET_AP), eq(1), eq(0), mSoftApModeConfigCaptor.capture());
        assertEquals(config, mSoftApModeConfigCaptor.getValue().getWifiConfiguration());
    }

    /**
     * Verify a SecurityException is thrown when a caller without the correct permission attempts to
     * start softap.
     */
    @Test(expected = SecurityException.class)
    public void testStartSoftApWithoutPermissionThrowsException() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_STACK),
                                                eq("WifiService"));
        mWifiServiceImpl.startSoftAp(null);
    }

    /**
     * Verify caller with proper permission can call stopSoftAp.
     */
    @Test
    public void testStopSoftApWithPermissions() {
        boolean result = mWifiServiceImpl.stopSoftAp();
        assertTrue(result);
        verify(mWifiController).sendMessage(eq(CMD_SET_AP), eq(0), eq(0));
    }

    /**
     * Verify SecurityException is thrown when a caller without the correct permission attempts to
     * stop softap.
     */
    @Test(expected = SecurityException.class)
    public void testStopSoftApWithoutPermissionThrowsException() throws Exception {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_STACK),
                                                eq("WifiService"));
        mWifiServiceImpl.stopSoftAp();
    }

    /**
     * Ensure foreground apps can always do wifi scans.
     */
    @Test
    public void testWifiScanStartedForeground() {
        when(mActivityManager.getPackageImportance(SCAN_PACKAGE_NAME)).thenReturn(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE);
        mWifiServiceImpl.startScan(null, null, SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine).startScan(
                anyInt(), anyInt(), (ScanSettings) eq(null), any(WorkSource.class));
    }

    /**
     * Ensure background apps get throttled when the previous scan is too close.
     */
    @Test
    public void testWifiScanBackgroundThrottled() {
        when(mActivityManager.getPackageImportance(SCAN_PACKAGE_NAME)).thenReturn(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        long startMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(startMs);
        mWifiServiceImpl.startScan(null, null, SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine).startScan(
                anyInt(), anyInt(), (ScanSettings) eq(null), any(WorkSource.class));

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                startMs + WIFI_BACKGROUND_SCAN_INTERVAL - 1000);
        mWifiServiceImpl.startScan(null, null, SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine, times(1)).startScan(
                anyInt(), anyInt(), (ScanSettings) eq(null), any(WorkSource.class));
    }

    /**
     * Ensure background apps can do wifi scan when the throttle interval reached.
     */
    @Test
    public void testWifiScanBackgroundNotThrottled() {
        when(mActivityManager.getPackageImportance(SCAN_PACKAGE_NAME)).thenReturn(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        long startMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(startMs);
        mWifiServiceImpl.startScan(null, null, SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine).startScan(
                anyInt(), eq(0), (ScanSettings) eq(null), any(WorkSource.class));

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                startMs + WIFI_BACKGROUND_SCAN_INTERVAL + 1000);
        mWifiServiceImpl.startScan(null, null, SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine).startScan(
                anyInt(), eq(1), (ScanSettings) eq(null), any(WorkSource.class));
    }

    /**
     * Ensure background apps can do wifi scan when the throttle interval reached.
     */
    @Test
    public void testWifiScanBackgroundWhiteListed() {
        when(mActivityManager.getPackageImportance(WHITE_LIST_SCAN_PACKAGE_NAME)).thenReturn(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        long startMs = 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(startMs);
        mWifiServiceImpl.startScan(null, null, WHITE_LIST_SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine).startScan(
                anyInt(), anyInt(), (ScanSettings) eq(null), any(WorkSource.class));

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                startMs + WIFI_BACKGROUND_SCAN_INTERVAL - 1000);
        mWifiServiceImpl.startScan(null, null, WHITE_LIST_SCAN_PACKAGE_NAME);
        verify(mWifiStateMachine, times(2)).startScan(
                anyInt(), anyInt(), (ScanSettings) eq(null), any(WorkSource.class));
    }

    private void registerLOHSRequestFull() {
        // allow test to proceed without a permission check failure
        when(mSettingsStore.getLocationModeSetting(mContext))
                .thenReturn(LOCATION_MODE_HIGH_ACCURACY);
        try {
            when(mFrameworkFacade.isAppForeground(anyInt())).thenReturn(true);
        } catch (RemoteException e) { }
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING))
                .thenReturn(false);
        int result = mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder,
                TEST_PACKAGE_NAME);
        assertEquals(LocalOnlyHotspotCallback.REQUEST_REGISTERED, result);
    }

    /**
     * Verify that the call to startLocalOnlyHotspot returns REQUEST_REGISTERED when successfully
     * called.
     */
    @Test
    public void testStartLocalOnlyHotspotSingleRegistrationReturnsRequestRegistered() {
        registerLOHSRequestFull();
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if the caller does not
     * have the CHANGE_WIFI_STATE permission.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutCorrectPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if the caller does not
     * have Location permission.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutLocationPermission() {
        when(mContext.getOpPackageName()).thenReturn(TEST_PACKAGE_NAME);
        doThrow(new SecurityException())
                .when(mWifiPermissionsUtil).enforceLocationPermission(eq(TEST_PACKAGE_NAME),
                                                                      anyInt());
        mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
    }

    /**
     * Verify that a call to startLocalOnlyHotspot throws a SecurityException if Location mode is
     * disabled.
     */
    @Test(expected = SecurityException.class)
    public void testStartLocalOnlyHotspotThrowsSecurityExceptionWithoutLocationEnabled() {
        when(mSettingsStore.getLocationModeSetting(mContext)).thenReturn(LOCATION_MODE_OFF);
        mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
    }

    /**
     * Only start LocalOnlyHotspot if the caller is the foreground app at the time of the request.
     */
    @Test
    public void testStartLocalOnlyHotspotFailsIfRequestorNotForegroundApp() throws Exception {
        when(mSettingsStore.getLocationModeSetting(mContext))
                .thenReturn(LOCATION_MODE_HIGH_ACCURACY);

        when(mFrameworkFacade.isAppForeground(anyInt())).thenReturn(false);
        int result = mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder,
                TEST_PACKAGE_NAME);
        assertEquals(LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE, result);
    }

    /**
     * Do not register the LocalOnlyHotspot request if the caller app cannot be verified as the
     * foreground app at the time of the request (ie, throws an exception in the check).
     */
    @Test
    public void testStartLocalOnlyHotspotFailsIfForegroundAppCheckThrowsRemoteException()
            throws Exception {
        when(mSettingsStore.getLocationModeSetting(mContext))
                .thenReturn(LOCATION_MODE_HIGH_ACCURACY);

        when(mFrameworkFacade.isAppForeground(anyInt())).thenThrow(new RemoteException());
        int result = mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder,
                TEST_PACKAGE_NAME);
        assertEquals(LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE, result);
    }

    /**
     * Only start LocalOnlyHotspot if we are not tethering.
     */
    @Test
    public void testHotspotDoesNotStartWhenAlreadyTethering() throws Exception {
        when(mSettingsStore.getLocationModeSetting(mContext))
                            .thenReturn(LOCATION_MODE_HIGH_ACCURACY);
        when(mFrameworkFacade.isAppForeground(anyInt())).thenReturn(true);
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        int returnCode = mWifiServiceImpl.startLocalOnlyHotspot(
                mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
        assertEquals(ERROR_INCOMPATIBLE_MODE, returnCode);
    }

    /**
     * Only start LocalOnlyHotspot if admin setting does not disallow tethering.
     */
    @Test
    public void testHotspotDoesNotStartWhenTetheringDisallowed() throws Exception {
        when(mSettingsStore.getLocationModeSetting(mContext))
                .thenReturn(LOCATION_MODE_HIGH_ACCURACY);
        when(mFrameworkFacade.isAppForeground(anyInt())).thenReturn(true);
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING))
                .thenReturn(true);
        int returnCode = mWifiServiceImpl.startLocalOnlyHotspot(
                mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
        assertEquals(ERROR_TETHERING_DISALLOWED, returnCode);
    }

    /**
     * Verify that callers can only have one registered LOHS request.
     */
    @Test(expected = IllegalStateException.class)
    public void testStartLocalOnlyHotspotThrowsExceptionWhenCallerAlreadyRegistered() {
        registerLOHSRequestFull();

        // now do the second request that will fail
        mWifiServiceImpl.startLocalOnlyHotspot(mAppMessenger, mAppBinder, TEST_PACKAGE_NAME);
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot does not do anything when there aren't any
     * registered callers.
     */
    @Test
    public void testStopLocalOnlyHotspotDoesNothingWithoutRegisteredRequests() {
        // allow test to proceed without a permission check failure
        mWifiServiceImpl.stopLocalOnlyHotspot();
        // there is nothing registered, so this shouldn't do anything
        verify(mWifiController, never()).sendMessage(eq(CMD_SET_AP), anyInt(), anyInt());
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot does not do anything when one caller unregisters
     * but there is still an active request
     */
    @Test
    public void testStopLocalOnlyHotspotDoesNothingWithARemainingRegisteredRequest() {
        // register a request that will remain after the stopLOHS call
        mWifiServiceImpl.registerLOHSForTest(mPid, mRequestInfo);

        registerLOHSRequestFull();

        // Since we are calling with the same pid, the second register call will be removed
        mWifiServiceImpl.stopLocalOnlyHotspot();
        // there is still a valid registered request - do not tear down LOHS
        verify(mWifiController, never()).sendMessage(eq(CMD_SET_AP), anyInt(), anyInt());
    }

    /**
     * Verify that the call to stopLocalOnlyHotspot sends a message to WifiController to stop
     * the softAp when there is one registered caller when that caller is removed.
     */
    @Test
    public void testStopLocalOnlyHotspotTriggersSoftApStopWithOneRegisteredRequest() {
        registerLOHSRequestFull();
        verify(mWifiController)
                .sendMessage(eq(CMD_SET_AP), eq(1), eq(0), any(SoftApModeConfiguration.class));

        mWifiServiceImpl.stopLocalOnlyHotspot();
        // there is was only one request registered, we should tear down softap
        verify(mWifiController).sendMessage(eq(CMD_SET_AP), eq(0), eq(0));
    }

    /**
     * Verify that a call to stopLocalOnlyHotspot throws a SecurityException if the caller does not
     * have the CHANGE_WIFI_STATE permission.
     */
    @Test(expected = SecurityException.class)
    public void testStopLocalOnlyHotspotThrowsSecurityExceptionWithoutCorrectPermission() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.CHANGE_WIFI_STATE),
                                                eq("WifiService"));
        mWifiServiceImpl.stopLocalOnlyHotspot();
    }

    /**
     * Verify that WifiServiceImpl does not send the stop ap message if there were no
     * pending LOHS requests upon a binder death callback.
     */
    @Test
    public void testServiceImplNotCalledWhenBinderDeathTriggeredNoRequests() {
        LocalOnlyRequestorCallback binderDeathCallback =
                mWifiServiceImpl.new LocalOnlyRequestorCallback();

        binderDeathCallback.onLocalOnlyHotspotRequestorDeath(mRequestInfo);
        verify(mWifiController, never()).sendMessage(eq(CMD_SET_AP), eq(0), eq(0));
    }

    /**
     * Verify that WifiServiceImpl does not send the stop ap message if there are remaining
     * registered LOHS requests upon a binder death callback.  Additionally verify that softap mode
     * will be stopped if that remaining request is removed (to verify the binder death properly
     * cleared the requestor that died).
     */
    @Test
    public void testServiceImplNotCalledWhenBinderDeathTriggeredWithRegisteredRequests() {
        LocalOnlyRequestorCallback binderDeathCallback =
                mWifiServiceImpl.new LocalOnlyRequestorCallback();

        // registering a request directly from the test will not trigger a message to start
        // softap mode
        mWifiServiceImpl.registerLOHSForTest(mPid, mRequestInfo);

        registerLOHSRequestFull();

        binderDeathCallback.onLocalOnlyHotspotRequestorDeath(mRequestInfo);
        verify(mWifiController, never()).sendMessage(eq(CMD_SET_AP), anyInt(), anyInt());

        reset(mWifiController);

        // now stop as the second request and confirm CMD_SET_AP will be sent to make sure binder
        // death requestor was removed
        mWifiServiceImpl.stopLocalOnlyHotspot();
        verify(mWifiController).sendMessage(eq(CMD_SET_AP), eq(0), eq(0));
    }

    private class IntentFilterMatcher implements ArgumentMatcher<IntentFilter> {
        @Override
        public boolean matches(IntentFilter filter) {
            return filter.hasAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        }
    }

    /**
     * Verify that onFailed is called for registered LOHS callers when a WIFI_AP_STATE_CHANGE
     * broadcast is received.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApFailureGeneric() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_DISABLED, SAP_START_FAILURE_GENERAL,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_GENERIC, message.arg1);
    }

    /**
     * Verify that onFailed is called for registered LOHS callers when a WIFI_AP_STATE_CHANGE
     * broadcast is received with the SAP_START_FAILURE_NO_CHANNEL error.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApFailureNoChannel() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_DISABLED, SAP_START_FAILURE_NO_CHANNEL,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_NO_CHANNEL, message.arg1);
    }

    /**
     * Verify that onStopped is called for registered LOHS callers when a WIFI_AP_STATE_CHANGE
     * broadcast is received with WIFI_AP_STATE_DISABLING and LOHS was active.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApDisabling() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        reset(mHandler);

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STOPPED, message.what);
    }


    /**
     * Verify that onStopped is called for registered LOHS callers when a WIFI_AP_STATE_CHANGE
     * broadcast is received with WIFI_AP_STATE_DISABLED and LOHS was enabled.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnSoftApDisabled() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        reset(mHandler);

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STOPPED, message.what);
    }

    /**
     * Verify that no callbacks are called for registered LOHS callers when a WIFI_AP_STATE_CHANGE
     * broadcast is received and the softap started.
     */
    @Test
    public void testRegisteredCallbacksNotTriggeredOnSoftApStart() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR, WIFI_IFACE_NAME,
                IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verifyNoMoreInteractions(mHandler);
    }

    /**
     * Verify that onStopped is called only once for registered LOHS callers when
     * WIFI_AP_STATE_CHANGE broadcasts are received with WIFI_AP_STATE_DISABLING and
     * WIFI_AP_STATE_DISABLED when LOHS was enabled.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnlyOnceWhenSoftApDisabling() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        reset(mHandler);

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STOPPED, message.what);
    }

    /**
     * Verify that onFailed is called only once for registered LOHS callers when
     * WIFI_AP_STATE_CHANGE broadcasts are received with WIFI_AP_STATE_FAILED twice.
     */
    @Test
    public void testRegisteredCallbacksTriggeredOnlyOnceWhenSoftApFailsTwice() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        registerLOHSRequestFull();

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_GENERIC, message.arg1);
    }

    /**
     * Verify that onFailed is called for all registered LOHS callers when
     * WIFI_AP_STATE_CHANGE broadcasts are received with WIFI_AP_STATE_FAILED.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApFails() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        // make an additional request for this test
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        registerLOHSRequestFull();

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_FAILED, WIFI_AP_STATE_FAILED, ERROR_GENERIC,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        verify(mRequestInfo).sendHotspotFailedMessage(ERROR_GENERIC);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_GENERIC, message.arg1);
    }

    /**
     * Verify that onStopped is called for all registered LOHS callers when
     * WIFI_AP_STATE_CHANGE broadcasts are received with WIFI_AP_STATE_DISABLED when LOHS was
     * active.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApStops() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mRequestInfo).sendHotspotStartedMessage(any());
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        reset(mHandler);

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        verify(mRequestInfo).sendHotspotStoppedMessage();
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STOPPED, message.what);
    }

    /**
     * Verify that onFailed is called for all registered LOHS callers when
     * WIFI_AP_STATE_CHANGE broadcasts are received with WIFI_AP_STATE_DISABLED when LOHS was
     * not active.
     */
    @Test
    public void testAllRegisteredCallbacksTriggeredWhenSoftApStopsLOHSNotActive() throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();

        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);
        mWifiServiceImpl.registerLOHSForTest(TEST_PID2, mRequestInfo2);

        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);

        verify(mRequestInfo).sendHotspotFailedMessage(ERROR_GENERIC);
        verify(mRequestInfo2).sendHotspotFailedMessage(ERROR_GENERIC);
    }

    /**
     * Verify that if we do not have registered LOHS requestors and we receive an update that LOHS
     * is up and ready for use, we tell WifiController to tear it down.  This can happen if softap
     * mode fails to come up properly and we get an onFailed message for a tethering call and we
     * had registered callers for LOHS.
     */
    @Test
    public void testLOHSReadyWithoutRegisteredRequestsStopsSoftApMode() {
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();

        verify(mWifiController).sendMessage(eq(CMD_SET_AP), eq(0), eq(0));
    }

    /**
     * Verify that all registered LOHS requestors are notified via a HOTSPOT_STARTED message that
     * the hotspot is up and ready to use.
     */
    @Test
    public void testRegisteredLocalOnlyHotspotRequestorsGetOnStartedCallbackWhenReady()
            throws Exception {
        registerLOHSRequestFull();

        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mRequestInfo).sendHotspotStartedMessage(any(WifiConfiguration.class));

        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        assertNotNull((WifiConfiguration) message.obj);
    }

    /**
     * Verify that if a LOHS is already active, a new call to register a request will trigger the
     * onStarted callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestAfterAlreadyStartedGetsOnStartedCallback()
            throws Exception {
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();

        registerLOHSRequestFull();

        mLooper.dispatchAll();

        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_STARTED, message.what);
        // since the first request was registered out of band, the config will be null
        assertNull((WifiConfiguration) message.obj);
    }

    /**
     * Verify that if a LOHS request is active and we receive an update with an ip mode
     * configuration error, callers are notified via the onFailed callback with the generic
     * error and are unregistered.
     */
    @Test
    public void testCallOnFailedLocalOnlyHotspotRequestWhenIpConfigFails() throws Exception {
        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_CONFIGURATION_ERROR);
        mLooper.dispatchAll();

        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_GENERIC, message.arg1);

        // sendMessage should only happen once since the requestor should be unregistered
        reset(mHandler);

        // send HOTSPOT_FAILED message should only happen once since the requestor should be
        // unregistered
        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_CONFIGURATION_ERROR);
        mLooper.dispatchAll();
        verify(mHandler, never()).handleMessage(any(Message.class));
    }

    /**
     * Verify that if a LOHS request is active and tethering starts, callers are notified on the
     * incompatible mode and are unregistered.
     */
    @Test
    public void testCallOnFailedLocalOnlyHotspotRequestWhenTetheringStarts() throws Exception {
        registerLOHSRequestFull();

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();

        verify(mHandler).handleMessage(mMessageCaptor.capture());
        Message message = mMessageCaptor.getValue();
        assertEquals(HOTSPOT_FAILED, message.what);
        assertEquals(ERROR_INCOMPATIBLE_MODE, message.arg1);

        // sendMessage should only happen once since the requestor should be unregistered
        reset(mHandler);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_TETHERED);
        mLooper.dispatchAll();
        verify(mHandler, never()).handleMessage(any(Message.class));
    }

    /**
     * Verify that if LOHS is disabled, a new call to register a request will not trigger the
     * onStopped callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestWhenStoppedDoesNotGetOnStoppedCallback()
            throws Exception {
        registerLOHSRequestFull();
        mLooper.dispatchAll();

        verify(mHandler, never()).handleMessage(any(Message.class));
    }

    /**
     * Verify that if a LOHS was active and then stopped, a new call to register a request will
     * not trigger the onStarted callback.
     */
    @Test
    public void testRegisterLocalOnlyHotspotRequestAfterStoppedNoOnStartedCallback()
            throws Exception {
        when(mFrameworkFacade.inStorageManagerCryptKeeperBounce()).thenReturn(false);
        when(mSettingsStore.isWifiToggleEnabled()).thenReturn(false);
        mWifiServiceImpl.checkAndStartWifi();
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                (IntentFilter) argThat(new IntentFilterMatcher()));

        // register a request so we don't drop the LOHS interface ip update
        mWifiServiceImpl.registerLOHSForTest(TEST_PID, mRequestInfo);

        mWifiServiceImpl.updateInterfaceIpState(WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();

        registerLOHSRequestFull();
        mLooper.dispatchAll();

        verify(mHandler).handleMessage(mMessageCaptor.capture());
        assertEquals(HOTSPOT_STARTED, mMessageCaptor.getValue().what);

        reset(mHandler);

        // now stop the hotspot
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        TestUtil.sendWifiApStateChanged(mBroadcastReceiverCaptor.getValue(), mContext,
                WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR,
                WIFI_IFACE_NAME, IFACE_IP_MODE_LOCAL_ONLY);
        mLooper.dispatchAll();
        verify(mHandler).handleMessage(mMessageCaptor.capture());
        assertEquals(HOTSPOT_STOPPED, mMessageCaptor.getValue().what);

        reset(mHandler);

        // now register a new caller - they should not get the onStarted callback
        Messenger messenger2 = new Messenger(mHandler);
        IBinder binder2 = mock(IBinder.class);

        int result = mWifiServiceImpl.startLocalOnlyHotspot(messenger2, binder2, TEST_PACKAGE_NAME);
        assertEquals(LocalOnlyHotspotCallback.REQUEST_REGISTERED, result);
        mLooper.dispatchAll();

        verify(mHandler, never()).handleMessage(any(Message.class));
    }

    /**
     * Verify that a call to startWatchLocalOnlyHotspot is only allowed from callers with the
     * signature only NETWORK_SETTINGS permission.
     *
     * This test is expecting the permission check to enforce the permission and throw a
     * SecurityException for callers without the permission.  This exception should be bubbled up to
     * the caller of startLocalOnlyHotspot.
     */
    @Test(expected = SecurityException.class)
    public void testStartWatchLocalOnlyHotspotNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                                                eq("WifiService"));
        mWifiServiceImpl.startWatchLocalOnlyHotspot(mAppMessenger, mAppBinder);
    }

    /**
     * Verify that the call to startWatchLocalOnlyHotspot throws the UnsupportedOperationException
     * when called until the implementation is complete.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testStartWatchLocalOnlyHotspotNotSupported() {
        mWifiServiceImpl.startWatchLocalOnlyHotspot(mAppMessenger, mAppBinder);
    }

    /**
     * Verify that a call to stopWatchLocalOnlyHotspot is only allowed from callers with the
     * signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testStopWatchLocalOnlyHotspotNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                                                eq("WifiService"));
        mWifiServiceImpl.stopWatchLocalOnlyHotspot();
    }

    /**
     * Verify that the call to stopWatchLocalOnlyHotspot throws the UnsupportedOperationException
     * until the implementation is complete.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testStopWatchLocalOnlyHotspotNotSupported() {
        mWifiServiceImpl.stopWatchLocalOnlyHotspot();
    }

    /**
     * Verify that the call to addOrUpdateNetwork for installing Passpoint profile is redirected
     * to the Passpoint specific API addOrUpdatePasspointConfiguration.
     */
    @Test
    public void testAddPasspointProfileViaAddNetwork() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createPasspointNetwork();
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);

        PackageManager pm = mock(PackageManager.class);
        when(pm.hasSystemFeature(PackageManager.FEATURE_WIFI_PASSPOINT)).thenReturn(true);
        when(mContext.getPackageManager()).thenReturn(pm);

        when(mWifiStateMachine.syncAddOrUpdatePasspointConfig(any(),
                any(PasspointConfiguration.class), anyInt())).thenReturn(true);
        assertEquals(0, mWifiServiceImpl.addOrUpdateNetwork(config));
        verify(mWifiStateMachine).syncAddOrUpdatePasspointConfig(any(),
                any(PasspointConfiguration.class), anyInt());
        reset(mWifiStateMachine);

        when(mWifiStateMachine.syncAddOrUpdatePasspointConfig(any(),
                any(PasspointConfiguration.class), anyInt())).thenReturn(false);
        assertEquals(-1, mWifiServiceImpl.addOrUpdateNetwork(config));
        verify(mWifiStateMachine).syncAddOrUpdatePasspointConfig(any(),
                any(PasspointConfiguration.class), anyInt());
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreBackupData(byte[])} is only allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRestoreBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreBackupData(null);
        verify(mWifiBackupRestore, never()).retrieveConfigurationsFromBackupData(any(byte[].class));
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#restoreSupplicantBackupData(byte[], byte[])} is
     * only allowed from callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRestoreSupplicantBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.restoreSupplicantBackupData(null, null);
        verify(mWifiBackupRestore, never()).retrieveConfigurationsFromSupplicantBackupData(
                any(byte[].class), any(byte[].class));
    }

    /**
     * Verify that a call to {@link WifiServiceImpl#retrieveBackupData()} is only allowed from
     * callers with the signature only NETWORK_SETTINGS permission.
     */
    @Test(expected = SecurityException.class)
    public void testRetrieveBackupDataNotApprovedCaller() {
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(android.Manifest.permission.NETWORK_SETTINGS),
                        eq("WifiService"));
        mWifiServiceImpl.retrieveBackupData();
        verify(mWifiBackupRestore, never()).retrieveBackupDataFromConfigurations(any(List.class));
    }

    /**
     * Helper to test handling of async messages by wifi service when the message comes from an
     * app without {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission.
     */
    private void verifyAsyncChannelMessageHandlingWithoutChangePermisson(
            int requestMsgWhat, int expectedReplyMsgwhat) {
        WifiAsyncChannelTester tester = verifyAsyncChannelHalfConnected();

        int uidWithoutPermission = 5;
        when(mWifiPermissionsUtil.checkChangePermission(eq(uidWithoutPermission)))
                .thenReturn(false);

        Message request = Message.obtain();
        request.what = requestMsgWhat;
        request.sendingUid = uidWithoutPermission;

        mLooper.startAutoDispatch();
        Message reply = tester.sendMessageSynchronously(request);
        mLooper.stopAutoDispatch();

        verify(mWifiStateMachine, never()).sendMessage(any(Message.class));
        assertEquals(expectedReplyMsgwhat, reply.what);
        assertEquals(WifiManager.NOT_AUTHORIZED, reply.arg1);
    }

    /**
     * Verify that the CONNECT_NETWORK message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testConnectNetworkWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.CONNECT_NETWORK, WifiManager.CONNECT_NETWORK_FAILED);
    }

    /**
     * Verify that the FORGET_NETWORK message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testForgetNetworkWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.SAVE_NETWORK, WifiManager.SAVE_NETWORK_FAILED);
    }

    /**
     * Verify that the START_WPS message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testStartWpsWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.START_WPS, WifiManager.WPS_FAILED);
    }

    /**
     * Verify that the CANCEL_WPS message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testCancelWpsWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.CANCEL_WPS, WifiManager.CANCEL_WPS_FAILED);
    }

    /**
     * Verify that the DISABLE_NETWORK message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testDisableNetworkWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.DISABLE_NETWORK, WifiManager.DISABLE_NETWORK_FAILED);
    }

    /**
     * Verify that the RSSI_PKTCNT_FETCH message received from an app without
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is rejected with the correct
     * error code.
     */
    @Test
    public void testRssiPktcntFetchWithoutChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithoutChangePermisson(
                WifiManager.RSSI_PKTCNT_FETCH, WifiManager.RSSI_PKTCNT_FETCH_FAILED);
    }

    /**
     * Helper to test handling of async messages by wifi service when the message comes from an
     * app with {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission.
     */
    private void verifyAsyncChannelMessageHandlingWithChangePermisson(
            int requestMsgWhat, Object requestMsgObj) {
        WifiAsyncChannelTester tester = verifyAsyncChannelHalfConnected();

        when(mWifiPermissionsUtil.checkChangePermission(anyInt())).thenReturn(true);

        Message request = Message.obtain();
        request.what = requestMsgWhat;
        request.obj = requestMsgObj;

        tester.sendMessage(request);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mWifiStateMachine).sendMessage(messageArgumentCaptor.capture());
        assertEquals(requestMsgWhat, messageArgumentCaptor.getValue().what);
    }

    /**
     * Verify that the CONNECT_NETWORK message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testConnectNetworkWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.CONNECT_NETWORK, new WifiConfiguration());
    }

    /**
     * Verify that the SAVE_NETWORK message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testSaveNetworkWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.SAVE_NETWORK, new WifiConfiguration());
    }

    /**
     * Verify that the START_WPS message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testStartWpsWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.START_WPS, new Object());
    }

    /**
     * Verify that the CANCEL_WPS message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testCancelWpsWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.CANCEL_WPS, new Object());
    }

    /**
     * Verify that the DISABLE_NETWORK message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testDisableNetworkWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.DISABLE_NETWORK, new Object());
    }

    /**
     * Verify that the RSSI_PKTCNT_FETCH message received from an app with
     * {@link android.Manifest.permission#CHANGE_WIFI_STATE} permission is forwarded to
     * WifiStateMachine.
     */
    @Test
    public void testRssiPktcntFetchWithChangePermission() throws Exception {
        verifyAsyncChannelMessageHandlingWithChangePermisson(
                WifiManager.RSSI_PKTCNT_FETCH, new Object());
    }
}
