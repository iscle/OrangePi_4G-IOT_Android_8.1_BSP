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


#ifndef ANDROID_METRICSSUMMARIZEREXTRACTOR_H
#define ANDROID_METRICSSUMMARIZEREXTRACTOR_H

#include <utils/threads.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include <utils/List.h>

#include <media/IMediaAnalyticsService.h>
#include "MetricsSummarizer.h"


namespace android {

class MetricsSummarizerExtractor : public MetricsSummarizer
{

 public:

    MetricsSummarizerExtractor(const char *key);
    virtual ~MetricsSummarizerExtractor() {};

};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_METRICSSUMMARIZEREXTRACTOR_H
