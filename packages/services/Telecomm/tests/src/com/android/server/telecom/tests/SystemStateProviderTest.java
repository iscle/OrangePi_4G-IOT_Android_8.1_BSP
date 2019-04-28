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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.SystemStateProvider;
import com.android.server.telecom.SystemStateProvider.SystemStateListener;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for SystemStateProvider
 */
public class SystemStateProviderTest extends TelecomTestCase {

    SystemStateProvider mSystemStateProvider;

    @Mock Context mContext;
    @Mock SystemStateListener mSystemStateListener;
    @Mock UiModeManager mUiModeManager;
    @Mock Intent mIntentEnter;
    @Mock Intent mIntentExit;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    public void testListeners() throws Exception {
        SystemStateProvider systemStateProvider = new SystemStateProvider(mContext);

        assertFalse(systemStateProvider.removeListener(mSystemStateListener));
        systemStateProvider.addListener(mSystemStateListener);
        assertTrue(systemStateProvider.removeListener(mSystemStateListener));
        assertFalse(systemStateProvider.removeListener(mSystemStateListener));
    }

    @SmallTest
    public void testQuerySystemForCarMode_True() {
        when(mContext.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(mUiModeManager);
        when(mUiModeManager.getCurrentModeType()).thenReturn(Configuration.UI_MODE_TYPE_CAR);
        assertTrue(new SystemStateProvider(mContext).isCarMode());
    }

    @SmallTest
    public void testQuerySystemForCarMode_False() {
        when(mContext.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(mUiModeManager);
        when(mUiModeManager.getCurrentModeType()).thenReturn(Configuration.UI_MODE_TYPE_NORMAL);
        assertFalse(new SystemStateProvider(mContext).isCarMode());
    }

    @SmallTest
    public void testReceiverAndIntentFilter() {
        ArgumentCaptor<IntentFilter> intentFilter = ArgumentCaptor.forClass(IntentFilter.class);
        new SystemStateProvider(mContext);
        verify(mContext).registerReceiver(any(BroadcastReceiver.class), intentFilter.capture());

        assertEquals(2, intentFilter.getValue().countActions());
        assertEquals(UiModeManager.ACTION_ENTER_CAR_MODE, intentFilter.getValue().getAction(0));
        assertEquals(UiModeManager.ACTION_EXIT_CAR_MODE, intentFilter.getValue().getAction(1));
    }

    @SmallTest
    public void testOnEnterExitCarMode() {
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        new SystemStateProvider(mContext).addListener(mSystemStateListener);

        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));

        when(mIntentEnter.getAction()).thenReturn(UiModeManager.ACTION_ENTER_CAR_MODE);
        receiver.getValue().onReceive(mContext, mIntentEnter);
        verify(mSystemStateListener).onCarModeChanged(true);

        when(mIntentExit.getAction()).thenReturn(UiModeManager.ACTION_EXIT_CAR_MODE);
        receiver.getValue().onReceive(mContext, mIntentExit);
        verify(mSystemStateListener).onCarModeChanged(false);

        receiver.getValue().onReceive(mContext, new Intent("invalid action"));
    }
}
