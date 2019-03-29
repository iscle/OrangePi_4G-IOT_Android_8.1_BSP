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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;

import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.graphics.drawable.Icon;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import junit.framework.AssertionFailedError;

@SmallTest
public class ShortcutManagerPostUpgradeTest extends ShortcutManagerDeviceTestBase {
    public void testPostUpgrade() {
        Log.i(Consts.TAG, "Post: ResIDs=" + R.drawable.black_32x32 + ", " + R.drawable.black_64x64);

        // Get the shortcuts published by the "pre" apk.

        // Check their original res IDs (stored in the extras) and make sure the res IDs are
        // different now.
        assertWith(getManager().getDynamicShortcuts())
                .haveIds("s1", "s2")
                .forShortcutWithId("s1", s -> {
                    assertTrue(
                            R.drawable.black_32x32 !=
                            s.getExtras().getInt(Consts.EXTRA_ICON_RES_ID));
                })
                .forShortcutWithId("s2", s -> {
                    assertTrue(
                            R.drawable.black_64x64 !=
                            s.getExtras().getInt(Consts.EXTRA_ICON_RES_ID));
                });

        // Next, actually fetch the icons as a launcher, and make sure the dimensions are correct.
        final Icon icon1 = Icon.createWithResource(getContext(), R.drawable.black_32x32);
        final Icon icon2 = Icon.createWithResource(getContext(), R.drawable.black_64x64);

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
