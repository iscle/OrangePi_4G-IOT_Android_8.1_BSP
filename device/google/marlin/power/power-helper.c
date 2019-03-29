/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
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
#include <inttypes.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <stdlib.h>

#define LOG_TAG "QCOM PowerHAL"
#include <utils/Log.h>
#include <hardware/power.h>
#include <cutils/properties.h>

#include "utils.h"
#include "metadata-defs.h"
#include "hint-data.h"
#include "performance.h"
#include "power-common.h"
#include "power-helper.h"

#define USINSEC 1000000L
#define NSINUS 1000L

#ifndef RPM_STAT
#define RPM_STAT "/d/rpm_stats"
#endif

#ifndef RPM_MASTER_STAT
#define RPM_MASTER_STAT "/d/rpm_master_stats"
#endif

#ifndef WLAN_POWER_STAT
#define WLAN_POWER_STAT "/d/wlan_wcnss/power_stats"
#endif

static const char *rpm_param_names[] = {
    "vlow_count",
    "accumulated_vlow_time",
    "vmin_count",
    "accumulated_vmin_time"
};

static const char *rpm_master_param_names[] = {
    "xo_accumulated_duration",
    "xo_count",
    "xo_accumulated_duration",
    "xo_count",
    "xo_accumulated_duration",
    "xo_count",
    "xo_accumulated_duration",
    "xo_count"
};

static const char *wlan_param_names[] = {
    "cumulative_sleep_time_ms",
    "cumulative_total_on_time_ms",
    "deep_sleep_enter_counter",
    "last_deep_sleep_enter_tstamp_ms"
};


static int saved_dcvs_cpu0_slack_max = -1;
static int saved_dcvs_cpu0_slack_min = -1;
static int saved_mpdecision_slack_max = -1;
static int saved_mpdecision_slack_min = -1;
static int saved_interactive_mode = -1;
static int slack_node_rw_failed = 0;
static int display_hint_sent;
int display_boost;
static int sustained_mode_handle = 0;
static int vr_mode_handle = 0;
int sustained_performance_mode = 0;
int vr_mode = 0;

//interaction boost global variables
static struct timespec s_previous_boost_timespec;
static int s_previous_duration;

void power_init(void)
{
    ALOGV("QCOM power HAL initing.");

    int fd;
    char buf[10] = {0};

    fd = open("/sys/devices/soc0/soc_id", O_RDONLY);
    if (fd >= 0) {
        if (read(fd, buf, sizeof(buf) - 1) == -1) {
            ALOGW("Unable to read soc_id");
        } else {
            int soc_id = atoi(buf);
            if (soc_id == 194 || (soc_id >= 208 && soc_id <= 218) || soc_id == 178) {
                display_boost = 1;
            }
        }
        close(fd);
    }
}

static void process_video_decode_hint(void *metadata)
{
    char governor[80];
    struct video_decode_metadata_t video_decode_metadata;

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return;
    }

    if (metadata) {
        ALOGV("Processing video decode hint. Metadata: %s", (char *)metadata);
    }

    /* Initialize encode metadata struct fields. */
    memset(&video_decode_metadata, 0, sizeof(struct video_decode_metadata_t));
    video_decode_metadata.state = -1;
    video_decode_metadata.hint_id = DEFAULT_VIDEO_DECODE_HINT_ID;

    if (metadata) {
        if (parse_video_decode_metadata((char *)metadata, &video_decode_metadata) ==
            -1) {
            ALOGE("Error occurred while parsing metadata.");
            return;
        }
    } else {
        return;
    }

    if (video_decode_metadata.state == 1) {
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
            int resource_values[] = {THREAD_MIGRATION_SYNC_OFF};

            perform_hint_action(video_decode_metadata.hint_id,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            int resource_values[] = {TR_MS_30, HISPEED_LOAD_90, HS_FREQ_1026, THREAD_MIGRATION_SYNC_OFF};

            perform_hint_action(video_decode_metadata.hint_id,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
        }
    } else if (video_decode_metadata.state == 0) {
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            undo_hint_action(video_decode_metadata.hint_id);
        }
    }
}

