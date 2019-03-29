/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.inputmethodservice.cts.provider;

import android.content.UriMatcher;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.List;

/**
 * Content URI helper.
 *
 * Helper object to parse content URI passed to content provider. A helper object is instantiated
 * via {@link Factory#newInstance(Uri)}, and a {@link Factory} object should be instantiated using
 * {@link FactoryBuilder}.
 *
 * A content URI is assumed to have a format "content://authority/table[/id]?" where table is a
 * SQLite table name in content provider and id is a primary key.
 */
final class UriHelper {

    static final class Factory {
        private final UriMatcher mUriMatcher;
        private final SparseArray<String> mUriTypeMap;

        public static FactoryBuilder builder() {
            return new FactoryBuilder();
        }

        private Factory(final FactoryBuilder builder) {
            mUriMatcher = builder.mUriMatcher;
            mUriTypeMap = builder.mUriTypeMap;
        }

        @NonNull
        UriHelper newInstance(final Uri uri) {
            if (mUriMatcher.match(uri) == UriMatcher.NO_MATCH) {
                throw new IllegalArgumentException("Unknown URI: " + uri);
            }
            return new UriHelper(uri);
        }

        @Nullable
        String getTypeOf(final Uri uri) {
            return mUriTypeMap.get(mUriMatcher.match(uri), null);
        }
    }

    static final class FactoryBuilder {
        private final UriMatcher mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        private final SparseArray<String> mUriTypeMap = new SparseArray<>();
        private int mMatcherCode;

        private FactoryBuilder() {
            mMatcherCode = 0;
        }

        FactoryBuilder addUri(final String authority, final String path, final String type) {
            if (TextUtils.isEmpty(authority)) {
                throw new IllegalArgumentException("Authority must not be empty");
            }
            if (TextUtils.isEmpty(path)) {
                throw new IllegalArgumentException("Path must not be empty");
            }
            final int matcherCode = mMatcherCode++;
            mUriMatcher.addURI(authority, path, matcherCode);
            mUriTypeMap.append(matcherCode, type);
            return this;
        }

        Factory build() {
            if (mMatcherCode == 0) {
                throw new IllegalStateException("No URI is defined");
            }
            return new Factory(this);
        }
    }

    /** Name of SQLite table specified by content uri. */
    @NonNull
    final String table;

    /** Primary id that is specified by content uri. Null if not. */
    @Nullable
    private final String mId;

    private UriHelper(final Uri uri) {
        final List<String> segments = uri.getPathSegments();
        table = segments.get(0);
        mId = (segments.size() >= 2) ? segments.get(1) : null;
    }

    /**
     * Composes selection SQL text from content uri and {@code selection} specified.
     * When content uri has a primary key, it needs to be composed with a selection text specified
     * as content provider parameter.
     *
     * @param selection selection text specified as a parameter to content provider.
     * @return composed selection SQL text, null if no selection specified.
     */
    @Nullable
    String buildSelection(@Nullable final String selection) {
        if (mId == null) {
            return selection;
        }
        // A primary key is specified by uri, so that selection should be at least "_id = ?".
        final StringBuilder sb = new StringBuilder().append(BaseColumns._ID).append(" = ?");
        if (selection != null) {
            // Selection is also specified as a parameter to content provider, so that it should be
            // appended with AND, such that "_id = ? AND (selection_text)".
            sb.append(" AND (").append(selection).append(")");
        }
        return sb.toString();
    }

    /**
     * Composes selection argument array from context uri and {@code selectionArgs} specified.
     * When content uri has a primary key, it needs to be provided in a final selection argument
     * array.
     *
     * @param selectionArgs selection argument array specified as a parameter to content provider.
     * @return composed selection argument array, null if selection argument is unnecessary.
     */
    @Nullable
    String[] buildSelectionArgs(@Nullable final String[] selectionArgs) {
        if (mId == null) {
            return selectionArgs;
        }
        // A primary key is specified by uri but not as a parameter to content provider, the primary
        // key value should be the sole selection argument.
        if (selectionArgs == null || selectionArgs.length == 0) {
            return new String[]{ mId };
        }
        // Selection args are also specified as a parameter to content provider, the primary key
        // value should be prepended to those selection args.
        final String[] args = new String[selectionArgs.length + 1];
        System.arraycopy(selectionArgs, 0, args, 1, selectionArgs.length);
        args[0] = mId;
        return args;
    }
}
