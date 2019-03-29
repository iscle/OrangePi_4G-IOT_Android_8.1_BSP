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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.*;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ShortcutManagerCtsTestsBase extends InstrumentationTestCase {
    protected static final String TAG = "ShortcutCTS";

    private static final boolean DUMPSYS_IN_TEARDOWN = false; // DO NOT SUBMIT WITH true

    private static class SpoofingContext extends ContextWrapper {
        private final String mPackageName;

        public SpoofingContext(Context base, String packageName) {
            super(base);
            mPackageName = packageName;
        }

        @Override
        public String getPackageName() {
            return mPackageName;
        }
    }

    private Context mCurrentCallerPackage;
    private int mUserId;
    private UserHandle mUserHandle;

    private String mOriginalLauncher;

    protected Context mPackageContext1;
    protected Context mPackageContext2;
    protected Context mPackageContext3;
    protected Context mPackageContext4;

    protected Context mLauncherContext1;
    protected Context mLauncherContext2;
    protected Context mLauncherContext3;
    protected Context mLauncherContext4;

    private LauncherApps mLauncherApps1;
    private LauncherApps mLauncherApps2;
    private LauncherApps mLauncherApps3;
    private LauncherApps mLauncherApps4;

    private Map<Context, ShortcutManager> mManagers = new HashMap<>();
    private Map<Context, LauncherApps> mLauncherAppses = new HashMap<>();

    private ShortcutManager mCurrentManager;
    private LauncherApps mCurrentLauncherApps;

    private static final String[] ACTIVITIES_WITH_MANIFEST_SHORTCUTS = {
            "Launcher_manifest_1",
            "Launcher_manifest_2",
            "Launcher_manifest_3",
            "Launcher_manifest_4a",
            "Launcher_manifest_4b",
            "Launcher_manifest_error_1",
            "Launcher_manifest_error_2",
            "Launcher_manifest_error_3"
    };

    private ComponentName mTargetActivityOverride;

    private static class ShortcutActivity extends Activity {
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserId = getTestContext().getUserId();
        mUserHandle = android.os.Process.myUserHandle();

        resetConfig(getInstrumentation());
        final String config = getOverrideConfig();
        if (config != null) {
            overrideConfig(getInstrumentation(), config);
        }
        mOriginalLauncher = getDefaultLauncher(getInstrumentation());

        mPackageContext1 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.package1");
        mPackageContext2 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.package2");
        mPackageContext3 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.package3");
        mPackageContext4 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.package4");
        mLauncherContext1 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.launcher1");
        mLauncherContext2 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.launcher2");
        mLauncherContext3 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.launcher3");
        mLauncherContext4 = new SpoofingContext(getTestContext(),
                "android.content.pm.cts.shortcutmanager.packages.launcher4");

        mLauncherApps1 = new LauncherApps(mLauncherContext1);
        mLauncherApps2 = new LauncherApps(mLauncherContext2);
        mLauncherApps3 = new LauncherApps(mLauncherContext3);
        mLauncherApps4 = new LauncherApps(mLauncherContext4);

        clearShortcuts(getInstrumentation(), mUserId, mPackageContext1.getPackageName());
        clearShortcuts(getInstrumentation(), mUserId, mPackageContext2.getPackageName());
        clearShortcuts(getInstrumentation(), mUserId, mPackageContext3.getPackageName());
        clearShortcuts(getInstrumentation(), mUserId, mPackageContext4.getPackageName());

        setCurrentCaller(mPackageContext1);

        // Make sure shortcuts are removed.
        withCallers(getAllPublishers(), () -> {
            // Clear all shortcuts.
            clearShortcuts(getInstrumentation(), mUserId, getCurrentCallingPackage());

            disableActivitiesWithManifestShortucts();

            assertEquals("for " + getCurrentCallingPackage(),
                    0, getManager().getDynamicShortcuts().size());
            assertEquals("for " + getCurrentCallingPackage(),
                    0, getManager().getPinnedShortcuts().size());
            assertEquals("for " + getCurrentCallingPackage(),
                    0, getManager().getManifestShortcuts().size());
        });
    }

    @Override
    protected void tearDown() throws Exception {
        if (DUMPSYS_IN_TEARDOWN) {
            dumpsysShortcut(getInstrumentation());
        }

        withCallers(getAllPublishers(), () -> disableActivitiesWithManifestShortucts());

        resetConfig(getInstrumentation());

        if (!TextUtils.isEmpty(mOriginalLauncher)) {
            setDefaultLauncher(getInstrumentation(), mOriginalLauncher);
        }

        super.tearDown();
    }

    protected Context getTestContext() {
        return getInstrumentation().getContext();
    }

    protected UserHandle getUserHandle() {
        return mUserHandle;
    }

    protected List<Context> getAllPublishers() {
        // 4 has a different signature, so we can't call for it.
        return list(mPackageContext1, mPackageContext2, mPackageContext3);
    }

    protected List<Context> getAllLaunchers() {
        // 4 has a different signature, so we can't call for it.
        return list(mLauncherContext1, mLauncherContext2, mLauncherContext3);
    }

    protected List<Context> getAllCallers() {
        return list(
                mPackageContext1, mPackageContext2, mPackageContext3, mPackageContext4,
                mLauncherContext1, mLauncherContext2, mLauncherContext3, mLauncherContext4);
    }

    protected ComponentName getActivity(String className) {
        return new ComponentName(getCurrentCallingPackage(),
                "android.content.pm.cts.shortcutmanager.packages." + className);

    }

    protected void disableActivitiesWithManifestShortucts() {
        if (getManager().getManifestShortcuts().size() > 0) {
            // Disable DISABLED_ACTIVITIES
            for (String className : ACTIVITIES_WITH_MANIFEST_SHORTCUTS) {
                enableManifestActivity(className, false);
            }
        }
    }

    protected void enableManifestActivity(String className, boolean enabled) {
        getTestContext().getPackageManager().setComponentEnabledSetting(getActivity(className),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    protected void setTargetActivityOverride(String className) {
        mTargetActivityOverride = getActivity(className);
    }


    protected void withCallers(List<Context> callers, Runnable r) {
        for (Context c : callers) {
            runWithCaller(c, r);
        }
    }

    protected String getOverrideConfig() {
        return null;
    }

    protected void setCurrentCaller(Context callerContext) {
        mCurrentCallerPackage = callerContext;

        if (!mManagers.containsKey(mCurrentCallerPackage)) {
            mManagers.put(mCurrentCallerPackage, new ShortcutManager(mCurrentCallerPackage));
        }
        mCurrentManager = mManagers.get(mCurrentCallerPackage);

        if (!mLauncherAppses.containsKey(mCurrentCallerPackage)) {
            mLauncherAppses.put(mCurrentCallerPackage, new LauncherApps(mCurrentCallerPackage));
        }
        mCurrentLauncherApps = mLauncherAppses.get(mCurrentCallerPackage);

        mTargetActivityOverride = null;
    }

    protected Context getCurrentCallerContext() {
        return mCurrentCallerPackage;
    }

    protected String getCurrentCallingPackage() {
        return getCurrentCallerContext().getPackageName();
    }

    protected ShortcutManager getManager() {
        return mCurrentManager;
    }

    protected LauncherApps getLauncherApps() {
        return mCurrentLauncherApps;
    }

    protected void runWithCaller(Context callerContext, Runnable r) {
        final Context prev = mCurrentCallerPackage;

        setCurrentCaller(callerContext);

        r.run();

        setCurrentCaller(prev);
    }

    public static Bundle makeBundle(Object... keysAndValues) {
        assertTrue((keysAndValues.length % 2) == 0);

        if (keysAndValues.length == 0) {
            return null;
        }
        final Bundle ret = new Bundle();

        for (int i = keysAndValues.length - 2; i >= 0; i -= 2) {
            final String key = keysAndValues[i].toString();
            final Object value = keysAndValues[i + 1];

            if (value == null) {
                ret.putString(key, null);
            } else if (value instanceof Integer) {
                ret.putInt(key, (Integer) value);
            } else if (value instanceof String) {
                ret.putString(key, (String) value);
            } else if (value instanceof Bundle) {
                ret.putBundle(key, (Bundle) value);
            } else {
                fail("Type not supported yet: " + value.getClass().getName());
            }
        }
        return ret;
    }

    public static PersistableBundle makePersistableBundle(Object... keysAndValues) {
        assertTrue((keysAndValues.length % 2) == 0);

        if (keysAndValues.length == 0) {
            return null;
        }
        final PersistableBundle ret = new PersistableBundle();

        for (int i = keysAndValues.length - 2; i >= 0; i -= 2) {
            final String key = keysAndValues[i].toString();
            final Object value = keysAndValues[i + 1];

            if (value == null) {
                ret.putString(key, null);
            } else if (value instanceof Integer) {
                ret.putInt(key, (Integer) value);
            } else if (value instanceof String) {
                ret.putString(key, (String) value);
            } else if (value instanceof PersistableBundle) {
                ret.putPersistableBundle(key, (PersistableBundle) value);
            } else {
                fail("Type not supported yet: " + value.getClass().getName());
            }
        }
        return ret;
    }

    /**
     * Make a shortcut with an ID.
     */
    protected ShortcutInfo makeShortcut(String id) {
        return makeShortcut(id, "Title-" + id);
    }

    protected ShortcutInfo makeShortcutWithRank(String id, int rank) {
        return makeShortcut(
                id, "Title-" + id, /* activity =*/ null, /* icon =*/ null,
                makeIntent(Intent.ACTION_VIEW, ShortcutActivity.class), rank);
    }

    /**
     * Make a shortcut with an ID and a title.
     */
    protected ShortcutInfo makeShortcut(String id, String shortLabel) {
        return makeShortcut(
                id, shortLabel, /* activity =*/ null, /* icon =*/ null,
                makeIntent(Intent.ACTION_VIEW, ShortcutActivity.class), /* rank =*/ 0);
    }

    protected ShortcutInfo makeShortcut(String id, ComponentName activity) {
        return makeShortcut(
                id, "Title-" + id, activity, /* icon =*/ null,
                makeIntent(Intent.ACTION_VIEW, ShortcutActivity.class), /* rank =*/ 0);
    }

    /**
     * Make a shortcut with an ID and icon.
     */
    protected ShortcutInfo makeShortcutWithIcon(String id, Icon icon) {
        return makeShortcut(
                id, "Title-" + id, /* activity =*/ null, icon,
                makeIntent(Intent.ACTION_VIEW, ShortcutActivity.class), /* rank =*/ 0);
    }

    /**
     * Make multiple shortcuts with IDs.
     */
    protected List<ShortcutInfo> makeShortcuts(String... ids) {
        final ArrayList<ShortcutInfo> ret = new ArrayList();
        for (String id : ids) {
            ret.add(makeShortcut(id));
        }
        return ret;
    }

    protected ShortcutInfo.Builder makeShortcutBuilder(String id) {
        return new ShortcutInfo.Builder(getCurrentCallerContext(), id);
    }

    /**
     * Make a shortcut with details.
     */
    protected ShortcutInfo makeShortcut(String id, String shortLabel, ComponentName activity,
            Icon icon, Intent intent, int rank) {
        final ShortcutInfo.Builder b = makeShortcutBuilder(id)
                .setShortLabel(shortLabel)
                .setRank(rank)
                .setIntent(intent);
        if (activity != null) {
            b.setActivity(activity);
        } else if (mTargetActivityOverride != null) {
            b.setActivity(mTargetActivityOverride);
        }
        if (icon != null) {
            b.setIcon(icon);
        }
        return b.build();
    }

    /**
     * Make an intent.
     */
    protected Intent makeIntent(String action, Class<?> clazz, Object... bundleKeysAndValues) {
        final Intent intent = new Intent(action);
        intent.setComponent(makeComponent(clazz));
        intent.replaceExtras(makeBundle(bundleKeysAndValues));
        return intent;
    }

    /**
     * Make an component name, with the client context.
     */
    @NonNull
    protected ComponentName makeComponent(Class<?> clazz) {
        return new ComponentName(getCurrentCallerContext(), clazz);
    }

    protected Drawable getIconAsLauncher(Context launcherContext, String packageName,
            String shortcutId) {
        return getIconAsLauncher(launcherContext, packageName, shortcutId, /* withBadge=*/ true);
    }

    protected Drawable getIconAsLauncher(Context launcherContext, String packageName,
            String shortcutId, boolean withBadge) {
        setDefaultLauncher(getInstrumentation(), launcherContext);

        final AtomicReference<Drawable> ret = new AtomicReference<>();

        runWithCaller(launcherContext, () -> {
            final ShortcutQuery q = new ShortcutQuery()
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                                    | ShortcutQuery.FLAG_MATCH_MANIFEST
                                    | ShortcutQuery.FLAG_MATCH_PINNED
                                    | ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
                    .setPackage(packageName)
                    .setShortcutIds(list(shortcutId));
            final List<ShortcutInfo> found = getLauncherApps().getShortcuts(q, getUserHandle());

            assertEquals("Shortcut not found", 1, found.size());

            if (withBadge) {
                ret.set(getLauncherApps().getShortcutBadgedIconDrawable(found.get(0), 0));
            } else {
                ret.set(getLauncherApps().getShortcutIconDrawable(found.get(0), 0));
            }
        });
        return ret.get();
    }

    protected void assertIconDimensions(Context launcherContext, String packageName,
            String shortcutId, Icon expectedIcon) {
        final Drawable actual = getIconAsLauncher(launcherContext, packageName, shortcutId);
        if (actual == null && expectedIcon == null) {
            return; // okay
        }
        final Drawable expected = expectedIcon.loadDrawable(getTestContext());
        assertEquals(expected.getIntrinsicWidth(), actual.getIntrinsicWidth());
        assertEquals(expected.getIntrinsicHeight(), actual.getIntrinsicHeight());
    }

    protected void assertIconDimensions(Icon expectedIcon, Drawable actual) {
        if (actual == null && expectedIcon == null) {
            return; // okay
        }
        final Drawable expected = expectedIcon.loadDrawable(getTestContext());

        if (expected instanceof AdaptiveIconDrawable) {
            assertTrue(actual instanceof AdaptiveIconDrawable);
        }
        assertEquals(expected.getIntrinsicWidth(), actual.getIntrinsicWidth());
        assertEquals(expected.getIntrinsicHeight(), actual.getIntrinsicHeight());
    }

    protected Icon loadPackageDrawableIcon(Context packageContext, String resName)
            throws Exception {
        final Resources res = getTestContext().getPackageManager().getResourcesForApplication(
                packageContext.getPackageName());

        // Note the resource package names don't have the numbers.
        final int id = res.getIdentifier(resName, "drawable",
                "android.content.pm.cts.shortcutmanager.packages");
        if (id == 0) {
            fail("Drawable " + resName + " is not found in package "
                    + packageContext.getPackageName());
        }
        return Icon.createWithResource(packageContext, id);
    }

    protected Icon loadCallerDrawableIcon(String resName) throws Exception {
        return loadPackageDrawableIcon(getCurrentCallerContext(), resName);
    }

    protected List<ShortcutInfo> getShortcutsAsLauncher(int flags, String packageName) {
        return getShortcutsAsLauncher(flags, packageName, null, 0, null);
    }

    protected List<ShortcutInfo> getShortcutsAsLauncher(
            int flags, String packageName, String activityName,
            long changedSince, List<String> ids) {
        final ShortcutQuery q = new ShortcutQuery();
        q.setQueryFlags(flags);
        if (packageName != null) {
            q.setPackage(packageName);
            if (activityName != null) {
                q.setActivity(new ComponentName(packageName,
                        "android.content.pm.cts.shortcutmanager.packages." + activityName));
            }
        }
        q.setChangedSince(changedSince);
        if (ids != null && ids.size() > 0) {
            q.setShortcutIds(ids);
        }
        return getLauncherApps().getShortcuts(q, getUserHandle());
    }
}
