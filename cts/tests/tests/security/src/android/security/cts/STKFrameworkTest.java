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
package android.security.cts;

import android.content.ComponentName;
import android.content.Intent;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

@SecurityTest
public class STKFrameworkTest extends AndroidTestCase {
    private boolean mHasTelephony;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasTelephony = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /*
     * Verifies commands Intercepting which has been sent from SIM card to Telephony using
     * zero-permission malicious application
     */
    public void testInterceptedSIMCommandsToTelephony() {
        if (!mHasTelephony) {
            return;
        }

        Intent intent = new Intent();
        intent.setAction("com.android.internal.stk.command");
        intent.putExtra("STK CMD", "test");
        ComponentName cn =
                ComponentName.unflattenFromString("com.android.stk/com.android.stk.StkCmdReceiver");
        intent.setComponent(cn);
        try {
            mContext.sendBroadcast(intent);
            fail("Able to send broadcast which can be received by any app which has registered " +
                    "broadcast for action 'com.android.internal.stk.command' since it is not " +
                    "protected with any permission. Device is vulnerable to CVE-2015-3843.");
        } catch (SecurityException e) {
            /* Pass the Test case: App should not be able to send broadcast using action
             * 'com.android.internal.stk.command' as it is protected by permission in
             * patched devices
             */
        }
    }
}
