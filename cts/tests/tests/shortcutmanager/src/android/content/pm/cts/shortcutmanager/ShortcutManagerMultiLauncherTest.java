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

import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ShortcutManagerMultiLauncherTest extends ShortcutManagerCtsTestsBase {
    /**
     * Make sure diffrerent launchers will have different set of pinned shortcuts.
     */
    public void testPinShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                    "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s2"),
                    makeShortcut("s3"),
                    makeShortcut("s4"),
                    makeShortcut("s5")
            )));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms21", "ms22")
                    .areAllManifest()
                    .areAllEnabled();
        });
        runWithCaller(mPackageContext2, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_3", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                    "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s2"),
                    makeShortcut("s3"),
                    makeShortcut("s4"),
                    makeShortcut("s5")
            )));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms31", "ms32")
                    .areAllManifest()
                    .areAllEnabled();
        });
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(
                    mPackageContext1.getPackageName(),
                    list("s1", "s2", "s3", "ms1", "ms21"), getUserHandle());
            getLauncherApps().pinShortcuts(
                    mPackageContext2.getPackageName(),
                    list("s2", "s3", "ms31"), getUserHandle());
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        runWithCaller(mLauncherContext2, () -> {
            getLauncherApps().pinShortcuts(
                    mPackageContext1.getPackageName(),
                    list("s3", "s4", "ms22"), getUserHandle());
            getLauncherApps().pinShortcuts(
                    mPackageContext2.getPackageName(),
                    list("s1", "s2", "s3", "ms32"), getUserHandle());
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms21", "ms22")
                    .areAllManifest()
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "ms1", "ms21", "ms22")
                    .areAllEnabled();

            // Then remove some
            getManager().removeDynamicShortcuts(list("s1", "s2", "s5"));
            getManager().disableShortcuts(list("s4"));

            enableManifestActivity("Launcher_manifest_1", false);
            retryUntil(() -> getManager().getManifestShortcuts().size() == 2,
                    "Manifest shortcuts didn't show up");

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s3")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms21", "ms22")
                    .areAllManifest()
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "ms1", "ms21", "ms22")
                    ;

        });
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms31", "ms32")
                    .areAllManifest()
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1", "s2", "s3", "ms31", "ms32")
                    .areAllEnabled();

            // Then remove some
            getManager().removeDynamicShortcuts(list("s1", "s2", "s3", "s4"));

            enableManifestActivity("Launcher_manifest_3", false);
            retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                    "Manifest shortcuts didn't show up");

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s5")
                    .areAllDynamic()
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1")
                    .areAllManifest()
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1", "s2", "s3", "ms31", "ms32")
                    ;
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            // For package 1.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("s3")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_MANIFEST | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("ms21", "ms22")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("s1", "s2", "s3", "ms1", "ms21")

                    .selectByIds("s1", "s2", "s3", "ms21")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms1")
                    .areAllDisabled();

            // For package 2.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("s5")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_MANIFEST | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("ms1")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("s2", "s3", "ms31")

                    .selectByIds("s2", "s3")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms31")
                    .areAllDisabled();
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        runWithCaller(mLauncherContext2, () -> {
            // For package 1.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("s3")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_MANIFEST | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("ms21", "ms22")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName()))
                    .haveIds("s3", "s4", "ms22")

                    .selectByIds("s3", "ms22")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("s4")
                    .areAllDisabled();

            // For package 2.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("s5")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_MANIFEST | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("ms1")
                    .areAllEnabled();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext2.getPackageName()))
                    .haveIds("s1", "s2", "s3", "ms32")

                    .selectByIds("s1", "s2", "s3")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms32")
                    .areAllDisabled();
        });
    }
}
