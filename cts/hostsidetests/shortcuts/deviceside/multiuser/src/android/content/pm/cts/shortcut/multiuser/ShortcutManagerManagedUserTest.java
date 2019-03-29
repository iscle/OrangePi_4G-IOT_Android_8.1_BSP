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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.List;

public class ShortcutManagerManagedUserTest extends ShortcutManagerDeviceTestBase {
    public void test01_managedProfileNotStarted() {
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        // Get user-0's handle.
        final UserHandle userManaged = getOtherUser();

        final ShortcutQuery q = new ShortcutQuery();

        try {
            getLauncherApps().getShortcuts(q, userManaged);
            fail("Didn't throw SecurityException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("locked or not running"));
        }
    }

    public void test02_createShortuctsOnPrimaryUser() {
        assertTrue(getManager().setDynamicShortcuts(list(
                new ShortcutInfo.Builder(getContext(), "s1")
                        .setShortLabel("label1")
                        .setIntent(new Intent(Intent.ACTION_VIEW).setComponent(
                                new ComponentName(getContext(), MainActivity.class))).build())));
    }

    public void test03_createShortuctsOnManagedProfile() {
        assertTrue(getManager().setDynamicShortcuts(list(
                new ShortcutInfo.Builder(getContext(), "s1")
                        .setShortLabel("label2")
                        .setIntent(new Intent(Intent.ACTION_VIEW).setComponent(
                                new ComponentName(getContext(), MainActivity.class))).build())));
    }

    public void test04_getAndLaunch_primary() {
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        final UserHandle userCurrent = android.os.Process.myUserHandle();
        final UserHandle userOther = getOtherUser();

        final ShortcutQuery q = new ShortcutQuery()
                .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC)
                .setPackage(getContext().getPackageName())
                .setShortcutIds(list("s1"));
        assertWith(getLauncherApps().getShortcuts(q, userCurrent))
                .haveIds("s1")
                .areAllDynamic()
                .forShortcutWithId("s1", si -> {
                    assertEquals("label1", si.getShortLabel());
                    assertEquals(userCurrent, si.getUserHandle());
                });
        assertWith(getLauncherApps().getShortcuts(q, userOther))
                .haveIds("s1")
                .areAllDynamic()
                .forShortcutWithId("s1", si -> {
                    assertEquals("label2", si.getShortLabel());
                    assertEquals(userOther, si.getUserHandle());
                });

        // Just call start and make sure they don't throw.
        getLauncherApps().startShortcut(getContext().getPackageName(), "s1", null, null,
                userCurrent);

        // TODO Make sure the activity actually starts.
        getLauncherApps().startShortcut(getContext().getPackageName(), "s1", null, null,
                userOther);
    }

    public void test05_getAndLaunch_managed() {
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        final UserHandle userCurrent = android.os.Process.myUserHandle();
        final UserHandle userOther = getOtherUser();

        final ShortcutQuery q = new ShortcutQuery()
                .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC)
                .setPackage(getContext().getPackageName())
                .setShortcutIds(list("s1"));

        assertWith(getLauncherApps().getShortcuts(q, userCurrent))
                .haveIds("s1")
                .areAllDynamic()
                .forShortcutWithId("s1", si -> {
                    assertEquals("label2", si.getShortLabel());
                    assertEquals(userCurrent, si.getUserHandle());
                });

        assertWith(getLauncherApps().getShortcuts(q, userOther)).isEmpty();

        // TODO Make sure the activity actually starts.
        getLauncherApps().startShortcut(getContext().getPackageName(), "s1", null, null,
                userCurrent);

        try {
            getLauncherApps().startShortcut(getContext().getPackageName(), "s1", null, null,
                    userOther);
            fail("Didn't throw ActivityNotFoundException");
        } catch (ActivityNotFoundException e) {
            // okay
        }
    }

    private UserHandle getOtherUser() {
        for (UserHandle user : getContext().getSystemService(UserManager.class).getUserProfiles()) {
            if (user.equals(android.os.Process.myUserHandle())) {
                continue;
            }
            return user;
        }
        fail("Other user not found");
        return null;
    }
}
