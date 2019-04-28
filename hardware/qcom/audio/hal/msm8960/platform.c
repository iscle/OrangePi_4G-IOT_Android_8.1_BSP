/*
 * Copyright (C) 2013-2014 The Android Open Source Project
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

#define LOG_TAG "msm8960_platform"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <stdlib.h>
#include <dlfcn.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <audio_hw.h>
#include <platform_api.h>
#include "platform.h"
#include "audio_extn.h"

#define LIB_ACDB_LOADER "libacdbloader.so"
#define LIB_CSD_CLIENT "libcsd-client.so"

#define DUALMIC_CONFIG_NONE 0      /* Target does not contain 2 mics */
#define DUALMIC_CONFIG_ENDFIRE 1
#define DUALMIC_CONFIG_BROADSIDE 2

/*
 * This is the sysfs path for the HDMI audio data block
 */
#define AUDIO_DATA_BLOCK_PATH "/sys/class/graphics/fb1/audio_data_block"
#define MIXER_XML_PATH "/system/etc/mixer_paths.xml"

/*
 * This file will have a maximum of 38 bytes:
 *
 * 4 bytes: number of audio blocks
 * 4 bytes: total length of Short Audio Descriptor (SAD) blocks
 * Maximum 10 * 3 bytes: SAD blocks
 */
#define MAX_SAD_BLOCKS      10
#define SAD_BLOCK_SIZE      3

/* EDID format ID for LPCM audio */
#define EDID_FORMAT_LPCM    1

struct audio_block_header
{
    int reserved;
    int length;
};


typedef void (*acdb_deallocate_t)();
typedef int  (*acdb_init_t)();
typedef void (*acdb_send_audio_cal_t)(int, int);
typedef void (*acdb_send_voice_cal_t)(int, int);

typedef int (*csd_client_init_t)();
typedef int (*csd_client_deinit_t)();
typedef int (*csd_disable_device_t)();
typedef int (*csd_enable_device_t)(int, int, uint32_t);
typedef int (*csd_volume_t)(int);
typedef int (*csd_mic_mute_t)(int);
typedef int (*csd_start_voice_t)();
typedef int (*csd_stop_voice_t)();


/* Audio calibration related functions */
struct platform_data {
    struct audio_device *adev;
    bool fluence_in_spkr_mode;
    bool fluence_in_voice_call;
    bool fluence_in_voice_rec;
    int  dualmic_config;
    bool speaker_lr_swap;

    void *acdb_handle;
    acdb_init_t acdb_init;
    acdb_deallocate_t acdb_deallocate;
    acdb_send_audio_cal_t acdb_send_audio_cal;
    acdb_send_voice_cal_t acdb_send_voice_cal;

    /* CSD Client related functions for voice call */
    void *csd_client;
    csd_client_init_t csd_client_init;
    csd_client_deinit_t csd_client_deinit;
    csd_disable_device_t csd_disable_device;
    csd_enable_device_t csd_enable_device;
    csd_volume_t csd_volume;
    csd_mic_mute_t csd_mic_mute;
    csd_start_voice_t csd_start_voice;
    csd_stop_voice_t csd_stop_voice;
};

static const int pcm_device_table[AUDIO_USECASE_MAX][2] = {
    [USECASE_AUDIO_PLAYBACK_DEEP_BUFFER] = {0, 0},
    [USECASE_AUDIO_PLAYBACK_LOW_LATENCY] = {14, 14},
    [USECASE_AUDIO_PLAYBACK_HIFI] = {1, 1},
    [USECASE_AUDIO_RECORD] = {0, 0},
    [USECASE_AUDIO_RECORD_LOW_LATENCY] = {14, 14},
    [USECASE_VOICE_CALL] = {12, 12},
};

/* Array to store sound devices */
static const char * const device_table[SND_DEVICE_MAX] = {
    [SND_DEVICE_NONE] = "none",
    /* Playback sound devices */
    [SND_DEVICE_OUT_HANDSET] = "handset",
    [SND_DEVICE_OUT_SPEAKER] = "speaker",
    [SND_DEVICE_OUT_SPEAKER_REVERSE] = "speaker-reverse",
    [SND_DEVICE_OUT_HEADPHONES] = "headphones",
    [SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES] = "speaker-and-headphones",
    [SND_DEVICE_OUT_VOICE_SPEAKER] = "voice-speaker",
    [SND_DEVICE_OUT_VOICE_HEADPHONES] = "voice-headphones",
    [SND_DEVICE_OUT_HDMI] = "hdmi",
    [SND_DEVICE_OUT_SPEAKER_AND_HDMI] = "speaker-and-hdmi",
    [SND_DEVICE_OUT_BT_SCO] = "bt-sco-headset",
    [SND_DEVICE_OUT_BT_SCO_WB] = "bt-sco-headset-wb",
    [SND_DEVICE_OUT_VOICE_HANDSET_TMUS] = "voice-handset-tmus",
    [SND_DEVICE_OUT_VOICE_HANDSET] = "voice-handset-tmus",
    [SND_DEVICE_OUT_VOICE_HAC_HANDSET] = "voice-handset-tmus",
    [SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES] = "voice-tty-full-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES] = "voice-tty-vco-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET] = "voice-tty-hco-handset",
    [SND_DEVICE_OUT_USB_HEADSET] = "usb-headset",
    [SND_DEVICE_OUT_USB_HEADPHONES] = "usb-headphones",
    [SND_DEVICE_OUT_VOICE_USB_HEADSET] = "usb-headset",
    [SND_DEVICE_OUT_VOICE_USB_HEADPHONES] = "usb-headphones",


    /* Capture sound devices */
    [SND_DEVICE_IN_HANDSET_MIC] = "handset-mic",
    [SND_DEVICE_IN_SPEAKER_MIC] = "speaker-mic",
    [SND_DEVICE_IN_HEADSET_MIC] = "headset-mic",
    [SND_DEVICE_IN_HANDSET_MIC_AEC] = "handset-mic",
    [SND_DEVICE_IN_SPEAKER_MIC_AEC] = "voice-speaker-mic",
    [SND_DEVICE_IN_HEADSET_MIC_AEC] = "headset-mic",
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC] = "voice-speaker-mic",
    [SND_DEVICE_IN_VOICE_HEADSET_MIC] = "voice-headset-mic",
    [SND_DEVICE_IN_HDMI_MIC] = "hdmi-mic",
    [SND_DEVICE_IN_BT_SCO_MIC] = "bt-sco-mic",
    [SND_DEVICE_IN_BT_SCO_MIC_WB] = "bt-sco-mic-wb",
    [SND_DEVICE_IN_CAMCORDER_MIC] = "camcorder-mic",
    [SND_DEVICE_IN_VOICE_DMIC_EF] = "voice-dmic-ef",
    [SND_DEVICE_IN_VOICE_DMIC_BS] = "voice-dmic-bs",
    [SND_DEVICE_IN_VOICE_DMIC_EF_TMUS] = "voice-dmic-ef-tmus",
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_EF] = "voice-speaker-dmic-ef",
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BS] = "voice-speaker-dmic-bs",
    [SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC] = "voice-tty-full-headset-mic",
    [SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC] = "voice-tty-vco-handset-mic",
    [SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC] = "voice-tty-hco-headset-mic",
    [SND_DEVICE_IN_VOICE_REC_MIC] = "voice-rec-mic",
    [SND_DEVICE_IN_VOICE_REC_DMIC_EF] = "voice-rec-dmic-ef",
    [SND_DEVICE_IN_VOICE_REC_DMIC_BS] = "voice-rec-dmic-bs",
    [SND_DEVICE_IN_VOICE_REC_DMIC_EF_FLUENCE] = "voice-rec-dmic-ef-fluence",
    [SND_DEVICE_IN_VOICE_REC_DMIC_BS_FLUENCE] = "voice-rec-dmic-bs-fluence",
};

