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

#define LOG_TAG "audio_hw_primary"
/*#define LOG_NDEBUG 0*/
/*#define VERY_VERY_VERBOSE_LOGGING*/
#ifdef VERY_VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
#else
#define ALOGVV(a...) do { } while(0)
#endif

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <sys/time.h>
#include <stdlib.h>
#include <math.h>
#include <dlfcn.h>
#include <sys/resource.h>
#include <sys/prctl.h>

#include <cutils/log.h>
#include <cutils/str_parms.h>
#include <cutils/properties.h>
#include <cutils/atomic.h>
#include <cutils/sched_policy.h>

#include <hardware/audio_effect.h>
#include <system/thread_defs.h>
#include <audio_effects/effect_aec.h>
#include <audio_effects/effect_ns.h>
#include <audio_utils/channels.h>
#include "audio_hw.h"
#include "cras_dsp.h"

/* TODO: the following PCM device profiles could be read from a config file */
struct pcm_device_profile pcm_device_playback_hs = {
    .config = {
        .channels = PLAYBACK_DEFAULT_CHANNEL_COUNT,
        .rate = PLAYBACK_DEFAULT_SAMPLING_RATE,
        .period_size = PLAYBACK_PERIOD_SIZE,
        .period_count = PLAYBACK_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = PLAYBACK_START_THRESHOLD,
        .stop_threshold = PLAYBACK_STOP_THRESHOLD,
        .silence_threshold = 0,
        .avail_min = PLAYBACK_AVAILABLE_MIN,
    },
    .card = SOUND_CARD,
    .id = 1,
    .device = 0,
    .type = PCM_PLAYBACK,
    .devices = AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE,
    .dsp_name = "invert_lr",
};

struct pcm_device_profile pcm_device_capture = {
    .config = {
        .channels = CAPTURE_DEFAULT_CHANNEL_COUNT,
        .rate = CAPTURE_DEFAULT_SAMPLING_RATE,
        .period_size = CAPTURE_PERIOD_SIZE,
        .period_count = CAPTURE_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = CAPTURE_START_THRESHOLD,
        .stop_threshold = 0,
        .silence_threshold = 0,
        .avail_min = 0,
    },
    .card = SOUND_CARD,
    .id = 2,
    .device = 0,
    .type = PCM_CAPTURE,
    .devices = AUDIO_DEVICE_IN_BUILTIN_MIC|AUDIO_DEVICE_IN_WIRED_HEADSET|AUDIO_DEVICE_IN_BACK_MIC,
};

struct pcm_device_profile pcm_device_capture_loopback_aec = {
    .config = {
        .channels = CAPTURE_DEFAULT_CHANNEL_COUNT,
        .rate = CAPTURE_DEFAULT_SAMPLING_RATE,
        .period_size = CAPTURE_PERIOD_SIZE,
        .period_count = CAPTURE_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = CAPTURE_START_THRESHOLD,
        .stop_threshold = 0,
        .silence_threshold = 0,
        .avail_min = 0,
    },
    .card = SOUND_CARD,
    .id = 3,
    .device = 1,
    .type = PCM_CAPTURE,
    .devices = SND_DEVICE_IN_LOOPBACK_AEC,
};

struct pcm_device_profile pcm_device_playback_spk_and_headset = {
    .config = {
        .channels = PLAYBACK_DEFAULT_CHANNEL_COUNT,
        .rate = PLAYBACK_DEFAULT_SAMPLING_RATE,
        .period_size = PLAYBACK_PERIOD_SIZE,
        .period_count = PLAYBACK_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = PLAYBACK_START_THRESHOLD,
        .stop_threshold = PLAYBACK_STOP_THRESHOLD,
        .silence_threshold = 0,
        .avail_min = PLAYBACK_AVAILABLE_MIN,
    },
    .card = SOUND_CARD,
    .id = 4,
    .device = 0,
    .type = PCM_PLAYBACK,
    .devices = AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE,
    .dsp_name = "speaker_eq",
};

struct pcm_device_profile pcm_device_playback_spk = {
    .config = {
        .channels = PLAYBACK_DEFAULT_CHANNEL_COUNT,
        .rate = PLAYBACK_DEFAULT_SAMPLING_RATE,
        .period_size = PLAYBACK_PERIOD_SIZE,
        .period_count = PLAYBACK_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = PLAYBACK_START_THRESHOLD,
        .stop_threshold = PLAYBACK_STOP_THRESHOLD,
        .silence_threshold = 0,
        .avail_min = PLAYBACK_AVAILABLE_MIN,
    },
    .card = SOUND_CARD,
    .id = 5,
    .device = 0,
    .type = PCM_PLAYBACK,
    .devices = AUDIO_DEVICE_OUT_SPEAKER,
    .dsp_name = "speaker_eq",
};

static struct pcm_device_profile pcm_device_hotword_streaming = {
    .config = {
        .channels = 1,
        .rate = 16000,
        .period_size = CAPTURE_PERIOD_SIZE,
        .period_count = CAPTURE_PERIOD_COUNT,
        .format = PCM_FORMAT_S16_LE,
        .start_threshold = CAPTURE_START_THRESHOLD,
        .stop_threshold = 0,
        .silence_threshold = 0,
        .avail_min = 0,
    },
    .card = SOUND_CARD,
    .id = 0,
    .type = PCM_HOTWORD_STREAMING,
    .devices = AUDIO_DEVICE_IN_BUILTIN_MIC |
               AUDIO_DEVICE_IN_WIRED_HEADSET |
               AUDIO_DEVICE_IN_BACK_MIC,
};

struct pcm_device_profile *pcm_devices[] = {
    &pcm_device_playback_hs,
    &pcm_device_capture,
    &pcm_device_playback_spk,
    &pcm_device_capture_loopback_aec,
    &pcm_device_playback_spk_and_headset,
    &pcm_device_hotword_streaming,
    NULL,
};

static const char * const use_case_table[AUDIO_USECASE_MAX] = {
    [USECASE_AUDIO_PLAYBACK] = "playback",
    [USECASE_AUDIO_PLAYBACK_MULTI_CH] = "playback multi-channel",
    [USECASE_AUDIO_CAPTURE] = "capture",
    [USECASE_AUDIO_CAPTURE_HOTWORD] = "capture-hotword",
    [USECASE_VOICE_CALL] = "voice-call",
};


#define STRING_TO_ENUM(string) { #string, string }

struct pcm_config pcm_config_deep_buffer = {
    .channels = 2,
    .rate = DEEP_BUFFER_OUTPUT_SAMPLING_RATE,
    .period_size = DEEP_BUFFER_OUTPUT_PERIOD_SIZE,
    .period_count = DEEP_BUFFER_OUTPUT_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
    .start_threshold = DEEP_BUFFER_OUTPUT_PERIOD_SIZE / 4,
    .stop_threshold = INT_MAX,
    .avail_min = DEEP_BUFFER_OUTPUT_PERIOD_SIZE / 4,
};

struct string_to_enum {
    const char *name;
    uint32_t value;
};

static const struct string_to_enum out_channels_name_to_enum_table[] = {
    STRING_TO_ENUM(AUDIO_CHANNEL_OUT_STEREO),
    STRING_TO_ENUM(AUDIO_CHANNEL_OUT_5POINT1),
    STRING_TO_ENUM(AUDIO_CHANNEL_OUT_7POINT1),
};

static bool is_supported_format(audio_format_t format)
{
    if (format == AUDIO_FORMAT_MP3 ||
            ((format & AUDIO_FORMAT_MAIN_MASK) == AUDIO_FORMAT_AAC))
        return true;

    return false;
}

static int get_snd_codec_id(audio_format_t format)
{
    int id = 0;

    switch (format & AUDIO_FORMAT_MAIN_MASK) {
    default:
        ALOGE("%s: Unsupported audio format", __func__);
    }

    return id;
}

/* Array to store sound devices */
static const char * const device_table[SND_DEVICE_MAX] = {
    [SND_DEVICE_NONE] = "none",
    /* Playback sound devices */
    [SND_DEVICE_OUT_HANDSET] = "handset",
    [SND_DEVICE_OUT_SPEAKER] = "speaker",
    [SND_DEVICE_OUT_HEADPHONES] = "headphones",
    [SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES] = "speaker-and-headphones",
    [SND_DEVICE_OUT_VOICE_HANDSET] = "voice-handset",
    [SND_DEVICE_OUT_VOICE_SPEAKER] = "voice-speaker",
    [SND_DEVICE_OUT_VOICE_HEADPHONES] = "voice-headphones",
    [SND_DEVICE_OUT_HDMI] = "hdmi",
    [SND_DEVICE_OUT_SPEAKER_AND_HDMI] = "speaker-and-hdmi",
    [SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES] = "voice-tty-full-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES] = "voice-tty-vco-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET] = "voice-tty-hco-handset",

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
    [SND_DEVICE_IN_CAMCORDER_MIC] = "camcorder-mic",
    [SND_DEVICE_IN_VOICE_DMIC_1] = "voice-dmic-1",
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_1] = "voice-speaker-dmic-1",
    [SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC] = "voice-tty-full-headset-mic",
    [SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC] = "voice-tty-vco-handset-mic",
    [SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC] = "voice-tty-hco-headset-mic",
    [SND_DEVICE_IN_VOICE_REC_HEADSET_MIC] = "voice-rec-headset-mic",
    [SND_DEVICE_IN_VOICE_REC_MIC] = "voice-rec-mic",
    [SND_DEVICE_IN_VOICE_REC_DMIC_1] = "voice-rec-dmic-1",
    [SND_DEVICE_IN_VOICE_REC_DMIC_NS_1] = "voice-rec-dmic-ns-1",
    [SND_DEVICE_IN_LOOPBACK_AEC] = "loopback-aec",
};

struct mixer_card *adev_get_mixer_for_card(struct audio_device *adev, int card)
{
    struct mixer_card *mixer_card;
    struct listnode *node;

    list_for_each(node, &adev->mixer_list) {
        mixer_card = node_to_item(node, struct mixer_card, adev_list_node);
        if (mixer_card->card == card)
            return mixer_card;
    }
    return NULL;
}

struct mixer_card *uc_get_mixer_for_card(struct audio_usecase *usecase, int card)
{
    struct mixer_card *mixer_card;
    struct listnode *node;

    list_for_each(node, &usecase->mixer_list) {
        mixer_card = node_to_item(node, struct mixer_card, uc_list_node[usecase->id]);
        if (mixer_card->card == card)
            return mixer_card;
    }
    return NULL;
}

void free_mixer_list(struct audio_device *adev)
{
    struct mixer_card *mixer_card;
    struct listnode *node;
    struct listnode *next;

    list_for_each_safe(node, next, &adev->mixer_list) {
        mixer_card = node_to_item(node, struct mixer_card, adev_list_node);
        list_remove(node);
        audio_route_free(mixer_card->audio_route);
        free(mixer_card);
    }
}

int mixer_init(struct audio_device *adev)
{
    int i;
    int card;
    int retry_num;
    struct mixer *mixer;
    struct audio_route *audio_route;
    char mixer_path[PATH_MAX];
    struct mixer_card *mixer_card;
    struct listnode *node;

    list_init(&adev->mixer_list);

    for (i = 0; pcm_devices[i] != NULL; i++) {
        card = pcm_devices[i]->card;
        if (adev_get_mixer_for_card(adev, card) == NULL) {
            retry_num = 0;
            do {
                mixer = mixer_open(card);
                if (mixer == NULL) {
                    if (++retry_num > RETRY_NUMBER) {
                        ALOGE("%s unable to open the mixer for--card %d, aborting.",
                              __func__, card);
                        goto error;
                    }
                    usleep(RETRY_US);
                }
            } while (mixer == NULL);

            sprintf(mixer_path, "/system/etc/mixer_paths_%d.xml", card);
            audio_route = audio_route_init(card, mixer_path);
            if (!audio_route) {
                ALOGE("%s: Failed to init audio route controls for card %d, aborting.",
                      __func__, card);
                goto error;
            }
            mixer_card = calloc(1, sizeof(struct mixer_card));
            mixer_card->card = card;
            mixer_card->mixer = mixer;
            mixer_card->audio_route = audio_route;
            list_add_tail(&adev->mixer_list, &mixer_card->adev_list_node);
        }
    }

    return 0;

error:
    free_mixer_list(adev);
    return -ENODEV;
}

const char *get_snd_device_name(snd_device_t snd_device)
{
    const char *name = NULL;

    if (snd_device >= SND_DEVICE_MIN && snd_device < SND_DEVICE_MAX)
        name = device_table[snd_device];

    ALOGE_IF(name == NULL, "%s: invalid snd device %d", __func__, snd_device);

   return name;
}

const char *get_snd_device_display_name(snd_device_t snd_device)
{
    const char *name = get_snd_device_name(snd_device);

    if (name == NULL)
        name = "SND DEVICE NOT FOUND";

    return name;
}

struct pcm_device_profile *get_pcm_device(usecase_type_t uc_type, audio_devices_t devices)
{
    int i;

    devices &= ~AUDIO_DEVICE_BIT_IN;

    if (!devices)
        return NULL;

