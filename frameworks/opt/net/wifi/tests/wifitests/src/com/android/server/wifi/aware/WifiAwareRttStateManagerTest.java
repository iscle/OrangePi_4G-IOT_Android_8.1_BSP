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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.IRttManager;
import android.net.wifi.RttManager;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.util.test.BidirectionalAsyncChannelServer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for WifiAwareManager class.
 */
@SmallTest
public class WifiAwareRttStateManagerTest {
    private WifiAwareRttStateManager mDut;
    private TestLooper mTestLooper;

    @Mock
    private Context mMockContext;

    @Mock
    private Handler mMockHandler;

    @Mock
    private IRttManager mMockRttService;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    /**
     * Initialize mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDut = new WifiAwareRttStateManager();
        mTestLooper = new TestLooper();
        BidirectionalAsyncChannelServer server = new BidirectionalAsyncChannelServer(
                mMockContext, mTestLooper.getLooper(), mMockHandler);
        when(mMockRttService.getMessenger(null, new int[1])).thenReturn(server.getMessenger());

        mDut.startWithRttService(mMockContext, mTestLooper.getLooper(), mMockRttService);
    }

    /**
     * Validates that startRanging flow works: (1) start ranging, (2) get success callback - pass
     * to client (while nulling BSSID info), (3) get fail callback - ignored (since client
     * cleaned-out after first callback).
     */
    @Test
    public void testStartRanging() throws Exception {
        final int rangingId = 1234;
        WifiAwareClientState mockClient = mock(WifiAwareClientState.class);
        RttManager.RttParams[] params = new RttManager.RttParams[1];
        params[0] = new RttManager.RttParams();
        RttManager.ParcelableRttResults results =
                new RttManager.ParcelableRttResults(new RttManager.RttResult[2]);
        results.mResults[0] = new RttManager.RttResult();
        results.mResults[0].bssid = "something non-null";
        results.mResults[1] = new RttManager.RttResult();
        results.mResults[1].bssid = "really really non-null";

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<RttManager.ParcelableRttResults> rttResultsCaptor =
                ArgumentCaptor.forClass(RttManager.ParcelableRttResults.class);

        InOrder inOrder = inOrder(mMockHandler, mockClient);

        // (1) start ranging
        mDut.startRanging(rangingId, mockClient, params);
        mTestLooper.dispatchAll();
        inOrder.verify(mMockHandler).handleMessage(messageCaptor.capture());
        Message msg = messageCaptor.getValue();
        collector.checkThat("msg.what=RttManager.CMD_OP_START_RANGING", msg.what,
                equalTo(RttManager.CMD_OP_START_RANGING));
        collector.checkThat("rangingId", msg.arg2, equalTo(rangingId));
        collector.checkThat("RTT params", ((RttManager.ParcelableRttParams) msg.obj).mParams,
                equalTo(params));

        // (2) get success callback - pass to client
        Message successMessage = Message.obtain();
        successMessage.what = RttManager.CMD_OP_SUCCEEDED;
        successMessage.arg2 = rangingId;
        successMessage.obj = results;
        msg.replyTo.send(successMessage);
        mTestLooper.dispatchAll();
        inOrder.verify(mockClient).onRangingSuccess(eq(rangingId), rttResultsCaptor.capture());
        collector.checkThat("ParcelableRttResults object", results,
                equalTo(rttResultsCaptor.getValue()));
        collector.checkThat("RttResults[0].bssid null",
                rttResultsCaptor.getValue().mResults[0].bssid, nullValue());
        collector.checkThat("RttResults[1].bssid null",
                rttResultsCaptor.getValue().mResults[1].bssid, nullValue());

        // (3) get fail callback - ignored
        Message failMessage = Message.obtain();
        failMessage.what = RttManager.CMD_OP_ABORTED;
        failMessage.arg2 = rangingId;
        msg.replyTo.send(failMessage);
        mTestLooper.dispatchAll();

        verifyNoMoreInteractions(mMockHandler, mockClient);
    }
}
