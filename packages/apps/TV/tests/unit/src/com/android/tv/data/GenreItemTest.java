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

package com.android.tv.data;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.tv.TvContract.Programs.Genres;
import android.os.Build;
import android.support.test.filters.SmallTest;

import org.junit.Test;

/**
 * Tests for {@link Channel}.
 */
@SmallTest
public class GenreItemTest {
    private static final String INVALID_GENRE = "INVALID GENRE";

    @Test
    public void testGetLabels() {
        // Checks if no exception is thrown.
        GenreItems.getLabels(getTargetContext());
    }

    @Test
    public void testGetCanonicalGenre() {
        int count = GenreItems.getGenreCount();
        assertNull(GenreItems.getCanonicalGenre(GenreItems.ID_ALL_CHANNELS));
        for (int i = 1; i < count; ++i) {
            assertNotNull(GenreItems.getCanonicalGenre(i));
        }
    }

    @Test
    public void testGetId_base() {
        int count = GenreItems.getGenreCount();
        assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(null));
        assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(INVALID_GENRE));
        assertInRange(GenreItems.getId(Genres.FAMILY_KIDS), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.SPORTS), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.SHOPPING), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.MOVIES), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.COMEDY), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.TRAVEL), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.DRAMA), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.EDUCATION), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.ANIMAL_WILDLIFE), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.NEWS), 1, count - 1);
        assertInRange(GenreItems.getId(Genres.GAMING), 1, count - 1);
    }

    @Test
    public void testGetId_lmp_mr1() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.ARTS));
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.ENTERTAINMENT));
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.LIFE_STYLE));
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.MUSIC));
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.PREMIER));
            assertEquals(GenreItems.ID_ALL_CHANNELS, GenreItems.getId(Genres.TECH_SCIENCE));
        } else {
            int count = GenreItems.getGenreCount();
            assertInRange(GenreItems.getId(Genres.ARTS), 1, count - 1);
            assertInRange(GenreItems.getId(Genres.ENTERTAINMENT), 1, count - 1);
            assertInRange(GenreItems.getId(Genres.LIFE_STYLE), 1, count - 1);
            assertInRange(GenreItems.getId(Genres.MUSIC), 1, count - 1);
            assertInRange(GenreItems.getId(Genres.PREMIER), 1, count - 1);
            assertInRange(GenreItems.getId(Genres.TECH_SCIENCE), 1, count - 1);
        }
    }

    private void assertInRange(int value, int lower, int upper) {
        assertTrue(value >= lower && value <= upper);
    }
}
