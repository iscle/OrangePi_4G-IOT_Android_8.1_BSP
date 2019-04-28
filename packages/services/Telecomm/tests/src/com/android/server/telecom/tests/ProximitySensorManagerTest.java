/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.telecom.tests;

import android.os.PowerManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ProximitySensorManager;
import com.android.server.telecom.TelecomWakeLock;

import org.mockito.Mock;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProximitySensorManagerTest extends TelecomTestCase{

    @Mock CallsManager mCallsManager;
    @Mock Call mCall;
    @Mock TelecomWakeLock.WakeLockAdapter mWakeLockAdapter;
    private ProximitySensorManager mProximitySensorManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TelecomWakeLock telecomWakeLock = new TelecomWakeLock(
                null, // Context is never used due to mock WakeLockAdapter
                mWakeLockAdapter, PowerManager.FULL_WAKE_LOCK,
                InCallWakeLockControllerTest.class.getSimpleName());
        mProximitySensorManager = new ProximitySensorManager(telecomWakeLock, mCallsManager);
    }

    @Override
    public void tearDown() throws Exception {
        mProximitySensorManager = null;
        super.tearDown();
    }

    @SmallTest
    public void testTurnOnProximityWithCallsActive() throws Exception {
        when(mCallsManager.getCalls()).thenReturn(new ArrayList<Call>(){{
            add(mCall);
        }});
        when(mWakeLockAdapter.isHeld()).thenReturn(false);

        mProximitySensorManager.turnOn();

        verify(mWakeLockAdapter).acquire();
    }

    @SmallTest
    public void testTurnOnProximityWithNoCallsActive() throws Exception {
        when(mCallsManager.getCalls()).thenReturn(new ArrayList<Call>());
        when(mWakeLockAdapter.isHeld()).thenReturn(false);

        mProximitySensorManager.turnOn();

        verify(mWakeLockAdapter, never()).acquire();

    }

    @SmallTest
    public void testTurnOffProximityExplicitly() throws Exception {
        when(mWakeLockAdapter.isHeld()).thenReturn(true);

        mProximitySensorManager.turnOff(true);

        verify(mWakeLockAdapter).release(0);
    }

    @SmallTest
    public void testCallRemovedFromCallsManagerCallsActive() throws Exception {
        when(mCallsManager.getCalls()).thenReturn(new ArrayList<Call>(){{
            add(mCall);
        }});
        when(mWakeLockAdapter.isHeld()).thenReturn(true);

        mProximitySensorManager.onCallRemoved(mock(Call.class));

        verify(mWakeLockAdapter, never()).release(0);
    }

    @SmallTest
    public void testCallRemovedFromCallsManagerNoCallsActive() throws Exception {
        when(mCallsManager.getCalls()).thenReturn(new ArrayList<Call>());
        when(mWakeLockAdapter.isHeld()).thenReturn(true);

        mProximitySensorManager.onCallRemoved(mock(Call.class));

        verify(mWakeLockAdapter).release(0);
    }
}
