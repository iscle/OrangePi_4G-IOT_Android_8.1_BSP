/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
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

#define LOG_NIDEBUG 0

#include <errno.h>
#include <inttypes.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <stdlib.h>

#define LOG_TAG "QCOMPowerHAL"
#include <utils/Log.h>
#include <hardware/hardware.h>
#include <hardware/power.h>

#include "utils.h"
#include "metadata-defs.h"
#include "hint-data.h"
#include "performance.h"
#include "power-common.h"

#define BUS_SPEED_PATH "/sys/class/devfreq/qcom,gpubw.70/min_freq"
#define GPU_MAX_FREQ_PATH "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
#define GPU_MIN_FREQ_PATH "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
#define CPU4_ONLINE_PATH "/sys/devices/system/cpu/cpu4/online"
#define CPU5_ONLINE_PATH "/sys/devices/system/cpu/cpu5/online"
#define CPU6_ONLINE_PATH "/sys/devices/system/cpu/cpu6/online"
#define CPU7_ONLINE_PATH "/sys/devices/system/cpu/cpu7/online"

#define PLATFORM_SLEEP_MODES 2
#define XO_VOTERS 3
#define VMIN_VOTERS 0

#define RPM_PARAMETERS 4
#define NUM_PARAMETERS 10

#ifndef RPM_STAT
#define RPM_STAT "/d/rpm_stats"
#endif

#ifndef RPM_MASTER_STAT
#define RPM_MASTER_STAT "/d/rpm_master_stats"
#endif

/* RPM runs at 19.2Mhz. Divide by 19200 for msec */
#define RPM_CLK 19200
#define USINSEC 1000000L
#define NSINUS 1000L

const char *parameter_names[] = {
    "vlow_count",
    "accumulated_vlow_time",
    "vmin_count",
    "accumulated_vmin_time",
    "xo_accumulated_duration",
    "xo_count",
    "xo_accumulated_duration",
    "xo_count",
    "xo_accumulated_duration",
    "xo_count"};

static int saved_dcvs_cpu0_slack_max = -1;
static int saved_dcvs_cpu0_slack_min = -1;
static int saved_mpdecision_slack_max = -1;
static int saved_mpdecision_slack_min = -1;
static int saved_interactive_mode = -1;
static int slack_node_rw_failed = 0;
static int display_hint_sent;
static int sustained_performance_mode = 0;
int display_boost;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

