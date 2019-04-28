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
package com.android.support.car.lenspicker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.media.MediaBrowserService;
import android.support.annotation.Nullable;

import java.net.URISyntaxException;

/**
 * Utility methods for the lenspicker
 */
public class LensPickerUtils {
    private static final String FACET_KEY_PREFIX = "facet_key_";
    private static final String PACKAGE_KEY_PREFIX = "package_key_";

    private static final String SHARED_PREF_FILE_KEY
            = "com.android.support.car.lenspicker.LENSPICKER_PREFERENCE_KEY";
    private static final String MEDIA_TEMPLATE_COMPONENT = "com.android.car.media";

    private static final String LAST_LAUNCHED_FACET_ID = "last_launched_facet_id";
    private static final String LAST_LAUNCHED_PACKAGE_NAME = "last_launched_package_name";
    private static final String LAST_LAUNCHED_INTENT_KEY = "last_launched_intent_key";

    // TODO: These two come from MediaManager.java in CarMediaApp and should probably be pushed
    // into a common place so that these two don't go out of sync. Duplicated for now.
    public static final String KEY_MEDIA_PACKAGE = "media_package";
    public static final String KEY_MEDIA_CLASS = "media_class";

    public static String getFacetKey(String facetId) {
        return FACET_KEY_PREFIX + facetId;
    }

    public static String getPackageKey(String packageName) {
        return PACKAGE_KEY_PREFIX + packageName;
    }

    public static SharedPreferences getFacetSharedPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREF_FILE_KEY, Context.MODE_PRIVATE);
    }

    /**
     * Launches the application that is specified by the given launch launch. The other parameters
     * are used to store information about what application was last launched so that subsequent
     * launches of that application are faster.
     */
    public static void launch(Context context, SharedPreferences sharedPrefs, String facetId,
            String packageName, Intent launchIntent) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(LensPickerUtils.getFacetKey(facetId), packageName);

        // Store information about the last launched application.
        editor.putString(LAST_LAUNCHED_FACET_ID, facetId);
        editor.putString(LAST_LAUNCHED_PACKAGE_NAME, packageName);

        String uriString = launchIntent.toUri(Intent.URI_INTENT_SCHEME);
        editor.putString(LAST_LAUNCHED_INTENT_KEY, uriString);

        editor.commit();

        context.startActivity(launchIntent);
    }

    /**
     * Saves the given app launch information as the last launched application.
     */
    public static void saveLastLaunchedAppInfo(SharedPreferences sharedPrefs, String facetId,
            String packageName, Intent launchIntent) {
        SharedPreferences.Editor editor = sharedPrefs.edit();

        // Store information about the last launched application.
        editor.putString(LAST_LAUNCHED_FACET_ID, facetId);
        editor.putString(LAST_LAUNCHED_PACKAGE_NAME, packageName);

        String uriString = launchIntent.toUri(Intent.URI_INTENT_SCHEME);
        editor.putString(LAST_LAUNCHED_INTENT_KEY, uriString);

        editor.commit();
    }

    /**
     * Returns the {@link AppLaunchInformation} for the last launched application from the
     * LensPicker.
     */
    @Nullable
    public static AppLaunchInformation getLastLaunchedAppInfo(SharedPreferences sharedPrefs) {
        String facetId = sharedPrefs.getString(LAST_LAUNCHED_FACET_ID, null);
        String packageName = sharedPrefs.getString(LAST_LAUNCHED_PACKAGE_NAME, null);
        String intentString = sharedPrefs.getString(LAST_LAUNCHED_INTENT_KEY, null);

        if (facetId == null || packageName == null || intentString == null) {
            return null;
        }

        return new AppLaunchInformation(facetId, packageName, intentString);
    }

    public static Intent getMediaLaunchIntent(PackageManager pm, String packageName,
            String className) {
        Intent intent = pm.getLaunchIntentForPackage(MEDIA_TEMPLATE_COMPONENT);
        intent.putExtra(KEY_MEDIA_PACKAGE, packageName);
        intent.putExtra(KEY_MEDIA_CLASS, className);
        return intent;
    }

    public static String getPackageName(ResolveInfo info) {
        if (info.activityInfo != null) {
            return info.activityInfo.packageName;
        } else if (info.serviceInfo != null) {
            return info.serviceInfo.packageName;
        }

        throw new RuntimeException("No activityInfo or serviceInfo. This should not happen!");
    }

    public static boolean isMediaService(ResolveInfo rInfo) {
        return rInfo.serviceInfo != null && rInfo.filter != null
                && rInfo.filter.hasAction(MediaBrowserService.SERVICE_INTERFACE);
    }

    @Nullable
    public static Intent getLaunchIntent(String packageName, ResolveInfo rInfo, PackageManager pm) {
        if (LensPickerUtils.isMediaService(rInfo)) {
            return LensPickerUtils.getMediaLaunchIntent(pm, packageName,
                    rInfo.serviceInfo.name);
        }

        return pm.getLaunchIntentForPackage(packageName);
    }

    /**
     * A class that wraps all the information needed to launch a particular application from the
     * LensPicker.
     */
    public static class AppLaunchInformation {
        private final String mFacetId;
        private final String mPackageName;
        private final String mIntentString;

        public AppLaunchInformation(String facetId, String packageName, String intentString) {
            mFacetId = facetId;
            mPackageName = packageName;
            mIntentString = intentString;
        }

        public String getFacetId() {
            return mFacetId;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public String getIntentString() {
            return mIntentString;
        }
    }
}
