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
#define MAX_CID_CH_PARAM_ENTRY 3
#define PIX0_MASK (1 << PIX0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define PIX1_MASK (1 << PIX1)
#define RDI0_MASK (1 << RDI0)
#define RDI1_MASK (1 << RDI1)
#define RDI2_MASK (1 << RDI2)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_ispif_vc {
  VC0,
  VC1,
  VC2,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  VC3,
  VC_MAX
};
enum msm_ispif_cid {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID0,
  CID1,
  CID2,
  CID3,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID4,
  CID5,
  CID6,
  CID7,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID8,
  CID9,
  CID10,
  CID11,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID12,
  CID13,
  CID14,
  CID15,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CID_MAX
};
enum msm_ispif_csid {
  CSID0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CSID1,
  CSID2,
  CSID3,
  CSID_MAX
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum msm_ispif_pixel_odd_even {
  PIX_EVEN,
  PIX_ODD
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum msm_ispif_pixel_pack_mode {
  PACK_BYTE,
  PACK_PLAIN_PACK,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  PACK_NV_P8,
  PACK_NV_P16
};
struct msm_ispif_pack_cfg {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int pixel_swap_en;
  enum msm_ispif_pixel_odd_even even_odd_sel;
  enum msm_ispif_pixel_pack_mode pack_mode;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ispif_params_entry {
  enum msm_ispif_vfe_intf vfe_intf;
  enum msm_ispif_intftype intftype;
  int num_cids;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum msm_ispif_cid cids[MAX_CID_CH_PARAM_ENTRY];
  enum msm_ispif_csid csid;
  int crop_enable;
  uint16_t crop_start_pixel;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t crop_end_pixel;
};
struct msm_ispif_param_data_ext {
  uint32_t num;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_ispif_params_entry entries[MAX_PARAM_ENTRIES];
  struct msm_ispif_pack_cfg pack_cfg[CID_MAX];
};
struct msm_ispif_param_data {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t num;
  struct msm_ispif_params_entry entries[MAX_PARAM_ENTRIES];
};
struct msm_isp_info {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t max_resolution;
  uint32_t id;
  uint32_t ver;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_ispif_vfe_info {
  int num_vfe;
  struct msm_isp_info info[VFE_MAX];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum ispif_cfg_type_t {
  ISPIF_CLK_ENABLE,
  ISPIF_CLK_DISABLE,
  ISPIF_INIT,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_CFG,
  ISPIF_START_FRAME_BOUNDARY,
  ISPIF_RESTART_FRAME_BOUNDARY,
  ISPIF_STOP_FRAME_BOUNDARY,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_STOP_IMMEDIATELY,
  ISPIF_RELEASE,
  ISPIF_ENABLE_REG_DUMP,
  ISPIF_SET_VFE_INFO,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ISPIF_CFG2,
};
struct ispif_cfg_data {
  enum ispif_cfg_type_t cfg_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  union {
    int reg_dump;
    uint32_t csid_version;
    struct msm_ispif_vfe_info vfe_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    struct msm_ispif_param_data params;
  };
};
struct ispif_cfg_data_ext {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum ispif_cfg_type_t cfg_type;
  void * data;
  uint32_t size;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define ISPIF_RDI_PACK_MODE_SUPPORT 1
#define VIDIOC_MSM_ISPIF_CFG _IOWR('V', BASE_VIDIOC_PRIVATE, struct ispif_cfg_data)
#define VIDIOC_MSM_ISPIF_CFG_EXT _IOWR('V', BASE_VIDIOC_PRIVATE + 1, struct ispif_cfg_data_ext)
#endif
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */

