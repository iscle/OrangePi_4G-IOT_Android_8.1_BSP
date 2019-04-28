/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "audio_hw_hfp"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <errno.h>
#include <math.h>
#include <cutils/log.h>

#include "audio_hw.h"
#include "platform.h"
#include "platform_api.h"
#include <stdlib.h>
#include <cutils/str_parms.h>
#include "audio_extn/tfa_98xx.h"
#include "audio_extn.h"

#define AUDIO_PARAMETER_HFP_ENABLE            "hfp_enable"
#define AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE "hfp_set_sampling_rate"
#define AUDIO_PARAMETER_KEY_HFP_VOLUME        "hfp_volume"
#define AUDIO_PARAMETER_HFP_VOL_MIXER_CTL     "hfp_vol_mixer_ctl"
#define AUDIO_PARAMATER_HFP_VALUE_MAX         128

#define AUDIO_PARAMETER_KEY_HFP_MIC_VOLUME "hfp_mic_volume"
#define PLAYBACK_VOLUME_MAX 0x2000
#define CAPTURE_VOLUME_DEFAULT                (15.0)

static int32_t start_hfp(struct audio_device *adev,
                               struct str_parms *parms);

static int32_t stop_hfp(struct audio_device *adev);

struct hfp_module {
    struct pcm *hfp_sco_rx;
    struct pcm *hfp_sco_tx;
    struct pcm *hfp_pcm_rx;
    struct pcm *hfp_pcm_tx;
    float  hfp_volume;
    float  mic_volume;
    char   hfp_vol_mixer_ctl[AUDIO_PARAMATER_HFP_VALUE_MAX];
    bool   is_hfp_running;
    bool   mic_mute;
    audio_usecase_t ucid;
};

static struct hfp_module hfpmod = {
    .hfp_sco_rx = NULL,
    .hfp_sco_tx = NULL,
    .hfp_pcm_rx = NULL,
    .hfp_pcm_tx = NULL,
    .hfp_volume = 0,
    .mic_volume = CAPTURE_VOLUME_DEFAULT,
    .hfp_vol_mixer_ctl = {0, },
    .is_hfp_running = 0,
    .mic_mute = 0,
    .ucid = USECASE_AUDIO_HFP_SCO,
};
static struct pcm_config pcm_config_hfp = {
    .channels = 1,
    .rate = 8000,
    .period_size = 240,
    .period_count = 2,
    .format = PCM_FORMAT_S16_LE,
    .start_threshold = 0,
    .stop_threshold = INT_MAX,
    .avail_min = 0,
};

static int32_t hfp_set_volume(struct audio_device *adev, float value)
{
    int32_t vol, ret = 0;
    struct mixer_ctl *ctl;

    ALOGV("%s: entry", __func__);
    ALOGD("%s: (%f)\n", __func__, value);

    hfpmod.hfp_volume = value;
    audio_extn_tfa_98xx_set_voice_vol(value);

    if (value < 0.0) {
        ALOGW("%s: (%f) Under 0.0, assuming 0.0\n", __func__, value);
        value = 0.0;
    } else {
        value = ((value > 15.000000) ? 1.0 : (value / 15));
        ALOGW("%s: Volume brought with in range (%f)\n", __func__, value);
    }
    vol  = lrint((value * 0x2000) + 0.5);

    if (!hfpmod.is_hfp_running) {
        ALOGV("%s: HFP not active, ignoring set_hfp_volume call", __func__);
        return -EIO;
    }

    ALOGD("%s: Setting HFP volume to %d \n", __func__, vol);
    if (0 == hfpmod.hfp_vol_mixer_ctl[0]) {
#ifdef EXTERNAL_BT_SUPPORTED
        strcpy(hfpmod.hfp_vol_mixer_ctl, "PRI AUXPCM LOOPBACK Volume");
#else
        strcpy(hfpmod.hfp_vol_mixer_ctl, "Internal HFP RX Volume");
#endif
        ALOGW("%s: Defaulting hfp mixer control to: %s",
                 __func__, hfpmod.hfp_vol_mixer_ctl);
    }
    ctl = mixer_get_ctl_by_name(adev->mixer, hfpmod.hfp_vol_mixer_ctl);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, hfpmod.hfp_vol_mixer_ctl);
        return -EINVAL;
    }
    if(mixer_ctl_set_value(ctl, 0, vol) < 0) {
        ALOGE("%s: Couldn't set HFP Volume: [%d]", __func__, vol);
        return -EINVAL;
    }

    ALOGV("%s: exit", __func__);
    return ret;
}


/*Set mic volume to value.
*
* This interface is used for mic volume control, set mic volume as value(range 0 ~ 15).
*/
static int hfp_set_mic_volume(struct audio_device *adev, float value)
{
    int volume, ret = 0;
    char mixer_ctl_name[128];
    struct mixer_ctl *ctl;
    int pcm_device_id = HFP_ASM_RX_TX;

    if (!hfpmod.is_hfp_running) {
        ALOGE("%s: HFP not active, ignoring set_hfp_mic_volume call", __func__);
        return -EIO;
    }

    if (value < 0.0) {
        ALOGW("%s: (%f) Under 0.0, assuming 0.0\n", __func__, value);
        value = 0.0;
    } else if (value > CAPTURE_VOLUME_DEFAULT) {
        value = CAPTURE_VOLUME_DEFAULT;
        ALOGW("%s: Volume brought within range (%f)\n", __func__, value);
    }

    value = value / CAPTURE_VOLUME_DEFAULT;
    memset(mixer_ctl_name, 0, sizeof(mixer_ctl_name));
    snprintf(mixer_ctl_name, sizeof(mixer_ctl_name),
             "Playback %d Volume", pcm_device_id);
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }
    volume = (int)(value * PLAYBACK_VOLUME_MAX);

    ALOGD("%s: Setting volume to %d (%s)\n", __func__, volume, mixer_ctl_name);
    if (mixer_ctl_set_value(ctl, 0, volume) < 0) {
        ALOGE("%s: Couldn't set HFP Volume: [%d]", __func__, volume);
        return -EINVAL;
    }

    return ret;
}

static float hfp_get_mic_volume(struct audio_device *adev)
{
    int volume, ret = 0;
    char mixer_ctl_name[128];
    struct mixer_ctl *ctl;
    int pcm_device_id = HFP_ASM_RX_TX;
    float value = 0.0;

    if (!hfpmod.is_hfp_running) {
        ALOGE("%s: HFP not active, ignoring set_hfp_mic_volume call", __func__);
        return -EIO;
    }

    memset(mixer_ctl_name, 0, sizeof(mixer_ctl_name));
    snprintf(mixer_ctl_name, sizeof(mixer_ctl_name),
             "Playback %d Volume", pcm_device_id);
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }

    volume = mixer_ctl_get_value(ctl, 0);
    if ( volume < 0) {
        ALOGE("%s: Couldn't set HFP Volume: [%d]", __func__, volume);
        return -EINVAL;
    }
    ALOGD("%s: getting mic volume %d \n", __func__, volume);

    value = (volume / PLAYBACK_VOLUME_MAX) * CAPTURE_VOLUME_DEFAULT;
    if (value < 0.0) {
        ALOGW("%s: (%f) Under 0.0, assuming 0.0\n", __func__, value);
        value = 0.0;
    } else if (value > CAPTURE_VOLUME_DEFAULT) {
        value = CAPTURE_VOLUME_DEFAULT;
        ALOGW("%s: Volume brought within range (%f)\n", __func__, value);
    }

    return value;
}

/*Set mic mute state.
*
* This interface is used for mic mute state control
*/
int audio_extn_hfp_set_mic_mute(struct audio_device *adev, bool state)
{
    int rc = 0;

    if (state == hfpmod.mic_mute)
        return rc;

    if (state == true) {
        hfpmod.mic_volume = hfp_get_mic_volume(adev);
    }
    rc = hfp_set_mic_volume(adev, (state == true) ? 0.0 : hfpmod.mic_volume);
    adev->voice.mic_mute = state;
    hfpmod.mic_mute = state;
    ALOGD("%s: Setting mute state %d, rc %d\n", __func__, state, rc);
    return rc;
}

static int32_t start_hfp(struct audio_device *adev,
                         struct str_parms *parms __unused)
{
    int32_t i, ret = 0;
    struct audio_usecase *uc_info;
    int32_t pcm_dev_rx_id, pcm_dev_tx_id, pcm_dev_asm_rx_id, pcm_dev_asm_tx_id;

    ALOGD("%s: enter", __func__);

    if (adev->enable_hfp == true) {
        ALOGD("%s: HFP is already active!\n", __func__);
        return 0;
    }
    adev->enable_hfp = true;
    platform_set_mic_mute(adev->platform, false);

    uc_info = (struct audio_usecase *)calloc(1, sizeof(struct audio_usecase));
    uc_info->id = hfpmod.ucid;
    uc_info->type = PCM_HFP_CALL;
    uc_info->stream.out = adev->primary_output;
    uc_info->devices = adev->primary_output->devices;
    uc_info->in_snd_device = SND_DEVICE_NONE;
    uc_info->out_snd_device = SND_DEVICE_NONE;

    list_add_tail(&adev->usecase_list, &uc_info->list);

    audio_extn_tfa_98xx_set_mode_bt();

    select_devices(adev, hfpmod.ucid);

    pcm_dev_rx_id = platform_get_pcm_device_id(uc_info->id, PCM_PLAYBACK);
    pcm_dev_tx_id = platform_get_pcm_device_id(uc_info->id, PCM_CAPTURE);
    pcm_dev_asm_rx_id = HFP_ASM_RX_TX;
    pcm_dev_asm_tx_id = HFP_ASM_RX_TX;
    if (pcm_dev_rx_id < 0 || pcm_dev_tx_id < 0 ||
        pcm_dev_asm_rx_id < 0 || pcm_dev_asm_tx_id < 0 ) {
        ALOGE("%s: Invalid PCM devices (rx: %d tx: %d asm: rx tx %d) for the usecase(%d)",
              __func__, pcm_dev_rx_id, pcm_dev_tx_id, pcm_dev_asm_rx_id, uc_info->id);
        ret = -EIO;
        goto exit;
    }

    ALOGV("%s: HFP PCM devices (hfp rx tx: %d pcm rx tx: %d) for the usecase(%d)",
              __func__, pcm_dev_rx_id, pcm_dev_tx_id, uc_info->id);

    ALOGV("%s: Opening PCM playback device card_id(%d) device_id(%d)",
          __func__, adev->snd_card, pcm_dev_rx_id);
    hfpmod.hfp_sco_rx = pcm_open(adev->snd_card,
                                  pcm_dev_asm_rx_id,
                                  PCM_OUT, &pcm_config_hfp);
    if (hfpmod.hfp_sco_rx && !pcm_is_ready(hfpmod.hfp_sco_rx)) {
        ALOGE("%s: %s", __func__, pcm_get_error(hfpmod.hfp_sco_rx));
        ret = -EIO;
        goto exit;
    }
    ALOGD("%s: Opening PCM capture device card_id(%d) device_id(%d)",
          __func__, adev->snd_card, pcm_dev_tx_id);

