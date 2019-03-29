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

#include <algos/ap_hub_sync.h>
#include <cpu/cpuMath.h>

#include <limits.h>
#include <seos.h>

#define S_IN_NS(s)          (UINT64_C(1000000000)*(s))

#define SYNC_EXPIRATION     S_IN_NS(50) //50 sec in ns, at max 500us diff
#define SYNC_WINDOW_TIMEOUT S_IN_NS(2)  //2 sec in ns
#define SYNC_FILTER_B       8
#define SYNC_FILTER_A       1

#define DEBUG_SYNC          false

enum ApHubSyncState {
    NOT_INITED = 0,
    USE_MAX,
    USE_FILTERED
};

void apHubSyncReset(struct ApHubSync* sync) {
    sync->state = 0;
    if (DEBUG_SYNC) {
        osLog(LOG_DEBUG, "ApHub sync reset");
    }
}

void apHubSyncAddDelta(struct ApHubSync* sync, uint64_t apTime, uint64_t hubTime) {

    int64_t delta = apTime - hubTime;

    // if data is expired or lastTs is not set before, reset
    if (apTime > sync->lastTs + SYNC_EXPIRATION || sync->lastTs == 0) {
        apHubSyncReset(sync);
    }

    sync->lastTs = apTime;

    if (sync->state == NOT_INITED) {
        // setup the windowMax before switching state
        sync->windowMax = delta;
        sync->windowTimeout = apTime + SYNC_WINDOW_TIMEOUT;

        sync->state = USE_MAX;
    } else {
        sync->windowMax = (delta > sync->windowMax) ? delta : sync->windowMax;
        if (apTime > sync->windowTimeout) {
            // collected a window

            // setup deltaEstimation before switching state
            if (sync->state == USE_MAX) {
                sync->deltaEstimation = sync->windowMax;
            } else {
                sync->deltaEstimation = ((SYNC_FILTER_B - SYNC_FILTER_A) * sync->deltaEstimation +
                                   SYNC_FILTER_A * sync->windowMax) / SYNC_FILTER_B;
            }
            sync->state = USE_FILTERED;
            if (DEBUG_SYNC) {
                osLog(LOG_DEBUG, "ApHub new sync offset = %" PRId64, sync->deltaEstimation);
            }
            // start new window by resetting windowMax and windowTimeout after this window is done
            sync->windowMax = INT64_MIN;
            sync->windowTimeout = apTime + SYNC_WINDOW_TIMEOUT;
        }
    }
}

int64_t apHubSyncGetDelta(struct ApHubSync* sync, uint64_t hubTime) {
    int64_t ret;
    switch (sync->state) {
        case NOT_INITED:
            ret = 0;
            break;
        case USE_MAX:
            ret = sync->windowMax;
            break;
        case USE_FILTERED:
            ret = sync->deltaEstimation;
            break;
        default:
            // indicate error, should never happen
            ret = INT64_MIN;
            osLog(LOG_WARN, "ApHub sync: Invalid sync state %d", sync->state);
            apHubSyncReset(sync);
    }
    return ret;
}


