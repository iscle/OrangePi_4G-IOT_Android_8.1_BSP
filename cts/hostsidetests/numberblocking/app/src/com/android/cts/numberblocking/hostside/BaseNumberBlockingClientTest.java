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

package com.android.cts.numberblocking.hostside;

import android.content.Context;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;

/**
 * Base class for number blocking tests.
 */
public class BaseNumberBlockingClientTest extends InstrumentationTestCase {
    protected TelecomManager mTelecomManager;
    protected Context mContext;
    protected String mPhoneAccountId;
    protected String mBlockedPhoneNumber;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        Bundle arguments = InstrumentationRegistry.getArguments();
        mBlockedPhoneNumber = arguments.getString("blocked_number");
        assertNotNull(mBlockedPhoneNumber);
        mPhoneAccountId = arguments.getString("phone_account_id");
        assertNotNull(mPhoneAccountId);
    }
}
