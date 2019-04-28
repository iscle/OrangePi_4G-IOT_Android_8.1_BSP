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

package com.android.documentsui.sorting;

import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.android.documentsui.base.State;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SortControllerTest {

    private TestWidget mTableHeader;
    private TestWidget mDropHeader;
    private SortController mController;

    @Test
    public void testGridMode_ShowsDrop() {
        createWidget(true);
        mController.onViewModeChanged(State.MODE_GRID);
        mDropHeader.assertVisible();
        mTableHeader.assertGone();
    }

    @Test
    public void testListMode_ShowsDrop_NoHeader() {
        createWidget(false);
        mController.onViewModeChanged(State.MODE_LIST);
        mDropHeader.assertVisible();
    }

    @Test
    public void testListMode_ShowsTable() {
        createWidget(true);
        mController.onViewModeChanged(State.MODE_LIST);
        mDropHeader.assertGone();
        mTableHeader.assertVisible();
    }

    @Test
    public void testDestroysWidgets() {
        createWidget(true);
        mController.destroy();

        mDropHeader.assertDestroyed();
        mTableHeader.assertDestroyed();
    }

    private void createWidget(boolean hasTableHeader) {
        mDropHeader = new TestWidget();
        if (hasTableHeader) {
            mTableHeader = new TestWidget();
        }
        mController = new SortController(mDropHeader, mTableHeader);
    }

    static class TestWidget implements SortController.WidgetController {
        private int mVisibility;
        private boolean mDestroyed;

        @Override
        public void setVisibility(int visibility) {
            mVisibility = visibility;
        }

        @Override
        public void destroy() {
            mDestroyed = true;
        }

        void assertVisible() {
            assertTrue(
                    "Expected mode VISIBLE, but was " + mVisibility,
                    mVisibility == View.VISIBLE);
        }

        void assertGone() {
            assertTrue(
                    "Expected mode GONE, but was " + mVisibility,
                    mVisibility == View.GONE);
        }

        void assertDestroyed() {
            assertTrue("Widget is not destroyed.", mDestroyed);
        }
    }
}
