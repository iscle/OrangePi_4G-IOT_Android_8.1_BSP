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
package android.content.pm.cts.shortcutmanager;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertCallbackNotReceived;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertCallbackReceived;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.checkAssertSuccess;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.dumpsysShortcut;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.resetAll;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.waitUntil;

import static org.mockito.Mockito.mock;

import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Looper;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Make sure switching between mPackageContext1..3 and mLauncherContext1..3 will work as intended.
 */
@SmallTest
public class ShortcutManagerSpoofingTest extends ShortcutManagerCtsTestsBase {
    /**
     * Create shortcuts from different packages and make sure they're really different.
     */
    public void testSpoofingPublisher() {
        runWithCaller(mPackageContext1, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title1");
            getManager().setDynamicShortcuts(list(s1));
        });
        runWithCaller(mPackageContext2, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title2");
            getManager().setDynamicShortcuts(list(s1));
        });
        runWithCaller(mPackageContext3, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title3");
            getManager().setDynamicShortcuts(list(s1));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1")
                    .forShortcutWithId("s1", s -> {
                        assertEquals("title1", s.getShortLabel());
                    });
        });
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1")
                    .forShortcutWithId("s1", s -> {
                        assertEquals("title2", s.getShortLabel());
                    });
        });
        runWithCaller(mPackageContext3, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1")
                    .forShortcutWithId("s1", s -> {
                        assertEquals("title3", s.getShortLabel());
                    });
        });
    }

    public void testSpoofingLauncher() {
        final LauncherApps.Callback c0_1 = mock(LauncherApps.Callback.class);
        final LauncherApps.Callback c0_2 = mock(LauncherApps.Callback.class);
        final LauncherApps.Callback c0_3 = mock(LauncherApps.Callback.class);
        final Handler h = new Handler(Looper.getMainLooper());

        runWithCaller(mLauncherContext1, () -> getLauncherApps().registerCallback(c0_1, h));
        runWithCaller(mLauncherContext2, () -> getLauncherApps().registerCallback(c0_2, h));
        runWithCaller(mLauncherContext3, () -> getLauncherApps().registerCallback(c0_3, h));

        // Change the default launcher
        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        dumpsysShortcut(getInstrumentation());

        runWithCaller(mLauncherContext1,
                () -> assertFalse(getLauncherApps().hasShortcutHostPermission()));
        runWithCaller(mLauncherContext2,
                () -> assertTrue(getLauncherApps().hasShortcutHostPermission()));
        runWithCaller(mLauncherContext3,
                () -> assertFalse(getLauncherApps().hasShortcutHostPermission()));

        // Call a publisher API and make sure only launcher2 gets it.

        resetAll(list(c0_1, c0_2, c0_3));

        runWithCaller(mPackageContext1, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title1");
            getManager().setDynamicShortcuts(list(s1));
        });

        // Because of the handlers, callback calls are not synchronous.
        waitUntil("Launcher 2 didn't receive message", () ->
                checkAssertSuccess(() ->
                        assertCallbackReceived(c0_2, android.os.Process.myUserHandle(),
                                mPackageContext1.getPackageName(), "s1")
                )
        );

        assertCallbackNotReceived(c0_1);
        assertCallbackNotReceived(c0_3);
    }
}
