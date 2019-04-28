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

#define LOG_TAG "msm8916_platform"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <stdlib.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <cutils/str_parms.h>
#include <audio_hw.h>
#include <platform_api.h>
#include "platform.h"
#include "audio_extn.h"
#include "voice_extn.h"
#include "sound/msmcal-hwdep.h"
#include "audio_extn/tfa_98xx.h"
#include <dirent.h>
#define MAX_MIXER_XML_PATH  100
#define MIXER_XML_PATH "/system/etc/mixer_paths.xml"
#define MIXER_XML_PATH_MTP "/system/etc/mixer_paths_mtp.xml"
#define MIXER_XML_PATH_MSM8909_PM8916 "/system/etc/mixer_paths_msm8909_pm8916.xml"
#define MIXER_XML_PATH_L9300 "/system/etc/mixer_paths_l9300.xml"

#define LIB_ACDB_LOADER "libacdbloader.so"
#define AUDIO_DATA_BLOCK_MIXER_CTL "HDMI EDID"
#define CVD_VERSION_MIXER_CTL "CVD Version"

/*
 * This file will have a maximum of 38 bytes:
 *
 * 4 bytes: number of audio blocks
 * 4 bytes: total length of Short Audio Descriptor (SAD) blocks
 * Maximum 10 * 3 bytes: SAD blocks
 */
#define MAX_SAD_BLOCKS      10
#define SAD_BLOCK_SIZE      3
#define MAX_CVD_VERSION_STRING_SIZE    100

/* EDID format ID for LPCM audio */
#define EDID_FORMAT_LPCM    1

/* fallback app type if the default app type from acdb loader fails */
#define DEFAULT_APP_TYPE_RX_PATH  0x11130
#define DEFAULT_APP_TYPE_TX_PATH  0x11132
#define DEFAULT_RX_BACKEND "SLIMBUS_0_RX"

/* Retry for delay in FW loading*/
#define RETRY_NUMBER 20
#define RETRY_US 500000
#define MAX_SND_CARD 8

#define SAMPLE_RATE_8KHZ  8000
#define SAMPLE_RATE_16KHZ 16000

#define MAX_SET_CAL_BYTE_SIZE 65536

#define MAX_CAL_NAME 20

#define QMIC_FLAG 0x00000004

#define TOSTRING_(x) #x
#define TOSTRING(x) TOSTRING_(x)

char cal_name_info[WCD9XXX_MAX_CAL][MAX_CAL_NAME] = {
        [WCD9XXX_MBHC_CAL] = "mbhc_cal",
};

struct audio_block_header
{
    int reserved;
    int length;
};

enum {
    CAL_MODE_SEND           = 0x1,
    CAL_MODE_PERSIST        = 0x2,
    CAL_MODE_RTAC           = 0x4
};

enum {
    BUFF_IDX_0 = 0,
    BUFF_IDX_1 = 1,
};

#define PLATFORM_CONFIG_KEY_OPERATOR_INFO "operator_info"

struct operator_info {
    struct listnode list;
    char *name;
    char *mccmnc;
};

struct operator_specific_device {
    struct listnode list;
    char *operator;
    char *mixer_path;
    int acdb_id;
};

static struct listnode operator_info_list;
static struct listnode *operator_specific_device_table[SND_DEVICE_MAX];

/* Audio calibration related functions */
typedef void (*acdb_deallocate_t)();
typedef int  (*acdb_init_v2_cvd_t)(const char *, char *, int);
typedef void (*acdb_send_audio_cal_t)(int, int);
typedef void (*acdb_send_audio_cal_v3_t)(int, int, int , int, int);
typedef void (*acdb_send_voice_cal_t)(int, int);
typedef int (*acdb_reload_vocvoltable_t)(int);
typedef int (*acdb_loader_get_calibration_t)(char *attr, int size, void *data);
acdb_loader_get_calibration_t acdb_loader_get_calibration;

struct platform_data {
    struct audio_device *adev;
    bool fluence_in_spkr_mode;
    bool fluence_in_voice_call;
    bool fluence_in_voice_rec;
    int  fluence_type;
    char fluence_cap[PROPERTY_VALUE_MAX];
    int  fluence_mode;
    bool ec_ref_enabled;
    bool gsm_mode_enabled;
    /* Audio calibration related functions */
    void                       *acdb_handle;
    acdb_init_v2_cvd_t         acdb_init;
    acdb_deallocate_t          acdb_deallocate;
    acdb_send_audio_cal_t      acdb_send_audio_cal;
    acdb_send_audio_cal_v3_t   acdb_send_audio_cal_v3;
    acdb_send_voice_cal_t      acdb_send_voice_cal;
    acdb_reload_vocvoltable_t  acdb_reload_vocvoltable;
    void *hw_info;
    char ec_ref_mixer_path[64];
    bool speaker_lr_swap;

    int max_vol_index;
};

int pcm_device_table[AUDIO_USECASE_MAX][2] = {
    [USECASE_AUDIO_PLAYBACK_DEEP_BUFFER] = {DEEP_BUFFER_PCM_DEVICE,
                                            DEEP_BUFFER_PCM_DEVICE},
    [USECASE_AUDIO_PLAYBACK_LOW_LATENCY] = {LOWLATENCY_PCM_DEVICE,
                                           LOWLATENCY_PCM_DEVICE},
    [USECASE_AUDIO_PLAYBACK_HIFI] = {MULTIMEDIA2_PCM_DEVICE,
                                        MULTIMEDIA2_PCM_DEVICE},
    [USECASE_AUDIO_PLAYBACK_OFFLOAD] =
                     {PLAYBACK_OFFLOAD_DEVICE, PLAYBACK_OFFLOAD_DEVICE},
    [USECASE_AUDIO_PLAYBACK_ULL] = {MULTIMEDIA3_PCM_DEVICE, MULTIMEDIA3_PCM_DEVICE},
    [USECASE_AUDIO_RECORD] = {AUDIO_RECORD_PCM_DEVICE, AUDIO_RECORD_PCM_DEVICE},
    [USECASE_AUDIO_RECORD_LOW_LATENCY] = {LOWLATENCY_PCM_DEVICE,
                                          LOWLATENCY_PCM_DEVICE},
    [USECASE_AUDIO_HFP_SCO] = {HFP_PCM_RX, HFP_SCO_RX},
    [USECASE_AUDIO_HFP_SCO_WB] = {HFP_PCM_RX, HFP_SCO_RX},
    [USECASE_VOICE_CALL] = {VOICE_CALL_PCM_DEVICE, VOICE_CALL_PCM_DEVICE},
    [USECASE_VOICE2_CALL] = {VOICE2_CALL_PCM_DEVICE, VOICE2_CALL_PCM_DEVICE},
    [USECASE_VOLTE_CALL] = {VOLTE_CALL_PCM_DEVICE, VOLTE_CALL_PCM_DEVICE},
    [USECASE_QCHAT_CALL] = {QCHAT_CALL_PCM_DEVICE, QCHAT_CALL_PCM_DEVICE},
    [USECASE_VOWLAN_CALL] = {VOWLAN_CALL_PCM_DEVICE, VOWLAN_CALL_PCM_DEVICE},
    [USECASE_VOICEMMODE1_CALL] = {-1, -1}, /* pcm ids updated from platform info file */
    [USECASE_VOICEMMODE2_CALL] = {-1, -1}, /* pcm ids updated from platform info file */
    [USECASE_INCALL_REC_UPLINK] = {AUDIO_RECORD_PCM_DEVICE,
                                   AUDIO_RECORD_PCM_DEVICE},
    [USECASE_INCALL_REC_DOWNLINK] = {AUDIO_RECORD_PCM_DEVICE,
                                     AUDIO_RECORD_PCM_DEVICE},
    [USECASE_INCALL_REC_UPLINK_AND_DOWNLINK] = {AUDIO_RECORD_PCM_DEVICE,
                                                AUDIO_RECORD_PCM_DEVICE},
    [USECASE_AUDIO_SPKR_CALIB_RX] = {SPKR_PROT_CALIB_RX_PCM_DEVICE, -1},
    [USECASE_AUDIO_SPKR_CALIB_TX] = {-1, SPKR_PROT_CALIB_TX_PCM_DEVICE},
    [USECASE_AUDIO_PLAYBACK_AFE_PROXY] = {AFE_PROXY_PLAYBACK_PCM_DEVICE,
                                          AFE_PROXY_RECORD_PCM_DEVICE},
    [USECASE_AUDIO_RECORD_AFE_PROXY] = {AFE_PROXY_PLAYBACK_PCM_DEVICE,
                                        AFE_PROXY_RECORD_PCM_DEVICE},
};

