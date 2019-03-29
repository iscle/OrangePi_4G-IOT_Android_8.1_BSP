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
package com.android.cts.managedprofile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Basic sanity test to ensure some basic functionalities of work profile are working.
 */
public class SanityTest {
    private static final ComponentName SIMPLE_APP_ACTIVITY =
            new ComponentName(
                    "com.android.cts.launcherapps.simpleapp",
                    "com.android.cts.launcherapps.simpleapp.SimpleActivity");
    /**
     * Broadcast sent from com.android.cts.launcherapps.simpleapp.SimpleActivity.
     */
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    /**
     * Verify that we can launch an app that installed in work profile only.
     */
    @Test
    public void testLaunchAppInstalledInProfileOnly() throws Exception {
        BlockingBroadcastReceiver receiver =
                new BlockingBroadcastReceiver(mContext, ACTIVITY_LAUNCHED_ACTION);
        try {
            receiver.register();
            Intent intent = new Intent();
            intent.setComponent(SIMPLE_APP_ACTIVITY);
            // Finish the activity after that.
            intent.putExtra("finish", true);
            mContext.startActivity(intent);
            Intent receivedBroadcast = receiver.awaitForBroadcast();
            assertNotNull(receivedBroadcast);
            assertEquals(ACTIVITY_LAUNCHED_ACTION, receivedBroadcast.getAction());
        } finally {
            receiver.unregisterQuietly();
        }
    }
}