/* ACDB IDs (audio DSP path configuration IDs) for each sound device */
static const int acdb_device_table[SND_DEVICE_MAX] = {
    [SND_DEVICE_NONE] = -1,
    [SND_DEVICE_OUT_HANDSET] = 7,
    [SND_DEVICE_OUT_SPEAKER] = 14,
    [SND_DEVICE_OUT_SPEAKER_REVERSE] = 14,
    [SND_DEVICE_OUT_HEADPHONES] = 10,
    [SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES] = 10,
    [SND_DEVICE_OUT_VOICE_SPEAKER] = 14,
    [SND_DEVICE_OUT_VOICE_HEADPHONES] = 10,
    [SND_DEVICE_OUT_HDMI] = 18,
    [SND_DEVICE_OUT_SPEAKER_AND_HDMI] = 14,
    [SND_DEVICE_OUT_BT_SCO] = 22,
    [SND_DEVICE_OUT_BT_SCO_WB] = 39,
    [SND_DEVICE_OUT_VOICE_HANDSET_TMUS] = 81,
    [SND_DEVICE_OUT_VOICE_HANDSET] = 81,
    [SND_DEVICE_OUT_VOICE_HAC_HANDSET] = 81,
    [SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES] = 17,
    [SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES] = 17,
    [SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET] = 37,
    [SND_DEVICE_OUT_USB_HEADSET] = 45,
    [SND_DEVICE_OUT_USB_HEADPHONES] = 45,
    [SND_DEVICE_OUT_VOICE_USB_HEADSET] = 45,
    [SND_DEVICE_OUT_VOICE_USB_HEADPHONES] = 45,

    [SND_DEVICE_IN_HANDSET_MIC] = 4,
    [SND_DEVICE_IN_SPEAKER_MIC] = 4,
    [SND_DEVICE_IN_HEADSET_MIC] = 8,
    [SND_DEVICE_IN_HANDSET_MIC_AEC] = 40,
    [SND_DEVICE_IN_SPEAKER_MIC_AEC] = 42,
    [SND_DEVICE_IN_HEADSET_MIC_AEC] = 47,
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC] = 11,
    [SND_DEVICE_IN_VOICE_HEADSET_MIC] = 8,
    [SND_DEVICE_IN_HDMI_MIC] = 4,
    [SND_DEVICE_IN_BT_SCO_MIC] = 21,
    [SND_DEVICE_IN_BT_SCO_MIC_WB] = 38,
    [SND_DEVICE_IN_CAMCORDER_MIC] = 61,
    [SND_DEVICE_IN_VOICE_DMIC_EF] = 6,
    [SND_DEVICE_IN_VOICE_DMIC_BS] = 5,
    [SND_DEVICE_IN_VOICE_DMIC_EF_TMUS] = 91,
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_EF] = 13,
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BS] = 12,
    [SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC] = 16,
    [SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC] = 36,
    [SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC] = 16,
    [SND_DEVICE_IN_VOICE_REC_MIC] = 62,
    /* TODO: Update with proper acdb ids */
    [SND_DEVICE_IN_VOICE_REC_DMIC_EF] = 62,
    [SND_DEVICE_IN_VOICE_REC_DMIC_BS] = 62,
    [SND_DEVICE_IN_VOICE_REC_DMIC_EF_FLUENCE] = 6,
    [SND_DEVICE_IN_VOICE_REC_DMIC_BS_FLUENCE] = 5,
};

#define DEEP_BUFFER_PLATFORM_DELAY (29*1000LL)
#define LOW_LATENCY_PLATFORM_DELAY (13*1000LL)

static pthread_once_t check_op_once_ctl = PTHREAD_ONCE_INIT;
static bool is_tmus = false;

static void check_operator()
{
    char value[PROPERTY_VALUE_MAX];
    int mccmnc;
    property_get("gsm.sim.operator.numeric",value,"0");
    mccmnc = atoi(value);
    ALOGD("%s: tmus mccmnc %d", __func__, mccmnc);
    switch(mccmnc) {
    /* TMUS MCC(310), MNC(490, 260, 026) */
    case 310490:
    case 310260:
    case 310026:
        is_tmus = true;
        break;
    }
}

