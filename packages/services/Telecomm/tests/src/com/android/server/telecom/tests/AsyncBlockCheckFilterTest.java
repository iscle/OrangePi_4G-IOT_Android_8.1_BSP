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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.Context;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.callfiltering.AsyncBlockCheckFilter;
import com.android.server.telecom.callfiltering.BlockCheckerAdapter;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.callfiltering.CallFilteringResult;

import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncBlockCheckFilterTest extends TelecomTestCase {
    @Mock private Context mContext;
    @Mock private BlockCheckerAdapter mBlockCheckerAdapter;
    @Mock private Call mCall;
    @Mock private CallFilterResultCallback mCallback;

    private AsyncBlockCheckFilter mFilter;
    private static final CallFilteringResult BLOCK_RESULT = new CallFilteringResult(
            false, // shouldAllowCall
            true, //shouldReject
            false, //shouldAddToCallLog
            false // shouldShowNotification
    );

    private static final CallFilteringResult PASS_RESULT = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private static final Uri TEST_HANDLE = Uri.parse("tel:1235551234");
    private static final int TEST_TIMEOUT = 100;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(mCall.getHandle()).thenReturn(TEST_HANDLE);
        mFilter = new AsyncBlockCheckFilter(mContext, mBlockCheckerAdapter);
    }

    @SmallTest
    public void testBlockNumber() {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return true;
        }).when(mBlockCheckerAdapter)
                .isBlocked(any(Context.class), eq(TEST_HANDLE.getSchemeSpecificPart()));
        mFilter.startFilterLookup(mCall, mCallback);
        waitOnLatch(latch);
        verify(mCallback, timeout(TEST_TIMEOUT))
                .onCallFilteringComplete(eq(mCall), eq(BLOCK_RESULT));
    }

    @SmallTest
    public void testDontBlockNumber() {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return false;
        }).when(mBlockCheckerAdapter)
                .isBlocked(any(Context.class), eq(TEST_HANDLE.getSchemeSpecificPart()));
        mFilter.startFilterLookup(mCall, mCallback);
        waitOnLatch(latch);
        verify(mCallback, timeout(TEST_TIMEOUT))
                .onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    private void waitOnLatch(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
}