    if (audio_extn_tfa_98xx_is_supported() == false) {
        hfpmod.hfp_pcm_rx = pcm_open(adev->snd_card,
                                       pcm_dev_rx_id,
                                       PCM_OUT, &pcm_config_hfp);
        if (hfpmod.hfp_pcm_rx && !pcm_is_ready(hfpmod.hfp_pcm_rx)) {
            ALOGE("%s: %s", __func__, pcm_get_error(hfpmod.hfp_pcm_rx));
            ret = -EIO;
            goto exit;
        }
    }
    hfpmod.hfp_sco_tx = pcm_open(adev->snd_card,
                                  pcm_dev_asm_tx_id,
                                  PCM_IN, &pcm_config_hfp);
    if (hfpmod.hfp_sco_tx && !pcm_is_ready(hfpmod.hfp_sco_tx)) {
        ALOGE("%s: %s", __func__, pcm_get_error(hfpmod.hfp_sco_tx));
        ret = -EIO;
        goto exit;
    }
    ALOGV("%s: Opening PCM capture device card_id(%d) device_id(%d)",
          __func__, adev->snd_card, pcm_dev_tx_id);

    if (audio_extn_tfa_98xx_is_supported() == false) {
        hfpmod.hfp_pcm_tx = pcm_open(adev->snd_card,
                                       pcm_dev_tx_id,
                                       PCM_IN, &pcm_config_hfp);
        if (hfpmod.hfp_pcm_tx && !pcm_is_ready(hfpmod.hfp_pcm_tx)) {
            ALOGE("%s: %s", __func__, pcm_get_error(hfpmod.hfp_pcm_tx));
            ret = -EIO;
            goto exit;
        }
    }
    pcm_start(hfpmod.hfp_sco_rx);
    pcm_start(hfpmod.hfp_sco_tx);
    if (audio_extn_tfa_98xx_is_supported() == false) {
        pcm_start(hfpmod.hfp_pcm_rx);
        pcm_start(hfpmod.hfp_pcm_tx);
    }

    audio_extn_tfa_98xx_enable_speaker();

    hfpmod.is_hfp_running = true;
    hfp_set_volume(adev, hfpmod.hfp_volume);

    /* Set mic volume by mute status, we don't provide set mic volume in phone app, only
    provide mute and unmute. */
    audio_extn_hfp_set_mic_mute(adev, adev->mic_muted);

    ALOGD("%s: exit: status(%d)", __func__, ret);
    return 0;

exit:
    stop_hfp(adev);
    ALOGE("%s: Problem in HFP start: status(%d)", __func__, ret);
    return ret;
}

static int32_t stop_hfp(struct audio_device *adev)
{
    int32_t i, ret = 0;
    struct audio_usecase *uc_info;

    ALOGD("%s: enter", __func__);
    hfpmod.is_hfp_running = false;

    /* 1. Close the PCM devices */
    if (hfpmod.hfp_sco_rx) {
        pcm_close(hfpmod.hfp_sco_rx);
        hfpmod.hfp_sco_rx = NULL;
    }
    if (hfpmod.hfp_sco_tx) {
        pcm_close(hfpmod.hfp_sco_tx);
        hfpmod.hfp_sco_tx = NULL;
    }
    if (hfpmod.hfp_pcm_rx) {
        pcm_close(hfpmod.hfp_pcm_rx);
        hfpmod.hfp_pcm_rx = NULL;
    }
    if (hfpmod.hfp_pcm_tx) {
        pcm_close(hfpmod.hfp_pcm_tx);
        hfpmod.hfp_pcm_tx = NULL;
    }

    uc_info = get_usecase_from_list(adev, hfpmod.ucid);
    if (uc_info == NULL) {
        ALOGE("%s: Could not find the usecase (%d) in the list",
              __func__, hfpmod.ucid);
        return -EINVAL;
    }

    /* 2. Get and set stream specific mixer controls */
    disable_audio_route(adev, uc_info);

    /* 3. Disable the rx and tx devices */
    disable_snd_device(adev, uc_info->out_snd_device);
    disable_snd_device(adev, uc_info->in_snd_device);

    /* Disable the echo reference for HFP Tx */
    platform_set_echo_reference(adev, false, AUDIO_DEVICE_NONE);

    /* Set the unmute Tx mixer control */
    if (voice_get_mic_mute(adev)) {
        platform_set_mic_mute(adev->platform, false);
        ALOGD("%s: unMute HFP Tx", __func__);
    }
    adev->enable_hfp = false;

    list_remove(&uc_info->list);
    free(uc_info);

    ALOGD("%s: exit: status(%d)", __func__, ret);
    return ret;
}

