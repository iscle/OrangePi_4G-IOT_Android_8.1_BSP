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

package com.android.server.wifi;

import static org.mockito.Mockito.*;

import android.os.IBinder;
import android.os.RemoteException;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.app.IBatteryStats;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigStoreData}.
 */
@SmallTest
public class WifiMulticastLockManagerTest {
    @Mock WifiMulticastLockManager.FilterController mHandler;
    @Mock IBatteryStats mBatteryStats;
    WifiMulticastLockManager mManager;

    /**
     * Initialize |WifiMulticastLockManager| instance before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mManager = new WifiMulticastLockManager(mHandler, mBatteryStats);
    }

    /**
     * Test behavior when no locks are held.
     */
    @Test
    public void noLocks() {
        assertFalse(mManager.isMulticastEnabled());
        mManager.initializeFiltering();
        verify(mHandler, times(1)).startFilteringMulticastPackets();
    }

    /**
     * Test behavior when one lock is aquired then released.
     */
    @Test
    public void oneLock() throws RemoteException {
        IBinder binder = mock(IBinder.class);
        mManager.acquireLock(binder, "Test");
        assertTrue(mManager.isMulticastEnabled());
        verify(mHandler).stopFilteringMulticastPackets();
        mManager.initializeFiltering();
        verify(mHandler, times(0)).startFilteringMulticastPackets();
        verify(mBatteryStats).noteWifiMulticastEnabled(anyInt());
        verify(mBatteryStats, times(0)).noteWifiMulticastDisabled(anyInt());

        mManager.releaseLock();
        verify(mBatteryStats).noteWifiMulticastDisabled(anyInt());
        assertFalse(mManager.isMulticastEnabled());
    }
}

