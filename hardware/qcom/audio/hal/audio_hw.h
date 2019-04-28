/*
 * Copyright (C) 2013-2016 The Android Open Source Project
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

#ifndef QCOM_AUDIO_HW_H
#define QCOM_AUDIO_HW_H

#include <cutils/str_parms.h>
#include <cutils/list.h>
#include <hardware/audio.h>

#include <tinyalsa/asoundlib.h>
#include <tinycompress/tinycompress.h>

#include <audio_route/audio_route.h>
#include <audio_utils/ErrorLog.h>
#include "voice.h"

// dlopen() does not go through default library path search if there is a "/" in the library name.
#ifdef __LP64__
#define VISUALIZER_LIBRARY_PATH "/vendor/lib64/soundfx/libqcomvisualizer.so"
#define OFFLOAD_EFFECTS_BUNDLE_LIBRARY_PATH "/vendor/lib64/soundfx/libqcompostprocbundle.so"
#else
#define VISUALIZER_LIBRARY_PATH "/vendor/lib/soundfx/libqcomvisualizer.so"
#define OFFLOAD_EFFECTS_BUNDLE_LIBRARY_PATH "/vendor/lib/soundfx/libqcompostprocbundle.so"
#endif
#define ADM_LIBRARY_PATH "libadm.so"

/* Flags used to initialize acdb_settings variable that goes to ACDB library */
#define DMIC_FLAG       0x00000002
#define TTY_MODE_OFF    0x00000010
#define TTY_MODE_FULL   0x00000020
#define TTY_MODE_VCO    0x00000040
#define TTY_MODE_HCO    0x00000080
#define TTY_MODE_CLEAR  0xFFFFFF0F

#define ACDB_DEV_TYPE_OUT 1
#define ACDB_DEV_TYPE_IN 2

#define MAX_SUPPORTED_CHANNEL_MASKS 8
#define MAX_SUPPORTED_FORMATS 15
#define MAX_SUPPORTED_SAMPLE_RATES 7
#define DEFAULT_HDMI_OUT_CHANNELS   2

#define ERROR_LOG_ENTRIES 16

/* Error types for the error log */
enum {
    ERROR_CODE_STANDBY = 1,
    ERROR_CODE_WRITE,
    ERROR_CODE_READ,
};

typedef enum card_status_t {
    CARD_STATUS_OFFLINE,
    CARD_STATUS_ONLINE
} card_status_t;

/* These are the supported use cases by the hardware.
 * Each usecase is mapped to a specific PCM device.
 * Refer to pcm_device_table[].
 */
enum {
    USECASE_INVALID = -1,
    /* Playback usecases */
    USECASE_AUDIO_PLAYBACK_DEEP_BUFFER = 0,
    USECASE_AUDIO_PLAYBACK_LOW_LATENCY,
    USECASE_AUDIO_PLAYBACK_HIFI,
    USECASE_AUDIO_PLAYBACK_OFFLOAD,
    USECASE_AUDIO_PLAYBACK_TTS,
    USECASE_AUDIO_PLAYBACK_ULL,
    USECASE_AUDIO_PLAYBACK_MMAP,

    /* HFP Use case*/
    USECASE_AUDIO_HFP_SCO,
    USECASE_AUDIO_HFP_SCO_WB,

    /* Capture usecases */
    USECASE_AUDIO_RECORD,
    USECASE_AUDIO_RECORD_LOW_LATENCY,
    USECASE_AUDIO_RECORD_MMAP,
    USECASE_AUDIO_RECORD_HIFI,

    /* Voice extension usecases
     *
     * Following usecase are specific to voice session names created by
     * MODEM and APPS on 8992/8994/8084/8974 platforms.
     */
    USECASE_VOICE_CALL,  /* Usecase setup for voice session on first subscription for DSDS/DSDA */
    USECASE_VOICE2_CALL, /* Usecase setup for voice session on second subscription for DSDS/DSDA */
    USECASE_VOLTE_CALL,  /* Usecase setup for VoLTE session on first subscription */
    USECASE_QCHAT_CALL,  /* Usecase setup for QCHAT session */
    USECASE_VOWLAN_CALL, /* Usecase setup for VoWLAN session */

    /*
     * Following usecase are specific to voice session names created by
     * MODEM and APPS on 8996 platforms.
     */

    USECASE_VOICEMMODE1_CALL, /* Usecase setup for Voice/VoLTE/VoWLAN sessions on first
                               * subscription for DSDS/DSDA
                               */
    USECASE_VOICEMMODE2_CALL, /* Usecase setup for voice/VoLTE/VoWLAN sessions on second
                               * subscription for DSDS/DSDA
                               */

    USECASE_INCALL_REC_UPLINK,
    USECASE_INCALL_REC_DOWNLINK,
    USECASE_INCALL_REC_UPLINK_AND_DOWNLINK,

    USECASE_AUDIO_SPKR_CALIB_RX,
    USECASE_AUDIO_SPKR_CALIB_TX,

