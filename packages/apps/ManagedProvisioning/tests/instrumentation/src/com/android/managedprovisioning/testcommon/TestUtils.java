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
package com.android.managedprovisioning.testcommon;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.android.managedprovisioning.common.StoreUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class TestUtils {
    public static Uri resourceToUri(Context context, int resID) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + context.getResources().getResourcePackageName(resID) + '/'
                + context.getResources().getResourceTypeName(resID) + '/'
                + context.getResources().getResourceEntryName(resID) );
    }

    public static String stringFromUri(ContentResolver cr, Uri uri) throws IOException {
        try (final InputStream in = cr.openInputStream(uri)) {
            try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                StoreUtils.copyStream(in, out);
                return out.toString();
            }
        }
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}
