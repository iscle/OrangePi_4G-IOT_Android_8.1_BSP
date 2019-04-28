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

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.IApplicationThread;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.WorkSource;
import android.preference.PreferenceManager;
import android.support.test.filters.FlakyTest;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;

public class GsmCdmaPhoneTest extends TelephonyTest {
    @Mock
    private Handler mTestHandler;

    //mPhoneUnderTest
    private GsmCdmaPhone mPhoneUT;
    private GsmCdmaPhoneTestHandler mGsmCdmaPhoneTestHandler;

    private static final int EVENT_EMERGENCY_CALLBACK_MODE_EXIT = 1;
    private static final int EVENT_EMERGENCY_CALL_TOGGLE = 2;

    private class GsmCdmaPhoneTestHandler extends HandlerThread {

        private GsmCdmaPhoneTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mPhoneUT = new GsmCdmaPhone(mContext, mSimulatedCommands, mNotifier, true, 0,
                    PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory);
            setReady(true);
        }
    }

    private void switchToGsm() {
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_GSM);
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_GSM}, null)));
        //wait for voice RAT to be updated
        waitForMs(50);
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());
    }

    private void switchToCdma() {
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_IS95A);
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_VOICE_RADIO_TECH_CHANGED,
                new AsyncResult(null, new int[]{ServiceState.RIL_RADIO_TECHNOLOGY_IS95A}, null)));
        //wait for voice RAT to be updated
        waitForMs(100);
        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhoneUT.getPhoneType());
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        doReturn(false).when(mSST).isDeviceShuttingDown();

        mGsmCdmaPhoneTestHandler = new GsmCdmaPhoneTestHandler(TAG);
        mGsmCdmaPhoneTestHandler.start();
        waitUntilReady();
        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mUiccController).registerForIccChanged(eq(mPhoneUT), integerArgumentCaptor.capture(),
                nullable(Object.class));
        Message msg = Message.obtain();
        msg.what = integerArgumentCaptor.getValue();
        mPhoneUT.sendMessage(msg);
        waitForMs(50);
    }

    @After
    public void tearDown() throws Exception {
        mPhoneUT.removeCallbacksAndMessages(null);
        mPhoneUT = null;
        mGsmCdmaPhoneTestHandler.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testPhoneTypeSwitch() {
        assertTrue(mPhoneUT.isPhoneTypeGsm());
        switchToCdma();
        assertTrue(mPhoneUT.isPhoneTypeCdmaLte());
    }

    @Test
    @SmallTest
    public void testHandleActionCarrierConfigChanged() {
        // set voice radio tech in RIL to 1xRTT. ACTION_CARRIER_CONFIG_CHANGED should trigger a
        // query and change phone type
        mSimulatedCommands.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        assertTrue(mPhoneUT.isPhoneTypeGsm());
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.sendBroadcast(intent);
        waitForMs(50);
        assertTrue(mPhoneUT.isPhoneTypeCdmaLte());
    }

    @Test
    @SmallTest
    public void testGetServiceState() {
        ServiceState serviceState = new ServiceState();
        mSST.mSS = serviceState;
        assertEquals(serviceState, mPhoneUT.getServiceState());
    }

    @Test
    @SmallTest
    public void testGetCellLocation() {
        // GSM
        CellLocation cellLocation = new GsmCellLocation();
        WorkSource workSource = new WorkSource(Process.myUid(),
            mContext.getPackageName());
        doReturn(cellLocation).when(mSST).getCellLocation(workSource);
        assertEquals(cellLocation, mPhoneUT.getCellLocation(workSource));

        // Switch to CDMA
        switchToCdma();

        CdmaCellLocation cdmaCellLocation = new CdmaCellLocation();
        cdmaCellLocation.setCellLocationData(0, 0, 0, 0, 0);
        mSST.mCellLoc = cdmaCellLocation;

        /*
        LOCATION_MODE is a special case in SettingsProvider. Adding the special handling in mock
        content provider is probably not worth the effort; it will also tightly couple tests with
        SettingsProvider implementation.
        // LOCATION_MODE_ON
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
        waitForMs(50);
        CdmaCellLocation actualCellLocation = (CdmaCellLocation) mPhoneUT.getCellLocation();
        assertEquals(0, actualCellLocation.getBaseStationLatitude());
        assertEquals(0, actualCellLocation.getBaseStationLongitude());

        // LOCATION_MODE_OFF
        Settings.Secure.putInt(TestApplication.getAppContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        waitForMs(50);
        */

        CdmaCellLocation actualCellLocation =
                (CdmaCellLocation) mPhoneUT.getCellLocation(workSource);
        assertEquals(CdmaCellLocation.INVALID_LAT_LONG,
                actualCellLocation.getBaseStationLatitude());
        assertEquals(CdmaCellLocation.INVALID_LAT_LONG,
                actualCellLocation.getBaseStationLongitude());
    }

    @Test
    @SmallTest
    public void testGetPhoneType() {
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());

        // Switch to CDMA
        switchToCdma();

        assertEquals(PhoneConstants.PHONE_TYPE_CDMA, mPhoneUT.getPhoneType());
    }

    @Test
    @SmallTest
    public void testGetDataConnectionState() {
        // There are several cases possible. Testing few of them for now.
        // 1. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn != APN_TYPE_EMERGENCY
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mSST).getCurrentDataConnectionState();
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_ALL));

        // 2. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY, apn
        // not connected
        doReturn(DctConstants.State.IDLE).when(mDcTracker).getState(
                PhoneConstants.APN_TYPE_EMERGENCY);
        assertEquals(PhoneConstants.DataState.DISCONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN is connected, callTracker state = idle
        doReturn(DctConstants.State.CONNECTED).when(mDcTracker).getState(
                PhoneConstants.APN_TYPE_EMERGENCY);
        mCT.mState = PhoneConstants.State.IDLE;
        assertEquals(PhoneConstants.DataState.CONNECTED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));

        // 3. GSM, getCurrentDataConnectionState != STATE_IN_SERVICE, apn = APN_TYPE_EMERGENCY,
        // APN enabled and CONNECTED, callTracker state != idle, !isConcurrentVoiceAndDataAllowed
        mCT.mState = PhoneConstants.State.RINGING;
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        assertEquals(PhoneConstants.DataState.SUSPENDED, mPhoneUT.getDataConnectionState(
                PhoneConstants.APN_TYPE_EMERGENCY));
    }

    @Test
    @SmallTest
    public void testHandleInCallMmiCommands() {
        try {
            // Switch to CDMA
            switchToCdma();

            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // Switch to GSM
            switchToGsm();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            // !isInCall
            assertFalse(mPhoneUT.handleInCallMmiCommands("0"));

            // isInCall
            doReturn(GsmCdmaCall.State.ACTIVE).when(mGsmCdmaCall).getState();
            assertTrue(mPhoneUT.handleInCallMmiCommands("0"));

            // empty dialString
            assertFalse(mPhoneUT.handleInCallMmiCommands(""));
            assertFalse(mPhoneUT.handleInCallMmiCommands(null));

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    @SmallTest
    public void testDial() {
        try {
            mSST.mSS = mServiceState;
            doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();

            mCT.mForegroundCall = mGsmCdmaCall;
            mCT.mBackgroundCall = mGsmCdmaCall;
            mCT.mRingingCall = mGsmCdmaCall;
            doReturn(GsmCdmaCall.State.IDLE).when(mGsmCdmaCall).getState();

            Connection connection = mPhoneUT.dial("1234567890", 0);
            verify(mCT).dial("1234567890", null, null);
        } catch (CallStateException e) {
            fail();
        }
    }

    @Test
    @SmallTest
    public void testHandlePinMmi() {
        assertFalse(mPhoneUT.handlePinMmi("1234567890"));
    }

    @Test
    @SmallTest
    public void testSendBurstDtmf() {
        //Should do nothing for GSM
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        switchToCdma();
        //invalid character
        mPhoneUT.sendBurstDtmf("12345a67890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        //state IDLE
        mCT.mState = PhoneConstants.State.IDLE;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        //state RINGING
        mCT.mState = PhoneConstants.State.RINGING;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).sendBurstDtmf(nullable(String.class), anyInt(),
                anyInt(), nullable(Message.class));

        mCT.mState = PhoneConstants.State.OFFHOOK;
        mPhoneUT.sendBurstDtmf("1234567890", 0, 0, null);
        verify(mSimulatedCommandsVerifier).sendBurstDtmf("1234567890", 0, 0, null);
    }

    @Test
    @SmallTest
    public void testVoiceMailNumberGsm() {
        String voiceMailNumber = "1234567890";
        // first test for GSM
        assertEquals(PhoneConstants.PHONE_TYPE_GSM, mPhoneUT.getPhoneType());

        // no resource or sharedPreference set -- should be null
        assertEquals(null, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config
        mContextFixture.getCarrierConfigBundle().
                putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_STRING, voiceMailNumber);
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number that is explicitly set
        voiceMailNumber = "1234567891";
        mPhoneUT.setVoiceMailNumber("alphaTag", voiceMailNumber, null);
        verify(mSimRecords).setVoiceMailNumber(eq("alphaTag"), eq(voiceMailNumber),
                nullable(Message.class));

        doReturn(voiceMailNumber).when(mSimRecords).getVoiceMailNumber();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
    }

    @Test
    @SmallTest
    public void testVoiceMailNumberCdma() {
        switchToCdma();
        String voiceMailNumber = "1234567890";

        // no resource or sharedPreference set -- should be *86
        assertEquals("*86", mPhoneUT.getVoiceMailNumber());

        // config_telephony_use_own_number_for_voicemail
        mContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_telephony_use_own_number_for_voicemail, true);
        doReturn(voiceMailNumber).when(mSST).getMdnNumber();
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from config
        voiceMailNumber = "1234567891";
        mContextFixture.getCarrierConfigBundle().
                putString(CarrierConfigManager.KEY_DEFAULT_VM_NUMBER_STRING, voiceMailNumber);
        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());

        // voicemail number from sharedPreference
        mPhoneUT.setVoiceMailNumber("alphaTag", voiceMailNumber, null);
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mRuimRecords).setVoiceMailNumber(eq("alphaTag"), eq(voiceMailNumber),
                messageArgumentCaptor.capture());

        Message msg = messageArgumentCaptor.getValue();
        AsyncResult.forMessage(msg).exception =
                new IccException("setVoiceMailNumber not implemented");
        msg.sendToTarget();
        waitForMs(50);

        assertEquals(voiceMailNumber, mPhoneUT.getVoiceMailNumber());
    }

    @FlakyTest
    @Test
    @Ignore
    public void testVoiceMailCount() {
        // initial value
        assertEquals(0, mPhoneUT.getVoiceMessageCount());

        // old sharedPreference set (testing upgrade from M to N scenario)
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String imsi = "1234567890";
        editor.putString("vm_id_key", imsi);
        editor.putInt("vm_count_key", 5);
        editor.apply();
        doReturn(imsi).when(mSimRecords).getIMSI();

        // updateVoiceMail should read old shared pref and delete it and new sharedPref should be
        // updated now
        doReturn(-1).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(5, mPhoneUT.getVoiceMessageCount());
        assertEquals(null, sharedPreferences.getString("vm_id_key", null));
        assertEquals(5, sharedPreferences.getInt("vm_count_key" + mPhoneUT.getSubId(), 0));

        // sim records return count as 0, that overrides shared preference
        doReturn(0).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(0, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1
        doReturn(-1).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.updateVoiceMail();
        assertEquals(-1, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1 and sharedPreference says 0
        mPhoneUT.setVoiceMessageCount(0);
        mPhoneUT.updateVoiceMail();
        assertEquals(-1, mPhoneUT.getVoiceMessageCount());

        // sim records return count as -1 and sharedPreference says 2
        mPhoneUT.setVoiceMessageCount(2);
        mPhoneUT.updateVoiceMail();
        assertEquals(2, mPhoneUT.getVoiceMessageCount());

        // sim records return count as 0 and sharedPreference says 2
        doReturn(0).when(mSimRecords).getVoiceMessageCount();
        mPhoneUT.setVoiceMessageCount(2);
        mPhoneUT.updateVoiceMail();
        assertEquals(0, mPhoneUT.getVoiceMessageCount());
    }

    @Test
    @SmallTest
    public void testGetCallForwardingOption() {
        // invalid reason (-1)
        mPhoneUT.getCallForwardingOption(-1, null);
        verify(mSimulatedCommandsVerifier, times(0)).queryCallForwardStatus(
                anyInt(), anyInt(), nullable(String.class), nullable(Message.class));

        // valid reason
        String imsi = "1234567890";
        doReturn(imsi).when(mSimRecords).getIMSI();
        mPhoneUT.getCallForwardingOption(CF_REASON_UNCONDITIONAL, null);
        verify(mSimulatedCommandsVerifier).queryCallForwardStatus(
                eq(CF_REASON_UNCONDITIONAL), anyInt(), nullable(String.class),
                nullable(Message.class));
        waitForMs(50);
        verify(mSimRecords).setVoiceCallForwardingFlag(anyInt(), anyBoolean(),
                nullable(String.class));

        // should have updated shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.
                getDefaultSharedPreferences(mContext);
        assertEquals(IccRecords.CALL_FORWARDING_STATUS_DISABLED,
                sharedPreferences.getInt(Phone.CF_STATUS + mPhoneUT.getSubId(),
                        IccRecords.CALL_FORWARDING_STATUS_ENABLED));

        // clean up
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(Phone.CF_STATUS + mPhoneUT.getSubId());
        editor.apply();
    }

    @Test
    @SmallTest
    public void testSetCallForwardingOption() {
        String cfNumber = "1234567890";

        // invalid action
        mPhoneUT.setCallForwardingOption(-1, CF_REASON_UNCONDITIONAL,
                cfNumber, 0, null);
        verify(mSimulatedCommandsVerifier, times(0)).setCallForward(anyInt(), anyInt(), anyInt(),
                nullable(String.class), anyInt(), nullable(Message.class));

        // valid action
        mPhoneUT.setCallForwardingOption(CF_ACTION_ENABLE, CF_REASON_UNCONDITIONAL, cfNumber, 0,
                null);
        verify(mSimulatedCommandsVerifier).setCallForward(eq(CF_ACTION_ENABLE),
                eq(CF_REASON_UNCONDITIONAL), anyInt(), eq(cfNumber), eq(0),
                nullable(Message.class));
        waitForMs(50);
        verify(mSimRecords).setVoiceCallForwardingFlag(anyInt(), anyBoolean(), eq(cfNumber));
    }

    /**
     * GsmCdmaPhone handles a lot of messages. This function verifies behavior for messages that are
     * received when obj is created and that are received on phone type switch
     */
    @FlakyTest
    @Ignore
    @Test
    @SmallTest
    public void testHandleInitialMessages() {
        // EVENT_RADIO_AVAILABLE
        verify(mSimulatedCommandsVerifier).getBasebandVersion(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).getDeviceIdentity(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).getRadioCapability(nullable(Message.class));
        // once as part of constructor, and once on radio available
        verify(mSimulatedCommandsVerifier, times(2)).startLceService(anyInt(), anyBoolean(),
                nullable(Message.class));

        // EVENT_RADIO_ON
        verify(mSimulatedCommandsVerifier).getVoiceRadioTechnology(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).setPreferredNetworkType(
                eq(RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA), nullable(Message.class));

        // verify responses for above requests:
        // baseband version
        verify(mTelephonyManager).setBasebandVersionForPhone(eq(mPhoneUT.getPhoneId()),
                nullable(String.class));
        // IMEI
        assertEquals(SimulatedCommands.FAKE_IMEI, mPhoneUT.getImei());
        // IMEISV
        assertEquals(SimulatedCommands.FAKE_IMEISV, mPhoneUT.getDeviceSvn());
        // radio capability
        verify(mSimulatedCommandsVerifier).getNetworkSelectionMode(nullable(Message.class));

        switchToCdma(); // this leads to eventRadioAvailable handling on cdma

        // EVENT_RADIO_AVAILABLE
        verify(mSimulatedCommandsVerifier, times(2)).getBasebandVersion(nullable(Message.class));
        verify(mSimulatedCommandsVerifier, times(2)).getDeviceIdentity(nullable(Message.class));
        verify(mSimulatedCommandsVerifier, times(3)).startLceService(anyInt(), anyBoolean(),
                nullable(Message.class));

        // EVENT_RADIO_ON
        verify(mSimulatedCommandsVerifier, times(2)).getVoiceRadioTechnology(
                nullable(Message.class));
        // once on radio on, and once on get baseband version
        verify(mSimulatedCommandsVerifier, times(3)).setPreferredNetworkType(
                eq(RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA), nullable(Message.class));

        // verify responses for above requests:
        // baseband version
        verify(mTelephonyManager, times(2)).setBasebandVersionForPhone(eq(mPhoneUT.getPhoneId()),
                nullable(String.class));
        // device identity
        assertEquals(SimulatedCommands.FAKE_IMEI, mPhoneUT.getImei());
        assertEquals(SimulatedCommands.FAKE_IMEISV, mPhoneUT.getDeviceSvn());
        assertEquals(SimulatedCommands.FAKE_ESN, mPhoneUT.getEsn());
        assertEquals(SimulatedCommands.FAKE_MEID, mPhoneUT.getMeid());
    }

    @Test
    @SmallTest
    public void testEmergencyCallbackMessages() {
        verify(mSimulatedCommandsVerifier).setEmergencyCallbackMode(eq(mPhoneUT), anyInt(),
                nullable(Object.class));
        verify(mSimulatedCommandsVerifier).registerForExitEmergencyCallbackMode(eq(mPhoneUT),
                anyInt(), nullable(Object.class));

        // verify handling of emergency callback mode
        mSimulatedCommands.notifyEmergencyCallbackMode();
        waitForMs(50);

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        try {
            verify(mIActivityManager, atLeast(1)).broadcastIntent(eq((IApplicationThread) null),
                    intentArgumentCaptor.capture(),
                    eq((String) null),
                    eq((IIntentReceiver) null),
                    eq(Activity.RESULT_OK),
                    eq((String) null),
                    eq((Bundle) null),
                    eq((String[]) null),
                    anyInt(),
                    eq((Bundle) null),
                    eq(false),
                    eq(true),
                    anyInt());
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(true, intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, false));
        assertEquals(true, mPhoneUT.isInEcm());

        // verify that wakeLock is acquired in ECM
        assertEquals(true, mPhoneUT.getWakeLock().isHeld());

        mPhoneUT.setOnEcbModeExitResponse(mTestHandler, EVENT_EMERGENCY_CALLBACK_MODE_EXIT, null);
        mPhoneUT.registerForEmergencyCallToggle(mTestHandler, EVENT_EMERGENCY_CALL_TOGGLE, null);

        // verify handling of emergency callback mode exit
        mSimulatedCommands.notifyExitEmergencyCallbackMode();
        waitForMs(50);

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        try {
            verify(mIActivityManager, atLeast(2)).broadcastIntent(eq((IApplicationThread) null),
                    intentArgumentCaptor.capture(),
                    eq((String) null),
                    eq((IIntentReceiver) null),
                    eq(Activity.RESULT_OK),
                    eq((String) null),
                    eq((Bundle) null),
                    eq((String[]) null),
                    anyInt(),
                    eq((Bundle) null),
                    eq(false),
                    eq(true),
                    anyInt());
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(false, intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, true));
        assertEquals(false, mPhoneUT.isInEcm());

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // verify EcmExitRespRegistrant and mEmergencyCallToggledRegistrants are notified
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        List<Message> msgList = messageArgumentCaptor.getAllValues();
        assertEquals(EVENT_EMERGENCY_CALLBACK_MODE_EXIT, msgList.get(0).what);
        assertEquals(EVENT_EMERGENCY_CALL_TOGGLE, msgList.get(1).what);

        // verify setInternalDataEnabled
        verify(mDcTracker).setInternalDataEnabled(true);

        // verify wakeLock released
        assertEquals(false, mPhoneUT.getWakeLock().isHeld());
    }

    @Test
    @SmallTest
    public void testModemResetInEmergencyCallbackMessages() {
        verify(mSimulatedCommandsVerifier).setEmergencyCallbackMode(eq(mPhoneUT), anyInt(),
                nullable(Object.class));
        verify(mSimulatedCommandsVerifier).registerForModemReset(eq(mPhoneUT),
                anyInt(), nullable(Object.class));

        switchToCdma();
        // verify handling of emergency callback mode
        mSimulatedCommands.notifyEmergencyCallbackMode();
        waitForMs(50);

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        try {
            verify(mIActivityManager, atLeast(1)).broadcastIntent(eq((IApplicationThread) null),
                    intentArgumentCaptor.capture(),
                    eq((String) null),
                    eq((IIntentReceiver) null),
                    eq(Activity.RESULT_OK),
                    eq((String) null),
                    eq((Bundle) null),
                    eq((String[]) null),
                    anyInt(),
                    eq((Bundle) null),
                    eq(false),
                    eq(true),
                    anyInt());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(true, intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, false));
        assertEquals(true, mPhoneUT.isInEcm());

        // verify that wakeLock is acquired in ECM
        assertEquals(true, mPhoneUT.getWakeLock().isHeld());

        mPhoneUT.setOnEcbModeExitResponse(mTestHandler, EVENT_EMERGENCY_CALLBACK_MODE_EXIT, null);
        mPhoneUT.registerForEmergencyCallToggle(mTestHandler, EVENT_EMERGENCY_CALL_TOGGLE, null);

        // verify handling of emergency callback mode exit when modem resets
        mSimulatedCommands.notifyModemReset();
        waitForMs(50);

        // verify ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
        try {
            verify(mIActivityManager, atLeast(2)).broadcastIntent(eq((IApplicationThread) null),
                    intentArgumentCaptor.capture(),
                    eq((String) null),
                    eq((IIntentReceiver) null),
                    eq(Activity.RESULT_OK),
                    eq((String) null),
                    eq((Bundle) null),
                    eq((String[]) null),
                    anyInt(),
                    eq((Bundle) null),
                    eq(false),
                    eq(true),
                    anyInt());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        intent = intentArgumentCaptor.getValue();
        assertEquals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, intent.getAction());
        assertEquals(false, intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, true));
        assertEquals(false, mPhoneUT.isInEcm());

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        // verify EcmExitRespRegistrant and mEmergencyCallToggledRegistrants are notified
        verify(mTestHandler, times(2)).sendMessageAtTime(messageArgumentCaptor.capture(),
                anyLong());
        List<Message> msgList = messageArgumentCaptor.getAllValues();
        assertEquals(EVENT_EMERGENCY_CALLBACK_MODE_EXIT, msgList.get(0).what);
        assertEquals(EVENT_EMERGENCY_CALL_TOGGLE, msgList.get(1).what);

        // verify setInternalDataEnabled
        verify(mDcTracker).setInternalDataEnabled(true);

        // verify wakeLock released
        assertEquals(false, mPhoneUT.getWakeLock().isHeld());
    }

    @Test
    @SmallTest
    public void testCallForwardingIndicator() {
        doReturn(IccRecords.CALL_FORWARDING_STATUS_UNKNOWN).when(mSimRecords).
                getVoiceCallForwardingFlag();

        // invalid subId
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionController).
                getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // valid subId, sharedPreference not present
        int subId1 = 0;
        int subId2 = 1;
        doReturn(subId1).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // old sharedPreference present
        String imsi = "1234";
        doReturn(imsi).when(mSimRecords).getIMSI();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(Phone.CF_ID, imsi);
        editor.putInt(Phone.CF_STATUS, IccRecords.CALL_FORWARDING_STATUS_ENABLED);
        editor.apply();
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // old sharedPreference should be removed now
        assertEquals(null, sp.getString(Phone.CF_ID, null));
        assertEquals(IccRecords.CALL_FORWARDING_STATUS_UNKNOWN,
                sp.getInt(Phone.CF_ID, IccRecords.CALL_FORWARDING_STATUS_UNKNOWN));

        // now verify value from new sharedPreference
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // check for another subId
        doReturn(subId2).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(false, mPhoneUT.getCallForwardingIndicator());

        // set value for the new subId in sharedPreference
        editor.putInt(Phone.CF_STATUS + subId2, IccRecords.CALL_FORWARDING_STATUS_ENABLED);
        editor.apply();
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // switching back to previous subId, stored value should still be available
        doReturn(subId1).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());
        assertEquals(true, mPhoneUT.getCallForwardingIndicator());

        // cleanup
        editor.remove(Phone.CF_STATUS + subId1);
        editor.remove(Phone.CF_STATUS + subId2);
        editor.apply();
    }

    @Test
    @SmallTest
    public void testEriLoading() {
        mPhoneUT.mEriManager = mEriManager;
        mPhoneUT.sendMessage(mPhoneUT.obtainMessage(GsmCdmaPhone.EVENT_CARRIER_CONFIG_CHANGED,
                null));
        waitForMs(100);
        verify(mEriManager, times(1)).loadEriFile();
    }
}
