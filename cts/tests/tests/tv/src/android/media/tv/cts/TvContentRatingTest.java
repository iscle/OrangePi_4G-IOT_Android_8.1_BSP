/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.media.tv.TvContentRating;

import java.util.List;

import junit.framework.TestCase;

/**
 * Test for {@link android.media.tv.TvContentRating}.
 */
public class TvContentRatingTest extends TestCase {

    private static final String DOMAIN = "android.media.tv";
    private static final String RATING_SYSTEM = "US_TVPG";
    private static final String MAIN_RATING_1 = "US_TVPG_TV_MA";
    private static final String MAIN_RATING_2 = "US_TVPG_TV_14";
    private static final String SUB_RATING_1 = "US_TVPG_TV_S";
    private static final String SUB_RATING_2 = "US_TVPG_TV_V";
    private static final String SUB_RATING_3 = "US_TVPG_TV_L";

    public void testCreateRating() throws Exception {
        TvContentRating rating = TvContentRating.createRating(DOMAIN, RATING_SYSTEM, MAIN_RATING_1,
                SUB_RATING_1, SUB_RATING_2);
        assertEquals(DOMAIN, rating.getDomain());
        assertEquals(RATING_SYSTEM, rating.getRatingSystem());
        assertEquals(MAIN_RATING_1, rating.getMainRating());
        List<String> subRatings = rating.getSubRatings();
        assertEquals(2, subRatings.size());
        assertTrue("Sub-ratings does not contain " + SUB_RATING_1,
                subRatings.contains(SUB_RATING_1));
        assertTrue("Sub-ratings does not contain " + SUB_RATING_2,
                subRatings.contains(SUB_RATING_2));
    }

    public void testFlattenAndUnflatten() throws Exception {
        String flattened = TvContentRating.createRating(DOMAIN, RATING_SYSTEM, MAIN_RATING_1,
                SUB_RATING_1, SUB_RATING_2).flattenToString();
        TvContentRating rating = TvContentRating.unflattenFromString(flattened);

        assertEquals(DOMAIN, rating.getDomain());
        assertEquals(RATING_SYSTEM, rating.getRatingSystem());
        assertEquals(MAIN_RATING_1, rating.getMainRating());
        List<String> subRatings = rating.getSubRatings();
        assertEquals(2, subRatings.size());
        assertTrue("Sub-ratings does not contain " + SUB_RATING_1,
                subRatings.contains(SUB_RATING_1));
        assertTrue("Sub-ratings does not contain " + SUB_RATING_2,
                subRatings.contains(SUB_RATING_2));
    }

    public void testContains() throws Exception {
        TvContentRating rating = TvContentRating.createRating(DOMAIN, RATING_SYSTEM, MAIN_RATING_1,
                SUB_RATING_1, SUB_RATING_2);

        assertTrue(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_1, SUB_RATING_1, SUB_RATING_2)));
        assertTrue(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_1, SUB_RATING_1)));
        assertFalse(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_1, SUB_RATING_3)));
        assertFalse(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_2, SUB_RATING_1, SUB_RATING_2)));
        assertFalse(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_2, SUB_RATING_3)));
        assertFalse(rating.contains(TvContentRating.createRating(DOMAIN, RATING_SYSTEM,
                MAIN_RATING_2)));
    }
}
