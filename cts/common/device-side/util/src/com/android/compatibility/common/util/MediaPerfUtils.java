/*
 * Copyright 2016 The Android Open Source Project
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
package com.android.compatibility.common.util;

import android.media.MediaFormat;
import android.util.Range;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.util.Arrays;
import android.util.Log;

public class MediaPerfUtils {
    private static final String TAG = "MediaPerfUtils";

    private static final int MOVING_AVERAGE_NUM_FRAMES = 10;
    private static final int MOVING_AVERAGE_WINDOW_MS = 1000;

    // allow a variance of 2x for measured frame rates (e.g. half of lower-limit to double of
    // upper-limit of the published values). Also allow an extra 10% margin. This also acts as
    // a limit for the size of the published rates (e.g. upper-limit / lower-limit <= tolerance).
    private static final double FRAMERATE_TOLERANCE = 2.0 * 1.1;

    /*
     *  ------------------ HELPER METHODS FOR ACHIEVABLE FRAME RATES ------------------
     */

    /** removes brackets from format to be included in JSON. */
    private static String formatForReport(MediaFormat format) {
        String asString = "" + format;
        return asString.substring(1, asString.length() - 1);
    }

    /**
     * Adds performance header info to |log| for |codecName|, |round|, |configFormat|, |inputFormat|
     * and |outputFormat|. Also appends same to |message| and returns the resulting base message
     * for logging purposes.
     */
    public static String addPerformanceHeadersToLog(
            DeviceReportLog log, String message, int round, String codecName,
            MediaFormat configFormat, MediaFormat inputFormat, MediaFormat outputFormat) {
        String mime = configFormat.getString(MediaFormat.KEY_MIME);
        int width = configFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = configFormat.getInteger(MediaFormat.KEY_HEIGHT);

        log.addValue("round", round, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("codec_name", codecName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("mime_type", mime, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("width", width, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("height", height, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("config_format", formatForReport(configFormat),
                ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("input_format", formatForReport(inputFormat),
                ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("output_format", formatForReport(outputFormat),
                ResultType.NEUTRAL, ResultUnit.NONE);

        message += " codec=" + codecName + " round=" + round + " configFormat=" + configFormat
                + " inputFormat=" + inputFormat + " outputFormat=" + outputFormat;

        Range<Double> reported =
            MediaUtils.getVideoCapabilities(codecName, mime)
                    .getAchievableFrameRatesFor(width, height);
        if (reported != null) {
            log.addValue("reported_low", reported.getLower(), ResultType.NEUTRAL, ResultUnit.FPS);
            log.addValue("reported_high", reported.getUpper(), ResultType.NEUTRAL, ResultUnit.FPS);
            message += " reported=" + reported.getLower() + "-" + reported.getUpper();
        }

        return message;
    }

    /**
     * Adds performance statistics based on the raw |stats| to |log|. Also prints the same into
     * logcat. Returns the "final fps" value.
     */
    public static double addPerformanceStatsToLog(
            DeviceReportLog log, MediaUtils.Stats durationsUsStats, String message) {

        MediaUtils.Stats frameAvgUsStats =
            durationsUsStats.movingAverage(MOVING_AVERAGE_NUM_FRAMES);
        log.addValue(
                "window_frames", MOVING_AVERAGE_NUM_FRAMES, ResultType.NEUTRAL, ResultUnit.COUNT);
        logPerformanceStats(log, frameAvgUsStats, "frame_avg_stats",
                message + " window=" + MOVING_AVERAGE_NUM_FRAMES);

        MediaUtils.Stats timeAvgUsStats =
            durationsUsStats.movingAverageOverSum(MOVING_AVERAGE_WINDOW_MS * 1000);
        log.addValue("window_time", MOVING_AVERAGE_WINDOW_MS, ResultType.NEUTRAL, ResultUnit.MS);
        double fps = logPerformanceStats(log, timeAvgUsStats, "time_avg_stats",
                message + " windowMs=" + MOVING_AVERAGE_WINDOW_MS);

        log.setSummary("fps", fps, ResultType.HIGHER_BETTER, ResultUnit.FPS);
        return fps;
    }

    /**
     * Adds performance statistics based on the processed |stats| to |log| using |prefix|.
     * Also prints the same into logcat using |message| as the base message. Returns the fps value
     * for |stats|. |prefix| must be lowercase alphanumeric underscored format.
     */
    private static double logPerformanceStats(
            DeviceReportLog log, MediaUtils.Stats statsUs, String prefix, String message) {
        final String[] labels = {
            "min", "p5", "p10", "p20", "p30", "p40", "p50", "p60", "p70", "p80", "p90", "p95", "max"
        };
        final double[] points = {
             0,     5,    10,    20,    30,    40,    50,    60,    70,    80,    90,    95,    100
        };

        int num = statsUs.getNum();
        long avg = Math.round(statsUs.getAverage());
        long stdev = Math.round(statsUs.getStdev());
        log.addValue(prefix + "_num", num, ResultType.NEUTRAL, ResultUnit.COUNT);
        log.addValue(prefix + "_avg", avg / 1000., ResultType.LOWER_BETTER, ResultUnit.MS);
        log.addValue(prefix + "_stdev", stdev / 1000., ResultType.LOWER_BETTER, ResultUnit.MS);
        message += " num=" + num + " avg=" + avg + " stdev=" + stdev;
        final double[] percentiles = statsUs.getPercentiles(points);
        for (int i = 0; i < labels.length; ++i) {
            long p = Math.round(percentiles[i]);
            message += " " + labels[i] + "=" + p;
            log.addValue(prefix + "_" + labels[i], p / 1000., ResultType.NEUTRAL, ResultUnit.MS);
        }

        // print result to logcat in case test aborts before logs are written
        Log.i(TAG, message);

        return 1e6 / percentiles[points.length - 2];
    }

    /** Verifies |measuredFps| against reported achievable rates. Returns null if at least
     *  one measurement falls within the margins of the reported range. Otherwise, returns
     *  an error message to display.*/
    public static String verifyAchievableFrameRates(
            String name, String mime, int w, int h, double... measuredFps) {
        Range<Double> reported =
            MediaUtils.getVideoCapabilities(name, mime).getAchievableFrameRatesFor(w, h);
        String kind = "achievable frame rates for " + name + " " + mime + " " + w + "x" + h;
        if (reported == null) {
            return "Failed to get " + kind;
        }
        double lowerBoundary1 = reported.getLower() / FRAMERATE_TOLERANCE;
        double upperBoundary1 = reported.getUpper() * FRAMERATE_TOLERANCE;
        double lowerBoundary2 = reported.getUpper() / Math.pow(FRAMERATE_TOLERANCE, 2);
        double upperBoundary2 = reported.getLower() * Math.pow(FRAMERATE_TOLERANCE, 2);
        Log.d(TAG, name + " " + mime + " " + w + "x" + h +
                " lowerBoundary1 " + lowerBoundary1 + " upperBoundary1 " + upperBoundary1 +
                " lowerBoundary2 " + lowerBoundary2 + " upperBoundary2 " + upperBoundary2 +
                " measured " + Arrays.toString(measuredFps));

        for (double measured : measuredFps) {
            if (measured >= lowerBoundary1 && measured <= upperBoundary1
                    && measured >= lowerBoundary2 && measured <= upperBoundary2) {
                return null;
            }
        }

        return "Expected " + kind + ": " + reported + ".\n"
                + "Measured frame rate: " + Arrays.toString(measuredFps) + ".\n";
    }
}
