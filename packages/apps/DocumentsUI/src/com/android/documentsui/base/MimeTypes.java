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
package com.android.documentsui.base;

import android.annotation.Nullable;
import android.provider.DocumentsContract.Document;

import java.util.List;

public final class MimeTypes {

    private MimeTypes() {}

    public static final String APK_TYPE = "application/vnd.android.package-archive";

    public static final String IMAGE_PREFIX = "image";
    public static final String AUDIO_PREFIX = "audio";
    public static final String VIDEO_PREFIX = "video";

    /**
     * MIME types that are visual in nature. For example, they should always be
     * shown as thumbnails in list mode.
     */
    public static final String[] VISUAL_MIMES = new String[] { "image/*", "video/*" };

    public static @Nullable String[] splitMimeType(String mimeType) {
        final String[] groups = mimeType.split("/");

        if (groups.length != 2 || groups[0].isEmpty() || groups[1].isEmpty()) {
            return null;
        }

        return groups;
    }

    public static String findCommonMimeType(List<String> mimeTypes) {
        String[] commonType = splitMimeType(mimeTypes.get(0));
        if (commonType == null) {
            return "*/*";
        }

        for (int i = 1; i < mimeTypes.size(); i++) {
            String[] type = mimeTypes.get(i).split("/");
            if (type.length != 2) continue;

            if (!commonType[1].equals(type[1])) {
                commonType[1] = "*";
            }

            if (!commonType[0].equals(type[0])) {
                commonType[0] = "*";
                commonType[1] = "*";
                break;
            }
        }

        return commonType[0] + "/" + commonType[1];
    }

    public static boolean mimeMatches(String[] filters, String[] tests) {
        if (tests == null) {
            return false;
        }
        for (String test : tests) {
            if (mimeMatches(filters, test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeMatches(String filter, String[] tests) {
        if (tests == null) {
            return true;
        }
        for (String test : tests) {
            if (mimeMatches(filter, test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeMatches(String[] filters, String test) {
        if (filters == null) {
            return true;
        }
        for (String filter : filters) {
            if (mimeMatches(filter, test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeMatches(String filter, String test) {
        if (test == null) {
            return false;
        } else if (filter == null || "*/*".equals(filter)) {
            return true;
        } else if (filter.equals(test)) {
            return true;
        } else if (filter.endsWith("/*")) {
            return filter.regionMatches(0, test, 0, filter.indexOf('/'));
        } else {
            return false;
        }
    }

    public static boolean isApkType(@Nullable String mimeType) {
        return APK_TYPE.equals(mimeType);
    }

    public static boolean isDirectoryType(@Nullable String mimeType) {
        return Document.MIME_TYPE_DIR.equals(mimeType);
    }
}