bool audio_extn_hfp_is_active(struct audio_device *adev)
{
    struct audio_usecase *hfp_usecase = NULL;
    hfp_usecase = get_usecase_from_list(adev, hfpmod.ucid);

    if (hfp_usecase != NULL)
        return true;
    else
        return false;
}

audio_usecase_t audio_extn_hfp_get_usecase()
{
    return hfpmod.ucid;
}

void audio_extn_hfp_set_parameters(struct audio_device *adev, struct str_parms *parms)
{
    int ret;
    int rate;
    int val;
    float vol;
    char value[AUDIO_PARAMATER_HFP_VALUE_MAX] = {0, };

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_HFP_ENABLE, value,
                            sizeof(value));
    if (ret >= 0) {
           if (!strncmp(value,"true",sizeof(value)))
               ret = start_hfp(adev,parms);
           else
               stop_hfp(adev);
    }
    memset(value, 0, sizeof(value));
    ret = str_parms_get_str(parms,AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE, value,
                            sizeof(value));
    if (ret >= 0) {
           rate = atoi(value);
           if (rate == 8000){
               hfpmod.ucid = USECASE_AUDIO_HFP_SCO;
               pcm_config_hfp.rate = rate;
           } else if (rate == 16000){
               hfpmod.ucid = USECASE_AUDIO_HFP_SCO_WB;
               pcm_config_hfp.rate = rate;
           } else
               ALOGE("Unsupported rate..");
    }

    if (hfpmod.is_hfp_running) {
        memset(value, 0, sizeof(value));
        ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING,
                                value, sizeof(value));
        if (ret >= 0) {
            val = atoi(value);
            if (val > 0)
                select_devices(adev, hfpmod.ucid);
        }
    }

    memset(value, 0, sizeof(value));
    ret = str_parms_get_str(parms, AUDIO_PARAMETER_HFP_VOL_MIXER_CTL,
                          value, sizeof(value));
    if (ret >= 0) {
        ALOGD("%s: mixer ctl name: %s", __func__, value);
        strcpy(hfpmod.hfp_vol_mixer_ctl, value);
        str_parms_del(parms, AUDIO_PARAMETER_HFP_VOL_MIXER_CTL);
    }

    memset(value, 0, sizeof(value));
    ret = str_parms_get_str(parms, AUDIO_PARAMETER_KEY_HFP_VOLUME,
                            value, sizeof(value));
    if (ret >= 0) {
        if (sscanf(value, "%f", &vol) != 1){
            ALOGE("%s: error in retrieving hfp volume", __func__);
            ret = -EIO;
            goto exit;
        }
        ALOGD("%s: set_hfp_volume usecase, Vol: [%f]", __func__, vol);
        hfp_set_volume(adev, vol);
    }

    memset(value, 0, sizeof(value));
    ret = str_parms_get_str(parms, AUDIO_PARAMETER_KEY_HFP_MIC_VOLUME,
                            value, sizeof(value));
    if (ret >= 0) {
        if (sscanf(value, "%f", &vol) != 1){
            ALOGE("%s: error in retrieving hfp mic volume", __func__);
            ret = -EIO;
            goto exit;
        }
        ALOGD("%s: set_hfp_mic_volume usecase, Vol: [%f]", __func__, vol);
        hfp_set_mic_volume(adev, vol);
    }

exit:
    ALOGV("%s Exit",__func__);
}
