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

package com.android.tv.license;

import android.content.res.AssetManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utilities for showing open source licenses.
 */
public final class LicenseUtils {
    public final static String RATING_SOURCE_FILE =
            "file:///android_asset/rating_sources.html";


    /**
     * Checks if the rating_attribution.html asset is include in the apk.
     */
    public static boolean hasRatingAttribution(AssetManager am) {
        try (InputStream is = am.open("rating_sources.html")) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private LicenseUtils() {
    }
}
