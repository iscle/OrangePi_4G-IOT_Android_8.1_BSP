/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import android.view.View;

/** Handles whether or not to hide/show the loading progress spinner. */
public class LoadingSpinnerController {
    private boolean mHasLoadedACategory;
    private View mListView;
    private DeletionHelperActivity mParentActivity;

    /**
     * Initializes the spinner with an activity that contains both a content view and a loading
     * view.
     */
    public LoadingSpinnerController(DeletionHelperActivity activity) {
        mParentActivity = activity;
    }

    /**
     * Initializes the loading progress bar.
     *
     * @param listView A content view to potentially swap in for the loading screen.
     */
    public void initializeLoading(View listView) {
        mListView = listView;
        if (!mHasLoadedACategory) {
            setLoading(true);
        }
    }

    /** If a category loads, we should hide the loading progress bar. This hides the loading. */
    public void onCategoryLoad() {
        mHasLoadedACategory = true;
        setLoading(false);
    }

    private void setLoading(boolean isLoading) {
        if (mListView != null && mParentActivity.isLoadingVisible() != isLoading) {
            mParentActivity.setLoading(mListView, isLoading, true);
        }
    }
}
