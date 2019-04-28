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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.VERBOSE;

import android.annotation.Nullable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.Toolbar;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;

import java.util.function.IntConsumer;

/**
 * A facade over the portions of the app and drawer toolbars.
 */
public class NavigationViewManager {

    private static final String TAG = "NavigationViewManager";

    private final DrawerController mDrawer;
    private final Toolbar mToolbar;
    private final State mState;
    private final NavigationViewManager.Environment mEnv;
    private final Breadcrumb mBreadcrumb;

    public NavigationViewManager(
            DrawerController drawer,
            Toolbar toolbar,
            State state,
            NavigationViewManager.Environment env,
            Breadcrumb breadcrumb) {

        mToolbar = toolbar;
        mDrawer = drawer;
        mState = state;
        mEnv = env;
        mBreadcrumb = breadcrumb;
        mBreadcrumb.setup(env, state, this::onNavigationItemSelected);

        mToolbar.setNavigationOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onNavigationIconClicked();
                    }
                });
    }

    private void onNavigationIconClicked() {
        if (mDrawer.isPresent()) {
            mDrawer.setOpen(true);
        }
    }

    void onNavigationItemSelected(int position) {
        boolean changed = false;
        while (mState.stack.size() > position + 1) {
            changed = true;
            mState.stack.pop();
        }
        if (changed) {
            mEnv.refreshCurrentRootAndDirectory(AnimationView.ANIM_LEAVE);
        }
    }

    public void update() {

        // TODO: Looks to me like this block is never getting hit.
        if (mEnv.isSearchExpanded()) {
            mToolbar.setTitle(null);
            mBreadcrumb.show(false);
            return;
        }

        mDrawer.setTitle(mEnv.getDrawerTitle());

        mToolbar.setNavigationIcon(getActionBarIcon());
        mToolbar.setNavigationContentDescription(R.string.drawer_open);

        if (mState.stack.size() <= 1) {
            mBreadcrumb.show(false);
            String title = mEnv.getCurrentRoot().title;
            if (VERBOSE) Log.v(TAG, "New toolbar title is: " + title);
            mToolbar.setTitle(title);
        } else {
            mBreadcrumb.show(true);
            mToolbar.setTitle(null);
            mBreadcrumb.postUpdate();
        }

        if (VERBOSE) Log.v(TAG, "Final toolbar title is: " + mToolbar.getTitle());
    }

    // Hamburger if drawer is present, else sad nullness.
    private @Nullable Drawable getActionBarIcon() {
        if (mDrawer.isPresent()) {
            return mToolbar.getContext().getDrawable(R.drawable.ic_hamburger);
        } else {
            return null;
        }
    }

    void revealRootsDrawer(boolean open) {
        mDrawer.setOpen(open);
    }

    interface Breadcrumb {
        void setup(Environment env, State state, IntConsumer listener);
        void show(boolean visibility);
        void postUpdate();
    }

    interface Environment {
        @Deprecated  // Use CommonAddones#getCurrentRoot
        RootInfo getCurrentRoot();
        String getDrawerTitle();
        @Deprecated  // Use CommonAddones#refreshCurrentRootAndDirectory
        void refreshCurrentRootAndDirectory(int animation);
        boolean isSearchExpanded();
    }
}
