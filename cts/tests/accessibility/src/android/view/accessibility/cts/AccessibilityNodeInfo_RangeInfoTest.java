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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.RangeInfo;

/**
 * Class for testing {@link AccessibilityNodeInfo.RangeInfo}.
 */
public class AccessibilityNodeInfo_RangeInfoTest extends AndroidTestCase {

    /** Allowed tolerance for floating point equality comparisons. */
    public static final float FLOAT_TOLERANCE = 0.001f;

    @SmallTest
    public void testObtain() {
        RangeInfo r;

        r = RangeInfo.obtain(RangeInfo.RANGE_TYPE_INT, -100, 0, -50);
        assertEquals(RangeInfo.RANGE_TYPE_INT, r.getType());
        assertEquals(-100, r.getMin(), FLOAT_TOLERANCE);
        assertEquals(0, r.getMax(), FLOAT_TOLERANCE);
        assertEquals(-50, r.getCurrent(), FLOAT_TOLERANCE);

        r = RangeInfo.obtain(RangeInfo.RANGE_TYPE_FLOAT, -1.5f, 1.5f, 0.0f);
        assertEquals(RangeInfo.RANGE_TYPE_FLOAT, r.getType());
        assertEquals(-1.5f, r.getMin(), FLOAT_TOLERANCE);
        assertEquals(1.5f, r.getMax(), FLOAT_TOLERANCE);
        assertEquals(0.0f, r.getCurrent(), FLOAT_TOLERANCE);

        r = RangeInfo.obtain(RangeInfo.RANGE_TYPE_PERCENT, 0.0f, 100.0f, 50.0f);
        assertEquals(RangeInfo.RANGE_TYPE_PERCENT, r.getType());
        assertEquals(0.0f, r.getMin(), FLOAT_TOLERANCE);
        assertEquals(100.0f, r.getMax(), FLOAT_TOLERANCE);
        assertEquals(50.0f, r.getCurrent(), FLOAT_TOLERANCE);
    }
}
