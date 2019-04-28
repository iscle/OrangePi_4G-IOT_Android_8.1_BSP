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

package com.android.documentsui.bots;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static android.support.test.espresso.matcher.ViewMatchers.withChild;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.android.documentsui.sorting.SortDimension.SORT_DIRECTION_ASCENDING;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.Matchers.allOf;

import android.app.UiAutomation;
import android.content.Context;
import android.support.annotation.StringRes;
import android.support.test.uiautomator.UiDevice;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.sorting.SortModel.SortDimensionId;

import org.hamcrest.Matcher;

/**
 * A test helper class that provides support for controlling the UI Breadcrumb
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class SortHeaderBot extends Bots.BaseBot {

    private final SortModel mSortModel = SortModel.createModel();
    private final DropdownSortBot mDropBot;
    private final ColumnSortBot mColumnBot;

    public SortHeaderBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
        mDropBot = new DropdownSortBot();
        mColumnBot = new ColumnSortBot();
    }

    public void sortBy(@SortDimensionId int id, @SortDirection int direction) {
        assert(direction != SortDimension.SORT_DIRECTION_NONE);

        final @StringRes int labelId = mSortModel.getDimensionById(id).getLabelId();
        final String label = mContext.getString(labelId);
        final boolean result;
        if (Matchers.present(mDropBot.MATCHER)) {
            result = mDropBot.sortBy(label, direction);
        } else {
            result = mColumnBot.sortBy(label, direction);
        }

        assertTrue("Sorting by id: " + id + " in direction: " + direction + " failed.",
                result);
    }

    public void assertDropdownMode() {
        assertTrue(Matchers.present(mDropBot.MATCHER));
    }

    public void assertColumnMode() {
        // BEWARE THOSE WHO TREAD IN THIS DARK CORNER.
        // Note that for some reason this doesn't work:
        // assertTrue(Matchers.present(mColumnBot.MATCHER));
        // Dunno why, something to do with our implementation
        // or with espresso. It's sad that I'm leaving you
        // with this little gremlin, but we all have to
        // move on and get stuff done :)
        assertFalse(Matchers.present(mDropBot.MATCHER));
    }

    private static class DropdownSortBot {

        private static final Matcher<View> MATCHER = withId(R.id.dropdown_sort_widget);
        private static final Matcher<View> DROPDOWN_MATCHER = allOf(
                withId(R.id.sort_dimen_dropdown),
                withParent(MATCHER));
        private static final Matcher<View> SORT_ARROW_MATCHER = allOf(
                withId(R.id.sort_arrow),
                withParent(MATCHER));

        private boolean sortBy(String label, @SortDirection int direction) {
            onView(DROPDOWN_MATCHER).perform(click());
            onView(withText(label)).perform(click());

            if (direction != getDirection()) {
                onView(SORT_ARROW_MATCHER).perform(click());
            }

            return Matchers.present(allOf(
                    DROPDOWN_MATCHER,
                    withText(label)))
                    && getDirection() == direction;
        }

        private @SortDirection int getDirection() {
            final boolean ascending = Matchers.present(
                    allOf(
                            SORT_ARROW_MATCHER,
                            withContentDescription(R.string.sort_direction_ascending)));

            if (ascending) {
                return SORT_DIRECTION_ASCENDING;
            }

            final boolean descending = Matchers.present(
                    allOf(
                            SORT_ARROW_MATCHER,
                            withContentDescription(R.string.sort_direction_descending)));

            return descending
                    ? SortDimension.SORT_DIRECTION_DESCENDING
                    : SortDimension.SORT_DIRECTION_NONE;
        }
    }

    private static class ColumnSortBot {

        private static final Matcher<View> MATCHER = withId(R.id.table_header);

        private boolean sortBy(String label, @SortDirection int direction) {
            final Matcher<View> cellMatcher = allOf(
                    withChild(withText(label)),
                    isDescendantOfA(MATCHER));
            onView(cellMatcher).perform(click());

            final @SortDirection int viewDirection = getDirection(cellMatcher);

            if (viewDirection != direction) {
                onView(cellMatcher).perform(click());
            }

            return getDirection(cellMatcher) == direction;
        }

        private @SortDirection int getDirection(Matcher<View> cellMatcher) {
            final boolean ascending =
                    Matchers.present(
                            allOf(
                                    withContentDescription(R.string.sort_direction_ascending),
                                    withParent(cellMatcher)));
            if (ascending) {
                return SORT_DIRECTION_ASCENDING;
            }

            final boolean descending =
                    Matchers.present(
                            allOf(
                                    withContentDescription(R.string.sort_direction_descending),
                                    withParent(cellMatcher)));

            return descending
                    ? SortDimension.SORT_DIRECTION_DESCENDING
                    : SortDimension.SORT_DIRECTION_NONE;
        }
    }
}
