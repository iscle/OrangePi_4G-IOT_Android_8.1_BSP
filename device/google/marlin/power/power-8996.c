/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * *    * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#define LOG_NDEBUG 1

#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <stdlib.h>

#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#define LOG_TAG "QCOM PowerHAL"
#include <utils/Log.h>
#include <cutils/trace.h>
#include <hardware/hardware.h>
#include <hardware/power.h>

#include "utils.h"
#include "metadata-defs.h"
#include "hint-data.h"
#include "performance.h"
#include "power-common.h"

static int display_hint_sent;
int launch_handle = -1;
int launch_mode;

#ifdef EXTRA_POWERHAL_HINTS
static int process_cam_preview_hint(void *metadata)
{
    char governor[80];
    struct cam_preview_metadata_t cam_preview_metadata;

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return HINT_NONE;
    }

    /* Initialize encode metadata struct fields */
    memset(&cam_preview_metadata, 0, sizeof(struct cam_preview_metadata_t));
    cam_preview_metadata.state = -1;
    cam_preview_metadata.hint_id = CAM_PREVIEW_HINT_ID;

    if (metadata) {
        if (parse_cam_preview_metadata((char *)metadata, &cam_preview_metadata) ==
            -1) {
            ALOGE("Error occurred while parsing metadata.");
            return HINT_NONE;
        }
    } else {
        return HINT_NONE;
    }

    if (cam_preview_metadata.state == 1) {
        if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            /* sched and cpufreq params
             * above_hispeed_delay for LVT - 40ms
             * go hispeed load for LVT - 95
             * hispeed freq for LVT - 556 MHz
             * target load for LVT - 90
             * above hispeed delay for sLVT - 40ms
             * go hispeed load for sLVT - 95
             * hispeed freq for sLVT - 556 MHz
             * target load for sLVT - 90
             * bus DCVS set to V2 config:
             *  low power ceil mpbs - 2500
             *  low power io percent - 50
             */
            int resource_values[] = {0x41400000, 0x4, 0x41410000, 0x5F, 0x41414000, 0x22C,
                0x41420000, 0x5A, 0x41400100, 0x4, 0x41410100, 0x5F, 0x41414100, 0x22C,
                0x41420100, 0x5A, 0x41810000, 0x9C4, 0x41814000, 0x32};

            perform_hint_action(cam_preview_metadata.hint_id,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
            ALOGI("Cam Preview hint start");
            return HINT_HANDLED;
        } else if ((strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(SCHED_GOVERNOR))) {
            /*
             * lower bus BW to save power
             *   0x41810000: low power ceil mpbs = 2500
             *   0x41814000: low power io percent = 50
             */
            int resource_values[] = {0x41810000, 0x9C4, 0x41814000, 0x32};

            perform_hint_action(
                cam_preview_metadata.hint_id, resource_values,
                sizeof(resource_values) / sizeof(resource_values[0]));
            ALOGI("Cam Preview hint start");
            return HINT_HANDLED;
        }
    } else if (cam_preview_metadata.state == 0) {
        if (((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) ||
            ((strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(SCHED_GOVERNOR)))) {
            undo_hint_action(cam_preview_metadata.hint_id);
            ALOGI("Cam Preview hint stop");
            return HINT_HANDLED;
        }
    }
    return HINT_NONE;
}
#endif

static int process_boost(int boost_handle, int duration)
{
    char governor[80];
    int eas_launch_resources[] = {0x40804000, 0xFFF, 0x40804100, 0xFFF,
                                         0x40800000, 0xFFF, 0x40800100, 0xFFF,
                                         0x41800000, 140,   0x40400000, 0x1};
    int hmp_launch_resources[] = {0x40C00000, 0x1,   0x40804000, 0xFFF,
                                         0x40804100, 0xFFF, 0x40800000, 0xFFF,
                                         0x40800100, 0xFFF, 0x41800000, 140,
                                         0x40400000, 0x1};
    int* launch_resources;
    size_t launch_resources_size;

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");
        return -1;
    }
    if (strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) {
        launch_resources = eas_launch_resources;
        launch_resources_size = sizeof(eas_launch_resources) / sizeof(eas_launch_resources[0]);
    } else if (strncmp(governor, INTERACTIVE_GOVERNOR,
                       strlen(INTERACTIVE_GOVERNOR)) == 0) { /*HMP boost*/
        launch_resources = hmp_launch_resources;
        launch_resources_size = sizeof(hmp_launch_resources) / sizeof(hmp_launch_resources[0]);
    } else {
        ALOGE("Unsupported governor.");
        return -1;
    }
    boost_handle = interaction_with_handle(
        boost_handle, duration, launch_resources_size, launch_resources);
    return boost_handle;
}

