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

import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.CallerInfo;
import com.android.server.telecom.Call;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.DirectToVoicemailCallFilter;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DirectToVoicemailCallFilterTest extends TelecomTestCase {
    @Mock private CallerInfoLookupHelper mCallerInfoLookupHelper;
    @Mock private CallFilterResultCallback mCallback;
    @Mock private Call mCall;

    private static final Uri TEST_HANDLE = Uri.parse("tel:1235551234");

    public void setUp() throws Exception {
        super.setUp();
    }

    @SmallTest
    public void testSendToVoicemail() {
        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();

        CallerInfo callerInfo = new CallerInfo();
        callerInfo.shouldSendToVoicemail = true;

        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);
        verify(mCallback).onCallFilteringComplete(mCall,
                new CallFilteringResult(
                        false, // shouldAllowCall
                        true, // shouldReject
                        true, // shouldAddToCallLog
                        true // shouldShowNotification
                ));
    }

    @SmallTest
    public void testDontSendToVoicemail() {
        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();

        CallerInfo callerInfo = new CallerInfo();
        callerInfo.shouldSendToVoicemail = false;

        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);
        verify(mCallback).onCallFilteringComplete(mCall,
                new CallFilteringResult(
                        true, // shouldAllowCall
                        false, // shouldReject
                        true, // shouldAddToCallLog
                        true // shouldShowNotification
                ));
    }

    @SmallTest
    public void testNullResponseFromLookupHelper() {
        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart(null);

        queryListener.onCallerInfoQueryComplete(null, null);
        verify(mCallback).onCallFilteringComplete(mCall,
                new CallFilteringResult(
                        true, // shouldAllowCall
                        false, // shouldReject
                        true, // shouldAddToCallLog
                        true // shouldShowNotification
                ));
    }

    private CallerInfoLookupHelper.OnQueryCompleteListener verifyLookupStart() {
        return verifyLookupStart(TEST_HANDLE);
    }

    private CallerInfoLookupHelper.OnQueryCompleteListener verifyLookupStart(Uri handle) {
        when(mCall.getHandle()).thenReturn(handle);
        DirectToVoicemailCallFilter filter =
                new DirectToVoicemailCallFilter(mCallerInfoLookupHelper);
        filter.startFilterLookup(mCall, mCallback);
        ArgumentCaptor<CallerInfoLookupHelper.OnQueryCompleteListener> captor =
                ArgumentCaptor.forClass(CallerInfoLookupHelper.OnQueryCompleteListener.class);
        verify(mCallerInfoLookupHelper).startLookup(eq(handle), captor.capture());
        return captor.getValue();
    }
}
