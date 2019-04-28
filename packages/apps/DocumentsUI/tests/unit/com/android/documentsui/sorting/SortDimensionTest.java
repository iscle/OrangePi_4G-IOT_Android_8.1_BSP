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

import static junit.framework.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension.SortCapability;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.testing.Parcelables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SortDimensionTest {

    private static final int ID = 1;
    private static final int LABEL_ID = R.string.sort_dimension_name;
    private static final @SortCapability int CAPABILITY =
            SortDimension.SORT_CAPABILITY_BOTH_DIRECTION;
    private static final @SortDirection int DEFAULT_DIRECTION =
            SortDimension.SORT_DIRECTION_DESCENDING;
    private static final @SortDirection int ALTERNATIVE_DIRECTION =
            SortDimension.SORT_DIRECTION_ASCENDING;
    private static final int DATA_TYPE = SortDimension.DATA_TYPE_NUMBER;
    private static final int VISIBILITY = View.VISIBLE;

    private SortDimension mDimension;

    @Before
    public void setUp() {
        mDimension = new SortDimension.Builder()
                .withId(ID)
                .withLabelId(LABEL_ID)
                .withSortCapability(CAPABILITY)
                .withDefaultSortDirection(DEFAULT_DIRECTION)
                .withDataType(DATA_TYPE)
                .withVisibility(VISIBILITY)
                .build();
    }

    @Test
    public void testBuilder() {
        assertEquals(ID, mDimension.getId());
        assertEquals(LABEL_ID, mDimension.getLabelId());
        assertEquals(CAPABILITY, mDimension.getSortCapability());
        assertEquals(DEFAULT_DIRECTION, mDimension.getDefaultSortDirection());
        assertEquals(DATA_TYPE, mDimension.getDataType());
        assertEquals(VISIBILITY, mDimension.getVisibility());

        assertEquals(SortDimension.SORT_DIRECTION_NONE, mDimension.getSortDirection());
    }

    @Test
    public void testNextDirection() {
        assertEquals(DEFAULT_DIRECTION, mDimension.getNextDirection());
    }

    @Test
    public void testNextDirection_sortByDefaultDirection() {
        mDimension.mSortDirection = DEFAULT_DIRECTION;
        assertEquals(ALTERNATIVE_DIRECTION, mDimension.getNextDirection());
    }
    @Test
    public void testParceling() {
        Parcelables.assertParcelable(mDimension, 0);
    }
}
