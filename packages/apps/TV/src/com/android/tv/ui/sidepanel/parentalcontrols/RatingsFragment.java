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

import android.graphics.drawable.Drawable;
import android.media.tv.TvContentRating;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.dialog.WebDialogFragment;
import com.android.tv.experiments.Experiments;
import com.android.tv.license.LicenseUtils;
import com.android.tv.parental.ContentRatingSystem;
import com.android.tv.parental.ContentRatingSystem.Rating;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.ui.sidepanel.CheckBoxItem;
import com.android.tv.ui.sidepanel.DividerItem;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.RadioButtonItem;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.util.TvSettings;
import com.android.tv.util.TvSettings.ContentRatingLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RatingsFragment extends SideFragment {
    private static final SparseIntArray sLevelResourceIdMap;
    private static final SparseIntArray sDescriptionResourceIdMap;
    private static final String TRACKER_LABEL = "Ratings";
    private int mItemsSize;

    static {
        sLevelResourceIdMap = new SparseIntArray(5);
        sLevelResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_NONE,
                R.string.option_rating_none);
        sLevelResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_HIGH,
                R.string.option_rating_high);
        sLevelResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_MEDIUM,
                R.string.option_rating_medium);
        sLevelResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_LOW,
                R.string.option_rating_low);
        sLevelResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_CUSTOM,
                R.string.option_rating_custom);

        sDescriptionResourceIdMap = new SparseIntArray(sLevelResourceIdMap.size());
        sDescriptionResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_HIGH,
                R.string.option_rating_high_description);
        sDescriptionResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_MEDIUM,
                R.string.option_rating_medium_description);
        sDescriptionResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_LOW,
                R.string.option_rating_low_description);
        sDescriptionResourceIdMap.put(TvSettings.CONTENT_RATING_LEVEL_CUSTOM,
                R.string.option_rating_custom_description);
    }

    private final List<RatingLevelItem> mRatingLevelItems = new ArrayList<>();
    // A map from the rating system ID string to RatingItem objects.
    private final Map<String, List<RatingItem>> mContentRatingSystemItemMap = new ArrayMap<>();
    private CheckBoxItem mBlockUnratedItem;
    private ParentalControlSettings mParentalControlSettings;

    public static String getDescription(MainActivity tvActivity) {
        @ContentRatingLevel int currentLevel =
                tvActivity.getParentalControlSettings().getContentRatingLevel();
        if (sLevelResourceIdMap.indexOfKey(currentLevel) >= 0) {
            return tvActivity.getString(sLevelResourceIdMap.get(currentLevel));
        }
        return null;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.option_ratings);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();

        if (mBlockUnratedItem != null
                && Boolean.TRUE.equals(Experiments.ENABLE_UNRATED_CONTENT_SETTINGS.get())) {
            items.add(mBlockUnratedItem);
            items.add(new DividerItem());
        }

        mRatingLevelItems.clear();
        for (int i = 0; i < sLevelResourceIdMap.size(); ++i) {
            mRatingLevelItems.add(new RatingLevelItem(sLevelResourceIdMap.keyAt(i)));
        }
        updateRatingLevels();
        items.addAll(mRatingLevelItems);

        mContentRatingSystemItemMap.clear();

        List<ContentRatingSystem> contentRatingSystems =
                getMainActivity().getContentRatingsManager().getContentRatingSystems();
        Collections.sort(contentRatingSystems, ContentRatingSystem.DISPLAY_NAME_COMPARATOR);

        for (ContentRatingSystem s : contentRatingSystems) {
            if (mParentalControlSettings.isContentRatingSystemEnabled(s)) {
                List<RatingItem> ratingItems = new ArrayList<>();
                boolean hasSubRating = false;
                items.add(new DividerItem(s.getDisplayName()));
                for (Rating rating : s.getRatings()) {
                    RatingItem item = rating.getSubRatings().isEmpty() ?
                            new RatingItem(s, rating) :
                            new RatingWithSubItem(s, rating);
                    items.add(item);
                    if (rating.getSubRatings().isEmpty()) {
                        ratingItems.add(item);
                    } else {
                        hasSubRating = true;
                    }
                }
                // Only include rating systems that don't contain any sub ratings in the map for
                // simplicity.
                if (!hasSubRating) {
                    mContentRatingSystemItemMap.put(s.getId(), ratingItems);
                }
            }
        }
        if (LicenseUtils.hasRatingAttribution(getMainActivity().getAssets())) {
            // Display the attribution if our content rating system is selected.
            items.add(new DividerItem());
            items.add(new AttributionItem(getMainActivity()));
        }
        mItemsSize = items.size();
        return items;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mParentalControlSettings = getMainActivity().getParentalControlSettings();
        mParentalControlSettings.loadRatings();
        if (Boolean.TRUE.equals(Experiments.ENABLE_UNRATED_CONTENT_SETTINGS.get())) {
            mBlockUnratedItem =
                    new CheckBoxItem(
                            getResources().getString(R.string.option_block_unrated_programs)) {

                        @Override
                        protected void onUpdate() {
                            super.onUpdate();
                            setChecked(
                                    mParentalControlSettings.isRatingBlocked(
                                            new TvContentRating[] {TvContentRating.UNRATED}));
                        }

                        @Override
                        protected void onSelected() {
                            super.onSelected();
                            if (mParentalControlSettings.setUnratedBlocked(isChecked())) {
                                updateRatingLevels();
                            }
                        }
                    };
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Although we set the attribution item at the end of the item list non-focusable, we do get
        // its position when the fragment is resumed. This ensures that we do not select the
        // non-focusable item at the end of the list. See b/17387103.
        if (getSelectedPosition() >= mItemsSize) {
            setSelectedPosition(mItemsSize - 1);
        }
    }

    private void updateRatingLevels() {
        @ContentRatingLevel int ratingLevel = mParentalControlSettings.getContentRatingLevel();
        for (RatingLevelItem ratingLevelItem : mRatingLevelItems) {
            ratingLevelItem.setChecked(ratingLevel == ratingLevelItem.mRatingLevel);
        }
    }

    private void updateDependentRatingItems(ContentRatingSystem.Order order,
            int selectedRatingOrderIndex, String contentRatingSystemId, boolean isChecked) {
        List<RatingItem> ratingItems = mContentRatingSystemItemMap.get(contentRatingSystemId);
        if (ratingItems != null) {
            for (RatingItem item : ratingItems) {
                int ratingOrderIndex = item.getRatingOrderIndex(order);
                if (ratingOrderIndex != -1
                        && ((ratingOrderIndex > selectedRatingOrderIndex && isChecked)
                        || (ratingOrderIndex < selectedRatingOrderIndex && !isChecked))) {
                    item.setRatingBlocked(isChecked);
                }
            }
        }
    }

    private class RatingLevelItem extends RadioButtonItem {
        private final int mRatingLevel;

        private RatingLevelItem(int ratingLevel) {
            super(getString(sLevelResourceIdMap.get(ratingLevel)),
                    (sDescriptionResourceIdMap.indexOfKey(ratingLevel) >= 0) ?
                            getString(sDescriptionResourceIdMap.get(ratingLevel)) : null);
            mRatingLevel = ratingLevel;
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            mParentalControlSettings.setContentRatingLevel(
                    getMainActivity().getContentRatingsManager(), mRatingLevel);
            if (mBlockUnratedItem != null
                    && Boolean.TRUE.equals(Experiments.ENABLE_UNRATED_CONTENT_SETTINGS.get())) {
                // set checked if UNRATED is blocked, and set unchecked otherwise.
                mBlockUnratedItem.setChecked(
                        mParentalControlSettings.isRatingBlocked(
                                new TvContentRating[] {TvContentRating.UNRATED}));
            }
            notifyItemsChanged(mRatingLevelItems.size());
        }
    }

    private class RatingItem extends CheckBoxItem {
        protected final ContentRatingSystem mContentRatingSystem;
        protected final Rating mRating;
        private final Drawable mIcon;
        private CompoundButton mCompoundButton;
        private final List<ContentRatingSystem.Order> mOrders = new ArrayList<>();
        private final List<Integer> mOrderIndexes = new ArrayList<>();

        private RatingItem(ContentRatingSystem contentRatingSystem, Rating rating) {
            super(rating.getTitle(), rating.getDescription());
            mContentRatingSystem = contentRatingSystem;
            mRating = rating;
            mIcon = rating.getIcon();
            for (ContentRatingSystem.Order order : mContentRatingSystem.getOrders()) {
                int orderIndex = order.getRatingIndex(mRating);
                if (orderIndex != -1) {
                    mOrders.add(order);
                    mOrderIndexes.add(orderIndex);
                }
            }
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);

            mCompoundButton = (CompoundButton) view.findViewById(getCompoundButtonId());
            mCompoundButton.setVisibility(View.VISIBLE);

            ImageView imageView = (ImageView) view.findViewById(R.id.icon);
            if (mIcon != null) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(mIcon);
            } else {
                imageView.setVisibility(View.GONE);
            }
        }

        @Override
        protected void onUnbind() {
            super.onUnbind();
            mCompoundButton = null;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            mCompoundButton.setButtonDrawable(getButtonDrawable());
            setChecked(mParentalControlSettings.isRatingBlocked(mContentRatingSystem, mRating));
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            if (mParentalControlSettings.setRatingBlocked(
                    mContentRatingSystem, mRating, isChecked())) {
                updateRatingLevels();
            }
            // Automatically check/uncheck dependent ratings.
            for (int i = 0; i < mOrders.size(); i++) {
                updateDependentRatingItems(mOrders.get(i), mOrderIndexes.get(i),
                        mContentRatingSystem.getId(), isChecked());
            }
        }

        @Override
        protected int getResourceId() {
            return R.layout.option_item_rating;
        }

        protected int getButtonDrawable() {
            return R.drawable.btn_lock_material_anim;
        }

        private int getRatingOrderIndex(ContentRatingSystem.Order order) {
            int orderIndex = mOrders.indexOf(order);
            return orderIndex == -1 ? -1 : mOrderIndexes.get(orderIndex);
        }

        private void setRatingBlocked(boolean isChecked) {
            if (isChecked() == isChecked) {
                return;
            }
            mParentalControlSettings.setRatingBlocked(mContentRatingSystem, mRating, isChecked);
            notifyUpdated();
        }
    }

    private class RatingWithSubItem extends RatingItem {
        private RatingWithSubItem(ContentRatingSystem contentRatingSystem, Rating rating) {
            super(contentRatingSystem, rating);
        }

        @Override
        protected void onSelected() {
            getMainActivity().getOverlayManager().getSideFragmentManager()
                    .show(SubRatingsFragment.create(mContentRatingSystem, mRating.getName()));
        }

        @Override
        protected int getButtonDrawable() {
            int blockedStatus = mParentalControlSettings.getBlockedStatus(
                    mContentRatingSystem, mRating);
            if (blockedStatus == ParentalControlSettings.RATING_BLOCKED) {
                return R.drawable.btn_lock_material;
            } else if (blockedStatus == ParentalControlSettings.RATING_BLOCKED_PARTIAL) {
                return R.drawable.btn_partial_lock_material;
            }
            return R.drawable.btn_unlock_material;
        }
    }

    /**
     * Opens a dialog showing the sources of the rating descriptions.
     */
    public static class AttributionItem extends Item {
        public final static String DIALOG_TAG = AttributionItem.class.getSimpleName();
        public static final String TRACKER_LABEL = "Sources for content rating systems";
        private final MainActivity mMainActivity;

        public AttributionItem(MainActivity mainActivity) {
            mMainActivity = mainActivity;
        }

        @Override
        protected int getResourceId() {
            return R.layout.option_item_attribution;
        }

        @Override
        protected void onSelected() {
            WebDialogFragment dialog = WebDialogFragment.newInstance(
                    LicenseUtils.RATING_SOURCE_FILE,
                    mMainActivity.getString(R.string.option_attribution), TRACKER_LABEL);
            mMainActivity.getOverlayManager().showDialogFragment(DIALOG_TAG, dialog, false);
        }
    }
}