/* Array to store sound devices */
static const char * const device_table[SND_DEVICE_MAX] = {
    [SND_DEVICE_NONE] = "none",
    /* Playback sound devices */
    [SND_DEVICE_OUT_HANDSET] = "handset",
    [SND_DEVICE_OUT_SPEAKER] = "speaker",
    [SND_DEVICE_OUT_SPEAKER_REVERSE] = "speaker-reverse",
    [SND_DEVICE_OUT_HEADPHONES] = "headphones",
    [SND_DEVICE_OUT_LINE] = "line",
    [SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES] = "speaker-and-headphones",
    [SND_DEVICE_OUT_SPEAKER_AND_LINE] = "speaker-and-line",
    [SND_DEVICE_OUT_VOICE_HANDSET] = "voice-handset",
    [SND_DEVICE_OUT_VOICE_HAC_HANDSET] = "voice-hac-handset",
    [SND_DEVICE_OUT_VOICE_SPEAKER] = "voice-speaker",
    [SND_DEVICE_OUT_VOICE_SPEAKER_HFP] = "voice-speaker-hfp",
    [SND_DEVICE_OUT_VOICE_HEADPHONES] = "voice-headphones",
    [SND_DEVICE_OUT_VOICE_LINE] = "voice-line",
    [SND_DEVICE_OUT_HDMI] = "hdmi",
    [SND_DEVICE_OUT_SPEAKER_AND_HDMI] = "speaker-and-hdmi",
    [SND_DEVICE_OUT_BT_SCO] = "bt-sco-headset",
    [SND_DEVICE_OUT_BT_SCO_WB] = "bt-sco-headset-wb",
    [SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES] = "voice-tty-full-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES] = "voice-tty-vco-headphones",
    [SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET] = "voice-tty-hco-handset",
    [SND_DEVICE_OUT_VOICE_TX] = "voice-tx",
    [SND_DEVICE_OUT_AFE_PROXY] = "afe-proxy",
    [SND_DEVICE_OUT_USB_HEADSET] = "usb-headphones",
    [SND_DEVICE_OUT_USB_HEADPHONES] = "usb-headphones",
    [SND_DEVICE_OUT_VOICE_USB_HEADSET] = "usb-headphones",
    [SND_DEVICE_OUT_VOICE_USB_HEADPHONES] = "usb-headphones",
    [SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET] = "speaker-and-usb-headphones",
    [SND_DEVICE_OUT_SPEAKER_PROTECTED] = "speaker-protected",
    [SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED] = "voice-speaker-protected",

    /* Capture sound devices */
    [SND_DEVICE_IN_HANDSET_MIC] = "handset-mic",
    [SND_DEVICE_IN_HANDSET_MIC_EXTERNAL] = "handset-mic-ext",
    [SND_DEVICE_IN_HANDSET_MIC_AEC] = "handset-mic",
    [SND_DEVICE_IN_HANDSET_MIC_NS] = "handset-mic",
    [SND_DEVICE_IN_HANDSET_MIC_AEC_NS] = "handset-mic",
    [SND_DEVICE_IN_HANDSET_DMIC] = "dmic-endfire",
    [SND_DEVICE_IN_HANDSET_DMIC_AEC] = "dmic-endfire",
    [SND_DEVICE_IN_HANDSET_DMIC_NS] = "dmic-endfire",
    [SND_DEVICE_IN_HANDSET_DMIC_AEC_NS] = "dmic-endfire",
    [SND_DEVICE_IN_SPEAKER_MIC] = "speaker-mic",
    [SND_DEVICE_IN_SPEAKER_MIC_AEC] = "speaker-mic",
    [SND_DEVICE_IN_SPEAKER_MIC_NS] = "speaker-mic",
    [SND_DEVICE_IN_SPEAKER_MIC_AEC_NS] = "speaker-mic",
    [SND_DEVICE_IN_SPEAKER_DMIC] = "speaker-dmic-endfire",
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC] = "speaker-dmic-endfire",
    [SND_DEVICE_IN_SPEAKER_DMIC_NS] = "speaker-dmic-endfire",
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS] = "speaker-dmic-endfire",
    [SND_DEVICE_IN_HEADSET_MIC] = "headset-mic",
    [SND_DEVICE_IN_HEADSET_MIC_FLUENCE] = "headset-mic",
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC] = "voice-speaker-mic",
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC_HFP] = "voice-speaker-mic-hfp",
    [SND_DEVICE_IN_VOICE_HEADSET_MIC] = "voice-headset-mic",
    [SND_DEVICE_IN_HDMI_MIC] = "hdmi-mic",
    [SND_DEVICE_IN_BT_SCO_MIC] = "bt-sco-mic",
    [SND_DEVICE_IN_BT_SCO_MIC_NREC] = "bt-sco-mic",
    [SND_DEVICE_IN_BT_SCO_MIC_WB] = "bt-sco-mic-wb",
    [SND_DEVICE_IN_BT_SCO_MIC_WB_NREC] = "bt-sco-mic-wb",
    [SND_DEVICE_IN_CAMCORDER_MIC] = "camcorder-mic",
    [SND_DEVICE_IN_VOICE_DMIC] = "voice-dmic-ef",
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC] = "voice-speaker-dmic-ef",
    [SND_DEVICE_IN_VOICE_SPEAKER_QMIC] = "voice-speaker-qmic",
    [SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC] = "voice-tty-full-headset-mic",
    [SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC] = "voice-tty-vco-handset-mic",
    [SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC] = "voice-tty-hco-headset-mic",
    [SND_DEVICE_IN_VOICE_REC_MIC] = "voice-rec-mic",
    [SND_DEVICE_IN_VOICE_REC_MIC_NS] = "voice-rec-mic",
    [SND_DEVICE_IN_VOICE_REC_DMIC_STEREO] = "voice-rec-dmic-ef",
    [SND_DEVICE_IN_VOICE_REC_DMIC_FLUENCE] = "voice-rec-dmic-ef-fluence",
    [SND_DEVICE_IN_VOICE_RX] = "voice-rx",
    [SND_DEVICE_IN_USB_HEADSET_MIC] = "usb-headset-mic",
    [SND_DEVICE_IN_CAPTURE_FM] = "capture-fm",
    [SND_DEVICE_IN_AANC_HANDSET_MIC] = "aanc-handset-mic",
    [SND_DEVICE_IN_QUAD_MIC] = "quad-mic",
    [SND_DEVICE_IN_HANDSET_STEREO_DMIC] = "handset-stereo-dmic-ef",
    [SND_DEVICE_IN_SPEAKER_STEREO_DMIC] = "speaker-stereo-dmic-ef",
    [SND_DEVICE_IN_CAPTURE_VI_FEEDBACK] = "vi-feedback",
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BROADSIDE] = "voice-speaker-dmic-broadside",
    [SND_DEVICE_IN_SPEAKER_DMIC_BROADSIDE] = "speaker-dmic-broadside",
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_BROADSIDE] = "speaker-dmic-broadside",
    [SND_DEVICE_IN_SPEAKER_DMIC_NS_BROADSIDE] = "speaker-dmic-broadside",
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS_BROADSIDE] = "speaker-dmic-broadside",
    [SND_DEVICE_IN_HANDSET_QMIC] = "quad-mic",
    [SND_DEVICE_IN_SPEAKER_QMIC_AEC] = "quad-mic",
    [SND_DEVICE_IN_SPEAKER_QMIC_NS] = "quad-mic",
    [SND_DEVICE_IN_SPEAKER_QMIC_AEC_NS] = "quad-mic",
};

/* ACDB IDs (audio DSP path configuration IDs) for each sound device */
static int acdb_device_table[SND_DEVICE_MAX] = {
    [SND_DEVICE_NONE] = -1,
    [SND_DEVICE_OUT_HANDSET] = 7,
    [SND_DEVICE_OUT_SPEAKER] = 14,
    [SND_DEVICE_OUT_SPEAKER_REVERSE] = 14,
    [SND_DEVICE_OUT_LINE] = 10,
    [SND_DEVICE_OUT_HEADPHONES] = 10,
    [SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES] = 10,
    [SND_DEVICE_OUT_SPEAKER_AND_LINE] = 10,
    [SND_DEVICE_OUT_VOICE_HANDSET] = 7,
    [SND_DEVICE_OUT_VOICE_HAC_HANDSET] = 53,
    [SND_DEVICE_OUT_VOICE_LINE] = 10,
    [SND_DEVICE_OUT_VOICE_SPEAKER] = 14,
    [SND_DEVICE_OUT_VOICE_HEADPHONES] = 10,
    [SND_DEVICE_OUT_HDMI] = 18,
    [SND_DEVICE_OUT_SPEAKER_AND_HDMI] = 14,
    [SND_DEVICE_OUT_BT_SCO] = 22,
    [SND_DEVICE_OUT_BT_SCO_WB] = 39,
    [SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES] = 17,
    [SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES] = 17,
    [SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET] = 37,
    [SND_DEVICE_OUT_VOICE_TX] = 45,
    [SND_DEVICE_OUT_AFE_PROXY] = 0,
    [SND_DEVICE_OUT_USB_HEADSET] = 45,
    [SND_DEVICE_OUT_VOICE_USB_HEADSET] = 45,
    [SND_DEVICE_OUT_VOICE_USB_HEADPHONES] = 45,
    [SND_DEVICE_OUT_USB_HEADPHONES] = 45,
    [SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET] = 14,
    [SND_DEVICE_OUT_SPEAKER_PROTECTED] = 124,
    [SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED] = 101,
    [SND_DEVICE_OUT_VOICE_SPEAKER_HFP] = 140,

    [SND_DEVICE_IN_HANDSET_MIC] = 4,
    [SND_DEVICE_IN_HANDSET_MIC_EXTERNAL] = 4,
    [SND_DEVICE_IN_HANDSET_MIC_AEC] = 106,
    [SND_DEVICE_IN_HANDSET_MIC_NS] = 107,
    [SND_DEVICE_IN_HANDSET_MIC_AEC_NS] = 108,
    [SND_DEVICE_IN_HANDSET_DMIC] = 41,
    [SND_DEVICE_IN_HANDSET_DMIC_AEC] = 109,
    [SND_DEVICE_IN_HANDSET_DMIC_NS] = 110,
    [SND_DEVICE_IN_HANDSET_DMIC_AEC_NS] = 111,
    [SND_DEVICE_IN_SPEAKER_MIC] = 11,
    [SND_DEVICE_IN_SPEAKER_MIC_AEC] = 112,
    [SND_DEVICE_IN_SPEAKER_MIC_NS] = 113,
    [SND_DEVICE_IN_SPEAKER_MIC_AEC_NS] = 114,
    [SND_DEVICE_IN_SPEAKER_DMIC] = 43,
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC] = 115,
    [SND_DEVICE_IN_SPEAKER_DMIC_NS] = 116,
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS] = 117,
    [SND_DEVICE_IN_HEADSET_MIC] = 8,
    [SND_DEVICE_IN_HEADSET_MIC_FLUENCE] = 47,
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC] = 11,
    [SND_DEVICE_IN_VOICE_HEADSET_MIC] = 8,
    [SND_DEVICE_IN_HDMI_MIC] = 4,
    [SND_DEVICE_IN_BT_SCO_MIC] = 21,
    [SND_DEVICE_IN_BT_SCO_MIC_NREC] = 122,
    [SND_DEVICE_IN_BT_SCO_MIC_WB] = 38,
    [SND_DEVICE_IN_BT_SCO_MIC_WB_NREC] = 123,
    [SND_DEVICE_IN_CAMCORDER_MIC] = 4,
    [SND_DEVICE_IN_VOICE_DMIC] = 41,
    [SND_DEVICE_IN_VOICE_SPEAKER_MIC_HFP] = 141,
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC] = 43,
    [SND_DEVICE_IN_VOICE_SPEAKER_QMIC] = 19,
    [SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC] = 16,
    [SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC] = 36,
    [SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC] = 16,
    [SND_DEVICE_IN_VOICE_REC_MIC] = 4,
    [SND_DEVICE_IN_VOICE_REC_MIC_NS] = 107,
    [SND_DEVICE_IN_VOICE_REC_DMIC_STEREO] = 34,
    [SND_DEVICE_IN_VOICE_REC_DMIC_FLUENCE] = 41,
    [SND_DEVICE_IN_VOICE_RX] = 44,
    [SND_DEVICE_IN_USB_HEADSET_MIC] = 44,
    [SND_DEVICE_IN_CAPTURE_FM] = 0,
    [SND_DEVICE_IN_AANC_HANDSET_MIC] = 104,
    [SND_DEVICE_IN_QUAD_MIC] = 46,
    [SND_DEVICE_IN_HANDSET_STEREO_DMIC] = 34,
    [SND_DEVICE_IN_SPEAKER_STEREO_DMIC] = 35,
    [SND_DEVICE_IN_CAPTURE_VI_FEEDBACK] = 102,
    [SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BROADSIDE] = 12,
    [SND_DEVICE_IN_SPEAKER_DMIC_BROADSIDE] = 12,
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_BROADSIDE] = 119,
    [SND_DEVICE_IN_SPEAKER_DMIC_NS_BROADSIDE] = 121,
    [SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS_BROADSIDE] = 120,
    [SND_DEVICE_IN_HANDSET_QMIC] = 125,
    [SND_DEVICE_IN_SPEAKER_QMIC_AEC] = 126,
    [SND_DEVICE_IN_SPEAKER_QMIC_NS] = 127,
    [SND_DEVICE_IN_SPEAKER_QMIC_AEC_NS] = 129,
};

