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

package com.android.services.telephony;

import static org.mockito.Mockito.when;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Mock Telephony Connection used in TelephonyConferenceController.java for testing purpose
 */

public class MockTelephonyConnection extends TelephonyConnection {

    @Mock
    com.android.internal.telephony.Connection mMockRadioConnection;

    @Mock
    Call mMockCall;

    @Mock
    Phone mMockPhone;

    @Override
    public com.android.internal.telephony.Connection getOriginalConnection() {
        return mMockRadioConnection;
    }

    public MockTelephonyConnection() {
        super(null, null, false);
        MockitoAnnotations.initMocks(this);

        // Set up mMockRadioConnection and mMockPhone to contain an active call
        when(mMockRadioConnection.getState()).thenReturn(Call.State.ACTIVE);
        when(mMockRadioConnection.getCall()).thenReturn(mMockCall);
        when(mMockPhone.getRingingCall()).thenReturn(mMockCall);
        when(mMockCall.getState()).thenReturn(Call.State.ACTIVE);
    }

    @Override
    public boolean isConferenceSupported() {
        return true;
    }

    @Override
    public Phone getPhone() {
        return mMockPhone;
    }

    public TelephonyConnection cloneConnection() {
        return this;
    }

}
