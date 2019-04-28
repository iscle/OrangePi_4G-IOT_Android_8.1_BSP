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

package com.android.car;

public class CarLog {
    private static final int MAX_TAG_LEN = 23;

    public static final String TAG_AM = "CAR.AM";
    public static final String TAG_APP_FOCUS = "CAR.APP_FOCUS";
    public static final String TAG_AUDIO = "CAR.AUDIO";
    public static final String TAG_CABIN = "CAR.CABIN";
    public static final String TAG_CAMERA = "CAR.CAMERA";
    public static final String TAG_CAN_BUS = "CAR.CAN_BUS";
    public static final String TAG_CLUSTER = "CAR.CLUSTER";
    public static final String TAG_HAL = "CAR.HAL";
    public static final String TAG_HVAC = "CAR.HVAC";
    public static final String TAG_VENDOR_EXT = "CAR.VENDOR_EXT";
    public static final String TAG_INFO = "CAR.INFO";
    public static final String TAG_INPUT = "CAR.INPUT";
    public static final String TAG_MONITORING = "CAR.MONITORING";
    public static final String TAG_NAV = "CAR.NAV";
    public static final String TAG_PACKAGE = "CAR.PACKAGE";
    public static final String TAG_POWER = "CAR.POWER";
    public static final String TAG_PROJECTION = "CAR.PROJECTION";
    public static final String TAG_PROPERTY = "CAR.PROPERTY";
    public static final String TAG_RADIO = "CAR.RADIO";
    public static final String TAG_SENSOR = "CAR.SENSOR";
    public static final String TAG_SERVICE = "CAR.SERVICE";
    public static final String TAG_SYS = "CAR.SYS";
    public static final String TAG_TEST = "CAR.TEST";
    public static final String TAG_DIAGNOSTIC = "CAR.DIAGNOSTIC";

    public static String concatTag(String tagPrefix, Class clazz) {
        String tag = tagPrefix + "." + clazz.getSimpleName();
        if (tag.length() > MAX_TAG_LEN) {
            tag = tag.substring(0, MAX_TAG_LEN);
        }
        return tag;
    }
}
