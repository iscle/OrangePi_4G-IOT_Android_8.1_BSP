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

package com.android.tv.testing;

import android.media.tv.TvContentRating;

/**
 * Constants for the content rating strings.
 */
public final class TvContentRatingConstants {
    /**
     * A content rating object.
     *
     * <p>Domain: com.android.tv
     * <p>Rating system: US_TV
     * <p>Rating: US_TV_Y7
     * <p>Sub ratings: US_TV_FV
     */
    public static final TvContentRating CONTENT_RATING_US_TV_Y7_US_TV_FV =
            TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_Y7", "US_TV_FV");

    public static final String STRING_US_TV_Y7_US_TV_FV = "com.android.tv/US_TV/US_TV_Y7/US_TV_FV";

    /**
     * A content rating object.
     *
     * <p>Domain: com.android.tv
     * <p>Rating system: US_TV
     * <p>Rating: US_TV_MA
     */
    public static final TvContentRating CONTENT_RATING_US_TV_MA =
            TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_MA");

    public static final String STRING_US_TV_MA = "com.android.tv/US_TV/US_TV_MA";

    /**
     * A content rating object.
     *
     * <p>Domain: com.android.tv
     * <p>Rating system: US_TV
     * <p>Rating: US_TV_PG
     * <p>Sub ratings: US_TV_L, US_TV_S
     */
    public static final TvContentRating CONTENT_RATING_US_TV_PG_US_TV_L_US_TV_S =
            TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_PG", "US_TV_L",
                    "US_TV_S");
}