static int process_video_encode_hint(void *metadata)
{
    char governor[80];
    static int boost_handle = -1;

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return HINT_NONE;
    }

    if (metadata) {
        int duration = 2000; // boosts 2s for starting encoding
        boost_handle = process_boost(boost_handle, duration);
        ALOGD("LAUNCH ENCODER-ON: %d MS", duration);
        if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            /* 1. cpufreq params
             *    -above_hispeed_delay for LVT - 40ms
             *    -go hispeed load for LVT - 95
             *    -hispeed freq for LVT - 556 MHz
             *    -target load for LVT - 90
             *    -above hispeed delay for sLVT - 40ms
             *    -go hispeed load for sLVT - 95
             *    -hispeed freq for sLVT - 806 MHz
             *    -target load for sLVT - 90
             * 2. bus DCVS set to V2 config:
             *    -low power ceil mpbs - 2500
             *    -low power io percent - 50
             * 3. hysteresis optimization
             *    -bus dcvs hysteresis tuning
             *    -sample_ms of 10 ms
             *    -disable ignore_hispeed_notif
             *    -sLVT hispeed freq to 806MHz
             */
            int resource_values[] = {
                0x41810000, 0x9C4, 0x41814000, 0x32, 0x4180C000, 0x0, 0x41820000, 0xA,
                0x41438100, 0x1,  0x41438000, 0x1 };

            perform_hint_action(DEFAULT_VIDEO_ENCODE_HINT_ID,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
            ALOGD("Video Encode hint start");
            return HINT_HANDLED;
        } else if ((strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(SCHED_GOVERNOR))) {

            /* 1. bus DCVS set to V2 config:
             *    0x41810000: low power ceil mpbs - 2500
             *    0x41814000: low power io percent - 50
             * 2. hysteresis optimization
             *    0x4180C000: bus dcvs hysteresis tuning
             *    0x41820000: sample_ms of 10 ms
             */
            int resource_values[] = {0x41810000, 0x9C4, 0x41814000, 0x32,
                                     0x4180C000, 0x0,   0x41820000, 0xA};

            perform_hint_action(DEFAULT_VIDEO_ENCODE_HINT_ID,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
            ALOGD("Video Encode hint start");
            return HINT_HANDLED;
        }
    } else {
        // boost handle is intentionally not released, release_request(boost_handle);
        if (((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) ||
            ((strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(SCHED_GOVERNOR)))) {
            undo_hint_action(DEFAULT_VIDEO_ENCODE_HINT_ID);
            ALOGD("Video Encode hint stop");
            return HINT_HANDLED;
        }
    }
    return HINT_NONE;
}

static int process_activity_launch_hint(void *data)
{
    // boost will timeout in 5s
    int duration = 5000;
    ATRACE_BEGIN("launch");
    if (sustained_performance_mode || vr_mode) {
        ATRACE_END();
        return HINT_HANDLED;
    }

    ALOGD("LAUNCH HINT: %s", data ? "ON" : "OFF");
    if (data && launch_mode == 0) {
        launch_handle = process_boost(launch_handle, duration);
        if (launch_handle > 0) {
            launch_mode = 1;
            ALOGD("Activity launch hint handled");
            ATRACE_INT("launch_lock", 1);
            ATRACE_END();
            return HINT_HANDLED;
        } else {
            ATRACE_END();
            return HINT_NONE;
        }
    } else if (data == NULL  && launch_mode == 1) {
        release_request(launch_handle);
        ATRACE_INT("launch_lock", 0);
        launch_mode = 0;
        ATRACE_END();
        return HINT_HANDLED;
    }
    ATRACE_END();
    return HINT_NONE;
}

int power_hint_override(power_hint_t hint, void *data)
{
    int ret_val = HINT_NONE;
    switch(hint) {
#ifdef EXTRA_POWERHAL_HINTS
        case POWER_HINT_CAM_PREVIEW:
            ret_val = process_cam_preview_hint(data);
            break;
#endif
        case POWER_HINT_VIDEO_ENCODE:
            ret_val = process_video_encode_hint(data);
            break;
        case POWER_HINT_LAUNCH:
            ret_val = process_activity_launch_hint(data);
            break;
        default:
            break;
    }
    return ret_val;
}

int set_interactive_override(int on)
{
    return HINT_HANDLED; /* Don't excecute this code path, not in use */
    char governor[80];

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return HINT_NONE;
    }

    if (!on) {
        /* Display off */
        if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
            (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            int resource_values[] = {}; /* dummy node */
            if (!display_hint_sent) {
                perform_hint_action(DISPLAY_STATE_HINT_ID,
                resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
                display_hint_sent = 1;
                ALOGV("Display Off hint start");
                return HINT_HANDLED;
            }
        }
    } else {
        /* Display on */
        if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
            (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            undo_hint_action(DISPLAY_STATE_HINT_ID);
            display_hint_sent = 0;
            ALOGV("Display Off hint stop");
            return HINT_HANDLED;
        }
    }
    return HINT_NONE;
}
