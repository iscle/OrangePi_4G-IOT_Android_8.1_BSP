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

import android.media.tv.TvContentRating;

import com.android.tv.parental.ContentRatingSystem.Rating;
import com.android.tv.parental.ContentRatingSystem.SubRating;
import com.android.tv.util.TvSettings;
import com.android.tv.util.TvSettings.ContentRatingLevel;

import java.util.HashSet;
import java.util.Set;

public class ContentRatingLevelPolicy {
    private static final int AGE_THRESHOLD_FOR_LEVEL_HIGH = 6;
    private static final int AGE_THRESHOLD_FOR_LEVEL_MEDIUM = 12;
    private static final int AGE_THRESHOLD_FOR_LEVEL_LOW = -1; // Highest age for each rating system

    private ContentRatingLevelPolicy() { }

    public static Set<TvContentRating> getRatingsForLevel(
            ParentalControlSettings settings, ContentRatingsManager manager, 
            @ContentRatingLevel int level) {
        if (level == TvSettings.CONTENT_RATING_LEVEL_NONE) {
            return new HashSet<>();
        } else if (level == TvSettings.CONTENT_RATING_LEVEL_HIGH) {
            return getRatingsForAge(settings, manager, AGE_THRESHOLD_FOR_LEVEL_HIGH);
        } else if (level == TvSettings.CONTENT_RATING_LEVEL_MEDIUM) {
            return getRatingsForAge(settings, manager, AGE_THRESHOLD_FOR_LEVEL_MEDIUM);
        } else if (level == TvSettings.CONTENT_RATING_LEVEL_LOW) {
            return getRatingsForAge(settings, manager, AGE_THRESHOLD_FOR_LEVEL_LOW);
        }
        throw new IllegalArgumentException("Unexpected rating level");
    }

    private static Set<TvContentRating> getRatingsForAge(
            ParentalControlSettings settings, ContentRatingsManager manager, int age) {
        Set<TvContentRating> ratings = new HashSet<>();

        for (ContentRatingSystem contentRatingSystem : manager.getContentRatingSystems()) {
            if (!settings.isContentRatingSystemEnabled(contentRatingSystem)) {
                continue;
            }
            int ageLimit = age;
            if (ageLimit == AGE_THRESHOLD_FOR_LEVEL_LOW) {
                ageLimit = getMaxAge(contentRatingSystem);
            }
            for (Rating rating : contentRatingSystem.getRatings()) {
                if (rating.getAgeHint() < ageLimit) {
                    continue;
                }
                TvContentRating tvContentRating = TvContentRating.createRating(
                        contentRatingSystem.getDomain(), contentRatingSystem.getName(),
                        rating.getName());
                ratings.add(tvContentRating);
                for (SubRating subRating : rating.getSubRatings()) {
                    tvContentRating = TvContentRating.createRating(
                            contentRatingSystem.getDomain(), contentRatingSystem.getName(),
                            rating.getName(), subRating.getName());
                    ratings.add(tvContentRating);
                }
            }
        }

        return ratings;
    }

    private static int getMaxAge(ContentRatingSystem contentRatingSystem) {
        int maxAge = 0;
        for (Rating rating : contentRatingSystem.getRatings()) {
            if (maxAge < rating.getAgeHint()) {
                maxAge = rating.getAgeHint();
            }
        }
        return maxAge;
    }
}
