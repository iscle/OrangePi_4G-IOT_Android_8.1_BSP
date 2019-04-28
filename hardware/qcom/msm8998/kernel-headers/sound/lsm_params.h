/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef _LSM_PARAMS_H__
#define _LSM_PARAMS_H__
#define LSM_POLLING_ENABLE_SUPPORT
#define LSM_EVENT_TIMESTAMP_MODE_SUPPORT
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <linux/types.h>
#include <sound/asound.h>
#define SNDRV_LSM_VERSION SNDRV_PROTOCOL_VERSION(0, 1, 0)
#define LSM_OUT_FORMAT_PCM (0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define LSM_OUT_FORMAT_ADPCM (1 << 0)
#define LSM_OUT_DATA_RAW (0)
#define LSM_OUT_DATA_PACKED (1)
#define LSM_OUT_DATA_EVENTS_DISABLED (0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define LSM_OUT_DATA_EVENTS_ENABLED (1)
#define LSM_OUT_TRANSFER_MODE_RT (0)
#define LSM_OUT_TRANSFER_MODE_FTRT (1)
#define LSM_ENDPOINT_DETECT_THRESHOLD (0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define LSM_OPERATION_MODE (1)
#define LSM_GAIN (2)
#define LSM_MIN_CONFIDENCE_LEVELS (3)
#define LSM_REG_SND_MODEL (4)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define LSM_DEREG_SND_MODEL (5)
#define LSM_CUSTOM_PARAMS (6)
#define LSM_POLLING_ENABLE (7)
#define LSM_PARAMS_MAX (LSM_POLLING_ENABLE + 1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define LSM_EVENT_NON_TIME_STAMP_MODE (0)
#define LSM_EVENT_TIME_STAMP_MODE (1)
enum lsm_app_id {
  LSM_VOICE_WAKEUP_APP_ID = 1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  LSM_VOICE_WAKEUP_APP_ID_V2 = 2,
};
enum lsm_detection_mode {
  LSM_MODE_KEYWORD_ONLY_DETECTION = 1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  LSM_MODE_USER_KEYWORD_DETECTION
};
enum lsm_vw_status {
  LSM_VOICE_WAKEUP_STATUS_RUNNING = 1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  LSM_VOICE_WAKEUP_STATUS_DETECTED,
  LSM_VOICE_WAKEUP_STATUS_END_SPEECH,
  LSM_VOICE_WAKEUP_STATUS_REJECTED
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct snd_lsm_ep_det_thres {
  __u32 epd_begin;
  __u32 epd_end;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct snd_lsm_detect_mode {
  enum lsm_detection_mode mode;
  bool detect_failure;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct snd_lsm_gain {
  __u16 gain;
};
struct snd_lsm_poll_enable {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  bool poll_en;
};
struct snd_lsm_sound_model_v2 {
  __u8 * data;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 * confidence_level;
  __u32 data_size;
  enum lsm_detection_mode detection_mode;
  __u8 num_confidence_levels;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  bool detect_failure;
};
struct snd_lsm_session_data {
  enum lsm_app_id app_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct snd_lsm_event_status {
  __u16 status;
  __u16 payload_size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 payload[0];
};
struct snd_lsm_event_status_v3 {
  __u32 timestamp_lsw;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u32 timestamp_msw;
  __u16 status;
  __u16 payload_size;
  __u8 payload[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct snd_lsm_detection_params {
  __u8 * conf_level;
  enum lsm_detection_mode detect_mode;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 num_confidence_levels;
  bool detect_failure;
  bool poll_enable;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct lsm_params_info {
  __u32 module_id;
  __u32 param_id;
  __u32 param_size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 * param_data;
  uint32_t param_type;
};
struct snd_lsm_module_params {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 * params;
  __u32 num_params;
  __u32 data_size;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct snd_lsm_output_format_cfg {
  __u8 format;
  __u8 packing;
  __u8 events;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 mode;
};
#define SNDRV_LSM_DEREG_SND_MODEL _IOW('U', 0x01, int)
#define SNDRV_LSM_EVENT_STATUS _IOW('U', 0x02, struct snd_lsm_event_status)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SNDRV_LSM_ABORT_EVENT _IOW('U', 0x03, int)
#define SNDRV_LSM_START _IOW('U', 0x04, int)
#define SNDRV_LSM_STOP _IOW('U', 0x05, int)
#define SNDRV_LSM_SET_SESSION_DATA _IOW('U', 0x06, struct snd_lsm_session_data)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SNDRV_LSM_REG_SND_MODEL_V2 _IOW('U', 0x07, struct snd_lsm_sound_model_v2)
#define SNDRV_LSM_LAB_CONTROL _IOW('U', 0x08, uint32_t)
#define SNDRV_LSM_STOP_LAB _IO('U', 0x09)
#define SNDRV_LSM_SET_PARAMS _IOW('U', 0x0A, struct snd_lsm_detection_params)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SNDRV_LSM_SET_MODULE_PARAMS _IOW('U', 0x0B, struct snd_lsm_module_params)
#define SNDRV_LSM_OUT_FORMAT_CFG _IOW('U', 0x0C, struct snd_lsm_output_format_cfg)
#define SNDRV_LSM_SET_PORT _IO('U', 0x0D)
#define SNDRV_LSM_SET_FWK_MODE_CONFIG _IOW('U', 0x0E, uint32_t)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SNDRV_LSM_EVENT_STATUS_V3 _IOW('U', 0x0F, struct snd_lsm_event_status_v3)
#endif

