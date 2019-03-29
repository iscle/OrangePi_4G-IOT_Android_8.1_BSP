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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertExpectException;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.List;

@SmallTest
public class ShortcutManagerStartShortcutTest extends ShortcutManagerCtsTestsBase {
    private ComponentName mLaunchedActivity;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mLaunchedActivity = new ComponentName(getTestContext(), ShortcutLaunchedActivity.class);
    }

    private List<Intent> launchShortcutAndGetIntents(Context launcher, Context client,
            String id, int expectedNumIntents, String[] expectedActions) {
        return launchShortcutAndGetIntents(launcher, client, id, expectedNumIntents, null, null,
                expectedActions);
    }

    private List<Intent> launchShortcutAndGetIntents(Context launcher, Context client,
            String id, int expectedNumIntents, Rect rect, Bundle options,
            String[] expectedActions) {

        ShortcutLaunchedActivity.setExpectedOrder(expectedActions);

        runWithCaller(launcher, () -> {
            getLauncherApps().startShortcut(client.getPackageName(), id, rect, options,
                    getUserHandle());
        });

        retryUntil(() -> ShortcutLaunchedActivity.getIntents().size() == expectedNumIntents,
                "No activities launched");

        return ShortcutLaunchedActivity.getIntents();
    }

    private void assertShortcutStarts(Context launcher, Context client, String id,
            String[] expectedActions) {
        final List<Intent> launched = launchShortcutAndGetIntents(launcher, client, id, 1,
                expectedActions);
        assertTrue(launched.size() > 0);
    }

    private void assertShortcutCantStart(Context launcher, Context client, String id,
            Class<? extends Throwable> exceptionClass) {
        runWithCaller(launcher, () -> {
            assertExpectException(exceptionClass, "", () -> {

                getLauncherApps().startShortcut(client.getPackageName(), id, null, null,
                        getUserHandle());
            });
        });
    }

    private static final String[] EXPECTED_ACTIONS_SINGLE = new String[]{Intent.ACTION_MAIN};
    private static final String[] EXPECTED_ACTIONS_MULTI = new String[]{"a3", "a2", "a1"};

    /**
     * Start a single activity.
     */
    public void testStartSingle() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(mLaunchedActivity)
                .setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("k1", "v1");

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcutBuilder("s1").setShortLabel("abc")
                            .setIntent(i).build()
                    )));
        });

        List<Intent> launched = launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                "s1", 1, EXPECTED_ACTIONS_SINGLE);
        assertEquals(1, launched.size());
        assertEquals(Intent.ACTION_MAIN, launched.get(0).getAction());
        assertTrue((launched.get(0).getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
        assertEquals("v1", launched.get(0).getStringExtra("k1"));
    }

    /**
     * Start multiple activities.
     */
    public void testStartMultiple() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        Intent i1 = new Intent("a1")
                .setComponent(mLaunchedActivity)
                .setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("k1", "v1");
        Intent i2 = new Intent("a2")
                .setComponent(mLaunchedActivity)
                .setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent i3 = new Intent("a3")
                .setComponent(mLaunchedActivity)
                .putExtra("kx", "vx");

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcutBuilder("s1").setShortLabel("abc")
                            .setIntents(new Intent[]{i1, i2, i3}).build()
                    )));
        });

        List<Intent> launched = launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                "s1", 3, EXPECTED_ACTIONS_MULTI);
        assertEquals(3, launched.size());

        Intent i = launched.get(2);
        assertEquals("a1", i.getAction());
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0);
        assertEquals("v1", i.getStringExtra("k1"));

        i = launched.get(1);
        assertEquals("a2", i.getAction());
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) == 0);
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0);
        assertEquals(null, i.getExtras());

        i = launched.get(0);
        assertEquals("a3", i.getAction());
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) == 0);
        assertTrue((i.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0);
        assertEquals("vx", i.getStringExtra("kx"));
    }

    /**
     * Non default launcher can't start.
     */
    public void testNonDefaultLauncherCantStart() {

        testStartSingle();

        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        // L2 can start it.
        assertShortcutStarts(mLauncherContext2, mPackageContext1, "s1",
                EXPECTED_ACTIONS_SINGLE);

        // L1 no longer can start it.
        assertShortcutCantStart(mLauncherContext1, mPackageContext1, "s1",
                SecurityException.class);
    }

    public void testShortcutNoLongerExists() {

        // Let it publish a shortcut.
        testStartMultiple();

        // then remove it.
        runWithCaller(mPackageContext1, () -> {
            getManager().removeAllDynamicShortcuts();
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        assertShortcutCantStart(mLauncherContext1, mPackageContext1, "s1",
                ActivityNotFoundException.class);
    }

    public void testShortcutWrongId() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(mLaunchedActivity)
                .setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("k1", "v1");

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcutBuilder("s1").setShortLabel("abc")
                            .setIntent(i).build()
            )));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        assertShortcutCantStart(mLauncherContext1, mPackageContext1, "s2",
                ActivityNotFoundException.class);
    }

    public void testPinnedShortcut_sameLauncher() {

        // Let it publish a shortcut.
        testStartSingle();

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        // then pin it.
        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s1"), getUserHandle());
        });

        // Then remove it.
        runWithCaller(mPackageContext1, () -> {
            getManager().removeAllDynamicShortcuts();
        });

        // Should still be launchable.
        assertShortcutStarts(mLauncherContext1, mPackageContext1, "s1", EXPECTED_ACTIONS_SINGLE);
    }

    public void testPinnedShortcut_differentLauncher() {

        // Let it publish a shortcut.
        testStartSingle();

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        // then pin it.
        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s1"), getUserHandle());
        });

        // L2 is not the default, so can't launch it.
        assertShortcutCantStart(mLauncherContext2, mPackageContext1, "s2",
                SecurityException.class);

        // Then change the launcher
        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        // L2 can now launch it.
        assertShortcutStarts(mLauncherContext2, mPackageContext1, "s1", EXPECTED_ACTIONS_SINGLE);

        // Then remove it.
        runWithCaller(mPackageContext1, () -> {
            getManager().removeAllDynamicShortcuts();
        });

        // L2 didn't pin it, so can't launch.
        assertShortcutCantStart(mLauncherContext2, mPackageContext1, "s2",
                ActivityNotFoundException.class);

        // But launcher 1 can still launch it too, because it's pinned by this launcher.
        assertShortcutStarts(mLauncherContext1, mPackageContext1, "s1", EXPECTED_ACTIONS_SINGLE);
    }

    public void testStartSingleWithOptions() {
        testStartSingle();

        List<Intent> launched = launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                "s1", 1, new Rect(1, 1, 2, 2), new Bundle(), EXPECTED_ACTIONS_SINGLE);

        Intent i = launched.get(0);
        assertEquals(1, i.getSourceBounds().left);
        assertEquals(2, i.getSourceBounds().bottom);

    }

    public void testStartMultipleWithOptions() {
        testStartMultiple();

        List<Intent> launched = launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                "s1", 3, new Rect(1, 1, 2, 2), new Bundle(), EXPECTED_ACTIONS_MULTI);

        Intent i = launched.get(2);
        assertEquals(1, i.getSourceBounds().left);
        assertEquals(2, i.getSourceBounds().bottom);

        i = launched.get(1);
        assertEquals(null, i.getSourceBounds());

        i = launched.get(0);
        assertEquals(null, i.getSourceBounds());
    }

    public void testNonExistent() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("abc", "def"));

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcutBuilder("s1").setShortLabel("abc")
                            .setIntent(i).build()
            )));
        });

        assertExpectException(
                ActivityNotFoundException.class, "Shortcut could not be started", () -> {
            launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                    "s1", 1, new String[0]);
        });
    }

    /**
     * Un-exported activities in other packages can't be started.
     */
    public void testUnExported() {
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(
                        "android.content.pm.cts.shortcutmanager.packages.package4",
                        "android.content.pm.cts.shortcutmanager.packages.Launcher"));

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcutBuilder("s1").setShortLabel("abc")
                            .setIntent(i).build()
            )));
        });

        assertExpectException(
                ActivityNotFoundException.class, "Shortcut could not be started", () -> {
                    launchShortcutAndGetIntents(mLauncherContext1, mPackageContext1,
                            "s1", 1, new String[0]);
                });
    }
}