struct name_to_index {
    char name[100];
    unsigned int index;
};

#define TO_NAME_INDEX(X)   #X, X

/* Used to get index from parsed sting */
static struct name_to_index snd_device_name_index[SND_DEVICE_MAX] = {
    {TO_NAME_INDEX(SND_DEVICE_OUT_HANDSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_REVERSE)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_LINE)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_AND_LINE)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_HANDSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_HAC_HANDSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_SPEAKER)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_SPEAKER_HFP)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_LINE)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_HDMI)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_AND_HDMI)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_BT_SCO)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_BT_SCO_WB)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_TX)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_AFE_PROXY)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_USB_HEADSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_USB_HEADSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_USB_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_USB_HEADPHONES)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_SPEAKER_PROTECTED)},
    {TO_NAME_INDEX(SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_MIC_EXTERNAL)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_MIC_AEC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_MIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_MIC_AEC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_DMIC_AEC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_DMIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_DMIC_AEC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_MIC_AEC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_MIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_MIC_AEC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_AEC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HEADSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HEADSET_MIC_FLUENCE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_SPEAKER_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_SPEAKER_MIC_HFP)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_HEADSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HDMI_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_BT_SCO_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_BT_SCO_MIC_NREC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_BT_SCO_MIC_WB)},
    {TO_NAME_INDEX(SND_DEVICE_IN_BT_SCO_MIC_WB_NREC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_CAMCORDER_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_SPEAKER_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_SPEAKER_QMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_TTY_FULL_HEADSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_TTY_VCO_HANDSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_TTY_HCO_HEADSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_REC_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_REC_MIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_REC_DMIC_STEREO)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_REC_DMIC_FLUENCE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_RX)},
    {TO_NAME_INDEX(SND_DEVICE_IN_USB_HEADSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_CAPTURE_FM)},
    {TO_NAME_INDEX(SND_DEVICE_IN_AANC_HANDSET_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_QUAD_MIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_STEREO_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_STEREO_DMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_CAPTURE_VI_FEEDBACK)},
    {TO_NAME_INDEX(SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BROADSIDE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_BROADSIDE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_AEC_BROADSIDE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_NS_BROADSIDE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS_BROADSIDE)},
    {TO_NAME_INDEX(SND_DEVICE_IN_HANDSET_QMIC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_QMIC_AEC)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_QMIC_NS)},
    {TO_NAME_INDEX(SND_DEVICE_IN_SPEAKER_QMIC_AEC_NS)},
};

static char * backend_table[SND_DEVICE_MAX] = {0};
static char * hw_interface_table[SND_DEVICE_MAX] = {0};

static struct name_to_index usecase_name_index[AUDIO_USECASE_MAX] = {
    {TO_NAME_INDEX(USECASE_AUDIO_PLAYBACK_DEEP_BUFFER)},
    {TO_NAME_INDEX(USECASE_AUDIO_PLAYBACK_LOW_LATENCY)},
    {TO_NAME_INDEX(USECASE_AUDIO_PLAYBACK_HIFI)},
    {TO_NAME_INDEX(USECASE_AUDIO_PLAYBACK_OFFLOAD)},
    {TO_NAME_INDEX(USECASE_AUDIO_PLAYBACK_ULL)},
    {TO_NAME_INDEX(USECASE_AUDIO_RECORD)},
    {TO_NAME_INDEX(USECASE_AUDIO_RECORD_LOW_LATENCY)},
    {TO_NAME_INDEX(USECASE_VOICE_CALL)},
    {TO_NAME_INDEX(USECASE_VOICE2_CALL)},
    {TO_NAME_INDEX(USECASE_VOLTE_CALL)},
    {TO_NAME_INDEX(USECASE_QCHAT_CALL)},
    {TO_NAME_INDEX(USECASE_VOWLAN_CALL)},
    {TO_NAME_INDEX(USECASE_VOICEMMODE1_CALL)},
    {TO_NAME_INDEX(USECASE_VOICEMMODE2_CALL)},
    {TO_NAME_INDEX(USECASE_AUDIO_HFP_SCO)},
    {TO_NAME_INDEX(USECASE_AUDIO_SPKR_CALIB_TX)},
};

#define DEEP_BUFFER_PLATFORM_DELAY (29*1000LL)
#define LOW_LATENCY_PLATFORM_DELAY (13*1000LL)

static void query_platform(const char *snd_card_name,
                                      char *mixer_xml_path)
{
    if (!strncmp(snd_card_name, "msm8x16-snd-card-mtp",
                 sizeof("msm8x16-snd-card-mtp"))) {
        strlcpy(mixer_xml_path, MIXER_XML_PATH_MTP,
                sizeof(MIXER_XML_PATH_MTP));
    } else if (!strncmp(snd_card_name, "msm8909-pm8916-snd-card",
                 sizeof("msm8909-pm8916-snd-card"))) {
        strlcpy(mixer_xml_path, MIXER_XML_PATH_MSM8909_PM8916,
                sizeof(MIXER_XML_PATH_MSM8909_PM8916));
    } else if (!strncmp(snd_card_name, "msm8952-snd-card-mtp",
                 sizeof("msm8952-snd-card-mtp"))) {
        strlcpy(mixer_xml_path, MIXER_XML_PATH_MTP,
                sizeof(MIXER_XML_PATH_MTP));
    } else if (!strncmp(snd_card_name, "msm8952-l9300-snd-card",
                 sizeof("msm8952-l9300-snd-card"))) {
        strlcpy(mixer_xml_path, MIXER_XML_PATH_L9300,
                sizeof(MIXER_XML_PATH_L9300));
    } else {
        strlcpy(mixer_xml_path, MIXER_XML_PATH,
                sizeof(MIXER_XML_PATH));
    }
}

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
    /* Add new TMUS MNC(800, 660, 580, 310, 270, 250, 240, 230, 220, 210, 200, 160) */
    case 310800:
    case 310660:
    case 310580:
    case 310310:
    case 310270:
    case 310250:
    case 310240:
    case 310230:
    case 310220:
    case 310210:
    case 310200:
    case 310160:
        is_tmus = true;
        break;
    }
}

bool is_operator_tmus()
{
    pthread_once(&check_op_once_ctl, check_operator);
    return is_tmus;
}

static char *get_current_operator()
{
    struct listnode *node;
    struct operator_info *info_item;
    char mccmnc[PROPERTY_VALUE_MAX];
    char *ret = NULL;

    property_get("gsm.sim.operator.numeric",mccmnc,"00000");

    list_for_each(node, &operator_info_list) {
        info_item = node_to_item(node, struct operator_info, list);
        if (strstr(info_item->mccmnc, mccmnc) != NULL) {
            ret = info_item->name;
        }
    }

    return ret;
}

static struct operator_specific_device *get_operator_specific_device(snd_device_t snd_device)
{
    struct listnode *node;
    struct operator_specific_device *ret = NULL;
    struct operator_specific_device *device_item;
    char *operator_name;

    operator_name = get_current_operator();
    if (operator_name == NULL)
        return ret;

    list_for_each(node, operator_specific_device_table[snd_device]) {
        device_item = node_to_item(node, struct operator_specific_device, list);
        if (strcmp(operator_name, device_item->operator) == 0) {
            ret = device_item;
        }
    }

    return ret;
}

static int get_operator_specific_device_acdb_id(snd_device_t snd_device)
{
    struct operator_specific_device *device;
    int ret = acdb_device_table[snd_device];

    device = get_operator_specific_device(snd_device);
    if (device != NULL)
        ret = device->acdb_id;

    return ret;
}

static const char *get_operator_specific_device_mixer_path(snd_device_t snd_device)
{
    struct operator_specific_device *device;
    const char *ret = device_table[snd_device];

    device = get_operator_specific_device(snd_device);
    if (device != NULL)
        ret = device->mixer_path;

    return ret;
}

bool platform_send_gain_dep_cal(void *platform __unused, int level __unused)
{
    return true;
}

void platform_set_echo_reference(struct audio_device *adev, bool enable,
    audio_devices_t out_device)
{
    struct platform_data *my_data = (struct platform_data *)adev->platform;
    snd_device_t snd_device = SND_DEVICE_NONE;

    if (strcmp(my_data->ec_ref_mixer_path, "")) {
        ALOGV("%s: disabling %s", __func__, my_data->ec_ref_mixer_path);
        audio_route_reset_and_update_path(adev->audio_route,
            my_data->ec_ref_mixer_path);
    }

    if (enable) {
        if (out_device != AUDIO_DEVICE_NONE) {
            snd_device = platform_get_output_snd_device(adev->platform, out_device);
            platform_add_backend_name(adev->platform, my_data->ec_ref_mixer_path, snd_device);
        }

        strlcpy(my_data->ec_ref_mixer_path, "echo-reference",
            sizeof(my_data->ec_ref_mixer_path));

        ALOGD("%s: enabling %s", __func__, my_data->ec_ref_mixer_path);
        audio_route_apply_and_update_path(adev->audio_route,
            my_data->ec_ref_mixer_path);
    }
}