    USECASE_AUDIO_PLAYBACK_AFE_PROXY,
    USECASE_AUDIO_RECORD_AFE_PROXY,
    USECASE_AUDIO_DSM_FEEDBACK,

    /* VOIP usecase*/
    USECASE_AUDIO_PLAYBACK_VOIP,
    USECASE_AUDIO_RECORD_VOIP,

    AUDIO_USECASE_MAX
};

const char * const use_case_table[AUDIO_USECASE_MAX];

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

/*
 * tinyAlsa library interprets period size as number of frames
 * one frame = channel_count * sizeof (pcm sample)
 * so if format = 16-bit PCM and channels = Stereo, frame size = 2 ch * 2 = 4 bytes
 * DEEP_BUFFER_OUTPUT_PERIOD_SIZE = 1024 means 1024 * 4 = 4096 bytes
 * We should take care of returning proper size when AudioFlinger queries for
 * the buffer size of an input/output stream
 */

enum {
    OFFLOAD_CMD_EXIT,               /* exit compress offload thread loop*/
    OFFLOAD_CMD_DRAIN,              /* send a full drain request to DSP */
    OFFLOAD_CMD_PARTIAL_DRAIN,      /* send a partial drain request to DSP */
    OFFLOAD_CMD_WAIT_FOR_BUFFER,    /* wait for buffer released by DSP */
    OFFLOAD_CMD_ERROR,              /* offload playback hit some error */
};

enum {
    OFFLOAD_STATE_IDLE,
    OFFLOAD_STATE_PLAYING,
    OFFLOAD_STATE_PAUSED,
};

struct offload_cmd {
    struct listnode node;
    int cmd;
    int data[];
};

struct stream_app_type_cfg {
    int sample_rate;
    uint32_t bit_width; // unused
    const char *mode;
    int app_type;
    int gain[2];
};

struct stream_out {
    struct audio_stream_out stream;
    pthread_mutex_t lock; /* see note below on mutex acquisition order */
    pthread_mutex_t pre_lock; /* acquire before lock to avoid DOS by playback thread */
    pthread_cond_t  cond;
    struct pcm_config config;
    struct compr_config compr_config;
    struct pcm *pcm;
    struct compress *compr;
    int standby;
    int pcm_device_id;
    unsigned int sample_rate;
    audio_channel_mask_t channel_mask;
    audio_format_t format;
    audio_devices_t devices;
    audio_output_flags_t flags;
    audio_usecase_t usecase;
    /* Array of supported channel mask configurations. +1 so that the last entry is always 0 */
    audio_channel_mask_t supported_channel_masks[MAX_SUPPORTED_CHANNEL_MASKS + 1];
    audio_format_t supported_formats[MAX_SUPPORTED_FORMATS + 1];
    uint32_t supported_sample_rates[MAX_SUPPORTED_SAMPLE_RATES + 1];
    bool muted;
    uint64_t written; /* total frames written, not cleared when entering standby */
    audio_io_handle_t handle;

    int non_blocking;
    int playback_started;
    int offload_state;
    pthread_cond_t offload_cond;
    pthread_t offload_thread;
    struct listnode offload_cmd_list;
    bool offload_thread_blocked;

    stream_callback_t offload_callback;
    void *offload_cookie;
    struct compr_gapless_mdata gapless_mdata;
    int send_new_metadata;
    bool realtime;
    int af_period_multiplier;
    struct audio_device *dev;
    card_status_t card_status;

    error_log_t *error_log;

    struct stream_app_type_cfg app_type_cfg;
};

struct stream_in {
    struct audio_stream_in stream;
    pthread_mutex_t lock; /* see note below on mutex acquisition order */
    pthread_mutex_t pre_lock; /* acquire before lock to avoid DOS by capture thread */
    struct pcm_config config;
    struct pcm *pcm;
    int standby;
    int source;
    int pcm_device_id;
    audio_devices_t device;
    audio_channel_mask_t channel_mask;
    unsigned int sample_rate;
    audio_usecase_t usecase;
    bool enable_aec;
    bool enable_ns;
    int64_t frames_read; /* total frames read, not cleared when entering standby */
    int64_t frames_muted; /* total frames muted, not cleared when entering standby */

    audio_io_handle_t capture_handle;
    audio_input_flags_t flags;
    bool is_st_session;
    bool is_st_session_active;
    bool realtime;
    int af_period_multiplier;
    struct audio_device *dev;
    audio_format_t format;
    card_status_t card_status;
    int capture_started;

    struct stream_app_type_cfg app_type_cfg;

    /* Array of supported channel mask configurations.
       +1 so that the last entry is always 0 */
    audio_channel_mask_t supported_channel_masks[MAX_SUPPORTED_CHANNEL_MASKS + 1];
    audio_format_t supported_formats[MAX_SUPPORTED_FORMATS + 1];
    uint32_t supported_sample_rates[MAX_SUPPORTED_SAMPLE_RATES + 1];