    for (i = 0; pcm_devices[i] != NULL; i++) {
        if ((pcm_devices[i]->type == uc_type) &&
                (devices & pcm_devices[i]->devices) == devices)
            return pcm_devices[i];
    }

    return NULL;
}

static struct audio_usecase *get_usecase_from_id(struct audio_device *adev,
                                                   audio_usecase_t uc_id)
{
    struct audio_usecase *usecase;
    struct listnode *node;

    list_for_each(node, &adev->usecase_list) {
        usecase = node_to_item(node, struct audio_usecase, adev_list_node);
        if (usecase->id == uc_id)
            return usecase;
    }
    return NULL;
}

static struct audio_usecase *get_usecase_from_type(struct audio_device *adev,
                                                        usecase_type_t type)
{
    struct audio_usecase *usecase;
    struct listnode *node;

    list_for_each(node, &adev->usecase_list) {
        usecase = node_to_item(node, struct audio_usecase, adev_list_node);
        if (usecase->type & type)
            return usecase;
    }
    return NULL;
}

/* always called with adev lock held */
static int set_voice_volume_l(struct audio_device *adev, float volume)
{
    int err = 0;
    (void)volume;

    if (adev->mode == AUDIO_MODE_IN_CALL) {
        /* TODO */
    }
    return err;
}


snd_device_t get_output_snd_device(struct audio_device *adev, audio_devices_t devices)
{

    audio_mode_t mode = adev->mode;
    snd_device_t snd_device = SND_DEVICE_NONE;

    ALOGV("%s: enter: output devices(%#x), mode(%d)", __func__, devices, mode);
    if (devices == AUDIO_DEVICE_NONE ||
        devices & AUDIO_DEVICE_BIT_IN) {
        ALOGV("%s: Invalid output devices (%#x)", __func__, devices);
        goto exit;
    }

    if (mode == AUDIO_MODE_IN_CALL) {
        if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
            devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            if (adev->tty_mode == TTY_MODE_FULL)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES;
            else if (adev->tty_mode == TTY_MODE_VCO)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES;
            else if (adev->tty_mode == TTY_MODE_HCO)
                snd_device = SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET;
            else
                snd_device = SND_DEVICE_OUT_VOICE_HEADPHONES;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            snd_device = SND_DEVICE_OUT_VOICE_SPEAKER;
        } else if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
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
        snd_device = SND_DEVICE_OUT_SPEAKER;
    } else if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
        snd_device = SND_DEVICE_OUT_HANDSET;
    } else {
        ALOGE("%s: Unknown device(s) %#x", __func__, devices);
    }
exit:
    ALOGV("%s: exit: snd_device(%s)", __func__, device_table[snd_device]);
    return snd_device;
}

snd_device_t get_input_snd_device(struct audio_device *adev, audio_devices_t out_device)
{
    audio_source_t  source;
    audio_mode_t    mode   = adev->mode;
    audio_devices_t in_device;
    audio_channel_mask_t channel_mask;
    snd_device_t snd_device = SND_DEVICE_NONE;
    struct stream_in *active_input = NULL;
    struct audio_usecase *usecase;

    usecase = get_usecase_from_type(adev, PCM_CAPTURE|VOICE_CALL);
    if (usecase != NULL) {
        active_input = (struct stream_in *)usecase->stream;
    }
    source = (active_input == NULL) ?
                                AUDIO_SOURCE_DEFAULT : active_input->source;

    in_device = ((active_input == NULL) ?
                                    AUDIO_DEVICE_NONE : active_input->devices)
                                & ~AUDIO_DEVICE_BIT_IN;
    channel_mask = (active_input == NULL) ?
                                AUDIO_CHANNEL_IN_MONO : active_input->main_channels;

    ALOGV("%s: enter: out_device(%#x) in_device(%#x)",
          __func__, out_device, in_device);
    if (mode == AUDIO_MODE_IN_CALL) {
        if (out_device == AUDIO_DEVICE_NONE) {
            ALOGE("%s: No output device set for voice call", __func__);
            goto exit;
        }
        if (adev->tty_mode != TTY_MODE_OFF) {
            if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
                out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
                switch (adev->tty_mode) {
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
                    ALOGE("%s: Invalid TTY mode (%#x)", __func__, adev->tty_mode);
                }
                goto exit;
            }
        }
        if (out_device & AUDIO_DEVICE_OUT_EARPIECE ||
                out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_VOICE_HEADSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_SPEAKER) {
            snd_device = SND_DEVICE_IN_VOICE_SPEAKER_MIC;
        }
    } else if (source == AUDIO_SOURCE_CAMCORDER) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC ||
            in_device & AUDIO_DEVICE_IN_BACK_MIC) {
            snd_device = SND_DEVICE_IN_CAMCORDER_MIC;
        }
    } else if (source == AUDIO_SOURCE_VOICE_RECOGNITION) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
            if (adev->dualmic_config == DUALMIC_CONFIG_1) {
                if (channel_mask == AUDIO_CHANNEL_IN_FRONT_BACK)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_1;
                else if (adev->ns_in_voice_rec)
                    snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_NS_1;
            }

            if (snd_device == SND_DEVICE_NONE) {
                snd_device = SND_DEVICE_IN_VOICE_REC_MIC;
            }
        } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_VOICE_REC_HEADSET_MIC;
        }
    } else if (source == AUDIO_SOURCE_VOICE_COMMUNICATION || source == AUDIO_SOURCE_MIC) {
        if (out_device & AUDIO_DEVICE_OUT_SPEAKER)
            in_device = AUDIO_DEVICE_IN_BACK_MIC;
        if (active_input) {
            if (active_input->enable_aec) {
                if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
                    snd_device = SND_DEVICE_IN_SPEAKER_MIC_AEC;
                } else if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
                    if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
                        snd_device = SND_DEVICE_IN_SPEAKER_MIC_AEC;
                    } else {
                        snd_device = SND_DEVICE_IN_HANDSET_MIC_AEC;
                    }
                } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
                    snd_device = SND_DEVICE_IN_HEADSET_MIC_AEC;
                }
            }
            /* TODO: set echo reference */
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

int set_hdmi_channels(struct audio_device *adev,  int channel_count)
{
    struct mixer_ctl *ctl;
    const char *mixer_ctl_name = "";
    (void)adev;
    (void)channel_count;
    /* TODO */

    return 0;
}

int edid_get_max_channels(struct audio_device *adev)
{
    int max_channels = 2;
    struct mixer_ctl *ctl;
    (void)adev;

    /* TODO */
    return max_channels;
}

/* Delay in Us */
int64_t render_latency(audio_usecase_t usecase)
{
    (void)usecase;
    /* TODO */
    return 0;
}

static int enable_snd_device(struct audio_device *adev,
                             struct audio_usecase *uc_info,
                             snd_device_t snd_device,
                             bool update_mixer)
{
    struct mixer_card *mixer_card;
    struct listnode *node;
    const char *snd_device_name = get_snd_device_name(snd_device);

    if (snd_device_name == NULL)
        return -EINVAL;

    adev->snd_dev_ref_cnt[snd_device]++;
    if (adev->snd_dev_ref_cnt[snd_device] > 1) {
        ALOGV("%s: snd_device(%d: %s) is already active",
              __func__, snd_device, snd_device_name);
        return 0;
    }

    ALOGV("%s: snd_device(%d: %s)", __func__,
          snd_device, snd_device_name);

    list_for_each(node, &uc_info->mixer_list) {
        mixer_card = node_to_item(node, struct mixer_card, uc_list_node[uc_info->id]);
        audio_route_apply_path(mixer_card->audio_route, snd_device_name);
        if (update_mixer)
            audio_route_update_mixer(mixer_card->audio_route);
    }

    return 0;
}

static int disable_snd_device(struct audio_device *adev,
                              struct audio_usecase *uc_info,
                              snd_device_t snd_device,
                              bool update_mixer)
{
    struct mixer_card *mixer_card;
    struct listnode *node;
    const char *snd_device_name = get_snd_device_name(snd_device);

    if (snd_device_name == NULL)
        return -EINVAL;

    if (adev->snd_dev_ref_cnt[snd_device] <= 0) {
        ALOGE("%s: device ref cnt is already 0", __func__);
        return -EINVAL;
    }
    adev->snd_dev_ref_cnt[snd_device]--;
    if (adev->snd_dev_ref_cnt[snd_device] == 0) {
        ALOGV("%s: snd_device(%d: %s)", __func__,
              snd_device, snd_device_name);
        list_for_each(node, &uc_info->mixer_list) {
            mixer_card = node_to_item(node, struct mixer_card, uc_list_node[uc_info->id]);
            audio_route_reset_path(mixer_card->audio_route, snd_device_name);
            if (update_mixer)
                audio_route_update_mixer(mixer_card->audio_route);
        }
    }
    return 0;
}

static int select_devices(struct audio_device *adev,
                          audio_usecase_t uc_id)
{
    snd_device_t out_snd_device = SND_DEVICE_NONE;
    snd_device_t in_snd_device = SND_DEVICE_NONE;
    struct audio_usecase *usecase = NULL;
    struct audio_usecase *vc_usecase = NULL;
    struct listnode *node;
    struct stream_in *active_input = NULL;
    struct stream_out *active_out;
    struct mixer_card *mixer_card;

    ALOGV("%s: usecase(%d)", __func__, uc_id);

    if (uc_id == USECASE_AUDIO_CAPTURE_HOTWORD)
        return 0;

    usecase = get_usecase_from_type(adev, PCM_CAPTURE|VOICE_CALL);
    if (usecase != NULL) {
        active_input = (struct stream_in *)usecase->stream;
    }

    usecase = get_usecase_from_id(adev, uc_id);
    if (usecase == NULL) {
        ALOGE("%s: Could not find the usecase(%d)", __func__, uc_id);
        return -EINVAL;
    }
    active_out = (struct stream_out *)usecase->stream;

    if (usecase->type == VOICE_CALL) {
        out_snd_device = get_output_snd_device(adev, active_out->devices);
        in_snd_device = get_input_snd_device(adev, active_out->devices);
        usecase->devices = active_out->devices;
    } else {
        /*
         * If the voice call is active, use the sound devices of voice call usecase
         * so that it would not result any device switch. All the usecases will
         * be switched to new device when select_devices() is called for voice call
         * usecase.
         */
        if (adev->in_call) {
            vc_usecase = get_usecase_from_id(adev, USECASE_VOICE_CALL);
            if (usecase == NULL) {
                ALOGE("%s: Could not find the voice call usecase", __func__);
            } else {
                in_snd_device = vc_usecase->in_snd_device;
                out_snd_device = vc_usecase->out_snd_device;
            }
        }
        if (usecase->type == PCM_PLAYBACK) {
            usecase->devices = active_out->devices;
            in_snd_device = SND_DEVICE_NONE;
            if (out_snd_device == SND_DEVICE_NONE) {
                out_snd_device = get_output_snd_device(adev, active_out->devices);
                if (active_out == adev->primary_output &&
                        active_input &&
                        active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION) {
                    select_devices(adev, active_input->usecase);
                }
            }
        } else if (usecase->type == PCM_CAPTURE) {
            usecase->devices = ((struct stream_in *)usecase->stream)->devices;
            out_snd_device = SND_DEVICE_NONE;
            if (in_snd_device == SND_DEVICE_NONE) {
                if (active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION &&
                        adev->primary_output && !adev->primary_output->standby) {
                    in_snd_device = get_input_snd_device(adev, adev->primary_output->devices);
                } else {
                    in_snd_device = get_input_snd_device(adev, AUDIO_DEVICE_NONE);
                }
            }
        }
    }

    if (out_snd_device == usecase->out_snd_device &&
        in_snd_device == usecase->in_snd_device) {
        return 0;
    }

    ALOGV("%s: out_snd_device(%d: %s) in_snd_device(%d: %s)", __func__,
          out_snd_device, get_snd_device_display_name(out_snd_device),
          in_snd_device,  get_snd_device_display_name(in_snd_device));


    /* Disable current sound devices */
    if (usecase->out_snd_device != SND_DEVICE_NONE) {
        disable_snd_device(adev, usecase, usecase->out_snd_device, false);
    }

    if (usecase->in_snd_device != SND_DEVICE_NONE) {
        disable_snd_device(adev, usecase, usecase->in_snd_device, false);
    }

    /* Enable new sound devices */
    if (out_snd_device != SND_DEVICE_NONE) {
        enable_snd_device(adev, usecase, out_snd_device, false);
    }

    if (in_snd_device != SND_DEVICE_NONE) {
        enable_snd_device(adev, usecase, in_snd_device, false);
    }

    list_for_each(node, &usecase->mixer_list) {
         mixer_card = node_to_item(node, struct mixer_card, uc_list_node[usecase->id]);
         audio_route_update_mixer(mixer_card->audio_route);
    }

    usecase->in_snd_device = in_snd_device;
    usecase->out_snd_device = out_snd_device;

    return 0;
}

static ssize_t read_frames(struct stream_in *in, void *buffer, ssize_t frames);
static int do_in_standby_l(struct stream_in *in);
static audio_format_t in_get_format(const struct audio_stream *stream);

#ifdef PREPROCESSING_ENABLED
static int get_command_status(int status, int fct_status, uint32_t cmd_status) {
    if (fct_status != 0)
        status = fct_status;
    else if (cmd_status != 0)
        status = cmd_status;
    return status;
}

static uint32_t in_get_aux_channels(struct stream_in *in)
{
    if (in->num_preprocessors == 0)
        return 0;

    /* do not enable quad mic configurations when capturing from other
     * microphones than main */
    if (!(in->devices & AUDIO_DEVICE_IN_BUILTIN_MIC & ~AUDIO_DEVICE_BIT_IN))
        return 0;

    return AUDIO_CHANNEL_INDEX_MASK_4;
}

static int in_configure_effect_channels(effect_handle_t effect,
                                        channel_config_t *channel_config)
{
    int status = 0;
    int fct_status;
    int32_t cmd_status;
    uint32_t reply_size;
    effect_config_t config;
    uint32_t cmd[(sizeof(uint32_t) + sizeof(channel_config_t) - 1) / sizeof(uint32_t) + 1];

    ALOGV("in_configure_effect_channels(): configure effect with channels: [%04x][%04x]",
            channel_config->main_channels,
            channel_config->aux_channels);

    config.inputCfg.mask = EFFECT_CONFIG_CHANNELS;
    config.outputCfg.mask = EFFECT_CONFIG_CHANNELS;
    reply_size = sizeof(effect_config_t);
    fct_status = (*effect)->command(effect,
                                EFFECT_CMD_GET_CONFIG,
                                0,
                                NULL,
                                &reply_size,
                                &config);
    if (fct_status != 0) {
        ALOGE("in_configure_effect_channels(): EFFECT_CMD_GET_CONFIG failed");
        return fct_status;
    }

    config.inputCfg.channels = channel_config->aux_channels;
    config.outputCfg.channels = config.inputCfg.channels;
    reply_size = sizeof(uint32_t);
    fct_status = (*effect)->command(effect,
                                    EFFECT_CMD_SET_CONFIG,
                                    sizeof(effect_config_t),
                                    &config,
                                    &reply_size,
                                    &cmd_status);
    status = get_command_status(status, fct_status, cmd_status);
    if (status != 0) {
        ALOGE("in_configure_effect_channels(): EFFECT_CMD_SET_CONFIG failed");
        return status;
    }

    /* some implementations need to be re-enabled after a config change */
    reply_size = sizeof(uint32_t);
    fct_status = (*effect)->command(effect,
                                  EFFECT_CMD_ENABLE,
                                  0,
                                  NULL,
                                  &reply_size,
                                  &cmd_status);
    status = get_command_status(status, fct_status, cmd_status);
    if (status != 0) {
        ALOGE("in_configure_effect_channels(): EFFECT_CMD_ENABLE failed");
        return status;
    }

    return status;
}

static int in_reconfigure_channels(struct stream_in *in,
                                   effect_handle_t effect,
                                   channel_config_t *channel_config,
                                   bool config_changed) {

    int status = 0;

    ALOGV("in_reconfigure_channels(): config_changed %d effect %p",
          config_changed, effect);

    /* if config changed, reconfigure all previously added effects */
    if (config_changed) {
        int i;
        ALOGV("%s: config_changed (%d)", __func__, config_changed);
        for (i = 0; i < in->num_preprocessors; i++) {
            int cur_status = in_configure_effect_channels(in->preprocessors[i].effect_itfe,
                                                  channel_config);
            ALOGV("%s: in_configure_effect_channels i=(%d), [main_channel,aux_channel]=[%d|%d], status=%d",
                          __func__, i, channel_config->main_channels, channel_config->aux_channels, cur_status);
            if (cur_status != 0) {
                ALOGV("in_reconfigure_channels(): error %d configuring effect "
                        "%d with channels: [%04x][%04x]",
                        cur_status,
                        i,
                        channel_config->main_channels,
                        channel_config->aux_channels);
                status = cur_status;
            }
        }
    } else if (effect != NULL && channel_config->aux_channels) {
        /* if aux channels config did not change but aux channels are present,
         * we still need to configure the effect being added */
        status = in_configure_effect_channels(effect, channel_config);
    }
    return status;
}

static void in_update_aux_channels(struct stream_in *in,
                                   effect_handle_t effect)
{
    uint32_t aux_channels;
    channel_config_t channel_config;
    int status;

    aux_channels = in_get_aux_channels(in);

    channel_config.main_channels = in->main_channels;
    channel_config.aux_channels = aux_channels;
    status = in_reconfigure_channels(in,
                                     effect,
                                     &channel_config,
                                     (aux_channels != in->aux_channels));

    if (status != 0) {
        ALOGV("in_update_aux_channels(): in_reconfigure_channels error %d", status);
        /* resetting aux channels configuration */
        aux_channels = 0;
        channel_config.aux_channels = 0;
        in_reconfigure_channels(in, effect, &channel_config, true);
    }
    ALOGV("%s: aux_channels=%d, in->aux_channels_changed=%d", __func__, aux_channels, in->aux_channels_changed);
    if (in->aux_channels != aux_channels) {
        in->aux_channels_changed = true;
        in->aux_channels = aux_channels;
        do_in_standby_l(in);
    }
}
#endif

/* This function reads PCM data and:
 * - resample if needed
 * - process if pre-processors are attached
 * - discard unwanted channels
 */
static ssize_t read_and_process_frames(struct audio_stream_in *stream, void* buffer, ssize_t frames_num)
{
    struct stream_in *in = (struct stream_in *)stream;
    ssize_t frames_wr = 0; /* Number of frames actually read */
    size_t bytes_per_sample = audio_bytes_per_sample(stream->common.get_format(&stream->common));
    void *proc_buf_out = buffer;

    /* Additional channels might be added on top of main_channels:
    * - aux_channels (by processing effects)
    * - extra channels due to HW limitations
    * In case of additional channels, we cannot work inplace
    */
    size_t src_channels = in->config.channels;
    size_t dst_channels = audio_channel_count_from_in_mask(in->main_channels);
    bool channel_remapping_needed = (dst_channels != src_channels);
    const size_t src_frame_size = src_channels * bytes_per_sample;

#ifdef PREPROCESSING_ENABLED
    const bool has_processing = in->num_preprocessors != 0;
#else
    const bool has_processing = false;
#endif

    /* With additional channels or processing, we need intermediate buffers */
    if (channel_remapping_needed || has_processing) {
        const size_t src_buffer_size = frames_num * src_frame_size;

        if (in->proc_buf_size < src_buffer_size) {
            in->proc_buf_size = src_buffer_size;
#ifdef PREPROCESSING_ENABLED
            /* we always reallocate both buffers in case # of effects change dynamically. */
            in->proc_buf_in = realloc(in->proc_buf_in, src_buffer_size);
            ALOG_ASSERT((in->proc_buf_in != NULL),
                    "process_frames() failed to reallocate proc_buf_in");
#endif
            in->proc_buf_out = realloc(in->proc_buf_out, src_buffer_size);
            ALOG_ASSERT((in->proc_buf_out != NULL),
                    "process_frames() failed to reallocate proc_buf_out");
        }
        if (channel_remapping_needed) {
            proc_buf_out = in->proc_buf_out;
        }
    }

#ifdef PREPROCESSING_ENABLED
    if (has_processing) {
        /* since all the processing below is done in frames and using the config.channels
         * as the number of channels, no changes is required in case aux_channels are present */
        while (frames_wr < frames_num) {
            /* first reload enough frames at the end of process input buffer */
            if (in->proc_buf_frames < (size_t)frames_num) {
                ssize_t frames_rd = read_frames(in,
                        (char *)in->proc_buf_in + in->proc_buf_frames * src_frame_size,
                        frames_num - in->proc_buf_frames);
                if (frames_rd < 0) {
                    /* Return error code */
                    frames_wr = frames_rd;
                    break;
                }
                in->proc_buf_frames += frames_rd;
            }

             /* in_buf.frameCount and out_buf.frameCount indicate respectively
              * the maximum number of frames to be consumed and produced by process() */
            audio_buffer_t in_buf;
            audio_buffer_t out_buf;

            in_buf.frameCount = in->proc_buf_frames;
            in_buf.s16 = in->proc_buf_in;  /* currently assumes PCM 16 effects */
            out_buf.frameCount = frames_num - frames_wr;
            out_buf.s16 = (int16_t *)proc_buf_out + frames_wr * src_channels;

            /* FIXME: this works because of current pre processing library implementation that
             * does the actual process only when the last enabled effect process is called.
             * The generic solution is to have an output buffer for each effect and pass it as
             * input to the next.
             */
            for (int i = 0; i < in->num_preprocessors; i++) {
                (*in->preprocessors[i].effect_itfe)->process(in->preprocessors[i].effect_itfe,
                                                   &in_buf,
                                                   &out_buf);
            }

            /* process() has updated the number of frames consumed and produced in
             * in_buf.frameCount and out_buf.frameCount respectively
             * move remaining frames to the beginning of in->proc_buf_in */
            in->proc_buf_frames -= in_buf.frameCount;

            if (in->proc_buf_frames) {
                memcpy(in->proc_buf_in,
                       (char *)in->proc_buf_in + in_buf.frameCount * src_frame_size,
                       in->proc_buf_frames * src_frame_size);
            }

            /* if not enough frames were passed to process(), read more and retry. */
            if (out_buf.frameCount == 0) {
                ALOGW("No frames produced by preproc");
                continue;
            }

            if ((frames_wr + (ssize_t)out_buf.frameCount) <= frames_num) {
                frames_wr += out_buf.frameCount;
            } else {
                /* The effect does not comply to the API. In theory, we should never end up here! */
                ALOGE("preprocessing produced too many frames: %d + %zd  > %d !",
                      (unsigned int)frames_wr, out_buf.frameCount, (unsigned int)frames_num);
                frames_wr = frames_num;
            }
        }
    }
    else
#endif //PREPROCESSING_ENABLED
    {
        /* No processing effects attached */
        frames_wr = read_frames(in, proc_buf_out, frames_num);
        ALOG_ASSERT(frames_wr <= frames_num, "read more frames than requested");
    }

    /* check negative frames_wr (error) before channel remapping to avoid overwriting memory. */
    if (channel_remapping_needed && frames_wr > 0) {
        size_t ret = adjust_channels(proc_buf_out, src_channels, buffer, dst_channels,
            bytes_per_sample, frames_wr * src_frame_size);
        ALOG_ASSERT(ret == (frames_wr * dst_channels * bytes_per_sample));
    }

    return frames_wr;
}

static int get_next_buffer(struct resampler_buffer_provider *buffer_provider,
                                   struct resampler_buffer* buffer)
{
    struct stream_in *in;
    struct pcm_device *pcm_device;

    if (buffer_provider == NULL || buffer == NULL)
        return -EINVAL;

    in = (struct stream_in *)((char *)buffer_provider -
                                   offsetof(struct stream_in, buf_provider));

    if (list_empty(&in->pcm_dev_list)) {
        buffer->raw = NULL;
        buffer->frame_count = 0;
        in->read_status = -ENODEV;
        return -ENODEV;
    }

    pcm_device = node_to_item(list_head(&in->pcm_dev_list),
                              struct pcm_device, stream_list_node);

    if (in->read_buf_frames == 0) {
        size_t size_in_bytes = pcm_frames_to_bytes(pcm_device->pcm, in->config.period_size);
        if (in->read_buf_size < in->config.period_size) {
            in->read_buf_size = in->config.period_size;
            in->read_buf = (int16_t *) realloc(in->read_buf, size_in_bytes);
            ALOG_ASSERT((in->read_buf != NULL),
                        "get_next_buffer() failed to reallocate read_buf");
        }

        in->read_status = pcm_read(pcm_device->pcm, (void*)in->read_buf, size_in_bytes);

        if (in->read_status != 0) {
            ALOGE("get_next_buffer() pcm_read error %d", in->read_status);
            buffer->raw = NULL;
            buffer->frame_count = 0;
            return in->read_status;
        }
        in->read_buf_frames = in->config.period_size;
    }

    buffer->frame_count = (buffer->frame_count > in->read_buf_frames) ?
                                in->read_buf_frames : buffer->frame_count;
    buffer->i16 = in->read_buf + (in->config.period_size - in->read_buf_frames) *
                                                in->config.channels;
    return in->read_status;
}

static void release_buffer(struct resampler_buffer_provider *buffer_provider,
                                  struct resampler_buffer* buffer)
{
    struct stream_in *in;

    if (buffer_provider == NULL || buffer == NULL)
        return;

    in = (struct stream_in *)((char *)buffer_provider -
                                   offsetof(struct stream_in, buf_provider));

    in->read_buf_frames -= buffer->frame_count;
}

/* read_frames() reads frames from kernel driver, down samples to capture rate
 * if necessary and output the number of frames requested to the buffer specified */
