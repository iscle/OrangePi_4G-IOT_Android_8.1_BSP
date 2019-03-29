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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getIconSize;

import android.content.pm.ShortcutManager;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ShortcutManagerMiscTest extends ShortcutManagerCtsTestsBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();

    }

    public void testMiscApis() throws Exception {
        ShortcutManager manager = getTestContext().getSystemService(ShortcutManager.class);

        assertEquals(5, manager.getMaxShortcutCountPerActivity());

        // during the test, this process always considered to be in the foreground.
        assertFalse(manager.isRateLimitingActive());

        final int iconDimension = getIconSize(getInstrumentation());
        assertEquals(iconDimension, manager.getIconMaxWidth());
        assertEquals(iconDimension, manager.getIconMaxHeight());
    }
}
