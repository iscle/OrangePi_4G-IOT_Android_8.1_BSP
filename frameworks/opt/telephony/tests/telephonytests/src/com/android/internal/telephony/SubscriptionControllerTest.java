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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.AppOpsManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionControllerTest extends TelephonyTest {

    private static final int SINGLE_SIM = 1;
    private String mCallingPackage;
    private SubscriptionController mSubscriptionControllerUT;
    private MockContentResolver mMockContentResolver;

    @Mock private List<SubscriptionInfo> mSubList;
    @Mock private AppOpsManager mAppOps;

    public class FakeSubscriptionContentProvider extends MockContentProvider {

        private ArrayList<ContentValues> mSubscriptionArray =
                new ArrayList<ContentValues>();

        private String[] mKeyMappingSet = new String[]{
                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID,
                SubscriptionManager.ICC_ID, SubscriptionManager.SIM_SLOT_INDEX,
                SubscriptionManager.DISPLAY_NAME, SubscriptionManager.CARRIER_NAME,
                SubscriptionManager.NAME_SOURCE, SubscriptionManager.COLOR,
                SubscriptionManager.NUMBER, SubscriptionManager.DISPLAY_NUMBER_FORMAT,
                SubscriptionManager.DATA_ROAMING, SubscriptionManager.MCC,
                SubscriptionManager.MNC, SubscriptionManager.CB_EXTREME_THREAT_ALERT,
                SubscriptionManager.CB_SEVERE_THREAT_ALERT, SubscriptionManager.CB_AMBER_ALERT,
                SubscriptionManager.CB_ALERT_SOUND_DURATION,
                SubscriptionManager.CB_ALERT_REMINDER_INTERVAL,
                SubscriptionManager.CB_ALERT_VIBRATE, SubscriptionManager.CB_ALERT_SPEECH,
                SubscriptionManager.CB_ETWS_TEST_ALERT, SubscriptionManager.CB_CHANNEL_50_ALERT,
                SubscriptionManager.CB_CMAS_TEST_ALERT, SubscriptionManager.CB_OPT_OUT_DIALOG,
                SubscriptionManager.SIM_PROVISIONING_STATUS, SubscriptionManager.IS_EMBEDDED,
                SubscriptionManager.ACCESS_RULES};

        /* internal util function */
        private MatrixCursor convertFromContentToCursor(ContentValues initialValues) {
            MatrixCursor cursor = null;
            ArrayList<Object> values = new ArrayList<Object>();

            if (initialValues != null && mKeyMappingSet.length != 0) {
                cursor = new MatrixCursor(mKeyMappingSet);
                /* push value from contentValues to matrixCursors */
                for (String key : mKeyMappingSet) {
                    if (initialValues.containsKey(key)) {
                        values.add(initialValues.get(key));
                    } else {
                        values.add(null);
                    }
                }
                cursor.addRow(values.toArray());
            }
            return cursor;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            if (mSubscriptionArray.size() > 0) {
                mSubscriptionArray.remove(0);
                return 1;
            }
            return -1;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            values.put(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID, 0);
            mSubscriptionArray.add(values);
            return uri;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            if (mSubscriptionArray.size() > 0) {
                return convertFromContentToCursor(mSubscriptionArray.get(0));
            }
            return null;
        }

        @Override
        public Bundle call(String method, String request, Bundle args) {
            return null;
        }

        @Override
        public int update(android.net.Uri uri, android.content.ContentValues values,
                          java.lang.String selection, java.lang.String[] selectionArgs) {
            if (mSubscriptionArray.size() > 0) {
                ContentValues val = mSubscriptionArray.get(0);
                for (String key : values.keySet()) {
                    val.put(key, values.getAsString(key));
                    Log.d(TAG, "update the values..." + key + "..." + values.getAsString(key));
                }
                mSubscriptionArray.set(0, val);
                return 1;
            }
            return -1;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("SubscriptionControllerTest");

        doReturn(SINGLE_SIM).when(mTelephonyManager).getSimCount();
        doReturn(SINGLE_SIM).when(mTelephonyManager).getPhoneCount();

        replaceInstance(SubscriptionController.class, "sInstance", null, null);

        SubscriptionController.init(mContext, null);
        mSubscriptionControllerUT = SubscriptionController.getInstance();
        mCallingPackage = mContext.getOpPackageName();

        doReturn(1).when(mProxyController).getMaxRafSupported();
        mContextFixture.putIntArrayResource(com.android.internal.R.array.sim_colors, new int[]{5});

        mSubscriptionControllerUT.getInstance().updatePhonesAvailability(new Phone[]{mPhone});
        mMockContentResolver = (MockContentResolver) mContext.getContentResolver();
        mMockContentResolver.addProvider(SubscriptionManager.CONTENT_URI.getAuthority(),
                new FakeSubscriptionContentProvider());
    }

    @After
    public void tearDown() throws Exception {
        /* should clear fake content provider and resolver here */
        mContext.getContentResolver().delete(SubscriptionManager.CONTENT_URI, null, null);

        /* clear settings for default voice/data/sms sub ID */
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        mSubscriptionControllerUT = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testInsertSim() {
        int slotID = mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage);

        //verify there is no sim inserted in the SubscriptionManager
        assertEquals(0, slotID);

        //insert one Subscription Info
        mSubscriptionControllerUT.addSubInfoRecord("test", slotID);

        //verify there is one sim
        assertEquals(1, mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));

        //sanity for slot id and sub id
        List<SubscriptionInfo> mSubList = mSubscriptionControllerUT
                .getActiveSubscriptionInfoList(mCallingPackage);
        assertTrue(mSubList != null && mSubList.size() > 0);
        for (int i = 0; i < mSubList.size(); i++) {
            assertTrue(SubscriptionManager.isValidSubscriptionId(
                    mSubList.get(i).getSubscriptionId()));
            assertTrue(SubscriptionManager.isValidSlotIndex(mSubList.get(i).getSimSlotIndex()));
        }
    }

    @Test @SmallTest
    public void testChangeSIMProperty() {
        int dataRoaming = 1;
        int iconTint = 1;
        String disName = "TESTING";
        String disNum = "12345";

        testInsertSim();
        /* Get SUB ID */
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        /* Setting */
        mSubscriptionControllerUT.setDisplayName(disName, subID);
        mSubscriptionControllerUT.setDataRoaming(dataRoaming, subID);
        mSubscriptionControllerUT.setDisplayNumber(disNum, subID);
        mSubscriptionControllerUT.setIconTint(iconTint, subID);

        /* Getting, there is no direct getter function for each fields of property */
        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(subID, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(dataRoaming, subInfo.getDataRoaming());
        assertEquals(disName, subInfo.getDisplayName());
        assertEquals(iconTint, subInfo.getIconTint());
        assertEquals(disNum, subInfo.getNumber());

        /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());
    }

    @Test @SmallTest
    public void testSetGetDisplayNameSrc() {
        testInsertSim();

        /* Get SUB ID */
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        /* Setting */
        String disName = "TESTING";
        long nameSource = 1;
        mSubscriptionControllerUT.setDisplayNameUsingSrc(disName, subID, nameSource);
        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(subID, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(disName, subInfo.getDisplayName());
        assertEquals(nameSource, subInfo.getNameSource());

        /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());

    }

    @Test @SmallTest
    public void testCleanUpSIM() {
        testInsertSim();
        assertFalse(mSubscriptionControllerUT.isActiveSubId(1));
        mSubscriptionControllerUT.clearSubInfo();
        assertFalse(mSubscriptionControllerUT.isActiveSubId(0));
        assertEquals(SubscriptionManager.SIM_NOT_INSERTED,
                mSubscriptionControllerUT.getSlotIndex(0));
    }

    @Test @SmallTest
    public void testDefaultSubID() {
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        /* insert one sim */
        testInsertSim();
        // if support single sim, sms/data/voice default sub should be the same
        assertNotSame(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSubId());
        assertEquals(mSubscriptionControllerUT.getDefaultDataSubId(),
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(mSubscriptionControllerUT.getDefaultDataSubId(),
                mSubscriptionControllerUT.getDefaultVoiceSubId());
    }

    @Test @SmallTest
    public void testSetGetMCCMNC() {
        testInsertSim();
        String mCcMncVERIZON = "310004";
        mSubscriptionControllerUT.setMccMnc(mCcMncVERIZON, 0);

        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(0, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(Integer.parseInt(mCcMncVERIZON.substring(0, 3)), subInfo.getMcc());
        assertEquals(Integer.parseInt(mCcMncVERIZON.substring(3)), subInfo.getMnc());

         /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());
    }

    @Test
    @SmallTest
    public void testSetDefaultDataSubId() throws Exception {
        doReturn(1).when(mPhone).getSubId();

        mSubscriptionControllerUT.setDefaultDataSubId(1);

        verify(mPhone, times(1)).updateDataConnectionTracker();
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendStickyBroadcastAsUser(
                captorIntent.capture(), eq(UserHandle.ALL));

        Intent intent = captorIntent.getValue();
        assertEquals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED, intent.getAction());

        Bundle b = intent.getExtras();

        assertTrue(b.containsKey(PhoneConstants.SUBSCRIPTION_KEY));
        assertEquals(1, b.getInt(PhoneConstants.SUBSCRIPTION_KEY));
    }
}
