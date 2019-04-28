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

import android.net.Uri;

import com.android.documentsui.archives.ArchivesProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * Details about various system providers. These all need to be in sync with the
 * resources in their respective packages.
 */
public final class Providers {

    public static final String AUTHORITY_STORAGE = "com.android.externalstorage.documents";
    public static final String ROOT_ID_DEVICE = "primary";
    public static final String ROOT_ID_HOME = "home";

    public static final String AUTHORITY_DOWNLOADS = "com.android.providers.downloads.documents";
    public static final String ROOT_ID_DOWNLOADS = "downloads";

    public static final String AUTHORITY_MEDIA = "com.android.providers.media.documents";
    public static final String ROOT_ID_IMAGES = "images_root";
    public static final String ROOT_ID_VIDEOS = "videos_root";
    public static final String ROOT_ID_AUDIO = "audio_root";

    public static final String AUTHORITY_MTP = "com.android.mtp.documents";

    private static final String DOCSUI_PACKAGE = "com.android.documentsui";
    private static final Set<String> SYSTEM_AUTHORITIES = new HashSet<String>() {{
        add(AUTHORITY_STORAGE);
        add(AUTHORITY_DOWNLOADS);
        add(AUTHORITY_MEDIA);
        add(AUTHORITY_MTP);
    }};

    public static boolean isArchiveUri(Uri uri) {
        return uri != null && ArchivesProvider.AUTHORITY.equals(uri.getAuthority());
    }

    public static boolean isSystemProvider(String authority) {
        return SYSTEM_AUTHORITIES.contains(authority)
                || authority == null  // Recents
                || authority.startsWith(DOCSUI_PACKAGE);  // covers internal and test providers
    }
}
