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
 * limitations under the License
 */

package com.android.tv.common;

import android.media.tv.TvContentRating;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * TvContentRating cache.
 */
public final class TvContentRatingCache implements MemoryManageable {
    private final static String TAG = "TvContentRatings";

    private final static TvContentRatingCache INSTANCE = new TvContentRatingCache();

    public static TvContentRatingCache getInstance() {
        return INSTANCE;
    }

    // @GuardedBy("TvContentRatingCache.this")
    private final Map<String, TvContentRating[]> mRatingsMultiMap = new ArrayMap<>();

    /**
     * Returns an array TvContentRatings from a string of comma separated set of rating strings
     * creating each from {@link TvContentRating#unflattenFromString(String)} if needed.
     * Returns {@code null} if the string is empty or contains no valid ratings.
     */
    @Nullable
    public synchronized TvContentRating[] getRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        TvContentRating[] tvContentRatings;
        if (mRatingsMultiMap.containsKey(commaSeparatedRatings)) {
            tvContentRatings = mRatingsMultiMap.get(commaSeparatedRatings);
        } else {
            String normalizedRatings = TextUtils
                    .join(",", getSortedSetFromCsv(commaSeparatedRatings));
            if (mRatingsMultiMap.containsKey(normalizedRatings)) {
                tvContentRatings = mRatingsMultiMap.get(normalizedRatings);
            } else {
                tvContentRatings = stringToContentRatings(commaSeparatedRatings);
                mRatingsMultiMap.put(normalizedRatings, tvContentRatings);
            }
            if (!normalizedRatings.equals(commaSeparatedRatings)) {
                // Add an entry so the non normalized entry points to the same result;
                mRatingsMultiMap.put(commaSeparatedRatings, tvContentRatings);
            }
        }
        return tvContentRatings;
    }

    /**
     * Returns a sorted array of TvContentRatings from a comma separated string of ratings.
     */
    @VisibleForTesting
    static TvContentRating[] stringToContentRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        Set<String> ratingStrings = getSortedSetFromCsv(commaSeparatedRatings);
        List<TvContentRating> contentRatings = new ArrayList<>();
        for (String rating : ratingStrings) {
            try {
                contentRatings.add(TvContentRating.unflattenFromString(rating));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Can't parse the content rating: '" + rating + "'", e);
            }
        }
        return contentRatings.size() == 0 ?
                null : contentRatings.toArray(new TvContentRating[contentRatings.size()]);
    }

    private static Set<String> getSortedSetFromCsv(String commaSeparatedRatings) {
        String[] ratingStrings = commaSeparatedRatings.split("\\s*,\\s*");
        return toSortedSet(ratingStrings);
    }

    private static Set<String> toSortedSet(String[] ratingStrings) {
        if (ratingStrings.length == 0) {
            return Collections.EMPTY_SET;
        } else if (ratingStrings.length == 1) {
            return Collections.singleton(ratingStrings[0]);
        } else {
            // Using a TreeSet here is not very efficient, however it is good enough because:
            //  - the results are cached
            //  - in testing with multiple TISs, less than 50 entries are created
            SortedSet<String> set = new TreeSet<>();
            Collections.addAll(set, ratingStrings);
            return set;
        }
    }

    /**
     * Returns a string of each flattened content rating, sorted and concatenated together
     * with a comma.
     */
    public static String contentRatingsToString(TvContentRating[] contentRatings) {
        if (contentRatings == null || contentRatings.length == 0) {
            return null;
        }
        String[] ratingStrings = new String[contentRatings.length];
        for (int i = 0; i < contentRatings.length; i++) {
            ratingStrings[i] = contentRatings[i].flattenToString();
        }
        if (ratingStrings.length == 1) {
            return ratingStrings[0];
        } else {
            return TextUtils.join(",", toSortedSet(ratingStrings));
        }
    }

    @Override
    public synchronized void performTrimMemory(int level) {
        mRatingsMultiMap.clear();
    }

    private TvContentRatingCache() {
    }
}
