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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.appOps;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStatsManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.Time;

@SmallTest
public class ShortcutManagerUsageTest extends ShortcutManagerCtsTestsBase {
    private static final String APPOPS_SET_SHELL_COMMAND = "appops set {0} " +
            AppOpsManager.OPSTR_GET_USAGE_STATS + " {1}";

    // We need some allowance due to b/30415390.
    private static long USAGE_STATS_RANGE_ALLOWANCE = 60 * 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        appOps(getInstrumentation(), getTestContext().getPackageName(),
                AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
    }

    @Override
    protected void tearDown() throws Exception {
        appOps(getInstrumentation(), getTestContext().getPackageName(),
                AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");

        super.tearDown();
    }

    private static String generateRandomId(String signature) {
        Time tobj = new Time();
        tobj.set(System.currentTimeMillis());
        return tobj.format("%Y-%m-%d %H:%M:%S") + "." + signature + "."
                + Constants.sRandom.nextLong();
    }

    private boolean hasEvent(UsageEvents events, String packageName, String id) {
        final Event e = new Event();
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) {
                break;
            }
            if (e.getEventType() == Event.SHORTCUT_INVOCATION
                    && packageName.equals(e.getPackageName())
                    && id.equals(e.getShortcutId())) {
                return true;
            }
        }
        return false;
    }

    public void testReportShortcutUsed() throws InterruptedException {

        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() > 0,
                    "Manifest shortcuts didn't show up");
        });

        final String id1 = generateRandomId("id1");
        final String id2 = generateRandomId("id2");
        final String id3 = generateRandomId("id3");

        final String idManifest = "ms21";
        final String idNonexistance = "nonexistence";

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut(id1),
                    makeShortcut(id2)
            )));
        });
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut(id1),
                    makeShortcut(id3)
            )));
        });

        final UsageStatsManager usm = getTestContext().getSystemService(UsageStatsManager.class);

        // Report usage.
        final long start1 = System.currentTimeMillis() - USAGE_STATS_RANGE_ALLOWANCE;
        runWithCaller(mPackageContext2, () -> getManager().reportShortcutUsed(id3));
        final long end1 = System.currentTimeMillis() + USAGE_STATS_RANGE_ALLOWANCE;

        // Check the log.
        retryUntil(() -> hasEvent(usm.queryEvents(start1, end1),
                mPackageContext2.getPackageName(), id3), "Events weren't populated");

        // Report usage.
        final long start2 = System.currentTimeMillis() - USAGE_STATS_RANGE_ALLOWANCE;
        runWithCaller(mPackageContext1, () -> getManager().reportShortcutUsed(id1));
        final long end2 = System.currentTimeMillis() + USAGE_STATS_RANGE_ALLOWANCE;

        // Check the log.
        retryUntil(() -> hasEvent(usm.queryEvents(start2, end2),
                mPackageContext1.getPackageName(), id1), "Events weren't populated");

        // Report usage.
        final long start3 = System.currentTimeMillis() - USAGE_STATS_RANGE_ALLOWANCE;
        runWithCaller(mPackageContext1, () -> getManager().reportShortcutUsed(idNonexistance));
        runWithCaller(mPackageContext1, () -> getManager().reportShortcutUsed(idManifest));
        final long end3 = System.currentTimeMillis() + USAGE_STATS_RANGE_ALLOWANCE;

        // Check the log.
        retryUntil(() -> hasEvent(usm.queryEvents(start3, end3),
                mPackageContext1.getPackageName(), idManifest), "Events weren't populated");
        // Ensure that the nonexistent shortcut is not reported, even after the other one is.
        assertFalse(hasEvent(usm.queryEvents(start3, end3),
                    mPackageContext1.getPackageName(), idNonexistance));
    }
}
