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

package com.android.car.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.android.car.stream.ui.R;
import com.android.car.view.PagedListView;

import java.util.Stack;

/**
 * Common base Activity for car apps that need to present a Drawer.
 * <p>
 * This Activity manages the overall layout. To use it sub-classes need to:
 * <ul>
 *     <li>Provide the root-items for the Drawer by implementing {@link #getRootAdapter()}.</li>
 *     <li>Add their main content using {@link #setMainContent(int)} or
 *     {@link #setMainContent(View)}. They can also add fragments to the main-content container by
 *     obtaining its id using {@link #getContentContainerId()}</li>
 * </ul>
 * This class will take care of drawer toggling and display.
 * <p>
 * The rootAdapter can implement nested-navigation, in its click-handling, by passing the
 * CarDrawerAdapter for the next level to {@link #switchToAdapter(CarDrawerAdapter)}. This
 * activity will maintain a stack of such adapters. When the user navigates up, it will pop the top
 * adapter off and display its contents again.
 * <p>
 * Any Activity's based on this class need to set their theme to CarDrawerActivityTheme or a
 * derivative.
 * <p>
 * NOTE: This version is based on a regular Activity unlike car-support-lib's CarDrawerActivity
 * which is based on CarActivity.
 */
public abstract class CarDrawerActivity extends AppCompatActivity {
    private static final float COLOR_SWITCH_SLIDE_OFFSET = 0.25f;

