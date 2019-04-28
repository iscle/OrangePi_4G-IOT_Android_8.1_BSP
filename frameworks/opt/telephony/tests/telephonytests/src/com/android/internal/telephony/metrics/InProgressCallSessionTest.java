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

package com.android.internal.telephony.metrics;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.TelephonyProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InProgressCallSessionTest extends TelephonyTest {

    private InProgressCallSession mCallSession;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mCallSession = new InProgressCallSession(mPhone.getPhoneId());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    // Test add event
    @Test
    @SmallTest
    public void testAddEvent() {
        CallSessionEventBuilder builder = new CallSessionEventBuilder(
                TelephonyProto.TelephonyCallSession.Event.Type.RIL_RESPONSE)
                .setRilRequest(1)
                .setRilRequestId(2)
                .setRilError(3);
        mCallSession.addEvent(builder);
        assertEquals(builder.build(), mCallSession.events.getFirst());
        assertFalse(mCallSession.isEventsDropped());
    }

    // Test dropped event scenario
    @Test
    @SmallTest
    public void testEventDropped() {
        for (int i = 0; i < 301; i++) {
            CallSessionEventBuilder builder = new CallSessionEventBuilder(
                    TelephonyProto.TelephonyCallSession.Event.Type.RIL_RESPONSE)
                    .setRilRequest(1)
                    .setRilRequestId(i + 1)
                    .setRilError(3);
            mCallSession.addEvent(builder);
        }

        assertTrue(mCallSession.isEventsDropped());
        assertEquals(2, mCallSession.events.getFirst().rilRequestId);
    }
}