bool is_operator_tmus()
{
    pthread_once(&check_op_once_ctl, check_operator);
    return is_tmus;
}

static int set_echo_reference(struct mixer *mixer, const char* ec_ref)
{
    struct mixer_ctl *ctl;
    const char *mixer_ctl_name = "EC_REF_RX";

    ctl = mixer_get_ctl_by_name(mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }
    ALOGV("Setting EC Reference: %s", ec_ref);
    mixer_ctl_set_enum_by_string(ctl, ec_ref);
    return 0;
}

void *platform_init(struct audio_device *adev)
{
    char platform[PROPERTY_VALUE_MAX];
    char baseband[PROPERTY_VALUE_MAX];
    char value[PROPERTY_VALUE_MAX];
    struct platform_data *my_data;

    adev->mixer = mixer_open(MIXER_CARD);

    if (!adev->mixer) {
        ALOGE("Unable to open the mixer, aborting.");
        return NULL;
    }

    adev->audio_route = audio_route_init(MIXER_CARD, MIXER_XML_PATH);
    if (!adev->audio_route) {
        ALOGE("%s: Failed to init audio route controls, aborting.", __func__);
        return NULL;
    }

    my_data = calloc(1, sizeof(struct platform_data));

    my_data->adev = adev;
    my_data->dualmic_config = DUALMIC_CONFIG_NONE;
    my_data->fluence_in_spkr_mode = false;
    my_data->fluence_in_voice_call = false;
    my_data->fluence_in_voice_rec = false;

    property_get("persist.audio.dualmic.config",value,"");
    if (!strcmp("broadside", value)) {
        my_data->dualmic_config = DUALMIC_CONFIG_BROADSIDE;
        adev->acdb_settings |= DMIC_FLAG;
    } else if (!strcmp("endfire", value)) {
        my_data->dualmic_config = DUALMIC_CONFIG_ENDFIRE;
        adev->acdb_settings |= DMIC_FLAG;
    }

    if (my_data->dualmic_config != DUALMIC_CONFIG_NONE) {
        property_get("persist.audio.fluence.voicecall",value,"");
        if (!strcmp("true", value)) {
            my_data->fluence_in_voice_call = true;
        }

        property_get("persist.audio.fluence.voicerec",value,"");
        if (!strcmp("true", value)) {
            my_data->fluence_in_voice_rec = true;
        }

        property_get("persist.audio.fluence.speaker",value,"");
        if (!strcmp("true", value)) {
            my_data->fluence_in_spkr_mode = true;
        }
    }

    my_data->acdb_handle = dlopen(LIB_ACDB_LOADER, RTLD_NOW);
    if (my_data->acdb_handle == NULL) {
        ALOGE("%s: DLOPEN failed for %s", __func__, LIB_ACDB_LOADER);
    } else {
        ALOGV("%s: DLOPEN successful for %s", __func__, LIB_ACDB_LOADER);
        my_data->acdb_deallocate = (acdb_deallocate_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_deallocate_ACDB");
        my_data->acdb_send_audio_cal = (acdb_send_audio_cal_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_send_audio_cal");
        if (!my_data->acdb_send_audio_cal)
            ALOGW("%s: Could not find the symbol acdb_send_audio_cal from %s",
                  __func__, LIB_ACDB_LOADER);
        my_data->acdb_send_voice_cal = (acdb_send_voice_cal_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_send_voice_cal");
        my_data->acdb_init = (acdb_init_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_init_ACDB");
        if (my_data->acdb_init == NULL)
            ALOGE("%s: dlsym error %s for acdb_loader_init_ACDB", __func__, dlerror());
        else
            my_data->acdb_init();
    }

    /* If platform is Fusion3, load CSD Client specific symbols
     * Voice call is handled by MDM and apps processor talks to
     * MDM through CSD Client
     */
    property_get("ro.board.platform", platform, "");
    property_get("ro.baseband", baseband, "");
    if (!strcmp("msm8960", platform) && !strcmp("mdm", baseband)) {
        my_data->csd_client = dlopen(LIB_CSD_CLIENT, RTLD_NOW);
        if (my_data->csd_client == NULL)
            ALOGE("%s: DLOPEN failed for %s", __func__, LIB_CSD_CLIENT);
    }

    if (my_data->csd_client) {
        ALOGV("%s: DLOPEN successful for %s", __func__, LIB_CSD_CLIENT);
        my_data->csd_client_deinit = (csd_client_deinit_t)dlsym(my_data->csd_client,
                                                    "csd_client_deinit");
        my_data->csd_disable_device = (csd_disable_device_t)dlsym(my_data->csd_client,
                                                    "csd_client_disable_device");
        my_data->csd_enable_device = (csd_enable_device_t)dlsym(my_data->csd_client,
                                                    "csd_client_enable_device");
        my_data->csd_start_voice = (csd_start_voice_t)dlsym(my_data->csd_client,
                                                    "csd_client_start_voice");
        my_data->csd_stop_voice = (csd_stop_voice_t)dlsym(my_data->csd_client,
                                                    "csd_client_stop_voice");
        my_data->csd_volume = (csd_volume_t)dlsym(my_data->csd_client,
                                                    "csd_client_volume");
        my_data->csd_mic_mute = (csd_mic_mute_t)dlsym(my_data->csd_client,
                                                    "csd_client_mic_mute");
        my_data->csd_client_init = (csd_client_init_t)dlsym(my_data->csd_client,
                                                    "csd_client_init");

        if (my_data->csd_client_init == NULL) {
            ALOGE("%s: dlsym error %s for csd_client_init", __func__, dlerror());
        } else {
            my_data->csd_client_init();
        }
    }

    return my_data;
}

void platform_deinit(void *platform)
{
    free(platform);
}

const char *platform_get_snd_device_name(snd_device_t snd_device)
{
    if (snd_device >= SND_DEVICE_MIN && snd_device < SND_DEVICE_MAX)
        return device_table[snd_device];
    else
        return "none";
}

void platform_add_backend_name(void *platform __unused, char *mixer_path,
                               snd_device_t snd_device)
{
    if (snd_device == SND_DEVICE_IN_BT_SCO_MIC)
        strcat(mixer_path, " bt-sco");
    else if(snd_device == SND_DEVICE_OUT_BT_SCO)
        strcat(mixer_path, " bt-sco");
    else if (snd_device == SND_DEVICE_OUT_HDMI)
        strcat(mixer_path, " hdmi");
    else if (snd_device == SND_DEVICE_OUT_SPEAKER_AND_HDMI)
        strcat(mixer_path, " speaker-and-hdmi");
    else if (snd_device == SND_DEVICE_OUT_BT_SCO_WB ||
             snd_device == SND_DEVICE_IN_BT_SCO_MIC_WB)
        strcat(mixer_path, " bt-sco-wb");
}

int platform_get_pcm_device_id(audio_usecase_t usecase, int device_type)
{
    int device_id;
    if (device_type == PCM_PLAYBACK)
        device_id = pcm_device_table[usecase][0];
    else
        device_id = pcm_device_table[usecase][1];
    return device_id;
}

int platform_get_snd_device_index(char *snd_device_index_name __unused)
{
    return -ENODEV;
}

int platform_set_snd_device_acdb_id(snd_device_t snd_device __unused,
                                    unsigned int acdb_id __unused)
{
    return -ENODEV;
}

int platform_get_default_app_type_v2(void *platform __unused, usecase_type_t type __unused,
                                     int *app_type __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

int platform_get_snd_device_acdb_id(snd_device_t snd_device __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

void platform_add_operator_specific_device(snd_device_t snd_device __unused,
                                           const char *operator __unused,
                                           const char *mixer_path __unused,
                                           unsigned int acdb_id __unused)
{
}

int platform_send_audio_calibration(void *platform, snd_device_t snd_device)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int acdb_dev_id, acdb_dev_type;

    acdb_dev_id = acdb_device_table[snd_device];
    if (acdb_dev_id < 0) {
        ALOGE("%s: Could not find acdb id for device(%d)",
              __func__, snd_device);
        return -EINVAL;
    }
    if (my_data->acdb_send_audio_cal) {
        ("%s: sending audio calibration for snd_device(%d) acdb_id(%d)",
              __func__, snd_device, acdb_dev_id);
        if (snd_device >= SND_DEVICE_OUT_BEGIN &&
                snd_device < SND_DEVICE_OUT_END)
            acdb_dev_type = ACDB_DEV_TYPE_OUT;
        else
            acdb_dev_type = ACDB_DEV_TYPE_IN;
        my_data->acdb_send_audio_cal(acdb_dev_id, acdb_dev_type);
    }
    return 0;
}

int platform_switch_voice_call_device_pre(void *platform)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int ret = 0;

    if (my_data->csd_client != NULL &&
        voice_is_in_call(my_data->adev)) {
        /* This must be called before disabling the mixer controls on APQ side */
        if (my_data->csd_disable_device == NULL) {
            ALOGE("%s: dlsym error for csd_disable_device", __func__);
        } else {
            ret = my_data->csd_disable_device();
            if (ret < 0) {
                ALOGE("%s: csd_client_disable_device, failed, error %d",
                      __func__, ret);
            }
        }
    }
    return ret;
}

int platform_switch_voice_call_device_post(void *platform,
                                           snd_device_t out_snd_device,
                                           snd_device_t in_snd_device)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int acdb_rx_id, acdb_tx_id;
    int ret = 0;

    if (my_data->csd_client) {
        if (my_data->csd_enable_device == NULL) {
            ALOGE("%s: dlsym error for csd_enable_device",
                  __func__);
        } else {
            acdb_rx_id = acdb_device_table[out_snd_device];
            acdb_tx_id = acdb_device_table[in_snd_device];

            if (acdb_rx_id > 0 || acdb_tx_id > 0) {
                ret = my_data->csd_enable_device(acdb_rx_id, acdb_tx_id,
                                                    my_data->adev->acdb_settings);
                if (ret < 0) {
                    ALOGE("%s: csd_enable_device, failed, error %d",
                          __func__, ret);
                }
            } else {
                ALOGE("%s: Incorrect ACDB IDs (rx: %d tx: %d)", __func__,
                      acdb_rx_id, acdb_tx_id);
            }
        }
    }

    return ret;
}

int platform_start_voice_call(void *platform, uint32_t vsid __unused)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int ret = 0;

    if (my_data->csd_client) {
        if (my_data->csd_start_voice == NULL) {
            ALOGE("dlsym error for csd_client_start_voice");
            ret = -ENOSYS;
        } else {
            ret = my_data->csd_start_voice();
            if (ret < 0) {
                ALOGE("%s: csd_start_voice error %d\n", __func__, ret);
            }
        }
    }

    return ret;
}

int platform_stop_voice_call(void *platform, uint32_t vsid __unused)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int ret = 0;

    if (my_data->csd_client) {
        if (my_data->csd_stop_voice == NULL) {
            ALOGE("dlsym error for csd_stop_voice");
        } else {
            ret = my_data->csd_stop_voice();
            if (ret < 0) {
                ALOGE("%s: csd_stop_voice error %d\n", __func__, ret);
            }
        }
    }

    return ret;
}

void platform_set_speaker_gain_in_combo(struct audio_device *adev __unused,
                                        snd_device_t snd_device  __unused,
                                        bool enable __unused) {
}

int platform_set_voice_volume(void *platform, int volume)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int ret = 0;

    if (my_data->csd_client) {
        if (my_data->csd_volume == NULL) {
            ALOGE("%s: dlsym error for csd_volume", __func__);
        } else {
            ret = my_data->csd_volume(volume);
            if (ret < 0) {
                ALOGE("%s: csd_volume error %d", __func__, ret);
            }
        }
    } else {
        ALOGE("%s: No CSD Client present", __func__);
    }

    return ret;
}

