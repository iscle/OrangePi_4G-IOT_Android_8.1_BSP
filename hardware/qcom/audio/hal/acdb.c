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

#define LOG_TAG "audio_hw_acdb"
//#define LOG_NDEBUG 0
#define LOG_NDDEBUG 0

#include <stdlib.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <cutils/log.h>
#include <cutils/str_parms.h>
#include <system/audio.h>
#include <tinyalsa/asoundlib.h>
#include "acdb.h"
#include <platform_api.h>

#define PLATFORM_CONFIG_KEY_SOUNDCARD_NAME "snd_card_name"

int acdb_init(int snd_card_num)
{

    int result = -1;
    char *cvd_version = NULL;

    char *snd_card_name = NULL;
    struct mixer *mixer = NULL;
    struct acdb_platform_data *my_data = NULL;

    if(snd_card_num < 0) {
        ALOGE("invalid sound card number");
        return result;
    }

    mixer = mixer_open(snd_card_num);
    if (!mixer) {
        ALOGE("%s: Unable to open the mixer card: %d", __func__,
               snd_card_num);
        goto cleanup;
    }

    my_data = calloc(1, sizeof(struct acdb_platform_data));
    if (!my_data) {
        ALOGE("failed to allocate acdb platform data");
        goto cleanup;
    }

    list_init(&my_data->acdb_meta_key_list);

    my_data->acdb_handle = dlopen(LIB_ACDB_LOADER, RTLD_NOW);
    if (my_data->acdb_handle == NULL) {
        ALOGE("%s: DLOPEN failed for %s", __func__, LIB_ACDB_LOADER);
        goto cleanup;
    }

    ALOGV("%s: DLOPEN successful for %s", __func__, LIB_ACDB_LOADER);

    my_data->acdb_init_v3 = (acdb_init_v3_t)dlsym(my_data->acdb_handle,
                                                     "acdb_loader_init_v3");
    if (my_data->acdb_init_v3 == NULL)
        ALOGE("%s: dlsym error %s for acdb_loader_init_v3", __func__, dlerror());

    my_data->acdb_init_v2 = (acdb_init_v2_cvd_t)dlsym(my_data->acdb_handle,
                                                     "acdb_loader_init_v2");
    if (my_data->acdb_init_v2 == NULL)
        ALOGE("%s: dlsym error %s for acdb_loader_init_v2", __func__, dlerror());

    my_data->acdb_init = (acdb_init_t)dlsym(my_data->acdb_handle,
                                                 "acdb_loader_init_ACDB");
    if (my_data->acdb_init == NULL && my_data->acdb_init_v2 == NULL
        && my_data->acdb_init_v3 == NULL) {
        ALOGE("%s: dlsym error %s for acdb_loader_init_ACDB", __func__, dlerror());
        goto cleanup;
    }

    /* Get CVD version */
    cvd_version = calloc(1, MAX_CVD_VERSION_STRING_SIZE);
    if (!cvd_version) {
        ALOGE("%s: Failed to allocate cvd version", __func__);
        goto cleanup;
    } else {
        struct mixer_ctl *ctl = NULL;
        int count = 0;

        ctl = mixer_get_ctl_by_name(mixer, CVD_VERSION_MIXER_CTL);
        if (!ctl) {
            ALOGE("%s: Could not get ctl for mixer cmd - %s",  __func__, CVD_VERSION_MIXER_CTL);
            goto cleanup;
        }
        mixer_ctl_update(ctl);

        count = mixer_ctl_get_num_values(ctl);
        if (count > MAX_CVD_VERSION_STRING_SIZE)
            count = MAX_CVD_VERSION_STRING_SIZE;

        result = mixer_ctl_get_array(ctl, cvd_version, count);
        if (result != 0) {
            ALOGE("%s: ERROR! mixer_ctl_get_array() failed to get CVD Version", __func__);
            goto cleanup;
        }
    }

    /* Get Sound card name */
    snd_card_name = strdup(mixer_get_name(mixer));
    if (!snd_card_name) {
        ALOGE("failed to allocate memory for snd_card_name");
        result = -1;
        goto cleanup;
    }

    if (my_data->acdb_init_v3)
        result = my_data->acdb_init_v3(snd_card_name, cvd_version,
                                       &my_data->acdb_meta_key_list);
    else if (my_data->acdb_init_v2)
        result = my_data->acdb_init_v2(snd_card_name, cvd_version, 0);
    else
        result = my_data->acdb_init();

cleanup:
    if (NULL != my_data) {
        if (my_data->acdb_handle)
            dlclose(my_data->acdb_handle);

        struct listnode *node;
        struct meta_key_list *key_info;
        list_for_each(node, &my_data->acdb_meta_key_list) {
            key_info = node_to_item(node, struct meta_key_list, list);
            free(key_info);
        }
        free(my_data);
    }

    mixer_close(mixer);
    free(cvd_version);
    free(snd_card_name);

    return result;
}

int acdb_set_parameters(void *platform, struct str_parms *parms)
{
    struct acdb_platform_data *my_data = (struct acdb_platform_data *)platform;
    char value[128];
    char *kv_pairs = str_parms_to_str(parms);
    int ret = 0;

    if (kv_pairs == NULL) {
        ret = -EINVAL;
        ALOGE("%s: key-value pair is NULL",__func__);
        goto done;
    }

    ret = str_parms_get_str(parms, PLATFORM_CONFIG_KEY_SOUNDCARD_NAME,
                            value, sizeof(value));
    if (ret >= 0) {
        str_parms_del(parms, PLATFORM_CONFIG_KEY_SOUNDCARD_NAME);
        my_data->snd_card_name = strdup(value);
        ALOGV("%s: sound card name %s", __func__, my_data->snd_card_name);
    }

done:
    free(kv_pairs);

    return ret;
}