static void set_platform_defaults()
{
    int32_t dev;
    for (dev = 0; dev < SND_DEVICE_MAX; dev++) {
        backend_table[dev] = NULL;
        hw_interface_table[dev] = NULL;
    }

    // TBD - do these go to the platform-info.xml file.
    // will help in avoiding strdups here
    backend_table[SND_DEVICE_IN_BT_SCO_MIC] = strdup("bt-sco");
    backend_table[SND_DEVICE_IN_BT_SCO_MIC_WB] = strdup("bt-sco-wb");
    backend_table[SND_DEVICE_IN_BT_SCO_MIC_NREC] = strdup("bt-sco");
    backend_table[SND_DEVICE_IN_BT_SCO_MIC_WB_NREC] = strdup("bt-sco-wb");
    backend_table[SND_DEVICE_OUT_BT_SCO] = strdup("bt-sco");
    backend_table[SND_DEVICE_OUT_BT_SCO_WB] = strdup("bt-sco-wb");
    backend_table[SND_DEVICE_OUT_HDMI] = strdup("hdmi");
    backend_table[SND_DEVICE_OUT_SPEAKER_AND_HDMI] = strdup("speaker-and-hdmi");
    backend_table[SND_DEVICE_OUT_VOICE_TX] = strdup("afe-proxy");
    backend_table[SND_DEVICE_IN_VOICE_RX] = strdup("afe-proxy");
    backend_table[SND_DEVICE_OUT_AFE_PROXY] = strdup("afe-proxy");
    backend_table[SND_DEVICE_OUT_USB_HEADSET] = strdup("usb-headphones");
    backend_table[SND_DEVICE_OUT_VOICE_USB_HEADSET] = strdup("usb-headphones");
    backend_table[SND_DEVICE_OUT_USB_HEADPHONES] = strdup("usb-headphones");
    backend_table[SND_DEVICE_OUT_VOICE_USB_HEADPHONES] = strdup("usb-headphones");
    backend_table[SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET] =
        strdup("speaker-and-usb-headphones");
    backend_table[SND_DEVICE_IN_USB_HEADSET_MIC] = strdup("usb-headset-mic");
    backend_table[SND_DEVICE_IN_CAPTURE_FM] = strdup("capture-fm");
}

void get_cvd_version(char *cvd_version, struct audio_device *adev)
{
    struct mixer_ctl *ctl;
    int count;
    int ret = 0;

    ctl = mixer_get_ctl_by_name(adev->mixer, CVD_VERSION_MIXER_CTL);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",  __func__, CVD_VERSION_MIXER_CTL);
        goto done;
    }
    mixer_ctl_update(ctl);

    count = mixer_ctl_get_num_values(ctl);
    if (count > MAX_CVD_VERSION_STRING_SIZE)
        count = MAX_CVD_VERSION_STRING_SIZE -1;

    ret = mixer_ctl_get_array(ctl, cvd_version, count);
    if (ret != 0) {
        ALOGE("%s: ERROR! mixer_ctl_get_array() failed to get CVD Version", __func__);
        goto done;
    }

done:
    return;
}

static int hw_util_open(int card_no)
{
    int fd = -1;
    char dev_name[256];

    snprintf(dev_name, sizeof(dev_name), "/dev/snd/hwC%uD%u",
                               card_no, WCD9XXX_CODEC_HWDEP_NODE);
    ALOGD("%s Opening device %s\n", __func__, dev_name);
    fd = open(dev_name, O_WRONLY);
    if (fd < 0) {
        ALOGE("%s: cannot open device '%s'\n", __func__, dev_name);
        return fd;
    }
    ALOGD("%s success", __func__);
    return fd;
}

struct param_data {
    int    use_case;
    int    acdb_id;
    int    get_size;
    int    buff_size;
    int    data_size;
    void   *buff;
};

static int send_codec_cal(acdb_loader_get_calibration_t acdb_loader_get_calibration,
                          struct platform_data *plat_data __unused, int fd)
{
    int ret = 0, type;

    for (type = WCD9XXX_ANC_CAL; type < WCD9XXX_MAX_CAL; type++) {
        struct wcdcal_ioctl_buffer codec_buffer;
        struct param_data calib;

        if (type != WCD9XXX_MBHC_CAL)
            continue;

        calib.get_size = 1;
        ret = acdb_loader_get_calibration(cal_name_info[type], sizeof(struct param_data),
                                                                 &calib);
        if (ret < 0) {
            ALOGE("%s get_calibration failed\n", __func__);
            return ret;
        }
        calib.get_size = 0;
        calib.buff = malloc(calib.buff_size);
        if(calib.buff == NULL) {
            ALOGE("%s mem allocation for %d bytes for %s failed\n"
                , __func__, calib.buff_size, cal_name_info[type]);
            return -1;
        }
        ret = acdb_loader_get_calibration(cal_name_info[type],
                              sizeof(struct param_data), &calib);
        if (ret < 0) {
            ALOGE("%s get_calibration failed type=%s calib.size=%d\n"
                , __func__, cal_name_info[type], codec_buffer.size);
            free(calib.buff);
            return ret;
        }
        codec_buffer.buffer = calib.buff;
        codec_buffer.size = calib.data_size;
        codec_buffer.cal_type = type;
        if (ioctl(fd, SNDRV_CTL_IOCTL_HWDEP_CAL_TYPE, &codec_buffer) < 0)
            ALOGE("Failed to call ioctl  for %s err=%d calib.size=%d",
                cal_name_info[type], errno, codec_buffer.size);
        ALOGD("%s cal sent for %s calib.size=%d"
            , __func__, cal_name_info[type], codec_buffer.size);
        free(calib.buff);
    }
    return ret;
}

static void audio_hwdep_send_cal(struct platform_data *plat_data)
{
    int fd;

    fd = hw_util_open(plat_data->adev->snd_card);
    if (fd == -1) {
        ALOGE("%s error open\n", __func__);
        return;
    }

    acdb_loader_get_calibration = (acdb_loader_get_calibration_t)
          dlsym(plat_data->acdb_handle, "acdb_loader_get_calibration");

    if (acdb_loader_get_calibration == NULL) {
        ALOGE("%s: ERROR. dlsym Error:%s acdb_loader_get_calibration", __func__,
           dlerror());
        return;
    }
    if (send_codec_cal(acdb_loader_get_calibration, plat_data, fd) < 0)
        ALOGE("%s: Could not send anc cal", __FUNCTION__);
}


int platform_acdb_init(void *platform)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    char *cvd_version = NULL;
    int key = 0;
    const char *snd_card_name;
    int result;
    char value[PROPERTY_VALUE_MAX];
    cvd_version = calloc(1, MAX_CVD_VERSION_STRING_SIZE);
    if (!cvd_version)
        ALOGE("Failed to allocate cvd version");
    else
        get_cvd_version(cvd_version, my_data->adev);

    property_get("audio.ds1.metainfo.key",value,"0");
    key = atoi(value);
    snd_card_name = mixer_get_name(my_data->adev->mixer);

    result = my_data->acdb_init(snd_card_name, cvd_version, key);

    if (cvd_version)
        free(cvd_version);
    if (!result) {
        ALOGD("ACDB initialized");
        audio_hwdep_send_cal(my_data);
    } else {
        ALOGD("ACDB initialization failed");
    }
    return result;
}