int platform_set_mic_mute(void *platform, bool state)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int ret = 0;

    if (my_data->adev->mode == AUDIO_MODE_IN_CALL) {
        if (my_data->csd_client) {
            if (my_data->csd_mic_mute == NULL) {
                ALOGE("%s: dlsym error for csd_mic_mute", __func__);
            } else {
                ret = my_data->csd_mic_mute(state);
                if (ret < 0) {
                    ALOGE("%s: csd_mic_mute error %d", __func__, ret);
                }
            }
        } else {
            ALOGE("%s: No CSD Client present", __func__);
        }
    }

    return ret;
}

int platform_set_device_mute(void *platform __unused, bool state __unused, char *dir __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

snd_device_t platform_get_output_snd_device(void *platform, audio_devices_t devices)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    audio_mode_t mode = adev->mode;
    snd_device_t snd_device = SND_DEVICE_NONE;

    ALOGV("%s: enter: output devices(%#x)", __func__, devices);
    if (devices == AUDIO_DEVICE_NONE ||
        devices & AUDIO_DEVICE_BIT_IN) {
        ALOGV("%s: Invalid output devices (%#x)", __func__, devices);
        goto exit;
    }

    if (voice_is_in_call(adev)) {
        if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
            devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            if (adev->voice.tty_mode == TTY_MODE_FULL)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES;
            else if (adev->voice.tty_mode == TTY_MODE_VCO)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES;
            else if (adev->voice.tty_mode == TTY_MODE_HCO)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET;
            else
                snd_device = SND_DEVICE_OUT_VOICE_HEADPHONES;
        } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            if (adev->bt_wb_speech_enabled) {
                snd_device = SND_DEVICE_OUT_BT_SCO_WB;
            } else {
                snd_device = SND_DEVICE_OUT_BT_SCO;
            }
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            snd_device = SND_DEVICE_OUT_VOICE_SPEAKER;
        } else if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
            if (is_operator_tmus())
                snd_device = SND_DEVICE_OUT_VOICE_HANDSET_TMUS;
            else
                snd_device = SND_DEVICE_OUT_HANDSET;
        }
        if (snd_device != SND_DEVICE_NONE) {
            goto exit;
        }
    }

    if (popcount(devices) == 2) {
        if (devices == (AUDIO_DEVICE_OUT_WIRED_HEADPHONE |
                        AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES;
        } else if (devices == (AUDIO_DEVICE_OUT_WIRED_HEADSET |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES;
        } else if (devices == (AUDIO_DEVICE_OUT_AUX_DIGITAL |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HDMI;
        } else {
            ALOGE("%s: Invalid combo device(%#x)", __func__, devices);
            goto exit;
        }
        if (snd_device != SND_DEVICE_NONE) {
            goto exit;
        }
    }

    if (popcount(devices) != 1) {
        ALOGE("%s: Invalid output devices(%#x)", __func__, devices);
        goto exit;
    }

    if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
        devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
        snd_device = SND_DEVICE_OUT_HEADPHONES;
    } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
        if (my_data->speaker_lr_swap)
            snd_device = SND_DEVICE_OUT_SPEAKER_REVERSE;
        else
            snd_device = SND_DEVICE_OUT_SPEAKER;
    } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
        if (adev->bt_wb_speech_enabled) {
            snd_device = SND_DEVICE_OUT_BT_SCO_WB;
        } else {
            snd_device = SND_DEVICE_OUT_BT_SCO;
        }
    } else if (devices & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
        snd_device = SND_DEVICE_OUT_HDMI ;
    } else if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
        snd_device = SND_DEVICE_OUT_HANDSET;
    } else {
        ALOGE("%s: Unknown device(s) %#x", __func__, devices);
    }
