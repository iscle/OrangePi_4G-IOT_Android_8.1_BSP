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

#ifndef TIME_H_

#define TIME_H_

#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define NUM_TIME_SYNC_DATAPOINTS    16

typedef struct {
    uint64_t time1[NUM_TIME_SYNC_DATAPOINTS];
    uint64_t time2[NUM_TIME_SYNC_DATAPOINTS];
    size_t n;
    size_t i;

    uint64_t time1_base;
    uint64_t time2_base;

    bool estimate_valid;
    float alpha, beta;

    uint8_t hold_count;

} time_sync_t;

void time_sync_reset(time_sync_t *sync);
bool time_sync_init(time_sync_t *sync);
void time_sync_truncate(time_sync_t *sync, size_t window_size);
bool time_sync_add(time_sync_t *sync, uint64_t time1, uint64_t time2);
bool time_sync_estimate_time1(time_sync_t *sync, uint64_t time2, uint64_t *time1);
void time_sync_hold(time_sync_t *sync, uint8_t count);

#ifdef __cplusplus
}
#endif

#endif  // TIME_H_
