/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.ui.sidepanel;


public abstract class SubMenuItem extends ActionItem {
    private final SideFragmentManager mSideFragmentManager;

    public SubMenuItem(String title, SideFragmentManager fragmentManager) {
        this(title, null, fragmentManager);
    }

    public SubMenuItem(String title, String description, SideFragmentManager fragmentManager) {
        super(title, description);
        mSideFragmentManager = fragmentManager;
    }

    @Override
    protected void onSelected() {
        launchFragment();
    }

    protected void launchFragment() {
        mSideFragmentManager.show(getFragment());
    }

    protected abstract SideFragment getFragment();
}
