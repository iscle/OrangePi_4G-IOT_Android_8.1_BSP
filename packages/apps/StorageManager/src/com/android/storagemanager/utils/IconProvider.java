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

package com.android.storagemanager.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.webkit.MimeTypeMap;

import java.io.File;

/**
 * IconProvider is a class for getting file icons. It is strongly based upon the DocumentsUI
 * implementation of IconUtils, but tailored for the Deletion Helper use-case. Useful for testing.
 */
public class IconProvider {
    private Context mContext;

    public IconProvider(Context context) {
        mContext = context;
    }

    /**
     * Returns an icon which represents a file with the given MIME type.
     *
     * @param mimeType The MIME type of the file.
     * @return
     */
    public Drawable loadMimeIcon(String mimeType) {
        return mContext.getContentResolver().getTypeDrawable(mimeType);
    }

    public static String getMimeType(File file) {
        String name = file.getName();
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1);
            final String mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "application/octet-stream";
    }

    public static boolean isImageType(File file) {
        String mimeType = getMimeType(file);
        return mimeType.startsWith("image/");
    }

}
