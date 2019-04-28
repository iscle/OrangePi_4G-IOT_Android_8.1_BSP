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

package com.android.server.telecom.settings;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.widget.Toast;
import java.util.Locale;

public final class BlockedNumbersUtil {
    private BlockedNumbersUtil() {}

    /**
     * @return locale and default to US if no locale was returned.
     */
    public static String getLocaleDefaultToUS() {
        String countryIso = Locale.getDefault().getCountry();
        if (countryIso == null || countryIso.length() != 2) {
            countryIso = "US";
        }
        return countryIso;
    }

    /**
     * Attempts to format the number, or returns the original number if it is not formattable. Also
     * wraps the returned number as LTR.
     */
    public static String formatNumber(String number){
      String formattedNumber = PhoneNumberUtils.formatNumber(number, getLocaleDefaultToUS());
      return BidiFormatter.getInstance().unicodeWrap(
              formattedNumber == null ? number : formattedNumber,
              TextDirectionHeuristics.LTR);
    }

    /**
     * Formats the number in the string and shows a toast for {@link Toast#LENGTH_SHORT}.
     *
     * <p>Adds the number in a TsSpan so that it reads as a phone number when talk back is on.
     */
    public static void showToastWithFormattedNumber(Context context, int stringId, String number) {
        String formattedNumber = formatNumber(number);
        String message = context.getString(stringId, formattedNumber);
        int startingPosition = message.indexOf(formattedNumber);
        Spannable messageSpannable = new SpannableString(message);
        PhoneNumberUtils.addTtsSpan(messageSpannable, startingPosition,
                startingPosition + formattedNumber.length());
        Toast.makeText(
                context,
                messageSpannable,
                Toast.LENGTH_SHORT).show();
    }
}
