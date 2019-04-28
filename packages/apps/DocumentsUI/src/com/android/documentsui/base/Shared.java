/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.PluralsRes;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.WindowManager;

import com.android.documentsui.R;
import com.android.documentsui.ui.MessageBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/** @hide */
public final class Shared {

    public static final String TAG = "Documents";

    public static final boolean DEBUG = Build.IS_DEBUGGABLE;
    public static final boolean VERBOSE = DEBUG && Log.isLoggable(TAG, Log.VERBOSE);

    /** Intent action name to pick a copy destination. */
    public static final String ACTION_PICK_COPY_DESTINATION =
            "com.android.documentsui.PICK_COPY_DESTINATION";

    /**
     * Extra boolean flag for {@link #ACTION_PICK_COPY_DESTINATION}, which
     * specifies if the destination directory needs to create new directory or not.
     */
    public static final String EXTRA_DIRECTORY_COPY = "com.android.documentsui.DIRECTORY_COPY";

    /**
     * Extra flag used to store the current stack so user opens in right spot.
     */
    public static final String EXTRA_STACK = "com.android.documentsui.STACK";

    /**
     * Extra flag used to store query of type String in the bundle.
     */
    public static final String EXTRA_QUERY = "query";

    /**
     * Extra flag used to store state of type State in the bundle.
     */
    public static final String EXTRA_STATE = "state";

    /**
     * Extra flag used to store root of type RootInfo in the bundle.
     */
    public static final String EXTRA_ROOT = "root";

    /**
     * Extra flag used to store document of DocumentInfo type in the bundle.
     */
    public static final String EXTRA_DOC = "document";

    /**
     * Extra flag used to store DirectoryFragment's selection of Selection type in the bundle.
     */
    public static final String EXTRA_SELECTION = "selection";

    /**
     * Extra flag used to store DirectoryFragment's ignore state of boolean type in the bundle.
     */
    public static final String EXTRA_IGNORE_STATE = "ignoreState";

    /**
     * Extra for an Intent for enabling performance benchmark. Used only by tests.
     */
    public static final String EXTRA_BENCHMARK = "com.android.documentsui.benchmark";

    /**
     * Extra flag used to signify to inspector that debug section can be shown.
     */
    public static final String EXTRA_SHOW_DEBUG = "com.android.documentsui.SHOW_DEBUG";

    /**
     * Maximum number of items in a Binder transaction packet.
     */
    public static final int MAX_DOCS_IN_INTENT = 500;

    /**
     * Animation duration of checkbox in directory list/grid in millis.
     */
    public static final int CHECK_ANIMATION_DURATION = 100;

    private static final Collator sCollator;

    static {
        sCollator = Collator.getInstance();
        sCollator.setStrength(Collator.SECONDARY);
    }

    /**
     * @deprecated use {@link MessageBuilder#getQuantityString}
     */
    @Deprecated
    public static final String getQuantityString(Context context, @PluralsRes int resourceId, int quantity) {
        return context.getResources().getQuantityString(resourceId, quantity, quantity);
    }

    public static String formatTime(Context context, long when) {
        // TODO: DateUtils should make this easier
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        int flags = DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT
                | DateUtils.FORMAT_ABBREV_ALL;

        if (then.year != now.year) {
            flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        return DateUtils.formatDateTime(context, when, flags);
    }

    /**
     * A convenient way to transform any list into a (parcelable) ArrayList.
     * Uses cast if possible, else creates a new list with entries from {@code list}.
     */
    public static <T> ArrayList<T> asArrayList(List<T> list) {
        return list instanceof ArrayList
            ? (ArrayList<T>) list
            : new ArrayList<>(list);
    }

    /**
     * Compare two strings against each other using system default collator in a
     * case-insensitive mode. Clusters strings prefixed with {@link DIR_PREFIX}
     * before other items.
     */
    public static int compareToIgnoreCaseNullable(String lhs, String rhs) {
        final boolean leftEmpty = TextUtils.isEmpty(lhs);
        final boolean rightEmpty = TextUtils.isEmpty(rhs);

        if (leftEmpty && rightEmpty) return 0;
        if (leftEmpty) return -1;
        if (rightEmpty) return 1;

        return sCollator.compare(lhs, rhs);
    }

    /**
     * Returns the calling package, possibly overridden by EXTRA_PACKAGE_NAME.
     * @param activity
     * @return
     */
    public static String getCallingPackageName(Activity activity) {
        String callingPackage = activity.getCallingPackage();
        // System apps can set the calling package name using an extra.
        try {
            ApplicationInfo info =
                    activity.getPackageManager().getApplicationInfo(callingPackage, 0);
            if (info.isSystemApp() || info.isUpdatedSystemApp()) {
                final String extra = activity.getIntent().getStringExtra(
                        DocumentsContract.EXTRA_PACKAGE_NAME);
                if (extra != null && !TextUtils.isEmpty(extra)) {
                    callingPackage = extra;
                }
            }
        } catch (NameNotFoundException e) {
            // Couldn't lookup calling package info. This isn't really
            // gonna happen, given that we're getting the name of the
            // calling package from trusty old Activity.getCallingPackage.
            // For that reason, we ignore this exception.
        }
        return callingPackage;
    }

    /**
     * Returns the default directory to be presented after starting the activity.
     * Method can be overridden if the change of the behavior of the the child activity is needed.
     */
    public static Uri getDefaultRootUri(Activity activity) {
        Uri defaultUri = Uri.parse(activity.getResources().getString(R.string.default_root_uri));

        if (!DocumentsContract.isRootUri(activity, defaultUri)) {
            throw new RuntimeException("Default Root URI is not a valid root URI.");
        }

        return defaultUri;
    }

    public static boolean isHardwareKeyboardAvailable(Context context) {
        return context.getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS;
    }

    public static void ensureKeyboardPresent(Context context, AlertDialog dialog) {
        if (!isHardwareKeyboardAvailable(context)) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    /**
     * Returns true if "Documents" root should be shown.
     */
    public static boolean shouldShowDocumentsRoot(Context context) {
        return context.getResources().getBoolean(R.bool.show_documents_root);
    }

    /*
     * Returns true if the local/device storage root must be visible (this also hides
     * the option to toggle visibility in the menu.)
     */
    public static boolean mustShowDeviceRoot(Intent intent) {
        return intent.getBooleanExtra(DocumentsContract.EXTRA_SHOW_ADVANCED, false);
    }

    public static String getDeviceName(ContentResolver resolver) {
        // We match the value supplied by ExternalStorageProvider for
        // the internal storage root.
        return Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME);
    }

    public static void checkMainLoop() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            Log.e(TAG, "Calling from non-UI thread!");
        }
    }

    public static @Nullable <T> T findView(Activity activity, int... resources) {
        for (int id : resources) {
            @SuppressWarnings("unchecked")
            T r = (T) activity.findViewById(id);
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
