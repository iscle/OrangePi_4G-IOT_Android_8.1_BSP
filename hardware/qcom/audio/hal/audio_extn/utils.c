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

#define LOG_TAG "audio_hw_utils"
//#define LOG_NDEBUG 0

#include <errno.h>
#include <cutils/properties.h>
#include <cutils/config_utils.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <unistd.h>
#include <cutils/str_parms.h>
#include <cutils/log.h>
#include <cutils/misc.h>

#include "acdb.h"
#include "audio_hw.h"
#include "platform.h"
#include "platform_api.h"
#include "audio_extn.h"

#define MAX_LENGTH_MIXER_CONTROL_IN_INT 128

static int set_stream_app_type_mixer_ctrl(struct audio_device *adev,
                                          int pcm_device_id, int app_type,
                                          int acdb_dev_id, int sample_rate,
                                          int stream_type,
                                          snd_device_t snd_device)
{

    char mixer_ctl_name[MAX_LENGTH_MIXER_CONTROL_IN_INT];
    struct mixer_ctl *ctl;
    int app_type_cfg[MAX_LENGTH_MIXER_CONTROL_IN_INT], len = 0, rc = 0;
    int snd_device_be_idx = -1;

    if (stream_type == PCM_PLAYBACK) {
        snprintf(mixer_ctl_name, sizeof(mixer_ctl_name),
             "Audio Stream %d App Type Cfg", pcm_device_id);
    } else if (stream_type == PCM_CAPTURE) {
        snprintf(mixer_ctl_name, sizeof(mixer_ctl_name),
             "Audio Stream Capture %d App Type Cfg", pcm_device_id);
    }

    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
             __func__, mixer_ctl_name);
        rc = -EINVAL;
        goto exit;
    }
    app_type_cfg[len++] = app_type;
    app_type_cfg[len++] = acdb_dev_id;
    app_type_cfg[len++] = sample_rate;

    snd_device_be_idx = platform_get_snd_device_backend_index(snd_device);
    if (snd_device_be_idx > 0)
        app_type_cfg[len++] = snd_device_be_idx;
    ALOGV("%s: stream type %d app_type %d, acdb_dev_id %d "
          "sample rate %d, snd_device_be_idx %d",
          __func__, stream_type, app_type, acdb_dev_id, sample_rate,
          snd_device_be_idx);
    mixer_ctl_set_array(ctl, app_type_cfg, len);

exit:
    return rc;
}

void audio_extn_utils_send_default_app_type_cfg(void *platform, struct mixer *mixer)
{
    int app_type_cfg[MAX_LENGTH_MIXER_CONTROL_IN_INT] = {-1};
    int length = 0, app_type = 0,rc = 0;
    struct mixer_ctl *ctl = NULL;
    const char *mixer_ctl_name = "App Type Config";

    ctl = mixer_get_ctl_by_name(mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",__func__, mixer_ctl_name);
        return;
    }
    rc = platform_get_default_app_type_v2(platform, PCM_PLAYBACK, &app_type);
    if (rc == 0) {
        app_type_cfg[length++] = 1;
        app_type_cfg[length++] = app_type;
        app_type_cfg[length++] = 48000;
        app_type_cfg[length++] = 16;
        mixer_ctl_set_array(ctl, app_type_cfg, length);
    }
    return;
}

static const char *flags_to_mode(int dir, uint32_t flags)
{
    if (dir == 0) {
        if (flags & AUDIO_OUTPUT_FLAG_VOIP_RX) {
            return "voip";
        }
    } else if (dir == 1) {
        if (flags & AUDIO_INPUT_FLAG_VOIP_TX) {
            return "voip";
        }
    }
    return "default";
}

static int audio_extn_utils_send_app_type_cfg_hfp(struct audio_device *adev,
                                       struct audio_usecase *usecase)
{
    struct mixer_ctl *ctl;
    int pcm_device_id, acdb_dev_id = 0, snd_device = usecase->out_snd_device;
    int32_t sample_rate = DEFAULT_OUTPUT_SAMPLING_RATE;
    int app_type = 0, rc = 0;

    ALOGV("%s", __func__);

    if (usecase->type != PCM_HFP_CALL) {
        ALOGV("%s: not a playback or HFP path, no need to cfg app type", __func__);
        rc = 0;
        goto exit_send_app_type_cfg;
    }
    if ((usecase->id != USECASE_AUDIO_HFP_SCO) &&
        (usecase->id != USECASE_AUDIO_HFP_SCO_WB)) {
        ALOGV("%s: a playback path where app type cfg is not required", __func__);
        rc = 0;
        goto exit_send_app_type_cfg;
    }

