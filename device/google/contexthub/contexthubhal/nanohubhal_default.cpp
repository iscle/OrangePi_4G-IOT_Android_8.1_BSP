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

#define LOG_TAG "NanohubHAL"
#include <hardware/context_hub.h>
#include "nanohub_perdevice.h"
#include "nanohubhal.h"
#include <log/log.h>

namespace android {

namespace nanohub {

#define DEVICE "Default"
#define DEVICE_TAG (DEVICE[0])

static const connected_sensor_t mSensors[] = {
    {
        .sensor_id = ((int)DEVICE_TAG << 8) + 1,
        .physical_sensor = {
            .name = "i'll get to this later",
        },
    },
    {
        .sensor_id = ((int)DEVICE_TAG << 8) + 2,
        .physical_sensor = {
            .name = "i'll get to this later as well",
        },
    },
};

static const context_hub_t mHub = {
    .name = "Google System Nanohub on " DEVICE,
    .vendor = "Google/StMicro",
    .toolchain = "gcc-arm-none-eabi",
    .platform_version = 1,
    .toolchain_version = 0x04080000, //4.8
    .hub_id = 0,

    .peak_mips = 16,
    .stopped_power_draw_mw = 0.010 * 1.800,
    .sleep_power_draw_mw   = 0.080 * 1.800,
    .peak_power_draw_mw    = 3.000 * 1.800,

    .connected_sensors = mSensors,
    .num_connected_sensors = sizeof(mSensors) / sizeof(*mSensors),

    .max_supported_msg_len = MAX_RX_PACKET,
    .os_app_name = { .id = 0 },
};

const char *get_devnode_path(void)
{
    return "/dev/nanohub_comms";
}

const context_hub_t* get_hub_info(void)
{
    return &mHub;
}

}; // namespace nanohub

}; // namespace android
