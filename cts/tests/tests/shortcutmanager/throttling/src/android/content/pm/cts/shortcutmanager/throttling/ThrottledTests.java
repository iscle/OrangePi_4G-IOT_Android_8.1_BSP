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
package android.content.pm.cts.shortcutmanager.throttling;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.pm.ShortcutManager;

import java.util.function.BooleanSupplier;

public class ThrottledTests {
    public static String TAG = "ShortcutThrottledTests";

    private ThrottledTests() {
    }

    public static void assertThrottled(Context context, BooleanSupplier apiCall) {
        final ShortcutManager manager = context.getSystemService(ShortcutManager.class);

        assertFalse("Throttling must be reset here", manager.isRateLimitingActive());

        assertTrue("First call should succeed", apiCall.getAsBoolean());

        // App can make 10 API calls between the interval, but there's a chance that the throttling
        // gets reset within this loop, so we make 20 calls.
        boolean throttled = false;
        for (int i = 0; i < 19; i++) {
            if (!apiCall.getAsBoolean()) {
                throttled = true;
                break;
            }
        }
        assertTrue("API call not throttled", throttled);
    }

    /**
     * Call shortcut manager APIs until throttled.
     */
    public static void ensureThrottled(Context context) {
        final ShortcutManager manager = context.getSystemService(ShortcutManager.class);

        retryUntil(() -> !manager.setDynamicShortcuts(list()), "Not throttled.");
    }

    public static void assertCallNotThrottled(Context context) {
        final ShortcutManager manager = context.getSystemService(ShortcutManager.class);

        assertFalse(manager.isRateLimitingActive());

        for (int i = 0; i < 20; i++) {
            assertTrue(manager.setDynamicShortcuts(list()));
            assertTrue(manager.addDynamicShortcuts(list()));
            assertTrue(manager.updateShortcuts(list()));
        }
    }
}
