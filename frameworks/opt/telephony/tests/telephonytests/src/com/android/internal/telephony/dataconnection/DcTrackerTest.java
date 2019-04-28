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

package com.android.internal.telephony.dataconnection;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static com.android.internal.telephony.dataconnection.ApnSettingTest.createApnSetting;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.test.filters.FlakyTest;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.LocalLog;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DcTrackerTest extends TelephonyTest {

    private final static String[] sNetworkAttributes = new String[]{
            "mobile,0,0,0,-1,true", "mobile_mms,2,0,2,60000,true",
            "mobile_supl,3,0,2,60000,true", "mobile_dun,4,0,2,60000,true",
            "mobile_hipri,5,0,3,60000,true", "mobile_fota,10,0,2,60000,true",
            "mobile_ims,11,0,2,60000,true", "mobile_cbs,12,0,2,60000,true",
            "mobile_ia,14,0,2,-1,true", "mobile_emergency,15,0,2,-1,true"};

    private final static List<String> sApnTypes = Arrays.asList(
            "default", "mms", "cbs", "fota", "supl", "ia", "emergency", "dun", "hipri", "ims");
    private static final int LTE_BEARER_BITMASK = 1 << (ServiceState.RIL_RADIO_TECHNOLOGY_LTE - 1);
    private static final int EHRPD_BEARER_BITMASK =
            1 << (ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD - 1);
    public static final String FAKE_APN1 = "FAKE APN 1";
    public static final String FAKE_APN2 = "FAKE APN 2";
    public static final String FAKE_APN3 = "FAKE APN 3";
    public static final String FAKE_APN4 = "FAKE APN 4";
    public static final String FAKE_APN5 = "FAKE APN 5";
    public static final String FAKE_IFNAME = "FAKE IFNAME";
    public static final String FAKE_PCSCF_ADDRESS = "22.33.44.55";
    public static final String FAKE_GATEWAY = "11.22.33.44";
    public static final String FAKE_DNS = "55.66.77.88";
    public static final String FAKE_ADDRESS = "99.88.77.66";

    @Mock
    ISub mIsub;
    @Mock
    IBinder mBinder;
    @Mock
    NetworkRequest mNetworkRequest;
    @Mock
    SubscriptionInfo mSubscriptionInfo;
    @Mock
    ApnContext mApnContext;
    @Mock
    ApnSetting mApnSetting;
    @Mock
    DcAsyncChannel mDcac;

    private DcTracker mDct;
    private DcTrackerTestHandler mDcTrackerTestHandler;

    private AlarmManager mAlarmManager;

    private PersistableBundle mBundle;

    private SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;

    private final ApnSettingContentProvider mApnSettingContentProvider =
            new ApnSettingContentProvider();

    private class DcTrackerTestHandler extends HandlerThread {

        private DcTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDct = new DcTracker(mPhone);
            setReady(true);
        }
    }

    private class ApnSettingContentProvider extends MockContentProvider {

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            logd("ApnSettingContentProvider: query");
            logd("   uri = " + uri);
            logd("   projection = " + Arrays.toString(projection));
            logd("   selection = " + selection);
            logd("   selectionArgs = " + Arrays.toString(selectionArgs));
            logd("   sortOrder = " + sortOrder);

            if (uri.compareTo(Telephony.Carriers.CONTENT_URI) == 0) {
                if (projection == null && selectionArgs == null && selection != null) {

                    Pattern pattern = Pattern.compile("^numeric = '([0-9]*)'");
                    Matcher matcher = pattern.matcher(selection);
                    if (!matcher.find()) {
                        logd("Cannot find MCC/MNC from " + selection);
                        return null;
                    }

                    String plmn = matcher.group(1);

                    logd("Query '" + plmn + "' APN settings");
                    MatrixCursor mc = new MatrixCursor(
                            new String[]{Telephony.Carriers._ID, Telephony.Carriers.NUMERIC,
                                    Telephony.Carriers.NAME, Telephony.Carriers.APN,
                                    Telephony.Carriers.PROXY, Telephony.Carriers.PORT,
                                    Telephony.Carriers.MMSC, Telephony.Carriers.MMSPROXY,
                                    Telephony.Carriers.MMSPORT, Telephony.Carriers.USER,
                                    Telephony.Carriers.PASSWORD, Telephony.Carriers.AUTH_TYPE,
                                    Telephony.Carriers.TYPE,
                                    Telephony.Carriers.PROTOCOL,
                                    Telephony.Carriers.ROAMING_PROTOCOL,
                                    Telephony.Carriers.CARRIER_ENABLED, Telephony.Carriers.BEARER,
                                    Telephony.Carriers.BEARER_BITMASK,
                                    Telephony.Carriers.PROFILE_ID,
                                    Telephony.Carriers.MODEM_COGNITIVE,
                                    Telephony.Carriers.MAX_CONNS, Telephony.Carriers.WAIT_TIME,
                                    Telephony.Carriers.MAX_CONNS_TIME, Telephony.Carriers.MTU,
                                    Telephony.Carriers.MVNO_TYPE,
                                    Telephony.Carriers.MVNO_MATCH_DATA});

                    mc.addRow(new Object[]{
                            2163,                   // id
                            plmn,                   // numeric
                            "sp-mode",              // name
                            FAKE_APN1,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2164,                   // id
                            plmn,                   // numeric
                            "mopera U",             // name
                            FAKE_APN2,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            ServiceState.RIL_RADIO_TECHNOLOGY_LTE, // bearer,
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2165,                   // id
                            plmn,                   // numeric
                            "b-mobile for Nexus",   // name
                            FAKE_APN3,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "ims",                  // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            0,                      // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2166,                   // id
                            plmn,                   // numeric
                            "sp-mode ehrpd",        // name
                            FAKE_APN4,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "default,supl",         // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD, // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });

                    mc.addRow(new Object[]{
                            2166,                   // id
                            plmn,                   // numeric
                            "b-mobile for Nexus",   // name
                            FAKE_APN5,              // apn
                            "",                     // proxy
                            "",                     // port
                            "",                     // mmsc
                            "",                     // mmsproxy
                            "",                     // mmsport
                            "",                     // user
                            "",                     // password
                            -1,                     // authtype
                            "dun",                  // types
                            "IP",                   // protocol
                            "IP",                   // roaming_protocol
                            1,                      // carrier_enabled
                            0,                      // bearer
                            0,                      // bearer_bitmask
                            0,                      // profile_id
                            0,                      // modem_cognitive
                            0,                      // max_conns
                            0,                      // wait_time
                            0,                      // max_conns_time
                            0,                      // mtu
                            "",                     // mvno_type
                            ""                      // mnvo_match_data
                    });
                    return mc;
                }
            }

            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DcTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        doReturn("fake.action_attached").when(mPhone).getActionAttached();
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_LTE).when(mServiceState)
                .getRilDataRadioTechnology();
        doReturn("44010").when(mSimRecords).getOperatorNumeric();

        mContextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                sNetworkAttributes);
        mContextFixture.putStringArrayResource(com.android.internal.R.array.
                config_mobile_tcp_buffers, new String[]{
                "umts:131072,262144,1452032,4096,16384,399360",
                "hspa:131072,262144,2441216,4096,16384,399360",
                "hsupa:131072,262144,2441216,4096,16384,399360",
                "hsdpa:131072,262144,2441216,4096,16384,399360",
                "hspap:131072,262144,2441216,4096,16384,399360",
                "edge:16384,32768,131072,4096,16384,65536",
                "gprs:4096,8192,24576,4096,8192,24576",
                "1xrtt:16384,32768,131070,4096,16384,102400",
                "evdo:131072,262144,1048576,4096,16384,524288",
                "lte:524288,1048576,8388608,262144,524288,4194304"});

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mApnSettingContentProvider);

        doReturn(true).when(mSimRecords).getRecordsLoaded();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        doReturn(true).when(mSST).getDesiredPowerState();
        doReturn(true).when(mSST).getPowerStateFromCarrier();
        doAnswer(
                new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        mOnSubscriptionsChangedListener =
                                (SubscriptionManager.OnSubscriptionsChangedListener)
                                        invocation.getArguments()[0];
                        return null;
                    }
                }
        ).when(mSubscriptionManager).addOnSubscriptionsChangedListener(any());
        doReturn(mSubscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());

        doReturn(1).when(mIsub).getDefaultDataSubId();
        doReturn(mIsub).when(mBinder).queryLocalInterface(anyString());
        mServiceManagerMockedServices.put("isub", mBinder);

        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_cell_retries_per_error_code,
                new String[]{"36,2"});

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mBundle = mContextFixture.getCarrierConfigBundle();

        mSimulatedCommands.setDataCallResponse(true, createDataCallResponse());

        mDcTrackerTestHandler = new DcTrackerTestHandler(getClass().getSimpleName());
        mDcTrackerTestHandler.start();
        waitUntilReady();
        waitForMs(600);
        logd("DcTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("DcTrackerTest -tearDown");
        mDct.removeCallbacksAndMessages(null);
        mDct = null;
        mDcTrackerTestHandler.quit();
        super.tearDown();
    }

    // Create a successful data response
    public static DataCallResponse createDataCallResponse() {

        return new DataCallResponse(0, -1, 1, 2, "IP", FAKE_IFNAME,
                FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
    }

    private void verifyDataProfile(DataProfile dp, String apn, int profileId,
                                   int supportedApnTypesBitmap, int type, int bearerBitmask) {
        assertEquals(profileId, dp.profileId);
        assertEquals(apn, dp.apn);
        assertEquals("IP", dp.protocol);
        assertEquals(0, dp.authType);
        assertEquals("", dp.user);
        assertEquals("", dp.password);
        assertEquals(type, dp.type);
        assertEquals(0, dp.maxConnsTime);
        assertEquals(0, dp.maxConns);
        assertEquals(0, dp.waitTime);
        assertTrue(dp.enabled);
        assertEquals(supportedApnTypesBitmap, dp.supportedApnTypesBitmap);
        assertEquals("IP", dp.roamingProtocol);
        assertEquals(bearerBitmask, dp.bearerBitmap);
        assertEquals(0, dp.mtu);
        assertEquals("", dp.mvnoType);
        assertEquals("", dp.mvnoMatchData);
        assertFalse(dp.modemCognitive);
    }

    private void verifyDataConnected(final String apnSetting) {
        verify(mPhone, times(1)).notifyDataConnection(eq(Phone.REASON_CONNECTED),
                eq(PhoneConstants.APN_TYPE_DEFAULT));

        verify(mAlarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME), anyLong(),
                any(PendingIntent.class));

        assertEquals(apnSetting, mDct.getActiveApnString(PhoneConstants.APN_TYPE_DEFAULT));
        assertArrayEquals(new String[]{PhoneConstants.APN_TYPE_DEFAULT}, mDct.getActiveApnTypes());

        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));

        LinkProperties linkProperties = mDct.getLinkProperties(PhoneConstants.APN_TYPE_DEFAULT);
        assertEquals(FAKE_IFNAME, linkProperties.getInterfaceName());
        assertEquals(1, linkProperties.getAddresses().size());
        assertEquals(FAKE_ADDRESS, linkProperties.getAddresses().get(0).getHostAddress());
        assertEquals(1, linkProperties.getDnsServers().size());
        assertEquals(FAKE_DNS, linkProperties.getDnsServers().get(0).getHostAddress());
        assertEquals(FAKE_GATEWAY, linkProperties.getRoutes().get(0).getGateway().getHostAddress());
    }

    private boolean isDataAllowed(DataConnectionReasons dataConnectionReasons) {
        try {
            Method method = DcTracker.class.getDeclaredMethod("isDataAllowed",
                    DataConnectionReasons.class);
            method.setAccessible(true);
            return (boolean) method.invoke(mDct, dataConnectionReasons);
        } catch (Exception e) {
            fail(e.toString());
            return false;
        }
    }

    // Test the normal data call setup scenario.
    @Test
    @MediumTest
    public void testDataSetup() {

        mDct.setDataEnabled(true);

        mSimulatedCommands.setDataCallResponse(true, createDataCallResponse());

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        ArgumentCaptor<String> apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_SIM_LOADED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ATTACHED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ENABLED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_ENABLE_NEW_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.setEnabled(0, true);
        waitForMs(200);

        dataConnectionReasons = new DataConnectionReasons();
        allowed = isDataAllowed(dataConnectionReasons);
        assertTrue(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);

        verifyDataConnected(FAKE_APN1);
    }

    // Test the scenario where the first data call setup is failed, and then retry the setup later.
    @Test
    @MediumTest
    public void testDataRetry() {

        mDct.setDataEnabled(true);

        // LOST_CONNECTION(0x10004) is a non-permanent failure, so we'll retry data setup later.
        DataCallResponse dcResponse = new DataCallResponse(0x10004, -1, 1, 2, "IP", FAKE_IFNAME,
                FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        // Simulate RIL fails the data call setup
        mSimulatedCommands.setDataCallResponse(false, dcResponse);

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        ArgumentCaptor<String> apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_SIM_LOADED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ATTACHED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        apnTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mPhone, times(sNetworkAttributes.length)).notifyDataConnection(
                eq(Phone.REASON_DATA_ENABLED), apnTypeArgumentCaptor.capture(),
                eq(PhoneConstants.DataState.DISCONNECTED));

        assertEquals(sApnTypes, apnTypeArgumentCaptor.getAllValues());

        logd("Sending EVENT_ENABLE_NEW_APN");
        // APN id 0 is APN_TYPE_DEFAULT
        mDct.setEnabled(0, true);
        waitForMs(200);


        dataConnectionReasons = new DataConnectionReasons();
        allowed = isDataAllowed(dataConnectionReasons);
        assertTrue(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);

        // Make sure we never notify connected because the data call setup is supposed to fail.
        verify(mPhone, never()).notifyDataConnection(eq(Phone.REASON_CONNECTED),
                eq(PhoneConstants.APN_TYPE_DEFAULT));

        // Verify the retry manger schedule another data call setup.
        verify(mAlarmManager, times(1)).setExact(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(), any(PendingIntent.class));

        // This time we'll let RIL command succeed.
        mSimulatedCommands.setDataCallResponse(true, createDataCallResponse());

        // Simulate the timer expires.
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect.default");
        intent.putExtra("reconnect_alarm_extra_type", PhoneConstants.APN_TYPE_DEFAULT);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
        waitForMs(200);

        dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN2, 0, 5, 1, LTE_BEARER_BITMASK);

        // Verify connected with APN2 setting.
        verifyDataConnected(FAKE_APN2);
    }

    @Test
    @MediumTest
    @Ignore
    @FlakyTest
    public void testUserDisableData() throws Exception {
        //step 1: setup two DataCalls one for Metered: default, another one for Non-metered: IMS
        //set Default and MMS to be metered in the CarrierConfigManager
        boolean dataEnabled = mDct.getDataEnabled();
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});
        mDct.setEnabled(5, true);
        mDct.setEnabled(0, true);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        logd("Sending DATA_ENABLED_CMD");
        mDct.setDataEnabled(true);

        waitForMs(200);
        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);

        logd("Sending DATA_DISABLED_CMD");
        mDct.setDataEnabled(false);
        waitForMs(200);

        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.IDLE, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(PhoneConstants.APN_TYPE_IMS));

        // reset the setting at the end of this test
        mDct.setDataEnabled(dataEnabled);
        waitForMs(200);
    }

    @Test
    @MediumTest
    public void testUserDisableRoaming() throws Exception {
        //step 1: setup two DataCalls one for Metered: default, another one for Non-metered: IMS
        //step 2: set roaming disabled, data is enabled
        //step 3: under roaming service
        //step 4: only tear down metered data connections.

        //set Default and MMS to be metered in the CarrierConfigManager
        boolean roamingEnabled = mDct.getDataRoamingEnabled();
        boolean dataEnabled = mDct.getDataEnabled();

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});
        mDct.setEnabled(5, true);
        mDct.setEnabled(0, true);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        logd("Sending DATA_ENABLED_CMD");
        mDct.setDataEnabled(true);

        waitForMs(300);
        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);

        //user is in roaming
        doReturn(true).when(mServiceState).getDataRoaming();
        logd("Sending DISABLE_ROAMING_CMD");
        mDct.setDataRoamingEnabledByUser(false);
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_ROAMING_ON));
        waitForMs(200);

        // expected tear down all metered DataConnections
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.IDLE, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(PhoneConstants.APN_TYPE_IMS));

        // reset roaming settings / data enabled settings at end of this test
        mDct.setDataRoamingEnabledByUser(roamingEnabled);
        mDct.setDataEnabled(dataEnabled);
        waitForMs(200);
    }

    @Test
    @MediumTest
    public void testDataCallOnUserDisableRoaming() throws Exception {
        //step 1: mock under roaming service and user disabled roaming from settings.
        //step 2: user toggled data settings on
        //step 3: only non-metered data call is established

        boolean roamingEnabled = mDct.getDataRoamingEnabled();
        boolean dataEnabled = mDct.getDataEnabled();
        doReturn(true).when(mServiceState).getDataRoaming();

        //set Default and MMS to be metered in the CarrierConfigManager
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});
        mDct.setEnabled(5, true);
        mDct.setEnabled(0, true);

        logd("Sending DATA_ENABLED_CMD");
        mDct.setDataEnabled(true);

        logd("Sending DISABLE_ROAMING_CMD");
        mDct.setDataRoamingEnabledByUser(false);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        waitForMs(200);
        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN3, 2, 64, 0, 0);

        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.IDLE, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));
        assertEquals(DctConstants.State.CONNECTED, mDct.getState(PhoneConstants.APN_TYPE_IMS));

        // reset roaming settings / data enabled settings at end of this test
        mDct.setDataRoamingEnabledByUser(roamingEnabled);
        mDct.setDataEnabled(dataEnabled);
        waitForMs(200);
    }

    // Test the default data switch scenario.
    @Test
    @MediumTest
    public void testDDSResetAutoAttach() throws Exception {

        mDct.setDataEnabled(true);

        mContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_auto_attach_data_on_creation, true);

        mSimulatedCommands.setDataCallResponse(true, createDataCallResponse());

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean allowed = isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);

        ArgumentCaptor<Integer> intArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mUiccController, times(1)).registerForIccChanged(eq(mDct),
                intArgumentCaptor.capture(), eq(null));
        // Ideally this should send EVENT_ICC_CHANGED.
        mDct.sendMessage(mDct.obtainMessage(intArgumentCaptor.getValue(), null));
        waitForMs(100);

        verify(mSimRecords, times(1)).registerForRecordsLoaded(eq(mDct),
                intArgumentCaptor.capture(), eq(null));
        // Ideally this should send EVENT_RECORDS_LOADED.
        mDct.sendMessage(mDct.obtainMessage(intArgumentCaptor.getValue(), null));
        waitForMs(100);

        verify(mSST, times(1)).registerForDataConnectionAttached(eq(mDct),
                intArgumentCaptor.capture(), eq(null));
        // Ideally this should send EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(intArgumentCaptor.getValue(), null));
        waitForMs(200);

        NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        LocalLog l = new LocalLog(100);
        mDct.requestNetwork(nr, l);
        waitForMs(200);

        verifyDataConnected(FAKE_APN1);

        assertTrue(mDct.getAutoAttachOnCreation());
        mDct.update();
        // The auto attach flag should be reset after update
        assertFalse(mDct.getAutoAttachOnCreation());

        verify(mSST, times(1)).registerForDataConnectionDetached(eq(mDct),
                intArgumentCaptor.capture(), eq(null));
        // Ideally this should send EVENT_DATA_CONNECTION_DETACHED
        mDct.sendMessage(mDct.obtainMessage(intArgumentCaptor.getValue(), null));
        waitForMs(200);

        // Data should not be allowed since auto attach flag has been reset.
        dataConnectionReasons = new DataConnectionReasons();
        allowed = isDataAllowed(dataConnectionReasons);
        assertFalse(dataConnectionReasons.toString(), allowed);
    }

    // Test for API carrierActionSetMeteredApnsEnabled.
    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testCarrierActionSetMeteredApnsEnabled() throws Exception {
        //step 1: setup two DataCalls one for Internet and IMS
        //step 2: set data is enabled
        //step 3: cold sim is detected
        //step 4: all data connection is torn down
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT, PhoneConstants.APN_TYPE_MMS});

        boolean dataEnabled = mDct.getDataEnabled();
        mDct.setDataEnabled(true);

        mDct.setEnabled(5, true);
        mDct.setEnabled(0, true);

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(2)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());

        Message msg = mDct.obtainMessage(DctConstants.EVENT_SET_CARRIER_DATA_ENABLED);
        AsyncResult.forMessage(msg).result = false;
        mDct.sendMessage(msg);

        waitForMs(100);

        // Validate all metered data connections have been torn down
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
        assertEquals(DctConstants.State.IDLE, mDct.getState(PhoneConstants.APN_TYPE_DEFAULT));

        // Reset settings at the end of test
        mDct.setDataEnabled(dataEnabled);
        waitForMs(200);
    }

    private void initApns(String targetApn, String[] canHandleTypes) {
        doReturn(targetApn).when(mApnContext).getApnType();
        doReturn(true).when(mApnContext).isConnectable();
        ApnSetting apnSetting = createApnSetting(canHandleTypes);
        doReturn(apnSetting).when(mApnContext).getNextApnSetting();
        doReturn(apnSetting).when(mApnContext).getApnSetting();
        doReturn(mDcac).when(mApnContext).getDcAc();
        doReturn(true).when(mApnContext).isEnabled();
        doReturn(true).when(mApnContext).getDependencyMet();
        doReturn(true).when(mApnContext).isReady();
        doReturn(true).when(mApnContext).hasNoRestrictedRequests(eq(true));
    }

    // Test the emergency APN setup.
    @Test
    @SmallTest
    public void testTrySetupDataEmergencyApn() throws Exception {
        initApns(PhoneConstants.APN_TYPE_EMERGENCY, new String[]{PhoneConstants.APN_TYPE_ALL});
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), any(DataProfile.class),
                eq(false), eq(false), any(Message.class));
    }

    // Test the unmetered APN setup when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupDataUnmeteredDataDisabled() throws Exception {
        initApns(PhoneConstants.APN_TYPE_FOTA, new String[]{PhoneConstants.APN_TYPE_ALL});
        mDct.setDataEnabled(false);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), any(DataProfile.class),
                eq(false), eq(false), any(Message.class));
    }

    // Test the metered APN setup when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupMeteredDataDisabled() throws Exception {
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});
        mDct.setDataEnabled(false);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(
                anyInt(), any(DataProfile.class), eq(false), eq(false), any(Message.class));
    }

    // Test the restricted data request when data is disabled.
    @Test
    @SmallTest
    public void testTrySetupRestrictedDataDisabled() throws Exception {
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});
        doReturn(false).when(mApnContext).hasNoRestrictedRequests(eq(true));

        mDct.setDataEnabled(false);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                anyInt(), any(DataProfile.class), eq(false), eq(false), any(Message.class));
    }

    // Test the default data when data is not connectable.
    @Test
    @SmallTest
    public void testTrySetupNotConnectable() throws Exception {
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});
        doReturn(false).when(mApnContext).isConnectable();
        mDct.setDataEnabled(true);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(
                anyInt(), any(DataProfile.class), eq(false), eq(false), any(Message.class));
    }

    // Test the default data on IWLAN.
    @Test
    @SmallTest
    public void testTrySetupDefaultOnIWLAN() throws Exception {
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN).when(mServiceState)
                .getRilDataRadioTechnology();
        mDct.setDataEnabled(true);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(
                anyInt(), any(DataProfile.class), eq(false), eq(false), any(Message.class));
    }

    // Test the default data when the phone is in ECBM.
    @Test
    @SmallTest
    public void testTrySetupDefaultInECBM() throws Exception {
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});
        doReturn(true).when(mPhone).isInEcm();
        mDct.setDataEnabled(true);

        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, mApnContext));
        waitForMs(200);

        verify(mSimulatedCommandsVerifier, times(0)).setupDataCall(
                anyInt(), any(DataProfile.class), eq(false), eq(false), any(Message.class));
    }

    // Test update waiting apn list when on data rat change
    @Test
    @SmallTest
    public void testUpdateWaitingApnListOnDataRatChange() throws Exception {
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD).when(mServiceState)
                .getRilDataRadioTechnology();
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});
        mDct.setEnabled(0, true);
        mDct.setDataEnabled(true);
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN4, 0, 5, 2, EHRPD_BEARER_BITMASK);
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());

        //data rat change from ehrpd to lte
        logd("Sending EVENT_DATA_RAT_CHANGED");
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_LTE).when(mServiceState)
                .getRilDataRadioTechnology();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForMs(200);

        // Verify the disconnected data call due to rat change and retry manger schedule another
        // data call setup
        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));
        verify(mAlarmManager, times(1)).setExact(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(), any(PendingIntent.class));

        // Simulate the timer expires.
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect.default");
        intent.putExtra("reconnect_alarm_extra_type", PhoneConstants.APN_TYPE_DEFAULT);
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
        waitForMs(200);

        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN1, 0, 5, 1, LTE_BEARER_BITMASK);
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
    }

    // Test for fetchDunApn()
    @Test
    @SmallTest
    public void testFetchDunApn() {
        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        String dunApnString = "[ApnSettingV3]HOT mobile PC,pc.hotm,,,,,,,,,440,10,,DUN,,,true,"
                + "0,,,,,,,,";
        ApnSetting dunApnExpected = ApnSetting.fromString(dunApnString);

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, dunApnString);
        // should return APN from Setting
        ApnSetting dunApn = mDct.fetchDunApn();
        assertTrue(dunApnExpected.equals(dunApn));

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_APN, null);
        // should return APN from db
        dunApn = mDct.fetchDunApn();
        assertEquals(FAKE_APN5, dunApn.apn);
    }

    // Test oos
    @Test
    @SmallTest
    public void testDataRatChangeOOS() throws Exception {
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD).when(mServiceState)
                .getRilDataRadioTechnology();
        mBundle.putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{PhoneConstants.APN_TYPE_DEFAULT});
        mDct.setEnabled(0, true);
        mDct.setDataEnabled(true);
        initApns(PhoneConstants.APN_TYPE_DEFAULT, new String[]{PhoneConstants.APN_TYPE_ALL});

        logd("Sending EVENT_RECORDS_LOADED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_RECORDS_LOADED, null));
        waitForMs(200);

        logd("Sending EVENT_DATA_CONNECTION_ATTACHED");
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null));
        waitForMs(200);

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        // Verify if RIL command was sent properly.
        verify(mSimulatedCommandsVerifier).setupDataCall(
                eq(mServiceState.getRilDataRadioTechnology()), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));
        verifyDataProfile(dpCaptor.getValue(), FAKE_APN4, 0, 5, 2, EHRPD_BEARER_BITMASK);
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());

        // Data rat change from ehrpd to unknown due to OOS
        logd("Sending EVENT_DATA_RAT_CHANGED");
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN).when(mServiceState)
                .getRilDataRadioTechnology();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForMs(200);

        // Verify data connection is on
        verify(mSimulatedCommandsVerifier, times(0)).deactivateDataCall(anyInt(), anyInt(),
                any(Message.class));

        // Data rat resume from unknown to ehrpd
        doReturn(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD).when(mServiceState)
                .getRilDataRadioTechnology();
        mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_DATA_RAT_CHANGED, null));
        waitForMs(200);

        // Verify the same data connection
        assertEquals(FAKE_APN4, mDct.getActiveApnString(PhoneConstants.APN_TYPE_DEFAULT));
        assertEquals(DctConstants.State.CONNECTED, mDct.getOverallState());
    }
}
