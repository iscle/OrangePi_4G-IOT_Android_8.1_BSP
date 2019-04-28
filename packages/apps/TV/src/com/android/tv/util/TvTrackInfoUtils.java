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
package com.android.tv.util;

import android.media.tv.TvTrackInfo;

import java.util.Comparator;
import java.util.List;

/**
 * Static utilities for {@link TvTrackInfo}.
 */
public class TvTrackInfoUtils {

    /**
     * Compares how closely two {@link android.media.tv.TvTrackInfo}s match {@code language}, {@code
     * channelCount} and {@code id} in that precedence.
     *
     * @param id           The track id to match.
     * @param language     The language to match.
     * @param channelCount The channel count to match.
     * @return -1 if lhs is a worse match, 0 if lhs and rhs match equally and 1 if lhs is a better
     * match.
     */
    public static Comparator<TvTrackInfo> createComparator(final String id, final String language,
            final int channelCount) {
        return new Comparator<TvTrackInfo>() {

            @Override
            public int compare(TvTrackInfo lhs, TvTrackInfo rhs) {
                if (lhs == rhs) {
                    return 0;
                }
                if (lhs == null) {
                    return -1;
                }
                if (rhs == null) {
                    return 1;
                }
                // Assumes {@code null} language matches to any language since it means user hasn't
                // selected any track before or selected a track without language information.
                boolean lhsLangMatch = language == null || Utils.isEqualLanguage(lhs.getLanguage(),
                        language);
                boolean rhsLangMatch = language == null || Utils.isEqualLanguage(rhs.getLanguage(),
                        language);
                if (lhsLangMatch && rhsLangMatch) {
                    boolean lhsCountMatch = lhs.getType() != TvTrackInfo.TYPE_AUDIO
                            || lhs.getAudioChannelCount() == channelCount;
                    boolean rhsCountMatch = rhs.getType() != TvTrackInfo.TYPE_AUDIO
                            || rhs.getAudioChannelCount() == channelCount;
                    if (lhsCountMatch && rhsCountMatch) {
                        return Boolean.compare(lhs.getId().equals(id), rhs.getId().equals(id));
                    } else {
                        return Boolean.compare(lhsCountMatch, rhsCountMatch);
                    }
                } else {
                    return Boolean.compare(lhsLangMatch, rhsLangMatch);
                }
            }
        };
    }

    /**
     * Selects the  best TvTrackInfo available or the first if none matches.
     *
     * @param tracks       The tracks to choose from
     * @param id           The track id to match.
     * @param language     The language to match.
     * @param channelCount The channel count to match.
     * @return the best matching track or the first one if none matches.
     */
    public static TvTrackInfo getBestTrackInfo(List<TvTrackInfo> tracks, String id, String language,
            int channelCount) {
        if (tracks == null) {
            return null;
        }
        Comparator<TvTrackInfo> comparator = createComparator(id, language, channelCount);
        TvTrackInfo best = null;
        for (TvTrackInfo track : tracks) {
            if (comparator.compare(track, best) > 0) {
                best = track;
            }
        }
        return best;
    }

    private TvTrackInfoUtils() {
    }
}