    snd_device = usecase->out_snd_device;
    pcm_device_id = platform_get_pcm_device_id(usecase->id, PCM_PLAYBACK);

    snd_device = (snd_device == SND_DEVICE_OUT_SPEAKER) ?
                 audio_extn_get_spkr_prot_snd_device(snd_device) : snd_device;
    acdb_dev_id = platform_get_snd_device_acdb_id(snd_device);
    if (acdb_dev_id < 0) {
        ALOGE("%s: Couldn't get the acdb dev id", __func__);
        rc = -EINVAL;
        goto exit_send_app_type_cfg;
    }

    if (usecase->type == PCM_HFP_CALL) {

        /* config HFP session:1 playback path */
        rc = platform_get_default_app_type_v2(adev->platform, PCM_PLAYBACK, &app_type);
        if (rc < 0)
            goto exit_send_app_type_cfg;

        sample_rate= CODEC_BACKEND_DEFAULT_SAMPLE_RATE;
        rc = set_stream_app_type_mixer_ctrl(adev, pcm_device_id, app_type,
                                            acdb_dev_id, sample_rate,
                                            PCM_PLAYBACK,
                                            SND_DEVICE_NONE); // use legacy behavior
        if (rc < 0)
            goto exit_send_app_type_cfg;
        /* config HFP session:1 capture path */
        rc = platform_get_default_app_type_v2(adev->platform, PCM_CAPTURE, &app_type);

        if (rc == 0) {
            rc = set_stream_app_type_mixer_ctrl(adev, pcm_device_id, app_type,
                                                acdb_dev_id, sample_rate,
                                                PCM_CAPTURE,
                                                SND_DEVICE_NONE);
            if (rc < 0)
                goto exit_send_app_type_cfg;
        }
        /* config HFP session:2 capture path */
        pcm_device_id = HFP_ASM_RX_TX;
        snd_device = usecase->in_snd_device;
        acdb_dev_id = platform_get_snd_device_acdb_id(snd_device);
        if (acdb_dev_id <= 0) {
            ALOGE("%s: Couldn't get the acdb dev id", __func__);
            rc = -EINVAL;
            goto exit_send_app_type_cfg;
        }
        rc = platform_get_default_app_type_v2(adev->platform, PCM_CAPTURE, &app_type);
        if (rc == 0) {
            rc = set_stream_app_type_mixer_ctrl(adev, pcm_device_id, app_type,
                                                acdb_dev_id, sample_rate, PCM_CAPTURE,
                                                SND_DEVICE_NONE);
            if (rc < 0)
                goto exit_send_app_type_cfg;
        }

        /* config HFP session:2 playback path */
        rc = platform_get_default_app_type_v2(adev->platform, PCM_PLAYBACK, &app_type);
        if (rc == 0) {
            rc = set_stream_app_type_mixer_ctrl(adev, pcm_device_id, app_type,
                                acdb_dev_id, sample_rate,
                                PCM_PLAYBACK, SND_DEVICE_NONE);
            if (rc < 0)
                goto exit_send_app_type_cfg;
        }
    }

    rc = 0;
exit_send_app_type_cfg:
    return rc;
}


static int derive_capture_app_type_cfg(struct audio_device *adev,
                                       struct audio_usecase *usecase,
                                       int *app_type,
                                       int *sample_rate)
{
    if (usecase->stream.in == NULL) {
        return -1;
    }
    struct stream_in *in = usecase->stream.in;
    struct stream_app_type_cfg *app_type_cfg = &in->app_type_cfg;

    *sample_rate = DEFAULT_INPUT_SAMPLING_RATE;
    if (audio_is_usb_in_device(in->device)) {
        platform_check_and_update_copp_sample_rate(adev->platform,
                                                   usecase->in_snd_device,
                                                   in->sample_rate,
                                                   sample_rate);
    }

    app_type_cfg->mode = flags_to_mode(1 /*capture*/, in->flags);
    ALOGV("%s mode %s", __func__, app_type_cfg->mode);
    if (in->format == AUDIO_FORMAT_PCM_16_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_CAPTURE,
                                 app_type_cfg->mode,
                                 16,
                                 app_type_cfg->sample_rate,
                                 app_type);
    } else if (in->format == AUDIO_FORMAT_PCM_24_BIT_PACKED ||
               in->format == AUDIO_FORMAT_PCM_8_24_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_CAPTURE,
                                 app_type_cfg->mode,
                                 24,
                                 app_type_cfg->sample_rate,
                                 app_type);
    } else if (in->format == AUDIO_FORMAT_PCM_32_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_CAPTURE,
                                 app_type_cfg->mode,
                                 32,
                                 app_type_cfg->sample_rate,
                                 app_type);
    } else {
        ALOGE("%s bad format\n", __func__);
        return -1;
    }

    app_type_cfg->app_type = *app_type;
    app_type_cfg->sample_rate = *sample_rate;
    return 0;
}

