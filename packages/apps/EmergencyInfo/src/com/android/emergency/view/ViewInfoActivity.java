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
package com.android.emergency.view;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TabLayout.TabLayoutOnPageChangeListener;
import android.support.design.widget.TabLayout.ViewPagerOnTabSelectedListener;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.ViewFlipper;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.util.PreferenceUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.ArrayList;

/**
 * Activity for viewing emergency information.
 */
public class ViewInfoActivity extends Activity {
    private TextView mPersonalCardLargeItem;
    private SharedPreferences mSharedPreferences;
    private LinearLayout mPersonalCard;
    private ViewFlipper mViewFlipper;
    private ViewPagerAdapter mTabsAdapter;
    private TabLayout mTabLayout;
    private ArrayList<Pair<String, Fragment>> mFragments;
    private Menu mMenu;

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        setupTabs();
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_activity_layout);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPersonalCard = (LinearLayout) findViewById(R.id.name_and_dob_linear_layout);
        mPersonalCardLargeItem = (TextView) findViewById(R.id.personal_card_large);
        mViewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);

        MetricsLogger.visible(this, MetricsEvent.ACTION_VIEW_EMERGENCY_INFO);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadName();
        // Update the tabs: new info might have been added/deleted from the edit screen that
        // could lead to adding/removing a fragment
        setupTabs();
        maybeHideTabs();
    }

    private void loadName() {
        String name = mSharedPreferences.getString(PreferenceKeys.KEY_NAME, "");
        if (TextUtils.isEmpty(name)) {
            mPersonalCard.setVisibility(View.GONE);
        } else {
            mPersonalCard.setVisibility(View.VISIBLE);
            mPersonalCardLargeItem.setText(name);
        }
    }

    private void maybeHideTabs() {
        // Show a TextView with "No information provided" if there are no fragments.
        if (mFragments.size() == 0) {
            mViewFlipper.setDisplayedChild(
                    mViewFlipper.indexOfChild(findViewById(R.id.no_info)));
        } else {
            mViewFlipper.setDisplayedChild(mViewFlipper.indexOfChild(findViewById(R.id.tabs)));
        }

        TabLayout tabLayout = mTabLayout;
        if (mFragments.size() <= 1) {
            tabLayout.setVisibility(View.GONE);
        } else {
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view_info_menu, menu);
        mMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.action_edit:
                Intent intent = new Intent(this, EditInfoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Return the tab layout. */
    @VisibleForTesting
    public TabLayout getTabLayout() {
        return mTabLayout;
    }

    @VisibleForTesting
    public Menu getMenu() {
        return mMenu;
    }

    /** Return the fragments. */
    @VisibleForTesting
    public ArrayList<Pair<String, Fragment>> getFragments() {
        return mFragments;
    }

    private ArrayList<Pair<String, Fragment>> setUpFragments() {
        // Return only the fragments that have at least one piece of information set:
        ArrayList<Pair<String, Fragment>> fragments = new ArrayList<>(2);

        if (PreferenceUtils.hasAtLeastOnePreferenceSet(this)) {
            fragments.add(Pair.create(getResources().getString(R.string.tab_title_info),
                    ViewEmergencyInfoFragment.newInstance()));
        }
        if (PreferenceUtils.hasAtLeastOneEmergencyContact(this)) {
            fragments.add(Pair.create(getResources().getString(R.string.tab_title_contacts),
                    ViewEmergencyContactsFragment.newInstance()));
        }
        return fragments;
    }

    private void setupTabs() {
        mFragments = setUpFragments();
        mTabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        if (mTabsAdapter == null) {
            // The viewpager that will host the section contents.
            ViewPager viewPager = (ViewPager) findViewById(R.id.view_pager);
            mTabsAdapter = new ViewPagerAdapter(getFragmentManager());
            viewPager.setAdapter(mTabsAdapter);
            mTabLayout.setTabsFromPagerAdapter(mTabsAdapter);

            // Set a listener via setOnTabSelectedListener(OnTabSelectedListener) to be notified
            // when any tab's selection state has been changed.
            mTabLayout.setOnTabSelectedListener(
                    new TabLayout.ViewPagerOnTabSelectedListener(viewPager));

            // Use a TabLayout.TabLayoutOnPageChangeListener to forward the scroll and selection
            // changes to this layout
            viewPager.addOnPageChangeListener(new TabLayoutOnPageChangeListener(mTabLayout));
        } else {
            mTabsAdapter.notifyDataSetChanged();
            mTabLayout.setTabsFromPagerAdapter(mTabsAdapter);
        }
    }

    /** The adapter used to handle the two fragments. */
    protected class ViewPagerAdapter extends FragmentStatePagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position).second;
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragments.get(position).first;
        }

        @Override
        public int getItemPosition(Object object) {
            // The default implementation assumes that items will never change position and always
            // returns POSITION_UNCHANGED. This is how you can specify that the positions can change
            return FragmentStatePagerAdapter.POSITION_NONE;
        }
    }
}
