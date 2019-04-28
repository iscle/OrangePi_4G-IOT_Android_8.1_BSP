/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.wifi.WifiManager.EXTRA_PREVIOUS_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_FAILURE_REASON;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;

import static com.android.server.wifi.LocalOnlyHotspotRequestInfo.HOTSPOT_NO_ERROR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import android.app.ActivityManager;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.dhcp.DhcpClient;
import android.net.ip.IpManager;
import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IWificond;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.WpsInfo;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.net.wifi.p2p.IWifiP2pManager;
import android.os.BatteryStats;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.INetworkManagementService;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.security.KeyStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.app.IBatteryStats;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;
import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Unit tests for {@link com.android.server.wifi.WifiStateMachine}.
 */
@SmallTest
public class WifiStateMachineTest {
    public static final String TAG = "WifiStateMachineTest";

    private static final int MANAGED_PROFILE_UID = 1100000;
    private static final int OTHER_USER_UID = 1200000;
    private static final int LOG_REC_LIMIT_IN_VERBOSE_MODE =
            (ActivityManager.isLowRamDeviceStatic()
                    ? WifiStateMachine.NUM_LOG_RECS_VERBOSE_LOW_MEMORY
                    : WifiStateMachine.NUM_LOG_RECS_VERBOSE);
    private static final int FRAMEWORK_NETWORK_ID = 0;
    private static final int TEST_RSSI = -54;
    private static final int TEST_NETWORK_ID = 54;
    private static final int TEST_VALID_NETWORK_SCORE = 54;
    private static final int TEST_OUTSCORED_NETWORK_SCORE = 100;
    private static final int WPS_SUPPLICANT_NETWORK_ID = 5;
    private static final int WPS_FRAMEWORK_NETWORK_ID = 10;
    private static final String DEFAULT_TEST_SSID = "\"GoogleGuest\"";
    private static final String OP_PACKAGE_NAME = "com.xxx";

    private long mBinderToken;

    private static <T> T mockWithInterfaces(Class<T> class1, Class<?>... interfaces) {
        return mock(class1, withSettings().extraInterfaces(interfaces));
    }

    private static <T, I> IBinder mockService(Class<T> class1, Class<I> iface) {
        T tImpl = mockWithInterfaces(class1, iface);
        IBinder binder = mock(IBinder.class);
        when(((IInterface) tImpl).asBinder()).thenReturn(binder);
        when(binder.queryLocalInterface(iface.getCanonicalName()))
                .thenReturn((IInterface) tImpl);
        return binder;
    }

    private void enableDebugLogs() {
        mWsm.enableVerboseLogging(1);
    }

    private FrameworkFacade getFrameworkFacade() throws Exception {
        FrameworkFacade facade = mock(FrameworkFacade.class);

        when(facade.getService(Context.NETWORKMANAGEMENT_SERVICE)).thenReturn(
                mockWithInterfaces(IBinder.class, INetworkManagementService.class));

        IBinder p2pBinder = mockService(WifiP2pServiceImpl.class, IWifiP2pManager.class);
        when(facade.getService(Context.WIFI_P2P_SERVICE)).thenReturn(p2pBinder);

        WifiP2pServiceImpl p2pm = (WifiP2pServiceImpl) p2pBinder.queryLocalInterface(
                IWifiP2pManager.class.getCanonicalName());

        final CountDownLatch untilDone = new CountDownLatch(1);
        mP2pThread = new HandlerThread("WifiP2pMockThread") {
            @Override
            protected void onLooperPrepared() {
                untilDone.countDown();
            }
        };

        mP2pThread.start();
        untilDone.await();

        Handler handler = new Handler(mP2pThread.getLooper());
        when(p2pm.getP2pStateMachineMessenger()).thenReturn(new Messenger(handler));

        IBinder batteryStatsBinder = mockService(BatteryStats.class, IBatteryStats.class);
        when(facade.getService(BatteryStats.SERVICE_NAME)).thenReturn(batteryStatsBinder);

        when(facade.makeIpManager(any(Context.class), anyString(), any(IpManager.Callback.class)))
                .then(new AnswerWithArguments() {
                    public IpManager answer(
                            Context context, String ifname, IpManager.Callback callback) {
                        mIpManagerCallback = callback;
                        return mIpManager;
                    }
                });

        return facade;
    }

    private Context getContext() throws Exception {
        PackageManager pkgMgr = mock(PackageManager.class);
        when(pkgMgr.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)).thenReturn(true);

        Context context = mock(Context.class);
        when(context.getPackageManager()).thenReturn(pkgMgr);

        MockContentResolver mockContentResolver = new MockContentResolver();
        mockContentResolver.addProvider(Settings.AUTHORITY,
                new MockContentProvider(context) {
                    @Override
                    public Bundle call(String method, String arg, Bundle extras) {
                        return new Bundle();
                    }
                });
        when(context.getContentResolver()).thenReturn(mockContentResolver);

        when(context.getSystemService(Context.POWER_SERVICE)).thenReturn(
                new PowerManager(context, mock(IPowerManager.class), new Handler()));

        mAlarmManager = new TestAlarmManager();
        when(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(
                mAlarmManager.getAlarmManager());

        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(
                mConnectivityManager);

        when(context.getOpPackageName()).thenReturn(OP_PACKAGE_NAME);

        return context;
    }

