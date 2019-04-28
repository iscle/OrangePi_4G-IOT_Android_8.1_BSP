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
#ifndef _MSM_MDP_EXT_H_
#define _MSM_MDP_EXT_H_
#include <linux/msm_mdp.h>
#define MDP_IOCTL_MAGIC 'S'
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSMFB_ATOMIC_COMMIT _IOWR(MDP_IOCTL_MAGIC, 128, void *)
#define MSMFB_ASYNC_POSITION_UPDATE _IOWR(MDP_IOCTL_MAGIC, 129, struct mdp_position_update)
#define MSMFB_MDP_SET_CFG _IOW(MDP_IOCTL_MAGIC, 130, struct mdp_set_cfg)
#define MSMFB_MDP_SET_PANEL_PPM _IOW(MDP_IOCTL_MAGIC, 131, int)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#ifdef __LP64
#define MDP_LAYER_COMMIT_V1_PAD 2
#else
#define MDP_LAYER_COMMIT_V1_PAD 3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif
#define MDP_LAYER_FLIP_LR 0x1
#define MDP_LAYER_FLIP_UD 0x2
#define MDP_LAYER_ENABLE_PIXEL_EXT 0x4
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_LAYER_FORGROUND 0x8
#define MDP_LAYER_SECURE_SESSION 0x10
#define MDP_LAYER_SOLID_FILL 0x20
#define MDP_LAYER_DEINTERLACE 0x40
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_LAYER_BWC 0x80
#define MDP_LAYER_ASYNC 0x100
#define MDP_LAYER_PP 0x200
#define MDP_LAYER_SECURE_DISPLAY_SESSION 0x400
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_LAYER_ENABLE_QSEED3_SCALE 0x800
#define MDP_LAYER_MULTIRECT_ENABLE 0x1000
#define MDP_LAYER_MULTIRECT_PARALLEL_MODE 0x2000
#define MDP_LAYER_SECURE_CAMERA_SESSION 0x4000
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_DESTSCALER_ENABLE 0x1
#define MDP_DESTSCALER_SCALE_UPDATE 0x2
#define MDP_DESTSCALER_ENHANCER_UPDATE 0x4
#define MDP_DESTSCALER_ROI_ENABLE 0x8
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_VALIDATE_LAYER 0x01
#define MDP_COMMIT_WAIT_FOR_FINISH 0x02
#define MDP_COMMIT_SYNC_FENCE_WAIT 0x04
#define MDP_COMMIT_AVR_EN 0x08
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COMMIT_AVR_ONE_SHOT_MODE 0x10
#define MDP_COMMIT_PARTIAL_UPDATE_DUAL_ROI 0x20
#define MDP_COMMIT_UPDATE_BRIGHTNESS 0x40
#define MDP_COMMIT_CWB_EN 0x800
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COMMIT_CWB_DSPP 0x1000
#define MDP_COMMIT_VERSION_1_0 0x00010000
#define OUT_LAYER_COLOR_SPACE
#define MDP_HDR_EOTF_SMTPE_ST2084 0x2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_HDR_EOTF_HLG 0x3
#define MDP_PIXEL_ENCODING_RGB 0x0
#define MDP_PIXEL_ENCODING_YCBCR_444 0x1
#define MDP_PIXEL_ENCODING_YCBCR_422 0x2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_PIXEL_ENCODING_YCBCR_420 0x3
#define MDP_PIXEL_ENCODING_Y_ONLY 0x4
#define MDP_PIXEL_ENCODING_RAW 0x5
#define MDP_COLORIMETRY_RGB_SRGB 0x0
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COLORIMETRY_RGB_WIDE_FIXED_POINT 0x1
#define MDP_COLORIMETRY_RGB_WIDE_FLOAT_POINT 0x2
#define MDP_COLORIMETRY_RGB_ADOBE 0x3
#define MDP_COLORIMETRY_RGB_DPI_P3 0x4
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COLORIMETRY_RGB_CUSTOM 0x5
#define MDP_COLORIMETRY_RGB_ITU_R_BT_2020 0x6
#define MDP_COLORIMETRY_YCBCR_ITU_R_BT_601 0x0
#define MDP_COLORIMETRY_YCBCR_ITU_R_BT_709 0x1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COLORIMETRY_YCBCR_XV_YCC_601 0x2
#define MDP_COLORIMETRY_YCBCR_XV_YCC_709 0x3
#define MDP_COLORIMETRY_YCBCR_S_YCC_601 0x4
#define MDP_COLORIMETRY_YCBCR_ADOBE_YCC_601 0x5
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_COLORIMETRY_YCBCR_ITU_R_BT_2020_YCBCR_CONST 0x6
#define MDP_COLORIMETRY_YCBCR_ITU_R_BT_2020_YCBCR 0x7
#define MDP_DYNAMIC_RANGE_VESA 0x0
#define MDP_DYNAMIC_RANGE_CEA 0x1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_RGB_6_BPC 0x0
#define MDP_RGB_8_BPC 0x1
#define MDP_RGB_10_BPC 0x2
#define MDP_RGB_12_BPC 0x3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_RGB_16_BPC 0x4
#define MDP_YUV_8_BPC 0x1
#define MDP_YUV_10_BPC 0x2
#define MDP_YUV_12_BPC 0x3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_YUV_16_BPC 0x4
#define MDP_RAW_6_BPC 0x1
#define MDP_RAW_7_BPC 0x2
#define MDP_RAW_8_BPC 0x3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_RAW_10_BPC 0x4
#define MDP_RAW_12_BPC 0x5
#define MDP_RAW_14_BPC 0x6
#define MDP_RAW16_BPC 0x7
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_CONTENT_TYPE_NOT_DEFINED 0x0
#define MDP_CONTENT_TYPE_GRAPHICS 0x1
#define MDP_CONTENT_TYPE_PHOTO 0x2
#define MDP_CONTENT_TYPE_VIDEO 0x3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MDP_CONTENT_TYPE_GAME 0x4
struct mdp_layer_plane {
  int fd;
  uint32_t offset;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t stride;
};
struct mdp_layer_buffer {
  uint32_t width;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t height;
  uint32_t format;
  struct mdp_layer_plane planes[MAX_PLANES];
  uint32_t plane_count;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct mult_factor comp_ratio;
  int fence;
  uint32_t reserved;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct mdp_input_layer {
  uint32_t flags;
  uint32_t pipe_ndx;
  uint8_t horz_deci;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint8_t vert_deci;
  uint8_t alpha;
  uint16_t z_order;
  uint32_t transp_mask;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t bg_color;
  enum mdss_mdp_blend_op blend_op;
  enum mdp_color_space color_space;
  struct mdp_rect src_rect;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct mdp_rect dst_rect;
  void * scale;
  struct mdp_layer_buffer buffer;
  void * pp_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int error_code;
  uint32_t reserved[6];
};
struct mdp_output_layer {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t flags;
  uint32_t writeback_ndx;
  struct mdp_layer_buffer buffer;
  enum mdp_color_space color_space;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t reserved[5];
};
struct mdp_destination_scaler_data {
  uint32_t flags;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t dest_scaler_ndx;
  uint32_t lm_width;
  uint32_t lm_height;
  uint64_t scale;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct mdp_rect panel_roi;
};
struct mdp_layer_commit_v1 {
  uint32_t flags;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int release_fence;
  struct mdp_rect left_roi;
  struct mdp_rect right_roi;
  struct mdp_input_layer * input_layers;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t input_layer_cnt;
  struct mdp_output_layer * output_layer;
  int retire_fence;
  void * dest_scaler;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t dest_scaler_cnt;
  uint32_t bl_level;
  uint32_t reserved[MDP_LAYER_COMMIT_V1_PAD];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct mdp_layer_commit {
  uint32_t version;
  union {
    struct mdp_layer_commit_v1 commit_v1;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  };
};
struct mdp_point {
  uint32_t x;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t y;
};
struct mdp_async_layer {
  uint32_t flags;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t pipe_ndx;
  struct mdp_point src;
  struct mdp_point dst;
  int error_code;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t reserved[3];
};
struct mdp_position_update {
  struct mdp_async_layer * input_layers;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t input_layer_cnt;
};
#define MAX_DET_CURVES 3
struct mdp_det_enhance_data {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t enable;
  int16_t sharpen_level1;
  int16_t sharpen_level2;
  uint16_t clip;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t limit;
  uint16_t thr_quiet;
  uint16_t thr_dieout;
  uint16_t thr_low;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t thr_high;
  uint16_t prec_shift;
  int16_t adjust_a[MAX_DET_CURVES];
  int16_t adjust_b[MAX_DET_CURVES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int16_t adjust_c[MAX_DET_CURVES];
};
#define ENABLE_SCALE 0x1
#define ENABLE_DETAIL_ENHANCE 0x2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define ENABLE_DIRECTION_DETECTION 0x4
#define SCALER_LUT_SWAP 0x1
#define SCALER_LUT_DIR_WR 0x2
#define SCALER_LUT_Y_CIR_WR 0x4
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SCALER_LUT_UV_CIR_WR 0x8
#define SCALER_LUT_Y_SEP_WR 0x10
#define SCALER_LUT_UV_SEP_WR 0x20
#define FILTER_EDGE_DIRECTED_2D 0x0
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define FILTER_CIRCULAR_2D 0x1
#define FILTER_SEPARABLE_1D 0x2
#define FILTER_BILINEAR 0x3
#define FILTER_ALPHA_DROP_REPEAT 0x0
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define FILTER_ALPHA_BILINEAR 0x1
struct mdp_scale_data_v2 {
  uint32_t enable;
  int32_t init_phase_x[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int32_t phase_step_x[MAX_PLANES];
  int32_t init_phase_y[MAX_PLANES];
  int32_t phase_step_y[MAX_PLANES];
  uint32_t num_ext_pxls_left[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t num_ext_pxls_right[MAX_PLANES];
  uint32_t num_ext_pxls_top[MAX_PLANES];
  uint32_t num_ext_pxls_btm[MAX_PLANES];
  int32_t left_ftch[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int32_t left_rpt[MAX_PLANES];
  int32_t right_ftch[MAX_PLANES];
  int32_t right_rpt[MAX_PLANES];
  uint32_t top_rpt[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t btm_rpt[MAX_PLANES];
  uint32_t top_ftch[MAX_PLANES];
  uint32_t btm_ftch[MAX_PLANES];
  uint32_t roi_w[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t preload_x[MAX_PLANES];
  uint32_t preload_y[MAX_PLANES];
  uint32_t src_width[MAX_PLANES];
  uint32_t src_height[MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t dst_width;
  uint32_t dst_height;
  uint32_t y_rgb_filter_cfg;
  uint32_t uv_filter_cfg;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t alpha_filter_cfg;
  uint32_t blend_cfg;
  uint32_t lut_flag;
  uint32_t dir_lut_idx;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t y_rgb_cir_lut_idx;
  uint32_t uv_cir_lut_idx;
  uint32_t y_rgb_sep_lut_idx;
  uint32_t uv_sep_lut_idx;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct mdp_det_enhance_data detail_enhance;
  uint64_t reserved[8];
};
struct mdp_scale_luts_info {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint64_t dir_lut;
  uint64_t cir_lut;
  uint64_t sep_lut;
  uint32_t dir_lut_size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t cir_lut_size;
  uint32_t sep_lut_size;
};
#define MDP_QSEED3_LUT_CFG 0x1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct mdp_set_cfg {
  uint64_t flags;
  uint32_t len;
  uint64_t payload;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define HDR_PRIMARIES_COUNT 3
#define MDP_HDR_STREAM
struct mdp_hdr_stream {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t eotf;
  uint32_t display_primaries_x[HDR_PRIMARIES_COUNT];
  uint32_t display_primaries_y[HDR_PRIMARIES_COUNT];
  uint32_t white_point_x;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t white_point_y;
  uint32_t max_luminance;
  uint32_t min_luminance;
  uint32_t max_content_light_level;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t max_average_light_level;
  uint32_t pixel_encoding;
  uint32_t colorimetry;
  uint32_t range;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint32_t bits_per_component;
  uint32_t content_type;
  uint32_t reserved[5];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif

