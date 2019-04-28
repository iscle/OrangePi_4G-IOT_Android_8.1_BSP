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
#ifndef __UAPI_LINUX_MSMB_CAMERA_H
#define __UAPI_LINUX_MSMB_CAMERA_H
#include <linux/videodev2.h>
#include <linux/types.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <linux/ioctl.h>
#define MSM_CAM_LOGSYNC_FILE_NAME "logsync"
#define MSM_CAM_LOGSYNC_FILE_BASEDIR "camera"
#define MSM_CAM_V4L2_IOCTL_NOTIFY _IOW('V', BASE_VIDIOC_PRIVATE + 30, struct msm_v4l2_event_data)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAM_V4L2_IOCTL_NOTIFY_META _IOW('V', BASE_VIDIOC_PRIVATE + 31, struct msm_v4l2_event_data)
#define MSM_CAM_V4L2_IOCTL_CMD_ACK _IOW('V', BASE_VIDIOC_PRIVATE + 32, struct msm_v4l2_event_data)
#define MSM_CAM_V4L2_IOCTL_NOTIFY_ERROR _IOW('V', BASE_VIDIOC_PRIVATE + 33, struct msm_v4l2_event_data)
#define MSM_CAM_V4L2_IOCTL_NOTIFY_DEBUG _IOW('V', BASE_VIDIOC_PRIVATE + 34, struct msm_v4l2_event_data)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAM_V4L2_IOCTL_DAEMON_DISABLED _IOW('V', BASE_VIDIOC_PRIVATE + 35, struct msm_v4l2_event_data)
#define QCAMERA_DEVICE_GROUP_ID 1
#define QCAMERA_VNODE_GROUP_ID 2
#define MSM_CAMERA_NAME "msm_camera"
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CONFIGURATION_NAME "msm_config"
#define MSM_CAMERA_SUBDEV_CSIPHY 0
#define MSM_CAMERA_SUBDEV_CSID 1
#define MSM_CAMERA_SUBDEV_ISPIF 2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_SUBDEV_VFE 3
#define MSM_CAMERA_SUBDEV_AXI 4
#define MSM_CAMERA_SUBDEV_VPE 5
#define MSM_CAMERA_SUBDEV_SENSOR 6
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_SUBDEV_ACTUATOR 7
#define MSM_CAMERA_SUBDEV_EEPROM 8
#define MSM_CAMERA_SUBDEV_CPP 9
#define MSM_CAMERA_SUBDEV_CCI 10
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_SUBDEV_LED_FLASH 11
#define MSM_CAMERA_SUBDEV_STROBE_FLASH 12
#define MSM_CAMERA_SUBDEV_BUF_MNGR 13
#define MSM_CAMERA_SUBDEV_SENSOR_INIT 14
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_SUBDEV_OIS 15
#define MSM_CAMERA_SUBDEV_FLASH 16
#define MSM_CAMERA_SUBDEV_EXT 17
#define MSM_MAX_CAMERA_SENSORS 5
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_MAX_STREAM_BUF 72
#define MSM_CAMERA_MAX_USER_BUFF_CNT 16
#define MSM_CAMERA_FEATURE_BASE 0x00010000
#define MSM_CAMERA_FEATURE_SHUTDOWN (MSM_CAMERA_FEATURE_BASE + 1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_STATUS_BASE 0x00020000
#define MSM_CAMERA_STATUS_FAIL (MSM_CAMERA_STATUS_BASE + 1)
#define MSM_CAMERA_STATUS_SUCCESS (MSM_CAMERA_STATUS_BASE + 2)
#define MSM_CAMERA_V4L2_EVENT_TYPE (V4L2_EVENT_PRIVATE_START + 0x00002000)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_EVENT_MIN 0
#define MSM_CAMERA_NEW_SESSION (MSM_CAMERA_EVENT_MIN + 1)
#define MSM_CAMERA_DEL_SESSION (MSM_CAMERA_EVENT_MIN + 2)
#define MSM_CAMERA_SET_PARM (MSM_CAMERA_EVENT_MIN + 3)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_GET_PARM (MSM_CAMERA_EVENT_MIN + 4)
#define MSM_CAMERA_MAPPING_CFG (MSM_CAMERA_EVENT_MIN + 5)
#define MSM_CAMERA_MAPPING_SES (MSM_CAMERA_EVENT_MIN + 6)
#define MSM_CAMERA_MSM_NOTIFY (MSM_CAMERA_EVENT_MIN + 7)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_EVENT_MAX (MSM_CAMERA_EVENT_MIN + 8)
#define MSM_CAMERA_PRIV_S_CROP (V4L2_CID_PRIVATE_BASE + 1)
#define MSM_CAMERA_PRIV_G_CROP (V4L2_CID_PRIVATE_BASE + 2)
#define MSM_CAMERA_PRIV_G_FMT (V4L2_CID_PRIVATE_BASE + 3)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_PRIV_S_FMT (V4L2_CID_PRIVATE_BASE + 4)
#define MSM_CAMERA_PRIV_TRY_FMT (V4L2_CID_PRIVATE_BASE + 5)
#define MSM_CAMERA_PRIV_METADATA (V4L2_CID_PRIVATE_BASE + 6)
#define MSM_CAMERA_PRIV_QUERY_CAP (V4L2_CID_PRIVATE_BASE + 7)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_PRIV_STREAM_ON (V4L2_CID_PRIVATE_BASE + 8)
#define MSM_CAMERA_PRIV_STREAM_OFF (V4L2_CID_PRIVATE_BASE + 9)
#define MSM_CAMERA_PRIV_NEW_STREAM (V4L2_CID_PRIVATE_BASE + 10)
#define MSM_CAMERA_PRIV_DEL_STREAM (V4L2_CID_PRIVATE_BASE + 11)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_PRIV_SHUTDOWN (V4L2_CID_PRIVATE_BASE + 12)
#define MSM_CAMERA_PRIV_STREAM_INFO_SYNC (V4L2_CID_PRIVATE_BASE + 13)
#define MSM_CAMERA_PRIV_G_SESSION_ID (V4L2_CID_PRIVATE_BASE + 14)
#define MSM_CAMERA_PRIV_CMD_MAX 20
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_CMD_SUCESS 0x00000001
#define MSM_CAMERA_BUF_MAP_SUCESS 0x00000002
#define MSM_CAMERA_ERR_EVT_BASE 0x00010000
#define MSM_CAMERA_ERR_CMD_FAIL (MSM_CAMERA_ERR_EVT_BASE + 1)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_ERR_MAPPING (MSM_CAMERA_ERR_EVT_BASE + 2)
#define MSM_CAMERA_ERR_DEVICE_BUSY (MSM_CAMERA_ERR_EVT_BASE + 3)
struct msm_v4l2_event_data {
  unsigned int command;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int status;
  unsigned int session_id;
  unsigned int stream_id;
  unsigned int map_op;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int map_buf_idx;
  unsigned int notify;
  unsigned int arg_value;
  unsigned int ret_value;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int v4l2_event_type;
  unsigned int v4l2_event_id;
  unsigned int handle;
  unsigned int nop6;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int nop7;
  unsigned int nop8;
  unsigned int nop9;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_v4l2_format_data {
  enum v4l2_buf_type type;
  unsigned int width;
  unsigned int height;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int pixelformat;
  unsigned char num_planes;
  unsigned int plane_sizes[VIDEO_MAX_PLANES];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define msm_v4l2_fourcc(a,b,c,d) ((__u32) (a) | ((__u32) (b) << 8) | ((__u32) (c) << 16) | ((__u32) (d) << 24))
#define MSM_V4L2_PIX_FMT_STATS_COMB v4l2_fourcc('S', 'T', 'C', 'M')
#define MSM_V4L2_PIX_FMT_STATS_AE v4l2_fourcc('S', 'T', 'A', 'E')
#define MSM_V4L2_PIX_FMT_STATS_AF v4l2_fourcc('S', 'T', 'A', 'F')
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_V4L2_PIX_FMT_STATS_AWB v4l2_fourcc('S', 'T', 'W', 'B')
#define MSM_V4L2_PIX_FMT_STATS_IHST v4l2_fourcc('I', 'H', 'S', 'T')
#define MSM_V4L2_PIX_FMT_STATS_CS v4l2_fourcc('S', 'T', 'C', 'S')
#define MSM_V4L2_PIX_FMT_STATS_RS v4l2_fourcc('S', 'T', 'R', 'S')
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_V4L2_PIX_FMT_STATS_BG v4l2_fourcc('S', 'T', 'B', 'G')
#define MSM_V4L2_PIX_FMT_STATS_BF v4l2_fourcc('S', 'T', 'B', 'F')
#define MSM_V4L2_PIX_FMT_STATS_BHST v4l2_fourcc('B', 'H', 'S', 'T')
enum smmu_attach_mode {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  NON_SECURE_MODE = 0x01,
  SECURE_MODE = 0x02,
  MAX_PROTECTION_MODE = 0x03,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_camera_smmu_attach_type {
  enum smmu_attach_mode attach;
};
struct msm_camera_user_buf_cont_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int buf_cnt;
  unsigned int buf_idx[MSM_CAMERA_MAX_USER_BUFF_CNT];
};
struct msm_camera_return_buf {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u32 index;
  __u32 reserved;
};
#define MSM_CAMERA_PRIV_IOCTL_ID_BASE 0
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_CAMERA_PRIV_IOCTL_ID_RETURN_BUF 1
struct msm_camera_private_ioctl_arg {
  __u32 id;
  __u32 size;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  __u32 result;
  __u32 reserved;
  __user __u64 ioctl_ptr;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define VIDIOC_MSM_CAMERA_PRIVATE_IOCTL_CMD _IOWR('V', BASE_VIDIOC_PRIVATE, struct msm_camera_private_ioctl_arg)
#endif

