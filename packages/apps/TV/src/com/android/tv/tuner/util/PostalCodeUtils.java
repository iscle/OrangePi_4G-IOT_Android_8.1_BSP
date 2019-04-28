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

package com.android.tv.tuner.util;

import android.content.Context;
import android.location.Address;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.util.LocationUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A utility class to update, get, and set the last known postal or zip code.
 */
public class PostalCodeUtils {
    private static final String TAG = "PostalCodeUtils";

    // Postcode formats, where A signifies a letter and 9 a digit:
    // US zip code format: 99999
    private static final String POSTCODE_REGEX_US = "^(\\d{5})";
    // UK postcode district formats: A9, A99, AA9, AA99
    // Full UK postcode format: Postcode District + space + 9AA
    // Should be able to handle both postcode district and full postcode
    private static final String POSTCODE_REGEX_GB =
            "^([A-Z][A-Z]?[0-9][0-9A-Z]?)( ?[0-9][A-Z]{2})?$";
    private static final String POSTCODE_REGEX_GB_GIR = "^GIR( ?0AA)?$"; // special UK postcode

    private static final Map<String, Pattern> REGION_PATTERN = new HashMap<>();
    private static final Map<String, Integer> REGION_MAX_LENGTH = new HashMap<>();

    static {
        REGION_PATTERN.put(Locale.US.getCountry(), Pattern.compile(POSTCODE_REGEX_US));
        REGION_PATTERN.put(
                Locale.UK.getCountry(),
                Pattern.compile(POSTCODE_REGEX_GB + "|" + POSTCODE_REGEX_GB_GIR));
        REGION_MAX_LENGTH.put(Locale.US.getCountry(), 5);
        REGION_MAX_LENGTH.put(Locale.UK.getCountry(), 8);
    }

    // The longest postcode number is 10-character-long.
    // Use a larger number to accommodate future changes.
    private static final int DEFAULT_MAX_LENGTH = 16;

    /** Returns {@code true} if postal code has been changed */
    public static boolean updatePostalCode(Context context)
            throws IOException, SecurityException, NoPostalCodeException {
        String postalCode = getPostalCode(context);
        String lastPostalCode = getLastPostalCode(context);
        if (TextUtils.isEmpty(postalCode)) {
            if (TextUtils.isEmpty(lastPostalCode)) {
                throw new NoPostalCodeException();
            }
        } else if (!TextUtils.equals(postalCode, lastPostalCode)) {
            setLastPostalCode(context, postalCode);
            return true;
        }
        return false;
    }

    /**
     * Gets the last stored postal or zip code, which might be decided by {@link LocationUtils} or
     * input by users.
     */
    public static String getLastPostalCode(Context context) {
        return TunerPreferences.getLastPostalCode(context);
    }

    /**
     * Sets the last stored postal or zip code. This method will overwrite the value written by
     * calling {@link #updatePostalCode(Context)}.
     */
    public static void setLastPostalCode(Context context, String postalCode) {
        Log.i(TAG, "Set Postal Code:" + postalCode);
        TunerPreferences.setLastPostalCode(context, postalCode);
    }

    @Nullable
    private static String getPostalCode(Context context) throws IOException, SecurityException {
        Address address = LocationUtils.getCurrentAddress(context);
        if (address != null) {
            Log.i(TAG, "Current country and postal code is " + address.getCountryName() + ", "
                    + address.getPostalCode());
            return address.getPostalCode();
        }
        return null;
    }

    /** An {@link java.lang.Exception} class to notify no valid postal or zip code is available. */
    public static class NoPostalCodeException extends Exception {
        public NoPostalCodeException() {
        }
    }

    /**
     * Checks whether a postcode matches the format of the specific region.
     *
     * @return {@code false} if the region is supported and the postcode doesn't match; {@code true}
     *     otherwise
     */
    public static boolean matches(@NonNull CharSequence postcode, @NonNull String region) {
        Pattern pattern = REGION_PATTERN.get(region.toUpperCase());
        return pattern == null || pattern.matcher(postcode).matches();
    }

    /**
     * Gets the largest possible postcode length in the region.
     *
     * @return maximum postcode length if the region is supported; {@link #DEFAULT_MAX_LENGTH}
     *     otherwise
     */
    public static int getRegionMaxLength(Context context) {
        Integer maxLength =
                REGION_MAX_LENGTH.get(LocationUtils.getCurrentCountry(context).toUpperCase());
        return maxLength == null ? DEFAULT_MAX_LENGTH : maxLength;
    }
}