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
#ifndef _UAPI__HDMI_HDCP_MGR_H
#define _UAPI__MSM_HDMI_HDCP_MGR_H
enum DS_TYPE {
  DS_UNKNOWN,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  DS_RECEIVER,
  DS_REPEATER,
};
enum {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSG_ID_IDX,
  RET_CODE_IDX,
  HEADER_LEN,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum RET_CODE {
  HDCP_NOT_AUTHED,
  HDCP_AUTHED,
  HDCP_DISABLE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum MSG_ID {
  DOWN_CHECK_TOPOLOGY,
  UP_REQUEST_TOPOLOGY,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  UP_SEND_TOPOLOGY,
  DOWN_REQUEST_TOPOLOGY,
  MSG_NUM,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum SOURCE_ID {
  HDCP_V1_TX,
  HDCP_V1_RX,
  HDCP_V2_RX,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  HDCP_V2_TX,
  SRC_NUM,
};
struct HDCP_V2V1_MSG_TOPOLOGY {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t ds_type;
  uint8_t bksv[5];
  uint8_t dev_count;
  uint8_t depth;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t ksv_list[5 * 127];
  uint32_t max_cascade_exceeded;
  uint32_t max_dev_exceeded;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif

