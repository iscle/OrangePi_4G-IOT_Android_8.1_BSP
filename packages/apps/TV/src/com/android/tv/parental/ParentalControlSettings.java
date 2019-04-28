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

package com.android.tv.parental;

import android.content.Context;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;

import com.android.tv.experiments.Experiments;
import com.android.tv.parental.ContentRatingSystem.Rating;
import com.android.tv.parental.ContentRatingSystem.SubRating;
import com.android.tv.util.TvSettings;
import com.android.tv.util.TvSettings.ContentRatingLevel;

import java.util.HashSet;
import java.util.Set;

public class ParentalControlSettings {
    /**
     * The rating and all of its sub-ratings are blocked.
     */
    public static final int RATING_BLOCKED = 0;

    /**
     * The rating is blocked but not all of its sub-ratings are blocked.
     */
    public static final int RATING_BLOCKED_PARTIAL = 1;

    /**
     * The rating is not blocked.
     */
    public static final int RATING_NOT_BLOCKED = 2;

    private final Context mContext;
    private final TvInputManager mTvInputManager;

    // mRatings is expected to be synchronized with mTvInputManager.getBlockedRatings().
    private Set<TvContentRating> mRatings;
    private Set<TvContentRating> mCustomRatings;

    public ParentalControlSettings(Context context) {
        mContext = context;
        mTvInputManager = (TvInputManager) mContext.getSystemService(Context.TV_INPUT_SERVICE);
    }

    public boolean isParentalControlsEnabled() {
        return mTvInputManager.isParentalControlsEnabled();
    }

    public void setParentalControlsEnabled(boolean enabled) {
        mTvInputManager.setParentalControlsEnabled(enabled);
    }

    public void setContentRatingSystemEnabled(ContentRatingsManager manager,
            ContentRatingSystem contentRatingSystem, boolean enabled) {
        if (enabled) {
            TvSettings.addContentRatingSystem(mContext, contentRatingSystem.getId());

            // Ensure newly added system has ratings for current level set
            updateRatingsForCurrentLevel(manager);
        } else {
            // Ensure no ratings are blocked for the selected rating system
            for (TvContentRating tvContentRating : mTvInputManager.getBlockedRatings()) {
                if (contentRatingSystem.ownsRating(tvContentRating)) {
                    mTvInputManager.removeBlockedRating(tvContentRating);
                }
            }

            TvSettings.removeContentRatingSystem(mContext, contentRatingSystem.getId());
        }
    }

    public boolean isContentRatingSystemEnabled(ContentRatingSystem contentRatingSystem) {
        return TvSettings.hasContentRatingSystem(mContext, contentRatingSystem.getId());
    }

    public void loadRatings() {
        mRatings = new HashSet<>(mTvInputManager.getBlockedRatings());
    }

    private void storeRatings() {
        Set<TvContentRating> removed = new HashSet<>(mTvInputManager.getBlockedRatings());
        removed.removeAll(mRatings);
        for (TvContentRating tvContentRating : removed) {
            mTvInputManager.removeBlockedRating(tvContentRating);
        }

        Set<TvContentRating> added = new HashSet<>(mRatings);
        added.removeAll(mTvInputManager.getBlockedRatings());
        for (TvContentRating tvContentRating : added) {
            mTvInputManager.addBlockedRating(tvContentRating);
        }
    }

    private void updateRatingsForCurrentLevel(ContentRatingsManager manager) {
        @ContentRatingLevel int currentLevel = getContentRatingLevel();
        if (currentLevel != TvSettings.CONTENT_RATING_LEVEL_CUSTOM) {
            mRatings = ContentRatingLevelPolicy.getRatingsForLevel(this, manager, currentLevel);
            if (currentLevel != TvSettings.CONTENT_RATING_LEVEL_NONE) {
                // UNRATED contents should be blocked unless the rating level is none or custom
                mRatings.add(TvContentRating.UNRATED);
            }
            storeRatings();
        }
    }

    public void setContentRatingLevel(ContentRatingsManager manager,
            @ContentRatingLevel int level) {
        @ContentRatingLevel int currentLevel = getContentRatingLevel();
        if (level == currentLevel) {
            return;
        }
        if (currentLevel == TvSettings.CONTENT_RATING_LEVEL_CUSTOM) {
            mCustomRatings = mRatings;
        }
        TvSettings.setContentRatingLevel(mContext, level);
        if (level == TvSettings.CONTENT_RATING_LEVEL_CUSTOM) {
            if (mCustomRatings != null) {
                mRatings = new HashSet<>(mCustomRatings);
            }
        } else {
            mRatings = ContentRatingLevelPolicy.getRatingsForLevel(this, manager, level);
            if (level != TvSettings.CONTENT_RATING_LEVEL_NONE
                    && Boolean.TRUE.equals(Experiments.ENABLE_UNRATED_CONTENT_SETTINGS.get())) {
                // UNRATED contents should be blocked unless the rating level is none or custom
                mRatings.add(TvContentRating.UNRATED);
            }
        }
        storeRatings();
    }

    @ContentRatingLevel
    public int getContentRatingLevel() {
        return TvSettings.getContentRatingLevel(mContext);
    }

    /** Sets the blocked status of a unrated contents. */
    public boolean setUnratedBlocked(boolean blocked) {
        boolean changed;
        if (blocked) {
            changed = mRatings.add(TvContentRating.UNRATED);
            mTvInputManager.addBlockedRating(TvContentRating.UNRATED);
        } else {
            changed = mRatings.remove(TvContentRating.UNRATED);
            mTvInputManager.removeBlockedRating(TvContentRating.UNRATED);
        }
        if (changed) {
            // change to custom level if the blocked status is changed
            changeToCustomLevel();
        }
        return changed;
    }

    /**
     * Sets the blocked status of a given content rating.
     * <p>
     * Note that a call to this method automatically changes the current rating level to
     * {@code TvSettings.CONTENT_RATING_LEVEL_CUSTOM} if needed.
     * </p>
     *
     * @param contentRatingSystem The content rating system where the given rating belongs.
     * @param rating The content rating to set.
     * @return {@code true} if changed, {@code false} otherwise.
     * @see #setSubRatingBlocked
     */
    public boolean setRatingBlocked(ContentRatingSystem contentRatingSystem, Rating rating,
            boolean blocked) {
        return setRatingBlockedInternal(contentRatingSystem, rating, null, blocked);
    }

    /**
     * Checks whether any of given ratings is blocked.
     *
     * @param ratings The array of ratings to check
     * @return {@code true} if a rating is blocked, {@code false} otherwise.
     */
    public boolean isRatingBlocked(TvContentRating[] ratings) {
        return getBlockedRating(ratings) != null;
    }

    /**
     * Checks whether any of given ratings is blocked and returns the first blocked rating.
     *
     * @param ratings The array of ratings to check
     * @return The {@link TvContentRating} that is blocked.
     */
    public TvContentRating getBlockedRating(TvContentRating[] ratings) {
        if (ratings == null || ratings.length <= 0) {
            return mTvInputManager.isRatingBlocked(TvContentRating.UNRATED)
                    ? TvContentRating.UNRATED
                    : null;
        }
        for (TvContentRating rating : ratings) {
            if (mTvInputManager.isRatingBlocked(rating)) {
                return rating;
            }
        }
        return null;
    }

    /**
     * Checks whether a given rating is blocked by the user or not.
     *
     * @param contentRatingSystem The content rating system where the given rating belongs.
     * @param rating The content rating to check.
     * @return {@code true} if blocked, {@code false} otherwise.
     */
    public boolean isRatingBlocked(ContentRatingSystem contentRatingSystem, Rating rating) {
        return mRatings.contains(toTvContentRating(contentRatingSystem, rating));
    }

    /**
     * Sets the blocked status of a given content sub-rating.
     * <p>
     * Note that a call to this method automatically changes the current rating level to
     * {@code TvSettings.CONTENT_RATING_LEVEL_CUSTOM} if needed.
     * </p>
     *
     * @param contentRatingSystem The content rating system where the given rating belongs.
     * @param rating The content rating associated with the given sub-rating.
     * @param subRating The content sub-rating to set.
     * @return {@code true} if changed, {@code false} otherwise.
     * @see #setRatingBlocked
     */
    public boolean setSubRatingBlocked(ContentRatingSystem contentRatingSystem, Rating rating,
            SubRating subRating, boolean blocked) {
        return setRatingBlockedInternal(contentRatingSystem, rating, subRating, blocked);
    }

    /**
     * Checks whether a given content sub-rating is blocked by the user or not.
     *
     * @param contentRatingSystem The content rating system where the given rating belongs.
     * @param rating The content rating associated with the given sub-rating.
     * @param subRating The content sub-rating to check.
     * @return {@code true} if blocked, {@code false} otherwise.
     */
    public boolean isSubRatingEnabled(ContentRatingSystem contentRatingSystem, Rating rating,
            SubRating subRating) {
        return mRatings.contains(toTvContentRating(contentRatingSystem, rating, subRating));
    }

    private boolean setRatingBlockedInternal(ContentRatingSystem contentRatingSystem, Rating rating,
            SubRating subRating, boolean blocked) {
        TvContentRating tvContentRating = (subRating == null)
                ? toTvContentRating(contentRatingSystem, rating)
                : toTvContentRating(contentRatingSystem, rating, subRating);
        boolean changed;
        if (blocked) {
            changed = mRatings.add(tvContentRating);
            mTvInputManager.addBlockedRating(tvContentRating);
        } else {
            changed = mRatings.remove(tvContentRating);
            mTvInputManager.removeBlockedRating(tvContentRating);
        }
        if (changed) {
            changeToCustomLevel();
        }
        return changed;
    }

    private void changeToCustomLevel() {
        if (getContentRatingLevel() != TvSettings.CONTENT_RATING_LEVEL_CUSTOM) {
            TvSettings.setContentRatingLevel(mContext, TvSettings.CONTENT_RATING_LEVEL_CUSTOM);
        }
    }

    /**
     * Returns the blocked status of a given rating. The status can be one of the followings:
     * {@link #RATING_BLOCKED}, {@link #RATING_BLOCKED_PARTIAL} and {@link #RATING_NOT_BLOCKED}
     */
    public int getBlockedStatus(ContentRatingSystem contentRatingSystem, Rating rating) {
        if (isRatingBlocked(contentRatingSystem, rating)) {
            return RATING_BLOCKED;
        }
        for (SubRating subRating : rating.getSubRatings()) {
            if (isSubRatingEnabled(contentRatingSystem, rating, subRating)) {
                return RATING_BLOCKED_PARTIAL;
            }
        }
        return RATING_NOT_BLOCKED;
    }

    private TvContentRating toTvContentRating(ContentRatingSystem contentRatingSystem,
            Rating rating) {
        return TvContentRating.createRating(contentRatingSystem.getDomain(),
                contentRatingSystem.getName(), rating.getName());
    }

    private TvContentRating toTvContentRating(ContentRatingSystem contentRatingSystem,
            Rating rating, SubRating subRating) {
        return TvContentRating.createRating(contentRatingSystem.getDomain(),
                contentRatingSystem.getName(), rating.getName(), subRating.getName());
    }
}
