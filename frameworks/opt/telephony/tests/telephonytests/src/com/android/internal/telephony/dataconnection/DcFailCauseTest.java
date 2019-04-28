/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class DcFailCauseTest extends TelephonyTest {

    private class DcFailCauseData {
        public final int mCause;
        public final boolean mPermanentFailure;
        public final boolean mEventLoggable;
        DcFailCauseData(int cause, boolean permanentFailure, boolean eventLoggable) {
            mCause = cause;
            mPermanentFailure = permanentFailure;
            mEventLoggable = eventLoggable;
        }
    }

    private ArrayList<DcFailCauseData> mFailCauseDataList;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        mFailCauseDataList = new ArrayList<>();

        mFailCauseDataList.add(new DcFailCauseData(0, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x08, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x0E, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x19, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1A, false, true));
        mFailCauseDataList.add(new DcFailCauseData(0x1B, true, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1C, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x1D, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x1E, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x1F, false, true));
        mFailCauseDataList.add(new DcFailCauseData(0x20, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x21, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x22, false, true));
        mFailCauseDataList.add(new DcFailCauseData(0x23, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x24, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x25, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x26, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x27, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x28, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x29, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x2A, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x2B, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x2C, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x2D, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x2E, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x32, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x33, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x34, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x35, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x36, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x37, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x41, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x42, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x51, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x5F, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x60, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x61, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x62, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x63, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x64, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x65, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x6F, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x70, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x71, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x72, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x73, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x74, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x75, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x76, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x77, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x78, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x79, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x7A, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1001, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1002, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1003, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1004, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1005, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1006, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1007, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1008, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x1009, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100A, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100B, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100C, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100D, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100E, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x100F, false, false));
        mFailCauseDataList.add(new DcFailCauseData(-1, false, false));
        mFailCauseDataList.add(new DcFailCauseData(-2, false, false));
        mFailCauseDataList.add(new DcFailCauseData(-3, true, true));
        mFailCauseDataList.add(new DcFailCauseData(-4, false, false));
        mFailCauseDataList.add(new DcFailCauseData(-5, true, true));
        mFailCauseDataList.add(new DcFailCauseData(-6, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0xFFFF, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x10000, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x10001, true, false));
        mFailCauseDataList.add(new DcFailCauseData(0x10002, true, true));
        mFailCauseDataList.add(new DcFailCauseData(0x10003, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x10004, false, false));
        mFailCauseDataList.add(new DcFailCauseData(0x10005, false, false));
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testPermanentFailDefault() throws Exception {
        for (DcFailCauseData data : mFailCauseDataList) {
            assertEquals("cause = " + data.mCause, data.mPermanentFailure, DcFailCause.fromInt(
                    data.mCause).isPermanentFailure(mContext, mPhone.getSubId()));
        }
        assertFalse(DcFailCause.fromInt(123456).isPermanentFailure(mContext, mPhone.getSubId()));
    }

    @Test
    @SmallTest
    public void testPermanentFailConfigured() throws Exception {

        doReturn(2).when(mPhone).getSubId();
        PersistableBundle mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putStringArray(
                CarrierConfigManager.KEY_CARRIER_DATA_CALL_PERMANENT_FAILURE_STRINGS,
                new String[]{"SERVICE_OPTION_NOT_SUBSCRIBED", "TETHERED_CALL_ACTIVE"});

        // Run it twice to make sure the cached carrier config is working as expected.
        for (int i = 0; i < 2; i++) {
            for (DcFailCauseData data : mFailCauseDataList) {
                if (DcFailCause.fromInt(data.mCause).equals(
                        DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED) ||
                        DcFailCause.fromInt(data.mCause).equals(DcFailCause.TETHERED_CALL_ACTIVE)) {
                    assertTrue("cause = " + data.mCause, DcFailCause.fromInt(data.mCause).
                            isPermanentFailure(mContext, mPhone.getSubId()));
                } else {
                    assertFalse("cause = " + data.mCause, DcFailCause.fromInt(data.mCause).
                            isPermanentFailure(mContext, mPhone.getSubId()));
                }
            }
        }
    }

    @Test
    @SmallTest
    public void testEventLoggable() throws Exception {
        for (DcFailCauseData data : mFailCauseDataList) {
            assertEquals("cause = " + data.mCause, data.mEventLoggable,
                    DcFailCause.fromInt(data.mCause).isEventLoggable());
        }
        assertFalse(DcFailCause.fromInt(123456).isEventLoggable());
    }

    @Test
    @SmallTest
    public void testGetErrorCode() throws Exception {
        for (DcFailCauseData data : mFailCauseDataList) {
            assertEquals(data.mCause, DcFailCause.fromInt(data.mCause).getErrorCode());
        }
        assertEquals(DcFailCause.UNKNOWN.getErrorCode(),
                DcFailCause.fromInt(123456).getErrorCode());
    }
}
