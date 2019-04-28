/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.documentsui.testing.TestRecyclerView;
import com.android.documentsui.testing.Views;

import java.util.List;

@SmallTest
public class AccessibilityTest extends AndroidTestCase {

    private static final List<String> ITEMS = TestData.create(10);

    private TestRecyclerView mView;
    private AccessibilityEventRouter mAccessibilityDelegate;
    private boolean mCallbackCalled = false;

    @Override
    public void setUp() throws Exception {
        mView = TestRecyclerView.create(ITEMS);
        mAccessibilityDelegate = new AccessibilityEventRouter(mView, (View v) -> {
            mCallbackCalled = true;
            return true;
        });
        mView.setAccessibilityDelegateCompat(mAccessibilityDelegate);
    }

    public void test_announceSelected() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertTrue(info.isSelected());
    }

    public void test_routesAccessibilityClicks() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.getItemDelegate().performAccessibilityAction(item, AccessibilityNodeInfoCompat.ACTION_CLICK, null);
        assertTrue(mCallbackCalled);
    }
}
