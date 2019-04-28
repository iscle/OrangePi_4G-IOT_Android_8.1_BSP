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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.support.annotation.Nullable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortModel.UpdateListener;
import com.android.documentsui.sorting.SortModel.UpdateType;
import com.android.documentsui.testing.Parcelables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SortModelTest {

    private static final SortDimension DIMENSION_1 = new SortDimension.Builder()
            .withId(1)
            .withLabelId(R.string.sort_dimension_name)
            .withDataType(SortDimension.DATA_TYPE_STRING)
            .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
            .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
            .withVisibility(View.VISIBLE)
            .build();

    private static final SortDimension DIMENSION_2 = new SortDimension.Builder()
            .withId(2)
            .withLabelId(R.string.sort_dimension_date)
            .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
            .withDefaultSortDirection(SortDimension.SORT_DIRECTION_DESCENDING)
            .build();

    private static final SortDimension DIMENSION_3 = new SortDimension.Builder()
            .withId(3)
            .withLabelId(R.string.sort_dimension_size)
            .withDataType(SortDimension.DATA_TYPE_NUMBER)
            .withSortCapability(SortDimension.SORT_CAPABILITY_NONE)
            .build();

    private static final SortDimension[] DIMENSIONS = new SortDimension[] {
                    DIMENSION_1,
                    DIMENSION_2,
                    DIMENSION_3
            };

    private static final DummyListener DUMMY_LISTENER = new DummyListener();

    private SortModel mModel;

    @Before
    public void setUp() {
        mModel = new SortModel(Arrays.asList(DIMENSIONS));
        mModel.addListener(DUMMY_LISTENER);
    }

    @Test
    public void testSizeEquals() {
        assertEquals(DIMENSIONS.length, mModel.getSize());
    }

    @Test
    public void testDimensionSame_getDimensionAt() {
        for (int i = 0; i < DIMENSIONS.length; ++i) {
            assertSame(DIMENSIONS[i], mModel.getDimensionAt(i));
        }
    }

    @Test
    public void testDimensionSame_getDimensionById() {
        for (SortDimension dimension : DIMENSIONS) {
            assertSame(dimension, mModel.getDimensionById(dimension.getId()));
        }
    }

    @Test
    public void testSetDimensionVisibility() {
        assertEquals(View.VISIBLE, DIMENSION_1.getVisibility());

        mModel.setDimensionVisibility(DIMENSION_1.getId(), View.GONE);

        assertEquals(View.GONE, DIMENSION_1.getVisibility());
        assertEquals(SortModel.UPDATE_TYPE_VISIBILITY, DUMMY_LISTENER.mLastUpdateType);
    }

    @Test
    public void testNotSortedByDefault() {
        assertEquals(SortModel.SORT_DIMENSION_ID_UNKNOWN, mModel.getSortedDimensionId());
    }

    @Test
    public void testSortByDefault() {
        mModel.setDefaultDimension(DIMENSION_1.getId());

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_1, sortedDimension);
        assertEquals(DIMENSION_1.getDefaultSortDirection(), sortedDimension.getSortDirection());

        assertSame(mModel, DUMMY_LISTENER.mLastSortModel);
        assertEquals(SortModel.UPDATE_TYPE_SORTING, DUMMY_LISTENER.mLastUpdateType);
    }

    @Test
    public void testSortByUser() {
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_DESCENDING);

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_1, sortedDimension);
        assertEquals(SortDimension.SORT_DIRECTION_DESCENDING, sortedDimension.getSortDirection());

        assertSame(mModel, DUMMY_LISTENER.mLastSortModel);
        assertEquals(SortModel.UPDATE_TYPE_SORTING, DUMMY_LISTENER.mLastUpdateType);
    }

    @Test
    public void testOrderNotChanged_sortByDefaultAfterSortByUser() {
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_DESCENDING);
        mModel.setDefaultDimension(DIMENSION_2.getId());

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_1, sortedDimension);
        assertEquals(SortDimension.SORT_DIRECTION_DESCENDING, sortedDimension.getSortDirection());

        assertSame(mModel, DUMMY_LISTENER.mLastSortModel);
        assertEquals(SortModel.UPDATE_TYPE_SORTING, DUMMY_LISTENER.mLastUpdateType);
    }

    @Test
    public void testOrderChanged_sortByUserAfterSortByDefault() {
        mModel.setDefaultDimension(DIMENSION_2.getId());
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_DESCENDING);

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_1, sortedDimension);
        assertEquals(SortDimension.SORT_DIRECTION_DESCENDING, sortedDimension.getSortDirection());

        assertSame(mModel, DUMMY_LISTENER.mLastSortModel);
        assertEquals(SortModel.UPDATE_TYPE_SORTING, DUMMY_LISTENER.mLastUpdateType);
    }

    @Test
    public void testSortByUserTwice() {
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_DESCENDING);
        mModel.sortByUser(DIMENSION_2.getId(), SortDimension.SORT_DIRECTION_ASCENDING);

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_2, sortedDimension);
        assertEquals(SortDimension.SORT_DIRECTION_ASCENDING, sortedDimension.getSortDirection());

        assertEquals(SortDimension.SORT_DIRECTION_NONE, DIMENSION_1.getSortDirection());
    }

    @Test
    public void testSortByUserTwice_sameDimension() {
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_DESCENDING);
        mModel.sortByUser(DIMENSION_1.getId(), SortDimension.SORT_DIRECTION_ASCENDING);

        SortDimension sortedDimension = getSortedDimension();
        assertSame(DIMENSION_1, sortedDimension);
        assertEquals(SortDimension.SORT_DIRECTION_ASCENDING, sortedDimension.getSortDirection());
    }

    @Test
    public void testSetDefaultDimension_noSortingCapability() {
        try {
            mModel.setDefaultDimension(DIMENSION_3.getId());
            fail("Expect exception but not raised.");
        } catch(IllegalStateException expected) {
            // Expected
        }
    }

    @Test
    public void testSortByUser_noSortingCapability() {
        try {
            mModel.sortByUser(DIMENSION_3.getId(), SortDimension.SORT_DIRECTION_DESCENDING);
            fail("Expect exception but not raised.");
        } catch(IllegalStateException expected) {
            // Expected
        }
    }

    @Test
    public void testParceling() {
        mModel.setDefaultDimension(DIMENSION_1.getId());
        mModel.sortByUser(DIMENSION_2.getId(), SortDimension.SORT_DIRECTION_DESCENDING);
        mModel.setDimensionVisibility(DIMENSION_3.getId(), View.GONE);

        Parcelables.assertParcelable(mModel, 0);
    }

    @Test
    public void testParceling_NoSortedDimension() {
        Parcelables.assertParcelable(mModel, 0);
    }

    private @Nullable SortDimension getSortedDimension() {
        final int sortedDimensionId = mModel.getSortedDimensionId();
        return mModel.getDimensionById(sortedDimensionId);
    }

    private static class DummyListener implements UpdateListener {

        private SortModel mLastSortModel;
        private @UpdateType int mLastUpdateType;

        @Override
        public void onModelUpdate(SortModel newModel, @UpdateType int updateType) {
            mLastSortModel = newModel;
            mLastUpdateType = updateType;
        }
    }
}
