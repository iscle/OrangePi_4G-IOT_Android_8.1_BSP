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

import android.os.Bundle;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.parental.ContentRatingSystem;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.ui.sidepanel.ActionItem;
import com.android.tv.ui.sidepanel.CheckBoxItem;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.util.TvSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RatingSystemsFragment extends SideFragment {
    private static final String TRACKER_LABEL = "Rating systems";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultRatingSystemsIfNeeded((MainActivity) getActivity());
    }

    public static String getDescription(MainActivity tvActivity) {
        setDefaultRatingSystemsIfNeeded(tvActivity);

        List<ContentRatingSystem> contentRatingSystems =
                tvActivity.getContentRatingsManager().getContentRatingSystems();
        Collections.sort(contentRatingSystems, ContentRatingSystem.DISPLAY_NAME_COMPARATOR);
        StringBuilder builder = new StringBuilder();
        for (ContentRatingSystem s : contentRatingSystems) {
            if (!tvActivity.getParentalControlSettings().isContentRatingSystemEnabled(s)) {
                continue;
            }
            builder.append(s.getDisplayName());
            builder.append(", ");
        }
        return builder.length() > 0 ? builder.substring(0, builder.length() - 2)
                : tvActivity.getString(R.string.option_no_enabled_rating_system);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.option_country_rating_systems);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        ContentRatingsManager contentRatingsManager = getMainActivity().getContentRatingsManager();
        ParentalControlSettings parentalControlSettings =
                getMainActivity().getParentalControlSettings();
        List<ContentRatingSystem> contentRatingSystems =
                contentRatingsManager.getContentRatingSystems();
        Collections.sort(contentRatingSystems, ContentRatingSystem.DISPLAY_NAME_COMPARATOR);
        List<Item> items = new ArrayList<>();
        List<Item> itemsHidden = new ArrayList<>();
        List<Item> itemsHiddenMultipleCountries = new ArrayList<>();

        // Add default, custom and preselected content rating systems to the "short" list.
        for (ContentRatingSystem s : contentRatingSystems) {
            if (!s.isCustom() && s.getCountries() != null
                    && s.getCountries().contains(Locale.getDefault().getCountry())) {
                items.add(new RatingSystemItem(s));
            } else if (s.isCustom() || parentalControlSettings.isContentRatingSystemEnabled(s)) {
                items.add(new RatingSystemItem(s));
            } else {
                List<String> countries = s.getCountries();
                if (countries.size() > 1) {
                    // Convert country codes to display names.
                    for (int i = 0; i < countries.size(); ++i) {
                        countries.set(i, new Locale("", countries.get(i)).getDisplayCountry());
                    }
                    Collections.sort(countries);
                    StringBuilder builder = new StringBuilder();
                    for (String country : countries) {
                        builder.append(country);
                        builder.append(", ");
                    }
                    itemsHiddenMultipleCountries.add(
                            new RatingSystemItem(s, builder.substring(0, builder.length() - 2)));
                } else {
                    itemsHidden.add(new RatingSystemItem(s));
                }
            }
        }

        // Add the rest of the content rating systems to the "long" list.
        final List<Item> allItems = new ArrayList<>(items);
        allItems.addAll(itemsHidden);
        allItems.addAll(itemsHiddenMultipleCountries);

        // Add "See All" to the "short" list.
        items.add(new ActionItem(getString(R.string.option_see_all_rating_systems)) {
            @Override
            protected void onSelected() {
                setItems(allItems);
            }
        });
        return items;
    }

    private static void setDefaultRatingSystemsIfNeeded(MainActivity tvActivity) {
        if (TvSettings.isContentRatingSystemSet(tvActivity)) {
            return;
        }
        // Sets the default if the content rating system has never been set.
        List<ContentRatingSystem> contentRatingSystems =
                tvActivity.getContentRatingsManager().getContentRatingSystems();
        ContentRatingsManager manager = tvActivity.getContentRatingsManager();
        ParentalControlSettings settings = tvActivity.getParentalControlSettings();
        for (ContentRatingSystem s : contentRatingSystems) {
            if (!s.isCustom() && s.getCountries() != null
                    && s.getCountries().contains(Locale.getDefault().getCountry())) {
                settings.setContentRatingSystemEnabled(manager, s, true);
            }
        }
    }

    private class RatingSystemItem extends CheckBoxItem {
        private final ContentRatingSystem mContentRatingSystem;

        RatingSystemItem(ContentRatingSystem contentRatingSystem) {
            this(contentRatingSystem, null);
        }

        RatingSystemItem(ContentRatingSystem contentRatingSystem, String description) {
            super(contentRatingSystem.getDisplayName(), description, description != null);
            mContentRatingSystem = contentRatingSystem;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            setChecked(getMainActivity().getParentalControlSettings()
                    .isContentRatingSystemEnabled(mContentRatingSystem));
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            getMainActivity().getParentalControlSettings().setContentRatingSystemEnabled(
                    getMainActivity().getContentRatingsManager(), mContentRatingSystem,
                    isChecked());
        }
    }
}
