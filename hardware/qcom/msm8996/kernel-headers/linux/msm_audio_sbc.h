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
#ifndef _UAPI_MSM_AUDIO_SBC_H
#define _UAPI_MSM_AUDIO_SBC_H
#include <linux/msm_audio.h>
#define AUDIO_SET_SBC_ENC_CONFIG _IOW(AUDIO_IOCTL_MAGIC, (AUDIO_MAX_COMMON_IOCTL_NUM + 0), struct msm_audio_sbc_enc_config)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define AUDIO_GET_SBC_ENC_CONFIG _IOR(AUDIO_IOCTL_MAGIC, (AUDIO_MAX_COMMON_IOCTL_NUM + 1), struct msm_audio_sbc_enc_config)
#define AUDIO_SBC_BA_LOUDNESS 0x0
#define AUDIO_SBC_BA_SNR 0x1
#define AUDIO_SBC_MODE_MONO 0x0
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define AUDIO_SBC_MODE_DUAL 0x1
#define AUDIO_SBC_MODE_STEREO 0x2
#define AUDIO_SBC_MODE_JSTEREO 0x3
#define AUDIO_SBC_BANDS_8 0x1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define AUDIO_SBC_BLOCKS_4 0x0
#define AUDIO_SBC_BLOCKS_8 0x1
#define AUDIO_SBC_BLOCKS_12 0x2
#define AUDIO_SBC_BLOCKS_16 0x3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_audio_sbc_enc_config {
  uint32_t channels;
  uint32_t sample_rate;
  uint32_t bit_allocation;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t number_of_subbands;
  uint32_t number_of_blocks;
  uint32_t bit_rate;
  uint32_t mode;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#endif

