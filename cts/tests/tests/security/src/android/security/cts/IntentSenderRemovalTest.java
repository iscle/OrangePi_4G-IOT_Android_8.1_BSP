/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.platform.test.annotations.SecurityTest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.test.AndroidTestCase;

/**
 * Make sure the DebugIntentSender activity, which allows privilege escalation of intent caller
 * to system uid, has been removed from the system.
 */
@SecurityTest
public class IntentSenderRemovalTest extends AndroidTestCase {

    /**
     * Verify that the DebugIntentSender activity in Settings has been removed
     * and cannot be invoked.
     */
    public void testIntentSenderIntent() throws InterruptedException {
        Intent debugIntentSender = new Intent(Intent.ACTION_MAIN);
        debugIntentSender.setClassName("com.android.settings",
                "com.android.settings.DebugIntentSender");
        PackageManager pm = getContext().getPackageManager();
        ResolveInfo ri = pm.resolveActivity(debugIntentSender, 0);
        // Test for null
        assertNull("com.android.settings.DebugIntentSender should not be a valid activity", ri);
    }
}
