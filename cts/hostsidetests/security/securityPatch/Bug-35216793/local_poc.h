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
#include <linux/types.h>

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

enum v4l2_buf_type {
  V4L2_BUF_TYPE_VIDEO_CAPTURE = 1,
  V4L2_BUF_TYPE_VIDEO_OUTPUT = 2,
  V4L2_BUF_TYPE_VIDEO_OVERLAY = 3,
  V4L2_BUF_TYPE_VBI_CAPTURE = 4,
  V4L2_BUF_TYPE_VBI_OUTPUT = 5,
  V4L2_BUF_TYPE_SLICED_VBI_CAPTURE = 6,
  V4L2_BUF_TYPE_SLICED_VBI_OUTPUT = 7,
#if 1
  /* Experimental */
  V4L2_BUF_TYPE_VIDEO_OUTPUT_OVERLAY = 8,
#endif
  V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE = 9,
  V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE = 10,
  V4L2_BUF_TYPE_SDR_CAPTURE = 11,
  /* Deprecated, do not use */
  V4L2_BUF_TYPE_PRIVATE = 0x80,
};

struct v4l2_timecode {
  __u32 type;
  __u32 flags;
  __u8 frames;
  __u8 seconds;
  __u8 minutes;
  __u8 hours;
  __u8 userbits[4];
};

struct v4l2_buffer {
  __u32 index;
  __u32 type;
  __u32 bytesused;
  __u32 flags;
  __u32 field;
  struct timeval timestamp;
  struct v4l2_timecode timecode;
  __u32 sequence;

  /* memory location */
  __u32 memory;
  union {
    __u32 offset;
    unsigned long userptr;
    struct v4l2_plane *planes;
    __s32 fd;
  } m;
  __u32 length;
  __u32 reserved2;
  __u32 reserved;
};

#define BASE_VIDIOC_PRIVATE 192 /* 192-255 are private */

enum msm_actuator_cfg_type_t {
  CFG_GET_ACTUATOR_INFO,
  CFG_SET_ACTUATOR_INFO,
  CFG_SET_DEFAULT_FOCUS,
  CFG_MOVE_FOCUS,
  CFG_SET_POSITION,
  CFG_ACTUATOR_POWERDOWN,
  CFG_ACTUATOR_POWERUP,
  CFG_ACTUATOR_INIT,
};

typedef unsigned int compat_uptr_t;
struct msm_actuator_move_params_t32 {
  int8_t dir;
  int8_t sign_dir;
  int16_t dest_step_pos;
  int32_t num_steps;
  uint16_t curr_lens_pos;
  compat_uptr_t ringing_params;
};

enum actuator_type {
  ACTUATOR_VCM,
  ACTUATOR_PIEZO,
  ACTUATOR_HVCM,
  ACTUATOR_BIVCM,
};

enum i2c_freq_mode_t {
  I2C_STANDARD_MODE,
  I2C_FAST_MODE,
  I2C_CUSTOM_MODE,
  I2C_CUSTOM1_MODE,
  I2C_CUSTOM2_MODE,
  I2C_FAST_PLUS_MODE,
  I2C_MAX_MODES,
};

enum msm_camera_i2c_reg_addr_type {
  MSM_CAMERA_I2C_BYTE_ADDR = 1,
  MSM_CAMERA_I2C_WORD_ADDR,
  MSM_CAMERA_I2C_3B_ADDR,
  MSM_CAMERA_I2C_ADDR_TYPE_MAX,
};

enum msm_camera_i2c_data_type {
  MSM_CAMERA_I2C_BYTE_DATA = 1,
  MSM_CAMERA_I2C_WORD_DATA,
  MSM_CAMERA_I2C_DWORD_DATA,
  MSM_CAMERA_I2C_SET_BYTE_MASK,
  MSM_CAMERA_I2C_UNSET_BYTE_MASK,
  MSM_CAMERA_I2C_SET_WORD_MASK,
  MSM_CAMERA_I2C_UNSET_WORD_MASK,
  MSM_CAMERA_I2C_SET_BYTE_WRITE_MASK_DATA,
  MSM_CAMERA_I2C_DATA_TYPE_MAX,
};

struct park_lens_data_t {
  uint32_t damping_step;
  uint32_t damping_delay;
  uint32_t hw_params;
  uint32_t max_step;
};

struct msm_actuator_params_t32 {
  enum actuator_type act_type;
  uint8_t reg_tbl_size;
  uint16_t data_size;
  uint16_t init_setting_size;
  uint32_t i2c_addr;
  enum i2c_freq_mode_t i2c_freq_mode;
  enum msm_camera_i2c_reg_addr_type i2c_addr_type;
  enum msm_camera_i2c_data_type i2c_data_type;
  compat_uptr_t reg_tbl_params;
  compat_uptr_t init_settings;
  struct park_lens_data_t park_lens;
};

