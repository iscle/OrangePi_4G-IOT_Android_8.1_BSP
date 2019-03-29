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
package android.content.pm.cts.shortcut.backup.publisher3;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;

import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;

public class ShortcutManagerPostBackupTest extends ShortcutManagerDeviceTestBase {
    public void testWithUninstall() {
        assertWith(getManager().getDynamicShortcuts())
                .isEmpty();

        // backup = false, so no pinned shortcuts should be restored.
        assertWith(getManager().getPinnedShortcuts())
                .isEmpty();

        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1", "ms2")
                .areAllNotPinned();
    }

    public void testWithNoUninstall() {
        // backup = false, so dynamic shortcuts shouldn't be overwritten.
        assertWith(getManager().getDynamicShortcuts())
                .haveIds("s1", "s2", "s3")
                .areAllNotPinned();

        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1", "ms2")
                .areAllNotPinned();
    }
}
