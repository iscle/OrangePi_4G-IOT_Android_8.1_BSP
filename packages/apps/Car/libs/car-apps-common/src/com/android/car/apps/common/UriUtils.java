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
package com.android.car.apps.common;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;

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
    private static final String DETAIL_DIALOG_URI_DIALOG_TITLE = "detail_dialog_title";
    private static final String DETAIL_DIALOG_URI_DIALOG_DESCRIPTION = "detail_dialog_description";
    private static final String DETAIL_DIALOG_URI_DIALOG_ACTION_START_INDEX =
            "detail_dialog_action_start_index";
    private static final String DETAIL_DIALOG_URI_DIALOG_ACTION_START_NAME =
            "detail_dialog_action_start_name";

    /**
     * Non instantiable.
     */
    private UriUtils() {}

    /**
     * Gets resource uri representation for a resource of a package
     */
    public static String getAndroidResourceUri(Context context, int resourceId) {
        return getAndroidResourceUri(context.getResources(), resourceId);
    }

    /**
     * Gets resource uri representation for a resource
     */
    public static String getAndroidResourceUri(Resources resources, int resourceId) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE
                + SCHEME_DELIMITER + resources.getResourceName(resourceId)
                        .replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER);
    }

    /**
     * Loads drawable from resource
     */
    public static Drawable getDrawable(Context context, ShortcutIconResource r)
            throws NameNotFoundException {
        Resources resources = context.getPackageManager().getResourcesForApplication(r.packageName);
        if (resources == null) {
            return null;
        }
        resources.updateConfiguration(context.getResources().getConfiguration(),
                context.getResources().getDisplayMetrics());
        final int id = resources.getIdentifier(r.resourceName, null, null);
        return resources.getDrawable(id);
    }

    /**
     * Gets a URI with short cut icon scheme.
     */
    public static Uri getShortcutIconResourceUri(ShortcutIconResource iconResource) {
        return Uri.parse(SCHEME_SHORTCUT_ICON_RESOURCE + SCHEME_DELIMITER + iconResource.packageName
                + URI_PATH_DELIMITER
                + iconResource.resourceName.replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER));
    }

    /**
     * Gets a URI with scheme = {@link ContentResolver#SCHEME_ANDROID_RESOURCE} for
     * a full resource name. This name is a single string of the form "package:type/entry".
     */
    public static Uri getAndroidResourceUri(String resourceName) {
        Uri uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + SCHEME_DELIMITER
                + resourceName.replace(URI_PACKAGE_DELIMITER, URI_PATH_DELIMITER));
        return uri;
    }

    /**
     * Checks if the URI refers to an Android resource.
     */
    public static boolean isAndroidResourceUri(Uri uri) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme());
    }

    /**
     * Gets a URI with the account image scheme.
     * @hide
     */
    public static Uri getAccountImageUri(String accountName) {
        Uri uri = Uri.parse(SCHEME_ACCOUNT_IMAGE + SCHEME_DELIMITER + accountName);
        return uri;
    }

    /**
     * Gets a URI with the account image scheme, and specifying an URI to be
     * used in notifyChange() when the image pointed to by the returned URI is
     * updated.
     * @hide
     */
    public static Uri getAccountImageUri(String accountName, Uri changeNotifyUri) {
        Uri uri = Uri.parse(SCHEME_ACCOUNT_IMAGE + SCHEME_DELIMITER + accountName);
        if (changeNotifyUri != null) {
            uri = uri.buildUpon().appendQueryParameter(ACCOUNT_IMAGE_CHANGE_NOTIFY_URI,
                    changeNotifyUri.toString()).build();
        }
        return uri;
    }

    /**
     * Checks if the URI refers to an account image.
     * @hide
     */
    public static boolean isAccountImageUri(Uri uri) {
        return uri == null ? false : SCHEME_ACCOUNT_IMAGE.equals(uri.getScheme());
    }

    /**
     * @hide
     */
    public static String getAccountName(Uri uri) {
        if (isAccountImageUri(uri)) {
            String accountName = uri.getAuthority() + uri.getPath();
            return accountName;
        } else {
            throw new IllegalArgumentException("Invalid account image URI. " + uri);
        }
    }

    /**
     * @hide
     */
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

    /**
     * Build a Uri for canvas details subactions dialog given content uri and optional parameters.
     * @param uri the subactions ContentUri
     * @param dialogTitle the custom subactions dialog title. If the value is null, canvas will
     *        fall back to use previous action's name as the subactions dialog title.
     * @param dialogDescription the custom subactions dialog description. If the value is null,
     *        canvas will fall back to use previous action's subname as the subactions dialog
     *        description.
     * @hide
     */
    public static Uri getSubactionDialogUri(Uri uri, String dialogTitle, String dialogDescription) {
        return getSubactionDialogUri(uri, dialogTitle, dialogDescription, null, -1);
    }

    /**
     * Build a Uri for canvas details subactions dialog given content uri and optional parameters.
     * @param uri the subactions ContentUri
     * @param dialogTitle the custom subactions dialog title. If the value is null, canvas will
     *        fall back to use previous action's name as the subactions dialog title.
     * @param dialogDescription the custom subactions dialog description. If the value is null,
     *        canvas will fall back to use previous action's subname as the subactions dialog
     *        description.
     * @param startIndex the focused action in actions list when started.
     * @hide
     */
    public static Uri getSubactionDialogUri(Uri uri, String dialogTitle, String dialogDescription,
            int startIndex) {
        return getSubactionDialogUri(uri, dialogTitle, dialogDescription, null, startIndex);
    }

    /**
     * Build a Uri for canvas details subactions dialog given content uri and optional parameters.
     * @param uri the subactions ContentUri
     * @param dialogTitle the custom subactions dialog title. If the value is null, canvas will
     *        fall back to use previous action's name as the subactions dialog title.
     * @param dialogDescription the custom subactions dialog description. If the value is null,
     *        canvas will fall back to use previous action's subname as the subactions dialog
     *        description.
     * @param startName the name of action that is focused in actions list when started.
     * @hide
     */
    public static Uri getSubactionDialogUri(Uri uri, String dialogTitle, String dialogDescription,
            String startName) {
        return getSubactionDialogUri(uri, dialogTitle, dialogDescription, startName, -1);
    }

    /**
     * Build a Uri for canvas details subactions dialog given content uri and optional parameters.
     * @param uri the subactions ContentUri
     * @param dialogTitle the custom subactions dialog title. If the value is null, canvas will
     *        fall back to use previous action's name as the subactions dialog title.
     * @param dialogDescription the custom subactions dialog description. If the value is null,
     *        canvas will fall back to use previous action's subname as the subactions dialog
     *        description.
     * @param startIndex the focused action in actions list when started.
     * @param startName the name of action that is focused in actions list when started. startName
     *        takes priority over start index.
     * @hide
     */
    public static Uri getSubactionDialogUri(Uri uri, String dialogTitle, String dialogDescription,
            String startName, int startIndex) {
        if (uri == null || !isContentUri(uri)) {
            // If given uri is null, or it is not of contentUri type, return null.
            return null;
        }

        Uri.Builder builder = uri.buildUpon();
        if (!TextUtils.isEmpty(dialogTitle)) {
            builder.appendQueryParameter(DETAIL_DIALOG_URI_DIALOG_TITLE, dialogTitle);
        }

        if (!TextUtils.isEmpty(DETAIL_DIALOG_URI_DIALOG_DESCRIPTION)) {
            builder.appendQueryParameter(DETAIL_DIALOG_URI_DIALOG_DESCRIPTION, dialogDescription);
        }

        if (startIndex != -1) {
            builder.appendQueryParameter(DETAIL_DIALOG_URI_DIALOG_ACTION_START_INDEX,
                    Integer.toString(startIndex));
        }

        if (!TextUtils.isEmpty(startName)) {
            builder.appendQueryParameter(DETAIL_DIALOG_URI_DIALOG_ACTION_START_NAME, startName);
        }

        return builder.build();
    }

    /**
     * Get subaction dialog title parameter from URI
     * @param uri ContentUri for canvas details subactions
     * @return custom dialog title if this parameter is available in URI. Otherwise, return null.
     * @hide
     */
    public static String getSubactionDialogTitle(Uri uri) {
        if (uri == null || !isContentUri(uri)) {
            return null;
        }

        return uri.getQueryParameter(DETAIL_DIALOG_URI_DIALOG_TITLE);
    }

    /**
     * Get subaction dialog description parameter from URI
     * @param uri ContentUri for canvas details subactions
     * @return custom dialog description if this parameter is available in URI.
     * Otherwise, return null.
     * @hide
     */
    public static String getSubactionDialogDescription(Uri uri) {
        if (uri == null || !isContentUri(uri)) {
            return null;
        }

        return uri.getQueryParameter(DETAIL_DIALOG_URI_DIALOG_DESCRIPTION);
    }

    /**
     * Get subaction dialog action list focused index when started from URI
     * @param uri ContentUri for canvas details subactions
     * @return action starting index if this parameter is available in URI. Otherwise, return -1.
     * @hide
     */
    public static int getSubactionDialogActionStartIndex(Uri uri) {
        if (uri == null || !isContentUri(uri)) {
            return -1;
        }

        String startIndexStr = uri.getQueryParameter(DETAIL_DIALOG_URI_DIALOG_ACTION_START_INDEX);
        if (!TextUtils.isEmpty(startIndexStr) && TextUtils.isDigitsOnly(startIndexStr)) {
            return Integer.parseInt(startIndexStr);
        } else {
            return -1;
        }
    }

    /**
     * Get subaction dialog action list focused action name when started from URI
     * @param uri ContentUri for canvas details subactions
     * @return that name of starting action if this parameter is available in URI.
     * Otherwise, return null.
     * @hide
     */
    public static String getSubactionDialogActionStartName(Uri uri) {
        if (uri == null || !isContentUri(uri)) {
            return null;
        }

        return uri.getQueryParameter(DETAIL_DIALOG_URI_DIALOG_ACTION_START_NAME);
    }
}
