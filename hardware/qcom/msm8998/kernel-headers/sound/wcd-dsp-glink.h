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
#ifndef _WCD_DSP_GLINK_H
#define _WCD_DSP_GLINK_H
#include <linux/types.h>
#define WDSP_CH_NAME_MAX_LEN 50
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum {
  WDSP_REG_PKT = 1,
  WDSP_CMD_PKT,
  WDSP_READY_PKT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define WDSP_READY_PKT WDSP_READY_PKT
struct wdsp_reg_pkt {
  __u8 no_of_channels;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 payload[0];
};
struct wdsp_cmd_pkt {
  char ch_name[WDSP_CH_NAME_MAX_LEN];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u32 payload_size;
  __u8 payload[0];
};
struct wdsp_write_pkt {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u8 pkt_type;
  __u8 payload[0];
};
struct wdsp_glink_ch_cfg {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char name[WDSP_CH_NAME_MAX_LEN];
  __u32 latency_in_us;
  __u32 no_of_intents;
  __u32 intents_size[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#endif

