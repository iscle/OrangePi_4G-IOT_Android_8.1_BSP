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
package android.content.pm.cts.shortcut.device.common;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.UserHandle;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import java.util.List;

/**
 * Base class for device side tests for the host test.
 */
public abstract class ShortcutManagerDeviceTestBase extends InstrumentationTestCase {
    private ShortcutManager mManager;
    private LauncherApps mLauncherApps;

    private String mOriginalLauncher;

    protected Context getContext() {
        return getInstrumentation().getTargetContext();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mOriginalLauncher = getDefaultLauncher(getInstrumentation());

        mManager = getContext().getSystemService(ShortcutManager.class);
        mLauncherApps = getContext().getSystemService(LauncherApps.class);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TextUtils.isEmpty(mOriginalLauncher)) {
            setDefaultLauncher(getInstrumentation(), mOriginalLauncher);
        }

        super.tearDown();
    }

    protected ShortcutManager getManager() {
        return mManager;
    }

    protected LauncherApps getLauncherApps() {
        return mLauncherApps;
    }

    protected UserHandle getUserHandle() {
        return android.os.Process.myUserHandle();
    }

    protected void setAsDefaultLauncher(Class<?> clazz) {
        setDefaultLauncher(getInstrumentation(),
                getContext().getPackageName() + "/" + clazz.getName());
    }

    protected Drawable getIconAsLauncher(String packageName, String shortcutId) {
        final ShortcutQuery q = new ShortcutQuery()
                .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | ShortcutQuery.FLAG_MATCH_MANIFEST
                        | ShortcutQuery.FLAG_MATCH_PINNED
                        | ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
                .setPackage(packageName)
                .setShortcutIds(list(shortcutId));
        final List<ShortcutInfo> found = getLauncherApps().getShortcuts(q, getUserHandle());

        assertEquals("Shortcut not found", 1, found.size());

        return getLauncherApps().getShortcutIconDrawable(found.get(0), 0);
    }

    protected void assertIconDimensions(String packageName,
            String shortcutId, Icon expectedIcon) {
        final Drawable actual = getIconAsLauncher(packageName, shortcutId);
        if (actual == null && expectedIcon == null) {
            return; // okay
        }
        final Drawable expected = expectedIcon.loadDrawable(getContext());
        assertEquals(expected.getIntrinsicWidth(), actual.getIntrinsicWidth());
        assertEquals(expected.getIntrinsicHeight(), actual.getIntrinsicHeight());
    }

    public ComponentName getActivity(String className) {
        return new ComponentName(getContext(), getContext().getPackageName() + "." + className);
    }

    protected List<ShortcutInfo> getPackageShortcuts(String packageName) {
        final ShortcutQuery q = new ShortcutQuery()
                .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | ShortcutQuery.FLAG_MATCH_MANIFEST
                        | ShortcutQuery.FLAG_MATCH_PINNED)
                .setPackage(packageName);
        return getLauncherApps().getShortcuts(q, getUserHandle());
    }
}