static ssize_t read_frames(struct stream_in *in, void *buffer, ssize_t frames)
{
    ssize_t frames_wr = 0;

    struct pcm_device *pcm_device;

    if (list_empty(&in->pcm_dev_list)) {
        ALOGE("%s: pcm device list empty", __func__);
        return -EINVAL;
    }

    pcm_device = node_to_item(list_head(&in->pcm_dev_list),
                              struct pcm_device, stream_list_node);

    while (frames_wr < frames) {
        size_t frames_rd = frames - frames_wr;
        ALOGVV("%s: frames_rd: %zd, frames_wr: %zd, in->config.channels: %d",
               __func__,frames_rd,frames_wr,in->config.channels);
        if (in->resampler != NULL) {
            in->resampler->resample_from_provider(in->resampler,
                    (int16_t *)((char *)buffer +
                            pcm_frames_to_bytes(pcm_device->pcm, frames_wr)),
                    &frames_rd);
        } else {
            struct resampler_buffer buf = {
                    { raw : NULL, },
                    frame_count : frames_rd,
            };
            get_next_buffer(&in->buf_provider, &buf);
            if (buf.raw != NULL) {
                memcpy((char *)buffer +
                            pcm_frames_to_bytes(pcm_device->pcm, frames_wr),
                        buf.raw,
                        pcm_frames_to_bytes(pcm_device->pcm, buf.frame_count));
                frames_rd = buf.frame_count;
            }
            release_buffer(&in->buf_provider, &buf);
        }
        /* in->read_status is updated by getNextBuffer() also called by
         * in->resampler->resample_from_provider() */
        if (in->read_status != 0)
            return in->read_status;

        frames_wr += frames_rd;
    }
    return frames_wr;
}

static int in_release_pcm_devices(struct stream_in *in)
{
    struct pcm_device *pcm_device;
    struct listnode *node;
    struct listnode *next;

    list_for_each_safe(node, next, &in->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        list_remove(node);
        free(pcm_device);
    }

    return 0;
}

static int stop_input_stream(struct stream_in *in)
{
    struct audio_usecase *uc_info;
    struct audio_device *adev = in->dev;

    adev->active_input = NULL;
    ALOGV("%s: enter: usecase(%d: %s)", __func__,
          in->usecase, use_case_table[in->usecase]);
    uc_info = get_usecase_from_id(adev, in->usecase);
    if (uc_info == NULL) {
        ALOGE("%s: Could not find the usecase (%d) in the list",
              __func__, in->usecase);
        return -EINVAL;
    }

    /* Disable the tx device */
    disable_snd_device(adev, uc_info, uc_info->in_snd_device, true);

    list_remove(&uc_info->adev_list_node);
    free(uc_info);

    if (list_empty(&in->pcm_dev_list)) {
        ALOGE("%s: pcm device list empty", __func__);
        return -EINVAL;
    }

    in_release_pcm_devices(in);
    list_init(&in->pcm_dev_list);

    return 0;
}

int start_input_stream(struct stream_in *in)
{
    /* Enable output device and stream routing controls */
    int ret = 0;
    bool recreate_resampler = false;
    struct audio_usecase *uc_info;
    struct audio_device *adev = in->dev;
    struct pcm_device_profile *pcm_profile;
    struct pcm_device *pcm_device;

    ALOGV("%s: enter: usecase(%d)", __func__, in->usecase);
    adev->active_input = in;
    pcm_profile = get_pcm_device(in->usecase_type, in->devices);
    if (pcm_profile == NULL) {
        ALOGE("%s: Could not find PCM device id for the usecase(%d)",
              __func__, in->usecase);
        ret = -EINVAL;
        goto error_config;
    }

    if (in->input_flags & AUDIO_INPUT_FLAG_FAST) {
        ALOGV("%s: change capture period size to low latency size %d",
              __func__, CAPTURE_PERIOD_SIZE_LOW_LATENCY);
        pcm_profile->config.period_size = CAPTURE_PERIOD_SIZE_LOW_LATENCY;
    }

    uc_info = (struct audio_usecase *)calloc(1, sizeof(struct audio_usecase));
    uc_info->id = in->usecase;
    uc_info->type = PCM_CAPTURE;
    uc_info->stream = (struct audio_stream *)in;
    uc_info->devices = in->devices;
    uc_info->in_snd_device = SND_DEVICE_NONE;
    uc_info->out_snd_device = SND_DEVICE_NONE;

    pcm_device = (struct pcm_device *)calloc(1, sizeof(struct pcm_device));
    pcm_device->pcm_profile = pcm_profile;
    list_init(&in->pcm_dev_list);
    list_add_tail(&in->pcm_dev_list, &pcm_device->stream_list_node);

    list_init(&uc_info->mixer_list);
    list_add_tail(&uc_info->mixer_list,
                  &adev_get_mixer_for_card(adev,
                                       pcm_device->pcm_profile->card)->uc_list_node[uc_info->id]);

    list_add_tail(&adev->usecase_list, &uc_info->adev_list_node);

    select_devices(adev, in->usecase);

    /* Config should be updated as profile can be changed between different calls
     * to this function:
     * - Trigger resampler creation
     * - Config needs to be updated */
    if (in->config.rate != pcm_profile->config.rate) {
        recreate_resampler = true;
    }
    in->config = pcm_profile->config;

#ifdef PREPROCESSING_ENABLED
    if (in->aux_channels_changed) {
        in->config.channels = audio_channel_count_from_in_mask(in->aux_channels);
        recreate_resampler = true;
    }
#endif

    if (in->requested_rate != in->config.rate) {
        recreate_resampler = true;
    }

    if (recreate_resampler) {
        if (in->resampler) {
            release_resampler(in->resampler);
            in->resampler = NULL;
        }
        in->buf_provider.get_next_buffer = get_next_buffer;
        in->buf_provider.release_buffer = release_buffer;
        ret = create_resampler(in->config.rate,
                               in->requested_rate,
                               in->config.channels,
                               RESAMPLER_QUALITY_DEFAULT,
                               &in->buf_provider,
                               &in->resampler);
    }

    /* Open the PCM device.
     * The HW is limited to support only the default pcm_profile settings.
     * As such a change in aux_channels will not have an effect.
     */
    ALOGV("%s: Opening PCM device card_id(%d) device_id(%d), channels %d, smp rate %d format %d, \
          period_size %d", __func__, pcm_device->pcm_profile->card, pcm_device->pcm_profile->device,
          pcm_device->pcm_profile->config.channels,pcm_device->pcm_profile->config.rate,
          pcm_device->pcm_profile->config.format, pcm_device->pcm_profile->config.period_size);

    if (pcm_profile->type == PCM_HOTWORD_STREAMING) {
        if (!adev->sound_trigger_open_for_streaming) {
            ALOGE("%s: No handle to sound trigger HAL", __func__);
            ret = -EIO;
            goto error_open;
        }
        pcm_device->pcm = NULL;
        pcm_device->sound_trigger_handle =
                adev->sound_trigger_open_for_streaming();
        if (pcm_device->sound_trigger_handle <= 0) {
            ALOGE("%s: Failed to open DSP for streaming", __func__);
            ret = -EIO;
            goto error_open;
        }
        ALOGV("Opened DSP successfully");
    } else {
        pcm_device->sound_trigger_handle = 0;
        pcm_device->pcm = pcm_open(pcm_device->pcm_profile->card,
                                   pcm_device->pcm_profile->device,
                                   PCM_IN | PCM_MONOTONIC,
                                   &pcm_device->pcm_profile->config);
        if (pcm_device->pcm && !pcm_is_ready(pcm_device->pcm)) {
            ALOGE("%s: %s", __func__, pcm_get_error(pcm_device->pcm));
            pcm_close(pcm_device->pcm);
            pcm_device->pcm = NULL;
            ret = -EIO;
            goto error_open;
        }
    }

    /* force read and proc buffer reallocation in case of frame size or
     * channel count change */
#ifdef PREPROCESSING_ENABLED
    in->proc_buf_frames = 0;
#endif
    in->proc_buf_size = 0;
    in->read_buf_size = 0;
    in->read_buf_frames = 0;

    /* if no supported sample rate is available, use the resampler */
    if (in->resampler) {
        in->resampler->reset(in->resampler);
    }

    ALOGV("%s: exit", __func__);
    return ret;

error_open:
    if (in->resampler) {
        release_resampler(in->resampler);
        in->resampler = NULL;
    }
    stop_input_stream(in);

error_config:
    ALOGV("%s: exit: status(%d)", __func__, ret);
    adev->active_input = NULL;
    return ret;
}

static void lock_input_stream(struct stream_in *in)
{
    pthread_mutex_lock(&in->pre_lock);
    pthread_mutex_lock(&in->lock);
    pthread_mutex_unlock(&in->pre_lock);
}

static void lock_output_stream(struct stream_out *out)
{
    pthread_mutex_lock(&out->pre_lock);
    pthread_mutex_lock(&out->lock);
    pthread_mutex_unlock(&out->pre_lock);
}

static int uc_release_pcm_devices(struct audio_usecase *usecase)
{
    struct stream_out *out = (struct stream_out *)usecase->stream;
    struct pcm_device *pcm_device;
    struct listnode *node;
    struct listnode *next;

    list_for_each_safe(node, next, &out->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        list_remove(node);
        free(pcm_device);
    }
    list_init(&usecase->mixer_list);

    return 0;
}

static int uc_select_pcm_devices(struct audio_usecase *usecase)

{
    struct stream_out *out = (struct stream_out *)usecase->stream;
    struct pcm_device *pcm_device;
    struct pcm_device_profile *pcm_profile;
    struct mixer_card *mixer_card;
    audio_devices_t devices = usecase->devices;

    list_init(&usecase->mixer_list);
    list_init(&out->pcm_dev_list);

    pcm_profile = get_pcm_device(usecase->type, devices);
    if (pcm_profile) {
        pcm_device = calloc(1, sizeof(struct pcm_device));
        pcm_device->pcm_profile = pcm_profile;
        list_add_tail(&out->pcm_dev_list, &pcm_device->stream_list_node);
        mixer_card = uc_get_mixer_for_card(usecase, pcm_profile->card);
        if (mixer_card == NULL) {
            mixer_card = adev_get_mixer_for_card(out->dev, pcm_profile->card);
            list_add_tail(&usecase->mixer_list, &mixer_card->uc_list_node[usecase->id]);
        }
        devices &= ~pcm_profile->devices;
    } else {
        ALOGE("usecase type=%d, devices=%d did not find exact match",
            usecase->type, devices);
    }

    return 0;
}

static int out_close_pcm_devices(struct stream_out *out)
{
    struct pcm_device *pcm_device;
    struct listnode *node;
    struct audio_device *adev = out->dev;

    list_for_each(node, &out->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        if (pcm_device->sound_trigger_handle > 0) {
            adev->sound_trigger_close_for_streaming(
                    pcm_device->sound_trigger_handle);
            pcm_device->sound_trigger_handle = 0;
        }
        if (pcm_device->pcm) {
            pcm_close(pcm_device->pcm);
            pcm_device->pcm = NULL;
        }
        if (pcm_device->resampler) {
            release_resampler(pcm_device->resampler);
            pcm_device->resampler = NULL;
        }
        if (pcm_device->res_buffer) {
            free(pcm_device->res_buffer);
            pcm_device->res_buffer = NULL;
        }
        if (pcm_device->dsp_context) {
            cras_dsp_context_free(pcm_device->dsp_context);
            pcm_device->dsp_context = NULL;
        }
    }

    return 0;
}

static int out_open_pcm_devices(struct stream_out *out)
{
    struct pcm_device *pcm_device;
    struct listnode *node;
    struct audio_device *adev = out->dev;
    int ret = 0;

    list_for_each(node, &out->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        ALOGV("%s: Opening PCM device card_id(%d) device_id(%d)",
              __func__, pcm_device->pcm_profile->card, pcm_device->pcm_profile->device);

        if (pcm_device->pcm_profile->dsp_name) {
            pcm_device->dsp_context = cras_dsp_context_new(pcm_device->pcm_profile->config.rate,
                    (adev->mode == AUDIO_MODE_IN_CALL || adev->mode == AUDIO_MODE_IN_COMMUNICATION)
                        ? "voice-comm" : "playback");
            if (pcm_device->dsp_context) {
                cras_dsp_set_variable(pcm_device->dsp_context, "dsp_name",
                                      pcm_device->pcm_profile->dsp_name);
                cras_dsp_load_pipeline(pcm_device->dsp_context);
            }
        }

        pcm_device->pcm = pcm_open(pcm_device->pcm_profile->card, pcm_device->pcm_profile->device,
                               PCM_OUT | PCM_MONOTONIC, &pcm_device->pcm_profile->config);

        if (pcm_device->pcm && !pcm_is_ready(pcm_device->pcm)) {
            ALOGE("%s: %s", __func__, pcm_get_error(pcm_device->pcm));
            pcm_device->pcm = NULL;
            ret = -EIO;
            goto error_open;
        }
        /*
        * If the stream rate differs from the PCM rate, we need to
        * create a resampler.
        */
        if (out->sample_rate != pcm_device->pcm_profile->config.rate) {
            ALOGV("%s: create_resampler(), pcm_device_card(%d), pcm_device_id(%d), \
                    out_rate(%d), device_rate(%d)",__func__,
                    pcm_device->pcm_profile->card, pcm_device->pcm_profile->device,
                    out->sample_rate, pcm_device->pcm_profile->config.rate);
            ret = create_resampler(out->sample_rate,
                    pcm_device->pcm_profile->config.rate,
                    audio_channel_count_from_out_mask(out->channel_mask),
                    RESAMPLER_QUALITY_DEFAULT,
                    NULL,
                    &pcm_device->resampler);
            pcm_device->res_byte_count = 0;
            pcm_device->res_buffer = NULL;
        }
    }
    return ret;

error_open:
    out_close_pcm_devices(out);
    return ret;
}

static int disable_output_path_l(struct stream_out *out)
{
    struct audio_device *adev = out->dev;
    struct audio_usecase *uc_info;

    uc_info = get_usecase_from_id(adev, out->usecase);
    if (uc_info == NULL) {
        ALOGE("%s: Could not find the usecase (%d) in the list",
             __func__, out->usecase);
        return -EINVAL;
    }
    disable_snd_device(adev, uc_info, uc_info->out_snd_device, true);
    uc_release_pcm_devices(uc_info);
    list_remove(&uc_info->adev_list_node);
    free(uc_info);

    return 0;
}

static void enable_output_path_l(struct stream_out *out)
{
    struct audio_device *adev = out->dev;
    struct audio_usecase *uc_info;

    uc_info = (struct audio_usecase *)calloc(1, sizeof(struct audio_usecase));
    uc_info->id = out->usecase;
    uc_info->type = PCM_PLAYBACK;
    uc_info->stream = (struct audio_stream *)out;
    uc_info->devices = out->devices;
    uc_info->in_snd_device = SND_DEVICE_NONE;
    uc_info->out_snd_device = SND_DEVICE_NONE;
    uc_select_pcm_devices(uc_info);

    list_add_tail(&adev->usecase_list, &uc_info->adev_list_node);

    select_devices(adev, out->usecase);
}

static int stop_output_stream(struct stream_out *out)
{
    int ret = 0;
    struct audio_device *adev = out->dev;
    bool do_disable = true;

    ALOGV("%s: enter: usecase(%d: %s)", __func__,
          out->usecase, use_case_table[out->usecase]);

    ret = disable_output_path_l(out);

    ALOGV("%s: exit: status(%d)", __func__, ret);
    return ret;
}

int start_output_stream(struct stream_out *out)
{
    int ret = 0;
    struct audio_device *adev = out->dev;

    ALOGV("%s: enter: usecase(%d: %s) devices(%#x) channels(%d)",
          __func__, out->usecase, use_case_table[out->usecase], out->devices, out->config.channels);

    enable_output_path_l(out);

    ret = out_open_pcm_devices(out);
    if (ret != 0)
        goto error_open;
    ALOGV("%s: exit", __func__);
    return 0;
error_open:
    stop_output_stream(out);
    return ret;
}

static int stop_voice_call(struct audio_device *adev)
{
    struct audio_usecase *uc_info;

    ALOGV("%s: enter", __func__);
    adev->in_call = false;

    /* TODO: implement voice call stop */

    uc_info = get_usecase_from_id(adev, USECASE_VOICE_CALL);
    if (uc_info == NULL) {
        ALOGE("%s: Could not find the usecase (%d) in the list",
              __func__, USECASE_VOICE_CALL);
        return -EINVAL;
    }

    disable_snd_device(adev, uc_info, uc_info->out_snd_device, false);
    disable_snd_device(adev, uc_info, uc_info->in_snd_device, true);

    uc_release_pcm_devices(uc_info);
    list_remove(&uc_info->adev_list_node);
    free(uc_info);

    ALOGV("%s: exit", __func__);
    return 0;
}

/* always called with adev lock held */
static int start_voice_call(struct audio_device *adev)
{
    struct audio_usecase *uc_info;

    ALOGV("%s: enter", __func__);

    uc_info = (struct audio_usecase *)calloc(1, sizeof(struct audio_usecase));
    uc_info->id = USECASE_VOICE_CALL;
    uc_info->type = VOICE_CALL;
    uc_info->stream = (struct audio_stream *)adev->primary_output;
    uc_info->devices = adev->primary_output->devices;
    uc_info->in_snd_device = SND_DEVICE_NONE;
    uc_info->out_snd_device = SND_DEVICE_NONE;

    uc_select_pcm_devices(uc_info);

    list_add_tail(&adev->usecase_list, &uc_info->adev_list_node);

    select_devices(adev, USECASE_VOICE_CALL);


    /* TODO: implement voice call start */

    /* set cached volume */
    set_voice_volume_l(adev, adev->voice_volume);

    adev->in_call = true;
    ALOGV("%s: exit", __func__);
    return 0;
}

static int check_input_parameters(uint32_t sample_rate,
                                  audio_format_t format,
                                  int channel_count)
{
    if (format != AUDIO_FORMAT_PCM_16_BIT) return -EINVAL;

    if ((channel_count < 1) || (channel_count > 4)) return -EINVAL;

    switch (sample_rate) {
    case 8000:
    case 11025:
    case 12000:
    case 16000:
    case 22050:
    case 24000:
    case 32000:
    case 44100:
    case 48000:
        break;
    default:
        return -EINVAL;
    }

    return 0;
}

static size_t get_input_buffer_size(uint32_t sample_rate,
                                    audio_format_t format,
                                    int channel_count,
                                    usecase_type_t usecase_type,
                                    audio_devices_t devices)
{
    size_t size = 0;
    struct pcm_device_profile *pcm_profile;

    if (check_input_parameters(sample_rate, format, channel_count) != 0)
        return 0;

    pcm_profile = get_pcm_device(usecase_type, devices);
    if (pcm_profile == NULL)
        return 0;

    /*
     * take resampling into account and return the closest majoring
     * multiple of 16 frames, as audioflinger expects audio buffers to
     * be a multiple of 16 frames
     */
    size = (pcm_profile->config.period_size * sample_rate) / pcm_profile->config.rate;
    size = ((size + 15) / 16) * 16;

    return (size * channel_count * audio_bytes_per_sample(format));

}

static uint32_t out_get_sample_rate(const struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;

    return out->sample_rate;
}

static int out_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    (void)stream;
    (void)rate;
    return -ENOSYS;
}

static size_t out_get_buffer_size(const struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;

    return out->config.period_size *
               audio_stream_out_frame_size((const struct audio_stream_out *)stream);
}

static uint32_t out_get_channels(const struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;

    return out->channel_mask;
}

static audio_format_t out_get_format(const struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;

    return out->format;
}

static int out_set_format(struct audio_stream *stream, audio_format_t format)
{
    (void)stream;
    (void)format;
    return -ENOSYS;
}

static int do_out_standby_l(struct stream_out *out)
{
    struct audio_device *adev = out->dev;
    int status = 0;

    out->standby = true;
    out_close_pcm_devices(out);
    status = stop_output_stream(out);

    return status;
}

static int out_standby(struct audio_stream *stream)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct audio_device *adev = out->dev;

    ALOGV("%s: enter: usecase(%d: %s)", __func__,
          out->usecase, use_case_table[out->usecase]);
    lock_output_stream(out);
    if (!out->standby) {
        pthread_mutex_lock(&adev->lock);
        do_out_standby_l(out);
        pthread_mutex_unlock(&adev->lock);
    }
    pthread_mutex_unlock(&out->lock);
    ALOGV("%s: exit", __func__);
    return 0;
}

static int out_dump(const struct audio_stream *stream, int fd)
{
    (void)stream;
    (void)fd;

    return 0;
}

static int out_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct audio_device *adev = out->dev;
    struct audio_usecase *usecase;
    struct listnode *node;
    struct str_parms *parms;
    char value[32];
    int ret, val = 0;
    bool devices_changed;
    struct pcm_device *pcm_device;
    struct pcm_device_profile *pcm_profile;
#ifdef PREPROCESSING_ENABLED
    struct stream_in *in = NULL;    /* if non-NULL, then force input to standby */
#endif

    ALOGV("%s: enter: usecase(%d: %s) kvpairs: %s out->devices(%d) adev->mode(%d)",
          __func__, out->usecase, use_case_table[out->usecase], kvpairs, out->devices, adev->mode);
    parms = str_parms_create_str(kvpairs);
    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING, value, sizeof(value));
    if (ret >= 0) {
        val = atoi(value);
        pthread_mutex_lock(&adev->lock_inputs);
        lock_output_stream(out);
        pthread_mutex_lock(&adev->lock);
#ifdef PREPROCESSING_ENABLED
        if (((int)out->devices != val) && (val != 0) && (!out->standby) &&
            (out->usecase == USECASE_AUDIO_PLAYBACK)) {
            /* reset active input:
             *  - to attach the echo reference
             *  - because a change in output device may change mic settings */
            if (adev->active_input && (adev->active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION ||
                    adev->active_input->source == AUDIO_SOURCE_MIC)) {
                in = adev->active_input;
            }
        }
#endif
        if (val != 0) {
            devices_changed = out->devices != (audio_devices_t)val;
            out->devices = val;

            if (!out->standby) {
                if (devices_changed)
                    do_out_standby_l(out);
                else
                    select_devices(adev, out->usecase);
            }

            if ((adev->mode == AUDIO_MODE_IN_CALL) && !adev->in_call &&
                    (out == adev->primary_output)) {
                start_voice_call(adev);
            } else if ((adev->mode == AUDIO_MODE_IN_CALL) && adev->in_call &&
                       (out == adev->primary_output)) {
                select_devices(adev, USECASE_VOICE_CALL);
            }
        }

        if ((adev->mode == AUDIO_MODE_NORMAL) && adev->in_call &&
                (out == adev->primary_output)) {
            stop_voice_call(adev);
        }
        pthread_mutex_unlock(&adev->lock);
        pthread_mutex_unlock(&out->lock);
#ifdef PREPROCESSING_ENABLED
        if (in) {
            /* The lock on adev->lock_inputs prevents input stream from being closed */
            lock_input_stream(in);
            pthread_mutex_lock(&adev->lock);
            LOG_ALWAYS_FATAL_IF(in != adev->active_input);
            do_in_standby_l(in);
            pthread_mutex_unlock(&adev->lock);
            pthread_mutex_unlock(&in->lock);
        }
#endif
        pthread_mutex_unlock(&adev->lock_inputs);
    }

    str_parms_destroy(parms);
    ALOGV("%s: exit: code(%d)", __func__, ret);
    return ret;
}

static char* out_get_parameters(const struct audio_stream *stream, const char *keys)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct str_parms *query = str_parms_create_str(keys);
    char *str;
    char value[256];
    struct str_parms *reply = str_parms_create();
    size_t i, j;
    int ret;
    bool first = true;
    ALOGV("%s: enter: keys - %s", __func__, keys);
    ret = str_parms_get_str(query, AUDIO_PARAMETER_STREAM_SUP_CHANNELS, value, sizeof(value));
    if (ret >= 0) {
        value[0] = '\0';
        i = 0;
        while (out->supported_channel_masks[i] != 0) {
            for (j = 0; j < ARRAY_SIZE(out_channels_name_to_enum_table); j++) {
                if (out_channels_name_to_enum_table[j].value == out->supported_channel_masks[i]) {
                    if (!first) {
                        strcat(value, "|");
                    }
                    strcat(value, out_channels_name_to_enum_table[j].name);
                    first = false;
                    break;
                }
            }
            i++;
        }
        str_parms_add_str(reply, AUDIO_PARAMETER_STREAM_SUP_CHANNELS, value);
        str = str_parms_to_str(reply);
    } else {
        str = strdup(keys);
    }
    str_parms_destroy(query);
    str_parms_destroy(reply);
    ALOGV("%s: exit: returns - %s", __func__, str);
    return str;
}

static uint32_t out_get_latency(const struct audio_stream_out *stream)
{
    struct stream_out *out = (struct stream_out *)stream;

    return (out->config.period_count * out->config.period_size * 1000) /
           (out->config.rate);
}

static int out_set_volume(struct audio_stream_out *stream, float left,
                          float right)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct audio_device *adev = out->dev;
    (void)right;

    if (out->usecase == USECASE_AUDIO_PLAYBACK_MULTI_CH) {
        /* only take left channel into account: the API is for stereo anyway */
        out->muted = (left == 0.0f);
        return 0;
    }

    return -ENOSYS;
}

/* Applies the DSP to the samples for the iodev if applicable. */
static void apply_dsp(struct pcm_device *iodev, uint8_t *buf, size_t frames)
{
	struct cras_dsp_context *ctx;
	struct pipeline *pipeline;

	ctx = iodev->dsp_context;
	if (!ctx)
		return;

	pipeline = cras_dsp_get_pipeline(ctx);
	if (!pipeline)
		return;

	cras_dsp_pipeline_apply(pipeline,
				buf,
				frames);

	cras_dsp_put_pipeline(ctx);
}

