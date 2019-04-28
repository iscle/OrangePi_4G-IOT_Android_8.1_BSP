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

// Non-blocking event logger intended for safe communication between processes via shared memory

#ifndef ANDROID_MEDIA_PERFORMANCEANALYSIS_H
#define ANDROID_MEDIA_PERFORMANCEANALYSIS_H

#include <map>
#include <deque>
#include <vector>
#include "NBLog.h"
#include "ReportPerformance.h"

namespace android {

namespace ReportPerformance {

class PerformanceAnalysis {
    // This class stores and analyzes audio processing wakeup timestamps from NBLog
    // FIXME: currently, all performance data is stored in deques. Need to add a mutex.
    // FIXME: continue this way until analysis is done in a separate thread. Then, use
    // the fifo writer utilities.
public:

    PerformanceAnalysis();

    // Given a series of audio processing wakeup timestamps,
    // compresses and and analyzes the data, and flushes
    // the timestamp series from memory.
    void processAndFlushTimeStampSeries();

    // Called when an audio on/off event is read from the buffer,
    // e.g. EVENT_AUDIO_STATE.
    // calls flushTimeStampSeries on the data up to the event,
    // effectively discarding the idle audio time interval
    void handleStateChange();

    // When the short-term histogram array mRecentHists has reached capacity,
    // merges histograms for data compression and stores them in mLongTermHists
    void processAndFlushRecentHists();

    // Writes wakeup timestamp entry to log and runs analysis
    // TODO: make this thread safe. Each thread should have its own instance
    // of PerformanceAnalysis.
    void logTsEntry(timestamp_raw ts);

    // FIXME: make peakdetector and storeOutlierData a single function
    // Input: mOutlierData. Looks at time elapsed between outliers
    // finds significant changes in the distribution
    // writes timestamps of significant changes to mPeakTimestamps
    void detectPeaks();

    // runs analysis on timestamp series before it is converted to a histogram
    // finds outliers
    // writes to mOutlierData <time elapsed since previous outlier, outlier timestamp>
    void storeOutlierData(const std::vector<timestamp_raw> &timestamps);

    // input: series of short histograms. Generates a string of analysis of the buffer periods
    // TODO: WIP write more detailed analysis
    // FIXME: move this data visualization to a separate class. Model/view/controller
    void reportPerformance(String8 *body, int maxHeight = 10);

    // TODO: delete this. temp for testing
    void testFunction();

    // This function used to detect glitches in a time series
    // TODO incorporate this into the analysis (currently unused)
    void alertIfGlitch(const std::vector<timestamp_raw> &samples);

private:

    // stores outlier analysis: <elapsed time between outliers in ms, outlier timestamp>
    std::deque<std::pair<outlierInterval, timestamp>> mOutlierData;

    // stores each timestamp at which a peak was detected
    // a peak is a moment at which the average outlier interval changed significantly
    std::deque<timestamp> mPeakTimestamps;

    // TODO: turn these into circular buffers for better data flow
    // FIFO of small histograms
    // stores fixed-size short buffer period histograms with timestamp of first sample
    std::deque<std::pair<timestamp, Histogram>> mRecentHists;

    // FIFO of small histograms
    // stores fixed-size long-term buffer period histograms with timestamp of first sample
    std::deque<std::pair<timestamp, Histogram>> mLongTermHists;

    // vector of timestamps, collected from NBLog for a (TODO) specific thread
    // when a vector reaches its maximum size, the data is processed and flushed
    std::vector<timestamp_raw> mTimeStampSeries;

    static const int kMsPerSec = 1000;

    // Parameters used when detecting outliers
    // TODO: learn some of these from the data, delete unused ones
    // FIXME: decide whether to make kPeriodMs static.
    static const int kNumBuff = 3; // number of buffers considered in local history
    int kPeriodMs; // current period length is ideally 4 ms
    static const int kOutlierMs = 7; // values greater or equal to this cause glitches
    // DAC processing time for 4 ms buffer
    static constexpr double kRatio = 0.75; // estimate of CPU time as ratio of period length
    int kPeriodMsCPU; // compute based on kPeriodLen and kRatio

    // Peak detection: number of standard deviations from mean considered a significant change
    static const int kStddevThreshold = 5;

    // capacity allocated to data structures
    // TODO: adjust all of these values
    static const int kRecentHistsCapacity = 100; // number of short-term histograms stored in memory
    static const int kShortHistSize = 50; // number of samples in a short-term histogram
    static const int kOutlierSeriesSize = 100; // number of values stored in outlier array
    static const int kPeakSeriesSize = 100; // number of values stored in peak array
    static const int kLongTermHistsCapacity = 20; // number of long-term histogram stored in memory
    // maximum elapsed time between first and last timestamp of a long-term histogram
    static const int kMaxHistTimespanMs = 5 * kMsPerSec;

    // these variables are stored in-class to ensure continuity while analyzing the timestamp
    // series one short sequence at a time: the variables are not re-initialized every time.
    // FIXME: create inner class for these variables and decide which other ones to add to it
    double mPeakDetectorMean = -1;
    double mPeakDetectorSd = -1;
    // variables for storeOutlierData
    uint64_t mElapsed = 0;
    int64_t mPrevNs = -1;

};

} // namespace ReportPerformance

}   // namespace android

#endif  // ANDROID_MEDIA_PERFORMANCEANALYSIS_H
