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
 * limitations under the License
 */

package com.android.tv.perf;

import android.support.annotation.StringDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Constants for performance event names.
 *
 * <p>Only constants are used to insure no PII is sent.
 *
 */
public final class EventNames {

    @Retention(SOURCE)
    @StringDef({
        APPLICATION_ONCREATE,
        FETCH_EPG_TASK,
        MAIN_ACTIVITY_ONCREATE,
        MAIN_ACTIVITY_ONSTART,
        MAIN_ACTIVITY_ONRESUME,
        ON_DEVICE_SEARCH
    })
    public @interface EventName {}

    public static final String APPLICATION_ONCREATE = "Application.onCreate";
    public static final String FETCH_EPG_TASK = "FetchEpgTask";
    public static final String MAIN_ACTIVITY_ONCREATE = "MainActivity.onCreate";
    public static final String MAIN_ACTIVITY_ONSTART = "MainActivity.onStart";
    public static final String MAIN_ACTIVITY_ONRESUME = "MainActivity.onResume";
    /**
     * Event name for query running time of on-device search in
     * {@link com.android.tv.search.LocalSearchProvider}.
     */
    public static final String ON_DEVICE_SEARCH = "OnDeviceSearch";

    private EventNames() {}
}