    private final Stack<CarDrawerAdapter> mAdapterStack = new Stack<>();
    private DrawerLayout mDrawerLayout;
    private PagedListView mDrawerList;
    private ProgressBar mProgressBar;
    private View mDrawerContent;
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mDrawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.car_drawer_activity);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mDrawerContent = findViewById(R.id.drawer_content);
        mDrawerList = (PagedListView)findViewById(R.id.drawer_list);
        // Let drawer list show unlimited pages of items.
        mDrawerList.setMaxPages(PagedListView.ItemCap.UNLIMITED);
        mProgressBar = (ProgressBar)findViewById(R.id.drawer_progress);

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);

        // Init drawer adapter stack.
        CarDrawerAdapter rootAdapter = getRootAdapter();
        mAdapterStack.push(rootAdapter);
        setToolbarTitleFrom(rootAdapter);
        mDrawerList.setAdapter(rootAdapter);

        setupDrawerToggling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDrawerLayout.closeDrawer(Gravity.LEFT, false /* animation */);
    }

    private void setToolbarTitleFrom(CarDrawerAdapter adapter) {
        if (adapter.getTitle() != null) {
            mToolbar.setTitle(adapter.getTitle());
        } else {
            throw new RuntimeException("CarDrawerAdapter subclass must supply title via " +
                " setTitle()");
        }
        adapter.setTitleChangeListener(mToolbar::setTitle);
    }

    /**
     * Set main content to display in this Activity. It will be added to R.id.content_frame in
     * car_drawer_activity.xml. NOTE: Do not use {@link #setContentView(View)}.
     *
     * @param view View to display as main content.
     */
    public void setMainContent(View view) {
        ViewGroup parent = (ViewGroup) findViewById(getContentContainerId());
        parent.addView(view);
    }

    /**
     * Set main content to display in this Activity. It will be added to R.id.content_frame in
     * car_drawer_activity.xml. NOTE: Do not use {@link #setContentView(int)}.
     *
     * @param resourceId Layout to display as main content.
     */
    public void setMainContent(@LayoutRes int resourceId) {
        ViewGroup parent = (ViewGroup) findViewById(getContentContainerId());
        LayoutInflater inflater = getLayoutInflater();
        inflater.inflate(resourceId, parent, true);
    }

    /**
     * @return Adapter for root content of the Drawer.
     */
    protected abstract CarDrawerAdapter getRootAdapter();

    /**
     * Used to pass in next level of items to display in the Drawer, including updated title. It is
     * pushed on top of the existing adapter in a stack. Navigating up from this level later will
     * pop this adapter off and surface contents of the next adapter at the top of the stack (and
     * its title).
     *
     * @param adapter Adapter for next level of content in the drawer.
     */
    public final void switchToAdapter(CarDrawerAdapter adapter) {
        mAdapterStack.peek().setTitleChangeListener(null);
        mAdapterStack.push(adapter);
        setTitleAndSwitchToAdapter(adapter);
    }

    /**
     * Close the drawer if open.
     */
    public void closeDrawer() {
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    /**
     * Used to open the drawer.
     */
    public void openDrawer() {
        if (!mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.openDrawer(Gravity.LEFT);
        }
    }

    /**
     * @param listener Listener to be notified of Drawer events.
     */
    public void addDrawerListener(@NonNull DrawerLayout.DrawerListener listener) {
        mDrawerLayout.addDrawerListener(listener);
    }

    /**
     * @param listener Listener to be notified of Drawer events.
     */
    public void removeDrawerListener(@NonNull DrawerLayout.DrawerListener listener) {
        mDrawerLayout.removeDrawerListener(listener);
    }

    /**
     * Used to switch between the Drawer PagedListView and the "loading" progress-bar while the next
     * level's adapter contents are being fetched.
     *
     * @param enable If true, the progress-bar is displayed. If false, the Drawer PagedListView is
     *               added.
     */
    public void showLoadingProgressBar(boolean enable) {
        mDrawerList.setVisibility(enable ? View.INVISIBLE : View.VISIBLE);
        mProgressBar.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    /**
     * Get the id of the main content Container which is a FrameLayout. Subclasses can add their own
     * content/fragments inside here.
     *
     * @return Id of FrameLayout where main content of the subclass Activity can be added.
     */
    protected int getContentContainerId() {
        return R.id.content_frame;
    }

    private void setupDrawerToggling() {
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.car_drawer_open,
                R.string.car_drawer_close
        );
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                setTitleAndArrowColor(slideOffset >= COLOR_SWITCH_SLIDE_OFFSET);
            }
            @Override
            public void onDrawerOpened(View drawerView) {}
            @Override
            public void onDrawerClosed(View drawerView) {
                // If drawer is closed for any reason, revert stack/drawer to initial root state.
                cleanupStackAndShowRoot();
                scrollToPosition(0);
            }
            @Override
            public void onDrawerStateChanged(int newState) {}
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void setTitleAndArrowColor(boolean drawerOpen) {
        // When drawer open, use car_title, which resolves to appropriate color depending on
        // day-night mode. When drawer is closed, we always use light color.
        int titleColorResId =  drawerOpen ?
                R.color.car_title : R.color.car_title_light;
        int titleColor = getColor(titleColorResId);
        mToolbar.setTitleTextColor(titleColor);
        mDrawerToggle.getDrawerArrowDrawable().setColor(titleColor);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();

        // In case we're restarting after a config change (e.g. day, night switch), set colors
        // again. Doing it here so that Drawer state is fully synced and we know if its open or not.
        // NOTE: isDrawerOpen must be passed the second child of the DrawerLayout.
        setTitleAndArrowColor(mDrawerLayout.isDrawerOpen(mDrawerContent));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle home-click and see if we can navigate up in the drawer.
        if (item != null && item.getItemId() == android.R.id.home && maybeHandleUpClick()) {
            return true;
        }

        // DrawerToggle gets next chance to handle up-clicks (and any other clicks).
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setTitleAndSwitchToAdapter(CarDrawerAdapter adapter) {
        setToolbarTitleFrom(adapter);
        // NOTE: We don't use swapAdapter() since different levels in the Drawer may switch between
        // car_menu_list_item_normal, car_menu_list_item_small and car_list_empty layouts.
        mDrawerList.getRecyclerView().setAdapter(adapter);
        scrollToPosition(0);
    }

    public void scrollToPosition(int position) {
        mDrawerList.getRecyclerView().smoothScrollToPosition(position);
    }

    private boolean maybeHandleUpClick() {
        if (mAdapterStack.size() > 1) {
            CarDrawerAdapter adapter = mAdapterStack.pop();
            adapter.setTitleChangeListener(null);
            adapter.cleanup();
            setTitleAndSwitchToAdapter(mAdapterStack.peek());
            return true;
        }
        return false;
    }

    /** Clears stack down to root adapter and switches to root adapter. */
    private void cleanupStackAndShowRoot() {
        while (mAdapterStack.size() > 1) {
            CarDrawerAdapter adapter = mAdapterStack.pop();
            adapter.setTitleChangeListener(null);
            adapter.cleanup();
        }
        setTitleAndSwitchToAdapter(mAdapterStack.peek());
    }
}
