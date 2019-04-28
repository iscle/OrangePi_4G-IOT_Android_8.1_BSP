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

package com.android.car.radio;

import android.content.Context;
import android.hardware.radio.RadioManager;
import com.android.car.radio.service.RadioStation;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Common formatters for displaying channel numbers for various radio bands.
 */
public final class RadioChannelFormatter {
    private static final String FM_CHANNEL_FORMAT = "###.#";
    private static final String AM_CHANNEL_FORMAT = "####";

    private RadioChannelFormatter() {}

    /**
     * The formatter for AM radio stations.
     */
    public static final DecimalFormat FM_FORMATTER = new DecimalFormat(FM_CHANNEL_FORMAT);

    /**
     * The formatter for FM radio stations.
     */
    public static final DecimalFormat AM_FORMATTER = new DecimalFormat(AM_CHANNEL_FORMAT);

    /**
     * Convenience method to format a given {@link RadioStation} based on the value in
     * {@link RadioStation#getRadioBand()}. If the band is invalid or support for its formatting is
     * not available, then an empty String is returned.
     *
     * @param band One of the band values specified in {@link RadioManager}. For example,
     *             {@link RadioManager#BAND_FM}.
     * @param channelNumber The channel number to format. This value should be in KHz.
     * @return A correctly formatted channel number or an empty string if one cannot be formed.
     */
    public static String formatRadioChannel(int band, int channelNumber) {
        switch (band) {
            case RadioManager.BAND_AM:
                return AM_FORMATTER.format(channelNumber);

            case RadioManager.BAND_FM:
                // FM channels are displayed in KHz, so divide by 1000.
                return FM_FORMATTER.format((float) channelNumber / 1000);

            // TODO: Handle formats for AM and FM HD stations.

            default:
                return "";
        }
    }

    /**
     * Formats the given band value into a readable String.
     *
     * @param band One of the band values specified in {@link RadioManager}. For example,
     *             {@link RadioManager#BAND_FM}.
     * @return The formatted string or an empty string if the band is invalid.
     */
    public static String formatRadioBand(Context context, int band) {
        String radioBandText;

        switch (band) {
            case RadioManager.BAND_AM:
                radioBandText = context.getString(R.string.radio_am_text);
                break;

            case RadioManager.BAND_FM:
                radioBandText = context.getString(R.string.radio_fm_text);
                break;

            // TODO: Handle formats for AM and FM HD stations.

            default:
                radioBandText = "";
        }

        return radioBandText.toUpperCase(Locale.getDefault());
    }
}
