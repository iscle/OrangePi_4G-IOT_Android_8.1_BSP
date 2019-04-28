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

#define LOG_TAG "audio_hw_extn"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <stdlib.h>
#include <errno.h>
#include <dlfcn.h>
#include <cutils/properties.h>
#include <cutils/log.h>

#include "audio_hw.h"
#include "audio_extn.h"
#include "platform.h"
#include "platform_api.h"

struct snd_card_split cur_snd_card_split = {
    .device = {0},
    .snd_card = {0},
    .form_factor = {0},
};

struct snd_card_split *audio_extn_get_snd_card_split()
{
    return &cur_snd_card_split;
}

void audio_extn_set_snd_card_split(const char* in_snd_card_name)
{
    /* sound card name follows below mentioned convention
       <target name>-<sound card name>-<form factor>-snd-card
       parse target name, sound card name and form factor
    */
    char *snd_card_name = strdup(in_snd_card_name);
    char *tmp = NULL;
    char *device = NULL;
    char *snd_card = NULL;
    char *form_factor = NULL;

    if (in_snd_card_name == NULL) {
        ALOGE("%s: snd_card_name passed is NULL", __func__);
        goto on_error;
    }

    device = strtok_r(snd_card_name, "-", &tmp);
    if (device == NULL) {
        ALOGE("%s: called on invalid snd card name", __func__);
        goto on_error;
    }
    strlcpy(cur_snd_card_split.device, device, HW_INFO_ARRAY_MAX_SIZE);

    snd_card = strtok_r(NULL, "-", &tmp);
    if (snd_card == NULL) {
        ALOGE("%s: called on invalid snd card name", __func__);
        goto on_error;
    }
    strlcpy(cur_snd_card_split.snd_card, snd_card, HW_INFO_ARRAY_MAX_SIZE);

    form_factor = strtok_r(NULL, "-", &tmp);
    if (form_factor == NULL) {
        ALOGE("%s: called on invalid snd card name", __func__);
        goto on_error;
    }
    strlcpy(cur_snd_card_split.form_factor, form_factor, HW_INFO_ARRAY_MAX_SIZE);

    ALOGI("%s: snd_card_name(%s) device(%s) snd_card(%s) form_factor(%s)",
               __func__, in_snd_card_name, device, snd_card, form_factor);

on_error:
    if (snd_card_name)
        free(snd_card_name);
}

#ifdef KPI_OPTIMIZE_ENABLED
typedef int (*perf_lock_acquire_t)(int, int, int*, int);
typedef int (*perf_lock_release_t)(int);

static void *qcopt_handle;
static perf_lock_acquire_t perf_lock_acq;
static perf_lock_release_t perf_lock_rel;

static int perf_lock_handle;
char opt_lib_path[PROPERTY_VALUE_MAX] = {0};

int perf_lock_opts[] = {0x101, 0x20E, 0x30E};

int audio_extn_perf_lock_init(void)
{
    int ret = 0;
    if (qcopt_handle == NULL) {
        if (property_get("ro.vendor.extension_library",
                         opt_lib_path, NULL) <= 0) {
            ALOGE("%s: Failed getting perf property", __func__);
            ret = -EINVAL;
            goto err;
        }
        if ((qcopt_handle = dlopen(opt_lib_path, RTLD_NOW)) == NULL) {
            ALOGE("%s: Failed to open perf handle", __func__);
            ret = -EINVAL;
            goto err;
        } else {
            perf_lock_acq = (perf_lock_acquire_t)dlsym(qcopt_handle,
                                                       "perf_lock_acq");
            if (perf_lock_acq == NULL) {
                ALOGE("%s: Perf lock Acquire NULL", __func__);
                dlclose(qcopt_handle);
                qcopt_handle = NULL;
                ret = -EINVAL;
                goto err;
            }
            perf_lock_rel = (perf_lock_release_t)dlsym(qcopt_handle,
                                                       "perf_lock_rel");
            if (perf_lock_rel == NULL) {
                ALOGE("%s: Perf lock Release NULL", __func__);
                dlclose(qcopt_handle);
                qcopt_handle = NULL;
                ret = -EINVAL;
                goto err;
            }
            ALOGD("%s: Perf lock handles Success", __func__);
        }
    }
err:
    return ret;
}

void audio_extn_perf_lock_acquire(void)
{
    if (perf_lock_acq) {
        perf_lock_handle = perf_lock_acq(perf_lock_handle, 0, perf_lock_opts, 3);
        ALOGV("%s: Perf lock acquired", __func__);
    } else {
        ALOGE("%s: Perf lock acquire error", __func__);
    }
}

void audio_extn_perf_lock_release(void)
{
    if (perf_lock_rel && perf_lock_handle) {
        perf_lock_rel(perf_lock_handle);
        perf_lock_handle = 0;
        ALOGV("%s: Perf lock released", __func__);
    } else {
        ALOGE("%s: Perf lock release error", __func__);
    }
}
#endif /* KPI_OPTIMIZE_ENABLED */
