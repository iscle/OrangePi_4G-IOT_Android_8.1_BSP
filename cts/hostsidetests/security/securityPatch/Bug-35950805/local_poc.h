/**
 * Copyright (C) 2017 The Android Open Source Project
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


#ifndef __CMD_H__
#define __CMD_H__

#define _IOC_NRBITS 8
#define _IOC_TYPEBITS 8

/*
 * Let any architecture override either of the following before
 * including this file.
 */

#ifndef _IOC_SIZEBITS
#define _IOC_SIZEBITS 14
#endif

#ifndef _IOC_DIRBITS
#define _IOC_DIRBITS 2
#endif

#define _IOC_NRMASK ((1 << _IOC_NRBITS) - 1)
#define _IOC_TYPEMASK ((1 << _IOC_TYPEBITS) - 1)
#define _IOC_SIZEMASK ((1 << _IOC_SIZEBITS) - 1)
#define _IOC_DIRMASK ((1 << _IOC_DIRBITS) - 1)

#define _IOC_NRSHIFT 0
#define _IOC_TYPESHIFT (_IOC_NRSHIFT + _IOC_NRBITS)
#define _IOC_SIZESHIFT (_IOC_TYPESHIFT + _IOC_TYPEBITS)
#define _IOC_DIRSHIFT (_IOC_SIZESHIFT + _IOC_SIZEBITS)

/*
 * Direction bits, which any architecture can choose to override
 * before including this file.
 */

#ifndef _IOC_NONE
#define _IOC_NONE 0U
#endif

#ifndef _IOC_WRITE
#define _IOC_WRITE 1U
#endif

#ifndef _IOC_READ
#define _IOC_READ 2U
#endif

#define _IOC_TYPECHECK(t) (sizeof(t))
#define _IOC(dir, type, nr, size)                          \
  (((dir) << _IOC_DIRSHIFT) | ((type) << _IOC_TYPESHIFT) | \
   ((nr) << _IOC_NRSHIFT) | ((size) << _IOC_SIZESHIFT))

/* used to create numbers */
#define _IO(type, nr) _IOC(_IOC_NONE, (type), (nr), 0)
#define _IOR(type, nr, size) \
  _IOC(_IOC_READ, (type), (nr), (_IOC_TYPECHECK(size)))
#define _IOW(type, nr, size) \
  _IOC(_IOC_WRITE, (type), (nr), (_IOC_TYPECHECK(size)))
#define _IOWR(type, nr, size) \
  _IOC(_IOC_READ | _IOC_WRITE, (type), (nr), (_IOC_TYPECHECK(size)))

#define MSMFB_IOCTL_MAGIC 'm'

struct mdp_pp_feature_version {
  uint32_t pp_feature;
  uint32_t version_info;
};
#define MSMFB_MDP_PP_GET_FEATURE_VERSION \
  _IOWR(MSMFB_IOCTL_MAGIC, 171, struct mdp_pp_feature_version)

struct fb_cmap_user {
  __u32 start; /* First entry	*/
  __u32 len;   /* Number of entries */
  __u16 *red;  /* Red values	*/
  __u16 *green;
  __u16 *blue;
  __u16 *transp; /* transparency, can be NULL */
};
#define FBIOPUTCMAP 0x4605

/* QSEED3 LUT sizes */
#define DIR_LUT_IDX 1
#define DIR_LUT_COEFFS 200
#define CIR_LUT_IDX 9
#define CIR_LUT_COEFFS 60
#define SEP_LUT_IDX 10
#define SEP_LUT_COEFFS 60

struct mdp_scale_luts_info {
  uint64_t dir_lut;
  uint64_t cir_lut;
  uint64_t sep_lut;
  uint32_t dir_lut_size;
  uint32_t cir_lut_size;
  uint32_t sep_lut_size;
};

struct mdp_set_cfg {
  uint64_t flags;
  uint32_t len;
  uint64_t payload;
};
#define MDP_QSEED3_LUT_CFG 0x1

#define MDP_IOCTL_MAGIC 'S'
#define MSMFB_MDP_SET_CFG _IOW(MDP_IOCTL_MAGIC, 130, struct mdp_set_cfg)

#define MDP_LAYER_COMMIT_V1_PAD 4

struct mdp_rect {
  uint32_t x;
  uint32_t y;
  uint32_t w;
  uint32_t h;
};