static ssize_t out_write(struct audio_stream_out *stream, const void *buffer,
                         size_t bytes)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct audio_device *adev = out->dev;
    ssize_t ret = 0;
    struct pcm_device *pcm_device;
    struct listnode *node;
    size_t frame_size = audio_stream_out_frame_size(stream);
    size_t frames_wr = 0, frames_rq = 0;
    unsigned char *data = NULL;
    struct pcm_config config;
#ifdef PREPROCESSING_ENABLED
    size_t in_frames = bytes / frame_size;
    size_t out_frames = in_frames;
    struct stream_in *in = NULL;
#endif

    lock_output_stream(out);
    if (out->standby) {
#ifdef PREPROCESSING_ENABLED
        pthread_mutex_unlock(&out->lock);
        /* Prevent input stream from being closed */
        pthread_mutex_lock(&adev->lock_inputs);
        lock_output_stream(out);
        if (!out->standby) {
            pthread_mutex_unlock(&adev->lock_inputs);
            goto false_alarm;
        }
#endif
        pthread_mutex_lock(&adev->lock);
        ret = start_output_stream(out);
        if (ret != 0) {
            pthread_mutex_unlock(&adev->lock);
#ifdef PREPROCESSING_ENABLED
            pthread_mutex_unlock(&adev->lock_inputs);
#endif
            goto exit;
        }
        out->standby = false;

#ifdef PREPROCESSING_ENABLED
        /* A change in output device may change the microphone selection */
        if (adev->active_input &&
            (adev->active_input->source == AUDIO_SOURCE_VOICE_COMMUNICATION ||
                adev->active_input->source == AUDIO_SOURCE_MIC)) {
                    in = adev->active_input;
                    ALOGV("%s: enter: force_input_standby true", __func__);
        }
#endif
        pthread_mutex_unlock(&adev->lock);
#ifdef PREPROCESSING_ENABLED
        if (!in) {
            /* Leave mutex locked iff in != NULL */
            pthread_mutex_unlock(&adev->lock_inputs);
        }
#endif
    }
false_alarm:

    if (out->muted)
        memset((void *)buffer, 0, bytes);
    list_for_each(node, &out->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        if (pcm_device->resampler) {
            if (bytes * pcm_device->pcm_profile->config.rate / out->sample_rate + frame_size
                    > pcm_device->res_byte_count) {
                pcm_device->res_byte_count =
                    bytes * pcm_device->pcm_profile->config.rate / out->sample_rate + frame_size;
                pcm_device->res_buffer =
                    realloc(pcm_device->res_buffer, pcm_device->res_byte_count);
                ALOGV("%s: resampler res_byte_count = %zu", __func__,
                    pcm_device->res_byte_count);
            }
            frames_rq = bytes / frame_size;
            frames_wr = pcm_device->res_byte_count / frame_size;
            ALOGVV("%s: resampler request frames = %zu frame_size = %zu",
                __func__, frames_rq, frame_size);
            pcm_device->resampler->resample_from_input(pcm_device->resampler,
                (int16_t *)buffer, &frames_rq, (int16_t *)pcm_device->res_buffer, &frames_wr);
            ALOGVV("%s: resampler output frames_= %zu", __func__, frames_wr);
        }
        if (pcm_device->pcm) {
            size_t src_channels = audio_channel_count_from_out_mask(out->channel_mask);
            size_t dst_channels = pcm_device->pcm_profile->config.channels;
            bool channel_remapping_needed = (dst_channels != src_channels);
            unsigned audio_bytes;
            const void *audio_data;

            ALOGVV("%s: writing buffer (%zd bytes) to pcm device", __func__, bytes);
            if (pcm_device->resampler && pcm_device->res_buffer) {
                audio_data = pcm_device->res_buffer;
                audio_bytes = frames_wr * frame_size;
            } else {
                audio_data = buffer;
                audio_bytes = bytes;
            }

            /*
             * This can only be S16_LE stereo because of the supported formats,
             * 4 bytes per frame.
             */
            apply_dsp(pcm_device, audio_data, audio_bytes/4);

            if (channel_remapping_needed) {
                const void *remapped_audio_data;
                size_t dest_buffer_size = audio_bytes * dst_channels / src_channels;
                size_t new_size;
                size_t bytes_per_sample = audio_bytes_per_sample(stream->common.get_format(&stream->common));

                /* With additional channels, we cannot use original buffer */
                if (out->proc_buf_size < dest_buffer_size) {
                    out->proc_buf_size = dest_buffer_size;
                    out->proc_buf_out = realloc(out->proc_buf_out, dest_buffer_size);
                    ALOG_ASSERT((out->proc_buf_out != NULL),
                                "out_write() failed to reallocate proc_buf_out");
                }
                new_size = adjust_channels(audio_data, src_channels, out->proc_buf_out, dst_channels,
                    bytes_per_sample, audio_bytes);
                ALOG_ASSERT(new_size == dest_buffer_size);
                audio_data = out->proc_buf_out;
                audio_bytes = dest_buffer_size;
            }

            pcm_device->status = pcm_write(pcm_device->pcm, audio_data, audio_bytes);
            if (pcm_device->status != 0)
                ret = pcm_device->status;
        }
    }
    if (ret == 0)
        out->written += bytes / frame_size;

exit:
    pthread_mutex_unlock(&out->lock);

    if (ret != 0) {
        list_for_each(node, &out->pcm_dev_list) {
            pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
            if (pcm_device->pcm && pcm_device->status != 0)
                ALOGE("%s: error %zd - %s", __func__, ret, pcm_get_error(pcm_device->pcm));
        }
        out_standby(&out->stream.common);
        usleep(bytes * 1000000 / audio_stream_out_frame_size(stream) /
               out_get_sample_rate(&out->stream.common));
    }

#ifdef PREPROCESSING_ENABLED
    if (in) {
        /* The lock on adev->lock_inputs prevents input stream from being closed */
        lock_input_stream(in);
        pthread_mutex_lock(&adev->lock);
        LOG_ALWAYS_FATAL_IF(in != adev->active_input);
        do_in_standby_l(in);
        pthread_mutex_unlock(&adev->lock);
        pthread_mutex_unlock(&in->lock);
        /* This mutex was left locked iff in != NULL */
        pthread_mutex_unlock(&adev->lock_inputs);
    }
#endif

    return bytes;
}

static int out_get_render_position(const struct audio_stream_out *stream,
                                   uint32_t *dsp_frames)
{
    (void)stream;
    *dsp_frames = 0;
    return -EINVAL;
}

static int out_add_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    (void)stream;
    (void)effect;
    return 0;
}

static int out_remove_audio_effect(const struct audio_stream *stream, effect_handle_t effect)
{
    (void)stream;
    (void)effect;
    return 0;
}

static int out_get_next_write_timestamp(const struct audio_stream_out *stream,
                                        int64_t *timestamp)
{
    (void)stream;
    (void)timestamp;
    return -EINVAL;
}

static int out_get_presentation_position(const struct audio_stream_out *stream,
                                   uint64_t *frames, struct timespec *timestamp)
{
    struct stream_out *out = (struct stream_out *)stream;
    int ret = -1;
    unsigned long dsp_frames;

    lock_output_stream(out);

    /* FIXME: which device to read from? */
    if (!list_empty(&out->pcm_dev_list)) {
        unsigned int avail;
        struct pcm_device *pcm_device = node_to_item(list_head(&out->pcm_dev_list),
                                               struct pcm_device, stream_list_node);

        if (pcm_get_htimestamp(pcm_device->pcm, &avail, timestamp) == 0) {
            size_t kernel_buffer_size = out->config.period_size * out->config.period_count;
            int64_t signed_frames = out->written - kernel_buffer_size + avail;
            /* This adjustment accounts for buffering after app processor.
               It is based on estimated DSP latency per use case, rather than exact. */
            signed_frames -=
                (render_latency(out->usecase) * out->sample_rate / 1000000LL);

            /* It would be unusual for this value to be negative, but check just in case ... */
            if (signed_frames >= 0) {
                *frames = signed_frames;
                ret = 0;
            }
        }
    }

    pthread_mutex_unlock(&out->lock);

    return ret;
}

/** audio_stream_in implementation **/
static uint32_t in_get_sample_rate(const struct audio_stream *stream)
{
    struct stream_in *in = (struct stream_in *)stream;

    return in->requested_rate;
}

static int in_set_sample_rate(struct audio_stream *stream, uint32_t rate)
{
    (void)stream;
    (void)rate;
    return -ENOSYS;
}

static uint32_t in_get_channels(const struct audio_stream *stream)
{
    struct stream_in *in = (struct stream_in *)stream;

    return in->main_channels;
}

static audio_format_t in_get_format(const struct audio_stream *stream)
{
    (void)stream;
    return AUDIO_FORMAT_PCM_16_BIT;
}

static int in_set_format(struct audio_stream *stream, audio_format_t format)
{
    (void)stream;
    (void)format;

    return -ENOSYS;
}

static size_t in_get_buffer_size(const struct audio_stream *stream)
{
    struct stream_in *in = (struct stream_in *)stream;

    return get_input_buffer_size(in->requested_rate,
                                 in_get_format(stream),
                                 audio_channel_count_from_in_mask(in->main_channels),
                                 in->usecase_type,
                                 in->devices);
}

static int in_close_pcm_devices(struct stream_in *in)
{
    struct pcm_device *pcm_device;
    struct listnode *node;
    struct audio_device *adev = in->dev;

    list_for_each(node, &in->pcm_dev_list) {
        pcm_device = node_to_item(node, struct pcm_device, stream_list_node);
        if (pcm_device) {
            if (pcm_device->pcm)
                pcm_close(pcm_device->pcm);
            pcm_device->pcm = NULL;
            if (pcm_device->sound_trigger_handle > 0)
                adev->sound_trigger_close_for_streaming(
                        pcm_device->sound_trigger_handle);
            pcm_device->sound_trigger_handle = 0;
        }
    }
    return 0;
}


/* must be called with stream and hw device mutex locked */
static int do_in_standby_l(struct stream_in *in)
{
    int status = 0;

    if (!in->standby) {

        in_close_pcm_devices(in);

        status = stop_input_stream(in);

        if (in->read_buf) {
            free(in->read_buf);
            in->read_buf = NULL;
        }

        in->standby = 1;
    }
    return 0;
}

// called with adev->lock_inputs locked
static int in_standby_l(struct stream_in *in)
{
    struct audio_device *adev = in->dev;
    int status = 0;
    lock_input_stream(in);
    if (!in->standby) {
        pthread_mutex_lock(&adev->lock);
        status = do_in_standby_l(in);
        pthread_mutex_unlock(&adev->lock);
    }
    pthread_mutex_unlock(&in->lock);
    return status;
}

static int in_standby(struct audio_stream *stream)
{
    struct stream_in *in = (struct stream_in *)stream;
    struct audio_device *adev = in->dev;
    int status;
    ALOGV("%s: enter", __func__);
    pthread_mutex_lock(&adev->lock_inputs);
    status = in_standby_l(in);
    pthread_mutex_unlock(&adev->lock_inputs);
    ALOGV("%s: exit:  status(%d)", __func__, status);
    return status;
}

static int in_dump(const struct audio_stream *stream, int fd)
{
    (void)stream;
    (void)fd;

    return 0;
}

static int in_set_parameters(struct audio_stream *stream, const char *kvpairs)
{
    struct stream_in *in = (struct stream_in *)stream;
    struct audio_device *adev = in->dev;
    struct str_parms *parms;
    char *str;
    char value[32];
    int ret, val = 0;
    struct audio_usecase *uc_info;
    bool do_standby = false;
    struct listnode *node;
    struct pcm_device *pcm_device;
    struct pcm_device_profile *pcm_profile;

    ALOGV("%s: enter: kvpairs=%s", __func__, kvpairs);
    parms = str_parms_create_str(kvpairs);

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_INPUT_SOURCE, value, sizeof(value));

    pthread_mutex_lock(&adev->lock_inputs);
    lock_input_stream(in);
    pthread_mutex_lock(&adev->lock);
    if (ret >= 0) {
        val = atoi(value);
        /* no audio source uses val == 0 */
        if (((int)in->source != val) && (val != 0)) {
            in->source = val;
        }
    }

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_STREAM_ROUTING, value, sizeof(value));
    if (ret >= 0) {
        val = atoi(value);
        if (((int)in->devices != val) && (val != 0)) {
            in->devices = val;
            /* If recording is in progress, change the tx device to new device */
            if (!in->standby) {
                uc_info = get_usecase_from_id(adev, in->usecase);
                if (uc_info == NULL) {
                    ALOGE("%s: Could not find the usecase (%d) in the list",
                          __func__, in->usecase);
                } else {
                    if (list_empty(&in->pcm_dev_list))
                        ALOGE("%s: pcm device list empty", __func__);
                    else {
                        pcm_device = node_to_item(list_head(&in->pcm_dev_list),
                                                  struct pcm_device, stream_list_node);
                        if ((pcm_device->pcm_profile->devices & val & ~AUDIO_DEVICE_BIT_IN) == 0) {
                            do_standby = true;
                        }
                    }
                }
                if (do_standby) {
                    ret = do_in_standby_l(in);
                } else
                    ret = select_devices(adev, in->usecase);
            }
        }
    }
    pthread_mutex_unlock(&adev->lock);
    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&adev->lock_inputs);
    str_parms_destroy(parms);

    if (ret > 0)
        ret = 0;

    return ret;
}

