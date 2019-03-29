/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.content.res.cts;

import android.content.res.Resources;
import android.os.Build;
import android.test.AndroidTestCase;
import android.util.TypedValue;

/**
 * Tests that private attributes are correctly placed in a separate type to
 * prevent future releases from stomping over private attributes with new public ones.
 */
public class PrivateAttributeTest extends AndroidTestCase {

    private static final int sLastPublicAttr = 0x0101056d;

    public void testNoAttributesAfterLastPublicAttribute() throws Exception {
        if (!Build.VERSION.CODENAME.equals("REL")) {
            // Skip the test if this is not a release build.
            // (resource IDs are not finalized until the OS API number is frozen).
            return;
        }

        final Resources res = getContext().getResources();

        final String lastPublicName;
        try {
            lastPublicName = res.getResourceEntryName(sLastPublicAttr);
        } catch (Resources.NotFoundException e) {
            throw new AssertionError("Last public resource was not found", e);
        }

        int currentAttr = sLastPublicAttr;
        while (currentAttr < 0x0101ffff) {
            currentAttr++;
            try {
                final String name = res.getResourceEntryName(currentAttr);
                throw new AssertionError("Found attribute '" + name + "'"
                        + " (0x" + Integer.toHexString(currentAttr) + ")"
                        + " after last public framework attribute "
                        + "'" + lastPublicName + "'"
                        + " (0x" + Integer.toHexString(sLastPublicAttr) + ")");
            } catch (Resources.NotFoundException e) {
                // continue
            }
        }
    }
}