exit:
    ALOGV("%s: exit: snd_device(%s)", __func__, device_table[snd_device]);
    return snd_device;
}

snd_device_t platform_get_input_snd_device(void *platform, audio_devices_t out_device)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    audio_source_t  source = (adev->active_input == NULL) ?
                                AUDIO_SOURCE_DEFAULT : adev->active_input->source;

    audio_mode_t    mode   = adev->mode;
    audio_devices_t in_device = ((adev->active_input == NULL) ?
                                    AUDIO_DEVICE_NONE : adev->active_input->device)
                                & ~AUDIO_DEVICE_BIT_IN;
    audio_channel_mask_t channel_mask = (adev->active_input == NULL) ?
                                AUDIO_CHANNEL_IN_MONO : adev->active_input->channel_mask;
    snd_device_t snd_device = SND_DEVICE_NONE;

    ALOGV("%s: enter: out_device(%#x) in_device(%#x)",
          __func__, out_device, in_device);
    if ((out_device != AUDIO_DEVICE_NONE) && voice_is_in_call(adev)) {
        if (adev->voice.tty_mode != TTY_MODE_OFF) {
            if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
                out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
                switch (adev->voice.tty_mode) {
                case TTY_MODE_FULL:
                    snd_device = SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC;
                    break;
                case TTY_MODE_VCO:
                    snd_device = SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC;
                    break;
                case TTY_MODE_HCO:
                    snd_device = SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC;
                    break;
                default:
                    ALOGE("%s: Invalid TTY mode (%#x)", __func__, adev->voice.tty_mode);
                }
                goto exit;
            }
        }
        if (out_device & AUDIO_DEVICE_OUT_EARPIECE ||
            out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            if (my_data->fluence_in_voice_call == false) {
                snd_device = SND_DEVICE_IN_HANDSET_MIC;
            } else {
                if (my_data->dualmic_config == DUALMIC_CONFIG_ENDFIRE) {
                    if (is_operator_tmus())
                        snd_device = SND_DEVICE_IN_VOICE_DMIC_EF_TMUS;
                    else
                        snd_device = SND_DEVICE_IN_VOICE_DMIC_EF;
                } else if(my_data->dualmic_config == DUALMIC_CONFIG_BROADSIDE)
                    snd_device = SND_DEVICE_IN_VOICE_DMIC_BS;
                else
                    snd_device = SND_DEVICE_IN_HANDSET_MIC;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_VOICE_HEADSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_ALL_SCO) {
            if (adev->bt_wb_speech_enabled) {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_SPEAKER) {
            if (my_data->fluence_in_voice_call && my_data->fluence_in_spkr_mode &&
                    my_data->dualmic_config == DUALMIC_CONFIG_ENDFIRE) {
                snd_device = SND_DEVICE_IN_VOICE_SPEAKER_DMIC_EF;
            } else if (my_data->fluence_in_voice_call && my_data->fluence_in_spkr_mode &&
                    my_data->dualmic_config == DUALMIC_CONFIG_BROADSIDE) {
                snd_device = SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BS;
            } else {
                snd_device = SND_DEVICE_IN_VOICE_SPEAKER_MIC;
            }
        }
    } else if (source == AUDIO_SOURCE_CAMCORDER) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC ||
            in_device & AUDIO_DEVICE_IN_BACK_MIC) {
            snd_device = SND_DEVICE_IN_CAMCORDER_MIC;
        }
    } else if (source == AUDIO_SOURCE_VOICE_RECOGNITION) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
            if (my_data->dualmic_config == DUALMIC_CONFIG_ENDFIRE) {
                if (channel_mask == AUDIO_CHANNEL_IN_FRONT_BACK)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_EF;
                else if (my_data->fluence_in_voice_rec)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_EF_FLUENCE;
            } else if (my_data->dualmic_config == DUALMIC_CONFIG_BROADSIDE) {
                if (channel_mask == AUDIO_CHANNEL_IN_FRONT_BACK)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_BS;
                else if (my_data->fluence_in_voice_rec)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_BS_FLUENCE;
            }

            if (snd_device == SND_DEVICE_NONE) {
                snd_device = SND_DEVICE_IN_VOICE_REC_MIC;
            }
        }
    } else if (source == AUDIO_SOURCE_VOICE_COMMUNICATION ||
            mode == AUDIO_MODE_IN_COMMUNICATION) {
        if (out_device & AUDIO_DEVICE_OUT_SPEAKER)
            in_device = AUDIO_DEVICE_IN_BACK_MIC;
        if (adev->active_input) {
            if (adev->active_input->enable_aec) {
                if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
                    snd_device = SND_DEVICE_IN_SPEAKER_MIC_AEC;
                } else if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
                    snd_device = SND_DEVICE_IN_HANDSET_MIC_AEC;
                } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
                    snd_device = SND_DEVICE_IN_HEADSET_MIC_AEC;
                }
                set_echo_reference(adev->mixer, "SLIM_RX");
            } else
                set_echo_reference(adev->mixer, "NONE");
        }
    } else if (source == AUDIO_SOURCE_DEFAULT) {
        goto exit;
    }


    if (snd_device != SND_DEVICE_NONE) {
        goto exit;
    }

    if (in_device != AUDIO_DEVICE_NONE &&
            !(in_device & AUDIO_DEVICE_IN_VOICE_CALL) &&
            !(in_device & AUDIO_DEVICE_IN_COMMUNICATION)) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
            snd_device = SND_DEVICE_IN_SPEAKER_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_HEADSET_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET) {
            if (adev->bt_wb_speech_enabled) {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (in_device & AUDIO_DEVICE_IN_AUX_DIGITAL) {
            snd_device = SND_DEVICE_IN_HDMI_MIC;
        } else {
            ALOGE("%s: Unknown input device(s) %#x", __func__, in_device);
            ALOGW("%s: Using default handset-mic", __func__);
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        }
    } else {
        if (out_device & AUDIO_DEVICE_OUT_EARPIECE) {
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_HEADSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_SPEAKER) {
            snd_device = SND_DEVICE_IN_SPEAKER_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) {
            if (adev->bt_wb_speech_enabled) {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
            snd_device = SND_DEVICE_IN_HDMI_MIC;
        } else {
            ALOGE("%s: Unknown output device(s) %#x", __func__, out_device);
            ALOGW("%s: Using default handset-mic", __func__);
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        }
    }
exit:
    ALOGV("%s: exit: in_snd_device(%s)", __func__, device_table[snd_device]);
    return snd_device;
}

int platform_set_hdmi_channels(void *platform,  int channel_count)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    struct mixer_ctl *ctl;
    const char *channel_cnt_str = NULL;
    const char *mixer_ctl_name = "HDMI_RX Channels";
    switch (channel_count) {
    case 8:
        channel_cnt_str = "Eight"; break;
    case 7:
        channel_cnt_str = "Seven"; break;
    case 6:
        channel_cnt_str = "Six"; break;
    case 5:
        channel_cnt_str = "Five"; break;
    case 4:
        channel_cnt_str = "Four"; break;
    case 3:
        channel_cnt_str = "Three"; break;
    default:
        channel_cnt_str = "Two"; break;
    }
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }
    ALOGV("HDMI channel count: %s", channel_cnt_str);
    mixer_ctl_set_enum_by_string(ctl, channel_cnt_str);
    return 0;
}

