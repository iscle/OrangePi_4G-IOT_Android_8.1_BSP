/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.util;

import android.content.ContentResolver;
import android.content.Intent.ShortcutIconResource;
import android.net.Uri;

/**
 * Utilities for working with URIs.
 */
public final class UriUtils {

    private static final String SCHEME_SHORTCUT_ICON_RESOURCE = "shortcut.icon.resource";
    private static final String SCHEME_DELIMITER = "://";
    private static final String URI_PATH_DELIMITER = "/";
    private static final String URI_PACKAGE_DELIMITER = ":";
    private static final String HTTP_PREFIX = "http";
    private static final String HTTPS_PREFIX = "https";
    private static final String SCHEME_ACCOUNT_IMAGE = "image.account";
    private static final String ACCOUNT_IMAGE_CHANGE_NOTIFY_URI = "change_notify_uri";

    /**
     * Non instantiable.
     */
    private UriUtils() {}

    /**
     * Gets a URI with short cut icon scheme.
     */
    public static Uri getShortcutIconResourceUri(ShortcutIconResource iconResource) {
        return Uri.parse(SCHEME_SHORTCUT_ICON_RESOURCE + SCHEME_DELIMITER + iconResource.packageName
                + URI_PATH_DELIMITER
                + iconResource.resourceName.replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER));
    }

    /**
     * Checks if the URI refers to an Android resource.
     */
    public static boolean isAndroidResourceUri(Uri uri) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme());
    }

    /**
     * Checks if the URI refers to an account image.
     */
    public static boolean isAccountImageUri(Uri uri) {
        return uri != null && SCHEME_ACCOUNT_IMAGE.equals(uri.getScheme());
    }

    public static String getAccountName(Uri uri) {
        if (isAccountImageUri(uri)) {
            return uri.getAuthority() + uri.getPath();
        } else {
            throw new IllegalArgumentException("Invalid account image URI. " + uri);
        }
    }

    public static Uri getAccountImageChangeNotifyUri(Uri uri) {
        if (isAccountImageUri(uri)) {
            String notifyUri = uri.getQueryParameter(ACCOUNT_IMAGE_CHANGE_NOTIFY_URI);
            if (notifyUri == null) {
                return null;
            } else {
                return Uri.parse(notifyUri);
            }
        } else {
            throw new IllegalArgumentException("Invalid account image URI. " + uri);
        }
    }

    /**
     * Returns {@code true} if the URI refers to a content URI which can be opened via
     * {@link ContentResolver#openInputStream(Uri)}.
     */
    public static boolean isContentUri(Uri uri) {
        return ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()) ||
                ContentResolver.SCHEME_FILE.equals(uri.getScheme());
    }

    /**
     * Checks if the URI refers to an shortcut icon resource.
     */
    public static boolean isShortcutIconResourceUri(Uri uri) {
        return SCHEME_SHORTCUT_ICON_RESOURCE.equals(uri.getScheme());
    }

    /**
     * Creates a shortcut icon resource object from an Android resource URI.
     */
    public static ShortcutIconResource getIconResource(Uri uri) {
        if(isAndroidResourceUri(uri)) {
            ShortcutIconResource iconResource = new ShortcutIconResource();
            iconResource.packageName = uri.getAuthority();
            // Trim off the scheme + 3 extra for "://", then replace the first "/" with a ":"
            iconResource.resourceName = uri.toString().substring(
                    ContentResolver.SCHEME_ANDROID_RESOURCE.length() + SCHEME_DELIMITER.length())
                    .replaceFirst(URI_PATH_DELIMITER, URI_PACKAGE_DELIMITER);
            return iconResource;
        } else if(isShortcutIconResourceUri(uri)) {
            ShortcutIconResource iconResource = new ShortcutIconResource();
            iconResource.packageName = uri.getAuthority();
            iconResource.resourceName = uri.toString().substring(
                    SCHEME_SHORTCUT_ICON_RESOURCE.length() + SCHEME_DELIMITER.length()
                    + iconResource.packageName.length() + URI_PATH_DELIMITER.length())
                    .replaceFirst(URI_PATH_DELIMITER, URI_PACKAGE_DELIMITER);
            return iconResource;
        } else {
            throw new IllegalArgumentException("Invalid resource URI. " + uri);
        }
    }

    /**
     * Returns {@code true} if this is a web URI.
     */
    public static boolean isWebUri(Uri resourceUri) {
        String scheme = resourceUri.getScheme() == null ? null
                : resourceUri.getScheme().toLowerCase();
        return HTTP_PREFIX.equals(scheme) || HTTPS_PREFIX.equals(scheme);
    }

}
