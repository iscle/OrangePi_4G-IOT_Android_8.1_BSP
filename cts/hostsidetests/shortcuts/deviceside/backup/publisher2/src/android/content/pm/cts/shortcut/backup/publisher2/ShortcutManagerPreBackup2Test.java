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
package android.content.pm.cts.shortcut.backup.publisher2;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ShortcutManagerPreBackup2Test extends ShortcutManagerDeviceTestBase {
    public void testPreBackup() {
        getManager().removeDynamicShortcuts(list("s2"));
        getManager().disableShortcuts(list("s3"));

        getContext().getPackageManager().setComponentEnabledSetting(
                new ComponentName(getContext(), MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                "Manifest shortcuts didn't update");

        assertWith(getManager().getDynamicShortcuts())
                .haveIds("s1")
                .areAllEnabled();

        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1")
                .areAllEnabled();

        assertWith(getManager().getPinnedShortcuts())
                .haveIds("s1", "s2", "s3", "ms1", "ms2")
                .selectByIds("s1", "s2", "ms1")
                .areAllEnabled()

                .revertToOriginalList()
                .selectByIds("s3", "ms2")
                .areAllDisabled();

   }
}