static int derive_playback_app_type_cfg(struct audio_device *adev,
                                        struct audio_usecase *usecase,
                                        int *app_type,
                                        int *sample_rate)
{
    if (usecase->stream.out == NULL) {
        return -1;
    }
    struct stream_out *out = usecase->stream.out;
    struct stream_app_type_cfg *app_type_cfg = &out->app_type_cfg;

    *sample_rate = DEFAULT_OUTPUT_SAMPLING_RATE;

    // add speaker prot changes if needed
    // and use that to check for device
    if (audio_is_usb_out_device(out->devices)) {
        platform_check_and_update_copp_sample_rate(adev->platform,
                                                   usecase->out_snd_device,
                                                   out->sample_rate,
                                                   sample_rate);
    }

    app_type_cfg->mode = flags_to_mode(0 /*playback*/, out->flags);
    if (!audio_is_linear_pcm(out->format)) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_PLAYBACK,
                                 app_type_cfg->mode,
                                 24,
                                 *sample_rate,
                                 app_type);
        ALOGV("Non pcm got app type %d", *app_type);
    } else if (out->format == AUDIO_FORMAT_PCM_16_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_PLAYBACK,
                                 app_type_cfg->mode,
                                 16,
                                 *sample_rate,
                                 app_type);
    } else if (out->format == AUDIO_FORMAT_PCM_24_BIT_PACKED ||
               out->format == AUDIO_FORMAT_PCM_8_24_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_PLAYBACK,
                                 app_type_cfg->mode,
                                 24,
                                 *sample_rate,
                                 app_type);
    } else if (out->format == AUDIO_FORMAT_PCM_32_BIT) {
        platform_get_app_type_v2(adev->platform,
                                 PCM_PLAYBACK,
                                 app_type_cfg->mode,
                                 32,
                                 *sample_rate,
                                 app_type);
    } else {
        ALOGE("%s bad format\n", __func__);
        return -1;
    }

    app_type_cfg->app_type = *app_type;
    app_type_cfg->sample_rate = *sample_rate;
    return 0;
}

static int derive_acdb_dev_id(struct audio_device *adev __unused,
                              struct audio_usecase *usecase)
{
    struct stream_out *out;
    struct stream_in *in;

    if (usecase->type == PCM_PLAYBACK) {
        return platform_get_snd_device_acdb_id(usecase->out_snd_device);
    } else if(usecase->type == PCM_CAPTURE) {
        return platform_get_snd_device_acdb_id(usecase->in_snd_device);
    }
    return -1;
}

int audio_extn_utils_send_app_type_cfg(struct audio_device *adev,
                                       struct audio_usecase *usecase)
{
    int len = 0;
    int sample_rate;
    int app_type;
    int acdb_dev_id;
    size_t app_type_cfg[MAX_LENGTH_MIXER_CONTROL_IN_INT] = {0};
    char mixer_ctl_name[MAX_LENGTH_MIXER_CONTROL_IN_INT] = {0};
    int pcm_device_id;
    struct mixer_ctl *ctl;
    int ret;

    if (usecase->type == PCM_HFP_CALL) {
        return audio_extn_utils_send_app_type_cfg_hfp(adev, usecase);
    }

    if (!platform_supports_app_type_cfg())
        return -1;

    if (usecase->type == PCM_PLAYBACK) {
        ret = derive_playback_app_type_cfg(adev,
                                           usecase,
                                           &app_type,
                                           &sample_rate);
    } else if (usecase->type == PCM_CAPTURE) {
        ret = derive_capture_app_type_cfg(adev,
                                          usecase,
                                          &app_type,
                                          &sample_rate);
    } else {
        ALOGE("%s: Invalid uc type : 0x%x", __func__, usecase->type);
        return -1;
    }

