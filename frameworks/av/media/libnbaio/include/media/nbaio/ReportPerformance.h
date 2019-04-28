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

#ifndef ANDROID_MEDIA_REPORTPERFORMANCE_H
#define ANDROID_MEDIA_REPORTPERFORMANCE_H

#include <deque>
#include <map>
#include <vector>

namespace android {

// This class is used by reportPerformance function
// TODO move reportPerformance function to ReportPerformance.cpp
class String8;

namespace ReportPerformance {

// stores a histogram: key: observed buffer period. value: count
// TODO: unsigned, unsigned
using Histogram = std::map<int, int>;

using outlierInterval = uint64_t;
// int64_t timestamps are converted to uint64_t in PerformanceAnalysis::storeOutlierData,
// and all analysis functions use uint64_t.
using timestamp = uint64_t;
using timestamp_raw = int64_t;

// FIXME: decide whether to use 64 or 32 bits
// TODO: the code has a mix of typedef and using. Standardize to one or the other.
typedef uint64_t log_hash_t;

static inline int deltaMs(int64_t ns1, int64_t ns2) {
    return (ns2 - ns1) / (1000 * 1000);
}

static inline uint32_t log2(uint32_t x) {
    // This works for x > 0
    return 31 - __builtin_clz(x);
}

// Writes outlier intervals, timestamps, and histograms spanning long time
// intervals to a file.
void writeToFile(std::deque<std::pair<outlierInterval, timestamp>> &outlierData,
                 std::deque<std::pair<timestamp, Histogram>> &hists,
                 const char * kName,
                 bool append);

} // namespace ReportPerformance

}   // namespace android

#endif  // ANDROID_MEDIA_REPORTPERFORMANCE_H
