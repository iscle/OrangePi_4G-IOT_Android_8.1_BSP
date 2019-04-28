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

package com.android.internal.telephony.gsm;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Telephony;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.test.mock.MockContentResolver;

import com.android.internal.telephony.FakeSmsContentProvider;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.util.HexDump;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Method;

public class GsmInboundSmsHandlerTest extends TelephonyTest {
    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mGsmSmsMessage;
    @Mock
    private SmsHeader mSmsHeader;
    @Mock
    private InboundSmsTracker mInboundSmsTrackerPart1;
    @Mock
    private InboundSmsTracker mInboundSmsTrackerPart2;
    @Mock
    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;

    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private GsmInboundSmsHandlerTestHandler mGsmInboundSmsHandlerTestHandler;

    private FakeSmsContentProvider mContentProvider;
    private static final String RAW_TABLE_NAME = "raw";
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI,
            RAW_TABLE_NAME);

    private ContentValues mInboundSmsTrackerCV = new ContentValues();
    // For multi-part SMS
    private ContentValues mInboundSmsTrackerCVPart1;
    private ContentValues mInboundSmsTrackerCVPart2;
    private String mMessageBody = "This is the message body of a single-part message";
    private String mMessageBodyPart1 = "This is the first part of a multi-part message";
    private String mMessageBodyPart2 = "This is the second part of a multi-part message";

    byte[] mSmsPdu = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF};

    private class GsmInboundSmsHandlerTestHandler extends HandlerThread {

        private GsmInboundSmsHandlerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(mContext,
                    mSmsStorageMonitor, mPhone);
            setReady(true);
        }
    }

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mGsmInboundSmsHandler);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("GsmInboundSmsHandlerTest");

        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();

        UserManager userManager = (UserManager)mContext.getSystemService(Context.USER_SERVICE);
        doReturn(true).when(userManager).isUserUnlocked();

        try {
            doReturn(new int[]{UserHandle.USER_SYSTEM}).when(mIActivityManager).getRunningUserIds();
        } catch (RemoteException re) {
            fail("Unexpected RemoteException: " + re.getStackTrace());
        }

        mSmsMessage.mWrappedSmsMessage = mGsmSmsMessage;
        mInboundSmsTrackerCV.put("destination_port", InboundSmsTracker.DEST_PORT_FLAG_NO_PORT);
        mInboundSmsTrackerCV.put("pdu", HexDump.toHexString(mSmsPdu));
        mInboundSmsTrackerCV.put("address", "1234567890");
        mInboundSmsTrackerCV.put("reference_number", 1);
        mInboundSmsTrackerCV.put("sequence", 1);
        mInboundSmsTrackerCV.put("count", 1);
        mInboundSmsTrackerCV.put("date", System.currentTimeMillis());
        mInboundSmsTrackerCV.put("message_body", mMessageBody);
        mInboundSmsTrackerCV.put("display_originating_addr", "1234567890");

        doReturn(1).when(mInboundSmsTracker).getMessageCount();
        doReturn(1).when(mInboundSmsTracker).getReferenceNumber();
        doReturn("1234567890").when(mInboundSmsTracker).getAddress();
        doReturn(1).when(mInboundSmsTracker).getSequenceNumber();
        doReturn(1).when(mInboundSmsTracker).getIndexOffset();
        doReturn(-1).when(mInboundSmsTracker).getDestPort();
        doReturn(mMessageBody).when(mInboundSmsTracker).getMessageBody();
        doReturn(mSmsPdu).when(mInboundSmsTracker).getPdu();
        doReturn(mInboundSmsTrackerCV.get("date")).when(mInboundSmsTracker).getTimestamp();
        doReturn(mInboundSmsTrackerCV).when(mInboundSmsTracker).getContentValues();

        mContentProvider = new FakeSmsContentProvider();
        ((MockContentResolver)mContext.getContentResolver()).addProvider(
                Telephony.Sms.CONTENT_URI.getAuthority(), mContentProvider);

        mGsmInboundSmsHandlerTestHandler = new GsmInboundSmsHandlerTestHandler(TAG);
        mGsmInboundSmsHandlerTestHandler.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        // wait for wakelock to be released; timeout at 10s
        int i = 0;
        while (mGsmInboundSmsHandler.getWakeLock().isHeld() && i < 100) {
            waitForMs(100);
            i++;
        }
        assertFalse(mGsmInboundSmsHandler.getWakeLock().isHeld());
        mGsmInboundSmsHandler = null;
        mContentProvider.shutdown();
        mGsmInboundSmsHandlerTestHandler.quit();
        super.tearDown();
    }

    private void transitionFromStartupToIdle() {
        // verify initially in StartupState
        assertEquals("StartupState", getCurrentState().getName());

        // trigger transition to IdleState
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    private void verifySmsIntentBroadcasts(int numPastBroadcasts) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1 + numPastBroadcasts)).sendBroadcast(
                intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getAllValues().get(numPastBroadcasts).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2 + numPastBroadcasts)).sendBroadcast(
                intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(numPastBroadcasts + 1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testNewSms() {
        transitionFromStartupToIdle();

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        verifySmsIntentBroadcasts(0);

        // send same SMS again, verify no broadcasts are sent
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        verify(mContext, times(2)).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testNewSmsFromBlockedNumber_noBroadcastsSent() {
        String blockedNumber = "1234567890";
        doReturn(blockedNumber).when(mInboundSmsTracker).getDisplayAddress();
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add(blockedNumber);

        transitionFromStartupToIdle();

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    private void verifyDataSmsIntentBroadcasts(int numPastBroadcasts) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1 + numPastBroadcasts)).sendBroadcast(
                intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(numPastBroadcasts).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testBroadcastSms() {
        transitionFromStartupToIdle();

        doReturn(0).when(mInboundSmsTracker).getDestPort();
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_BROADCAST_SMS,
                mInboundSmsTracker);
        waitForMs(100);

        verifyDataSmsIntentBroadcasts(0);

        // send same data sms again, and since it's not text sms it should be broadcast again
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_BROADCAST_SMS,
                mInboundSmsTracker);
        waitForMs(100);

        verifyDataSmsIntentBroadcasts(1);
    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testInjectSms() {
        transitionFromStartupToIdle();

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_INJECT_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(200);

        verifySmsIntentBroadcasts(0);

        // inject same SMS again, verify no broadcasts are sent
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_INJECT_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        verify(mContext, times(2)).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    private void prepareMultiPartSms(boolean isWapPush) {
        // Part 1
        mInboundSmsTrackerCVPart1 = new ContentValues();
        mInboundSmsTrackerCVPart1.put("pdu", HexDump.toHexString(mSmsPdu));
        mInboundSmsTrackerCVPart1.put("address", "1234567890");
        mInboundSmsTrackerCVPart1.put("reference_number", 1);
        mInboundSmsTrackerCVPart1.put("sequence", 1);
        mInboundSmsTrackerCVPart1.put("count", 2);
        mInboundSmsTrackerCVPart1.put("date", System.currentTimeMillis());
        mInboundSmsTrackerCVPart1.put("message_body", mMessageBodyPart1);
        mInboundSmsTrackerCVPart1.put("display_originating_addr", "1234567890");

        doReturn(2).when(mInboundSmsTrackerPart1).getMessageCount();
        doReturn(1).when(mInboundSmsTrackerPart1).getReferenceNumber();
        doReturn("1234567890").when(mInboundSmsTrackerPart1).getAddress();
        doReturn(1).when(mInboundSmsTrackerPart1).getSequenceNumber();
        doReturn(1).when(mInboundSmsTrackerPart1).getIndexOffset();
        doReturn(-1).when(mInboundSmsTrackerPart1).getDestPort();
        doReturn(mMessageBodyPart1).when(mInboundSmsTrackerPart1).getMessageBody();
        doReturn(mSmsPdu).when(mInboundSmsTrackerPart1).getPdu();
        doReturn(new String[]{mInboundSmsTrackerPart1.getAddress(),
                Integer.toString(mInboundSmsTrackerPart1.getReferenceNumber()),
                Integer.toString(mInboundSmsTrackerPart1.getMessageCount())})
                .when(mInboundSmsTrackerPart1).getDeleteWhereArgs();
        doReturn(mInboundSmsTrackerCVPart1.get("date")).when(mInboundSmsTrackerPart1).
                getTimestamp();
        doReturn(mInboundSmsTrackerCVPart1).when(mInboundSmsTrackerPart1).getContentValues();
        if (isWapPush) {
            mInboundSmsTrackerCVPart1.put("destination_port",
                    (InboundSmsTracker.DEST_PORT_FLAG_3GPP2 |
                            InboundSmsTracker.DEST_PORT_FLAG_3GPP2_WAP_PDU |
                            SmsHeader.PORT_WAP_PUSH));
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE_3GPP2WAP).when(mInboundSmsTrackerPart1)
                    .getQueryForSegments();
            doReturn(InboundSmsTracker.SELECT_BY_DUPLICATE_REFERENCE_3GPP2WAP)
                    .when(mInboundSmsTrackerPart1).getQueryForMultiPartDuplicates();
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE_3GPP2WAP).when(mInboundSmsTrackerPart1)
                    .getDeleteWhere();
            doReturn(SmsHeader.PORT_WAP_PUSH).when(mInboundSmsTrackerPart1).getDestPort();
            doReturn(true).when(mInboundSmsTrackerPart1).is3gpp2();

        } else {
            mInboundSmsTrackerCVPart1.put("destination_port",
                    InboundSmsTracker.DEST_PORT_FLAG_NO_PORT);
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE).when(mInboundSmsTrackerPart1)
                    .getQueryForSegments();
            doReturn(InboundSmsTracker.SELECT_BY_DUPLICATE_REFERENCE)
                    .when(mInboundSmsTrackerPart1).getQueryForMultiPartDuplicates();
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE).when(mInboundSmsTrackerPart1)
                    .getDeleteWhere();
        }

        // Part 2
        mInboundSmsTrackerCVPart2 = new ContentValues();
        mInboundSmsTrackerCVPart2.put("pdu", HexDump.toHexString(mSmsPdu));
        mInboundSmsTrackerCVPart2.put("address", "1234567890");
        mInboundSmsTrackerCVPart2.put("reference_number", 1);
        mInboundSmsTrackerCVPart2.put("sequence", 2);
        mInboundSmsTrackerCVPart2.put("count", 2);
        mInboundSmsTrackerCVPart2.put("date", System.currentTimeMillis());
        mInboundSmsTrackerCVPart2.put("message_body", mMessageBodyPart2);
        mInboundSmsTrackerCVPart2.put("display_originating_addr", "1234567890");

        doReturn(2).when(mInboundSmsTrackerPart2).getMessageCount();
        doReturn(1).when(mInboundSmsTrackerPart2).getReferenceNumber();
        doReturn("1234567890").when(mInboundSmsTrackerPart2).getAddress();
        doReturn(2).when(mInboundSmsTrackerPart2).getSequenceNumber();
        doReturn(1).when(mInboundSmsTrackerPart2).getIndexOffset();
        doReturn(-1).when(mInboundSmsTrackerPart2).getDestPort();
        doReturn(mMessageBodyPart2).when(mInboundSmsTrackerPart2).getMessageBody();
        doReturn(mSmsPdu).when(mInboundSmsTrackerPart2).getPdu();
        doReturn(new String[]{mInboundSmsTrackerPart2.getAddress(),
                Integer.toString(mInboundSmsTrackerPart2.getReferenceNumber()),
                Integer.toString(mInboundSmsTrackerPart2.getMessageCount())})
                .when(mInboundSmsTrackerPart2).getDeleteWhereArgs();
        doReturn(mInboundSmsTrackerCVPart2.get("date")).when(mInboundSmsTrackerPart2).
                getTimestamp();
        doReturn(mInboundSmsTrackerCVPart2).when(mInboundSmsTrackerPart2).getContentValues();
        if (isWapPush) {
            mInboundSmsTrackerCVPart2.put("destination_port",
                    (InboundSmsTracker.DEST_PORT_FLAG_3GPP2 |
                            InboundSmsTracker.DEST_PORT_FLAG_3GPP2_WAP_PDU |
                            SmsHeader.PORT_WAP_PUSH));
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE_3GPP2WAP).when(mInboundSmsTrackerPart2)
                    .getQueryForSegments();
            doReturn(InboundSmsTracker.SELECT_BY_DUPLICATE_REFERENCE_3GPP2WAP)
                    .when(mInboundSmsTrackerPart2).getQueryForMultiPartDuplicates();
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE_3GPP2WAP).when(mInboundSmsTrackerPart2)
                    .getDeleteWhere();
            doReturn(SmsHeader.PORT_WAP_PUSH).when(mInboundSmsTrackerPart2).getDestPort();
            doReturn(true).when(mInboundSmsTrackerPart2).is3gpp2();

        } else {
            mInboundSmsTrackerCVPart2.put("destination_port",
                    InboundSmsTracker.DEST_PORT_FLAG_NO_PORT);
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE).when(mInboundSmsTrackerPart2)
                    .getQueryForSegments();
            doReturn(InboundSmsTracker.SELECT_BY_DUPLICATE_REFERENCE)
                    .when(mInboundSmsTrackerPart2).getQueryForMultiPartDuplicates();
            doReturn(InboundSmsTracker.SELECT_BY_REFERENCE).when(mInboundSmsTrackerPart2)
                    .getDeleteWhere();
        }
    }

    @Test
    @MediumTest
    public void testMultiPartSmsWithIncompleteWAP() {
        /**
         * Test scenario: 3 messages are received with same address, ref number, count. two of the
         * messages are belonging to the same multi-part SMS and the other one is a 3GPP2WAP.
         * we should not try to merge 3gpp2wap with the multi-part SMS.
         */
        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();

        // part 2 of non-3gpp2wap arrives first
        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(200);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        // mock a 3gpp2wap push
        prepareMultiPartSms(true);
        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(200);
        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        // verify no broadcast sent.
        verify(mContext, times(0)).sendBroadcast(any(Intent.class));

        // additional copy of part 1 of non-3gpp2wap
        prepareMultiPartSms(false);
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(200);

        // verify broadcast intents
        verifySmsIntentBroadcasts(0);
        assertEquals("IdleState", getCurrentState().getName());
        // verify there are three segments in the db and only one of them is not marked as deleted.
        assertEquals(3, mContentProvider.getNumRows());
        assertEquals(1, mContentProvider.query(sRawUri, null, "deleted=0", null, null).getCount());
    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testMultiPartSms() {
        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();

        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // verify broadcast intents
        verifySmsIntentBroadcasts(0);

        // if an additional copy of one of the segments above is received, it should not be kept in
        // the db and should not be combined with any subsequent messages received from the same
        // sender

        // additional copy of part 2 of message
        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // verify no additional broadcasts sent
        verify(mContext, times(2)).sendBroadcast(any(Intent.class));

        // part 1 of new sms recieved from same sender with same parameters, just different
        // timestamps, should not be combined with the additional part 2 received above

        // call prepareMultiPartSms() to update timestamps
        prepareMultiPartSms(false);

        // part 1 of new sms
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // verify no additional broadcasts sent
        verify(mContext, times(2)).sendBroadcast(any(Intent.class));

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testMultiPartIncompleteSms() {
        /**
         * Test scenario: 2 messages are received with same address, ref number, count, and
         * seqNumber, with count = 2 and seqNumber = 1. We should not try to merge these.
         */
        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);
        // change seqNumber in part 2 to 1
        mInboundSmsTrackerCVPart2.put("sequence", 1);
        doReturn(1).when(mInboundSmsTrackerPart2).getSequenceNumber();

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();

        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // verify no broadcasts sent
        verify(mContext, never()).sendBroadcast(any(Intent.class));
        // verify there's only 1 of the segments in the db (other should be discarded as dup)
        assertEquals(1, mContentProvider.getNumRows());
        // State machine should go back to idle
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testMultipartSmsFromBlockedNumber_noBroadcastsSent() {
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add("1234567890");

        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testMultipartSmsFromBlockedEmail_noBroadcastsSent() {
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add("1234567890@test.com");

        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);
        // only the first SMS is configured with the display originating email address
        mInboundSmsTrackerCVPart1.put("display_originating_addr", "1234567890@test.com");

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(nullable(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        nullable(String.class), nullable(String.class), anyInt(), anyInt(),
                        anyInt(), anyBoolean(), nullable(String.class));
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testBroadcastUndeliveredUserLocked() throws Exception {
        replaceInstance(SmsBroadcastUndelivered.class, "instance", null, null);
        doReturn(0).when(mInboundSmsTracker).getDestPort();

        // add a fake entry to db
        mContentProvider.insert(sRawUri, mInboundSmsTrackerCV);

        // make it a single-part message
        doReturn(1).when(mInboundSmsTracker).getMessageCount();

        // user locked
        UserManager userManager = (UserManager)mContext.getSystemService(Context.USER_SERVICE);
        doReturn(false).when(userManager).isUserUnlocked();

        SmsBroadcastUndelivered.initialize(mContext, mGsmInboundSmsHandler, mCdmaInboundSmsHandler);

        // verify that a broadcast receiver is registered for current user (user == null) based on
        // implementation in ContextFixture
        verify(mContext).registerReceiverAsUser(any(BroadcastReceiver.class), eq((UserHandle)null),
                any(IntentFilter.class), eq((String)null), eq((Handler)null));

        waitForMs(100);

        // verify no broadcasts sent because due to !isUserUnlocked
        verify(mContext, never()).sendBroadcast(any(Intent.class));

        // when user unlocks the device, the message in db should be broadcast
        doReturn(true).when(userManager).isUserUnlocked();
        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
        waitForMs(100);

        verifyDataSmsIntentBroadcasts(1);
    }

    @Test
    @MediumTest
    public void testBroadcastUndeliveredUserUnlocked() throws Exception {
        replaceInstance(SmsBroadcastUndelivered.class, "instance", null, null);
        doReturn(0).when(mInboundSmsTracker).getDestPort();

        // add a fake entry to db
        mContentProvider.insert(sRawUri, mInboundSmsTrackerCV);

        // make it a single-part message
        doReturn(1).when(mInboundSmsTracker).getMessageCount();

        SmsBroadcastUndelivered.initialize(mContext, mGsmInboundSmsHandler, mCdmaInboundSmsHandler);
        waitForMs(100);

        // user is unlocked; intent should be broadcast right away
        verifyDataSmsIntentBroadcasts(0);
    }

    @Test
    @MediumTest
    public void testBroadcastUndeliveredDeleted() throws Exception {
        replaceInstance(SmsBroadcastUndelivered.class, "instance", null, null);
        SmsBroadcastUndelivered.initialize(mContext, mGsmInboundSmsHandler, mCdmaInboundSmsHandler);
        doReturn(0).when(mInboundSmsTracker).getDestPort();

        //add a fake entry to db
        ContentValues rawSms = new ContentValues();
        rawSms.put("deleted", 1);
        mContentProvider.insert(sRawUri, rawSms);

        //make it a single-part message
        doReturn(1).when(mInboundSmsTracker).getMessageCount();

        //when user unlocks the device, broadcast should not be sent for new message
        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
        waitForMs(100);

        verify(mContext, times(1)).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());

    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testBroadcastUndeliveredMultiPart() throws Exception {
        replaceInstance(SmsBroadcastUndelivered.class, "instance", null, null);

        // prepare SMS part 1 and part 2
        prepareMultiPartSms(false);

        //add the 2 SMS parts to db
        mContentProvider.insert(sRawUri, mInboundSmsTrackerCVPart1);
        mContentProvider.insert(sRawUri, mInboundSmsTrackerCVPart2);

        //return InboundSmsTracker objects corresponding to the 2 parts
        doReturn(mInboundSmsTrackerPart1).doReturn(mInboundSmsTrackerPart2).
                when(mTelephonyComponentFactory).makeInboundSmsTracker(any(Cursor.class),
                anyBoolean());

        SmsBroadcastUndelivered.initialize(mContext, mGsmInboundSmsHandler, mCdmaInboundSmsHandler);
        waitForMs(100);

        verifySmsIntentBroadcasts(0);
    }

    @FlakyTest
    @Ignore
    @Test
    @MediumTest
    public void testWaitingStateTimeout() throws Exception {
        transitionFromStartupToIdle();

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(
                intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getAllValues().get(0).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        waitForMs(InboundSmsHandler.STATE_TIMEOUT + 300);

        assertEquals("IdleState", getCurrentState().getName());
    }
}
