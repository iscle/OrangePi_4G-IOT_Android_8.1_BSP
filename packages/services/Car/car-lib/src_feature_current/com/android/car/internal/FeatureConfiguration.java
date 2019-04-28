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
package com.android.car.internal;

/**
 * Class to hold static boolean flag for enabling / disabling features.
 *
 * @hide
 */
public class FeatureConfiguration {
    /** Disable future feature by default. */
    public static final boolean DEFAULT = false;
    /** product configuration in CarInfoManager */
    public static final boolean ENABLE_PRODUCT_CONFIGURATION_INFO = DEFAULT;
    public static final boolean ENABLE_VEHICLE_MAP_SERVICE = DEFAULT;
}
