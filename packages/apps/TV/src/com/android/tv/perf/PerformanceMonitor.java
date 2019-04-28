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

package com.android.tv.perf;

import android.content.Context;

import static com.android.tv.perf.EventNames.EventName;

/** Measures Performance. */
public interface PerformanceMonitor {

    /**
     * Starts monitoring application's lifecylce for interesting memory events, captures and records
     * memory usage data whenever these events are fired.
     */
    void startMemoryMonitor();

    /**
     * Collects and records memory usage for a specific custom event
     *
     * @param eventName to record
     */
    void recordMemory(@EventName String eventName);

    /**
     * Starts a timer for a global event to allow measuring the event's latency across activities If
     * multiple events with the same name are started, only the last event is retained.
     *
     * @param eventName for which the timer starts
     */
    void startGlobalTimer(@EventName String eventName);

    /**
     * Stops a cross activities timer for a specific eventName and records the timer duration. If no
     * timer found for the event specified an error will be logged, and recording will be skipped.
     *
     * @param eventName for which the timer stops
     */
    void stopGlobalTimer(@EventName String eventName);

    /**
     * Starts a timer to record latency of a specific scenario or event. Use this method to track
     * latency in the same method/class
     *
     * @return TimerEvent object to be used for stopping/recording the timer for a specific event.
     *     If PerformanceMonitor is not initialized for any reason, an empty TimerEvent will be
     *     returned.
     */
    TimerEvent startTimer();


    /**
     * Stops timer for a specific event and records the timer duration. passing a null TimerEvent
     * will cause this operation to be skipped.
     *
     * @param event that needs to be stopped
     * @param eventName for which the timer stops. This must be constant with no PII.
     */
    void stopTimer(TimerEvent event, @EventName String eventName);

    /**
     * Starts recording jank for a specific scenario or event.
     *
     * <p>If jank recording was started already for an event with the current name, but was never
     * stopped, the previously recorded event will be skipped.
     *
     * @param eventName of the event for which tracking is started
     */
    void startJankRecorder(@EventName String eventName);

    /**
     * Stops recording jank for a specific event and records the jank event.
     *
     * @param eventName of the event that needs to be stopped
     */
    void stopJankRecorder(@EventName String eventName);

    /**
     * Starts activity to display PerformanceMonitor events recorded in local database for debug
     * purpose.
     *
     * @return true if the activity is available to start
     */
    boolean startPerformanceMonitorEventDebugActivity(Context context);
}