static char* in_get_parameters(const struct audio_stream *stream,
                               const char *keys)
{
    (void)stream;
    (void)keys;

    return strdup("");
}

static int in_set_gain(struct audio_stream_in *stream, float gain)
{
    (void)stream;
    (void)gain;

    return 0;
}

static ssize_t read_bytes_from_dsp(struct stream_in *in, void* buffer,
                                   size_t bytes)
{
    struct pcm_device *pcm_device;
    struct audio_device *adev = in->dev;

    pcm_device = node_to_item(list_head(&in->pcm_dev_list),
                              struct pcm_device, stream_list_node);

    if (pcm_device->sound_trigger_handle > 0)
        return adev->sound_trigger_read_samples(
                pcm_device->sound_trigger_handle, buffer, bytes);
    else
        return 0;
}

static ssize_t in_read(struct audio_stream_in *stream, void *buffer,
                       size_t bytes)
{
    struct stream_in *in = (struct stream_in *)stream;
    struct audio_device *adev = in->dev;
    ssize_t frames = -1;
    int ret = -1;
    int read_and_process_successful = false;

    size_t frames_rq = bytes / audio_stream_in_frame_size(stream);

    /* no need to acquire adev->lock_inputs because API contract prevents a close */
    lock_input_stream(in);
    if (in->standby) {
        pthread_mutex_unlock(&in->lock);
        pthread_mutex_lock(&adev->lock_inputs);
        lock_input_stream(in);
        if (!in->standby) {
            pthread_mutex_unlock(&adev->lock_inputs);
            goto false_alarm;
        }
        pthread_mutex_lock(&adev->lock);
        ret = start_input_stream(in);
        pthread_mutex_unlock(&adev->lock);
        pthread_mutex_unlock(&adev->lock_inputs);
        if (ret != 0) {
            goto exit;
        }
        in->standby = 0;
    }
false_alarm:

    if (!list_empty(&in->pcm_dev_list)) {
        if (in->usecase == USECASE_AUDIO_CAPTURE_HOTWORD) {
            bytes = read_bytes_from_dsp(in, buffer, bytes);
            if (bytes > 0)
                read_and_process_successful = true;
        } else {
            /*
             * Read PCM and:
             * - resample if needed
             * - process if pre-processors are attached
             * - discard unwanted channels
             */
            frames = read_and_process_frames(stream, buffer, frames_rq);
            if (frames >= 0)
                read_and_process_successful = true;
        }
    }

    /*
     * Instead of writing zeroes here, we could trust the hardware
     * to always provide zeroes when muted.
     */
    if (read_and_process_successful == true && adev->mic_mute)
        memset(buffer, 0, bytes);

exit:
    pthread_mutex_unlock(&in->lock);

    if (read_and_process_successful == false) {
        in_standby(&in->stream.common);
        ALOGV("%s: read failed - sleeping for buffer duration", __func__);
        usleep(bytes * 1000000 / audio_stream_in_frame_size(stream) /
               in->requested_rate);
    }
    return bytes;
}

static uint32_t in_get_input_frames_lost(struct audio_stream_in *stream)
{
    (void)stream;

    return 0;
}

static int add_remove_audio_effect(const struct audio_stream *stream,
                                   effect_handle_t effect,
                                   bool enable)
{
    struct stream_in *in = (struct stream_in *)stream;
    struct audio_device *adev = in->dev;
    int status = 0;
    effect_descriptor_t desc;
#ifdef PREPROCESSING_ENABLED
    int i;
#endif
    status = (*effect)->get_descriptor(effect, &desc);
    if (status != 0)
        return status;

    ALOGI("add_remove_audio_effect(), effect type: %08x, enable: %d ", desc.type.timeLow, enable);

    pthread_mutex_lock(&adev->lock_inputs);
    lock_input_stream(in);
    pthread_mutex_lock(&in->dev->lock);
#ifndef PREPROCESSING_ENABLED
    if ((in->source == AUDIO_SOURCE_VOICE_COMMUNICATION) &&
            in->enable_aec != enable &&
            (memcmp(&desc.type, FX_IID_AEC, sizeof(effect_uuid_t)) == 0)) {
        in->enable_aec = enable;
        if (!in->standby)
            select_devices(in->dev, in->usecase);
    }
#else
    if (enable) {
        if (in->num_preprocessors >= MAX_PREPROCESSORS) {
            status = -ENOSYS;
            goto exit;
        }
        in->preprocessors[in->num_preprocessors].effect_itfe = effect;
        in->num_preprocessors ++;
        /* check compatibility between main channel supported and possible auxiliary channels */
        in_update_aux_channels(in, effect);//wesley crash
        in->aux_channels_changed = true;
    } else {
        /* if ( enable == false ) */
        if (in->num_preprocessors <= 0) {
            status = -ENOSYS;
            goto exit;
        }
        status = -EINVAL;
        for (i = 0; i < in->num_preprocessors && status != 0; i++) {
            if ( in->preprocessors[i].effect_itfe == effect ) {
                ALOGV("add_remove_audio_effect found fx at index %d", i);
                free(in->preprocessors[i].channel_configs);
                in->num_preprocessors--;
                memcpy(in->preprocessors + i,
                       in->preprocessors + i + 1,
                       (in->num_preprocessors - i) * sizeof(in->preprocessors[0]));
                memset(in->preprocessors + in->num_preprocessors,
                       0,
                       sizeof(in->preprocessors[0]));
                status = 0;
            }
        }
        if (status != 0)
            goto exit;
        in->aux_channels_changed = false;
        ALOGV("%s: enable(%d), in->aux_channels_changed(%d)",
              __func__, enable, in->aux_channels_changed);
    }
    ALOGI("%s:  num_preprocessors = %d", __func__, in->num_preprocessors);

exit:
#endif
    ALOGW_IF(status != 0, "add_remove_audio_effect() error %d", status);
    pthread_mutex_unlock(&in->dev->lock);
    pthread_mutex_unlock(&in->lock);
    pthread_mutex_unlock(&adev->lock_inputs);
    return status;
}

static int in_add_audio_effect(const struct audio_stream *stream,
                               effect_handle_t effect)
{
    ALOGV("%s: effect %p", __func__, effect);
    return add_remove_audio_effect(stream, effect, true /* enabled */);
}

static int in_remove_audio_effect(const struct audio_stream *stream,
                                  effect_handle_t effect)
{
    ALOGV("%s: effect %p", __func__, effect);
    return add_remove_audio_effect(stream, effect, false /* disabled */);
}

static int adev_open_output_stream(struct audio_hw_device *dev,
                                   audio_io_handle_t handle,
                                   audio_devices_t devices,
                                   audio_output_flags_t flags,
                                   struct audio_config *config,
                                   struct audio_stream_out **stream_out,
                                   const char *address __unused)
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct stream_out *out;
    int i, ret;
    struct pcm_device_profile *pcm_profile;

    ALOGV("%s: enter: sample_rate(%d) channel_mask(%#x) devices(%#x) flags(%#x)",
          __func__, config->sample_rate, config->channel_mask, devices, flags);
    *stream_out = NULL;
    out = (struct stream_out *)calloc(1, sizeof(struct stream_out));

    if (devices == AUDIO_DEVICE_NONE)
        devices = AUDIO_DEVICE_OUT_SPEAKER;

    out->flags = flags;
    out->devices = devices;
    out->dev = adev;
    out->format = config->format;
    out->sample_rate = config->sample_rate;
    out->channel_mask = AUDIO_CHANNEL_OUT_STEREO;
    out->supported_channel_masks[0] = AUDIO_CHANNEL_OUT_STEREO;
    out->handle = handle;

    pcm_profile = get_pcm_device(PCM_PLAYBACK, devices);
    if (pcm_profile == NULL) {
        ret = -EINVAL;
        goto error_open;
    }
    out->config = pcm_profile->config;

    /* Init use case and pcm_config */
    if (out->flags & (AUDIO_OUTPUT_FLAG_DEEP_BUFFER)) {
        out->usecase = USECASE_AUDIO_PLAYBACK_DEEP_BUFFER;
        out->config = pcm_config_deep_buffer;
        out->sample_rate = out->config.rate;
        ALOGV("%s: use AUDIO_PLAYBACK_DEEP_BUFFER",__func__);
    } else {
        out->usecase = USECASE_AUDIO_PLAYBACK;
        out->sample_rate = out->config.rate;
    }

    if (flags & AUDIO_OUTPUT_FLAG_PRIMARY) {
        if (adev->primary_output == NULL)
            adev->primary_output = out;
        else {
            ALOGE("%s: Primary output is already opened", __func__);
            ret = -EEXIST;
            goto error_open;
        }
    }

    /* Check if this usecase is already existing */
    pthread_mutex_lock(&adev->lock);
    if (get_usecase_from_id(adev, out->usecase) != NULL) {
        ALOGE("%s: Usecase (%d) is already present", __func__, out->usecase);
        pthread_mutex_unlock(&adev->lock);
        ret = -EEXIST;
        goto error_open;
    }
    pthread_mutex_unlock(&adev->lock);

    out->stream.common.get_sample_rate = out_get_sample_rate;
    out->stream.common.set_sample_rate = out_set_sample_rate;
    out->stream.common.get_buffer_size = out_get_buffer_size;
    out->stream.common.get_channels = out_get_channels;
    out->stream.common.get_format = out_get_format;
    out->stream.common.set_format = out_set_format;
    out->stream.common.standby = out_standby;
    out->stream.common.dump = out_dump;
    out->stream.common.set_parameters = out_set_parameters;
    out->stream.common.get_parameters = out_get_parameters;
    out->stream.common.add_audio_effect = out_add_audio_effect;
    out->stream.common.remove_audio_effect = out_remove_audio_effect;
    out->stream.get_latency = out_get_latency;
    out->stream.set_volume = out_set_volume;
    out->stream.write = out_write;
    out->stream.get_render_position = out_get_render_position;
    out->stream.get_next_write_timestamp = out_get_next_write_timestamp;
    out->stream.get_presentation_position = out_get_presentation_position;

    out->standby = 1;
    /* out->muted = false; by calloc() */
    /* out->written = 0; by calloc() */

    pthread_mutex_init(&out->lock, (const pthread_mutexattr_t *) NULL);
    pthread_mutex_init(&out->pre_lock, (const pthread_mutexattr_t *) NULL);
    pthread_cond_init(&out->cond, (const pthread_condattr_t *) NULL);

    config->format = out->stream.common.get_format(&out->stream.common);
    config->channel_mask = out->stream.common.get_channels(&out->stream.common);
    config->sample_rate = out->stream.common.get_sample_rate(&out->stream.common);

    *stream_out = &out->stream;
    ALOGV("%s: exit", __func__);
    return 0;

error_open:
    free(out);
    *stream_out = NULL;
    ALOGV("%s: exit: ret %d", __func__, ret);
    return ret;
}

static void adev_close_output_stream(struct audio_hw_device *dev,
                                     struct audio_stream_out *stream)
{
    struct stream_out *out = (struct stream_out *)stream;
    struct audio_device *adev = out->dev;
    (void)dev;

    ALOGV("%s: enter", __func__);
    out_standby(&stream->common);
    pthread_cond_destroy(&out->cond);
    pthread_mutex_destroy(&out->lock);
    pthread_mutex_destroy(&out->pre_lock);
    free(out->proc_buf_out);
    free(stream);
    ALOGV("%s: exit", __func__);
}

static int adev_set_parameters(struct audio_hw_device *dev, const char *kvpairs)
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct str_parms *parms;
    char *str;
    char value[32];
    int val;
    int ret;

    ALOGV("%s: enter: %s", __func__, kvpairs);

    parms = str_parms_create_str(kvpairs);
    ret = str_parms_get_str(parms, AUDIO_PARAMETER_KEY_TTY_MODE, value, sizeof(value));
    if (ret >= 0) {
        int tty_mode;

        if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_OFF) == 0)
            tty_mode = TTY_MODE_OFF;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_VCO) == 0)
            tty_mode = TTY_MODE_VCO;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_HCO) == 0)
            tty_mode = TTY_MODE_HCO;
        else if (strcmp(value, AUDIO_PARAMETER_VALUE_TTY_FULL) == 0)
            tty_mode = TTY_MODE_FULL;
        else
            return -EINVAL;

        pthread_mutex_lock(&adev->lock);
        if (tty_mode != adev->tty_mode) {
            adev->tty_mode = tty_mode;
            if (adev->in_call)
                select_devices(adev, USECASE_VOICE_CALL);
        }
        pthread_mutex_unlock(&adev->lock);
    }

    ret = str_parms_get_str(parms, AUDIO_PARAMETER_KEY_BT_NREC, value, sizeof(value));
    if (ret >= 0) {
        /* When set to false, HAL should disable EC and NS
         * But it is currently not supported.
         */
        if (strcmp(value, AUDIO_PARAMETER_VALUE_ON) == 0)
            adev->bluetooth_nrec = true;
        else
            adev->bluetooth_nrec = false;
    }

    ret = str_parms_get_str(parms, "screen_state", value, sizeof(value));
    if (ret >= 0) {
        if (strcmp(value, AUDIO_PARAMETER_VALUE_ON) == 0)
            adev->screen_off = false;
        else
            adev->screen_off = true;
    }

    ret = str_parms_get_int(parms, "rotation", &val);
    if (ret >= 0) {
        bool reverse_speakers = false;
        switch(val) {
        /* Assume 0deg rotation means the front camera is up with the usb port
         * on the lower left when the user is facing the screen. This assumption
         * is device-specific, not platform-specific like this code.
         */
        case 180:
            reverse_speakers = true;
            break;
        case 0:
        case 90:
        case 270:
            break;
        default:
            ALOGE("%s: unexpected rotation of %d", __func__, val);
        }
        pthread_mutex_lock(&adev->lock);
        if (adev->speaker_lr_swap != reverse_speakers) {
            adev->speaker_lr_swap = reverse_speakers;
            struct mixer_card *mixer_card;
            mixer_card = adev_get_mixer_for_card(adev, SOUND_CARD);
            if (mixer_card)
                audio_route_apply_and_update_path(mixer_card->audio_route,
                        reverse_speakers ? "speaker-lr-reverse" :
                                           "speaker-lr-normal");
        }
        pthread_mutex_unlock(&adev->lock);
    }

    str_parms_destroy(parms);
    ALOGV("%s: exit with code(%d)", __func__, ret);
    return ret;
}