static void power_init(struct power_module *module)
{
    ALOGI("QCOM power HAL initing.");

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
        ALOGI("Processing video decode hint. Metadata: %s", (char *)metadata);
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

int __attribute__ ((weak)) power_hint_override(struct power_module *module, power_hint_t hint,
        void *data)
{
    return HINT_NONE;
}

/* Declare function before use */
int interaction(int duration, int num_args, int opt_list[]);
int interaction_with_handle(int lock_handle, int duration, int num_args, int opt_list[]);

static long long calc_timespan_us(struct timespec start, struct timespec end) {
    long long diff_in_us = 0;
    diff_in_us += (end.tv_sec - start.tv_sec) * USINSEC;
    diff_in_us += (end.tv_nsec - start.tv_nsec) / NSINUS;
    return diff_in_us;
}

static void power_hint(struct power_module *module, power_hint_t hint,
        void *data)
{
    static int handle_hotplug = 0;
    int resources_hotplug[] = {0x3DFF};
    /* Check if this hint has been overridden. */
    if (power_hint_override(module, hint, data) == HINT_HANDLED) {
        /* The power_hint has been handled. We can skip the rest. */
        return;
    }

    switch(hint) {
        case POWER_HINT_VSYNC:
        break;
        case POWER_HINT_INTERACTION:
        {
            int duration_hint = 0;
            static struct timespec previous_boost_timespec = {0, 0};

            // If we are in sustained performance mode, touch boost
            // should be ignored.
            pthread_mutex_lock(&lock);
            if (sustained_performance_mode) {
                pthread_mutex_unlock(&lock);
                return;
            }
            pthread_mutex_unlock(&lock);

            // little core freq bump for 1.5s
            int resources[] = {0x20C};
            int duration = 1500;
            static int handle_little = 0;

            // big core freq bump for 500ms
            int resources_big[] = {0x2312, 0x1F08};
            int duration_big = 500;
            static int handle_big = 0;

            // sched_downmigrate lowered to 10 for 1s at most
            // should be half of upmigrate
            int resources_downmigrate[] = {0x4F00};
            int duration_downmigrate = 1000;
            static int handle_downmigrate = 0;

            // sched_upmigrate lowered to at most 20 for 500ms
            // set threshold based on elapsed time since last boost
            int resources_upmigrate[] = {0x4E00};
            int duration_upmigrate = 500;
            static int handle_upmigrate = 0;

            // set duration hint
            if (data) {
                duration_hint = *((int*)data);
            }

            struct timespec cur_boost_timespec;
            clock_gettime(CLOCK_MONOTONIC, &cur_boost_timespec);
            pthread_mutex_lock(&lock);
            long long elapsed_time = calc_timespan_us(previous_boost_timespec, cur_boost_timespec);
            if (elapsed_time > 750000)
                elapsed_time = 750000;
            // don't hint if it's been less than 250ms since last boost
            // also detect if we're doing anything resembling a fling
            // support additional boosting in case of flings
            else if (elapsed_time < 250000 && duration_hint <= 750) {
                pthread_mutex_unlock(&lock);
                return;
            }

            previous_boost_timespec = cur_boost_timespec;
            pthread_mutex_unlock(&lock);

            // 95: default upmigrate for phone
            // 20: upmigrate for sporadic touch
            // 750ms: a completely arbitrary threshold for last touch
            int upmigrate_value = 95 - (int)(75. * ((elapsed_time*elapsed_time) / (750000.*750000.)));

            // keep sched_upmigrate high when flinging
            if (duration_hint >= 750)
                upmigrate_value = 20;

            resources_upmigrate[0] = resources_upmigrate[0] | upmigrate_value;
            resources_downmigrate[0] = resources_downmigrate[0] | (upmigrate_value / 2);

            // modify downmigrate duration based on interaction data hint
            // 1000 <= duration_downmigrate <= 5000
            // extend little core freq bump past downmigrate to soften downmigrates
            if (duration_hint > 1000) {
                if (duration_hint < 5000) {
                    duration_downmigrate = duration_hint;
                    duration = duration_hint + 750;
                } else {
                    duration_downmigrate = 5000;
                    duration = 5750;
                }
            }

            handle_little = interaction_with_handle(handle_little,duration, sizeof(resources)/sizeof(resources[0]), resources);
            handle_big = interaction_with_handle(handle_big, duration_big, sizeof(resources_big)/sizeof(resources_big[0]), resources_big);
            handle_downmigrate = interaction_with_handle(handle_downmigrate, duration_downmigrate, sizeof(resources_downmigrate)/sizeof(resources_downmigrate[0]), resources_downmigrate);
            handle_upmigrate = interaction_with_handle(handle_upmigrate, duration_upmigrate, sizeof(resources_upmigrate)/sizeof(resources_upmigrate[0]), resources_upmigrate);
        }
        break;
        case POWER_HINT_VIDEO_ENCODE:
            process_video_encode_hint(data);
        break;
        case POWER_HINT_VIDEO_DECODE:
            process_video_decode_hint(data);
        break;

        /* While the system is Sustained Performance Mode:
         * CPUfreq for the little cores are capped to 864MHz
         * Big cores are hotplugged out
         * GPU frequency is capped to 305 MHz
         */
        case POWER_HINT_SUSTAINED_PERFORMANCE:
        {
            static int handle = 0;

            pthread_mutex_lock(&lock);
            if (data && sustained_performance_mode == 0) {
                int resources[] = {0x1509};
                int duration = 0;
                handle = interaction_with_handle(handle, duration,
                                        sizeof(resources)/sizeof(resources[0]),
                                        resources);
                sysfs_write(GPU_MAX_FREQ_PATH, "305000000");
                handle_hotplug = interaction_with_handle(handle_hotplug, duration,
                                    sizeof(resources_hotplug)/sizeof(resources_hotplug[0]),
                                    resources_hotplug);
                sustained_performance_mode = 1;
            } else if (sustained_performance_mode == 1){
                release_request(handle);
                sysfs_write(GPU_MAX_FREQ_PATH, "600000000");
                release_request(handle_hotplug);
                sustained_performance_mode = 0;
           }
           pthread_mutex_unlock(&lock);
        }
        break;
    }
}

int __attribute__ ((weak)) set_interactive_override(struct power_module *module, int on)
{
    return HINT_NONE;
}

void set_interactive(struct power_module *module, int on)
{
    char governor[80];
    char tmp_str[NODE_MAX];
    struct video_encode_metadata_t video_encode_metadata;
    int rc;

    if (set_interactive_override(module, on) == HINT_HANDLED) {
        return;
    }

    ALOGI("Got set_interactive hint");

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

static ssize_t get_number_of_platform_modes(struct power_module *module) {
   return PLATFORM_SLEEP_MODES;
}

static int get_voter_list(struct power_module *module, size_t *voter) {
   voter[0] = XO_VOTERS;
   voter[1] = VMIN_VOTERS;

   return 0;
}

static int extract_stats(uint64_t *list, char *file,
    unsigned int num_parameters, unsigned int index) {
    FILE *fp;
    ssize_t read;
    size_t len;
    char *line;
    int ret;

    fp = fopen(file, "r");
    if (fp == NULL) {
        ret = -errno;
        ALOGE("%s: failed to open: %s", __func__, strerror(errno));
        return ret;
    }

    for (line = NULL, len = 0;
         ((read = getline(&line, &len, fp) != -1) && (index < num_parameters));
         free(line), line = NULL, len = 0) {
        uint64_t value;
        char* offset;

        size_t begin = strspn(line, " \t");
        if (strncmp(line + begin, parameter_names[index], strlen(parameter_names[index]))) {
            continue;
        }

        offset = memchr(line, ':', len);
        if (!offset) {
            continue;
        }

        if (!strcmp(file, RPM_MASTER_STAT)) {
            /* RPM_MASTER_STAT is reported in hex */
            sscanf(offset, ":%" SCNx64, &value);
            /* Duration is reported in rpm SLEEP TICKS */
            if (!strcmp(parameter_names[index], "xo_accumulated_duration")) {
                value /= RPM_CLK;
            }
        } else {
            /* RPM_STAT is reported in decimal */
            sscanf(offset, ":%" SCNu64, &value);
        }
        list[index] = value;
        index++;
    }
    free(line);

    fclose(fp);
    return 0;
}

static int get_platform_low_power_stats(struct power_module *module,
    power_state_platform_sleep_state_t *list) {
    uint64_t stats[sizeof(parameter_names)] = {0};
    int ret;

    if (!list) {
        return -EINVAL;
    }

    ret = extract_stats(stats, RPM_STAT, RPM_PARAMETERS, 0);

    if (ret) {
        return ret;
    }

    ret = extract_stats(stats, RPM_MASTER_STAT, NUM_PARAMETERS, 4);

    if (ret) {
        return ret;
    }

    /* Update statistics for XO_shutdown */
    strcpy(list[0].name, "XO_shutdown");
    list[0].total_transitions = stats[0];
    list[0].residency_in_msec_since_boot = stats[1];
    list[0].supported_only_in_suspend = false;
    list[0].number_of_voters = XO_VOTERS;

    /* Update statistics for APSS voter */
    strcpy(list[0].voters[0].name, "APSS");
    list[0].voters[0].total_time_in_msec_voted_for_since_boot = stats[4];
    list[0].voters[0].total_number_of_times_voted_since_boot = stats[5];

    /* Update statistics for MPSS voter */
    strcpy(list[0].voters[1].name, "MPSS");
    list[0].voters[1].total_time_in_msec_voted_for_since_boot = stats[6];
    list[0].voters[1].total_number_of_times_voted_since_boot = stats[7];

    /* Update statistics for LPASS voter */
    strcpy(list[0].voters[2].name, "LPASS");
    list[0].voters[2].total_time_in_msec_voted_for_since_boot = stats[8];
    list[0].voters[2].total_number_of_times_voted_since_boot = stats[9];

    /* Update statistics for VMIN state */
    strcpy(list[1].name, "VMIN");
    list[1].total_transitions = stats[2];
    list[1].residency_in_msec_since_boot = stats[3];
    list[1].supported_only_in_suspend = false;
    list[1].number_of_voters = VMIN_VOTERS;

    return 0;
}

static int power_open(const hw_module_t* module, const char* name,
                    hw_device_t** device)
{
    ALOGD("%s: enter; name=%s", __FUNCTION__, name);
    int retval = 0; /* 0 is ok; -1 is error */

    if (strcmp(name, POWER_HARDWARE_MODULE_ID) == 0) {
        power_module_t *dev = (power_module_t *)calloc(1,
                sizeof(power_module_t));

        if (dev) {
            /* Common hw_device_t fields */
            dev->common.tag = HARDWARE_DEVICE_TAG;
            dev->common.module_api_version = POWER_MODULE_API_VERSION_0_5;
            dev->common.hal_api_version = HARDWARE_HAL_API_VERSION;

            dev->init = power_init;
            dev->powerHint = power_hint;
            dev->setInteractive = set_interactive;
            dev->get_number_of_platform_modes = get_number_of_platform_modes;
            dev->get_platform_low_power_stats = get_platform_low_power_stats;
            dev->get_voter_list = get_voter_list;

            *device = (hw_device_t*)dev;
        } else
            retval = -ENOMEM;
    } else {
        retval = -EINVAL;
    }

    ALOGD("%s: exit %d", __FUNCTION__, retval);
    return retval;
}

static struct hw_module_methods_t power_module_methods = {
    .open = power_open,
};

struct power_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = POWER_MODULE_API_VERSION_0_5,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = POWER_HARDWARE_MODULE_ID,
        .name = "QCOM Power HAL",
        .author = "Qualcomm",
        .methods = &power_module_methods,
    },

    .init = power_init,
    .powerHint = power_hint,
    .setInteractive = set_interactive,
    .get_number_of_platform_modes = get_number_of_platform_modes,
    .get_platform_low_power_stats = get_platform_low_power_stats,
    .get_voter_list = get_voter_list
};
