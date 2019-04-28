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

import android.app.AppOpsManager;
import android.content.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import org.junit.Test;
import org.mockito.Mock;


import android.test.suitebuilder.annotation.SmallTest;

public class PhoneSubInfoControllerTest extends TelephonyTest {
    private PhoneSubInfoController mPhoneSubInfoControllerUT;
    private AppOpsManager mAppOsMgr;

    @Mock
    GsmCdmaPhone mSecondPhone;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        /* mPhone -> PhoneId: 0 -> SubId:0
           mSecondPhone -> PhoneId:1 -> SubId: 1*/
        doReturn(0).when(mSubscriptionController).getPhoneId(eq(0));
        doReturn(1).when(mSubscriptionController).getPhoneId(eq(1));
        doReturn(2).when(mTelephonyManager).getPhoneCount();

        mServiceManagerMockedServices.put("isub", mSubscriptionController);
        doReturn(mSubscriptionController).when(mSubscriptionController)
                .queryLocalInterface(anyString());
        doReturn(mContext).when(mSecondPhone).getContext();

        mAppOsMgr = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);

        mPhoneSubInfoControllerUT = new PhoneSubInfoController(mContext,
                new Phone[]{mPhone, mSecondPhone});
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetDeviceId() {
        doReturn("353626073736741").when(mPhone).getDeviceId();
        doReturn("353626073736742").when(mSecondPhone).getDeviceId();

        assertEquals("353626073736741", mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG));
        assertEquals("353626073736742", mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetDeviceIdWithOutPermission() {
        doReturn("353626073736741").when(mPhone).getDeviceId();
        doReturn("353626073736742").when(mSecondPhone).getDeviceId();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getDeviceId", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getDeviceId", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("353626073736741", mPhoneSubInfoControllerUT.getDeviceIdForPhone(0, TAG));
        assertEquals("353626073736742", mPhoneSubInfoControllerUT.getDeviceIdForPhone(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetNai() {
        doReturn("aaa@example.com").when(mPhone).getNai();
        assertEquals("aaa@example.com", mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG));

        doReturn("bbb@example.com").when(mSecondPhone).getNai();
        assertEquals("bbb@example.com", mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetNaiWithOutPermission() {
        doReturn("aaa@example.com").when(mPhone).getNai();
        doReturn("bbb@example.com").when(mSecondPhone).getNai();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getNai", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getNai", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("aaa@example.com", mPhoneSubInfoControllerUT.getNaiForSubscriber(0, TAG));
        assertEquals("bbb@example.com", mPhoneSubInfoControllerUT.getNaiForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetImei() {
        doReturn("990000862471854").when(mPhone).getImei();
        assertEquals("990000862471854", mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG));

        doReturn("990000862471855").when(mSecondPhone).getImei();
        assertEquals("990000862471855", mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetImeiWithOutPermission() {
        doReturn("990000862471854").when(mPhone).getImei();
        doReturn("990000862471855").when(mSecondPhone).getImei();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getImei", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getImei", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("990000862471854", mPhoneSubInfoControllerUT.getImeiForSubscriber(0, TAG));
        assertEquals("990000862471855", mPhoneSubInfoControllerUT.getImeiForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetDeviceSvn() {
        doReturn("00").when(mPhone).getDeviceSvn();
        assertEquals("00", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG));

        doReturn("01").when(mSecondPhone).getDeviceSvn();
        assertEquals("01", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetDeviceSvnWithOutPermission() {
        doReturn("00").when(mPhone).getDeviceSvn();
        doReturn("01").when(mSecondPhone).getDeviceSvn();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getDeviceSvn", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getDeviceSvn", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("00", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(0, TAG));
        assertEquals("01", mPhoneSubInfoControllerUT.getDeviceSvnUsingSubId(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetSubscriberId() {
        //IMSI
        doReturn("310260426283121").when(mPhone).getSubscriberId();
        assertEquals("310260426283121", mPhoneSubInfoControllerUT
                .getSubscriberIdForSubscriber(0, TAG));

        doReturn("310260426283122").when(mSecondPhone).getSubscriberId();
        assertEquals("310260426283122", mPhoneSubInfoControllerUT
                .getSubscriberIdForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetSubscriberIdWithOutPermission() {
        doReturn("310260426283121").when(mPhone).getSubscriberId();
        doReturn("310260426283122").when(mSecondPhone).getSubscriberId();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getSubscriberId", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getSubscriberId", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("310260426283121",
                mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(0, TAG));
        assertEquals("310260426283122",
                mPhoneSubInfoControllerUT.getSubscriberIdForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetIccSerialNumber() {
        //IccId
        doReturn("8991101200003204510").when(mPhone).getIccSerialNumber();
        assertEquals("8991101200003204510", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(0, TAG));

        doReturn("8991101200003204511").when(mSecondPhone).getIccSerialNumber();
        assertEquals("8991101200003204511", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetIccSerialNumberWithOutPermission() {
        doReturn("8991101200003204510").when(mPhone).getIccSerialNumber();
        doReturn("8991101200003204511").when(mSecondPhone).getIccSerialNumber();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getIccSerialNumber", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getIccSerialNumber", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getIccSerialNumberForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("8991101200003204510", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(0, TAG));
        assertEquals("8991101200003204511", mPhoneSubInfoControllerUT
                .getIccSerialNumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testLine1Number() {
        doReturn("+18051234567").when(mPhone).getLine1Number();
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));

        doReturn("+18052345678").when(mSecondPhone).getLine1Number();
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testLine1NumberWithOutPermission() {
        doReturn("+18051234567").when(mPhone).getLine1Number();
        doReturn("+18052345678").when(mSecondPhone).getLine1Number();

        /* case 1: no READ_PRIVILEGED_PHONE_STATE & READ_PHONE_STATE &
        READ_SMS and no OP_WRITE_SMS & OP_READ_SMS from appOsMgr */
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_SMS), anyInt(), eq(TAG));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_WRITE_SMS), anyInt(), eq(TAG));
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        try {
            mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
        }

        /* case 2: only enable WRITE_SMS permission */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_WRITE_SMS), anyInt(), eq(TAG));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));

        /* case 3: only enable READ_PRIVILEGED_PHONE_STATE */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_WRITE_SMS), anyInt(), eq(TAG));
        mContextFixture.addCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));

        /* case 4: only enable READ_PHONE_STATE permission */
        mContextFixture.removeCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));

        /* case 5: enable appOsMgr READ_PHONE_PERMISSION & READ_PHONE_STATE */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));

        /* case 6: only enable READ_SMS */
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        mContextFixture.removeCallingOrSelfPermission(READ_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(READ_SMS);
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));

        /* case 7: enable READ_SMS and OP_READ_SMS */
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_SMS), anyInt(), eq(TAG));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getLine1NumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testLine1AlphaTag() {
        doReturn("LINE1_SIM_0").when(mPhone).getLine1AlphaTag();
        assertEquals("LINE1_SIM_0", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(0, TAG));

        doReturn("LINE1_SIM_1").when(mSecondPhone).getLine1AlphaTag();
        assertEquals("LINE1_SIM_1", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testLine1AlphaTagWithOutPermission() {
        doReturn("LINE1_SIM_0").when(mPhone).getLine1AlphaTag();
        doReturn("LINE1_SIM_1").when(mSecondPhone).getLine1AlphaTag();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getLine1AlphaTag", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getLine1AlphaTag", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getLine1AlphaTagForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("LINE1_SIM_0", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(0, TAG));
        assertEquals("LINE1_SIM_1", mPhoneSubInfoControllerUT
                .getLine1AlphaTagForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testMsisdn() {
        doReturn("+18051234567").when(mPhone).getMsisdn();
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG));

        doReturn("+18052345678").when(mSecondPhone).getMsisdn();
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testMsisdnWithOutPermission() {
        doReturn("+18051234567").when(mPhone).getMsisdn();
        doReturn("+18052345678").when(mSecondPhone).getMsisdn();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getMsisdn", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getMsisdn", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT.getMsisdnForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT.getMsisdnForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailNumber() {
        doReturn("+18051234567").when(mPhone).getVoiceMailNumber();
        assertEquals("+18051234567", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(0, TAG));

        doReturn("+18052345678").when(mSecondPhone).getVoiceMailNumber();
        assertEquals("+18052345678", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailNumberWithOutPermission() {
        doReturn("+18051234567").when(mPhone).getVoiceMailNumber();
        doReturn("+18052345678").when(mSecondPhone).getVoiceMailNumber();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getVoiceMailNumber", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getVoiceMailNumber", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getVoiceMailNumberForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("+18051234567", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(0, TAG));
        assertEquals("+18052345678", mPhoneSubInfoControllerUT
                .getVoiceMailNumberForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailAlphaTag() {
        doReturn("VM_SIM_0").when(mPhone).getVoiceMailAlphaTag();
        assertEquals("VM_SIM_0", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(0, TAG));

        doReturn("VM_SIM_1").when(mSecondPhone).getVoiceMailAlphaTag();
        assertEquals("VM_SIM_1", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(1, TAG));
    }

    @Test
    @SmallTest
    public void testGetVoiceMailAlphaTagWithOutPermission() {
        doReturn("VM_SIM_0").when(mPhone).getVoiceMailAlphaTag();
        doReturn("VM_SIM_1").when(mSecondPhone).getVoiceMailAlphaTag();

        //case 1: no READ_PRIVILEGED_PHONE_STATE, READ_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        try {
            mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(0, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getVoiceMailAlphaTag", ex.getMessage());
        }

        try {
            mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(1, TAG);
            Assert.fail("expected Security Exception Thrown");
        } catch (Exception ex) {
            assertTrue(ex instanceof SecurityException);
            assertEquals(READ_PHONE_STATE + " denied: getVoiceMailAlphaTag", ex.getMessage());
        }

        //case 2: no READ_PRIVILEGED_PHONE_STATE & appOsMgr READ_PHONE_PERMISSION
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));

        assertNull(mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(0, TAG));
        assertNull(mPhoneSubInfoControllerUT.getVoiceMailAlphaTagForSubscriber(1, TAG));

        //case 3: no READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(READ_PHONE_STATE);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOsMgr).noteOp(
                eq(AppOpsManager.OP_READ_PHONE_STATE), anyInt(), eq(TAG));
        assertEquals("VM_SIM_0", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(0, TAG));
        assertEquals("VM_SIM_1", mPhoneSubInfoControllerUT
                .getVoiceMailAlphaTagForSubscriber(1, TAG));
    }
}
