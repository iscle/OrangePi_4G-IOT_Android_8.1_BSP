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

package com.android.cts.verifier.tv;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility class for verifying channel/program results on global search requests.
 */
public class SearchUtil {
    private SearchUtil() {}

    /**
     * Returns {@code true} if one of the search results matches the given {@code expectedResult}.
     *
     * @param context The context object to be used for getting content resolver
     * @param searchable The {@link android.app.SearchableInfo} the TV app implements
     * @param query A query string to search for
     * @param expectedResult The expected search result
     */
    public static boolean verifySearchResult(Context context, SearchableInfo searchable,
            String query, String expectedResult) {
        Uri.Builder uriBuilder = getSearchUri(searchable).buildUpon();
        String selection = searchable.getSuggestSelection();
        String[] selectionArg = null;
        if (selection != null) {
            selectionArg = new String[] { query };
        } else {
            uriBuilder.appendPath(query);
        }

        Uri uri = uriBuilder.build();
        ContentProviderClient provider = context.getContentResolver()
                .acquireUnstableContentProviderClient(uri);
        try (Cursor c = provider.query(uri, null, selection, selectionArg, null, null)) {
            while (c.moveToNext()) {
                int index = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1);
                if (index >= 0) {
                    if (TextUtils.equals(expectedResult, c.getString(index))) {
                        return true;
                    }
                }
            }
        } catch (SQLiteException | RemoteException e) {
            return false;
        } finally {
            provider.release();
        }
        return false;
    }

    /**
     * Returns the {@link android.app.SearchableInfo} instances which should provide search results
     * for channels and programs in TvProvider.
     *
     * @param context The context object to used for accessing system services
     */
    public static List<SearchableInfo> getSearchableInfos(Context context) {
        // Just in case EPG is provided by a separate package, collect all possible TV packages
        // that can be searchable.
        PackageManager pm = context.getPackageManager();
        Set<String> tvPackages = new HashSet<>();
        List<ResolveInfo> infos = pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW,
                TvContract.Channels.CONTENT_URI), 0);
        for (ResolveInfo info : infos) {
            tvPackages.add(info.activityInfo.packageName);
        }
        infos = pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW,
                TvContract.Programs.CONTENT_URI), 0);
        for (ResolveInfo info : infos) {
            tvPackages.add(info.activityInfo.packageName);
        }
        SearchManager sm = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        List<SearchableInfo> globalSearchableInfos = sm.getSearchablesInGlobalSearch();
        List<SearchableInfo> tvSearchableInfos = new ArrayList<>();
        for (SearchableInfo info : globalSearchableInfos) {
            if (tvPackages.contains(info.getSearchActivity().getPackageName())) {
                tvSearchableInfos.add(info);
            }
        }
        return tvSearchableInfos;
    }

    private static Uri getSearchUri(SearchableInfo searchable) {
        if (searchable == null) {
            return null;
        }
        String authority = searchable.getSuggestAuthority();
        if (authority == null) {
            return null;
        }
        Uri.Builder uriBuilder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority);

        final String contentPath = searchable.getSuggestPath();
        if (contentPath != null) {
            uriBuilder.appendEncodedPath(contentPath);
        }

        uriBuilder.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY);
        return uriBuilder.build();
    }
}
