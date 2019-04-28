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
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import com.android.car.radio.service.RadioStation;

/**
 * A class that will take a {@link RadioStation} and return its corresponding color. The colors
 * for different channels can be found on go/aae-ncar-por in the radio section.
 */
public class RadioChannelColorMapper {
    // AM values range from 530 - 1700. The following two values represent where this range is cut
    // into thirds.
    private static final int AM_LOW_THIRD_RANGE = 920;
    private static final int AM_HIGH_THIRD_RANGE = 1210;

    // FM values range from 87.9 - 108.1 kHz. The following two values represent where this range
    // is cut into thirds.
    private static final int FM_LOW_THIRD_RANGE = 94600;
    private static final int FM_HIGH_THIRD_RANGE = 101300;

    @ColorInt private final int mDefaultColor;
    @ColorInt private final int mAmRange1Color;
    @ColorInt private final int mAmRange2Color;
    @ColorInt private final int mAmRange3Color;
    @ColorInt private final int mFmRange1Color;
    @ColorInt private final int mFmRange2Color;
    @ColorInt private final int mFmRange3Color;

    public static RadioChannelColorMapper getInstance(Context context) {
        return new RadioChannelColorMapper(context);
    }

    private RadioChannelColorMapper(Context context) {
        mDefaultColor = context.getColor(R.color.car_radio_bg_color);
        mAmRange1Color = context.getColor(R.color.am_range_1_bg_color);
        mAmRange2Color = context.getColor(R.color.am_range_2_bg_color);
        mAmRange3Color = context.getColor(R.color.am_range_3_bg_color);
        mFmRange1Color = context.getColor(R.color.fm_range_1_bg_color);
        mFmRange2Color = context.getColor(R.color.fm_range_2_bg_color);
        mFmRange3Color = context.getColor(R.color.fm_range_3_bg_color);
    }

    /**
     * Returns the default color for the radio.
     */
    @ColorInt
    public int getDefaultColor() {
        return mDefaultColor;
    }

    /**
     * Convenience method for returning a color based on a {@link RadioStation}.
     *
     * @see #getColorForStation(int, int)
     */
    @ColorInt
    public int getColorForStation(@NonNull RadioStation radioStation) {
        return getColorForStation(radioStation.getRadioBand(), radioStation.getChannelNumber());
    }

    /**
     * Returns the color that should be used for the given radio band and channel. If a match cannot
     * be made, then {@link #mDefaultColor} is returned.
     *
     * @param band One of {@link RadioManager}'s band values. (e.g. {@link RadioManager#BAND_AM}.
     * @param channel The channel frequency in Hertz.
     */
    @ColorInt
    public int getColorForStation(int band, int channel) {
        switch (band) {
            case RadioManager.BAND_AM:
                if (channel < AM_LOW_THIRD_RANGE) {
                    return mAmRange1Color;
                } else if (channel > AM_HIGH_THIRD_RANGE) {
                    return mAmRange3Color;
                }

                return mAmRange2Color;

            case RadioManager.BAND_FM:
                if (channel < FM_LOW_THIRD_RANGE) {
                    return mFmRange1Color;
                } else if (channel > FM_HIGH_THIRD_RANGE) {
                    return mFmRange3Color;
                }

                return mFmRange2Color;

            default:
                return mDefaultColor;
        }
    }
}
