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


#ifndef ANDROID_METRICSSUMMARIZER_H
#define ANDROID_METRICSSUMMARIZER_H

#include <utils/threads.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include <utils/List.h>

#include <media/IMediaAnalyticsService.h>


namespace android {

class MetricsSummarizer
{

 public:

    MetricsSummarizer(const char *key);
    virtual ~MetricsSummarizer();

    // show the key
    const char * getKey();

    // should the record be given to this summarizer
    bool isMine(MediaAnalyticsItem &item);

    // hand the record to this summarizer
    void handleRecord(MediaAnalyticsItem *item);

    virtual void mergeRecord(MediaAnalyticsItem &have, MediaAnalyticsItem &incoming);

    // dump the summarized records (for dumpsys)
    AString dumpSummary(int &slot);
    AString dumpSummary(int &slot, const char *only);

    void setIgnorables(const char **);
    const char **getIgnorables();

 protected:

    // various comparators
    // "do these records have same attributes and values in those attrs"
    bool sameAttributes(MediaAnalyticsItem *summ, MediaAnalyticsItem *single, const char **ignoreables);

    void minMaxVar64(MediaAnalyticsItem &summ, const char *key, int64_t value);

    static int PropSorter(const void *a, const void *b);
    void sortProps(MediaAnalyticsItem *item);

 private:
    const char *mKey;
    const char **mIgnorables;
    List<MediaAnalyticsItem *> *mSummaries;


};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_METRICSSUMMARIZER_H
