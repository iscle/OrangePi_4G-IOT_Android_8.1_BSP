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
 * limitations under the License
 */
package com.android.providers.blockednumber;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.location.Country;
import android.location.CountryDetector;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import java.util.Locale;

public class Utils {
    private Utils() {
    }

    public static final int MIN_INDEX_LEN = 8;

    /**
     * @return The current country code.
     */
    public static @NonNull String getCurrentCountryIso(@NonNull Context context) {
        final CountryDetector detector = (CountryDetector) context.getSystemService(
                Context.COUNTRY_DETECTOR);
        if (detector != null) {
            final Country country = detector.detectCountry();
            if (country != null) {
                return country.getCountryIso();
            }
        }
        final Locale locale = context.getResources().getConfiguration().locale;
        return locale.getCountry();
    }

    /**
     * Converts a phone number to an E164 number, assuming the current country.  If {@code
     * incomingE16Number} is provided, it'll just strip it and returns.  If the number is not valid,
     * it'll return "".
     *
     * <p>Special case: if {@code rawNumber} contains '@', it's considered as an email address and
     * returned unmodified.
     */
    public static @NonNull String getE164Number(@NonNull Context context,
            @Nullable String rawNumber, @Nullable String incomingE16Number) {
        if (rawNumber != null && rawNumber.contains("@")) {
            return rawNumber;
        }
        if (!TextUtils.isEmpty(incomingE16Number)) {
            return incomingE16Number;
        }
        if (TextUtils.isEmpty(rawNumber)) {
            return "";
        }
        final String e164 =
                PhoneNumberUtils.formatNumberToE164(rawNumber, getCurrentCountryIso(context));
        return e164 == null ? "" : e164;
    }

    public static @Nullable String wrapSelectionWithParens(@Nullable String selection) {
        return TextUtils.isEmpty(selection) ? null : "(" + selection + ")";
    }
}
