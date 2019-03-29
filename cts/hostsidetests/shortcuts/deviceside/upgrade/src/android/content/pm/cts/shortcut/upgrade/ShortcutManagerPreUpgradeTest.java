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
package android.content.pm.cts.shortcut.upgrade;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import junit.framework.AssertionFailedError;

@SmallTest
public class ShortcutManagerPreUpgradeTest extends ShortcutManagerDeviceTestBase {
    public void testPreUpgrade() {
        Log.i(Consts.TAG, "Pre: ResIDs=" + R.drawable.black_32x32 + ", " + R.drawable.black_64x64);

        // Publish shortcuts with drawable icons.
        final Icon icon1 = Icon.createWithResource(getContext(), R.drawable.black_32x32);
        final Icon icon2 = Icon.createWithResource(getContext(), R.drawable.black_64x64);

        // Store the original resource ID in the extras.
        final PersistableBundle b1 = new PersistableBundle();
        b1.putInt(Consts.EXTRA_ICON_RES_ID, R.drawable.black_32x32);
        final ShortcutInfo s1 = new ShortcutInfo.Builder(getContext(), "s1")
                .setShortLabel("shortlabel1")
                .setIcon(icon1)
                .setIntents(new Intent[]{new Intent(Intent.ACTION_VIEW)})
                .setExtras(b1)
                .build();

        final PersistableBundle b2 = new PersistableBundle();
        b2.putInt(Consts.EXTRA_ICON_RES_ID, R.drawable.black_64x64);
        final ShortcutInfo s2 = new ShortcutInfo.Builder(getContext(), "s2")
                .setShortLabel("shortlabel2")
                .setIcon(icon2)
                .setIntents(new Intent[]{new Intent(Intent.ACTION_VIEW)})
                .setExtras(b2)
                .build();

        assertTrue(getManager().setDynamicShortcuts(list(s1, s2)));

        // Set this package as a default launcher to access LauncherApps.
        Launcher.setAsDefaultLauncher(getInstrumentation(), getContext());

        // Check the published icons as a launcher.
        assertIconDimensions(getContext().getPackageName(), "s1", icon1);
        assertIconDimensions(getContext().getPackageName(), "s2", icon2);

        // Paranoid: this should fail.
        boolean notThrown = false;
        try {
            assertIconDimensions(getContext().getPackageName(), "s1", icon2);
            notThrown = true;
        } catch (AssertionFailedError expected) {
            // okay
        }
        assertFalse(notThrown);
    }
}
