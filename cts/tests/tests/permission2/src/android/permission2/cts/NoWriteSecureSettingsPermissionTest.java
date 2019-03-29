/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permission2.cts;

import android.Manifest;
import android.content.ContentValues;
import android.provider.Settings;
import android.test.AndroidTestCase;

/**
 * Verify secure settings cannot be written to without required permissions.
 */
public class NoWriteSecureSettingsPermissionTest extends AndroidTestCase {

    /**
     * Verify that write to secure settings requires permissions.
     * This test app must have WRITE_SETTINGS permission but not WRITE_SECURE_SETTINGS
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#WRITE_SECURE_SETTINGS}
     */
    public void testWriteSecureSettings() {
        try {
            ContentValues values = new ContentValues();
            values.put(Settings.Secure.NAME, Settings.Secure.ACCESSIBILITY_ENABLED);
            values.put(Settings.Secure.VALUE, Boolean.TRUE);
            getContext().getContentResolver().insert(Settings.Secure.CONTENT_URI, values);
            fail("expected SecurityException requiring "
                    + Manifest.permission.WRITE_SECURE_SETTINGS);
        } catch (SecurityException expected) {
           /* do nothing */
        }
    }
}
