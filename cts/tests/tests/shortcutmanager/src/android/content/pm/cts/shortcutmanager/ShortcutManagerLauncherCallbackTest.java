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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.ShortcutListAsserter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@SmallTest
public class ShortcutManagerLauncherCallbackTest extends ShortcutManagerCtsTestsBase {

    private static class MyCallback extends LauncherApps.Callback {
        private final HashSet<String> mInterestedPackages = new HashSet<String>();
        private boolean called;
        private String lastPackage;
        private final List<ShortcutInfo> lastShortcuts = new ArrayList<>();

        public MyCallback(String... interestedPackages) {
            mInterestedPackages.addAll(Arrays.asList(interestedPackages));
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
        }

        @Override
        public synchronized void onShortcutsChanged(
                String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
            if (!mInterestedPackages.contains(packageName)) {
                return; // Ignore other packages.
            }

            final StringBuilder sb = new StringBuilder();
            for (ShortcutInfo si : shortcuts) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(si.getId());
            }

            Log.i(TAG, "package=" + packageName + " shortcuts=" + sb.toString());
            lastPackage = packageName;
            lastShortcuts.clear();
            lastShortcuts.addAll(shortcuts);
            called = true;
        }

        public synchronized void reset() {
            lastPackage = null;
            lastShortcuts.clear();
            called = false;
        }

        public synchronized boolean isCalled() {
            return called;
        }

        public synchronized ShortcutListAsserter assertCalled(Context clientContext) {
            assertEquals(clientContext.getPackageName(), lastPackage);
            return assertWith(lastShortcuts);
        }

        public synchronized List<ShortcutInfo> getList() {
            return lastShortcuts;
        }

        public synchronized boolean isShortcutById(String id, Predicate<ShortcutInfo> predicate) {
            for (ShortcutInfo si : lastShortcuts) {
                if (id.equals(si.getId()) && predicate.test(si)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void testRegisterAndUnRegister() {
        final MyCallback c = new MyCallback();
        final Handler handler = new Handler(Looper.getMainLooper());
        getLauncherApps().registerCallback(c, handler);
        getLauncherApps().unregisterCallback(c);
    }

    public void testCallbacks() {
        final MyCallback c = new MyCallback(mPackageContext1.getPackageName());

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        final Handler handler = new Handler(Looper.getMainLooper());

        final AtomicBoolean registered = new AtomicBoolean(false);

        final Runnable reset = () -> {
            runWithCaller(mLauncherContext1, () -> {
                if (registered.get()) {
                    getLauncherApps().unregisterCallback(c);
                    registered.set(false);
                }
                c.reset();
                getLauncherApps().registerCallback(c, handler);
                registered.set(true);
            });
        };
        reset.run();
        try {
            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                assertTrue(getManager().setDynamicShortcuts(list(
                        makeShortcut("s1"),
                        makeShortcut("s2")
                )));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                assertTrue(getManager().addDynamicShortcuts(list(
                        makeShortcutWithRank("sx", 1)
                )));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2", "sx")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                assertTrue(getManager().updateShortcuts(list(
                        makeShortcut("s2")
                )));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2", "sx")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                assertTrue(getManager().updateShortcuts(list(
                        makeShortcut("sx")
                )));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2", "sx")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                enableManifestActivity("Launcher_manifest_1", true);
                retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                        "Manifest shortcuts didn't show up");
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2", "sx", "ms1")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                enableManifestActivity("Launcher_manifest_2", true);
                retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                        "Manifest shortcuts didn't show up");
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "s2", "sx", "ms1", "ms21", "ms22")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            // Pin some shortcuts.
            runWithCaller(mLauncherContext1, () -> {
                getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                        list("s1", "ms1", "ms21"), getUserHandle());
            });

            setDefaultLauncher(getInstrumentation(), mLauncherContext2);
            runWithCaller(mLauncherContext2, () -> {
                getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                        list("s1", "s2", "s3", "ms22"), getUserHandle());
            });

            setDefaultLauncher(getInstrumentation(), mLauncherContext1);
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                getManager().removeDynamicShortcuts(list("s1", "s2"));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");

            // s2 is still pinned by L2, but not visible to L1.
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "sx", "ms1", "ms21", "ms22")
                    .areAllEnabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                enableManifestActivity("Launcher_manifest_2", false);

                retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                        "Manifest shortcuts didn't show up");
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "sx", "ms1", "ms21")

                    .selectByIds("s1", "sx", "ms1")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms21")
                    .areAllDisabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                getManager().disableShortcuts(list("s1"));
            });
            retryUntil(() -> (c.isCalled() && c.isShortcutById("s1", si -> !si.isEnabled())),
                    "s1 not disabled");

            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "sx", "ms1", "ms21")

                    .selectByIds("sx", "ms1")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("s1", "ms21")
                    .areAllDisabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                getManager().enableShortcuts(list("s1"));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "sx", "ms1", "ms21")

                    .selectByIds("s1", "sx", "ms1")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms21")
                    .areAllDisabled();
            reset.run();

            //-----------------------
            runWithCaller(mPackageContext1, () -> {
                getManager().enableShortcuts(list("s2"));
            });
            retryUntil(() -> c.isCalled(), "callback not called.");
            c.assertCalled(mPackageContext1)
                    .haveIds("s1", "sx", "ms1", "ms21")

                    .selectByIds("s1", "sx", "ms1")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms21")
                    .areAllDisabled();
            reset.run();

        } finally {
            runWithCaller(mLauncherContext1, () -> {
                if (registered.get()) {
                    getLauncherApps().unregisterCallback(c);
                    registered.set(false);
                }
            });
        }
    }
}
