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

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.State;
import com.android.documentsui.sorting.SortControllerTest.TestWidget;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tablet layout features a fancy columnar sort widget.
 * For this reason we have a tablet layout specific sort controller test.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SortController_TabletLayoutTest {

    private TestWidget mDropHeader;
    private TestWidget mTableHeader;
    private SortController mController;

    @Before
    public void setUp() {
        mDropHeader = new TestWidget();
        mTableHeader = new TestWidget();
        mController = new SortController(mDropHeader, mTableHeader);
    }

    @Test
    public void testGridMode_ShowsDrop() {
        mController.onViewModeChanged(State.MODE_GRID);
        mDropHeader.assertVisible();
        mTableHeader.assertGone();
    }

    @Test
    public void testListMode_ShowsTable() {
        mController.onViewModeChanged(State.MODE_LIST);
        mDropHeader.assertGone();
        mTableHeader.assertVisible();
    }
}
