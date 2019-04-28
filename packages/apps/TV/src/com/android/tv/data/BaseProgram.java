/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.media.tv.TvContentRating;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.R;

import java.util.Comparator;

/**
 * Base class for {@link com.android.tv.data.Program} and
 * {@link com.android.tv.dvr.data.RecordedProgram}.
 */
public abstract class BaseProgram {
    /**
     * Comparator used to compare {@link BaseProgram} according to its season and episodes number.
     * If a program's season or episode number is null, it will be consider "smaller" than programs
     * with season or episode numbers.
     */
    public static final Comparator<BaseProgram> EPISODE_COMPARATOR =
            new EpisodeComparator(false);

    /**
     * Comparator used to compare {@link BaseProgram} according to its season and episodes number
     * with season numbers in a reversed order. If a program's season or episode number is null, it
     * will be consider "smaller" than programs with season or episode numbers.
     */
    public static final Comparator<BaseProgram> SEASON_REVERSED_EPISODE_COMPARATOR =
            new EpisodeComparator(true);

    private static class EpisodeComparator implements Comparator<BaseProgram> {
        private final boolean mReversedSeason;

        EpisodeComparator(boolean reversedSeason) {
            mReversedSeason = reversedSeason;
        }

        @Override
        public int compare(BaseProgram lhs, BaseProgram rhs) {
            if (lhs == rhs) {
                return 0;
            }
            int seasonNumberCompare =
                    numberCompare(lhs.getSeasonNumber(), rhs.getSeasonNumber());
            if (seasonNumberCompare != 0) {
                return mReversedSeason ? -seasonNumberCompare : seasonNumberCompare;
            } else {
                return numberCompare(lhs.getEpisodeNumber(), rhs.getEpisodeNumber());
            }
        }
    }

    /**
     * Compares two strings represent season numbers or episode numbers of programs.
     */
    public static int numberCompare(String s1, String s2) {
        if (s1 == s2) {
            return 0;
        } else if (s1 == null) {
            return -1;
        } else if (s2 == null) {
            return 1;
        } else if (s1.equals(s2)) {
            return 0;
        }
        try {
            return Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2));
        } catch (NumberFormatException e) {
            return s1.compareTo(s2);
        }
    }

    /**
     * Returns ID of the program.
     */
    abstract public long getId();

    /**
     * Returns the title of the program.
     */
    abstract public String getTitle();

    /**
     * Returns the episode title.
     */
    abstract public String getEpisodeTitle();

    /**
     * Returns the displayed title of the program episode.
     */
    public String getEpisodeDisplayTitle(Context context) {
        if (!TextUtils.isEmpty(getEpisodeNumber())) {
            String episodeTitle = getEpisodeTitle() == null ? "" : getEpisodeTitle();
            if (TextUtils.equals(getSeasonNumber(), "0")) {
                // Do not show "S0: ".
                return String.format(context.getResources().getString(
                        R.string.display_episode_title_format_no_season_number),
                        getEpisodeNumber(), episodeTitle);
            } else {
                return String.format(context.getResources().getString(
                        R.string.display_episode_title_format),
                        getSeasonNumber(), getEpisodeNumber(), episodeTitle);
            }
        }
        return getEpisodeTitle();
    }

    /**
     * Returns the description of the program.
     */
    abstract public String getDescription();

    /**
     * Returns the long description of the program.
     */
    abstract public String getLongDescription();

    /**
     * Returns the start time of the program in Milliseconds.
     */
    abstract public long getStartTimeUtcMillis();

    /**
     * Returns the end time of the program in Milliseconds.
     */
    abstract public long getEndTimeUtcMillis();

    /**
     * Returns the duration of the program in Milliseconds.
     */
    abstract public long getDurationMillis();

    /**
     * Returns the series ID.
     */
    abstract public String getSeriesId();

    /**
     * Returns the season number.
     */
    abstract public String getSeasonNumber();

    /**
     * Returns the episode number.
     */
    abstract public String getEpisodeNumber();

    /**
     * Returns URI of the program's poster.
     */
    abstract public String getPosterArtUri();

    /**
     * Returns URI of the program's thumbnail.
     */
    abstract public String getThumbnailUri();

    /**
     * Returns the array of the ID's of the canonical genres.
     */
    abstract public int[] getCanonicalGenreIds();

    /** Returns the array of content ratings. */
    @Nullable
    abstract public TvContentRating[] getContentRatings();

    /**
     * Returns channel's ID of the program.
     */
    abstract public long getChannelId();

    /**
     * Returns if the program is valid.
     */
    abstract public boolean isValid();

    /**
     * Checks whether the program is episodic or not.
     */
    public boolean isEpisodic() {
        return getSeriesId() != null;
    }

    /**
     * Generates the series ID for the other inputs than the tuner TV input.
     */
    public static String generateSeriesId(String packageName, String title) {
        return packageName + "/" + title;
    }
}