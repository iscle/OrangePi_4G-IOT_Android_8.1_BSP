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
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_ADDRESS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_DNS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_GATEWAY;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_IFNAME;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_PCSCF_ADDRESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.dataconnection.DataConnection.ConnectionParams;
import com.android.internal.telephony.dataconnection.DataConnection.DisconnectParams;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DataConnectionTest extends TelephonyTest {

    @Mock
    DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    @Mock
    ConnectionParams mCp;
    @Mock
    DisconnectParams mDcp;
    @Mock
    ApnContext mApnContext;
    @Mock
    DcFailBringUp mDcFailBringUp;

    private DataConnection mDc;
    private DataConnectionTestHandler mDataConnectionTestHandler;
    private DcController mDcc;

    private ApnSetting mApn1 = new ApnSetting(
            2163,                   // id
            "44010",                // numeric
            "sp-mode",              // name
            "spmode.ne.jp",         // apn
            "",                     // proxy
            "",                     // port
            "",                     // mmsc
            "",                     // mmsproxy
            "",                     // mmsport
            "",                     // user
            "",                     // password
            -1,                     // authtype
            new String[]{"default", "supl"},     // types
            "IP",                   // protocol
            "IP",                   // roaming_protocol
            true,                   // carrier_enabled
            0,                      // bearer
            0,                      // bearer_bitmask
            0,                      // profile_id
            false,                  // modem_cognitive
            0,                      // max_conns
            0,                      // wait_time
            0,                      // max_conns_time
            0,                      // mtu
            "",                     // mvno_type
            "");                    // mnvo_match_data

    private class DataConnectionTestHandler extends HandlerThread {

        private DataConnectionTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            Handler h = new Handler();

            mDcc = DcController.makeDcc(mPhone, mDcTracker, h);
            mDc = DataConnection.makeDataConnection(mPhone, 0, mDcTracker, mDcTesterFailBringUpAll,
                    mDcc);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("+Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn("fake.action_detached").when(mPhone).getActionDetached();
        replaceInstance(ConnectionParams.class, "mApnContext", mCp, mApnContext);
        replaceInstance(ConnectionParams.class, "mRilRat", mCp,
                ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);
        doReturn(mApn1).when(mApnContext).getApnSetting();
        doReturn(PhoneConstants.APN_TYPE_DEFAULT).when(mApnContext).getApnType();

        mDcFailBringUp.saveParameters(0, 0, -2);
        doReturn(mDcFailBringUp).when(mDcTesterFailBringUpAll).getDcFailBringUp();

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


        mDcp.mApnContext = mApnContext;

        mDataConnectionTestHandler = new DataConnectionTestHandler(getClass().getSimpleName());
        mDataConnectionTestHandler.start();

        waitForMs(200);
        logd("-Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        mDc = null;
        mDcc = null;
        mDataConnectionTestHandler.quit();
        super.tearDown();
    }

    private IState getCurrentState() throws Exception {
        Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
        method.setAccessible(true);
        return (IState) method.invoke(mDc);
    }

    private long getSuggestedRetryDelay(AsyncResult ar) throws Exception {
        Class[] cArgs = new Class[1];
        cArgs[0] = AsyncResult.class;
        Method method = DataConnection.class.getDeclaredMethod("getSuggestedRetryDelay", cArgs);
        method.setAccessible(true);
        return (long) method.invoke(mDc, ar);
    }

    @Test
    @SmallTest
    public void testSanity() throws Exception {
        assertEquals("DcInactiveState", getCurrentState().getName());
    }

    @Test
    @SmallTest
    public void testConnectEvent() throws Exception {
        testSanity();

        mDc.sendMessage(DataConnection.EVENT_CONNECT, mCp);
        waitForMs(200);

        verify(mCT, times(1)).registerForVoiceCallStarted(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_STARTED), eq(null));
        verify(mCT, times(1)).registerForVoiceCallEnded(any(Handler.class),
                eq(DataConnection.EVENT_DATA_CONNECTION_VOICE_CALL_ENDED), eq(null));

        ArgumentCaptor<DataProfile> dpCaptor = ArgumentCaptor.forClass(DataProfile.class);
        verify(mSimulatedCommandsVerifier, times(1)).setupDataCall(
                eq(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS), dpCaptor.capture(),
                eq(false), eq(false), any(Message.class));

        assertEquals("spmode.ne.jp", dpCaptor.getValue().apn);

        assertEquals("DcActiveState", getCurrentState().getName());
    }

    @Test
    @SmallTest
    public void testDisconnectEvent() throws Exception {
        testConnectEvent();

        mDc.sendMessage(DataConnection.EVENT_DISCONNECT, mDcp);
        waitForMs(100);

        verify(mSimulatedCommandsVerifier, times(1)).deactivateDataCall(eq(1),
                eq(RILConstants.DEACTIVATE_REASON_NONE), any(Message.class));

        assertEquals("DcInactiveState", getCurrentState().getName());
    }

    @Test
    @SmallTest
    public void testModemSuggestRetry() throws Exception {
        DataCallResponse response = new DataCallResponse(0, 0, 1, 2, "IP",
                FAKE_IFNAME, FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        AsyncResult ar = new AsyncResult(null, response, null);
        assertEquals(response.suggestedRetryTime, getSuggestedRetryDelay(ar));

        response = new DataCallResponse(0, 1000, 1, 2, "IP",
                FAKE_IFNAME, FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        ar = new AsyncResult(null, response, null);
        assertEquals(response.suggestedRetryTime, getSuggestedRetryDelay(ar));

        response = new DataCallResponse(0, 9999, 1, 2, "IP",
                FAKE_IFNAME, FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        ar = new AsyncResult(null, response, null);
        assertEquals(response.suggestedRetryTime, getSuggestedRetryDelay(ar));
    }

    @Test
    @SmallTest
    public void testModemNotSuggestRetry() throws Exception {
        DataCallResponse response = new DataCallResponse(0, -1, 1, 2, "IP", FAKE_IFNAME,
                FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        AsyncResult ar = new AsyncResult(null, response, null);
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(ar));

        response = new DataCallResponse(0, -5, 1, 2, "IP", FAKE_IFNAME,
                FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        ar = new AsyncResult(null, response, null);
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(ar));

        response = new DataCallResponse(0, Integer.MIN_VALUE, 1, 2, "IP", FAKE_IFNAME,
                FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        ar = new AsyncResult(null, response, null);
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY, getSuggestedRetryDelay(ar));
    }

    @Test
    @SmallTest
    public void testModemSuggestNoRetry() throws Exception {
        DataCallResponse response = new DataCallResponse(0, Integer.MAX_VALUE, 1, 2, "IP",
                FAKE_IFNAME, FAKE_ADDRESS, FAKE_DNS, FAKE_GATEWAY, FAKE_PCSCF_ADDRESS, 1440);
        AsyncResult ar = new AsyncResult(null, response, null);
        assertEquals(RetryManager.NO_RETRY, getSuggestedRetryDelay(ar));
    }

    private NetworkInfo getNetworkInfo() throws Exception {
        Field f = DataConnection.class.getDeclaredField("mNetworkInfo");
        f.setAccessible(true);
        return (NetworkInfo) f.get(mDc);
    }

    private NetworkCapabilities getNetworkCapabilities() throws Exception {
        Method method = DataConnection.class.getDeclaredMethod("getNetworkCapabilities");
        method.setAccessible(true);
        return (NetworkCapabilities) method.invoke(mDc);
    }

    @Test
    @SmallTest
    public void testMeteredCapability() throws Exception {

        mContextFixture.getCarrierConfigBundle().
                putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] {"default"});

        testConnectEvent();

        assertFalse(getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
    }

    @Test
    @SmallTest
    public void testNonMeteredCapability() throws Exception {

        doReturn(2819).when(mPhone).getSubId();
        mContextFixture.getCarrierConfigBundle().
                putStringArray(CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                        new String[] {"mms"});

        testConnectEvent();

        assertTrue(getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
    }
}