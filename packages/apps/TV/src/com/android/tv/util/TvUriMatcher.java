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

package com.android.tv.util;

import android.app.SearchManager;
import android.content.UriMatcher;
import android.media.tv.TvContract;
import android.net.Uri;
import android.support.annotation.IntDef;

import com.android.tv.search.LocalSearchProvider;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class to aid in matching URIs in TvProvider.
 */
public class TvUriMatcher {
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MATCH_CHANNEL, MATCH_CHANNEL_ID, MATCH_PROGRAM, MATCH_PROGRAM_ID,
            MATCH_RECORDED_PROGRAM, MATCH_RECORDED_PROGRAM_ID, MATCH_WATCHED_PROGRAM_ID,
            MATCH_ON_DEVICE_SEARCH})
    private @interface TvProviderUriMatchCode {}
    /** The code for the channels URI. */
    public static final int MATCH_CHANNEL = 1;
    /** The code for the channel URI. */
    public static final int MATCH_CHANNEL_ID = 2;
    /** The code for the programs URI. */
    public static final int MATCH_PROGRAM = 3;
    /** The code for the program URI. */
    public static final int MATCH_PROGRAM_ID = 4;
    /** The code for the recorded programs URI. */
    public static final int MATCH_RECORDED_PROGRAM = 5;
    /** The code for the recorded program URI. */
    public static final int MATCH_RECORDED_PROGRAM_ID = 6;
    /** The code for the watched program URI. */
    public static final int MATCH_WATCHED_PROGRAM_ID = 7;
    /** The code for the on-device search URI. */
    public static final int MATCH_ON_DEVICE_SEARCH = 8;
    static {
        URI_MATCHER.addURI(TvContract.AUTHORITY, "channel", MATCH_CHANNEL);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "channel/#", MATCH_CHANNEL_ID);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "program", MATCH_PROGRAM);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "program/#", MATCH_PROGRAM_ID);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "recorded_program", MATCH_RECORDED_PROGRAM);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "recorded_program/#", MATCH_RECORDED_PROGRAM_ID);
        URI_MATCHER.addURI(TvContract.AUTHORITY, "watched_program/#", MATCH_WATCHED_PROGRAM_ID);
        URI_MATCHER.addURI(LocalSearchProvider.AUTHORITY,
                SearchManager.SUGGEST_URI_PATH_QUERY + "/*", MATCH_ON_DEVICE_SEARCH);
    }

    private TvUriMatcher() { }

    /**
     * Try to match against the path in a url.
     *
     * @see UriMatcher#match
     */
    @SuppressWarnings("WrongConstant")
    @TvProviderUriMatchCode public static int match(Uri uri) {
        return URI_MATCHER.match(uri);
    }
}