    error_log_t *error_log;
};

typedef enum usecase_type_t {
    PCM_PLAYBACK,
    PCM_CAPTURE,
    VOICE_CALL,
    PCM_HFP_CALL,
    USECASE_TYPE_MAX
} usecase_type_t;

union stream_ptr {
    struct stream_in *in;
    struct stream_out *out;
};

struct audio_usecase {
    struct listnode list;
    audio_usecase_t id;
    usecase_type_t  type;
    audio_devices_t devices;
    snd_device_t out_snd_device;
    snd_device_t in_snd_device;
    union stream_ptr stream;
};

typedef void* (*adm_init_t)();
typedef void (*adm_deinit_t)(void *);
typedef void (*adm_register_output_stream_t)(void *, audio_io_handle_t, audio_output_flags_t);
typedef void (*adm_register_input_stream_t)(void *, audio_io_handle_t, audio_input_flags_t);
typedef void (*adm_deregister_stream_t)(void *, audio_io_handle_t);
typedef void (*adm_request_focus_t)(void *, audio_io_handle_t);
typedef void (*adm_abandon_focus_t)(void *, audio_io_handle_t);
typedef void (*adm_set_config_t)(void *, audio_io_handle_t,
                                         struct pcm *,
                                         struct pcm_config *);
typedef void (*adm_request_focus_v2_t)(void *, audio_io_handle_t, long);
typedef bool (*adm_is_noirq_avail_t)(void *, int, int, int);
typedef void (*adm_on_routing_change_t)(void *, audio_io_handle_t);

struct audio_device {
    struct audio_hw_device device;
    pthread_mutex_t lock; /* see note below on mutex acquisition order */
    struct mixer *mixer;
    audio_mode_t mode;
    struct stream_in *active_input;
    struct stream_out *primary_output;
    struct stream_out *voice_tx_output;
    struct stream_out *current_call_output;
    bool bluetooth_nrec;
    bool screen_off;
    int *snd_dev_ref_cnt;
    struct listnode usecase_list;
    struct audio_route *audio_route;
    int acdb_settings;
    struct voice voice;
    unsigned int cur_hdmi_channels;
    bool bt_wb_speech_enabled;
    bool mic_muted;
    bool enable_voicerx;
    bool enable_hfp;

    int snd_card;
    void *platform;
    void *extspk;

    card_status_t card_status;

    void *visualizer_lib;
    int (*visualizer_start_output)(audio_io_handle_t, int);
    int (*visualizer_stop_output)(audio_io_handle_t, int);

    /* The pcm_params use_case_table is loaded by adev_verify_devices() upon
     * calling adev_open().
     *
     * If an entry is not NULL, it can be used to determine if extended precision
     * or other capabilities are present for the device corresponding to that usecase.
     */
    struct pcm_params *use_case_table[AUDIO_USECASE_MAX];
    void *offload_effects_lib;
    int (*offload_effects_start_output)(audio_io_handle_t, int);
    int (*offload_effects_stop_output)(audio_io_handle_t, int);

    void *adm_data;
    void *adm_lib;
    adm_init_t adm_init;
    adm_deinit_t adm_deinit;
    adm_register_input_stream_t adm_register_input_stream;
    adm_register_output_stream_t adm_register_output_stream;
    adm_deregister_stream_t adm_deregister_stream;
    adm_request_focus_t adm_request_focus;
    adm_abandon_focus_t adm_abandon_focus;
    adm_set_config_t adm_set_config;
    adm_request_focus_v2_t adm_request_focus_v2;
    adm_is_noirq_avail_t adm_is_noirq_avail;
    adm_on_routing_change_t adm_on_routing_change;

    /* logging */
    snd_device_t last_logged_snd_device[AUDIO_USECASE_MAX][2]; /* [out, in] */
};

int select_devices(struct audio_device *adev,
                   audio_usecase_t uc_id);

int disable_audio_route(struct audio_device *adev,
                        struct audio_usecase *usecase);

int disable_snd_device(struct audio_device *adev,
                       snd_device_t snd_device);

int enable_snd_device(struct audio_device *adev,
                      snd_device_t snd_device);

int enable_audio_route(struct audio_device *adev,
                       struct audio_usecase *usecase);

struct audio_usecase *get_usecase_from_list(struct audio_device *adev,
                                            audio_usecase_t uc_id);

#define LITERAL_TO_STRING(x) #x
#define CHECK(condition) LOG_ALWAYS_FATAL_IF(!(condition), "%s",\
            __FILE__ ":" LITERAL_TO_STRING(__LINE__)\
            " ASSERT_FATAL(" #condition ") failed.")

/*
 * NOTE: when multiple mutexes have to be acquired, always take the
 * stream_in or stream_out mutex first, followed by the audio_device mutex.
 */

#endif // QCOM_AUDIO_HW_H
