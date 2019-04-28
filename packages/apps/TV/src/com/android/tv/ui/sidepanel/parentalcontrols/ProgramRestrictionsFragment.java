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

package com.android.tv.ui.sidepanel.parentalcontrols;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.ui.sidepanel.SubMenuItem;

import java.util.ArrayList;
import java.util.List;

public class ProgramRestrictionsFragment extends SideFragment {
    private static final String TRACKER_LABEL = "Program restrictions";

    private final SideFragmentListener mSideFragmentListener = new SideFragmentListener() {
        @Override
        public void onSideFragmentViewDestroyed() {
            notifyDataSetChanged();
        }
    };

    public static String getDescription(MainActivity tvActivity) {
        return RatingsFragment.getDescription(tvActivity);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.option_program_restrictions);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();

        items.add(new SubMenuItem(getString(R.string.option_country_rating_systems),
                RatingSystemsFragment.getDescription(getMainActivity()),
                getMainActivity().getOverlayManager().getSideFragmentManager()) {
            @Override
            protected SideFragment getFragment() {
                SideFragment fragment = new RatingSystemsFragment();
                fragment.setListener(mSideFragmentListener);
                return fragment;
            }
        });
        String ratingsDescription = RatingsFragment.getDescription(getMainActivity());
        SubMenuItem ratingsItem = new SubMenuItem(getString(R.string.option_ratings),
                ratingsDescription,
                getMainActivity().getOverlayManager().getSideFragmentManager()) {
            @Override
            protected SideFragment getFragment() {
                SideFragment fragment = new RatingsFragment();
                fragment.setListener(mSideFragmentListener);
                return fragment;
            }
        };
        // When "None" is selected for rating systems, disable the Ratings option.
        if (RatingSystemsFragment.getDescription(getMainActivity()).equals(
                getString(R.string.option_no_enabled_rating_system))) {
            ratingsItem.setEnabled(false);
        }
        items.add(ratingsItem);
        return items;
    }
}