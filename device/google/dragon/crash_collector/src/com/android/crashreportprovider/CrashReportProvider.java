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

package com.android.crashreportprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.FileObserver;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class CrashReportProvider extends ContentProvider {
    private static final File sCrashReportDirectory = new File("/data/system/crash_reports/");
    private static FileObserver sFileObserver;

    public static final String ACTION_CRASH_REPORT_ADDED =
            "com.android.crashreportprovider.action.CRASH_REPORT_ADDED";

    public static final Uri URI = Uri.parse("content://com.android.crashreportprovider/");

    public final String COLUMN_NAME = "name";
    private final String[] DEFAULT_PROJECTION = { COLUMN_NAME };

    @Override
    public boolean onCreate() {
        if (sFileObserver == null) {
            sFileObserver = new FileObserver(sCrashReportDirectory.getPath(),
                    FileObserver.CLOSE_WRITE) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path.endsWith(".meta")) {
                            final Intent intent = new Intent(ACTION_CRASH_REPORT_ADDED);
                            getContext().sendBroadcastAsUser(intent, UserHandle.OWNER,
                                    android.Manifest.permission.READ_LOGS);
                        }
                    }
                };
            sFileObserver.startWatching();
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        final MatrixCursor cursor =
                new MatrixCursor(projection == null ? DEFAULT_PROJECTION : projection);
        final File[] files = sCrashReportDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                final MatrixCursor.RowBuilder row = cursor.newRow();
                row.add(COLUMN_NAME, file.getName());
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (ParcelFileDescriptor.parseMode(mode) != ParcelFileDescriptor.MODE_READ_ONLY) {
            throw new FileNotFoundException("Cannot to open: " + uri + ", mode = " + mode);
        }
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 1 || !FileUtils.isValidExtFilename(segments.get(0))) {
            throw new FileNotFoundException("Invalid path.");
        }
        return ParcelFileDescriptor.open(new File(sCrashReportDirectory, segments.get(0)),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot delete: " + uri);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Cannot insert: " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot update: " + uri);
    }
}
