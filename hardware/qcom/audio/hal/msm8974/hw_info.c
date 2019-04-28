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

#define LOG_TAG "hardware_info"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <stdlib.h>
#include <cutils/log.h>
#include "audio_hw.h"
#include "platform.h"
#include "audio_extn.h"

struct hardware_info {
    char name[HW_INFO_ARRAY_MAX_SIZE];
    char type[HW_INFO_ARRAY_MAX_SIZE];
    /* variables for handling target variants */
    uint32_t num_snd_devices;
    char dev_extn[HW_INFO_ARRAY_MAX_SIZE];
    snd_device_t  *snd_devices;
};

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))


static const snd_device_t tasha_db_variant_devices[] = {
    SND_DEVICE_OUT_SPEAKER
};

static const snd_device_t tasha_fluid_variant_devices[] = {
    SND_DEVICE_OUT_SPEAKER,
    SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES,
    SND_DEVICE_OUT_VOICE_SPEAKER,
    SND_DEVICE_OUT_SPEAKER_AND_HDMI,
    SND_DEVICE_OUT_SPEAKER_PROTECTED,
    SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED,
};

static const snd_device_t tasha_liquid_variant_devices[] = {
    SND_DEVICE_OUT_SPEAKER,
    SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES,
    SND_DEVICE_IN_SPEAKER_MIC,
    SND_DEVICE_IN_HEADSET_MIC,
    SND_DEVICE_IN_VOICE_DMIC,
    SND_DEVICE_IN_VOICE_SPEAKER_DMIC,
    SND_DEVICE_IN_VOICE_REC_DMIC_STEREO,
    SND_DEVICE_IN_VOICE_REC_DMIC_FLUENCE,
    SND_DEVICE_IN_QUAD_MIC,
};

static void  update_hardware_info_8996(struct hardware_info *hw_info)
{
    struct snd_card_split *tmp_handle = audio_extn_get_snd_card_split();
    ALOGV("%s: device %s snd_card %s form_factor %s",
               __func__, tmp_handle->device, tmp_handle->snd_card, tmp_handle->form_factor);

    strlcpy(hw_info->name, tmp_handle->device, sizeof(hw_info->name));
    snprintf(hw_info->type, sizeof(hw_info->type), " %s", tmp_handle->form_factor);
    snprintf(hw_info->dev_extn, sizeof(hw_info->dev_extn), "-%s", tmp_handle->form_factor);

    if (!strncmp(tmp_handle->form_factor, "fluid", sizeof("fluid"))) {
        hw_info->snd_devices = (snd_device_t *)tasha_fluid_variant_devices;
        hw_info->num_snd_devices = ARRAY_SIZE(tasha_fluid_variant_devices);
    } else if (!strncmp(tmp_handle->form_factor, "liquid", sizeof("liquid"))) {
        hw_info->snd_devices = (snd_device_t *)tasha_liquid_variant_devices;
        hw_info->num_snd_devices = ARRAY_SIZE(tasha_liquid_variant_devices);
    } else if (!strncmp(tmp_handle->form_factor, "db", sizeof("db"))) {
        hw_info->snd_devices = (snd_device_t *)tasha_db_variant_devices;
        hw_info->num_snd_devices = ARRAY_SIZE(tasha_db_variant_devices);
    } else {
        ALOGW("%s: %s form factor doesnt need mixer path over ride", __func__, tmp_handle->form_factor);
    }

    ALOGV("name %s type %s dev_extn %s", hw_info->name, hw_info->type, hw_info->dev_extn);
}


void *hw_info_init(const char *snd_card_name)
{
    struct hardware_info *hw_info = NULL;
    bool hw_supported = false;

    if (strstr(snd_card_name, "msm8996")) {
        ALOGD("8996 - variant soundcard");
        hw_supported = true;
    } else {
        ALOGE("%s: Unsupported target %s:",__func__, snd_card_name);
    }

    if (hw_supported) {
        hw_info = malloc(sizeof(struct hardware_info));
        if (!hw_info) {
            ALOGE("failed to allocate mem for hardware info");
            goto on_finish;
        }

        hw_info->snd_devices = NULL;
        hw_info->num_snd_devices = 0;
        strlcpy(hw_info->dev_extn, "", sizeof(hw_info->dev_extn));
        strlcpy(hw_info->type, "", sizeof(hw_info->type));
        strlcpy(hw_info->name, "", sizeof(hw_info->name));
        update_hardware_info_8996(hw_info);
    }

on_finish:
    return hw_info;
}

void hw_info_deinit(void *hw_info)
{
    free(hw_info);
}

void hw_info_append_hw_type(void *hw_info, snd_device_t snd_device,
                            char *device_name)
{
    struct hardware_info *my_data = (struct hardware_info*) hw_info;
    uint32_t i = 0;

    if (my_data == NULL)
        return;

    snd_device_t *snd_devices =
            (snd_device_t *) my_data->snd_devices;

    if (snd_devices != NULL) {
        for (i = 0; i <  my_data->num_snd_devices; i++) {
            if (snd_device == (snd_device_t)snd_devices[i]) {
                ALOGV("extract dev_extn device %d, device_name %s extn = %s ",
                        (snd_device_t)snd_devices[i], device_name,  my_data->dev_extn);
                CHECK(strlcat(device_name,  my_data->dev_extn,
                        DEVICE_NAME_MAX_SIZE) < DEVICE_NAME_MAX_SIZE);
                break;
            }
        }
    }
    ALOGD("%s : device_name = %s", __func__,device_name);
}
