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

import android.content.Context;
import android.media.tv.TvContract.Programs.Genres;

import com.android.tv.R;

public class GenreItems {
    /**
     * Genre ID indicating all channels.
     */
    public static final int ID_ALL_CHANNELS = 0;

    private static final String[] CANONICAL_GENRES = {
        null, // All channels
        Genres.FAMILY_KIDS,
        Genres.SPORTS,
        Genres.SHOPPING,
        Genres.MOVIES,
        Genres.COMEDY,
        Genres.TRAVEL,
        Genres.DRAMA,
        Genres.EDUCATION,
        Genres.ANIMAL_WILDLIFE,
        Genres.NEWS,
        Genres.GAMING,
        Genres.ARTS,
        Genres.ENTERTAINMENT,
        Genres.LIFE_STYLE,
        Genres.MUSIC,
        Genres.PREMIER,
        Genres.TECH_SCIENCE
    };

    private GenreItems() { }

    /**
     * Returns array of all genre labels.
     */
    public static String[] getLabels(Context context) {
        String[] items = context.getResources().getStringArray(R.array.genre_labels);
        if (items.length != CANONICAL_GENRES.length) {
            throw new IllegalArgumentException("Genre data mismatch");
        }
        return items;
    }

    /**
     * Returns the number of genres including all channels.
     */
    public static int getGenreCount() {
        return CANONICAL_GENRES.length;
    }

    /**
     * Returns the canonical genre for the given id.
     * If the id is invalid, {@code null} will be returned instead.
     */
    public static String getCanonicalGenre(int id) {
        if (id < 0 || id >= CANONICAL_GENRES.length) {
            return null;
        }
        return CANONICAL_GENRES[id];
    }

    /**
     * Returns id for the given canonical genre.
     * If the genre is invalid, {@link #ID_ALL_CHANNELS} will be returned instead.
     */
    public static int getId(String canonicalGenre) {
        if (canonicalGenre == null) {
            return ID_ALL_CHANNELS;
        }
        for (int i = 1; i < CANONICAL_GENRES.length; ++i) {
            if (CANONICAL_GENRES[i].equals(canonicalGenre)) {
                return i;
            }
        }
        return ID_ALL_CHANNELS;
    }
}
