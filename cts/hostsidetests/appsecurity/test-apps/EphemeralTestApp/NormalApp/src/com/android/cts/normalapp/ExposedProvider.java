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

package com.android.cts.normalapp;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.cts.util.TestResult;

public class ExposedProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUriMatcher.addURI("com.android.cts.normalapp.exposed.provider", "table", 1);
    }
    private static final String[] sColumnNames = { "_ID", "name" };
    private static final MatrixCursor sCursor = new MatrixCursor(sColumnNames, 1);
    static {
        sCursor.newRow().add(1).add("ExposedProvider");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        boolean canAccessInstantApp = false;
        String exception = null;
        try {
            canAccessInstantApp = tryAccessingInstantApp();
        } catch (Throwable t) {
            exception = t.getClass().getName();
        }

        TestResult.getBuilder()
                .setPackageName("com.android.cts.normalapp")
                .setComponentName("ExposedProvider")
                .setStatus("PASS")
                .setException(exception)
                .setEphemeralPackageInfoExposed(canAccessInstantApp)
                .build()
                .broadcast(getContext());

        return (sUriMatcher.match(uri) != 1) ? null : sCursor;
    }

    private boolean tryAccessingInstantApp() throws NameNotFoundException {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo("com.android.cts.ephemeralapp1", 0 /*flags*/);
        return (info != null);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

}
