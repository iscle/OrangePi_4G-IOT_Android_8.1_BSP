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

package android.view.accessibility.cts;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.accessibility.AccessibilityNodeInfo.CollectionInfo;

/**
 * Class for testing {@link CollectionInfo}.
 */
public class AccessibilityNodeInfo_CollectionInfoTest extends AndroidTestCase {

    @SmallTest
    public void testObtain() {
        CollectionInfo c;

        c = CollectionInfo.obtain(0, 1, true);
        assertNotNull(c);
        assertEquals(0, c.getRowCount());
        assertEquals(1, c.getColumnCount());
        assertTrue(c.isHierarchical());
        assertEquals(CollectionInfo.SELECTION_MODE_NONE, c.getSelectionMode());

        c = CollectionInfo.obtain(1, 2, true, CollectionInfo.SELECTION_MODE_MULTIPLE);
        assertNotNull(c);
        assertEquals(1, c.getRowCount());
        assertEquals(2, c.getColumnCount());
        assertTrue(c.isHierarchical());
        assertEquals(CollectionInfo.SELECTION_MODE_MULTIPLE, c.getSelectionMode());
    }
}
