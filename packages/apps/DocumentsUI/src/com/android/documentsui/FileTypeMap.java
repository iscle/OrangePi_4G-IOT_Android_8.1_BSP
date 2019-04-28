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

package com.android.documentsui;

import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.MimeTypes;

import libcore.net.MimeUtils;

import java.util.HashMap;

/**
 * A map from mime type to user friendly type string.
 */
public class FileTypeMap implements Lookup<String, String> {

    private static final String TAG = "FileTypeMap";

    private final Resources mRes;

    private final SparseArray<Integer> mMediaTypeStringMap = new SparseArray<>();

    private final HashMap<String, Integer> mFileTypeMap = new HashMap<>();
    private final HashMap<String, String> mArchiveTypeMap = new HashMap<>();
    private final HashMap<String, Integer> mSpecialMediaMimeType = new HashMap<>();

    FileTypeMap(Context context) {
        mRes = context.getResources();

        // Mapping from generic media type string to extension media type string
        mMediaTypeStringMap.put(R.string.video_file_type, R.string.video_extension_file_type);
        mMediaTypeStringMap.put(R.string.audio_file_type, R.string.audio_extension_file_type);
        mMediaTypeStringMap.put(R.string.image_file_type, R.string.image_extension_file_type);

        // Common file types
        mFileTypeMap.put(MimeTypes.APK_TYPE, R.string.apk_file_type);
        mFileTypeMap.put("text/plain", R.string.txt_file_type);
        mFileTypeMap.put("text/html", R.string.html_file_type);
        mFileTypeMap.put("application/xhtml+xml", R.string.html_file_type);
        mFileTypeMap.put("application/pdf", R.string.pdf_file_type);

        // MS file types
        mFileTypeMap.put("application/msword", R.string.word_file_type);
        mFileTypeMap.put(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                R.string.word_file_type);
        mFileTypeMap.put("application/vnd.ms-powerpoint", R.string.ppt_file_type);
        mFileTypeMap.put(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                R.string.ppt_file_type);
        mFileTypeMap.put("application/vnd.ms-excel", R.string.excel_file_type);
        mFileTypeMap.put(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                R.string.excel_file_type);

        // Google doc types
        mFileTypeMap.put("application/vnd.google-apps.document", R.string.gdoc_file_type);
        mFileTypeMap.put("application/vnd.google-apps.spreadsheet", R.string.gsheet_file_type);
        mFileTypeMap.put("application/vnd.google-apps.presentation", R.string.gslides_file_type);
        mFileTypeMap.put("application/vnd.google-apps.drawing", R.string.gdraw_file_type);
        mFileTypeMap.put("application/vnd.google-apps.fusiontable", R.string.gtable_file_type);
        mFileTypeMap.put("application/vnd.google-apps.form", R.string.gform_file_type);
        mFileTypeMap.put("application/vnd.google-apps.map", R.string.gmap_file_type);
        mFileTypeMap.put("application/vnd.google-apps.sites", R.string.gsite_file_type);

        // Directory type
        mFileTypeMap.put("vnd.android.document/directory", R.string.directory_type);

        // Archive types
        mArchiveTypeMap.put("application/rar", "RAR");
        mArchiveTypeMap.put("application/zip", "Zip");
        mArchiveTypeMap.put("application/x-tar", "Tar");
        mArchiveTypeMap.put("application/gzip", "Gzip");
        mArchiveTypeMap.put("application/x-7z-compressed", "7z");
        mArchiveTypeMap.put("application/x-rar-compressed", "RAR");

        // Special media mime types
        mSpecialMediaMimeType.put("application/ogg", R.string.audio_file_type);
        mSpecialMediaMimeType.put("application/x-flac", R.string.audio_file_type);
    }

    @Override
    public String lookup(String mimeType) {
        if (mFileTypeMap.containsKey(mimeType)) {
            return getPredefinedFileTypeString(mimeType);
        }

        if (mArchiveTypeMap.containsKey(mimeType)) {
            return buildArchiveTypeString(mimeType);
        }

        if (mSpecialMediaMimeType.containsKey(mimeType)) {
            int genericType = mSpecialMediaMimeType.get(mimeType);
            return getFileTypeString(mimeType, mMediaTypeStringMap.get(genericType), genericType);
        }

        final String[] type = MimeTypes.splitMimeType(mimeType);
        if (type == null) {
            Log.w(TAG, "Unexpected mime type " + mimeType);
            return getGenericFileTypeString();
        }

        switch (type[0]) {
            case MimeTypes.IMAGE_PREFIX:
                return getFileTypeString(
                        mimeType, R.string.image_extension_file_type, R.string.image_file_type);
            case MimeTypes.AUDIO_PREFIX:
                return getFileTypeString(
                        mimeType, R.string.audio_extension_file_type, R.string.audio_file_type);
            case MimeTypes.VIDEO_PREFIX:
                return getFileTypeString(
                        mimeType, R.string.video_extension_file_type, R.string.video_file_type);
            default:
                return getFileTypeString(
                        mimeType, R.string.generic_extention_file_type, R.string.generic_file_type);
        }
    }

    private String buildArchiveTypeString(String mimeType) {
        final String archiveType = mArchiveTypeMap.get(mimeType);

        assert(!TextUtils.isEmpty(archiveType));

        final String format = mRes.getString(R.string.archive_file_type);
        return String.format(format, archiveType);
    }

    private String getPredefinedFileTypeString(String mimeType) {
        return mRes.getString(mFileTypeMap.get(mimeType));
    }

    private String getFileTypeString(
            String mimeType, @StringRes int formatStringId, @StringRes int defaultStringId) {
        final String extension = MimeUtils.guessExtensionFromMimeType(mimeType);

        return TextUtils.isEmpty(extension)
                ? mRes.getString(defaultStringId)
                : String.format(mRes.getString(formatStringId), extension.toUpperCase());
    }

    private String getGenericFileTypeString() {
        return mRes.getString(R.string.generic_file_type);
    }
}
