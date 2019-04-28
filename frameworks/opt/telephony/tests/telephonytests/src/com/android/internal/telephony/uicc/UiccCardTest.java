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
package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.cat.CatService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class UiccCardTest extends TelephonyTest {
    private UiccCard mUicccard;

    public UiccCardTest() {
        super();
    }

    private IccIoResult mIccIoResult;

    private UiccCardHandlerThread mTestHandlerThread;
    private Handler mHandler;
    private static final int UICCCARD_UPDATE_CARD_STATE_EVENT = 1;
    private static final int UICCCARD_UPDATE_CARD_APPLICATION_EVENT = 2;
    private static final int UICCCARD_CARRIER_PRIVILEDGE_LOADED_EVENT = 3;
    private static final int UICCCARD_ABSENT = 4;

    @Mock
    private CatService mCAT;
    @Mock
    private IccCardStatus mIccCardStatus;
    @Mock
    private Handler mMockedHandler;


    private class UiccCardHandlerThread extends HandlerThread {

        private UiccCardHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mUicccard = new UiccCard(mContextFixture.getTestDouble(),
                                     mSimulatedCommands, mIccCardStatus, 0 /* phoneId */);
            /* create a custom handler for the Handler Thread */
            mHandler = new Handler(mTestHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case UICCCARD_UPDATE_CARD_STATE_EVENT:
                            /* Upon handling this event, new CarrierPrivilegeRule
                            will be created with the looper of HandlerThread */
                            logd("Update UICC Card State");
                            mUicccard.update(mContextFixture.getTestDouble(),
                                    mSimulatedCommands, mIccCardStatus);
                            setReady(true);
                            break;
                        case UICCCARD_UPDATE_CARD_APPLICATION_EVENT:
                            logd("Update UICC Card Applications");
                            mUicccard.update(mContextFixture.getTestDouble(),
                                    mSimulatedCommands, mIccCardStatus);
                            setReady(true);
                            break;
                        default:
                            logd("Unknown Event " + msg.what);
                    }
                }
            };

            setReady(true);
            logd("create UiccCard");
        }
    }

    private IccCardApplicationStatus composeUiccApplicationStatus(
            IccCardApplicationStatus.AppType appType,
            IccCardApplicationStatus.AppState appState, String aid) {
        IccCardApplicationStatus mIccCardAppStatus = new IccCardApplicationStatus();
        mIccCardAppStatus.aid = aid;
        mIccCardAppStatus.app_type = appType;
        mIccCardAppStatus.app_state = appState;
        mIccCardAppStatus.pin1 = mIccCardAppStatus.pin2 =
                IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        return mIccCardAppStatus;
    }

    @Before
    public void setUp() throws Exception {

        super.setUp(getClass().getSimpleName());
        /* initially there are no application available */
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{};
        mIccCardStatus.mCdmaSubscriptionAppIndex =
                mIccCardStatus.mImsSubscriptionAppIndex =
                        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = -1;

        mIccIoResult = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes("FF40"));
        mSimulatedCommands.setIccIoResultForApduLogicalChannel(mIccIoResult);
        /* starting the Handler Thread */
        mTestHandlerThread = new UiccCardHandlerThread(TAG);
        mTestHandlerThread.start();

        waitUntilReady();
        replaceInstance(UiccCard.class, "mCatService", mUicccard, mCAT);
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void tesUiccCartdInfoSanity() {
        /* before update sanity test */
        assertEquals(0, mUicccard.getNumApplications());
        assertNull(mUicccard.getCardState());
        assertNull(mUicccard.getUniversalPinState());
        assertNull(mUicccard.getOperatorBrandOverride());
        /* CarrierPrivilegeRule equals null, return true */
        assertTrue(mUicccard.areCarrierPriviligeRulesLoaded());
        for (IccCardApplicationStatus.AppType mAppType :
                IccCardApplicationStatus.AppType.values()) {
            assertFalse(mUicccard.isApplicationOnIcc(mAppType));
        }
    }

    @Test @SmallTest
    public void testUpdateUiccCardApplication() {
        /* update app status and index */
        IccCardApplicationStatus cdmaApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_CSIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA0");
        IccCardApplicationStatus imsApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_ISIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA1");
        IccCardApplicationStatus umtsApp = composeUiccApplicationStatus(
                IccCardApplicationStatus.AppType.APPTYPE_USIM,
                IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN, "0xA2");
        mIccCardStatus.mApplications = new IccCardApplicationStatus[]{cdmaApp, imsApp, umtsApp};
        mIccCardStatus.mCdmaSubscriptionAppIndex = 0;
        mIccCardStatus.mImsSubscriptionAppIndex = 1;
        mIccCardStatus.mGsmUmtsSubscriptionAppIndex = 2;
        Message mCardUpdate = mHandler.obtainMessage(UICCCARD_UPDATE_CARD_APPLICATION_EVENT);
        setReady(false);
        mCardUpdate.sendToTarget();

        waitUntilReady();

        assertEquals(3, mUicccard.getNumApplications());
        assertTrue(mUicccard.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_CSIM));
        assertTrue(mUicccard.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_ISIM));
        assertTrue(mUicccard.isApplicationOnIcc(IccCardApplicationStatus.AppType.APPTYPE_USIM));
    }

    @Test @SmallTest
    public void testUpdateUiccCardState() {
        int mChannelId = 1;
        /* set card as present */
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        /* Mock open Channel ID 1 */
        mSimulatedCommands.setOpenChannelId(mChannelId);
        Message mCardUpdate = mHandler.obtainMessage(UICCCARD_UPDATE_CARD_STATE_EVENT);
        setReady(false);
        mCardUpdate.sendToTarget();
        /* try to create a new CarrierPrivilege, loading state -> loaded state */
        /* wait till the async result and message delay */
        waitUntilReady();

        assertEquals(IccCardStatus.CardState.CARDSTATE_PRESENT, mUicccard.getCardState());

        waitForMs(50);

        assertTrue(mUicccard.areCarrierPriviligeRulesLoaded());
        verify(mSimulatedCommandsVerifier, times(1)).iccOpenLogicalChannel(isA(String.class),
                anyInt(), isA(Message.class));
        verify(mSimulatedCommandsVerifier, times(1)).iccTransmitApduLogicalChannel(
                eq(mChannelId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(),
                isA(Message.class)
        );
    }

    @Test @SmallTest
    public void testUpdateUiccCardPinState() {
        mIccCardStatus.mUniversalPinState = IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        mUicccard.update(mContextFixture.getTestDouble(), mSimulatedCommands, mIccCardStatus);
        assertEquals(IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED,
                mUicccard.getUniversalPinState());
    }

    @Test @SmallTest
    public void testCarrierPriviledgeLoadedListener() {
        mUicccard.registerForCarrierPrivilegeRulesLoaded(mMockedHandler,
                UICCCARD_CARRIER_PRIVILEDGE_LOADED_EVENT, null);
        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long> mCaptorLong = ArgumentCaptor.forClass(Long.class);
        testUpdateUiccCardState();
        verify(mMockedHandler, atLeast(1)).sendMessageDelayed(mCaptorMessage.capture(),
                mCaptorLong.capture());
        assertEquals(UICCCARD_CARRIER_PRIVILEDGE_LOADED_EVENT, mCaptorMessage.getValue().what);
    }

    @Test @SmallTest
    public void testCardAbsentListener() {
        mUicccard.registerForAbsent(mMockedHandler, UICCCARD_ABSENT, null);
        /* assume hotswap capable, avoid bootup on card removal */
        mContextFixture.putBooleanResource(com.android.internal.R.bool.config_hotswapCapable, true);
        mSimulatedCommands.setRadioPower(true, null);

        /* Mock Card State transition from card_present to card_absent */
        logd("UICC Card Present update");
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_PRESENT;
        Message mCardUpdate = mHandler.obtainMessage(UICCCARD_UPDATE_CARD_STATE_EVENT);
        mCardUpdate.sendToTarget();
        waitForMs(50);

        logd("UICC Card absent update");
        mIccCardStatus.mCardState = IccCardStatus.CardState.CARDSTATE_ABSENT;
        mUicccard.update(mContextFixture.getTestDouble(), mSimulatedCommands, mIccCardStatus);
        waitForMs(50);

        ArgumentCaptor<Message> mCaptorMessage = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Long> mCaptorLong = ArgumentCaptor.forClass(Long.class);
        verify(mMockedHandler, atLeast(1)).sendMessageDelayed(mCaptorMessage.capture(),
                                                             mCaptorLong.capture());
        assertEquals(UICCCARD_ABSENT, mCaptorMessage.getValue().what);
    }
}
