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

package com.android.documentsui.prefs;

import static com.android.documentsui.base.State.MODE_UNKNOWN;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.UserHandle;
import android.preference.PreferenceManager;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class LocalPreferences {
    private static final String ROOT_VIEW_MODE_PREFIX = "rootViewMode-";

    public static @ViewMode int getViewMode(Context context, RootInfo root,
            @ViewMode int fallback) {
        return getPrefs(context).getInt(createKey(root), fallback);
    }

    public static void setViewMode(Context context, RootInfo root, @ViewMode int viewMode) {
        assert(viewMode != MODE_UNKNOWN);
        getPrefs(context).edit().putInt(createKey(root), viewMode).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static String createKey(RootInfo root) {
        return ROOT_VIEW_MODE_PREFIX + root.authority + root.rootId;
    }

    public static final int PERMISSION_ASK = 0;
    public static final int PERMISSION_ASK_AGAIN = 1;
    public static final int PERMISSION_NEVER_ASK = -1;

    @IntDef(flag = true, value = {
            PERMISSION_ASK,
            PERMISSION_ASK_AGAIN,
            PERMISSION_NEVER_ASK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionStatus {}

    /**
     * Clears all preferences associated with a given package.
     *
     * <p>Typically called when a package is removed or when user asked to clear its data.
     */
    public static void clearPackagePreferences(Context context, String packageName) {
        clearScopedAccessPreferences(context, packageName);
    }

    /**
     * Methods below are used to keep track of denied user requests on scoped directory access so
     * the dialog is not offered when user checked the 'Do not ask again' box
     *
     * <p>It uses a shared preferences, whose key is:
     * <ol>
     * <li>{@code USER_ID|PACKAGE_NAME|VOLUME_UUID|DIRECTORY} for storage volumes that have a UUID
     * (typically physical volumes like SD cards).
     * <li>{@code USER_ID|PACKAGE_NAME||DIRECTORY} for storage volumes that do not have a UUID
     * (typically the emulated volume used for primary storage
     * </ol>
     */
    public static @PermissionStatus int getScopedAccessPermissionStatus(Context context,
            String packageName, @Nullable String uuid, String directory) {
        final String key = getScopedAccessDenialsKey(packageName, uuid, directory);
        return getPrefs(context).getInt(key, PERMISSION_ASK);
    }

    public static void setScopedAccessPermissionStatus(Context context, String packageName,
            @Nullable String uuid, String directory, @PermissionStatus int status) {
      final String key = getScopedAccessDenialsKey(packageName, uuid, directory);
      getPrefs(context).edit().putInt(key, status).apply();
    }

    private static void clearScopedAccessPreferences(Context context, String packageName) {
        final String keySubstring = "|" + packageName + "|";
        final SharedPreferences prefs = getPrefs(context);
        Editor editor = null;
        for (final String key : prefs.getAll().keySet()) {
            if (key.contains(keySubstring)) {
                if (editor == null) {
                    editor = prefs.edit();
                }
                editor.remove(key);
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private static String getScopedAccessDenialsKey(String packageName, String uuid,
            String directory) {
        final int userId = UserHandle.myUserId();
        return uuid == null
                ? userId + "|" + packageName + "||" + directory
                : userId + "|" + packageName + "|" + uuid + "|" + directory;
    }

    public static boolean shouldBackup(String s) {
        return (s != null) ? s.startsWith(ROOT_VIEW_MODE_PREFIX) : false;
    }
}
