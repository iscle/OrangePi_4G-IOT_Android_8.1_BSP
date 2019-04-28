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

#ifndef AUDIO_EXTN_H
#define AUDIO_EXTN_H

#include <cutils/str_parms.h>

#define HW_INFO_ARRAY_MAX_SIZE 32

struct snd_card_split {
    char device[HW_INFO_ARRAY_MAX_SIZE];
    char snd_card[HW_INFO_ARRAY_MAX_SIZE];
    char form_factor[HW_INFO_ARRAY_MAX_SIZE];
};

void *audio_extn_extspk_init(struct audio_device *adev);
void audio_extn_extspk_deinit(void *extn);
void audio_extn_extspk_update(void* extn);
void audio_extn_extspk_set_mode(void* extn, audio_mode_t mode);
void audio_extn_extspk_set_voice_vol(void* extn, float vol);
struct snd_card_split *audio_extn_get_snd_card_split();
void audio_extn_set_snd_card_split(const char* in_snd_card_name);

#ifndef SPKR_PROT_ENABLED
#define audio_extn_spkr_prot_init(adev)       (0)
#define audio_extn_spkr_prot_start_processing(snd_device)    (-EINVAL)
#define audio_extn_spkr_prot_calib_cancel(adev) (0)
#define audio_extn_spkr_prot_stop_processing(snd_device)     (0)
#define audio_extn_spkr_prot_is_enabled() (false)
#define audio_extn_spkr_prot_get_acdb_id(snd_device)         (-EINVAL)
#define audio_extn_get_spkr_prot_snd_device(snd_device) (snd_device)
#else
void audio_extn_spkr_prot_init(void *adev);
int audio_extn_spkr_prot_start_processing(snd_device_t snd_device);
void audio_extn_spkr_prot_stop_processing(snd_device_t snd_device);
bool audio_extn_spkr_prot_is_enabled();
int audio_extn_spkr_prot_get_acdb_id(snd_device_t snd_device);
int audio_extn_get_spkr_prot_snd_device(snd_device_t snd_device);
void audio_extn_spkr_prot_calib_cancel(void *adev);
#endif

#ifndef HFP_ENABLED
#define audio_extn_hfp_is_active(adev)                  (0)
#define audio_extn_hfp_get_usecase()                    (-1)
#define audio_extn_hfp_set_parameters(adev, params)     (0)
#define audio_extn_hfp_set_mic_mute(adev, state)        (0)

#else
bool audio_extn_hfp_is_active(struct audio_device *adev);

audio_usecase_t audio_extn_hfp_get_usecase();

void audio_extn_hfp_set_parameters(struct audio_device *adev,
                                    struct str_parms *parms);
int audio_extn_hfp_set_mic_mute(struct audio_device *adev, bool state);

#endif

#ifndef USB_TUNNEL_ENABLED
#define audio_extn_usb_init(adev)                                      (0)
#define audio_extn_usb_deinit()                                        (0)
#define audio_extn_usb_add_device(device, card)                        (0)
#define audio_extn_usb_remove_device(device, card)                     (0)
#define audio_extn_usb_is_config_supported(bit_width, sample_rate, ch, pb) (false)
#define audio_extn_usb_enable_sidetone(device, enable)                 (0)
#define audio_extn_usb_set_sidetone_gain(parms, value, len)            (0)
#define audio_extn_usb_is_capture_supported()                          (false)
#define audio_extn_usb_get_max_channels(dir)                           (0)
#define audio_extn_usb_get_max_bit_width(dir)                          (0)
#define audio_extn_usb_sup_sample_rates(t, s, l)                       (0)
#define audio_extn_usb_alive(adev)                                     (false)
#else
void audio_extn_usb_init(void *adev);
void audio_extn_usb_deinit();
void audio_extn_usb_add_device(audio_devices_t device, int card);
void audio_extn_usb_remove_device(audio_devices_t device, int card);
bool audio_extn_usb_is_config_supported(unsigned int *bit_width,
                                        unsigned int *sample_rate,
                                        unsigned int *ch,
                                        bool is_playback);
int audio_extn_usb_enable_sidetone(int device, bool enable);
int audio_extn_usb_set_sidetone_gain(struct str_parms *parms,
                                     char *value, int len);
bool audio_extn_usb_is_capture_supported();
int audio_extn_usb_get_max_channels(bool is_playback);
int audio_extn_usb_get_max_bit_width(bool is_playback);
int audio_extn_usb_sup_sample_rates(bool is_playback, uint32_t *sr, uint32_t l);
bool audio_extn_usb_alive(int card);
#endif


