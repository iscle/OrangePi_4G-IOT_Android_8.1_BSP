/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "GnssHAL_GnssBatchingInterface"

#include "GnssBatching.h"
#include <Gnss.h> // for wakelock consolidation
#include <GnssUtils.h>

#include <cutils/log.h>  // for ALOGE
#include <vector>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

sp<IGnssBatchingCallback> GnssBatching::sGnssBatchingCbIface = nullptr;
bool GnssBatching::sFlpSupportsBatching = false;

FlpCallbacks GnssBatching::sFlpCb = {
    .size = sizeof(FlpCallbacks),
    .location_cb = locationCb,
    .acquire_wakelock_cb = acquireWakelockCb,
    .release_wakelock_cb = releaseWakelockCb,
    .set_thread_event_cb = setThreadEventCb,
    .flp_capabilities_cb = flpCapabilitiesCb,
    .flp_status_cb = flpStatusCb,
};

GnssBatching::GnssBatching(const FlpLocationInterface* flpLocationIface) :
    mFlpLocationIface(flpLocationIface) {
}

/*
 * This enum is used locally by various methods below. It is only used by the default
 * implementation and is not part of the GNSS interface.
 */
enum BatchingValues : uint16_t {
    // Numbers 0-3 were used in earlier implementations - using 4 to be distinct to the HAL
    FLP_GNSS_BATCHING_CLIENT_ID = 4,
    // Tech. mask of GNSS, and sensor aiding, for legacy HAL to fit with GnssBatching API
    FLP_TECH_MASK_GNSS_AND_SENSORS = FLP_TECH_MASK_GNSS | FLP_TECH_MASK_SENSORS,
    // Putting a cap to avoid possible memory issues.  Unlikely values this high are supported.
    MAX_LOCATIONS_PER_BATCH = 1000
};

void GnssBatching::locationCb(int32_t locationsCount, FlpLocation** locations) {
    if (sGnssBatchingCbIface == nullptr) {
        ALOGE("%s: GNSS Batching Callback Interface configured incorrectly", __func__);
        return;
    }

    if (locations == nullptr) {
        ALOGE("%s: Invalid locations from GNSS HAL", __func__);
        return;
    }

    if (locationsCount < 0) {
        ALOGE("%s: Negative location count: %d set to 0", __func__, locationsCount);
        locationsCount = 0;
    } else if (locationsCount > MAX_LOCATIONS_PER_BATCH) {
        ALOGW("%s: Unexpected high location count: %d set to %d", __func__, locationsCount,
                MAX_LOCATIONS_PER_BATCH);
        locationsCount = MAX_LOCATIONS_PER_BATCH;
    }

    /**
     * Note:
     * Some existing implementations may drop duplicate locations.  These could be expanded here
     * but as there's ambiguity between no-GPS-fix vs. dropped duplicates in that implementation,
     * and that's not specified by the fused_location.h, that isn't safe to do here.
     * Fortunately, this shouldn't be a major issue in cases where GNSS batching is typically
     * used (e.g. when user is likely in vehicle/bicycle.)
     */
    std::vector<android::hardware::gnss::V1_0::GnssLocation> gnssLocations;
    for (int iLocation = 0; iLocation < locationsCount; iLocation++) {
        if (locations[iLocation] == nullptr) {
            ALOGE("%s: Null location at slot: %d of %d, skipping", __func__, iLocation,
                    locationsCount);
            continue;
        }
        if ((locations[iLocation]->sources_used & ~FLP_TECH_MASK_GNSS_AND_SENSORS) != 0)
        {
            ALOGE("%s: Unrequested location type %d at slot: %d of %d, skipping", __func__,
                    locations[iLocation]->sources_used, iLocation, locationsCount);
            continue;
        }
        gnssLocations.push_back(convertToGnssLocation(locations[iLocation]));
    }

    auto ret = sGnssBatchingCbIface->gnssLocationBatchCb(gnssLocations);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssBatching::acquireWakelockCb() {
    Gnss::acquireWakelockFused();
}

void GnssBatching::releaseWakelockCb() {
    Gnss::releaseWakelockFused();
}

// this can just return success, because threads are now set up on demand in the jni layer
int32_t GnssBatching::setThreadEventCb(ThreadEvent /*event*/) {
    return FLP_RESULT_SUCCESS;
}

void GnssBatching::flpCapabilitiesCb(int32_t capabilities) {
    ALOGD("%s capabilities %d", __func__, capabilities);

    if (capabilities & CAPABILITY_GNSS) {
        // once callback is received and capabilities high enough, we know version is
        // high enough for flush()
        sFlpSupportsBatching = true;
    }
}

void GnssBatching::flpStatusCb(int32_t status) {
    ALOGD("%s (default implementation) not forwarding status: %d", __func__, status);
}

// Methods from ::android::hardware::gnss::V1_0::IGnssBatching follow.
Return<bool> GnssBatching::init(const sp<IGnssBatchingCallback>& callback) {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching is unavailable", __func__);
        return false;
    }

    sGnssBatchingCbIface = callback;

    return (mFlpLocationIface->init(&sFlpCb) == 0);
}

Return<uint16_t> GnssBatching::getBatchSize() {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching interface is unavailable", __func__);
        return 0;
    }

    return mFlpLocationIface->get_batch_size();
}

Return<bool> GnssBatching::start(const IGnssBatching::Options& options) {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching interface is unavailable", __func__);
        return false;
    }

    if (!sFlpSupportsBatching) {
        ALOGE("%s: Flp batching interface not supported, no capabilities callback received",
                __func__);
        return false;
    }

    FlpBatchOptions optionsHw;
    // Legacy code used 9999 mW for High accuracy, and 21 mW for balanced.
    // New GNSS API just expects reasonable GNSS chipset behavior - do something efficient
    // given the interval.  This 100 mW limit should be quite sufficient (esp. given legacy code
    // implementations may not even use this value.)
    optionsHw.max_power_allocation_mW = 100;
    optionsHw.sources_to_use = FLP_TECH_MASK_GNSS_AND_SENSORS;
    optionsHw.flags = 0;
    if (options.flags & Flag::WAKEUP_ON_FIFO_FULL) {
        optionsHw.flags |= FLP_BATCH_WAKEUP_ON_FIFO_FULL;
    }
    optionsHw.period_ns = options.periodNanos;
    optionsHw.smallest_displacement_meters = 0; // Zero offset - just use time interval

    return (mFlpLocationIface->start_batching(FLP_GNSS_BATCHING_CLIENT_ID, &optionsHw)
            == FLP_RESULT_SUCCESS);
}

Return<void> GnssBatching::flush() {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching interface is unavailable", __func__);
        return Void();
    }

    mFlpLocationIface->flush_batched_locations();

    return Void();
}

Return<bool> GnssBatching::stop() {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching interface is unavailable", __func__);
        return false;
    }

    return (mFlpLocationIface->stop_batching(FLP_GNSS_BATCHING_CLIENT_ID) == FLP_RESULT_SUCCESS);
}

Return<void> GnssBatching::cleanup() {
    if (mFlpLocationIface == nullptr) {
        ALOGE("%s: Flp batching interface is unavailable", __func__);
        return Void();
    }

    mFlpLocationIface->cleanup();

    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
