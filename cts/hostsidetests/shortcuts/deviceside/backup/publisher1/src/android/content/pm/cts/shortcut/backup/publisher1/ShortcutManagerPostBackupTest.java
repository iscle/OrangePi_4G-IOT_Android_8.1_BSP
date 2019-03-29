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
package android.content.pm.cts.shortcut.backup.publisher1;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;

import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;

public class ShortcutManagerPostBackupTest extends ShortcutManagerDeviceTestBase {
    public void testWithUninstall() {
        assertWith(getManager().getDynamicShortcuts())
                .isEmpty();

        assertWith(getManager().getPinnedShortcuts())
                .haveIds("s1", "s3", "ms1", "ms2")
                .areAllEnabled();

        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1", "ms2")
                .areAllPinned()
                .areAllEnabled();
    }

    public void testWithNoUninstall() {
        // Ideally this shouldn't be cleared, but for now it will be.
//        assertWith(getManager().getDynamicShortcuts())
//                .isEmpty();

        assertWith(getManager().getPinnedShortcuts())
                .isEmpty();

        // Should still have the manifest shortcuts.
        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1", "ms2")
                .areAllEnabled();
    }
}
