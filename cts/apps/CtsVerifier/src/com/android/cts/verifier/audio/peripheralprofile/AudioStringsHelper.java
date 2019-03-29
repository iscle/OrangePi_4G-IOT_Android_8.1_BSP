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

package com.android.cts.verifier.audio.peripheralprofile;

import android.support.annotation.NonNull;

public class AudioStringsHelper {
    // These correspond to encoding constants defined in AudioFormats.java
    static private final String formatStrings[] = {
            "0 Unknown Format",
            "1 Unknown Format",
            "2 ENCODING_PCM_16BIT",
            "3 ENCODING_PCM_8BIT",
            "4 ENCODING_PCM_FLOAT",
            "5 ENCODING_AC3",
            "6 ENCODING_E_AC3" };

    // These too.
    static private final String shortFormatStrings[] = {
            "??? [0]",
            "??? [1]",
            "PCM16",
            "PCM8",
            "PCMFLOAT",
            "AC3",
            "E_AC3" };

    static private String encodingToString(int fmt) {
        return fmt < formatStrings.length ? formatStrings[fmt] : ("" + fmt + "  Unknown Format ");
    }

    static private String encodingToShortString(int fmt) {
        return fmt < shortFormatStrings.length
            ? shortFormatStrings[fmt]
            : ("" + fmt + "  Unknown Format ");
    }

    static private String getRateString(int rate) {
        if (rate % 1000 == 0) {
            return "" + rate/1000 + "K";
        } else {
            return "" + (float)rate/1000 + "K";
        }
    }

    @NonNull
    static public String buildRatesStr(int[] rates) {
        StringBuilder builder = new StringBuilder();
        for(int rateIndex = 0; rateIndex < rates.length; rateIndex++) {
            builder.append(getRateString(rates[rateIndex]));
            if (rateIndex < rates.length - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    @NonNull
    static public String buildEncodingsStr(int encodings[]) {
        StringBuilder builder = new StringBuilder();
        for(int encodingIndex = 0; encodingIndex < encodings.length; encodingIndex++) {
            builder.append(encodingToShortString(encodings[encodingIndex]));
            if (encodingIndex < encodings.length - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    @NonNull
    static public String buildChannelCountsStr(int counts[]) {
        return makeIntList(counts);
    }

    @NonNull
    public static String makeRatesList(int[] values) {
        return makeIntList(values);
    }

    @NonNull
    public static String makeIntList(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            sb.append(values[index]);
            if (index < values.length - 1) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    @NonNull
    public static String makeHexList(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            sb.append("0x" + Integer.toHexString(values[index]).toUpperCase());
            if (index < values.length - 1) {
                sb.append(",");
            }
        }

        return sb.toString();
    }
}
