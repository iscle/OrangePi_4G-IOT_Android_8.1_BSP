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

package com.android.internal.telephony;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.IAlarmManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.V1_0.CellIdentityGsm;
import android.hardware.radio.V1_0.CellInfoType;
import android.hardware.radio.V1_0.VoiceRegStateResult;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.WorkSource;
import android.support.test.filters.FlakyTest;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ServiceStateTrackerTest extends TelephonyTest {

    @Mock
    private DcTracker mDct;
    @Mock
    private ProxyController mProxyController;
    @Mock
    private Handler mTestHandler;
    @Mock
    protected IAlarmManager mAlarmManager;

    private ServiceStateTracker sst;
    private ServiceStateTrackerTestHandler mSSTTestHandler;
    private PersistableBundle mBundle;

    private static final int EVENT_REGISTERED_TO_NETWORK = 1;
    private static final int EVENT_SUBSCRIPTION_INFO_READY = 2;
    private static final int EVENT_DATA_ROAMING_ON = 3;
    private static final int EVENT_DATA_ROAMING_OFF = 4;
    private static final int EVENT_DATA_CONNECTION_ATTACHED = 5;
    private static final int EVENT_DATA_CONNECTION_DETACHED = 6;
    private static final int EVENT_DATA_RAT_CHANGED = 7;
    private static final int EVENT_PS_RESTRICT_ENABLED = 8;
    private static final int EVENT_PS_RESTRICT_DISABLED = 9;
    private static final int EVENT_VOICE_ROAMING_ON = 10;
    private static final int EVENT_VOICE_ROAMING_OFF = 11;

    private class ServiceStateTrackerTestHandler extends HandlerThread {

        private ServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            sst = new ServiceStateTracker(mPhone, mSimulatedCommands);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {

        logd("ServiceStateTrackerTest +Setup!");
        super.setUp("ServiceStateTrackerTest");

        doReturn(true).when(mDct).isDisconnected();
        mPhone.mDcTracker = mDct;

        replaceInstance(ProxyController.class, "sProxyController", null, mProxyController);
        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putStringArray(
                CarrierConfigManager.KEY_ROAMING_OPERATOR_STRING_ARRAY, new String[]{"123456"});

        mBundle.putStringArray(
                CarrierConfigManager.KEY_NON_ROAMING_OPERATOR_STRING_ARRAY, new String[]{"123456"});

        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA);

        int dds = SubscriptionManager.getDefaultDataSubscriptionId();
        doReturn(dds).when(mPhone).getSubId();

        mSSTTestHandler = new ServiceStateTrackerTestHandler(getClass().getSimpleName());
        mSSTTestHandler.start();
        waitUntilReady();
        waitForMs(600);
        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        sst = null;
        mSSTTestHandler.quit();
        super.tearDown();
    }

    @Test
    @MediumTest
    public void testSetRadioPower() {
        boolean oldState = mSimulatedCommands.getRadioState().isOn();
        sst.setRadioPower(!oldState);
        waitForMs(100);
        assertTrue(oldState != mSimulatedCommands.getRadioState().isOn());
    }

    @Test
    @MediumTest
    public void testSetRadioPowerFromCarrier() {
        // Carrier disable radio power
        sst.setRadioPowerFromCarrier(false);
        waitForMs(100);
        assertFalse(mSimulatedCommands.getRadioState().isOn());
        assertTrue(sst.getDesiredPowerState());
        assertFalse(sst.getPowerStateFromCarrier());

        // User toggle radio power will not overrides carrier settings
        sst.setRadioPower(true);
        waitForMs(100);
        assertFalse(mSimulatedCommands.getRadioState().isOn());
        assertTrue(sst.getDesiredPowerState());
        assertFalse(sst.getPowerStateFromCarrier());

        // Carrier re-enable radio power
        sst.setRadioPowerFromCarrier(true);
        waitForMs(100);
        assertTrue(mSimulatedCommands.getRadioState().isOn());
        assertTrue(sst.getDesiredPowerState());
        assertTrue(sst.getPowerStateFromCarrier());

        // User toggle radio power off (airplane mode) and set carrier on
        sst.setRadioPower(false);
        sst.setRadioPowerFromCarrier(true);
        waitForMs(100);
        assertFalse(mSimulatedCommands.getRadioState().isOn());
        assertFalse(sst.getDesiredPowerState());
        assertTrue(sst.getPowerStateFromCarrier());
    }

    @Test
    @MediumTest
    public void testRilTrafficAfterSetRadioPower() {
        sst.setRadioPower(true);
        final int getOperatorCallCount = mSimulatedCommands.getGetOperatorCallCount();
        final int getDataRegistrationStateCallCount =
                mSimulatedCommands.getGetDataRegistrationStateCallCount();
        final int getVoiceRegistrationStateCallCount =
                mSimulatedCommands.getGetVoiceRegistrationStateCallCount();
        final int getNetworkSelectionModeCallCount =
                mSimulatedCommands.getGetNetworkSelectionModeCallCount();
        sst.setRadioPower(false);

        waitForMs(500);
        sst.pollState();
        waitForMs(250);

        // This test was meant to be for *no* ril traffic. However, RADIO_STATE_CHANGED is
        // considered a modem triggered action and that causes a pollState() to be done
        assertEquals(getOperatorCallCount + 1, mSimulatedCommands.getGetOperatorCallCount());
        assertEquals(getDataRegistrationStateCallCount + 1,
                mSimulatedCommands.getGetDataRegistrationStateCallCount());
        assertEquals(getVoiceRegistrationStateCallCount + 1,
                mSimulatedCommands.getGetVoiceRegistrationStateCallCount());
        assertEquals(getNetworkSelectionModeCallCount + 1,
                mSimulatedCommands.getGetNetworkSelectionModeCallCount());

        // Note that if the poll is triggered by a network change notification
        // and the modem is supposed to be off, we should still do the poll
        mSimulatedCommands.notifyNetworkStateChanged();
        waitForMs(250);

        assertEquals(getOperatorCallCount + 2 , mSimulatedCommands.getGetOperatorCallCount());
        assertEquals(getDataRegistrationStateCallCount + 2,
                mSimulatedCommands.getGetDataRegistrationStateCallCount());
        assertEquals(getVoiceRegistrationStateCallCount + 2,
                mSimulatedCommands.getGetVoiceRegistrationStateCallCount());
        assertEquals(getNetworkSelectionModeCallCount + 2,
                mSimulatedCommands.getGetNetworkSelectionModeCallCount());
    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testSpnUpdateShowPlmnOnly() {
        doReturn(0x02).when(mSimRecords).getDisplayRule(anyString());
        doReturn(IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN).
                when(mUiccCardApplication3gpp).getState();

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_NETWORK_STATE_CHANGED, null));

        waitForMs(750);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble(), times(3))
                .sendStickyBroadcastAsUser(intentArgumentCaptor.capture(), eq(UserHandle.ALL));

        // We only want to verify the intent SPN_STRINGS_UPDATED_ACTION.
        List<Intent> intents = intentArgumentCaptor.getAllValues();
        logd("Total " + intents.size() + " intents");
        for (Intent intent : intents) {
            logd("  " + intent.getAction());
        }
        Intent intent = intents.get(2);
        assertEquals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION, intent.getAction());

        Bundle b = intent.getExtras();

        // For boolean we need to make sure the key exists first
        assertTrue(b.containsKey(TelephonyIntents.EXTRA_SHOW_SPN));
        assertFalse(b.getBoolean(TelephonyIntents.EXTRA_SHOW_SPN));

        assertEquals(null, b.getString(TelephonyIntents.EXTRA_SPN));
        assertEquals(null, b.getString(TelephonyIntents.EXTRA_DATA_SPN));

        // For boolean we need to make sure the key exists first
        assertTrue(b.containsKey(TelephonyIntents.EXTRA_SHOW_PLMN));
        assertTrue(b.getBoolean(TelephonyIntents.EXTRA_SHOW_PLMN));

        assertEquals(SimulatedCommands.FAKE_LONG_NAME, b.getString(TelephonyIntents.EXTRA_PLMN));

        ArgumentCaptor<Integer> intArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mTelephonyManager).setDataNetworkTypeForPhone(anyInt(), intArgumentCaptor.capture());
        assertEquals(ServiceState.RIL_RADIO_TECHNOLOGY_HSPA,
                intArgumentCaptor.getValue().intValue());
    }

    @Test
    @MediumTest
    public void testCellInfoList() {
        Parcel p = Parcel.obtain();
        p.writeInt(1);
        p.writeInt(1);
        p.writeInt(2);
        p.writeLong(1453510289108L);
        p.writeInt(310);
        p.writeInt(260);
        p.writeInt(123);
        p.writeInt(456);
        p.writeInt(99);
        p.writeInt(3);
        p.setDataPosition(0);

        CellInfoGsm cellInfo = CellInfoGsm.CREATOR.createFromParcel(p);

        ArrayList<CellInfo> list = new ArrayList();
        list.add(cellInfo);
        mSimulatedCommands.setCellInfoList(list);

        WorkSource workSource = new WorkSource(Process.myUid(),
                mContext.getPackageName());
        assertEquals(sst.getAllCellInfo(workSource), list);
    }

    @Test
    @MediumTest
    public void testImsRegState() {
        // Simulate IMS registered
        mSimulatedCommands.setImsRegistrationState(new int[]{1, PhoneConstants.PHONE_TYPE_GSM});

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_IMS_STATE_CHANGED, null));
        waitForMs(200);

        assertTrue(sst.isImsRegistered());

        // Simulate IMS unregistered
        mSimulatedCommands.setImsRegistrationState(new int[]{0, PhoneConstants.PHONE_TYPE_GSM});

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_IMS_STATE_CHANGED, null));
        waitForMs(200);

        assertFalse(sst.isImsRegistered());
    }

    @Test
    @MediumTest
    public void testSignalStrength() {
        SignalStrength ss = new SignalStrength(
                30, // gsmSignalStrength
                0,  // gsmBitErrorRate
                -1, // cdmaDbm
                -1, // cdmaEcio
                -1, // evdoDbm
                -1, // evdoEcio
                -1, // evdoSnr
                99, // lteSignalStrength
                SignalStrength.INVALID,     // lteRsrp
                SignalStrength.INVALID,     // lteRsrq
                SignalStrength.INVALID,     // lteRssnr
                SignalStrength.INVALID,     // lteCqi
                SignalStrength.INVALID,     // tdScdmaRscp
                true                        // gsmFlag
        );

        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        waitForMs(300);
        assertEquals(sst.getSignalStrength(), ss);
        assertEquals(sst.getSignalStrength().isGsm(), true);

        // switch to CDMA
        doReturn(false).when(mPhone).isPhoneTypeGsm();
        doReturn(true).when(mPhone).isPhoneTypeCdmaLte();
        sst.updatePhoneType();
        sst.mSS.setRilDataRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        mSimulatedCommands.notifySignalStrength();
        waitForMs(300);
        assertEquals(sst.getSignalStrength(), ss);
        assertEquals(sst.getSignalStrength().isGsm(), true);

        // notify signal strength again, but this time data RAT is not LTE
        sst.mSS.setRilVoiceRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        sst.mSS.setRilDataRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD);
        mSimulatedCommands.notifySignalStrength();
        waitForMs(300);
        assertEquals(sst.getSignalStrength(), ss);
        assertEquals(sst.getSignalStrength().isGsm(), false);
    }

    @Test
    @MediumTest
    public void testGsmCellLocation() {

        VoiceRegStateResult result = new VoiceRegStateResult();
        result.cellIdentity.cellInfoType = CellInfoType.GSM;
        result.cellIdentity.cellIdentityGsm.add(new CellIdentityGsm());
        result.cellIdentity.cellIdentityGsm.get(0).lac = 2;
        result.cellIdentity.cellIdentityGsm.get(0).cid = 3;

        sst.sendMessage(sst.obtainMessage(ServiceStateTracker.EVENT_GET_LOC_DONE,
                new AsyncResult(null, result, null)));

        waitForMs(200);
        WorkSource workSource = new WorkSource(Process.myUid(), mContext.getPackageName());
        GsmCellLocation cl = (GsmCellLocation) sst.getCellLocation(workSource);
        assertEquals(2, cl.getLac());
        assertEquals(3, cl.getCid());
    }

    @Test
    @MediumTest
    public void testUpdatePhoneType() {
        doReturn(false).when(mPhone).isPhoneTypeGsm();
        doReturn(true).when(mPhone).isPhoneTypeCdmaLte();
        doReturn(CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM).when(mCdmaSSM).
                getCdmaSubscriptionSource();

        logd("Calling updatePhoneType");
        // switch to CDMA
        sst.updatePhoneType();

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mRuimRecords).registerForRecordsLoaded(eq(sst), integerArgumentCaptor.capture(),
                nullable(Object.class));

        // response for mRuimRecords.registerForRecordsLoaded()
        Message msg = Message.obtain();
        msg.what = integerArgumentCaptor.getValue();
        msg.obj = new AsyncResult(null, null, null);
        sst.sendMessage(msg);
        waitForMs(100);

        // on RUIM_RECORDS_LOADED, sst is expected to call following apis
        verify(mRuimRecords, times(1)).isProvisioned();

        // switch back to GSM
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        doReturn(false).when(mPhone).isPhoneTypeCdmaLte();

        // response for mRuimRecords.registerForRecordsLoaded() can be sent after switching to GSM
        msg = Message.obtain();
        msg.what = integerArgumentCaptor.getValue();
        msg.obj = new AsyncResult(null, null, null);
        sst.sendMessage(msg);

        // There's no easy way to check if the msg was handled or discarded. Wait to make sure sst
        // did not crash, and then verify that the functions called records loaded are not called
        // again
        waitForMs(200);

        verify(mRuimRecords, times(1)).isProvisioned();
    }

    @Test
    @MediumTest
    public void testRegAndUnregForVoiceRoamingOn() throws Exception {
        sst.registerForVoiceRoamingOn(mTestHandler, EVENT_DATA_ROAMING_ON, null);

        // Enable roaming and trigger events to notify handler registered
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_ROAMING_ON, messageArgumentCaptor.getValue().what);

        // Disable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForVoiceRoamingOn(mTestHandler);

        // Enable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndUnregForVoiceRoamingOff() throws Exception {
        // Enable roaming
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForVoiceRoamingOff(mTestHandler, EVENT_DATA_ROAMING_OFF, null);

        // Disable roaming
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_ROAMING_OFF, messageArgumentCaptor.getValue().what);

        // Enable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForVoiceRoamingOff(mTestHandler);

        // Disable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndUnregForDataRoamingOn() throws Exception {
        sst.registerForDataRoamingOn(mTestHandler, EVENT_DATA_ROAMING_ON, null);

        // Enable roaming and trigger events to notify handler registered
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_ROAMING_ON, messageArgumentCaptor.getValue().what);

        // Disable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForDataRoamingOn(mTestHandler);

        // Enable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndUnregForDataRoamingOff() throws Exception {
        // Enable roaming
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForDataRoamingOff(mTestHandler, EVENT_DATA_ROAMING_OFF, null, true);

        // Disable roaming
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_ROAMING_OFF, messageArgumentCaptor.getValue().what);

        // Enable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForDataRoamingOff(mTestHandler);

        // Disable roaming
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_HOME);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndInvalidregForDataConnAttach() throws Exception {
        // Initially set service state out of service
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(23);
        mSimulatedCommands.setDataRegState(23);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForDataConnectionAttached(mTestHandler, EVENT_DATA_CONNECTION_ATTACHED, null);

        // set service state in service and trigger events to post message on handler
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_CONNECTION_ATTACHED, messageArgumentCaptor.getValue().what);

        // set service state out of service
        mSimulatedCommands.setVoiceRegState(-1);
        mSimulatedCommands.setDataRegState(-1);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForDataConnectionAttached(mTestHandler);

        // set service state in service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndUnregForDataConnAttach() throws Exception {
        // Initially set service state out of service
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForDataConnectionAttached(mTestHandler, EVENT_DATA_CONNECTION_ATTACHED, null);

        // set service state in service and trigger events to post message on handler
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_CONNECTION_ATTACHED, messageArgumentCaptor.getValue().what);

        // set service state out of service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForDataConnectionAttached(mTestHandler);

        // set service state in service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndUnregForDataConnDetach() throws Exception {
        // Initially set service state in service
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        sst.registerForDataConnectionDetached(mTestHandler, EVENT_DATA_CONNECTION_DETACHED, null);

        // set service state out of service and trigger events to post message on handler
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_CONNECTION_DETACHED, messageArgumentCaptor.getValue().what);

        // set service state in service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForDataConnectionDetached(mTestHandler);

        // set service state out of service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegisterForDataRegStateOrRatChange() {
        int drs = sst.mSS.RIL_REG_STATE_HOME;
        int rat = sst.mSS.RIL_RADIO_TECHNOLOGY_LTE;
        sst.mSS.setRilDataRadioTechnology(rat);
        sst.mSS.setDataRegState(drs);
        sst.registerForDataRegStateOrRatChanged(mTestHandler, EVENT_DATA_RAT_CHANGED, null);

        waitForMs(100);

        // Verify if message was posted to handler and value of result
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_DATA_RAT_CHANGED, messageArgumentCaptor.getValue().what);
        assertEquals(new Pair<Integer, Integer>(drs, rat),
                ((AsyncResult)messageArgumentCaptor.getValue().obj).result);
    }

    @Test
    @MediumTest
    public void testRegAndUnregForNetworkAttached() throws Exception {
        // Initially set service state out of service
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForNetworkAttached(mTestHandler, EVENT_REGISTERED_TO_NETWORK, null);

        // set service state in service and trigger events to post message on handler
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_REGISTERED_TO_NETWORK, messageArgumentCaptor.getValue().what);

        // set service state out of service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_UNKNOWN);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForNetworkAttached(mTestHandler);

        // set service state in service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify that no new message posted to handler
        verify(mTestHandler, times(1)).sendMessageAtTime(any(Message.class), anyLong());
    }

    @Test
    @MediumTest
    public void testRegAndInvalidRegForNetworkAttached() throws Exception {
        // Initially set service state out of service
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mSimulatedCommands.setVoiceRegState(23);
        mSimulatedCommands.setDataRegState(23);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        sst.registerForNetworkAttached(mTestHandler, EVENT_REGISTERED_TO_NETWORK, null);

        // set service state in service and trigger events to post message on handler
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_REGISTERED_TO_NETWORK, messageArgumentCaptor.getValue().what);

        // set service state out of service
        mSimulatedCommands.setVoiceRegState(-1);
        mSimulatedCommands.setDataRegState(-1);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // Unregister registrant
        sst.unregisterForNetworkAttached(mTestHandler);


        waitForMs(100);

        sst.registerForNetworkAttached(mTestHandler, EVENT_REGISTERED_TO_NETWORK, null);

        // set service state in service
        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(100);

        // verify if registered handler has message posted to it
        messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        assertEquals(EVENT_REGISTERED_TO_NETWORK, messageArgumentCaptor.getValue().what);
    }

    @Test
    @MediumTest
    public void testRegisterForPsRestrictedEnabled() throws Exception {
        sst.mRestrictedState.setPsRestricted(true);
        // Since PsRestricted is set to true, registerForPsRestrictedEnabled will
        // also post message to handler
        sst.registerForPsRestrictedEnabled(mTestHandler, EVENT_PS_RESTRICT_ENABLED, null);

        waitForMs(100);

        // verify posted message
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_PS_RESTRICT_ENABLED, messageArgumentCaptor.getValue().what);
    }

    @Test
    @MediumTest
    public void testRegisterForPsRestrictedDisabled() throws Exception {
        sst.mRestrictedState.setPsRestricted(true);
        // Since PsRestricted is set to true, registerForPsRestrictedDisabled will
        // also post message to handler
        sst.registerForPsRestrictedDisabled(mTestHandler, EVENT_PS_RESTRICT_DISABLED, null);

        waitForMs(100);

        // verify posted message
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_PS_RESTRICT_DISABLED, messageArgumentCaptor.getValue().what);
    }

    @Test
    @MediumTest
    public void testOnRestrictedStateChanged() throws Exception {
        ServiceStateTracker spySst = spy(sst);
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        doReturn(IccCardApplicationStatus.AppState.APPSTATE_READY).when(
                mUiccCardApplication3gpp).getState();

        ArgumentCaptor<Integer> intArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mSimulatedCommandsVerifier).setOnRestrictedStateChanged(any(Handler.class),
                intArgumentCaptor.capture(), eq(null));
        // Since spy() creates a copy of sst object we need to call
        // setOnRestrictedStateChanged() explicitly.
        mSimulatedCommands.setOnRestrictedStateChanged(spySst,
                intArgumentCaptor.getValue().intValue(), null);

        // Combination of restricted state and expected notification type.
        final int CS_ALL[] = {RILConstants.RIL_RESTRICTED_STATE_CS_ALL,
                ServiceStateTracker.CS_ENABLED};
        final int CS_NOR[] = {RILConstants.RIL_RESTRICTED_STATE_CS_NORMAL,
                ServiceStateTracker.CS_NORMAL_ENABLED};
        final int CS_EME[] = {RILConstants.RIL_RESTRICTED_STATE_CS_EMERGENCY,
                ServiceStateTracker.CS_EMERGENCY_ENABLED};
        final int CS_NON[] = {RILConstants.RIL_RESTRICTED_STATE_NONE,
                ServiceStateTracker.CS_DISABLED};
        final int PS_ALL[] = {RILConstants.RIL_RESTRICTED_STATE_PS_ALL,
                ServiceStateTracker.PS_ENABLED};
        final int PS_NON[] = {RILConstants.RIL_RESTRICTED_STATE_NONE,
                ServiceStateTracker.PS_DISABLED};

        int notifyCount = 0;
        // cs not restricted -> cs emergency/normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_ALL);
        // cs emergency/normal restricted -> cs normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NOR);
        // cs normal restricted -> cs emergency restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_EME);
        // cs emergency restricted -> cs not restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NON);
        // cs not restricted -> cs normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NOR);
        // cs normal restricted -> cs emergency/normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_ALL);
        // cs emergency/normal restricted -> cs emergency restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_EME);
        // cs emergency restricted -> cs emergency/normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_ALL);
        // cs emergency/normal restricted -> cs not restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NON);
        // cs not restricted -> cs emergency restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_EME);
        // cs emergency restricted -> cs normal restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NOR);
        // cs normal restricted -> cs not restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, CS_NON);

        // ps not restricted -> ps restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, PS_ALL);
        // ps restricted -> ps not restricted
        internalCheckForRestrictedStateChange(spySst, ++notifyCount, PS_NON);
    }

    private void internalCheckForRestrictedStateChange(ServiceStateTracker serviceStateTracker,
                int times, int[] restrictedState) {
        mSimulatedCommands.triggerRestrictedStateChanged(restrictedState[0]);
        waitForMs(100);
        ArgumentCaptor<Integer> intArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(serviceStateTracker, times(times)).setNotification(intArgumentCaptor.capture());
        assertEquals(intArgumentCaptor.getValue().intValue(), restrictedState[1]);
    }

    @Test
    @MediumTest
    public void testRegisterForSubscriptionInfoReady() {
        sst.registerForSubscriptionInfoReady(mTestHandler, EVENT_SUBSCRIPTION_INFO_READY, null);

        // Call functions which would trigger posting of message on test handler
        doReturn(false).when(mPhone).isPhoneTypeGsm();
        sst.updatePhoneType();
        mSimulatedCommands.notifyOtaProvisionStatusChanged();

        waitForMs(200);

        // verify posted message
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler).sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_SUBSCRIPTION_INFO_READY, messageArgumentCaptor.getValue().what);
    }

    @Test
    @MediumTest
    public void testRoamingPhoneTypeSwitch() {
        // Enable roaming
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        mSimulatedCommands.setVoiceRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.setDataRegState(ServiceState.RIL_REG_STATE_ROAMING);
        mSimulatedCommands.notifyNetworkStateChanged();

        waitForMs(200);

        sst.registerForDataRoamingOff(mTestHandler, EVENT_DATA_ROAMING_OFF, null, true);
        sst.registerForVoiceRoamingOff(mTestHandler, EVENT_VOICE_ROAMING_OFF, null);
        sst.registerForDataConnectionDetached(mTestHandler, EVENT_DATA_CONNECTION_DETACHED, null);

        // Call functions which would trigger posting of message on test handler
        doReturn(false).when(mPhone).isPhoneTypeGsm();
        sst.updatePhoneType();

        // verify if registered handler has message posted to it
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, atLeast(3)).sendMessageAtTime(
                messageArgumentCaptor.capture(), anyLong());
        HashSet<Integer> messageSet = new HashSet<>();
        for (Message m : messageArgumentCaptor.getAllValues()) {
            messageSet.add(m.what);
        }

        assertTrue(messageSet.contains(EVENT_DATA_ROAMING_OFF));
        assertTrue(messageSet.contains(EVENT_VOICE_ROAMING_OFF));
        assertTrue(messageSet.contains(EVENT_DATA_CONNECTION_DETACHED));
    }

    @Test
    @SmallTest
    public void testGetDesiredPowerState() {
        sst.setRadioPower(true);
        assertEquals(sst.getDesiredPowerState(), true);
    }

    @Test
    @MediumTest
    public void testEnableLocationUpdates() throws Exception {
        sst.enableLocationUpdates();
        verify(mSimulatedCommandsVerifier, times(1)).setLocationUpdates(eq(true),
                any(Message.class));
    }

    @Test
    @SmallTest
    public void testDisableLocationUpdates() throws Exception {
        sst.disableLocationUpdates();
        verify(mSimulatedCommandsVerifier, times(1)).setLocationUpdates(eq(false),
                nullable(Message.class));
    }

    @Test
    @SmallTest
    public void testGetCurrentDataRegState() throws Exception {
        sst.mSS.setDataRegState(ServiceState.STATE_OUT_OF_SERVICE);
        assertEquals(sst.getCurrentDataConnectionState(), ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    @SmallTest
    public void testIsConcurrentVoiceAndDataAllowed() {
        // Verify all 3 branches in the function isConcurrentVoiceAndDataAllowed
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        sst.mSS.setRilVoiceRadioTechnology(sst.mSS.RIL_RADIO_TECHNOLOGY_HSPA);
        assertEquals(true, sst.isConcurrentVoiceAndDataAllowed());

        doReturn(false).when(mPhone).isPhoneTypeGsm();
        doReturn(true).when(mPhone).isPhoneTypeCdma();
        assertEquals(false, sst.isConcurrentVoiceAndDataAllowed());

        doReturn(false).when(mPhone).isPhoneTypeGsm();
        doReturn(false).when(mPhone).isPhoneTypeCdma();
        sst.mSS.setCssIndicator(1);
        assertEquals(true, sst.isConcurrentVoiceAndDataAllowed());
        sst.mSS.setCssIndicator(0);
        assertEquals(false, sst.isConcurrentVoiceAndDataAllowed());
    }

    @Test
    @MediumTest
    public void testIsImsRegistered() throws Exception {
        mSimulatedCommands.setImsRegistrationState(new int[]{1, PhoneConstants.PHONE_TYPE_GSM});
        mSimulatedCommands.notifyImsNetworkStateChanged();
        waitForMs(200);
        assertEquals(sst.isImsRegistered(), true);
    }

    @Test
    @SmallTest
    public void testIsDeviceShuttingDown() throws Exception {
        sst.requestShutdown();
        assertEquals(true, sst.isDeviceShuttingDown());
    }

    @Test
    @SmallTest
    public void testSetTimeFromNITZStr() throws Exception {
        doReturn(mAlarmManager).when(mIBinder).queryLocalInterface(anyString());
        mServiceManagerMockedServices.put(Context.ALARM_SERVICE, mIBinder);

        // Mock sending incorrect nitz str from RIL
        mSimulatedCommands.triggerNITZupdate("38/06/20,00:00:00+0");
        waitForMs(200);
        // AlarmManger.setTime is triggered by SystemClock.setCurrentTimeMillis().
        // Verify system time is not set to incorrect NITZ time
        verify(mAlarmManager, times(0)).setTime(anyLong());

        // Mock sending correct nitz str from RIL
        mSimulatedCommands.triggerNITZupdate("15/06/20,00:00:00+0");
        waitForMs(200);
        verify(mAlarmManager, times(1)).setTime(anyLong());
    }
}
