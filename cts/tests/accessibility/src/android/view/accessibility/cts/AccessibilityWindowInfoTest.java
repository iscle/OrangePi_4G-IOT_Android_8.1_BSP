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

import android.graphics.Rect;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Class for testing {@link AccessibilityWindowInfo}.
 */
public class AccessibilityWindowInfoTest extends AndroidTestCase {

    @SmallTest
    public void testObtain() {
        AccessibilityWindowInfo w1 = AccessibilityWindowInfo.obtain();
        assertNotNull(w1);

        AccessibilityWindowInfo w2 = AccessibilityWindowInfo.obtain(w1);
        assertNotSame(w1, w2);
        assertTrue(areWindowsEqual(w1, w2));
    }

    @SmallTest
    public void testParceling() {
        Parcel parcel = Parcel.obtain();
        AccessibilityWindowInfo w1 = AccessibilityWindowInfo.obtain();
        w1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityWindowInfo w2 = AccessibilityWindowInfo.CREATOR.createFromParcel(parcel);
        assertNotSame(w1, w2);
        assertTrue(areWindowsEqual(w1, w2));
    }

    @SmallTest
    public void testDefaultValues() {
        AccessibilityWindowInfo w = AccessibilityWindowInfo.obtain();
        assertEquals(0, w.getChildCount());
        assertEquals(-1, w.getType());
        assertEquals(-1, w.getLayer());
        assertEquals(-1, w.getId());
        assertEquals(0, w.describeContents());
        assertNull(w.getParent());
        assertNull(w.getRoot());
        assertFalse(w.isAccessibilityFocused());
        assertFalse(w.isActive());
        assertFalse(w.isFocused());
        assertNull(w.getTitle());

        Rect rect = new Rect();
        w.getBoundsInScreen(rect);
        assertTrue(rect.isEmpty());

        try {
            w.getChild(0);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected.
        }
    }

    @SmallTest
    public void testRecycle() {
        AccessibilityWindowInfo w = AccessibilityWindowInfo.obtain();
        w.recycle();

        try {
            w.recycle();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected.
        }
    }

    private boolean areWindowsEqual(AccessibilityWindowInfo w1, AccessibilityWindowInfo w2) {
        boolean equality = w1.equals(w2);
        equality &= TextUtils.equals(w1.getTitle(), w2.getTitle());
        equality &= w1.isAccessibilityFocused() == w2.isAccessibilityFocused();
        equality &= w1.isActive() == w2.isActive();
        equality &= w1.getType() == w2.getType();
        equality &= w1.getLayer() == w2.getLayer();
        Rect bounds1 = new Rect();
        Rect bounds2 = new Rect();
        w1.getBoundsInScreen(bounds1);
        w2.getBoundsInScreen(bounds2);
        equality &= bounds1.equals(bounds2);
        return equality;
    }
}