void *platform_init(struct audio_device *adev)
{
    char platform[PROPERTY_VALUE_MAX] = {0};
    char baseband[PROPERTY_VALUE_MAX] = {0};
    char value[PROPERTY_VALUE_MAX] = {0};
    struct platform_data *my_data = NULL;
    int retry_num = 0, snd_card_num = 0, key = 0;
    const char *snd_card_name;
    char mixer_xml_path[MAX_MIXER_XML_PATH] = {0};
    char ffspEnable[PROPERTY_VALUE_MAX] = {0};
    char *cvd_version = NULL;
    int idx;

    my_data = calloc(1, sizeof(struct platform_data));
    if (!my_data) {
        ALOGE("failed to allocate platform data");
        return NULL;
    }

    list_init(&operator_info_list);
    bool card_verifed[MAX_SND_CARD] = {0};
    const int retry_limit = property_get_int32("audio.snd_card.open.retries", RETRY_NUMBER);

    for (;;) {
        if (snd_card_num >= MAX_SND_CARD) {
            if (retry_num++ >= retry_limit) {
                ALOGE("%s: Unable to find correct sound card, aborting.", __func__);
                free(my_data);
                my_data = NULL;
                return NULL;
            }

            snd_card_num = 0;
            usleep(RETRY_US);
            continue;
        }

        if (card_verifed[snd_card_num]) {
            ++snd_card_num;
            continue;
        }

        adev->mixer = mixer_open(snd_card_num);

        if (!adev->mixer) {
            ALOGE("%s: Unable to open the mixer card: %d", __func__,
               snd_card_num);
            ++snd_card_num;
            continue;
        }

        card_verifed[snd_card_num] = true;

        snd_card_name = mixer_get_name(adev->mixer);
        ALOGV("%s: snd_card_name: %s", __func__, snd_card_name);

        my_data->hw_info = hw_info_init(snd_card_name);
        if (!my_data->hw_info) {
            ALOGE("%s: Failed to init hardware info", __func__);
        } else {
            query_platform(snd_card_name, mixer_xml_path);
            ALOGD("%s: mixer path file is %s", __func__,
                                    mixer_xml_path);
            adev->audio_route = audio_route_init(snd_card_num,
                                                 mixer_xml_path);
            if (!adev->audio_route) {
                ALOGE("%s: Failed to init audio route controls, aborting.",
                       __func__);
                hw_info_deinit(my_data->hw_info);
                my_data->hw_info = NULL;
                free(my_data);
                my_data = NULL;
                mixer_close(adev->mixer);
                adev->mixer = NULL;
                return NULL;
            }
            adev->snd_card = snd_card_num;
            ALOGD("%s: Opened sound card:%d", __func__, snd_card_num);
            break;
        }
        ++snd_card_num;
        mixer_close(adev->mixer);
        adev->mixer = NULL;
    }

    //set max volume step for voice call
    property_get("ro.config.vc_call_vol_steps", value, TOSTRING(MAX_VOL_INDEX));
    my_data->max_vol_index = atoi(value);

    my_data->adev = adev;
    my_data->fluence_in_spkr_mode = false;
    my_data->fluence_in_voice_call = false;
    my_data->fluence_in_voice_rec = false;
    my_data->fluence_type = FLUENCE_NONE;
    my_data->fluence_mode = FLUENCE_ENDFIRE;

    property_get("ro.qc.sdk.audio.fluencetype", my_data->fluence_cap, "");
    if (!strncmp("fluencepro", my_data->fluence_cap, sizeof("fluencepro"))) {
        my_data->fluence_type = FLUENCE_QUAD_MIC | FLUENCE_DUAL_MIC;
    } else if (!strncmp("fluence", my_data->fluence_cap, sizeof("fluence"))) {
        my_data->fluence_type = FLUENCE_DUAL_MIC;
    } else {
        my_data->fluence_type = FLUENCE_NONE;
    }

    if (my_data->fluence_type != FLUENCE_NONE) {
        property_get("persist.audio.fluence.voicecall",value,"");
        if (!strncmp("true", value, sizeof("true"))) {
            my_data->fluence_in_voice_call = true;
        }

        property_get("persist.audio.fluence.voicerec",value,"");
        if (!strncmp("true", value, sizeof("true"))) {
            my_data->fluence_in_voice_rec = true;
        }

        property_get("persist.audio.fluence.speaker",value,"");
        if (!strncmp("true", value, sizeof("true"))) {
            my_data->fluence_in_spkr_mode = true;
        }

        property_get("persist.audio.fluence.mode",value,"");
        if (!strncmp("broadside", value, sizeof("broadside"))) {
            my_data->fluence_mode = FLUENCE_BROADSIDE;
        }
    }

    property_get("persist.audio.FFSP.enable", ffspEnable, "");
    if (!strncmp("true", ffspEnable, sizeof("true"))) {
        acdb_device_table[SND_DEVICE_OUT_SPEAKER] = 131;
        acdb_device_table[SND_DEVICE_OUT_SPEAKER_REVERSE] = 131;
        acdb_device_table[SND_DEVICE_OUT_SPEAKER_AND_HDMI] = 131;
        acdb_device_table[SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET] = 131;
    }

    my_data->acdb_handle = dlopen(LIB_ACDB_LOADER, RTLD_NOW);
    if (my_data->acdb_handle == NULL) {
        ALOGE("%s: DLOPEN failed for %s", __func__, LIB_ACDB_LOADER);
    } else {
        ALOGV("%s: DLOPEN successful for %s", __func__, LIB_ACDB_LOADER);
        my_data->acdb_deallocate = (acdb_deallocate_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_deallocate_ACDB");
        if (!my_data->acdb_deallocate)
            ALOGE("%s: Could not find the symbol acdb_loader_deallocate_ACDB from %s",
                  __func__, LIB_ACDB_LOADER);

        my_data->acdb_send_audio_cal_v3 = (acdb_send_audio_cal_v3_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_send_audio_cal_v3");
        if (!my_data->acdb_send_audio_cal_v3)
            ALOGE("%s: Could not find the symbol acdb_send_audio_cal from %s",
                  __func__, LIB_ACDB_LOADER);

        my_data->acdb_send_audio_cal = (acdb_send_audio_cal_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_send_audio_cal");
        if (!my_data->acdb_send_audio_cal)
            ALOGE("%s: Could not find the symbol acdb_send_audio_cal from %s",
                  __func__, LIB_ACDB_LOADER);

        my_data->acdb_send_voice_cal = (acdb_send_voice_cal_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_send_voice_cal");
        if (!my_data->acdb_send_voice_cal)
            ALOGE("%s: Could not find the symbol acdb_loader_send_voice_cal from %s",
                  __func__, LIB_ACDB_LOADER);

        my_data->acdb_reload_vocvoltable = (acdb_reload_vocvoltable_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_reload_vocvoltable");
        if (!my_data->acdb_reload_vocvoltable)
            ALOGE("%s: Could not find the symbol acdb_loader_reload_vocvoltable from %s",
                  __func__, LIB_ACDB_LOADER);

        my_data->acdb_init = (acdb_init_v2_cvd_t)dlsym(my_data->acdb_handle,
                                                    "acdb_loader_init_v2");
        if (my_data->acdb_init == NULL) {
            ALOGE("%s: dlsym error %s for acdb_loader_init_v2", __func__, dlerror());
            goto acdb_init_fail;
        }
        platform_acdb_init(my_data);
    }

acdb_init_fail:

    set_platform_defaults();

    /* Initialize ACDB and PCM ID's */
    platform_info_init(PLATFORM_INFO_XML_PATH, my_data);


    /* Read one time ssr property */
    audio_extn_spkr_prot_init(adev);

    return my_data;
}

void platform_deinit(void *platform)
{
    struct platform_data *my_data = (struct platform_data *)platform;

    int32_t dev;
    struct operator_info *info_item;
    struct operator_specific_device *device_item;
    struct listnode *node;

    hw_info_deinit(my_data->hw_info);

    for (dev = 0; dev < SND_DEVICE_MAX; dev++) {
        if (backend_table[dev]) {
            free(backend_table[dev]);
            backend_table[dev]= NULL;
        }
        if (operator_specific_device_table[dev]) {
            while (!list_empty(operator_specific_device_table[dev])) {
                node = list_head(operator_specific_device_table[dev]);
                list_remove(node);
                device_item = node_to_item(node, struct operator_specific_device, list);
                free(device_item->operator);
                free(device_item->mixer_path);
                free(device_item);
            }
            free(operator_specific_device_table[dev]);
        }
    }

    while (!list_empty(&operator_info_list)) {
        node = list_head(&operator_info_list);
        list_remove(node);
        info_item = node_to_item(node, struct operator_info, list);
        free(info_item->name);
        free(info_item->mccmnc);
        free(info_item);
    }

    mixer_close(my_data->adev->mixer);
    free(platform);
}

const char *platform_get_snd_device_name(snd_device_t snd_device)
{
    if (snd_device >= SND_DEVICE_MIN && snd_device < SND_DEVICE_MAX) {
        if (operator_specific_device_table[snd_device] != NULL) {
            return get_operator_specific_device_mixer_path(snd_device);
        }
        return device_table[snd_device];
    } else
        return "none";
}

int platform_get_snd_device_name_extn(void *platform, snd_device_t snd_device,
                                      char *device_name)
{
    struct platform_data *my_data = (struct platform_data *)platform;

    if (platform == NULL) {
        ALOGW("%s: something wrong, use legacy get_snd_device name", __func__);
        strlcpy(device_name, platform_get_snd_device_name(snd_device),
                 DEVICE_NAME_MAX_SIZE);
    } else if (snd_device >= SND_DEVICE_MIN && snd_device < SND_DEVICE_MAX) {
        if (operator_specific_device_table[snd_device] != NULL) {
            strlcpy(device_name, get_operator_specific_device_mixer_path(snd_device),
                     DEVICE_NAME_MAX_SIZE);
        } else {
            strlcpy(device_name, device_table[snd_device], DEVICE_NAME_MAX_SIZE);
        }
        hw_info_append_hw_type(my_data->hw_info, snd_device, device_name);
    } else {
        strlcpy(device_name, "none", DEVICE_NAME_MAX_SIZE);
        return -EINVAL;
    }

    return 0;
}

bool platform_check_and_set_playback_backend_cfg(struct audio_device* adev __unused,
                                              struct audio_usecase *usecase __unused,
                                              snd_device_t snd_device __unused)
{
    return false;
}

bool platform_check_and_set_capture_backend_cfg(struct audio_device* adev __unused,
                                              struct audio_usecase *usecase __unused,
                                              snd_device_t snd_device __unused)
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

void platform_add_backend_name(void *platform, char *mixer_path,
                               snd_device_t snd_device)
{

    struct platform_data *my_data = (struct platform_data *)platform;

    if ((snd_device < SND_DEVICE_MIN) || (snd_device >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %d", __func__, snd_device);
        return;
    }

    const char * suffix = backend_table[snd_device];

    if (suffix != NULL) {
        strlcat(mixer_path, " ", MIXER_PATH_MAX_LENGTH);
        strlcat(mixer_path, suffix, MIXER_PATH_MAX_LENGTH);
    }
}

bool platform_check_backends_match(snd_device_t snd_device1, snd_device_t snd_device2)
{
    bool result = true;

    ALOGV("%s: snd_device1 = %s, snd_device2 = %s", __func__,
                platform_get_snd_device_name(snd_device1),
                platform_get_snd_device_name(snd_device2));

    if ((snd_device1 < SND_DEVICE_MIN) || (snd_device1 >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %s", __func__,
                platform_get_snd_device_name(snd_device1));
        return false;
    }
    if ((snd_device2 < SND_DEVICE_MIN) || (snd_device2 >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %s", __func__,
                platform_get_snd_device_name(snd_device2));
        return false;
    }
    const char * be_itf1 = hw_interface_table[snd_device1];
    const char * be_itf2 = hw_interface_table[snd_device2];

    if (NULL != be_itf1 && NULL != be_itf2) {
        if ((NULL == strstr(be_itf2, be_itf1)) && (NULL == strstr(be_itf1, be_itf2)))
            result = false;
    } else if (NULL != be_itf2 && (NULL == strstr(be_itf2, DEFAULT_RX_BACKEND))) {
        result = false;
    } else if (NULL != be_itf1 && (NULL == strstr(be_itf1, DEFAULT_RX_BACKEND))) {
        result = false;
    }

    ALOGV("%s: be_itf1 = %s, be_itf2 = %s, match %d", __func__, be_itf1, be_itf2, result);
    return result;
}

int platform_get_pcm_device_id(audio_usecase_t usecase, int device_type)
{
    int device_id = -1;

    if (device_type == PCM_PLAYBACK)
        device_id = pcm_device_table[usecase][0];
    else
        device_id = pcm_device_table[usecase][1];
    return device_id;
}

static int find_index(struct name_to_index * table, int32_t len, const char * name)
{
    int ret = 0;
    int32_t i;

    if (table == NULL) {
        ALOGE("%s: table is NULL", __func__);
        ret = -ENODEV;
        goto done;
    }

    if (name == NULL) {
        ALOGE("null key");
        ret = -ENODEV;
        goto done;
    }

    for (i=0; i < len; i++) {
        const char* tn = table[i].name;
        size_t len = strlen(tn);
        if (strncmp(tn, name, len) == 0) {
            if (strlen(name) != len) {
                continue; // substring
            }
            ret = table[i].index;
            goto done;
        }
    }
    ALOGE("%s: Could not find index for name = %s",
            __func__, name);
    ret = -ENODEV;
done:
    return ret;
}

int platform_get_snd_device_index(char *device_name)
{
    return find_index(snd_device_name_index, SND_DEVICE_MAX, device_name);
}

int platform_get_usecase_index(const char *usecase_name)
{
    return find_index(usecase_name_index, AUDIO_USECASE_MAX, usecase_name);
}

void platform_add_operator_specific_device(snd_device_t snd_device,
                                           const char *operator,
                                           const char *mixer_path,
                                           unsigned int acdb_id)
{
    struct operator_specific_device *device;

    if (operator_specific_device_table[snd_device] == NULL) {
        operator_specific_device_table[snd_device] =
            (struct listnode *)calloc(1, sizeof(struct listnode));
        list_init(operator_specific_device_table[snd_device]);
    }

    device = (struct operator_specific_device *)calloc(1, sizeof(struct operator_specific_device));

    device->operator = strdup(operator);
    device->mixer_path = strdup(mixer_path);
    device->acdb_id = acdb_id;

    list_add_tail(operator_specific_device_table[snd_device], &device->list);

    ALOGD("%s: device[%s] -> operator[%s] mixer_path[%s] acdb_id[%d]", __func__,
            platform_get_snd_device_name(snd_device), operator, mixer_path, acdb_id);

}

int platform_set_snd_device_acdb_id(snd_device_t snd_device, unsigned int acdb_id)
{
    int ret = 0;

    if ((snd_device < SND_DEVICE_MIN) || (snd_device >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %d",
            __func__, snd_device);
        ret = -EINVAL;
        goto done;
    }
    ALOGV("%s: acdb_device_table[%s]: old = %d new = %d", __func__,
          platform_get_snd_device_name(snd_device), acdb_device_table[snd_device], acdb_id);
    acdb_device_table[snd_device] = acdb_id;
done:
    return ret;
}

int platform_get_default_app_type_v2(void *platform, usecase_type_t type, int *app_type)
{
    ALOGV("%s: platform: %p, type: %d", __func__, platform, type);
    int rc = 0;
    if (type == PCM_CAPTURE) {
        *app_type = DEFAULT_APP_TYPE_TX_PATH;
    } else if (type == PCM_PLAYBACK) {
        *app_type =  DEFAULT_APP_TYPE_RX_PATH;
    } else {
        rc = -EINVAL;
    }
    return rc;
}

int platform_get_snd_device_acdb_id(snd_device_t snd_device)
{
    if ((snd_device < SND_DEVICE_MIN) || (snd_device >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %d", __func__, snd_device);
        return -EINVAL;
    }

    if (operator_specific_device_table[snd_device] != NULL)
        return get_operator_specific_device_acdb_id(snd_device);
    else
        return acdb_device_table[snd_device];
}

int platform_send_audio_calibration(void *platform, snd_device_t snd_device)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int acdb_dev_id, acdb_dev_type;
    int sample_rate = CODEC_BACKEND_DEFAULT_SAMPLE_RATE;

    acdb_dev_id = acdb_device_table[audio_extn_get_spkr_prot_snd_device(snd_device)];
    if (acdb_dev_id < 0) {
        ALOGE("%s: Could not find acdb id for device(%d)",
              __func__, snd_device);
        return -EINVAL;
    }
        ALOGV("%s: sending audio calibration for snd_device(%d) acdb_id(%d)",
              __func__, snd_device, acdb_dev_id);
    if (snd_device >= SND_DEVICE_OUT_BEGIN && snd_device < SND_DEVICE_OUT_END)
        acdb_dev_type = ACDB_DEV_TYPE_OUT;
    else
        acdb_dev_type = ACDB_DEV_TYPE_IN;

    if ((my_data->acdb_send_audio_cal_v3) &&
        (snd_device == SND_DEVICE_IN_VOICE_SPEAKER_MIC_HFP) &&
        !audio_extn_tfa_98xx_is_supported() ) {
            /* TX path calibration */
            my_data->acdb_send_audio_cal_v3(acdb_dev_id, ACDB_DEV_TYPE_IN,
                                DEFAULT_APP_TYPE_TX_PATH, sample_rate, BUFF_IDX_0);
            my_data->acdb_send_audio_cal_v3(acdb_dev_id, ACDB_DEV_TYPE_OUT,
                                DEFAULT_APP_TYPE_RX_PATH, sample_rate, BUFF_IDX_0);
    } else if ((my_data->acdb_send_audio_cal_v3) &&
               (snd_device == SND_DEVICE_OUT_VOICE_SPEAKER_HFP) &&
               !audio_extn_tfa_98xx_is_supported()) {
            /* RX path calibration */
            ALOGV("%s: sending audio calibration for snd_device(%d) acdb_id(%d)",
                       __func__, snd_device, acdb_dev_id);
            my_data->acdb_send_audio_cal_v3(acdb_dev_id, ACDB_DEV_TYPE_IN,
                                DEFAULT_APP_TYPE_TX_PATH, sample_rate, BUFF_IDX_1);
            my_data->acdb_send_audio_cal_v3(acdb_dev_id, ACDB_DEV_TYPE_OUT,
                                DEFAULT_APP_TYPE_RX_PATH, sample_rate, BUFF_IDX_1);
    } else if (my_data->acdb_send_audio_cal) {
        my_data->acdb_send_audio_cal(acdb_dev_id, acdb_dev_type);
    }
    return 0;
}

int platform_switch_voice_call_device_pre(void *platform __unused)
{
    return 0;
}

int platform_switch_voice_call_enable_device_config(void *platform __unused,
                                                    snd_device_t out_snd_device __unused,
                                                    snd_device_t in_snd_device __unused)
{
    return 0;
}

int platform_switch_voice_call_device_post(void *platform,
                                           snd_device_t out_snd_device,
                                           snd_device_t in_snd_device)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    int acdb_rx_id, acdb_tx_id;

    if (my_data->acdb_send_voice_cal == NULL) {
        ALOGE("%s: dlsym error for acdb_send_voice_call", __func__);
    } else {
        if (out_snd_device == SND_DEVICE_OUT_VOICE_SPEAKER &&
            audio_extn_spkr_prot_is_enabled())
            out_snd_device = SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED;

        acdb_rx_id = platform_get_snd_device_acdb_id(out_snd_device);
        acdb_tx_id = platform_get_snd_device_acdb_id(in_snd_device);

        if (acdb_rx_id > 0 && acdb_tx_id > 0)
            my_data->acdb_send_voice_cal(acdb_rx_id, acdb_tx_id);
        else
            ALOGE("%s: Incorrect ACDB IDs (rx: %d tx: %d)", __func__,
                  acdb_rx_id, acdb_tx_id);
    }

    return 0;
}

int platform_switch_voice_call_usecase_route_post(void *platform __unused,
                                                  snd_device_t out_snd_device __unused,
                                                  snd_device_t in_snd_device __unused)
{
    return 0;
}

int platform_start_voice_call(void *platform __unused, uint32_t vsid __unused)
{
    return 0;
}

int platform_stop_voice_call(void *platform __unused, uint32_t vsid __unused)
{
    return 0;
}

int platform_get_sample_rate(void *platform __unused, uint32_t *rate __unused)
{
    return 0;
}

void platform_set_speaker_gain_in_combo(struct audio_device *adev __unused,
                                        snd_device_t snd_device __unused,
                                        bool enable __unused)
{
    return;
}

int platform_set_voice_volume(void *platform, int volume)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    struct mixer_ctl *ctl;
    const char *mixer_ctl_name = "Voice Rx Gain";
    int vol_index = 0, ret = 0;
    uint32_t set_values[ ] = {0,
                              ALL_SESSION_VSID,
                              DEFAULT_VOLUME_RAMP_DURATION_MS};

    // Voice volume levels are mapped to adsp volume levels as follows.
    // 100 -> 5, 80 -> 4, 60 -> 3, 40 -> 2, 20 -> 1  0 -> 0
    // But this values don't changed in kernel. So, below change is need.
    vol_index = (int)percent_to_index(volume, MIN_VOL_INDEX, my_data->max_vol_index);
    set_values[0] = vol_index;

    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }
    ALOGV("Setting voice volume index: %d", set_values[0]);
    ret = mixer_ctl_set_array(ctl, set_values, ARRAY_SIZE(set_values));

    return ret;
}

int platform_set_mic_mute(void *platform, bool state)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    struct mixer_ctl *ctl;
    const char *mixer_ctl_name = "Voice Tx Mute";
    int ret = 0;
    uint32_t set_values[ ] = {0,
                              ALL_SESSION_VSID,
                              DEFAULT_MUTE_RAMP_DURATION_MS};

    if (audio_extn_hfp_is_active(adev))
        mixer_ctl_name = "HFP TX Mute";

    set_values[0] = state;
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }
    ALOGV("Setting voice mute state: %d", state);
    // "HFP TX mute" mixer control has xcount of 1.
    if (audio_extn_hfp_is_active(adev))
        ret = mixer_ctl_set_array(ctl, set_values, 1 /*count*/);
    else
        ret = mixer_ctl_set_array(ctl, set_values, ARRAY_SIZE(set_values));
    return ret;
}

int platform_set_device_mute(void *platform, bool state, char *dir)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    struct mixer_ctl *ctl;
    char *mixer_ctl_name = NULL;
    int ret = 0;
    uint32_t set_values[ ] = {0,
                              ALL_SESSION_VSID,
                              0};
    if(dir == NULL) {
        ALOGE("%s: Invalid direction:%s", __func__, dir);
        return -EINVAL;
    }

    if (!strncmp("rx", dir, sizeof("rx"))) {
        mixer_ctl_name = "Voice Rx Device Mute";
    } else if (!strncmp("tx", dir, sizeof("tx"))) {
        mixer_ctl_name = "Voice Tx Device Mute";
    } else {
        return -EINVAL;
    }

    set_values[0] = state;
    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        return -EINVAL;
    }

    ALOGV("%s: Setting device mute state: %d, mixer ctrl:%s",
          __func__,state, mixer_ctl_name);
    mixer_ctl_set_array(ctl, set_values, ARRAY_SIZE(set_values));

    return ret;
}

