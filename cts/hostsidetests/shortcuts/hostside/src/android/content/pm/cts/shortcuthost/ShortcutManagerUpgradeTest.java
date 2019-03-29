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
package android.content.pm.cts.shortcuthost;


public class ShortcutManagerUpgradeTest extends BaseShortcutManagerHostTest {
    private static final String VERSION1_APK = "CtsShortcutUpgradeVersion1.apk";
    private static final String VERSION2_APK = "CtsShortcutUpgradeVersion2.apk";

    private static final String TARGET_PKG = "android.content.pm.cts.shortcut.upgrade";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TARGET_PKG);

        super.tearDown();
    }

    /**
     * Make sure that, even when icon resource IDs have changed during an app upgrade,
     * ShortcutManager correctly resolves the right resources by resource name.
     */
    public void testUpgrade() throws Exception {
        installAppAsUser(VERSION1_APK, getPrimaryUserId());

        runDeviceTestsAsUser(TARGET_PKG, ".ShortcutManagerPreUpgradeTest", getPrimaryUserId());

        installAppAsUser(VERSION2_APK, getPrimaryUserId());

        runDeviceTestsAsUser(TARGET_PKG, ".ShortcutManagerPostUpgradeTest", getPrimaryUserId());
    }
}
