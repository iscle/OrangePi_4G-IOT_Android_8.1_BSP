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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.service.euicc.EuiccProfileInfo;
import android.service.euicc.EuiccService;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SubscriptionInfoUpdaterTest extends TelephonyTest {

    private static final int FAKE_SUB_ID_1 = 0;
    private static final int FAKE_SUB_ID_2 = 1;
    private static final String FAKE_MCC_MNC_1 = "123456";
    private static final String FAKE_MCC_MNC_2 = "456789";

    private SubscriptionInfoUpdaterHandlerThread mSubscriptionInfoUpdaterHandlerThread;
    private SubscriptionInfoUpdater mUpdater;
    private IccRecords mIccRecord;
    @Mock
    private UserInfo mUserInfo;
    @Mock
    private SubscriptionInfo mSubInfo;
    @Mock
    private ContentProvider mContentProvider;
    @Mock
    private HashMap<String, Object> mSubscriptionContent;
    @Mock
    private IccFileHandler mIccFileHandler;
    @Mock
    private EuiccController mEuiccController;
    @Mock
    private IntentBroadcaster mIntentBroadcaster;

    /*Custom ContentProvider */
    private class FakeSubscriptionContentProvider extends MockContentProvider {
        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return mContentProvider.update(uri, values, selection, selectionArgs);
        }
    }

    private class SubscriptionInfoUpdaterHandlerThread extends HandlerThread {

        private SubscriptionInfoUpdaterHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mUpdater = new SubscriptionInfoUpdater(getLooper(), mContext, new Phone[]{mPhone},
                    new CommandsInterface[]{mSimulatedCommands});
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        replaceInstance(SubscriptionInfoUpdater.class, "mIccId", null, new String[1]);
        replaceInstance(SubscriptionInfoUpdater.class, "mInsertSimState", null, new int[1]);
        replaceInstance(SubscriptionInfoUpdater.class, "mContext", null, null);
        replaceInstance(SubscriptionInfoUpdater.class, "PROJECT_SIM_NUM", null, 1);

        replaceInstance(EuiccController.class, "sInstance", null, mEuiccController);
        replaceInstance(IntentBroadcaster.class, "sIntentBroadcaster", null, mIntentBroadcaster);

        doReturn(1).when(mTelephonyManager).getSimCount();
        doReturn(1).when(mTelephonyManager).getPhoneCount();

        when(mContentProvider.update(any(), any(), any(), isNull())).thenAnswer(
                new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocation) throws Throwable {
                        ContentValues values = invocation.getArgument(1);
                        for (String key : values.keySet()) {
                            mSubscriptionContent.put(key, values.get(key));
                        }
                        return 1;
                    }
                });

        doReturn(mUserInfo).when(mIActivityManager).getCurrentUser();
        doReturn(new int[]{FAKE_SUB_ID_1}).when(mSubscriptionController).getSubId(0);
        doReturn(new int[]{FAKE_SUB_ID_1}).when(mSubscriptionManager).getActiveSubscriptionIdList();
        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                SubscriptionManager.CONTENT_URI.getAuthority(),
                new FakeSubscriptionContentProvider());
        doReturn(new int[]{}).when(mSubscriptionController).getActiveSubIdList();
        mIccRecord = mIccCardProxy.getIccRecords();

        mSubscriptionInfoUpdaterHandlerThread = new SubscriptionInfoUpdaterHandlerThread(TAG);
        mSubscriptionInfoUpdaterHandlerThread.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mSubscriptionInfoUpdaterHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSimAbsent() throws Exception {
        doReturn(Arrays.asList(mSubInfo)).when(mSubscriptionController)
                .getSubInfoUsingSlotIndexWithCheck(eq(FAKE_SUB_ID_1), anyBoolean(), anyString());
        doReturn(new int[]{FAKE_SUB_ID_1}).when(mSubscriptionController).getActiveSubIdList();
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_ABSENT);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(mIntent);

        waitForMs(100);
        verify(mSubscriptionContent).put(eq(SubscriptionManager.SIM_SLOT_INDEX),
                eq(SubscriptionManager.INVALID_SIM_SLOT_INDEX));

        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_ABSENT));
        verify(mSubscriptionController, times(1)).clearSubInfo();
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimUnknown() throws Exception {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_UNKNOWN);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(mIntent);

        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_UNKNOWN));
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimError() throws Exception {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 0);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager).updateConfigForPhoneId(eq(0),
                eq(IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR));
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testWrongSimState() throws Exception {
        Intent mIntent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_IMSI);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, 2);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        verify(mSubscriptionContent, times(0)).put(anyString(), any());
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(0)).updateConfigForPhoneId(eq(2),
                eq(IccCardConstants.INTENT_VALUE_ICC_IMSI));
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimLoaded() throws Exception {
        /* mock new sim got loaded and there is no sim loaded before */
        doReturn(null).when(mSubscriptionController)
                .getSubInfoUsingSlotIndexWithCheck(eq(FAKE_SUB_ID_1), anyBoolean(), anyString());
        doReturn("89012604200000000000").when(mIccRecord).getIccId();
        doReturn(FAKE_MCC_MNC_1).when(mTelephonyManager).getSimOperatorNumeric(FAKE_SUB_ID_1);
        Intent intentInternalSimStateChanged =
                new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        intentInternalSimStateChanged.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOADED);
        intentInternalSimStateChanged.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(intentInternalSimStateChanged);
        waitForMs(100);

        // verify SIM_STATE_CHANGED broadcast. It should be broadcast twice, once for
        // READ_PHONE_STATE and once for READ_PRIVILEGED_PHONE_STATE
        /* todo: cannot verify as intent is sent using ActivityManagerNative.broadcastStickyIntent()
         * uncomment code below when that is fixed
         */
        /* ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture(),
                stringArgumentCaptor.capture());
        assertEquals(TelephonyIntents.ACTION_SIM_STATE_CHANGED,
                intentArgumentCaptor.getAllValues().get(0).getAction());
        assertEquals(Manifest.permission.READ_PHONE_STATE,
                stringArgumentCaptor.getAllValues().get(0));
        assertEquals(TelephonyIntents.ACTION_SIM_STATE_CHANGED,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals(Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                stringArgumentCaptor.getAllValues().get(1)); */

        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mTelephonyManager).getSimOperatorNumeric(FAKE_SUB_ID_1);
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(
                eq("89012604200000000000"), eq(FAKE_SUB_ID_1));
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
        verify(mSubscriptionController, times(1)).setMccMnc(FAKE_MCC_MNC_1, FAKE_SUB_ID_1);
        verify(mSubscriptionController, times(0)).clearSubInfo();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOADED));

        // ACTION_USER_UNLOCKED should trigger another SIM_STATE_CHANGED
        Intent intentSimStateChanged = new Intent(Intent.ACTION_USER_UNLOCKED);
        mContext.sendBroadcast(intentSimStateChanged);
        waitForMs(100);

        // verify SIM_STATE_CHANGED broadcast
        /* todo: cannot verify as intent is sent using ActivityManagerNative.broadcastStickyIntent()
         * uncomment code below when that is fixed
         */
        /* verify(mContext, times(4)).sendBroadcast(intentArgumentCaptor.capture(),
                stringArgumentCaptor.capture());
        assertEquals(TelephonyIntents.ACTION_SIM_STATE_CHANGED,
                intentArgumentCaptor.getAllValues().get(2).getAction());
        assertEquals(Manifest.permission.READ_PHONE_STATE,
                stringArgumentCaptor.getAllValues().get(2));
        assertEquals(TelephonyIntents.ACTION_SIM_STATE_CHANGED,
                intentArgumentCaptor.getAllValues().get(3).getAction());
        assertEquals(Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                stringArgumentCaptor.getAllValues().get(3)); */
    }

    @Test
    @SmallTest
    public void testSimLoadedEmptyOperatorNumeric() throws Exception {
        /* mock new sim got loaded and there is no sim loaded before */
        doReturn(null).when(mSubscriptionController)
                .getSubInfoUsingSlotIndexWithCheck(eq(FAKE_SUB_ID_1), anyBoolean(), anyString());
        doReturn("89012604200000000000").when(mIccRecord).getIccId();
        // operator numeric is empty
        doReturn("").when(mTelephonyManager).getSimOperatorNumeric(FAKE_SUB_ID_1);
        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOADED);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mTelephonyManager).getSimOperatorNumeric(FAKE_SUB_ID_1);
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(
                eq("89012604200000000000"), eq(FAKE_SUB_ID_1));
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
        verify(mSubscriptionController, times(0)).setMccMnc(anyString(), anyInt());
        verify(mSubscriptionController, times(0)).clearSubInfo();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOADED));
    }

    @Test
    @SmallTest
    public void testSimLockedWithOutIccId() throws Exception {
        /* mock no IccId Info present and try to query IccId
         after IccId query, update subscriptionDB */
        doReturn(mIccFileHandler).when(mIccCardProxy).getIccFileHandler();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message msg = (Message) invocation.getArguments()[1];
                AsyncResult.forMessage(msg).result = IccUtils
                        .hexStringToBytes("89012604200000000000");
                msg.sendToTarget();
                return null;
            }
        }).when(mIccFileHandler).loadEFTransparent(anyInt(), any(Message.class));

        doReturn(Arrays.asList(mSubInfo)).when(mSubscriptionController)
                .getSubInfoUsingSlotIndexWithCheck(eq(FAKE_SUB_ID_1), anyBoolean(), anyString());

        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOCKED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, "TESTING");
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        /* old IccId != new queried IccId */
        verify(mSubscriptionContent).put(eq(SubscriptionManager.SIM_SLOT_INDEX),
                eq(SubscriptionManager.INVALID_SIM_SLOT_INDEX));
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(
                eq("98106240020000000000"), eq(FAKE_SUB_ID_1));

        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
        verify(mSubscriptionController, times(0)).clearSubInfo();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOCKED));
    }

    @Test
    @SmallTest
    public void testDualSimLoaded() throws Exception {
        // Mock there is two sim cards

        replaceInstance(SubscriptionInfoUpdater.class, "mIccId", null,
                new String[]{null, null});
        replaceInstance(SubscriptionInfoUpdater.class, "PROJECT_SIM_NUM", null, 2);
        replaceInstance(SubscriptionInfoUpdater.class, "mPhone", null,
                new Phone[]{mPhone, mPhone});
        replaceInstance(SubscriptionInfoUpdater.class, "mInsertSimState", null,
                new int[]{SubscriptionInfoUpdater.SIM_NOT_CHANGE,
                        SubscriptionInfoUpdater.SIM_NOT_CHANGE});

        doReturn(new int[]{FAKE_SUB_ID_1, FAKE_SUB_ID_2}).when(mSubscriptionManager)
                .getActiveSubscriptionIdList();
        doReturn(FAKE_SUB_ID_1).when(mSubscriptionController).getPhoneId(eq(FAKE_SUB_ID_1));
        doReturn(FAKE_SUB_ID_2).when(mSubscriptionController).getPhoneId(eq(FAKE_SUB_ID_2));
        doReturn(2).when(mTelephonyManager).getSimCount();
        doReturn(FAKE_MCC_MNC_1).when(mTelephonyManager).getSimOperatorNumeric(eq(FAKE_SUB_ID_1));
        doReturn(FAKE_MCC_MNC_2).when(mTelephonyManager).getSimOperatorNumeric(eq(FAKE_SUB_ID_2));
        // Mock there is no sim inserted before
        doReturn(null).when(mSubscriptionController)
                .getSubInfoUsingSlotIndexWithCheck(anyInt(), anyBoolean(), anyString());
        verify(mSubscriptionController, times(0)).clearSubInfo();
        doReturn("89012604200000000000").when(mIccRecord).getIccId();

        // Mock sending a sim loaded for SIM 1
        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOADED);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);
        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(0)).addSubscriptionInfoRecord(anyString(), anyInt());
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
        verify(mSubscriptionController, times(0)).setMccMnc(anyString(), anyInt());

        // Mock sending a sim loaded for SIM 2
        doReturn("89012604200000000001").when(mIccRecord).getIccId();
        mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOADED);
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_2);
        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(eq("89012604200000000000"),
                eq(FAKE_SUB_ID_1));
        verify(mSubscriptionManager, times(1)).addSubscriptionInfoRecord(eq("89012604200000000001"),
                eq(FAKE_SUB_ID_2));
        verify(mSubscriptionController, times(1)).setMccMnc(eq(FAKE_MCC_MNC_1), eq(FAKE_SUB_ID_1));
        verify(mSubscriptionController, times(1)).setMccMnc(eq(FAKE_MCC_MNC_2), eq(FAKE_SUB_ID_2));
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, times(1)).notifySubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSimLockWithIccId() throws Exception {
        /* no need for IccId query */

        replaceInstance(SubscriptionInfoUpdater.class, "mIccId", null,
                new String[]{"89012604200000000000"});
        doReturn(mIccFileHandler).when(mIccCardProxy).getIccFileHandler();

        Intent mIntent = new Intent(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE,
                IccCardConstants.INTENT_VALUE_ICC_LOCKED);
        mIntent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, "TESTING");
        mIntent.putExtra(PhoneConstants.PHONE_KEY, FAKE_SUB_ID_1);

        mContext.sendBroadcast(mIntent);
        waitForMs(100);

        verify(mIccFileHandler, times(0)).loadEFTransparent(anyInt(), any(Message.class));
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(mContext);
        verify(mSubscriptionManager, times(0)).addSubscriptionInfoRecord(
                anyString(), eq(FAKE_SUB_ID_1));
        verify(mSubscriptionController, times(0)).notifySubscriptionInfoChanged();
        verify(mSubscriptionController, times(0)).clearSubInfo();
        CarrierConfigManager mConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        /* broadcast is done */
        verify(mConfigManager, times(1)).updateConfigForPhoneId(eq(FAKE_SUB_ID_1),
                eq(IccCardConstants.INTENT_VALUE_ICC_LOCKED));
    }

    @Test
    @SmallTest
    public void testUpdateEmbeddedSubscriptions_listSuccess() throws Exception {
        when(mEuiccManager.isEnabled()).thenReturn(true);

        EuiccProfileInfo[] euiccProfiles = new EuiccProfileInfo[] {
                new EuiccProfileInfo("1", null /* accessRules */, null /* nickname */),
                new EuiccProfileInfo("3", null /* accessRules */, null /* nickname */),
        };
        when(mEuiccController.blockingGetEuiccProfileInfoList()).thenReturn(
                new GetEuiccProfileInfoListResult(
                        EuiccService.RESULT_OK, euiccProfiles, false /* removable */));

        List<SubscriptionInfo> subInfoList = new ArrayList<>();
        // 1: not embedded, but has matching iccid with an embedded subscription.
        subInfoList.add(new SubscriptionInfo(
                        0, "1", 0, "", "", 0, 0, "", 0, null, 0, 0, "", false /* isEmbedded */,
                        null /* accessRules */));
        // 2: embedded but no longer present.
        subInfoList.add(new SubscriptionInfo(
                0, "2", 0, "", "", 0, 0, "", 0, null, 0, 0, "", true /* isEmbedded */,
                null /* accessRules */));

        when(mSubscriptionController.getSubscriptionInfoListForEmbeddedSubscriptionUpdate(
                new String[] { "1", "3"}, false /* removable */)).thenReturn(subInfoList);

        assertTrue(mUpdater.updateEmbeddedSubscriptions());

        // 3 is new and so a new entry should have been created.
        verify(mSubscriptionController).insertEmptySubInfoRecord(
                "3", SubscriptionManager.SIM_NOT_INSERTED);
        // 1 already existed, so no new entries should be created for it.
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, never()).insertEmptySubInfoRecord(eq("1"), anyInt());

        // Info for 1 and 3 should be updated as active embedded subscriptions.
        ArgumentCaptor<ContentValues> iccid1Values = ArgumentCaptor.forClass(ContentValues.class);
        verify(mContentProvider).update(eq(SubscriptionManager.CONTENT_URI), iccid1Values.capture(),
                eq(SubscriptionManager.ICC_ID + "=\"1\""), isNull());
        assertEquals(1,
                iccid1Values.getValue().getAsInteger(SubscriptionManager.IS_EMBEDDED).intValue());
        ArgumentCaptor<ContentValues> iccid3Values = ArgumentCaptor.forClass(ContentValues.class);
        verify(mContentProvider).update(eq(SubscriptionManager.CONTENT_URI), iccid3Values.capture(),
                eq(SubscriptionManager.ICC_ID + "=\"3\""), isNull());
        assertEquals(1,
                iccid3Values.getValue().getAsInteger(SubscriptionManager.IS_EMBEDDED).intValue());

        // 2 should have been removed since it was returned from the cache but was not present
        // in the list provided by the LPA.
        ArgumentCaptor<ContentValues> iccid2Values = ArgumentCaptor.forClass(ContentValues.class);
        verify(mContentProvider).update(eq(SubscriptionManager.CONTENT_URI), iccid2Values.capture(),
                eq(SubscriptionManager.ICC_ID + " IN (\"2\")"), isNull());
        assertEquals(0,
                iccid2Values.getValue().getAsInteger(SubscriptionManager.IS_EMBEDDED).intValue());
    }

    @Test
    @SmallTest
    public void testUpdateEmbeddedSubscriptions_listFailure() throws Exception {
        when(mEuiccManager.isEnabled()).thenReturn(true);
        when(mEuiccController.blockingGetEuiccProfileInfoList())
                .thenReturn(new GetEuiccProfileInfoListResult(
                        42, null /* subscriptions */, false /* removable */));

        List<SubscriptionInfo> subInfoList = new ArrayList<>();
        // 1: not embedded, but has matching iccid with an embedded subscription.
        subInfoList.add(new SubscriptionInfo(
                0, "1", 0, "", "", 0, 0, "", 0, null, 0, 0, "", false /* isEmbedded */,
                null /* accessRules */));
        // 2: embedded.
        subInfoList.add(new SubscriptionInfo(
                0, "2", 0, "", "", 0, 0, "", 0, null, 0, 0, "", true /* isEmbedded */,
                null /* accessRules */));

        when(mSubscriptionController.getSubscriptionInfoListForEmbeddedSubscriptionUpdate(
                new String[0], false /* removable */)).thenReturn(subInfoList);

        assertTrue(mUpdater.updateEmbeddedSubscriptions());

        // No new entries should be created.
        verify(mSubscriptionController, times(0)).clearSubInfo();
        verify(mSubscriptionController, never()).insertEmptySubInfoRecord(anyString(), anyInt());

        // 1 should not have been touched.
        verify(mContentProvider, never()).update(eq(SubscriptionManager.CONTENT_URI), any(),
                eq(SubscriptionManager.ICC_ID + "=\"1\""), isNull());
        verify(mContentProvider, never()).update(eq(SubscriptionManager.CONTENT_URI), any(),
                eq(SubscriptionManager.ICC_ID + "IN (\"1\")"), isNull());

        // 2 should have been removed since it was returned from the cache but the LPA had an
        // error when listing.
        ArgumentCaptor<ContentValues> iccid2Values = ArgumentCaptor.forClass(ContentValues.class);
        verify(mContentProvider).update(eq(SubscriptionManager.CONTENT_URI), iccid2Values.capture(),
                eq(SubscriptionManager.ICC_ID + " IN (\"2\")"), isNull());
        assertEquals(0,
                iccid2Values.getValue().getAsInteger(SubscriptionManager.IS_EMBEDDED).intValue());
    }

    @Test
    @SmallTest
    public void testUpdateEmbeddedSubscriptions_emptyToEmpty() throws Exception {
        when(mEuiccManager.isEnabled()).thenReturn(true);
        when(mEuiccController.blockingGetEuiccProfileInfoList())
                .thenReturn(new GetEuiccProfileInfoListResult(
                        42, null /* subscriptions */, true /* removable */));

        List<SubscriptionInfo> subInfoList = new ArrayList<>();
        // 1: not embedded.
        subInfoList.add(new SubscriptionInfo(
                0, "1", 0, "", "", 0, 0, "", 0, null, 0, 0, "", false /* isEmbedded */,
                null /* accessRules */));

        when(mSubscriptionController.getSubscriptionInfoListForEmbeddedSubscriptionUpdate(
                new String[0], false /* removable */)).thenReturn(subInfoList);

        assertFalse(mUpdater.updateEmbeddedSubscriptions());

        // No new entries should be created.
        verify(mSubscriptionController, never()).insertEmptySubInfoRecord(anyString(), anyInt());

        // No existing entries should have been updated.
        verify(mContentProvider, never()).update(eq(SubscriptionManager.CONTENT_URI), any(),
                any(), isNull());
    }
}