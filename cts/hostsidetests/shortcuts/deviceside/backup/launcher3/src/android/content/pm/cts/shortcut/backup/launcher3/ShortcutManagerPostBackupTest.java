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
package android.content.pm.cts.shortcut.backup.launcher3;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;

import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;

public class ShortcutManagerPostBackupTest extends ShortcutManagerDeviceTestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        setAsDefaultLauncher(MainActivity.class);
    }

    public void testWithUninstall_afterAppRestore() {
        assertWith(getPackageShortcuts(ShortcutManagerPreBackupTest.PUBLISHER1_PKG))
                .haveIds("ms1", "ms2")
                .areAllEnabled()
                .areAllNotPinned();

        assertWith(getPackageShortcuts(ShortcutManagerPreBackupTest.PUBLISHER2_PKG))
                .haveIds("ms1", "ms2")
                .areAllEnabled();

        assertWith(getPackageShortcuts(ShortcutManagerPreBackupTest.PUBLISHER3_PKG))
                .haveIds("ms1", "ms2")
                .areAllEnabled();
    }
}
