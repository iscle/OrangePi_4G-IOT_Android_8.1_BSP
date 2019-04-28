/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "hardware_cal"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#ifdef HWDEP_CAL_ENABLED

#include <stdlib.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <cutils/log.h>
#include <audio_hw.h>
#include "audio_extn.h"
#include "sound/msmcal-hwdep.h"

#define SOUND_TRIGGER_DEVICE_HANDSET_MONO_LOW_POWER_ACDB_ID (100)
#define MAX_CAL_NAME 20

typedef struct acdb_audio_cal_cfg {
    uint32_t             persist;
    uint32_t             snd_dev_id;
    audio_devices_t      dev_id;
    int32_t              acdb_dev_id;
    uint32_t             app_type;
    uint32_t             topo_id;
    uint32_t             sampling_rate;
    uint32_t             cal_type;
    uint32_t             module_id;
    uint32_t             param_id;
} acdb_audio_cal_cfg_t;

struct param_data {
    int    use_case;
    int    acdb_id;
    int    get_size;
    int    buff_size;
    int    data_size;
    void   *buff;
};

char cal_name_info[WCD9XXX_MAX_CAL][MAX_CAL_NAME] = {
        [WCD9XXX_ANC_CAL] = "anc_cal",
        [WCD9XXX_MBHC_CAL] = "mbhc_cal",
        [WCD9XXX_MAD_CAL] = "mad_cal",
};

typedef int (*acdb_get_calibration_t)(char *attr, int size, void *data);
acdb_get_calibration_t     acdb_get_calibration;

static int hw_util_open(int card_no)
{
    int fd = -1;
    char dev_name[256];

    snprintf(dev_name, sizeof(dev_name), "/dev/snd/hwC%uD%u",
                               card_no, WCD9XXX_CODEC_HWDEP_NODE);
    ALOGD("%s: Opening device %s\n", __func__, dev_name);
    fd = open(dev_name, O_WRONLY);
    if (fd < 0) {
        ALOGE("%s: cannot open device '%s'\n", __func__, dev_name);
        return fd;
    }
    ALOGD("%s: success", __func__);
    return fd;
}

static int send_codec_cal(acdb_get_calibration_t acdb_loader_get_calibration, int fd)
{
    int ret = 0, type;

    for (type = WCD9XXX_ANC_CAL; type < WCD9XXX_MAX_CAL; type++) {
        struct wcdcal_ioctl_buffer codec_buffer;
        struct param_data calib;

        if (!strcmp(cal_name_info[type], "mad_cal"))
            calib.acdb_id = SOUND_TRIGGER_DEVICE_HANDSET_MONO_LOW_POWER_ACDB_ID;
        calib.get_size = 1;
        ret = acdb_loader_get_calibration(cal_name_info[type], sizeof(struct param_data),
                                                                 &calib);
        if (ret < 0) {
            ALOGE("%s get_calibration failed\n", __func__);
            return ret;
        }
        calib.get_size = 0;
        calib.buff = malloc(calib.buff_size);
        ret = acdb_loader_get_calibration(cal_name_info[type],
                              sizeof(struct param_data), &calib);
        if (ret < 0) {
            ALOGE("%s get_calibration failed\n", __func__);
            free(calib.buff);
            return ret;
        }
        codec_buffer.buffer = calib.buff;
        codec_buffer.size = calib.data_size;
        codec_buffer.cal_type = type;
        if (ioctl(fd, SNDRV_CTL_IOCTL_HWDEP_CAL_TYPE, &codec_buffer) < 0)
            ALOGE("Failed to call ioctl  for %s err=%d",
                                  cal_name_info[type], errno);
        ALOGD("%s cal sent for %s", __func__, cal_name_info[type]);
        free(calib.buff);
    }
    return ret;
}


void audio_extn_hwdep_cal_send(int snd_card, void *acdb_handle)
{
    int fd;

    fd = hw_util_open(snd_card);
    if (fd == -1) {
        ALOGE("%s error open\n", __func__);
        return;
    }

    acdb_get_calibration = (acdb_get_calibration_t)
          dlsym(acdb_handle, "acdb_loader_get_calibration");

    if (acdb_get_calibration == NULL) {
        ALOGE("%s: ERROR. dlsym Error:%s acdb_loader_get_calibration", __func__,
           dlerror());
        return;
    }
    if (send_codec_cal(acdb_get_calibration, fd) < 0)
        ALOGE("%s: Could not send anc cal", __FUNCTION__);

    close(fd);
}

#endif