    if (ret < 0) {
        ALOGE("%s: Failed to derive app_type for uc type : 0x%x", __func__,
              usecase->type);
        return -1;
    }

    acdb_dev_id = derive_acdb_dev_id(adev, usecase);
    if (acdb_dev_id <= 0) {
        ALOGE("%s: Couldn't get the acdb dev id", __func__);
        return -1;
    }

    pcm_device_id = platform_get_pcm_device_id(usecase->id, usecase->type);
    set_stream_app_type_mixer_ctrl(adev, pcm_device_id, app_type, acdb_dev_id,
                                   sample_rate,
                                   usecase->type,
                                   usecase->type == PCM_PLAYBACK ? usecase->out_snd_device :
                                                                   usecase->in_snd_device);
    return 0;
}

int audio_extn_utils_send_app_type_gain(struct audio_device *adev,
                                        int app_type,
                                        int *gain)
{
    int gain_cfg[4];
    const char *mixer_ctl_name = "App Type Gain";
    struct mixer_ctl *ctl;
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get volume ctl mixer %s", __func__,
              mixer_ctl_name);
        return -EINVAL;
    }
    gain_cfg[0] = 0;
    gain_cfg[1] = app_type;
    gain_cfg[2] = gain[0];
    gain_cfg[3] = gain[1];
    ALOGV("%s app_type %d l(%d) r(%d)", __func__,  app_type, gain[0], gain[1]);
    return mixer_ctl_set_array(ctl, gain_cfg,
                               sizeof(gain_cfg)/sizeof(gain_cfg[0]));
}

// this assumes correct app_type and sample_rate fields
// have been set for the stream using audio_extn_utils_send_app_type_cfg
void audio_extn_utils_send_audio_calibration(struct audio_device *adev,
                                             struct audio_usecase *usecase)
{
    int type = usecase->type;
    int app_type = 0;

    if (type == PCM_PLAYBACK && usecase->stream.out != NULL) {
        struct stream_out *out = usecase->stream.out;
        ALOGV("%s send cal for app_type %d, rate %d", __func__,
              out->app_type_cfg.app_type,
              out->app_type_cfg.sample_rate);
        platform_send_audio_calibration_v2(adev->platform, usecase,
                                           out->app_type_cfg.app_type,
                                           out->app_type_cfg.sample_rate);
    } else if (type == PCM_CAPTURE && usecase->stream.in != NULL) {
        struct stream_in *in = usecase->stream.in;
        ALOGV("%s send cal for capture app_type %d, rate %d", __func__,
              in->app_type_cfg.app_type,
              in->app_type_cfg.sample_rate);
        platform_send_audio_calibration_v2(adev->platform, usecase,
                                           in->app_type_cfg.app_type,
                                           in->app_type_cfg.sample_rate);
    } else {
        /* when app type is default. the sample rate is not used to send cal */
        platform_get_default_app_type_v2(adev->platform, type, &app_type);
        platform_send_audio_calibration_v2(adev->platform, usecase, app_type,
                                           48000);
    }
}

#define MAX_SND_CARD 8
#define RETRY_US 500000
#define RETRY_NUMBER 10

#define min(a, b) ((a) < (b) ? (a) : (b))

static const char *kConfigLocationList[] =
        {"/odm/etc", "/vendor/etc", "/system/etc"};
static const int kConfigLocationListSize =
        (sizeof(kConfigLocationList) / sizeof(kConfigLocationList[0]));

bool audio_extn_utils_resolve_config_file(char file_name[MIXER_PATH_MAX_LENGTH])
{
    char full_config_path[MIXER_PATH_MAX_LENGTH];
    for (int i = 0; i < kConfigLocationListSize; i++) {
        snprintf(full_config_path,
                 MIXER_PATH_MAX_LENGTH,
                 "%s/%s",
                 kConfigLocationList[i],
                 file_name);
        if (F_OK == access(full_config_path, 0)) {
            strcpy(file_name, full_config_path);
            return true;
        }
    }
    return false;
}

