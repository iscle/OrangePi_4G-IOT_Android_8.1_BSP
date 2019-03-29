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

package com.android.compatibility.common.util;

import java.util.Arrays;

/**
 * Utilities for doing statistics
 */
public class Stat {
    /**
     * Private constructor for static class.
     */
    private Stat() {}

    /**
     * Collection of statistical propertirs like average, max, min, and stddev
     */
    public static class StatResult {
        public double mAverage;
        public double mMin;
        public double mMax;
        public double mStddev;
        public int mDataCount;
        public StatResult(double average, double min, double max, double stddev, int dataCount) {
            mAverage = average;
            mMin = min;
            mMax = max;
            mStddev = stddev;
            mDataCount = dataCount;
        }
    }

    /**
     * Calculate statistics properties likes average, min, max, and stddev for the given array
     */
    public static StatResult getStat(double[] data) {
        double average = data[0];
        double min = data[0];
        double max = data[0];
        for (int i = 1; i < data.length; i++) {
            average += data[i];
            if (data[i] > max) {
                max = data[i];
            }
            if (data[i] < min) {
                min = data[i];
            }
        }
        average /= data.length;
        double sumOfSquares = 0.0;
        for (int i = 0; i < data.length; i++) {
            double diff = average - data[i];
            sumOfSquares += diff * diff;
        }
        double variance = sumOfSquares / (data.length - 1);
        double stddev = Math.sqrt(variance);
        return new StatResult(average, min, max, stddev, data.length);
    }

    /**
     * Calculate statistics properties likes average, min, max, and stddev for the given array
     * while rejecting outlier +/- median * rejectionThreshold.
     * rejectionThreshold should be bigger than 0.0 and be lowerthan 1.0
     */
    public static StatResult getStatWithOutlierRejection(double[] data, double rejectionThreshold) {
        double[] dataCopied = Arrays.copyOf(data, data.length);
        Arrays.sort(dataCopied);
        int medianIndex = dataCopied.length / 2;
        double median;
        if (dataCopied.length % 2 == 1) {
            median = dataCopied[medianIndex];
        } else {
            median = (dataCopied[medianIndex - 1] + dataCopied[medianIndex]) / 2.0;
        }
        double thresholdMin = median * (1.0 - rejectionThreshold);
        double thresholdMax = median * (1.0 + rejectionThreshold);

        double[] validData = new double[data.length];
        int index = 0;
        for (int i = 0; i < data.length; i++) {
            if ((data[i] > thresholdMin) && (data[i] < thresholdMax)) {
                validData[index] = data[i];
                index++;
            }
            // TODO report rejected data
        }
        return getStat(Arrays.copyOf(validData, index));
    }

    /**
     * return the average value of the passed array
     */
    public static double getAverage(double[] data) {
        double sum = data[0];
        for (int i = 1; i < data.length; i++) {
            sum += data[i];
        }
        return sum / data.length;
    }

    /**
     * return the minimum value of the passed array
     */
    public static double getMin(double[] data) {
        double min = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] < min) {
                min = data[i];
            }
        }
        return min;
    }

    /**
     * return the maximum value of the passed array
     */
    public static double getMax(double[] data) {
        double max = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] > max) {
                max = data[i];
            }
        }
        return max;
    }

    /**
     * Calculate rate per sec for given change happened during given timeInMSec.
     * timeInSec with 0 value will be changed to small value to prevent divide by zero.
     * @param change total change of quality for the given duration timeInMSec.
     * @param timeInMSec
     */
    public static double calcRatePerSec(double change, double timeInMSec) {
        if (timeInMSec == 0) {
            return change * 1000.0 / 0.001; // do not allow zero
        } else {
            return change * 1000.0 / timeInMSec;
        }
    }

    /**
     * array version of calcRatePerSecArray
     */
    public static double[] calcRatePerSecArray(double change, double[] timeInMSec) {
        double[] result = new double[timeInMSec.length];
        change *= 1000.0;
        for (int i = 0; i < timeInMSec.length; i++) {
            if (timeInMSec[i] == 0) {
                result[i] = change / 0.001;
            } else {
                result[i] = change / timeInMSec[i];
            }
        }
        return result;
    }

    /**
     * Get the value of the 95th percentile using nearest rank algorithm.
     */
    public static double get95PercentileValue(double[] values) {
        Arrays.sort(values);
        // zero-based array index
        int index = (int) Math.round(values.length * 0.95 + .5) - 1;
        return values[index];
    }

}
