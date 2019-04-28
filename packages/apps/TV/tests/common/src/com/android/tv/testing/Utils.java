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

package com.android.tv.testing;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.util.Log;

import com.android.tv.common.TvCommonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * An utility class for testing.
 *
 * <p>This class is also used to check whether TV app is running in tests or not.
 *
 * @see TvCommonUtils#isRunningInTest
 */
public final class Utils {
    private static final String TAG ="Utils";

    private static final long DEFAULT_RANDOM_SEED = getSeed();

    public static String getUriStringForResource(Context context, int resId) {
        if (resId == 0) {
            return "";
        }
        Resources res = context.getResources();
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(res.getResourcePackageName(resId))
            .path(res.getResourceTypeName(resId))
            .appendPath(res.getResourceEntryName(resId)).build().toString();
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    public static String getServiceNameFromInputId(Context context, String inputId) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getId().equals(inputId)) {
                return info.getServiceInfo().name;
            }
        }
        return null;
    }

    public static String getInputIdFromComponentName(Context context, ComponentName name) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            ServiceInfo si = info.getServiceInfo();
            if (new ComponentName(si.packageName, si.name).equals(name)) {
                return info.getId();
            }
        }
        return null;
    }

    /**
     * Return the Random class which is needed to make random data for testing.
     * Default seed of the random is today's date.
     */
    public static Random createTestRandom() {
        return new Random(DEFAULT_RANDOM_SEED);
    }

    private static long getSeed() {
        // Set random seed as the date to track failed test data easily.
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = dateFormat.format(new Date());
        Log.d(TAG, "Today's random seed is " + today);
        return Long.valueOf(today);
    }

    private Utils() {}

    /**
     * Checks whether TvActivity is enabled or not.
     */
    public static boolean isTvActivityEnabled(Context context) {
        PackageManager pm = context.getPackageManager();
        ComponentName name = new ComponentName("com.android.tv",
                "com.android.tv.TvActivity");
        int enabled = pm.getComponentEnabledSetting(name);
        return enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || enabled == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }
}