/* platform_info_file should be size 'MIXER_PATH_MAX_LENGTH' */
int audio_extn_utils_get_platform_info(const char* snd_card_name, char* platform_info_file)
{
    if (NULL == snd_card_name) {
        return -1;
    }

    struct snd_card_split *snd_split_handle = NULL;
    int ret = 0;
    audio_extn_set_snd_card_split(snd_card_name);
    snd_split_handle = audio_extn_get_snd_card_split();

    snprintf(platform_info_file, MIXER_PATH_MAX_LENGTH, "%s_%s_%s.xml",
                     PLATFORM_INFO_XML_BASE_STRING, snd_split_handle->snd_card,
                     snd_split_handle->form_factor);

    if (!audio_extn_utils_resolve_config_file(platform_info_file)) {
        memset(platform_info_file, 0, MIXER_PATH_MAX_LENGTH);
        snprintf(platform_info_file, MIXER_PATH_MAX_LENGTH, "%s_%s.xml",
                     PLATFORM_INFO_XML_BASE_STRING, snd_split_handle->snd_card);

        if (!audio_extn_utils_resolve_config_file(platform_info_file)) {
            memset(platform_info_file, 0, MIXER_PATH_MAX_LENGTH);
            strlcpy(platform_info_file, PLATFORM_INFO_XML_PATH, MIXER_PATH_MAX_LENGTH);
            ret = audio_extn_utils_resolve_config_file(platform_info_file) ? 0 : -1;
        }
    }

    return ret;
}

int audio_extn_utils_get_snd_card_num()
{

    void *hw_info = NULL;
    struct mixer *mixer = NULL;
    int retry_num = 0;
    int snd_card_num = 0;
    const char* snd_card_name = NULL;
    char platform_info_file[MIXER_PATH_MAX_LENGTH]= {0};

    struct acdb_platform_data *my_data = calloc(1, sizeof(struct acdb_platform_data));

    bool card_verifed[MAX_SND_CARD] = {0};
    const int retry_limit = property_get_int32("audio.snd_card.open.retries", RETRY_NUMBER);

    for (;;) {
        if (snd_card_num >= MAX_SND_CARD) {
            if (retry_num++ >= retry_limit) {
                ALOGE("%s: Unable to find correct sound card, aborting.", __func__);
                snd_card_num = -1;
                goto done;
            }

            snd_card_num = 0;
            usleep(RETRY_US);
            continue;
        }

        if (card_verifed[snd_card_num]) {
            ++snd_card_num;
            continue;
        }

        mixer = mixer_open(snd_card_num);

        if (!mixer) {
            ALOGE("%s: Unable to open the mixer card: %d", __func__,
               snd_card_num);
            ++snd_card_num;
            continue;
        }

        card_verifed[snd_card_num] = true;

        snd_card_name = mixer_get_name(mixer);
        hw_info = hw_info_init(snd_card_name);

        if (audio_extn_utils_get_platform_info(snd_card_name, platform_info_file) < 0) {
            ALOGE("Failed to find platform_info_file");
            goto cleanup;
        }

        /* Initialize snd card name specific ids and/or backends*/
        if (snd_card_info_init(platform_info_file, my_data,
                               &acdb_set_parameters) < 0) {
            ALOGE("Failed to find platform_info_file");
            goto cleanup;
        }

        /* validate the sound card name
         * my_data->snd_card_name can contain
         *     <a> complete sound card name, i.e. <device>-<codec>-<form_factor>-snd-card
         *         example: msm8994-tomtom-mtp-snd-card
         *     <b> or sub string of the card name, i.e. <device>-<codec>
         *         example: msm8994-tomtom
         * snd_card_name is truncated to 32 charaters as per mixer_get_name() implementation
         * so use min of my_data->snd_card_name and snd_card_name length for comparison
         */

        if (my_data->snd_card_name != NULL &&
                strncmp(snd_card_name, my_data->snd_card_name,
                        min(strlen(snd_card_name), strlen(my_data->snd_card_name))) != 0) {
            ALOGI("%s: found valid sound card %s, but not primary sound card %s",
                   __func__, snd_card_name, my_data->snd_card_name);
            goto cleanup;
        }

        ALOGI("%s: found sound card %s, primary sound card expected is %s",
              __func__, snd_card_name, my_data->snd_card_name);
        break;
  cleanup:
        ++snd_card_num;
        mixer_close(mixer);
        mixer = NULL;
        hw_info_deinit(hw_info);
        hw_info = NULL;
    }

done:
    mixer_close(mixer);
    hw_info_deinit(hw_info);

    if (my_data)
        free(my_data);

    return snd_card_num;
}
