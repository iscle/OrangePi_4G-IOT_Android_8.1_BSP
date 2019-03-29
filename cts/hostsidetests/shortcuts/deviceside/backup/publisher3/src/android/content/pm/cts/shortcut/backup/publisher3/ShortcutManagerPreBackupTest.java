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
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.makePersistableBundle;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.set;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.cts.shortcut.device.common.ShortcutManagerDeviceTestBase;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ShortcutManagerPreBackupTest extends ShortcutManagerDeviceTestBase {
    public void testPreBackup() {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getContext().getResources(), R.drawable.black_16x64));
        final Icon icon3 = Icon.createWithResource(getContext(), R.drawable.black_64x16);

        final ShortcutInfo s1 = new ShortcutInfo.Builder(getContext(), "s1")
                .setShortLabel("shortlabel1")
                .setLongLabel("longlabel1")
                .setIcon(icon1)
                .setDisabledMessage("disabledmessage1")
                .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                .setExtras(makePersistableBundle("ek1", "ev1"))
                .setCategories(set("cat1"))
                .build();

        final ShortcutInfo s2 = new ShortcutInfo.Builder(getContext(), "s2")
                .setShortLabel("shortlabel2")
                .setIntents(new Intent[]{new Intent("main")})
                .build();

        final ShortcutInfo s3 = new ShortcutInfo.Builder(getContext(), "s3")
                .setShortLabel("shortlabel2")
                .setIcon(icon3)
                .setIntents(new Intent[]{new Intent("main")})
                .build();

        assertTrue(getManager().setDynamicShortcuts(list(s1, s2, s3)));

        assertWith(getManager().getDynamicShortcuts())
                .haveIds("s1", "s2", "s3")
                .areAllNotPinned();

        assertWith(getManager().getManifestShortcuts())
                .haveIds("ms1", "ms2")
                .areAllNotPinned();
   }
}
