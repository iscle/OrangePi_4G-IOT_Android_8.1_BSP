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

package android.telephony.cts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A simple provider to send MMS PDU to platform MMS service
 */
public class MmsPduProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // Not supported
        return null;
    }

    @Override
    public String getType(Uri uri) {
        // Not supported
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Not supported
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Not supported
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Not supported
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String fileMode) throws FileNotFoundException {
        File file = new File(getContext().getCacheDir(), uri.getPath());
        int mode = (TextUtils.equals(fileMode, "r") ? ParcelFileDescriptor.MODE_READ_ONLY :
                ParcelFileDescriptor.MODE_WRITE_ONLY
                        |ParcelFileDescriptor.MODE_TRUNCATE
                        |ParcelFileDescriptor.MODE_CREATE);
        return ParcelFileDescriptor.open(file, mode);
    }
}