int platform_edid_get_max_channels(void *platform __unused)
{
    FILE *file;
    struct audio_block_header header;
    char block[MAX_SAD_BLOCKS * SAD_BLOCK_SIZE];
    char *sad = block;
    int num_audio_blocks;
    int channel_count;
    int max_channels = 0;
    int i;

    file = fopen(AUDIO_DATA_BLOCK_PATH, "rb");
    if (file == NULL) {
        ALOGE("Unable to open '%s'", AUDIO_DATA_BLOCK_PATH);
        return 0;
    }

    /* Read audio block header */
    fread(&header, 1, sizeof(header), file);

    /* Read SAD blocks, clamping the maximum size for safety */
    if (header.length > (int)sizeof(block))
        header.length = (int)sizeof(block);
    fread(&block, header.length, 1, file);

    fclose(file);

    /* Calculate the number of SAD blocks */
    num_audio_blocks = header.length / SAD_BLOCK_SIZE;

    for (i = 0; i < num_audio_blocks; i++) {
        /* Only consider LPCM blocks */
        if ((sad[0] >> 3) != EDID_FORMAT_LPCM)
            continue;

        channel_count = (sad[0] & 0x7) + 1;
        if (channel_count > max_channels)
            max_channels = channel_count;

        /* Advance to next block */
        sad += 3;
    }

    return max_channels;
}

