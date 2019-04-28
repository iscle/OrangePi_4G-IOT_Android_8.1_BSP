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
package com.android.documentsui.base;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.roots.RootCursorWrapper;

import java.util.function.Predicate;

/**
 * Predicates for matching certain types of document records
 * in {@link Cursor}s.
 */
public final class DocumentFilters {

    private static int MOVABLE_MASK = Document.FLAG_SUPPORTS_REMOVE
            | Document.FLAG_SUPPORTS_DELETE
            | Document.FLAG_SUPPORTS_MOVE;

    public static final Predicate<Cursor> ANY = (Cursor c) -> { return true; };
    public static final Predicate<Cursor> VIRTUAL  = DocumentFilters::isVirtual;
    public static final Predicate<Cursor> NOT_MOVABLE = DocumentFilters::isNotMovable;
    private static final Predicate<Cursor> O_SHARABLE = DocumentFilters::isSharableInO;
    private static final Predicate<Cursor> PREO_SHARABLE = DocumentFilters::isSharablePreO;

    public static Predicate<Cursor> sharable(Features features) {
        return features.isVirtualFilesSharingEnabled()
                ? DocumentFilters.O_SHARABLE
                : DocumentFilters.PREO_SHARABLE;
    }

    private static boolean isSharableInO(Cursor c) {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        String authority = getCursorString(c, RootCursorWrapper.COLUMN_AUTHORITY);

        return (flags & Document.FLAG_PARTIAL) == 0
                && !ArchivesProvider.AUTHORITY.equals(authority);
    }

    /**
     * Filter that passes (returns true) for all files which can be shared in O.
     */
    private static boolean isSharablePreO(Cursor c) {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        String authority = getCursorString(c, RootCursorWrapper.COLUMN_AUTHORITY);

        return (flags & Document.FLAG_PARTIAL) == 0
                && (flags & Document.FLAG_VIRTUAL_DOCUMENT) == 0
                && !ArchivesProvider.AUTHORITY.equals(authority);
    }

    /**
     * Filter that passes (returns true) only virtual documents.
     */
    private static final boolean isVirtual(Cursor c) {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        return (flags & Document.FLAG_VIRTUAL_DOCUMENT) != 0;
    }

    /**
     * Filter that passes (returns true) for files that can not be moved.
     */
    private static final boolean isNotMovable(Cursor c) {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        return (flags & MOVABLE_MASK) == 0;
    }
}
