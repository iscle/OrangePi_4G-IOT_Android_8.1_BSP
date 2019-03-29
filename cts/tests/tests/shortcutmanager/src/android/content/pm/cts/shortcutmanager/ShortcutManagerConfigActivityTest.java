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
package android.content.pm.cts.shortcutmanager;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for various APIs related to starting shortcut config activity.
 */
@SmallTest
public class ShortcutManagerConfigActivityTest extends ShortcutManagerCtsTestsBase {

    private static final String SHORTCUT_ID = "s12345";

    private static final String CONFIG_ACTIVITY_NAME =
            "android.content.pm.cts.shortcutmanager.main_shortcut_config";

    public void testGetShortcutConfigActivityList() throws Exception {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            assertNotNull(getConfigActivity());
            assertNotNull(getLauncherApps().getShortcutConfigActivityIntent(getConfigActivity()));
        });

        // Get config activity works even for non-default activity.
        setDefaultLauncher(getInstrumentation(), mLauncherContext4);

        runWithCaller(mLauncherContext1, () -> {
            assertNotNull(getConfigActivity());
            // throws exception when default launcher is different.
            assertExpectException(SecurityException.class, null, () ->
                getLauncherApps().getShortcutConfigActivityIntent(getConfigActivity()));
        });
    }

    public void testCorrectIntentSenderCreated() throws Throwable {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        final AtomicReference<IntentSender> sender = new AtomicReference<>();
        runWithCaller(mLauncherContext1, () ->
            sender.set(getLauncherApps().getShortcutConfigActivityIntent(getConfigActivity())));

        Instrumentation.ActivityMonitor monitor =
                getInstrumentation().addMonitor((String) null, null, false);

        runTestOnUiThread(() -> {
            try {
                mLauncherContext1.startIntentSender(sender.get(), null, 0, 0, 0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verify that the config activity was launched with proper action.
        Activity activity = monitor.waitForActivityWithTimeout(10000);
        assertNotNull(activity);
        Intent intent = activity.getIntent();
        assertEquals(Intent.ACTION_CREATE_SHORTCUT, intent.getAction());
        assertEquals(CONFIG_ACTIVITY_NAME, intent.getComponent().getClassName());
        activity.finish();
    }

    public void testCreateShortcutResultIntent_defaultLauncher() throws Exception {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        PinItemRequest request = getShortcutRequestForPackage1();
        runWithCaller(mLauncherContext1, () -> {
            assertTrue(request.isValid());
            assertTrue(request.accept());
        });
    }

    public void testCreateShortcutResultIntent_defaultChanges() throws Exception {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        PinItemRequest request = getShortcutRequestForPackage1();

        setDefaultLauncher(getInstrumentation(), mLauncherContext4);
        // Launcher1 can still access the request
        runWithCaller(mLauncherContext1, () -> {
            assertTrue(request.isValid());
            assertTrue(request.accept());
        });
    }

    public void testCreateShortcutResultIntent_noDefault() throws Exception {
        setDefaultLauncher(getInstrumentation(), mLauncherContext4);
        PinItemRequest request = getShortcutRequestForPackage1();

        // Launcher1 can still access the request
        runWithCaller(mLauncherContext1, () -> {
            assertFalse(request.isValid());
            assertExpectException(SecurityException.class, null, request::accept);
        });
    }

    private PinItemRequest getShortcutRequestForPackage1() {
        final AtomicReference<PinItemRequest> result = new AtomicReference<>();
        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo shortcut = makeShortcutBuilder(SHORTCUT_ID)
                    .setShortLabel("label1")
                    .setIntent(new Intent(Intent.ACTION_MAIN))
                    .build();
            Intent intent = getManager().createShortcutResultIntent(shortcut);
            assertNotNull(intent);
            PinItemRequest request = getLauncherApps().getPinItemRequest(intent);
            assertNotNull(request);
            assertEquals(PinItemRequest.REQUEST_TYPE_SHORTCUT, request.getRequestType());
            assertEquals(SHORTCUT_ID, request.getShortcutInfo().getId());
            result.set(request);
        });
        return result.get();
    }

    private LauncherActivityInfo getConfigActivity() {
        for (LauncherActivityInfo info : getLauncherApps().getShortcutConfigActivityList(
                getTestContext().getPackageName(), getUserHandle())) {
            if (CONFIG_ACTIVITY_NAME.equals(info.getComponentName().getClassName())) {
                return info;
            }
        }
        return null;
    }
}