int platform_set_incall_recording_session_id(void *platform __unused,
                                             uint32_t session_id __unused, int rec_mode __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

int platform_stop_incall_recording_usecase(void *platform __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

int platform_start_incall_music_usecase(void *platform __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

int platform_stop_incall_music_usecase(void *platform __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

int platform_set_parameters(void *platform __unused,
                            struct str_parms *parms __unused)
{
    ALOGE("%s: Not implemented", __func__);
    return -ENOSYS;
}

/* Delay in Us */
int64_t platform_render_latency(audio_usecase_t usecase)
{
    switch (usecase) {
        case USECASE_AUDIO_PLAYBACK_DEEP_BUFFER:
            return DEEP_BUFFER_PLATFORM_DELAY;
        case USECASE_AUDIO_PLAYBACK_LOW_LATENCY:
            return LOW_LATENCY_PLATFORM_DELAY;
        default:
            return 0;
    }
}

int platform_switch_voice_call_enable_device_config(void *platform __unused,
                                                    snd_device_t out_snd_device __unused,
                                                    snd_device_t in_snd_device __unused)
{
    return 0;
}

int platform_switch_voice_call_usecase_route_post(void *platform __unused,
                                                  snd_device_t out_snd_device __unused,
                                                  snd_device_t in_snd_device __unused)
{
    return 0;
}

int platform_get_sample_rate(void *platform __unused, uint32_t *rate __unused)
{
    return -ENOSYS;
}

int platform_get_usecase_index(const char * usecase __unused)
{
    return -ENOSYS;
}

int platform_set_usecase_pcm_id(audio_usecase_t usecase __unused, int32_t type __unused,
                                int32_t pcm_id __unused)
{
    return -ENOSYS;
}

int platform_set_snd_device_backend(snd_device_t device __unused,
                                    const char *backend __unused,
                                    const char *hw_interface __unused)
{
    return -ENOSYS;
}

void platform_set_echo_reference(struct audio_device *adev __unused,
                                 bool enable __unused,
                                 audio_devices_t out_device __unused)
{
    return;
}

#define DEFAULT_NOMINAL_SPEAKER_GAIN 20
int ramp_speaker_gain(struct audio_device *adev, bool ramp_up, int target_ramp_up_gain) {
    // backup_gain: gain to try to set in case of an error during ramp
    int start_gain, end_gain, step, backup_gain, i;
    bool error = false;
    const struct mixer_ctl *ctl;
    const char *mixer_ctl_name_gain_left = "Left Speaker Gain";
    const char *mixer_ctl_name_gain_right = "Right Speaker Gain";
    struct mixer_ctl *ctl_left = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name_gain_left);
    struct mixer_ctl *ctl_right = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name_gain_right);
    if (!ctl_left || !ctl_right) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s or %s, not applying speaker gain ramp",
                      __func__, mixer_ctl_name_gain_left, mixer_ctl_name_gain_right);
        return -EINVAL;
    } else if ((mixer_ctl_get_num_values(ctl_left) != 1)
            || (mixer_ctl_get_num_values(ctl_right) != 1)) {
        ALOGE("%s: Unexpected num values for mixer cmd - %s or %s, not applying speaker gain ramp",
                              __func__, mixer_ctl_name_gain_left, mixer_ctl_name_gain_right);
        return -EINVAL;
    }
    if (ramp_up) {
        start_gain = 0;
        end_gain = target_ramp_up_gain > 0 ? target_ramp_up_gain : DEFAULT_NOMINAL_SPEAKER_GAIN;
        step = +1;
        backup_gain = end_gain;
    } else {
        // using same gain on left and right
        const int left_gain = mixer_ctl_get_value(ctl_left, 0);
        start_gain = left_gain > 0 ? left_gain : DEFAULT_NOMINAL_SPEAKER_GAIN;
        end_gain = 0;
        step = -1;
        backup_gain = start_gain;
    }
    for (i = start_gain ; i != (end_gain + step) ; i += step) {
        //ALOGV("setting speaker gain to %d", i);
        if (mixer_ctl_set_value(ctl_left, 0, i)) {
            ALOGE("%s: error setting %s to %d during gain ramp",
                    __func__, mixer_ctl_name_gain_left, i);
            error = true;
            break;
        }
        if (mixer_ctl_set_value(ctl_right, 0, i)) {
            ALOGE("%s: error setting %s to %d during gain ramp",
                    __func__, mixer_ctl_name_gain_right, i);
            error = true;
            break;
        }
        usleep(1000);
    }
    if (error) {
        // an error occured during the ramp, let's still try to go back to a safe volume
        if (mixer_ctl_set_value(ctl_left, 0, backup_gain)) {
            ALOGE("%s: error restoring left gain to %d", __func__, backup_gain);
        }
        if (mixer_ctl_set_value(ctl_right, 0, backup_gain)) {
            ALOGE("%s: error restoring right gain to %d", __func__, backup_gain);
        }
    }
    return start_gain;
}

int platform_set_swap_mixer(struct audio_device *adev, bool swap_channels)
{
    const char *mixer_ctl_name = "Swap channel";
    struct mixer_ctl *ctl;
    const char *mixer_path;
    struct platform_data *my_data = (struct platform_data *)adev->platform;

    // forced to set to swap, but device not rotated ... ignore set
    if (swap_channels && !my_data->speaker_lr_swap)
        return 0;

    ALOGV("%s:", __func__);

    if (swap_channels) {
        mixer_path = platform_get_snd_device_name(SND_DEVICE_OUT_SPEAKER_REVERSE);
        audio_route_apply_and_update_path(adev->audio_route, mixer_path);
    } else {
        mixer_path = platform_get_snd_device_name(SND_DEVICE_OUT_SPEAKER);
        audio_route_apply_and_update_path(adev->audio_route, mixer_path);
    }

    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",__func__, mixer_ctl_name);
        return -EINVAL;
    }

    if (mixer_ctl_set_value(ctl, 0, swap_channels) < 0) {
        ALOGE("%s: Could not set reverse cotrol %d",__func__, swap_channels);
        return -EINVAL;
    }

    ALOGV("platfor_force_swap_channel :: Channel orientation ( %s ) ",
           swap_channels?"R --> L":"L --> R");

    return 0;
}

