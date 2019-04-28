/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio.demo;

import android.hardware.radio.RadioManager;
import com.android.car.radio.service.RadioRds;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static lists of the mock radio stations for AM and FM bands.
 */
public class DemoRadioStations {
    private static final List<RadioStation> mFmStations = Arrays.asList(
        new RadioStation(94900, 0, RadioManager.BAND_FM,
                new RadioRds("Wild 94.9", "Drake ft. Rihanna", "Too Good")),
        new RadioStation(96500, 0, RadioManager.BAND_FM,
                new RadioRds("KOIT", "Celine Dion", "All By Myself")),
        new RadioStation(97300, 0, RadioManager.BAND_FM,
                new RadioRds("Alice@97.3", "Drops of Jupiter", "Train")),
        new RadioStation(99700, 0, RadioManager.BAND_FM,
                new RadioRds("99.7 Now!", "The Chainsmokers", "Closer")),
        new RadioStation(101300, 0, RadioManager.BAND_FM,
                new RadioRds("101-3 KISS-FM", "Justin Timberlake", "Rock Your Body")),
        new RadioStation(103700, 0, RadioManager.BAND_FM,
                new RadioRds("iHeart80s @ 103.7", "Michael Jackson", "Billie Jean")),
        new RadioStation(106100, 0, RadioManager.BAND_FM,
                new RadioRds("106 KMEL", "Drake", "Marvins Room")));

    private static final List<RadioStation> mAmStations = Arrays.asList(
        new RadioStation(530, 0, RadioManager.BAND_AM, null),
        new RadioStation(610, 0, RadioManager.BAND_AM, null),
        new RadioStation(730, 0, RadioManager.BAND_AM, null),
        new RadioStation(801, 0, RadioManager.BAND_AM, null),
        new RadioStation(930, 0, RadioManager.BAND_AM, null),
        new RadioStation(1100, 0, RadioManager.BAND_AM, null),
        new RadioStation(1480, 0, RadioManager.BAND_AM, null),
        new RadioStation(1530, 0, RadioManager.BAND_AM, null));

    /**
     * Returns a list of {@link RadioStation}s that represent all the FM channels.
     */
    public static List<RadioStation> getFmStations() {
        // Create a new list so that the user of the list can modify the contents, but the main
        // list will remain unchanged in this class.
        return new ArrayList<>(mFmStations);
    }

    /**
     * Returns a list of {@link RadioStation}s that represent all the AM channels.
     */
    public static List<RadioStation> getAmStations() {
        // Create a new list so that the user of the list can modify the contents, but the main
        // list will remain unchanged in this class.
        return new ArrayList<>(mAmStations);
    }
}
