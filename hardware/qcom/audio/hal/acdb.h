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

#ifndef ACDB_H
#define ACDB_H

#include <cutils/list.h>
#include <linux/msm_audio_calibration.h>

#define MAX_CVD_VERSION_STRING_SIZE 100
#define LIB_ACDB_LOADER "libacdbloader.so"
#define CVD_VERSION_MIXER_CTL "CVD Version"
#define ACDB_METAINFO_KEY_MODULE_NAME_LEN 100

/* Audio calibration related functions */
typedef void (*acdb_deallocate_t)();
typedef int  (*acdb_init_v3_t)(const char *, char *, struct listnode *);
typedef int  (*acdb_init_v2_cvd_t)(char *, char *, int);
typedef int  (*acdb_init_v2_t)(char *);
typedef int  (*acdb_init_t)();
typedef void (*acdb_send_audio_cal_t)(int, int);
typedef void (*acdb_send_voice_cal_t)(int, int);
typedef int (*acdb_reload_vocvoltable_t)(int);
typedef int (*acdb_send_gain_dep_cal_t)(int, int, int, int, int);
typedef int (*acdb_send_custom_top_t) (void);

struct meta_key_list {
    struct listnode list;
    struct audio_cal_info_metainfo cal_info;
    char name[ACDB_METAINFO_KEY_MODULE_NAME_LEN];
};

struct acdb_platform_data {
    /* Audio calibration related functions */
    void                       *acdb_handle;
    acdb_init_t                acdb_init;
    acdb_init_v2_cvd_t         acdb_init_v2;
    acdb_init_v3_t             acdb_init_v3;
    char *snd_card_name;
    struct listnode acdb_meta_key_list;
};

int acdb_init(int);

int acdb_set_metainfo_key(void *platform, char *name, int key);
int acdb_set_parameters(void *platform, struct str_parms *parms);
#endif //ACDB_H
