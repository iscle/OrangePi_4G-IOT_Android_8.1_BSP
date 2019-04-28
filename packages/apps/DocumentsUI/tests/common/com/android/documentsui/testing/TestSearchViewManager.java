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

package com.android.documentsui.testing;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.queries.CommandInterceptor;
import com.android.documentsui.queries.SearchViewManager;

/**
 * Test copy of {@link com.android.documentsui.queries.SearchViewManager}
 *
 * Specficially used to test whether {@link #showMenu(boolean)}
 * and {@link #updateMenu()} are called.
 */
public class TestSearchViewManager extends SearchViewManager {

    public boolean isSearching;

    private boolean mUpdateMenuCalled;
    private boolean mShowMenuCalled;

    public TestSearchViewManager() {
        super(
                new SearchManagerListener() {
                    @Override
                    public void onSearchChanged(String query) { }
                    @Override
                    public void onSearchFinished() { }
                    @Override
                    public void onSearchViewChanged(boolean opened) { }
                },
                new CommandInterceptor(new TestFeatures()),
                null);
    }

    @Override
    public boolean isSearching() {
        return isSearching;
    }

    @Override
    public void showMenu(DocumentStack stack) {
        mShowMenuCalled = true;
    }

    @Override
    public void updateMenu() {
        mUpdateMenuCalled = true;
    }

    public boolean showMenuCalled() {
        return mShowMenuCalled;
    }

    public boolean updateMenuCalled() {
        return mUpdateMenuCalled;
    }
}
