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

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvTrackInfo;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A class about the constants for TV settings.
 * Objects that are returned from the various {@code get} methods must be treated as immutable.
 */
public final class TvSettings {
    public static final String PREF_DISPLAY_MODE = "display_mode";  // int value
    public static final String PREF_PIN = "pin"; // 4-digit string value. Otherwise, it's not set.

    // Multi-track audio settings
    private static final String PREF_MULTI_AUDIO_ID = "pref.multi_audio_id";
    private static final String PREF_MULTI_AUDIO_LANGUAGE = "pref.multi_audio_language";
    private static final String PREF_MULTI_AUDIO_CHANNEL_COUNT = "pref.multi_audio_channel_count";

    // DVR Multi-audio and subtitle settings
    private static final String PREF_DVR_MULTI_AUDIO_ID = "pref.dvr_multi_audio_id";
    private static final String PREF_DVR_MULTI_AUDIO_LANGUAGE = "pref.dvr_multi_audio_language";
    private static final String PREF_DVR_MULTI_AUDIO_CHANNEL_COUNT =
            "pref.dvr_multi_audio_channel_count";
    private static final String PREF_DVR_SUBTITLE_ID = "pref.dvr_subtitle_id";
    private static final String PREF_DVR_SUBTITLE_LANGUAGE = "pref.dvr_subtitle_language";

    // Parental Control settings
    private static final String PREF_CONTENT_RATING_SYSTEMS = "pref.content_rating_systems";
    private static final String PREF_CONTENT_RATING_LEVEL = "pref.content_rating_level";
    private static final String PREF_DISABLE_PIN_UNTIL = "pref.disable_pin_until";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CONTENT_RATING_LEVEL_NONE, CONTENT_RATING_LEVEL_HIGH, CONTENT_RATING_LEVEL_MEDIUM,
            CONTENT_RATING_LEVEL_LOW, CONTENT_RATING_LEVEL_CUSTOM })
    public @interface ContentRatingLevel {}
    public static final int CONTENT_RATING_LEVEL_NONE = 0;
    public static final int CONTENT_RATING_LEVEL_HIGH = 1;
    public static final int CONTENT_RATING_LEVEL_MEDIUM = 2;
    public static final int CONTENT_RATING_LEVEL_LOW = 3;
    public static final int CONTENT_RATING_LEVEL_CUSTOM = 4;

    private TvSettings() {}

    // Multi-track audio settings
    public static String getMultiAudioId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
                PREF_MULTI_AUDIO_ID, null);
    }

    public static void setMultiAudioId(Context context, String language) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                PREF_MULTI_AUDIO_ID, language).apply();
    }

    public static String getMultiAudioLanguage(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
                PREF_MULTI_AUDIO_LANGUAGE, null);
    }

    public static void setMultiAudioLanguage(Context context, String language) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                PREF_MULTI_AUDIO_LANGUAGE, language).apply();
    }

    public static int getMultiAudioChannelCount(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(
                PREF_MULTI_AUDIO_CHANNEL_COUNT, 0);
    }

    public static void setMultiAudioChannelCount(Context context, int channelCount) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(
                PREF_MULTI_AUDIO_CHANNEL_COUNT, channelCount).apply();
    }

    public static void setDvrPlaybackTrackSettings(Context context, int trackType,
            TvTrackInfo info) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            if (info == null) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .remove(PREF_DVR_MULTI_AUDIO_ID).apply();
            } else {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(PREF_DVR_MULTI_AUDIO_LANGUAGE, info.getLanguage())
                        .putInt(PREF_DVR_MULTI_AUDIO_CHANNEL_COUNT, info.getAudioChannelCount())
                        .putString(PREF_DVR_MULTI_AUDIO_ID, info.getId()).apply();
            }
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            if (info == null) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .remove(PREF_DVR_SUBTITLE_ID).apply();
            } else {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(PREF_DVR_SUBTITLE_LANGUAGE, info.getLanguage())
                        .putString(PREF_DVR_SUBTITLE_ID, info.getId()).apply();
            }
        }
    }

    public static TvTrackInfo getDvrPlaybackTrackSettings(Context context,
            int trackType) {
        String language;
        String trackId;
        int channelCount;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            trackId = pref.getString(PREF_DVR_MULTI_AUDIO_ID, null);
            if (trackId == null) {
                return null;
            }
            language = pref.getString(PREF_DVR_MULTI_AUDIO_LANGUAGE, null);
            channelCount = pref.getInt(PREF_DVR_MULTI_AUDIO_CHANNEL_COUNT, 0);
            return new TvTrackInfo.Builder(trackType, trackId)
                    .setLanguage(language).setAudioChannelCount(channelCount).build();
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            trackId = pref.getString(PREF_DVR_SUBTITLE_ID, null);
            if (trackId == null) {
                return null;
            }
            language = pref.getString(PREF_DVR_SUBTITLE_LANGUAGE, null);
            return new TvTrackInfo.Builder(trackType, trackId).setLanguage(language).build();
        } else {
            return null;
        }
    }

    // Parental Control settings
    public static void addContentRatingSystem(Context context, String id) {
        Set<String> contentRatingSystemSet = getContentRatingSystemSet(context);
        if (contentRatingSystemSet.add(id)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putStringSet(PREF_CONTENT_RATING_SYSTEMS, contentRatingSystemSet).apply();
        }
    }

    public static void removeContentRatingSystem(Context context, String id) {
        Set<String> contentRatingSystemSet = getContentRatingSystemSet(context);
        if (contentRatingSystemSet.remove(id)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putStringSet(PREF_CONTENT_RATING_SYSTEMS, contentRatingSystemSet).apply();
        }
    }

    public static boolean hasContentRatingSystem(Context context, String id) {
        return getContentRatingSystemSet(context).contains(id);
    }

    /**
     * Returns whether the content rating system is ever set. Returns {@code false} only when the
     * user changes parental control settings for the first time.
     */
    public static boolean isContentRatingSystemSet(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_CONTENT_RATING_SYSTEMS, null) != null;
    }

    private static Set<String> getContentRatingSystemSet(Context context) {
        return new HashSet<>(PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_CONTENT_RATING_SYSTEMS, Collections.emptySet()));
    }

    @ContentRatingLevel
    @SuppressWarnings("ResourceType")
    public static int getContentRatingLevel(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(
                PREF_CONTENT_RATING_LEVEL, CONTENT_RATING_LEVEL_NONE);
    }

    public static void setContentRatingLevel(Context context,
            @ContentRatingLevel int level) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(
                PREF_CONTENT_RATING_LEVEL, level).apply();
    }

    /**
     * Returns the time until we should disable the PIN dialog (because the user input wrong PINs
     * repeatedly).
     */
    public static long getDisablePinUntil(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(
                PREF_DISABLE_PIN_UNTIL, 0);
    }

    /**
     * Saves the time until we should disable the PIN dialog (because the user input wrong PINs
     * repeatedly).
     */
    public static void setDisablePinUntil(Context context, long timeMillis) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(
                PREF_DISABLE_PIN_UNTIL, timeMillis).apply();
    }
}