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
package android.content.pm.cts.shortcut.multiuser;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertExpectException;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.os.UserHandle;
import android.util.DisplayMetrics;

import java.lang.reflect.Method;
import java.util.List;

public class ShortcutManagerSecondaryUserTest extends ShortcutManagerDeviceTestBase {
    public void testCreateAndStart() {
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        // Publish a shortcut.
        final UserHandle user = android.os.Process.myUserHandle();

        assertTrue(getManager().setDynamicShortcuts(list(
                new ShortcutInfo.Builder(getContext(), "s1")
                    .setShortLabel("label")
                    .setIntent(new Intent(Intent.ACTION_VIEW).setComponent(
                            new ComponentName(getContext(), MainActivity.class))).build())));

        // Retrieve as a launcher.
        final ShortcutQuery q = new ShortcutQuery()
                .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC)
                .setPackage(getContext().getPackageName())
                .setShortcutIds(list("s1"));
        final List<ShortcutInfo> list = getLauncherApps().getShortcuts(q, user);
        assertWith(list)
                .haveIds("s1")
                .areAllDynamic()
                .forShortcutWithId("s1", si -> {
                    assertEquals(user, si.getUserHandle());
                });

        final ShortcutInfo s1 = list.get(0);

        // Just make sure they don't throw SecurityException.
        getLauncherApps().getShortcutIconDrawable(s1, DisplayMetrics.DENSITY_DEFAULT);
        getLauncherApps().getShortcutBadgedIconDrawable(s1, DisplayMetrics.DENSITY_DEFAULT);

        final long now = System.currentTimeMillis();

        // Start it.
        getLauncherApps().startShortcut(s1, null, null);

        retryUntil(() -> MainActivity.getLastCreateTime() >= now, "Activity not started");
    }

    public void testDifferentUserNotAccessible() throws Exception {
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        // Get user-0's handle.
        final UserHandle user0 = getUser0Handle();

        final ShortcutQuery q = new ShortcutQuery();

        try {
            getLauncherApps().getShortcuts(q, user0);
            fail("Didn't throw SecurityException");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("unrelated profile"));
        }
    }

    private static UserHandle getUser0Handle() throws Exception {
        Method of = UserHandle.class.getMethod("of", int.class);

        return (UserHandle) of.invoke(null, 0);
    }
}
