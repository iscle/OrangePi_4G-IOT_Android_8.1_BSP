/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.test.filters.FlakyTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

public class IccCardProxyTest extends TelephonyTest {
    private IccCardProxy mIccCardProxyUT;
    // private UiccCard mUiccCard;
    private IccCardProxyHandlerThread mIccCardProxyHandlerThread;
    private static final int PHONE_ID = 0;
    private static final int PHONE_COUNT = 1;

    private static final int SCARY_SLEEP_MS = 200;
    // Must match IccCardProxy.EVENT_ICC_CHANGED
    private static final int EVENT_ICC_CHANGED = 3;

    @Mock private Handler mMockedHandler;
    @Mock private IccCardStatus mIccCardStatus;
    @Mock private UiccCard mUiccCard;
    @Mock private UiccCardApplication mUiccCardApplication;

    private class IccCardProxyHandlerThread extends HandlerThread {

        private IccCardProxyHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            /* create a new UICC Controller associated with the simulated Commands */
            mIccCardProxyUT = new IccCardProxy(mContext, mSimulatedCommands, PHONE_ID);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        doReturn(PHONE_COUNT).when(mTelephonyManager).getPhoneCount();
        doReturn(PHONE_COUNT).when(mTelephonyManager).getSimCount();
        mSimulatedCommands.setIccCardStatus(mIccCardStatus);
        mIccCardProxyHandlerThread = new IccCardProxyHandlerThread(TAG);
        mIccCardProxyHandlerThread.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mIccCardProxyHandlerThread.quitSafely();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testInitialCardState() {
        assertEquals(mIccCardProxyUT.getState(), State.UNKNOWN);
    }

    @Test
    @SmallTest
    public void testPowerOn() {
        mSimulatedCommands.setRadioPower(true, null);
        mSimulatedCommands.notifyRadioOn();
        when(mUiccController.getUiccCard(anyInt())).thenReturn(mUiccCard);
        mIccCardProxyUT.sendMessage(mIccCardProxyUT.obtainMessage(EVENT_ICC_CHANGED));
        waitForMs(SCARY_SLEEP_MS);
        assertEquals(CommandsInterface.RadioState.RADIO_ON, mSimulatedCommands.getRadioState());
        assertEquals(mIccCardProxyUT.getState(), State.NOT_READY);
        logd("IccCardProxy state = " + mIccCardProxyUT.getState());
    }

    @Test
    @SmallTest
    public void testCardLoaded() {
        testPowerOn();
        when(mUiccCard.getCardState()).thenReturn(CardState.CARDSTATE_PRESENT);
        mIccCardProxyUT.sendMessage(mIccCardProxyUT.obtainMessage(EVENT_ICC_CHANGED));
        waitForMs(SCARY_SLEEP_MS);
        assertEquals(mIccCardProxyUT.getState(), State.NOT_READY);
    }

    @Test
    @SmallTest
    public void testAppNotLoaded() {
        testPowerOn();
        when(mUiccCard.getCardState()).thenReturn(CardState.CARDSTATE_PRESENT);
        mIccCardProxyUT.sendMessage(mIccCardProxyUT.obtainMessage(EVENT_ICC_CHANGED));
        when(mUiccCardApplication.getState()).thenReturn(AppState.APPSTATE_UNKNOWN);
        when(mUiccCard.getApplication(anyInt())).thenReturn(mUiccCardApplication);

        waitForMs(SCARY_SLEEP_MS);
        assertEquals(mIccCardProxyUT.getState(), State.NOT_READY);
    }

    @Test
    @Ignore
    @FlakyTest
    @SmallTest
    public void testAppReady() {
        testPowerOn();
        when(mUiccCard.getCardState()).thenReturn(CardState.CARDSTATE_PRESENT);
        mIccCardProxyUT.sendMessage(mIccCardProxyUT.obtainMessage(EVENT_ICC_CHANGED));
        when(mUiccCardApplication.getState()).thenReturn(AppState.APPSTATE_READY);
        when(mUiccCard.getApplication(anyInt())).thenReturn(mUiccCardApplication);

        waitForMs(SCARY_SLEEP_MS);
        assertEquals(mIccCardProxyUT.getState(), State.READY);
    }
}
