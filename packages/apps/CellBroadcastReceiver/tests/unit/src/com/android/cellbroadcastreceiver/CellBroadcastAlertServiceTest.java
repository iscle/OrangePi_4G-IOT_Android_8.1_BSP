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

import static com.android.cellbroadcastreceiver.CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE;
import static com.android.cellbroadcastreceiver.CellBroadcastAlertService.SHOW_NEW_ALERT_ACTION;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.content.Intent;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.CellBroadcastMessage;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;

import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;

public class CellBroadcastAlertServiceTest extends
        CellBroadcastServiceTestCase<CellBroadcastAlertService> {

    public CellBroadcastAlertServiceTest() {
        super(CellBroadcastAlertService.class);
    }

    static SmsCbMessage createMessage(int serialNumber) {
        return new SmsCbMessage(1, 2, serialNumber, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(1, 2, 3, 4, 5, 6));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private static void compareEtwsWarningInfo(SmsCbEtwsInfo info1, SmsCbEtwsInfo info2) {
        if (info1 == info2) return;
        assertEquals(info1.toString(), info2.toString());
        assertArrayEquals(info1.getPrimaryNotificationSignature(),
                info2.getPrimaryNotificationSignature());
        assertEquals(info1.isPrimary(), info2.isPrimary());
    }

    private static void compareCmasWarningInfo(SmsCbCmasInfo info1, SmsCbCmasInfo info2) {
        if (info1 == info2) return;
        assertEquals(info1.getCategory(), info2.getCategory());
        assertEquals(info1.getCertainty(), info2.getCertainty());
        assertEquals(info1.getMessageClass(), info2.getMessageClass());
        assertEquals(info1.getResponseType(), info2.getResponseType());
        assertEquals(info1.getSeverity(), info2.getSeverity());
        assertEquals(info1.getUrgency(), info2.getUrgency());
    }

    private static void compareCellBroadCastMessage(CellBroadcastMessage cbm1,
                                                    CellBroadcastMessage cbm2) {
        if (cbm1 == cbm2) return;
        assertEquals(cbm1.getCmasMessageClass(), cbm2.getCmasMessageClass());
        compareCmasWarningInfo(cbm1.getCmasWarningInfo(), cbm2.getCmasWarningInfo());
        compareEtwsWarningInfo(cbm1.getEtwsWarningInfo(), cbm2.getEtwsWarningInfo());
        assertEquals(cbm1.getLanguageCode(), cbm2.getLanguageCode());
        assertEquals(cbm1.getMessageBody(), cbm2.getMessageBody());
        assertEquals(cbm1.getSerialNumber(), cbm2.getSerialNumber());
        assertEquals(cbm1.getServiceCategory(), cbm2.getServiceCategory());
        assertEquals(cbm1.getSubId(), cbm2.getSubId());
        assertEquals(cbm1.getSerialNumber(), cbm2.getSerialNumber());
    }

    private void sendMessage(int serialNumber) {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);

        SmsCbMessage m = createMessage(serialNumber);
        sendMessage(m, intent);
    }

    private void sendMessage(SmsCbMessage m, Intent intent) {
        intent.putExtra("message", m);
        startService(intent);
    }

    // Test handleCellBroadcastIntent method
    public void testHandleCellBroadcastIntent() throws Exception {
        sendMessage(987654321);
        waitForMs(200);

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        CellBroadcastMessage cbm = new CellBroadcastMessage(createMessage(987654321));

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test showNewAlert method
    public void testShowNewAlert() throws Exception {
        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        SmsCbMessage message = createMessage(34788612);
        intent.putExtra("message", new CellBroadcastMessage(message));
        startService(intent);
        waitForMs(200);

        // verify audio service intent
        assertEquals(CellBroadcastAlertAudio.ACTION_START_ALERT_AUDIO,
                mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertService.AlertType.CMAS_DEFAULT,
                mServiceIntentToVerify.getSerializableExtra(ALERT_AUDIO_TONE_TYPE));
        assertEquals(message.getMessageBody(),
                mServiceIntentToVerify.getStringExtra(
                        CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY));

        // verify alert dialog activity intent
        ArrayList<CellBroadcastMessage> newMessageList = mActivityIntentToVerify
                .getParcelableArrayListExtra(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA);
        assertEquals(1, newMessageList.size());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK,
                (mActivityIntentToVerify.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK));
        compareCellBroadCastMessage(new CellBroadcastMessage(message), newMessageList.get(0));
    }

    // Test if we ignore the duplicate message
    public void testDuplicateMessage() throws Exception {
        sendMessage(4321);
        waitForMs(200);

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());

        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        CellBroadcastMessage cbm = new CellBroadcastMessage(createMessage(4321));

        compareCellBroadCastMessage(cbm, cbmTest);

        mServiceIntentToVerify = null;

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);

        SmsCbMessage m = createMessage(4321);
        intent.putExtra("message", m);

        startService(intent);
        waitForMs(200);

        // If the duplicate detection is working, the service should not pop-up the dialog and
        // play the alert tones.
        assertNull(mServiceIntentToVerify);
    }

    // Test if we allow non-duplicate message
    public void testNonDuplicateMessage() throws Exception {
        sendMessage(187286123);

        mServiceIntentToVerify = null;

        Intent intent = new Intent(mContext, CellBroadcastAlertService.class);
        intent.setAction(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);

        SmsCbMessage m = createMessage(129487394);
        intent.putExtra("message", m);

        startService(intent);
        waitForMs(200);

        assertEquals(SHOW_NEW_ALERT_ACTION, mServiceIntentToVerify.getAction());
        assertEquals(CellBroadcastAlertService.class.getName(),
                intent.getComponent().getClassName());

        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        CellBroadcastMessage cbm = new CellBroadcastMessage(m);

        compareCellBroadCastMessage(cbm, cbmTest);
    }

    // Test when we reach the maximum messages, the oldest one should be evicted.
    public void testMaximumMessages() throws Exception {
        for (int i = 0; i < 1024 + 1; i++) {
            sendMessage(i);
            waitForMs(50);
        }

        sendMessage(0);
        waitForMs(40);
        // Check if the oldest one has been already evicted.
        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        CellBroadcastMessage cbm = new CellBroadcastMessage(createMessage(0));

        compareCellBroadCastMessage(cbm, cbmTest);
        mActivityIntentToVerify = null;
    }

    public void testExpiration() throws Exception {
        PersistableBundle b = new PersistableBundle();
        b.putLong(CarrierConfigManager.KEY_MESSAGE_EXPIRATION_TIME_LONG, 1000);
        doReturn(b).when(mMockedCarrierConfigManager).getConfigForSubId(anyInt());

        sendMessage(91924);
        waitForMs(100);

        CellBroadcastMessage cbmTest =
                (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        assertEquals(91924, cbmTest.getSerialNumber());
        mServiceIntentToVerify = null;

        // Wait until it expires.
        waitForMs(1500);
        sendMessage(91924);
        waitForMs(100);

        // Since the previous one has already expired, this one should not be treated as a duplicate
        cbmTest = (CellBroadcastMessage) mServiceIntentToVerify.getExtras().get("message");
        assertEquals(91924, cbmTest.getSerialNumber());

        waitForMs(500);
        mServiceIntentToVerify = null;
        // This one should be treated as a duplicate since it's not expired yet.
        sendMessage(91924);
        assertNull(mServiceIntentToVerify);

    }
}