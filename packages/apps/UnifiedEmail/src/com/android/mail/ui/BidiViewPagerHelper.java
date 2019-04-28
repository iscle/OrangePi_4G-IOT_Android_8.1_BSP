/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.ui;

import android.support.v4.view.ViewPager;

import com.android.mail.utils.ViewUtils;

/**
 * Helper class for adding RTL support to a ViewPager. Note that this also requires a PagerAdapter
 * that returns the pages in reversed order in the RTL case.
 */
public class BidiViewPagerHelper {
    private ViewPager mViewPager;

    public BidiViewPagerHelper(ViewPager viewPager) {
        mViewPager = viewPager;
    }

    public int getFirstPage() {
        return getIndexFromBidiIndex(0);
    }

    public int getLastPage() {
        return getIndexFromBidiIndex(mViewPager.getAdapter().getCount() - 1);
    }

    public int getPreviousPage() {
        return clampToBounds(getIndexFromBidiIndex(getBidiIndex(mViewPager.getCurrentItem()) - 1));
    }

    public int getNextPage() {
        return clampToBounds(getIndexFromBidiIndex(getBidiIndex(mViewPager.getCurrentItem()) + 1));
    }

    private int clampToBounds(int position) {
        return Math.max(0, Math.min(mViewPager.getAdapter().getCount() - 1, position));
    }

    private int getBidiIndex(int position) {
        if (ViewUtils.isViewRtl(mViewPager)) {
            return mViewPager.getAdapter().getCount() - 1 - position;
        } else {
            return position;
        }
    }

    /** Inverse of {@link #getBidiIndex} function. */
    private int getIndexFromBidiIndex(int position) {
        // getBidiIndex is equal to its inverse function so we are just calling getBidiIndex.
        return getBidiIndex(position);
    }
}