#ifndef SOUND_TRIGGER_ENABLED
#define audio_extn_sound_trigger_init(adev)                            (0)
#define audio_extn_sound_trigger_deinit(adev)                          (0)
#define audio_extn_sound_trigger_update_device_status(snd_dev, event)  (0)
#define audio_extn_sound_trigger_set_parameters(adev, parms)           (0)
#define audio_extn_sound_trigger_check_and_get_session(in)             (0)
#define audio_extn_sound_trigger_stop_lab(in)                          (0)
#define audio_extn_sound_trigger_read(in, buffer, bytes)               (0)

#else

enum st_event_type {
    ST_EVENT_SND_DEVICE_FREE,
    ST_EVENT_SND_DEVICE_BUSY,
    ST_EVENT_STREAM_FREE,
    ST_EVENT_STREAM_BUSY
};
typedef enum st_event_type st_event_type_t;

int audio_extn_sound_trigger_init(struct audio_device *adev);
void audio_extn_sound_trigger_deinit(struct audio_device *adev);
void audio_extn_sound_trigger_update_device_status(snd_device_t snd_device,
                                     st_event_type_t event);
void audio_extn_sound_trigger_set_parameters(struct audio_device *adev,
                                             struct str_parms *parms);
void audio_extn_sound_trigger_check_and_get_session(struct stream_in *in);
void audio_extn_sound_trigger_stop_lab(struct stream_in *in);
int audio_extn_sound_trigger_read(struct stream_in *in, void *buffer,
                                  size_t bytes);
#endif

#ifndef DSM_FEEDBACK_ENABLED
#define audio_extn_dsm_feedback_enable(adev, snd_device, benable)                (0)
#else
void audio_extn_dsm_feedback_enable(struct audio_device *adev,
                         snd_device_t snd_device,
                         bool benable);
#endif

void audio_extn_utils_send_default_app_type_cfg(void *platform, struct mixer *mixer);
int audio_extn_utils_send_app_type_cfg(struct audio_device *adev,
                                       struct audio_usecase *usecase);
void audio_extn_utils_send_audio_calibration(struct audio_device *adev,
                                             struct audio_usecase *usecase);
int audio_extn_utils_send_app_type_gain(struct audio_device *adev,
                                        int app_type,
                                        int *gain);
#ifndef HWDEP_CAL_ENABLED
#define  audio_extn_hwdep_cal_send(snd_card, acdb_handle) (0)
#else
void audio_extn_hwdep_cal_send(int snd_card, void *acdb_handle);
#endif

#ifndef KPI_OPTIMIZE_ENABLED
#define audio_extn_perf_lock_init() (0)
#define audio_extn_perf_lock_acquire() (0)
#define audio_extn_perf_lock_release() (0)
#else
int audio_extn_perf_lock_init(void);
void audio_extn_perf_lock_acquire(void);
void audio_extn_perf_lock_release(void);
#endif /* KPI_OPTIMIZE_ENABLED */

#ifndef HW_VARIANTS_ENABLED
#define hw_info_init(snd_card_name)                  (0)
#define hw_info_deinit(hw_info)                      (0)
#define hw_info_append_hw_type(hw_info,\
        snd_device, device_name)                     (0)
#else
void *hw_info_init(const char *snd_card_name);
void hw_info_deinit(void *hw_info);
void hw_info_append_hw_type(void *hw_info, snd_device_t snd_device,
                             char *device_name);
#endif /* HW_VARIANTS_ENABLED */

typedef void (* snd_mon_cb)(void * stream, struct str_parms * parms);
#ifndef SND_MONITOR_ENABLED
#define audio_extn_snd_mon_init()           (0)
#define audio_extn_snd_mon_deinit()         (0)
#define audio_extn_snd_mon_register_listener(stream, cb) (0)
#define audio_extn_snd_mon_unregister_listener(stream) (0)
#else
int audio_extn_snd_mon_init();
int audio_extn_snd_mon_deinit();
int audio_extn_snd_mon_register_listener(void *stream, snd_mon_cb cb);
int audio_extn_snd_mon_unregister_listener(void *stream);
#endif

bool audio_extn_utils_resolve_config_file(char[]);
int audio_extn_utils_get_platform_info(const char* snd_card_name,
                                       char* platform_info_file);
int audio_extn_utils_get_snd_card_num();
#endif /* AUDIO_EXTN_H */