int platform_can_split_snd_device(snd_device_t snd_device,
                                   int *num_devices,
                                   snd_device_t *new_snd_devices)
{
    int ret = -EINVAL;

    if (NULL == num_devices || NULL == new_snd_devices) {
        ALOGE("%s: NULL pointer ..", __func__);
        return -EINVAL;
    }

    /*
     * If wired headset/headphones/line devices share the same backend
     * with speaker/earpiece this routine -EINVAL.
     */
    if (snd_device == SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES &&
        !platform_check_backends_match(SND_DEVICE_OUT_SPEAKER, SND_DEVICE_OUT_HEADPHONES)) {
        *num_devices = 2;
        new_snd_devices[0] = SND_DEVICE_OUT_SPEAKER;
        new_snd_devices[1] = SND_DEVICE_OUT_HEADPHONES;
        ret = 0;
    } else if (snd_device == SND_DEVICE_OUT_SPEAKER_AND_LINE &&
               !platform_check_backends_match(SND_DEVICE_OUT_SPEAKER, SND_DEVICE_OUT_LINE)) {
        *num_devices = 2;
        new_snd_devices[0] = SND_DEVICE_OUT_SPEAKER;
        new_snd_devices[1] = SND_DEVICE_OUT_LINE;
        ret = 0;
    }
    return ret;
}