int platform_check_and_set_swap_lr_channels(struct audio_device *adev, bool swap_channels)
{
    // only update if there is active pcm playback on speaker
    struct audio_usecase *usecase;
    struct listnode *node;
    struct platform_data *my_data = (struct platform_data *)adev->platform;

    my_data->speaker_lr_swap = swap_channels;

    return platform_set_swap_channels(adev, swap_channels);
}

int platform_set_swap_channels(struct audio_device *adev, bool swap_channels)
{
    // only update if there is active pcm playback on speaker
    struct audio_usecase *usecase;
    struct listnode *node;
    struct platform_data *my_data = (struct platform_data *)adev->platform;

    // do not swap channels in audio modes with concurrent capture and playback
    // as this may break the echo reference
    if ((adev->mode == AUDIO_MODE_IN_COMMUNICATION) || (adev->mode == AUDIO_MODE_IN_CALL)) {
        ALOGV("%s: will not swap due to audio mode %d", __func__, adev->mode);
        return 0;
    }

    list_for_each(node, &adev->usecase_list) {
        usecase = node_to_item(node, struct audio_usecase, list);
        if (usecase->type == PCM_PLAYBACK &&
                usecase->stream.out->devices & AUDIO_DEVICE_OUT_SPEAKER) {
            /*
             * If acdb tuning is different for SPEAKER_REVERSE, it is must
             * to perform device switch to disable the current backend to
             * enable it with new acdb data.
             */
            if (acdb_device_table[SND_DEVICE_OUT_SPEAKER] !=
                acdb_device_table[SND_DEVICE_OUT_SPEAKER_REVERSE]) {
                const int initial_skpr_gain = ramp_speaker_gain(adev, false /*ramp_up*/, -1);
                select_devices(adev, usecase->id);
                if (initial_skpr_gain != -EINVAL)
                    ramp_speaker_gain(adev, true /*ramp_up*/, initial_skpr_gain);

            } else {
                platform_set_swap_mixer(adev, swap_channels);
            }
            break;
        }
    }

    return 0;
}

bool platform_send_gain_dep_cal(void *platform __unused,
                                int level __unused)
{
    return true;
}

int platform_can_split_snd_device(snd_device_t in_snd_device __unused,
                                   int *num_devices __unused,
                                   snd_device_t *out_snd_devices __unused)
{
    return -ENOSYS;
}

bool platform_check_backends_match(snd_device_t snd_device1 __unused,
                                   snd_device_t snd_device2 __unused)
{
    return true;
}

int platform_get_snd_device_name_extn(void *platform __unused,
                                      snd_device_t snd_device,
                                      char *device_name)
{
    strlcpy(device_name, platform_get_snd_device_name(snd_device),
            DEVICE_NAME_MAX_SIZE);
    return 0;
}

bool platform_check_and_set_playback_backend_cfg(struct audio_device* adev __unused,
                                              struct audio_usecase *usecase __unused,
                                              snd_device_t snd_device __unused)
{
    return false;
}

bool platform_check_and_set_capture_backend_cfg(struct audio_device* adev __unused,
    struct audio_usecase *usecase __unused, snd_device_t snd_device __unused)
{
    return false;
}

bool platform_add_gain_level_mapping(struct amp_db_and_gain_table *tbl_entry __unused)
{
    return false;
}

int platform_get_gain_level_mapping(struct amp_db_and_gain_table *mapping_tbl __unused,
                                    int table_size __unused)
{
    return 0;
}

int platform_snd_card_update(void *platform __unused,
                             card_status_t status __unused)
{
    return -1;
}

int platform_get_snd_device_backend_index(snd_device_t snd_device __unused)
{
    return -ENOSYS;
}

void platform_check_and_update_copp_sample_rate(void* platform __unused, snd_device_t snd_device __unused,
                                                unsigned int stream_sr __unused, int* sample_rate __unused)
{

}

int platform_send_audio_calibration_v2(void *platform __unused, struct audio_usecase *usecase __unused,
                                       int app_type __unused, int sample_rate __unused)
{
    return -ENOSYS;
}

bool platform_supports_app_type_cfg() { return false; }

void platform_add_app_type(const char *uc_type __unused,
                           const char *mode __unused,
                           int bw __unused, int app_type __unused,
                           int max_sr __unused) {}

int platform_get_app_type_v2(void *platform __unused,
                             enum usecase_type_t type __unused,
                             const char *mode __unused,
                             int bw __unused, int sr __unused,
                             int *app_type __unused) {
    return -ENOSYS;
}

int platform_set_sidetone(struct audio_device *adev,
                          snd_device_t out_snd_device,
                          bool enable, char *str)
{
    int ret;
    if (out_snd_device == SND_DEVICE_OUT_USB_HEADSET ||
        out_snd_device == SND_DEVICE_OUT_VOICE_USB_HEADSET) {
            ret = audio_extn_usb_enable_sidetone(out_snd_device, enable);
            if (ret)
                ALOGI("%s: usb device %d does not support device sidetone\n",
                  __func__, out_snd_device);
    } else {
        ALOGV("%s: sidetone out device(%d) mixer cmd = %s\n",
              __func__, out_snd_device, str);
        if (enable)
            audio_route_apply_and_update_path(adev->audio_route, str);
        else
            audio_route_reset_and_update_path(adev->audio_route, str);
    }
    return 0;
}

int platform_get_mmap_data_fd(void *platform __unused, int fe_dev __unused, int dir __unused,
                              int *fd __unused, uint32_t *size __unused)
{
    return -ENOSYS;
}
