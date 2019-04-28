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

import android.content.ContentResolver;
import android.content.IContentProvider;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.IncomingCallFilter;
import com.android.server.telecom.TelecomSystem;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IncomingCallFilterTest extends TelecomTestCase {
    @Mock private CallFilterResultCallback mResultCallback;
    @Mock private Call mCall;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};

    @Mock private IncomingCallFilter.CallFilter mFilter1;
    @Mock private IncomingCallFilter.CallFilter mFilter2;
    @Mock private IncomingCallFilter.CallFilter mFilter3;

    @Mock private Timeouts.Adapter mTimeoutsAdapter;

    private static final Uri TEST_HANDLE = Uri.parse("tel:1235551234");
    private static final long LONG_TIMEOUT = 1000000;
    private static final long SHORT_TIMEOUT = 100;

    private static final CallFilteringResult RESULT1 =
            new CallFilteringResult(
                    true, // shouldAllowCall
                    false, // shouldReject
                    true, // shouldAddToCallLog
                    true // shouldShowNotification
            );

    private static final CallFilteringResult RESULT2 =
            new CallFilteringResult(
                    false, // shouldAllowCall
                    true, // shouldReject
                    false, // shouldAddToCallLog
                    true // shouldShowNotification
            );

    private static final CallFilteringResult RESULT3 =
            new CallFilteringResult(
                    false, // shouldAllowCall
                    true, // shouldReject
                    true, // shouldAddToCallLog
                    false // shouldShowNotification
            );

    private static final CallFilteringResult DEFAULT_RESULT = RESULT1;

    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        when(mCall.getHandle()).thenReturn(TEST_HANDLE);
        setTimeoutLength(LONG_TIMEOUT);
    }

    @SmallTest
    public void testSingleFilter() {
        IncomingCallFilter testFilter = new IncomingCallFilter(mContext, mResultCallback, mCall,
                mLock, mTimeoutsAdapter, Collections.singletonList(mFilter1));
        testFilter.performFiltering();
        verify(mFilter1).startFilterLookup(mCall, testFilter);

        testFilter.onCallFilteringComplete(mCall, RESULT1);
        waitForHandlerAction(testFilter.getHandler(), SHORT_TIMEOUT * 2);
        verify(mResultCallback).onCallFilteringComplete(eq(mCall), eq(RESULT1));
    }

    @SmallTest
    public void testMultipleFilters() {
        List<IncomingCallFilter.CallFilter> filters =
                new ArrayList<IncomingCallFilter.CallFilter>() {{
                    add(mFilter1);
                    add(mFilter2);
                    add(mFilter3);
                }};
        IncomingCallFilter testFilter = new IncomingCallFilter(mContext, mResultCallback, mCall,
                mLock, mTimeoutsAdapter, filters);
        testFilter.performFiltering();
        verify(mFilter1).startFilterLookup(mCall, testFilter);
        verify(mFilter2).startFilterLookup(mCall, testFilter);
        verify(mFilter3).startFilterLookup(mCall, testFilter);

        testFilter.onCallFilteringComplete(mCall, RESULT1);
        testFilter.onCallFilteringComplete(mCall, RESULT2);
        testFilter.onCallFilteringComplete(mCall, RESULT3);
        waitForHandlerAction(testFilter.getHandler(), SHORT_TIMEOUT * 2);
        verify(mResultCallback).onCallFilteringComplete(eq(mCall), eq(
                new CallFilteringResult(
                        false, // shouldAllowCall
                        true, // shouldReject
                        false, // shouldAddToCallLog
                        false // shouldShowNotification
                )));
    }

    @SmallTest
    public void testFilterTimeout() throws Exception {
        setTimeoutLength(SHORT_TIMEOUT);
        IncomingCallFilter testFilter = new IncomingCallFilter(mContext, mResultCallback, mCall,
                mLock, mTimeoutsAdapter, Collections.singletonList(mFilter1));
        testFilter.performFiltering();
        verify(mResultCallback, timeout((int) SHORT_TIMEOUT * 2)).onCallFilteringComplete(eq(mCall),
                eq(DEFAULT_RESULT));
        testFilter.onCallFilteringComplete(mCall, RESULT1);
        waitForHandlerAction(testFilter.getHandler(), SHORT_TIMEOUT * 2);
        // verify that we don't report back again with the result
        verify(mResultCallback, atMost(1)).onCallFilteringComplete(any(Call.class),
                any(CallFilteringResult.class));
    }

    @SmallTest
    public void testFilterTimeoutDoesntTrip() throws Exception {
        setTimeoutLength(SHORT_TIMEOUT);
        IncomingCallFilter testFilter = new IncomingCallFilter(mContext, mResultCallback, mCall,
                mLock, mTimeoutsAdapter, Collections.singletonList(mFilter1));
        testFilter.performFiltering();
        testFilter.onCallFilteringComplete(mCall, RESULT1);
        waitForHandlerAction(testFilter.getHandler(), SHORT_TIMEOUT * 2);
        Thread.sleep(SHORT_TIMEOUT);
        verify(mResultCallback, atMost(1)).onCallFilteringComplete(any(Call.class),
                any(CallFilteringResult.class));
    }

    @SmallTest
    public void testToString() {
        assertEquals("[Allow, logged, notified]", RESULT1.toString());
        assertEquals("[Reject, notified]", RESULT2.toString());
        assertEquals("[Reject, logged]", RESULT3.toString());
    }

    private void setTimeoutLength(long length) throws Exception {
        when(mTimeoutsAdapter.getCallScreeningTimeoutMillis(any(ContentResolver.class)))
                .thenReturn(length);
    }
}
