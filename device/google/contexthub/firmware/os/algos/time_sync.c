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

#include <math.h>
#include <floatRt.h>
#include <algos/time_sync.h>

void time_sync_reset(time_sync_t *sync) {
    sync->n = 0;
    sync->i = 0;
    sync->estimate_valid = false;

    sync->hold_count = 0;
}

bool time_sync_init(time_sync_t *sync) {
    time_sync_reset(sync);

    return true;
}

void time_sync_truncate(time_sync_t *sync, size_t window_size) {
    size_t k, m;
    sync->n = (window_size < sync->n) ? window_size : sync->n;
    sync->estimate_valid = false;

    // oldest sample index (new time_base) after truncation
    size_t bidx = (sync->i >= sync->n) ? (sync->i - sync->n)
        : (sync->i + NUM_TIME_SYNC_DATAPOINTS - sync->n);

    // left circular-shift oldest sample to index 0
    for (k = 0; k < bidx; ++k) {
        uint64_t tmp1 = sync->time1[0];
        uint64_t tmp2 = sync->time2[0];

        for (m = 0; m < NUM_TIME_SYNC_DATAPOINTS - 1; ++m) {
            sync->time1[m] = sync->time1[m + 1];
            sync->time2[m] = sync->time2[m + 1];
        }
        sync->time1[NUM_TIME_SYNC_DATAPOINTS - 1] = tmp1;
        sync->time2[NUM_TIME_SYNC_DATAPOINTS - 1] = tmp2;
    }

    sync->i = (sync->n < NUM_TIME_SYNC_DATAPOINTS) ? sync->n : 0;
}

bool time_sync_add(time_sync_t *sync, uint64_t time1, uint64_t time2) {
    size_t i = sync->i;

    sync->time1[i] = time1;
    sync->time2[i] = time2;

    if (++i == NUM_TIME_SYNC_DATAPOINTS) {
        i = 0;
    }

    sync->i = i;

    size_t prev_n = sync->n;
    if (sync->n < NUM_TIME_SYNC_DATAPOINTS) {
        ++sync->n;
    }

    sync->estimate_valid = false;

    if (sync->hold_count > 0) {
        --sync->hold_count;
        time_sync_truncate(sync, prev_n);
    }

    return true;
}

bool time_sync_estimate_time1(time_sync_t *sync, uint64_t time2, uint64_t *time1)
{
    size_t j;

    if (sync->n < 2)
        return false;

    *time1 = 0;

    if (!sync->estimate_valid) {
        size_t n = sync->n;

        // Rewind to the oldest sample in the history.
        size_t i = sync->i;
        if (n < NUM_TIME_SYNC_DATAPOINTS) {
            if (i != n) {
                return false;
            }
            i = 0;
        }

        uint64_t time1_base = sync->time1[i];
        uint64_t time2_base = sync->time2[i];

        // Least-square linear regresison:
        // Compute alpha and beta so that time1 = alpha + beta * time2.
        // x = time2, y = time1
        float mean_x = 0.0f;
        float mean_y = 0.0f;
        float invN = 1.0f / n;
        size_t ii = i;
        for (j = 0; j < n; ++j) {
            mean_y += floatFromUint64(sync->time1[ii] - time1_base) * invN;
            mean_x += floatFromUint64(sync->time2[ii] - time2_base) * invN;

            if (++ii == NUM_TIME_SYNC_DATAPOINTS) {
                ii = 0;
            }
        }

        // Two-pass approach so that only values relative to mean are computed.
        // Typically, |y| and |x| are smaller than 8e8 in nsec.
        // So sum_x2 and sum_xy are smaller than 1e19.
        // That leaves plenty of room for blocking tasks.
        float sum_x2 = 0.0f, sum_xy = 0.0f;
        ii = i;
        for (j = 0; j < n; ++j) {
            float y = floatFromUint64(sync->time1[ii] - time1_base) - mean_y;
            float x = floatFromUint64(sync->time2[ii] - time2_base) - mean_x;

            sum_x2 += x * x;
            sum_xy += x * y;

            if (++ii == NUM_TIME_SYNC_DATAPOINTS) {
                ii = 0;
            }
        }

        float beta = sum_xy / sum_x2;
        float alpha = mean_y - beta * mean_x;

        sync->alpha = alpha;
        sync->beta = beta;
        sync->time1_base = time1_base;
        sync->time2_base = time2_base;

        sync->estimate_valid = true;
    }

    *time1 = sync->time1_base + floatToInt64(sync->alpha + sync->beta * floatFromInt64(time2 - sync->time2_base));

    return true;
}

void time_sync_hold(time_sync_t *sync, uint8_t count) {
    sync->hold_count = count;
}