struct msm_actuator_tuning_params_t32 {
  int16_t initial_code;
  uint16_t pwd_step;
  uint16_t region_size;
  uint32_t total_steps;
  compat_uptr_t region_params;
};

struct msm_actuator_set_info_t32 {
  struct msm_actuator_params_t32 actuator_params;
  struct msm_actuator_tuning_params_t32 af_tuning_params;
};

struct msm_actuator_get_info_t {
  uint32_t focal_length_num;
  uint32_t focal_length_den;
  uint32_t f_number_num;
  uint32_t f_number_den;
  uint32_t f_pix_num;
  uint32_t f_pix_den;
  uint32_t total_f_dist_num;
  uint32_t total_f_dist_den;
  uint32_t hor_view_angle_num;
  uint32_t hor_view_angle_den;
  uint32_t ver_view_angle_num;
  uint32_t ver_view_angle_den;
};

#define MAX_NUMBER_OF_STEPS 47
struct msm_actuator_set_position_t {
  uint16_t number_of_steps;
  uint32_t hw_params;
  uint16_t pos[MAX_NUMBER_OF_STEPS];
  uint16_t delay[MAX_NUMBER_OF_STEPS];
};

enum af_camera_name {
  ACTUATOR_MAIN_CAM_0,
  ACTUATOR_MAIN_CAM_1,
  ACTUATOR_MAIN_CAM_2,
  ACTUATOR_MAIN_CAM_3,
  ACTUATOR_MAIN_CAM_4,
  ACTUATOR_MAIN_CAM_5,
  ACTUATOR_WEB_CAM_0,
  ACTUATOR_WEB_CAM_1,
  ACTUATOR_WEB_CAM_2,
};

struct msm_actuator_cfg_data32 {
  int cfgtype;
  uint8_t is_af_supported;
  union {
    struct msm_actuator_move_params_t32 move;
    struct msm_actuator_set_info_t32 set_info;
    struct msm_actuator_get_info_t get_info;
    struct msm_actuator_set_position_t setpos;
    enum af_camera_name cam_name;
  } cfg;
};
struct region_params_t {
  /* [0] = ForwardDirection Macro boundary
     [1] = ReverseDirection Inf boundary
  */
  unsigned short step_bound[2];
  unsigned short code_per_step;
  /* qvalue for converting float type numbers to integer format */
  unsigned int qvalue;
};

#define VIDIOC_MSM_ACTUATOR_CFG32 \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 6, struct msm_actuator_cfg_data32)

#define VIDIOC_QBUF _IOWR('V', 15, struct v4l2_buffer)

#define I2C_SEQ_REG_DATA_MAX 1024

enum msm_ois_cfg_type_t {
  CFG_OIS_INIT,
  CFG_OIS_POWERDOWN,
  CFG_OIS_POWERUP,
  CFG_OIS_CONTROL,
  CFG_OIS_I2C_WRITE_SEQ_TABLE,
};

enum msm_ois_i2c_operation {
  MSM_OIS_WRITE = 0,
  MSM_OIS_POLL,
};

struct reg_settings_ois_t {
  uint16_t reg_addr;
  enum msm_camera_i2c_reg_addr_type addr_type;
  uint32_t reg_data;
  enum msm_camera_i2c_data_type data_type;
  enum msm_ois_i2c_operation i2c_operation;
  uint32_t delay;
};
struct msm_ois_params_t {
  uint16_t data_size;
  uint16_t setting_size;
  uint32_t i2c_addr;
  enum i2c_freq_mode_t i2c_freq_mode;
  enum msm_camera_i2c_reg_addr_type i2c_addr_type;
  enum msm_camera_i2c_data_type i2c_data_type;
  struct reg_settings_ois_t *settings;
};

struct msm_ois_set_info_t {
  struct msm_ois_params_t ois_params;
};

struct msm_camera_i2c_seq_reg_array {
  unsigned short reg_addr;
  unsigned char reg_data[I2C_SEQ_REG_DATA_MAX];
  unsigned short reg_data_size;
};

struct msm_camera_i2c_seq_reg_setting {
  struct msm_camera_i2c_seq_reg_array *reg_setting;
  unsigned short size;
  enum msm_camera_i2c_reg_addr_type addr_type;
  unsigned short delay;
};

struct msm_ois_cfg_data {
  int cfgtype;
  union {
    struct msm_ois_set_info_t set_info;
    struct msm_camera_i2c_seq_reg_setting *settings;
  } cfg;
};

#define VIDIOC_MSM_OIS_CFG \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 11, struct msm_ois_cfg_data)

#endif
