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
 * limitations under the License
 */

package com.android.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.common.feature.Feature;
import com.android.tv.common.feature.GServiceFeature;
import com.android.tv.common.feature.PropertyFeature;
import com.android.tv.config.RemoteConfig;
import com.android.tv.experiments.Experiments;
import com.android.tv.util.LocationUtils;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.Utils;

import java.util.Locale;

import static com.android.tv.common.feature.EngOnlyFeature.ENG_ONLY_FEATURE;
import static com.android.tv.common.feature.FeatureUtils.AND;
import static com.android.tv.common.feature.FeatureUtils.OFF;
import static com.android.tv.common.feature.FeatureUtils.ON;
import static com.android.tv.common.feature.FeatureUtils.OR;

/**
 * List of {@link Feature} for the Live TV App.
 *
 * <p>Remove the {@code Feature} once it is launched.
 */
public final class Features {
    private static final String TAG = "Features";
    private static final boolean DEBUG = false;

    /**
     * UI for opting in to analytics.
     *
     * <p>Do not turn this on until the splash screen asking existing users to opt-in is launched.
     * See <a href="http://b/20228119">b/20228119</a>
     */
    public static final Feature ANALYTICS_OPT_IN = ENG_ONLY_FEATURE;

    /**
     * Analytics that include sensitive information such as channel or program identifiers.
     *
     * <p>See <a href="http://b/22062676">b/22062676</a>
     */
    public static final Feature ANALYTICS_V2 = AND(ON, ANALYTICS_OPT_IN);

    public static final Feature EPG_SEARCH =
            new PropertyFeature("feature_tv_use_epg_search", false);

    public static final Feature TUNER =
            new Feature() {
                @Override
                public boolean isEnabled(Context context) {

                    if (Utils.isDeveloper()) {
                        // we enable tuner for developers to test tuner in any platform.
                        return true;
                    }

                    // This is special handling just for USB Tuner.
                    // It does not require any N API's but relies on a improvements in N for AC3 support
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
                }
            };

    /**
     * Use network tuner if it is available and there is no other tuner types.
     */
    public static final Feature NETWORK_TUNER =
            new Feature() {
                @Override
                public boolean isEnabled(Context context) {
                    if (!TUNER.isEnabled(context)) {
                        return false;
                    }
                    if (Utils.isDeveloper()) {
                        // Network tuner will be enabled for developers.
                        return true;
                    }
                    return Locale.US.getCountry().equalsIgnoreCase(
                            LocationUtils.getCurrentCountry(context));
                }
            };

    private static final String GSERVICE_KEY_UNHIDE = "live_channels_unhide";
    /**
     * A flag which indicates that LC app is unhidden even when there is no input.
     */
    public static final Feature UNHIDE =
            OR(new GServiceFeature(GSERVICE_KEY_UNHIDE, false), new Feature() {
                @Override
                public boolean isEnabled(Context context) {
                    // If LC app runs as non-system app, we unhide the app.
                    return !PermissionUtils.hasAccessAllEpg(context);
                }
            });

    public static final Feature PICTURE_IN_PICTURE =
            new Feature() {
                private Boolean mEnabled;

                @Override
                public boolean isEnabled(Context context) {
                    if (mEnabled == null) {
                        mEnabled =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                        && context.getPackageManager()
                                                .hasSystemFeature(
                                                        PackageManager.FEATURE_PICTURE_IN_PICTURE);
                    }
                    return mEnabled;
                }
            };

    /** Use AC3 software decode. */
    public static final Feature AC3_SOFTWARE_DECODE =
            new Feature() {
                private final String[] SUPPORTED_REGIONS = {};

                private Boolean mEnabled;

                @Override
                public boolean isEnabled(Context context) {
                    if (mEnabled == null) {
                        if (mEnabled == null) {
                            // We will not cache the result of fallback solution.
                            String country = LocationUtils.getCurrentCountry(context);
                            for (int i = 0; i < SUPPORTED_REGIONS.length; ++i) {
                                if (SUPPORTED_REGIONS[i].equalsIgnoreCase(country)) {
                                    return true;
                                }
                            }
                            if (DEBUG) Log.d(TAG, "AC3 flag false after country check");
                            return false;
                        }
                    }
                    if (DEBUG) Log.d(TAG, "AC3 flag " + mEnabled);
                    return mEnabled;
                }
            };

    /** Show postal code fragment before channel scan. */
    public static final Feature ENABLE_CLOUD_EPG_REGION =
            new Feature() {
                private final String[] SUPPORTED_REGIONS = {
                };


                @Override
                public boolean isEnabled(Context context) {
                    if (!Experiments.CLOUD_EPG.get()) {
                        if (DEBUG) Log.d(TAG, "Experiments.CLOUD_EPG is false");
                        return false;
                    }
                    String country = LocationUtils.getCurrentCountry(context);
                    for (int i = 0; i < SUPPORTED_REGIONS.length; i++) {
                        if (SUPPORTED_REGIONS[i].equalsIgnoreCase(country)) {
                            return true;
                        }
                    }
                    if (DEBUG) Log.d(TAG, "EPG flag false after country check");
                    return false;
                }
            };

    /** Enable a conflict dialog between currently watched channel and upcoming recording. */
    public static final Feature SHOW_UPCOMING_CONFLICT_DIALOG = OFF;

    /**
     * Use input blacklist to disable partner's tuner input.
     */
    public static final Feature USE_PARTNER_INPUT_BLACKLIST = ON;

    /**
     * Enable Dvb parsers and listeners.
     */
    public static final Feature ENABLE_FILE_DVB = OFF;

    @VisibleForTesting
    public static final Feature TEST_FEATURE = new PropertyFeature("test_feature", false);

    private Features() {
    }
}
