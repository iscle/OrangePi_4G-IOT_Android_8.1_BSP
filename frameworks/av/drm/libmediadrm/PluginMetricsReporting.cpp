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

//#define LOG_NDEBUG 0
#define LOG_TAG "PluginMetricsReporting"
#include <utils/Log.h>

#include <media/PluginMetricsReporting.h>

#include <media/MediaAnalyticsItem.h>

#include "protos/plugin_metrics.pb.h"

namespace android {

namespace {

using android::drm_metrics::MetricsGroup;
using android::drm_metrics::MetricsGroup_Metric;
using android::drm_metrics::MetricsGroup_Metric_MetricValue;

const char* const kParentAttribute = "/parent/external";

status_t reportMetricsGroup(const MetricsGroup& metricsGroup,
                            const String8& batchName,
                            const int64_t* parentId) {
    MediaAnalyticsItem analyticsItem(batchName.c_str());
    analyticsItem.generateSessionID();
    int64_t sessionId = analyticsItem.getSessionID();
    if (parentId != NULL) {
        analyticsItem.setInt64(kParentAttribute, *parentId);
    }

    // Report the package name.
    if (metricsGroup.has_app_package_name()) {
      AString app_package_name(metricsGroup.app_package_name().c_str(),
                               metricsGroup.app_package_name().size());
      analyticsItem.setPkgName(app_package_name);
    }

    for (int i = 0; i < metricsGroup.metric_size(); ++i) {
        const MetricsGroup_Metric& metric = metricsGroup.metric(i);
        if (!metric.has_name()) {
            ALOGE("Metric with no name.");
            return BAD_VALUE;
        }

        if (!metric.has_value()) {
            ALOGE("Metric with no value.");
            return BAD_VALUE;
        }

        const MetricsGroup_Metric_MetricValue& value = metric.value();
        if (value.has_int_value()) {
            analyticsItem.setInt64(metric.name().c_str(),
                                   value.int_value());
        } else if (value.has_double_value()) {
            analyticsItem.setDouble(metric.name().c_str(),
                                    value.double_value());
        } else if (value.has_string_value()) {
            analyticsItem.setCString(metric.name().c_str(),
                                     value.string_value().c_str());
        } else {
            ALOGE("Metric Value with no actual value.");
            return BAD_VALUE;
        }
    }

    analyticsItem.setFinalized(true);
    if (!analyticsItem.selfrecord()) {
      // Note the cast to int is because we build on 32 and 64 bit.
      // The cast prevents a peculiar printf problem where one format cannot
      // satisfy both.
      ALOGE("selfrecord() returned false. sessioId %d", (int) sessionId);
    }

    for (int i = 0; i < metricsGroup.metric_sub_group_size(); ++i) {
        const MetricsGroup& subGroup = metricsGroup.metric_sub_group(i);
        status_t res = reportMetricsGroup(subGroup, batchName, &sessionId);
        if (res != OK) {
            return res;
        }
    }

    return OK;
}

String8 sanitize(const String8& input) {
    // Filters the input string down to just alphanumeric characters.
    String8 output;
    for (size_t i = 0; i < input.size(); ++i) {
        char candidate = input[i];
        if ((candidate >= 'a' && candidate <= 'z') ||
                (candidate >= 'A' && candidate <= 'Z') ||
                (candidate >= '0' && candidate <= '9')) {
            output.append(&candidate, 1);
        }
    }
    return output;
}

}  // namespace

status_t reportDrmPluginMetrics(const Vector<uint8_t>& serializedMetrics,
                                const String8& vendor,
                                const String8& description) {
    MetricsGroup root_metrics_group;
    if (!root_metrics_group.ParseFromArray(serializedMetrics.array(),
                                           serializedMetrics.size())) {
        ALOGE("Failure to parse.");
        return BAD_VALUE;
    }

    String8 name = String8::format("drm.vendor.%s.%s",
                                   sanitize(vendor).c_str(),
                                   sanitize(description).c_str());

    return reportMetricsGroup(root_metrics_group, name, NULL);
}

}  // namespace android