snd_device_t platform_get_output_snd_device(void *platform, audio_devices_t devices)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    audio_mode_t mode = adev->mode;
    snd_device_t snd_device = SND_DEVICE_NONE;

    audio_channel_mask_t channel_mask = (adev->active_input == NULL) ?
                                AUDIO_CHANNEL_IN_MONO : adev->active_input->channel_mask;
    int channel_count = popcount(channel_mask);

    ALOGV("%s: enter: output devices(%#x)", __func__, devices);
    if (devices == AUDIO_DEVICE_NONE ||
        devices & AUDIO_DEVICE_BIT_IN) {
        ALOGV("%s: Invalid output devices (%#x)", __func__, devices);
        goto exit;
    }

    if (popcount(devices) == 2) {
        if (devices == (AUDIO_DEVICE_OUT_WIRED_HEADPHONE |
                        AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES;
        } else if (devices == (AUDIO_DEVICE_OUT_LINE |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
                snd_device = SND_DEVICE_OUT_SPEAKER_AND_LINE;
        } else if (devices == (AUDIO_DEVICE_OUT_WIRED_HEADSET |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES;
        } else if (devices == (AUDIO_DEVICE_OUT_AUX_DIGITAL |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_HDMI;
        } else if (devices == (AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET |
                               AUDIO_DEVICE_OUT_SPEAKER)) {
            snd_device = SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET;
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

    if (mode == AUDIO_MODE_IN_CALL || audio_extn_hfp_is_active(adev)) {
        if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
            devices & AUDIO_DEVICE_OUT_WIRED_HEADSET ||
            devices & AUDIO_DEVICE_OUT_LINE) {
            if (adev->voice.tty_mode != TTY_MODE_OFF) {
                switch (adev->voice.tty_mode) {
                case TTY_MODE_FULL:
                    snd_device = SND_DEVICE_OUT_VOICE_TTY_FULL_HEADPHONES;
                    break;
                case TTY_MODE_VCO:
                    snd_device = SND_DEVICE_OUT_VOICE_TTY_VCO_HEADPHONES;
                    break;
                case TTY_MODE_HCO:
                    snd_device = SND_DEVICE_OUT_VOICE_TTY_HCO_HANDSET;
                    break;
                default:
                    ALOGE("%s: Invalid TTY mode (%#x)",
                          __func__, adev->voice.tty_mode);
                }
            } else if (devices & AUDIO_DEVICE_OUT_LINE) {
                snd_device = SND_DEVICE_OUT_VOICE_LINE;
            } else {
                snd_device = SND_DEVICE_OUT_VOICE_HEADPHONES;
            }
        } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            if (adev->bt_wb_speech_enabled)
                snd_device = SND_DEVICE_OUT_BT_SCO_WB;
            else
                snd_device = SND_DEVICE_OUT_BT_SCO;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            if (audio_extn_hfp_is_active(adev))
                snd_device = SND_DEVICE_OUT_VOICE_SPEAKER_HFP;
            else
                snd_device = SND_DEVICE_OUT_VOICE_SPEAKER;
        } else if (devices & AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET ||
                   devices & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET) {
            snd_device = SND_DEVICE_OUT_USB_HEADSET;
        } else if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
                snd_device = SND_DEVICE_OUT_VOICE_HANDSET;
        } else if (devices & AUDIO_DEVICE_OUT_TELEPHONY_TX)
            snd_device = SND_DEVICE_OUT_VOICE_TX;

        if (snd_device != SND_DEVICE_NONE) {
            goto exit;
        }
    }

    if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
        devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
                snd_device = SND_DEVICE_OUT_HEADPHONES;
    } else if (devices & AUDIO_DEVICE_OUT_LINE) {
        snd_device = SND_DEVICE_OUT_LINE;
    } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
        if (my_data->speaker_lr_swap)
            snd_device = SND_DEVICE_OUT_SPEAKER_REVERSE;
        else
            snd_device = SND_DEVICE_OUT_SPEAKER;
    } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
        if (adev->bt_wb_speech_enabled)
            snd_device = SND_DEVICE_OUT_BT_SCO_WB;
        else
            snd_device = SND_DEVICE_OUT_BT_SCO;
    } else if (devices & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
        snd_device = SND_DEVICE_OUT_HDMI ;
    } else if (devices & AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET ||
               devices & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET) {
        snd_device = SND_DEVICE_OUT_USB_HEADSET;
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
    int channel_count = popcount(channel_mask);

    ALOGV("%s: enter: out_device(%#x) in_device(%#x)",
          __func__, out_device, in_device);

    if ((out_device != AUDIO_DEVICE_NONE) && ((mode == AUDIO_MODE_IN_CALL) ||
        audio_extn_hfp_is_active(adev))) {
        if (adev->voice.tty_mode != TTY_MODE_OFF) {
            if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
                out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET ||
                out_device & AUDIO_DEVICE_OUT_LINE) {
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
                    ALOGE("%s: Invalid TTY mode (%#x)",
                          __func__, adev->voice.tty_mode);
                }
                goto exit;
            }
        }
        if (out_device & AUDIO_DEVICE_OUT_EARPIECE ||
            out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
            out_device & AUDIO_DEVICE_OUT_LINE) {
            if (my_data->fluence_type == FLUENCE_NONE ||
                my_data->fluence_in_voice_call == false) {
                snd_device = SND_DEVICE_IN_HANDSET_MIC;
                if (audio_extn_hfp_is_active(adev))
                    platform_set_echo_reference(adev, true, out_device);
            } else {
                snd_device = SND_DEVICE_IN_VOICE_DMIC;
                adev->acdb_settings |= DMIC_FLAG;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_VOICE_HEADSET_MIC;
            if (audio_extn_hfp_is_active(adev))
                platform_set_echo_reference(adev, true, out_device);
        } else if (out_device & AUDIO_DEVICE_OUT_ALL_SCO) {
            if (adev->bt_wb_speech_enabled) {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_SPEAKER) {
            if (my_data->fluence_type != FLUENCE_NONE &&
                my_data->fluence_in_voice_call &&
                my_data->fluence_in_spkr_mode) {
                    if(my_data->fluence_type & FLUENCE_QUAD_MIC) {
                       adev->acdb_settings |= QMIC_FLAG;
                       snd_device = SND_DEVICE_IN_VOICE_SPEAKER_QMIC;
                    } else {
                       adev->acdb_settings |= DMIC_FLAG;
                       if (my_data->fluence_mode == FLUENCE_BROADSIDE)
                           snd_device = SND_DEVICE_IN_VOICE_SPEAKER_DMIC_BROADSIDE;
                       else
                           snd_device = SND_DEVICE_IN_VOICE_SPEAKER_DMIC;
                    }
            } else {
                snd_device = SND_DEVICE_IN_VOICE_SPEAKER_MIC;
                if (audio_extn_hfp_is_active(adev)) {
                    snd_device = SND_DEVICE_IN_VOICE_SPEAKER_MIC_HFP;
                    platform_set_echo_reference(adev, true, out_device);
                } else {
                    snd_device = SND_DEVICE_IN_VOICE_SPEAKER_MIC;
                }
            }
        } else if (out_device & AUDIO_DEVICE_OUT_TELEPHONY_TX)
            snd_device = SND_DEVICE_IN_VOICE_RX;
    } else if (source == AUDIO_SOURCE_CAMCORDER) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC ||
            in_device & AUDIO_DEVICE_IN_BACK_MIC) {
            if (my_data->fluence_type & FLUENCE_DUAL_MIC &&
                channel_count == 2)
                snd_device = SND_DEVICE_IN_HANDSET_STEREO_DMIC;
            else
                snd_device = SND_DEVICE_IN_CAMCORDER_MIC;
        }
    } else if (source == AUDIO_SOURCE_VOICE_RECOGNITION) {
        if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
            if (channel_count == 2) {
                snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_STEREO;
                adev->acdb_settings |= DMIC_FLAG;
            } else if (adev->active_input->enable_ns)
                snd_device = SND_DEVICE_IN_VOICE_REC_MIC_NS;
            else if (my_data->fluence_type != FLUENCE_NONE &&
                     my_data->fluence_in_voice_rec) {
                snd_device = SND_DEVICE_IN_VOICE_REC_DMIC_FLUENCE;
                adev->acdb_settings |= DMIC_FLAG;
            } else {
                snd_device = SND_DEVICE_IN_VOICE_REC_MIC;
            }
        }
    } else if ((source == AUDIO_SOURCE_VOICE_COMMUNICATION) ||
              (mode == AUDIO_MODE_IN_COMMUNICATION)) {
        if (out_device & AUDIO_DEVICE_OUT_SPEAKER)
            in_device = AUDIO_DEVICE_IN_BACK_MIC;
        if (adev->active_input) {
            if (my_data->fluence_type != FLUENCE_NONE &&
                adev->active_input->enable_aec &&
                adev->active_input->enable_ns) {
                if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
                    if (my_data->fluence_in_spkr_mode) {
                        if (my_data->fluence_type & FLUENCE_QUAD_MIC) {
                            snd_device = SND_DEVICE_IN_SPEAKER_QMIC_AEC_NS;
                        } else if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                            if (my_data->fluence_mode == FLUENCE_BROADSIDE)
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS_BROADSIDE;
                            else
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_AEC_NS;
                        }
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_SPEAKER_MIC_AEC_NS;
                } else if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
                    if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                        snd_device = SND_DEVICE_IN_HANDSET_DMIC_AEC_NS;
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_HANDSET_MIC_AEC_NS;
                } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
                    snd_device = SND_DEVICE_IN_HEADSET_MIC_FLUENCE;
                }
                platform_set_echo_reference(adev, true, out_device);
            } else if (my_data->fluence_type != FLUENCE_NONE &&
                       adev->active_input->enable_aec) {
                if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
                    if (my_data->fluence_in_spkr_mode) {
                        if (my_data->fluence_type & FLUENCE_QUAD_MIC) {
                            snd_device = SND_DEVICE_IN_SPEAKER_QMIC_AEC;
                        } else if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                            if (my_data->fluence_mode == FLUENCE_BROADSIDE)
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_AEC_BROADSIDE;
                            else
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_AEC;
                        }
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_SPEAKER_MIC_AEC;
                } else if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
                    if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                        snd_device = SND_DEVICE_IN_HANDSET_DMIC_AEC;
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_HANDSET_MIC_AEC;
                } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
                    snd_device = SND_DEVICE_IN_HEADSET_MIC_FLUENCE;
                }
                platform_set_echo_reference(adev, true, out_device);
            } else if (my_data->fluence_type != FLUENCE_NONE &&
                       adev->active_input->enable_ns) {
                if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
                    if (my_data->fluence_in_spkr_mode) {
                        if (my_data->fluence_type & FLUENCE_QUAD_MIC) {
                            snd_device = SND_DEVICE_IN_SPEAKER_QMIC_NS;
                        } else if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                            if (my_data->fluence_mode == FLUENCE_BROADSIDE)
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_NS_BROADSIDE;
                            else
                                snd_device = SND_DEVICE_IN_SPEAKER_DMIC_NS;
                        }
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_SPEAKER_MIC_NS;
                } else if (in_device & AUDIO_DEVICE_IN_BUILTIN_MIC) {
                    if (my_data->fluence_type & FLUENCE_DUAL_MIC) {
                        snd_device = SND_DEVICE_IN_HANDSET_DMIC_NS;
                        adev->acdb_settings |= DMIC_FLAG;
                    } else
                        snd_device = SND_DEVICE_IN_HANDSET_MIC_NS;
                } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
                    snd_device = SND_DEVICE_IN_HEADSET_MIC_FLUENCE;
                }
                platform_set_echo_reference(adev, false, out_device);
            } else
                platform_set_echo_reference(adev, false, out_device);
        }
    } else if (source == AUDIO_SOURCE_FM_TUNER) {
        snd_device = SND_DEVICE_IN_CAPTURE_FM;
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
            if (my_data->fluence_type & (FLUENCE_DUAL_MIC | FLUENCE_QUAD_MIC) &&
                    channel_count == 2)
                snd_device = SND_DEVICE_IN_HANDSET_STEREO_DMIC;
            else
                snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_BACK_MIC) {
            snd_device = SND_DEVICE_IN_SPEAKER_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_WIRED_HEADSET) {
            snd_device = SND_DEVICE_IN_HEADSET_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET) {
            if (adev->bt_wb_speech_enabled) {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (in_device & AUDIO_DEVICE_IN_AUX_DIGITAL) {
            snd_device = SND_DEVICE_IN_HDMI_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET ||
                   in_device & AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET) {
            snd_device = SND_DEVICE_IN_USB_HEADSET_MIC;
        } else if (in_device & AUDIO_DEVICE_IN_FM_TUNER) {
            snd_device = SND_DEVICE_IN_CAPTURE_FM;
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
            if (channel_count > 1)
                snd_device = SND_DEVICE_IN_SPEAKER_STEREO_DMIC;
            else
                snd_device = SND_DEVICE_IN_SPEAKER_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE ||
                       out_device & AUDIO_DEVICE_OUT_LINE) {
            snd_device = SND_DEVICE_IN_HANDSET_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET) {
            if (adev->bt_wb_speech_enabled) {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_WB;
            } else {
                if (adev->bluetooth_nrec)
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC_NREC;
                else
                    snd_device = SND_DEVICE_IN_BT_SCO_MIC;
            }
        } else if (out_device & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
            snd_device = SND_DEVICE_IN_HDMI_MIC;
        } else if (out_device & AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET ||
                   out_device & AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET) {
            snd_device = SND_DEVICE_IN_USB_HEADSET_MIC;
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

int platform_edid_get_max_channels(void *platform)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    char block[MAX_SAD_BLOCKS * SAD_BLOCK_SIZE];
    char *sad = block;
    int num_audio_blocks;
    int channel_count;
    int max_channels = 0;
    int i, ret, count;

    struct mixer_ctl *ctl;

    ctl = mixer_get_ctl_by_name(adev->mixer, AUDIO_DATA_BLOCK_MIXER_CTL);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, AUDIO_DATA_BLOCK_MIXER_CTL);
        return 0;
    }

    mixer_ctl_update(ctl);

    count = mixer_ctl_get_num_values(ctl);

    /* Read SAD blocks, clamping the maximum size for safety */
    if (count > (int)sizeof(block))
        count = (int)sizeof(block);

    ret = mixer_ctl_get_array(ctl, block, count);
    if (ret != 0) {
        ALOGE("%s: mixer_ctl_get_array() failed to get EDID info", __func__);
        return 0;
    }

    /* Calculate the number of SAD blocks */
    num_audio_blocks = count / SAD_BLOCK_SIZE;

    for (i = 0; i < num_audio_blocks; i++) {
        /* Only consider LPCM blocks */
        if ((sad[0] >> 3) != EDID_FORMAT_LPCM) {
            sad += 3;
            continue;
        }

        channel_count = (sad[0] & 0x7) + 1;
        if (channel_count > max_channels)
            max_channels = channel_count;

        /* Advance to next block */
        sad += 3;
    }

    return max_channels;
}

