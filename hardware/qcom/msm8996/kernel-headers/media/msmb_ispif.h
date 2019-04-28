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
#ifndef UAPI_MSMB_ISPIF_H
#define UAPI_MSMB_ISPIF_H
#include <linux/types.h>
#include <linux/ioctl.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <linux/videodev2.h>
#define CSID_VERSION_V20 0x02000011
#define CSID_VERSION_V22 0x02001000
#define CSID_VERSION_V30 0x30000000
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CSID_VERSION_V3 0x30000000
enum msm_ispif_vfe_intf {
  VFE0,
  VFE1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  VFE_MAX
};
#define VFE0_MASK (1 << VFE0)
#define VFE1_MASK (1 << VFE1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_ispif_intftype {
  PIX0,
  RDI0,
  PIX1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  RDI1,
  RDI2,
  INTF_MAX
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MAX_PARAM_ENTRIES (INTF_MAX * 2)
#define MAX_CID_CH 8
#define PIX0_MASK (1 << PIX0)
#define PIX1_MASK (1 << PIX1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define RDI0_MASK (1 << RDI0)
#define RDI1_MASK (1 << RDI1)
#define RDI2_MASK (1 << RDI2)
enum msm_ispif_vc {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  VC0,
  VC1,
  VC2,
  VC3,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  VC_MAX
};
enum msm_ispif_cid {
  CID0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID1,
  CID2,
  CID3,
  CID4,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID5,
  CID6,
  CID7,
  CID8,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID9,
  CID10,
  CID11,
  CID12,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID13,
  CID14,
  CID15,
  CID_MAX
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum msm_ispif_csid {
  CSID0,
  CSID1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CSID2,
  CSID3,
  CSID_MAX
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ispif_params_entry {
  enum msm_ispif_vfe_intf vfe_intf;
  enum msm_ispif_intftype intftype;
  int num_cids;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum msm_ispif_cid cids[3];
  enum msm_ispif_csid csid;
  int crop_enable;
  uint16_t crop_start_pixel;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t crop_end_pixel;
};
struct msm_ispif_param_data {
  uint32_t num;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_ispif_params_entry entries[MAX_PARAM_ENTRIES];
};
struct msm_isp_info {
  uint32_t max_resolution;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t id;
  uint32_t ver;
};
struct msm_ispif_vfe_info {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int num_vfe;
  struct msm_isp_info info[VFE_MAX];
};
enum ispif_cfg_type_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_CLK_ENABLE,
  ISPIF_CLK_DISABLE,
  ISPIF_INIT,
  ISPIF_CFG,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_START_FRAME_BOUNDARY,
  ISPIF_RECONFIG,
  ISPIF_STOP_FRAME_BOUNDARY,
  ISPIF_STOP_IMMEDIATELY,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_RELEASE,
  ISPIF_ENABLE_REG_DUMP,
  ISPIF_SET_VFE_INFO,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct ispif_cfg_data {
  enum ispif_cfg_type_t cfg_type;
  union {
    int reg_dump;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    uint32_t csid_version;
    struct msm_ispif_vfe_info vfe_info;
    struct msm_ispif_param_data params;
  };
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define VIDIOC_MSM_ISPIF_CFG _IOWR('V', BASE_VIDIOC_PRIVATE, struct ispif_cfg_data)
#endif

