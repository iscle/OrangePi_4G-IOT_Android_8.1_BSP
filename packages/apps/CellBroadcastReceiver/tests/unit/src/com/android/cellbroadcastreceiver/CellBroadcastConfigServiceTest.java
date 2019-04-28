/**
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

package com.android.cellbroadcastreceiver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Method;

/**
 * Cell broadcast config service tests
 */
public class CellBroadcastConfigServiceTest extends CellBroadcastTest {

    @Mock
    ISms.Stub mMockedSmsService;

    @Mock
    SharedPreferences mMockedSharedPreferences;

    private CellBroadcastConfigService mConfigService;

    private SmsManager mSmsManager = SmsManager.getDefault();

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mConfigService = spy(new CellBroadcastConfigService());

        Class[] cArgs = new Class[1];
        cArgs[0] = Context.class;

        Method method = ContextWrapper.class.getDeclaredMethod("attachBaseContext", cArgs);
        method.setAccessible(true);
        method.invoke(mConfigService, mContext);

        doReturn(mMockedSharedPreferences).when(mContext)
                .getSharedPreferences(anyString(), anyInt());

        mMockedServiceManager.replaceService("isms", mMockedSmsService);
        doReturn(mMockedSmsService).when(mMockedSmsService).queryLocalInterface(anyString());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private boolean setCellBroadcastRange(boolean enable, int type, int start, int end)
            throws Exception {

        Class[] cArgs = new Class[5];
        cArgs[0] = SmsManager.class;
        cArgs[1] = Boolean.TYPE;
        cArgs[2] = cArgs[3] = cArgs[4] = Integer.TYPE;

        Method method =
                CellBroadcastConfigService.class.getDeclaredMethod("setCellBroadcastRange", cArgs);
        method.setAccessible(true);

        return (boolean) method.invoke(mConfigService, mSmsManager, enable, type, start, end);
    }

    /**
     * Test enable cell broadcast range
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRange() throws Exception {
        setCellBroadcastRange(true, 0, 10, 20);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(0, captorType.getValue().intValue());
    }

    /**
     * Test disable cell broadcast range
     */
    @Test
    @SmallTest
    public void testDisableCellBroadcastRange() throws Exception {
        setCellBroadcastRange(false, 0, 10, 20);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(0, captorType.getValue().intValue());
    }

    private void setPreference(String pref, boolean value) {
        doReturn(value).when(mMockedSharedPreferences).getBoolean(eq(pref), eq(true));
    }

    /**
     * Test enabling channels for default countries (US)
     */
    @Test
    @SmallTest
    public void testEnablingChannelsDefault() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));


        // GSM
        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));
    }

    /**
     * Test enabling channels for Presidential alert
     */
    @Test
    @SmallTest
    public void testEnablingPresidential() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, false);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, false);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

    }

    /**
     * Test enabling channels for extreme alert
     */
    @Test
    @SmallTest
    public void testEnablingExtreme() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, false);

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

    }

    /**
     * Test enabling channels for severe alert
     */
    @Test
    @SmallTest
    public void testEnablingSevere() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, false);

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));
    }

    /**
     * Test enabling channels for amber alert
     */
    @Test
    @SmallTest
    public void testEnablingAmber() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, false);

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));
    }

    /**
     * Test enabling channels for ETWS alert
     */
    @Test
    @SmallTest
    public void testEnablingETWS() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, false);
        mConfigService.setCellBroadcastOnSub(mSmsManager, true);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mConfigService.setCellBroadcastOnSub(mSmsManager, false);

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsManager.CELL_BROADCAST_RAN_TYPE_GSM));
    }
}