int platform_set_incall_recording_session_id(void *platform,
                                             uint32_t session_id,
                                             int rec_mode __unused)
{
    int ret = 0;
    struct platform_data *my_data = (struct platform_data *)platform;
    struct audio_device *adev = my_data->adev;
    struct mixer_ctl *ctl;
    const char *mixer_ctl_name = "Voc VSID";
    int num_ctl_values;
    int i;

    ctl = mixer_get_ctl_by_name(adev->mixer, mixer_ctl_name);
    if (!ctl) {
        ALOGE("%s: Could not get ctl for mixer cmd - %s",
              __func__, mixer_ctl_name);
        ret = -EINVAL;
    } else {
        num_ctl_values = mixer_ctl_get_num_values(ctl);
        for (i = 0; i < num_ctl_values; i++) {
            if (mixer_ctl_set_value(ctl, i, session_id)) {
                ALOGV("Error: invalid session_id: %x", session_id);
                ret = -EINVAL;
                break;
            }
        }
    }
    return ret;
}

int platform_stop_incall_recording_usecase(void *platform __unused)
{
    return 0;
}

int platform_start_incall_music_usecase(void *platform __unused)
{
    return 0;
}

int platform_stop_incall_music_usecase(void *platform __unused)
{
    return 0;
}

int platform_set_parameters(void *platform, struct str_parms *parms)
{
    struct platform_data *my_data = (struct platform_data *)platform;
    char value[128];
    char *kv_pairs = str_parms_to_str(parms);
    int ret = 0, err;

    if (kv_pairs == NULL) {
        ret = -EINVAL;
        ALOGE("%s: key-value pair is NULL",__func__);
        goto done;
    }

    ALOGV("%s: enter: %s", __func__, kv_pairs);

    err = str_parms_get_str(parms, PLATFORM_CONFIG_KEY_OPERATOR_INFO,
                            value, sizeof(value));
    if (err >= 0) {
        struct operator_info *info;
        char *str = value;
        char *name;

        str_parms_del(parms, PLATFORM_CONFIG_KEY_OPERATOR_INFO);
        info = (struct operator_info *)calloc(1, sizeof(struct operator_info));
        name = strtok(str, ";");
        info->name = strdup(name);
        info->mccmnc = strdup(str + strlen(name) + 1);

        list_add_tail(&operator_info_list, &info->list);
        ALOGV("%s: add operator[%s] mccmnc[%s]", __func__, info->name, info->mccmnc);
    }

    audio_extn_hfp_set_parameters(my_data->adev, parms);
done:
    ALOGV("%s: exit with code(%d)", __func__, ret);
    if (kv_pairs != NULL)
        free(kv_pairs);

    return ret;
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

int platform_set_snd_device_backend(snd_device_t device, const char *backend, const char * hw_interface)
{
    int ret = 0;

    if ((device < SND_DEVICE_MIN) || (device >= SND_DEVICE_MAX)) {
        ALOGE("%s: Invalid snd_device = %d",
            __func__, device);
        ret = -EINVAL;
        goto done;
    }

    ALOGV("%s: backend_tag_table[%s]: old = %s new = %s", __func__,
          platform_get_snd_device_name(device),
          backend_table[device] != NULL ? backend_table[device]: "null", backend);
    if (backend_table[device]) {
        free(backend_table[device]);
    }
    backend_table[device] = strdup(backend);

    if (hw_interface != NULL) {
        if (hw_interface_table[device])
            free(hw_interface_table[device]);
        ALOGV("%s: hw_interface_table[%d] = %s", __func__, device, hw_interface);
        hw_interface_table[device] = strdup(hw_interface);
    }
done:
    return ret;
}

int platform_set_usecase_pcm_id(audio_usecase_t usecase, int32_t type, int32_t pcm_id)
{
    int ret = 0;
    if ((usecase <= USECASE_INVALID) || (usecase >= AUDIO_USECASE_MAX)) {
        ALOGE("%s: invalid usecase case idx %d", __func__, usecase);
        ret = -EINVAL;
        goto done;
    }

    if ((type != 0) && (type != 1)) {
        ALOGE("%s: invalid usecase type", __func__);
        ret = -EINVAL;
    }
    pcm_device_table[usecase][type] = pcm_id;
done:
    return ret;
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

int platform_snd_card_update(void *platform __unused,
                             card_status_t status __unused)
{
    return -1;
}

int platform_send_audio_calibration_v2(void *platform __unused,
                                       struct audio_usecase *usecase __unused,
                                       int app_type __unused,
                                       int sample_rate __unused)
{
    return -ENOSYS;
}

void platform_check_and_update_copp_sample_rate(void* platform __unused,
                                               snd_device_t snd_device __unused,
                                                unsigned int stream_sr __unused,
                                                int* sample_rate __unused)
{

}

int platform_get_snd_device_backend_index(snd_device_t snd_device __unused)
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
