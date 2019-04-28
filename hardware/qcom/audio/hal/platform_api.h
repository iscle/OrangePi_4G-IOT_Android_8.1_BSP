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

#ifndef AUDIO_PLATFORM_API_H
#define AUDIO_PLATFORM_API_H

#include "voice.h"
#define MAX_VOLUME_CAL_STEPS 15
#define CODEC_BACKEND_DEFAULT_SAMPLE_RATE 48000
#define CODEC_BACKEND_DEFAULT_BIT_WIDTH 16
#define CODEC_BACKEND_DEFAULT_CHANNELS 2
#define CODEC_BACKEND_DEFAULT_TX_CHANNELS 1
#define SAMPLE_RATE_8000 8000
#define SAMPLE_RATE_11025 11025
#define sample_rate_multiple(sr, base) ((sr % base)== 0?true:false)

struct amp_db_and_gain_table {
    float amp;
    float db;
    uint32_t level;
};

enum card_status_t;
struct audio_usecase;
enum usecase_type_t;

void *platform_init(struct audio_device *adev);
void platform_deinit(void *platform);
const char *platform_get_snd_device_name(snd_device_t snd_device);
int platform_get_snd_device_name_extn(void *platform, snd_device_t snd_device,
                                      char *device_name);
void platform_add_backend_name(void *platform, char *mixer_path,
                                                    snd_device_t snd_device);
bool platform_send_gain_dep_cal(void *platform, int level);
int platform_get_pcm_device_id(audio_usecase_t usecase, int device_type);
int platform_get_snd_device_index(char *snd_device_index_name);
int platform_set_snd_device_acdb_id(snd_device_t snd_device, unsigned int acdb_id);
int platform_get_snd_device_acdb_id(snd_device_t snd_device);
int platform_send_audio_calibration(void *platform, snd_device_t snd_device);
int platform_send_audio_calibration_v2(void *platform, struct audio_usecase *usecase,
                                       int app_type, int sample_rate);
int platform_get_default_app_type_v2(void *platform, enum usecase_type_t type, int *app_type);
int platform_switch_voice_call_device_pre(void *platform);
int platform_switch_voice_call_enable_device_config(void *platform,
                                                    snd_device_t out_snd_device,
                                                    snd_device_t in_snd_device);
int platform_switch_voice_call_device_post(void *platform,
                                           snd_device_t out_snd_device,
                                           snd_device_t in_snd_device);
int platform_switch_voice_call_usecase_route_post(void *platform,
                                                  snd_device_t out_snd_device,
                                                  snd_device_t in_snd_device);
int platform_start_voice_call(void *platform, uint32_t vsid);
int platform_stop_voice_call(void *platform, uint32_t vsid);
int platform_set_voice_volume(void *platform, int volume);
void platform_set_speaker_gain_in_combo(struct audio_device *adev,
                                        snd_device_t snd_device,
                                        bool enable);
int platform_set_mic_mute(void *platform, bool state);
int platform_get_sample_rate(void *platform, uint32_t *rate);
int platform_set_device_mute(void *platform, bool state, char *dir);
snd_device_t platform_get_output_snd_device(void *platform, audio_devices_t devices);
snd_device_t platform_get_input_snd_device(void *platform, audio_devices_t out_device);
int platform_set_hdmi_channels(void *platform, int channel_count);
int platform_edid_get_max_channels(void *platform);
void platform_add_operator_specific_device(snd_device_t snd_device,
                                           const char *operator,
                                           const char *mixer_path,
                                           unsigned int acdb_id);
/* return true if adding entry success
   return false if adding entry fails */

bool platform_add_gain_level_mapping(struct amp_db_and_gain_table *tbl_entry);

/* return 0 if no custome mapping table found or when error detected
            use default mapping in this case
   return > 0 indicates number of entries in mapping table */

int platform_get_gain_level_mapping(struct amp_db_and_gain_table *mapping_tbl,
                                    int table_size);

/* returns the latency for a usecase in Us */
int64_t platform_render_latency(audio_usecase_t usecase);

int platform_set_incall_recording_session_id(void *platform,
                                             uint32_t session_id, int rec_mode);
int platform_stop_incall_recording_usecase(void *platform);
int platform_start_incall_music_usecase(void *platform);
int platform_stop_incall_music_usecase(void *platform);

int platform_set_snd_device_backend(snd_device_t snd_device, const char * backend,
                                    const char * hw_interface);

/* From platform_info.c */
int platform_info_init(const char *filename, void *);

typedef int (*set_parameters_fn)(void *platform, struct str_parms *parms);
int snd_card_info_init(const char *filename, void *, set_parameters_fn);

int platform_get_usecase_index(const char * usecase);
int platform_set_usecase_pcm_id(audio_usecase_t usecase, int32_t type, int32_t pcm_id);
void platform_set_echo_reference(struct audio_device *adev, bool enable, audio_devices_t out_device);
int platform_check_and_set_swap_lr_channels(struct audio_device *adev, bool swap_channels);
int platform_set_swap_channels(struct audio_device *adev, bool swap_channels);

int platform_can_split_snd_device(snd_device_t in_snd_device,
                                  int *num_devices,
                                  snd_device_t *out_snd_devices);

bool platform_check_backends_match(snd_device_t snd_device1, snd_device_t snd_device2);

int platform_set_parameters(void *platform, struct str_parms *parms);

bool platform_check_and_set_playback_backend_cfg(struct audio_device* adev,
                   struct audio_usecase *usecase, snd_device_t snd_device);

bool platform_check_and_set_capture_backend_cfg(struct audio_device* adev,
                   struct audio_usecase *usecase, snd_device_t snd_device);

int platform_snd_card_update(void *platform, enum card_status_t status);
void platform_check_and_update_copp_sample_rate(void *platform, snd_device_t snd_device,
     unsigned int stream_sr,int *sample_rate);
int platform_get_snd_device_backend_index(snd_device_t snd_device);
bool platform_supports_app_type_cfg();
int platform_get_app_type_v2(void *platform,
                             enum usecase_type_t type,
                             const char *mode,
                             int bw, int sr, int *app_type);
void platform_add_app_type(const char *uc_type,
                           const char *mode,
                           int bw, int app_type, int max_sr);
int platform_get_snd_device_backend_index(snd_device_t snd_device);
int platform_set_sidetone(struct audio_device *adev,
                          snd_device_t out_snd_device,
                          bool enable, char * str);
int platform_get_mmap_data_fd(void *platform, int dev, int dir,
                              int *fd, uint32_t *size);
#endif // AUDIO_PLATFORM_API_H
