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

#define LOG_TAG "ReportPerformance"

#include <fstream>
#include <iostream>
#include <queue>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <utility>
#include <media/nbaio/NBLog.h>
#include <media/nbaio/PerformanceAnalysis.h>
#include <media/nbaio/ReportPerformance.h>
// #include <utils/CallStack.h> // used to print callstack
#include <utils/Log.h>
#include <utils/String8.h>

namespace android {

namespace ReportPerformance {

// Writes outlier intervals, timestamps, and histograms spanning long time intervals to a file.
// TODO: format the data efficiently and write different types of data to different files
void writeToFile(std::deque<std::pair<outlierInterval, timestamp>> &outlierData,
                                    std::deque<std::pair<timestamp, Histogram>> &hists,
                                    const char * kName,
                                    bool append) {
    ALOGD("writing performance data to file");
    if (outlierData.empty() || hists.empty()) {
        return;
    }

    std::ofstream ofs;
    ofs.open(kName, append ? std::ios::app : std::ios::trunc);
    if (!ofs.is_open()) {
        ALOGW("couldn't open file %s", kName);
        return;
    }
    ofs << "Outlier data: interval and timestamp\n";
    for (const auto &outlier : outlierData) {
        ofs << outlier.first << ": " << outlier.second << "\n";
    }
    ofs << "Histogram data\n";
    for (const auto &hist : hists) {
        ofs << "\ttimestamp\n";
        ofs << hist.first << "\n";
        ofs << "\tbuckets and counts\n";
        for (const auto &bucket : hist.second) {
            ofs << bucket.first << ": " << bucket.second << "\n";
        }
    }
    ofs.close();
}

} // namespace ReportPerformance

}   // namespace android