enum mdss_mdp_blend_op {
  BLEND_OP_NOT_DEFINED = 0,
  BLEND_OP_OPAQUE,
  BLEND_OP_PREMULTIPLIED,
  BLEND_OP_COVERAGE,
  BLEND_OP_MAX,
};

enum mdp_color_space {
  MDP_CSC_ITU_R_601,
  MDP_CSC_ITU_R_601_FR,
  MDP_CSC_ITU_R_709,
};

struct mdp_layer_plane {
  /* DMA buffer file descriptor information. */
  int fd;

  /* Pixel offset in the dma buffer. */
  uint32_t offset;

  /* Number of bytes in one scan line including padding bytes. */
  uint32_t stride;
};

#define MAX_PLANES 4

struct mult_factor {
  uint32_t numer;
  uint32_t denom;
};
struct mdp_layer_buffer {
  uint32_t width;
  uint32_t height;
  uint32_t format;

  /* plane to hold the fd, offset, etc for all color components */
  struct mdp_layer_plane planes[MAX_PLANES];

  /* valid planes count in layer planes list */
  uint32_t plane_count;

  /* compression ratio factor, value depends on the pixel format */
  struct mult_factor comp_ratio;

  /*
   * SyncFence associated with this buffer. It is used in two ways.
   *
   * 1. Driver waits to consume the buffer till producer signals in case
   * of primary and external display.
   *
   * 2. Writeback device uses buffer structure for output buffer where
   * driver is producer. However, client sends the fence with buffer to
   * indicate that consumer is still using the buffer and it is not ready
   * for new content.
   */
  int fence;

  /* 32bits reserved value for future usage. */
  uint32_t reserved;
};

struct mdp_input_layer {
  uint32_t flags;
  uint32_t pipe_ndx;
  uint8_t horz_deci;
  uint8_t vert_deci;
  uint8_t alpha;
  uint16_t z_order;
  uint32_t transp_mask;
  uint32_t bg_color;

  /* blend operation defined in "mdss_mdp_blend_op" enum. */
  enum mdss_mdp_blend_op blend_op;

  /* color space of the source */
  enum mdp_color_space color_space;

  struct mdp_rect src_rect;

  /*
   * Destination rectangle, the position and size of image on screen.
   * This should always be within panel boundaries.
   */
  struct mdp_rect dst_rect;

  /* Scaling parameters. */
  void __user *scale;

  /* Buffer attached with each layer. Device uses it for commit call. */
  struct mdp_layer_buffer buffer;

  void __user *pp_info;
  int error_code;
  uint32_t reserved[6];
};

struct mdp_output_layer {
  /*
   * Flag to enable/disable properties for layer configuration. Refer
   * layer flag config section for all possible flags.
   */
  uint32_t flags;

  /*
   * Writeback destination selection for output. Client provides the index
   * in validate and commit call.
   */
  uint32_t writeback_ndx;

  /* Buffer attached with output layer. Device uses it for commit call */
  struct mdp_layer_buffer buffer;

  /* color space of the destination */
  enum mdp_color_space color_space;

  /* 32bits reserved value for future usage. */
  uint32_t reserved[5];
};

struct mdp_layer_commit_v1 {
  uint32_t flags;
  int release_fence;
  struct mdp_rect left_roi;
  struct mdp_rect right_roi;
  struct mdp_input_layer __user *input_layers;

  /* Input layer count present in input list */
  uint32_t input_layer_cnt;

  struct mdp_output_layer __user *output_layer;

  int retire_fence;
  void __user *dest_scaler;
  uint32_t dest_scaler_cnt;

  uint32_t reserved[MDP_LAYER_COMMIT_V1_PAD];
};

struct mdp_layer_commit {
  /*
   * 32bit version indicates the commit structure selection
   * from union. Lower 16bits indicates the minor version while
   * higher 16bits indicates the major version. It selects the
   * commit structure based on major version selection. Minor version
   * indicates that reserved fields are in use.
   *
   * Current supported version is 1.0 (Major:1 Minor:0)
   */
  uint32_t version;
  union {
    /* Layer commit/validate definition for V1 */
    struct mdp_layer_commit_v1 commit_v1;
  };
};

#define MDP_IOCTL_MAGIC 'S'
/* atomic commit ioctl used for validate and commit request */
#define MSMFB_ATOMIC_COMMIT _IOWR(MDP_IOCTL_MAGIC, 128, void *)

#endif
