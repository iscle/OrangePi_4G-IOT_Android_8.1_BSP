/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;

import libcore.io.IoUtils;

public class DirectoryResult implements AutoCloseable {

    public Cursor cursor;
    public Exception exception;
    public DocumentInfo doc;
    ContentProviderClient client;

    @Override
    public void close() {
        IoUtils.closeQuietly(cursor);
        if (client != null && doc.isInArchive()) {
            ArchivesProvider.releaseArchive(client, doc.derivedUri);
        }
        cursor = null;
        client = null;
        doc = null;
    }
}