static char* adev_get_parameters(const struct audio_hw_device *dev,
                                 const char *keys)
{
    (void)dev;
    (void)keys;

    return strdup("");
}

static int adev_init_check(const struct audio_hw_device *dev)
{
    (void)dev;

    return 0;
}

static int adev_set_voice_volume(struct audio_hw_device *dev, float volume)
{
    int ret = 0;
    struct audio_device *adev = (struct audio_device *)dev;
    pthread_mutex_lock(&adev->lock);
    /* cache volume */
    adev->voice_volume = volume;
    ret = set_voice_volume_l(adev, adev->voice_volume);
    pthread_mutex_unlock(&adev->lock);
    return ret;
}

static int adev_set_master_volume(struct audio_hw_device *dev, float volume)
{
    (void)dev;
    (void)volume;

    return -ENOSYS;
}

static int adev_get_master_volume(struct audio_hw_device *dev,
                                  float *volume)
{
    (void)dev;
    (void)volume;

    return -ENOSYS;
}

static int adev_set_master_mute(struct audio_hw_device *dev, bool muted)
{
    (void)dev;
    (void)muted;

    return -ENOSYS;
}

static int adev_get_master_mute(struct audio_hw_device *dev, bool *muted)
{
    (void)dev;
    (void)muted;

    return -ENOSYS;
}

static int adev_set_mode(struct audio_hw_device *dev, audio_mode_t mode)
{
    struct audio_device *adev = (struct audio_device *)dev;

    pthread_mutex_lock(&adev->lock);
    if (adev->mode != mode) {
        ALOGI("%s mode = %d", __func__, mode);
        adev->mode = mode;
    }
    pthread_mutex_unlock(&adev->lock);
    return 0;
}

static int adev_set_mic_mute(struct audio_hw_device *dev, bool state)
{
    struct audio_device *adev = (struct audio_device *)dev;
    int err = 0;

    pthread_mutex_lock(&adev->lock);
    adev->mic_mute = state;

    if (adev->mode == AUDIO_MODE_IN_CALL) {
        /* TODO */
    }

    pthread_mutex_unlock(&adev->lock);
    return err;
}

static int adev_get_mic_mute(const struct audio_hw_device *dev, bool *state)
{
    struct audio_device *adev = (struct audio_device *)dev;

    *state = adev->mic_mute;

    return 0;
}

static size_t adev_get_input_buffer_size(const struct audio_hw_device *dev,
                                         const struct audio_config *config)
{
    (void)dev;

    /* NOTE: we default to built in mic which may cause a mismatch between what we
     * report here and the actual buffer size
     */
    return get_input_buffer_size(config->sample_rate,
                                 config->format,
                                 audio_channel_count_from_in_mask(config->channel_mask),
                                 PCM_CAPTURE /* usecase_type */,
                                 AUDIO_DEVICE_IN_BUILTIN_MIC);
}

static int adev_open_input_stream(struct audio_hw_device *dev,
                                  audio_io_handle_t handle __unused,
                                  audio_devices_t devices,
                                  struct audio_config *config,
                                  struct audio_stream_in **stream_in,
                                  audio_input_flags_t flags,
                                  const char *address __unused,
                                  audio_source_t source)
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct stream_in *in;
    struct pcm_device_profile *pcm_profile;

    ALOGV("%s: enter", __func__);

    *stream_in = NULL;
    if (check_input_parameters(config->sample_rate, config->format,
                               audio_channel_count_from_in_mask(config->channel_mask)) != 0)
        return -EINVAL;

    usecase_type_t usecase_type = (source == AUDIO_SOURCE_HOTWORD) ?
                PCM_HOTWORD_STREAMING : PCM_CAPTURE;
    pcm_profile = get_pcm_device(usecase_type, devices);
    if (pcm_profile == NULL)
        return -EINVAL;

    in = (struct stream_in *)calloc(1, sizeof(struct stream_in));

    in->stream.common.get_sample_rate = in_get_sample_rate;
    in->stream.common.set_sample_rate = in_set_sample_rate;
    in->stream.common.get_buffer_size = in_get_buffer_size;
    in->stream.common.get_channels = in_get_channels;
    in->stream.common.get_format = in_get_format;
    in->stream.common.set_format = in_set_format;
    in->stream.common.standby = in_standby;
    in->stream.common.dump = in_dump;
    in->stream.common.set_parameters = in_set_parameters;
    in->stream.common.get_parameters = in_get_parameters;
    in->stream.common.add_audio_effect = in_add_audio_effect;
    in->stream.common.remove_audio_effect = in_remove_audio_effect;
    in->stream.set_gain = in_set_gain;
    in->stream.read = in_read;
    in->stream.get_input_frames_lost = in_get_input_frames_lost;

    in->devices = devices;
    in->source = source;
    in->dev = adev;
    in->standby = 1;
    in->main_channels = config->channel_mask;
    in->requested_rate = config->sample_rate;
    if (config->sample_rate != CAPTURE_DEFAULT_SAMPLING_RATE)
        flags = flags & ~AUDIO_INPUT_FLAG_FAST;
    in->input_flags = flags;
    /* HW codec is limited to default channels. No need to update with
     * requested channels */
    in->config = pcm_profile->config;

    /* Update config params with the requested sample rate and channels */
    if (source == AUDIO_SOURCE_HOTWORD) {
        in->usecase = USECASE_AUDIO_CAPTURE_HOTWORD;
    } else {
        in->usecase = USECASE_AUDIO_CAPTURE;
    }
    in->usecase_type = usecase_type;

    pthread_mutex_init(&in->lock, (const pthread_mutexattr_t *) NULL);
    pthread_mutex_init(&in->pre_lock, (const pthread_mutexattr_t *) NULL);

    *stream_in = &in->stream;
    ALOGV("%s: exit", __func__);
    return 0;
}

static void adev_close_input_stream(struct audio_hw_device *dev,
                                    struct audio_stream_in *stream)
{
    struct audio_device *adev = (struct audio_device *)dev;
    struct stream_in *in = (struct stream_in*)stream;
    ALOGV("%s", __func__);

    /* prevent concurrent out_set_parameters, or out_write from standby */
    pthread_mutex_lock(&adev->lock_inputs);

    in_standby_l(in);
    pthread_mutex_destroy(&in->lock);
    pthread_mutex_destroy(&in->pre_lock);
    free(in->proc_buf_out);

#ifdef PREPROCESSING_ENABLED
    int i;

    for (i=0; i<in->num_preprocessors; i++) {
        free(in->preprocessors[i].channel_configs);
    }

    if (in->read_buf) {
        free(in->read_buf);
    }

    if (in->proc_buf_in) {
        free(in->proc_buf_in);
    }

    if (in->resampler) {
        release_resampler(in->resampler);
    }
#endif

    free(stream);

    pthread_mutex_unlock(&adev->lock_inputs);

    return;
}

static int adev_dump(const audio_hw_device_t *device, int fd)
{
    (void)device;
    (void)fd;

    return 0;
}

static int adev_close(hw_device_t *device)
{
    struct audio_device *adev = (struct audio_device *)device;
    free(adev->snd_dev_ref_cnt);
    free_mixer_list(adev);
    free(device);
    return 0;
}

static int adev_open(const hw_module_t *module, const char *name,
                     hw_device_t **device)
{
    struct audio_device *adev;
    int i, ret, retry_count;

    ALOGV("%s: enter", __func__);
    if (strcmp(name, AUDIO_HARDWARE_INTERFACE) != 0) return -EINVAL;

    adev = calloc(1, sizeof(struct audio_device));

    adev->device.common.tag = HARDWARE_DEVICE_TAG;
    adev->device.common.version = AUDIO_DEVICE_API_VERSION_2_0;
    adev->device.common.module = (struct hw_module_t *)module;
    adev->device.common.close = adev_close;

    adev->device.init_check = adev_init_check;
    adev->device.set_voice_volume = adev_set_voice_volume;
    adev->device.set_master_volume = adev_set_master_volume;
    adev->device.get_master_volume = adev_get_master_volume;
    adev->device.set_master_mute = adev_set_master_mute;
    adev->device.get_master_mute = adev_get_master_mute;
    adev->device.set_mode = adev_set_mode;
    adev->device.set_mic_mute = adev_set_mic_mute;
    adev->device.get_mic_mute = adev_get_mic_mute;
    adev->device.set_parameters = adev_set_parameters;
    adev->device.get_parameters = adev_get_parameters;
    adev->device.get_input_buffer_size = adev_get_input_buffer_size;
    adev->device.open_output_stream = adev_open_output_stream;
    adev->device.close_output_stream = adev_close_output_stream;
    adev->device.open_input_stream = adev_open_input_stream;
    adev->device.close_input_stream = adev_close_input_stream;
    adev->device.dump = adev_dump;

    /* Set the default route before the PCM stream is opened */
    adev->mode = AUDIO_MODE_NORMAL;
    adev->active_input = NULL;
    adev->primary_output = NULL;
    adev->voice_volume = 1.0f;
    adev->tty_mode = TTY_MODE_OFF;
    adev->bluetooth_nrec = true;
    adev->in_call = false;
    /* adev->cur_hdmi_channels = 0;  by calloc() */
    adev->snd_dev_ref_cnt = calloc(SND_DEVICE_MAX, sizeof(int));

    adev->dualmic_config = DUALMIC_CONFIG_NONE;
    adev->ns_in_voice_rec = false;

    list_init(&adev->usecase_list);

    if (mixer_init(adev) != 0) {
        free(adev->snd_dev_ref_cnt);
        free(adev);
        ALOGE("%s: Failed to init, aborting.", __func__);
        *device = NULL;
        return -EINVAL;
    }


    if (access(SOUND_TRIGGER_HAL_LIBRARY_PATH, R_OK) == 0) {
        adev->sound_trigger_lib = dlopen(SOUND_TRIGGER_HAL_LIBRARY_PATH,
                                         RTLD_NOW);
        if (adev->sound_trigger_lib == NULL) {
            ALOGE("%s: DLOPEN failed for %s", __func__,
                  SOUND_TRIGGER_HAL_LIBRARY_PATH);
        } else {
            ALOGV("%s: DLOPEN successful for %s", __func__,
                  SOUND_TRIGGER_HAL_LIBRARY_PATH);
            adev->sound_trigger_open_for_streaming =
                    (int (*)(void))dlsym(adev->sound_trigger_lib,
                                         "sound_trigger_open_for_streaming");
            adev->sound_trigger_read_samples =
                    (size_t (*)(int, void *, size_t))dlsym(
                            adev->sound_trigger_lib,
                            "sound_trigger_read_samples");
            adev->sound_trigger_close_for_streaming =
                        (int (*)(int))dlsym(
                                adev->sound_trigger_lib,
                                "sound_trigger_close_for_streaming");
            if (!adev->sound_trigger_open_for_streaming ||
                !adev->sound_trigger_read_samples ||
                !adev->sound_trigger_close_for_streaming) {

                ALOGE("%s: Error grabbing functions in %s", __func__,
                      SOUND_TRIGGER_HAL_LIBRARY_PATH);
                adev->sound_trigger_open_for_streaming = 0;
                adev->sound_trigger_read_samples = 0;
                adev->sound_trigger_close_for_streaming = 0;
            }
        }
    }

    *device = &adev->device.common;

    cras_dsp_init("/system/etc/cras/speakerdsp.ini");

    ALOGV("%s: exit", __func__);
    return 0;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = adev_open,
};

struct audio_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = AUDIO_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = AUDIO_HARDWARE_MODULE_ID,
        .name = "NVIDIA Tegra Audio HAL",
        .author = "The Android Open Source Project",
        .methods = &hal_module_methods,
    },
};
