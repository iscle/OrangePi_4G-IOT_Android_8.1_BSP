/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.mail.analytics;

import android.os.SystemClock;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Generic static singleton timer that keeps track of start time of various events. It  logs the
 * event's duration into Analytics using the provided naming information.
 * This timer class supports multiple data points per event ("lapping").
 *
 * This class also holds some defaults constant IDs that we log. This provides an easy way to check
 * what data we are logging as well as ensuring that the IDs are consistent when accessed by
 * different classes.
 */
public class AnalyticsTimer {
    public static final String OPEN_CONV_VIEW_FROM_LIST = "open_conv_from_list";
    public static final String COLD_START_LAUNCHER = "cold_start_to_list";
    public static final String SEARCH_TO_LIST = "search_to_list";
    public static final String COMPOSE_HTML_TO_SPAN = "compose_html_to_span";
    public static final String COMPOSE_SPAN_TO_HTML = "compose_span_to_html";

    private final Map<String, Long> mStartTimes = Maps.newConcurrentMap();

    // Static singleton class to ensure that you can access the timer from anywhere in the code
    private static final AnalyticsTimer mInstance = new AnalyticsTimer();

    private AnalyticsTimer() {}

    public static AnalyticsTimer getInstance() {
        return mInstance;
    }

    /**
     * Record the current time as the start time of the provided id. If the id has a previously
     * recorded start time, that time is overwritten.
     * @param id
     */
    public void trackStart(String id) {
        mStartTimes.put(id, SystemClock.uptimeMillis());
    }

    /**
     * Logs the duration of the event with the provided category, name, and label.
     * This method can be destructive, meaning that any additional calls without calling
     * {@link AnalyticsTimer#trackStart(String)} will do nothing
     * We allow the method to be destructive to prevent the following cases from happening:
     *   - recurring methods that call this with irrelevant mapped start times.
     *   - multiple entry ways to the method that calls this, thus misusing the
     *     start time.
     * With destructive read, we ensure that we only log the event that we care about and only once.
     * @param id id of the event
     * @param isDestructive if you are done with this tag (used for multiple data points per tag)
     * @param category category for analytics logging
     * @param name name for analytics logging
     * @param label label for analytics logging
     */
    public void logDuration(String id, boolean isDestructive, String category, String name,
            String label) {
        try {
            logDurationAndReturn(id, isDestructive, category, name, label);
        } catch (IllegalStateException e) { }
    }


    /**
     * Same as logDuration except with the logged time returned (or exception thrown)
     * @return logged time in millis
     * @throws java.lang.IllegalStateException
     */
    public long logDurationAndReturn(String id, boolean isDestructive, String category, String name,
            String label) throws IllegalStateException {
        final Long value = isDestructive ? mStartTimes.remove(id) : mStartTimes.get(id);
        if (value == null) {
            throw new IllegalStateException("Trying to log id that doesn't exist: " + id);
        }
        final long time = SystemClock.uptimeMillis() - value;
        Analytics.getInstance().sendTiming(category, time, name, label);
        return time;
    }

    /**
     * Removes a previously recorded start time of the provided id, if it exists.
     */
    public void stopTracking(String id) {
        mStartTimes.remove(id);
    }
}
