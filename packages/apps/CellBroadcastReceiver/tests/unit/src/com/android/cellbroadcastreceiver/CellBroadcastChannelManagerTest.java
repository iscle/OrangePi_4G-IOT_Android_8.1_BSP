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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.cellbroadcastreceiver.CellBroadcastAlertService.AlertType;
import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * APN retry manager tests
 */
public class CellBroadcastChannelManagerTest extends CellBroadcastTest {

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test getting cell broadcast additional channels from Carrier Config.
     */
    @Test
    @SmallTest
    public void testGetCellBroadcastChannelRanges() throws Exception {
        putResources(R.array.additional_cbs_channels_strings, new String[]{
                "12:type=earthquake, emergency=true",
                "456:type=tsunami, emergency=true",
                "0xAC00-0xAFED:type=other, emergency=false",
                "54-60:emergency=true",
                "100-200"
        });

        ArrayList<CellBroadcastChannelRange> list = CellBroadcastChannelManager.getInstance()
                .getCellBroadcastChannelRanges(mContext);

        assertEquals(12, list.get(0).mStartId);
        assertEquals(12, list.get(0).mEndId);
        assertEquals(AlertType.EARTHQUAKE, list.get(0).mAlertType);
        assertTrue(list.get(0).mIsEmergency);

        assertEquals(456, list.get(1).mStartId);
        assertEquals(456, list.get(1).mEndId);
        assertEquals(AlertType.TSUNAMI, list.get(1).mAlertType);
        assertTrue(list.get(1).mIsEmergency);

        assertEquals(0xAC00, list.get(2).mStartId);
        assertEquals(0xAFED, list.get(2).mEndId);
        assertEquals(AlertType.OTHER, list.get(2).mAlertType);
        assertFalse(list.get(2).mIsEmergency);

        assertEquals(54, list.get(3).mStartId);
        assertEquals(60, list.get(3).mEndId);
        assertEquals(AlertType.CMAS_DEFAULT, list.get(3).mAlertType);
        assertTrue(list.get(3).mIsEmergency);

        assertEquals(100, list.get(4).mStartId);
        assertEquals(200, list.get(4).mEndId);
        assertEquals(AlertType.CMAS_DEFAULT, list.get(4).mAlertType);
        assertFalse(list.get(4).mIsEmergency);
    }
}