    private MockResources getMockResources() {
        MockResources resources = new MockResources();
        resources.setBoolean(R.bool.config_wifi_enable_wifi_firmware_debugging, false);
        resources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, false);
        return resources;
    }

    private IState getCurrentState() throws
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
        method.setAccessible(true);
        return (IState) method.invoke(mWsm);
    }

    private static HandlerThread getWsmHandlerThread(WifiStateMachine wsm) throws
            NoSuchFieldException, InvocationTargetException, IllegalAccessException {
        Field field = StateMachine.class.getDeclaredField("mSmThread");
        field.setAccessible(true);
        return (HandlerThread) field.get(wsm);
    }

    private static void stopLooper(final Looper looper) throws Exception {
        new Handler(looper).post(new Runnable() {
            @Override
            public void run() {
                looper.quitSafely();
            }
        });
    }

    private void dumpState() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        mWsm.dump(null, writer, null);
        writer.flush();
        Log.d(TAG, "WifiStateMachine state -" + stream.toString());
    }

    private static ScanDetail getGoogleGuestScanDetail(int rssi, String bssid, int freq) {
        ScanResult.InformationElement ie[] = new ScanResult.InformationElement[1];
        ie[0] = ScanResults.generateSsidIe(sSSID);
        NetworkDetail nd = new NetworkDetail(sBSSID, ie, new ArrayList<String>(), sFreq);
        ScanDetail detail = new ScanDetail(nd, sWifiSsid, bssid, "", rssi, freq,
                Long.MAX_VALUE, /* needed so that scan results aren't rejected because
                                   there older than scan start */
                ie, new ArrayList<String>());
        return detail;
    }

    private ArrayList<ScanDetail> getMockScanResults() {
        ScanResults sr = ScanResults.create(0, 2412, 2437, 2462, 5180, 5220, 5745, 5825);
        ArrayList<ScanDetail> list = sr.getScanDetailArrayList();

        list.add(getGoogleGuestScanDetail(TEST_RSSI, sBSSID, sFreq));
        return list;
    }

    private void injectDhcpSuccess(DhcpResults dhcpResults) {
        mIpManagerCallback.onNewDhcpResults(dhcpResults);
        mIpManagerCallback.onProvisioningSuccess(new LinkProperties());
    }

    private void injectDhcpFailure() {
        mIpManagerCallback.onNewDhcpResults(null);
        mIpManagerCallback.onProvisioningFailure(new LinkProperties());
    }

    static final String   sSSID = "\"GoogleGuest\"";
    static final WifiSsid sWifiSsid = WifiSsid.createFromAsciiEncoded(sSSID);
    static final String   sBSSID = "01:02:03:04:05:06";
    static final String   sBSSID1 = "02:01:04:03:06:05";
    static final int      sFreq = 2437;
    static final int      sFreq1 = 5240;
    static final String   WIFI_IFACE_NAME = "mockWlan";

    WifiStateMachine mWsm;
    HandlerThread mWsmThread;
    HandlerThread mP2pThread;
    HandlerThread mSyncThread;
    AsyncChannel  mWsmAsyncChannel;
    AsyncChannel  mNetworkFactoryChannel;
    TestAlarmManager mAlarmManager;
    MockWifiMonitor mWifiMonitor;
    TestLooper mLooper;
    Context mContext;
    MockResources mResources;
    FrameworkFacade mFrameworkFacade;
    IpManager.Callback mIpManagerCallback;
    PhoneStateListener mPhoneStateListener;
    NetworkRequest mDefaultNetworkRequest;

    final ArgumentCaptor<SoftApManager.Listener> mSoftApManagerListenerCaptor =
                    ArgumentCaptor.forClass(SoftApManager.Listener.class);

    @Mock WifiScanner mWifiScanner;
    @Mock SupplicantStateTracker mSupplicantStateTracker;
    @Mock WifiMetrics mWifiMetrics;
    @Mock UserManager mUserManager;
    @Mock WifiApConfigStore mApConfigStore;
    @Mock BackupManagerProxy mBackupManagerProxy;
    @Mock WifiCountryCode mCountryCode;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock PropertyService mPropertyService;
    @Mock BuildProperties mBuildProperties;
    @Mock IWificond mWificond;
    @Mock IApInterface mApInterface;
    @Mock IClientInterface mClientInterface;
    @Mock IBinder mApInterfaceBinder;
    @Mock IBinder mClientInterfaceBinder;
    @Mock IBinder mPackageManagerBinder;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock SoftApManager mSoftApManager;
    @Mock WifiStateTracker mWifiStateTracker;
    @Mock PasspointManager mPasspointManager;
    @Mock SelfRecovery mSelfRecovery;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock IpManager mIpManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock WrongPasswordNotifier mWrongPasswordNotifier;
    @Mock Clock mClock;
    @Mock ScanDetailCache mScanDetailCache;
    @Mock BaseWifiDiagnostics mWifiDiagnostics;
    @Mock ConnectivityManager mConnectivityManager;

    public WifiStateMachineTest() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "Setting up ...");

        // Ensure looper exists
        mLooper = new TestLooper();

        MockitoAnnotations.initMocks(this);

        /** uncomment this to enable logs from WifiStateMachines */
        // enableDebugLogs();

        mWifiMonitor = new MockWifiMonitor();
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        when(mWifiInjector.getClock()).thenReturn(new Clock());
        when(mWifiInjector.getWifiLastResortWatchdog()).thenReturn(mWifiLastResortWatchdog);
        when(mWifiInjector.getPropertyService()).thenReturn(mPropertyService);
        when(mWifiInjector.getBuildProperties()).thenReturn(mBuildProperties);
        when(mWifiInjector.getKeyStore()).thenReturn(mock(KeyStore.class));
        when(mWifiInjector.getWifiBackupRestore()).thenReturn(mock(WifiBackupRestore.class));
        when(mWifiInjector.makeWifiDiagnostics(anyObject())).thenReturn(mWifiDiagnostics);
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getWifiScanner()).thenReturn(mWifiScanner);
        when(mWifiInjector.makeWifiConnectivityManager(any(WifiInfo.class), anyBoolean()))
                .thenReturn(mWifiConnectivityManager);
        when(mWifiInjector.makeSoftApManager(any(INetworkManagementService.class),
                mSoftApManagerListenerCaptor.capture(), any(IApInterface.class),
                any(WifiConfiguration.class)))
                .thenReturn(mSoftApManager);
        when(mWifiInjector.getPasspointManager()).thenReturn(mPasspointManager);
        when(mWifiInjector.getWifiStateTracker()).thenReturn(mWifiStateTracker);
        when(mWifiInjector.getWifiMonitor()).thenReturn(mWifiMonitor);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getSelfRecovery()).thenReturn(mSelfRecovery);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiInjector.makeTelephonyManager()).thenReturn(mTelephonyManager);
        when(mWifiInjector.getClock()).thenReturn(mClock);

        when(mWifiNative.setupForClientMode())
                .thenReturn(Pair.create(WifiNative.SETUP_SUCCESS, mClientInterface));
        when(mWifiNative.setupForSoftApMode())
                .thenReturn(Pair.create(WifiNative.SETUP_SUCCESS, mApInterface));
        when(mApInterface.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
        when(mWifiNative.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
        when(mWifiNative.enableSupplicant()).thenReturn(true);
        when(mWifiNative.disableSupplicant()).thenReturn(true);
        when(mWifiNative.getFrameworkNetworkId(anyInt())).thenReturn(0);


        mFrameworkFacade = getFrameworkFacade();
        mContext = getContext();

        mResources = getMockResources();
        when(mContext.getResources()).thenReturn(mResources);

        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_FREQUENCY_BAND,
                WifiManager.WIFI_FREQUENCY_BAND_AUTO)).thenReturn(
                WifiManager.WIFI_FREQUENCY_BAND_AUTO);

        when(mFrameworkFacade.makeApConfigStore(eq(mContext), eq(mBackupManagerProxy)))
                .thenReturn(mApConfigStore);

        when(mFrameworkFacade.makeSupplicantStateTracker(
                any(Context.class), any(WifiConfigManager.class),
                any(Handler.class))).thenReturn(mSupplicantStateTracker);

        when(mUserManager.getProfileParent(11))
                .thenReturn(new UserInfo(UserHandle.USER_SYSTEM, "owner", 0));
        when(mUserManager.getProfiles(UserHandle.USER_SYSTEM)).thenReturn(Arrays.asList(
                new UserInfo(UserHandle.USER_SYSTEM, "owner", 0),
                new UserInfo(11, "managed profile", 0)));

        when(mApInterface.asBinder()).thenReturn(mApInterfaceBinder);
        when(mClientInterface.asBinder()).thenReturn(mClientInterfaceBinder);

        doAnswer(new AnswerWithArguments() {
            public void answer(PhoneStateListener phoneStateListener, int events)
                    throws Exception {
                mPhoneStateListener = phoneStateListener;
            }
        }).when(mTelephonyManager).listen(any(PhoneStateListener.class), anyInt());

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        initializeWsm();
    }

    private void registerAsyncChannel(Consumer<AsyncChannel> consumer, Messenger messenger) {
        final AsyncChannel channel = new AsyncChannel();
        Handler handler = new Handler(mLooper.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                        if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                            consumer.accept(channel);
                        } else {
                            Log.d(TAG, "Failed to connect Command channel " + this);
                        }
                        break;
                    case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                        Log.d(TAG, "Command channel disconnected" + this);
                        break;
                }
            }
        };

        channel.connect(mContext, handler, messenger);
        mLooper.dispatchAll();
    }

    private void initializeWsm() throws Exception {
        mWsm = new WifiStateMachine(mContext, mFrameworkFacade, mLooper.getLooper(),
                mUserManager, mWifiInjector, mBackupManagerProxy, mCountryCode, mWifiNative,
                mWrongPasswordNotifier);
        mWsmThread = getWsmHandlerThread(mWsm);

        registerAsyncChannel((x) -> {
            mWsmAsyncChannel = x;
        }, mWsm.getMessenger());

        mBinderToken = Binder.clearCallingIdentity();

        /* Send the BOOT_COMPLETED message to setup some WSM state. */
        mWsm.sendMessage(WifiStateMachine.CMD_BOOT_COMPLETED);
        mLooper.dispatchAll();

        /* Simulate the initial NetworkRequest sent in by ConnectivityService. */
        ArgumentCaptor<Messenger> captor = ArgumentCaptor.forClass(Messenger.class);
        verify(mConnectivityManager, atLeast(2)).registerNetworkFactory(
                captor.capture(), anyString());
        Messenger networkFactoryMessenger = captor.getAllValues().get(0);
        registerAsyncChannel((x) -> {
            mNetworkFactoryChannel = x;
        }, networkFactoryMessenger);

        mDefaultNetworkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        sendDefaultNetworkRequest(TEST_VALID_NETWORK_SCORE);
    }

    /**
     * Helper function to resend the cached network request (id == 0) with the specified score.
     * Note: If you need to add a separate network request, don't use the builder to create one
     * since the new request object will again default to id == 0.
     */
    private void sendDefaultNetworkRequest(int score) {
        mNetworkFactoryChannel.sendMessage(
                NetworkFactory.CMD_REQUEST_NETWORK, score, 0, mDefaultNetworkRequest);
        mLooper.dispatchAll();
    }

    @After
    public void cleanUp() throws Exception {
        Binder.restoreCallingIdentity(mBinderToken);

        if (mSyncThread != null) stopLooper(mSyncThread.getLooper());
        if (mWsmThread != null) stopLooper(mWsmThread.getLooper());
        if (mP2pThread != null) stopLooper(mP2pThread.getLooper());

        mWsmThread = null;
        mP2pThread = null;
        mSyncThread = null;
        mWsmAsyncChannel = null;
        mWsm = null;
        mNetworkFactoryChannel = null;
    }

    @Test
    public void createNew() throws Exception {
        assertEquals("InitialState", getCurrentState().getName());

        mWsm.sendMessage(WifiStateMachine.CMD_BOOT_COMPLETED);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
    }

    @Test
    public void loadComponentsInStaMode() throws Exception {
        startSupplicantAndDispatchMessages();

        verify(mContext).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), eq(UserHandle.ALL));

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    private void checkApStateChangedBroadcast(Intent intent, int expectedCurrentState,
            int expectedPrevState, int expectedErrorCode, String expectedIfaceName,
            int expectedMode) {
        int currentState = intent.getIntExtra(EXTRA_WIFI_AP_STATE, WIFI_AP_STATE_DISABLED);
        int prevState = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_AP_STATE, WIFI_AP_STATE_DISABLED);
        int errorCode = intent.getIntExtra(EXTRA_WIFI_AP_FAILURE_REASON, HOTSPOT_NO_ERROR);
        String ifaceName = intent.getStringExtra(EXTRA_WIFI_AP_INTERFACE_NAME);
        int mode = intent.getIntExtra(EXTRA_WIFI_AP_MODE, WifiManager.IFACE_IP_MODE_UNSPECIFIED);
        assertEquals(expectedCurrentState, currentState);
        assertEquals(expectedPrevState, prevState);
        assertEquals(expectedErrorCode, errorCode);
        assertEquals(expectedIfaceName, ifaceName);
        assertEquals(expectedMode, mode);
    }

    private void loadComponentsInApMode(int mode) throws Exception {
        SoftApModeConfiguration config = new SoftApModeConfiguration(mode, new WifiConfiguration());
        mWsm.setHostApRunning(config, true);
        mLooper.dispatchAll();

        assertEquals("SoftApState", getCurrentState().getName());

        verify(mWifiNative).setupForSoftApMode();
        verify(mSoftApManager).start();

        // reset expectations for mContext due to previously sent AP broadcast
        reset(mContext);

        // get the SoftApManager.Listener and trigger some updates
        SoftApManager.Listener listener = mSoftApManagerListenerCaptor.getValue();
        listener.onStateChanged(WIFI_AP_STATE_ENABLING, 0);
        listener.onStateChanged(WIFI_AP_STATE_ENABLED, 0);
        listener.onStateChanged(WIFI_AP_STATE_DISABLING, 0);
        // note, this will trigger a mode change when TestLooper is dispatched
        listener.onStateChanged(WIFI_AP_STATE_DISABLED, 0);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(4))
                .sendStickyBroadcastAsUser(intentCaptor.capture(), eq(UserHandle.ALL));

        List<Intent> capturedIntents = intentCaptor.getAllValues();
        checkApStateChangedBroadcast(capturedIntents.get(0), WIFI_AP_STATE_ENABLING,
                WIFI_AP_STATE_DISABLED, HOTSPOT_NO_ERROR, WIFI_IFACE_NAME, mode);
        checkApStateChangedBroadcast(capturedIntents.get(1), WIFI_AP_STATE_ENABLED,
                WIFI_AP_STATE_ENABLING, HOTSPOT_NO_ERROR, WIFI_IFACE_NAME, mode);
        checkApStateChangedBroadcast(capturedIntents.get(2), WIFI_AP_STATE_DISABLING,
                WIFI_AP_STATE_ENABLED, HOTSPOT_NO_ERROR, WIFI_IFACE_NAME, mode);
        checkApStateChangedBroadcast(capturedIntents.get(3), WIFI_AP_STATE_DISABLED,
                WIFI_AP_STATE_DISABLING, HOTSPOT_NO_ERROR, WIFI_IFACE_NAME, mode);
    }

    @Test
    public void loadComponentsInApModeForTethering() throws Exception {
        loadComponentsInApMode(WifiManager.IFACE_IP_MODE_TETHERED);
    }

    @Test
    public void loadComponentsInApModeForLOHS() throws Exception {
        loadComponentsInApMode(WifiManager.IFACE_IP_MODE_LOCAL_ONLY);
    }

    @Test
    public void shouldRequireSupplicantStartupToLeaveInitialState() throws Exception {
        when(mWifiNative.enableSupplicant()).thenReturn(false);
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
        // we should not be sending a wifi enabling update
        verify(mContext, never()).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), any());
    }

    @Test
    public void shouldRequireWificondToLeaveInitialState() throws Exception {
        // We start out with valid default values, break them going backwards so that
        // we test all the bailout cases.

        // ClientInterface dies after creation.
        doThrow(new RemoteException()).when(mClientInterfaceBinder).linkToDeath(any(), anyInt());
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());

        // Failed to even create the client interface.
        when(mWificond.createClientInterface()).thenReturn(null);
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());

        // Failed to get wificond proxy.
        when(mWifiInjector.makeWificond()).thenReturn(null);
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
    }

    @Test
    public void loadComponentsFailure() throws Exception {
        when(mWifiNative.enableSupplicant()).thenReturn(false);

        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());

        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
    }

    @Test
    public void checkInitialStateStickyWhenDisabledMode() throws Exception {
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());

        mWsm.setOperationalMode(WifiStateMachine.DISABLED_MODE);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.DISABLED_MODE, mWsm.getOperationalModeForTest());
        assertEquals("InitialState", getCurrentState().getName());
    }

    @Test
    public void shouldStartSupplicantWhenConnectModeRequested() throws Exception {
        // The first time we start out in InitialState, we sit around here.
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());

        // But if someone tells us to enter connect mode, we start up supplicant
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals("SupplicantStartingState", getCurrentState().getName());
    }

    /**
     *  Test that mode changes accurately reflect the value for isWifiEnabled.
     */
    @Test
    public void checkIsWifiEnabledForModeChanges() throws Exception {
        // Check initial state
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
        assertEquals(WifiManager.WIFI_STATE_DISABLED, mWsm.syncGetWifiState());

        mWsm.setOperationalMode(WifiStateMachine.SCAN_ONLY_MODE);
        startSupplicantAndDispatchMessages();
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.SCAN_ONLY_MODE, mWsm.getOperationalModeForTest());
        assertEquals("ScanModeState", getCurrentState().getName());
        assertEquals(WifiManager.WIFI_STATE_DISABLED, mWsm.syncGetWifiState());
        verify(mContext, never()).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), any());


        // switch to connect mode and verify wifi is reported as enabled
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals(WifiManager.WIFI_STATE_ENABLED, mWsm.syncGetWifiState());
        verify(mContext).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), eq(UserHandle.ALL));

        // reset the expectations on mContext since we did get an expected broadcast, but we should
        // not on the next transition
        reset(mContext);

        // now go back to scan mode with "wifi disabled" to verify the reported wifi state.
        mWsm.setOperationalMode(WifiStateMachine.SCAN_ONLY_WITH_WIFI_OFF_MODE);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.SCAN_ONLY_WITH_WIFI_OFF_MODE,
                     mWsm.getOperationalModeForTest());
        assertEquals("ScanModeState", getCurrentState().getName());
        assertEquals(WifiManager.WIFI_STATE_DISABLED, mWsm.syncGetWifiState());
        verify(mContext, never()).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), any());

        // now go to AP mode
        mWsm.setSupplicantRunning(false);
        mWsm.sendMessage(WifiStateMachine.CMD_DISABLE_P2P_RSP);
        mWsm.sendMessage(WifiMonitor.SUP_DISCONNECTION_EVENT);
        SoftApModeConfiguration config = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, new WifiConfiguration());
        mWsm.setHostApRunning(config, true);
        mLooper.dispatchAll();
        assertEquals("SoftApState", getCurrentState().getName());
        assertEquals(WifiManager.WIFI_STATE_DISABLED, mWsm.syncGetWifiState());
        verify(mContext, never()).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), any());
    }

    private class WifiEnablingStateIntentMatcher implements ArgumentMatcher<Intent> {
        @Override
        public boolean matches(Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION != intent.getAction()) {
                // not the correct type
                return false;
            }
            return WifiManager.WIFI_STATE_ENABLING
                    == intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                                          WifiManager.WIFI_STATE_DISABLED);
        }
    }

    /**
     * Test that mode changes for WifiStateMachine in the InitialState are realized when supplicant
     * is started.
     */
    @Test
    public void checkStartInCorrectStateAfterChangingInitialState() throws Exception {
        // Check initial state
        mLooper.dispatchAll();
        assertEquals("InitialState", getCurrentState().getName());
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());

        // Update the mode
        mWsm.setOperationalMode(WifiStateMachine.SCAN_ONLY_MODE);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.SCAN_ONLY_MODE, mWsm.getOperationalModeForTest());

        // Start supplicant so we move to the next state
        startSupplicantAndDispatchMessages();

        assertEquals("ScanModeState", getCurrentState().getName());
        verify(mContext, never()).sendStickyBroadcastAsUser(
                (Intent) argThat(new WifiEnablingStateIntentMatcher()), any());
    }

    private void canRemoveNetwork() {
        boolean result;
        when(mWifiConfigManager.removeNetwork(eq(0), anyInt())).thenReturn(true);
        mLooper.startAutoDispatch();
        result = mWsm.syncRemoveNetwork(mWsmAsyncChannel, 0);
        mLooper.stopAutoDispatch();

        assertTrue(result);
        verify(mWifiConfigManager).removeNetwork(anyInt(), anyInt());
    }

    /**
     * Verifies that configs can be removed when not in client mode.
     */
    @Test
    public void canRemoveNetworkConfigWhenWifiDisabled() {
        canRemoveNetwork();
    }


    /**
     * Verifies that configs can be removed when in client mode.
     */
    @Test
    public void canRemoveNetworkConfigInClientMode() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        canRemoveNetwork();
    }

    private void canForgetNetwork() {
        when(mWifiConfigManager.removeNetwork(eq(0), anyInt())).thenReturn(true);
        mWsm.sendMessage(WifiManager.FORGET_NETWORK, 0, MANAGED_PROFILE_UID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).removeNetwork(anyInt(), anyInt());
    }

    /**
     * Verifies that configs can be removed when not in client mode.
     */
    @Test
    public void canForgetNetworkConfigWhenWifiDisabled() throws Exception {
        canForgetNetwork();
    }

    /**
     * Verifies that configs can be forgotten when in client mode.
     */
    @Test
    public void canForgetNetworkConfigInClientMode() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        canForgetNetwork();
    }

    private void canSaveNetworkConfig() {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        int networkId = TEST_NETWORK_ID;
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(networkId));
        when(mWifiConfigManager.enableNetwork(eq(networkId), eq(false), anyInt()))
                .thenReturn(true);

        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.SAVE_NETWORK, config);
        mLooper.stopAutoDispatch();
        assertEquals(WifiManager.SAVE_NETWORK_SUCCEEDED, reply.what);

        verify(mWifiConfigManager).addOrUpdateNetwork(any(WifiConfiguration.class), anyInt());
        verify(mWifiConfigManager).enableNetwork(eq(networkId), eq(false), anyInt());
    }

    /**
     * Verifies that configs can be saved when not in client mode.
     */
    @Test
    public void canSaveNetworkConfigWhenWifiDisabled() throws Exception {
        canSaveNetworkConfig();
    }

    /**
     * Verifies that configs can be saved when in client mode.
     */
    @Test
    public void canSaveNetworkConfigInClientMode() throws Exception {
        loadComponentsInStaMode();
        canSaveNetworkConfig();
    }

    /**
     * Verifies that null configs are rejected in SAVE_NETWORK message.
     */
    @Test
    public void saveNetworkConfigFailsWithNullConfig() throws Exception {
        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.SAVE_NETWORK, null);
        mLooper.stopAutoDispatch();
        assertEquals(WifiManager.SAVE_NETWORK_FAILED, reply.what);

        verify(mWifiConfigManager, never())
                .addOrUpdateNetwork(any(WifiConfiguration.class), anyInt());
        verify(mWifiConfigManager, never())
                .enableNetwork(anyInt(), anyBoolean(), anyInt());
    }

    /**
     * Verifies that configs save fails when the addition of network fails.
     */
    @Test
    public void saveNetworkConfigFailsWithConfigAddFailure() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID));

        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.SAVE_NETWORK, config);
        mLooper.stopAutoDispatch();
        assertEquals(WifiManager.SAVE_NETWORK_FAILED, reply.what);

        verify(mWifiConfigManager).addOrUpdateNetwork(any(WifiConfiguration.class), anyInt());
        verify(mWifiConfigManager, never())
                .enableNetwork(anyInt(), anyBoolean(), anyInt());
    }

    /**
     * Verifies that configs save fails when the enable of network fails.
     */
    @Test
    public void saveNetworkConfigFailsWithConfigEnableFailure() throws Exception {
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();

        int networkId = 5;
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(networkId));
        when(mWifiConfigManager.enableNetwork(eq(networkId), eq(false), anyInt()))
                .thenReturn(false);

        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.SAVE_NETWORK, config);
        mLooper.stopAutoDispatch();
        assertEquals(WifiManager.SAVE_NETWORK_FAILED, reply.what);

        verify(mWifiConfigManager).addOrUpdateNetwork(any(WifiConfiguration.class), anyInt());
        verify(mWifiConfigManager).enableNetwork(eq(networkId), eq(false), anyInt());
    }

    /**
     * Helper method to move through SupplicantStarting and SupplicantStarted states.
     */
    private void startSupplicantAndDispatchMessages() throws Exception {
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();

        assertEquals("SupplicantStartingState", getCurrentState().getName());

        when(mWifiNative.setDeviceName(anyString())).thenReturn(true);
        when(mWifiNative.setManufacturer(anyString())).thenReturn(true);
        when(mWifiNative.setModelName(anyString())).thenReturn(true);
        when(mWifiNative.setModelNumber(anyString())).thenReturn(true);
        when(mWifiNative.setSerialNumber(anyString())).thenReturn(true);
        when(mWifiNative.setConfigMethods(anyString())).thenReturn(true);
        when(mWifiNative.setDeviceType(anyString())).thenReturn(true);
        when(mWifiNative.setSerialNumber(anyString())).thenReturn(true);
        when(mWifiNative.setScanningMacOui(any(byte[].class))).thenReturn(true);

        mWsm.sendMessage(WifiMonitor.SUP_CONNECTION_EVENT);
        mLooper.dispatchAll();

        verify(mWifiNative).setupForClientMode();
        verify(mWifiLastResortWatchdog).clearAllFailureCounts();
    }

    private void addNetworkAndVerifySuccess(boolean isHidden) throws Exception {
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = sSSID;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.hiddenSSID = isHidden;

        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(0));
        when(mWifiConfigManager.getSavedNetworks()).thenReturn(Arrays.asList(config));
        when(mWifiConfigManager.getConfiguredNetwork(0)).thenReturn(config);
        when(mWifiConfigManager.getConfiguredNetworkWithPassword(0)).thenReturn(config);

        mLooper.startAutoDispatch();
        mWsm.syncAddOrUpdateNetwork(mWsmAsyncChannel, config);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).addOrUpdateNetwork(eq(config), anyInt());

        mLooper.startAutoDispatch();
        List<WifiConfiguration> configs = mWsm.syncGetConfiguredNetworks(-1, mWsmAsyncChannel);
        mLooper.stopAutoDispatch();
        assertEquals(1, configs.size());

        WifiConfiguration config2 = configs.get(0);
        assertEquals("\"GoogleGuest\"", config2.SSID);
        assertTrue(config2.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
    }

    private void initializeAndAddNetworkAndVerifySuccess() throws Exception {
        initializeAndAddNetworkAndVerifySuccess(false);
    }

    private void initializeAndAddNetworkAndVerifySuccess(boolean isHidden) throws Exception {
        loadComponentsInStaMode();
        addNetworkAndVerifySuccess(isHidden);
    }

    /**
     * Helper method to retrieve WifiConfiguration by SSID.
     *
     * Returns the associated WifiConfiguration if it is found, null otherwise.
     */
    private WifiConfiguration getWifiConfigurationForNetwork(String ssid) {
        mLooper.startAutoDispatch();
        List<WifiConfiguration> configs = mWsm.syncGetConfiguredNetworks(-1, mWsmAsyncChannel);
        mLooper.stopAutoDispatch();

        for (WifiConfiguration checkConfig : configs) {
            if (checkConfig.SSID.equals(ssid)) {
                return checkConfig;
            }
        }
        return null;
    }

    private void verifyScan(int band, int reportEvents, Set<String> hiddenNetworkSSIDSet) {
        ArgumentCaptor<WifiScanner.ScanSettings> scanSettingsCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanSettings.class);
        ArgumentCaptor<WifiScanner.ScanListener> scanListenerCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanListener.class);
        verify(mWifiScanner).startScan(scanSettingsCaptor.capture(), scanListenerCaptor.capture(),
                eq(null));
        WifiScanner.ScanSettings actualSettings = scanSettingsCaptor.getValue();
        assertEquals("band", band, actualSettings.band);
        assertEquals("reportEvents", reportEvents, actualSettings.reportEvents);

        if (hiddenNetworkSSIDSet == null) {
            hiddenNetworkSSIDSet = new HashSet<>();
        }
        Set<String> actualHiddenNetworkSSIDSet = new HashSet<>();
        if (actualSettings.hiddenNetworks != null) {
            for (int i = 0; i < actualSettings.hiddenNetworks.length; ++i) {
                actualHiddenNetworkSSIDSet.add(actualSettings.hiddenNetworks[i].ssid);
            }
        }
        assertEquals("hidden networks", hiddenNetworkSSIDSet, actualHiddenNetworkSSIDSet);

        when(mWifiNative.getScanResults()).thenReturn(getMockScanResults());
        mWsm.sendMessage(WifiMonitor.SCAN_RESULTS_EVENT);

        mLooper.dispatchAll();

        List<ScanResult> reportedResults = mWsm.syncGetScanResultsList();
        assertEquals(8, reportedResults.size());
    }

    @Test
    public void scan() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mWsm.startScan(-1, 0, null, null);
        mLooper.dispatchAll();

        verifyScan(WifiScanner.WIFI_BAND_BOTH_WITH_DFS,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT, null);
    }

    @Test
    public void scanWithHiddenNetwork() throws Exception {
        initializeAndAddNetworkAndVerifySuccess(true);

        Set<String> hiddenNetworkSet = new HashSet<>();
        hiddenNetworkSet.add(sSSID);
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenNetworkList = new ArrayList<>();
        hiddenNetworkList.add(new WifiScanner.ScanSettings.HiddenNetwork(sSSID));
        when(mWifiConfigManager.retrieveHiddenNetworkList()).thenReturn(hiddenNetworkList);

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mWsm.startScan(-1, 0, null, null);
        mLooper.dispatchAll();

        verifyScan(WifiScanner.WIFI_BAND_BOTH_WITH_DFS,
                WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                hiddenNetworkSet);
    }

    private void setupAndStartConnectSequence(WifiConfiguration config) throws Exception {
        when(mWifiConfigManager.enableNetwork(eq(config.networkId), eq(true), anyInt()))
                .thenReturn(true);
        when(mWifiConfigManager.checkAndUpdateLastConnectUid(eq(config.networkId), anyInt()))
                .thenReturn(true);
        when(mWifiConfigManager.getConfiguredNetwork(eq(config.networkId)))
                .thenReturn(config);
        when(mWifiConfigManager.getConfiguredNetworkWithPassword(eq(config.networkId)))
                .thenReturn(config);

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        verify(mWifiNative).removeAllNetworks();

        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncEnableNetwork(mWsmAsyncChannel, config.networkId, true));
        mLooper.stopAutoDispatch();
    }

    private void validateSuccessfulConnectSequence(WifiConfiguration config) {
        verify(mWifiConfigManager).enableNetwork(eq(config.networkId), eq(true), anyInt());
        verify(mWifiConnectivityManager).setUserConnectChoice(eq(config.networkId));
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager).getConfiguredNetworkWithPassword(eq(config.networkId));
        verify(mWifiNative).connectToNetwork(eq(config));
    }

    private void validateFailureConnectSequence(WifiConfiguration config) {
        verify(mWifiConfigManager).enableNetwork(eq(config.networkId), eq(true), anyInt());
        verify(mWifiConnectivityManager).setUserConnectChoice(eq(config.networkId));
        verify(mWifiConnectivityManager).prepareForForcedConnection(eq(config.networkId));
        verify(mWifiConfigManager, never()).getConfiguredNetworkWithPassword(eq(config.networkId));
        verify(mWifiNative, never()).connectToNetwork(eq(config));
    }

    /**
     * Tests the network connection initiation sequence with the default network request pending
     * from WifiNetworkFactory.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnect() throws Exception {
        loadComponentsInStaMode();
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID;
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
    }

    /**
     * Tests the network connection initiation sequence with no network request pending from
     * from WifiNetworkFactory.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we don't invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequest() throws Exception {
        loadComponentsInStaMode();
        // Change the network score to remove the network request.
        sendDefaultNetworkRequest(TEST_OUTSCORED_NETWORK_SCORE);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID;
        setupAndStartConnectSequence(config);
        validateFailureConnectSequence(config);
    }

    /**
     * Tests the entire successful network connection flow.
     */
    @Test
    public void connect() throws Exception {
        triggerConnect();
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(sBSSID)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID, sFreq));
        when(mScanDetailCache.getScanResult(sBSSID)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID, sFreq).getScanResult());

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());

        DhcpResults dhcpResults = new DhcpResults();
        dhcpResults.setGateway("1.2.3.4");
        dhcpResults.setIpAddress("192.168.1.100", 0);
        dhcpResults.addDns("8.8.8.8");
        dhcpResults.setLeaseDuration(3600);

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWsm.getWifiInfo();
        assertNotNull(wifiInfo);
        assertEquals(sBSSID, wifiInfo.getBSSID());
        assertEquals(sFreq, wifiInfo.getFrequency());
        assertTrue(sWifiSsid.equals(wifiInfo.getWifiSsid()));

        assertEquals("ConnectedState", getCurrentState().getName());
    }

    /**
     * Tests the network connection initiation sequence with no network request pending from
     * from WifiNetworkFactory when we're already connected to a different network.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequestAndAlreadyConnected() throws Exception {
        // Simulate the first connection.
        connect();

        // Change the network score to remove the network request.
        sendDefaultNetworkRequest(TEST_OUTSCORED_NETWORK_SCORE);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        validateSuccessfulConnectSequence(config);
        verify(mWifiPermissionsUtil, times(2)).checkNetworkSettingsPermission(anyInt());
    }

    /**
     * Tests the network connection initiation sequence from a non-privileged app with no network
     * request pending from from WifiNetworkFactory when we're already connected to a different
     * network.
     * This simulates the connect sequence using the public
     * {@link WifiManager#enableNetwork(int, boolean)} and ensures that we don't invoke
     * {@link WifiNative#connectToNetwork(WifiConfiguration)}.
     */
    @Test
    public void triggerConnectWithNoNetworkRequestAndAlreadyConnectedButNonPrivilegedApp()
            throws Exception {
        // Simulate the first connection.
        connect();

        // Change the network score to remove the network request.
        sendDefaultNetworkRequest(TEST_OUTSCORED_NETWORK_SCORE);

        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(false);

        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.networkId = FRAMEWORK_NETWORK_ID + 1;
        setupAndStartConnectSequence(config);
        validateFailureConnectSequence(config);
        verify(mWifiPermissionsUtil, times(2)).checkNetworkSettingsPermission(anyInt());
    }

    @Test
    public void connectWithNoEnablePermission() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        when(mWifiConfigManager.enableNetwork(eq(0), eq(true), anyInt())).thenReturn(false);
        when(mWifiConfigManager.checkAndUpdateLastConnectUid(eq(0), anyInt())).thenReturn(false);

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        verify(mWifiNative).removeAllNetworks();

        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true));
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());
        verify(mWifiConnectivityManager, never()).setUserConnectChoice(eq(0));

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());

        DhcpResults dhcpResults = new DhcpResults();
        dhcpResults.setGateway("1.2.3.4");
        dhcpResults.setIpAddress("192.168.1.100", 0);
        dhcpResults.addDns("8.8.8.8");
        dhcpResults.setLeaseDuration(3600);

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        assertEquals("ConnectedState", getCurrentState().getName());
    }

    @Test
    public void enableWithInvalidNetworkId() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();
        when(mWifiConfigManager.getConfiguredNetwork(eq(0))).thenReturn(null);

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        verify(mWifiNative).removeAllNetworks();

        mLooper.startAutoDispatch();
        assertFalse(mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true));
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager, never()).enableNetwork(eq(0), eq(true), anyInt());
        verify(mWifiConfigManager, never()).checkAndUpdateLastConnectUid(eq(0), anyInt());
    }

    /**
     * If caller tries to connect to a network that is already connected, the connection request
     * should succeed.
     *
     * Test: Create and connect to a network, then try to reconnect to the same network. Verify
     * that connection request returns with CONNECT_NETWORK_SUCCEEDED.
     */
    @Test
    public void reconnectToConnectedNetwork() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        verify(mWifiNative).removeAllNetworks();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());

        // try to reconnect
        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.CONNECT_NETWORK, 0);
        mLooper.stopAutoDispatch();

        assertEquals(WifiManager.CONNECT_NETWORK_SUCCEEDED, reply.what);
    }

    @Test
    public void testDhcpFailure() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());

        injectDhcpFailure();
        mLooper.dispatchAll();

        assertEquals("DisconnectingState", getCurrentState().getName());
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_AUTHENTICATION_FAILURE
     * when wrong password authentication failure is detected and the network had been
     * connected previously.
     */
    @Test
    public void testWrongPasswordWithPreviouslyConnected() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        WifiConfiguration config = new WifiConfiguration();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        mWsm.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, 0,
                WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier, never()).onWrongPasswordError(anyString());
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE));

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_BY_WRONG_PASSWORD
     * when wrong password authentication failure is detected and the network has never been
     * connected.
     */
    @Test
    public void testWrongPasswordWithNeverConnected() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = sSSID;
        config.getNetworkSelectionStatus().setHasEverConnected(false);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);

        mWsm.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, 0,
                WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD);
        mLooper.dispatchAll();

        verify(mWrongPasswordNotifier).onWrongPasswordError(eq(sSSID));
        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD));

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify that the network selection status will be updated with DISABLED_BY_WRONG_PASSWORD
     * when wrong password authentication failure is detected and the network is unknown.
     */
    @Test
    public void testWrongPasswordWithNullNetwork() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(null);

        mWsm.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, 0,
                WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD);
        mLooper.dispatchAll();

        verify(mWifiConfigManager).updateNetworkSelectionStatus(anyInt(),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD));

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    @Test
    public void testBadNetworkEvent() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        mLooper.startAutoDispatch();
        mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true);
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());

        mWsm.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
    }


    @Test
    public void smToString() throws Exception {
        assertEquals("CMD_CHANNEL_HALF_CONNECTED", mWsm.smToString(
                AsyncChannel.CMD_CHANNEL_HALF_CONNECTED));
        assertEquals("CMD_PRE_DHCP_ACTION", mWsm.smToString(
                DhcpClient.CMD_PRE_DHCP_ACTION));
        assertEquals("CMD_IP_REACHABILITY_LOST", mWsm.smToString(
                WifiStateMachine.CMD_IP_REACHABILITY_LOST));
    }

    @Test
    public void disconnect() throws Exception {
        connect();

        mWsm.sendMessage(WifiMonitor.NETWORK_DISCONNECTION_EVENT, -1, 3, sBSSID);
        mLooper.dispatchAll();
        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.DISCONNECTED));
        mLooper.dispatchAll();

        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Successfully connecting to a network will set WifiConfiguration's value of HasEverConnected
     * to true.
     *
     * Test: Successfully create and connect to a network. Check the config and verify
     * WifiConfiguration.getHasEverConnected() is true.
     */
    @Test
    public void setHasEverConnectedTrueOnConnect() throws Exception {
        connect();
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkAfterConnect(0);
    }

    /**
     * Fail network connection attempt and verify HasEverConnected remains false.
     *
     * Test: Successfully create a network but fail when connecting. Check the config and verify
     * WifiConfiguration.getHasEverConnected() is false.
     */
    @Test
    public void connectionFailureDoesNotSetHasEverConnectedTrue() throws Exception {
        testDhcpFailure();
        verify(mWifiConfigManager, never()).updateNetworkAfterConnect(0);
    }

    @Test
    public void iconQueryTest() throws Exception {
        // TODO(b/31065385): Passpoint config management.
    }

    @Test
    public void verboseLogRecSizeIsGreaterThanNormalSize() {
        assertTrue(LOG_REC_LIMIT_IN_VERBOSE_MODE > WifiStateMachine.NUM_LOG_RECS_NORMAL);
    }

    /**
     * Verifies that, by default, we allow only the "normal" number of log records.
     */
    @Test
    public void normalLogRecSizeIsUsedByDefault() {
        assertEquals(WifiStateMachine.NUM_LOG_RECS_NORMAL, mWsm.getLogRecMaxSize());
    }

    /**
     * Verifies that, in verbose mode, we allow a larger number of log records.
     */
    @Test
    public void enablingVerboseLoggingUpdatesLogRecSize() {
        mWsm.enableVerboseLogging(1);
        assertEquals(LOG_REC_LIMIT_IN_VERBOSE_MODE, mWsm.getLogRecMaxSize());
    }

    @Test
    public void disablingVerboseLoggingClearsRecords() {
        mWsm.sendMessage(WifiStateMachine.CMD_DISCONNECT);
        mLooper.dispatchAll();
        assertTrue(mWsm.getLogRecSize() >= 1);

        mWsm.enableVerboseLogging(0);
        assertEquals(0, mWsm.getLogRecSize());
    }

    @Test
    public void disablingVerboseLoggingUpdatesLogRecSize() {
        mWsm.enableVerboseLogging(1);
        mWsm.enableVerboseLogging(0);
        assertEquals(WifiStateMachine.NUM_LOG_RECS_NORMAL, mWsm.getLogRecMaxSize());
    }

    @Test
    public void logRecsIncludeDisconnectCommand() {
        // There's nothing special about the DISCONNECT command. It's just representative of
        // "normal" commands.
        mWsm.sendMessage(WifiStateMachine.CMD_DISCONNECT);
        mLooper.dispatchAll();
        assertEquals(1, mWsm.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == WifiStateMachine.CMD_DISCONNECT)
                .count());
    }

    @Test
    public void logRecsExcludeRssiPollCommandByDefault() {
        mWsm.sendMessage(WifiStateMachine.CMD_RSSI_POLL);
        mLooper.dispatchAll();
        assertEquals(0, mWsm.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == WifiStateMachine.CMD_RSSI_POLL)
                .count());
    }

    @Test
    public void logRecsIncludeRssiPollCommandWhenVerboseLoggingIsEnabled() {
        mWsm.enableVerboseLogging(1);
        mWsm.sendMessage(WifiStateMachine.CMD_RSSI_POLL);
        mLooper.dispatchAll();
        assertEquals(1, mWsm.copyLogRecs()
                .stream()
                .filter(logRec -> logRec.getWhat() == WifiStateMachine.CMD_RSSI_POLL)
                .count());
    }

    /** Verifies that enabling verbose logging sets the hal log property in eng builds. */
    @Test
    public void enablingVerboseLoggingSetsHalLogPropertyInEngBuilds() {
        reset(mPropertyService);  // Ignore calls made in setUp()
        when(mBuildProperties.isEngBuild()).thenReturn(true);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        mWsm.enableVerboseLogging(1);
        verify(mPropertyService).set("log.tag.WifiHAL", "V");
    }

    /** Verifies that enabling verbose logging sets the hal log property in userdebug builds. */
    @Test
    public void enablingVerboseLoggingSetsHalLogPropertyInUserdebugBuilds() {
        reset(mPropertyService);  // Ignore calls made in setUp()
        when(mBuildProperties.isUserdebugBuild()).thenReturn(true);
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        mWsm.enableVerboseLogging(1);
        verify(mPropertyService).set("log.tag.WifiHAL", "V");
    }

    /** Verifies that enabling verbose logging does NOT set the hal log property in user builds. */
    @Test
    public void enablingVerboseLoggingDoeNotSetHalLogPropertyInUserBuilds() {
        reset(mPropertyService);  // Ignore calls made in setUp()
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        mWsm.enableVerboseLogging(1);
        verify(mPropertyService, never()).set(anyString(), anyString());
    }

    private int testGetSupportedFeaturesCase(int supportedFeatures, boolean rttConfigured) {
        AsyncChannel channel = mock(AsyncChannel.class);
        Message reply = Message.obtain();
        reply.arg1 = supportedFeatures;
        reset(mPropertyService);  // Ignore calls made in setUp()
        when(channel.sendMessageSynchronously(WifiStateMachine.CMD_GET_SUPPORTED_FEATURES))
                .thenReturn(reply);
        when(mPropertyService.getBoolean("config.disable_rtt", false))
                .thenReturn(rttConfigured);
        return mWsm.syncGetSupportedFeatures(channel);
    }

    /** Verifies that syncGetSupportedFeatures() masks out capabilities based on system flags. */
    @Test
    public void syncGetSupportedFeatures() {
        final int featureAware = WifiManager.WIFI_FEATURE_AWARE;
        final int featureInfra = WifiManager.WIFI_FEATURE_INFRA;
        final int featureD2dRtt = WifiManager.WIFI_FEATURE_D2D_RTT;
        final int featureD2apRtt = WifiManager.WIFI_FEATURE_D2AP_RTT;

        assertEquals(0, testGetSupportedFeaturesCase(0, false));
        assertEquals(0, testGetSupportedFeaturesCase(0, true));
        assertEquals(featureAware | featureInfra,
                testGetSupportedFeaturesCase(featureAware | featureInfra, false));
        assertEquals(featureAware | featureInfra,
                testGetSupportedFeaturesCase(featureAware | featureInfra, true));
        assertEquals(featureInfra | featureD2dRtt,
                testGetSupportedFeaturesCase(featureInfra | featureD2dRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCase(featureInfra | featureD2dRtt, true));
        assertEquals(featureInfra | featureD2apRtt,
                testGetSupportedFeaturesCase(featureInfra | featureD2apRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCase(featureInfra | featureD2apRtt, true));
        assertEquals(featureInfra | featureD2dRtt | featureD2apRtt,
                testGetSupportedFeaturesCase(featureInfra | featureD2dRtt | featureD2apRtt, false));
        assertEquals(featureInfra,
                testGetSupportedFeaturesCase(featureInfra | featureD2dRtt | featureD2apRtt, true));
    }

    /**
     * Verify that syncAddOrUpdatePasspointConfig will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager}.
     */
    @Test
    public void syncAddOrUpdatePasspointConfig() throws Exception {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);

        when(mPasspointManager.addOrUpdateProvider(config, MANAGED_PROFILE_UID)).thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncAddOrUpdatePasspointConfig(
                mWsmAsyncChannel, config, MANAGED_PROFILE_UID));
        mLooper.stopAutoDispatch();
        reset(mPasspointManager);

        when(mPasspointManager.addOrUpdateProvider(config, MANAGED_PROFILE_UID)).thenReturn(false);
        mLooper.startAutoDispatch();
        assertFalse(mWsm.syncAddOrUpdatePasspointConfig(
                mWsmAsyncChannel, config, MANAGED_PROFILE_UID));
        mLooper.stopAutoDispatch();
    }

    /**
     * Verify that syncAddOrUpdatePasspointConfig will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager} when in client mode.
     */
    @Test
    public void syncAddOrUpdatePasspointConfigInClientMode() throws Exception {
        loadComponentsInStaMode();
        syncAddOrUpdatePasspointConfig();
    }

    /**
     * Verify that syncRemovePasspointConfig will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager}.
     */
    @Test
    public void syncRemovePasspointConfig() throws Exception {
        String fqdn = "test.com";
        when(mPasspointManager.removeProvider(fqdn)).thenReturn(true);
        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncRemovePasspointConfig(mWsmAsyncChannel, fqdn));
        mLooper.stopAutoDispatch();
        reset(mPasspointManager);

        when(mPasspointManager.removeProvider(fqdn)).thenReturn(false);
        mLooper.startAutoDispatch();
        assertFalse(mWsm.syncRemovePasspointConfig(mWsmAsyncChannel, fqdn));
        mLooper.stopAutoDispatch();
    }

    /**
     * Verify that syncRemovePasspointConfig will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager} when in client mode.
     */
    @Test
    public void syncRemovePasspointConfigInClientMode() throws Exception {
        loadComponentsInStaMode();
        syncRemovePasspointConfig();
    }

    /**
     * Verify that syncGetPasspointConfigs will redirect calls to {@link PasspointManager}
     * and returning the result that's returned from {@link PasspointManager}.
     */
    @Test
    public void syncGetPasspointConfigs() throws Exception {
        // Setup expected configs.
        List<PasspointConfiguration> expectedConfigs = new ArrayList<>();
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("test.com");
        config.setHomeSp(homeSp);
        expectedConfigs.add(config);

        when(mPasspointManager.getProviderConfigs()).thenReturn(expectedConfigs);
        mLooper.startAutoDispatch();
        assertEquals(expectedConfigs, mWsm.syncGetPasspointConfigs(mWsmAsyncChannel));
        mLooper.stopAutoDispatch();
        reset(mPasspointManager);

        when(mPasspointManager.getProviderConfigs())
                .thenReturn(new ArrayList<PasspointConfiguration>());
        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncGetPasspointConfigs(mWsmAsyncChannel).isEmpty());
        mLooper.stopAutoDispatch();
    }

    /**
     * Verify that syncGetMatchingWifiConfig will redirect calls to {@link PasspointManager}
     * with expected {@link WifiConfiguration} being returned when in client mode.
     *
     * @throws Exception
     */
    @Test
    public void syncGetMatchingWifiConfigInClientMode() throws Exception {
        loadComponentsInStaMode();

        when(mPasspointManager.getMatchingWifiConfig(any(ScanResult.class))).thenReturn(null);
        mLooper.startAutoDispatch();
        assertNull(mWsm.syncGetMatchingWifiConfig(new ScanResult(), mWsmAsyncChannel));
        mLooper.stopAutoDispatch();
        reset(mPasspointManager);

        WifiConfiguration expectedConfig = new WifiConfiguration();
        expectedConfig.SSID = "TestSSID";
        when(mPasspointManager.getMatchingWifiConfig(any(ScanResult.class)))
                .thenReturn(expectedConfig);
        mLooper.startAutoDispatch();
        WifiConfiguration actualConfig = mWsm.syncGetMatchingWifiConfig(new ScanResult(),
                mWsmAsyncChannel);
        mLooper.stopAutoDispatch();
        assertEquals(expectedConfig.SSID, actualConfig.SSID);
    }

    /**
     * Verify that syncGetMatchingWifiConfig will be a no-op and return {@code null} when not in
     * client mode.
     *
     * @throws Exception
     */
    @Test
    public void syncGetMatchingWifiConfigInNonClientMode() throws Exception {
        mLooper.startAutoDispatch();
        assertNull(mWsm.syncGetMatchingWifiConfig(new ScanResult(), mWsmAsyncChannel));
        mLooper.stopAutoDispatch();
        verify(mPasspointManager, never()).getMatchingWifiConfig(any(ScanResult.class));
    }

    /**
     *  Test that we disconnect from a network if it was removed while we are in the
     *  ObtainingIpState.
     */
    @Test
    public void disconnectFromNetworkWhenRemovedWhileObtainingIpAddr() throws Exception {
        initializeAndAddNetworkAndVerifySuccess();

        when(mWifiConfigManager.enableNetwork(eq(0), eq(true), anyInt())).thenReturn(true);
        when(mWifiConfigManager.checkAndUpdateLastConnectUid(eq(0), anyInt())).thenReturn(true);

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        verify(mWifiNative).removeAllNetworks();

        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncEnableNetwork(mWsmAsyncChannel, 0, true));
        mLooper.stopAutoDispatch();

        verify(mWifiConfigManager).enableNetwork(eq(0), eq(true), anyInt());
        verify(mWifiConnectivityManager).setUserConnectChoice(eq(0));
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);

        when(mScanDetailCache.getScanDetail(sBSSID)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID, sFreq));
        when(mScanDetailCache.getScanResult(sBSSID)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID, sFreq).getScanResult());

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, sBSSID);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());

        // now remove the config
        when(mWifiConfigManager.removeNetwork(eq(FRAMEWORK_NETWORK_ID), anyInt()))
                .thenReturn(true);
        mWsm.sendMessage(WifiManager.FORGET_NETWORK, FRAMEWORK_NETWORK_ID, MANAGED_PROFILE_UID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).removeNetwork(eq(FRAMEWORK_NETWORK_ID), anyInt());

        reset(mWifiConfigManager);

        when(mWifiConfigManager.getConfiguredNetwork(FRAMEWORK_NETWORK_ID)).thenReturn(null);

        DhcpResults dhcpResults = new DhcpResults();
        dhcpResults.setGateway("1.2.3.4");
        dhcpResults.setIpAddress("192.168.1.100", 0);
        dhcpResults.addDns("8.8.8.8");
        dhcpResults.setLeaseDuration(3600);

        injectDhcpSuccess(dhcpResults);
        mLooper.dispatchAll();

        assertEquals("DisconnectingState", getCurrentState().getName());
    }

    /**
     * Sunny-day scenario for WPS connections. Verifies that after a START_WPS and
     * NETWORK_CONNECTION_EVENT command, WifiStateMachine will have transitioned to ObtainingIpState
     */
    @Test
    public void wpsPbcConnectSuccess() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());

        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.PBC;
        wpsInfo.BSSID = sBSSID;
        when(mWifiNative.removeAllNetworks()).thenReturn(true);
        when(mWifiNative.startWpsPbc(anyString())).thenReturn(true);
        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        assertEquals("WpsRunningState", getCurrentState().getName());

        setupMocksForWpsNetworkMigration();
        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        assertEquals("ObtainingIpState", getCurrentState().getName());
        verifyMocksForWpsNetworkMigration();
    }

    /**
     * Verify failure in starting Wps PBC network connection.
     */
    @Test
    public void wpsPbcConnectFailure() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        when(mWifiNative.startWpsPbc(eq(sBSSID))).thenReturn(false);
        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.PBC;
        wpsInfo.BSSID = sBSSID;

        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).startWpsPbc(eq(sBSSID));

        assertFalse("WpsRunningState".equals(getCurrentState().getName()));
    }

    /**
     * Verify that if Supplicant generates multiple networks for a WPS configuration,
     * WifiStateMachine cycles into DisconnectedState
     */
    @Test
    public void wpsPbcConnectFailure_tooManyConfigs() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());

        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.PBC;
        wpsInfo.BSSID = sBSSID;
        when(mWifiNative.removeAllNetworks()).thenReturn(true);
        when(mWifiNative.startWpsPbc(anyString())).thenReturn(true);
        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        assertEquals("WpsRunningState", getCurrentState().getName());

        setupMocksForWpsNetworkMigration();
        doAnswer(new AnswerWithArguments() {
            public boolean answer(Map<String, WifiConfiguration> configs,
                                  SparseArray<Map<String, String>> networkExtras) throws Exception {
                configs.put("dummy1", new WifiConfiguration());
                configs.put("dummy2", new WifiConfiguration());
                return true;
            }
        }).when(mWifiNative).migrateNetworksFromSupplicant(any(Map.class), any(SparseArray.class));
        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        assertTrue("DisconnectedState".equals(getCurrentState().getName()));
    }

    /**
     * Verify that when supplicant fails to load networks during WPS, WifiStateMachine cycles into
     * DisconnectedState
     */
    @Test
    public void wpsPbcConnectFailure_migrateNetworksFailure() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());

        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.PBC;
        wpsInfo.BSSID = sBSSID;
        when(mWifiNative.removeAllNetworks()).thenReturn(true);
        when(mWifiNative.startWpsPbc(anyString())).thenReturn(true);
        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        assertEquals("WpsRunningState", getCurrentState().getName());

        setupMocksForWpsNetworkMigration();
        when(mWifiNative.migrateNetworksFromSupplicant(any(Map.class), any(SparseArray.class)))
                .thenReturn(false);
        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        assertEquals("DisconnectedState", getCurrentState().getName());
    }

    /**
     * Verify successful Wps Pin Display network connection.
     */
    @Test
    public void wpsPinDisplayConnectSuccess() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        when(mWifiNative.startWpsPinDisplay(eq(sBSSID))).thenReturn("34545434");
        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.DISPLAY;
        wpsInfo.BSSID = sBSSID;

        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).startWpsPinDisplay(eq(sBSSID));

        assertEquals("WpsRunningState", getCurrentState().getName());

        setupMocksForWpsNetworkMigration();

        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, null);
        mLooper.dispatchAll();

        assertEquals("ObtainingIpState", getCurrentState().getName());
        verifyMocksForWpsNetworkMigration();
    }

    /**
     * Verify failure in Wps Pin Display network connection.
     */
    @Test
    public void wpsPinDisplayConnectFailure() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        when(mWifiNative.startWpsPinDisplay(eq(sBSSID))).thenReturn(null);
        WpsInfo wpsInfo = new WpsInfo();
        wpsInfo.setup = WpsInfo.DISPLAY;
        wpsInfo.BSSID = sBSSID;

        mWsm.sendMessage(WifiManager.START_WPS, 0, 0, wpsInfo);
        mLooper.dispatchAll();
        verify(mWifiNative).startWpsPinDisplay(eq(sBSSID));

        assertFalse("WpsRunningState".equals(getCurrentState().getName()));
    }

    @Test
    public void handleVendorHalDeath() throws Exception {
        ArgumentCaptor<WifiNative.VendorHalDeathEventHandler> deathHandlerCapturer =
                ArgumentCaptor.forClass(WifiNative.VendorHalDeathEventHandler.class);
        when(mWifiNative.initializeVendorHal(deathHandlerCapturer.capture())).thenReturn(true);

        // Trigger initialize to capture the death handler registration.
        mLooper.startAutoDispatch();
        assertTrue(mWsm.syncInitialize(mWsmAsyncChannel));
        mLooper.stopAutoDispatch();

        verify(mWifiNative).initializeVendorHal(any(WifiNative.VendorHalDeathEventHandler.class));
        WifiNative.VendorHalDeathEventHandler deathHandler = deathHandlerCapturer.getValue();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        // Now trigger the death notification.
        deathHandler.onDeath();
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumHalCrashes();
        verify(mSelfRecovery).trigger(eq(SelfRecovery.REASON_HAL_CRASH));
        verify(mWifiDiagnostics).captureBugReportData(WifiDiagnostics.REPORT_REASON_HAL_CRASH);
    }

    @Test
    public void handleWificondDeath() throws Exception {
        ArgumentCaptor<StateMachineDeathRecipient> deathHandlerCapturer =
                ArgumentCaptor.forClass(StateMachineDeathRecipient.class);

        // Trigger initialize to capture the death handler registration.
        loadComponentsInStaMode();

        verify(mClientInterfaceBinder).linkToDeath(deathHandlerCapturer.capture(), anyInt());
        StateMachineDeathRecipient deathHandler = deathHandlerCapturer.getValue();

        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();

        // Now trigger the death notification.
        deathHandler.binderDied();
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumWificondCrashes();
        verify(mSelfRecovery).trigger(eq(SelfRecovery.REASON_WIFICOND_CRASH));
        verify(mWifiDiagnostics).captureBugReportData(WifiDiagnostics.REPORT_REASON_WIFICOND_CRASH);
    }

    private void setupMocksForWpsNetworkMigration() {
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = WPS_SUPPLICANT_NETWORK_ID;
        config.SSID = DEFAULT_TEST_SSID;
        doAnswer(new AnswerWithArguments() {
            public boolean answer(Map<String, WifiConfiguration> configs,
                                  SparseArray<Map<String, String>> networkExtras) throws Exception {
                configs.put("dummy", config);
                return true;
            }
        }).when(mWifiNative).migrateNetworksFromSupplicant(any(Map.class), any(SparseArray.class));
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(WPS_FRAMEWORK_NETWORK_ID));
        when(mWifiConfigManager.enableNetwork(eq(WPS_FRAMEWORK_NETWORK_ID), anyBoolean(), anyInt()))
                .thenReturn(true);
        when(mWifiNative.getFrameworkNetworkId(eq(WPS_FRAMEWORK_NETWORK_ID))).thenReturn(
                WPS_FRAMEWORK_NETWORK_ID);
        when(mWifiConfigManager.getConfiguredNetwork(eq(WPS_FRAMEWORK_NETWORK_ID))).thenReturn(
                config);
    }

    private void verifyMocksForWpsNetworkMigration() {
        // Network Ids should be reset so that it is treated as addition.
        ArgumentCaptor<WifiConfiguration> wifiConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(wifiConfigCaptor.capture(), anyInt());
        assertEquals(WifiConfiguration.INVALID_NETWORK_ID, wifiConfigCaptor.getValue().networkId);
        assertEquals(DEFAULT_TEST_SSID, wifiConfigCaptor.getValue().SSID);
        verify(mWifiConfigManager).enableNetwork(eq(WPS_FRAMEWORK_NETWORK_ID), anyBoolean(),
                anyInt());
    }

    /**
     * Verifies that WifiInfo is updated upon SUPPLICANT_STATE_CHANGE_EVENT.
     */
    @Test
    public void testWifiInfoUpdatedUponSupplicantStateChangedEvent() throws Exception {
        // Connect to network with |sBSSID|, |sFreq|.
        connect();

        // Set the scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(sBSSID1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID1, sFreq1));
        when(mScanDetailCache.getScanResult(sBSSID1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID1, sFreq1).getScanResult());

        // This simulates the behavior of roaming to network with |sBSSID1|, |sFreq1|.
        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated.
        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID1, SupplicantState.COMPLETED));
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWsm.getWifiInfo();
        assertEquals(sBSSID1, wifiInfo.getBSSID());
        assertEquals(sFreq1, wifiInfo.getFrequency());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());
    }

    /**
     * Verifies that WifiInfo is updated upon CMD_ASSOCIATED_BSSID event.
     */
    @Test
    public void testWifiInfoUpdatedUponAssociatedBSSIDEvent() throws Exception {
        // Connect to network with |sBSSID|, |sFreq|.
        connect();

        // Set the scan detail cache for roaming target.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(FRAMEWORK_NETWORK_ID))
                .thenReturn(mScanDetailCache);
        when(mScanDetailCache.getScanDetail(sBSSID1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID1, sFreq1));
        when(mScanDetailCache.getScanResult(sBSSID1)).thenReturn(
                getGoogleGuestScanDetail(TEST_RSSI, sBSSID1, sFreq1).getScanResult());

        // This simulates the behavior of roaming to network with |sBSSID1|, |sFreq1|.
        // Send a CMD_ASSOCIATED_BSSID, verify WifiInfo is updated.
        mWsm.sendMessage(WifiStateMachine.CMD_ASSOCIATED_BSSID, 0, 0, sBSSID1);
        mLooper.dispatchAll();

        WifiInfo wifiInfo = mWsm.getWifiInfo();
        assertEquals(sBSSID1, wifiInfo.getBSSID());
        assertEquals(sFreq1, wifiInfo.getFrequency());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());
    }

    /**
     * Verifies that WifiInfo is cleared upon exiting and entering WifiInfo, and that it is not
     * updated by SUPPLICAN_STATE_CHANGE_EVENTs in ScanModeState.
     * This protects WifiStateMachine from  getting into a bad state where WifiInfo says wifi is
     * already Connected or Connecting, (when it is in-fact Disconnected), so
     * WifiConnectivityManager does not attempt any new Connections, freezing wifi.
     */
    @Test
    public void testWifiInfoCleanedUpEnteringExitingConnectModeState() throws Exception {
        InOrder inOrder = inOrder(mWifiConnectivityManager);
        Log.i(TAG, mWsm.getCurrentState().getName());
        String initialBSSID = "aa:bb:cc:dd:ee:ff";
        WifiInfo wifiInfo = mWsm.getWifiInfo();
        wifiInfo.setBSSID(initialBSSID);

        // Set WSM to CONNECT_MODE and verify state, and wifi enabled in ConnectivityManager
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        startSupplicantAndDispatchMessages();
        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals(WifiManager.WIFI_STATE_ENABLED, mWsm.syncGetWifiState());
        inOrder.verify(mWifiConnectivityManager).setWifiEnabled(eq(true));
        assertNull(wifiInfo.getBSSID());

        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is updated
        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        assertEquals(sBSSID, wifiInfo.getBSSID());
        assertEquals(SupplicantState.COMPLETED, wifiInfo.getSupplicantState());

        // Set WSM to SCAN_ONLY_MODE, verify state and wifi disabled in ConnectivityManager, and
        // WifiInfo is reset() and state set to DISCONNECTED
        mWsm.setOperationalMode(WifiStateMachine.SCAN_ONLY_MODE);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.SCAN_ONLY_MODE, mWsm.getOperationalModeForTest());
        assertEquals("ScanModeState", getCurrentState().getName());
        assertEquals(WifiManager.WIFI_STATE_DISABLED, mWsm.syncGetWifiState());
        inOrder.verify(mWifiConnectivityManager).setWifiEnabled(eq(false));
        assertNull(wifiInfo.getBSSID());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());

        // Send a SUPPLICANT_STATE_CHANGE_EVENT, verify WifiInfo is not updated
        mWsm.sendMessage(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED));
        mLooper.dispatchAll();
        assertNull(wifiInfo.getBSSID());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());

        // Set the bssid to something, so we can verify it is cleared (just in case)
        wifiInfo.setBSSID(initialBSSID);

        // Set WSM to CONNECT_MODE and verify state, and wifi enabled in ConnectivityManager,
        // and WifiInfo has been reset
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals(WifiManager.WIFI_STATE_ENABLED, mWsm.syncGetWifiState());
        inOrder.verify(mWifiConnectivityManager).setWifiEnabled(eq(true));
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertEquals(SupplicantState.DISCONNECTED, wifiInfo.getSupplicantState());
        assertNull(wifiInfo.getBSSID());
    }

    /**
     * Test that the process uid has full wifiInfo access.
     * Also tests that {@link WifiStateMachine#syncRequestConnectionInfo(String)} always
     * returns a copy of WifiInfo.
     */
    @Test
    public void testConnectedIdsAreVisibleFromOwnUid() throws Exception {
        assertEquals(Process.myUid(), Binder.getCallingUid());
        WifiInfo wifiInfo = mWsm.getWifiInfo();
        wifiInfo.setBSSID(sBSSID);
        wifiInfo.setSSID(WifiSsid.createFromAsciiEncoded(sSSID));

        connect();
        WifiInfo connectionInfo = mWsm.syncRequestConnectionInfo(mContext.getOpPackageName());

        assertNotEquals(wifiInfo, connectionInfo);
        assertEquals(wifiInfo.getSSID(), connectionInfo.getSSID());
        assertEquals(wifiInfo.getBSSID(), connectionInfo.getBSSID());
    }

    /**
     * Test that connected SSID and BSSID are not exposed to an app that does not have the
     * appropriate permissions.
     * Also tests that {@link WifiStateMachine#syncRequestConnectionInfo(String)} always
     * returns a copy of WifiInfo.
     */
    @Test
    public void testConnectedIdsAreHiddenFromRandomApp() throws Exception {
        int actualUid = Binder.getCallingUid();
        int fakeUid = Process.myUid() + 100000;
        assertNotEquals(actualUid, fakeUid);
        BinderUtil.setUid(fakeUid);
        try {
            WifiInfo wifiInfo = mWsm.getWifiInfo();

            // Get into a connected state, with known BSSID and SSID
            connect();
            assertEquals(sBSSID, wifiInfo.getBSSID());
            assertEquals(sWifiSsid, wifiInfo.getWifiSsid());

            when(mWifiPermissionsUtil.canAccessFullConnectionInfo(any(), anyString(), eq(fakeUid),
                    anyInt())).thenReturn(false);

            WifiInfo connectionInfo = mWsm.syncRequestConnectionInfo(mContext.getOpPackageName());

            assertNotEquals(wifiInfo, connectionInfo);
            assertEquals(WifiSsid.NONE, connectionInfo.getSSID());
            assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, connectionInfo.getBSSID());
        } finally {
            BinderUtil.setUid(actualUid);
        }
    }

    /**
     * Test that connected SSID and BSSID are not exposed to an app that does not have the
     * appropriate permissions, when canAccessScanResults raises a SecurityException.
     * Also tests that {@link WifiStateMachine#syncRequestConnectionInfo(String)} always
     * returns a copy of WifiInfo.
     */
    @Test
    public void testConnectedIdsAreHiddenOnSecurityException() throws Exception {
        int actualUid = Binder.getCallingUid();
        int fakeUid = Process.myUid() + 100000;
        assertNotEquals(actualUid, fakeUid);
        BinderUtil.setUid(fakeUid);
        try {
            WifiInfo wifiInfo = mWsm.getWifiInfo();

            // Get into a connected state, with known BSSID and SSID
            connect();
            assertEquals(sBSSID, wifiInfo.getBSSID());
            assertEquals(sWifiSsid, wifiInfo.getWifiSsid());

            when(mWifiPermissionsUtil.canAccessFullConnectionInfo(any(), anyString(), eq(fakeUid),
                    anyInt())).thenThrow(new SecurityException());

            WifiInfo connectionInfo = mWsm.syncRequestConnectionInfo(mContext.getOpPackageName());

            assertNotEquals(wifiInfo, connectionInfo);
            assertEquals(WifiSsid.NONE, connectionInfo.getSSID());
            assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, connectionInfo.getBSSID());
        } finally {
            BinderUtil.setUid(actualUid);
        }
    }

    /**
     * Test that connected SSID and BSSID are exposed to an app that does have the
     * appropriate permissions.
     */
    @Test
    public void testConnectedIdsAreVisibleFromPermittedApp() throws Exception {
        int actualUid = Binder.getCallingUid();
        int fakeUid = Process.myUid() + 100000;
        BinderUtil.setUid(fakeUid);
        try {
            WifiInfo wifiInfo = mWsm.getWifiInfo();

            // Get into a connected state, with known BSSID and SSID
            connect();
            assertEquals(sBSSID, wifiInfo.getBSSID());
            assertEquals(sWifiSsid, wifiInfo.getWifiSsid());

            when(mWifiPermissionsUtil.canAccessFullConnectionInfo(any(), anyString(), eq(fakeUid),
                    anyInt())).thenReturn(true);

            WifiInfo connectionInfo = mWsm.syncRequestConnectionInfo(mContext.getOpPackageName());

            assertNotEquals(wifiInfo, connectionInfo);
            assertEquals(wifiInfo.getSSID(), connectionInfo.getSSID());
            assertEquals(wifiInfo.getBSSID(), connectionInfo.getBSSID());
            // Access to our MAC address uses a different permission, make sure it is not granted
            assertEquals(WifiInfo.DEFAULT_MAC_ADDRESS, connectionInfo.getMacAddress());
        } finally {
            BinderUtil.setUid(actualUid);
        }
    }

    /**
     * Test that reconnectCommand() triggers connectivity scan when WifiStateMachine
     * is in DisconnectedMode.
     */
    @Test
    public void testReconnectCommandWhenDisconnected() throws Exception {
        // Connect to network with |sBSSID|, |sFreq|, and then disconnect.
        disconnect();

        mWsm.reconnectCommand(WifiStateMachine.WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager).forceConnectivityScan(WifiStateMachine.WIFI_WORK_SOURCE);
    }

    /**
     * Test that reconnectCommand() doesn't trigger connectivity scan when WifiStateMachine
     * is in ConnectedMode.
     */
    @Test
    public void testReconnectCommandWhenConnected() throws Exception {
        // Connect to network with |sBSSID|, |sFreq|.
        connect();

        mWsm.reconnectCommand(WifiStateMachine.WIFI_WORK_SOURCE);
        mLooper.dispatchAll();
        verify(mWifiConnectivityManager, never())
                .forceConnectivityScan(WifiStateMachine.WIFI_WORK_SOURCE);
    }

    /**
     * Adds the network without putting WifiStateMachine into ConnectMode.
     */
    @Test
    public void addNetworkInInitialState() throws Exception {
        // We should not be in initial state now.
        assertTrue("InitialState".equals(getCurrentState().getName()));
        addNetworkAndVerifySuccess(false);
        verify(mWifiConnectivityManager, never()).setUserConnectChoice(eq(0));
    }

    /**
     * Test START_WPS with a null wpsInfo object fails gracefully (No NPE)
     */
    @Test
    public void testStartWps_nullWpsInfo() throws Exception {
        loadComponentsInStaMode();

        mLooper.startAutoDispatch();
        Message reply = mWsmAsyncChannel.sendMessageSynchronously(WifiManager.START_WPS, 0, 0,
                null);
        mLooper.stopAutoDispatch();
        assertEquals(WifiManager.WPS_FAILED, reply.what);
    }

    /**
     * Test that DISABLE_NETWORK returns failure to public API when WifiConfigManager returns
     * failure.
     */
    @Test
    public void testSyncDisableNetwork_failure() throws Exception {
        loadComponentsInStaMode();
        when(mWifiConfigManager.disableNetwork(anyInt(), anyInt())).thenReturn(false);

        mLooper.startAutoDispatch();
        boolean succeeded = mWsm.syncDisableNetwork(mWsmAsyncChannel, 0);
        mLooper.stopAutoDispatch();
        assertFalse(succeeded);
    }

    /**
     * Test that failure to start HAL in AP mode increments the corresponding metrics.
     */
    @Test
    public void testSetupForSoftApModeHalFailureIncrementsMetrics() throws Exception {
        when(mWifiNative.setupForSoftApMode())
                .thenReturn(Pair.create(WifiNative.SETUP_FAILURE_HAL, null));

        SoftApModeConfiguration config = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, new WifiConfiguration());
        mWsm.setHostApRunning(config, true);
        mLooper.dispatchAll();

        verify(mWifiNative).setupForSoftApMode();
        verify(mWifiMetrics).incrementNumWifiOnFailureDueToHal();
    }

    /**
     * Test that failure to start HAL in AP mode increments the corresponding metrics.
     */
    @Test
    public void testSetupForSoftApModeWificondFailureIncrementsMetrics() throws Exception {
        when(mWifiNative.setupForSoftApMode())
                .thenReturn(Pair.create(WifiNative.SETUP_FAILURE_WIFICOND, null));

        SoftApModeConfiguration config = new SoftApModeConfiguration(
                WifiManager.IFACE_IP_MODE_TETHERED, new WifiConfiguration());
        mWsm.setHostApRunning(config, true);
        mLooper.dispatchAll();

        verify(mWifiNative).setupForSoftApMode();
        verify(mWifiMetrics).incrementNumWifiOnFailureDueToWificond();
    }

    /**
     * Test that failure to start HAL in client mode increments the corresponding metrics.
     */
    @Test
    public void testSetupForClientModeHalFailureIncrementsMetrics() throws Exception {
        when(mWifiNative.setupForClientMode())
                .thenReturn(Pair.create(WifiNative.SETUP_FAILURE_HAL, null));

        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUP_CONNECTION_EVENT);
        mLooper.dispatchAll();

        verify(mWifiNative).setupForClientMode();
        verify(mWifiMetrics).incrementNumWifiOnFailureDueToHal();
    }

    /**
     * Test that failure to start HAL in client mode increments the corresponding metrics.
     */
    @Test
    public void testSetupForClientModeWificondFailureIncrementsMetrics() throws Exception {
        when(mWifiNative.setupForClientMode())
                .thenReturn(Pair.create(WifiNative.SETUP_FAILURE_WIFICOND, null));

        mWsm.setSupplicantRunning(true);
        mLooper.dispatchAll();

        mWsm.sendMessage(WifiMonitor.SUP_CONNECTION_EVENT);
        mLooper.dispatchAll();

        verify(mWifiNative).setupForClientMode();
        verify(mWifiMetrics).incrementNumWifiOnFailureDueToWificond();
    }

    /**
     * Test that we don't register the telephony call state listener on devices which do not support
     * setting/resetting Tx power limit.
     */
    @Test
    public void testVoiceCallSar_disabledTxPowerScenario_WifiOn() throws Exception {
        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNull(mPhoneStateListener);
    }

    /**
     * Test that we do register the telephony call state listener on devices which do support
     * setting/resetting Tx power limit.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenario_WifiOn() throws Exception {
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, true);
        initializeWsm();

        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNotNull(mPhoneStateListener);
    }

    /**
     * Test that we do register the telephony call state listener on devices which do support
     * setting/resetting Tx power limit and set the tx power level if we're in state
     * {@link TelephonyManager#CALL_STATE_OFFHOOK}.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateOffHook_WhenWifiTurnedOn()
            throws Exception {
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, true);
        initializeWsm();

        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(true);
        when(mTelephonyManager.isOffhook()).thenReturn(true);

        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNotNull(mPhoneStateListener);
        verify(mWifiNative).selectTxPowerScenario(eq(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
    }

    /**
     * Test that we do register the telephony call state listener on devices which do support
     * setting/resetting Tx power limit and set the tx power level if we're in state
     * {@link TelephonyManager#CALL_STATE_IDLE}.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateIdle_WhenWifiTurnedOn()
            throws Exception {
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, true);
        initializeWsm();

        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(true);
        when(mTelephonyManager.isIdle()).thenReturn(true);

        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNotNull(mPhoneStateListener);
    }

    /**
     * Test that we do register the telephony call state listener on devices which do support
     * setting/resetting Tx power limit and set the tx power level if we're in state
     * {@link TelephonyManager#CALL_STATE_OFFHOOK}. This test checks if the
     * {@link WifiNative#selectTxPowerScenario(int)} failure is handled correctly.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateOffHook_WhenWifiTurnedOn_Fails()
            throws Exception {
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, true);
        initializeWsm();

        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(false);
        when(mTelephonyManager.isOffhook()).thenReturn(true);

        loadComponentsInStaMode();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        assertEquals(WifiStateMachine.CONNECT_MODE, mWsm.getOperationalModeForTest());
        assertEquals("DisconnectedState", getCurrentState().getName());
        assertNotNull(mPhoneStateListener);
        verify(mWifiNative).selectTxPowerScenario(eq(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
    }

    /**
     * Test that we invoke the corresponding WifiNative method when
     * {@link PhoneStateListener#onCallStateChanged(int, String)} is invoked with state
     * {@link TelephonyManager#CALL_STATE_OFFHOOK}.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateOffHook_WhenWifiOn()
            throws Exception {
        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(true);
        testVoiceCallSar_enabledTxPowerScenario_WifiOn();

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "");
        mLooper.dispatchAll();
        verify(mWifiNative).selectTxPowerScenario(eq(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
    }

    /**
     * Test that we invoke the corresponding WifiNative method when
     * {@link PhoneStateListener#onCallStateChanged(int, String)} is invoked with state
     * {@link TelephonyManager#CALL_STATE_IDLE}.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateIdle_WhenWifiOn() throws Exception {
        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(true);
        testVoiceCallSar_enabledTxPowerScenario_WifiOn();

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, "");
        mLooper.dispatchAll();
        verify(mWifiNative, atLeastOnce())
                .selectTxPowerScenario(eq(WifiNative.TX_POWER_SCENARIO_NORMAL));
    }

    /**
     * Test that we invoke the corresponding WifiNative method when
     * {@link PhoneStateListener#onCallStateChanged(int, String)} is invoked with state
     * {@link TelephonyManager#CALL_STATE_OFFHOOK}. This test checks if the
     * {@link WifiNative#selectTxPowerScenario(int)} failure is handled correctly.
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallStateOffHook_WhenWifiOn_Fails()
            throws Exception {
        when(mWifiNative.selectTxPowerScenario(anyInt())).thenReturn(false);
        testVoiceCallSar_enabledTxPowerScenario_WifiOn();

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "");
        mLooper.dispatchAll();
        verify(mWifiNative).selectTxPowerScenario(eq(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
    }

    /**
     * Test that we don't invoke the corresponding WifiNative method when
     * {@link PhoneStateListener#onCallStateChanged(int, String)} is invoked with state
     * {@link TelephonyManager#CALL_STATE_IDLE} or {@link TelephonyManager#CALL_STATE_OFFHOOK} when
     * wifi is off (state machine is not in SupplicantStarted state).
     */
    @Test
    public void testVoiceCallSar_enabledTxPowerScenarioCallState_WhenWifiOff() throws Exception {
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_voice_call_sar_tx_power_limit, true);
        initializeWsm();

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "");
        mLooper.dispatchAll();
        verify(mWifiNative, never()).selectTxPowerScenario(anyInt());

        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, "");
        mLooper.dispatchAll();
        verify(mWifiNative, never()).selectTxPowerScenario(anyInt());
    }

    /**
     * Verifies that a network disconnection event will result in WifiStateMachine invoking
     * {@link WifiConfigManager#removeAllEphemeralOrPasspointConfiguredNetworks()} to remove
     * any ephemeral or passpoint networks from it's internal database.
     */
    @Test
    public void testDisconnectionRemovesEphemeralAndPasspointNetworks() throws Exception {
        disconnect();
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /**
     * Verifies that WifiStateMachine sets and unsets appropriate 'RecentFailureReason' values
     * on a WifiConfiguration when it fails association, authentication, or successfully connects
     */
    @Test
    public void testExtraFailureReason_ApIsBusy() throws Exception {
        // Setup CONNECT_MODE & a WifiConfiguration
        initializeAndAddNetworkAndVerifySuccess();
        mWsm.setOperationalMode(WifiStateMachine.CONNECT_MODE);
        mLooper.dispatchAll();
        // Trigger a connection to this (CMD_START_CONNECT will actually fail, but it sets up
        // targetNetworkId state)
        mWsm.sendMessage(WifiStateMachine.CMD_START_CONNECT, 0, 0, sBSSID);
        mLooper.dispatchAll();
        // Simulate an ASSOCIATION_REJECTION_EVENT, due to the AP being busy
        mWsm.sendMessage(WifiMonitor.ASSOCIATION_REJECTION_EVENT, 0,
                ISupplicantStaIfaceCallback.StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA, sBSSID);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).setRecentFailureAssociationStatus(eq(0),
                eq(WifiConfiguration.RecentFailure.STATUS_AP_UNABLE_TO_HANDLE_NEW_STA));
        assertEquals("DisconnectedState", getCurrentState().getName());

        // Simulate an AUTHENTICATION_FAILURE_EVENT, which should clear the ExtraFailureReason
        reset(mWifiConfigManager);
        mWsm.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).clearRecentFailureReason(eq(0));
        verify(mWifiConfigManager, never()).setRecentFailureAssociationStatus(anyInt(), anyInt());

        // Simulate a NETWORK_CONNECTION_EVENT which should clear the ExtraFailureReason
        reset(mWifiConfigManager);
        mWsm.sendMessage(WifiMonitor.NETWORK_CONNECTION_EVENT, 0, 0, null);
        mLooper.dispatchAll();
        verify(mWifiConfigManager).clearRecentFailureReason(eq(0));
        verify(mWifiConfigManager, never()).setRecentFailureAssociationStatus(anyInt(), anyInt());
    }

    /**
     * Test that the helper method
     * {@link WifiStateMachine#shouldEvaluateWhetherToSendExplicitlySelected(WifiConfiguration)}
     * returns true when we connect to the last selected network before expiration of
     * {@link WifiStateMachine#LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS}.
     */
    @Test
    public void testShouldEvaluateWhetherToSendExplicitlySelected_SameNetworkNotExpired() {
        long lastSelectedTimestamp = 45666743454L;
        int lastSelectedNetworkId = 5;

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                lastSelectedTimestamp
                        + WifiStateMachine.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(lastSelectedTimestamp);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(lastSelectedNetworkId);

        WifiConfiguration currentConfig = new WifiConfiguration();
        currentConfig.networkId = lastSelectedNetworkId;
        assertTrue(mWsm.shouldEvaluateWhetherToSendExplicitlySelected(currentConfig));
    }

    /**
     * Test that the helper method
     * {@link WifiStateMachine#shouldEvaluateWhetherToSendExplicitlySelected(WifiConfiguration)}
     * returns false when we connect to the last selected network after expiration of
     * {@link WifiStateMachine#LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS}.
     */
    @Test
    public void testShouldEvaluateWhetherToSendExplicitlySelected_SameNetworkExpired() {
        long lastSelectedTimestamp = 45666743454L;
        int lastSelectedNetworkId = 5;

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                lastSelectedTimestamp
                        + WifiStateMachine.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS + 1);
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(lastSelectedTimestamp);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(lastSelectedNetworkId);

        WifiConfiguration currentConfig = new WifiConfiguration();
        currentConfig.networkId = lastSelectedNetworkId;
        assertFalse(mWsm.shouldEvaluateWhetherToSendExplicitlySelected(currentConfig));
    }

    /**
     * Test that the helper method
     * {@link WifiStateMachine#shouldEvaluateWhetherToSendExplicitlySelected(WifiConfiguration)}
     * returns false when we connect to a different network to the last selected network.
     */
    @Test
    public void testShouldEvaluateWhetherToSendExplicitlySelected_DifferentNetwork() {
        long lastSelectedTimestamp = 45666743454L;
        int lastSelectedNetworkId = 5;

        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                lastSelectedTimestamp
                        + WifiStateMachine.LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS - 1);
        when(mWifiConfigManager.getLastSelectedTimeStamp()).thenReturn(lastSelectedTimestamp);
        when(mWifiConfigManager.getLastSelectedNetwork()).thenReturn(lastSelectedNetworkId);

        WifiConfiguration currentConfig = new WifiConfiguration();
        currentConfig.networkId = lastSelectedNetworkId - 1;
        assertFalse(mWsm.shouldEvaluateWhetherToSendExplicitlySelected(currentConfig));
    }
}
