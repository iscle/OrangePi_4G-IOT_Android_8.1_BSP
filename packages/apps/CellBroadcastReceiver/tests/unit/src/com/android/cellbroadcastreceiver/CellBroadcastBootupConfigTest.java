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
 * limitations under the License
 */

package com.android.cellbroadcastreceiver;

import android.content.Intent;

import com.android.internal.telephony.ISms;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static android.telephony.SmsManager.CELL_BROADCAST_RAN_TYPE_CDMA;
import static android.telephony.SmsManager.CELL_BROADCAST_RAN_TYPE_GSM;
import static com.android.internal.telephony.cdma.sms.SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY;
import static com.android.internal.telephony.cdma.sms.SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT;
import static com.android.internal.telephony.cdma.sms.SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT;
import static com.android.internal.telephony.cdma.sms.SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING;
import static com.android.internal.telephony.gsm.SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CellBroadcastBootupConfigTest extends
        CellBroadcastServiceTestCase<CellBroadcastConfigService> {

    @Mock
    ISms.Stub mSmsService;

    @Captor
    private ArgumentCaptor<Integer> mStartIds;

    @Captor
    private ArgumentCaptor<Integer> mEndIds;

    @Captor
    private ArgumentCaptor<Integer> mTypes;

    private MockedServiceManager mMockedServiceManager;

    public CellBroadcastBootupConfigTest() {
        super(CellBroadcastConfigService.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        doReturn(mSmsService).when(mSmsService).queryLocalInterface(anyString());
        mMockedServiceManager = new MockedServiceManager();
        mMockedServiceManager.replaceService("isms", mSmsService);
    }

    @After
    public void tearDown() throws Exception {
        mMockedServiceManager.restoreAllServices();
        super.tearDown();
    }

    private static class CbConfig {
        final int startId;
        final int endId;
        final int type;

        CbConfig(int startId, int endId, int type) {
            this.startId = startId;
            this.endId = endId;
            this.type = type;
        }
    }

    // Test if CellbroadcastConfigService properly configure all the required channels.
    public void testConfiguration() throws Exception {
        Intent intent = new Intent(mContext, CellBroadcastConfigService.class);
        intent.setAction(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS);

        startService(intent);
        waitForMs(200);

        CbConfig[] configs = new CbConfig[] {
                new CbConfig(SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT,
                        CELL_BROADCAST_RAN_TYPE_CDMA),
                new CbConfig(SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        SERVICE_CATEGORY_CMAS_EXTREME_THREAT,
                        CELL_BROADCAST_RAN_TYPE_CDMA),
                new CbConfig(SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        SERVICE_CATEGORY_CMAS_SEVERE_THREAT,
                        CELL_BROADCAST_RAN_TYPE_CDMA),
                new CbConfig(SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY,
                        CELL_BROADCAST_RAN_TYPE_CDMA),
                new CbConfig(MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                        MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                        MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                        MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE,
                        CELL_BROADCAST_RAN_TYPE_GSM),
                new CbConfig(MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        CELL_BROADCAST_RAN_TYPE_GSM),
        };

        verify(mSmsService, times(configs.length)).enableCellBroadcastRangeForSubscriber(anyInt(),
                mStartIds.capture(), mEndIds.capture(), mTypes.capture());

        for (int i = 0; i < configs.length; i++) {
            assertEquals("i=" + i, configs[i].startId, mStartIds.getAllValues().get(i).intValue());
            assertEquals("i=" + i, configs[i].endId, mEndIds.getAllValues().get(i).intValue());
            assertEquals("i=" + i, configs[i].type, mTypes.getAllValues().get(i).intValue());
        }
     }
}