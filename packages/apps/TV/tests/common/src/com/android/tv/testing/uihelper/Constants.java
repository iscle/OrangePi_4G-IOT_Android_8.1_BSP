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
package com.android.tv.testing.uihelper;

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;

public final class Constants {

    public static final double EXTRA_TIMEOUT_PERCENT = .05;
    public static final int MIN_EXTRA_TIMEOUT = 10;
    public static final long MAX_SHOW_DELAY_MILLIS = 200;
    public static final long MAX_FOCUSED_DELAY_MILLIS = 1000;
    public static final String TV_APP_PACKAGE = "com.android.tv";
    public static final BySelector TV_VIEW = By.res(TV_APP_PACKAGE, "main_tunable_tv_view");
    public static final BySelector CHANNEL_BANNER = By.res(TV_APP_PACKAGE, "channel_banner_view");
    public static final BySelector KEYPAD_CHANNEL_SWITCH = By.res(TV_APP_PACKAGE, "channel_number");
    public static final BySelector MENU = By.res(TV_APP_PACKAGE, "menu");
    public static final BySelector SIDE_PANEL = By.res(TV_APP_PACKAGE, "side_panel");
    public static final BySelector PROGRAM_GUIDE = By.res(TV_APP_PACKAGE, "program_guide");
    public static final BySelector DVR_LIBRARY = By.res(TV_APP_PACKAGE, "dvr_frame");
    public static final BySelector DVR_SCHEDULES = By.res(TV_APP_PACKAGE, "dvr_schedules");
    public static final BySelector FOCUSED_VIEW = By.focused(true);

    private Constants() {
    }
}
