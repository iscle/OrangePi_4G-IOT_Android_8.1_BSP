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

package com.android.documentsui.archives;

import android.net.Uri;

public class ArchiveId {
    private final static char DELIMITER = '#';

    public final Uri mArchiveUri;
    public final int mAccessMode;
    public final String mPath;

    public ArchiveId(Uri archiveUri, int accessMode, String path) {
        assert(archiveUri.toString().indexOf(DELIMITER) == -1);
        assert(!path.isEmpty());

        mArchiveUri = archiveUri;
        mAccessMode = accessMode;
        mPath = path;
    }

    static public ArchiveId fromDocumentId(String documentId) {
        final int delimiterPosition = documentId.indexOf(DELIMITER);
        assert(delimiterPosition != -1);

        final int secondDelimiterPosition = documentId.indexOf(DELIMITER, delimiterPosition + 1);
        assert(secondDelimiterPosition != -1);

        final String archiveUriPart = documentId.substring(0, delimiterPosition);
        final String accessModePart = documentId.substring(delimiterPosition + 1,
                secondDelimiterPosition);

        final String pathPart = documentId.substring(secondDelimiterPosition + 1);

        return new ArchiveId(Uri.parse(archiveUriPart), Integer.parseInt(accessModePart),
                pathPart);
    }

    public String toDocumentId() {
        return mArchiveUri.toString() + DELIMITER + mAccessMode + DELIMITER + mPath;
    }
};