static void process_video_encode_hint(void *metadata)
{
    char governor[80];
    struct video_encode_metadata_t video_encode_metadata;

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return;
    }

    /* Initialize encode metadata struct fields. */
    memset(&video_encode_metadata, 0, sizeof(struct video_encode_metadata_t));
    video_encode_metadata.state = -1;
    video_encode_metadata.hint_id = DEFAULT_VIDEO_ENCODE_HINT_ID;

    if (metadata) {
        if (parse_video_encode_metadata((char *)metadata, &video_encode_metadata) ==
            -1) {
            ALOGE("Error occurred while parsing metadata.");
            return;
        }
    } else {
        return;
    }

    if (video_encode_metadata.state == 1) {
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
            int resource_values[] = {IO_BUSY_OFF, SAMPLING_DOWN_FACTOR_1, THREAD_MIGRATION_SYNC_OFF};

            perform_hint_action(video_encode_metadata.hint_id,
                resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            int resource_values[] = {TR_MS_30, HISPEED_LOAD_90, HS_FREQ_1026, THREAD_MIGRATION_SYNC_OFF,
                INTERACTIVE_IO_BUSY_OFF};

            perform_hint_action(video_encode_metadata.hint_id,
                    resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
        }
    } else if (video_encode_metadata.state == 0) {
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
            undo_hint_action(video_encode_metadata.hint_id);
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            undo_hint_action(video_encode_metadata.hint_id);
        }
    }
}

int __attribute__ ((weak)) power_hint_override(power_hint_t UNUSED(hint),
        void * UNUSED(data))
{
    return HINT_NONE;
}

/* Declare function before use */
void interaction(int duration, int num_args, int opt_list[]);
void release_request(int lock_handle);

static long long calc_timespan_us(struct timespec start, struct timespec end) {
    long long diff_in_us = 0;
    diff_in_us += (end.tv_sec - start.tv_sec) * USINSEC;
    diff_in_us += (end.tv_nsec - start.tv_nsec) / NSINUS;
    return diff_in_us;
}

void power_hint(power_hint_t hint, void *data)
{
    /* Check if this hint has been overridden. */
    if (power_hint_override(hint, data) == HINT_HANDLED) {
        /* The power_hint has been handled. We can skip the rest. */
        return;
    }

    switch(hint) {
        case POWER_HINT_VSYNC:
        break;
        /* Sustained performance mode:
         * All CPUs are capped to ~1.2GHz
         * GPU frequency is capped to 315MHz
         */
        /* VR+Sustained performance mode:
         * All CPUs are locked to ~1.2GHz
         * GPU frequency is locked to 315MHz
         * GPU BW min_freq is raised to 775MHz
         */
        case POWER_HINT_SUSTAINED_PERFORMANCE:
        {
            int duration = 0;
            if (data && sustained_performance_mode == 0) {
                int* resources;
                if (vr_mode == 0) { // Sustained mode only.
                    // Ensure that POWER_HINT_LAUNCH is not in progress.
                    if (launch_mode == 1) {
                        release_request(launch_handle);
                        launch_mode = 0;
                    }
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40804000, 1209, 0x40804100, 1209,
                                       0x42C24000, 133,  0x42C20000, 315,
                                       0x42C28000, 7759};
                    sustained_mode_handle = interaction_with_handle(
                        sustained_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                } else if (vr_mode == 1) { // Sustained + VR mode.
                    release_request(vr_mode_handle);
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x40800000: cpu0 min freq
                    // 0x40800100: cpu2 min freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40800000, 1209, 0x40800100, 1209,
                                       0x40804000, 1209, 0x40804100, 1209,
                                       0x42C24000, 315,  0x42C20000, 315,
                                       0x42C28000, 7759};
                    sustained_mode_handle = interaction_with_handle(
                        sustained_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                }
                sustained_performance_mode = 1;
            } else if (sustained_performance_mode == 1) {
                release_request(sustained_mode_handle);
                if (vr_mode == 1) { // Switch back to VR Mode.
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x40800000: cpu0 min freq
                    // 0x40800100: cpu2 min freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40804000, 1440, 0x40804100, 1440,
                                       0x40800000, 1440, 0x40800100, 1440,
                                       0x42C20000, 510,  0x42C24000, 510,
                                       0x42C28000, 7759};
                    vr_mode_handle = interaction_with_handle(
                        vr_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                }
                sustained_performance_mode = 0;
            }
        }
        break;
        /* VR mode:
         * All CPUs are locked at ~1.4GHz
         * GPU frequency is locked  to 510MHz
         * GPU BW min_freq is raised to 775MHz
         */
        case POWER_HINT_VR_MODE:
        {
            int duration = 0;
            if (data && vr_mode == 0) {
                if (sustained_performance_mode == 0) { // VR mode only.
                    // Ensure that POWER_HINT_LAUNCH is not in progress.
                    if (launch_mode == 1) {
                        release_request(launch_handle);
                        launch_mode = 0;
                    }
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x40800000: cpu0 min freq
                    // 0x40800100: cpu2 min freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40800000, 1440, 0x40800100, 1440,
                                       0x40804000, 1440, 0x40804100, 1440,
                                       0x42C20000, 510,  0x42C24000, 510,
                                       0x42C28000, 7759};
                    vr_mode_handle = interaction_with_handle(
                        vr_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                } else if (sustained_performance_mode == 1) { // Sustained + VR mode.
                    release_request(sustained_mode_handle);
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x40800000: cpu0 min freq
                    // 0x40800100: cpu2 min freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40800000, 1209, 0x40800100, 1209,
                                       0x40804000, 1209, 0x40804100, 1209,
                                       0x42C24000, 315,  0x42C20000, 315,
                                       0x42C28000, 7759};

                    vr_mode_handle = interaction_with_handle(
                        vr_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                }
                vr_mode = 1;
            } else if (vr_mode == 1) {
                release_request(vr_mode_handle);
                if (sustained_performance_mode == 1) { // Switch back to sustained Mode.
                    // 0x40804000: cpu0 max freq
                    // 0x40804100: cpu2 max freq
                    // 0x40800000: cpu0 min freq
                    // 0x40800100: cpu2 min freq
                    // 0x42C20000: gpu max freq
                    // 0x42C24000: gpu min freq
                    // 0x42C28000: gpu bus min freq
                    int resources[] = {0x40800000, 0,    0x40800100, 0,
                                       0x40804000, 1209, 0x40804100, 1209,
                                       0x42C24000, 133,  0x42C20000, 315,
                                       0x42C28000, 0};
                    sustained_mode_handle = interaction_with_handle(
                        sustained_mode_handle, duration,
                        sizeof(resources) / sizeof(resources[0]), resources);
                }
                vr_mode = 0;
            }
        }
        break;
        case POWER_HINT_INTERACTION:
        {
            char governor[80];

            if (get_scaling_governor(governor, sizeof(governor)) == -1) {
                ALOGE("Can't obtain scaling governor.");
                return;
            }

            if (sustained_performance_mode || vr_mode) {
                return;
            }

            int duration = 1500; // 1.5s by default
            if (data) {
                int input_duration = *((int*)data) + 750;
                if (input_duration > duration) {
                    duration = (input_duration > 5750) ? 5750 : input_duration;
                }
            }

            struct timespec cur_boost_timespec;
            clock_gettime(CLOCK_MONOTONIC, &cur_boost_timespec);

            long long elapsed_time = calc_timespan_us(s_previous_boost_timespec, cur_boost_timespec);
            // don't hint if previous hint's duration covers this hint's duration
            if ((s_previous_duration * 1000) > (elapsed_time + duration * 1000)) {
                return;
            }
            s_previous_boost_timespec = cur_boost_timespec;
            s_previous_duration = duration;

            // Scheduler is EAS.
            if (true || strncmp(governor, SCHED_GOVERNOR, strlen(SCHED_GOVERNOR)) == 0) {
                // Setting the value of foreground schedtune boost to 50 and
                // scaling_min_freq to 1100MHz.
                int resources[] = {0x40800000, 1100, 0x40800100, 1100, 0x42C0C000, 0x32, 0x41800000, 0x33};
                interaction(duration, sizeof(resources)/sizeof(resources[0]), resources);
            } else { // Scheduler is HMP.
                int resources[] = {0x41800000, 0x33, 0x40800000, 1000, 0x40800100, 1000, 0x40C00000, 0x1};
                interaction(duration, sizeof(resources)/sizeof(resources[0]), resources);
            }
        }
        break;
        case POWER_HINT_VIDEO_ENCODE:
            process_video_encode_hint(data);
        break;
        case POWER_HINT_VIDEO_DECODE:
            process_video_decode_hint(data);
        break;
        default:
        break;
    }
}

int __attribute__ ((weak)) set_interactive_override(int UNUSED(on))
{
    return HINT_NONE;
}

void power_set_interactive(int on)
{
    char governor[80];
    char tmp_str[NODE_MAX];
    struct video_encode_metadata_t video_encode_metadata;
    int rc = 0;

    if (set_interactive_override(on) == HINT_HANDLED) {
        return;
    }

    ALOGV("Got set_interactive hint");

    if (get_scaling_governor(governor, sizeof(governor)) == -1) {
        ALOGE("Can't obtain scaling governor.");

        return;
    }

    if (!on) {
        /* Display off. */
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
            int resource_values[] = {DISPLAY_OFF, MS_500, THREAD_MIGRATION_SYNC_OFF};

            if (!display_hint_sent) {
                perform_hint_action(DISPLAY_STATE_HINT_ID,
                        resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
                display_hint_sent = 1;
            }
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            int resource_values[] = {TR_MS_50, THREAD_MIGRATION_SYNC_OFF};

            if (!display_hint_sent) {
                perform_hint_action(DISPLAY_STATE_HINT_ID,
                        resource_values, sizeof(resource_values)/sizeof(resource_values[0]));
                display_hint_sent = 1;
            }
        } else if ((strncmp(governor, MSMDCVS_GOVERNOR, strlen(MSMDCVS_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(MSMDCVS_GOVERNOR))) {
            if (saved_interactive_mode == 1){
                /* Display turned off. */
                if (sysfs_read(DCVS_CPU0_SLACK_MAX_NODE, tmp_str, NODE_MAX - 1)) {
                    if (!slack_node_rw_failed) {
                        ALOGE("Failed to read from %s", DCVS_CPU0_SLACK_MAX_NODE);
                    }

                    rc = 1;
                } else {
                    saved_dcvs_cpu0_slack_max = atoi(tmp_str);
                }

                if (sysfs_read(DCVS_CPU0_SLACK_MIN_NODE, tmp_str, NODE_MAX - 1)) {
                    if (!slack_node_rw_failed) {
                        ALOGE("Failed to read from %s", DCVS_CPU0_SLACK_MIN_NODE);
                    }

                    rc = 1;
                } else {
                    saved_dcvs_cpu0_slack_min = atoi(tmp_str);
                }

                if (sysfs_read(MPDECISION_SLACK_MAX_NODE, tmp_str, NODE_MAX - 1)) {
                    if (!slack_node_rw_failed) {
                        ALOGE("Failed to read from %s", MPDECISION_SLACK_MAX_NODE);
                    }

                    rc = 1;
                } else {
                    saved_mpdecision_slack_max = atoi(tmp_str);
                }

                if (sysfs_read(MPDECISION_SLACK_MIN_NODE, tmp_str, NODE_MAX - 1)) {
                    if(!slack_node_rw_failed) {
                        ALOGE("Failed to read from %s", MPDECISION_SLACK_MIN_NODE);
                    }

                    rc = 1;
                } else {
                    saved_mpdecision_slack_min = atoi(tmp_str);
                }

                /* Write new values. */
                if (saved_dcvs_cpu0_slack_max != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", 10 * saved_dcvs_cpu0_slack_max);

                    if (sysfs_write(DCVS_CPU0_SLACK_MAX_NODE, tmp_str) != 0) {
                        if (!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", DCVS_CPU0_SLACK_MAX_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_dcvs_cpu0_slack_min != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", 10 * saved_dcvs_cpu0_slack_min);

                    if (sysfs_write(DCVS_CPU0_SLACK_MIN_NODE, tmp_str) != 0) {
                        if(!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", DCVS_CPU0_SLACK_MIN_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_mpdecision_slack_max != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", 10 * saved_mpdecision_slack_max);

                    if (sysfs_write(MPDECISION_SLACK_MAX_NODE, tmp_str) != 0) {
                        if(!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", MPDECISION_SLACK_MAX_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_mpdecision_slack_min != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", 10 * saved_mpdecision_slack_min);

                    if (sysfs_write(MPDECISION_SLACK_MIN_NODE, tmp_str) != 0) {
                        if(!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", MPDECISION_SLACK_MIN_NODE);
                        }

                        rc = 1;
                    }
                }
            }

            slack_node_rw_failed = rc;
        }
    } else {
        /* Display on. */
        if ((strncmp(governor, ONDEMAND_GOVERNOR, strlen(ONDEMAND_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(ONDEMAND_GOVERNOR))) {
            undo_hint_action(DISPLAY_STATE_HINT_ID);
            display_hint_sent = 0;
        } else if ((strncmp(governor, INTERACTIVE_GOVERNOR, strlen(INTERACTIVE_GOVERNOR)) == 0) &&
                (strlen(governor) == strlen(INTERACTIVE_GOVERNOR))) {
            undo_hint_action(DISPLAY_STATE_HINT_ID);
            display_hint_sent = 0;
        } else if ((strncmp(governor, MSMDCVS_GOVERNOR, strlen(MSMDCVS_GOVERNOR)) == 0) && 
                (strlen(governor) == strlen(MSMDCVS_GOVERNOR))) {
            if (saved_interactive_mode == -1 || saved_interactive_mode == 0) {
                /* Display turned on. Restore if possible. */
                if (saved_dcvs_cpu0_slack_max != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", saved_dcvs_cpu0_slack_max);

                    if (sysfs_write(DCVS_CPU0_SLACK_MAX_NODE, tmp_str) != 0) {
                        if (!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", DCVS_CPU0_SLACK_MAX_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_dcvs_cpu0_slack_min != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", saved_dcvs_cpu0_slack_min);

                    if (sysfs_write(DCVS_CPU0_SLACK_MIN_NODE, tmp_str) != 0) {
                        if (!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", DCVS_CPU0_SLACK_MIN_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_mpdecision_slack_max != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", saved_mpdecision_slack_max);

                    if (sysfs_write(MPDECISION_SLACK_MAX_NODE, tmp_str) != 0) {
                        if (!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", MPDECISION_SLACK_MAX_NODE);
                        }

                        rc = 1;
                    }
                }

                if (saved_mpdecision_slack_min != -1) {
                    snprintf(tmp_str, NODE_MAX, "%d", saved_mpdecision_slack_min);

                    if (sysfs_write(MPDECISION_SLACK_MIN_NODE, tmp_str) != 0) {
                        if (!slack_node_rw_failed) {
                            ALOGE("Failed to write to %s", MPDECISION_SLACK_MIN_NODE);
                        }

                        rc = 1;
                    }
                }
            }

            slack_node_rw_failed = rc;
        }
    }

    saved_interactive_mode = !!on;
}


static int extract_stats(uint64_t *list, char *file, const char**param_names,
                         unsigned int num_parameters, int isHex) {
    FILE *fp;
    ssize_t read;
    size_t len;
    size_t index = 0;
    char *line;
    int ret;

    fp = fopen(file, "r");
    if (fp == NULL) {
        ret = -errno;
        ALOGE("%s: failed to open: %s Error = %s", __func__, file, strerror(errno));
        return ret;
    }

    for (line = NULL, len = 0;
         ((read = getline(&line, &len, fp) != -1) && (index < num_parameters));
         free(line), line = NULL, len = 0) {
        uint64_t value;
        char* offset;

        size_t begin = strspn(line, " \t");
        if (strncmp(line + begin, param_names[index], strlen(param_names[index]))) {
            continue;
        }

        offset = memchr(line, ':', len);
        if (!offset) {
            continue;
        }

        if (isHex) {
            sscanf(offset, ":%" SCNx64, &value);
        } else {
            sscanf(offset, ":%" SCNu64, &value);
        }
        list[index] = value;
        index++;
    }

    free(line);
    fclose(fp);

    return 0;
}


int extract_platform_stats(uint64_t *list) {

    int ret;

    //Data is located in two files

    ret = extract_stats(list, RPM_STAT, rpm_param_names, RPM_PARAM_COUNT, false);
    if (ret) {
        for (size_t i=0; i < RPM_PARAM_COUNT; i++)
            list[i] = 0;
    }

    ret = extract_stats(list + RPM_PARAM_COUNT, RPM_MASTER_STAT,
                        rpm_master_param_names, PLATFORM_PARAM_COUNT - RPM_PARAM_COUNT, true);
    if (ret) {
        for (size_t i=RPM_PARAM_COUNT; i < PLATFORM_PARAM_COUNT; i++)
        list[i] = 0;
    }

    return 0;
}

int extract_wlan_stats(uint64_t *list) {

    int ret;

    ret = extract_stats(list, WLAN_POWER_STAT, wlan_param_names, WLAN_PARAM_COUNT, false);
    if (ret) {
        for (size_t i=0; i < WLAN_PARAM_COUNT; i++)
            list[i] = 0;
    }

    return 0;
}
